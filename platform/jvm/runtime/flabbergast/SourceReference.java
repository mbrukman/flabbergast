package flabbergast;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Description of the current Flabbergast stack. */
public abstract class SourceReference {
  /** Write the current stack trace. */
  public void write(Consumer<String> writer, String prefix) {
    write(writer, prefix, new HashSet<SourceReference>());
  }

  protected abstract void write(Consumer<String> writer, String prefix, Set<SourceReference> seen);
}
