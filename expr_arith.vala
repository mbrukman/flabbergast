namespace Flabbergast.Expressions {
	internal class LogicalAnd : Expression {
		public Expression left {
			get;
			set;
		}
		public Expression right {
			get;
			set;
		}
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (left);
			var left_value = engine.operands.peek ();
			if (left_value is Data.Boolean) {
				if (((Data.Boolean)left_value).value) {
					engine.operands.pop ();
					engine.call (right);
				}
			} else {
				throw new EvaluationError.TYPE_MISMATCH ("Non-boolean value in logical AND.");
			}
		}
	}

	internal class LogicalOr : Expression {
		public Expression left {
			get;
			set;
		}
		public Expression right {
			get;
			set;
		}
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (left);
			var left_value = engine.operands.peek ();
			if (left_value is Data.Boolean) {
				if (!((Data.Boolean)left_value).value) {
					engine.operands.pop ();
					engine.call (right);
				}
			} else {
				throw new EvaluationError.TYPE_MISMATCH ("Non-boolean value in logical OR.");
			}
		}
	}

	public int compare (ExecutionEngine engine) throws EvaluationError {
		var right = engine.operands.pop ();
		var left = engine.operands.pop ();
		if (left is Data.String && right is Data.String) {
			return ((Data.String)left).value.collate (((Data.String)right).value);
		}
		if (left is Data.Boolean && right is Data.Boolean) {
			var left_value = ((Data.Boolean)left).value;
			var right_value = ((Data.Boolean)right).value;
			return left_value == right_value ? 0 : (left_value) ? 1 : -1;
		}
		if (left is Data.Integer) {
			if (right is Data.Integer) {
				var left_value = ((Data.Integer)left).value;
				var right_value = ((Data.Integer)right).value;
				return (left_value - right_value).clamp (-1, 1);
			}
			if (right is Data.Float) {
				var left_value = (double) ((Data.Integer)left).value;
				var right_value = ((Data.Float)right).value;
				return (int) (left_value - right_value).clamp (-1, 1);
			}
		}
		if (left is Data.Float) {
			if (right is Data.Float) {
				var left_value = ((Data.Float)left).value;
				var right_value = ((Data.Float)right).value;
				return (int) (left_value - right_value).clamp (-1, 1);
			}
			if (right is Data.Integer) {
				var left_value = ((Data.Float)left).value;
				var right_value = (double) ((Data.Integer)right).value;
				return (int) (left_value - right_value).clamp (-1, 1);
			}
		}
		throw new EvaluationError.TYPE_MISMATCH ("Incompatible types to comparison operation.");
	}

	internal class Shuttle : Expression {
		public Expression left {
			get;
			set;
		}
		public Expression right {
			get;
			set;
		}

		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (left);
			engine.call (right);
			var result = compare (engine);
			engine.operands.push (new Data.Integer (result));
		}
	}
	internal abstract class Comparison : Expression {
		public Expression left {
			get;
			set;
		}
		public Expression right {
			get;
			set;
		}
		protected abstract bool check (int result);
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (left);
			engine.call (right);
			var result = compare (engine);
			engine.operands.push (new Data.Boolean (check (result)));
		}
	}
	internal class Equality : Comparison {
		protected override bool check (int result) {
			return result == 0;
		}
	}
	internal class Inequality : Comparison {
		protected override bool check (int result) {
			return result != 0;
		}
	}
	internal class GreaterThan : Comparison {
		protected override bool check (int result) {
			return result > 0;
		}
	}
	internal class GreaterThanOrEqualTo : Comparison {
		protected override bool check (int result) {
			return result >= 0;
		}
	}
	internal class LessThan : Comparison {
		protected override bool check (int result) {
			return result < 0;
		}
	}
	internal class LessThanOrEqualTo : Comparison {
		protected override bool check (int result) {
			return result <= 0;
		}
	}
	internal abstract class BinaryOperation : Expression {
		public Expression left {
			get;
			set;
		}
		public Expression right {
			get;
			set;
		}
		public abstract string name {
			get;
		}
		protected abstract int compute_int (int left_value, int right_value) throws EvaluationError;
		protected abstract double compute_double (double left_value, double right_value);
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (left);
			var left = engine.operands.pop ();
			engine.call (right);
			var right = engine.operands.pop ();
			if (left is Data.Integer && right is Data.Integer) {
				var result = compute_int (((Data.Integer)left).value, ((Data.Integer)right).value);
				engine.operands.push (new Data.Integer (result));
				return;
			}
			if ((left is Data.Integer || left is Data.Float) && (right is Data.Integer || right is Data.Float)) {
				var left_value = (left is Data.Integer) ? ((double) ((Data.Integer)left).value) : ((Data.Float)left).value;
				var right_value = (right is Data.Integer) ? ((double) ((Data.Integer)right).value) : ((Data.Float)right).value;
				engine.operands.push (new Data.Float (compute_double (left_value, right_value)));
				return;
			}
			throw new EvaluationError.TYPE_MISMATCH (@"Invalid type to mathematical operator for $(name).");
		}
	}
	internal class Addition : BinaryOperation {
		public override string name {
			get {
				return "addition";
			}
		}
		protected override int compute_int (int left_value, int right_value) throws EvaluationError {
			return left_value + right_value;
		}
		protected override double compute_double (double left_value, double right_value) {
			return left_value + right_value;
		}
	}
	internal class Subtraction : BinaryOperation {
		public override string name {
			get {
				return "subtraction";
			}
		}
		protected override int compute_int (int left_value, int right_value) throws EvaluationError {
			return left_value - right_value;
		}
		protected override double compute_double (double left_value, double right_value) {
			return left_value - right_value;
		}
	}
	internal class Multiplication : BinaryOperation {
		public override string name {
			get {
				return "multiplication";
			}
		}
		protected override int compute_int (int left_value, int right_value) throws EvaluationError {
			return left_value * right_value;
		}
		protected override double compute_double (double left_value, double right_value) {
			return left_value * right_value;
		}
	}
	internal class Division : BinaryOperation {
		public override string name {
			get {
				return "division";
			}
		}
		protected override int compute_int (int left_value, int right_value) throws EvaluationError {
			if (right_value == 0) {
				throw new EvaluationError.NUMERIC ("Division by zero.");
			}
			return left_value / right_value;
		}
		protected override double compute_double (double left_value, double right_value) {
			return left_value / right_value;
		}
	}
	internal class Modulus : Expression {
		public Expression left {
			get;
			set;
		}
		public Expression right {
			get;
			set;
		}
		public override override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (left);
			var left = engine.operands.pop ();
			engine.call (right);
			var right = engine.operands.pop ();
			if (left is Data.Integer && right is Data.Integer) {
				var left_value = ((Data.Integer)left).value;
				var right_value = ((Data.Integer)right).value;
				if (right_value == 0) {
					throw new EvaluationError.NUMERIC ("Modulus by zero.");
				}
				engine.operands.push (new Data.Integer (left_value % right_value));
				return;
			}
			throw new EvaluationError.TYPE_MISMATCH ("Invalid type to mathematical operator for modulus.");
		}
	}
	internal class Not : Expression {
		public Expression expression {
			get;
			set;
		}
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (expression);
			var result = engine.operands.pop ();
			if (result is Data.Boolean) {
				engine.operands.push (new Data.Boolean (!((Data.Boolean)result).value));
				return;
			}
			throw new EvaluationError.TYPE_MISMATCH ("Inavlid type for boolean not operation.");
		}
	}
	internal class Negation : Expression {
		public Expression expression {
			get;
			set;
		}
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (expression);
			var result = engine.operands.pop ();
			if (result is Data.Integer) {
				engine.operands.push (new Data.Integer (-((Data.Integer)result).value));
				return;
			}
			if (result is Data.Float) {
				engine.operands.push (new Data.Float (-((Data.Float)result).value));
				return;
			}
			throw new EvaluationError.TYPE_MISMATCH ("Inavlid type for negation operation.");
		}
	}
	internal class IsNaN : Expression {
		public Expression expression {
			get;
			set;
		}
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (expression);
			var result = engine.operands.pop ();
			if (result is Data.Integer) {
				engine.operands.push (new Data.Boolean (false)); return;
			}
			if (result is Data.Float) {
				engine.operands.push (new Data.Boolean (((Data.Float)result).value.is_nan ())); return;
			}
			throw new EvaluationError.TYPE_MISMATCH ("Invalid type to not-a-number check.");
		}
	}
	internal class IsFinite : Expression {
		public Expression expression {
			get;
			set;
		}
		public override void evaluate (ExecutionEngine engine) throws EvaluationError {
			engine.call (expression);
			var result = engine.operands.pop ();
			if (result is Data.Integer) {
				engine.operands.push (new Data.Boolean (false)); return;
			}
			if (result is Data.Float) {
				engine.operands.push (new Data.Boolean (((Data.Float)result).value.is_finite ())); return;
			}
			throw new EvaluationError.TYPE_MISMATCH ("Invalid type to infinite check.");
		}
	}
}