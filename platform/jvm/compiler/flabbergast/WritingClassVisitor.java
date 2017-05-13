package flabbergast;

import java.util.function.BiConsumer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class WritingClassVisitor extends ClassVisitor {
  public static ClassVisitor create(BiConsumer<String, byte[]> consumer) {
    return new WritingClassVisitor(
        new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS), consumer);
  }

  private String className;
  private final BiConsumer<String, byte[]> consumer;

  private ClassWriter writer;

  private WritingClassVisitor(ClassWriter writer, BiConsumer<String, byte[]> consumer) {
    super(Opcodes.ASM5, writer);
    this.writer = writer;
    this.consumer = consumer;
  }

  @Override
  public void visit(
      int version,
      int access,
      String className,
      String signature,
      String super_name,
      String[] interfaces) {
    this.className = className;
    super.visit(version, access, className, signature, super_name, interfaces);
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
    consumer.accept(className, writer.toByteArray());
  }
}
