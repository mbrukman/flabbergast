package flabbergast;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

class ModifyTime extends BaseMapFunctionInterop<ZonedDateTime, Frame> {
  private long days;
  private long hours;
  private long milliseconds;
  private long minutes;
  private long months;
  private long seconds;
  private long years;

  ModifyTime(TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, asDateTime(false), taskMaster, sourceReference, context, self);
  }

  @Override
  protected Frame computeResult(ZonedDateTime initial) {
    return MarshalledFrame.create(
        taskMaster,
        sourceReference,
        context,
        null,
        initial
            .plus((int) milliseconds, ChronoUnit.MILLIS)
            .plusSeconds((int) seconds)
            .plusMinutes((int) minutes)
            .plusHours((int) hours)
            .plusDays((int) days)
            .plusMonths((int) months)
            .plusYears((int) years),
        AssistedFuture.getTimeTransforms());
  }

  @Override
  protected void setupExtra() {
    find(asInt(false), x -> milliseconds = x, "milliseconds");
    find(asInt(false), x -> seconds = x, "seconds");
    find(asInt(false), x -> minutes = x, "minutes");
    find(asInt(false), x -> hours = x, "hours");
    find(asInt(false), x -> months = x, "months");
    find(asInt(false), x -> days = x, "days");
    find(asInt(false), x -> years = x, "years");
  }
}
