package flabbergast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.fusesource.jansi.Ansi;

/** A task master meant for interactive TTYs */
public abstract class ConsoleTaskMaster extends TaskMaster {
  private boolean dirty = false;

  private String pad(String str, int length) {
    return String.format("%1$-" + length + "s", str);
  }

  protected abstract void print(String str);

  protected abstract void println(String str);

  public void reportCircularEvaluation() {
    final boolean exit = !hasInflightLookups() || dirty;
    dirty = false;
    if (exit) {
      clearInFlight();
      return;
    }
    final Set<SourceReference> seen = new HashSet<>();
    print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.RED).toString());
    println("Circular evaluation detected.");
    print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
    for (final BaseLookup lookup : this) {
      print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLUE).toString());
      print("Lookup for “");
      print(lookup.getName());
      println("” blocked. Lookup initiated at:");
      print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
      lookup.getSourceReference().write(this::print, "  ", seen);
      print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.YELLOW).toString());
      print(" is waiting for “");
      print(lookup.getLastName());
      println("” in frame defined at:");
      print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
      lookup.getLastFrame().getSourceReference().write(this::print, "  ", seen);
    }
    clearInFlight();
  }

  @Override
  public void reportLookupError(BaseLookup lookup, String failType) {
    dirty = true;
    print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.RED).toString());
    if (failType == null) {
      print("Undefined name “");
      print(lookup.getName());
      println("”. Lookup was as follows:");
    } else {
      print("Non-frame type ");
      print(failType);
      print(" while resolving name “");
      print(lookup.getName());
      println("”. Lookup was as follows:");
    }
    print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
    final int colWidth =
        IntStream.concat(
                IntStream.of((int) Math.log10(lookup.getFrameCount()) + 1, 3),
                lookup.names().mapToInt(String::length))
            .max()
            .getAsInt();
    lookup
        .names()
        .forEach(
            name -> {
              print("│");
              print(pad(name, colWidth));
            });
    println("│");
    IntStream.range(0, lookup.getNameCount())
        .mapToObj(i -> (i == 0 ? "├" : "┼") + String.join("", Collections.nCopies(colWidth, "─")))
        .forEach(this::print);
    println("┤");
    final Map<Frame, String> knownFrames = new HashMap<>();
    final List<Frame> frameList = new ArrayList<>();
    final String nullText = pad("│ ", colWidth + 2);
    for (int frameIt = 0; frameIt < lookup.getFrameCount(); frameIt++) {
      for (int name = 0; name < lookup.getNameCount(); name++) {
        final Frame frame = lookup.get(name, frameIt);
        if (frame == null) {
          print(nullText);
          continue;
        }
        if (!knownFrames.containsKey(frame)) {
          frameList.add(frame);
          knownFrames.put(frame, pad(Integer.toString(frameList.size()), colWidth));
        }
        print("│");
        print(knownFrames.get(frame));
      }
      println("│");
    }
    final Set<SourceReference> seen = new HashSet<>();
    print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLUE).toString());
    println("Lookup happened here:");
    print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
    lookup.getSourceReference().write(this::print, "  ", seen);
    for (int it = 0; it < frameList.size(); it++) {
      print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.YELLOW).toString());
      print("Frame ");
      print(Integer.toString(it + 1));
      println(" defined:");
      print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
      frameList.get(it).getSourceReference().write(this::print, "  ", seen);
    }
  }

  @Override
  public void reportOtherError(SourceReference reference, String message) {
    dirty = true;
    print(Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLUE).toString());
    println(message);
    print(Ansi.ansi().a(Ansi.Attribute.RESET).toString());
    reference.write(this::print, "  ");
  }
}
