package flabbergast;

import java.util.Arrays;
import java.util.stream.Stream;

/** A Flabbergast Template, holding functions for computing attributes. */
public class Template extends DefinitionBuilder {

  public static Definition instantiate(
      Frame.RuntimeBuilder[] builders,
      String filename,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn,
      String... names) {
    return (taskMaster, sourceReference, context, self) ->
        new AssistedFuture(taskMaster, sourceReference, context) {
          private Template template;

          @Override
          protected void resolve() {
            complete(
                Any.of(
                    Frame.create(
                        taskMaster,
                        new JunctionReference(
                            "instantiate template",
                            filename,
                            startLine,
                            startColumn,
                            endLine,
                            endColumn,
                            sourceReference,
                            template.getSourceReference()),
                        context.append(template.getContext()),
                        self,
                        Stream.concat(Stream.of(template), Arrays.stream(builders)))));
          }

          @Override
          protected void setup() {
            find(asTemplate(false), t -> template = t, names);
          }
        };
  }

  private final Frame container;

  private final Context context;

  private final SourceReference sourceReference;

  public Template(
      SourceReference sourceReference,
      Context context,
      Frame container,
      DefinitionBuilder... builders) {
    super(Arrays.stream(builders));
    this.sourceReference = sourceReference;
    this.context = context;
    this.container = container;
  }

  public Frame getContainer() {
    return container;
  }

  /** The context in which this template was created. */
  public Context getContext() {
    return context;
  }

  /** The stack trace at the time of creation. */
  public SourceReference getSourceReference() {
    return sourceReference;
  }

  public SourceReference joinSourceReference(
      String filename,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn,
      SourceReference caller) {
    return new JunctionReference(
        "amend template",
        filename,
        startLine,
        startColumn,
        endLine,
        endColumn,
        caller,
        getSourceReference());
  }
}
