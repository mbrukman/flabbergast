package flabbergast;

public class FailureFuture extends Future {

  public static Definition create(String message) {
    return (taskMaster, sourceReference, context, self) ->
        new FailureFuture(taskMaster, sourceReference, message);
  }

  private final String message;
  private final SourceReference sourceReference;

  public FailureFuture(TaskMaster taskMaster, SourceReference reference, String message) {
    super(taskMaster);
    sourceReference = reference;
    this.message = message;
  }

  @Override
  protected void run() {
    taskMaster.reportOtherError(sourceReference, message);
  }
}
