package flabbergast;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;
import jline.console.history.FileHistory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.fusesource.jansi.Ansi;

public class MainREPL {

  private static Optional<Path> getDataDir() {
    String userHome = System.getProperty("user.home");
    return Stream.of(
            System.getenv("XDG_DATA_HOME"),
            userHome + File.separator + ".config",
            userHome + File.separator + "Library" + File.separator + "Application Support",
            userHome)
        .map(Paths::get)
        .filter(Files::exists)
        .filter(Files::isDirectory)
        .findFirst();
  }

  public static void main(String[] args) throws IOException {
    Options options = new Options();
    options.addOption("p", "no-precomp", false, "Do not use precompiled libraries");
    options.addOption("h", "help", false, "Show this message and exit");
    CommandLineParser argParser = new GnuParser();
    CommandLine result;

    try {
      result = argParser.parse(options, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }

    if (result.hasOption('h')) {
      HelpFormatter formatter = new HelpFormatter();
      System.err.println(
          "Run a Flabbergast file and browse the results or just enter expessions to see what happens.");
      formatter.printHelp("gnu", options);
      System.exit(1);
    }

    String[] files = result.getArgs();
    if (files.length > 1) {
      System.err.println("Only one Flabbergast script may be given.");
      System.exit(1);
    }
    System.out.print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLUE).toString());
    System.out.print("o_0 ");
    System.out.print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.WHITE).toString());
    System.out.println("Flabbergast " + Configuration.VERSION);
    System.out.print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
    System.out.println("Type Help for a list of commands.");

    Optional<Path> script =
        files.length == 1 ? Optional.of(Paths.get(files[0]).toAbsolutePath()) : Optional.empty();

    ResourcePathFinder resourceFinder = new ResourcePathFinder();
    Optional.of(script.map(Path::getParent).orElse(Paths.get(".")).resolve("lib"))
        .filter(Files::exists)
        .filter(Files::isDirectory)
        .ifPresent(resourceFinder::add);
    resourceFinder.addDefaults();
    ConsoleTaskMaster taskMaster =
        new ConsoleTaskMaster() {

          @Override
          protected void print(String str) {
            System.err.println(str);
          }

          @Override
          protected void println(String str) {
            System.err.print(str);
          }
        };
    DynamicCompiler dynamicCompiler =
        new DynamicCompiler(SourceFormat.FLABBERGAST, ErrorCollector.toStandardError());
    dynamicCompiler.setFinder(resourceFinder);
    taskMaster.addUriHandler(dynamicCompiler);
    EnumSet<LoadRule> rules = EnumSet.of(LoadRule.INTERACTIVE);
    if (result.hasOption('p')) {
      rules.add(LoadRule.PRECOMPILED);
    }
    taskMaster.addAllUriHandlers(resourceFinder, rules);
    Ptr<Frame> root = new Ptr<>(InteractiveState.EMPTY_ROOT);
    if (files.length == 1) {
      Instantiator instantiator =
          new Instantiator(
              ErrorCollector.toStandardError(),
              taskMaster,
              new AcceptOrFail() {

                @Override
                public void accept(Frame value) {
                  root.set(value);
                }

                @Override
                protected void fail(String type) {
                  System.err.printf("Expected file to be of type Frame, but got %s.\n", type);
                }
              }.toConsumer());
      dynamicCompiler.getCompiler().compile(Paths.get(files[0])).collect(instantiator);
      taskMaster.run();
    }

    FileHistory history =
        new FileHistory(getDataDir().get().resolve("flabbergast.history").toFile());
    ConsoleInteractiveState state = new ConsoleInteractiveState(root.get(), history);
    while (state.showPrompt()) ;
    history.flush();
  }
}
