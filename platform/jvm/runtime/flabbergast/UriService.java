package flabbergast;

import java.util.Set;

public interface UriService {

  UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags);
}
