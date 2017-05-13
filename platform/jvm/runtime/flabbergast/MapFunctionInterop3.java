package flabbergast;

import java.util.function.Function;

class MapFunctionInterop3<T1, T2, T3, R> extends BaseMapFunctionInterop<T1, R> {
  private final Func3<T1, T2, T3, R> func;
  private final String parameter1;
  private final Matcher<T2> parameter1Matcher;
  private final String parameter2;
  private final Matcher<T3> parameter2Matcher;
  private T2 reference1;
  private T3 reference2;

  MapFunctionInterop3(
      Function<R, Any> packer,
      Matcher<T1> matcher,
      Func3<T1, T2, T3, R> func,
      Matcher<T2> parameter1Matcher,
      String parameter1,
      Matcher<T3> parameter2Matcher,
      String parameter2,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(packer, matcher, taskMaster, sourceReference, context, self);
    this.func = func;
    this.parameter1Matcher = parameter1Matcher;
    this.parameter2Matcher = parameter2Matcher;
    this.parameter1 = parameter1;
    this.parameter2 = parameter2;
  }

  @Override
  protected R computeResult(T1 input) throws Exception {
    return func.invoke(input, reference1, reference2);
  }

  @Override
  protected void setupExtra() {
    find(parameter1Matcher, x -> this.reference1 = x, parameter1);
    find(parameter2Matcher, x -> this.reference2 = x, parameter2);
  }
}
