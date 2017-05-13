package flabbergast;

import java.util.Map.Entry;
import java.util.stream.Stream;

public interface DefinitionSource {
  Stream<Entry<String, DefinitionProcessor>> stream();
}
