package flabbergast;

/**
 * The null type.
 *
 * <p>For type dispatch, there are plenty of reasons to distinguish between the underlying VM's null
 * and Flabbergast's Null, and this type makes this possible.
 */
public class Unit {
  public static Unit NULL = new Unit();

  private Unit() {}

  @Override
  public String toString() {
    return "Null";
  }
}
