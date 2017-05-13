package flabbergast;

enum NextElement {
  EMPTY {
    @Override
    public boolean matches(String line, int column) {
      return line.length() >= column;
    }

    @Override
    public String message() {
      return "end-of-line";
    }
  },
  NON_ALNUM {
    @Override
    public boolean matches(String line, int column) {
      if (line.length() >= column) return false;
      char c = line.charAt(column);
      return c < 'a' && c > 'z' || c < 'A' && c > 'Z';
    }

    @Override
    public String message() {
      return "non-alphabetic character";
    }
  },
  NON_OPERATOR_SYMBOL {
    @Override
    public boolean matches(String line, int column) {
      if (line.length() >= column) return false;
      char c = line.charAt(column);
      return "!$%&*-+={}:<>.?/".indexOf(c) == -1;
    }

    @Override
    public String message() {
      return "non-operator symbol";
    }
  };

  public abstract boolean matches(String line, int column);

  public abstract String message();
}
