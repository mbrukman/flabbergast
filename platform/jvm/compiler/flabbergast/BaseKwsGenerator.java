package flabbergast;

import static flabbergast.LanguageType.ANY_TYPE;
import static flabbergast.LanguageType.BIN_TYPE;
import static flabbergast.LanguageType.CONSUME_FRAME_TYPE;
import static flabbergast.LanguageType.CONSUME_RESULT_TYPE;
import static flabbergast.LanguageType.CONTEXT_TYPE;
import static flabbergast.LanguageType.DEFINITION_BUILDER_TYPE;
import static flabbergast.LanguageType.DEFINITION_TYPE;
import static flabbergast.LanguageType.FRAME_TYPE;
import static flabbergast.LanguageType.FRICASSEE_MERGE_TYPE;
import static flabbergast.LanguageType.FRICASSEE_TYPE;
import static flabbergast.LanguageType.FUTURE_TYPE;
import static flabbergast.LanguageType.JDOUBLE_TYPE;
import static flabbergast.LanguageType.JSTRING_TYPE;
import static flabbergast.LanguageType.LOOKUP_HANDLER_TYPE;
import static flabbergast.LanguageType.NAME_SOURCE_TYPE;
import static flabbergast.LanguageType.OVERRIDE_DEFINITION_TYPE;
import static flabbergast.LanguageType.RUNNABLE_TYPE;
import static flabbergast.LanguageType.SOURCE_REFERENCE_TYPE;
import static flabbergast.LanguageType.STR_TYPE;
import static flabbergast.LanguageType.TASK_MASTER_TYPE;
import static flabbergast.LanguageType.TMPL_TYPE;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.DOUBLE_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.LONG_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import flabbergast.Frame.RuntimeBuilder;
import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public abstract class BaseKwsGenerator {

  public abstract class Block {
    private abstract class ConstantValue<T> extends LoadableValue<T> {

      public ConstantValue(Type type) {
        super(type);
      }

      @Override
      public void addTo(Set<LoadableValue<?>> liveValues) {}

      @Override
      public void hoist() {}
    }

    private final class ExistingFieldValue<T> extends LoadableValue<T> {
      private final String name;
      private final Type ownerType;

      private ExistingFieldValue(Class<?> owner, String name, Class<T> fieldType) {
        super(fieldType);
        ownerType = Type.getType(owner);
        this.name = name;
      }

      @Override
      public void addTo(Set<LoadableValue<?>> liveValues) {}

      @Override
      public void hoist() {}

      @Override
      public void load(Block block) {
        block.methodGen.getStatic(ownerType, name, getType());
      }
    }

    private abstract class InstructionWithCallbackResult<T, C> extends LoadableValue<T>
        implements InstructionGroup {
      private final String acceptMethod;
      private final MethodVisitor acceptor;
      private final LoadableValue<?>[] args;
      private final int blockId = blockCount++;
      private final Class<C> consumerClazz;
      private final String continueMethod;

      private boolean hoisted;
      private final String resultField;

      InstructionWithCallbackResult(
          Class<T> returnType, Class<C> consumerClazz, LoadableValue<?>... args) {
        super(returnType);
        this.consumerClazz = consumerClazz;
        this.args = args;
        int id = callbackId++;
        acceptMethod = String.format("accept%d_%d", blockId, id);
        continueMethod = String.format("continue%d_%d", blockId, id);
        resultField = String.format("result%d_%d", blockId, id);
        acceptor =
            classVisitor.visitMethod(
                Opcodes.ACC_SYNTHETIC,
                acceptMethod,
                Type.getMethodDescriptor(VOID_TYPE, getType()),
                null,
                null);
      }

      @Override
      public final void addTo(Set<LoadableValue<?>> liveValues) {}

      protected final void continueNext() {
        acceptor.visitCode();
        if (hoisted) {
          acceptor.visitVarInsn(Opcodes.ALOAD, 0);
          acceptor.visitVarInsn(getType().getOpcode(Opcodes.ILOAD), 1);
          acceptor.visitFieldInsn(
              Opcodes.PUTFIELD, selfType.getInternalName(), resultField, getType().getDescriptor());
        }
        acceptor.visitVarInsn(Opcodes.ALOAD, 0);
        acceptor.visitInsn(Opcodes.DUP);
        if (!hoisted) {
          acceptor.visitVarInsn(getType().getOpcode(Opcodes.ILOAD), 1);
        }
        acceptor.visitInvokeDynamicInsn(
            "run",
            Type.getMethodDescriptor(RUNNABLE_TYPE, selfType, getType()),
            LAMBDA_METAFACTORY_BSM,
            "()V",
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                selfType.getInternalName(),
                continueMethod,
                hoisted ? "()V" : Type.getMethodDescriptor(VOID_TYPE, getType())),
            "()V");
        acceptor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            selfType.getInternalName(),
            "next",
            Type.getMethodDescriptor(VOID_TYPE, RUNNABLE_TYPE),
            false);
        acceptor.visitInsn(Opcodes.RETURN);
        acceptor.visitMaxs(0, 0);
        acceptor.visitEnd();
        methodGen.visitInsn(Opcodes.RETURN);
        methodGen.visitMaxs(0, 0);
        methodGen.visitEnd();

        methodGen =
            new GeneratorAdapter(
                Opcodes.ACC_SYNTHETIC,
                new Method(continueMethod, VOID_TYPE, hoisted ? null : new Type[] {getType()}),
                null,
                null,
                classVisitor);
        methodGen.visitCode();
      }

      @Override
      public final void hoist() {
        if (!hoisted) {
          hoisted = true;
          classVisitor
              .visitField(Opcodes.ACC_PRIVATE, resultField, getType().getDescriptor(), null, null)
              .visitEnd();
        }
      }

      @Override
      public final void load(Block block) {
        if (hoisted) {
          block.methodGen.loadThis();
          block.methodGen.getField(selfType, resultField, getType());
        } else {
          methodGen.loadArg(1);
        }
      }

      protected final void pushConsumer() {
        String targetDescriptor = Type.getMethodDescriptor(VOID_TYPE, Type.getType(consumerClazz));
        methodGen.loadThis();
        methodGen.invokeDynamic(
            "consume",
            Type.getMethodDescriptor(Type.getType(consumerClazz), selfType),
            LAMBDA_METAFACTORY_BSM,
            targetDescriptor,
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                selfType.getInternalName(),
                acceptMethod,
                targetDescriptor),
            targetDescriptor);
      }

      @Override
      public void update(Set<LoadableValue<?>> liveValues) {
        liveValues.remove(this);
        liveValues.forEach(LoadableValue::hoist);
        Arrays.stream(args).forEach(arg -> arg.addTo(liveValues));
      }
    }

    private abstract class InstructionWithResult<T> extends LoadableValue<T>
        implements InstructionGroup {
      private final LoadableValue<?>[] args;
      private boolean hoisted;
      private int slot = -1;
      private final SourceLocation sourceLocation = sourceLocations.peek();

      public InstructionWithResult(Class<T> clazz, LoadableValue<?>... args) {
        this(Type.getType(clazz), args);
      }

      InstructionWithResult(Type type, LoadableValue<?>... args) {
        super(type);
        this.args = args;
      }

      @Override
      public final void addTo(Set<LoadableValue<?>> liveValues) {
        liveValues.add(this);
      }

      @Override
      public final void hoist() {
        hoisted = true;
      }

      @Override
      public final void load(Block block) {
        if (hoisted) {
          block.methodGen.loadThis();
          block.methodGen.getField(selfType, "temp" + slot, getType());
        } else {
          if (block != Block.this) {
            throw new IllegalStateException(
                "Trying to use local variable outside of block where it is defined.");
          }
          methodGen.loadLocal(slot, getType());
        }
      }

      @Override
      public final void render() {
        if (hoisted) {
          slot = fieldCount++;
          classVisitor
              .visitField(Opcodes.ACC_PRIVATE, "temp" + slot, getType().getDescriptor(), null, null)
              .visitEnd();
          methodGen.loadThis();
          renderValue();
          methodGen.putField(selfType, "temp" + slot, getType());

        } else {
          slot = methodGen.newLocal(getType());
          renderValue();
          methodGen.storeLocal(slot, getType());
        }
      }

      protected final void renderSourceLocation() {
        sourceLocation.pushToStack(methodGen);
      }

      protected abstract void renderValue();

      @Override
      public final void update(Set<LoadableValue<?>> liveValues) {
        liveValues.remove(this);
        Arrays.stream(args).forEach(arg -> arg.addTo(liveValues));
      }
    }

    protected final class ParameterValue<T> extends LoadableValue<T> {
      private boolean hoisted = false;

      private final int slot;

      public ParameterValue(Class<T> clazz, int slot) {
        this(Type.getType(clazz), slot);
      }

      public ParameterValue(Type type, int slot) {
        super(type);
        this.slot = slot;
      }

      @Override
      public void addTo(Set<LoadableValue<?>> liveValues) {
        liveValues.add(this);
      }

      @Override
      public void hoist() {
        hoisted = true;
      }

      @Override
      public void load(Block block) {
        if (hoisted) {
          methodGen.loadThis();
          methodGen.getField(selfType, "param" + slot, getType());
        } else {
          methodGen.loadArg(slot);
        }
      }

      public void store() {
        if (hoisted) {
          classVisitor
              .visitField(
                  Opcodes.ACC_PRIVATE, "param" + slot, getType().getDescriptor(), null, null)
              .visitEnd();
          methodGen.loadThis();
          methodGen.loadArg(slot);
          methodGen.putField(selfType, "param" + slot, getType());
        }
      }
    }

    final int blockId = blockCount++;

    private int callbackId;

    private List<InstructionGroup> instructions = new ArrayList<>();

    private GeneratorAdapter methodGen;

    protected Block(Type... parameters) {
      methodGen =
          new GeneratorAdapter(
              Opcodes.ACC_SYNTHETIC,
              new Method("block" + blockId, VOID_TYPE, parameters),
              null,
              null,
              classVisitor);
    }

    public void anyDispatch(Dispatch dispatch, LoadableValue<Any> value) {
      Type acceptorType = dispatch.generate(blockId);
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              value.load(Block.this);
              methodGen.newInstance(acceptorType);
              methodGen.dup();
              methodGen.loadThis();
              methodGen.visitMethodInsn(
                  Opcodes.INVOKESPECIAL,
                  acceptorType.getInternalName(),
                  "<init>",
                  Type.getMethodDescriptor(VOID_TYPE, selfType),
                  false);
              ANY__ACCEPT.invoke(methodGen);
              methodGen.visitInsn(Opcodes.RETURN);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              value.addTo(liveValues);
            }
          });
      writeMethod();
    }

    public LoadableValue<Any> anyOfBin(LoadableValue<byte[]> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_BIN.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfBool(LoadableValue<Boolean> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_BOOL.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfFloat(LoadableValue<Double> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_FLOAT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfFrame(LoadableValue<Frame> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_FRAME.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfInt(LoadableValue<Long> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_INT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfLookupHandler(LoadableValue<LookupHandler> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_LOOKUP_HANDLER.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfNull() {
      return append(
          new InstructionWithResult<Any>(Any.class) {

            @Override
            protected void renderValue() {
              ANY__EMPTY.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfStr(LoadableValue<Stringish> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_STR.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> anyOfTemplate(LoadableValue<Template> value) {
      return append(
          new InstructionWithResult<Any>(Any.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              ANY__OF_TEMPLATE.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Stringish> anyStr(LoadableValue<Any> value) {
      return append(
          new InstructionWithCallbackResult<Stringish, Any.StringConsumer>(
              Stringish.class, Any.StringConsumer.class) {
            @Override
            public void render() {
              value.load(Block.this);
              loadTaskMaster();
              pushSourceReferences(methodGen);
              pushConsumer();
              ANY__TO_STR.invoke(methodGen);
              continueNext();
            }
          });
    }

    private <T, X extends LoadableValue<T> & InstructionGroup> LoadableValue<T> append(X arg) {
      instructions.add(arg);
      return arg;
    }

    public LoadableValue<Long> binaryLength(LoadableValue<byte[]> value) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              methodGen.arrayLength();
              methodGen.cast(INT_TYPE, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> boolCompare(
        LoadableValue<Boolean> left, LoadableValue<Boolean> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {

            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.SUB, INT_TYPE);
              methodGen.cast(INT_TYPE, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Boolean> boolFalse() {
      return new ConstantValue<Boolean>(BOOLEAN_TYPE) {

        @Override
        public void load(Block block) {
          block.methodGen.push(false);
        }
      };
    }

    public LoadableValue<Boolean> boolNot(LoadableValue<Boolean> value) {
      return append(
          new InstructionWithResult<Boolean>(BOOLEAN_TYPE, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              methodGen.not();
            }
          });
    }

    public LoadableValue<Stringish> boolStr(LoadableValue<Boolean> value) {
      return append(
          new InstructionWithResult<Stringish>(Stringish.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              STR__FROM_BOOL.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Boolean> boolTrue() {
      return new ConstantValue<Boolean>(BOOLEAN_TYPE) {

        @Override
        public void load(Block block) {
          block.methodGen.push(true);
        }
      };
    }

    public void branch(Block0 target) {
      branch(target);
    }

    public <T> void branch(Block1<T> target, LoadableValue<T> parameter) {
      writeBranch(target, parameter);
    }

    public <T, U> void branch(
        Block2<T, U> target, LoadableValue<T> parameter1, LoadableValue<U> parameter2) {
      writeBranch(target, parameter1, parameter2);
    }

    public <T, U, V> void branch(
        Block3<T, U, V> target,
        LoadableValue<T> parameter1,
        LoadableValue<U> parameter2,
        LoadableValue<V> parameter3) {
      writeBranch(target, parameter1, parameter2, parameter3);
    }

    public <T, U, V, W> void branch(
        Block4<T, U, V, W> target,
        LoadableValue<T> parameter1,
        LoadableValue<U> parameter2,
        LoadableValue<V> parameter3,
        LoadableValue<W> parameter4) {
      writeBranch(target, parameter1, parameter2, parameter3, parameter4);
    }

    public void conditional(Block0 whenTrue, Block0 whenFalse, LoadableValue<Boolean> cond) {
      instructions.add(
          new InstructionGroup() {
            @Override
            public void render() {
              methodGen.loadThis();
              cond.load(Block.this);
              Label falseBranch = methodGen.newLabel();
              Label end = methodGen.newLabel();
              methodGen.ifZCmp(GeneratorAdapter.EQ, falseBranch);
              methodGen.invokeDynamic(
                  "run",
                  Type.getMethodDescriptor(RUNNABLE_TYPE, selfType),
                  LAMBDA_METAFACTORY_BSM,
                  "()V",
                  new Handle(
                      Opcodes.H_INVOKEVIRTUAL,
                      selfType.getInternalName(),
                      "block" + whenTrue.blockId,
                      Type.getMethodDescriptor(VOID_TYPE)),
                  "()V");

              methodGen.goTo(end);
              methodGen.mark(falseBranch);
              methodGen.invokeDynamic(
                  "run",
                  Type.getMethodDescriptor(RUNNABLE_TYPE, selfType),
                  LAMBDA_METAFACTORY_BSM,
                  "()V",
                  new Handle(
                      Opcodes.H_INVOKEVIRTUAL,
                      selfType.getInternalName(),
                      "block" + whenFalse.blockId,
                      Type.getMethodDescriptor(VOID_TYPE)),
                  "()V");
              methodGen.mark(end);

              methodGen.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  selfType.getInternalName(),
                  "next",
                  Type.getMethodDescriptor(VOID_TYPE, RUNNABLE_TYPE),
                  false);
              methodGen.visitInsn(Opcodes.RETURN);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              cond.addTo(liveValues);
            }
          });
      writeMethod();
    }

    public LoadableValue<Context> contextAppend(
        LoadableValue<Context> first, LoadableValue<Context> second) {
      return append(
          new InstructionWithResult<Context>(Context.class, first, second) {

            @Override
            protected void renderValue() {
              first.load(Block.this);
              second.load(Block.this);
              CONTEXT__APPEND.invoke(methodGen);
              ANY__OF_TEMPLATE.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Context> contextEmpty() {
      return new ExistingFieldValue<>(Context.class, "EMPTY", Context.class);
    }

    public LoadableValue<Context> contextPrepend(
        LoadableValue<Frame> head, LoadableValue<Context> tail) {
      return append(
          new InstructionWithResult<Context>(Context.class, head, tail) {

            @Override
            protected void renderValue() {
              tail.load(Block.this);
              head.load(Block.this);
              CONTEXT__PREPEND.invoke(methodGen);
            }
          });
    }

    public Dispatch createDispatch() {
      return new Dispatch();
    }

    public LoadableValue<DefinitionBuilder> definitionBuilderCreate() {
      return append(
          new InstructionWithResult<DefinitionBuilder>(DefinitionBuilder.class) {

            @Override
            protected void renderValue() {
              methodGen.newInstance(getType());
              methodGen.dup();
              methodGen.invokeConstructor(getType(), Method.getMethod("void <init> ()"));
            }
          });
    }

    public void definitionBuilderDrop(
        LoadableValue<DefinitionBuilder> builder, LoadableValue<Stringish> name) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              builder.load(Block.this);
              name.load(Block.this);
              DEFINITION_BUILDER__DROP.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              builder.addTo(liveValues);
              name.addTo(liveValues);
            }
          });
    }

    public void definitionBuilderRequire(
        LoadableValue<DefinitionBuilder> builder, LoadableValue<Stringish> name) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              builder.load(Block.this);
              name.load(Block.this);
              DEFINITION_BUILDER__REQUIRE.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              builder.addTo(liveValues);
              name.addTo(liveValues);
            }
          });
    }

    public void definitionBuilderSetDefinition(
        LoadableValue<DefinitionBuilder> builder,
        LoadableValue<Stringish> name,
        LoadableValue<Definition> definition) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              builder.load(Block.this);
              name.load(Block.this);
              definition.load(Block.this);
              DEFINITION_BUILDER__SET_DEFINITION.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              builder.addTo(liveValues);
              name.addTo(liveValues);
              definition.addTo(liveValues);
            }
          });
    }

    public void definitionBuilderSetOverride(
        LoadableValue<DefinitionBuilder> builder,
        LoadableValue<Stringish> name,
        LoadableValue<OverrideDefinition> override) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              builder.load(Block.this);
              name.load(Block.this);
              override.load(Block.this);
              DEFINITION_BUILDER__SET_DEFINITION_OVERRIDE.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              builder.addTo(liveValues);
              name.addTo(liveValues);
              override.addTo(liveValues);
            }
          });
    }

    public void error(LoadableValue<Stringish> value) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              loadTaskMaster();
              pushSourceReferences(methodGen);
              value.load(Block.this);
              OBJECT__TO_STRING.invoke(methodGen);
              TASK_MASTER__REPORT_OTHER_ERROR.invoke(methodGen);
              methodGen.visitInsn(Opcodes.RETURN);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              value.addTo(liveValues);
            }
          });
      writeMethod();
    }

    public LoadableValue<Any> external(String uri) {
      return append(
          new InstructionWithCallbackResult<Any, ConsumeResult>(Any.class, ConsumeResult.class) {
            @Override
            public void render() {
              loadTaskMaster();
              methodGen.push(uri);
              pushConsumer();
              TASK_MASTER__LOAD_EXTERNAL.invoke(methodGen);
              continueNext();
            }
          });
    }

    public LoadableValue<Double> floatAdd(LoadableValue<Double> left, LoadableValue<Double> right) {
      return append(
          new InstructionWithResult<Double>(DOUBLE_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.ADD, DOUBLE_TYPE);
            }
          });
    }

    public LoadableValue<Long> floatCompare(
        LoadableValue<Double> left, LoadableValue<Double> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              DOUBLE__COMPARE.invoke(methodGen);
              methodGen.cast(INT_TYPE, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Double> floatConst(double value) {
      return new ConstantValue<Double>(DOUBLE_TYPE) {

        @Override
        public void load(Block block) {
          block.methodGen.push(value);
        }
      };
    }

    public LoadableValue<Double> floatDivide(
        LoadableValue<Double> left, LoadableValue<Double> right) {
      return append(
          new InstructionWithResult<Double>(DOUBLE_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.DIV, DOUBLE_TYPE);
            }
          });
    }

    public LoadableValue<Double> floatInfinity() {
      return new ExistingFieldValue<>(Double.class, "POSITIVE_INFINITY", double.class);
    }

    public LoadableValue<Boolean> floatIsFinite(LoadableValue<Double> value) {
      return append(
          new InstructionWithResult<Boolean>(BOOLEAN_TYPE, value) {
            @Override
            protected void renderValue() {

              value.load(Block.this);
              DOUBLE__IS_FINITE.invoke(methodGen);
              methodGen.not();
            }
          });
    }

    public LoadableValue<Boolean> floatIsNan(LoadableValue<Double> value) {
      return append(
          new InstructionWithResult<Boolean>(BOOLEAN_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              DOUBLE__IS_NAN.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Double> floatMax() {
      return new ExistingFieldValue<>(Double.class, "MAX_VALUE", double.class);
    }

    public LoadableValue<Double> floatMin() {
      return new ExistingFieldValue<>(Double.class, "MIN_VALUE", double.class);
    }

    public LoadableValue<Double> floatMultiply(
        LoadableValue<Double> left, LoadableValue<Double> right) {
      return append(
          new InstructionWithResult<Double>(DOUBLE_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.MUL, DOUBLE_TYPE);
            }
          });
    }

    public LoadableValue<Double> floatNan() {
      return new ExistingFieldValue<>(Double.class, "NaN", double.class);
    }

    public LoadableValue<Double> floatNegate(LoadableValue<Double> value) {
      return append(
          new InstructionWithResult<Double>(DOUBLE_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              value.load(Block.this);
              methodGen.math(GeneratorAdapter.NEG, DOUBLE_TYPE);
            }
          });
    }

    public LoadableValue<Stringish> floatStr(LoadableValue<Double> value) {
      return append(
          new InstructionWithResult<Stringish>(Stringish.class, value) {

            @Override
            protected void renderValue() {
              value.load(Block.this);
              STR__FROM_FLOAT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Double> floatSub(LoadableValue<Double> left, LoadableValue<Double> right) {
      return append(
          new InstructionWithResult<Double>(DOUBLE_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.SUB, DOUBLE_TYPE);
            }
          });
    }

    public LoadableValue<Long> floatToInt(LoadableValue<Double> value) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              methodGen.cast(DOUBLE_TYPE, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Context> frameContext(LoadableValue<Frame> value) {
      return append(
          new InstructionWithResult<Context>(Context.class, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              FRAME__CONTEXT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Stringish> frameId(LoadableValue<Frame> value) {
      return append(
          new InstructionWithResult<Stringish>(Stringish.class, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              FRAME__ID.invoke(methodGen);
            }
          });
    }

    @SafeVarargs
    public final LoadableValue<Frame> frameNew(
        LoadableValue<Context> context, LoadableValue<? extends DefinitionBuilder>... builders) {
      return append(
          new InstructionWithResult<Frame>(
              Frame.class,
              Stream.concat(Arrays.stream(builders), Stream.of(context))
                  .toArray(LoadableValue[]::new)) {
            @Override
            protected void renderValue() {
              loadTaskMaster();
              pushSourceReferences(methodGen);
              context.load(Block.this);
              methodGen.push(builders.length);
              methodGen.newArray(Type.getType(RuntimeBuilder.class));
              for (int i = 0; i < builders.length; i++) {
                methodGen.dup();
                methodGen.push(i);
                builders[i].load(Block.this);
                methodGen.arrayStore(Type.getType(RuntimeBuilder.class));
              }

              FRAME__NEW.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Frame> frameThrough(
        LoadableValue<Long> start, LoadableValue<Long> end, LoadableValue<Context> context) {
      return append(
          new InstructionWithResult<Frame>(Frame.class, start, end, context) {
            @Override
            protected void renderValue() {
              loadTaskMaster();
              pushSourceReferences(methodGen);
              start.load(Block.this);
              end.load(Block.this);
              context.load(Block.this);
              FRAME__THROUGH.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeAcccumulate(
        LoadableValue<Definition> reducer,
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Stringish> name,
        LoadableValue<Any> initial) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source, name, initial, reducer) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              name.load(Block.this);
              initial.load(Block.this);
              reducer.load(Block.this);
              FRICASSEE__ACCUMULATE.invoke(methodGen);
            }
          });
    }

    @SafeVarargs
    public final LoadableValue<Fricassee> fricasseeConcat(
        LoadableValue<? extends Fricassee>... sources) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, sources) {
            @Override
            protected void renderValue() {
              methodGen.newArray(FRICASSEE_TYPE);
              for (int i = 0; i < sources.length; i++) {
                methodGen.dup();
                methodGen.push(i);
                sources[i].load(Block.this);
                methodGen.arrayStore(FRICASSEE_TYPE);
              }
              FRICASSEE__CONCAT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeForEach(LoadableValue<Frame> source) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              renderSourceLocation();
              FRICASSEE__FOR_EACH.invoke(methodGen);
            }
          });
    }

    LoadableValue<Fricassee> fricasseeLet(
        LoadableValue<? extends Fricassee> source, LoadableValue<DefinitionBuilder> builder) {

      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source, builder) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              builder.load(Block.this);
              FRICASSEE__LET.invoke(methodGen);
            }
          });
    }

    public void fricasseeMergeAdd(
        LoadableValue<Fricassee.Merge> merge,
        LoadableValue<Stringish> name,
        LoadableValue<Frame> frame) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              merge.load(Block.this);
              name.load(Block.this);
              frame.load(Block.this);
              FRICASSEE_MERGE__ADD.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              merge.addTo(liveValues);
              name.addTo(liveValues);
              frame.addTo(liveValues);
            }
          });
    }

    public void fricasseeMergeAddName(
        LoadableValue<Fricassee.Merge> merge, LoadableValue<Stringish> name) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              merge.load(Block.this);
              name.load(Block.this);
              FRICASSEE_MERGE__ADD_NAME.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              merge.addTo(liveValues);
              name.addTo(liveValues);
            }
          });
    }

    public void fricasseeMergeAddOrdinal(
        LoadableValue<Fricassee.Merge> merge, LoadableValue<Stringish> name) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              merge.load(Block.this);
              name.load(Block.this);
              FRICASSEE_MERGE__ADD_ORDINAL.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              merge.addTo(liveValues);
              name.addTo(liveValues);
            }
          });
    }

    public LoadableValue<Fricassee.Merge> fricasseeMergeCreate() {
      return append(
          new InstructionWithResult<Fricassee.Merge>(Fricassee.Merge.class) {
            @Override
            protected void renderValue() {
              methodGen.newInstance(Type.getType(Fricassee.Merge.class));
              methodGen.dup();
              renderSourceLocation();
              FRICASSEE_MERGE__CTOR.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeOrderBool(
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Boolean> ascending,
        LoadableValue<Definition> clause) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source, ascending, clause) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              ascending.load(Block.this);
              clause.load(Block.this);
              FRICASSEE__ORDER_BY_BOOL.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeOrderFloat(
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Boolean> ascending,
        LoadableValue<Definition> clause) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source, ascending, clause) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              ascending.load(Block.this);
              clause.load(Block.this);
              FRICASSEE__ORDER_BY_FLOAT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeOrderInt(
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Boolean> ascending,
        LoadableValue<Definition> clause) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source, ascending, clause) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              ascending.load(Block.this);
              clause.load(Block.this);
              FRICASSEE__ORDER_BY_INT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeOrderStr(
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Boolean> ascending,
        LoadableValue<Definition> clause) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source, ascending, clause) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              ascending.load(Block.this);
              clause.load(Block.this);
              FRICASSEE__ORDER_BY_STR.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Any> fricasseeReduce(
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Context> context,
        LoadableValue<Frame> self,
        LoadableValue<Stringish> name,
        LoadableValue<Any> initial,
        LoadableValue<Definition> reducer) {
      return append(
          new InstructionWithCallbackResult<Any, ConsumeResult>(
              Any.class, ConsumeResult.class, source, context, self, name, initial, reducer) {
            @Override
            public void render() {
              source.load(Block.this);
              loadTaskMaster();
              pushSourceReferences(methodGen);
              context.load(Block.this);
              self.load(Block.this);
              name.load(Block.this);
              initial.load(Block.this);
              reducer.load(Block.this);
              FRICASSEE__REDUCE.invoke(methodGen);
              pushConsumer();
              FUTURE__LISTEN.invoke(methodGen);
              continueNext();
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeReverse(LoadableValue<? extends Fricassee> source) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              FRICASSEE__REVERSE.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Frame> fricasseeToFrame(
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Context> context,
        LoadableValue<Frame> self,
        LoadableValue<Definition> computeName,
        LoadableValue<Definition> computeValue) {
      return append(
          new InstructionWithCallbackResult<Frame, Fricassee.ConsumeFrame>(
              Frame.class,
              Fricassee.ConsumeFrame.class,
              source,
              context,
              self,
              computeName,
              computeValue) {
            @Override
            public void render() {
              source.load(Block.this);
              loadTaskMaster();
              pushSourceReferences(methodGen);
              context.load(Block.this);
              self.load(Block.this);
              computeName.load(Block.this);
              computeValue.load(Block.this);
              pushConsumer();
              FRICASSEE__TO_FRAME.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Frame> fricasseeToList(
        LoadableValue<? extends Fricassee> source,
        LoadableValue<Context> context,
        LoadableValue<Frame> self,
        LoadableValue<Definition> computeValue) {
      return append(
          new InstructionWithCallbackResult<Frame, Fricassee.ConsumeFrame>(
              Frame.class, Fricassee.ConsumeFrame.class, source, context, self, computeValue) {
            @Override
            public void render() {
              source.load(Block.this);
              loadTaskMaster();
              pushSourceReferences(methodGen);
              context.load(Block.this);
              self.load(Block.this);
              computeValue.load(Block.this);
              pushConsumer();
              FRICASSEE__TO_LIST.invoke(methodGen);
              continueNext();
            }
          });
    }

    public LoadableValue<Fricassee> fricasseeWhere(
        LoadableValue<? extends Fricassee> source, LoadableValue<Definition> clause) {
      return append(
          new InstructionWithResult<Fricassee>(Fricassee.class, source, clause) {
            @Override
            protected void renderValue() {
              source.load(Block.this);
              clause.load(Block.this);
              FRICASSEE__WHERE.invoke(methodGen);
            }
          });
    }

    protected abstract Collection<ParameterValue<?>> getParameters();

    public LoadableValue<Long> intAdd(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.ADD, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intAnd(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.AND, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intCompare(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.visitInsn(Opcodes.LCMP);
              methodGen.cast(INT_TYPE, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intComplement(LoadableValue<Long> value) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              methodGen.push(-1L);
              methodGen.math(GeneratorAdapter.XOR, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intConst(long value) {
      return new ConstantValue<Long>(LONG_TYPE) {
        @Override
        public void load(Block block) {
          block.methodGen.push(value);
        }
      };
    }

    public LoadableValue<Long> intDivide(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.DIV, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intMax() {
      return new ExistingFieldValue<>(Long.class, "MAX_VALUE", long.class);
    }

    public LoadableValue<Long> intMin() {
      return new ExistingFieldValue<>(Long.class, "MIN_VALUE", long.class);
    }

    public LoadableValue<Long> intModulus(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.REM, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intMuliply(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.MUL, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intNegate(LoadableValue<Long> value) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              methodGen.math(GeneratorAdapter.NEG, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Long> intOr(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.OR, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Stringish> intStr(LoadableValue<Long> value) {
      return append(
          new InstructionWithResult<Stringish>(Stringish.class, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              STR__FROM_INT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Long> intSubtract(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.SUB, LONG_TYPE);
            }
          });
    }

    public LoadableValue<Boolean> intToBool(LoadableValue<Long> value, long reference) {
      return append(
          new InstructionWithResult<Boolean>(BOOLEAN_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              methodGen.push(reference);
              Label ne = methodGen.newLabel();
              Label end = methodGen.newLabel();
              methodGen.ifCmp(LONG_TYPE, GeneratorAdapter.NE, ne);
              methodGen.push(true);
              methodGen.goTo(end);
              methodGen.mark(ne);
              methodGen.push(false);
              methodGen.mark(end);
            }
          });
    }

    public LoadableValue<Double> intToFloat(LoadableValue<Long> value) {
      return append(
          new InstructionWithResult<Double>(DOUBLE_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              methodGen.cast(LONG_TYPE, DOUBLE_TYPE);
            }
          });
    }

    public LoadableValue<Long> intXor(LoadableValue<Long> left, LoadableValue<Long> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              methodGen.math(GeneratorAdapter.XOR, LONG_TYPE);
            }
          });
    }

    LoadableValue<Any> listen(LoadableValue<Future> value) {
      return append(
          new InstructionWithCallbackResult<Any, ConsumeResult>(Any.class, ConsumeResult.class) {
            @Override
            public void render() {
              value.load(Block.this);
              pushConsumer();
              FUTURE__LISTEN.invoke(methodGen);
              continueNext();
            }
          });
    }

    private void loadTaskMaster() {
      methodGen.visitFieldInsn(
          Opcodes.GETFIELD,
          Type.getInternalName(Future.class),
          "taskMaster",
          Type.getInternalName(TaskMaster.class));
    }

    public final LoadableValue<Any> lookup(LoadableValue<Context> context, String... names) {
      return append(
          new InstructionWithCallbackResult<Any, ConsumeResult>(Any.class, ConsumeResult.class) {
            @Override
            public void render() {
              lookupHandlerContextual().load(Block.this);
              loadTaskMaster();
              pushSourceReferences(methodGen);
              context.load(Block.this);
              methodGen.newArray(Type.getType(String.class));
              for (int i = 0; i < names.length; i++) {
                methodGen.dup();
                methodGen.push(i);
                methodGen.push(names[i]);
                methodGen.arrayStore(Type.getType(String.class));
              }
              LOOKUP_HANDLER__INVOKE.invoke(methodGen);
              pushConsumer();
              FUTURE__LISTEN.invoke(methodGen);
              continueNext();
            }
          });
    }

    public final LoadableValue<Any> lookup(
        LoadableValue<NameSource> nameSource,
        LoadableValue<LookupHandler> handler,
        LoadableValue<Context> context) {
      return append(
          new InstructionWithCallbackResult<Any, ConsumeResult>(Any.class, ConsumeResult.class) {
            @Override
            public void render() {
              nameSource.load(Block.this);
              handler.load(Block.this);
              loadTaskMaster();
              pushSourceReferences(methodGen);
              context.load(Block.this);
              NAME_SOURCE__LOOKUP.invoke(methodGen);
              pushConsumer();
              FUTURE__LISTEN.invoke(methodGen);
              continueNext();
            }
          });
    }

    public LoadableValue<LookupHandler> lookupHandlerContextual() {
      return new ExistingFieldValue<>(ContextualLookup.class, "HANDLER", LookupHandler.class);
    }

    public void nameSourceAddFrame(
        LoadableValue<NameSource> nameSource, LoadableValue<Frame> frame) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              String continueMethod = String.format("continue%d_%d", blockId, callbackId++);
              nameSource.load(Block.this);
              loadTaskMaster();
              pushSourceReferences(methodGen);
              frame.load(Block.this);
              methodGen.loadThis();
              methodGen.visitInvokeDynamicInsn(
                  "run",
                  Type.getMethodDescriptor(RUNNABLE_TYPE, selfType),
                  LAMBDA_METAFACTORY_BSM,
                  "()V",
                  new Handle(
                      Opcodes.H_INVOKEVIRTUAL, selfType.getInternalName(), continueMethod, "()V"),
                  "()V");
              NAME_SOURCE__ADD_FRAME.invoke(methodGen);
              methodGen.visitInsn(Opcodes.RETURN);
              methodGen.visitMaxs(0, 0);
              methodGen.visitEnd();

              methodGen =
                  new GeneratorAdapter(
                      Opcodes.ACC_SYNTHETIC,
                      new Method(continueMethod, VOID_TYPE, null),
                      null,
                      null,
                      classVisitor);
              methodGen.visitCode();
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              nameSource.addTo(liveValues);
              frame.addTo(liveValues);
              liveValues.forEach(LoadableValue::hoist);
            }
          });
    }

    public void nameSourceAddLiteral(LoadableValue<NameSource> nameSource, String name) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              nameSource.load(Block.this);
              methodGen.push(name);
              NAME_SOURCE__ADD_LITERAL.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              nameSource.addTo(liveValues);
            }
          });
    }

    public void nameSourceAddOrdinal(
        LoadableValue<NameSource> nameSource, LoadableValue<Long> ordinal) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              nameSource.load(Block.this);
              ordinal.load(Block.this);
              NAME_SOURCE__ADD_ORDINAL.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              nameSource.addTo(liveValues);
              ordinal.addTo(liveValues);
            }
          });
    }

    public void nameSourceAddStr(
        LoadableValue<NameSource> nameSource, LoadableValue<Stringish> str) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              nameSource.load(Block.this);
              str.load(Block.this);
              NAME_SOURCE__ADD_STR.invoke(methodGen);
              Label end = methodGen.newLabel();
              methodGen.ifZCmp(GeneratorAdapter.NE, end);
              methodGen.visitInsn(Opcodes.RETURN);
              methodGen.mark(end);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              nameSource.addTo(liveValues);
              str.addTo(liveValues);
            }
          });
    }

    public void nameSourceAddTypeOf(
        LoadableValue<NameSource> nameSource, LoadableValue<Any> value) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              nameSource.load(Block.this);
              value.load(Block.this);
              NAME_SOURCE__ADD_TYPE_OF.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              nameSource.addTo(liveValues);
              value.addTo(liveValues);
            }
          });
    }

    public LoadableValue<NameSource> nameSourceCreate() {
      return append(
          new InstructionWithResult<NameSource>(NameSource.class) {

            @Override
            protected void renderValue() {
              methodGen.newInstance(getType());
              methodGen.dup();
              methodGen.invokeConstructor(getType(), DEFAULT_CTOR);
            }
          });
    }

    public void ret(LoadableValue<Any> value) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              methodGen.loadThis();
              value.load(Block.this);
              FUTURE__COMPLETE.invoke(methodGen);
              methodGen.visitInsn(Opcodes.RETURN);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              value.addTo(liveValues);
            }
          });
      writeMethod();
    }

    public LoadableValue<Long> stringCompare(
        LoadableValue<Stringish> left, LoadableValue<Stringish> right) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, left, right) {
            @Override
            protected void renderValue() {
              left.load(Block.this);
              right.load(Block.this);
              STR__COMPARE.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Stringish> stringConcatenate(
        LoadableValue<Stringish> first, LoadableValue<Stringish> second) {
      return append(
          new InstructionWithResult<Stringish>(Stringish.class, first, second) {
            @Override
            protected void renderValue() {
              first.load(Block.this);
              second.load(Block.this);
              STR__CONCAT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Stringish> stringConstant(String s) {
      return append(
          new InstructionWithResult<Stringish>(Stringish.class) {
            @Override
            protected void renderValue() {
              methodGen.push(s);
              STR__FROM_STRING.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Long> stringLength(LoadableValue<Stringish> value) {
      return append(
          new InstructionWithResult<Long>(LONG_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              STR__LENGTH.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Stringish> stringOrdinal(LoadableValue<Long> value) {
      return append(
          new InstructionWithResult<Stringish>(Stringish.class, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              STR__ORDINAL.invoke(methodGen);
            }
          });
    }

    @SafeVarargs
    public final LoadableValue<Template> templateCreate(
        LoadableValue<Context> context, LoadableValue<? extends DefinitionSource>... builders) {
      return append(
          new InstructionWithResult<Template>(
              Template.class,
              Stream.concat(Arrays.stream(builders), Stream.of(context))
                  .toArray(LoadableValue[]::new)) {
            @Override
            protected void renderValue() {
              methodGen.newInstance(Type.getType(Template.class));
              methodGen.dup();
              boolean needSourceReference = true;
              for (LoadableValue<? extends DefinitionSource> builder : builders) {
                if (builder.getType().equals(Type.getType(Template.class))) {
                  builder.load(Block.this);
                  renderSourceLocation();
                  pushSourceReferences(methodGen);
                  TEMPLATE__JOIN_SOURCE_REFERENCE.invoke(methodGen);
                  needSourceReference = false;
                  break;
                }
              }
              if (needSourceReference) {
                pushSourceReferences(methodGen);
              }
              context.load(Block.this);
              methodGen.push(builders.length);
              methodGen.newArray(Type.getType(DefinitionSource.class));
              for (int i = 0; i < builders.length; i++) {
                methodGen.dup();
                methodGen.push(i);
                builders[i].load(Block.this);
                methodGen.arrayStore(Type.getType(DefinitionSource.class));
              }
              TEMPLATE__CTOR.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Frame> tmplContainer(LoadableValue<Template> value) {
      return append(
          new InstructionWithResult<Frame>(Frame.class, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              TEMPLATE__CONTAINER.invoke(methodGen);
            }
          });
    }

    public LoadableValue<Context> tmplContext(LoadableValue<Template> value) {
      return append(
          new InstructionWithResult<Context>(Context.class, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              TEMPLATE__CONTEXT.invoke(methodGen);
            }
          });
    }

    public LoadableValue<ValueBuilder> valueBuilderCreate() {
      return append(
          new InstructionWithResult<ValueBuilder>(ValueBuilder.class) {

            @Override
            protected void renderValue() {
              methodGen.newInstance(getType());
              methodGen.dup();
              methodGen.invokeConstructor(getType(), DEFAULT_CTOR);
            }
          });
    }

    public LoadableValue<Boolean> valueBuilderHas(
        LoadableValue<ValueBuilder> builder, LoadableValue<Stringish> name) {
      return append(
          new InstructionWithResult<Boolean>(BOOLEAN_TYPE, builder, name) {

            @Override
            protected void renderValue() {
              builder.load(Block.this);
              name.load(Block.this);
              VALUE_BUILDER__HAS.invoke(methodGen);
            }
          });
    }

    public void valueBuilderSetName(
        LoadableValue<ValueBuilder> builder,
        LoadableValue<Stringish> name,
        LoadableValue<Any> value) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              builder.load(Block.this);
              name.load(Block.this);
              value.load(Block.this);
              VALUE_BUILDER__SET_NAME.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              builder.addTo(liveValues);
              name.addTo(liveValues);
              value.addTo(liveValues);
            }
          });
    }

    public void valueBuilderSetOrdinal(
        LoadableValue<ValueBuilder> builder,
        LoadableValue<Long> ordinal,
        LoadableValue<Any> value) {
      instructions.add(
          new InstructionGroup() {

            @Override
            public void render() {
              builder.load(Block.this);
              ordinal.load(Block.this);
              value.load(Block.this);
              VALUE_BUILDER__SET_ORDINAL.invoke(methodGen);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              builder.addTo(liveValues);
              ordinal.addTo(liveValues);
              value.addTo(liveValues);
            }
          });
    }

    public LoadableValue<Boolean> verifySymbol(LoadableValue<Stringish> value) {
      return append(
          new InstructionWithResult<Boolean>(BOOLEAN_TYPE, value) {
            @Override
            protected void renderValue() {
              value.load(Block.this);
              TASK_MASTER__VERIFY_SYMBOL.invoke(methodGen);
            }
          });
    }

    private void writeBranch(Block target, LoadableValue<?>... parameters) {
      Type[] parameterTypes =
          Arrays.stream(parameters).map(LoadableValue::getType).toArray(Type[]::new);

      instructions.add(
          new InstructionGroup() {
            @Override
            public void render() {
              methodGen.loadThis();
              methodGen.invokeDynamic(
                  "run",
                  Type.getMethodDescriptor(
                      RUNNABLE_TYPE,
                      Stream.concat(Stream.of(selfType), Arrays.stream(parameterTypes))
                          .toArray(Type[]::new)),
                  LAMBDA_METAFACTORY_BSM,
                  "()V",
                  new Handle(
                      Opcodes.H_INVOKEVIRTUAL,
                      selfType.getInternalName(),
                      "block" + target.blockId,
                      Type.getMethodDescriptor(VOID_TYPE, parameterTypes)),
                  "()V");
              methodGen.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  selfType.getInternalName(),
                  "next",
                  Type.getMethodDescriptor(VOID_TYPE, RUNNABLE_TYPE),
                  false);
              methodGen.visitInsn(Opcodes.RETURN);
            }

            @Override
            public void update(Set<LoadableValue<?>> liveValues) {
              Arrays.stream(parameters).forEach(parameter -> parameter.addTo(liveValues));
            }
          });
      writeMethod();
    }

    private void writeMethod() {
      Set<LoadableValue<?>> liveValues = new HashSet<>();
      for (int i = instructions.size() - 1; i >= 0; i--) {
        instructions.get(i).update(liveValues);
      }
      liveValues.removeAll(getParameters());
      if (liveValues.size() > 0) {
        throw new IllegalStateException("Undeclared locals used in block.");
      }
      methodGen.visitCode();
      getParameters().forEach(ParameterValue::store);
      instructions.forEach(instruction -> instruction.render());
      methodGen.visitMaxs(0, 0);
      methodGen.visitEnd();
    }
  }

  public final class Block0 extends Block {

    @Override
    protected Collection<ParameterValue<?>> getParameters() {
      return Collections.emptyList();
    }
  }

  public final class Block1<T> extends Block {
    private final ParameterValue<T> parameter;

    Block1(Class<T> clazz) {
      parameter = new ParameterValue<>(clazz, 1);
    }

    @Override
    protected Collection<ParameterValue<?>> getParameters() {
      return Collections.singletonList(parameter);
    }

    public LoadableValue<T> parameter() {
      return parameter;
    }
  }

  public final class Block2<T, U> extends Block {
    private final ParameterValue<T> parameter1;
    private final ParameterValue<U> parameter2;

    Block2(Class<T> clazz1, Class<U> clazz2) {
      parameter1 = new ParameterValue<>(clazz1, 1);
      parameter2 = new ParameterValue<>(clazz2, 2);
    }

    @Override
    protected Collection<ParameterValue<?>> getParameters() {
      return Arrays.asList(parameter1, parameter2);
    }

    public LoadableValue<T> parameter1() {
      return parameter1;
    }

    public LoadableValue<U> parameter2() {
      return parameter2;
    }
  }

  public final class Block3<T, U, V> extends Block {
    private final ParameterValue<T> parameter1;
    private final ParameterValue<U> parameter2;
    private final ParameterValue<V> parameter3;

    Block3(Class<T> clazz1, Class<U> clazz2, Class<V> clazz3) {
      parameter1 = new ParameterValue<>(clazz1, 1);
      parameter2 = new ParameterValue<>(clazz2, 2);
      parameter3 = new ParameterValue<>(clazz3, 3);
    }

    @Override
    protected Collection<ParameterValue<?>> getParameters() {
      return Arrays.asList(parameter1, parameter2, parameter3);
    }

    public LoadableValue<T> parameter1() {
      return parameter1;
    }

    public LoadableValue<U> parameter2() {
      return parameter2;
    }

    public LoadableValue<V> parameter3() {
      return parameter3;
    }
  }

  public final class Block4<T, U, V, W> extends Block {
    private final ParameterValue<T> parameter1;
    private final ParameterValue<U> parameter2;
    private final ParameterValue<V> parameter3;
    private final ParameterValue<W> parameter4;

    Block4(Class<T> clazz1, Class<U> clazz2, Class<V> clazz3, Class<W> clazz4) {
      parameter1 = new ParameterValue<>(clazz1, 1);
      parameter2 = new ParameterValue<>(clazz2, 2);
      parameter3 = new ParameterValue<>(clazz3, 3);
      parameter4 = new ParameterValue<>(clazz4, 4);
    }

    @Override
    protected Collection<ParameterValue<?>> getParameters() {
      return Arrays.asList(parameter1, parameter2, parameter3, parameter4);
    }

    public LoadableValue<T> parameter1() {
      return parameter1;
    }

    public LoadableValue<U> parameter2() {
      return parameter2;
    }

    public LoadableValue<V> parameter3() {
      return parameter3;
    }

    public LoadableValue<W> parameter4() {
      return parameter4;
    }
  }

  protected final class ConstructorValue<T> extends LoadableValue<T> {
    private final String name;

    public ConstructorValue(String name, Class<T> fieldType) {
      super(fieldType);
      this.name = name;
    }

    @Override
    public void addTo(Set<LoadableValue<?>> liveValues) {}

    public String getName() {
      return name;
    }

    @Override
    public void hoist() {}

    @Override
    public void load(Block block) {
      block.methodGen.loadThis();
      block.methodGen.getField(selfType, name, getType());
    }

    public void store(GeneratorAdapter methodGen) {
      methodGen.putField(selfType, name, getType());
    }
  }

  public final class Dispatch {
    private final Map<String, BiConsumer<Type, ClassVisitor>> accepted = new TreeMap<>();

    public void dispatchBin(Block1<byte[]> target) {
      accepted.put("Bin", makeAcceptor(Type.getType(byte[].class), target));
    }

    public void dispatchBool(Block1<Boolean> target) {
      accepted.put("Bool", makeAcceptor(BOOLEAN_TYPE, target));
    }

    public void dispatchFloat(Block1<Double> target) {
      accepted.put("Float", makeAcceptor(DOUBLE_TYPE, target));
    }

    public void dispatchFrame(Block1<Frame> target) {
      accepted.put("Frame", makeAcceptor(Type.getType(Frame.class), target));
    }

    public void dispatchInt(Block1<Long> target) {
      accepted.put("Int", makeAcceptor(LONG_TYPE, target));
    }

    public void dispatchLookupHandler(Block1<LookupHandler> target) {
      accepted.put("LookupHandler", makeAcceptor(LOOKUP_HANDLER_TYPE, target));
    }

    public void dispatchNull(Block0 target) {
      accepted.put(
          "Null",
          (dispatcherType, acceptorClassVisitor) -> {
            MethodVisitor acceptor =
                acceptorClassVisitor.visitMethod(
                    Opcodes.ACC_PUBLIC, "accept", Type.getMethodDescriptor(VOID_TYPE), null, null);
            acceptor.visitCode();
            acceptor.visitVarInsn(Opcodes.ALOAD, 0);
            acceptor.visitFieldInsn(
                Opcodes.GETFIELD,
                dispatcherType.getInternalName(),
                "owner",
                selfType.getDescriptor());
            acceptor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                selfType.getInternalName(),
                "block" + target.blockId,
                Type.getMethodDescriptor(VOID_TYPE),
                false);
            acceptor.visitInsn(Opcodes.RETURN);
            acceptor.visitMaxs(0, 0);
            acceptor.visitEnd();
          });
    }

    public void dispatchStr(Block1<Stringish> target) {
      accepted.put("Str", makeAcceptor(Type.getType(Stringish.class), target));
    }

    public void dispatchTemplate(Block1<Template> target) {
      accepted.put("Template", makeAcceptor(Type.getType(Template.class), target));
    }

    private Type generate(int blockId) {
      int dispatchId = dispatchCount++;
      Type dispatcherType = Type.getObjectType(selfType.getInternalName() + "$A" + dispatchId);
      ClassVisitor acceptorClassVisitor = classMaker.get();
      acceptorClassVisitor.visit(
          Opcodes.V1_8,
          0,
          dispatcherType.getInternalName(),
          null,
          Type.getInternalName(AcceptOrFail.class),
          null);
      acceptorClassVisitor
          .visitField(Opcodes.ACC_PRIVATE, "owner", selfType.getDescriptor(), null, null)
          .visitEnd();
      MethodVisitor ctor =
          acceptorClassVisitor.visitMethod(
              Opcodes.ACC_FINAL | Opcodes.ACC_PUBLIC,
              "<init>",
              Type.getMethodDescriptor(VOID_TYPE, selfType),
              null,
              null);
      ctor.visitVarInsn(Opcodes.ALOAD, 0);
      ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      ctor.visitVarInsn(Opcodes.ALOAD, 0);
      ctor.visitVarInsn(Opcodes.ALOAD, 1);
      ctor.visitFieldInsn(
          Opcodes.PUTFIELD, dispatcherType.getInternalName(), "owner", selfType.getDescriptor());
      ctor.visitInsn(Opcodes.RETURN);
      ctor.visitMaxs(0, 0);
      ctor.visitEnd();

      accepted.values().forEach(acceptor -> acceptor.accept(dispatcherType, acceptorClassVisitor));
      MethodVisitor acceptorFail =
          acceptorClassVisitor.visitMethod(
              Opcodes.ACC_FINAL | Opcodes.ACC_PUBLIC,
              "fail",
              Type.getMethodDescriptor(VOID_TYPE, Type.getType(String.class)),
              null,
              null);
      acceptorFail.visitCode();
      acceptorFail.visitVarInsn(Opcodes.ALOAD, 0);
      acceptorFail.visitFieldInsn(
          Opcodes.GETFIELD, dispatcherType.getInternalName(), "owner", selfType.getDescriptor());
      acceptorFail.visitVarInsn(Opcodes.ALOAD, 1);
      acceptorFail.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL,
          selfType.getInternalName(),
          "fail" + dispatchId,
          "(Ljava/lang/String)V",
          false);
      acceptorFail.visitInsn(Opcodes.RETURN);
      acceptorFail.visitMaxs(0, 0);
      acceptorFail.visitEnd();
      acceptorClassVisitor.visitEnd();

      MethodVisitor fail =
          classVisitor.visitMethod(
              Opcodes.ACC_PUBLIC,
              "fail" + blockId,
              Type.getMethodDescriptor(VOID_TYPE, Type.getType(String.class)),
              null,
              null);
      fail.visitVarInsn(Opcodes.ALOAD, 0);
      fail.visitFieldInsn(
          Opcodes.GETFIELD,
          selfType.getInternalName(),
          "taskMaster",
          Type.getType(TaskMaster.class).getDescriptor());
      fail.visitLdcInsn("Expected %s, but got " + String.join(" or ", accepted.keySet()) + ".");
      fail.visitVarInsn(Opcodes.ALOAD, 1);
      fail.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          Type.getInternalName(String.class),
          "format",
          Type.getMethodDescriptor(
              Type.getType(String.class), Type.getType(String.class), Type.getType(Object[].class)),
          false);
      fail.visitInsn(Opcodes.RETURN);
      fail.visitMaxs(0, 0);
      fail.visitEnd();
      return dispatcherType;
    }

    private BiConsumer<Type, ClassVisitor> makeAcceptor(Type type, Block target) {
      return (dispatchType, acceptorClassVisitor) -> {
        MethodVisitor acceptor =
            acceptorClassVisitor.visitMethod(
                Opcodes.ACC_PUBLIC,
                "accept",
                Type.getMethodDescriptor(VOID_TYPE, type),
                null,
                null);
        acceptor.visitCode();
        acceptor.visitVarInsn(Opcodes.ALOAD, 0);
        acceptor.visitFieldInsn(
            Opcodes.GETFIELD, dispatchType.getInternalName(), "owner", selfType.getDescriptor());
        acceptor.visitVarInsn(type.getOpcode(Opcodes.ILOAD), 1);
        acceptor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            dispatchType.getInternalName(),
            "block" + target.blockId,
            Type.getMethodDescriptor(VOID_TYPE, type),
            false);
        acceptor.visitInsn(Opcodes.RETURN);
        acceptor.visitMaxs(0, 0);
        acceptor.visitEnd();
      };
    }
  }

  private interface InstructionGroup {
    void render();

    void update(Set<LoadableValue<?>> liveValues);
  }

  public interface Invokable {
    void invoke(GeneratorAdapter methodGen);
  }

  public abstract static class LoadableValue<T> {
    private final Type type;

    public LoadableValue(Class<T> clazz) {
      this(Type.getType(clazz));
    }

    public LoadableValue(Type type) {
      super();
      this.type = type;
    }

    public abstract void addTo(Set<LoadableValue<?>> liveValues);

    public final Type getType() {
      return type;
    }

    public abstract void hoist();

    public abstract void load(Block block);
  }

  private static final Invokable ANY__ACCEPT = virtualMethod(ANY_TYPE, VOID_TYPE, "accept");

  private static final Invokable ANY__EMPTY = staticMethod(ANY_TYPE, ANY_TYPE, "empty");

  private static final Invokable ANY__OF_BIN = staticMethod(ANY_TYPE, ANY_TYPE, "of", BIN_TYPE);

  private static final Invokable ANY__OF_BOOL =
      staticMethod(ANY_TYPE, ANY_TYPE, "of", BOOLEAN_TYPE);

  private static final Invokable ANY__OF_FLOAT =
      staticMethod(ANY_TYPE, ANY_TYPE, "of", DOUBLE_TYPE);

  private static final Invokable ANY__OF_FRAME = staticMethod(ANY_TYPE, ANY_TYPE, "of", FRAME_TYPE);
  private static final Invokable ANY__OF_INT = staticMethod(ANY_TYPE, ANY_TYPE, "of", LONG_TYPE);
  private static final Invokable ANY__OF_LOOKUP_HANDLER =
      staticMethod(ANY_TYPE, ANY_TYPE, "of", LOOKUP_HANDLER_TYPE);
  private static final Invokable ANY__OF_STR = staticMethod(ANY_TYPE, ANY_TYPE, "of", STR_TYPE);
  private static final Invokable ANY__OF_TEMPLATE =
      staticMethod(ANY_TYPE, ANY_TYPE, "of", TMPL_TYPE);
  private static final Invokable ANY__TO_STR =
      virtualMethod(
          ANY_TYPE,
          VOID_TYPE,
          "toStr",
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          Type.getType(Any.StringConsumer.class));
  private static final Invokable CONTEXT__APPEND =
      virtualMethod(CONTEXT_TYPE, CONTEXT_TYPE, "append", CONTEXT_TYPE);
  private static final Invokable CONTEXT__PREPEND =
      virtualMethod(CONTEXT_TYPE, CONTEXT_TYPE, "prepend", FRAME_TYPE);
  private static final Method DEFAULT_CTOR = Method.getMethod("void <init> ()");
  private static final Invokable DEFINITION_BUILDER__DROP =
      virtualMethod(DEFINITION_BUILDER_TYPE, VOID_TYPE, "drop", STR_TYPE);
  private static final Invokable DEFINITION_BUILDER__REQUIRE =
      virtualMethod(DEFINITION_BUILDER_TYPE, VOID_TYPE, "require", STR_TYPE);

  private static final Invokable DEFINITION_BUILDER__SET_DEFINITION =
      virtualMethod(DEFINITION_BUILDER_TYPE, VOID_TYPE, "set", STR_TYPE, DEFINITION_TYPE);
  private static final Invokable DEFINITION_BUILDER__SET_DEFINITION_OVERRIDE =
      virtualMethod(DEFINITION_BUILDER_TYPE, VOID_TYPE, "set", STR_TYPE, OVERRIDE_DEFINITION_TYPE);
  private static final Invokable DOUBLE__COMPARE =
      staticMethod(JDOUBLE_TYPE, INT_TYPE, "compare", DOUBLE_TYPE, DOUBLE_TYPE);
  private static final Invokable DOUBLE__IS_FINITE =
      staticMethod(JDOUBLE_TYPE, BOOLEAN_TYPE, "isInfinite", DOUBLE_TYPE);
  private static final Invokable DOUBLE__IS_NAN =
      staticMethod(JDOUBLE_TYPE, BOOLEAN_TYPE, "isNaN", DOUBLE_TYPE);
  private static final Invokable FRAME__CONTEXT =
      virtualMethod(FRAME_TYPE, CONTEXT_TYPE, "getContext");
  private static final Invokable FRAME__ID = virtualMethod(FRAME_TYPE, STR_TYPE, "getId");
  private static final Invokable FRAME__NEW =
      staticMethod(
          FRAME_TYPE,
          FRAME_TYPE,
          "create",
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          CONTEXT_TYPE,
          FRAME_TYPE,
          Type.getType(RuntimeBuilder[].class));
  private static final Invokable FRAME__THROUGH =
      staticMethod(
          FRAME_TYPE,
          FRAME_TYPE,
          "through",
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          LONG_TYPE,
          LONG_TYPE,
          CONTEXT_TYPE,
          FRAME_TYPE);
  private static final Invokable FRICASSEE__ACCUMULATE =
      virtualMethod(
          FRICASSEE_TYPE, FRICASSEE_TYPE, "accumulate", STR_TYPE, ANY_TYPE, DEFINITION_TYPE);
  private static final Invokable FRICASSEE__CONCAT =
      staticMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "concat", Type.getType(Fricassee[].class));

  private static final Invokable FRICASSEE__FOR_EACH =
      staticMethod(
          FRICASSEE_TYPE,
          FRICASSEE_TYPE,
          "forEach",
          FRAME_TYPE,
          JSTRING_TYPE,
          INT_TYPE,
          INT_TYPE,
          INT_TYPE,
          INT_TYPE);

  private static final Invokable FRICASSEE__LET =
      virtualMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "let", DEFINITION_BUILDER_TYPE);
  private static final Invokable FRICASSEE__ORDER_BY_BOOL =
      virtualMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "orderByBool", BOOLEAN_TYPE, DEFINITION_TYPE);
  private static final Invokable FRICASSEE__ORDER_BY_FLOAT =
      virtualMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "orderByFloat", BOOLEAN_TYPE, DEFINITION_TYPE);
  private static final Invokable FRICASSEE__ORDER_BY_INT =
      virtualMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "orderByInt", BOOLEAN_TYPE, DEFINITION_TYPE);
  private static final Invokable FRICASSEE__ORDER_BY_STR =
      virtualMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "orderByStr", BOOLEAN_TYPE, DEFINITION_TYPE);
  private static final Invokable FRICASSEE__REDUCE =
      virtualMethod(
          FRICASSEE_TYPE,
          FUTURE_TYPE,
          "reduce",
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          CONTEXT_TYPE,
          FRAME_TYPE,
          STR_TYPE,
          ANY_TYPE,
          DEFINITION_TYPE);
  private static final Invokable FRICASSEE__REVERSE =
      virtualMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "reverse");
  private static final Invokable FRICASSEE__TO_FRAME =
      virtualMethod(
          FRICASSEE_TYPE,
          VOID_TYPE,
          "toFrame",
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          CONTEXT_TYPE,
          FRAME_TYPE,
          DEFINITION_TYPE,
          DEFINITION_TYPE,
          CONSUME_FRAME_TYPE);

  private static final Invokable FRICASSEE__TO_LIST =
      virtualMethod(
          FRICASSEE_TYPE,
          VOID_TYPE,
          "toList",
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          CONTEXT_TYPE,
          FRAME_TYPE,
          DEFINITION_TYPE,
          CONSUME_FRAME_TYPE);
  private static final Invokable FRICASSEE__WHERE =
      virtualMethod(FRICASSEE_TYPE, FRICASSEE_TYPE, "where", DEFINITION_TYPE);
  private static final Invokable FRICASSEE_MERGE__ADD =
      virtualMethod(FRICASSEE_MERGE_TYPE, VOID_TYPE, "add", STR_TYPE, FRAME_TYPE);
  private static final Invokable FRICASSEE_MERGE__ADD_NAME =
      virtualMethod(FRICASSEE_MERGE_TYPE, VOID_TYPE, "addName", STR_TYPE);
  private static final Invokable FRICASSEE_MERGE__ADD_ORDINAL =
      virtualMethod(FRICASSEE_MERGE_TYPE, VOID_TYPE, "addOrdinal", STR_TYPE);
  private static final Invokable FRICASSEE_MERGE__CTOR =
      constructMethod(FRICASSEE_MERGE_TYPE, JSTRING_TYPE, INT_TYPE, INT_TYPE, INT_TYPE, INT_TYPE);
  private static final Invokable FUTURE__COMPLETE =
      virtualMethod(FUTURE_TYPE, VOID_TYPE, "complete", ANY_TYPE);
  private static final Invokable FUTURE__LISTEN =
      virtualMethod(FUTURE_TYPE, VOID_TYPE, "listen", CONSUME_RESULT_TYPE);
  static final Handle LAMBDA_METAFACTORY_BSM =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(LambdaMetafactory.class).getInternalName(),
          "metafactory",
          "(Ljava/lang/invoke/MethodHandles;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite");
  private static final Invokable LOOKUP_HANDLER__INVOKE =
      interfaceMethod(
          LOOKUP_HANDLER_TYPE,
          FUTURE_TYPE,
          "lookup",
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          CONTEXT_TYPE,
          Type.getType(String[].class));
  private static final Invokable NAME_SOURCE__ADD_FRAME =
      virtualMethod(
          NAME_SOURCE_TYPE,
          VOID_TYPE,
          "add",
          FRAME_TYPE,
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          RUNNABLE_TYPE);
  private static final Invokable NAME_SOURCE__ADD_LITERAL =
      virtualMethod(NAME_SOURCE_TYPE, VOID_TYPE, "add", JSTRING_TYPE);
  private static final Invokable NAME_SOURCE__ADD_ORDINAL =
      virtualMethod(NAME_SOURCE_TYPE, VOID_TYPE, "add", LONG_TYPE);
  private static final Invokable NAME_SOURCE__ADD_STR =
      virtualMethod(
          NAME_SOURCE_TYPE, BOOLEAN_TYPE, "add", TASK_MASTER_TYPE, SOURCE_REFERENCE_TYPE, STR_TYPE);
  private static final Invokable NAME_SOURCE__ADD_TYPE_OF =
      virtualMethod(NAME_SOURCE_TYPE, VOID_TYPE, "add", ANY_TYPE);
  private static final Invokable NAME_SOURCE__LOOKUP =
      virtualMethod(
          NAME_SOURCE_TYPE,
          VOID_TYPE,
          "lookup",
          LOOKUP_HANDLER_TYPE,
          TASK_MASTER_TYPE,
          SOURCE_REFERENCE_TYPE,
          CONTEXT_TYPE);

  private static final Invokable OBJECT__TO_STRING =
      virtualMethod(Type.getType(Object.class), JSTRING_TYPE, "toString");
  private static final Invokable STR__COMPARE =
      virtualMethod(STR_TYPE, INT_TYPE, "compareTo", STR_TYPE);

  private static final Invokable STR__CONCAT =
      virtualMethod(STR_TYPE, STR_TYPE, "concat", STR_TYPE);

  private static final Invokable STR__FROM_BOOL =
      staticMethod(STR_TYPE, STR_TYPE, "from", BOOLEAN_TYPE);

  private static final Invokable STR__FROM_FLOAT =
      staticMethod(STR_TYPE, STR_TYPE, "from", DOUBLE_TYPE);

  private static final Invokable STR__FROM_INT =
      staticMethod(STR_TYPE, STR_TYPE, "from", LONG_TYPE);
  private static final Invokable STR__FROM_STRING =
      staticMethod(STR_TYPE, STR_TYPE, "from", JSTRING_TYPE);
  private static final Invokable STR__LENGTH = staticMethod(STR_TYPE, LONG_TYPE, "getLength");
  private static final Invokable STR__ORDINAL =
      staticMethod(Type.getType(SupportFunctions.class), STR_TYPE, "ordinalName", LONG_TYPE);
  private static final Invokable TASK_MASTER__LOAD_EXTERNAL =
      virtualMethod(TASK_MASTER_TYPE, VOID_TYPE, "getExternal", JSTRING_TYPE, CONSUME_RESULT_TYPE);
  private static final Invokable TASK_MASTER__REPORT_OTHER_ERROR =
      virtualMethod(
          TASK_MASTER_TYPE, VOID_TYPE, "reportOtherError", SOURCE_REFERENCE_TYPE, JSTRING_TYPE);
  private static final Invokable TASK_MASTER__VERIFY_SYMBOL =
      virtualMethod(TASK_MASTER_TYPE, BOOLEAN_TYPE, "verifySymbol", STR_TYPE);
  private static final Invokable TEMPLATE__CONTAINER =
      virtualMethod(TMPL_TYPE, FRAME_TYPE, "getContainer");

  private static final Invokable TEMPLATE__CONTEXT =
      virtualMethod(TMPL_TYPE, CONTEXT_TYPE, "getContext");
  private static final Invokable TEMPLATE__CTOR =
      constructMethod(
          TMPL_TYPE,
          SOURCE_REFERENCE_TYPE,
          CONTEXT_TYPE,
          FRAME_TYPE,
          Type.getType(DefinitionSource[].class));
  private static final Invokable TEMPLATE__JOIN_SOURCE_REFERENCE =
      virtualMethod(
          TMPL_TYPE,
          SOURCE_REFERENCE_TYPE,
          "joinSourceReference",
          JSTRING_TYPE,
          INT_TYPE,
          INT_TYPE,
          INT_TYPE,
          INT_TYPE,
          SOURCE_REFERENCE_TYPE);

  private static final Invokable VALUE_BUILDER__HAS =
      virtualMethod(TMPL_TYPE, BOOLEAN_TYPE, "has", STR_TYPE);
  private static final Invokable VALUE_BUILDER__SET_NAME =
      virtualMethod(TMPL_TYPE, VOID_TYPE, "set", STR_TYPE, ANY_TYPE);
  private static final Invokable VALUE_BUILDER__SET_ORDINAL =
      virtualMethod(TMPL_TYPE, VOID_TYPE, "set", VOID_TYPE, LONG_TYPE, ANY_TYPE);

  public static Invokable constructMethod(Type owner, Type... parameters) {
    Method method = new Method("<init>", VOID_TYPE, parameters);
    return methodGen -> methodGen.invokeConstructor(owner, method);
  }

  public static Invokable interfaceMethod(
      Type owner, Type returnType, String name, Type... parameters) {
    Method method = new Method(name, returnType, parameters);
    return methodGen -> methodGen.invokeInterface(owner, method);
  }

  public static Invokable staticMethod(
      Type owner, Type returnType, String name, Type... parameters) {
    Method method = new Method(name, returnType, parameters);
    return methodGen -> methodGen.invokeStatic(owner, method);
  }

  public static Invokable virtualMethod(
      Type owner, Type returnType, String name, Type... parameters) {
    Method method = new Method(name, returnType, parameters);
    return methodGen -> methodGen.invokeVirtual(owner, method);
  }

  private int blockCount = 0;

  protected final Supplier<ClassVisitor> classMaker;
  protected final ClassVisitor classVisitor;
  private final Type[] constructorTypes;
  private int dispatchCount = 0;
  private final Block0 entry = new Block0();
  private int fieldCount;
  protected final Type selfType;

  private Stack<SourceLocation> sourceLocations = new Stack<>();

  private int sourceReferenceCount;

  protected final Stack<String> sourceReferences = new Stack<>();

  protected BaseKwsGenerator(
      Supplier<ClassVisitor> classMaker,
      String name,
      SourceLocation start,
      Stream<Type> ctorArguments) {
    this.classMaker = classMaker;
    selfType = Type.getObjectType(name);
    sourceLocations.push(start);
    classVisitor = classMaker.get();
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        name,
        null,
        Type.getInternalName(GeneratedFuture.class),
        null);
    constructorTypes =
        Stream.concat(Stream.of(Type.getType(TaskMaster.class)), ctorArguments)
            .toArray(Type[]::new);
  }

  public final Block0 createBlock() {
    return new Block0();
  }

  public final <T> Block1<T> createBlock(Class<T> clazz) {
    return new Block1<>(clazz);
  }

  public final <T, U> Block2<T, U> createBlock(Class<T> clazz1, Class<U> clazz2) {
    return new Block2<>(clazz1, clazz2);
  }

  public final <T, U, V> Block3<T, U, V> createBlock(
      Class<T> clazz1, Class<U> clazz2, Class<V> clazz3) {
    return new Block3<>(clazz1, clazz2, clazz3);
  }

  public final <T, U, V, W> Block4<T, U, V, W> createBlock(
      Class<T> clazz1, Class<U> clazz2, Class<V> clazz3, Class<W> clazz4) {
    return new Block4<>(clazz1, clazz2, clazz3, clazz4);
  }

  public final Type finish() {
    classVisitor.visitEnd();
    return selfType;
  }

  public final Block0 getEntry() {
    return entry;
  }

  protected final <T> LoadableValue<T> getSelfReference(Class<T> packagingClazz) {
    return new LoadableValue<T>(packagingClazz) {

      @Override
      public void addTo(Set<LoadableValue<?>> liveValues) {}

      @Override
      public void hoist() {}

      @Override
      public void load(Block block) {
        String targetDescriptor =
            Type.getMethodDescriptor(Type.getType(Future.class), constructorTypes);
        block.methodGen.invokeDynamic(
            "invoke",
            Type.getMethodDescriptor(Type.getType(packagingClazz)),
            LAMBDA_METAFACTORY_BSM,
            targetDescriptor,
            new Handle(
                Opcodes.H_NEWINVOKESPECIAL,
                selfType.getInternalName(),
                "<init>",
                Type.getMethodDescriptor(VOID_TYPE, constructorTypes)),
            targetDescriptor);
      }
    };
  }

  protected final void makeConstructor() {
    GeneratorAdapter ctor =
        new GeneratorAdapter(
            Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC,
            new Method("<init>", VOID_TYPE, constructorTypes),
            null,
            null,
            classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.loadArg(1);
    ctor.loadThis();
    ctor.invokeDynamic(
        "run",
        Type.getMethodDescriptor(RUNNABLE_TYPE, selfType),
        LAMBDA_METAFACTORY_BSM,
        "()V",
        new Handle(
            Opcodes.H_INVOKEVIRTUAL,
            selfType.getInternalName(),
            "block" + entry.blockId,
            Type.getMethodDescriptor(VOID_TYPE)),
        "()V");
    ctor.invokeConstructor(
        Type.getType(GeneratedFuture.class),
        new Method("<init>", VOID_TYPE, new Type[] {TASK_MASTER_TYPE, RUNNABLE_TYPE}));
    setupConstructor(ctor);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();
  }

  public final void popSourceLocation() {
    sourceLocations.pop();
  }

  public final void popSourceReference() {
    sourceReferences.pop();
  }

  public final void pushSourceLocation(SourceLocation location) {
    sourceLocations.push(location);
  }

  protected final void pushSourceLocationToStack(GeneratorAdapter methodGen) {
    sourceLocations.peek().pushToStack(methodGen);
  }

  protected final void pushSourceReference(String fieldName) {
    sourceReferences.push(fieldName);
  }

  public final void pushSourceReferenceFromLocation(GeneratorAdapter methodGen, String message) {
    methodGen.loadThis();
    methodGen.newInstance(Type.getType(BasicSourceReference.class));
    methodGen.dup();
    methodGen.push(message);
    pushSourceLocationToStack(methodGen);
    pushSourceReferences(methodGen);
    methodGen.invokeConstructor(
        Type.getType(BasicSourceReference.class),
        new Method(
            "<init>",
            VOID_TYPE,
            new Type[] {
              Type.getType(String.class),
              Type.getType(String.class),
              INT_TYPE,
              INT_TYPE,
              INT_TYPE,
              INT_TYPE,
              Type.getType(SourceReference.class)
            }));
    String fieldName = "sourceReference" + (sourceReferenceCount++);
    methodGen.putField(selfType, fieldName, Type.getType(SourceReference.class));
    sourceReferences.push(fieldName);
  }

  public final void pushSourceReferences(GeneratorAdapter methodGen) {
    if (sourceReferences.isEmpty()) {
      methodGen.visitInsn(Opcodes.ACONST_NULL);
    } else {
      methodGen.loadThis();
      methodGen.getField(selfType, sourceReferences.peek(), Type.getType(SourceReference.class));
    }
  }

  protected abstract void setupConstructor(GeneratorAdapter method);
}
