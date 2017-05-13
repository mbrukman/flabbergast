package flabbergast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Escape extends BaseMapFunctionInterop<String, String> {

  private enum CharFormat {
    DECIMAL {
      @Override
      public String get(int bits) {
        return "%d";
      }
    },
    HEX_LOWER {
      @Override
      public String get(int bits) {
        switch (bits) {
          case 32:
            return "%08x";
          case 16:
            return "%04x";
          case 8:
            return "%02x";
          default:
            throw new IllegalArgumentException();
        }
      }
    },
    HEX_UPPER {
      @Override
      public String get(int bits) {
        switch (bits) {
          case 32:
            return "%08X";
          case 16:
            return "%04X";
          case 8:
            return "%02X";
          default:
            throw new IllegalArgumentException();
        }
      }
    };

    public abstract String get(int bits);
  }

  private interface DefaultConsumer extends IntFunction<Stream<String>> {
    boolean matches(int codepoint);
  }

  static class Range implements DefaultConsumer {
    private final int end;
    private final List<RangeAction> replacement;
    private final int start;

    Range(int start, int end, List<RangeAction> replacement) {
      this.start = start;
      this.end = end;
      this.replacement = replacement;
    }

    @Override
    public Stream<String> apply(int codepoint) {
      return replacement.stream().map(action -> action.apply(codepoint));
    }

    @Override
    public boolean matches(int codepoint) {
      return start <= codepoint && codepoint <= end;
    }
  }

  interface RangeAction extends IntFunction<String> {}

  private static final DefaultConsumer DEFAULT =
      new DefaultConsumer() {
        @Override
        public Stream<String> apply(int codepoint) {
          return Stream.of(new String(new int[] {codepoint}, 0, 1));
        }

        @Override
        public boolean matches(int codepoint) {
          return true;
        }
      };

  private static void add(
      BiConsumer<String, Frame> consumer,
      int bits,
      int index,
      CharFormat format,
      IntUnaryOperator encode) {
    final String interopUrl =
        "utils/str/escape/utf" + bits + "/" + index + "/" + format.name().toLowerCase();
    final String name = "utf" + bits + "_" + index + "_" + format.name().toLowerCase();
    final RangeAction action =
        codepoint -> String.format(format.get(bits), encode.applyAsInt(codepoint));

    consumer.accept(interopUrl, MarshalledFrame.create(name, "<escape>", action, Stream.empty()));
  }

  static Definition create(Map<Integer, String> singleSubstitutions, List<Range> ranges) {
    Collections.sort(ranges, (a, b) -> a.start - b.start);
    return (taskMaster, sourceReference, context, self) ->
        new Escape(singleSubstitutions, ranges, taskMaster, sourceReference, context, self);
  }

  static void createUnicodeActions(BiConsumer<String, Frame> consumer) {
    for (final CharFormat format : CharFormat.values()) {
      add(consumer, 32, 0, format, IntUnaryOperator.identity());
      // http://scripts.sil.org/cms/scripts/page.php?site_id=nrsi&id=iws-appendixa
      add(consumer, 16, 0, format, c -> (c < 65536 ? c : ((c - 65536) % 1024 + 56320) % 65536));
      add(consumer, 16, 1, format, c -> c < 65536 ? 0 : ((c - 65536) / 1024 + 55296) % 65536);
      add(
          consumer,
          8,
          0,
          format,
          c ->
              (c <= 127
                      ? c
                      : c <= 2047 ? c / 64 + 192 : c <= 65535 ? c / 4096 + 224 : c / 262144 + 240)
                  % 256);
      add(
          consumer,
          8,
          1,
          format,
          c ->
              (c <= 127
                      ? 0
                      : c <= 2047
                          ? c % 64 + 128
                          : c <= 65535 ? c / 64 % 64 + 128 : c % 262144 * 4096 + 128)
                  % 256);
      add(
          consumer,
          8,
          2,
          format,
          c -> (c <= 2047 ? 0 : c <= 65535 ? c % 64 + 128 : c % 4096 / 64 + 128) % 256);
      add(consumer, 8, 3, format, c -> (c <= 65535 ? 0 : c % 64 + 128) % 256);
    }
  }

  private final List<DefaultConsumer> ranges = new ArrayList<>();
  private final Map<Integer, String> singleSubstitutions;

  private Escape(
      Map<Integer, String> singleSubstitutions,
      List<Range> ranges,
      TaskMaster taskmaster,
      SourceReference sourceReference,
      Context context,
      Frame self) {
    super(Any::of, asString(false), taskmaster, sourceReference, context, self);
    this.singleSubstitutions = singleSubstitutions;
    this.ranges.addAll(ranges);
  }

  @Override
  protected String computeResult(String input) {
    return input
        .codePoints()
        .boxed()
        .flatMap(
            c -> {
              if (singleSubstitutions.containsKey(c)) {
                return Stream.of(singleSubstitutions.get(c));
              } else {
                return ranges
                    .stream()
                    .filter(r -> r.matches(c))
                    .findFirst()
                    .orElse(DEFAULT)
                    .apply(c);
              }
            })
        .collect(Collectors.joining());
  }

  @Override
  protected void setupExtra() {}
}
