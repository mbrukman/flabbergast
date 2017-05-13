package flabbergast;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Create a {@link Frame.Builder} that contains an automatically numbered list of values.
 *
 * <p>This can be used to build a frame that would contain the values provided as the result of an
 * anonymous “Select” or literal list.
 */
public final class ArrayValueBuilder implements Frame.Builder {

  /**
   * Convenience method to create a list of frames. Note the frames should be: {@link
   * MarshalledFrame}, frames built using only {@link Frame.Builder} (not {@link
   * Frame.RuntimeBuilder}), or Frames that are the result of a {@link Future}.
   */
  public static ArrayValueBuilder create(Frame... frames) {
    return new ArrayValueBuilder(Arrays.stream(frames).map(Any::of).collect(Collectors.toList()));
  }

  /** Convenience method to create a list of “Int”. */
  public static ArrayValueBuilder create(LongStream source) {
    return new ArrayValueBuilder(source.mapToObj(Any::of).collect(Collectors.toList()));
  }

  private final List<Any> values;

  public ArrayValueBuilder(List<Any> values) {
    super();
    this.values = values;
  }

  @Override
  public void prepare(
      SourceReference sourceReference,
      Context context,
      Frame self,
      Map<String, Future> attributes) {
    for (int it = 0; it < values.size(); it++) {
      attributes.put(SupportFunctions.ordinalNameStr(it + 1), values.get(it).future());
    }
  }
}
