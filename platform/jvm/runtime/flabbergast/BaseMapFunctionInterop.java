package flabbergast;

import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Base for injecting native functions into Flabbergast as function-like templates that map the
 * variadic “args” frame to a “value” frame.
 *
 * @param<T> The argument type expected in “args”
 * @param <R> The return type of the function-like template
 */
public abstract class BaseMapFunctionInterop<T, R> extends AssistedFuture {
  private Map<String, T> input;
  private final Matcher<T> matcher;

  private final Function<R, Any> packer;

  protected final Frame self;

  public BaseMapFunctionInterop(
      Function<R, Any> packer,
      Matcher<T> matcher,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(taskMaster, sourceReference, context);
    this.packer = packer;
    this.matcher = matcher;
    this.self = self;
  }

  /**
   * Compute the value to be returned to Flabbergast for one item in the “args” frame
   *
   * <p>The attribute name of the argument will be preserved in the output.
   *
   * @param input the value to be transformed
   * @throws Exception Any exception thrown will be caught and the message will be presented in the
   *     Flabbergast stack trace.
   */
  protected abstract R computeResult(T input) throws Exception;

  @Override
  protected final void resolve() {
    final ValueBuilder builder = new ValueBuilder();
    for (final Entry<String, T> entry : input.entrySet()) {
      final Any item = correctOutput(() -> computeResult(entry.getValue()), packer);
      if (item == null) {
        return;
      }
      builder.set(entry.getKey(), item);
    }
    complete(Any.of(Frame.create(taskMaster, sourceReference, context, self, builder)));
  }

  @Override
  protected final void setup() {
    findAll(matcher, x -> this.input = x, "args");
    setupExtra();
  }

  /** If additional parameters are required, override this function to define them */
  protected void setupExtra() {}
}
