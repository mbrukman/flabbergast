package flabbergast;

import java.util.function.Function;

class MapFunctionInterop<T, R> extends BaseMapFunctionInterop<T, R> {
  private final Func<T, R> func;

  MapFunctionInterop(
      Function<R, Any> packer,
      Matcher<T> matcher,
      Func<T, R> func,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(packer, matcher, taskMaster, sourceReference, context, self);
    this.func = func;
  }

  @Override
  protected R computeResult(T input) throws Exception {
    return func.invoke(input);
  }
}
