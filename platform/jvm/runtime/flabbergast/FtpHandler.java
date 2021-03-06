package flabbergast;

import java.net.URL;

public class FtpHandler extends UrlConnectionHandler {

  public static final FtpHandler INSTANCE = new FtpHandler();

  @Override
  protected URL convert(String uri) throws Exception {

    if (!uri.startsWith("ftp:") && !uri.startsWith("ftps:")) return null;
    return new URL(uri);
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
