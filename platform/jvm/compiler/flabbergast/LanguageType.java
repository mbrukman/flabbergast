package flabbergast;

import org.objectweb.asm.Type;

public final class LanguageType {
  public static final Type ANY_TYPE = Type.getType(Any.class);
  public static final Type BIN_TYPE = Type.getType(byte[].class);
  public static final Type CONSUME_FRAME_TYPE = Type.getType(Fricassee.ConsumeFrame.class);
  public static final Type CONSUME_RESULT_TYPE = Type.getType(ConsumeResult.class);
  public static final Type CONTEXT_TYPE = Type.getType(Context.class);
  public static final Type DEFINITION_BUILDER_TYPE = Type.getType(DefinitionBuilder.class);
  public static final Type DEFINITION_TYPE = Type.getType(Definition.class);
  public static final Type FRAME_TYPE = Type.getType(Frame.class);
  public static final Type FRICASSEE_MERGE_TYPE = Type.getType(Fricassee.Merge.class);
  public static final Type FRICASSEE_TYPE = Type.getType(Fricassee.class);
  public static final Type FUTURE_TYPE = Type.getType(Future.class);
  public static final Type JDOUBLE_TYPE = Type.getType(Double.class);
  public static final Type JSTRING_TYPE = Type.getType(String.class);
  public static final Type LOOKUP_HANDLER_TYPE = Type.getType(LookupHandler.class);
  public static final Type NAME_SOURCE_TYPE = Type.getType(NameSource.class);
  public static final Type OVERRIDE_DEFINITION_TYPE = Type.getType(OverrideDefinition.class);
  public static final Type RUNNABLE_TYPE = Type.getType(Runnable.class);
  public static final Type SOURCE_REFERENCE_TYPE = Type.getType(SourceReference.class);
  public static final Type STR_TYPE = Type.getType(Stringish.class);
  public static final Type TASK_MASTER_TYPE = Type.getType(TaskMaster.class);
  public static final Type TMPL_TYPE = Type.getType(Template.class);
  public static final Type VALUE_BUILDER_TYPE = Type.getType(ValueBuilder.class);

  private LanguageType() {}
}
