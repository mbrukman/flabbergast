package flabbergast;

import java.net.URI;

public class DynamicCompiler extends LoadLibraries {

  private final class DynamicCompilerBuildCollector extends DynamicBuildCollector<Future> {
    Class<? extends Future> output;

    DynamicCompilerBuildCollector() {
      super(Future.class, classLoader, errorCollector);
    }

    @Override
    protected void emit(Class<? extends Future> output) {
      this.output = output;
    }
  }

  final DynamicClassLoader classLoader = new DynamicClassLoader();

  private final Compiler compiler;

  private final ErrorCollector errorCollector;

  public DynamicCompiler(SourceFormat format, ErrorCollector errorCollector) {
    this.compiler =
        Compiler.find(format, TargetFormat.JVM)
            .orElseThrow(() -> new IllegalArgumentException("Compiler backend not available."));
    this.errorCollector = errorCollector;
  }

  public Compiler getCompiler() {
    return compiler;
  }

  @Override
  public int getPriority() {
    return -50;
  }

  @Override
  public String getUriName() {
    return "dynamically-compiled " + compiler.getSourceFormat().name();
  }

  @Override
  public Maybe<Class<? extends Future>> resolveUri(URI uri) {
    return Maybe.of(uri)
        .filter(x -> x.getScheme().equals("lib"))
        .map(URI::getSchemeSpecificPart)
        .optionalMap(x -> getFinder().find(x, compiler.getSourceFormat().getExtension()))
        .flatMap(
            file -> {
              DynamicCompilerBuildCollector collector = new DynamicCompilerBuildCollector();
              compiler.compile(file.toPath()).collect(collector);
              return collector.output == null
                  ? Maybe.error(String.format("Failed to compile library “%s”.", uri))
                  : Maybe.of(collector.output);
            });
  }
}
