package flabbergast;

import java.net.URI;
import java.net.URL;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UriService.class)
class FileHandler extends UrlConnectionHandler {

  @Override
  protected Maybe<URL> convert(URI uri) {
    return Maybe.of(uri).filter(x -> x.getScheme().equals("file")).map(URI::toURL);
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public String getUriName() {
    return "local files";
  }
}
