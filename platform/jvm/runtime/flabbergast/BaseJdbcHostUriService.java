package flabbergast;

import java.net.URI;
import java.util.Properties;
import org.kohsuke.MetaInfServices;

/**
 * The base for connecting to a JDBC database using “From sql+x://host/db”
 *
 * <p>Non-abstract derived classes should be annotated with {@link MetaInfServices} for {@link
 * UriService} to be available to Flabbergast programs.
 */
public abstract class BaseJdbcHostUriService extends BaseJdbcUriService {

  private final int defaultPort;
  private final String passwordParam;
  private final String userParam;

  /**
   * Create a new JDBC connection handler.
   *
   * @param uriSchema the name to appear in the URI ("foo" will result in "sql+foo:")
   * @param friendlyName the name of the database as it should be shown to the user
   * @param userParam the key for the JDBC {@link Properties} that stores the connection's user name
   * @param passwordparam the key for the JDBC {@link Properties} that stores the connection's
   *     password
   * @param defaultPort the port to use if the user does not specify
   */
  public BaseJdbcHostUriService(
      String uriSchema,
      String friendlyName,
      String userParam,
      String passwordparam,
      int defaultPort) {
    super(uriSchema, friendlyName, false);
    this.userParam = userParam;
    this.passwordParam = passwordparam;
    this.defaultPort = defaultPort;
  }

  /** Create the JDBC URI from the connection information */
  protected abstract Maybe<String> parse(
      String host, int port, String catalog, Properties properties);

  @Override
  protected final Maybe<String> parse(URI uri, Properties properties, ResourcePathFinder finder) {
    return Maybe.of(uri)
        .map(URI::parseServerAuthority)
        .filter(x -> x.getHost() != null, "Host not defined.")
        .filter(x -> x.getPath() != null, "Catalog not defined.")
        .flatMap(
            x -> {
              if (x.getUserInfo() != null) {
                String[] user = x.getUserInfo().split(":", 2);
                properties.setProperty(userParam, user[0]);
                if (user.length > 1) {
                  properties.setProperty(passwordParam, user[1]);
                }
              }

              return parse(
                  uri.getHost(),
                  uri.getPort() == -1 ? defaultPort : uri.getPort(),
                  uri.getPath(),
                  properties);
            });
  }
}
