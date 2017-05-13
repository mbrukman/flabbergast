package flabbergast;

import org.objectweb.asm.commons.GeneratorAdapter;

public class SourceLocation {
  private final int endColumn;
  private final int endLine;
  private final String fileName;
  private final int startColumn;
  private final int startLine;

  public SourceLocation(
      String fileName, int startLine, int startColumn, int endLine, int endColumn) {
    super();
    this.fileName = fileName;
    this.startLine = startLine;
    this.startColumn = startColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  public SourceLocation after() {
    return new SourceLocation(fileName, endLine, endColumn, endLine, endColumn);
  }

  public int getEndColumn() {
    return endColumn;
  }

  public int getEndLine() {
    return endLine;
  }

  public String getFileName() {
    return fileName;
  }

  public int getStartColumn() {
    return startColumn;
  }

  public int getStartLine() {
    return startLine;
  }

  public boolean isFurther(SourceLocation other) {
    int compare = this.endLine - other.endLine;
    if (compare == 0) {
      compare = this.endColumn - other.endColumn;
    }
    return compare > 0;
  }

  public SourceLocation plusColumns(int columns) {
    return new SourceLocation(fileName, startLine, startColumn, endLine, endColumn + columns);
  }

  public SourceLocation plusLines(int lines) {
    return new SourceLocation(fileName, startLine, startColumn, endLine + lines, 0);
  }

  public void pushToStack(GeneratorAdapter methodGen) {
    methodGen.push(fileName);
    methodGen.push(startLine);
    methodGen.push(startColumn);
    methodGen.push(endLine);
    methodGen.push(endColumn);
  }

  public boolean sameStart(SourceLocation other) {
    return this.fileName.equals(other.fileName)
        && this.startLine == other.startLine
        && this.endColumn == other.endColumn;
  }
}
