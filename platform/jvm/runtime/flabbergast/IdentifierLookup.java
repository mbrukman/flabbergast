package flabbergast;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IdentifierLookup extends BaseContextualLookup {

  public IdentifierLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names) {
    super(taskMaster, sourceReference, context, names);
  }

  @Override
  protected void outOfFrames() {
    getTaskMaster().reportLookupError(this, null);
  }

  @Override
  protected boolean receiveTerminal(Any value, int frame) {
    ArrayValueBuilder builder =
        new ArrayValueBuilder(
            IntStream.range(0, getNameCount())
                .mapToObj(name -> Any.of(get(name, frame).getId().toString()))
                .collect(Collectors.toList()));
    emit(Any.of(Frame.create(getTaskMaster(), getSourceReference(), Context.EMPTY, null, builder)));
    return false;
  }
}
