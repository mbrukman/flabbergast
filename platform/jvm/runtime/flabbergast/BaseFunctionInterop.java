package flabbergast;

import java.util.function.Function;

/**
 * Base for injecting native functions into Flabbergast as function-like templates
 *
 * @param <R> The return type of the function-like template
 */
public abstract class BaseFunctionInterop<R> extends AssistedFuture {
  private final Function<R, Any> packer;
  protected final Frame self;

  public BaseFunctionInterop(
      Function<R, Any> packer,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(taskMaster, sourceReference, context);
    this.self = self;
    this.packer = packer;
  }

  /**
   * Compute the value to be returned to the calling Flabbergast code
   *
   * @throws Exception Any exception thrown will be caught and the message will be presented in the
   *     Flabbergast stack trace.
   */
  protected abstract R computeResult() throws Exception;

  @Override
  protected final void resolve() {
    complete(correctOutput(this::computeResult, packer));
  }
}
