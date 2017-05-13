package flabbergast;

public interface LookupHandler {
  String description();

  Future lookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names);
}
