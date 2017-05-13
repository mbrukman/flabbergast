package flabbergast;

import java.time.ZoneId;
import java.time.ZonedDateTime;

class CreateTime extends BaseFunctionInterop<Frame> {
  private static final Matcher<Integer> MONTH_MATCHER =
      (consumer, error) ->
          new MatchAcceptor<Integer>("Int or Frame from “months”", false, consumer, error) {
            @Override
            public void accept(Frame value) {
              if (!value.get(
                  "ordinal",
                  new AcceptOrFail() {
                    @Override
                    public void accept(long value) {
                      consumer.accept((int) value);
                    }

                    @Override
                    protected final void fail(String type) {
                      error.accept(
                          "Frame with attribute “ordinal” of type Int",
                          String.format("Frame with attribute “ordinal” of type %s", type));
                    }
                  })) {
                fail("Frame");
              }
            }

            @Override
            public void accept(long value) {
              consumer.accept((int) value);
            }
          };
  private long day;
  private long hour;
  private long millisecond;
  private long minute;
  private long month;
  private long second;
  private long year;

  private ZoneId zone;

  CreateTime(TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, taskMaster, sourceReference, context, self);
  }

  @Override
  protected Frame computeResult() {
    return MarshalledFrame.create(
        taskMaster,
        sourceReference,
        context,
        null,
        ZonedDateTime.of(
            (int) year,
            (int) month,
            (int) day,
            (int) hour,
            (int) minute,
            (int) second,
            (int) millisecond * 1_000_000,
            zone),
        AssistedFuture.getTimeTransforms());
  }

  @Override
  protected void setup() {
    find(MONTH_MATCHER, x -> month = x, "month");
    find(asInt(false), x -> millisecond = x, "millisecond");
    find(asInt(false), x -> second = x, "second");
    find(asInt(false), x -> minute = x, "minute");
    find(asInt(false), x -> hour = x, "hour");
    find(asInt(false), x -> day = x, "day");
    find(asInt(false), x -> year = x, "year");
    find(asBool(false), x -> zone = x ? ZoneId.of("Z") : ZoneId.systemDefault(), "is_utc");
  }
}
