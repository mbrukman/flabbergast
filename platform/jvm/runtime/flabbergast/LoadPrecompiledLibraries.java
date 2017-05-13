package flabbergast;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UriService.class)
class LoadPrecompiledLibraries implements UriService {

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    return flags.contains(LoadRule.PRECOMPILED)
        ? new LoadLibraries() {
          private URLClassLoader classLoader =
              new URLClassLoader(finder.urls().toArray(URL[]::new));

          @Override
          public int getPriority() {
            return 0;
          }

          @Override
          public String getUriName() {
            return "pre-compiled libraries";
          }

          @Override
          @SuppressWarnings("unchecked")
          protected Maybe<Class<? extends Future>> resolveUri(URI uri) {
            return Maybe.of(uri)
                .filter(x -> x.getScheme().equals("lib"))
                .map(UriHandler::convertLibraryUriToClass)
                .map(typeName -> (Class<? extends Future>) classLoader.loadClass(typeName));
          }
        }
        : null;
  }
}
