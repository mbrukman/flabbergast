package flabbergast;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Description of the current Flabbergast stack.
 *
 * <p>Since the Flabbergast stack is utterly alien to the underlying VM (it can bifurcate), this
 * object records it such that it can be presented to the user when needed.
 */
public class BasicSourceReference extends SourceReference {
  protected final SourceReference caller;
  private final int endColumn;
  private final int endLine;
  private final String fileName;
  private final String message;
  private final int startColumn;
  private final int startLine;

  public BasicSourceReference(
      String message,
      String filename,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn,
      SourceReference caller) {
    this.message = message;
    fileName = filename;
    this.startLine = startLine;
    this.startColumn = startColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
    this.caller = caller;
  }

  public SourceReference getCaller() {
    return caller;
  }

  public int getEndColumn() {
    return endColumn;
  }

  public int getEndLine() {
    return endLine;
  }

  public String getFileName() {
    return fileName;
  }

  public String getMessage() {
    return message;
  }

  public int getStartColumn() {
    return startColumn;
  }

  public int getStartLine() {
    return startLine;
  }

  @Override
  public void write(Consumer<String> writer, String prefix, Set<SourceReference> seen) {
    writer.accept(prefix);
    writer.accept(caller == null ? "└ " : "├ ");
    writeMessage(writer);
    final boolean before = seen.contains(this);
    if (before) {
      writer.accept(" (previously mentioned)");
    } else {
      seen.add(this);
    }
    writer.accept("\n");
    if (caller != null) {
      if (before) {
        writer.accept(prefix);
        writer.accept("┊\n");
      } else {
        caller.write(writer, prefix, seen);
      }
    }
  }

  protected void writeMessage(Consumer<String> writer) {
    writer.accept(fileName);
    writer.accept(": ");
    writer.accept(Integer.toString(startLine));
    writer.accept(":");
    writer.accept(Integer.toString(startColumn));
    writer.accept("-");
    writer.accept(Integer.toString(endLine));
    writer.accept(":");
    writer.accept(Integer.toString(endColumn));
    writer.accept(": ");
    writer.accept(message);
  }
}
