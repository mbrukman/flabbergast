package flabbergast;

import java.util.Set;
import java.util.function.Consumer;

/**
 * A stack element that bifurcates.
 *
 * <p>These are typical of instantiation and attribute overriding that have both a container and an
 * ancestor.
 */
public class JunctionReference extends BasicSourceReference {
  private final SourceReference junction;

  public JunctionReference(
      String message,
      String filename,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn,
      SourceReference caller,
      SourceReference junction) {
    super(message, filename, startLine, startColumn, endLine, endColumn, caller);
    this.junction = junction;
  }

  /** The stack trace of the non-local component. (i.e., the ancestor's stack trace). */
  public SourceReference getJunction() {
    return junction;
  }

  @Override
  public void write(Consumer<String> writer, String prefix, Set<SourceReference> seen) {
    writer.accept(prefix);
    writer.accept(caller == null ? "└─┬ " : "├─┬ ");
    writeMessage(writer);
    final boolean before = seen.contains(this);
    if (before) {
      writer.accept(" (previously mentioned)");
    } else {
      seen.add(this);
    }
    writer.accept("\n");

    if (before) {
      writer.accept(prefix);
      writer.accept(caller == null ? "  " : "┊ ");
      writer.accept("┊\n");
    } else {
      junction.write(writer, prefix + (caller == null ? "  " : "│ "), seen);
      if (caller != null) {
        caller.write(writer, prefix, seen);
      }
    }
  }
}
