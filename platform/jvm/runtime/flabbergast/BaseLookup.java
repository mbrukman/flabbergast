package flabbergast;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.stream.Stream;

/**
 * Base for doing lookups
 *
 * <p>All lookups in Flabbergast work by creating a grid of frames where the value might reside and
 * all the names. {@link ContextualLookup} provides standard contextual lookup, but derivatives of
 * this class may provide other semantics.
 */
public abstract class BaseLookup extends Future {
  /**
   * A lookup proceedes in a series of steps, or “attempts” to get the value for a lookup
   *
   * <p>Other lookups should create attempts to indicate where in the grid they are processing and
   * the frame where the lookup is occuring.
   */
  protected abstract class Attempt implements ConsumeResult {
    private final int frame;
    private final int name;
    private final Frame sourceFrame;

    protected Attempt(int name, int frame, Frame sourceFrame) {
      this.name = name;
      this.frame = frame;
      this.sourceFrame = sourceFrame;
      knownAttempts.add(this);
    }

    protected final int getFrame() {
      return frame;
    }

    protected final int getName() {
      return name;
    }

    protected final Frame getSource() {
      return sourceFrame;
    }
  }

  protected interface LookupInvoke {
    Future invoke(
        TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names);
  }

  /**
   * Helper method to create a {@link Definition} so that the lookup of a name set can be performed
   * by a not-yet-instantiated {@link Template} or {@link Fricassee} operation used by native code.
   *
   * <p>For use of this method, see {@link ContextualLookup#create(String...)}
   *
   * @param names the names to be resolved
   * @param invoker a constructor reference to a derivative of this class
   */
  protected static Definition createHelper(String[] names, LookupInvoke invoker) {
    if (names.length == 0) {
      FailureFuture.create("Missing names in lookup.");
    }
    for (final String name : names) {
      if (!TaskMaster.verifySymbol(name)) {
        return FailureFuture.create(
            String.format("Invalid name “%s” in lookup for “%s”.", name, String.join(".", names)));
      }
    }

    return (taskMaster, sourceReference, context, self) ->
        invoker.invoke(taskMaster, sourceReference, context, names);
  }

  private final Frame[] frames;

  private final LinkedList<Attempt> knownAttempts = new LinkedList<>();

  /** The name components in the lookup expression. */
  private final String[] names;

  protected final SourceReference sourceReference;

  public BaseLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names) {
    super(taskMaster);
    this.sourceReference = sourceReference;
    this.names = names;
    frames = context.stream().toArray(Frame[]::new);
    taskMaster.getInflight().add(this);
  }

  /** Signal that the lookup is complete and provide the value. */
  protected final void emit(Any value) {
    taskMaster.getInflight().remove(this);
    complete(value);
  }

  /**
   * Signal that this lookup was a failure
   *
   * @param type if null, indicate the lookup finished without finding a candidate. If not null,
   *     this should be the unexpected type of an object encountered during lookup
   */
  protected final void fail(String type) {
    taskMaster.reportLookupError(BaseLookup.this, type);
  }

  /**
   * Get the frame that was inspected at this position in the grid.
   *
   * @return the frame or null if skipped/not started
   */
  public final Frame get(int name, int frame) {
    return knownAttempts
        .stream()
        .filter(attempt -> attempt.getFrame() == frame && attempt.getName() == name)
        .map(Attempt::getSource)
        .findFirst()
        .orElse(null);
  }

  protected final Frame getFrame(int frame) {
    return frames[frame];
  }

  public final int getFrameCount() {
    return frames.length;
  }

  public final Frame getLastFrame() {
    return knownAttempts.getLast().getSource();
  }

  public final String getLastName() {
    return names[knownAttempts.getLast().getName()];
  }

  public final String getName() {
    final StringBuilder sb = new StringBuilder();
    for (int n = 0; n < names.length; n++) {
      if (n > 0) {
        sb.append(".");
      }
      sb.append(names[n]);
    }
    return sb.toString();
  }

  public final String getName(int index) {
    return names[index];
  }

  public final int getNameCount() {
    return names.length;
  }

  public final SourceReference getSourceReference() {
    return sourceReference;
  }

  public Stream<String> names() {
    return Arrays.stream(names);
  }
}
