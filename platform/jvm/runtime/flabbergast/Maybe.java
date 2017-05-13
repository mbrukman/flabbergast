package flabbergast;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A monad that stores nothing, a value, or an error
 *
 * @param <T> the type of value that is stored
 */
public abstract class Maybe<T> {
  /** Identical to {@link Consumer}, but can throw an exception */
  public interface WhineyConsumer<T> {
    void accept(T arg) throws Exception;
  }

  /** Identical to {@link Function}, but can throw an exception */
  public interface WhineyFunction<T, R> {
    R apply(T arg) throws Exception;
  }

  /** Identical to {@link Predicate}, but can throw an exception */
  public interface WhineyPredicate<T> {
    boolean test(T arg) throws Exception;
  }

  /** Identical to {@link Supplier}, but can throw an exception */
  public interface WhineySupplier<T> {
    T get() throws Exception;
  }

  public static <T> T callWhiney(WhineySupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a Maybe monad with no value
   *
   * <p>Most operations on an empty Maybe monad will yield an empty result.
   */
  public static <T> Maybe<T> empty() {
    return new Maybe<T>() {

      @Override
      public Maybe<T> combine(Maybe<T> other) {
        return other;
      }

      @Override
      public Maybe<T> filter(WhineyPredicate<T> predicate) {
        return this;
      }

      @Override
      public Maybe<T> filter(WhineyPredicate<T> predicate, String error) {
        return this;
      }

      @Override
      public <R> Maybe<R> flatMap(WhineyFunction<T, Maybe<R>> func) {
        return empty();
      }

      @Override
      public void forEach(Consumer<T> consumer) {}

      @Override
      public T get() {
        return null;
      }

      @Override
      public <R> Maybe<R> map(WhineyFunction<T, R> func) {
        return empty();
      }

      @Override
      public <R> Maybe<R> optionalMap(WhineyFunction<T, Optional<R>> func) {
        return empty();
      }

      @Override
      public <R> Maybe<R> optionalMap(WhineyFunction<T, Optional<R>> func, String errorMessage) {
        return empty();
      }

      @Override
      public T orElse(T other) {
        return other;
      }

      @Override
      public T orElseGet(Supplier<? extends T> other) {
        return other.get();
      }

      @Override
      public T orElseThrow(String error) {
        throw new IllegalStateException(error);
      }

      @Override
      public <R> R orHandle(
          Function<T, ? extends R> goodCase,
          String emptyMessage,
          Function<String, ? extends R> errorCase) {
        return errorCase.apply(emptyMessage);
      }

      @Override
      public Maybe<T> peek(WhineyConsumer<T> consumer) {
        return this;
      }

      @Override
      public Maybe<T> reduce(Supplier<Maybe<T>> supplier) {
        return supplier.get();
      }

      @Override
      public Stream<T> stream() {
        return Stream.empty();
      }
    };
  }

  /** Create a monad containing an error */
  public static <T> Maybe<T> error(String message) {
    return new Maybe<T>() {

      @Override
      public Maybe<T> combine(Maybe<T> other) {
        return this;
      }

      @Override
      public Maybe<T> filter(WhineyPredicate<T> predicate) {
        return this;
      }

      @Override
      public Maybe<T> filter(WhineyPredicate<T> predicate, String error) {
        return this;
      }

      @Override
      public <R> Maybe<R> flatMap(WhineyFunction<T, Maybe<R>> func) {
        return error(message);
      }

      @Override
      public void forEach(Consumer<T> consumer) {
        throw new IllegalStateException(message);
      }

      @Override
      public T get() {
        throw new IllegalStateException(message);
      }

      @Override
      public <R> Maybe<R> map(WhineyFunction<T, R> func) {
        return error(message);
      }

      @Override
      public <R> Maybe<R> optionalMap(WhineyFunction<T, Optional<R>> func) {
        return error(message);
      }

      @Override
      public <R> Maybe<R> optionalMap(WhineyFunction<T, Optional<R>> func, String errorMessage) {
        return error(message);
      }

      @Override
      public T orElse(T other) {
        throw new IllegalStateException(message);
      }

      @Override
      public T orElseGet(Supplier<? extends T> other) {
        throw new IllegalStateException(message);
      }

      @Override
      public T orElseThrow(String error) {
        throw new IllegalStateException(message);
      }

      @Override
      public <R> R orHandle(
          Function<T, ? extends R> goodCase,
          String emptyMessage,
          Function<String, ? extends R> errorCase) {
        return errorCase.apply(message);
      }

      @Override
      public Maybe<T> peek(WhineyConsumer<T> consumer) {
        return this;
      }

      @Override
      public Maybe<T> reduce(Supplier<Maybe<T>> supplier) {
        return this;
      }

      @Override
      public Stream<T> stream() {
        throw new IllegalStateException(message);
      }
    };
  }

  public static <T> WhineyConsumer<T> makeWhiney(Consumer<T> consumer) {
    return consumer::accept;
  }

  public static <T, R> WhineyFunction<T, R> makeWhiney(Function<T, R> func) {
    return func::apply;
  }

  public static <T> WhineyPredicate<T> makeWhiney(Predicate<T> pred) {
    return pred::test;
  }

  /**
   * Create a monad containing a value
   *
   * @param arg the value; if null, treated as if an empty monad was created
   */
  public static <T> Maybe<T> of(T arg) {
    if (arg == null) {
      return empty();
    }
    return new Maybe<T>() {

      @Override
      public Maybe<T> combine(Maybe<T> other) {
        return this;
      }

      @Override
      public Maybe<T> filter(WhineyPredicate<T> predicate) {
        try {
          return predicate.test(arg) ? this : empty();
        } catch (final Exception e) {
          return error(e.getMessage());
        }
      }

      @Override
      public Maybe<T> filter(WhineyPredicate<T> predicate, String errorMessage) {
        try {
          return predicate.test(arg) ? this : error(errorMessage);
        } catch (final Exception e) {
          return error(e.getMessage());
        }
      }

      @Override
      public <R> Maybe<R> flatMap(WhineyFunction<T, Maybe<R>> func) {
        try {
          return func.apply(arg);
        } catch (final Exception e) {
          return error(e.getMessage());
        }
      }

      @Override
      public void forEach(Consumer<T> consumer) {
        consumer.accept(arg);
      }

      @Override
      public T get() {
        return arg;
      }

      @Override
      public <R> Maybe<R> map(WhineyFunction<T, R> func) {
        try {
          return of(func.apply(arg));
        } catch (final Exception e) {
          return error(e.getMessage());
        }
      }

      @Override
      public <R> Maybe<R> optionalMap(WhineyFunction<T, Optional<R>> func) {
        try {
          return of(func.apply(arg).orElse(null));
        } catch (final Exception e) {
          return error(e.getMessage());
        }
      }

      @Override
      public <R> Maybe<R> optionalMap(WhineyFunction<T, Optional<R>> func, String errorMessage) {
        try {
          R result = func.apply(arg).orElse(null);
          return result == null ? error(errorMessage) : of(result);
        } catch (final Exception e) {
          return error(e.getMessage());
        }
      }

      @Override
      public T orElse(T other) {
        return arg;
      }

      @Override
      public T orElseGet(Supplier<? extends T> other) {
        return arg;
      }

      @Override
      public T orElseThrow(String error) {
        return arg;
      }

      @Override
      public <R> R orHandle(
          Function<T, ? extends R> goodCase,
          String emptyMessage,
          Function<String, ? extends R> errorCase) {
        return goodCase.apply(arg);
      }

      @Override
      public Maybe<T> peek(WhineyConsumer<T> consumer) {
        try {
          consumer.accept(arg);
        } catch (Exception e) {
          return error(e.getMessage());
        }
        return this;
      }

      @Override
      public Maybe<T> reduce(Supplier<Maybe<T>> supplier) {
        return this;
      }

      @Override
      public Stream<T> stream() {
        return Stream.of(arg);
      }
    };
  }

  public static <T> Maybe<T> ofOptional(Optional<T> optional) {
    return optional.map(Maybe::of).orElseGet(Maybe::empty);
  }

  /**
   * Given a stream of possible monads, return the first monad that contains either an error or a
   * value
   */
  public static <T> Maybe<T> reduce(Maybe<T> initial, Stream<Supplier<Maybe<T>>> stream) {
    return stream.reduce(initial, Maybe::reduce, Maybe::combine);
  }

  /**
   * Given a stream of possible monads, return the first monad that contains either an error or a
   * value
   */
  public static <T> Maybe<T> reduce(Stream<Supplier<Maybe<T>>> stream) {
    return reduce(Maybe.empty(), stream);
  }

  private Maybe() {}

  /** Combine two monads: if the receiver is non-empty, choose it, otherwise, choose the argument */
  public abstract Maybe<T> combine(Maybe<T> other);

  /**
   * Filter the contents of the monad
   *
   * <p>If the monad has a value and the predicate fails, an empty monad is returned. Otherwise, the
   * original empty/error monad is returned.
   */
  public abstract Maybe<T> filter(WhineyPredicate<T> predicate);

  /**
   * Filter the contents of the monad
   *
   * <p>If the monad has a value and the predicate fails, an error monad is returned. Otherwise, the
   * original empty/error monad is returned.
   */
  public abstract Maybe<T> filter(WhineyPredicate<T> predicate, String error);

  /** Apply the transformation to the monad */
  public abstract <R> Maybe<R> flatMap(WhineyFunction<T, Maybe<R>> func);

  /**
   * Transform the monad to a stream.
   *
   * @param func a function that transforms the value in the monad into a stream
   * @return the stream provided by the transformation if the monad is non-empty, an empty stream if
   *     the monad is empty, or an {@link IllegalStateException} if the monad contains an error
   */
  public final <R> Stream<R> flatStream(WhineyFunction<T, Stream<R>> func) {
    return map(func).orElse(Stream.empty());
  }

  /**
   * Consume the value in the monad if there is one.
   *
   * <p>If the monad contains an error, an {@link IllegalStateException} is thrown.
   */
  public abstract void forEach(Consumer<T> consumer);

  /**
   * Retrieve the value in the monad.
   *
   * <p>If the monad is empty, null is returned. If the monad contains an error, an {@link
   * IllegalStateException} is thrown.
   */
  public abstract T get();

  /**
   * Transform the value in the monad.
   *
   * <p>If the monad is empty or contains an error, it is unaffected.
   */
  public abstract <R> Maybe<R> map(WhineyFunction<T, R> func);

  /**
   * Apply a transformation to the monad which returns an optional result
   *
   * <p>The empty optional is transformed to an empty monad and a non-empty optional is transformed
   * to a non-empty monad.
   */
  public abstract <R> Maybe<R> optionalMap(WhineyFunction<T, Optional<R>> func);

  public abstract <R> Maybe<R> optionalMap(
      WhineyFunction<T, Optional<R>> func, String errorMessage);

  /**
   * Get the value in the monad, or a supplied value if empty.
   *
   * <p>If the monad contains an error, an {@link IllegalStateException} is thrown.
   *
   * @param other the alternate value to use
   * @return
   */
  public abstract T orElse(T other);

  /**
   * Get the value in the monad, or a supplied value if empty.
   *
   * <p>If the monad contains an error, an {@link IllegalStateException} is thrown.
   *
   * @param other a generator of the alternate value to use
   * @return
   */
  public abstract T orElseGet(Supplier<? extends T> other);

  /**
   * Get the value in the monad, or throw if empty.
   *
   * <p>If the monad contains an error, an {@link IllegalStateException} is thrown.
   *
   * @param error the error message to throw if the monad is empty
   */
  public abstract T orElseThrow(String error);

  /**
   * Get the result from the monad in any state.
   *
   * @param goodCase transform the value in the monad to the output type required. Typically {@link
   *     Function#identity()}
   * @param emptyMessage The error message to give when the monad is empty
   * @param errorCase transform an error message (either stored in the monad or provided by the
   *     previous parameter) in a value of the target type
   * @return
   */
  public abstract <R> R orHandle(
      Function<T, ? extends R> goodCase,
      String emptyMessage,
      Function<String, ? extends R> errorCase);

  /**
   * View the current value in the monad if there is one
   *
   * <p>If the consumer throws an error, the monad is replaced by the error. If the monad is empty
   * or an error, the consumer is not called.
   */
  public abstract Maybe<T> peek(WhineyConsumer<T> consumer);

  /** Replace an empty monad with the value generated. */
  public abstract Maybe<T> reduce(Supplier<Maybe<T>> supplier);

  /**
   * Convert the monad to a stream.
   *
   * <p>If the monad contains an error, an {@link IllegalStateException} is thrown.
   *
   * @return an empty stream if the monad is empty, or a stream of one item if th monad contains a
   *     value
   */
  public abstract Stream<T> stream();
}
