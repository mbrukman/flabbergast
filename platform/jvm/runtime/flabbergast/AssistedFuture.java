package flabbergast;

import flabbergast.MarshalledFrame.Transform;
import java.text.DateFormatSymbols;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class AssistedFuture extends Future {
  public abstract static class MatchAcceptor<T> extends AcceptOrFail {
    protected final Consumer<T> consumer;
    protected final BiConsumer<String, String> error;
    private final String expected;
    private final boolean nullable;

    public MatchAcceptor(
        String expected, boolean nullable, Consumer<T> consumer, BiConsumer<String, String> error) {
      this.expected = expected;
      this.nullable = nullable;
      this.consumer = consumer;
      this.error = error;
    }

    @Override
    public final void accept() {
      if (nullable) {
        consumer.accept(null);
      } else {
        fail("Null");
      }
    }

    @Override
    protected final void fail(String type) {
      error.accept(type, expected + (nullable ? " or Null" : ""));
    }
  }

  public interface Matcher<T> {
    AcceptAny prepare(Consumer<T> consumer, BiConsumer<String, String> error);
  }

  static final Frame[] DAYS =
      makeFrames(
          new String[] {
            "sunday", "monday", "tuesday", "wednesday", "thrusday", "friday", "saturday"
          },
          DateFormatSymbols.getInstance().getShortWeekdays(),
          DateFormatSymbols.getInstance().getWeekdays());

  static final Frame[] MONTHS =
      makeFrames(
          new String[] {
            "january",
            "februrary",
            "march",
            "april",
            "may",
            "june",
            "july",
            "august",
            "september",
            "october",
            "november",
            "december"
          },
          DateFormatSymbols.getInstance().getShortMonths(),
          DateFormatSymbols.getInstance().getMonths());

  public static Matcher<byte[]> asBin(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<byte[]>("Bin", nullable, consumer, error) {
          @Override
          public void accept(byte[] value) {
            consumer.accept(value);
          }
        };
  }

  public static Matcher<Boolean> asBool(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<Boolean>("Bool", nullable, consumer, error) {
          @Override
          public void accept(boolean value) {
            consumer.accept(value);
          }
        };
  }

  public static Matcher<Integer> asCodepoint() {
    return (consumer, error) ->
        new MatchAcceptor<Integer>("Str (1 character)", false, consumer, error) {
          @Override
          public void accept(Stringish value) {
            if (value.getLength() == 0) {
              fail("empty Str");
            } else if (value.getLength() > 1) {
              fail(String.format("Str (%d characters)", value.getLength()));
            } else {
              consumer.accept(value.toString().codePointAt(0));
            }
          }
        };
  }

  public static Matcher<ZonedDateTime> asDateTime(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<ZonedDateTime>(
            "Float or Int or Frame with “epoch”", nullable, consumer, error) {
          @Override
          public void accept(double value) {
            emit(value);
          }

          @Override
          public void accept(Frame frame) {
            if (frame instanceof MarshalledFrame) {
              Object backing = ((MarshalledFrame) frame).getBacking();
              if (backing instanceof ZonedDateTime) {
                consumer.accept((ZonedDateTime) backing);
                return;
              }
            }
            if (!frame.get(
                "epoch",
                new AcceptOrFail() {

                  @Override
                  public void accept(double value) {
                    emit(value);
                  }

                  @Override
                  public void accept(long value) {
                    emit(Instant.ofEpochSecond(value));
                  }

                  @Override
                  protected void fail(String type) {
                    error.accept(
                        "Frame with attribute “epoch” of type Float or Int",
                        String.format("Frame with attribute “epoch” of type %s", type));
                  }
                })) {
              fail("Frame");
            }
          }

          @Override
          public void accept(long value) {
            emit(Instant.ofEpochSecond(value));
          }

          private void emit(double value) {
            emit(Instant.ofEpochSecond((long) value, (long) (value - (long) value) * 1_000_000));
          }

          private void emit(Instant value) {
            consumer.accept(ZonedDateTime.ofInstant(value, ZoneId.of("Z")));
          }
        };
  }

  public static Matcher<Double> asFloat(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<Double>("Int or Float", nullable, consumer, error) {
          @Override
          public void accept(double value) {
            consumer.accept(value);
          }

          @Override
          public void accept(long value) {
            consumer.accept((double) value);
          }
        };
  }

  public static Matcher<Frame> asFrame(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<Frame>("Frame", nullable, consumer, error) {
          @Override
          public void accept(Frame value) {
            consumer.accept(value);
          }
        };
  }

  public static final Matcher<Long> asInt(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<Long>("Int", nullable, consumer, error) {
          @Override
          public void accept(long value) {
            consumer.accept(value);
          }
        };
  }

  public static <T> Matcher<T> asMarshalled(
      Class<T> clazz, boolean nullable, String specialLocation) {
    return (consumer, error) ->
        new MatchAcceptor<T>("Frame from " + specialLocation, nullable, consumer, error) {
          @Override
          public void accept(Frame value) {
            if (value instanceof MarshalledFrame) {
              final Object backing = ((MarshalledFrame) value).getBacking();
              if (clazz.isInstance(backing)) {
                consumer.accept(clazz.cast(backing));
                return;
              }
            }
            fail("Frame");
          }
        };
  }

  public static Matcher<Stringish> asStr(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<Stringish>("Bool or Float or Int or Str", nullable, consumer, error) {
          @Override
          public void accept(boolean value) {
            consumer.accept(Stringish.from(value));
          }

          @Override
          public void accept(double value) {
            consumer.accept(Stringish.from(Double.toString(value)));
          }

          @Override
          public void accept(long value) {
            consumer.accept(Stringish.from(Long.toString(value)));
          }

          @Override
          public void accept(Stringish value) {
            consumer.accept(value);
          }
        };
  }

  public static Matcher<String> asString(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<String>("Bool or Float or Int or Str", nullable, consumer, error) {
          @Override
          public void accept(boolean value) {
            consumer.accept(value ? "True" : "False");
          }

          @Override
          public void accept(double value) {
            consumer.accept(Double.toString(value));
          }

          @Override
          public void accept(long value) {
            consumer.accept(Long.toString(value));
          }

          @Override
          public void accept(Stringish value) {
            consumer.accept(value.toString());
          }
        };
  }

  public static Matcher<String> asSymbol(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<String>("Int or Str (valid identifier)", nullable, consumer, error) {
          @Override
          public void accept(long value) {
            consumer.accept(SupportFunctions.ordinalNameStr(value));
          }

          @Override
          public void accept(Stringish value) {
            if (TaskMaster.verifySymbol(value)) {
              consumer.accept(value.toString());
            } else {
              fail("Str");
            }
          }
        };
  }

  public static Matcher<Template> asTemplate(boolean nullable) {
    return (consumer, error) ->
        new MatchAcceptor<Template>("Template", nullable, consumer, error) {
          @Override
          public void accept(Template value) {
            consumer.accept(value);
          }
        };
  }

  public static Stream<Transform<ZonedDateTime>> getTimeTransforms() {
    return Stream.of(
        MarshalledFrame.extractFrame("day_of_week", d -> DAYS[d.getDayOfWeek().getValue() % 7]),
        MarshalledFrame.extractInt("from_midnight", d -> (long) d.toLocalTime().toSecondOfDay()),
        MarshalledFrame.extractInt("milliseconds", d -> (long) d.get(ChronoField.MILLI_OF_SECOND)),
        MarshalledFrame.extractInt("second", d -> (long) d.getSecond()),
        MarshalledFrame.extractInt("minute", d -> (long) d.getMinute()),
        MarshalledFrame.extractInt("hour", d -> (long) d.getHour()),
        MarshalledFrame.extractInt("day", d -> (long) d.getDayOfMonth()),
        MarshalledFrame.extractFrame("month", d -> MONTHS[d.getMonthValue() - 1]),
        MarshalledFrame.extractInt("year", d -> (long) d.getYear()),
        MarshalledFrame.extractInt("week", d -> (long) d.get(WeekFields.ISO.weekOfWeekBasedYear())),
        MarshalledFrame.extractInt("day_of_year", d -> (long) d.getDayOfYear()),
        MarshalledFrame.extractFloat("epoch", d -> d.toEpochSecond() + d.getNano() / 1_000_000.0),
        MarshalledFrame.extractBool("is_utc", d -> d.getOffset().equals(ZoneOffset.UTC)),
        MarshalledFrame.extractBool("is_leap_year", d -> Year.isLeap(d.getYear())));
  }

  private static Frame[] makeFrames(String[] attrs, String[] shortNames, String[] longNames) {
    final Frame[] result = new Frame[attrs.length];
    for (int i = 0; i < attrs.length; i++) {
      final ValueBuilder builder = new ValueBuilder();
      builder.set("short_name", Any.of(shortNames[i]));
      builder.set("long_name", Any.of(longNames[i]));
      builder.set("ordinal", Any.of(i + 1));
      result[i] = Frame.create(attrs[i], "the big bang", builder);
    }
    return result;
  }

  protected final Context context;
  private boolean first = true;
  private final AtomicInteger interlock = new AtomicInteger(1);
  protected final SourceReference sourceReference;

  public AssistedFuture(TaskMaster taskMaster, SourceReference sourceReference, Context context) {
    super(taskMaster);
    this.sourceReference = sourceReference;
    this.context = context;
  }

  protected final <T> Any correctOutput(Callable<T> generator, Function<T, Any> packer) {
    try {
      return packer.apply(generator.call());
    } catch (final Exception e) {
      taskMaster.reportOtherError(sourceReference, e.getMessage());
      return null;
    }
  }

  protected final void find(Consumer<Any> writer, String... names) {
    interlock.incrementAndGet();
    new ContextualLookup(taskMaster, sourceReference, context, names)
        .listen(
            result -> {
              writer.accept(result);
              if (interlock.decrementAndGet() == 0) {
                slot();
              }
            });
  }

  protected final <T> void find(Matcher<T> matcher, Consumer<T> writer, String... names) {
    interlock.incrementAndGet();
    new ContextualLookup(taskMaster, sourceReference, context, names)
        .listen(
            matcher.prepare(
                result -> {
                  writer.accept(result);
                  if (interlock.decrementAndGet() == 0) {
                    slot();
                  }
                },
                (actual, expected) ->
                    taskMaster.reportOtherError(
                        sourceReference,
                        String.format(
                            "Expected %s for “%s”, but got %s.",
                            expected, String.join(".", names), actual))));
  }

  protected final <T> void findAll(
      Matcher<T> matcher, Consumer<Map<String, T>> writer, String... names) {
    interlock.incrementAndGet();
    final Map<String, T> results = new TreeMap<>();
    new ContextualLookup(taskMaster, sourceReference, context, names)
        .listen(
            asFrame(false)
                .prepare(
                    frame -> {
                      final AtomicInteger listInterlock = new AtomicInteger(frame.count());
                      frame
                          .stream()
                          .forEach(
                              name ->
                                  frame.get(
                                      name,
                                      matcher.prepare(
                                          result -> {
                                            results.put(name, result);
                                            if (listInterlock.decrementAndGet() == 0) {
                                              writer.accept(results);
                                              if (interlock.decrementAndGet() == 0) {
                                                slot();
                                              }
                                            }
                                          },
                                          (actual, expected) ->
                                              taskMaster.reportOtherError(
                                                  sourceReference,
                                                  String.format(
                                                      "Expected %s for item “%s” in frame “%s”, but got %s.",
                                                      expected,
                                                      name,
                                                      String.join(".", names),
                                                      actual)))));
                    },
                    (actual, expected) ->
                        taskMaster.reportOtherError(
                            sourceReference,
                            String.format(
                                "Expected %s for “%s”, but got %s.",
                                expected, String.join(".", names), actual))));
  }

  protected abstract void resolve();

  @Override
  protected final void run() {
    if (first) {
      setup();
      first = false;
      if (interlock.decrementAndGet() > 0) {
        return;
      }
    }
    resolve();
  }

  protected abstract void setup();
}
