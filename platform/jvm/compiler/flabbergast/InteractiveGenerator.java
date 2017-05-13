package flabbergast;

import java.util.function.BiConsumer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

class InteractiveGenerator {
  private final ClassVisitor classVisitor;
  private final GeneratorAdapter methodGen;

  InteractiveGenerator(String className, BiConsumer<String, byte[]> consumer) {
    classVisitor = WritingClassVisitor.create(consumer);
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        className,
        null,
        Type.getInternalName(InteractiveCommand.class),
        null);

    MethodVisitor ctor =
        classVisitor.visitMethod(
            Opcodes.ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), null, null);
    ctor.visitCode();
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        Type.getInternalName(Object.class),
        "<init>",
        Type.getMethodDescriptor(Type.VOID_TYPE),
        false);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    methodGen =
        new GeneratorAdapter(
            Opcodes.ACC_PUBLIC,
            new Method("launch", Type.VOID_TYPE, new Type[] {Type.getType(InteractiveState.class)}),
            null,
            null,
            classVisitor);
    methodGen.visitCode();
  }

  public void finish() {
    methodGen.visitInsn(Opcodes.RETURN);
    methodGen.visitMaxs(0, 0);
    methodGen.visitEnd();
    classVisitor.visitEnd();
  }

  public void go(String definition) {}

  public void help() {
    methodGen.loadArg(1);
    methodGen.invokeVirtual(
        Type.getType(InteractiveState.class), new Method("help", Type.VOID_TYPE, null));
  }

  public void home() {
    methodGen.loadArg(1);
    methodGen.invokeVirtual(
        Type.getType(InteractiveState.class), new Method("home", Type.VOID_TYPE, null));
  }

  public void ls() {
    methodGen.loadArg(1);
    methodGen.invokeVirtual(
        Type.getType(InteractiveState.class), new Method("ls", Type.VOID_TYPE, null));
  }

  private void makeLambda(Type definition) {
    Type[] constructorTypes =
        new Type[] {
          Type.getType(TaskMaster.class),
          Type.getType(SourceReference.class),
          Type.getType(Context.class),
          Type.getType(Frame.class)
        };
    String targetDescriptor =
        Type.getMethodDescriptor(Type.getType(Future.class), constructorTypes);
    methodGen.invokeDynamic(
        "invoke",
        Type.getMethodDescriptor(Type.getType(Definition.class)),
        BaseKwsGenerator.LAMBDA_METAFACTORY_BSM,
        targetDescriptor,
        new Handle(
            Opcodes.H_NEWINVOKESPECIAL,
            definition.getInternalName(),
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, constructorTypes)),
        targetDescriptor);
  }

  public void quit() {
    methodGen.loadArg(1);
    methodGen.invokeVirtual(
        Type.getType(InteractiveState.class), new Method("quit", Type.VOID_TYPE, null));
  }

  public void show(Type definition) {
    methodGen.loadArg(1);
    makeLambda(definition);
    methodGen.invokeVirtual(
        Type.getType(InteractiveState.class), new Method("trace", Type.VOID_TYPE, null));
  }

  public void trace() {
    methodGen.loadArg(1);
    methodGen.invokeVirtual(
        Type.getType(InteractiveState.class), new Method("trace", Type.VOID_TYPE, null));
  }

  public void up(int count) {
    methodGen.loadArg(1);
    methodGen.push(count);
    methodGen.invokeVirtual(
        Type.getType(InteractiveState.class),
        new Method("up", Type.VOID_TYPE, new Type[] {Type.INT_TYPE}));
  }
}
