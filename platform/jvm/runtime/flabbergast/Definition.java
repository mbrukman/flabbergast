package flabbergast;

public interface Definition {
  Future invoke(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self);
}
