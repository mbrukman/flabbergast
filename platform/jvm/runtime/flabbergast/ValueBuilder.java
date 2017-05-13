package flabbergast;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

/** Puts pre-calculated values into a frame */
public final class ValueBuilder implements Frame.Builder, DefinitionSource {
  private final Map<String, Any> attributes = new HashMap<>();

  public boolean has(String name) {
    return attributes.containsKey(name);
  }

  public boolean has(Stringish name) {
    return has(name.toString());
  }

  @Override
  public void prepare(
      SourceReference sourceReference,
      Context context,
      Frame self,
      Map<String, Future> attributes) {
    this.attributes
        .entrySet()
        .stream()
        .forEach(entry -> attributes.put(entry.getKey(), entry.getValue().future()));
  }

  public void set(long ordinal, Any value) {
    attributes.put(SupportFunctions.ordinalNameStr(ordinal), value);
  }

  public void set(String name, Any value) {
    attributes.put(name, value);
  }

  public void set(Stringish name, Any value) {
    set(name.toString(), value);
  }

  @Override
  public Stream<Entry<String, DefinitionProcessor>> stream() {
    return attributes
        .entrySet()
        .stream()
        .map(entry -> new Pair<>(entry.getKey(), new DefinitionHolder(entry.getValue().compute())));
  }
}
