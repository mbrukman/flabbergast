package flabbergast;

/** Unbox a boxed value ({@link Any}) into the matching native type for the Flabbergast type. */
public interface AcceptAny {
  /** Receive a “Null” value. */
  void accept();

  /** Receive a “Bool” value. */
  void accept(boolean value);
  /** Receive a “Bin” value. */
  void accept(byte[] value);

  /** Receive a “Float” value. */
  void accept(double value);

  /** Receive a “Frame” value. */
  void accept(Frame value);

  /** Receive an “Int” value. */
  void accept(long value);

  /** Receive a “LookupHandler” value. */
  void accept(LookupHandler value);

  /** Receive a “Str” value. */
  void accept(Stringish value);

  /** Receive a “Template” value. */
  void accept(Template value);

  default ConsumeResult toConsumer() {
    return x -> x.accept(this);
  }
}
