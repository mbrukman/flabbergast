package flabbergast;

final class CheckResult extends AcceptOrFail implements ErrorCollector {
  private boolean success;

  @Override
  public void accept(Frame value) {
    value.get(
        "value",
        new AcceptOrFail() {
          @Override
          public void accept(boolean value) {
            success = value;
          }

          @Override
          protected void fail(String type) {
            success = false;
          }
        });
  }

  @Override
  public void emitError(SourceLocation location, String error) {
    success = false;
  }

  @Override
  protected void fail(String type) {
    success = false;
  }

  public boolean getSuccess() {
    return success;
  }
}
