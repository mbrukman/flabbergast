package flabbergast;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class Compiler {

  public interface LineSource {
    String pull(String outstandingKeyword);
  }

  public static final class StringSource implements LineSource {
    private int index;

    private final List<String> lines;

    public StringSource(List<String> lines) {
      super();
      this.lines = lines;
    }

    @Override
    public String pull(String outstandingKeyword) {
      if (index >= lines.size()) {
        return null;
      }
      String line = lines.get(index++);
      return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }
  }

  public static Optional<Compiler> find(SourceFormat sourceFormat, TargetFormat targetFormat) {
    return StreamSupport.stream(ServiceLoader.load(Compiler.class).spliterator(), false)
        .filter(
            compiler ->
                compiler.getSourceFormat() == sourceFormat
                    && compiler.getTargetFormat() == targetFormat)
        .findFirst();
  }

  private final SourceFormat sourceFormat;
  private final TargetFormat targetFormat;

  protected Compiler(SourceFormat sourceFormat, TargetFormat targetFormat) {
    this.sourceFormat = sourceFormat;
    this.targetFormat = targetFormat;
  }

  public CompileOutput compile(Path input) {
    try {
      return compile(
          input.getFileName().toString(),
          new StringSource(Files.readAllLines(input, StandardCharsets.UTF_8)));
    } catch (Exception e) {
      return CompileOutput.error(e.getMessage());
    }
  }

  public final Stream<Pair<String, CompileOutput>> compile(Path directory, String extension)
      throws IOException {
    try (Stream<Path> files = Files.walk(directory, Integer.MAX_VALUE)) {
      return files
          .filter(path -> path.endsWith(extension))
          .map(
              path -> {
                String fileName = path.relativize(directory).toString();
                try {
                  return new Pair<>(
                      fileName,
                      compile(
                          fileName,
                          new StringSource(Files.readAllLines(path, StandardCharsets.UTF_8))));
                } catch (Exception e) {
                  return new Pair<>(fileName, CompileOutput.error(e.getMessage()));
                }
              });
    }
  }

  public abstract CompileOutput compile(String fileName, LineSource input);

  public final SourceFormat getSourceFormat() {
    return sourceFormat;
  }

  public final TargetFormat getTargetFormat() {
    return targetFormat;
  }

  public abstract Stream<String> keywords();

  public abstract void setGitHubUrl(String url);
}
