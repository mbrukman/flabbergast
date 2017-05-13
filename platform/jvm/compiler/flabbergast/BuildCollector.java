package flabbergast;

public interface BuildCollector extends ErrorCollector {
  void emitOutput(String fileName, byte[] data);

  void emitRoot(String fileName);
}
