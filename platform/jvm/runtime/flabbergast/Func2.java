package flabbergast;

interface Func2<T1, T2, R> {
  R invoke(T1 arg1, T2 arg2) throws Exception;
}
