package flabbergast;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ResourcePathFinder {
  private static final Pattern PATH_SEPARATOR = Pattern.compile(Pattern.quote(File.pathSeparator));
  private final List<Path> paths = new ArrayList<>();

  public void add(Path path) {
    paths.add(path);
  }

  public void addDefaults() {
    final boolean isntWindows = !System.getProperty("os.name").startsWith("Windows");
    Stream.of(
            Maybe.of(System.getenv("FLABBERGAST_PATH"))
                .flatStream(PATH_SEPARATOR::splitAsStream)
                .map(Paths::get),
            isntWindows
                ? Stream.of(
                    Paths.get(
                        System.getProperty("user.home"), ".local", "share", "flabbergast", "lib"))
                : Stream.<Path>empty(),
            getSelfPath(),
            isntWindows
                ? Stream.of(
                    Paths.get("/usr/share/flabbergast/lib"),
                    Paths.get("/usr/local/lib/flabbergast/lib"))
                : Stream.<Path>empty())
        .flatMap(Function.identity())
        .forEach(paths::add);
  }

  public Optional<File> find(String basename, String... extensions) {
    return paths
        .stream()
        .flatMap(
            path -> Arrays.stream(extensions).map(extension -> path.resolve(basename + extension)))
        .map(Path::toFile)
        .filter(File::canRead)
        .findFirst();
  }

  private Stream<Path> getSelfPath() {
    try {
      return Stream.of(
          Paths.get(
              Frame.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath(),
              "..",
              "..",
              "flabbergast",
              "lib",
              "flabbergast"));
    } catch (final URISyntaxException e) {
      return Stream.empty();
    }
  }

  public Stream<URL> urls() {
    return paths
        .stream()
        .map(Path::toUri)
        .map(
            uri -> {
              try {
                return uri.toURL();
              } catch (MalformedURLException e) {
                return null;
              }
            })
        .filter(Objects::nonNull);
  }
}
