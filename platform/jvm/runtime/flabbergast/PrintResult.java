package flabbergast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Print a result to either the console or a file, if provided. */
public abstract class PrintResult extends AcceptOrFail {
  public static class ToFile extends PrintResult {

    private final String outputFilename;

    public ToFile(String outputFilename) {
      super();
      this.outputFilename = outputFilename;
    }

    @Override
    public void accept(byte[] value) {
      success = writeToFile(value);
    }

    private boolean writeToFile(byte[] data) {
      try (FileOutputStream out = new FileOutputStream(outputFilename)) {
        out.write(data);
        return true;
      } catch (final IOException e) {
        System.err.println(e.getMessage());
        e.printStackTrace(System.err);
        return false;
      }
    }

    @Override
    protected boolean writeToFile(String data) {
      return writeToFile(data.getBytes(StandardCharsets.UTF_8));
    }
  }

  public static class ToStandardOut extends PrintResult {

    @Override
    protected boolean writeToFile(String data) {
      System.out.println(data);
      return true;
    }
  }

  private Set<Frame> frames = new HashSet<>();
  protected boolean success;

  @Override
  public void accept(boolean value) {
    success = writeToFile(value ? "True" : "False");
  }

  @Override
  public final void accept(double value) {
    success = writeToFile(Double.toString(value));
  }

  @Override
  public final void accept(Frame frame) {
    if (frames.contains(frame)) {
      System.err.println("Frame's “value” attribute contains a cycle. Giving up.");
      return;
    }
    frames.add(frame);
    if (!frame.get("value", this)) {
      System.err.println("Frame has no “value” attribute. Giving up.");
    }
  }

  @Override
  public final void accept(long value) {
    success = writeToFile(Long.toString(value));
  }

  @Override
  public final void accept(Stringish value) {
    success = writeToFile(value.toString());
  }

  @Override
  protected final void fail(String type) {
    System.err.println(String.format("Refusing to print result of type %s.", type));
  }
  /** Whether a valid type was received and printed. */
  public boolean getSuccess() {
    return success;
  }

  protected abstract boolean writeToFile(String data);
}
