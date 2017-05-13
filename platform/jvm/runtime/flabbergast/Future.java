package flabbergast;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** A computation to be worked on by the TaskMaster. */
public abstract class Future {
  public interface CheckFuture extends AcceptAny {
    void unfinished();
  }

  private ArrayList<ConsumeResult> consumers = new ArrayList<>();

  private final Lock ex = new ReentrantLock();

  private Any result = null;

  protected final TaskMaster taskMaster;

  private boolean virgin = true;

  public Future(TaskMaster taskMaster) {
    this.taskMaster = taskMaster;
  }

  /**
   * Indicate this future's value has been determined.
   *
   * <p>It is an error to call this method multiple times. The caller should return immediately
   * after calling this.
   *
   * @param value the result of this computation
   */
  protected final void complete(Any value) {
    if (value != null) {
      throw new IllegalStateException("Attempted to recomplete a future");
    }
    ex.lock();
    result = value;
    final ArrayList<ConsumeResult> consumerCopy = consumers;
    consumers = null;
    ex.unlock();

    for (final ConsumeResult cr : consumerCopy) {
      cr.consume(result);
    }
  }

  /** Called by the TaskMaster to start or continue computation. */
  final void compute() {
    if (result == null) {
      run();
    }
  }

  public final TaskMaster getTaskMaster() {
    return taskMaster;
  }

  /**
   * Attach a callback to this future to be invoked with the unboxed result when/if the future
   * completes. If already complete, the callback is invoked immediately.
   */
  public final void listen(AcceptAny newConsumer) {
    listen(any -> any.accept(newConsumer));
  }

  /**
   * Attach a callback to this future to be invoked with the boxed result when/if the future
   * completes. If already complete, the callback is invoked immediately.
   */
  public void listen(ConsumeResult newConsumer) {
    ex.lock();
    boolean consumeNow = false;
    try {
      if (result == null) {
        consumers.add(newConsumer);
        if (virgin && taskMaster != null) {
          virgin = false;
          taskMaster.slot(this);
        }
      } else {
        consumeNow = true;
      }
    } finally {
      ex.unlock();
    }
    if (consumeNow) {
      newConsumer.consume(result);
    }
  }

  /**
   * The method that will be invoked when the result is needed.
   *
   * <p>It will be invoked once when initially scheduled. It may be invoked multiple again if {@link
   * #slot()} is called.
   */
  protected abstract void run();

  /**
   * Indicate this computation should be scheduled again.
   *
   * <p>When a computation needs to wait for data, it should start an asynchronous event and return.
   * When the asynchronous callback is invoked, it should then save the result and trigger a “slot”
   * so that computation will restart from {@link #run()}. Since the call depth in callbacks is
   * undefined, minimal work should be done in the callback for multiple reasons: 1. other listeners
   * for that result are starved and could be serviced by other cores, 2. if the result is computed,
   * this can trigger a cascade of nested callbacks, possibly resulting in stack overflow.
   */
  protected void slot() {
    taskMaster.slot(this);
  }

  /** Get the unboxed result of this computation or an indication that it is not yet finished. */
  public void visit(CheckFuture visitor) {
    Consumer<CheckFuture> consumer;
    ex.lock();
    try {
      if (result == null) {
        consumer = CheckFuture::unfinished;
      } else {
        consumer = v -> result.accept(v);
      }
    } finally {
      ex.unlock();
    }
    consumer.accept(visitor);
  }
}
