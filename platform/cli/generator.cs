using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using System;

namespace Flabbergast {

/**
 * A handle for generating the needed components in an assembly.
 */
public class CompilationUnit {
	/**
	 * A call back that will populate a function with generated code.
	 */
	internal delegate void FunctionBlock(Generator generator, LoadableValue source_reference, LoadableValue context, LoadableValue self, LoadableValue container);
	/**
	 * A call back that will populate a function with generated code.
	 */
	internal delegate void FunctionOverrideBlock(Generator generator, LoadableValue source_reference, LoadableValue context, LoadableValue self, LoadableValue container, LoadableValue original);

	public IEnumerable<string> ExternalUris { get { return externals.Keys; } }
	/**
	 * The backing module builder from the library.
	 */
	public ModuleBuilder ModuleBuilder { get; private set; }
	/**
	 * The debugging symbol for this file.
	 */
	public System.Diagnostics.SymbolStore.ISymbolDocumentWriter SymbolDocument { get; private set; }

	internal Dictionary<string, bool> externals = new Dictionary<string, bool>();

	/**
	 * For generating unique class names.
	 */
	private System.Runtime.Serialization.ObjectIDGenerator id_gen = new System.Runtime.Serialization.ObjectIDGenerator();
	/**
	 * Functions and override functions we have generated before.
	 *
	 * Since the surrounding syntax cannot affect a function, we cache the
	 * functions to avoid regenerating them.
	 */
	private Dictionary<string, MethodInfo> functions = new Dictionary<string, MethodInfo>();

	public CompilationUnit(string filename, ModuleBuilder module_builder, bool debuggable) {
		ModuleBuilder = module_builder;
		SymbolDocument = debuggable ? module_builder.DefineDocument(filename, Guid.Empty, Guid.Empty, Guid.Empty) : null;
	}

	/**
	 * Create a new function, and use the provided block to fill it with code.
	 */
	internal MethodInfo CreateFunction(AstNode instance, string syntax_id, FunctionBlock block, string root_prefix, Dictionary<string, bool> owner_externals) {
		bool used;
		var name = String.Concat(root_prefix, "Function", id_gen.GetId(instance, out used), syntax_id);
		if (functions.ContainsKey(name)) {
			return functions[name];
		}
		var generator = CreateFunctionGenerator(name, false, root_prefix, owner_externals);
		block(generator, generator.InitialContainerFrame, generator.InitialContext, generator.InitialSelfFrame, generator.InitialSourceReference);
		generator.GenerateSwitchBlock();
		functions[name] = generator.Initialiser;
		generator.TypeBuilder.CreateType();

		return generator.Initialiser;
	}
	/**
	 * Create a new override function, and use the provided block to fill it with code.
	 */
	internal MethodInfo CreateFunctionOverride(AstNode instance, string syntax_id, FunctionOverrideBlock block, string root_prefix, Dictionary<string, bool> owner_externals) {
		bool used;
		var name = String.Concat(root_prefix, "Override", id_gen.GetId(instance, out used), syntax_id);
		if (functions.ContainsKey(name)) {
			return functions[name];
		}
		 var generator = CreateFunctionGenerator(name, true, root_prefix, owner_externals);
		block(generator, generator.InitialContainerFrame, generator.InitialContext, generator.InitialOriginal, generator.InitialSelfFrame, generator.InitialSourceReference);
		generator.GenerateSwitchBlock();
		functions[name] = generator.Initialiser;
		generator.TypeBuilder.CreateType();

		return generator.Initialiser;
	}

	private Generator CreateFunctionGenerator(string name, bool has_original, string root_prefix, Dictionary<string, bool> owner_externals) {
		var type_builder = ModuleBuilder.DefineType(name, TypeAttributes.AutoLayout | TypeAttributes.Class | TypeAttributes.NotPublic | TypeAttributes.Sealed | TypeAttributes.UnicodeClass, typeof(Computation));
		return new Generator(this, type_builder, has_original, root_prefix, owner_externals);
	}

	internal System.Type CreateRootGenerator(string name, Generator.Block block) {
		var type_builder = ModuleBuilder.DefineType(name, TypeAttributes.AutoLayout | TypeAttributes.Class | TypeAttributes.Public | TypeAttributes.Sealed | TypeAttributes.UnicodeClass, typeof(Computation));
		var generator = new Generator(this, type_builder, name);
		block(generator);
		generator.GenerateSwitchBlock(true);
		return type_builder.CreateType();
	}
}

/**
 * Helper to generate code for a particular function or override function.
 */
internal class Generator {
	/**
	 * Generate code with no input.
	 */
	public delegate void Block(Generator generator);
	/**
	 * Generate code for an item during a fold operation, using an initial value
	 * and passing the output to a result block.
	 */
	public delegate void FoldBlock<T, R>(int index, T item, R left, ParameterisedBlock<R> result);
	/**
	 * Generate code given a single input.
	 */
	public delegate void ParameterisedBlock<R>(R result);
	/**
	 * The compilation unit that created this function.
	 */
	public CompilationUnit Owner { get; private set; }
	/**
	 * The underlying class builder for this function.
	 */
	public TypeBuilder TypeBuilder { get; private set; }
	/**
	 * The body of the `Run` method for this function.
	 */
	public ILGenerator Builder { get; private set; }
	/**
	 * A static method capable of creating a new instance of the class.
	 */
	public MethodBuilder Initialiser { get; private set; }
	/**
	 * The source reference of the caller of this function.
	 */
	public FieldValue InitialSourceReference { get; private set; }
	/**
	 * The lookup context provided by the caller.
	 */
	public FieldValue InitialContext { get; private set; }
	/**
	 * The “This” frame provided by the caller.
	 */
	public FieldValue InitialSelfFrame { get; private set; }
	/**
	 * The “Container” provided by from the caller.
	 */
	public FieldValue InitialContainerFrame { get; private set; }
	/**
	 * The original value to an override function, null otherwise.
	 */
	public FieldValue InitialOriginal { get; private set; }
	/**
	 * The current number of environment-induced bifurcation points;
	 */
	public int Paths { get; set; }
	/**
	 * The field containing the current state for this function to continue upon
	 * re-entry.
	 */
	private FieldInfo state_field;
	/**
	 * A reference count to control mutual exclusion.
	 */
	private FieldInfo interlock_field;
	/**
	 * The field containing the task master.
	 */
	private FieldInfo task_master;
	/**
	 * The labels in the code where the function may enter into a particular
	 * state. The index is the state number.
	 */
	private List<Label> entry_points = new List<Label>();
	/**
	 * A counter for producing unique result consumers names.
	 */
	private int result_consumer = 0;
	/**
	 * The branch point, at the end of the function, that does dispatch from the
	 * state field to the correct branch.
	 *
	 * It would be ideal to place the dispatch at the start of the function, but
	 * the number of states is not known, so the function branches to this
	 * address, then branches based on the dispatch.
	 */
	private Label switch_label;
	/**
	 * The collection of external URIs needed by this computation and where they are stored.
	 */
	private Dictionary<string, FieldValue> externals = new Dictionary<string, FieldValue>();
	private Dictionary<string, bool> owner_externals;
	/**
	 * The namespace in which all the child types will live.
	 */
	private string root_prefix;

	/**
	 * The number of fields holding temporary variables that cross sleep boundaries.
	 */
	private int num_fields = 0;

	private Dictionary<System.Type, LocalBuilder> locals = new Dictionary<System.Type, LocalBuilder>();
	public static bool IsNumeric(System.Type type) {
		return type == typeof(double) || type == typeof(long);
	}

	internal Generator(CompilationUnit owner, TypeBuilder type_builder, string root_prefix) {
		Owner = owner;
		TypeBuilder = type_builder;
		Paths = 1;
		this.root_prefix = root_prefix + "__" ;
		owner_externals = new Dictionary<string, bool>();
		state_field = TypeBuilder.DefineField("state", typeof(int), FieldAttributes.Private);
		interlock_field = TypeBuilder.DefineField("interlock", typeof(int), FieldAttributes.Private);
		task_master = TypeBuilder.DefineField("task_master", typeof(TaskMaster), FieldAttributes.Private);
		var ctor = type_builder.DefineConstructor(MethodAttributes.Public, CallingConventions.Standard, new System.Type[] { typeof(TaskMaster) });
		var ctor_builder = ctor.GetILGenerator();
		ctor_builder.Emit(OpCodes.Ldarg_0);
		ctor_builder.Emit(OpCodes.Call, typeof(Computation).GetConstructors()[0]);
		ctor_builder.Emit(OpCodes.Ldarg_0);
		ctor_builder.Emit(OpCodes.Ldarg_1);
		ctor_builder.Emit(OpCodes.Stfld, task_master);
		ctor_builder.Emit(OpCodes.Ldarg_0);
		ctor_builder.Emit(OpCodes.Ldc_I4_0);
		ctor_builder.Emit(OpCodes.Stfld, state_field);
		ctor_builder.Emit(OpCodes.Ret);
		Builder = type_builder.DefineMethod("Run", MethodAttributes.Family | MethodAttributes.Virtual | MethodAttributes.HideBySig, typeof(bool), new System.Type[0]).GetILGenerator();
		switch_label = Builder.DefineLabel();
		// Label for load externals
		entry_points.Add(Builder.DefineLabel());
		// Label for main body
		entry_points.Add(Builder.DefineLabel());
		Builder.Emit(OpCodes.Br, switch_label);
		MarkState(1);
		DeclareLocals();
	}
	internal Generator(CompilationUnit owner, TypeBuilder type_builder, bool has_original, string root_prefix, Dictionary<string, bool> owner_externals) {
		Owner = owner;
		TypeBuilder = type_builder;
		Paths = 1;
		this.owner_externals = owner_externals;
		this.root_prefix = root_prefix;
		// Create fields for all information provided by the caller.
		state_field = TypeBuilder.DefineField("state", typeof(int), FieldAttributes.Private);
		interlock_field = TypeBuilder.DefineField("interlock", typeof(int), FieldAttributes.Private);
		task_master = TypeBuilder.DefineField("task_master", typeof(TaskMaster), FieldAttributes.Private);
		InitialSourceReference = new FieldValue(TypeBuilder.DefineField("source_reference", typeof(SourceReference), FieldAttributes.Private));
		InitialContext = new FieldValue(TypeBuilder.DefineField("context", typeof(Context), FieldAttributes.Private));
		InitialSelfFrame = new FieldValue(TypeBuilder.DefineField("self", typeof(Frame), FieldAttributes.Private));
		InitialContainerFrame = new FieldValue(TypeBuilder.DefineField("container", typeof(Frame), FieldAttributes.Private));
		var construct_params = new System.Type[] { typeof(TaskMaster), typeof(SourceReference), typeof(Context), typeof(Frame), typeof(Frame) };
		var initial_information = new FieldInfo[] { task_master, InitialSourceReference.Field, InitialContext.Field, InitialSelfFrame.Field, InitialContainerFrame.Field };

		// Create a constructor the takes all the state information provided by the
		// caller and stores it in appropriate fields.
		var ctor = type_builder.DefineConstructor(MethodAttributes.Public, CallingConventions.Standard, construct_params);
		var ctor_builder = ctor.GetILGenerator();
		ctor_builder.Emit(OpCodes.Ldarg_0);
		ctor_builder.Emit(OpCodes.Call, typeof(Computation).GetConstructors()[0]);
		for(var it = 0; it < initial_information.Length; it++) {
			ctor_builder.Emit(OpCodes.Ldarg_0);
			ctor_builder.Emit(OpCodes.Ldarg, it + 1);
			ctor_builder.Emit(OpCodes.Stfld, initial_information[it]);
		}
		ctor_builder.Emit(OpCodes.Ldarg_0);
		ctor_builder.Emit(OpCodes.Ldc_I4_0);
		ctor_builder.Emit(OpCodes.Stfld, state_field);
		ctor_builder.Emit(OpCodes.Ret);

		System.Type[] init_params;
		if (has_original) {
			init_params = new System.Type[construct_params.Length + 1];
			for (var it = 0; it < construct_params.Length; it++) {
				init_params[it] = construct_params[it];
			}
			init_params[init_params.Length - 1] = typeof(Computation);
		} else {
			init_params = construct_params;
		}
		// Create a static method that wraps the constructor. This is needed to create a delegate.
		Initialiser = type_builder.DefineMethod("Init", MethodAttributes.Public | MethodAttributes.Static | MethodAttributes.HideBySig, typeof(Computation), init_params);
		var init_builder = Initialiser.GetILGenerator();
		if (has_original) {
			// If the thing we are overriding is null, create an error and give up.
			var has_instance = init_builder.DefineLabel();
			init_builder.Emit(OpCodes.Ldarg, init_params.Length - 1);
			init_builder.Emit(OpCodes.Brtrue, has_instance);
			init_builder.Emit(OpCodes.Ldarg_0);
			init_builder.Emit(OpCodes.Ldarg_1);
			init_builder.Emit(OpCodes.Ldstr, "Cannot perform override. No value in source tuple to override!");
			init_builder.Emit(OpCodes.Callvirt, typeof(TaskMaster).GetMethod("ReportOtherError", new System.Type[] { typeof(SourceReference), typeof(string) }));
			init_builder.Emit(OpCodes.Ldnull);
			init_builder.Emit(OpCodes.Ret);
			init_builder.MarkLabel(has_instance);
		}
		for (var it = 0; it < initial_information.Length; it++) {
			init_builder.Emit(OpCodes.Ldarg, it);
		}
		init_builder.Emit(OpCodes.Newobj, ctor);

		// If overriding, attach the overriding function to the original computation.
		FieldInfo original_computation = null;
		if (has_original) {
			InitialOriginal = new FieldValue(TypeBuilder.DefineField("original", typeof(object), FieldAttributes.Private));
			original_computation = TypeBuilder.DefineField("original_computation", typeof(Computation), FieldAttributes.Private);
			init_builder.Emit(OpCodes.Dup);
			init_builder.Emit(OpCodes.Ldarg, initial_information.Length);
			init_builder.Emit(OpCodes.Stfld, original_computation);
		}

		init_builder.Emit(OpCodes.Ret);

		// Create a run method with an initial state.
		Builder = type_builder.DefineMethod("Run", MethodAttributes.Family | MethodAttributes.Virtual | MethodAttributes.HideBySig, typeof(bool), new System.Type[0]).GetILGenerator();
		switch_label = Builder.DefineLabel();
		// Label for load externals
		entry_points.Add(Builder.DefineLabel());
		// Label for main body
		entry_points.Add(Builder.DefineLabel());
		Builder.Emit(OpCodes.Br, switch_label);
		MarkState(1);
		if (has_original) {
			var state = DefineState();
			SetState(state);
			LoadTaskMaster();
			Builder.Emit(OpCodes.Ldarg_0);
			Builder.Emit(OpCodes.Ldfld, original_computation);
			Builder.Emit(OpCodes.Dup);
			GenerateConsumeResult(InitialOriginal);
			Builder.Emit(OpCodes.Callvirt, typeof(Computation).GetMethod("Notify", new System.Type[] { typeof(ConsumeResult) }));
			Builder.Emit(OpCodes.Call, typeof(TaskMaster).GetMethod("Slot", new System.Type[] { typeof(Computation) }));
			Builder.Emit(OpCodes.Ldc_I4_0);
			Builder.Emit(OpCodes.Ret);
			MarkState(state);
		}
		DeclareLocals();
	}

	/**
	 * Create a new source reference based on an existing one, updated to reflect
	 * entry into a new AST node.
	 */
	public void AmendSourceReference(AstNode node, string message, LoadableValue source_reference, LoadableValue source_template) {
		if (source_template == null) {
			source_reference.Load(Builder);
		} else {
			Builder.Emit(OpCodes.Ldstr, message + ":");
			Builder.Emit(OpCodes.Ldstr, node.FileName);
			Builder.Emit(OpCodes.Ldc_I4, node.StartRow);
			Builder.Emit(OpCodes.Ldc_I4, node.StartColumn);
			Builder.Emit(OpCodes.Ldc_I4, node.EndRow);
			Builder.Emit(OpCodes.Ldc_I4, node.EndColumn);
			source_reference.Load(Builder);
			source_template.Load(Builder);
			Builder.Emit(OpCodes.Call, typeof(Template).GetMethod("get_SourceReference"));
			Builder.Emit(OpCodes.Newobj, typeof(JunctionReference).GetConstructors()[0]);
		}
	}
	public LoadableValue Compare(LoadableValue left, LoadableValue right, LoadableValue source_reference) {
		if (left.BackingType == typeof(object) || right.BackingType == typeof(object)) {
			throw new System.InvalidOperationException(System.String.Format("Can't compare values of type {0} and {1}.", left.BackingType, right.BackingType));
		}
		if (left.BackingType != right.BackingType) {
			if (Generator.IsNumeric(left.BackingType) && Generator.IsNumeric(right.BackingType)) {
				return Compare(new UpgradeValue(left), new UpgradeValue(right), source_reference);
			} else {
				EmitTypeError(source_reference, "Cannot compare value of type {0} and type {1}.", left, right);
				return null;
			}
		}
		return new CompareValue(locals, left, right);
	}
	/**
	 * Copies the contents of one field to another, boxing or unboxing based on
	 * the field types.
	 */
	public void CopyField(LoadableValue source, FieldValue target) {
		CopyField(source, target.Field);
	}
	void CopyField(LoadableValue source, FieldInfo target) {
		Builder.Emit(OpCodes.Ldarg_0);
		LoadReboxed(source, target.FieldType);
		Builder.Emit(OpCodes.Stfld, target);
	}
	internal MethodInfo CreateFunctionOverride(AstNode instance, string syntax_id, CompilationUnit.FunctionOverrideBlock block) {
		return Owner.CreateFunctionOverride(instance, syntax_id, block, root_prefix, owner_externals);
	}
	internal MethodInfo CreateFunction(AstNode instance, string syntax_id, CompilationUnit.FunctionBlock block) {
		return Owner.CreateFunction(instance, syntax_id, block, root_prefix, owner_externals);
	}
	/**
	 * Insert debugging information based on an AST node.
	 */
	public void DebugPosition(CodeRegion node) {
		if (Owner.SymbolDocument != null) {
			Builder.MarkSequencePoint(Owner.SymbolDocument, node.StartRow, node.StartColumn, node.EndRow, node.EndColumn);
		}
	}
	private void DeclareLocals() {
		foreach (var type in new System.Type[] { typeof(bool), typeof(long), typeof(double) }) {
			locals[type] = Builder.DeclareLocal(type);
		}
	}
	public void DecrementInterlock(ILGenerator builder) {
		builder.Emit(OpCodes.Ldarg_0);
		builder.Emit(OpCodes.Ldflda, interlock_field);
		builder.Emit(OpCodes.Call, typeof(System.Threading.Interlocked).GetMethod("Decrement", new System.Type[] { typeof(int).MakeByRefType() }));
	}
	/**
	 * Generate a runtime dispatch that checks each of the provided types.
	 */
	public void DynamicTypeDispatch(LoadableValue original, LoadableValue source_reference, System.Type[] types, ParameterisedBlock<LoadableValue> block) {
		if (original.BackingType != typeof(object)) {
			foreach (var type in types) {
				if (original.BackingType == type) {
					block(original);
					return;
				}
			}
			LoadTaskMaster();
			source_reference.Load(Builder);
			Builder.Emit(OpCodes.Ldstr, String.Format("Unexpected type {0} instead of {1}.", original.BackingType, string.Join(", ", (object[]) types)));
			Builder.Emit(OpCodes.Callvirt, typeof(TaskMaster).GetMethod("ReportOtherError", new System.Type[] { typeof(SourceReference), typeof(string) }));
			return;
		}
		var labels = new Label[types.Length];
		for(var it = 0; it < types.Length; it++) {
			labels[it] = Builder.DefineLabel();
			original.Load(Builder);
			Builder.Emit(OpCodes.Isinst, types[it]);
			Builder.Emit(OpCodes.Brtrue, labels[it]);
		}
		EmitTypeError(source_reference, String.Format("Unexpected type {0} instead of {1}.", "{0}", string.Join(", ", (object[]) types)), original);

		for(var it = 0; it < types.Length; it++) {
			Builder.MarkLabel(labels[it]);
			block(new AutoUnboxValue(original, types[it]));
		}
	}
	/**
	 * Create a new state and put it in the dispatch logic.
	 *
	 * This state must later be attached to a place in the code using `MarkState`.
	 */
	public int DefineState() {
		var label = Builder.DefineLabel();
		var id = entry_points.Count;
		entry_points.Add(label);
		return id;
	}
	public void EmitTypeError(LoadableValue source_reference, string message, params LoadableValue[] data) {
		if (data.Length == 0) {
			throw new InvalidOperationException("Type errors must have at least one argument.");
		}
		LoadTaskMaster();
		source_reference.Load(Builder);
		Builder.Emit(OpCodes.Ldstr, message);
		foreach (var item in data) {
			if (item.BackingType == typeof(object)) {
				item.Load(Builder);
				Builder.Emit(OpCodes.Call, typeof(object).GetMethod("GetType"));
				Builder.Emit(OpCodes.Call, typeof(Stringish).GetMethod("HideImplementation", new System.Type[] { typeof(System.Type) }));
			} else {
				Builder.Emit(OpCodes.Ldtoken, Stringish.HideImplementation(item.BackingType));
				Builder.Emit(OpCodes.Call, typeof(System.Type).GetMethod("GetTypeFromHandle", new System.Type[] { typeof(RuntimeTypeHandle) }));
			}
		}
		var signature = new System.Type[data.Length + 1];
		signature[0] = typeof(string);
		for (var it = 0; it < data.Length; it++) {
			signature[it + 1] =  typeof(object);
		}
		Builder.Emit(OpCodes.Call, typeof(String).GetMethod("Format", signature));
		Builder.Emit(OpCodes.Callvirt, typeof(TaskMaster).GetMethod("ReportOtherError", new System.Type[] { typeof(SourceReference), typeof(string) }));
		Builder.Emit(OpCodes.Ldc_I4_0);
		Builder.Emit(OpCodes.Ret);
	}
	/**
	 * Generate code for a list using a fold (i.e., each computation in the list
	 * is made from the previous computation).
	 */
	public void Fold<T, R>(R initial, List<T> list, FoldBlock<T, R> expand, ParameterisedBlock<R> result) {
		FoldHelper(list, expand, result, initial, 0);
	}
	private void FoldHelper<T, R>(List<T> list, FoldBlock<T, R> expand, ParameterisedBlock<R> result, R curr_result, int it) {
		if (it < list.Count) {
			expand(it, list[it], curr_result, (next_result) => FoldHelper(list, expand, result, next_result, it + 1));
		} else {
			result(curr_result);
		}
	}
	/**
	 * Generate a function to receive a value and request continued computation from the task master.
	 */
	public void GenerateConsumeResult(FieldValue result_target, bool interlocked = false) {
		var method = TypeBuilder.DefineMethod("ConsumeResult" + result_consumer++, MethodAttributes.Public, typeof(void), new System.Type[] { typeof(object) });
		var consume_builder = method.GetILGenerator();

		consume_builder.Emit(OpCodes.Ldarg_0);
		consume_builder.Emit(OpCodes.Ldarg_1);
		consume_builder.Emit(OpCodes.Stfld, result_target.Field);
		var return_label = consume_builder.DefineLabel();
		if (interlocked) {
			DecrementInterlock(consume_builder);
			consume_builder.Emit(OpCodes.Brtrue, return_label);
		}
		LoadTaskMaster(consume_builder);
		consume_builder.Emit(OpCodes.Ldarg_0);
		consume_builder.Emit(OpCodes.Call, typeof(TaskMaster).GetMethod("Slot", new System.Type[] { typeof(Computation) }));
		consume_builder.MarkLabel(return_label);
		consume_builder.Emit(OpCodes.Ret);

		Builder.Emit(OpCodes.Ldarg_0);
		Builder.Emit(OpCodes.Ldftn, method);
		Builder.Emit(OpCodes.Newobj, typeof(ConsumeResult).GetConstructors()[0]);
	}
	public void GenerateNextId() {
		LoadTaskMaster();
		Builder.Emit(OpCodes.Call, typeof(TaskMaster).GetMethod("NextId"));
	}

	/**
	 * Finish this function by creating the state dispatch instruction using a
	 * switch (computed goto). Also generate the block that loads all the
	 * external values.
	 */
	internal void GenerateSwitchBlock(bool load_owner_externals = false) {
		MarkState(0);
		// If this is a top level function, load all the external values for our children.
		if (load_owner_externals) {
			foreach(var uri in owner_externals.Keys) {
				if (!externals.ContainsKey(uri)) {
					externals[uri] = MakeField(uri, typeof(object));
				}
			}
		}
		if (externals.Count > 0) {
			StartInterlock(externals.Count);
			foreach (var entry in externals) {
				LoadTaskMaster();
				Builder.Emit(OpCodes.Ldstr, entry.Key);
				GenerateConsumeResult(entry.Value, true);
				Builder.Emit(OpCodes.Callvirt, typeof(TaskMaster).GetMethod("GetExternal", new System.Type[] { typeof(string), typeof(ConsumeResult) }));
			}
			StopInterlock(1);
		} else {
			Builder.Emit(OpCodes.Br, entry_points[1]);
		}

		Builder.MarkLabel(switch_label);
		Builder.Emit(OpCodes.Ldarg_0);
		Builder.Emit(OpCodes.Ldfld, state_field);
		Builder.Emit(OpCodes.Switch, entry_points.ToArray());
		Builder.ThrowException(typeof(ArgumentOutOfRangeException));
	}
	private bool InvokeParameterPenalty(System.Type method, System.Type given, ref int penalty) {
		if (method == given) {
			return true;
		}
		if (method == typeof(string) && given == typeof(Stringish)) {
			return true;
		}
		if (given == typeof(long)) {
			if (method == typeof(sbyte) || method == typeof(byte)) {
				penalty += sizeof(long) - sizeof(byte);
				return true;
			} else if (method == typeof(short) || method == typeof(ushort)) {
				penalty += sizeof(long) - sizeof(short);
				return true;
			} else if (method == typeof(int) || method == typeof(uint)) {
				penalty += sizeof(long) - sizeof(int);
				return true;
			} else if (method == typeof(ulong)) {
				return true;
			}
		} else if (given == typeof(double)) {
			if (method == typeof(float)) {
				penalty += sizeof(double) - sizeof(float);
				return true;
			}
		}
		return false;
	}
	public LoadableValue InvokeNative(LoadableValue source_reference, List<System.Reflection.MethodInfo> methods, LoadableValue[] arguments) {
		System.Reflection.MethodInfo best_method = null;
		var best_penalty = int.MaxValue;
		foreach (var method in methods) {
			var penalty = 0;
			var parameters = method.GetParameters();
			if (!method.IsStatic && !InvokeParameterPenalty(method.ReflectedType, arguments[0].BackingType, ref penalty)) {
				break;
			}
			bool possible = true;
			for (var it = 0; it < parameters.Length && possible; it++) {
				possible = InvokeParameterPenalty(parameters[it].ParameterType, arguments[it + (method.IsStatic ? 0 : 1)].BackingType, ref penalty);
			}
			if (possible && penalty < best_penalty) {
				best_method = method;
			}
		}
		if (best_method == null) {
			LoadTaskMaster();
			source_reference.Load(Builder);
			Builder.Emit(OpCodes.Ldstr, String.Format("Cannot find overloaded matching method for {0}.{1}({3}).", methods[0].Name, methods[0].ReflectedType.Name, String.Join(",", arguments.Select(a => a.BackingType.Name)))); 
			Builder.Emit(OpCodes.Callvirt, typeof(TaskMaster).GetMethod("ReportOtherError", new System.Type[] { typeof(SourceReference), typeof(string) }));
			Builder.Emit(OpCodes.Ldc_I4_0);
			Builder.Emit(OpCodes.Ret);
			return null;
		}
		var method_parameters = best_method.GetParameters();
		var method_arguments = new System.Type[method_parameters.Length + (best_method.IsStatic ? 0 : 1)];
		if (!best_method.IsStatic) {
			method_arguments[0] = best_method.ReflectedType;
		}
		for (var it = 0; it < method_parameters.Length; it++) {
			method_arguments[it + (best_method.IsStatic ? 0 : 1)] = method_parameters[it].ParameterType;
		}

		var result = MakeField(best_method.Name, AstTypeableNode.ClrTypeFromType(AstTypeableNode.TypeFromClrType(best_method.ReturnType))[0]);
		Builder.Emit(OpCodes.Ldarg_0);
		for(var it = 0; it < arguments.Length; it++) {
			arguments[it].Load(this);
			if (arguments[it].BackingType != method_arguments[it]) {
				if (method_arguments[it] == typeof(sbyte) || method_arguments[it] == typeof(byte)) {
					Builder.Emit(OpCodes.Conv_I1);
				} else if (method_arguments[it] == typeof(short) || method_arguments[it] == typeof(ushort)) {
					Builder.Emit(OpCodes.Conv_I2);
				} else if (method_arguments[it] == typeof(int) || method_arguments[it] == typeof(uint)) {
					Builder.Emit(OpCodes.Conv_I4);
				} else if (method_arguments[it] == typeof(ulong)) {
				} else if (method_arguments[it] == typeof(float)) {
					Builder.Emit(OpCodes.Conv_R4);
				} else if (method_arguments[it] == typeof(string)) {
					Builder.Emit(OpCodes.Callvirt, typeof(Stringish).GetMethod("ToString"));
				} else {
					throw new InvalidOperationException(String.Format("No conversation from {0} to {1} while invoking {2}.{3}.", arguments[it].BackingType.Name, method_arguments[it].Name, best_method.ReflectedType.Name, best_method.Name));
				}
			}
		}
		Builder.Emit(OpCodes.Call, best_method);
		if (result.BackingType != best_method.ReturnType) {
				if (result.BackingType == typeof(long)) {
					Builder.Emit(OpCodes.Conv_I8);
				} else if (result.BackingType == typeof(double)) {
					Builder.Emit(OpCodes.Conv_R8);
				} else if (result.BackingType == typeof(Stringish)) {
					Builder.Emit(OpCodes.Newobj, typeof(SimpleStringish).GetConstructors()[0]);
				} else {
					throw new InvalidOperationException(String.Format("No conversation from {0} to {1} while invoking {2}.{3}.", best_method.ReturnType.Name, result.BackingType.Name, best_method.ReflectedType.Name, best_method.Name));
				}
		}
		Builder.Emit(OpCodes.Stfld, result.Field);
		return result;
	}
	/**
	 * Loads a value and repackages to match the target type, as needed.
	 *
	 * This can be boxing, unboxing, or casting.
	 */
	public void LoadReboxed(LoadableValue source, System.Type target_type) {
		source.Load(Builder);
		if (source.BackingType != target_type) {
			if (target_type == typeof(object)) {
				if (source.BackingType == typeof(bool) || source.BackingType == typeof(double) ||source.BackingType == typeof(long)) {
					Builder.Emit(OpCodes.Box, source.BackingType);
				} else {
					Builder.Emit(OpCodes.Castclass, typeof(object));
				}
			} else {
				Builder.Emit(OpCodes.Unbox_Any, target_type);
			}
		}
	}
	/**
	 * Load the task master in the `Run` function.
	 */
	public void LoadTaskMaster() {
		LoadTaskMaster(Builder);
	}
	/**
	 * Load the task master in any method of this class.
	 */
	public void LoadTaskMaster(ILGenerator builder) {
		builder.Emit(OpCodes.Ldarg_0);
		builder.Emit(System.Reflection.Emit.OpCodes.Ldfld, task_master);
	}
	/**
	 * Create an anonymous field with the specified type.
	 */
	public FieldValue MakeField(string name, System.Type type) {
		/* There is a limit to a shorts worth a fields. Our limit is lower since
		 * there are fields used by the base class and the state machine. */
		if (++num_fields > 64000) {
			throw new InvalidOperationException("Exceeded the maximum number of fields set by the CLI.");
		}
		return new FieldValue(TypeBuilder.DefineField(name, type, FieldAttributes.PrivateScope));
	}
	/**
	 * Mark the current code position as the entry point for a state.
	 */
	public void MarkState(int id) {
		Builder.MarkLabel(entry_points[id]);
	}
	private void PushSourceReferenceHelper(AstNode node, LoadableValue original_reference) {
		Builder.Emit(OpCodes.Ldstr, node.FileName);
		Builder.Emit(OpCodes.Ldc_I4, node.StartRow);
		Builder.Emit(OpCodes.Ldc_I4, node.StartColumn);
		Builder.Emit(OpCodes.Ldc_I4, node.EndRow);
		Builder.Emit(OpCodes.Ldc_I4, node.EndColumn);
		if (original_reference == null) {
			Builder.Emit(OpCodes.Ldnull);
		} else {
			original_reference.Load(this);
		}
		Builder.Emit(OpCodes.Newobj, typeof(SourceReference).GetConstructors()[0]);
	}
	public LoadableValue PushSourceReference(AstNode node, LoadableValue original_reference) {
		var reference = MakeField("source_reference", typeof(SourceReference));
		Builder.Emit(OpCodes.Ldarg_0);
		Builder.Emit(OpCodes.Ldstr, node.PrettyName);
		PushSourceReferenceHelper(node, original_reference);
		Builder.Emit(OpCodes.Stfld, reference.Field);
		return reference;
	}
	public LoadableValue PushIteratorSourceReference(AstNode node, LoadableValue iterator, LoadableValue original_reference) {
		var reference = MakeField("source_reference", typeof(SourceReference));
		Builder.Emit(OpCodes.Ldarg_0);
		Builder.Emit(OpCodes.Ldstr, "fricassée iteration {0}: {1}");
		iterator.Load(Builder);
		Builder.Emit(OpCodes.Call, typeof(MergeIterator).GetMethod("get_Position"));
		var local = locals[typeof(long)];
		Builder.Emit(OpCodes.Stloc, local);
		Builder.Emit(OpCodes.Ldloca, local);
		Builder.Emit(OpCodes.Call, typeof(long).GetMethod("ToString", new System.Type[] { }));
		iterator.Load(Builder);
		Builder.Emit(OpCodes.Call, typeof(MergeIterator).GetMethod("get_Current"));
		Builder.Emit(OpCodes.Call, typeof(String).GetMethod("Format", new System.Type[] { typeof(string), typeof(object), typeof(object) }));
		PushSourceReferenceHelper(node, original_reference);
		Builder.Emit(OpCodes.Stfld, reference.Field);
		return reference;
	}
	public LoadableValue ResolveUri(string uri) {
		owner_externals[uri] = true;
		if (!externals.ContainsKey(uri)) {
			var library_field = MakeField(uri, typeof(object));
			externals[uri] = library_field;
		}
		return externals[uri];
	}
	/**
	 * Change the state that will be entered upon re-entry.
	 */
	public void SetState(int state) {
		SetState(state, Builder);
	}
	private void SetState(int state, ILGenerator builder) {
		builder.Emit(OpCodes.Ldarg_0);
		builder.Emit(OpCodes.Ldc_I4, state);
		builder.Emit(OpCodes.Stfld, state_field);
	}
	/**
	 * Slot a computation for execution by the task master.
	 */
	public void Slot(LoadableValue target) {
		LoadTaskMaster();
		target.Load(Builder);
		Builder.Emit(OpCodes.Call, typeof(TaskMaster).GetMethod("Slot", new System.Type[] { typeof(Computation) }));
	}
	/**
	 * Slot a computation for execution and stop execution.
	 */
	public void SlotSleep(LoadableValue target) {
		Slot(target);
		Builder.Emit(OpCodes.Ldc_I4_0);
		Builder.Emit(OpCodes.Ret);
	}
	public void StartInterlock(int count) {
		Builder.Emit(OpCodes.Ldarg_0);
		Builder.Emit(OpCodes.Ldc_I4, count + 1);
		Builder.Emit(OpCodes.Stfld, interlock_field);
	}
	public void StopInterlock() {
		var state = DefineState();
		StopInterlock(state);
		MarkState(state);
	}
	public void StopInterlock(int state) {
		SetState(state);
		DecrementInterlock(Builder);
		Builder.Emit(OpCodes.Brfalse, entry_points[state]);
		Builder.Emit(OpCodes.Ldc_I4_0);
		Builder.Emit(OpCodes.Ret);
	}
	/**
	 * Generate a successful return.
	 */
	public void Return(LoadableValue result) {
		if (result.BackingType == typeof(Frame) || result.BackingType == typeof(object)) {
			var end = Builder.DefineLabel();
			if (result.BackingType == typeof(object)) {
				result.Load(Builder);
				Builder.Emit(OpCodes.Isinst, typeof(Frame));
				Builder.Emit(OpCodes.Brfalse, end);
				result.Load(Builder);
				Builder.Emit(OpCodes.Castclass, typeof(Frame));
			} else {
				result.Load(Builder);
			}
			Builder.Emit(OpCodes.Call, typeof(Frame).GetMethod("Slot"));
			Builder.MarkLabel(end);
		}
		CopyField(result, typeof(Computation).GetField("result", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance));
		Builder.Emit(OpCodes.Ldc_I4_1);
		Builder.Emit(System.Reflection.Emit.OpCodes.Ret);
	}
	private LoadableValue ToStringishHelper(LoadableValue source) {
		if (source.BackingType == typeof(bool)) {
			return new BooleanStringish(source);
		} else if (source.BackingType == typeof(long) || source.BackingType == typeof(double)) {
			return new NumericStringish(locals, source);
		} else if (source.BackingType == typeof(Stringish)) {
			return source;
		} else {
			throw new InvalidOperationException(String.Format("Cannot convert {0} to stringish.", source.BackingType));
		}
	}
	public LoadableValue ToStringish(LoadableValue source, LoadableValue source_reference) {
		if (source.BackingType == typeof(object)) {
			var field = MakeField("str", typeof(Stringish));
			var end = Builder.DefineLabel();
			foreach (var type in new System.Type[] { typeof(long), typeof(double), typeof(bool), typeof(Stringish) }) {
				var next = Builder.DefineLabel();
				source.Load(Builder);
				Builder.Emit(OpCodes.Isinst, type);
				Builder.Emit(OpCodes.Brfalse, next);
				CopyField(ToStringishHelper(new AutoUnboxValue(source, type)), field);
				Builder.Emit(OpCodes.Br, end);
				Builder.MarkLabel(next);
			}
			EmitTypeError(source_reference, "Cannot convert type {0} to string.", source);
			Builder.MarkLabel(end);
			return field;
		} else {
			return ToStringishHelper(source);
		}
	}
}
internal class LookupCache {
	private LookupCache parent;
	private Dictionary<NameInfo, LoadableValue> defined_values = new Dictionary<NameInfo, LoadableValue>();

	public LookupCache(LookupCache parent) {
		this.parent = parent;
	}

	public LoadableValue this[NameInfo name_info] {
		get {
			if (defined_values.ContainsKey(name_info)) {
				return defined_values[name_info];
			} else if (parent != null) {
				return parent[name_info];
			} else {
				throw new InvalidOperationException("Attempt to lookup cached name “" + name_info.Name + "”, but it was never cached. This is a compiler bug.");
			}
		}
		set { defined_values[name_info] = value; }
	}
	public bool Has(NameInfo name_info) {
		if (defined_values.ContainsKey(name_info)) {
			return true;
		}
		return parent == null ? false : parent.Has(name_info);
	}
}
internal abstract class LoadableValue {
	public static LoadableValue NULL_LIST = new NullValue(typeof(Context));
	public static LoadableValue NULL_FRAME = new NullValue(typeof(Frame));
	public abstract System.Type BackingType { get; }
	public abstract void Load(ILGenerator generator);
	public void Load(Generator generator) {
		Load(generator.Builder);
	}
}
internal class NullValue : LoadableValue {
	public override System.Type BackingType { get { return backing; } }
	private System.Type backing;
	public NullValue(System.Type backing) {
		this.backing = backing;
	}
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldnull);
	}
}
internal class FieldValue : LoadableValue {
	public FieldInfo Field { get; private set; }
	public override System.Type BackingType { get { return Field.FieldType; } }
	public FieldValue(FieldInfo field) {
		Field = field;
	}
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldarg_0);
		generator.Emit(OpCodes.Ldfld, Field);
	}
}
internal class AutoUnboxValue : LoadableValue {
	private System.Type unbox_type;
	private LoadableValue backing_value;
	public override System.Type BackingType { get { return unbox_type; } }
	public AutoUnboxValue(LoadableValue backing_value, System.Type unbox_type) {
		this.backing_value = backing_value;
		this.unbox_type = unbox_type;
	}
	public override void Load(ILGenerator generator) {
		backing_value.Load(generator);
		generator.Emit(OpCodes.Unbox_Any, unbox_type);
	}
}
internal class BoolConstant : LoadableValue {
	private bool number;
	public override System.Type BackingType { get { return typeof(bool); } }
	public BoolConstant(bool number) {
		this.number = number;
	}
	public override void Load(ILGenerator generator) {
		generator.Emit(number ? OpCodes.Ldc_I4_1 : OpCodes.Ldc_I4_0);
	}
}
internal class FloatConstant : LoadableValue {
	private double number;
	public readonly static FloatConstant NAN = new FloatConstant(Double.NaN);
	public readonly static FloatConstant INFINITY = new FloatConstant(Double.PositiveInfinity);
	public FloatConstant(double number) {
		this.number = number;
	}
	public override System.Type BackingType { get { return typeof(double); } }
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldc_R8, number);
	}
}
internal class IntConstant : LoadableValue {
	private long number;
	public IntConstant(long number) {
		this.number = number;
	}
	public override System.Type BackingType { get { return typeof(long); } }
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldc_I8, number);
	}
}
internal class StringishValue : LoadableValue {
	private string str;
	public StringishValue(string str) {
		this.str = str;
	}
	public override System.Type BackingType { get { return typeof(Stringish); } }
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldstr, str);
		generator.Emit(OpCodes.Newobj, typeof(SimpleStringish).GetConstructors()[0]);
	}
}
internal class UnitConstant : LoadableValue {
	public readonly static UnitConstant NULL = new UnitConstant();
	private UnitConstant() {}
	public override System.Type BackingType { get { return typeof(Unit); } }
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldsfld, typeof(Unit).GetField("NULL"));
	}
}
internal class DelegateValue : LoadableValue {
	private System.Type backing_type;
	private MethodInfo method;
	public DelegateValue(MethodInfo method, System.Type backing_type) {
		this.method = method;
		this.backing_type = backing_type;
	}
	public override System.Type BackingType { get { return backing_type; } }
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldnull);
		generator.Emit(OpCodes.Ldftn, method);
		generator.Emit(OpCodes.Newobj, backing_type.GetConstructors()[0]);
	}
}
internal class MethodValue : LoadableValue {
	private LoadableValue instance;
	private MethodInfo method;
	public MethodValue(LoadableValue instance, MethodInfo method) {
		this.method = method;
		this.instance = instance;
	}
	public override System.Type BackingType { get { return method.ReturnType; } }
	public override void Load(ILGenerator generator) {
		if (instance == null) {
			generator.Emit(OpCodes.Ldnull);
		} else {
			instance.Load(generator);
		}
		generator.Emit(method.IsVirtual ? OpCodes.Callvirt : OpCodes.Call, method);
	}
}
internal class UpgradeValue : LoadableValue {
	private LoadableValue original;
	public UpgradeValue(LoadableValue original) {
		this.original = original;
	}
	public override System.Type BackingType { get { return typeof(double); } }
	public override void Load(ILGenerator generator) {
		original.Load(generator);
		if (original.BackingType == typeof(long)) {
			generator.Emit(OpCodes.Conv_R8);
		}
	}
}
internal class CompareValue : LoadableValue {
	private LoadableValue left;
	private LoadableValue right;
	private Dictionary<System.Type, LocalBuilder> locals;
	public override System.Type BackingType { get { return typeof(long); } }
	public CompareValue(Dictionary<System.Type, LocalBuilder> locals, LoadableValue left, LoadableValue right) {
		this.locals = locals;
		this.left = left;
		this.right = right;
	}
	public override void Load(ILGenerator generator) {
		if (left.BackingType == typeof(bool)) {
			left.Load(generator);
			right.Load(generator);
			generator.Emit(OpCodes.Sub);
		} else {
			left.Load(generator);
			if (Generator.IsNumeric(left.BackingType)) {
				var local = locals[left.BackingType];
				generator.Emit(OpCodes.Stloc, local);
				generator.Emit(OpCodes.Ldloca, local);
			}
			right.Load(generator);
			generator.Emit(OpCodes.Call, left.BackingType.GetMethod("CompareTo", new System.Type[] { left.BackingType }));
			generator.Emit(OpCodes.Ldc_I4_1);
			generator.Emit(OpCodes.Call, typeof(Math).GetMethod("Min", new System.Type[] { typeof(int), typeof(int) }));
			generator.Emit(OpCodes.Ldc_I4_M1);
			generator.Emit(OpCodes.Call, typeof(Math).GetMethod("Max", new System.Type[] { typeof(int), typeof(int) }));
		}
		generator.Emit(OpCodes.Conv_I8);
	}
}
internal class BooleanStringish : LoadableValue {
	private LoadableValue source;
	public override System.Type BackingType { get { return typeof(Stringish); } }
	public BooleanStringish(LoadableValue source) {
		this.source = source;
	}
	public override void Load(ILGenerator generator) {
		generator.Emit(OpCodes.Ldsfld, typeof(Stringish).GetField("BOOLEANS"));
		source.Load(generator);
		generator.Emit(OpCodes.Ldelem, typeof(Stringish));
	}
}
internal class NumericStringish : LoadableValue {
	private LoadableValue source;
	private Dictionary<System.Type, LocalBuilder> locals;
	public override System.Type BackingType { get { return typeof(Stringish); } }
	public NumericStringish(Dictionary<System.Type, LocalBuilder> locals, LoadableValue source) {
		this.locals = locals;
		this.source = source;
	}
	public override void Load(ILGenerator generator) {
		source.Load(generator);
		var local = locals[source.BackingType];
		generator.Emit(OpCodes.Stloc, local);
		generator.Emit(OpCodes.Ldloca, local);
		generator.Emit(OpCodes.Call, source.BackingType.GetMethod("ToString", new System.Type[] {}));
		generator.Emit(OpCodes.Newobj, typeof(SimpleStringish).GetConstructors()[0]);
	}
}
internal class GeneratedValue : LoadableValue {
	public delegate void GenerationBlock(ILGenerator g);
	System.Type type;
	GenerationBlock block;
	public GeneratedValue(System.Type type, GenerationBlock block) {
		this.type = type;
		this.block = block;
	}
	public override System.Type BackingType { get { return type; } }
	public override void Load(ILGenerator generator) {
		block(generator);
	}
}
internal class RevCons<T> {
	private T head;
	private RevCons<T> tail;
	private int index;
	internal RevCons(T item, RevCons<T> tail) {
		head = item;
		this.tail = tail;
		index = tail == null ? 0 : (tail.index + 1);
	}
	private void Assign(T[] array) {
		array[index] = head;
		if (tail != null) {
			tail.Assign(array);
		}
	}
	internal T[] ToArray() {
		var array = new T[index + 1];
		Assign(array);
		return array;
	}
}
}
