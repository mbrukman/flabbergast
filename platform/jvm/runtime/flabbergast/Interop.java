package flabbergast;

import flabbergast.AssistedFuture.Matcher;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.kohsuke.MetaInfServices;

/**
 * Inject native functions and data into Flabbergast as function-like templates and boxed values,
 * respectively
 *
 * <p>To make use of this class, create a class implementing {@link UriService} annotated with
 * {@link MetaInfServices} that returns a derivative of this class. In the constructor, use the
 * methods in this class to bind native data. Every bound value needs a URI associated with it.
 * Create a library in Flabbergast that pulls in these values using “From interop:x” where “x” is
 * the URI used.
 */
public abstract class Interop implements UriHandler {
  protected interface LookupInstantiator {
    Future lookup(
        TaskMaster taskMaster, SourceReference sourceReference, Context context, String[] names);
  }

  private static final Frame NOTHING = Frame.create("interop", "interop");

  private final Map<String, Future> bindings = new HashMap<>();

  /**
   * Bind a function with one argument to a function-like template
   *
   * @param packer a function to box the result. Probably a method reference of {@link Any#of}.
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param func The function to be called.
   * @param matcher The {@link Matcher} to unbox the Flabbergast value
   * @param parameter The Flabbergast name to lookup as the parameter
   */
  protected <T, R> void add(
      Function<R, Any> packer, String name, Func<T, R> func, Matcher<T> matcher, String parameter) {
    add(
        name,
        (taskMaster, sourceReference, context, self) ->
            new FunctionInterop<>(
                packer, func, matcher, parameter, taskMaster, sourceReference, context, self));
  }

  /**
   * Bind a function with two arguments to a function-like template
   *
   * @param packer a function to box the result. Probably a method reference of {@link Any#of}.
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param func The function to be called.
   * @param matcher1 The {@link Matcher} to unbox the Flabbergast value for the first parameter
   * @param parameter1 The Flabbergast name to lookup as the first parameter
   * @param matcher2 The {@link Matcher} to unbox the Flabbergast value as the second parameter
   * @param parameter2 The Flabbergast name to lookup as the second parameter
   */
  protected <T1, T2, R> void add(
      Function<R, Any> packer,
      String name,
      Func2<T1, T2, R> func,
      Matcher<T1> matcher1,
      String parameter1,
      Matcher<T2> matcher2,
      String parameter2) {
    add(
        name,
        (taskMaster, sourceReference, context, self) ->
            new FunctionInterop2<>(
                packer,
                func,
                matcher1,
                parameter1,
                matcher2,
                parameter2,
                taskMaster,
                sourceReference,
                context,
                self));
  }

  /**
   * Bind a complex function-like template
   *
   * <p>Since there is a limit to the binding capabilities of the methods provided in this class, a
   * more complex function can be made by extending {@link BaseFunctionInterop}, {@link
   * BaseMapFunctionInterop}, or {@link AssistedFuture} to perform the necessary computation. This
   * method will bind computation as the “value” attribute of a function-like template.
   *
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param compute usually, a reference to the constructor of the appropriate subclass of {@link
   *     BaseFunctionInterop}, {@link BaseMapFunctionInterop}, or {@link AssistedFuture}
   */
  protected void add(String name, Definition compute) {
    final Template tmpl = new Template(NOTHING.getSourceReference(), null, NOTHING);
    tmpl.set("value", compute);
    bindings.put(name, Any.of(tmpl).future());
  }
  /**
   * Bind a frame
   *
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param frame a frame, constructed using {@link Frame#create(String, String,
   *     flabbergast.Frame.Builder...)} or {@link MarshalledFrame#create(String, String, Object,
   *     java.util.stream.Stream)}
   */
  protected void add(String name, Frame frame) {
    bindings.put(name, Any.of(frame).future());
  }

  /**
   * Bind a new lookup handler.
   *
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param handler The lookup handler to bind.
   */
  protected void addHandler(String name, LookupInstantiator handler) {
    bindings.put(
        name,
        Any.of(
                new LookupHandler() {

                  @Override
                  public String description() {
                    return name;
                  }

                  @Override
                  public Future lookup(
                      TaskMaster taskMaster,
                      SourceReference sourceReference,
                      Context context,
                      String[] names) {
                    return handler.lookup(taskMaster, sourceReference, context, names);
                  }
                })
            .future());
  }

  /**
   * Bind a function-like template that does a “map” operation over variadic “args”
   *
   * @param packer a function to box the result. Probably a method reference of {@link Any#of}.
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param func The function to be called.
   * @param matcher The {@link Matcher} to unbox the Flabbergast value for each of the values in
   *     “args”
   */
  protected <T, R> void addMap(
      Function<R, Any> packer, Matcher<T> matcher, String name, Func<T, R> func) {
    add(
        name,
        (taskMaster, sourceReference, context, self) ->
            new MapFunctionInterop<>(
                packer, matcher, func, taskMaster, sourceReference, context, self));
  }

  /**
   * Bind a function-like template that does a “map” operation over variadic “args” with an
   * additional argument that does not vary
   *
   * @param packer a function to box the result. Probably a method reference of {@link Any#of}.
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param func The function to be called.
   * @param matcher The {@link Matcher} to unbox the Flabbergast value for each of the values in
   *     “args”
   * @param parameterMatcher The {@link Matcher} to unbox the Flabbergast value for the fixed
   *     parameter
   * @param parameter The Flabbergast name to lookup as the fixed parameter
   */
  protected <T1, T2, R> void addMap(
      Function<R, Any> packer,
      Matcher<T1> matcher,
      String name,
      Func2<T1, T2, R> func,
      Matcher<T2> parameterMatcher,
      String parameter) {
    add(
        name,
        (taskMaster, sourceReference, context, self) ->
            new MapFunctionInterop2<>(
                packer,
                matcher,
                func,
                parameterMatcher,
                parameter,
                taskMaster,
                sourceReference,
                context,
                self));
  }

  /**
   * Bind a function-like template that does a “map” operation over variadic “args” with two
   * additional arguments that do not vary
   *
   * @param packer a function to box the result. Probably a method reference of {@link Any#of}.
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param func The function to be called.
   * @param matcher The {@link Matcher} to unbox the Flabbergast value for each of the values in
   *     “args”
   * @param parameter1Matcher The {@link Matcher} to unbox the Flabbergast value for the first fixed
   *     parameter
   * @param parameter1 The Flabbergast name to lookup as the first fixed parameter
   * @param parameter2Matcher The {@link Matcher} to unbox the Flabbergast value for the second
   *     fixed parameter
   * @param parameter2 The Flabbergast name to lookup as the second fixed parameter
   */
  protected <T1, T2, T3, R> void addMap(
      Function<R, Any> packer,
      Matcher<T1> matcher,
      String name,
      Func3<T1, T2, T3, R> func,
      Matcher<T2> parameter1Matcher,
      String parameter1,
      Matcher<T3> parameter2Matcher,
      String parameter2) {
    add(
        name,
        (taskMaster, sourceReference, context, self) ->
            new MapFunctionInterop3<>(
                packer,
                matcher,
                func,
                parameter1Matcher,
                parameter1,
                parameter2Matcher,
                parameter2,
                taskMaster,
                sourceReference,
                context,
                self));
  }

  /**
   * Bind a function-like template that does a “map” operation over variadic “args” with three
   * additional arguments that do not vary
   *
   * @param packer a function to box the result. Probably a method reference of {@link Any#of}.
   * @param name The URI to bind to. (i.e., “x/y” for “From interop:x/y”)
   * @param func The function to be called.
   * @param matcher The {@link Matcher} to unbox the Flabbergast value for each of the values in
   *     “args”
   * @param parameter1Matcher The {@link Matcher} to unbox the Flabbergast value for the first fixed
   *     parameter
   * @param parameter1 The Flabbergast name to lookup as the first fixed parameter
   * @param parameter2Matcher The {@link Matcher} to unbox the Flabbergast value for the second
   *     fixed parameter
   * @param parameter2 The Flabbergast name to lookup as the second fixed parameter
   * @param parameter3Matcher The {@link Matcher} to unbox the Flabbergast value for the third fixed
   *     parameter
   * @param parameter3 The Flabbergast name to lookup as the third fixed parameter
   */
  protected <T1, T2, T3, T4, R> void addMap(
      Function<R, Any> packer,
      Matcher<T1> matcher,
      String name,
      Func4<T1, T2, T3, T4, R> func,
      Matcher<T2> parameter1Matcher,
      String parameter1,
      Matcher<T3> parameter2Matcher,
      String parameter2,
      Matcher<T4> parameter3Matcher,
      String parameter3) {
    add(
        name,
        (taskMaster, sourceReference, context, self) ->
            new MapFunctionInterop4<>(
                packer,
                matcher,
                func,
                parameter1Matcher,
                parameter1,
                parameter2Matcher,
                parameter2,
                parameter3Matcher,
                parameter3,
                taskMaster,
                sourceReference,
                context,
                self));
  }

  /**
   * Bind a function that causes a not-implemented error when expanded.
   *
   * <p>This is used for adding bindings in the standard library that are not availble on all
   * platforms.
   *
   * @param name the URI to bind to.
   */
  protected void addMissing(String name) {
    add(
        name,
        FailureFuture.create(
            String.format("This platform has no implementation of “interop:%s”.", name)));
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public String getUriName() {
    return "library bindings";
  }

  @Override
  public final Maybe<Future> resolveUri(TaskMaster master, URI uri) {
    return Maybe.of(uri)
        .filter(x -> x.getScheme().equals("interop"))
        .map(URI::getSchemeSpecificPart)
        .map(bindings::get);
  }
}
