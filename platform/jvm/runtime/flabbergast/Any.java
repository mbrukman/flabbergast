package flabbergast;

import java.util.function.Consumer;

/**
 * Box a real value into the “Any” type.
 *
 * <p>{@link Frame} and {@link Future} output is one of any possible Flabbergast type. This stores
 * the appropriate value for later unboxing.
 */
public abstract class Any implements Consumer<AcceptAny> {
  public interface StringConsumer {
    void consume(Stringish value);
  }

  /** Box a “Bool”. */
  public static Any of(boolean value) {
    return new Any() {
      @Override
      public void accept(AcceptAny acceptor) {
        acceptor.accept(value);
      }

      @Override
      public <T> T apply(ApplyAny<T> function) {
        return function.apply(value);
      }
    };
  }

  /**
   * Box a “Bool”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(Boolean value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /**
   * Box a “Bin”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(byte[] value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /**
   * Box a “Float”.
   *
   * @param value The value to store.
   */
  public static Any of(double value) {
    return new Any() {
      @Override
      public void accept(AcceptAny acceptor) {
        acceptor.accept(value);
      }

      @Override
      public <T> T apply(ApplyAny<T> function) {
        return function.apply(value);
      }
    };
  }

  /**
   * Box a “Float”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(Double value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /**
   * Box a “Frame”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(Frame value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /**
   * Box a “Int”.
   *
   * @param value The value to store.
   */
  public static Any of(long value) {
    return new Any() {
      @Override
      public void accept(AcceptAny acceptor) {
        acceptor.accept(value);
      }

      @Override
      public <T> T apply(ApplyAny<T> function) {
        return function.apply(value);
      }
    };
  }

  /**
   * Box a “Int”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(Long value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /**
   * Box a “LookupHandler”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(LookupHandler value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /**
   * Box a “Str”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(String value) {
    return value == null ? unit() : of(Stringish.from(value));
  }

  /**
   * Box a “Str”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(Stringish value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /**
   * Box a “Template”.
   *
   * @param value The value to store. If null, Flabbergast “Null” is substituted.
   */
  public static Any of(Template value) {
    return value == null
        ? unit()
        : new Any() {
          @Override
          public void accept(AcceptAny acceptor) {
            acceptor.accept(value);
          }

          @Override
          public <T> T apply(ApplyAny<T> function) {
            return function.apply(value);
          }
        };
  }

  /** Box a “Null”. */
  public static Any unit() {
    return new Any() {
      @Override
      public void accept(AcceptAny acceptor) {
        acceptor.accept();
      }

      @Override
      public <T> T apply(ApplyAny<T> function) {
        return function.apply();
      }
    };
  }

  public abstract <T> T apply(ApplyAny<T> function);

  /**
   * Create a thunk over this value that can be used in {@link Template} and {@link Fricassee}
   * operations.
   */
  public final Definition compute() {
    return (t, sr, cx, s) -> future();
  }

  /** Create a thunk over this value that can be used in {@link Future}-requiring places. */
  public final Future future() {
    return new Future(null) {
      {
        complete(Any.this);
      }

      @Override
      protected void run() {}
    };
  }

  public final void toStr(
      TaskMaster taskMaster, SourceReference sourceReference, StringConsumer consumer) {
    this.accept(
        new AcceptOrFail() {
          @Override
          public void accept(boolean value) {
            consumer.consume(Stringish.from(value));
          }

          @Override
          public void accept(double value) {
            consumer.consume(Stringish.from(Double.toString(value)));
          }

          @Override
          public void accept(long value) {
            consumer.consume(Stringish.from(Long.toString(value)));
          }

          @Override
          public void accept(Stringish value) {
            consumer.consume(value);
          }

          @Override
          protected final void fail(String type) {
            taskMaster.reportOtherError(
                sourceReference,
                String.format("Expected Bool or Float or Int or Str, but got %s.", type));
          }
        });
  }
}
