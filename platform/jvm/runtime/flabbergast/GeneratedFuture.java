package flabbergast;

public class GeneratedFuture extends Future {

  private Runnable current;
  private boolean entered;

  public GeneratedFuture(TaskMaster taskMaster, Runnable initial) {
    super(taskMaster);
    this.current = initial;
  }

  protected final void dispatch(
      SourceReference sourceReference,
      Any left,
      Any right,
      LongBiConsumer longConsumer,
      DoubleBiConsumer doubleConsumer) {
    left.accept(
        new AcceptOrFail() {

          @Override
          public void accept(double leftValue) {
            dispatch(sourceReference, leftValue, right, doubleConsumer);
          }

          @Override
          public void accept(long leftValue) {
            dispatch(sourceReference, leftValue, right, longConsumer, doubleConsumer);
          }

          @Override
          protected void fail(String type) {
            taskMaster.reportOtherError(
                sourceReference, String.format("Expected Float or Int, but got %s.", type));
          }
        });
  }

  protected final void dispatch(
      SourceReference sourceReference,
      double leftValue,
      Any right,
      DoubleBiConsumer doubleConsumer) {
    right.accept(
        new AcceptOrFail() {
          @Override
          public void accept(double rightValue) {
            next(() -> doubleConsumer.accept(leftValue, rightValue));
          }

          @Override
          public void accept(long rightValue) {
            next(() -> doubleConsumer.accept(leftValue, rightValue));
          }

          @Override
          protected void fail(String type) {
            taskMaster.reportOtherError(
                sourceReference, String.format("Expected Float or Int, but got %s.", type));
          }
        });
  }

  protected final void dispatch(
      SourceReference sourceReference,
      long leftValue,
      Any right,
      LongBiConsumer longConsumer,
      DoubleBiConsumer doubleConsumer) {
    right.accept(
        new AcceptOrFail() {

          @Override
          public void accept(double rightValue) {
            next(() -> doubleConsumer.accept(leftValue, rightValue));
          }

          @Override
          public void accept(long rightValue) {
            next(() -> longConsumer.accept(leftValue, rightValue));
          }

          @Override
          protected void fail(String type) {
            taskMaster.reportOtherError(
                sourceReference, String.format("Expected Float or Int, but got %s.", type));
          }
        });
  }

  protected final void next(Runnable next) {
    current = next;
    if (!entered) {
      slot();
    }
  }

  @Override
  protected final void run() {
    entered = true;
    while (current != null) {
      Runnable todo = current;
      current = null;
      todo.run();
    }
    entered = false;
  }
}
