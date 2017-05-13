package flabbergast;

public abstract class CompileOutput {

  public static CompileOutput error(String message) {
    return null;
  }

  public abstract void collect(BuildCollector collector);
}
