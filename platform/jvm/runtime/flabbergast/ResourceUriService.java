package flabbergast;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UriService.class)
class ResourceUriService implements UriService {

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    return new UrlConnectionHandler() {

      @Override
      protected Maybe<URL> convert(URI uri) {

        if (!uri.getScheme().equals("res")) {
          return Maybe.empty();
        }
        return Maybe.of(uri)
            .map(URI::getSchemeSpecificPart)
            .optionalMap(tail -> finder.find(tail, ""))
            .map(File::toURI)
            .map(URI::toURL);
      }

      @Override
      public int getPriority() {
        return 0;
      }

      @Override
      public String getUriName() {
        return "resource files";
      }
    };
  }
}
