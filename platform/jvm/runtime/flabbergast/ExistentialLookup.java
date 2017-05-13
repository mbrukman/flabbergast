package flabbergast;

public class ExistentialLookup extends BaseContextualLookup {

  public ExistentialLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names) {
    super(taskMaster, sourceReference, context, names);
  }

  @Override
  protected void outOfFrames() {
    emit(Any.of(false));
  }

  @Override
  protected boolean receiveTerminal(Any value, int frame) {
    emit(Any.of(true));
    return false;
  }
}
