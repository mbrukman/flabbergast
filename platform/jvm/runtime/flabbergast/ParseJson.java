package flabbergast;

import flabbergast.Frame.RuntimeBuilder;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;

class ParseJson extends BaseMapFunctionInterop<String, Template> {

  ParseJson(TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
    super(Any::of, asString(false), taskMaster, sourceReference, context, self);
  }

  @Override
  protected Template computeResult(String input) {
    final JsonParser parser = Json.createParser(new StringReader(input));

    final Template tmpl = new Template(sourceReference, context, self);
    tmpl.set("json_root", parse(new Ptr<>(parser.next()), parser, null));
    return tmpl;
  }

  private Definition emitComplex(
      JsonLocation start, JsonLocation end, RuntimeBuilder children, String keyName, String type) {
    final ValueBuilder builder = new ValueBuilder();
    builder.set("json_name", Any.of(keyName));
    final DefinitionBuilder computeBuilder = new DefinitionBuilder();
    computeBuilder.set("children", Frame.create(children));

    return Template.instantiate(
        new RuntimeBuilder[] {builder, computeBuilder},
        "<json>",
        (int) start.getLineNumber(),
        (int) start.getColumnNumber(),
        (int) end.getLineNumber(),
        (int) end.getColumnNumber(),
        "json",
        type);
  }

  private Definition emitScalar(Ptr<Event> event, JsonParser parser, Any argument, String keyName) {
    final JsonLocation start = parser.getLocation();
    event.set(parser.next());
    final JsonLocation end = parser.getLocation();
    final ValueBuilder builder = new ValueBuilder();
    builder.set("json_name", Any.of(keyName));
    builder.set("arg", argument);
    return Template.instantiate(
        new RuntimeBuilder[] {builder},
        "<json>",
        (int) start.getLineNumber(),
        (int) start.getColumnNumber(),
        (int) end.getLineNumber(),
        (int) end.getColumnNumber(),
        "json",
        "scalar");
  }

  private Definition parse(Ptr<Event> event, JsonParser parser, String keyName) {
    Definition result;
    switch (event.get()) {
      case START_OBJECT:
        result = parseObject(parser, keyName);
        event.set(parser.hasNext() ? parser.next() : null);
        return result;
      case START_ARRAY:
        result = parseArray(parser, keyName);
        event.set(parser.hasNext() ? parser.next() : null);
        return result;
      case VALUE_NULL:
        return emitScalar(event, parser, Any.unit(), keyName);
      case VALUE_FALSE:
        return emitScalar(event, parser, Any.of(false), keyName);
      case VALUE_TRUE:
        return emitScalar(event, parser, Any.of(true), keyName);
      case VALUE_NUMBER:
        return parser.isIntegralNumber()
            ? emitScalar(event, parser, Any.of(parser.getLong()), keyName)
            : emitScalar(event, parser, Any.of(parser.getBigDecimal().doubleValue()), keyName);
      default:
        throw new IllegalStateException("Error parsing JSON");
    }
  }

  private Definition parseArray(JsonParser parser, String keyName) {
    final JsonLocation start = parser.getLocation();
    final List<Definition> stream = new ArrayList<>();
    for (final Ptr<Event> event = new Ptr<>(parser.next());
        event.get() != Event.END_ARRAY;
        stream.add(parse(event, parser, null))) {}
    return emitComplex(
        start, parser.getLocation(), new ArrayComputeBuilder(stream), keyName, "list");
  }

  private Definition parseObject(JsonParser parser, String keyName) {
    final JsonLocation start = parser.getLocation();
    final List<Definition> stream = new ArrayList<>();
    for (final Ptr<Event> event = new Ptr<>(parser.next()); event.get() != Event.END_OBJECT; ) {
      final String itemKey = parser.getString();
      event.set(parser.next());
      stream.add(parse(event, parser, itemKey));
    }
    return emitComplex(
        start, parser.getLocation(), new ArrayComputeBuilder(stream), keyName, "object");
  }
}
