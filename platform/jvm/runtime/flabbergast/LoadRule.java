package flabbergast;

/** Indicate what kinds of URI handlers should be enabled. */
public enum LoadRule {
  /** There is a real live user we can interact with */
  INTERACTIVE,
  /** Allow loading of precompiled cached libraries */
  PRECOMPILED,
  /**
   * Do not allow access to external resources
   *
   * <p>This includes any network activity, databases, or the file system. Resource files and
   * databases are permitted.
   */
  SANDBOXED
}
