package flabbergast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Scheduler for computations. */
public abstract class TaskMaster implements Iterable<Lookup> {
  public enum LibraryFailure {
    BAD_NAME,
    CORRUPT,
    MISSING
  }

  private interface ReportError {
    public void invoke(String error_msg);
  }

  private static ServiceLoader<UriService> URI_SERVICES = ServiceLoader.load(UriService.class);

  private static boolean verifySymbol(String str, ReportError error) {
    if (str.length() < 1) {
      error.invoke("An attribute name cannot be empty.");
      return false;
    }
    switch (Character.getType(str.charAt(0))) {
      case Character.LOWERCASE_LETTER:
      case Character.OTHER_LETTER:
        break;
      default:
        error.invoke(
            String.format(
                "The name “%s” is unbecoming of an attribute; it cannot start with “%s”.",
                str, str.charAt(0)));
        return false;
    }
    for (int it = 1; it < str.length(); it++) {
      if (str.charAt(it) == '_') {
        continue;
      }
      switch (Character.getType(str.charAt(it))) {
        case Character.DECIMAL_DIGIT_NUMBER:
        case Character.LETTER_NUMBER:
        case Character.LOWERCASE_LETTER:
        case Character.OTHER_LETTER:
        case Character.OTHER_NUMBER:
        case Character.TITLECASE_LETTER:
        case Character.UPPERCASE_LETTER:
          continue;
        default:
          error.invoke(
              String.format(
                  "The name “%s” is unbecoming of an attribute; it cannot contain “%s”.",
                  str, str.charAt(it)));
          return false;
      }
    }
    return true;
  }

  public static boolean verifySymbol(Stringish strish) {
    return verifySymbol(strish.toString(), (msg) -> {});
  }

  public static void verifySymbolOrThrow(String str) {
    verifySymbol(
        str,
        (msg) -> {
          throw new IllegalArgumentException(msg);
        });
  }

  private Queue<Future> computations = new LinkedList<Future>();

  private Map<String, Future> external_cache = new HashMap<String, Future>();

  private ArrayList<UriHandler> handlers = new ArrayList<UriHandler>();

  /** These are computations that have not completed. */
  private Set<Lookup> inflight = new HashSet<Lookup>();

  private AtomicInteger next_id = new AtomicInteger();

  public TaskMaster() {}

  void addAllUriHandlers(ResourcePathFinder finder, EnumSet<LoadRule> flags) {
    for (UriService service : URI_SERVICES) {
      UriHandler handler = service.create(finder, flags);
      if (handler != null) {
        addUriHandler(handler);
      }
    }
    if (!flags.contains(LoadRule.SANDBOXED)) {
      addUriHandler(SettingsHandler.INSTANCE);
      addUriHandler(EnvironmentUriHandler.INSTANCE);
      addUriHandler(FtpHandler.INSTANCE);
      addUriHandler(HttpHandler.INSTANCE);
      addUriHandler(FileHandler.INSTANCE);
      ResourceHandler resource_handler = new ResourceHandler();
      resource_handler.setFinder(finder);
      addUriHandler(resource_handler);
    }

    addUriHandler(BuiltInLibraries.INSTANCE);
    addUriHandler(StandardInterop.INSTANCE);
    addUriHandler(new CurrentInformation(flags.contains(LoadRule.PRECOMPILED)));
    if (flags.contains(LoadRule.PRECOMPILED)) {
      LoadPrecompiledLibraries precomp = new LoadPrecompiledLibraries();
      addUriHandler(precomp);
      precomp.setFinder(finder);
    }
  }

  public void addUriHandler(UriHandler handler) {
    handlers.add(handler);
  }

  public void addUriHandler(UriLoader handler) {
    handlers.add(new UriInstantiator(handler));
  }

  protected void clearInFlight() {
    inflight.clear();
  }

  public void getExternal(String uri, ConsumeResult target) {
    if (external_cache.containsKey(uri)) {
      external_cache.get(uri).listen(target);
      return;
    }
    if (uri.startsWith("lib:")) {
      if (uri.length() < 5) {
        reportExternalError(uri, LibraryFailure.BAD_NAME);
        external_cache.put(uri, BlackholeFuture.INSTANCE);
        return;
      }
      for (int it = 5; it < uri.length(); it++) {
        if (uri.charAt(it) != '/' && !Character.isLetterOrDigit(uri.charAt(it))) {
          reportExternalError(uri, LibraryFailure.BAD_NAME);
          external_cache.put(uri, BlackholeFuture.INSTANCE);
          return;
        }
      }
    }

    for (UriHandler handler : handlers) {
      Ptr<LibraryFailure> reason = new Ptr<LibraryFailure>();
      Future computation = handler.resolveUri(this, uri, reason);
      if (reason.get() != null && reason.get() != LibraryFailure.MISSING) {
        reportExternalError(uri, reason.get());
        external_cache.put(uri, BlackholeFuture.INSTANCE);
        return;
      }
      if (computation != null) {
        external_cache.put(uri, computation);
        computation.listen(target);
        return;
      }
    }
    reportExternalError(uri, LibraryFailure.MISSING);
    external_cache.put(uri, BlackholeFuture.INSTANCE);
  }

  public boolean hasInflightLookups() {
    return inflight.size() > 0;
  }

  @Override
  public Iterator<Lookup> iterator() {
    return inflight.iterator();
  }

  public long nextId() {
    return next_id.getAndIncrement();
  }

  public abstract void reportExternalError(String uri, LibraryFailure reason);

  /** Report an error during lookup. */
  public void reportLookupError(Lookup lookup, Class<?> fail_type) {
    if (fail_type == null) {
      reportOtherError(
          lookup.getSourceReference(),
          String.format("Undefined name %s”. Lookup was as follows:", lookup.getName()));
    } else {
      reportOtherError(
          lookup.getSourceReference(),
          String.format(
              "Non-frame type %s while resolving name “%s”. Lookup was as follows:",
              fail_type, lookup.getName()));
    }
  }

  /** Report an error during execution of the program. */
  public abstract void reportOtherError(SourceReference reference, String message);

  /** Perform computations until the Flabbergast program is complete or deadlocked. */
  public void run() {
    Collections.sort(handlers, (a, b) -> a.getPriority() - b.getPriority());
    while (!computations.isEmpty()) {
      Future task = computations.poll();
      task.compute();
    }
  }

  /** Add a computation to be executed. */
  public void slot(final Future computation) {
    if (computation instanceof Lookup && !inflight.contains(computation)) {
      inflight.add((Lookup) computation);
      computation.listenDelayed(result -> inflight.remove(computation));
    }
    computations.offer(computation);
  }

  public boolean verifySymbol(final SourceReference source_reference, String str) {
    return verifySymbol(
        str,
        new ReportError() {
          @Override
          public void invoke(String error_msg) {
            reportOtherError(source_reference, error_msg);
          }
        });
  }

  public boolean verifySymbol(final SourceReference source_reference, Stringish strish) {
    return verifySymbol(source_reference, strish.toString());
  }
}
