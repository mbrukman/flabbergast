package flabbergast;

import java.net.URI;
import java.util.Set;

class BuiltInLibraries extends UriInstantiator implements UriService {

  private BuiltInLibraries() {}

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    return this;
  }

  @Override
  public int getPriority() {
    return -100;
  }

  @Override
  public String getUriName() {
    return "built-in libraries";
  }

  @Override
  public Maybe<Class<? extends Future>> resolveUri(URI uri) {
    return Maybe.of(uri)
        .filter(x -> x.getScheme().equals("lib"))
        .map(UriHandler::convertLibraryUriToClass)
        .map(typeName -> Class.forName(typeName).asSubclass(Future.class));
  }
}
