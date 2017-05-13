package flabbergast;

import java.nio.ByteBuffer;
import java.util.UUID;

class GenerateUuid extends BaseFunctionInterop<byte[]> {

  GenerateUuid(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, taskMaster, sourceReference, context, self);
  }

  @Override
  protected byte[] computeResult() {
    UUID uuid = UUID.randomUUID();
    return ByteBuffer.allocate(16)
        .putLong(uuid.getMostSignificantBits())
        .putLong(uuid.getLeastSignificantBits())
        .array();
  }

  @Override
  protected void setup() {}
}
