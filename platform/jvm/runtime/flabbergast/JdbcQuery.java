package flabbergast;

import flabbergast.Frame.RuntimeBuilder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class JdbcQuery extends BaseFunctionInterop<Frame> {
  private interface ComputeRetriever {
    Definition access(ResultSet rs, int column) throws SQLException;
  }

  private interface MakeRetriever {
    Stream<Retriever> make(int position, String columnName, int columnType);
  }

  private interface Retriever {
    void store(
        ResultSet rs,
        ValueBuilder valueBuilder,
        DefinitionBuilder computeBuilder,
        Ptr<String> attrName)
        throws SQLException;
  }

  private interface ValueRetriever {
    Any access(ResultSet rs, int column) throws SQLException;

    default Any accessWithNull(ResultSet rs, int column) throws SQLException {
      final Any result = access(rs, column);
      return rs.wasNull() ? Any.unit() : result;
    }
  }

  private static final List<MakeRetriever> MAKE_RETRIEVERS =
      Arrays.asList(
          (position, columnName, columnType) ->
              columnName.equals("ATTRNAME")
                      && IntStream.of(Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR)
                          .anyMatch(t -> t == columnType)
                  ? Stream.of(
                      (rs, valueBuilder, computeBuilder, attrName) ->
                          attrName.set(rs.getString(position)))
                  : Stream.empty(),
          (position, columnName, columnType) ->
              columnName.equals("ATTRNAME")
                      && IntStream.of(Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT)
                          .anyMatch(t -> t == columnType)
                  ? Stream.of(
                      (rs, valueBuilder, computeBuilder, attrName) ->
                          attrName.set(SupportFunctions.ordinalNameStr(rs.getLong(position))))
                  : Stream.empty(),
          (position, columnName, columnType) ->
              columnName.startsWith("$")
                      && TaskMaster.verifySymbol(columnName.substring(1))
                      && IntStream.of(Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR)
                          .anyMatch(t -> t == columnType)
                  ? Stream.of(
                      (rs, valueBuilder, computeBuilder, attrName) -> {
                        final String result = rs.getString(position);
                        computeBuilder.set(
                            columnName.substring(1),
                            result == null
                                ? Any.unit().compute()
                                : ContextualLookup.create(result.split("\\.")));
                      })
                  : Stream.empty(),
          makeValue(
              (rs, position) -> {
                final String str = rs.getString(position);
                return str == null ? Any.unit() : Any.of(str);
              },
              Types.CHAR,
              Types.VARCHAR,
              Types.LONGVARCHAR),
          makeValue(
              (rs, position) -> Any.of(rs.getLong(position)),
              Types.TINYINT,
              Types.SMALLINT,
              Types.INTEGER,
              Types.BIGINT),
          makeValue(
              (rs, position) -> Any.of(rs.getDouble(position)),
              Types.REAL,
              Types.FLOAT,
              Types.DOUBLE),
          makeValue((rs, position) -> Any.of(rs.getBoolean(position)), Types.BIT),
          makeCompute(
              (rs, position) ->
                  MarshalledFrame.create(
                      ZonedDateTime.ofInstant(
                          rs.getTimestamp(
                                  position, Calendar.getInstance(TimeZone.getTimeZone("UTC")))
                              .toInstant(),
                          ZoneOffset.UTC),
                      getTimeTransforms()),
              Types.DATE,
              Types.TIME,
              Types.TIMESTAMP),
          makeValue(
              (rs, position) -> Any.of(rs.getBytes(position)),
              Types.BLOB,
              Types.BINARY,
              Types.NCHAR,
              Types.NCLOB,
              Types.VARBINARY));

  private static MakeRetriever makeCompute(ComputeRetriever retriever, int... sqlTypes) {
    return (position, columnName, columnType) ->
        TaskMaster.verifySymbol(columnName) && IntStream.of(sqlTypes).anyMatch(t -> t == columnType)
            ? Stream.of(
                (rs, valueBuilder, computeBuilder, attrName) ->
                    computeBuilder.set(columnName, retriever.access(rs, position)))
            : Stream.empty();
  }

  private static MakeRetriever makeValue(ValueRetriever retriever, int... sqlTypes) {
    return (position, columnName, columnType) ->
        TaskMaster.verifySymbol(columnName) && IntStream.of(sqlTypes).anyMatch(t -> t == columnType)
            ? Stream.of(
                (rs, valueBuilder, computeBuilder, attrName) ->
                    valueBuilder.set(columnName, retriever.accessWithNull(rs, position)))
            : Stream.empty();
  }

  private Connection connection;
  private String query;

  public JdbcQuery(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, taskMaster, sourceReference, context, self);
  }

  @Override
  public Frame computeResult() throws Exception {
    try (final Statement stmt = connection.createStatement();
        final ResultSet rs = stmt.executeQuery(query)) {

      final ResultSetMetaData rsmd = rs.getMetaData();
      final List<Retriever> retrievers =
          IntStream.rangeClosed(1, rsmd.getColumnCount())
              .filter(
                  position -> {
                    try {
                      return rsmd.getColumnType(position) != Types.NULL;
                    } catch (final SQLException e) {
                      throw new IllegalArgumentException(e.getMessage());
                    }
                  })
              .mapToObj(
                  position -> {
                    String name;
                    int type;
                    String typeName;
                    try {
                      name = rsmd.getColumnLabel(position);
                      type = rsmd.getColumnType(position);
                      typeName = rsmd.getColumnTypeName(position);
                    } catch (final SQLException e) {
                      throw new IllegalArgumentException(e.getMessage());
                    }
                    return MAKE_RETRIEVERS
                        .stream()
                        .flatMap(maker -> maker.make(position, name, type))
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    String.format(
                                        "Cannot convert SQL type “%s” for column “%s” into Flabbergast type.",
                                        typeName, name)));
                  })
              .collect(Collectors.toList());

      final DefinitionBuilder rows = new DefinitionBuilder();
      for (int it = 1; rs.next(); it++) {
        final ValueBuilder valueBuilder = new ValueBuilder();
        final DefinitionBuilder computeBuilder = new DefinitionBuilder();
        final Ptr<String> name = new Ptr<>();
        for (final Retriever r : retrievers) {
          r.store(rs, valueBuilder, computeBuilder, name);
        }
        final int index = it;
        rows.set(
            name.orElseGet(() -> SupportFunctions.ordinalNameStr(index)),
            Template.instantiate(
                new RuntimeBuilder[] {valueBuilder, computeBuilder},
                "<sql>",
                it,
                0,
                it,
                rsmd.getColumnCount(),
                "sql_row_tmpl"));
      }
      return Frame.create(taskMaster, sourceReference, context, self, rows);
    }
  }

  @Override
  public void setup() {
    find(asMarshalled(Connection.class, false, "“From sql+:”"), x -> connection = x, "connection");
    find(asString(false), x -> query = x, "sql_query");
  }
}
