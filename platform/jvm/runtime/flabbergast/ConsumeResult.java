package flabbergast;

/** Receive a boxed value as the result value of a {@link Future}. */
public interface ConsumeResult {
  void consume(Any result);
}
