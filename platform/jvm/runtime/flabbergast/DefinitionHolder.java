package flabbergast;

final class DefinitionHolder implements DefinitionProcessor {
  private final Definition value;

  DefinitionHolder(Definition value) {
    this.value = value;
  }

  @Override
  public DefinitionProcessor override(DefinitionProcessor definitionProcessor) {
    return this;
  }

  @Override
  public Future override(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Future future) {
    return value.invoke(taskMaster, sourceReference, context, self);
  }

  @Override
  public Definition toValue() {
    return value;
  }
}
