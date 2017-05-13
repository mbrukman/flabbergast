package flabbergast;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPOutputStream;

class BinaryFunctions {

  static byte[] checksum(byte[] input, String algorithm) {
    try {
      final MessageDigest complete = MessageDigest.getInstance(algorithm);
      complete.update(input);
      return complete.digest();
    } catch (final NoSuchAlgorithmException e) {
      return new byte[0];
    }
  }

  static byte[] compress(byte[] input) {
    try (final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final GZIPOutputStream gzip = new GZIPOutputStream(output)) {
      gzip.write(input);
      return output.toByteArray();
    } catch (final Exception e) {
      return new byte[0];
    }
  }

  private BinaryFunctions() {}
}
