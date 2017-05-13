package flabbergast;

import java.io.DataInputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Set;

abstract class UrlConnectionHandler implements UriHandler, UriService {
  protected abstract Maybe<URL> convert(URI uri);

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    return flags.contains(LoadRule.SANDBOXED) ? null : this;
  }

  @Override
  public final Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri) {
    return convert(uri)
        .map(
            url -> {
              final URLConnection conn = url.openConnection();
              final byte[] data = new byte[conn.getContentLength()];
              try (final DataInputStream inputStream = new DataInputStream(conn.getInputStream())) {
                inputStream.readFully(data);
              }
              return Any.of(data).future();
            });
  }
}
