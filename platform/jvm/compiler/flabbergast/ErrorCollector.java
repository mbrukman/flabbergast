package flabbergast;

import java.io.PrintStream;

public interface ErrorCollector {
  static ErrorCollector of(PrintStream stream) {
    return (where, error) ->
        stream.printf(
            "%s:%d:%d-%d:%d: %s\n",
            where.getFileName(),
            where.getStartLine(),
            where.getStartColumn(),
            where.getEndLine(),
            where.getEndColumn(),
            error);
  }

  static ErrorCollector toStandardError() {
    return of(System.err);
  }

  void emitError(SourceLocation location, String error);
}
