package flabbergast;

import java.util.List;
import java.util.function.Supplier;
import jline.console.completer.Completer;

class AttributeCompleter implements Completer {
  private final Supplier<Frame> source;

  public AttributeCompleter(Supplier<Frame> source) {
    this.source = source;
  }

  @Override
  public int complete(String buffer, int cursor, final List<CharSequence> candidates) {
    source
        .get()
        .getContext()
        .stream()
        .flatMap(Frame::stream)
        .distinct()
        .filter(attribute -> buffer.regionMatches(true, 0, attribute, 0, cursor))
        .forEach(candidates::add);

    return candidates.isEmpty() ? -1 : 0;
  }
}
