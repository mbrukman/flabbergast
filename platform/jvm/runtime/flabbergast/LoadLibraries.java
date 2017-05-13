package flabbergast;

public abstract class LoadLibraries extends UriInstantiator {

  private ResourcePathFinder finder;

  public ResourcePathFinder getFinder() {
    return finder;
  }

  public void setFinder(ResourcePathFinder finder) {
    this.finder = finder;
  }
}
