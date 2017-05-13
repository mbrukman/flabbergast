package flabbergast;

import flabbergast.ParseRule.Memory;
import flabbergast.ParserState.Mode;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public abstract class Parser<T> {

  @SafeVarargs
  public static <T> Parser<T> all(Parser<T>... parts) {
    return new Parser<T>() {

      @Override
      <X> X nextSymbol(X initial, BiFunction<X, String, X> function) {
        return Arrays.stream(parts)
            .reduce(
                initial,
                (i, parser) -> parser.nextSymbol(i, function),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
      }

      @Override
      ParserState<T> parse(ParserState<T> input) {
        return Arrays.stream(parts)
            .reduce(
                input,
                (i, parser) -> parser.parse(i),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
      }
    };
  }

  public static <T> Parser<T> keyword(String keyword) {
    return new Parser<T>() {
      @Override
      <X> X nextSymbol(X initial, BiFunction<X, String, X> function) {
        return function.apply(initial, keyword);
      }

      @Override
      ParserState<T> parse(ParserState<T> input) {
        return input.consume(keyword).is(NextElement.EMPTY, NextElement.NON_ALNUM);
      }
    };
  }

  public static <T> Parser<T> optional(Parser<T> optionalPart) {
    return new Parser<T>() {
      @Override
      <X> X nextSymbol(X initial, BiFunction<X, String, X> function) {
        return initial;
      }

      @Override
      ParserState<T> parse(ParserState<T> input) {
        if (input.mode() != Mode.OK) {
          return input;
        }
        ParserState<T> optionalState = optionalPart.parse(input);
        return (optionalState.mode() == Mode.FAILED) ? input : optionalState;
      }
    };
  }

  public static <T, R> Parser<T> rule(
      ParseRule<R> inner, Memory<R> memories, BiConsumer<T, R> writer) {
    return new Parser<T>() {
      @Override
      <X> X nextSymbol(X initial, BiFunction<X, String, X> function) {
        return function.apply(initial, inner.name());
      }

      @Override
      ParserState<T> parse(ParserState<T> input) {
        return input.parse(inner, memories, output -> self -> writer.accept(self, output));
      }
    };
  }

  public static <T> Parser<T> symbol(String symbol) {
    return new Parser<T>() {
      @Override
      <X> X nextSymbol(X initial, BiFunction<X, String, X> function) {
        return function.apply(initial, symbol);
      }

      @Override
      ParserState<T> parse(ParserState<T> input) {
        return input.consume(symbol).is(NextElement.EMPTY, NextElement.NON_OPERATOR_SYMBOL);
      }
    };
  }

  public static <T> Parser<T> integer(int base, BiConsumer<T, Long> writer) {}

  public static <T, S> Parser<T> list(Parser<S> item, X separator, BiConsumer<T, List<S>> writer) {
    // TODO
  }

  public static <T> Parser<T> whitespace() {
    return new Parser<T>() {
      @Override
      <X> X nextSymbol(X initial, BiFunction<X, String, X> function) {
        return initial;
      }

      @Override
      ParserState<T> parse(ParserState<T> input) {
        return input.whitespace();
      }
    };
  }

  abstract <X> X nextSymbol(X initial, BiFunction<X, String, X> function);

  abstract ParserState<T> parse(ParserState<T> input);
}
