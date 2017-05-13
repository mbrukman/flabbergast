package flabbergast;

import java.util.function.Function;
import java.util.stream.Stream;

/** A Frame wrapper over a Java object. */
public class MarshalledFrame extends Frame {

  public static final class Transform<T> {
    private final Function<T, Any> extract;
    private final String name;

    Transform(String name, Function<T, Any> extract) {
      this.name = name;
      this.extract = extract;
    }

    Frame.Builder apply(T src) {
      return (sourceReference, context, self, attributes) ->
          attributes.put(name, extract.apply(src).future());
    }
  }

  public static <T> MarshalledFrame create(
      String id, String caller, T backing, Stream<Transform<T>> transforms) {
    return new MarshalledFrame(
        Stringish.from(id),
        new NativeSourceReference(caller),
        Context.EMPTY,
        null,
        backing,
        transforms.map(transform -> transform.apply(backing)));
  }

  public static <T> Definition create(T backing, Stream<Transform<T>> transforms) {
    return (taskMaster, sourceReference, context, self) ->
        Any.of(create(taskMaster, sourceReference, context, self, backing, transforms)).future();
  }

  public static <T> MarshalledFrame create(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame container,
      T backing,
      Stream<Transform<T>> transforms) {
    return new MarshalledFrame(
        SupportFunctions.ordinalName(taskMaster.nextId()),
        sourceReference,
        context,
        container,
        backing,
        transforms.map(transform -> transform.apply(backing)));
  }

  public static <T> Transform<T> extractBool(String name, Function<T, Boolean> extract) {
    return new Transform<>(name, extract.andThen(Any::of));
  }

  public static <T> Transform<T> extractFloat(String name, Function<T, Double> extract) {
    return new Transform<>(name, extract.andThen(Any::of));
  }

  public static <T> Transform<T> extractFrame(String name, Function<T, Frame> extract) {
    return new Transform<>(name, extract.andThen(Any::of));
  }

  public static <T> Transform<T> extractInt(String name, Function<T, Long> extract) {
    return new Transform<>(name, extract.andThen(Any::of));
  }

  public static <T> Transform<T> extractStr(String name, Function<T, String> extract) {
    return new Transform<>(name, extract.andThen(Any::of));
  }

  private final Object backing;

  private MarshalledFrame(
      Stringish id,
      SourceReference sourceReference,
      Context context,
      Frame container,
      Object backing,
      Stream<Builder> builders) {
    super(id, sourceReference, context, container, builders);
    this.backing = backing;
  }

  public Object getBacking() {
    return backing;
  }
}
