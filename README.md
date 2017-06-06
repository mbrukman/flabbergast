# The Flabbergast Programming Language
![](https://rawgithub.com/flabbergast-config/flabbergast/master/flabbergast.svg)

Flabbergast is a object-oriented macro system that uses contextual lookup (dynamic scope) and inheritance to making writing complex configurations easy.

In most languages, afterthoughts are not appreciated. However, most configurations are nothing but afterthoughts and exceptions. “I want the test version of the webserver to be the same as the production except for the database connection.” “I want the videos SMB share to be the same as the documents SMB share with a few extra users.” Flabbergast is built to service “except”, “and”, and “but”.

There is nothing so unutterably stupid that people won't waste their time on it. – Mark Rendle, [The Worst Programming Language Ever](http://skillsmatter.com/skillscasts/6088-the-worst-programming-language-ever). This was not explicitly about Flabbergast.

## Community

We are around on FreeNode IRC in `#flabbergast`, [Google Groups](https://groups.google.com/forum/#!forum/flabbergast-users), [Twitter](http://twitter.com/co_0nfig) or [Google+](https://plus.google.com/communities/103010827049942376743). Feel free to file bugs and ask question on GitHub.

## Documentation

There are two important pieces of documentation: the friendly [manual](flabbergast-manual.md) and the [API docs](http://docs.flabbergast.org). You might want to consult the [glossary](glossary.md) when you get started and keep the [syntax cheatsheet](syntax-cheatsheet.md) around.

The manual describes the syntax is broad strokes and a more prosaic explanation of how it works with examples. It also describes philosophy, design patterns, and libraries. It's worth looking at some of the [examples](examples). If you want more whimsical examples, look at the [Rosetta Code](rosettacode) solutions.

For programming language geeks, the [language spec](http://docs.flabbergast.org/flabbergast_language.7.html) describes the syntax and behaviour with formal semantics (or, at least, a poorly-written attempt at formal semantics). This is provided as a manual page such that it is included with the installed packages.

If interested in compiler hacking, the language can be compiled to a virtual machine simpler to implement than the full language spec and a self-hosting compiler is provided. The VM is documented in the [KWS VM](kws-vm.md) document, which does not include formal semantics because they are largely implied by the language spec.

## Installation
For Ubuntu and Debian systems, Flabbergast can be easily installed by:

    apt-add-repository ppa:flabbergast/ppa && apt-get update && apt-get install flabbergast-java flabbergast-cil

MacOS X packages are available on the [releases](https://github.com/flabbergast-config/flabbergast/releases) page.

There are two editions of the language: one which uses the Java Virtual Machine (`flabbergast-java`) and one which uses the Common Language Infrastructure (`flabbergast-cil`). You need only install one. You can then get started by running `flabbergast` to process a file or `flabbergast-repl` to start an interactive session. If you want to run a specific edition when both are installed, use `jflabbergast` or `nflabbergast` for the JVM and CLI versions, respectively. The MacOS packages include only the Java edition.

For efficiency, Flabbergast allows pre-compilation of libraries. This can be done using `update-flabbergast`. It can only update directories writable by the user; but it is automatically trigged upon installation of new packages containing Flabbergast code. You may wish to keep a set of your own libraries in `~/.local/share/flabbergast/lib`, and it will manage those too.

Maven users can get the Java edition from [The Central Repository](http://search.maven.org/#search|ga|1|g%3A%22com.github.apmasell.flabbergast%22). The dependencies are:

    <dependency><groupId>com.github.apmasell.flabbergast</groupId><artifactId>stdlib</artifactId><version>$VERSION</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.github.apmasell.flabbergast</groupId><artifactId>compiler</artifactId><version>$VERSION</version><scope>runtime</scope></dependency>

or you can download the JARs alone using:

    mvn -DgroupId=com.github.apmasell.flabbergast -DartifactId=flabbergst-stdlib -Dversion=$VERSION dependency:get
    mvn -DgroupId=com.github.apmasell.flabbergast -DartifactId=flabbergast-compiler -Dversion=$VERSION dependency:get

substituting `$VESRION` as appropriate. To run it, invoke Java on the JARs and run `flabbergast.MainPrinter` to run the default interface, which simply runs the provided script and dumps the output, or `flabbergast.MainREPL` to access the interactive debugger.

NuGet users can install the CLI edition from [NuGET](http://nuget.org/packages/flabbergast/). You can install the package at the package management console using:

    Install-Package flabbergast

or from the command line using:

    nuget install flabbergast

## Building
To build Flabbergast, you will need to have an existing copy of Flabbergast or obtain the generated compiler sources directly. You will need GNU AutoTools, Flabbergast, Make, rsync, and the preqrequistes to build one of either the JVM edition or the CLI edition. For the JVM edition, you will need the JVM 1.7 or later, [ObjectWeb ASM](http://asm.ow2.org), [JLine](http://jline.sourceforge.net/), [Commons CLI](https://commons.apache.org/cli/), and [Android JSON](http://source.android.com/index.html). If you are building the CLI edition, you will need either Mono or the Common Language Runtime, the C# compiler, and MSBuild.

To install all these packages on Ubuntu/Debian, do:

    sudo apt-get install java8-jdk autotools-dev libasm4-java libjline-java libcommons-cli-java libandroid-json-org-java libmetainf-services-java msbuild mono-mcs rsync

To build the compiler, if Flabbergast is installed, run the bootstrap step:

    ./bootstrap

If it is not available, download the matching bootstrap package from the Internet using:

    ./bootstrap-from-web

Then perform a normal AutoTools install:

    ./configure && make && sudo make install

## Implementation
There are two implementations of Flabbergast. The self-hosting version targets the JVM and the CLR.

The self-hosting compiler is rather strange, as it is not really self-hosting. The self-hosting compiler is actually a Flabbergast program that, with added syntax templates, generates a compiler for a Flabbergast compiler in a target programming language. This generated compiler reduces Flabbergast syntax to KWS VM bytecode, which are then reduced to the native VM's bytecode. This is conceptually easier to understand and allows more code re-use.

Each platform also contains an implementation of the runtime library and non-portable pieces of each library.

## Mission
Flabbergast's mission is to provide a language and libraries to create configuration files for many different software applications from composable pieces that minimises the plumbing and stamp coupling while doing as much logic and validation as possible to ensure correct configuration before deployment.

## Patches
Patches are welcome and patches are preferred to whining. For details, see [Conduct unbecoming of a hacker](http://sealedabstract.com/rants/conduct-unbecoming-of-a-hacker/). In general, the rules are as follows:

- It is very important that the language stays in a small sandbox. Free roaming of the host system or the Internet is not permitted.
- Changes to the compiler or runtime that are invisible to user are welcome. Bonus points if you apply an improvement to multiple platforms.
- Changes to the libraries are welcome with two conditions: names are `lower_snake_case` and if the interface of a platform-dependent library is changed, the other implementations get updated, even if they are stubs that throw “not implemented” errors.
- Every change to the language itself is a tattoo. We must be conservative and sure that we aren't getting a face tattoo we will regret later. Expect slow and cautious. Many things deserve to be put in the `X` experimental keyword name space until a community process gives feedback.

## Miscellaneous
The Flabbergast language would not be possible without the help of [Kyle](https://github.com/edarc) and Jonathan.

The logo is the worst symbolic representation of contextual lookup, previously called inside-out lookup.

At every point, I made design decisions that were what I thought are the “best”. Some of them are going to seem wrong to you; some of them are going to seem wrong to me. I will happily tell you the reason or goal for a decision, but I will never defend it. Experience always changes our views on things, but one cannot regret decisions where one's past self failed to be clairvoyant–that is unrealistic. Even if a feature achieves its goal, you might disagree with the goal. My goals, in no particular order, are:

- Do not be truthy.
- Use words for complicated or unusual operations in preference to symbols. The symbols we have so far are mathematics and logical operators, string join, null coalescence, and the attribute definitions.
- Fail in the most helpful way possible (_i.e._, fail as early and explicitly as possible).
- Avoid magic names. Current, there are `args`, `value`, and the ones generated automatically in lists. I am strongly opposed to users knowing about the one in lists; in future, they might be randomised on startup.
- Avoid strings as messengers for structured data. This is part of the reason for lacking an evaluation operator.
- Do not dictate policy to the user. For instance, the predecessor to Flabbergast had a frame merge operator and it had control over how conflicting attributes were merged. The fricassée expressions give that control to the user. This is also why schema validation is unlikely; it will always be a subset of what the user really needs. This serves the “do not be truthy” goal.
- Efficiency is not as important as creating comprehensible, uniform code. Flabbergast programs should never be on a serving path, so having them be very efficient is not critical. In making decisions, doing something that uses the existing nature of the language is preferable to doing it efficiently. Efficiency is something that the compiler can worry about, not something use user should.
- Don't circumvent lookup. The lookup semantics are complicated, but powerful. Use them whenever possible, and avoid creating new semantics. Once the user has learnt them, they should never have to learn new ones, or make the mode switch to new lookup semantics.
- Don't circumvent overriding. Again, the override and definition semantics are complicated. A user should not have to switch through multiple modes of override semantics.

## Alternatives
You may be interested in [Jsonnet](http://google.github.io/jsonnet/doc/), which is inspired by the same forces, but takes design decisions in very different ways.
