package flabbergast;

public abstract class BaseContextualLookup extends BaseLookup {
  private class FinalAttempt extends Attempt {
    public FinalAttempt(int frame, Frame sourceFrame) {
      super(getNameCount() - 1, frame, sourceFrame);
    }

    @Override
    public void consume(Any value) {
      if (receiveTerminal(value, getFrame())) {
        activateNext();
      }
    }
  }

  private class IntermediateAttempt extends Attempt {
    Frame resultFrame;

    public IntermediateAttempt(int name, int frame, Frame sourceFrame) {
      super(name, frame, sourceFrame);
    }

    @Override
    public void consume(Any result) {
      result.accept(
          new AcceptOrFail() {
            @Override
            public void accept(Frame value) {
              resultFrame = value;
              final Attempt nextAttempt =
                  getName() == getNameCount() - 2
                      ? new FinalAttempt(getFrame(), resultFrame)
                      : new IntermediateAttempt(getName() + 1, getFrame(), resultFrame);
              if (resultFrame.get(BaseContextualLookup.this.getName(getName() + 1), nextAttempt)) {
                return;
              }
              activateNext();
            }

            @Override
            protected void fail(String type) {
              taskMaster.reportLookupError(BaseContextualLookup.this, type);
            }
          });
    }
  }

  private int frameIndex = 0;

  public BaseContextualLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String... names) {
    super(taskMaster, sourceReference, context, names);
  }

  private final void activateNext() {
    while (frameIndex < getFrameCount()) {
      final int index = frameIndex++;
      final Attempt rootAttempt =
          getNameCount() == 1
              ? new FinalAttempt(index, getFrame(index))
              : new IntermediateAttempt(0, index, getFrame(index));
      if (getFrame(index).get(getName(0), rootAttempt)) {
        return;
      }
    }
    outOfFrames();
  }

  protected abstract void outOfFrames();

  protected abstract boolean receiveTerminal(Any value, int frame);

  @Override
  protected final void run() {
    activateNext();
  }
}
