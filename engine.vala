namespace Flabbergast {
	public errordomain EvaluationError {
		CIRCULAR,
		RESOLUTION,
		TYPE_MISMATCH,
		INTERNAL
	}

	internal class Promise : Expression, Object {
		WeakRef owner;
		Expression expression;
		bool is_running = false;
		MachineState state;
		internal Datum? evaluated_form = null;

		internal Promise(ExecutionEngine engine, Expression expression, MachineState state) {
			owner = WeakRef(engine);
			this.expression = expression;
			this.state = state;
		}

		public void evaluate(ExecutionEngine engine) throws EvaluationError {
			if (owner.get() != engine) {
				throw new EvaluationError.INTERNAL("Tried to execute a promise on a different evaluation enginge.");
			}
			if (is_running) {
				throw new EvaluationError.CIRCULAR("Circular evaluation detected.");
			}
			if (evaluated_form != null) {
				engine.operands.push(evaluated_form);
				return;
			}
			engine.state = state;
			is_running = true;
			expression.evaluate(engine);
			is_running = false;
			evaluated_form = engine.operands.peek();
		}
	}

	internal struct MachineState {
		internal uint context;
	}

	public class DataStack {
		Gee.Deque<Datum> stack = new Gee.ArrayQueue<Datum> ();
		public DataStack() {}

		public Datum? peek() {
			return (stack.size == 0) ? null : stack.peek_head();
		}
		public Datum? pop() {
			return (stack.size == 0) ? null : stack.poll_head();
		}
		public void push(Datum datum) {
			stack.offer_head(datum);
		}
	}
	public class NameEnvironment {
		Utils.DefaultMap<string, Gee.HashMap<uint, Expression> > defined_names = new Utils.DefaultMap<string, Gee.HashMap<uint, Expression> > ((key) => new Gee.HashMap<uint, Expression> ());

		Utils.DefaultMap<string, Utils.DefaultMap<uint, Gee.List<Expression> > > known_names = new Utils.DefaultMap<string, Utils.DefaultMap<uint, Gee.List<Expression> > > ((key) => new Utils.DefaultMap<uint, Gee.List<Expression> > ((key) => new Gee.ArrayList<Expression> ()));

		uint next_context = 0;

		public NameEnvironment() {}

		public uint create(uint current_context, uint[]? inherited_contexts = null) {
			var context = ++next_context;
			foreach (var entry in defined_names.entries) {
				var list = known_names[entry.key][context];
				if (current_context != 0) {
					list.add(entry.value[current_context]);
				}
				if (inherited_contexts != null) {
					foreach (var inherited_context in inherited_contexts) {
						if (entry.value.has_key(inherited_context)) {
							list.add(entry.value[inherited_context]);
						}
					}
				}
			}
			return context;
		}

		public Expression? get(uint context, string name) {
			if (!defined_names.has_key(name)) {
				return null;
			}
			var map = defined_names[name];
			if (!map.has_key(context)) {
				return null;
			}
			return map[context];
		}

		public Gee.List<Expression> lookup(uint context, string name) {
			return known_names[name][context];
		}

		public void set(uint context, string name, Expression @value) {
			defined_names[name][context] = @value;
			known_names[name][context].insert(0, @value);
		}
	}

	public class ExecutionEngine : Object {
		StackFrame[] call_stack = {};
		public NameEnvironment environment { get; private set; default = new NameEnvironment(); }
		public DataStack operands { get; private set; default = new DataStack(); }
		internal MachineState state { get { return call_stack[call_stack.length - 1].state; } set { call_stack[call_stack.length - 1].state = value; } }

		struct StackFrame {
			internal Expression expression;
			internal MachineState state;
			internal StackFrame(MachineState state, Expression expression) {
				this.expression = expression;
				this.state = state;
			}
		}

		public void call(Expression expression) throws EvaluationError {
			call_stack += StackFrame(call_stack[call_stack.length - 1].state, expression);
			expression.evaluate(this);
			call_stack.length--;
		}

		public Expression create_closure(Expression expression) {
			return new Promise(this, expression, state);
		}

		public bool is_defined(string[] names) throws EvaluationError requires(names.length > 0) {
			var result = lookup_contextual_internal(names);
			return result != null;
		}

		private Expression? lookup_contextual_internal(string[] names) throws EvaluationError {
			var tail_names = names[1 : names.length - 1];
			foreach (var start_context in environment.lookup(state.context, names[0])) {
				call(start_context);
				if (tail_names.length == 0) {
					return null;
				}
				var result = lookup_direct_internal(tail_names);
				if (result != null) {
					return result;
				}
			}
			return null;
		}

		public Expression lookup_contextual(string[] names) throws EvaluationError requires(names.length > 0) {
			var result = lookup_contextual_internal(names);
			if (result == null) {
				throw new EvaluationError.RESOLUTION(@"Could not resolve $(string.join(".", names)).");
			}
			return (!)result;
		}
		public Expression? lookup_direct_internal(string[] names) throws EvaluationError {
			var start = operands.pop();
			for (var it = 0; it < names.length - 1; it++) {
				if (start is Tuple) {
					var promise = ((Tuple) start)[names[it]];
					call(promise);
					start = operands.pop();
				} else {
					return null;
				}
			}
			if (start is Tuple) {
				return ((Tuple) start)[names[names.length - 1]];
			} else {
				return null;
			}
		}

		public Expression lookup_direct(string[] names) throws EvaluationError requires(names.length > 0) {
			var result = lookup_direct_internal(names);
			if (result == null) {
				throw new EvaluationError.TYPE_MISMATCH("Tried to do a direct lookup inside a non-tuple.");
			}
			return (!)result;
		}
	}
}