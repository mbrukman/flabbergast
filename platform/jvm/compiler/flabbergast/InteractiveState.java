package flabbergast;

import java.util.List;
import java.util.stream.Collectors;

public abstract class InteractiveState {
  protected static final Frame EMPTY_ROOT = Frame.create("interactive", "interactive");
  private static final SourceReference sourceReference = new NativeSourceReference("interactive");
  private Frame current;

  private final Frame root;

  protected InteractiveState(Frame root) {
    super();
    this.root = root;
    this.current = root;
  }

  public final Future evaluate(Definition definition) {
    return definition.invoke(getTaskMaster(), sourceReference, current.getContext(), current);
  }

  public Frame getCurrent() {
    return current;
  }

  public Frame getRoot() {
    return root;
  }

  protected abstract TaskMaster getTaskMaster();

  public final void go(Definition definition) {

    evaluate(definition)
        .listen(
            new AcceptOrFail() {

              @Override
              public void accept(Frame value) {
                current = value;
              }

              @Override
              public void accept(Template value) {
                current = value.getContainer();
              }

              @Override
              protected void fail(String type) {
                getTaskMaster()
                    .reportOtherError(
                        sourceReference, String.format("“Go” got %s, but expects Frame.", type));
              }
            });
  }

  public final void help() {
    print("Known commands:\n");
    print("<expr>      Evaluate a Flabbergast expression.\n");
    print(
        "Go <expr>   Change the current frame (This) to some other frame or container of a template.\n");
    print("Help        This dreck.\n");
    print(
        "Home        Change the current frame (This) to the file-level frame of the script provided.\n");
    print("Ls          List the names of the attributes in the current frame (This).\n");
    print("Quit        Stop with the Flabbergast.\n");
    print("Trace       Print the stack trace for the current frame (This).\n");
    print("Up          Change the current frame to the containing frame (Container).\n");
    print("Up <n>      Change the current frame to the n-th containing frame (Container).\n");
  }

  public final void home() {
    current = root;
  }

  public final void ls() {
    printColumns(current.stream().collect(Collectors.toList()));
  }

  protected abstract void print(String value);

  protected abstract void printColumns(List<String> collection);

  public abstract void quit();

  public final void show(Definition definition) {
    evaluate(definition)
        .listen(
            new PrintResult() {

              @Override
              protected boolean writeToFile(String data) {
                print(data);
                print("\n");
                return true;
              }
            });
  }

  public final void trace() {
    current.getSourceReference().write(this::print, "");
  }

  public final void up(int count) {
    while (count-- > 0) {
      current = current.getContainer();
    }
  }
}
