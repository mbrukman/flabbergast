package flabbergast;

import java.util.function.Supplier;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;

public final class KwsRootGenerator extends BaseKwsGenerator {

  public KwsRootGenerator(Supplier<ClassVisitor> classMaker, String name, SourceLocation start) {
    super(classMaker, name, start, Stream.empty());
  }

  @Override
  protected void setupConstructor(GeneratorAdapter ctor) {

    pushSourceReferenceFromLocation(ctor, "file");
  }
}
