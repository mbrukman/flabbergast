package flabbergast;

/** Delegate used for parsing lists in a generic way from existing parse rules. */
public interface ParseRule<T> {
  boolean invoke(Ptr<Parser.Position> position, Ptr<T> result);
}
