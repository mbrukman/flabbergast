package flabbergast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * A frame/template builder that collects unfinished computations
 *
 * <p>Once placed in a frame, they will be computed in the current context.
 */
public class DefinitionBuilder implements Frame.RuntimeBuilder, DefinitionSource, Iterable<String> {
  private final Map<String, DefinitionProcessor> attributes = new HashMap<>();

  public DefinitionBuilder() {}

  protected DefinitionBuilder(Stream<DefinitionSource> builders) {
    builders
        .flatMap(DefinitionSource::stream)
        .forEach(
            entry -> {
              DefinitionProcessor processor =
                  entry.getValue().override(attributes.get(entry.getKey()));
              if (processor == null) {
                attributes.remove(entry.getKey());
              } else {
                attributes.put(entry.getKey(), processor);
              }
            });
  }

  @Override
  public Frame.Builder attach(TaskMaster taskMaster) {
    return (sourceReference, context, self, attributes) -> {
      for (final Entry<String, DefinitionProcessor> entry :
          DefinitionBuilder.this.attributes.entrySet()) {
        Future future =
            entry
                .getValue()
                .override(
                    taskMaster, sourceReference, context, self, attributes.get(entry.getKey()));
        if (future == null) {
          attributes.remove(entry.getKey());
        } else {
          attributes.put(entry.getKey(), future);
        }
      }
    };
  }

  public final void drop(String name) {
    attributes.put(
        name,
        new DefinitionProcessor() {

          @Override
          public DefinitionProcessor override(DefinitionProcessor definitionProcessor) {
            return null;
          }

          @Override
          public Future override(
              TaskMaster taskMaster,
              SourceReference sourceReference,
              Context context,
              Frame self,
              Future original) {
            return null;
          }

          @Override
          public Definition toValue() {
            return FailureFuture.create(
                String.format("Attempt to override non-existent attribute “%s”.", name));
          }
        });
  }

  public final void drop(Stringish name) {
    drop(name.toString());
  }

  @Override
  public final Iterator<String> iterator() {
    return attributes.keySet().iterator();
  }

  public final void require(String name) {
    attributes.put(
        name,
        new DefinitionHolder(
            FailureFuture.create(String.format("Attribute “%s” must be overridden.", name))));
  }

  public final void require(Stringish name) {
    require(name.toString());
  }

  public final void set(String name, Definition value) {
    attributes.put(name, new DefinitionHolder(value));
  }

  public final void set(String name, OverrideDefinition override) {
    attributes.put(
        name,
        new DefinitionProcessor() {

          @Override
          public DefinitionProcessor override(DefinitionProcessor definitionProcessor) {
            if (definitionProcessor == null) {
              return this;
            }
            final Definition original = definitionProcessor.toValue();
            return new DefinitionHolder(
                (taskMaster, sourceReference, context, self) ->
                    override.invoke(
                        taskMaster,
                        sourceReference,
                        context,
                        self,
                        original.invoke(taskMaster, sourceReference, context, self)));
          }

          @Override
          public Future override(
              TaskMaster taskMaster,
              SourceReference sourceReference,
              Context context,
              Frame self,
              Future original) {
            return override.invoke(taskMaster, sourceReference, context, self, original);
          }

          @Override
          public Definition toValue() {
            return FailureFuture.create(
                String.format("Attempt to override non-existent attribute “%s”.", name));
          }
        });
  }

  public final void set(Stringish name, Definition value) {
    set(name.toString(), value);
  }

  public final void set(Stringish name, OverrideDefinition override) {
    set(name.toString(), override);
  }

  @Override
  public Stream<Entry<String, DefinitionProcessor>> stream() {
    return attributes.entrySet().stream();
  }
}
