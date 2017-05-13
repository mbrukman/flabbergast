package flabbergast;

import flabbergast.Escape.Range;
import flabbergast.Escape.RangeAction;
import flabbergast.EscapeBuilder.Transformation;
import flabbergast.MarshalledFrame.Transform;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class EscapeRangeBuilder extends BaseReflectedInterop<Transformation> {

  private static final Matcher<RangeAction> RANGE_ACTION_MATCHER =
      (consumer, error) ->
          new MatchAcceptor<RangeAction>(
              "Frame from lib:utils str_transform range_tmpl mode or Bool or Float or Int or Str",
              false,
              consumer,
              error) {
            @Override
            public void accept(boolean value) {
              makeAction(value ? "True" : "False");
            }

            @Override
            public void accept(double value) {
              makeAction(Double.toString(value));
            }

            @Override
            public void accept(Frame value) {
              if (value instanceof MarshalledFrame) {
                final Object backing = ((MarshalledFrame) value).getBacking();
                if (backing instanceof RangeAction) {
                  consumer.accept((RangeAction) backing);
                  return;
                }
              }
              fail("Frame");
            }

            @Override
            public void accept(long value) {
              makeAction(Long.toString(value));
            }

            @Override
            public void accept(Stringish value) {
              makeAction(value.toString());
            }

            private void makeAction(String value) {
              consumer.accept(codepoint -> value);
            }
          };
  private List<RangeAction> actions;
  private int end;
  private int start;

  EscapeRangeBuilder(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(taskMaster, sourceReference, context, self);
  }

  @Override
  protected Transformation computeReflectedValue() {
    if (start > end) {
      throw new IllegalArgumentException("Transformation range has start before end.");
    }
    final Range range = new Range(start, end, actions);
    return builder -> builder.ranges.add(range);
  }

  @Override
  protected Stream<Transform<Transformation>> getTransforms() {
    return Stream.empty();
  }

  @Override
  protected void setup() {
    find(asCodepoint(), x -> start = x, "start");
    find(asCodepoint(), x -> end = x, "end");
    findAll(RANGE_ACTION_MATCHER, x -> actions = new ArrayList<>(x.values()), "replacement");
  }
}
