package flabbergast;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class ParserState<T> {
  private static class GoodState<T> extends ParserState<T> {
    private final RevCons<Consumer<T>> actions;
    private final ParseBuffer buffer;
    private final RevCons<String> comments;
    private final RevCons<String> expectedSymbols;
    private final SourceLocation location;

    public GoodState(
        ParseBuffer buffer,
        SourceLocation location,
        RevCons<Consumer<T>> actions,
        RevCons<String> comments,
        RevCons<String> expectedSymbols) {
      super();
      this.buffer = buffer;
      this.location = location;
      this.actions = actions;
      this.comments = comments;
      this.expectedSymbols = expectedSymbols;
    }

    @Override
    public T applyTo(T object) {
      actions.stream().forEach(action -> action.accept(object));
      return object;
    }

    @Override
    public ParserState<T> consume(String keyword) {
      String line = buffer.line(expectedSymbols.head(), location.getEndLine());
      if (line == null) {
        return error(
            Mode.FAILED, location(), String.format("Expected “%s”, but no more input.", keyword));
      }
      if (line.regionMatches(location.getEndColumn(), keyword, 0, keyword.length())) {
        return new GoodState<T>(
            buffer,
            location.plusColumns(keyword.length()),
            actions,
            comments,
            expectedSymbols.head().equals(keyword) ? expectedSymbols.pop() : expectedSymbols);
      }
      return error(Mode.FAILED, location(), String.format("Expected “%s”.", keyword));
    }

    @Override
    public <S> ParserState<S> createChild(RevCons<String> expectedSymbols) {
      return new GoodState<S>(buffer, location, RevCons.empty(), RevCons.empty(), expectedSymbols);
    }

    @Override
    public ParserState<T> is(NextElement... elements) {
      for (NextElement next : elements) {
        if (next.matches(
            buffer.line(expectedSymbols.head(), location.getEndLine()), location.getEndColumn())) {
          return this;
        }
      }
      return error(
          Mode.FAILED,
          location(),
          Arrays.stream(elements)
              .map(NextElement::message)
              .collect(Collectors.joining(", ", "Expected one of: ", "")));
    }

    @Override
    public SourceLocation location() {
      return location;
    }

    @Override
    public String message() {
      return "No error.";
    }

    @Override
    public Mode mode() {
      return Mode.OK;
    }

    @Override
    public <R> ParserState<T> parse(
        ParseRule<R> inner, ParseRule.Memory<R> memories, Function<R, Consumer<T>> output) {
      Ptr<R> result = new Ptr<>();
      ParserState<R> state =
          memories.check(createChild(RevCons.empty()), inner::parse, result::set);
      if (state.mode() == Mode.OK) {
        Consumer<T> newAction = output.apply(result.get());
        return new GoodState<T>(
            buffer,
            state.location(),
            RevCons.cons(newAction, actions),
            comments,
            expectedSymbols.head().equals(inner.name()) ? expectedSymbols.pop() : expectedSymbols);
      }
      return error(state.mode(), state.location(), state.message());
    }

    @Override
    public ParserState<T> whitespace() {
      String currentLine;
      RevCons<String> accumulatedComments = comments;
      SourceLocation current;
      for (current = location;
          (currentLine = buffer.line(expectedSymbols.head(), current.getEndLine())) != null;
          current = current.plusLines(1)) {
        Matcher matcher = COMMENT.matcher(currentLine.substring(current.getEndColumn()));
        if (!matcher.matches()) {
          break;
        }
        if (matcher.group(1).length() == 0) {
          current = current.plusColumns(matcher.end() + 1);
          break;
        }
        if (matcher.group(2).length() > 0) {
          accumulatedComments = RevCons.cons(matcher.group(2), accumulatedComments);
        }
      }
      return new GoodState<>(buffer, current, actions, accumulatedComments, expectedSymbols);
    }
  }

  public enum Mode {
    BAD,
    FAILED,
    OK
  }

  private static final Pattern COMMENT = Pattern.compile("\\s*(#\\s*(.*$)$)\\?");

  public static <T> ParserState<T> create(ParseBuffer buffer, String filename) {
    return new GoodState<>(
        buffer,
        new SourceLocation(filename, 1, 1, 1, 1),
        RevCons.empty(),
        RevCons.empty(),
        RevCons.empty());
  }

  public static <T> ParserState<T> error(Mode mode, SourceLocation location, String message) {
    return new ParserState<T>() {
      @Override
      public T applyTo(T object) {
        return object;
      }

      @Override
      public ParserState<T> consume(String keyword) {
        return this;
      }

      @Override
      public <S> ParserState<S> createChild(RevCons<String> expectedSymbols) {
        return error(mode, location, message);
      }

      @Override
      public ParserState<T> is(NextElement... elements) {
        return this;
      }

      @Override
      public SourceLocation location() {
        return location;
      }

      @Override
      public String message() {
        return message;
      }

      @Override
      public Mode mode() {
        return mode;
      }

      @Override
      public <R> ParserState<T> parse(
          ParseRule<R> inner, ParseRule.Memory<R> memories, Function<R, Consumer<T>> output) {
        return this;
      }

      @Override
      public ParserState<T> whitespace() {
        return this;
      }
    };
  }

  private ParserState() {}

  public abstract T applyTo(T object);

  public abstract ParserState<T> consume(String keyword);

  public abstract <S> ParserState<S> createChild(RevCons<String> expectedSymbols);

  public abstract ParserState<T> is(NextElement... elements);

  public abstract SourceLocation location();

  public abstract String message();

  public abstract Mode mode();

  public abstract <R> ParserState<T> parse(
      ParseRule<R> inner, ParseRule.Memory<R> memories, Function<R, Consumer<T>> output);

  public abstract ParserState<T> whitespace();
}
