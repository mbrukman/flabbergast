package flabbergast;

import java.io.DataInputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Set;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UriService.class)
public class HttpHandler implements UriHandler, UriService {

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    return flags.contains(LoadRule.SANDBOXED) ? null : this;
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public String getUriName() {
    return "HTTP files";
  }

  @Override
  public final Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri) {

    return Maybe.of(uri)
        .filter(x -> uri.getScheme().equals("http") || uri.getScheme().equals("https"))
        .map(
            x -> {
              final URLConnection conn = x.toURL().openConnection();
              final byte[] data = new byte[conn.getContentLength()];
              try (final DataInputStream inputStream = new DataInputStream(conn.getInputStream())) {
                inputStream.readFully(data);
              }
              final ValueBuilder builder = new ValueBuilder();
              builder.set("data", Any.of(data));
              builder.set("content_type", Any.of(conn.getContentType()));
              return Any.of(Frame.create("http" + uri.hashCode(), uri.toString(), builder))
                  .future();
            });
  }
}
