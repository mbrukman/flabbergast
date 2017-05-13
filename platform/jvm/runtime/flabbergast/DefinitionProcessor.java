package flabbergast;

interface DefinitionProcessor {

  DefinitionProcessor override(DefinitionProcessor definitionProcessor);

  Future override(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Future original);

  Definition toValue();
}
