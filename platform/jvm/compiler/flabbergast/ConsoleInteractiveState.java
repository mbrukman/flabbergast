package flabbergast;

import flabbergast.Compiler.LineSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import jline.console.ConsoleReader;
import jline.console.completer.AggregateCompleter;
import jline.console.completer.StringsCompleter;
import jline.console.history.FileHistory;

public class ConsoleInteractiveState extends InteractiveState {

  private final BuildCollector collector =
      new DynamicBuildCollector<InteractiveCommand>(
          InteractiveCommand.class, new DynamicClassLoader(), ErrorCollector.toStandardError()) {

        @Override
        protected void emit(Class<? extends InteractiveCommand> output) {
          try {
            output.newInstance().launch(ConsoleInteractiveState.this);
          } catch (InstantiationException | IllegalAccessException e) {
            System.err.printf(
                "Internal error: Failed to instantiate command: %s\n", e.getMessage());
          }
        }
      };
  private final Compiler compiler;
  private int lineNumber;
  private final ConsoleReader reader;
  private boolean running = true;
  private TaskMaster taskMaster =
      new ConsoleTaskMaster() {

        @Override
        protected void print(String str) {
          try {
            reader.print(str);
          } catch (IOException e) {
            // If we can't print to the console, then what.
          }
        }

        @Override
        protected void println(String str) {
          try {
            reader.println(str);
          } catch (IOException e) {
            // If we can't print to the console, then what.
          }
        }
      };

  public ConsoleInteractiveState(Frame root, FileHistory history) throws IOException {
    super(root);
    compiler = Compiler.find(SourceFormat.FLABBERGAST_REPL, TargetFormat.JVM).get();
    reader = new ConsoleReader();
    reader.addCompleter(
        new AggregateCompleter(
            Arrays.asList(
                new StringsCompleter(compiler.keywords().collect(Collectors.toList())),
                new AttributeCompleter(this::getCurrent))));
    reader.setPrompt("‽ ");
    reader.setHistory(history);
    reader.setHistoryEnabled(true);
    reader.setPaginationEnabled(true);
    reader.setExpandEvents(false);
  }

  @Override
  protected TaskMaster getTaskMaster() {
    return taskMaster;
  }

  @Override
  protected void print(String value) {
    try {
      reader.print(value);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void printColumns(List<String> collection) {
    try {
      reader.printColumns(collection);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void quit() {
    running = false;
  }

  public boolean showPrompt() {
    if (!running) {
      return false;
    }
    compiler
        .compile(
            "line" + (++lineNumber),
            new LineSource() {
              private boolean first = true;

              @Override
              public String pull(String outstandingKeyword) {
                if (!first && outstandingKeyword == null) {
                  return null;
                }
                first = false;
                try {
                  return reader.readLine(
                      outstandingKeyword == null ? "‽ " : (outstandingKeyword + "‽ "));
                } catch (IOException e) {
                  return null;
                }
              }
            })
        .collect(collector);
    return true;
  }
}
