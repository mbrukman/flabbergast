package flabbergast;

import java.util.Set;
import java.util.function.Consumer;

/** A stack element that captures part of the language infrastructure. */
public class NativeSourceReference extends SourceReference {

  private final String name;

  public NativeSourceReference(String name) {
    this.name = name;
  }

  @Override
  public void write(Consumer<String> writer, String prefix, Set<SourceReference> seen) {
    writer.accept(prefix);
    writer.accept("└ <");
    writer.accept(name);
    writer.accept(">\n");
  }
}
