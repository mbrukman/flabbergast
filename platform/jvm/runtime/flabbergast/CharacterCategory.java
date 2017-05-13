package flabbergast;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

class CharacterCategory extends BaseMapFunctionInterop<String, Frame> {
  private static final Map<Byte, String> categories;

  static {
    categories = new HashMap<>();
    categories.put(Character.LOWERCASE_LETTER, "letter_lower");
    categories.put(Character.MODIFIER_LETTER, "letter_modifier");
    categories.put(Character.OTHER_LETTER, "letter_other");
    categories.put(Character.TITLECASE_LETTER, "letter_title");
    categories.put(Character.UPPERCASE_LETTER, "letter_upper");
    categories.put(Character.COMBINING_SPACING_MARK, "mark_combining");
    categories.put(Character.ENCLOSING_MARK, "mark_enclosing");
    categories.put(Character.NON_SPACING_MARK, "mark_nonspace");
    categories.put(Character.DECIMAL_DIGIT_NUMBER, "number_decimal");
    categories.put(Character.LETTER_NUMBER, "number_letter");
    categories.put(Character.OTHER_NUMBER, "number_other");
    categories.put(Character.CONTROL, "other_control");
    categories.put(Character.FORMAT, "other_format");
    categories.put(Character.PRIVATE_USE, "other_private");
    categories.put(Character.SURROGATE, "other_surrogate");
    categories.put(Character.UNASSIGNED, "other_unassigned");
    categories.put(Character.CONNECTOR_PUNCTUATION, "punctuation_connector");
    categories.put(Character.DASH_PUNCTUATION, "punctuation_dash");
    categories.put(Character.END_PUNCTUATION, "punctuation_end");
    categories.put(Character.FINAL_QUOTE_PUNCTUATION, "punctuation_final_quote");
    categories.put(Character.INITIAL_QUOTE_PUNCTUATION, "punctuation_initial_quote");
    categories.put(Character.OTHER_PUNCTUATION, "punctuation_other");
    categories.put(Character.START_PUNCTUATION, "punctuation_start");
    categories.put(Character.LINE_SEPARATOR, "separator_line");
    categories.put(Character.PARAGRAPH_SEPARATOR, "separator_paragraph");
    categories.put(Character.SPACE_SEPARATOR, "separator_space");
    categories.put(Character.CURRENCY_SYMBOL, "symbol_currency");
    categories.put(Character.MATH_SYMBOL, "symbol_math");
    categories.put(Character.MODIFIER_SYMBOL, "symbol_modifier");
    categories.put(Character.OTHER_SYMBOL, "symbol_other");
  }

  private final Map<Byte, Any> mappings = new HashMap<>();

  CharacterCategory(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, asString(false), taskMaster, sourceReference, context, self);
  }

  @Override
  protected Frame computeResult(String input) {
    return Frame.create(
        taskMaster,
        sourceReference,
        context,
        self,
        new ArrayValueBuilder(
            input
                .codePoints()
                .map(Character::getType)
                .mapToObj(type -> mappings.get((byte) type))
                .collect(Collectors.toList())));
  }

  @Override
  protected void setupExtra() {
    for (final Map.Entry<Byte, String> entry : categories.entrySet()) {
      final Byte key = entry.getKey();
      find(x -> mappings.put(key, x), entry.getValue());
    }
  }
}
