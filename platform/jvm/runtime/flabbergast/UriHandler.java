package flabbergast;

import java.net.URI;

/** Resolver for “From” URIs */
public interface UriHandler {
  static String convertLibraryUriToClass(URI uri) {
    return String.format(
        "flabbergast.library.%s.Root", uri.getSchemeSpecificPart().replace('/', '$'));
  }
  /**
   * The relative order of this resolver
   *
   * <p>If a resolver has a lower number, it will be given the opportunity to return a URI first. If
   * it passes, another resolver may have a chance.
   *
   * @return
   */
  int getPriority();

  /** A human-friendly name for this URI handler. */
  String getUriName();

  /**
   * Resolve a URI
   *
   * @param taskMaster the calling task master
   * @param uri the URI string provided by the Flabbergast program
   * @return
   */
  Maybe<Future> resolveUri(TaskMaster taskMaster, URI uri);
}
