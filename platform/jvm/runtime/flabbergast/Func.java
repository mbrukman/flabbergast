package flabbergast;

interface Func<T, R> {
  R invoke(T arg) throws Exception;
}
