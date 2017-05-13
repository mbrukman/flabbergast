package flabbergast;

import java.util.function.Supplier;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

public final class KwsDefinitionGenerator extends BaseKwsGenerator {

  private LoadableValue<Context> context;

  private final LoadableValue<Definition> instance;
  private LoadableValue<Frame> self;
  private String stackName;

  public KwsDefinitionGenerator(
      Supplier<ClassVisitor> classMaker, String name, String stackName, SourceLocation start) {
    super(
        classMaker,
        name,
        start,
        Stream.of(SourceReference.class, Context.class, Frame.class).map(Type::getType));
    this.stackName = stackName;
    instance = getSelfReference(Definition.class);
    makeConstructor();
  }

  public LoadableValue<Definition> asLoadable() {
    return instance;
  }

  public LoadableValue<Context> getContext() {
    return context;
  }

  public LoadableValue<Frame> getSelf() {
    return self;
  }

  @Override
  protected void setupConstructor(GeneratorAdapter ctor) {
    ConstructorValue<SourceReference> caller =
        new ConstructorValue<>("callerSourceReference", SourceReference.class);
    ctor.loadThis();
    ctor.loadArg(2);
    caller.store(ctor);
    pushSourceReference("callerSourceReference");
    if (stackName != null) {
      pushSourceReferenceFromLocation(ctor, stackName);
    }
    ConstructorValue<Context> context = new ConstructorValue<>("context", Context.class);
    ctor.loadThis();
    ctor.loadArg(3);
    caller.store(ctor);
    this.context = context;
    ConstructorValue<Frame> self = new ConstructorValue<>("self", Frame.class);
    ctor.loadThis();
    ctor.loadArg(4);
    caller.store(ctor);
    this.self = self;
  }
}
