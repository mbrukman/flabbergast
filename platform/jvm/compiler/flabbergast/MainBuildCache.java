package flabbergast;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class MainBuildCache {

  public static void main(String[] args) throws IOException {
    Options options = new Options();
    CommandLineParser argParser = new GnuParser();
    final CommandLine result;

    try {
      result = argParser.parse(options, args);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }

    if (result.getArgs().length != 0) {
      System.exit(1);
      return;
    }
    try (Stream<Path> files = Files.walk(Paths.get(".jvmcache"), FileVisitOption.FOLLOW_LINKS)) {
      files
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .peek(System.out::println)
          .forEach(File::delete);
    }
    Compiler compiler = Compiler.find(SourceFormat.FLABBERGAST, TargetFormat.JVM).get();
    Set<String> brokenFiles = new HashSet<>();
    BuildCollector collector =
        new BuildCollector() {

          @Override
          public void emitError(SourceLocation location, String error) {
            brokenFiles.add(location.getFileName());
          }

          @Override
          public void emitOutput(String fileName, byte[] data) {
            Path path = Paths.get(".jvmcache", fileName + ".class");
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
    compiler
        .compile(Paths.get("."), SourceFormat.FLABBERGAST.getExtension())
        .map(Pair::getValue)
        .forEach(output -> output.collect(collector));
    brokenFiles.stream().forEach(file -> System.err.println("Failed to compile: " + file));
    System.exit(brokenFiles.isEmpty() ? 0 : 1);
  }
}
