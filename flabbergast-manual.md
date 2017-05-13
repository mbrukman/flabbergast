# The Flabbergast Programming Language
![](https://rawgithub.com/apmasell/flabbergast/master/flabbergast.svg)

Flabbergast is a rather unique programming language. It is best-described as an object-oriented macro system, though it's best not to associate it too much with object-oriented programming. Conceptually, it is based on a proprietary programming language used at Google for creating configurations. At Google, this language is much despised because it is difficult to debug and has many design flaws. However, it is special in that it is a member of a unique language family. It has features from other languages: particularly functional languages and object-oriented languages. Flabbergast aims to be the second in that family of languages and, hopefully, be more loved by being easier to debug, easier to understand, and more semantically sturdy.

Follow along at home with [Flabbergast Fiddle](http://fiddle.flabbergast.org/)!

## A Worked Example: Apache Aurora
Let's start with an example for a simple job that runs on Apache Aurora. Aurora is a long-running job configuration system for Mesos. It's purposes is to collect and dispatch all the needed job information to Mesos. In Aurora, a file consists of many jobs, each with a task to perform. A task can have multiple processes that run inside of it. Most resources are configured on the job level; some on the task level.

Flabbergast is going to describe the Aurora configuration using _templates_ and _frames_ and it will generate a big string, the configuration file for Aurora. If you run the Flabbergast interpreter on these files, it will produce a working Aurora configuration as output.

The first thing needed is some basic plumbing:

    aurora_lib : From lib:apache/aurora # Import the Aurora library.
    hw_file : aurora_lib.aurora_file { # Create an Aurora configuration file
      jobs : [ ] # Aurora requires we provide a list of job. We have none so far.
    }
    value : hw_file.value # Flabbergast is going to dump this string to the output.

This won't do anything useful, but we have a basic skeleton. Let's create a job.

    aurora_lib : From lib:apache/aurora
    hw_file : aurora_lib.aurora_file {
      jobs : [
        job { # Create a job. This could also be `aurora_lib.job`, but we have nothing else named `job`.
          instances : 1 # One replica of this job should run.
          job_name : "hello_world" # Provide a friendly job name.
          task : aurora_lib.task { processes : [] } # Each job needs a task.
        }
      ]
    }
    value : hw_file.value

This will create one job, but this file won't work. A job needs a `cluster` and a `role`; it is also desirable to specify `resources`.

    aurora_lib : From lib:apache/aurora
    cluster : "cluster1"
    role : "jrhacker"
    resources : {
      cpu : 0.1
      ram : 16Mi
      disk : 16Mi
    }
    hw_file : aurora_lib.aurora_file {
      jobs : [
        job {
          instances : 1
          job_name : "hello_world"
          task : aurora_lib.task { processes : [] }
        }
      ]
    }
    value : hw_file.value

I chose to put `cluster`, `role`, and `resources` resources at the top-level. This is so that they are shared between all jobs that I create. It is also completely valid to do:

    aurora_lib : From lib:apache/aurora
    hw_file : aurora_lib.aurora_file {
      resources : {
        cpu : 0.1
        ram : 16Mi
        disk : 16Mi
      }
      jobs : [
        job {
          cluster : "cluster1"
          role : "jrhacker"
          instances : 1
          job_name : "hello_world"
          task : aurora_lib.task { processes : [] }
        }
      ]
    }
    value : hw_file.value

I can even split the `resources` frame.

    aurora_lib : From lib:apache/aurora
    role : "jrhacker"
    resources : {
      cpu : 0.2 # This value will be eclipsed by⋯
      ram : 16Mi
    }
    hw_file : aurora_lib.aurora_file {
      resources : {
        disk : 16Mi
      }
      jobs : [
        job {
          resources : {
            cpu : 0.1 # ⋯ this one, because this one is “closer” to the point of use.
          }
          cluster : "cluster1"
          instances : 1
          job_name : "hello_world"
          task : aurora_lib.task { processes : [] }
        }
      ]
    }
    value : hw_file.value

In this case, it doesn't change the output, but it allows me to control which parameters are shared by which jobs.

Our task is pretty useless, let's give it some processes:

    aurora_lib : From lib:apache/aurora
    cluster : "cluster1"
    role : "jrhacker"
    resources : {
      cpu : 0.1
      ram : 16Mi
      disk : 16Mi
    }
    hw_file : aurora_lib.aurora_file {
      jobs : [
        job {
          instances : 1
          job_name : "hello_world"
          task : aurora_lib.task {
            processes : { # Define the processes ⋯
              hw : process { # ⋯ using process template
                process_name : "hw" # The name of this process will be `hw`
                command_line : [ "echo hello world" ] # The command line we want to run.
              }
            }
          }
        }
      ]
    }
    value : hw_file.value

This will give us a basic configuration. Aurora allows multiple replicas/instances of a job. Suppose we want each replica to know its identity:

    aurora_lib : From lib:apache/aurora
    cluster : "cluster1"
    role : "jrhacker"
    resources : {
      cpu : 0.1
      ram : 16Mi
      disk : 16Mi
    }
    hw_file : aurora_lib.aurora_file {
      jobs : [
        job {
          instances : 1
          job_name : "hello_world"
          task : aurora_lib.task {
            processes : {
              hw : process {
                process_name : "hw"
                command_line : [ "echo hello world. I am ", current_instance ]
              }
            }
          }
        }
      ]
    }
    value : hw_file.value

Aurora also allows setting multiple ports for incoming connections.

    aurora_lib : From lib:apache/aurora
    cluster : "cluster1"
    role : "jrhacker"
    resources : {
      cpu : 0.1
      ram : 16Mi
      disk : 16Mi
    }
    hw_file : aurora_lib.aurora_file {
      jobs : [
        job {
          instances : 1
          job_name : "hello_world"
          task : aurora_lib.task {
            port_defs +: {
              xmpp : Null # Creating a new null entry, defines a new port. By setting it to a string, it becomes an alias.
            }
            processes : {
              hw : process {
                process_name : "hw"
                command_line : [ "helloworldd --http ", ports.http, " --xmpp ", ports.xmpp ]
              }
            }
          }
        }
      ]
    }
    value : hw_file.value

What makes Flabbergast helpful is how you can extend these base templates to suit your needs. You can create a template that runs a database. It could calculate the needed disk given a list of datasets. That way, a user gets a correctly configured database only having to specify the data sets. Other things to consider might be creating a standard configuration for a sharded system, then using a fricassée to create them over the right range. Or, even, combine the two: create a lists of grouped datasets, then use those to bring up many similar databases to serve them.

## Background
It is important to understand the niche for Flabbergast: it is a configuration language. The configuration of some systems is rather trivial: `fstab` and `resolv.conf` have remained virtually unchanged over many years. More complex programs, such as Apache, BIND, Samba, and CUPS have significantly more complicated configurations. The complexity is not a function of the configuration itself; indeed `smb.conf` is just an INI file. Complexity comes because there's a desire to share common configuration between elements (_e.g._, the list of privileged users among SMB shares). Configuration files start to grow awkward macro systems, get preprocessed using M4 or the like, or get evermore specific configuration operations to migrate the complexity into the binary.

Flabbergast, in some ways, is like a macro system. However, macro systems, such as M4 and the CPP, operate by manipulating text or tokens and can only ever output more text or tokens. Flabbergast is a language meant to manipulate structured configuration: frames. Frames can behave somewhat like objects: they can have inheritance relations, they can be extended, and, finally, rendered into a standard configuration format, which might be the frames themselves or it could be text. Either way, the needs of the configuration remain in the Flabbergast language; not the binary consuming the configuration. Moreover, Flabbergast makes it possible to write libraries of utilities and templates for configurations.

Another way to think about that language is that it is a system where dependency injection is not something added to the language, but is implicit. Every reference is an injection point. Defining an attribute is performing an injection. Since attributes can be overridden, the injection framework remains fluid.

For more background on the complexity of configuration languages, take a look at [Configuration Pinocchio](https://www.usenix.org/conference/srecon15europe/program/presentation/masella), presented at SREcon15 Europe, and the [companion paper](http://www.masella.name/~andre/2015-srecon-andre_masella.pdf).

## Quick Notes on Syntax

To make adoption easier, Flabbergast tries to be like other popular languages in the inconsequential stuff. If compared to any mainstream language, the arithmetic operators are going to behave like “normal”. Flabbergast also tries to use words over symbols. Granted, it has the provincial assumption that everyone knows English, but the goal is to avoid looking like line noise. The unusual symbols are: `:` for definition (like JSON), `&` for string join, and `??` for null coalescence (like C#), `?.` for the null-coalescing lookup (like CoffeeScript's existential property access), and `+:` for overriding (a unique property of the language).

For a quick reference, check the [cheatsheet](syntax-cheatsheet.md).

## Getting Started

As general background, this guide assumes some previous experience programming. Familiarity with one functional language and one object-oriented language is helpful. A deeper understanding of a dynamically-typed object-oriented languages can be extremely helpful, though not necessary. Use of a macro processor might provide some extra insights.

Flabbergast has many of the same data types as many other languages: Booleans, integers, floating-point numbers, and strings; which look and act much like they do in other languages. It also has two special data types: frames and templates. Note that there are no functions (first class or otherwise); templates can do some of the work of functions. Flabbergast is pure and declarative: everything is an expression and no expression can have indirect consequences on another expression.

Frames are similar to Perl's hashes, Python's objects, JavaScript's objects and C#'s anonymous classes. A frame is a dictionary/map between identifiers and values, which may be of any of the aforementioned types. Frames are immutable upon creation. Each entry in a frame is called an _attribute_, having a _name_ and a _value_. The entire scope of a file is also one big frame:

    a : 5
    b : {
      x : True
      y : "Hello, World"
    }

This creates a frame (the file-level frame) that has two attributes: `a`, which is the integer 5, and `b`, which is another frame containing two attributes: `x`, which is the Boolean true, and `y` which is a string.

When creating a frame, an expression can appear on the right side of the `:` to compute the value.

    a : 5
    b : a * a # Yields 25

For integers and floating-point numbers, the usual mathematical and comparison operators are provided. There are also a few special operators for floating-point numbers (`Is Finite` and `Is NaN`) and some special constants (`Infinity` and `NaN`). There are also some limit constants: `IntMax`, `IntMin`, `FloatMax`, and `FloatMin`.

    a : 5.0
    b : Infinity
    c : a Is Finite # Yields True
    d : b Is Finite # Yields False
    c : a * b # Yields Infinity
    e : b * 0 # Yields NaN
    f : 10 / 3 # Yields 3 (10 and 3 are integers)
    g : 10.0 / 3 # Yields 3.333333 (3 is upgraded)
    h : 10 < 5 # Yields False
    i : 1.0 >= -3 # Yields True

Integers also have a few special suffixes for dealing with large values, including time. The suffixes `G`, `M`, and `k` are giga-, mega- and kilo-, respectively. These are the SI versions (powers of 1000). There are also the computer versions, `Gi`, `Mi` and `ki`, which are powers of 1024. For time durations, there are the suffixes `d`, `h`, `m`, and `s`, which express to days, hours, minutes, and seconds, respectively, in seconds.

    a : 4ki # Yields 4096
    b : 5G # Yields 5000 000 000
    c : 1h2m5s # Yields 3725

Integers also have bit-wise operators `B&` (and), `B|` (or), `B^` (xor), and `B!` (complement).

Booleans are very much as expected, with `!` for logical negation, `&&` for short-circuiting conjunction, and `||` for short-circuiting disjunction. The comparison operators also work; one quirk is that they all work, not only equal and not equal. This means that truth is greater than falsehood–don't read too much into it.

    a : True && False # Yields False
    b : False && Error "I don't care" # Yields False
    c : True != False # Yields True, this is XOR
    d : True > False # Yields True

Strings support the usual C-style escape sequences, and Unicode escape sequences. They are also implicitly multi-line, like in shell. There is a special escape sequence `\()` which allows embedding an expression inside a string. Strings can also be joined using `&`. Comparison works lexicographically on the strings.

    a : "Single-line string."
    b : "Multi
    line
    string."
    c : "3 * 4 = \(3 * 4)" # Yields "3 * 4 = 12"
    d : "a" & "b" # Yields "ab"
    e : "a" < "b" # Yields True

The embedded expression must have converted an integer to a string; this is done using the `To` operator, which coerces from one type to another, though it happens implicitly for embedded expressions and string concatenation. There is also an `Is` operator that checks the type of its operand. Finally, there is an `Enforce` operator, which checks the type of its operand, and causes an error if it is not correct.

    a : 5 To Str # Yields "5"
    b : 3.0 Is Int # Yields False
    c : 3 Enforce Int # Yields 3
    d : "Hi" Enforce Int # Error
    e : 3.5 To Int # Yields 3

On to frames, the core data structure of the language. Frames are arranged in a hierarchy: one frame nested inside another, this is called _containment_. Attributes in containing frames are available to contained frames:

    a : 5
    b : {
      x : a + 1 # Yields 6
    }

If there are multiple candidates, the closest one is the one chosen (_i.e._, the one which is found first traversing starting with the immediate container):

    a : 5
    b : {
      a : 1
      c : {
        x : a + 1 # Yields 2
      }
    }

This is called contextual lookup. Multiple identifiers can be put together using a `.` to access inside frames:

    a : {
      x : 5
    }
    b : a.x + 1 # Yields 6

And that works upward too:

    a : {
      x : 5
    }
    b : {
      c : a.x + 1 # Yields 6
    }

Now, something unexpected happens when using this notation, compared with most other languages. A reference is not considered to be pieces: it is atomic.

    a : { # Frame 1
      x : 5
    }
    b : {
      a : { # Frame 2
        y : 1
      }
      c : {
        x : a.x + 1 # Yields 6
      }
    }

Although the closest match of `a` is frame 2, it does not contain an attribute `x`, so it must not be the correct `a`. Resolution can continue and consider other `a` values until one is matched! This will find frame 1, which does have an `x`.

There are other ways to generate frames beyond typing them literally. Templates are prototype frames, like classes are prototype objects. It might be fair to call templates “abstract frames” in the Java or C# sense of the word. This measure is called _inheritance_, as it is in object-oriented languages; a frame _inherits_ a template or the template is an _ancestor_ of the frame.

    a_tmpl : Template {
      x : y + 1
    }
    a : a_tmpl {
      y : 1
    } # Yields { x : 2  y : 1 }

Notice that `a_tmpl` does not produce an error for lacking `y`, because the attributes in it weren't evaluated until it was instantiated. The instantiation also _amended_ the template by adding `y`. Like classes, templates can also inherit from other templates and create derivatives of them:

    a_tmpl : Template {
      x : y + 1
    }
    b_tmpl : Template a_tmpl {
      y : z * 2
    }
    z : 3
    b : b_tmpl { } # Yields { x : 7  y : 6 }

When instantiated, the new frame can perform lookups into the _containers_ of the location where it was instantiated and into the _containers_ of its _ancestors_, that is, the containers of the template that defined it, and any ancestors of that template. This is described in great detail in the more advanced sections. This feature, coupled with contextual lookup, is the useful basis to the Flabbergast language.

Like Java and C#, templates can only inherit a single parent. In Java and C#, this is mostly a concern over how to handle methods with the same name inherited from both parents. Flabbergast has an additional reason not to encourage this: how to combine the ancestry of the two templates. Java and C# work around their lack of multiple inheritance issues using interfaces. In Flabbergast, there is no need for interfaces, since those are a by-product of a type system that Flabbergast doesn't have. The consumer of a frame can pluck the attributes it needs out of that frame; it doesn't need to define a type. Frames also don't have methods, as attributes can perform computation, so there are no type signatures to “get right”.

Like object-oriented languages, in addition to adding new things, template inheritance can also replace existing things:

    a_tmpl : Template {
      x : y + 1
      y : z * 3
    }
    b_tmpl : Template a_tmpl {
      y : z * 2
    }

Because there are no methods, there are no signatures to match. It will be generally necessary to have the replacement return something of the same type, but this is not a strict requirement.

An unusual feature of Flabbergast is the ability to remove attributes:

    a_tmpl : Template {
      x : y + 1
      y : z * 3
    }
    b_tmpl : Template a_tmpl {
      y : Drop
    }

In most other languages, this would break the class since any references to the deleted method or field would be invalid, but in Flabbergast, any references to the removed attribute follow normal lookup and can find the value elsewhere.

Templates can also act as functions. Undefined attributes are parameters, and a single attribute can act as a result:

    square : Template {
      x : Required
      value : x * x
    }
    b : (square { x : 5 }).value

The `Required` attribute definition defines an attribute to be present, but contain an error, forcing the user of a template to replace this value.

For convenience, Flabbergast provides alternate syntax for consuming such a template:

    b : square(x : 5)

Unnamed values are placed in an `args` attribute.

    sum_of_squares : Template {
        args : Required
        value : For x : args Reduce acc + x * x With acc : 0
    }
    c : sum_of_squares(3, 4, 5)
    d : sum_of_squares(args : 3 Through 5)

This means that function-like template can offer both variadic and non-variadic calling conventions. It is an error to specify both `args` and have unnamed arguments. If you find templates a clunky way to do this, there is a special syntax to make it easier:

    sum_of_squares : Function(args) For x : args Reduce acc + x * x With acc : 0

There is an entirely different way to generate frames: from existing frames using the fricassée expressions. These work something like SQL or XQuery statements to generate new frames from existing frames, as SQL generates new tables from existing tables and XQuery generates new trees from existing trees.

    a : { x : 1  y : 2  z : 3 }
    b : For n : Name, v : a Select n : v + 1 # Yields { x : 2  y : 3  z : 4 }
    c : For v : a Reduce v + acc With acc : 0 # Yields 6

These are the essential features of the language. Many other built-in expressions are provided, including an `If` expression, other ways to generate frames, access to external libraries, more variants of the fricassée expression, and more subtle ways to lookup identifiers.

## Core Concepts

There are two core concepts in Flabbergast: contextual (dynamic) lookup and inheritance. Both of these exist in the context of frames, which are the primary data structure. They somewhat resemble objects in Python, Perl and JavaScript.

A frame is a map of names to values, including other frames. Each frame is immutable upon creation. The values in the frame can be expressions (_i.e._, dynamically computed when the frame is created); there are no methods as there are in other object-oriented languages. Expressions can reference each other and the Flabbergast interpreter will determine the correct evaluation order. Although frames cannot be modified, new frames can be created from existing frames using fricassée (`For`) expressions that allow generation of frames based on the values in existing frames, much like SQL or XQuery statements produce new tables or trees from existing tables or trees, respectively. Frames can also be instantiated from templates, which are essentially unevaluated frames (_i.e._, the values of the attributes have not yet been computed).

Each frame also has an evaluation context. In most languages, there are multiple scopes in which variables can be found. For instance, in C, the compiler tries to resolve a variable in the current block and proceeds through each containing block, then checks the function parameters, and finally global variables. Java is considerably more complicated as it needs to check not only the enclosing blocks and the method parameters, but also the fields of the class, and it has to make a distinction between static and instantiated fields, and inner classes are even more involved as the containing classes have to be checked. Flabbergast's algorithm for resolution is very simple, but can yield very complicated results.

Flabbergast uses contextual lookup. It is easiest to think of resolution as having two dimensions: containment and inheritance. When resolving a variable, the language will first look for an attribute of the same name in the current frame; if none exists, it will look in the containing frame (_i.e._, the frame in which the current frame is an attribute) and will continue to examine containers until a matching frame is found. If there is no match, resolution will continue in the parents of the template; that is, it will go to the context in which the template was defined and search through the containing frames there. If that yields no matches, resolution will proceed back through the template's template's containers and back until there are no more contexts.

![Resolution order diagram](https://rawgithub.com/apmasell/flabbergast/master/flabbergast-resolution.svg "The order in which resolution occurs")

In the figure, there are two templates, shown in colour, and frames, shown in grey. The frame containment is indicated by physical containment and inheritance is shown by arrows. When resolution inside of the frame marked 1 is needed, resolution begins in the frame itself, then proceeds through the frames, as indicated by their numbers. Note that some frames are considered multiple times due to the template and the frame sharing some containment; this is inconsequential, as it will either be found the first time, or fail every time. Note that templates themselves are not checked: only the frames in which they were defined or amended.

Resolution has an extra complexity: chained accesses. Since frames can be nested, it is desirable to access the attributes of an inner frame using the `x.y` syntax. In most languages, resolution stops when the first name match is met (_e.g._, in Java, if there is a parameter `int foo` and a class field `String foo`, then `foo.charAt(3)` will fail to compile because `foo` has been resolved to the parameter, which is an `int` and so does not have the `charAt` method) while Flabbergast will attempt to find “the one you meant”. In the case of `x.y`, if a frame `x` is found but it does not contain an attribute `y`, then the language assumes that this was not the `x` intended and _continues_ looking for an `x` does _does_ contain an attribute `y`. This means that the expression `x.y * x.z` can have two different frames referenced for the first and second `x`! For instance, below, `b.z` will be 12 and the `a` in the `a.x` reference will be the top-level `a` while the `a` in the `a.y` reference will be `b.a`:

    a : { x : 3 }
    b : {
      a : { y : 4 }
      z : a.x * a.y
    }

In general-purpose programming languages, this idea sounds like madness, but Flabbergast is not a general-purpose programming language: it is intended to be used for writing configuration files. In that case, this feature allows brevity and clarity. If an SMB share looks for `users.admins`, it will pick up the one that is “closest” to the expression using it and, if the closest one does not provide that information, it must be somewhere else. Perhaps the best way to think of it is how human languages work:

> My father hates cats even though my mum likes them, so my parents never got a cat. When my sister moved out, she got a cat. My mum loves playing with her cat.

In the last sentence, to whom does _her_ refer? While _my mum_ is the closest noun that could match _her_, it has been previously established that my mum does not have a cat, so _my mum's cat_ wouldn't make sense. Because we treat _her cat_ as a unit, we contextually keep looking for a _her_ that does have a cat, which would be _my sister_. Conceptually, this is how Flabbergast's resolution algorithm works: it finds the match that makes the most contextual sense.

Inheritance allows creation of attributes, in addition to providing a history for contextual lookup. A frame or template is a collection of key-value pairs, where each key is a valid identifier and a value can be any expression. A template can be _amended_ at the time of instantiation or through the `Template` expression, which creates a new template that copies all the existing attributes, less the amended ones. In most object-oriented languages, fields and methods can be overridden (_i.e._, replaced with new code). Similarly, attributes can be overridden with new expressions. Some languages allow access the overridden method through special keywords (_e.g._, Java's `super`, C#'s `base`, or Python's `super()`). Flabbergast permits accessing the original attribute, but a new name must be specified; there is no default name like `super`. Unlike most languages, Flabbergast permits deletion of an attribute. Because of contextual lookup, any other attributes referencing the deleted attribute will look elsewhere.

Since attributes can refer to each other, it is the interpreter's duty to determine the order to evaluate the expressions. This means that attributes can be specified in any order (unlike C and C++). In fact, contextual lookup makes it impossible to determine to what attributes references refer until evaluation time. One unusual effect is that the inheritance path of a frame can be changed at runtime (_i.e._, the “class” hierarchy is not determined at compile-time)! In fact, since templates can be instantiated in different contexts, it is possible for the same declaration to be used in different contexts that create two different frame inheritance paths. This is the kind of stuff that can be used for good or evil–there are legitimate reasons to re-parent frames, but it can be very confusing and cause unexpected behaviour.

The interpreter must be able to linearise the order in which it will perform evaluations. If the expression evaluation contains a cycle, then it is not possible to evaluate any of the expressions in the cycle. This is called _circular evaluation_. There are pseudo-cycles that are acceptable: the expressions can refer to one-another circularly so long as they don't need the value. This happens mostly during contextual lookup:

    x : {
      w : 4
      z : y
    }
    y : x.w

Here, `x` as a frame, cannot be completely evaluated because the attribute inside `z` depends on the value of `y`, which in turn, depends on `w`, inside of `x`. Although there is cross-reference into and out of `x`, there is an order that works: evaluate `x` to be a frame with attributes `w` and `z` (though do not evaluate them), evaluate `x.w`i to be 4, evaluate `y` to be 4, and finally `x.z` to be 4. Here is an example of true circularity:

    x : y
    y : x

or even more succinctly:

    x : x

Usually, the intended meaning of this expression is:

    {
      x : Lookup x In Container
    }

## Syntax

In Flabbergast, all keywords start with a capital letter and identifiers start with a small letter, making them easily distinguishable. There are also a number of special characters used for operators. Comments begin with `#` and terminate at the end of the line. For the purposes of code formatting, comments preceding an attribute are assumed to be associated with it.

### Types and Constants

Flabbergast has a small handful of types: integers (`Int`), floating-pointer numbers (`Float`), Boolean values (`Bool`), text strings (`Str`), binary strings (`Bin`), frames (`Frame`) and templates (`Template`).

Integral and floating-point number literals are specified in the familiar way. They can also be manipulated using the typical `+`, `-`, `*`, `/` and `%` operators. In mixed-type expressions, integers are automatically promoted to floating-point numbers. They also can be compared using `==`, `!=`, `<`, `<=`, `>`, `>=`, and `<=>`. The `<=>` operator will be familiar to Perl and Ruby programmers: it compares two values and returns -1, 0, or 1 if the left operand is less than, equal to, or greater than the right operand, respectively. There is also a unary negation operator `-`. A few floating-point exceptional tests are provided: `Is Finite` and `Is NaN` to check if the number is finite or not-a-number in the IEEE sense, respectively. Also, the floating-point constants `NaN`, `Infinity`, `FloatMax`, and `FloatMin`, and integer constants `IntMax` and `IntMin` are provided.

The Boolean constants provided are `True` and `False` that can be manipulated using the operators `&&`, `||`, and `!`. They can also be compared using the full set of comparison operators; true is considered to be greater than false. There is no separate exclusive-or operator as `!=` serves this role. Both `&&` and `||` are short-circuiting. As a confusing side benefit, _a_ implies _b_ can be written as `a <= b`.

String literals, delimited by double quotation marks, can cover multiple lines and can contain embedded escape sequences and expressions. The escape sequences supported include the single-character escapes supported by C, triplets of octal digits, pairs of hexadecimal digits, and Unicode-friendly quadruplets of hexadecimal digits. Embedded expressions start with `\(`, followed by an expression, and terminated with a matching `)`; the expression must return a string, or a type that can be coerced to a string. Strings can also be joined together with the `&` operator. For example, `a`, `b` and `c` will have the same value and the reference to `x`, which is an integer, is converted to a string:

    x : 3
    a : "foo=\(x)\n"
    b : "foo=\(x)
    "
    c : "foo=" & x & "\n"

Sometimes, attribute names are provided as strings and, since not all strings are valid attribute names, it is useful to have a way to create a string that is a valid attribute name. By placing `$` before a valid identifier, a string with the identifier name will be created.

    x : $foo == "foo" # True
    y : $5 # Error

Binary strings are handled mostly by library functions as a way to pass binary data around, including conversion to and from text strings, hashing, and database interaction.

Frames are collections of attributes. Literal frames are specified starting with `{`, followed by a list of attributes, and terminated with a matching `}`. Each attribute is a name, followed by `:`, and an expression. Frames inherit the context of the frame in which they are defined. Templates look similar except that they are preceded by the `Template` keyword. There are two important differences between templates and frames: frames are immutable while templates can be manipulated and variable resolution can look inside frames, but not inside of templates. Neither can be coerced to strings. More details on frames are provided later.

There is also a special `Null` constant which can be checked for using the `Is Null` operator. The null value cannot be used in any comparison operator and doing so will cause an error. The null value is not the same as an undefined variable. There is a null coalescence operator `??` that can substitute a default value if the left operand is null. Unlike most languages, null should be used extremely sparingly in Flabbergast: it is usually preferable to let contextual lookup find appropriate values. Null should mean “this value is not helpful and that's the final word” instead of the usual meanings of “I don't know” or “this does not exist”.

    x : Null
    y : x ?? 3 # Yields 3
    z : x == Null # Error
    w : x Is Null # Yields True

The `To` operator can be used to coerce values to other types. Integral and floating-point types can be inter-converted, with appropriate truncation, and converted to strings.

The `Is` operator checks whether a value is a particular type and returns the result as a Boolean value.

The `Enforce` operator ensures that a value is of a particular type, if not, an error occurs.

    v : "3" Enforce Str # "3"
    w : 3 Enforce Str # Error
    x : 3 To Str # "3"
    y : 4 Is Int # True
    z : 4.5 Is Int # False

Also see the `TypeOf` expression.

### Special Frame Literals

Two special frame literals are provided: the literal list and the range operator.

Frames are implicitly sorted by their attribute names. The literal list is a way to create a frame with only values and the names are set to arbitrary labels that have a guaranteed stable sorting. It is a comma-separated list of expressions starting with a `[` and terminating with a `]`.

The `Through` operator produces a list with the values being numbers starting from the left operand up to, and including, the right operand, both of which must be integers. If the right operand is the same or less than the left, the list returned will be empty.

The values of the following frames all have the same values in the same order:

    x : [ 1, 2, 3 ]
    y : 1 Through 3
    z : {
      a : 1
      b : 2
      c : 3
    }

### Flow Control

A conditional expression is provided: `If`, an expression which returns a Boolean value, `Then`, an expression to be returned if the first expression is true, `Else`, an expression to be returned if the first expression is false. This expression, very importantly, impacts contextual lookup. Free identifiers in the `Then` and `Else` expressions are not resolved unless that expression is selected. This means that they can contain invalid references without causing an error. For instance, `y` will be 5 and no error will occur even though `z` is not defined.

    x : 5
    y : If x < 10 Then x Else z

The `Error` expression raises an error, stopping execution of the whole program. Any expression attempting to consume the return value of this expression will fail to evaluate.

    x : 5
    y : If x < 10
      Then x
      Else Error "The value \(x) is too large."

### Lookup

A period-separated list of identifiers forms a free variable to be resolved by contextual lookup; this is called contextual lookup.

    a : 5
    b : {
      c : a + 3
    }
    x : b.c

Here, in `b.c`, lookup will start in the current frame, which does not contain `a`, so lookup will continue to the containing frame, which does have `a`. For `x`, it will look for a frame `b` which contains an attribute `c`.

A period-separated list of identifiers can also be appended to any expression, in which case it will do exact matching starting from the expression supplied; this is called a direct lookup. The keyword `This` gives access to the frame where the expression is being evaluated, effectively forcing direct lookup. Using parentheses or the result of an expression will also result in direct lookup.

     x : 1
     a : {
       x : 3
       y : This.x + 1 # Yields 4
     }
     b : (a).x # Yields 4

The keyword `Container` access the parent of the frame (either the current frame if it is used alone, or the preceding frame if used in an contextual or direct lookup).

     a : 1
     b : {
       a : Container.a # Yields 1
     }

The `Lookup` expression performs a remote contextual lookup; `Lookup a.b.c In x` will start contextual lookup for `a.b.c` starting from the context of the frame `y`, rather than the current context.

Here is non-trivial example uses of all lookup styles:

    i : 4
    a : {
      h : i - 1 # This frame does not contain i, but the container does. Yields 4 - 1 = 3
    }
    x : a.h # Will be 3
    y : a.i # Will be an error
    z : Lookup i In a # Will be 4
    w : {
      a : {
        i : 2
      }
      x : a.h # Will be 3
      y : a.i # Will be 2
      z : Lookup i In a # Will be 2
      v : (a).h # Will be an error
    }

In `a`, contextual lookup searches for an `i`, which does not exist in the current frame, but does exist in its container. In `x`, contextual looks for an frame `a` that contains an attribute `h`, which it finds at the top-level. In `y`, although `i` is accessible from the frame `a`, it does not exist in `a`, so `a.i` fails to find a matching frame. However, in `z`, a remote lookup searches for `i` starting from `a`, which is found in exactly the same way as when computing the value for `a.h`. Inside `w`, the situation is more complicated as another frame named `a` is created. Searching for `a.h`, as `w.x` does, first checks the frame `w.a`, but this frame lacks an `h` attribute, so lookup continues, instead finding the top-level frame. In `w.y`, lookup for `a.i` will match the `w.a` frame as it does have an `i` attribute. In both `w.z` and `w.v`, searching for `a` yields `w.a`. In the case of `w.z`, doing a remote lookup inside `w.a` for `i` will find the attribute inside it. In the case of `w.v`, the parentheses have broken the lookup into a contextual lookup (`a`) and a direct get (`.h`); this is an error as the matched `a` (`w.a`) does not have an attribute `h`.

> __This is the most complicated part of the language, but also the most useful.__

The pattern `If x Is Null Then Null Else (x).y` tends to show up frequently. The nullable lookup operator, makes this `x?.y`.

### Fricassée Expressions

Although frames are immutable, it is possible to create new values from existing frames using fricassée expressions. These expressions take a collection of input frames an iterate over the attributes they share. The concept was based on [XQuery's FLOWR](https://en.wikipedia.org/wiki/XQuery) expressions, which are based on SQL queries. It should be familiar to users of Haskell and LISP's `map` and `fold` functions, [C#'s LINQ](https://msdn.microsoft.com/en-us/library/mt693024.aspx) expressions, [Python's list and dict comprehensions](https://docs.python.org/3/reference/expressions.html#displays-for-lists-sets-and-dictionaries), or Perl's [`map`](http://perldoc.perl.org/functions/map.html) and [`grep`](http://perldoc.perl.org/functions/grep.html) constructs. Conceptually, the expression has three parts: a source, optional manipulations, and a sink. The source extracts data from frames and produces a context in which each subsequent expression will be evaluated. The manipulations can filter can discard any contexts that do not meet certain requirements, reorder the data, or compute intermediate values. The sink produces a value from the contexts: either a new frame, or a single value for a reduction.

There are two sources provided: the combined attributes of frames, and, prepared context frames. In all cases, the select is done over a collection of input frames and all the attributes of the input frames. The following example shows the first part of a fricassée expression for different sources over the same input frames. In the first three cases, the source will iterate over the union of all the attributes in the frames `x`, `y`, and `z` and each context will have `a`, `b`, and `c` bound to the values in the corresponding frames, or `Null` if there is no corresponding value. For the values only source, `i`, this is all the context will contain. In the case of `j`, the attribute name itself will be bound as `n` in a string, using the special `Name` value. In the case of `k`, the position will be provided using the special `Ordinal` value; indexing starts from 1.

    x : {
      p : 1
      q : 2
    }
    y : {
      p : 3
      q : 4
    }
    z : {
      p : 5
    }
    i : For a : x, b : y, c : z ...
      # Will consider { a : 1  b : 3  c : 5 }, { a : 2  b : 4  c : Null }
    j : For n : Name, a : x, b : y, c : z ...
      # Will consider { a : 1  b : 3  c : 5  n : "p" }, { a : 2  b : 4  c : Null  n : "q" }
    k : For n : Ordinal, a : x, b : y, c : z ...
      # Will consider { a : 1  b : 3  c : 5  n : 1 }, { a : 2  b : 4  c : Null  n : 2}
    l : For Each [ x, y, z ] ...
      # Will consider { p : 1  q : 2 }, { p : 3  q : 4 }, { p : 5 }

The prepared frame source, `Each`, is meant for library functions to produce iterable sources of data. One could imagine a library function matching a regular expression and returning the matched groups. It becomes the responsibility of the source to provide sensible attributes in each frame. In the example, `z` makes for an awkward environment since `q` is not bound, and the `Each` source is not obligated to correct the inconsistency.

Optionally, a `Where` clause can be used to filter the results. It must return a Boolean value.

    x : 1 Through 7
    i : For a : x Where a > 5 ... # Yields { a : 6 }, { a : 7 }

Finally, a sink is needed to produce a value from the contexts. Three are supported: a reducer, an anonymous frame generator, and a named frame generator. The reducer and the anonymous frame generator both support ordering.

The reducer works as a typical fold/reduce operation:

    x : 1 Through 7
    y : For v : x Reduce v + a With a : 0

The initial value of the accumulator, `a`, is set to zero and the reduce expression is repeated for each of the contexts. In each context, the accumulator will be bound to the previous value. The reducer can support ordering operations, shown later, on the contexts to choose the reduction order.

Sometimes, it is useful to compute intermediate values during iteration. For this, a `Let` clause can be added:

    x : 1 Through 7
    y : For v : x Let w : v * v + v Where w > 4 * v Select w

It is also possible to produce a running value using `Accumulate`:

    x : 1 Through 7
    cumulative_sum :
      For v : x
        Accumulate current_sum + v
          With current_sum : 0
        Select current_sum

The anonymous frame generator produces a new frame with each value being the result of an expression. The names of the frames are generated automatically in the same way as if they had been generated by a literal list or `Through` expression.

    x : 7
    y : For v : (1 Through x) Select v * v # Produces a list of squares

Because multiple input frames can be provided, much like LISP's variadic `map`, it also functions like Haskell's `zip`:

    x : [ 1, 2, 3 ]
    y : [ 4, 5, 6 ]
    z : For a : x, y : b Select a + b # Element-wise sum of the lists

The anonymous frame generator can support ordering operations, shown later, on the context to choose the order of the output.

The named attribute frame generator produces a frame where the element names are provided as strings:

    x : 1 Through 3
    y : For a : x Select "foo\(x)" : x # Produces the frame { foo1 : 1  foo2 : 2  foo3 : 3 }

The name provided must be a valid identifier, which is not true of all strings, otherwise an error will occur. This named attribute frame generator does not support ordering operations since the order of attributes in a frame is controlled by their names.

Presently, there are two ordering operations: `Reverse` reverses the order of the input and `Order By` produces a sorting item.

    x : -3 Through 3
    y : For a : x  Reverse  Select a # Yields [ 3, 2, 1, 0, -1, -2, -3 ]
    z : For a : x  Order By (If a < 0 Then -a Else a) Enforce Int Select a # Yields [ 0, -1, 1, -2, 2, -3, 3 ]

Note that if two values have the same sort key, in the example -1 and 1 do, then the order between them is not guaranteed. Any type that can be compared using the `<=>` can be used as a sort key, but all must be of the same type.

If there is a collection of nested frames, they can concatenated using `Flatten`, which works like `For`, so it can do the same zip, if required.

    For a :
      [
        { text : "Hi"  recipients : [ "Andre", "Gráinne", "Kyle" ] },
        { text : "Something else"  recipients : [ "Andre", "Kyle" ] }
      ]
      Flatten recipient : a.recipients
      Select recipient & ": " & a.text
    # Yields [ "Andre: Hi", "Gráinne: Hi", "Kyle: Hi", "Andre: Something else", "Kyle: Something else" ]

Things can also be recomposed using `Group By`:

    For a :
      [
        { text : "Hi"  recipients : [ "Andre", "Gráinne", "Kyle" ] },
        { text : "Something else"  recipients : [ "Andre", "Kyle" ] }
      ]
      Flatten recipient : a.recipients
      Group messages : a.text By recipient
      Select { r : recipient  m : messages }
    # Yields [ { r : "Andre" m : [ "Hi", "Something else" ] }, { r : "Gráinne" m : [ "Hi" ] }, { r : "Kyle"  m : [ "Hi", "Something else" ] } ]

### Frames and Templates

In addition to literal frames, frames can be instantiated from templates. The instantiation can also amended a template by adding, removing, or overriding attributes. The syntax for instantiation is an expression yielding a template followed by `{`, an optional list of amendments, and terminated by `}`. Templates are created in a syntax similar to literal frames: `Template {`, a list of attributes, followed by `}`.

    foo_tmpl : Template { a : b + 4 }
    foo : foo_tmpl { b : 3 } # Yields { a : 7  b : 4 }

Templates can also be derived using a syntax that is a hybrid of the two: `Template`, an expression for the template from which to derive, followed by `{`, an optional list of amendments, and a terminating `}`. It's important to note that deriving a template, even with no changes, amends it because it adds additional lookup scopes. In general, it's useful think of the curly braces and capturing scope. In this example, `foo2_tmpl` is capturing the scope inside of `x`, making `b` available to its descendants.

    foo_tmpl : Template { a : b + 4 }
    x : {
      foo2_tmpl : Template { }
      b : 1
    }
    y : x.foo2_tmpl { } # Yields { a : 5 }

There are several amendment attributes, not all of which can be used in all contexts:

 - `:`, followed by an expression, defines an attribute to be the supplied expression. If there previously was an attribute of the same name, it is discarded.
 - `: Required` creates an attribute that is always an error. This can be thought of as an _abstract_ attribute, in the C++/Java/C# terminology. This is not permitted during instantiation.
 - `: Drop` deletes an attribute. The attribute must already exist, so this is not valid when declaring a new template.
 - `+`, followed by an identifier, then `:`, followed by an expression, will replace an attribute but allows the previous value to be bound to the identifier supplied. The attribute must already exist, so this is not valid when declaring a new template.
 - `+: {`, followed by a list of amendments, terminated by `}`, performs template amendment. It is short-hand for `+oldtemplate: Template oldtemplate { ... }` with the convenience of not having to choose a name.
 - `: Used` indicates that a value is expected to be available through lookup. It does not actually *do* anything; it is merely a way to explain intentions to others and provide a place to hang documentation. It can be thought of as a weak version of `Required` attributes, and is usually preferable.
 - `: Now`, followed by an expression, creates an attribute, but evaluates is eagerly, in the current context, much like the function call convenience syntax.

There is also a function call convenience syntax. In their own way, templates can act as lambdas. In frame instantiation, the expressions are evaluated in the context of the frame created. In a function call, expressions provided are evaluated in the current (parent) context, then placed into the instantiated template. A list of unnamed expressions can be provided and these are collected into an `args` frame. Finally, instead of returning the entire frame, the `value` attribute is returned from the instantiated frame. For instance, the function call:

    {
      a : 3
      b : 2
      c : 1
      z : f(a, b, c : c)
    }

is almost rewritten as:

    {
      a : 3
      b : 2
      c : 1
      z : (f {
        args : Now [ a,  b ]
        c : Now c
      }).value
    }

In this example, `c` would be circular evaluation when using normal evaluation semantics, but because the evaluation of the parameters happens in the containing context, this is fine. There is a subtle different too: the resulting frame's container will not be the one where it is instantiated, but the one where it is defined.

For Java programmers, there's an analogy for the two modes of template instantiation: anonymous inner classes. Creating a template is a bit like instantiating an anonymous inner class. If that class has multiple methods, then template instantiation gives similar behaviour. If only one method is of interest, then it can be instantiated using function-like instantiation, much like lambda notation in Java 8.

### Data Reshaping

It's useful to remember how all the pieces of structured data manipulation in the language work. The following diagram shows the different formats of data and the syntax that navigates between them.

![](https://rawgithub.com/apmasell/flabbergast/master/inheritance.svg)

Templates can be made from scratch or from existing templates. Frames can be made from scratch, by instantiating templates, the fricassée `Select` operations, or the `Append` expression. Scalar values can be distilled from frames using the fricassée `Reduce` operation. Scalar values can be manipulated a number of ways not shown in the diagram.

### TypeOf Expression
The `TypeOf` expression allows type-directed lookup. This operation gets the type of a value. Rather than return that value as a string or type type, it performs a lookup so that a user-defined value is returned.

For instance, `TypeOf "x"` will perform a lookup for `str` and return whatever value that is. The name of the variable looked up is the same as the types but lower case.

The expression takes an optional prefix specified using `With`: `TypeOf "x" With foo` would resolve to `foo.str`

This allows the `TypeOf` expression to change the semantics as desired. For instance, here is a case where direct comparison is desired:

    type_id : { bin : 0  bool : 1  int : 2  float :  3  frame : 4  null : 5  str : 6  template : 7 }
    are_x_and_y_the_same_type : TypeOf x With type_id == TypeOf y With type_id

Suppose we want to know if a type can be converted to an SQL literal:

    has_sql_literal : { bin : True  bool : True  int : True float :  True  frame : False  null : True  str : True  template : False }
    can_x_be_an_sql_literal : TypeOf has_sql_literal With x

We can even be craftier and convert a value by lookup:

    create_sql_literal : {
      bin : Template { value : provider.blob_start & utils_lib.bin_to_hex_str(input, uppercase : True) & provider.blob_end }
      bool : Template { value : If input Then "true" Else "false" }
      int : Template { value : input To Str }
      float :  Template { value : input To Str }
      frame : Template { value : Error "Cannot convert frame to SQL." }
      null : Template { value : "NULL" }
      str :  Template { value : provider.quote_start & utils_lib.str_escape(input, transformations : provider.transformations) & provider.quote_end }
      template :  Template { value : Error "Cannot convert template to SQL." }
    }
    value : (TypeOf x With create_sql_literal)(input : x)

That is, use `TypeOf` to find a function-like template and then call it on the value.


### Miscellaneous

The `Let` expression allows binding a value to a new name. For example, `Let a : 3 In a * a`. This is a convenient way to eliminate common subexpressions. Be advised that the normal short-circuiting rules do not apply: all the values in the expression must be evaluated first. Multiple attributes can be bound at once (_e.g._, `Let a : 3, b : 4 In a * a + b * b`).

The `Append` operators concatenates two frames. The attribute names are the same as if a literal list had been used. This means that `{ a : 5 } Append [ 6 ]` will produce the same frame as `[ 5, 6 ]`.

The `From` expression allows importing external content into the program. This does two jobs: allows accessing libraries and allows access information for the program being configured. The `From` keyword is always followed by a URI. The `lib:` URI is  used for the standard library. By convention, it is best to do all the importing at the start of a file:

    foo_lib : From lib:foo

Presently, there are handlers for:

* SQL databases (`sql:`)
* local files (`file:` and `res:`)
* FTP and HTTP URLs (`ftp:`, `ftps:`, `http:`, and `https:`)
* VM-specific settings using `java.lang.System.getProperty` on the JVM and `System.Configuration.ConfigurationManager.AppSettings` (`settings:`)
* environment variables (`env:`)
* Flabbergast runtime information (`current:`)

    version : From env:EXAMPLE_VERSION
    release_db : From sql:postgresql://o_0@db.example.com/release
    sql_lib : From lib:sql

    release_versions : sql_lib.retrieve { connection : release_db, sql_query : "SELECT version, artifact, checksum FROM release_info ORDER BY push_date WHERE version == '\(token)'" }

Implementation-specific keywords start with `X`. They should not be used in most code, but are often present in libraries to support binding to the underlying platform.

## Using the Language

The language is meant for creating configurations. What, precisely, is a configuration? Or, more specifically, how is it different from regular code?

Certainly, Flabbergast is a bad choice for writing a GUI application, or a data processing pipeline. It's almost useful to think of Flabbergast as something like a compiler: it takes highly structured data and outputs very simple data. Effectively, it is meant to render data. What the language is attempting to do is compress the duplication in a very ad-hoc way, as a macro system does. Real compilers for real languages are large, complicated, and, hopefully, well-designed. Flabbergast aims to help you put together a very simple language with almost no effort, but, unlike most macro systems, it's going to have rather sophisticated descriptions of data. As such, it's not going to do everything right; it's going to rely on the user to put in moderately sane input and that the downstream consumer will validate the input.

In most languages, afterthoughts are not appreciated. However, most configurations are nothing but afterthoughts and exceptions. “I want the test version of the webserver to be the same as the production except for the database connection.” “I want the videos SMB share to be the same as the documents SMB share with a few extra users.” Flabbergast is built to service “except”, “and”, and “but”. Everything is malleable and done in such a way that it can be changed even if the base version didn't anticipate that change. There's no concept of Java's `final`, or C++'s `const`.

Most languages, particularly object-oriented languages, have a lot of plumbing: taking data from one place and copying it to another. Most constructors in object-oriented languages spend their time stuffing parameters into fields. There is a push in multi-paradigm object-oriented languages, including Python, Scala, Ruby, Groovy, Boo, and Nemerle, to have the compiler write the plumbing, freeing the programmer to work on the real logic. Flabbergast has a different approach: don't have plumbing at all. Define the data where it should be defined and use contextual lookup to pull data from the wherever. Copying data is generally a sign that contextual lookup is not being used effectively.

Although Flabbergast has the `Required` attribute definition, it should almost never be used. This is one of the most frequent mistakes of novice programmers. If there's a value needed, just use it; there's no need force the consumer of a template to fill in the blank. The real use case is for things that must be provided and unique. For instance, the name of an SMB share is probably best defined with `Required`, but the list of users that can access the share should certainly not use `Required`. It's okay that a user who instantiates a share template without providing a users list somewhere will cause a lookup error: failure to provide an appropriately name value is a failure to consume that API correctly and, indeed, this was noted by an error being produced. There's no need to make this some how “more” explicit. The `Used` attribute definition provides an advisory version of `Required` that indicates that a value should be supplied, but does not stipulate that it needs to be included directly.

The most important feature of Flabbergast is overriding. When starting out with Java or C#, understanding how to divide things up into objects is the hard part. When starting out with ML or Haskell, understanding how to make things stateless is the hard part. When starting out with Flabbergast, understanding how to make things easy to override is the hard part.


### Interfaces
In object-oriented languages, there is typically some convention surround how to tell if an object is consumable in a particular situation. Statically-typed object-oriented languages typically have “interfaces”, in the Java or C# sense. These are functions of the type system: since each expression needs a type and multiple inheritance is not permitted, an interface provides a type that any object can fulfill outside of the inheritance hierarchy. Dynamically-typed object-oriented languages, particularly Python, JavaScript, and Ruby, eschew this and proudly extol the benefits of “duck” typing: that is, call a method and expect it to work.

Flabbergast is necessarily dynamically-typed by virtually of being dynamically-scoped. Therefore, interfaces are definitely of the duck-typing variety. Since methods aren't present, the interface is still simpler: it is the attributes that a frame is expected to have and, possibly, expected types for those attributes. Using the `Enforce` operator is a polite way of insisting that certain types are provided from an interface.

Often, the caller has some driver to convert code. For instance, this is a completely reasonable block of Python:

    strs = []
    for item in items:
     strs.add("name: %s country: %d" % (item.name, item.country))
    return "\n".join(strs)

Indeed, this could be translated into Flabbergast as follows:

    strs : For item : items
      Reduce "\(acc)name: \(item.name) country: \(item.country)\n"
      With acc : ""

However, it is often simpler to make items self-rendering:

    item_tmpl : Template {
      name : Required
      country : Required
      value : "name: \(name) country: \(country)\n"
    }
    strs : For item : items
      Reduce acc & item.value
      With acc : ""

This has two advantages: the rendering logic can be overridden and the interface is simpler. As a disadvantage, there is now an inheritance implication for the values of `items`. However, because the template `item_tmpl` can be overridden and replaced, the inheritance implication is flexible. In fact, it would be reasonable to have:

    item_base_tmpl : Template {
      name : Required
      country : Required
    }
    item_xml_tmpl : Template item_base_tmpl {
      value : "<person><name>\(name)</name><country>\(country)</country></person>"
    }
    item_pretty_tmpl : Template item_base_tmpl {
      value : "name: \(name) country: \(country)\n"
    }
    item_tmpl : If xml_output Then item_xml_tmpl Else item_pretty_tmpl
    items : [
      item_tmpl { name : "Andre"  country : "Canada" },
      item_tmpl { name : "Gráinne"  country : "Ireland" }
    ]

By changing the definition for `item_tmpl`, we can re-ancestor the frames using it. Effectively, Flabbergast has a kind of multiple inheritance: there can be only one ancestor at a time, but the choice of ancestor can be varied at run time.

### Subexpressions and Encapsulation

In complicated subexpressions, it is often useful to migrate common subexpressions to a `Let` expression. In general, `Let` is less preferred to creating a new attribute in the current frame. There are places where that is not possible (_e.g._, inside a fricassée expression).

There are two reasons that is preferred: debugging and overriding. Since there is no way to see the value bound in a let, it is much better if intermediate values can be seen if the entire output is dumped. It is also possible that a user would like to override this value. That violates all the usual object-oriented mindset about data encapsulation, but this isn't a usual object-oriented language.

First, it can be extremely useful for debugging purposes to tinker with subexpressions. For instance, if there is a condition that isn't working properly, it could be useful to override the condition and check if the output is at least correct. Second, the user might be doing something sufficiently different that overriding that condition is helpful. Perhaps they need an extra clause or there is a special case where that logic isn't appropriate.

In a language designed around exceptions, exposing the inner workings is a feature, not a bug. However, this implies a larger interface surface, which is more difficult to maintain–a balance must be struck. The language has made interface simpler, so adding some extra complexity is not as grievous. It's also worth noting that some overrides of non-existent attribute is dead, but not dangerous, code. As a general rule, the real description of the interface belongs in documentation (either external or comments) that describe the interface.

When writing templates, it is good style to separate attributes into blocks: the “parameters”, “private” intermediate attributes, and “output” attributes.

### Name-ability

Naming things is difficult. Very difficult. The major disadvantage to dynamic scoping is that names can collide and have unintended consequences. There are several ways to address the problems:

1. Name things well. That might sound glib, but it isn't. The traditional loop variables `i`, `j`, and `k` are a heap of trouble in Flabbergast. The opposite end of the spectrum `where_super_explicit_names_that_no_one_can_confuse` are used is equally unpleasant. If the name is semantically meaningful and the same term isn't overloaded (_e.g._, `mu` can be the magnetic field permeability of free space and the coefficient of friction), then it is probably a good choice and collisions will be intentional (making `mu` unfortunate, but `magnetic_perm` quite reasonable).
2. Use frames as name spaces. While frames are _not_ name spaces, contextual lookup can be used to help the situation. Using `parser.space` can provide more information than `space`.
3. Name library imports with `_lib`. It is good hygiene to import libraries using `foo_lib : From lib:foo` as if there is a collision, the values will be the same anyway.
4. Use lookup traps when needed. If lookup should stop beyond a certain point, define the name to `Null` to stop lookup from continuing. In templates, if the value needs to be provided or the name is common (_e.g._, `name` or `enabled`) use the `Required` definition to trap lookup.

For a good reflection on naming, read [What's in a Name](https://blogs.janestreet.com/whats-in-a-name/) from Jane Street Tech Blog.

## Patterns
In all languages, having common design patterns that introduce intent are important and this is especially true in languages that are more flexible, since they serve the added duty of communicating intent.

### Self-Rendering Items
If a list of items is generated, it can be useful to give each an attribute that renders the result. The result can then be accumulated from this attribute.

    arg_tmpl : Template {
      name : Required
      value : Required
      spec : "--" & name & " " & value
    }
    switch_tmpl : Template {
      name : Required
      active : True
      spec : If active Then "--" & name Else ""
    }
    binary : "foo"
    args : {
      input : arg_tmpl { name : "input"  value : "~/input.txt" }
      compression : arg_tmpl { name : "c"  value : 8 }
      log : switch_tmpl { name : "log" }
    }
    arg_str : For arg : args Reduce acc & " " & arg.spec With acc : binary

This will allow each argument to choose how to render itself as a string and provide a uniform way to aggregate the results, free of the rendering logic itself. It is convention to use `spec` (_i.e._, specification) or `value` as the name for rendered results.

Notice that `switch_tmpl` has a sane default for `active`. Since users can always override, it is best to specify a default if one is reasonable.

### Modifiable Inputs
In the previous example, an argument list is created for an executable binary. The problem with this design is that it becomes impossible to modify. It would be better to keep it as a template:

    foo_tmpl : Template
      arg_tmpl : Template {
        name : Required
        value : Required
        spec : "--" & name & " " & value
      }
      switch_tmpl : Template {
        name : Required
        active : True
        spec : If active Then "--" & name Else ""
      }
      binary : "foo"
      args : Template {
        input : Template arg_tmpl { name : "input"  value : "~/input.txt" }
        compression : Template arg_tmpl { name : "c"  value : 8 }
        log : Template switch_tmpl { name : "log" }
      }
      arg_str : For arg : args {} Reduce acc & " " & (arg {}).spec With acc : binary
    }
    foo_devel : foo_tmpl {
       binary : "foo-nightly"
       args +: {
         log +: { value : False }
       }
    }

Now, frames inheriting from `foo_tmpl` can easily change the `args`.

The attribute names, if useful, can be included in the template instantiation.

    arg_tmpl : Template {
      name : Used
      value : Required
      spec : "--" & name & " " & value
    }
    binary : "foo"
    args : Template {
      input : Template arg_tmpl { value : "~/input.txt" }
      compression : Template arg_tmpl { value : 8 }
    }
    arg_str : For arg : args {}, name : Name
      Reduce acc & " " & (arg {}).spec
      With acc : binary

### Enabled Items
Since there can be logic to decide if an item should be included or not, it is tempting to write code as follows:

     x : If y > 5 Then Template a_tmpl { a : 3 } Else Null

And then include a null check. There are two problems with this approach: the condition cannot be modified and once the value has been replaced with null, it becomes difficult to override `x`, since a null check must be performed every time.

The solution is to add an `enabled` attribute:

     x : Template a_tmpl {
       a : 3
       enabled : y > 5
     }

This means that the `+:` attribute definition can always be used. In sequence, one could do the following in an inheriting template:

     x +: { enabled : False }

Although `x` is now permanently disabled, in an inheriting template, the following is still legal:

     x +: { a : 9 }

This also allows the logic to be extended in more complex ways:

     x +: { enabled +old_enabled: old_enabled && database_connection.enabled }

In the case where a collection of items is used, this can be trivial to work with using a `Where` clause:

    args : Template {
      input : Template arg_tmpl { name : "input"  value : "~/input.txt" }
      compression : Template arg_tmpl { name : "c"  value : 8 }
      log : Template switch_tmpl { name : "log"  value : True }
    }
    args_frames : For arg : args {} Select arg {}
    args_str : For arg : args_frames Where arg.enabled Reduce acc & " " & arg.spec With acc : ""

### Flexible Rendering
This uses frame re-parenting to create multiple ways of rendering the same data:

    weather : Template {
      toronto : data_tmpl { city : "Toronto"  temperature : 35  humdity : 71 }
      waterloo : data_tmpl { city : "Waterloo"  temperature : 30  humdity : 64 }
      ottawa : data_tmpl { city : "Ottawa"  temperature : 33  humdity : 68 }
    }
    xml : {
       data_tmpl : Template {
         spec : "<city><name>\(city)</name><temperature>\(temperature)</temperature></city>"
       }
       weather_data : weather {}
       str : "<weather>" & (For city : weather_data Reduce acc & city.spec With acc : "") & "</weather>"
    }
    text : {
       data_tmpl : Template {
         spec : "\(city)\t\(temperature)\t\(humidity)"
       }
       weather_data : weather {}
       str : For city : weather_data Reduce acc & "\n" & city.spec With acc : "City\tTemp\tHum"
    }

Here, the data is specified separate and the renders are provided as a template allowing different parts of the program to use different rendering methods.

### Multi-part Rendering
While `spec` or `value` attributes are extremely useful, sometimes, it can be helpful to have more than one. Consider generating C code: there need to be prototypes and implementations for each function.

    c_function_tmpl : Template {
      signature : Required
      body : Required
      prototype_spec : signature & ";\n"
      implementation_spec : signature & "{\n" & body & "\n}\n"
    }
    functions : ...
    signatures : For function : functions Reduce acc & function.prototype_spec With acc : ""
    implementations : For function : functions Reduce acc & function.implementation_spec With acc : ""
    c_file : signatures & "\n" & implementations

### Contextual Accumulation
There are situations where it is desirable to have an accumulator that is not common to all cases. For instance, suppose Python code was being generated and the indentation must be correct:

    python_stmt : Template { line : Required  spec : indent & line }
    python_group : Template {
      statements : Required
      spec : For statement : statements Reduce acc & statement.spec & "\n" With acc : ""
    }
    python_block : Template {
      line : Required
      statements : Required

      parent_indent : Lookup indent In Container
      indent : parent_indent & "\t"
      spec : For statement : statements
        Reduce acc & statement.spec & "\n"
        With acc : (parent_indent & line & "\n")
    }
    indent : ""

These templates can now be nested and the resulting `spec` will have correct indentation. Groups here, are collections of statements at the same indentation level, so any statement or block inside will continue contextual lookup until it finds `indent`. Each block takes the existing `indent` and then adds another tab, making all of the statements contained within be indented.

Finally, the `indent` definition is an contextual lookup trap. If the user of these templates did not define an `indent` value at the top level, contextual lookup would continue until it found this one.

The following would produce correctly indented, if not pointless, Python:

    transform_var : Template python_group {
        var : Used
        statements : [
            python_stmt { line : "\(var) = bar(\(var))" },
            python_block {
                line : "if \(var) < 10:"
                statements : [
                    python_stmt { line : "return \(var)" }
                ]
            }
        ]
    }
    pycode : python_group {
        statements : [
            python_block {
                line : "def foo(x):"
                statements : [
                    python_stmt { line : "return 3 * x" }
                ]
            },
            python_block {
                line : "def foo2(x):"
                statements : [
                    transform_var { var : "x" },
                    python_stmt { line : "return 3 * x" }
                ]
             }
         ]
     }

In most other languages, this effect would be achieved by passing the indentation value as a parameter to every function (plumbing) while Flabbergast can use contextual lookup to do the heavy lifting. It's also unusual to pass the value rather than a proxy in other languages; for example, most programmers would pass an indentation number rather than the indentation prefix.

### Layered Overriding

In some cases, it is desirable to combine templates. There is no direct template merge operation, but is is possible to create a mixin that extends a template. For instance:

    a_tmpl : Template { x : 3  y : 4 }
    b_tmpl : Template a_tmpl { z : x + y }

Here, `b_tmpl` extends `a_tmpl`. If the changes that `b_tmpl` are general, it might be nice to have a higher-order way to apply those changes to any template, not only `a_tmpl`. This could be accomplished in the following way:

    b_ifier : Template { base : Required  value : Template base { z : x + y } }
    a_tmpl : Template { x : 3  y : 4 }
    b_tmpl : b_ifier(base : a_tmpl)

The `b_ifier` function-like template can apply the same changes to any desired template; it is a mixin, capable of extending the behaviour of a template. The overriding mixins can be layered on top of one another:

    overrides : [
      Template { base : Required  value : Template base { x : 4 } },
      Template { base : Required  value : Template base { y : 3 } },
      Template { base : Required  value : Template base { z : 2 } }
    ]
    foo_tmpl : Template { a : 1 }
    derived_tmpl :
      For override : overrides
      Reduce override(base : tmpl)
      With tmpl : foo_tmpl
    frame : derived_tmpl { }

Here `derived_tmpl` is the combination of all the layered overrides in the `overrides` frame. It's also possible to compose two overriding mixins:

    a_ifier : Template { base : Required  value : Template base { x : 2 * y } }
    b_ifier : Template { base : Required  value : Template base { z : x + y } }
    ab_ifier : Template a_ifier { value +original: b_ifier(base : original) }

This will compose `a_ifier` and `b_ifier` into `ab_ifier`.

### The Up-Down Problem
Many rendering problems have this conflicting nature: containers have a limit on size, but also need their sized to be determined by their contents. This leads to a kind of dynamic system where the tree of nested boxes needs to be traversed multiple times to determine the optimal layout. Examples of this include hierarchical layout of widgets in a GUI (_e.g._, Gtk+ has its collection of size allocation methods) and text layout in document (_e.g._, line breaking and spacing in LaTeX).

Flabbergast can't actually make the problem easier to solve, but it can make it easier to define. In procedural and functional languages, the state information from each step of the rendering process must be explicitly managed. Flabbergast can use lookup to automatically unwind the dependency order of the operations given the hierarchy of widgets. If circular evaluation occurs, then the problem is specified in a way that could only work with iterative refinement.

Consider something like:

    text_box : Template {
        text : Required
        width : Required
        height : Required
        # Explicitly define our minimums to be passed up.
        min_width : 5  min_height : 1
        preferred_width : 10
        # Here, our preferred height (which we pass up) is defined in terms of
        # our width, which is passed down. This might result in circular
        # evaluation depending on the implementation of our container.
        preferred_height : Length text * 1.5 / width
        value : # Render output using width and height
    }
    fancy_border : Template {
        child : Required
        width : Required
        height : Required
        # We enforce what our parent forced on us onto our child widget. (Down)
        exp_child : child {
            width : Lookup width In Container - 1
            height : Lookup height In Container - 1
        }
        # We set our “up” values based on our child.
        min_width : exp_child.min_width + 1
        min_height : exp_child.min_height + 1
        preferred_width : exp_child.preferred_width + 1
        preferred_height : exp_child.preferred_height + 1
        value : # Render output using width, height, and exp_child.value
    }
    vertical_box : Template {
        children : Required
        width : Required
        height : Required
        child_height : height / (For c : children Reduce acc + 1 With acc : 0)
        exp_children : For c : children Select c { width : Drop  height : Lookup child_height In Container }
        # The maximum of the minimum width of the children.
        min_width : For c : exp_children Reduce If c.min_width > acc Then c.min_width Else acc With acc : 0
        min_height : For c : exp_children Reduce c.min_height + acc With acc : 0
        # The maximum of the preferred width of the children.
        preferred_width :
          For c : exp_children
            Reduce If c.preferred_width > acc Then c.preferred_width Else acc
            With acc : 0
        preferred_height : For c : exp_children Reduce c.preferred_height + acc With acc : 0
        value : # Render output using exp_children's values
    }

The most interesting part of this example is `text_box.preferred_height`. It might result in circular evaluation. It also might not. The determining factors will be how the containers are implemented and the preferred dimensions of its siblings. This is somewhat metastable: If a text box is placed inside a vertical box (or inside a fancy border placed in a vertical box) the answer will be stably produced. In a horizontal box (not shown), it would almost certainly be disastrous circular evaluation. However, there are situations where it might still work depending on the exact properties of the container's layout algorithm.

The fact that the behaviour is not universally consistent is both good and bad. In the abstract, the problem is framed this way and it makes sense: the optimal layout might not exist depending on the desired composition and the solution would be to insert extra nodes into the rendering tree to capture the intended output (_e.g._, LaTeX's `minipage` environment). However, circular evaluation is not the most helpful error. To achieve this in a traditional language would be much more complicated; the would have to be many passes and widgets would need a way to defer their decision until the next pass. Adding a new widget which requires extra information or passes would involve invasive changes to the render (see [the expression problem](http://en.wikipedia.org/wiki/Expression_problem)). The Flabbergast interpreter is essentially dynamically determining the order in which to do the passes from the implied dependencies.

## The Standard Library
Because Flabbergast is meant to render data, it has a rather lean standard library. Most languages have the following major elements in their standard libraries:

 - data structures. These are rather unnecessary once frames have been accepted as the one true data structure.
 - search and sort algorithms. These are built into the language via the fricassée expression.
 - I/O. This is highly discouraged as configurations should be hermetic and the controlling program may have security reasons to restrict access to the world. Since there is no way to control the order of operations, output that cause side-effects in the program is discouraged. In particular, if writing is permitted, should it happen immediately on evaluation or after success of the whole program (_i.e._, can a program which produces an error still write).
   - files. Traditional file I/O has too much state to be practical. The best compromise would be operations to read and write whole files. The write issue still applies.
   - GUIs. This is entirely mutable state.
   - databases. Read-only queries of databases are probably reasonable as the results can be converted to frames. Writing can also be done transactionally, dependent on the success of the whole program.
   - network. Again, this is entirely mutable state. It would certainly be possible to do read-only network access, however, the semantics surrounding network failures is unclear. Suppose an HTTP GET is available: what errors should be handled by the program? 404? 403? 500? non-existent domain? Any can be argued, but the question is: which represents the failure of the program and which represent the failure of the network and, most importantly, what is the correct behaviour of the program when an error of any kind occurs?
 - threading. Generally, threading control is only needed so that data structures and I/O can be done safely. Since both of those are handled outside the language, threading should be too.
 - string manipulation. Yuck. String manipulation is the source of all computational suffering. Embrace the frames.
 - regular expressions and parsing. Ignoring that parsing is usually absent, generally, this is done on the strings read from files.
 - mathematics. Computation is good!
 - generating XML, JSON, and other formats to be read by the programs being configured.

The Flabbergast libraries look like they do to avoid I/O and minimise the ensuing insanity.

There are platform specific libraries, which end in `interop`, to provide access to the underlying libraries. Do not access these libraries directly. Instead, using the corresponding platform-independent library (_e.g._, `utils` rather than `utilsinterop`). These libraries enhance the functionality of these base libraries, but the originals may still be visible in stack traces; consider them an implementation detail.

Complete, up-to-date documentation is available on the [Flabbergast Documentation](http://docs.flabbergast.org/) site.

### General Utilities (`lib:utils`)
This library is a collection of convenience function-like templates. It has several broad categories of functions:

 - aggregation functions: `all`, `any`, `count`, `first`, `last`, `max`, `min`, `product`, `str_concat`, `sum`
 - filtering functions: `enabled`, `non_null`
 - precision formatting: `float_to_str` `int_to_str`,
 - Unicode manipulation: `int_to_char`, `str_categories`, `str_codepoints`
 - general string manipulation : `str_escape`, `str_find`, `str_lower_case`, `str_pad`, `str_replace`, `str_slice`, `str_upper_case`, `str_trim`
 - string analysis: `str_prefixed` (check for prefix), `str_suffixed` (check for suffix), `str_utf8_length`
 - parsing: `parse_float`, `parse_int`
 - frame manipulation: `frame`, `or_default`, `int_to_ordinal`, `is_list` (check if a frame has auto-generated attribute names)

Many of these functions have a `_list` variant. In the list variant, the output is a frame with each of the arguments treated separately (_e.g._, `str_utf8_length_list("what", "€")` returns `[ 4, 2 ]`) while the non-list variant will return either an aggregation of the results of the first (_e.g._, `str_utf8_length("what", "€")` returns `6`). 

There are also several `ifier` function-like templates. These are higher-order templates: they transform the output of a function-like template. For instance, `sumifier` converts a function-like template that returns a list of numbers into one that returns a sum. The `sum` function-like template is the `identity` function-like template transformed by the `sumifier`. Similarly, the `str_utf8_length` is also made by `sumifier` applied to `str_utf8_length_list`.

There is also a good example of using lookup to do useful work: `str_categories`. This function-like template takes a string and returns the Unicode category for each character in the string. The categories are found by lookup. The `str_categories_spec` function converts them into the two letter names used in the Unicode specification. This function can be re-purposed though; for instance, creating an `is_digit` check could be done by amending the template and setting `number_decimal` to true, and all the other categories to false.

The `str_escape` and corresponding `str_transform` templates allow escaping a string for output. To do so, first, create a frame that contains all the transformations to be performed, then pass this frame and the strings to be escaped to `str_escape` to produce an escaped version of the input. There are two kinds of transformation supported: `char_tmpl` transforms a single character to an escaped version (_e.g._, &amp; → `&amp;` or " → `\"`) and `range_tmpl` converts characters in a range into a numeric escape (_e.g._, ë → `%C3%AB` or ë → `\u00EB`). The escaping will preferentially choose a character transformation to a range transformation. Ranges must not overlap.

Currently, Flabbergast lacks a documentation generator, so the best information is the source comments.

### Rendering (`lib:render`)
This library converts frames in to other formats, including INI, JSON, Python, XML, and YAML. In short, the library provides templates such that a frame can be rendered to output formats. The exact semantics are different for each format, but, in general, the templates are composed into the desired structure providing the hierarchy and the library generates a string containing the output file. Escaping is handled automatically.

### Mathematics (`lib:math`)
The mathematics library contains all the usual functions for:

 - exponentiation and logarithms: `log`, `power`
 - trigonometry (`circle` and `hyperbola`): `arccos`, `arcsin`, `arctan`, `cos`, `sin`, `tan`
 - rounding: `absolute`, `ceiling`, `floor`, `round`
 - constants: `natural`, `pi`, `tau`

Much like `lib:utils`, there are `_list` variants of these. The trigonometric functions are a bit unique: by setting `angle_unit`, the units used for angles can be changed between degrees, gradians, radians, and turns; the default is radians.

### Relational Database Access (`lib:sql` and `sql:`)
Flabbergast provides access to SQL-like databases available through JDBC and ADO.NET providers. Fundamentally, this system exists in two parts: `lib:sql` provides mechanisms for composing queries and the `sql:` URIs connect to databases. When accessing a URI, for instance, `sql:sqlite:foo.sqlite`, Flabbergast scans the database metadata and provides that as a structure of frames. The `lib:sql` library can then be used to compose a query, checking that the query is valid given the format of the database, and converts the results to a list of matching rows. This format is easily ingested by `For Each`.

Not all SQL data types can be converted to Flabbergast. The templates in the library smooth out the differences between different data source providers. This means that some functionality might not be available for all different database types.

    sql_lib : From lib:sql
    results : sql_lib.retrieve {
       source : From sql:postgresql:pgdb.example.com/human_resources
       columns : {
        name : expr.str_join { args : [ column.employee.first_name, " ", column.employee.last_name ] }
        salary : column.payroll.salary
      }
      where : [ expr.equal { left : column.employee.emp_id  right : column.payroll.emp_id } ]
    }
    report :
      For Each result.value
        Reduce acc & "\(name) makes $\(salary) per year.\n"
        With acc : ""

This is not intended as a general interface to relational databases. In particular, the column and table names really need to be Flabbergast identifiers, which is not generally true. The complexity of the queries is also limited since the system tries to work on multiple databases seamlessly. It might be best to create a view in a compatible format.

Flabbergast has a standard format for database connection strings, due to the fact that no one else does. That format is: `sql:<provider>:<file path>` or `sql:<provider>:[<user>[:<password>]@]host[:<port>]/<catalog>` optionally suffixed by a `?` and various ampersand-separated key-value pairs. The format used depends on the database (_e.g._, SQLite uses the former, PostgreSQL uses the latter). Additional libraries are required for each different database provider.

For a list of supported databases and their options, view `man flabbergast_sql`. Currently, only a handful of databases are supported. Patches for new databases are welcome.

### Environment Variables (`env:`)
Current environment variables may be accessed with `env:` URIs. For instance, to access the current path, use `env:PATH`.

### External Files (`ftp:`, `ftps:`, `file:`, and `res:`)
These read data from an external file and provide a `Bin` value containing the contents of the file. The `res:` URI gathers data from files intended to be resources. It searches the same paths as `lib:` URIs for a matching file name.

### HTTP Files (`http:`, `https:`)
These read data from a web URL and provide a frame with two attributes: `data` containing the contents of the file, as a `Bin` value; `content_type` containing the content-type string provided by the server.

### Parsing (`lib:parse`)
A mirror to `lib:render`, this contains `parse_json`, which parses a string containing JSON and produces templates compatible with `lib:render`. It will produce a template of the form:

     Template {
       json : Used
       json_root : <contents of file>
     }

The structure of the JSON file is preserved using template instantiations: `json.object`, `json.list`, and `json.scalar`. These mirror exactly the format in `lib:render`. This means a JSON file can be re-encoded using:

     render_lib : From lib:render
     parse_lib : From lib:parse
     foo : parse_lib.parse_json(json_data) {
        json : render_lib.json
     }
     value : foo.json_root.json_value

It is reasonable to supply other templates to convert the JSON input to something else. For instance, these transform it in to Flabbergast objects, assuming all the names are Flabbergast-compatible:

     foo : parse_lib.parse_json(json_data) {
        json : {
          list : Template {
            value : children
          }
          scalar : Template {
            value : arg
          }
          object : Template {
            value : For child : children Select child.json_name : child.value
          }
        }
     }
     flabbergast_value : foo.json_root.value

Due to contextual lookup, it is possible to define templates inside to change the behaviour at lower levels:

     foo : parse_lib.parse_json(json_data) {
        json : {
          list : Template {
            # If there is a list in attribute named “integers”, convert all the
            # numbers in it to integers.
            json :
              If json_name == "integers"
                Then {
                  scalar : Template {
                    json_value : arg To Int
                } Else {}
            json_value : children
          }
          scalar : Template {
            json_value : arg
          }
          render_lib : From lib:render
          object : Template {
            # The tree under an attribute named “magic” will be converted back
            # to JSON as a string; the rest to Flabbergast.
            json : If json_name == "magic"
              Then render_lib.json
              Else Lookup json in Container
            json_value : For child : children Select child.json_name : child.json_value
          }
        }
     }
     flabbergast_value : foo.json_root.json_value

### Apache Aurora (`lib:apache/aurora`)
Configures jobs for running on the Apache Aurora framework, which managers long-running jobs on Mesos.

### Archives (`lib:unix/ar`)
Since Flabbergast only outputs a single value, it can be awkward to generate multiple files. To combat this, it supports creating `ar`-style archives, which are plain text. To do this, instantiate `archive_tmpl` with the `args` being a list of `file_tmpl`, each with `file_name` and `contents`. The resulting file can then be unpacked using the UNIX `ar` program.

### Permission Specifiers (`lib:unix/perm`)
Helps generate UNIX-style file permissions and format them nicely.

### Cron Specifiers (`lib:unix/cron`)
Allows generating the time specifications in for `crontab` entries.

## Automatic Documentation

Flabbergast has a built-in documentation system. Unlike, say JavaDoc, Flabbergast documentation is parsed by the compiler and certain checks are done on it. Adding documentation goes in two places: an introduction and attribute documentation.

An introduction is associated with a whole file and appears at the top of a file:

    Introduction{{{A library of templates for doing useful things.}}}

Before each attribute, a description of the attribute can be inserted in triple braces:

    {{{The URL for the Flabbergast project.}}}
    flabbergast_url : "http://flabbergast.org"

That will continue inside literal frames and templates:

    {{{All the URLs.}}}
    urls : {
      {{{The URL for the Flabbergast project.}}}
      flabbergast : "http://flabbergast.org"
      {{{The URL for the Flabbergast project.}}}
      github : "https://github.com"
    }

But it won't traverse other expressions:

    {{{This will be in the documentation.}}}
    thingie : If x Then {
     {{{This will never be displayed}}}
     saddness : True
    } Else Null

Inside documentation, there are text formatting tags: `\Emph{x}` will add emphasis to the text, and `\Mono{x}` will type set it in a fixed-width font. Hyperlinks can also be inserted using `\Link{http://flabbergast.org/|Click to go to the Flabbergast page}`. Cross-references to other attributes can be created using `\{urls.github}` or to other libraries `\From{lib:x}`.

## Questions of Varying Frequency

The following answers are universally discouraging of certain ideas as these ideas are not congruent with normal Flabbergast use. More philosophical questions are answered in [FAQ](faq.md).

### When do I end something with `_tmpl`? How do you feel about Hungary?

I sometimes suffix attribute names that hold templates with `_tmpl`, but not always. I would love to provide some rule, but it is one of those value judgements. In particular, there are two scenarios I see: mixed frames and meta-templates. Templates meant to be used in function-like scenarios should never end with `_tmpl`.

In certain contexts, there is a mix of different types and suffixing templates can be helpful. For example:

    thingie_tmpl : Template { name : Required }
    thingies : [ thingie_tmpl { name : $a } ]
    num_thingies : For x : thingies Reduce acc + 1 With acc : 0
    length_thingies : For x : thingies Reduce acc + Length x.name With acc : 0
    thingie_csv : For x : thingies, pos : Ordinal Reduce acc & (If pos > 1 Then "," Else "") & x.name With acc : ""

This context has a number of unrelated items. They are of different types and meant for different purposes. Naming the template `thingie_tmpl` gives an indication that this is the intended template to be used by all these other `thingie` definitions.

The other situation that occurs relatively frequently is the meta-template. There are many of these in the compiler. It is often useful to have a template which is never meant to be instantiated by the end user; it is simply a base on which to build more templates. In that case, the `_tmpl` suffix functions almost as an indicator of abstractness. For example:

    instruction_tmpl : Template {}
    add_instruction : Template instruction_tmpl { left : Required  right : Required }
    multiply_instruction : Template instruction_tmpl { left : Required  right : Required }

For inheritance reasons, it will provide a place for common plumbing between all the other instructions, but it isn't meant to be consume directly.

Hungary makes delicious food. Please do not apply it to Flabbergast.

### How do I use a string as the URI in `From`?

This is intentionally not possible. For various embedding reasons, it's important that the dependencies of a file are known during compilation. Importing new files can lead to all kinds of misery for doing any analysis of the code, and might represent a security problem.

If the goal is to read from a configuration specific file, then invert the inheritance structure:

    global_config : Template {
      name : Required
      machine_info : From "info:machine/\(name)" # Desired, but unreal syntax
    }
    local_config : global_config { name : "foo" }

The correct refactoring is something like:

    global_config : Template {
      machine_info : Required
    }
    local_config : global_config {
      machine_info : From info:machine/foo
    }

In general, the solution is to either use contextual lookup or inheritance to find the needed data. If there is a collection of data, then it might be useful to have a “footprint”. In a global library, create a template for each type of configuration:

    global_config : Template {
      x : footprint.machine_info.cpu
    }
    test_config : Template global_config {
    }

Then create a single situation-specific footprint:

    foo_footprint : {
      machine_info : From info:machine/foo
    }

Then for each configuration is only a marriage of footprint and template:

    config_lib : From lib:config_templates
    footprint : From lib:foo_footprint
    config : config_lib.global_config { }

These final files tend to be rather short.

### How do I create a new type?

There isn't a mechanism to create user-defined types as such. There are two reasons: what would be the look-up rules for type names and how would one define new primitive operations? In general, there's no reason to create a new type: create a new frame with a well-defined interface. For all typical purposes, that is as good as defining a new type. In the typical class-based object-oriented sense, there is no guarantee that a frame will inherit the intended template, but if it has a compliant interface, there isn't necessarily any harm.

Many languages have restricted value sets: enumerations (C, C++, Java, C#) or symbols (LISP, Ruby, SmallTalk). These are intended to be universally comparable values that exist in some kind of special name space. Symbols are essentially strings, so the `$` identifier-like string is essentially the same. An enumeration-like structure can be easily made:

    my_enum : For x : [$a, $b, $c] Select x : x

The value of enumerations is mostly that they are cheap to compare and use in `switch` statements. Since none of that really translates to Flabbergast, this isn't so pressing. Rather than have dispatch logic, put the results in the frames or templates.

Again, the way to think of this is to invert where the data is stored, rather than:

    my_enum : For x : [$a, $b, $c] Select x : x
    tmpl : Template {
        enumish : Required
        name : Required
        value : name & " = " & (
            If enumish == $a Then "0" Else
            If enumish == $b Then "1" Else
            If enumish == $c Then "2" Else
            Error "Bad enum.")
    }
    x : tmpl { name : "foo" enumish : $a }

It would also be completely reasonable to provide a frame with all the needs:

    my_enum : {
      a : { id : 0 }
      b : { id : 1 }
      c : { id : 2 }
    }
    tmpl : Template {
        enumish : Required
        name : Required
        value : name & " = " & enumish.id
    }
    x : tmpl { name : "foo" enumish : my_enum.a }

That can be taken further by making the enumeration values as templates:

    my_enum : {
      a : Template { value : "0 \(name) 0" }
      b : Template { value : "1 \(name) 0" }
      c : Template { value : "0 \(name) 1" }
    }
    tmpl : Template {
        enumish : Required
        name : Required
        value : name & " = " & (enumish {}).value
    }
    x : tmpl { name : "foo" enumish : my_enum.a }

