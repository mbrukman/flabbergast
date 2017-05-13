package flabbergast;

import java.util.Arrays;
import java.util.stream.Stream;

/** The collection of frames in which lookup should be performed. */
public class Context {
  public static final Context EMPTY = new Context(Stream.empty());
  private final Frame[] frames;

  private Context(Stream<Frame> frames) {
    this.frames = frames.sequential().distinct().toArray(Frame[]::new);
  }

  /**
   * Conjoin two contexts, placing all the frames of the provided context after all the frames in
   * the original context.
   */
  public Context append(Context tail) {
    return new Context(Stream.concat(stream(), tail.stream()));
  }

  public Context prepend(Frame head) {
    if (head == null) {
      throw new IllegalArgumentException("Cannot prepend a null frame to a context.");
    }
    return new Context(Stream.concat(Stream.of(head), stream()));
  }

  public Stream<Frame> stream() {
    return Arrays.stream(frames);
  }
}
