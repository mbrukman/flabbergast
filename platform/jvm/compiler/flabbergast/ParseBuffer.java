package flabbergast;

import flabbergast.Compiler.LineSource;
import java.util.ArrayList;
import java.util.List;

class ParseBuffer {

  private final List<String> lines = new ArrayList<>();
  private final LineSource source;

  public ParseBuffer(LineSource source) {
    this.source = source;
  }

  public String line(String outstandingKeyword, int requested) {
    while (requested > lines.size()) {
      String line = source.pull(outstandingKeyword);
      if (line == null) {
        return null;
      }
      lines.add(line);
    }
    return lines.get(requested);
  }
}
