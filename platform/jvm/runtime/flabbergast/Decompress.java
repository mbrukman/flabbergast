package flabbergast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;

class Decompress extends BaseMapFunctionInterop<byte[], byte[]> {
  Decompress(TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, asBin(false), taskMaster, sourceReference, context, self);
  }

  @Override
  protected byte[] computeResult(byte[] input) throws Exception {
    try (GZIPInputStream gunzip = new GZIPInputStream(new ByteArrayInputStream(input));
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {

      int count;
      final byte[] buffer = new byte[1024];
      while ((count = gunzip.read(buffer, 0, buffer.length)) > 0) {
        output.write(buffer, 0, count);
      }
      return output.toByteArray();
    }
  }
}
