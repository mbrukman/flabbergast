package flabbergast;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MergeLookup extends BaseLookup {

  private class MergeAttempt extends Attempt {
    Frame resultFrame;

    public MergeAttempt(int name, int frame, Frame sourceFrame) {
      super(name, frame, sourceFrame);
    }

    @Override
    public void consume(Any result) {
      result.accept(
          new AcceptOrFail() {
            @Override
            public void accept(Frame value) {
              resultFrame = value;
              if (getName() == getNameCount() - 1) {
                List<String> names =
                    value.stream().filter(name -> !builder.has(name)).collect(Collectors.toList());
                if (names.isEmpty()) {
                  activateNext();
                  return;
                }
                AtomicInteger interlock = new AtomicInteger(names.size());
                names.forEach(
                    name ->
                        value.get(
                            name,
                            result -> {
                              builder.set(name, result);
                              if (interlock.decrementAndGet() == 0) {
                                activateNext();
                              }
                            }));

              } else {
                if (resultFrame.get(
                    MergeLookup.this.getName(getName() + 1),
                    new MergeAttempt(getName() + 1, getFrame(), resultFrame))) {
                  return;
                }
                activateNext();
              }
            }

            @Override
            protected void fail(String type) {
              taskMaster.reportLookupError(MergeLookup.this, type);
            }
          });
    }
  }

  private final ValueBuilder builder = new ValueBuilder();

  private final Context context;

  private int frameIndex = 0;

  public MergeLookup(
      TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names) {
    super(taskMaster, sourceReference, context, names);
    this.context = context;
  }

  private void activateNext() {
    while (frameIndex < getFrameCount()) {
      final int index = frameIndex++;
      if (getFrame(index).get(getName(0), new MergeAttempt(0, index, getFrame(index)))) {
        return;
      }
    }
    emit(Any.of(Frame.create(getTaskMaster(), getSourceReference(), context, null, builder)));
  }

  @Override
  protected void run() {
    activateNext();
  }
}
