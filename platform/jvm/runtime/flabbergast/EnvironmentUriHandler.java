package flabbergast;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

class EnvironmentUriHandler implements UriHandler, UriService {
  private static final Maybe.WhineyPredicate<String> VALID_ENV =
      Maybe.makeWhiney(Pattern.compile("^[A-Z_][A-Z0-9_]*$").asPredicate());

  private EnvironmentUriHandler() {}

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
    return "Environment variables";
  }

  @Override
  public Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri) {
    return Maybe.of(uri)
        .filter(x -> x.getScheme().equals("env"))
        .map(URI::getSchemeSpecificPart)
        .filter(VALID_ENV, "Environment variable does not follow POSIX naming.")
        .map(System::getenv)
        .map(Any::of)
        .map(Any::future);
  }
}
