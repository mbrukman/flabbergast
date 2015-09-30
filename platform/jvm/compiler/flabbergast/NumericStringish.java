package flabbergast;

import static org.objectweb.asm.Type.getInternalName;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class NumericStringish extends LoadableValue {
    private final LoadableValue source;

    public NumericStringish(LoadableValue source) {
        this.source = source;
    }

    @Override
    public Class<?> getBackingType() {
        return Stringish.class;
    }

    @Override
    public void load(MethodVisitor generator) throws Exception {
        generator.visitTypeInsn(Opcodes.NEW,
                                getInternalName(SimpleStringish.class));
        generator.visitInsn(Opcodes.DUP);
        source.load(generator);
        generator
        .visitMethodInsn(Opcodes.INVOKESTATIC,
                         getInternalName(Generator.getBoxedType(source
                                         .getBackingType())), "toString", Generator
                         .makeSignature(String.class,
                                        source.getBackingType()));

        generator.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                  getInternalName(SimpleStringish.class), "<init>",
                                  Generator.makeSignature(null, String.class));
    }
}
