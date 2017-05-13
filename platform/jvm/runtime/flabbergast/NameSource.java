package flabbergast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class NameSource {
  private List<String> names = new ArrayList<>();

  public final void add(Any value) {
    names.add(
        value.apply(
            new ApplyAny<String>() {
              @Override
              public String apply() {
                return "null";
              }

              @Override
              public String apply(boolean value) {
                return "bool";
              }

              @Override
              public String apply(byte[] value) {
                return "bin";
              }

              @Override
              public String apply(double value) {
                return "float";
              }

              @Override
              public String apply(Frame value) {
                return "frame";
              }

              @Override
              public String apply(long value) {
                return "int";
              }

              @Override
              public String apply(LookupHandler value) {
                return "lookup_handler";
              }

              @Override
              public String apply(Stringish value) {
                return "str";
              }

              @Override
              public String apply(Template value) {
                return "template";
              }
            }));
  }

  public final void add(long value) {
    names.add(SupportFunctions.ordinalNameStr(value));
  }

  public final void add(String literal) {
    names.add(literal);
  }

  public final void add(
      TaskMaster taskMaster, SourceReference sourceReference, Frame frame, Runnable next) {
    final AtomicInteger listInterlock = new AtomicInteger(frame.count());
    final Map<String, String> results = new TreeMap<>();
    frame
        .stream()
        .forEach(
            name -> {
              frame.get(
                  name,
                  AssistedFuture.asSymbol(false)
                      .prepare(
                          targetName -> {
                            results.put(name, targetName);
                            if (listInterlock.decrementAndGet() == 0) {
                              results.values().stream().forEach(names::add);
                              next.run();
                            }
                          },
                          (actual, expected) ->
                              taskMaster.reportOtherError(
                                  sourceReference,
                                  String.format(
                                      "Expected %s for “%s” in “Dynamic”, but got %s.",
                                      expected, name, actual))));
            });
  }

  public final boolean add(
      TaskMaster taskMaster, SourceReference sourceReference, Stringish nameStr) {
    String name = nameStr.toString();
    if (taskMaster.verifySymbol(sourceReference, name)) {
      names.add(name);
      return true;
    }
    return false;
  }

  public Future lookup(
      LookupHandler lookupHandler,
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context) {
    String[] nameList = names.stream().toArray(String[]::new);
    if (nameList.length == 0) {
      return new FailureFuture(taskMaster, sourceReference, "No names to lookup.");
    }
    return lookupHandler.lookup(taskMaster, sourceReference, context, nameList);
  }
}
