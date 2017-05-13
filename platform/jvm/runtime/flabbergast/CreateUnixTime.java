package flabbergast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

class CreateUnixTime extends BaseFunctionInterop<Frame> {

  private long epoch;
  private ZoneId zone;

  CreateUnixTime(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, taskMaster, sourceReference, context, self);
  }

  @Override
  protected Frame computeResult() {
    return MarshalledFrame.create(
        taskMaster,
        sourceReference,
        context,
        self,
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), zone),
        AssistedFuture.getTimeTransforms());
  }

  @Override
  protected void setup() {
    find(asInt(false), x -> epoch = x, "epoch");
    find(asBool(false), isUtc -> zone = isUtc ? ZoneId.of("Z") : ZoneId.systemDefault(), "is_utc");
  }
}
