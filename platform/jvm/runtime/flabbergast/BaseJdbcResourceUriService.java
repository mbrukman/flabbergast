package flabbergast;

import java.io.File;
import java.net.URI;
import java.util.Properties;
import java.util.regex.Pattern;
import org.kohsuke.MetaInfServices;

/**
 * The base for connecting to a JDBC database using “From sql:”
 *
 * <p>Non-abstract derived classes should be annotated with {@link MetaInfServices} for {@link
 * UriService} to be available to Flabbergast programs.
 */
public abstract class BaseJdbcResourceUriService extends BaseJdbcUriService {
  private static final Maybe.WhineyPredicate<String> VALID_FRAGMENT =
      Maybe.makeWhiney(Pattern.compile("^[A-Za-z0-9/]*$").asPredicate());
  private final String[] extensions;
  /**
   * Create a new JDBC connection handler.
   *
   * @param uriSchema the name to appear in the URI ("foo" will result in "sql:foo:")
   * @param friendlyName the name of the database as it should be shown to the user
   */
  public BaseJdbcResourceUriService(String uriSchema, String friendlyName, String... extensions) {
    super(uriSchema, friendlyName, true);
    this.extensions = extensions;
  }

  /**
   * Create the JDBC URI from the connection information
   *
   * @param file the file containing the database
   * @param properties
   * @return
   */
  protected abstract Maybe<String> parse(File file, Properties properties);

  @Override
  protected final Maybe<String> parse(URI uri, Properties properties, ResourcePathFinder finder) {
    return Maybe.of(uri.getSchemeSpecificPart())
        .filter(VALID_FRAGMENT, "Invalid resource name specified.")
        .optionalMap(
            fragment -> finder.find(fragment, extensions),
            "Cannot find resource “" + uri.toString() + "”.")
        .flatMap(path -> parse(path, properties));
  }
}
