package flabbergast;

import java.util.HashMap;
import java.util.Map;

/** A pretty-printer for console output */
public abstract class ElaboratePrinter {
  private class PrintVisitor implements Future.CheckFuture {
    private final String prefix;

    public PrintVisitor(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public void accept() {
      write("Null\n");
    }

    @Override
    public void accept(boolean result) {
      write(result ? "True\n" : "False\n");
    }

    @Override
    public void accept(byte[] result) {
      write(Integer.toString(result.length));
      write(" bytes of Unspeakable Horror\n");
    }

    @Override
    public void accept(double result) {
      write(result + "\n");
    }

    @Override
    public void accept(Frame f) {
      if (seen.containsKey(f)) {
        write(f.getId().toString());
        write(" # Frame ");
        write(seen.get(f));
      } else {
        write("{ # Frame ");
        final String id = Integer.toString(seen.size());
        write(id);
        write("\n");
        seen.put(f, id);
        final PrintVisitor visitor = new PrintVisitor(prefix + "  ");
        f.stream()
            .forEach(
                name -> {
                  write(prefix);
                  write(name);
                  write(" : ");
                  f.get(name).visit(visitor);
                });
        write(prefix);
        write("}\n");
      }
    }

    @Override
    public void accept(long result) {
      write(result + "\n");
    }

    @Override
    public void accept(LookupHandler value) {
      write("LookupHandler #");
      write(value.description());
      write("\n");
    }

    @Override
    public void accept(Stringish result) {
      write("\"");
      final String s = result.toString();
      for (int it = 0; it < s.length(); it++) {
        if (s.charAt(it) == 7) {
          write("\\a");
        } else if (s.charAt(it) == 8) {
          write("\\b");
        } else if (s.charAt(it) == 12) {
          write("\\f");
        } else if (s.charAt(it) == 10) {
          write("\\n");
        } else if (s.charAt(it) == 13) {
          write("\\r");
        } else if (s.charAt(it) == 9) {
          write("\\t");
        } else if (s.charAt(it) == 11) {
          write("\\v");
        } else if (s.charAt(it) == 34) {
          write("\\\"");
        } else if (s.charAt(it) == 92) {
          write("\\\\");
        } else if (s.charAt(it) < 16) {
          write("\\x0");
          write(Integer.toHexString(s.charAt(it)));
        } else if (s.charAt(it) < 32) {
          write("\\x");
          write(Integer.toHexString(s.charAt(it)));
        } else {
          write(s.substring(it, it + 1));
        }
      }
      write("\"\n");
    }

    @Override
    public void accept(Template template) {
      write("Template\n");
      for (final String name : template) {
        write(" ");
        write(name);
      }
      write("\n");
    }

    @Override
    public void unfinished() {
      write("<unfinished>");
      write("\n");
    }
  }

  private final Map<Frame, String> seen = new HashMap<>();

  protected void print(Future future) {
    future.visit(new PrintVisitor(""));
  }

  protected abstract void write(String string);
}
