package flabbergast;

import java.net.URI;
import java.util.Set;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UriService.class)
class SettingsHandler implements UriHandler, UriService {

  private SettingsHandler() {}

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
    return "VM-specific settings";
  }

  @Override
  public Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri) {
    if (!uri.getScheme().equals("settings")) {
      return null;
    }
    return Maybe.of(uri.getSchemeSpecificPart())
        .map(System::getProperty)
        .map(Any::of)
        .map(Any::future);
  }
}
