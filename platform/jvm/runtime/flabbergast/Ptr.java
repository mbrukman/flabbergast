package flabbergast;

import java.util.function.Supplier;

/** Holder of a value to create out parameters or mutable values in closures */
public class Ptr<T> {
  private T value;

  public Ptr() {
    this(null);
  }

  Ptr(T initial) {
    value = initial;
  }

  public T get() {
    return value;
  }

  public T orElse(T other) {
    return value == null ? other : value;
  }

  public T orElseGet(Supplier<? extends T> other) {
    return value == null ? other.get() : value;
  }

  public void set(T value) {
    this.value = value;
  }
}
