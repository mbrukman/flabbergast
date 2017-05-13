package flabbergast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class MainDocumenter {
  public static void main(String[] args) {
    Options options = new Options();
    options.addOption("g", "github", true, "The URL to the GitHub version of these files.");
    options.addOption("o", "output", true, "The directory to place the docs.");
    options.addOption("h", "help", false, "Show this message and exit");
    CommandLineParser clParser = new GnuParser();
    final CommandLine result;

    try {
      result = clParser.parse(options, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }

    if (result.hasOption('h')) {
      HelpFormatter formatter = new HelpFormatter();
      System.err.println("Document a directory containing Flabbergast files.");
      formatter.printHelp("gnu", options);
      System.exit(1);
    }

    String[] directories = result.getArgs();
    if (directories.length == 0) {
      System.err.println("I need some directories full of delicious source files to document.");
      System.exit(1);
      return;
    }
    Path output = Paths.get(result.getOptionValue('o', "."));
    Compiler compiler = Compiler.find(SourceFormat.FLABBERGAST, TargetFormat.APIDOC).get();
    compiler.setGitHubUrl(result.getOptionValue('g'));
    Set<String> brokenFiles = new HashSet<>();
    BuildCollector collector =
        new BuildCollector() {

          @Override
          public void emitError(SourceLocation location, String error) {
            brokenFiles.add(location.getFileName());
          }

          @Override
          public void emitOutput(String fileName, byte[] data) {
            Path path = output.resolve(String.format("doc-%s.xml", fileName.replace('/', '-')));
            try {
              Files.createDirectories(path.getParent());
              Files.write(
                  path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
              System.err.printf("Failed to write %s: %s\n", path, e.getMessage());
            }
          }

          @Override
          public void emitRoot(String fileName) {}
        };
    Arrays.stream(directories)
        .map(Paths::get)
        .flatMap(
            directory -> {
              try {
                return compiler.compile(directory, SourceFormat.FLABBERGAST.getExtension());
              } catch (IOException e) {
                brokenFiles.add(directory.toString());
                return Stream.empty();
              }
            })
        .map(Pair::getValue)
        .forEach(artefact -> artefact.collect(collector));
    brokenFiles.stream().forEach(file -> System.err.println("Failed to compile: " + file));
    System.exit(brokenFiles.isEmpty() ? 0 : 1);
  }
}
