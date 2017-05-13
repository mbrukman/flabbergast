package flabbergast;

import flabbergast.ParserState.Mode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

class ParseRule<T> {
  private interface Alternate<T> {
    ParserState<? extends T> attempt(ParserState<T> input, Consumer<? super T> consumer);
  }

  public static class Memory<R> {
    private class Item {
      R result;
      ParserState<R> state;
    }

    private List<Item> items = new ArrayList<>();

    ParserState<R> check(ParserState<R> state, MemoryGenerator<R> generator, Consumer<R> output) {
      Item item =
          items
              .stream()
              .filter(i -> i.state.location().sameStart(state.location()))
              .findFirst()
              .orElseGet(
                  () -> {
                    Item newItem = new Item();
                    newItem.state = generator.parse(state, x -> newItem.result = x);
                    items.add(newItem);
                    return newItem;
                  });

      output.accept(item.result);
      return item.state;
    }
  }

  public interface MemoryGenerator<R> {
    ParserState<R> parse(ParserState<R> state, Consumer<R> output);
  }

  private final List<Alternate<T>> alternates = new ArrayList<>();

  private final String name;

  ParseRule(String name) {
    this.name = name;
  }

  @SafeVarargs
  public final <S extends T> void create(Function<SourceLocation, S> ctor, Parser<S>... parsers) {
    alternates.add(
        (input, output) -> {
          RevCons<String> expectedSymbols = RevCons.empty();
          for (int it = parsers.length - 1; it >= 0; it--) {
            expectedSymbols = parsers[it].nextSymbol(expectedSymbols, (t, h) -> RevCons.cons(h, t));
          }

          ParserState<S> state =
              Arrays.stream(parsers)
                  .reduce(
                      input.createChild(expectedSymbols),
                      (i, parser) -> parser.parse(i),
                      (a, b) -> {
                        throw new UnsupportedOperationException();
                      });
          if (state.mode() == Mode.OK) {
            output.accept(state.applyTo(ctor.apply(state.location())));
          }
          return state;
        });
  }

  public String name() {
    return name;
  }

  ParserState<T> parse(ParserState<T> input, Consumer<? super T> output) {
    ParserState<T> best =
        ParserState.error(Mode.BAD, input.location(), String.format("Expected %s.", name));
    for (Alternate<T> alternate : alternates) {
      ParserState<? extends T> current = alternate.attempt(input, output);
      if (current.mode() == Mode.OK || current.mode() == Mode.BAD) {
        return current.createChild(RevCons.empty());
      }
      if (current.location().isFurther(best.location())) {
        best = current.createChild(RevCons.empty());
      }
    }
    return best;
  }
}
