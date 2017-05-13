package flabbergast;

import java.lang.reflect.InvocationTargetException;

public class Instantiator extends DynamicBuildCollector<Future> {
  private final ConsumeResult consumer;
  private final TaskMaster taskMaster;

  Instantiator(ErrorCollector errorCollector, TaskMaster taskMaster, ConsumeResult consumer) {
    super(Future.class, new DynamicClassLoader(), errorCollector);
    this.taskMaster = taskMaster;
    this.consumer = consumer;
  }

  @Override
  protected void emit(Class<? extends Future> output) {
    try {
      output.getConstructor(TaskMaster.class).newInstance(taskMaster).listen(consumer);
    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException e) {
      taskMaster.reportOtherError(
          new NativeSourceReference("compiler"),
          "Internal error: generated output cannot be instantiated: " + e.getMessage());
    }
  }
}
