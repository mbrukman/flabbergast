package flabbergast;

import java.nio.file.Paths;
import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class MainPrinter {
  public static void main(String[] args) {
    Options options = new Options();
    options.addOption("o", "output", true, "Write output to file instead of standard output.");
    options.addOption("p", "no-precomp", false, "Do not use precompiled libraries");
    options.addOption("s", "sandbox", false, "Do not allow network/disk access");
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
      System.err.println("Run a Flabbergast file and display the “value” attribute.");
      formatter.printHelp("gnu", options);
      System.exit(1);
    }

    String[] files = result.getArgs();
    if (files.length != 1) {
      System.err.println("Exactly one Flabbergast script must be given.");
      System.exit(1);
    }

    ResourcePathFinder resourceFinder = new ResourcePathFinder();
    resourceFinder.add(Paths.get(files[0]).toAbsolutePath().getParent().resolve("lib"));
    resourceFinder.addDefaults();
    ConsoleTaskMaster taskMaster =
        new ConsoleTaskMaster() {

          @Override
          protected void print(String str) {
            System.err.print(str);
          }

          @Override
          protected void println(String str) {
            System.err.println(str);
          }
        };
    DynamicCompiler compilerLoader =
        new DynamicCompiler(SourceFormat.FLABBERGAST, ErrorCollector.toStandardError());
    compilerLoader.setFinder(resourceFinder);
    taskMaster.addUriHandler(compilerLoader);
    EnumSet<LoadRule> rules = EnumSet.noneOf(LoadRule.class);
    if (result.hasOption('p')) {
      rules.add(LoadRule.PRECOMPILED);
    }
    if (result.hasOption('s')) {
      rules.add(LoadRule.SANDBOXED);
    }
    taskMaster.addAllUriHandlers(resourceFinder, rules);
    Compiler compiler =
        SourceFormat.find(files[0])
            .optionalMap(format -> Compiler.find(format, TargetFormat.JVM))
            .orElseThrow("Cannot find compiler for this file.");
    PrintResult printer =
        result.hasOption('o')
            ? new PrintResult.ToFile(result.getOptionValue('o'))
            : new PrintResult.ToStandardOut();
    Instantiator instantiator =
        new Instantiator(ErrorCollector.toStandardError(), taskMaster, printer.toConsumer());
    compiler.compile(Paths.get(files[0])).collect(instantiator);
    taskMaster.run();
    taskMaster.reportCircularEvaluation();
    System.exit(printer.getSuccess() ? 0 : 1);
  }
}
