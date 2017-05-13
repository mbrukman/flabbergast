package flabbergast;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Stringish implements Comparable<Stringish> {
  private static final Stringish[] BOOLEANS = new Stringish[] {from("False"), from("True")};
  private static final Collator COLLATOR = Collator.getInstance();

  public static Stringish from(boolean value) {
    return BOOLEANS[value ? 1 : 0];
  }

  public static Stringish from(double value) {
    return from(Double.toString(value));
  }

  public static Stringish from(double value, boolean exponential, long digits) {
    final DecimalFormat format = new DecimalFormat(exponential ? "#.#E0" : "#.#");
    format.setMinimumFractionDigits((int) digits);
    format.setMaximumFractionDigits((int) digits);
    return from(format.format(value));
  }

  public static Stringish from(long value) {
    return from(Long.toString(value));
  }

  public static Stringish from(long value, boolean hex, long digits) {
    return from(String.format("%" + (digits > 0 ? "0" + digits : "") + (hex ? "X" : "d"), value));
  }

  public static Stringish from(String value) {
    return new Stringish(
        Collections.singletonList(value),
        value.codePointCount(0, value.length()),
        value.length(),
        value.getBytes(StandardCharsets.UTF_8).length);
  }

  public static Stringish fromCodepoint(long codepoint) {
    return from(new String(new int[] {(int) codepoint}, 0, 1));
  }

  private int hash;

  private final long length;

  private final long length16;

  private final long length8;
  private List<String> parts;

  private Stringish(List<String> parts, long length, long length16, long length8) {
    this.parts = parts;
    this.length = length;
    this.length16 = length16;
    this.length8 = length8;
  }

  @Override
  public int compareTo(Stringish other) {
    final String thisStr = toString();
    final String otherStr = other.toString();
    return COLLATOR.compare(thisStr, otherStr);
  }

  public Stringish concat(Stringish other) {
    return new Stringish(
        Stream.concat(parts.stream(), other.parts.stream()).collect(Collectors.toList()),
        length + other.length,
        length16 + other.length16,
        length8 + other.length8);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof Stringish) {
      return this.compareTo((Stringish) other) == 0;
    }
    return false;
  }

  public Long find(String str, long start, boolean backward) {
    final long originalStart = start >= 0 ? start : start + getLength();
    final long realStart = backward ? getLength() - originalStart - 1 : originalStart;
    if (realStart < 0 || realStart > getLength()) {
      return null;
    }
    final String thisStr = toString();
    final int thisStrStart = thisStr.offsetByCodePoints(0, (int) realStart);
    final long pos =
        backward ? thisStr.lastIndexOf(str, thisStrStart) : thisStr.indexOf(str, thisStrStart);
    return pos == -1 ? null : pos;
  }

  public long getLength() {
    return length;
  }

  public long getUtf16Length() {
    return length16;
  }

  public long getUtf8Length() {
    return length8;
  }

  @Override
  public int hashCode() {
    if (hash == 0 && length16 > 0) {
      hash = parts.stream().flatMapToInt(String::chars).reduce(0, (a, v) -> 31 * a + v);
    }
    return hash;
  }

  public String slice(long start, Long end, Long length) {
    if ((end == null) == (length == null)) {
      throw new IllegalArgumentException("Only one of “length” or “end” maybe specified.");
    }
    final long originalStart = start >= 0 ? start : start + getLength();
    if (originalStart > getLength() || start < 0) {
      return null;
    }
    final String thisStr = toString();
    final int realStart = thisStr.offsetByCodePoints(0, (int) originalStart);
    int realEnd;
    if (length != null) {
      if (length < 0) {
        throw new IllegalArgumentException("“length” must be non-negative.");
      }
      realEnd = thisStr.offsetByCodePoints(0, (int) (originalStart + length));
    } else {
      final long originalEnd = end >= 0 ? end : getLength() + end;
      if (originalEnd < originalStart) {
        return null;
      }
      realEnd = thisStr.offsetByCodePoints(0, (int) originalEnd);
    }
    return thisStr.substring(realStart, realEnd);
  }

  @Override
  public synchronized String toString() {
    if (parts.size() > 1) {
      parts = Collections.singletonList(String.join("", parts));
    }
    return parts.get(0);
  }

  public byte[] toUtf16(boolean big) {
    return toString().getBytes(big ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE);
  }

  public byte[] toUtf32(boolean big) {
    try {
      return toString().getBytes("UTF-32" + (big ? "BE" : "LE"));
    } catch (final UnsupportedEncodingException e) {
      return new byte[0];
    }
  }

  public byte[] toUtf8() {
    return toString().getBytes(StandardCharsets.UTF_8);
  }
}
