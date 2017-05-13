package flabbergast;
/**
 * Unbox a boxed value ({@link Any}) into the matching native type for the Flabbergast type for a
 * limited subset of types and produce an error otherwise.
 *
 * <p>By default, all types will produce an error. To handle a type, override the matching method.
 */
public abstract class AcceptOrFail implements AcceptAny {

  @Override
  public void accept() {
    fail("Null");
  }

  @Override
  public void accept(boolean value) {
    fail("Bool");
  }

  @Override
  public void accept(byte[] value) {
    fail("Bin");
  }

  @Override
  public void accept(double value) {
    fail("Double");
  }

  @Override
  public void accept(Frame value) {
    fail("Frame");
  }

  @Override
  public void accept(long value) {
    fail("Int");
  }

  @Override
  public void accept(LookupHandler value) {
    fail("LookupHandler");
  }

  @Override
  public void accept(Stringish value) {
    fail("Str");
  }

  @Override
  public void accept(Template value) {
    fail("Template");
  }

  /**
   * Handle an unwanted type.
   *
   * @param type the Flabbergast name for the type matched.
   */
  protected abstract void fail(String type);
}
