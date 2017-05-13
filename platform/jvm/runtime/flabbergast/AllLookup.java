package flabbergast;

import java.util.ArrayList;
import java.util.List;

/** Perform contextual lookup, but return a list of all possible answers. */
public final class AllLookup extends BaseContextualLookup {

  private Context context;

  private final List<Any> items = new ArrayList<>();

  public AllLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String... names) {
    super(taskMaster, sourceReference, context, names);
    this.context = context;
  }

  @Override
  protected void outOfFrames() {
    emit(
        Any.of(
            Frame.create(
                getTaskMaster(),
                getSourceReference(),
                context,
                null,
                new ArrayValueBuilder(items))));
  }

  @Override
  protected boolean receiveTerminal(Any value, int frame) {
    items.add(value);
    return true;
  }
}
