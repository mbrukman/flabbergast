package flabbergast;

import java.net.URI;

public abstract class UriInstantiator implements UriHandler {
  @Override
  public final Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri) {
    return resolveUri(uri)
        .filter(Future.class::isAssignableFrom, "Invalid class type.")
        .map(clazz -> clazz.getDeclaredConstructor(TaskMaster.class).newInstance(taskMaster));
  }

  protected abstract Maybe<Class<? extends Future>> resolveUri(URI uri);
}
