package flabbergast;

import java.util.Arrays;

public enum SourceFormat {
  FLABBERGAST("o_0"),
  FLABBERGAST_REPL(null),
  KWS("kws");

  public static Maybe<SourceFormat> find(String filename) {
    return Maybe.ofOptional(
        Arrays.stream(values())
            .filter(format -> filename.endsWith(format.getExtension()))
            .findFirst());
  }

  private final String extension;

  SourceFormat(String extension) {
    this.extension = extension == null ? null : ("." + extension);
  }

  public String getExtension() {
    return extension;
  }
}
