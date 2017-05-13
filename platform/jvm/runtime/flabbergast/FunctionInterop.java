package flabbergast;

import java.util.function.Function;

public class FunctionInterop<T, R> extends BaseFunctionInterop<R> {
  private final Func<T, R> func;
  private T input;
  private final Matcher<T> matcher;
  private final String parameter;

  public FunctionInterop(
      Function<R, Any> packer,
      Func<T, R> func,
      Matcher<T> matcher,
      String parameter,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(packer, taskMaster, sourceReference, context, self);
    this.func = func;
    this.matcher = matcher;
    this.parameter = parameter;
  }

  @Override
  protected R computeResult() throws Exception {
    return func.invoke(input);
  }

  @Override
  protected void setup() {
    find(matcher, x -> this.input = x, parameter);
  }
}
