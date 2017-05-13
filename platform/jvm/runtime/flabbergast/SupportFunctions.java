package flabbergast;

public final class SupportFunctions {
  private static char[] symbols = createOrdinalSymbols();

  private static char[] createOrdinalSymbols() {
    final char[] array = new char[62];
    for (int it = 0; it < 10; it++) {
      array[it] = (char) ('0' + it);
    }
    for (int it = 0; it < 26; it++) {
      array[it + 10] = (char) ('A' + it);
      array[it + 36] = (char) ('a' + it);
    }
    return array;
  }

  public static Stringish ordinalName(long id) {
    return Stringish.from(ordinalNameStr(id));
  }

  public static String ordinalNameStr(long id) {
    final char[] idStr = new char[(int) (Long.SIZE * Math.log(2) / Math.log(symbols.length)) + 1];
    if (id < 0) {
      idStr[0] = 'e';
      id = Long.MAX_VALUE + id;
    } else {
      idStr[0] = 'f';
    }
    for (int it = idStr.length - 1; it > 0; it--) {
      idStr[it] = symbols[(int) (id % symbols.length)];
      id = id / symbols.length;
    }
    return new String(idStr);
  }

  public static long shift(long number, long shift) {
    if (shift < 0) {
      shift = -shift;
      if (shift < 64) {
        return number >> shift;
      }
      return number < 0 ? -1 : 0;
    } else {
      if (shift < 64) {
        return number << shift;
      }
      return 0;
    }
  }

  private SupportFunctions() {}
}
