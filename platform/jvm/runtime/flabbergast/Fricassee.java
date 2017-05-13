package flabbergast;

import flabbergast.AssistedFuture.Matcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Implementation of Flabbergast's fricassée operations */
public abstract class Fricassee {
  public interface ConsumeFrame {
    void consume(Frame frame);
  }

  private interface FetchDefinition {
    void fetch(long ordinal, String name, ConsumeResult output);
  }

  private interface FetchDefinitionPrototype {
    void prepare(
        TaskMaster taskMaster,
        SourceReference sourceReference,
        Context context,
        Frame self,
        Set<String> attributes,
        Consumer<FetchDefinition> consumer);
  }

  public final class Flatten extends Fricassee {
    private final int endColumn;
    private final int endLine;
    private final String filename;
    private final Fricassee owner;
    private final Map<String, FetchDefinitionPrototype> sources = new HashMap<>();
    private final int startColumn;
    private final int startLine;

    public Flatten(
        Fricassee owner,
        String filename,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn) {
      this.owner = owner;
      this.filename = filename;
      this.startLine = startLine;
      this.startColumn = startColumn;
      this.endLine = endLine;
      this.endColumn = endColumn;
    }

    /** Add definition yielding a frame for a particular attribute name. */
    public void add(String name, Definition definition) {
      sources.put(
          name,
          (taskMaster, sourceReference, context, self, attributes, consumer) ->
              definition
                  .invoke(taskMaster, sourceReference, context, self)
                  .listen(
                      new AcceptOrFail() {
                        @Override
                        public void accept(Frame frame) {
                          frame.stream().forEach(attributes::add);
                          consumer.accept(
                              (ordinal, attribute, output) -> frame.getOrNull(attribute, output));
                        }

                        @Override
                        protected void fail(String type) {
                          taskMaster.reportOtherError(
                              sourceReference,
                              String.format(
                                  "In “Flatten” clause, attribute “%s” is %s, but Frame was expected.",
                                  name, type));
                        }
                      }));
    }

    public void add(Stringish name, Definition definition) {
      add(name.toString(), definition);
    }

    /** Add a “Name” definition. */
    public void addName(String name) {
      sources.put(
          name,
          (taskMaster, sourceReference, context, self, attributes, consumer) ->
              consumer.accept((ordinal, attribute, output) -> output.consume(Any.of(attribute))));
    }

    public void addName(Stringish name) {
      addName(name.toString());
    }

    /** Add an “Ordinal” definition. */
    public void addOrdinal(String name) {
      sources.put(
          name,
          (taskMaster, sourceReference, context, self, attributes, consumer) ->
              consumer.accept((ordinal, attribute, output) -> output.consume(Any.of(ordinal))));
    }

    public void addOrdinal(Stringish name) {
      addOrdinal(name.toString());
    }

    @Override
    void setup(Sink sink) {
      owner.setup(
          new Transform(sink) {
            private Context innerContext;
            private SourceReference innerSourceReference;
            private final Map<String, FetchDefinition> innerSources = new HashMap<>();
            private Iterator<String> iterator;
            private int ordinal;

            @Override
            public void accept(SourceReference sourceReference, Context context) {
              if (context == null) {
                emit(null, null);
                return;
              }
              innerSourceReference = sourceReference;
              innerContext = context;
              final AtomicInteger interlock = new AtomicInteger(sources.size());
              final Set<String> attributes = new TreeSet<>();
              for (final Entry<String, FetchDefinitionPrototype> source : sources.entrySet()) {
                source
                    .getValue()
                    .prepare(
                        getTaskMaster(),
                        sourceReference,
                        context,
                        getSelf(),
                        attributes,
                        fetcher -> {
                          innerSources.put(source.getKey(), fetcher);
                          if (interlock.decrementAndGet() == 0) {
                            iterator = attributes.iterator();
                            pullNext();
                          }
                        });
              }
            }

            @Override
            void pullNext() {
              if (iterator == null || !iterator.hasNext()) {
                nextInput();
                return;
              }
              final String attribute = iterator.next();
              ordinal++;
              final SourceReference junctionReference =
                  new BasicSourceReference(
                      String.format("fricassée flatten iteration %d “%s”", ordinal, attribute),
                      filename,
                      startLine,
                      startColumn,
                      endLine,
                      endColumn,
                      innerSourceReference);
              final AtomicInteger interlock = new AtomicInteger(sources.size());
              final ValueBuilder builder = new ValueBuilder();
              for (final Entry<String, FetchDefinition> source : innerSources.entrySet()) {
                source
                    .getValue()
                    .fetch(
                        ordinal,
                        attribute,
                        result -> {
                          builder.set(source.getKey(), result);
                          if (interlock.decrementAndGet() == 0) {
                            sink.accept(
                                junctionReference,
                                Frame.create(
                                        getTaskMaster(),
                                        junctionReference,
                                        innerContext,
                                        null,
                                        builder)
                                    .getContext());
                          }
                        });
              }
            }
          });
    }
  }

  public final class GroupBy extends Fricassee {
    private final Map<String, Definition> collections = new HashMap<>();
    private final List<GroupByDiscriminator> discriminators = new ArrayList<>();

    private final GroupByNameGenerator nameGenerator;
    private final Fricassee owner;

    public GroupBy(Fricassee owner, Definition namer) {
      this.owner = owner;
      if (namer == null) {
        nameGenerator =
            (taskMaster, sourceReference, context, self, iteration, output) ->
                output.accept(SupportFunctions.ordinalNameStr(iteration));
      } else {
        nameGenerator =
            (taskMaster, sourceReference, context, self, iteration, output) ->
                namer
                    .invoke(taskMaster, sourceReference, context, self)
                    .listen(
                        new AcceptOrFail() {
                          @Override
                          public void accept(long value) {
                            output.accept(SupportFunctions.ordinalNameStr(value));
                          }

                          @Override
                          public void accept(Stringish value) {
                            String str = value.toString();
                            if (taskMaster.verifySymbol(sourceReference, str)) {
                              output.accept(str);
                            }
                          }

                          @Override
                          protected void fail(String type) {
                            taskMaster.reportOtherError(
                                sourceReference,
                                String.format(
                                    "Expected Str (symbol) or Int for “Group By” name, but got %s",
                                    type));
                          }
                        });
      }
    }

    public void addCollection(String name, Definition clause) {
      collections.put(name, clause);
    }

    public void addDisciminatorByBool(String name, Definition clause) {
      addDiscriminator(
          AssistedFuture.asBool(true), (a, b) -> (a ? 1 : 0) - (b ? 1 : 0), Any::of, name, clause);
    }

    private <T> void addDiscriminator(
        Matcher<T> matcher,
        Comparator<T> comparator,
        Function<T, Any> packer,
        String name,
        Definition clause) {
      discriminators.add(
          (taskMaster, sourceReference, context, self, root, output) ->
              clause
                  .invoke(taskMaster, sourceReference, context, self)
                  .listen(
                      matcher.prepare(
                          x ->
                              output.accept(
                                  new GroupByKeyValue<>(name, comparator, packer, x, root)),
                          (actual, expected) ->
                              taskMaster.reportOtherError(
                                  sourceReference,
                                  String.format(
                                      "Expected %s for “%s” in Group By, but got %s.",
                                      expected, name, actual)))));
    }

    public void addDiscriminatorByFloat(String name, Definition clause) {
      addDiscriminator(AssistedFuture.asFloat(true), Double::compareTo, Any::of, name, clause);
    }

    public void addDiscriminatorByInt(String name, Definition clause) {
      addDiscriminator(AssistedFuture.asInt(true), Long::compareTo, Any::of, name, clause);
    }

    public void addDiscriminatorByStr(String name, Definition clause) {
      addDiscriminator(AssistedFuture.asStr(true), Stringish::compareTo, Any::of, name, clause);
    }

    @Override
    void setup(Sink sink) {
      owner.setup(
          new Transform(sink) {

            class GroupedFrameBuilder {
              ValueBuilder commonValues = new ValueBuilder();

              Map<String, ValueBuilder> groupedValues =
                  collections
                      .keySet()
                      .stream()
                      .collect(Collectors.toMap(Function.identity(), name -> new ValueBuilder()));

              private long iteration = 1;

              GroupedFrameBuilder(GroupByKey key) {
                key.populate(commonValues);
              }

              void process(SourceReference sourceReference, Context context) {
                nameGenerator.generate(
                    getTaskMaster(),
                    sourceReference,
                    context,
                    getSelf(),
                    iteration++,
                    currentName -> {
                      AtomicInteger interlock = new AtomicInteger(groupedValues.size());
                      for (Entry<String, ValueBuilder> entry : groupedValues.entrySet()) {
                        collections
                            .get(entry.getKey())
                            .invoke(getTaskMaster(), sourceReference, context, getSelf())
                            .listen(
                                result -> {
                                  entry.getValue().set(currentName, result);
                                  if (interlock.decrementAndGet() == 0) {
                                    nextInput();
                                  }
                                });
                      }
                    });
              }

              Frame toFrame() {
                DefinitionBuilder definitionBuilder = new DefinitionBuilder();
                for (Entry<String, ValueBuilder> entry : groupedValues.entrySet()) {
                  definitionBuilder.set(entry.getKey(), Frame.create(entry.getValue()));
                }
                return Frame.create(
                    getTaskMaster(),
                    getSourceReference(),
                    getContext(),
                    getSelf(),
                    definitionBuilder,
                    commonValues);
              }
            }

            Iterator<GroupedFrameBuilder> iterator = null;

            SortedMap<GroupByKey, GroupedFrameBuilder> knownGroups = new TreeMap<>();

            @Override
            public void accept(SourceReference sourceReference, Context context) {
              if (context == null) {
                iterator = knownGroups.values().iterator();
                pullNext();
                return;
              }
              Iterator<GroupByDiscriminator> discriminator = discriminators.iterator();
              makeKey(
                  sourceReference,
                  context,
                  discriminator,
                  GroupByKey.START,
                  key -> {
                    if (!knownGroups.containsKey(key)) {
                      knownGroups.put(key, new GroupedFrameBuilder(key));
                    }
                    knownGroups.get(key).process(sourceReference, context);
                  });
            }

            void makeKey(
                SourceReference sourceReference,
                Context context,
                Iterator<GroupByDiscriminator> discriminator,
                GroupByKey root,
                Consumer<GroupByKey> consumer) {
              if (!discriminator.hasNext()) {
                consumer.accept(root);
                return;
              }
              discriminator
                  .next()
                  .build(
                      getTaskMaster(),
                      sourceReference,
                      context,
                      getSelf(),
                      root,
                      next -> makeKey(sourceReference, context, discriminator, next, consumer));
            }

            @Override
            void pullNext() {
              if (iterator == null) {
                nextInput();
                return;
              }
              if (iterator.hasNext()) {
                Frame result = iterator.next().toFrame();
                emit(result.getSourceReference(), result.getContext());
              } else {
                emit(null, null);
              }
            }
          });
    }
  }

  private interface GroupByDiscriminator {
    void build(
        TaskMaster taskMaster,
        SourceReference sourceReference,
        Context context,
        Frame self,
        GroupByKey root,
        Consumer<GroupByKey> output);
  }

  private abstract static class GroupByKey implements Comparable<GroupByKey> {
    public static final GroupByKey START =
        new GroupByKey() {
          @Override
          public int compareTo(GroupByKey other) {
            return 0;
          }

          @Override
          public void populate(ValueBuilder builder) {}
        };

    public abstract void populate(ValueBuilder builder);
  }

  private final class GroupByKeyValue<T> extends GroupByKey {
    private final Comparator<T> comparator;
    private final String name;
    private final GroupByKey next;
    private final Function<T, Any> packer;
    private final T value;

    public GroupByKeyValue(
        String name, Comparator<T> comparator, Function<T, Any> packer, T value, GroupByKey next) {
      super();
      this.name = name;
      this.comparator = comparator;
      this.packer = packer;
      this.value = value;
      this.next = next;
    }

    @Override
    public int compareTo(GroupByKey other) {
      @SuppressWarnings("unchecked")
      GroupByKeyValue<T> castOther = (GroupByKeyValue<T>) other;
      int result = comparator.compare(value, castOther.value);
      return result == 0 ? next.compareTo(castOther.next) : result;
    }

    @Override
    public void populate(ValueBuilder builder) {
      builder.set(name, packer.apply(value));
      next.populate(builder);
    }
  }

  private interface GroupByNameGenerator {
    void generate(
        TaskMaster taskMaster,
        SourceReference sourceReference,
        Context context,
        Frame self,
        long iteration,
        Consumer<String> output);
  }

  /**
   * Create a “merge” source that combines the values of several frames
   *
   * <p>This is the usual “For x : y, n : Name”.
   */
  public final class Merge extends Fricassee {
    private final Set<String> attributes = new TreeSet<>();
    private final int endColumn;
    private final int endLine;
    private final String filename;
    private final Map<String, FetchDefinition> sources = new HashMap<>();
    private final int startColumn;
    private final int startLine;

    public Merge(String filename, int startLine, int startColumn, int endLine, int endColumn) {
      this.filename = filename;
      this.startLine = startLine;
      this.startColumn = startColumn;
      this.endLine = endLine;
      this.endColumn = endColumn;
    }

    /** Add frame for a particular attribute name. */
    public void add(String name, Frame frame) {
      sources.put(name, (ordinal, attribute, output) -> frame.getOrNull(attribute, output));
      frame.stream().forEach(attributes::add);
    }

    public void add(Stringish name, Frame frame) {
      add(name.toString(), frame);
    }

    /** Add a “Name” definition. */
    public void addName(String name) {
      sources.put(name, (ordinal, attribute, output) -> output.consume(Any.of(attribute)));
    }

    public void addName(Stringish name) {
      addName(name.toString());
    }

    /** Add an “Ordinal” definition. */
    public void addOrdinal(String name) {
      sources.put(name, (ordinal, attribute, output) -> output.consume(Any.of(ordinal)));
    }

    public void addOrdinal(Stringish name) {
      addOrdinal(name.toString());
    }

    @Override
    void setup(Sink sink) {
      new Source(sink) {
        private final Iterator<String> iterator = attributes.iterator();
        private int ordinal;

        @Override
        void pullNext() {
          if (iterator.hasNext()) {
            final String attribute = iterator.next();
            ordinal++;
            final SourceReference junctionReference =
                new BasicSourceReference(
                    String.format("fricassée iteration %d “%s”", ordinal, attribute),
                    filename,
                    startLine,
                    startColumn,
                    endLine,
                    endColumn,
                    sink.getSourceReference());
            final AtomicInteger interlock = new AtomicInteger(sources.size());
            final ValueBuilder builder = new ValueBuilder();
            for (final Entry<String, FetchDefinition> source : sources.entrySet()) {
              source
                  .getValue()
                  .fetch(
                      ordinal,
                      attribute,
                      result -> {
                        builder.set(source.getKey(), result);
                        if (interlock.decrementAndGet() == 0) {
                          sink.accept(
                              junctionReference,
                              Frame.create(
                                      sink.getTaskMaster(),
                                      junctionReference,
                                      sink.getContext(),
                                      null,
                                      builder)
                                  .getContext());
                        }
                      });
            }
          } else {
            output.accept(null, null);
          }
        }
      };
    }
  }

  private interface Sink {
    void accept(SourceReference sourceReference, Context context);

    void again();

    Context getContext();

    Frame getSelf();

    SourceReference getSourceReference();

    TaskMaster getTaskMaster();

    void wire(Source input);
  }

  private abstract class Source {
    protected final Sink output;

    protected Source(Sink output) {
      this.output = output;
      output.wire(this);
    }

    abstract void pullNext();
  }

  private abstract class TerminalSink extends Future implements Sink {
    private final Context context;
    private Source input;
    private final Frame self;
    private final SourceReference sourceReference;

    protected TerminalSink(
        TaskMaster taskMaster, SourceReference sourceReference, Context context, Frame self) {
      super(taskMaster);
      this.context = context;
      this.sourceReference = sourceReference;
      this.self = self;
    }

    @Override
    public final void accept(SourceReference sourceReference, Context context) {
      if (context == null) {
        process(sourceReference, context);
      } else {
        finalValue(this::complete);
      }
    }

    @Override
    public final void again() {
      slot();
    }

    protected abstract void finalValue(ConsumeResult consumer);

    @Override
    public final Context getContext() {
      return context;
    }

    @Override
    public final Frame getSelf() {
      return self;
    }

    @Override
    public final SourceReference getSourceReference() {
      return sourceReference;
    }

    protected abstract void process(SourceReference sourceReference, Context context);

    protected final void pullNext() {
      slot();
    }

    @Override
    protected final void run() {
      input.pullNext();
    }

    @Override
    public final void wire(Source input) {
      this.input = input;
    }
  }

  private abstract class Transform extends Source implements Sink {
    private Source input;

    protected Transform(Sink output) {
      super(output);
    }

    @Override
    public final void again() {
      output.again();
    }

    protected final void emit(SourceReference sourceReference, Context context) {
      output.accept(sourceReference, context);
    }

    @Override
    public final Context getContext() {
      return output.getContext();
    }

    @Override
    public final Frame getSelf() {
      return output.getSelf();
    }

    @Override
    public final SourceReference getSourceReference() {
      return output.getSourceReference();
    }

    @Override
    public final TaskMaster getTaskMaster() {
      return output.getTaskMaster();
    }

    protected final void nextInput() {
      input.pullNext();
    }

    @Override
    public final void wire(Source input) {
      this.input = input;
    }
  }

  public static Fricassee concat(Fricassee... sources) {
    return new Fricassee() {
      @Override
      void setup(Sink output) {
        Sink proxy =
            new Sink() {
              private final Deque<Source> sources = new ArrayDeque<>();

              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context != null) {
                  output.accept(sourceReference, context);
                } else {
                  sources.pollFirst();
                  if (sources.isEmpty()) {
                    output.accept(null, null);
                  } else {
                    sources.peekFirst().pullNext();
                  }
                }
              }

              @Override
              public void again() {
                output.again();
              }

              @Override
              public Context getContext() {
                return output.getContext();
              }

              @Override
              public Frame getSelf() {
                return output.getSelf();
              }

              @Override
              public SourceReference getSourceReference() {
                return output.getSourceReference();
              }

              @Override
              public TaskMaster getTaskMaster() {
                return output.getTaskMaster();
              }

              @Override
              public void wire(Source input) {
                sources.offerLast(input);
              }
            };

        for (Fricassee source : sources) {
          source.setup(proxy);
        }
      }
    };
  }

  /** Create a “For Each” fricassée source */
  public static Fricassee forEach(
      Frame source, String filename, int startLine, int startColumn, int endLine, int endColumn) {
    return new Fricassee() {
      @Override
      void setup(Sink sink) {
        new Source(sink) {
          private final Iterator<String> iterator = source.stream().iterator();
          private int ordinal = 1;

          @Override
          void pullNext() {
            if (iterator.hasNext()) {
              final String attribute = iterator.next();
              source.get(
                  attribute,
                  result ->
                      new AcceptOrFail() {
                        @Override
                        public void accept(Frame frame) {
                          sink.accept(getSourceReference(), sink.getContext().prepend(frame));
                        }

                        @Override
                        protected void fail(String type) {
                          sink.getTaskMaster()
                              .reportOtherError(
                                  getSourceReference(),
                                  String.format(
                                      "In “Each” clause, attribute “%s” is %s, but Frame was expected.",
                                      attribute, type));
                        }

                        private SourceReference getSourceReference() {
                          return new JunctionReference(
                              String.format(
                                  "fricassée “Each” iteration %d “%s”", ordinal++, attribute),
                              filename,
                              startLine,
                              startColumn,
                              endLine,
                              endColumn,
                              sink.getSourceReference(),
                              source.getSourceReference());
                        }
                      });
            } else {
              output.accept(null, null);
            }
          }
        };
      }
    };
  }

  private Fricassee() {}

  /**
   * Create an “Accumulate” clause
   *
   * @param name the name of the attribute to define
   * @param initialValue the initial value set in the “With” clause
   * @param reducer an definition that calculates the next value
   */
  public final Fricassee accumulate(String name, Any initialValue, Definition reducer) {
    final Fricassee owner = this;
    return new Fricassee() {
      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              private Any currentValue = initialValue;

              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  emit(null, null);
                  return;
                }
                final ValueBuilder builder = new ValueBuilder();
                builder.set(name, currentValue);
                final Context newContext =
                    Frame.create(getTaskMaster(), sourceReference, context, getSelf(), builder)
                        .getContext();
                reducer
                    .invoke(getTaskMaster(), sourceReference, context, getSelf())
                    .listen(
                        result -> {
                          currentValue = result;
                          emit(sourceReference, newContext);
                        });
              }

              @Override
              public void pullNext() {
                nextInput();
              }
            });
      }
    };
  }

  public final Fricassee accumulate(Stringish name, Any initialValue, Definition reducer) {
    return accumulate(name.toString(), initialValue, reducer);
  }

  /**
   * A “Let” clause that defines the supplied values in the existing context
   *
   * @param builder the new attribtes to define
   */
  public final Fricassee let(DefinitionBuilder builder) {
    final Fricassee owner = this;
    return new Fricassee() {
      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  emit(null, null);
                  return;
                }
                emit(
                    sourceReference,
                    Frame.create(getTaskMaster(), sourceReference, context, getSelf(), builder)
                        .getContext());
              }

              @Override
              public void pullNext() {
                nextInput();
              }
            });
      }
    };
  }

  private <T> Fricassee orderBy(
      Matcher<T> matcher, boolean ascending, Comparator<T> comparator, Definition clause) {
    final Fricassee owner = this;
    return new Fricassee() {
      class Item {
        Context context;
        T sortKey;
        SourceReference sourceReference;
      }

      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              private final List<Item> items = new ArrayList<>();
              private Iterator<Item> iterator;

              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  Collections.sort(
                      items,
                      (a, b) -> comparator.compare(a.sortKey, b.sortKey) * (ascending ? 1 : -1));
                  iterator = items.iterator();
                  pullNext();
                } else {
                  clause
                      .invoke(getTaskMaster(), sourceReference, context, getSelf())
                      .listen(
                          matcher.prepare(
                              key -> {
                                final Item item = new Item();
                                item.sortKey = key;
                                item.sourceReference = sourceReference;
                                item.context = context;
                                items.add(item);
                                again();
                              },
                              (actual, expected) ->
                                  getTaskMaster()
                                      .reportOtherError(
                                          sourceReference,
                                          String.format(
                                              "In “Order By” clause, result is %s, but %s was expected.",
                                              actual, expected))));
                }
              }

              @Override
              public void pullNext() {
                if (iterator == null) {
                  nextInput();
                } else {
                  if (iterator.hasNext()) {
                    final Item item = iterator.next();
                    emit(item.sourceReference, item.context);
                  } else {
                    emit(null, null);
                  }
                }
              }
            });
      }
    };
  }

  /**
   * Reorder the attributes based on a boolean value.
   *
   * <p>This is order preserving. That is, if two values have the same sort key, the will appear in
   * the original order.
   *
   * @param clause a definition that computes the sort key. If the sort key is not of the correct
   *     type, an error occurs
   * @param ascending if true, order the result ascending (false through true), false to order
   *     descending
   */
  public final Fricassee orderByBool(boolean ascending, Definition clause) {
    return orderBy(
        AssistedFuture.asBool(false), ascending, (a, b) -> (a ? 1 : 0) - (b ? 1 : 0), clause);
  }

  /**
   * Reorder the attributes based on a floating-point value.
   *
   * <p>NaN is considered the largest value.
   *
   * <p>This is order preserving. That is, if two values have the same sort key, the will appear in
   * the original order.
   *
   * @param clause a definition that computes the sort key. If the sort key is not of the correct
   *     type, an error occurs
   * @param ascending if true, order the result ascending (-Inf through +Inf), false to order
   *     descending
   */
  public final Fricassee orderByFloat(boolean ascending, Definition clause) {
    return orderBy(AssistedFuture.asFloat(false), ascending, Double::compareTo, clause);
  }

  /**
   * Reorder the attributes based on an integer value.
   *
   * <p>This is order preserving. That is, if two values have the same sort key, the will appear in
   * the original order.
   *
   * @param clause a definition that computes the sort key. If the sort key is not of the correct
   *     type, an error occurs
   * @param ascending if true, order the result ascending (IntMin through IntMax), false to order
   *     descending
   */
  public final Fricassee orderByInt(boolean ascending, Definition clause) {
    return orderBy(AssistedFuture.asInt(false), ascending, Long::compareTo, clause);
  }

  /**
   * Reorder the attributes based on a string value.
   *
   * <p>This sorting is lexicographical for the current locale.
   *
   * <p>This is order preserving. That is, if two values have the same sort key, the will appear in
   * the original order.
   *
   * @param clause a definition that computes the sort key. If the sort key is not of the correct
   *     type, an error occurs
   * @param ascending if true, order the result ascending, false to order descending; this is
   *     locale-dependant
   */
  public final Fricassee orderByStr(boolean ascending, Definition clause) {
    return orderBy(AssistedFuture.asStr(false), ascending, Stringish::compareTo, clause);
  }

  /**
   * Reduce the fricassée values to a single value
   *
   * @param taskMaster the scheduler for all the operations in this fricassée chain
   * @param self the self frame for all the operations in this fricassée chain
   * @param name the name of the attribute to define
   * @param initialValue the initial value to use (i.e., the “With” clause
   * @param reducer an definition that calculates the next value
   * @return the last output of the reducer, or the initial value if the fricassée had no values
   */
  public final Future reduce(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      String name,
      Any initialValue,
      Definition reducer) {
    final TerminalSink sink =
        new TerminalSink(taskMaster, sourceReference, context, self) {
          Any currentValue = initialValue;

          @Override
          protected void finalValue(ConsumeResult consumer) {
            consumer.consume(currentValue);
          }

          @Override
          public void process(SourceReference sourceReference, Context context) {
            final ValueBuilder builder = new ValueBuilder();
            builder.set(name, currentValue);
            final Context newContext =
                Frame.create(getTaskMaster(), sourceReference, context, getSelf(), builder)
                    .getContext();
            reducer
                .invoke(getTaskMaster(), sourceReference, newContext, getSelf())
                .listen(
                    result -> {
                      currentValue = result;
                      pullNext();
                    });
          }
        };
    setup(sink);
    return sink;
  }

  public final Future reduce(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Stringish name,
      Any initialValue,
      Definition reducer) {
    return reduce(
        taskMaster, sourceReference, context, self, name.toString(), initialValue, reducer);
  }

  /** Reverse the order of the items in the fricassée operation */
  public final Fricassee reverse() {
    final Fricassee owner = this;
    return new Fricassee() {
      private boolean collect = true;
      private final Deque<Runnable> results = new ArrayDeque<>();

      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  collect = false;
                  emit(sourceReference, context);
                } else {
                  results.push(() -> emit(sourceReference, context));
                  again();
                }
              }

              @Override
              public void pullNext() {
                if (collect) {
                  nextInput();
                } else if (results.isEmpty()) {
                  emit(null, null);
                } else {
                  results.pop().run();
                }
              }
            });
      }
    };
  }

  abstract void setup(Sink sink);

  public Fricassee shuffle() {
    final Fricassee owner = this;
    return new Fricassee() {
      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              private final List<Runnable> items = new ArrayList<>();
              private Iterator<Runnable> iterator;

              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  Collections.shuffle(items);
                  iterator = items.iterator();
                  pullNext();
                } else {
                  items.add(() -> emit(sourceReference, context));
                }
              }

              @Override
              public void pullNext() {
                if (iterator == null) {
                  nextInput();
                } else {
                  if (iterator.hasNext()) {
                    iterator.next().run();
                  } else {
                    emit(null, null);
                  }
                }
              }
            });
      }
    };
  }

  private final Future single(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Definition getter,
      Consumer<ConsumeResult> emptyHandler) {
    final TerminalSink sink =
        new TerminalSink(taskMaster, sourceReference, context, self) {
          Any currentValue;

          @Override
          protected void finalValue(ConsumeResult consumer) {
            if (currentValue == null) {
              emptyHandler.accept(consumer);
            } else {
              consumer.consume(currentValue);
            }
          }

          @Override
          public void process(SourceReference sourceReference, Context context) {
            if (currentValue != null) {
              getTaskMaster()
                  .reportOtherError(
                      getSourceReference(), "Multiple values for “Single” fricassée operation.");
              return;
            }
            getter
                .invoke(getTaskMaster(), sourceReference, context, getSelf())
                .listen(
                    result -> {
                      currentValue = result;
                      pullNext();
                    });
          }
        };
    setup(sink);
    return sink;
  }

  /**
   * Get a single value from the fricassée
   *
   * <p>If multiple values are matched, an error occrus.
   *
   * @param taskMaster the scheduler for all the operations in this fricassée chain
   * @param self the self frame for all the operations in this fricassée chain
   * @param getter the value to return
   * @param defaultValue the value to use if the fricasseé is empty
   */
  public final Future single(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Definition getter,
      Definition defaultValue) {
    return single(
        taskMaster,
        sourceReference,
        context,
        self,
        getter,
        consumer ->
            defaultValue.invoke(taskMaster, sourceReference, context, self).listen(consumer));
  }

  /**
   * Get a single value from the fricassée
   *
   * <p>If multiple values are present, an error occrus. If no values are available, null is
   * returned.
   *
   * @param taskMaster the scheduler for all the operations in this fricassée chain
   * @param self the self frame for all the operations in this fricassée chain
   * @param getter the value to return
   */
  public final Future singleOrNull(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Definition getter) {
    return single(
        taskMaster,
        sourceReference,
        context,
        self,
        getter,
        consumer -> consumer.consume(Any.unit()));
  }

  /**
   * Remove items from the fricassée operation until a condition is satisfied
   *
   * @param clause the clause to decide whether to keep an item or skip; it must return “Bool” or an
   *     error will occur
   */
  public final Fricassee skip(Definition clause) {
    final Fricassee owner = this;
    return new Fricassee() {
      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              boolean allow = false;

              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  emit(null, null);
                  return;
                }
                if (allow) {
                  emit(sourceReference, context);
                  return;
                }

                clause
                    .invoke(getTaskMaster(), sourceReference, context, getSelf())
                    .listen(
                        new AcceptOrFail() {

                          @Override
                          public void accept(boolean filter) {
                            if (filter) {
                              nextInput();
                            } else {
                              emit(sourceReference, context);
                              allow = true;
                            }
                          }

                          @Override
                          protected void fail(String type) {
                            getTaskMaster()
                                .reportOtherError(
                                    sourceReference,
                                    String.format(
                                        "In “Skip” clause, result is %s, but Bool was expected.",
                                        type));
                          }
                        });
              }

              @Override
              public void pullNext() {
                nextInput();
              }
            });
      }
    };
  }

  /**
   * Collect the items of the fricassée operation into a new frame with user-defined attribute names
   *
   * @param taskMaster the scheduler for all the operations in this fricassée chain
   * @param sourceReference the source reference that will be used to create the frame
   * @param context the context that will be used to create the frame
   * @param self the self frame for all the operations in this fricassée chain
   * @param computeName a definition to compute the attribute name of each item in the frame; it
   *     must return “Int” or “Str” and be a valid symbol name
   * @param computeValue a definition to compute the attribute value
   * @return a future that will yield the new frame
   */
  public final Future toFrame(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Definition computeName,
      Definition computeValue) {
    final TerminalSink sink =
        new TerminalSink(taskMaster, sourceReference, context, self) {
          private final ValueBuilder builder = new ValueBuilder();

          @Override
          protected void finalValue(ConsumeResult consumer) {
            consumer.consume(
                Any.of(
                    Frame.create(
                        getTaskMaster(), getSourceReference(), getContext(), getSelf(), builder)));
          }

          @Override
          public void process(SourceReference sourceReference, Context context) {
            computeName
                .invoke(taskMaster, sourceReference, context, getSelf())
                .listen(
                    new AcceptOrFail() {
                      @Override
                      public void accept(long id) {
                        getValue(SupportFunctions.ordinalNameStr(id));
                      }

                      @Override
                      public void accept(Stringish id) {
                        if (!taskMaster.verifySymbol(sourceReference, id)) {
                          return;
                        }
                        getValue(id.toString());
                      }

                      @Override
                      protected void fail(String type) {
                        taskMaster.reportOtherError(
                            sourceReference,
                            String.format(
                                "In “Select” (named), attribute name is %s, but Str or Int was expected.",
                                type));
                      }

                      private void getValue(String attribute) {

                        if (builder.has(attribute)) {
                          taskMaster.reportOtherError(
                              sourceReference,
                              String.format(
                                  "In “Select” (named), duplicate attribute name “%s” in fricassée result.",
                                  attribute));
                          return;
                        }

                        computeValue
                            .invoke(taskMaster, sourceReference, context, self)
                            .listen(
                                value -> {
                                  builder.set(attribute, value);
                                  pullNext();
                                });
                      }
                    });
          }
        };
    setup(sink);
    return sink;
  }

  public final void toFrame(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Definition computeName,
      Definition computeValue,
      ConsumeFrame consumer) {
    Future result = toFrame(taskMaster, sourceReference, context, self, computeName, computeValue);
    result.listen(
        new AcceptOrFail() {
          @Override
          public void accept(Frame frame) {
            consumer.consume(frame);
          }

          @Override
          protected void fail(String type) {
            taskMaster.reportOtherError(
                sourceReference,
                String.format("Internal error: Expected Frame, but got %s.", type));
          }
        });
  }

  /**
   * Collect the items of the fricassée operation into a new frame with automatically generated
   * attribute names
   *
   * @param taskMaster the scheduler for all the operations in this fricassée chain
   * @param sourceReference the source reference that will be used to create the frame
   * @param context the context that will be used to create the frame
   * @param self the self frame for all the operations in this fricassée chain
   * @param compute a definition to compute the attribute value
   * @return a future that will yield the new frame
   */
  public final Future toList(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Definition compute) {
    final TerminalSink sink =
        new TerminalSink(taskMaster, sourceReference, context, self) {
          private final ValueBuilder builder = new ValueBuilder();
          private long index = 1;

          @Override
          protected void finalValue(ConsumeResult consumer) {
            consumer.consume(
                Any.of(
                    Frame.create(
                        getTaskMaster(), getSourceReference(), getContext(), getSelf(), builder)));
          }

          @Override
          public void process(SourceReference sourceReference, Context context) {
            compute
                .invoke(getTaskMaster(), sourceReference, context, getSelf())
                .listen(
                    value -> {
                      builder.set(index++, value);
                      pullNext();
                    });
          }
        };

    setup(sink);
    return sink;
  }

  public final void toList(
      TaskMaster taskMaster,
      SourceReference sourceReference,
      Context context,
      Frame self,
      Definition compute,
      ConsumeFrame consumer) {
    Future result = toList(taskMaster, sourceReference, context, self, compute);
    result.listen(
        new AcceptOrFail() {
          @Override
          public void accept(Frame frame) {
            consumer.consume(frame);
          }

          @Override
          protected void fail(String type) {
            taskMaster.reportOtherError(
                sourceReference,
                String.format("Internal error: Expected Frame, but got %s.", type));
          }
        });
  }

  /**
   * Remove items from the fricassée operation after a limit
   *
   * @param clause the clause to decide whether to keep an item or stop processing; it must return
   *     “Bool” or an error will occur
   */
  public final Fricassee until(Definition clause) {
    final Fricassee owner = this;
    return new Fricassee() {
      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  emit(null, null);
                  return;
                }

                clause
                    .invoke(getTaskMaster(), sourceReference, context, getSelf())
                    .listen(
                        new AcceptOrFail() {

                          @Override
                          public void accept(boolean filter) {
                            if (filter) {
                              emit(null, null);
                            } else {
                              emit(sourceReference, context);
                            }
                          }

                          @Override
                          protected void fail(String type) {
                            getTaskMaster()
                                .reportOtherError(
                                    sourceReference,
                                    String.format(
                                        "In “Until” clause, result is %s, but Bool was expected.",
                                        type));
                          }
                        });
              }

              @Override
              public void pullNext() {
                nextInput();
              }
            });
      }
    };
  }

  /**
   * Conditionally remove some items from the fricassée operation
   *
   * @param clause the clause to decide whether to keep an item; it must return “Bool” or an error
   *     will occur
   */
  public final Fricassee where(Definition clause) {
    final Fricassee owner = this;
    return new Fricassee() {
      @Override
      void setup(Sink sink) {
        owner.setup(
            new Transform(sink) {
              @Override
              public void accept(SourceReference sourceReference, Context context) {
                if (context == null) {
                  emit(null, null);
                  return;
                }

                clause
                    .invoke(getTaskMaster(), sourceReference, context, getSelf())
                    .listen(
                        new AcceptOrFail() {

                          @Override
                          public void accept(boolean filter) {
                            if (filter) {
                              emit(sourceReference, context);
                            } else {
                              again();
                            }
                          }

                          @Override
                          protected void fail(String type) {
                            getTaskMaster()
                                .reportOtherError(
                                    sourceReference,
                                    String.format(
                                        "In “Where” clause, result is %s, but Bool was expected.",
                                        type));
                          }
                        });
              }

              @Override
              public void pullNext() {
                nextInput();
              }
            });
      }
    };
  }
}
