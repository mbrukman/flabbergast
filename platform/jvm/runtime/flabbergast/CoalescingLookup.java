package flabbergast;

public class CoalescingLookup extends BaseContextualLookup {
  private static final ApplyAny<Boolean> IS_NULL =
      new ApplyAny<Boolean>() {

        @Override
        public Boolean apply() {
          return true;
        }

        @Override
        public Boolean apply(boolean value) {
          return false;
        }

        @Override
        public Boolean apply(byte[] value) {
          return false;
        }

        @Override
        public Boolean apply(double value) {
          return false;
        }

        @Override
        public Boolean apply(Frame value) {
          return false;
        }

        @Override
        public Boolean apply(long value) {
          return false;
        }

        @Override
        public Boolean apply(LookupHandler value) {
          return false;
        }

        @Override
        public Boolean apply(Stringish value) {
          return false;
        }

        @Override
        public Boolean apply(Template value) {
          return false;
        }
      };

  public CoalescingLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names) {
    super(taskMaster, sourceReference, context, names);
  }

  @Override
  protected void outOfFrames() {
    getTaskMaster().reportLookupError(this, null);
  }

  @Override
  protected boolean receiveTerminal(Any value, int frame) {
    if (value.apply(IS_NULL)) {
      return true;
    } else {
      emit(value);
      return false;
    }
  }
}
