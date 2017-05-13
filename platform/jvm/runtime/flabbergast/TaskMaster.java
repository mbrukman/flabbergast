package flabbergast;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

/** Scheduler for computations. */
public abstract class TaskMaster implements Iterable<BaseLookup> {

  private interface ReportError {
    void invoke(String errorMessage);
  }

  private static final ServiceLoader<UriService> URI_SERVICES =
      ServiceLoader.load(UriService.class);

  public static boolean verifySymbol(String str) {
    return verifySymbol(str, msg -> {});
  }

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
          break;
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
    return verifySymbol(strish.toString(), msg -> {});
  }

  public static void verifySymbolOrThrow(String str) {
    verifySymbol(
        str,
        msg -> {
          throw new IllegalArgumentException(msg);
        });
  }

  private final Queue<Future> computations = new LinkedList<>();

  private final Map<String, Future> externalCache = new HashMap<>();

  private final ArrayList<UriHandler> handlers = new ArrayList<>();

  /** These are computations that have not completed. */
  private final Set<BaseLookup> inflight = new HashSet<>();

  private final AtomicInteger nextId = new AtomicInteger();

  public TaskMaster() {}

  void addAllUriHandlers(ResourcePathFinder finder, EnumSet<LoadRule> flags) {
    StreamSupport.stream(URI_SERVICES.spliterator(), false)
        .map(service -> service.create(finder, flags))
        .filter(Objects::nonNull)
        .forEach(this::addUriHandler);
  }

  public void addUriHandler(UriHandler handler) {
    handlers.add(handler);
  }

  protected void clearInFlight() {
    inflight.clear();
  }

  public void getExternal(String uri, ConsumeResult target) {
    if (externalCache.containsKey(uri)) {
      externalCache.get(uri).listen(target);
      return;
    }
    Maybe<URI> address = Maybe.of(uri).map(URI::new);
    Maybe<Future> initial =
        address
            .filter(x -> x.getScheme().equals("lib"))
            .map(URI::getSchemeSpecificPart)
            .filter(
                x ->
                    x.length() == 0
                        || x.chars().anyMatch(c -> c != '/' || !Character.isLetterOrDigit(c)))
            .map(
                x ->
                    new FailureFuture(
                        this,
                        new NativeSourceReference(uri),
                        String.format("“%s” is not a valid library name.", x)));

    Future result =
        Maybe.reduce(
                initial,
                handlers
                    .stream()
                    .map(handler -> () -> address.flatMap(x -> handler.resolveUri(this, x))))
            .orElseGet(
                () ->
                    new FailureFuture(
                        this,
                        new NativeSourceReference(uri),
                        String.format("Unable to resolve URI “%s”.", uri)));
    externalCache.put(uri, result);
    result.listen(target);
  }

  Set<BaseLookup> getInflight() {
    return inflight;
  }

  public boolean hasInflightLookups() {
    return !inflight.isEmpty();
  }

  @Override
  public Iterator<BaseLookup> iterator() {
    return inflight.iterator();
  }

  long nextId() {
    return nextId.getAndIncrement();
  }

  /** Report an error during lookup. */
  public void reportLookupError(BaseLookup lookup, String failType) {
    if (failType == null) {
      reportOtherError(
          lookup.getSourceReference(),
          String.format("Undefined name %s”. Lookup was as follows:", lookup.getName()));
    } else {
      reportOtherError(
          lookup.getSourceReference(),
          String.format(
              "Non-frame type %s while resolving name “%s”. Lookup was as follows:",
              failType, lookup.getName()));
    }
  }

  /** Report an error during execution of the program. */
  public abstract void reportOtherError(SourceReference reference, String message);

  /** Perform computations until the Flabbergast program is complete or deadlocked. */
  public void run() {
    Collections.sort(handlers, (a, b) -> a.getPriority() - b.getPriority());
    while (!computations.isEmpty()) {
      final Future task = computations.poll();
      task.compute();
    }
  }

  /** Add a computation to be executed. */
  void slot(Future computation) {
    computations.offer(computation);
  }

  public boolean verifySymbol(final SourceReference sourceReference, String str) {
    return verifySymbol(str, errorMessage -> reportOtherError(sourceReference, errorMessage));
  }

  public boolean verifySymbol(final SourceReference sourceReference, Stringish strish) {
    return verifySymbol(sourceReference, strish.toString());
  }
}
