package flabbergast;

public interface OverrideDefinition {
  Future invoke(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Future original);
}
