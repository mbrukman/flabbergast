package flabbergast;

class StringToCodepoints extends BaseMapFunctionInterop<String, Frame> {
  StringToCodepoints(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, asString(false), taskMaster, sourceReference, context, self);
  }

  @Override
  protected Frame computeResult(String input) {
    return Frame.create(
        taskMaster,
        sourceReference,
        context,
        self,
        ArrayValueBuilder.create(input.codePoints().asLongStream()));
  }
}
