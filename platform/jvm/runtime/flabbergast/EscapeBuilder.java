package flabbergast;

import flabbergast.Escape.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

class EscapeBuilder extends AssistedFuture {
  interface Transformation extends Consumer<EscapeBuilder> {}

  final List<Range> ranges = new ArrayList<>();
  private final Frame self;
  final Map<Integer, String> singleSubstitutions = new TreeMap<>();

  EscapeBuilder(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(taskMaster, sourceReference, context);
    this.self = self;
  }

  @Override
  protected void resolve() {
    ranges.sort(null);
    final Template tmpl =
        new Template(
            new BasicSourceReference(
                "Make escape template", "<escape>", 0, 0, 0, 0, sourceReference),
            context,
            self);
    tmpl.set("value", Escape.create(singleSubstitutions, ranges));
    complete(Any.of(tmpl));
  }

  @Override
  protected void setup() {
    findAll(
        asMarshalled(Transformation.class, false, "lib:utils str_transform"),
        input -> input.values().stream().forEach(x -> x.accept(this)),
        "arg_values");
  }
}
