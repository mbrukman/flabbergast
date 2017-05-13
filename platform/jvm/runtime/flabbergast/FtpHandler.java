package flabbergast;

import java.net.URI;
import java.net.URL;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UriService.class)
class FtpHandler extends UrlConnectionHandler {

  @Override
  protected Maybe<URL> convert(URI uri) {
    return Maybe.of(uri)
        .filter(x -> x.getScheme().equals("ftp") || x.getScheme().equals("ftps"))
        .map(URI::toURL);
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public String getUriName() {
    return "FTP files";
  }
}
