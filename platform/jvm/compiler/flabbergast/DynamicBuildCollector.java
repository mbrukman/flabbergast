package flabbergast;

public abstract class DynamicBuildCollector<T> implements BuildCollector {

  private DynamicClassLoader classLoader;
  private Class<T> clazz;
  private final ErrorCollector errorCollector;

  DynamicBuildCollector(
      Class<T> clazz, DynamicClassLoader classLoader, ErrorCollector errorCollector) {
    this.clazz = clazz;
    this.classLoader = classLoader;
    this.errorCollector = errorCollector;
  }

  protected abstract void emit(Class<? extends T> output);

  @Override
  public final void emitError(SourceLocation location, String error) {
    errorCollector.emitError(location, error);
  }

  @Override
  public final void emitOutput(String fileName, byte[] data) {
    classLoader.accept(fileName, data);
  }

  @Override
  public final void emitRoot(String fileName) {
    try {
      emit(classLoader.findClass(fileName.replace('/', '.')).asSubclass(clazz));
    } catch (ClassNotFoundException e) {
    }
  }
}
