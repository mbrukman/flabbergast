package flabbergast;

/** Unbox a boxed value ({@link Any}) into the matching native type for the Flabbergast type. */
public interface ApplyAny<T> {
  /** Receive a “Null” value. */
  T apply();

  /** Receive a “Bool” value. */
  T apply(boolean value);
  /** Receive a “Bin” value. */
  T apply(byte[] value);

  /** Receive a “Float” value. */
  T apply(double value);

  /** Receive a “Frame” value. */
  T apply(Frame value);

  /** Receive an “Int” value. */
  T apply(long value);

  /** Receive a “LookupHandler” value. */
  T apply(LookupHandler value);

  /** Receive a “Str” value. */
  T apply(Stringish value);

  /** Receive a “Template” value. */
  T apply(Template value);
}
