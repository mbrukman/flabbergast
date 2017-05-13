package flabbergast;

import flabbergast.EscapeBuilder.Transformation;
import flabbergast.MarshalledFrame.Transform;
import java.util.stream.Stream;

class EscapeCharacterBuilder extends BaseReflectedInterop<Transformation> {
  private int character;
  private String replacement;

  EscapeCharacterBuilder(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(taskMaster, sourceReference, context, self);
  }

  @Override
  protected Transformation computeReflectedValue() {
    return builder -> builder.singleSubstitutions.put(character, replacement);
  }

  @Override
  protected Stream<Transform<Transformation>> getTransforms() {
    return Stream.empty();
  }

  @Override
  protected void setup() {
    find(asCodepoint(), x -> character = x, "char");
    find(asString(false), x -> replacement = x, "replacement");
  }
}
