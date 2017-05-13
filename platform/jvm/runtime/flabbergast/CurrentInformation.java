package flabbergast;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class CurrentInformation implements UriService {

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    return new UriHandler() {
      private final Map<String, Future> information = new HashMap<>();

      {
        information.put("interactive", Any.of(flags.contains(LoadRule.INTERACTIVE)).future());
        add("login", System.getProperty("user.name"));
        add("directory", Paths.get(".").toAbsolutePath().toString());
        information.put("version", Any.of(Configuration.VERSION).future());
        add("machine/directory_separator", File.separator);
        add("machine/name", System.getProperty("os.name"));
        add("machine/path_separator", File.pathSeparator);
        add("machine/line_ending", String.format("%n"));
        add("vm/name", "JVM");
        add("vm/vendor", System.getProperty("java.vendor"));
        add("vm/version", System.getProperty("java.version"));
      }

      private void add(String key, String value) {
        information.put(key, Any.of(value).future());
      }

      @Override
      public int getPriority() {
        return 0;
      }

      @Override
      public String getUriName() {
        return "current information";
      }

      @Override
      public Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri) {
        return Maybe.of(uri)
            .filter(x -> x.getScheme().equals("current"))
            .map(URI::getSchemeSpecificPart)
            .map(information::get);
      }
    };
  }
}
