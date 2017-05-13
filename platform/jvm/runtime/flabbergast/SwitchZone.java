package flabbergast;

import java.time.ZoneId;
import java.time.ZonedDateTime;

class SwitchZone extends BaseMapFunctionInterop<ZonedDateTime, Frame> {

  private ZoneId zone;

  SwitchZone(TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, asDateTime(false), taskMaster, sourceReference, context, self);
  }

  @Override
  protected Frame computeResult(ZonedDateTime input) {
    return MarshalledFrame.create(
        taskMaster,
        sourceReference,
        context,
        self,
        input.withZoneSameInstant(zone),
        AssistedFuture.getTimeTransforms());
  }

  @Override
  protected void setupExtra() {
    find(asBool(false), isUtc -> zone = isUtc ? ZoneId.of("Z") : ZoneId.systemDefault(), "is_utc");
  }
}
