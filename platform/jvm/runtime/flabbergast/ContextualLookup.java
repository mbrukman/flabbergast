package flabbergast;

/** Perform “contextual lookup” as described in the Flabbergast language specification */
public final class ContextualLookup extends BaseContextualLookup {

  public static final LookupHandler HANDLER =
      new LookupHandler() {

        @Override
        public String description() {
          return "contextual";
        }

        @Override
        public Future lookup(
            TaskMaster taskMaster,
            SourceReference sourceReference,
            Context context,
            String[] names) {
          return new ContextualLookup(taskMaster, sourceReference, context, names);
        }
      };

  /** Create a lookup for a particular set of names to be performed in a context provided later. */
  public static Definition create(String... names) {
    return createHelper(names, ContextualLookup::new);
  }

  public ContextualLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String... names) {
    super(taskMaster, sourceReference, context, names);
  }

  @Override
  protected void outOfFrames() {
    taskMaster.reportLookupError(this, null);
  }

  @Override
  protected boolean receiveTerminal(Any value, int frame) {
    emit(value);
    return false;
  }
}
