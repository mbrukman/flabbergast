package flabbergast;

import java.util.stream.Stream;

abstract class RevCons<T> {

  public static <T> RevCons<T> cons(T head, RevCons<T> tail) {
    return new RevCons<T>() {

      @Override
      protected void build(Stream.Builder<T> builder) {
        tail.build(builder);
        builder.accept(head);
      }

      @Override
      public T head() {
        return head;
      }

      @Override
      public RevCons<T> pop() {
        return tail;
      }
    };
  }

  public static <T> RevCons<T> empty() {
    return new RevCons<T>() {
      @Override
      protected void build(Stream.Builder<T> builder) {}

      @Override
      public T head() {
        return null;
      }

      @Override
      public RevCons<T> pop() {
        return this;
      }
    };
  }

  public static String toString(RevCons<Integer> list) {
    int[] codepoints = list.stream().mapToInt(Integer::intValue).toArray();
    return new String(codepoints, 0, codepoints.length);
  }

  protected abstract void build(Stream.Builder<T> builder);

  public abstract T head();

  public abstract RevCons<T> pop();

  Stream<T> stream() {
    Stream.Builder<T> builder = Stream.builder();
    build(builder);
    return builder.build();
  }
}
