package flabbergast;

import java.util.function.Function;

class MapFunctionInterop2<T1, T2, R> extends BaseMapFunctionInterop<T1, R> {
  private final Func2<T1, T2, R> func;
  private final String parameter;
  private final Matcher<T2> parameterMatcher;
  private T2 reference;

  MapFunctionInterop2(
      Function<R, Any> packer,
      Matcher<T1> matcher,
      Func2<T1, T2, R> func,
      Matcher<T2> parameterMatcher,
      String parameter,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(packer, matcher, taskMaster, sourceReference, context, self);
    this.func = func;
    this.parameterMatcher = parameterMatcher;
    this.parameter = parameter;
  }

  @Override
  protected R computeResult(T1 input) throws Exception {
    return func.invoke(input, reference);
  }

  @Override
  protected void setupExtra() {
    find(parameterMatcher, x -> this.reference = x, parameter);
  }
}
