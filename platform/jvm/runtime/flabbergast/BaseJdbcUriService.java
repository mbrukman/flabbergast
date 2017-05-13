package flabbergast;

import flabbergast.MarshalledFrame.Transform;
import java.net.URI;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.kohsuke.MetaInfServices;

/**
 * The base for connecting to a JDBC database using “From sql+x:”
 *
 * <p>Non-abstract derived classes should be annotated with {@link MetaInfServices} for {@link
 * UriService} to be available to Flabbergast programs.
 */
public abstract class BaseJdbcUriService implements UriService {
  private final class JdbcUriHandler implements UriHandler {
    private ResourcePathFinder finder;

    @Override
    public int getPriority() {
      return 0;
    }

    @Override
    public String getUriName() {
      return "JDBC gateway for " + friendlyName;
    }

    @Override
    public Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri) {
      final Properties properties = new Properties();
      return Maybe.of(uri)
          .filter(x -> x.getScheme().equals(uriPrefix))
          .flatMap(
              x -> {
                setFixed(properties);
                Maybe.of(x.getQuery())
                    .reduce(
                        () -> {
                          String[] parts = uri.getSchemeSpecificPart().split("?", 2);
                          return Maybe.of(parts.length > 1 ? parts[1] : null);
                        })
                    .flatStream(AMPERSAND::splitAsStream)
                    .forEach(
                        paramChunk ->
                            Maybe.of(paramChunk.split("=", 2))
                                .filter(
                                    chunkParts -> chunkParts.length == 2,
                                    String.format("Invalid URL parameter “%s”.", paramChunk))
                                .forEach(chunk -> parseProperty(chunk[0], chunk[1], properties)));
                return parse(x, properties, finder);
              })
          .map(jdbcUri -> DriverManager.getConnection(jdbcUri, properties))
          .filter(Objects::nonNull, "Failed to create connection.")
          .peek(
              connection -> {
                connection.setAutoCommit(false);
                connection.setReadOnly(true);
              })
          .map(
              connection ->
                  new Connection() {
                    public void abort(Executor executor) throws SQLException {
                      connection.abort(executor);
                    }

                    public void clearWarnings() throws SQLException {
                      connection.clearWarnings();
                    }

                    public void close() throws SQLException {
                      connection.close();
                    }

                    public void commit() throws SQLException {
                      connection.commit();
                    }

                    public Array createArrayOf(String typeName, Object[] elements)
                        throws SQLException {
                      return connection.createArrayOf(typeName, elements);
                    }

                    public Blob createBlob() throws SQLException {
                      return connection.createBlob();
                    }

                    public Clob createClob() throws SQLException {
                      return connection.createClob();
                    }

                    public NClob createNClob() throws SQLException {
                      return connection.createNClob();
                    }

                    public SQLXML createSQLXML() throws SQLException {
                      return connection.createSQLXML();
                    }

                    public Statement createStatement() throws SQLException {
                      return connection.createStatement();
                    }

                    public Statement createStatement(int resultSetType, int resultSetConcurrency)
                        throws SQLException {
                      return connection.createStatement(resultSetType, resultSetConcurrency);
                    }

                    public Statement createStatement(
                        int resultSetType, int resultSetConcurrency, int resultSetHoldability)
                        throws SQLException {
                      return connection.createStatement(
                          resultSetType, resultSetConcurrency, resultSetHoldability);
                    }

                    public Struct createStruct(String typeName, Object[] attributes)
                        throws SQLException {
                      return connection.createStruct(typeName, attributes);
                    }

                    public boolean getAutoCommit() throws SQLException {
                      return connection.getAutoCommit();
                    }

                    public String getCatalog() throws SQLException {
                      return connection.getCatalog();
                    }

                    public Properties getClientInfo() throws SQLException {
                      return connection.getClientInfo();
                    }

                    public String getClientInfo(String name) throws SQLException {
                      return connection.getClientInfo(name);
                    }

                    public int getHoldability() throws SQLException {
                      return connection.getHoldability();
                    }

                    public DatabaseMetaData getMetaData() throws SQLException {
                      return connection.getMetaData();
                    }

                    public int getNetworkTimeout() throws SQLException {
                      return connection.getNetworkTimeout();
                    }

                    public String getSchema() throws SQLException {
                      return connection.getSchema();
                    }

                    public int getTransactionIsolation() throws SQLException {
                      return connection.getTransactionIsolation();
                    }

                    public Map<String, Class<?>> getTypeMap() throws SQLException {
                      return connection.getTypeMap();
                    }

                    public SQLWarning getWarnings() throws SQLException {
                      return connection.getWarnings();
                    }

                    public boolean isClosed() throws SQLException {
                      return connection.isClosed();
                    }

                    public boolean isReadOnly() throws SQLException {
                      return connection.isReadOnly();
                    }

                    public boolean isValid(int timeout) throws SQLException {
                      return connection.isValid(timeout);
                    }

                    public boolean isWrapperFor(Class<?> iface) throws SQLException {
                      return connection.isWrapperFor(iface);
                    }

                    public String nativeSQL(String sql) throws SQLException {
                      return connection.nativeSQL(sql);
                    }

                    public CallableStatement prepareCall(String sql) throws SQLException {
                      return connection.prepareCall(sql);
                    }

                    public CallableStatement prepareCall(
                        String sql, int resultSetType, int resultSetConcurrency)
                        throws SQLException {
                      return connection.prepareCall(sql, resultSetType, resultSetConcurrency);
                    }

                    public CallableStatement prepareCall(
                        String sql,
                        int resultSetType,
                        int resultSetConcurrency,
                        int resultSetHoldability)
                        throws SQLException {
                      return connection.prepareCall(
                          sql, resultSetType, resultSetConcurrency, resultSetHoldability);
                    }

                    public PreparedStatement prepareStatement(String sql) throws SQLException {
                      return connection.prepareStatement(sql);
                    }

                    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
                        throws SQLException {
                      return connection.prepareStatement(sql, autoGeneratedKeys);
                    }

                    public PreparedStatement prepareStatement(
                        String sql, int resultSetType, int resultSetConcurrency)
                        throws SQLException {
                      return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
                    }

                    public PreparedStatement prepareStatement(
                        String sql,
                        int resultSetType,
                        int resultSetConcurrency,
                        int resultSetHoldability)
                        throws SQLException {
                      return connection.prepareStatement(
                          sql, resultSetType, resultSetConcurrency, resultSetHoldability);
                    }

                    public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
                        throws SQLException {
                      return connection.prepareStatement(sql, columnIndexes);
                    }

                    public PreparedStatement prepareStatement(String sql, String[] columnNames)
                        throws SQLException {
                      return connection.prepareStatement(sql, columnNames);
                    }

                    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
                      connection.releaseSavepoint(savepoint);
                    }

                    public void rollback() throws SQLException {
                      connection.rollback();
                    }

                    public void rollback(Savepoint savepoint) throws SQLException {
                      connection.rollback(savepoint);
                    }

                    public void setAutoCommit(boolean autoCommit) throws SQLException {
                      connection.setAutoCommit(autoCommit);
                    }

                    public void setCatalog(String catalog) throws SQLException {
                      connection.setCatalog(catalog);
                    }

                    public void setClientInfo(Properties properties) throws SQLClientInfoException {
                      connection.setClientInfo(properties);
                    }

                    public void setClientInfo(String name, String value)
                        throws SQLClientInfoException {
                      connection.setClientInfo(name, value);
                    }

                    public void setHoldability(int holdability) throws SQLException {
                      connection.setHoldability(holdability);
                    }

                    public void setNetworkTimeout(Executor executor, int milliseconds)
                        throws SQLException {
                      connection.setNetworkTimeout(executor, milliseconds);
                    }

                    public void setReadOnly(boolean readOnly) throws SQLException {
                      connection.setReadOnly(readOnly);
                    }

                    public Savepoint setSavepoint() throws SQLException {
                      return connection.setSavepoint();
                    }

                    public Savepoint setSavepoint(String name) throws SQLException {
                      return connection.setSavepoint(name);
                    }

                    public void setSchema(String schema) throws SQLException {
                      connection.setSchema(schema);
                    }

                    public void setTransactionIsolation(int level) throws SQLException {
                      connection.setTransactionIsolation(level);
                    }

                    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
                      connection.setTypeMap(map);
                    }

                    public String toString() {
                      return uri.toString();
                    }

                    public <T> T unwrap(Class<T> iface) throws SQLException {
                      return connection.unwrap(iface);
                    }
                  })
          .map(
              connection ->
                  MarshalledFrame.create(
                      taskMaster,
                      new NativeSourceReference(uri.toString()),
                      null,
                      null,
                      connection,
                      getTransforms(providerName)))
          .map(Any::of)
          .map(Any::future);
    }

    public void setFinder(ResourcePathFinder finder) {
      this.finder = finder;
    }
  }

  private interface SqlFunction<T, R> {
    static <T, R> Function<T, R> silence(SqlFunction<T, R> function) {
      return arg -> {
        try {
          return function.apply(arg);
        } catch (final SQLException e) {
          return null;
        }
      };
    }

    R apply(T arg) throws SQLException;
  }

  private static final Pattern AMPERSAND = Pattern.compile("&");

  private static Stream<Transform<Connection>> getTransforms(String provider) {
    return Stream.of(
        MarshalledFrame.extractStr(
            "provider",
            (Connection c) -> {
              final int plusPosition = provider.indexOf('+');
              return plusPosition == -1 ? provider : provider.substring(0, plusPosition);
            }),
        MarshalledFrame.extractStr("database", SqlFunction.silence(Connection::getCatalog)),
        MarshalledFrame.extractStr(
            "product_name", SqlFunction.silence(c -> c.getMetaData().getDatabaseProductName())),
        MarshalledFrame.extractStr(
            "product_version",
            SqlFunction.silence(c -> c.getMetaData().getDatabaseProductVersion())),
        MarshalledFrame.extractStr(
            "driver_name", SqlFunction.silence(c -> c.getMetaData().getDriverName())),
        MarshalledFrame.extractStr(
            "driver_version", SqlFunction.silence(c -> c.getMetaData().getDriverVersion())),
        MarshalledFrame.extractStr("platform", (Connection c) -> "JDBC"));
  }

  private boolean allowSandboxed;

  private final String friendlyName;

  public final String providerName;

  private final String uriPrefix;

  /**
   * Create a JDBC connection handler.
   *
   * @param uriSchema the name to appear in the URI ("foo" will result in "sql+foo:")
   * @param friendlyName the name of the database as it should be shown to the user
   * @param allowSandboxed allow access to this database even when {@link LoadRule#SANDBOXED}
   */
  protected BaseJdbcUriService(String uriSchema, String friendlyName, boolean allowSandboxed) {
    this.allowSandboxed = allowSandboxed;
    final int plusIndex = uriSchema.indexOf('+');
    providerName = plusIndex == -1 ? uriSchema : uriSchema.substring(0, plusIndex);
    uriPrefix = "sql+" + uriSchema;
    this.friendlyName = friendlyName;
  }

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    if (!allowSandboxed && flags.contains(LoadRule.SANDBOXED)) {
      return null;
    }
    final JdbcUriHandler handler = new JdbcUriHandler();
    handler.setFinder(finder);
    return handler;
  }

  /**
   * Create a JDBC URI string (or error) for the database
   *
   * @param uri the URI provided by the user
   * @param properties the properties for the database connection, filled with all the user-provided
   *     parameters and fixed strings
   * @param finder a resource finder if the database is on disk
   */
  protected abstract Maybe<String> parse(URI uri, Properties properties, ResourcePathFinder finder);

  /**
   * Update the connection properties for a provided URI parameter
   *
   * @param name the parameter's name, as specified in Flabbergast
   * @param value the parameter's value, as specified in Flabbergast
   * @param output the properties object to modify
   */
  protected abstract void parseProperty(String name, String value, Properties output);

  /**
   * Set any fixed parameters on the connection
   *
   * <p>This happens before {@ #parseProperty(String, String, Properties)}, so it can override
   * values if necessary.
   */
  protected abstract void setFixed(Properties properties);
}
