package flabbergast;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JdbcLookup extends AssistedFuture {

  public interface ParameterWriter {

    void setParameter(PreparedStatement statement, int index, String name, long id)
        throws SQLException;
  }

  private class QueryLookup extends BaseLookup {

    public QueryLookup(
        TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names) {
      super(taskMaster, sourceReference, context, names);
    }

    @Override
    protected void run() {
      for (int frame = 0; frame < getFrameCount(); frame++) {
        if (getFrame(frame)
            .get(
                idAttribute,
                new Attempt(0, frame, getFrame(frame)) {

                  @Override
                  public void consume(Any result) {
                    result.accept(
                        new AcceptOrFail() {
                          @Override
                          public void accept(long initialId) {
                            long id = initialId;
                            for (int name = 0; name < getNameCount(); name++) {
                              try (PreparedStatement statement =
                                  connection.prepareStatement(query)) {
                                for (int index = 0; index < writers.size(); index++) {
                                  writers
                                      .get(index)
                                      .setParameter(
                                          statement, index, QueryLookup.this.getName(name), id);
                                }
                                try (ResultSet rs = statement.executeQuery()) {
                                  if (rs.next()) {
                                    long nextId = rs.getLong(1);
                                    if (rs.next()) {
                                      getTaskMaster()
                                          .reportOtherError(
                                              getSourceReference(),
                                              String.format(
                                                  "Multiple results for name “%s” for %d.",
                                                  QueryLookup.this.getName(name), id));
                                      return;
                                    }
                                    id = nextId;
                                  } else {
                                    getTaskMaster()
                                        .reportOtherError(
                                            getSourceReference(),
                                            String.format(
                                                "No results for name “%s” for %d.",
                                                QueryLookup.this.getName(name), id));
                                    return;
                                  }
                                }
                              } catch (SQLException e) {
                                taskMaster.reportOtherError(getSourceReference(), e.getMessage());
                                return;
                              }
                            }
                            emit(Any.of(id));
                          }

                          @Override
                          protected void fail(String type) {
                            getTaskMaster()
                                .reportOtherError(
                                    getSourceReference(),
                                    String.format(
                                        "Expected Int for “%s”, but got %s", idAttribute, type));
                          }
                        });
                  }
                })) {
          return;
        }
      }
      getTaskMaster()
          .reportOtherError(
              getSourceReference(),
              String.format(
                  "Cannot find attribute “%s” to start database-based lookup.", idAttribute));
    }
  }

  public static final Frame ID_WRITER =
      createFrame("id", (statement, index, name, id) -> statement.setLong(index, id));

  public static final Frame NAME_WRITER =
      createFrame("name", (statement, index, name, id) -> statement.setString(index, name));

  private static Frame createFrame(String name, ParameterWriter writer) {
    return MarshalledFrame.create("sql_lookup_" + name, "sql", writer, Stream.empty());
  }

  private Connection connection;

  private String idAttribute;

  private String query;

  private List<ParameterWriter> writers;

  public JdbcLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(taskMaster, sourceReference, context);
  }

  @Override
  protected void resolve() {
    String description =
        String.format(
            "sql/lookup id=%s connection=“%s” query=“%s”",
            idAttribute, connection.toString(), query);
    complete(
        Any.of(
            new LookupHandler() {

              @Override
              public String description() {
                return description;
              }

              @Override
              public Future lookup(
                  TaskMaster taskMaster,
                  SourceReference sourceReference,
                  Context context,
                  String[] names) {
                return new QueryLookup(taskMaster, sourceReference, context, names);
              }
            }));
  }

  @Override
  protected void setup() {
    find(asMarshalled(Connection.class, false, "From “sql+:”"), x -> connection = x, "connection");
    find(asString(false), x -> query = x, "sql_query");
    find(asSymbol(false), x -> idAttribute = x, "id_attribute");
    findAll(
        asMarshalled(ParameterWriter.class, false, "lib:sql lookup_parameter"),
        x -> writers = x.values().stream().collect(Collectors.toList()),
        "sql_parameters");
  }
}
