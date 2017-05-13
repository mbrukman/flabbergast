package flabbergast;

import java.util.function.Function;

class FunctionInterop2<T1, T2, R> extends BaseFunctionInterop<R> {
  private final Func2<T1, T2, R> func;
  private T1 input1;
  private T2 input2;
  private final Matcher<T1> matcher1;
  private final Matcher<T2> matcher2;
  private final String parameter1;
  private final String parameter2;

  FunctionInterop2(
      Function<R, Any> packer,
      Func2<T1, T2, R> func,
      Matcher<T1> matcher1,
      String parameter1,
      Matcher<T2> matcher2,
      String parameter2,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(packer, taskMaster, sourceReference, context, self);
    this.func = func;
    this.matcher1 = matcher1;
    this.parameter1 = parameter1;
    this.matcher2 = matcher2;
    this.parameter2 = parameter2;
  }

  @Override
  protected R computeResult() throws Exception {
    return func.invoke(input1, input2);
  }

  @Override
  protected void setup() {
    find(matcher1, x -> this.input1 = x, parameter1);
    find(matcher2, x -> this.input2 = x, parameter2);
  }
}
