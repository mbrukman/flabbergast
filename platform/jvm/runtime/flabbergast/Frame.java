package flabbergast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/** A Frame in the Flabbergast language. */
public class Frame {

  /**
   * A builder populates a frame with attributes on construction.
   *
   * <p>These values can be aware of the frame's context, but are precomputed; that is, they do not
   * need a {@link TaskMaster} to execute them.
   */
  public interface Builder extends RuntimeBuilder {
    @Override
    default Builder attach(TaskMaster taskMaster) {
      return this;
    }

    void prepare(
        SourceReference sourceReference,
        Context context,
        Frame self,
        Map<String, Future> attributes);
  }

  /**
   * A runtime builder populates a frame with attributes on construction.
   *
   * <p>These values can be activated in this context and their values scheduled to be computed by a
   * {@link TaskMaster}.
   */
  public interface RuntimeBuilder {
    Builder attach(TaskMaster taskMaster);
  }

  /**
   * Create a frame to be computed later when a {@link Template} is instantiated or in a {@link
   * Fricassee} operation.
   */
  public static Definition create(RuntimeBuilder... builders) {
    return (taskMaster, sourceReference, context, self) ->
        new Future(taskMaster) {
          @Override
          protected void run() {
            complete(Any.of(Frame.create(taskMaster, sourceReference, context, self, builders)));
          }
        };
  }

  /**
   * Construct a frame for fixed data generated by native code.
   *
   * @param id an identification string for this frame which must be unique and not overlap with
   *     automatically generated names
   * @param source the value to write in the stack trace (e.g., "<sql>")
   * @see SupportFunctions#ordinalNameStr(long)
   */
  public static Frame create(String id, String source, Builder... builders) {
    return new Frame(
        Stringish.from(id),
        new NativeSourceReference(source),
        Context.EMPTY,
        null,
        Arrays.stream(builders));
  }

  /** Construct a frame from the specified builders in the provided context. */
  public static Frame create(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame container,
      RuntimeBuilder... builders) {
    return create(taskMaster, sourceReference, context, container, Arrays.stream(builders));
  }

  /** Construct a frame from the specified builders in the provided context. */
  public static Frame create(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame container,
      Stream<RuntimeBuilder> builders) {

    Frame frame =
        new Frame(
            SupportFunctions.ordinalName(taskMaster.nextId()),
            sourceReference,
            context,
            container,
            builders.map(builder -> builder.attach(taskMaster)));
    frame.attributes.values().stream().forEach(future -> future.listen(x -> {}));
    return frame;
  }

  /**
   * Construct a frame containing a range of numbers for the “Through” operation.
   *
   * <p>If the end of the range is before the beginning, an empty list is produced.
   *
   * @param start the start of the range, inclusive
   * @param end the end of the range, inclusive
   */
  public static Frame through(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      long start,
      long end,
      Context context,
      Frame container) {
    return Frame.create(
        taskMaster,
        sourceReference,
        context,
        container,
        ArrayValueBuilder.create(LongStream.rangeClosed(start, end)));
  }

  private final TreeMap<String, Future> attributes = new TreeMap<>();
  private final Frame container;
  private final Context context;
  private final Stringish id;
  private final SourceReference sourceReference;

  protected Frame(
      Stringish id,
      SourceReference sourceReference,
      Context context,
      Frame container,
      Stream<Builder> builders) {
    this.id = id;
    this.sourceReference = sourceReference;
    this.context = context.prepend(this);
    this.container = container == null ? this : container;
    builders.forEach(this::build);
  }

  private void build(Builder builder) {
    builder.prepare(sourceReference, context, this, attributes);
  }

  /** The number of attributes in this frame */
  public final int count() {
    return attributes.size();
  }

  /** Get a value (future) in this frame. */
  public final Future get(String name) {
    return attributes.get(name);
  }

  /**
   * Get a value from this frame and unbox it.
   *
   * @param name the attribute name
   * @param consumer the consumer of the unpacked value; this maybe called asynchronously if the
   *     value has not yet been computed
   * @return true if the attribute exists; if false, the consumer will never be called
   */
  public final boolean get(String name, AcceptAny consumer) {
    return get(name, any -> any.accept(consumer));
  }

  /**
   * Get a boxed value from this frame.
   *
   * @param name the attribute name
   * @param consumer the consumer of the unpacked value; this maybe called asynchronously if the
   *     value has not yet been computed
   * @return true if the attribute exists; if false, the consumer will never be called
   */
  public final boolean get(String name, ConsumeResult consumer) {
    final Future future = get(name);
    if (future == null) {
      return false;
    }
    future.listen(consumer);
    return true;
  }

  /** The containing frame, or null for file-level frames. */
  public final Frame getContainer() {
    return container;
  }

  /** The lookup context when this frame was created and any of its ancestors. */
  public final Context getContext() {
    return context;
  }

  /** The “GenerateId” value for this frame. */
  public final Stringish getId() {
    return id;
  }

  /**
   * Get a boxed value from this frame or “Null” if the attribute is not found.
   *
   * @param name the attribute name
   * @param consumer the consumer of the unpacked value; this maybe called asynchronously if the
   *     value has not yet been computed
   */
  public final void getOrNull(String name, ConsumeResult consumer) {
    if (!get(name, consumer)) {
      consumer.consume(Any.unit());
    }
  }

  /** The stack trace when this frame was created. */
  public final SourceReference getSourceReference() {
    return sourceReference;
  }

  /**
   * Create a trace of the source reference that generated this frame
   *
   * @param prefix a string to prefix to every line
   */
  public final Stringish renderTrace(Stringish prefix) {
    final StringBuilder buffer = new StringBuilder();
    final HashSet<SourceReference> seen = new HashSet<>();
    sourceReference.write(buffer::append, prefix.toString(), seen);
    return Stringish.from(buffer.toString());
  }

  public final Stream<String> stream() {
    return attributes.keySet().stream();
  }
}
