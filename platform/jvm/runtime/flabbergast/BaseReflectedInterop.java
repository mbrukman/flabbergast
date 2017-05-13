package flabbergast;

import flabbergast.MarshalledFrame.Transform;
import java.util.stream.Stream;
/**
 * Base for injecting native functions into Flabbergast as function-like templates where the results
 * are {@link MarshalledFrame}
 *
 * @param <R> The return type of the function-like template
 */
public abstract class BaseReflectedInterop<R> extends BaseFunctionInterop<Frame> {

  public BaseReflectedInterop(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, taskMaster, sourceReference, context, self);
  }

  /**
   * Compute the value to be wrapped in a {@link MarshalledFrame} and returned to the calling
   * Flabbergast code
   *
   * @throws Exception Any exception thrown will be caught and the message will be presented in the
   *     Flabbergast stack trace.
   */
  protected abstract R computeReflectedValue() throws Exception;

  @Override
  protected final Frame computeResult() throws Exception {
    return MarshalledFrame.create(
        taskMaster, sourceReference, context, self, computeReflectedValue(), getTransforms());
  }

  /** Provide all the transformations to create attributes in the output frame. */
  protected abstract Stream<Transform<R>> getTransforms();
}
