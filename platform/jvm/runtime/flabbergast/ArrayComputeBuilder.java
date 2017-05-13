package flabbergast;

import flabbergast.Frame.Builder;
import java.util.List;

final class ArrayComputeBuilder implements Frame.RuntimeBuilder {

  private final List<Definition> values;

  ArrayComputeBuilder(List<Definition> values) {
    super();
    this.values = values;
  }

  @Override
  public Builder attach(TaskMaster taskMaster) {
    return (sourceReference, context, self, attributes) -> {
      for (int it = 0; it < values.size(); it++) {
        attributes.put(
            SupportFunctions.ordinalNameStr(it + 1),
            values.get(it).invoke(taskMaster, sourceReference, context, self));
      }
    };
  }
}
