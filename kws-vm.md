# KWS VM

KWS is the standard virtual machine for running Flabbergast programs on other virtual machines (e.g., CLR and JVM). It makes the following assumptions:

 - The target VM can cope with cyclic references between objects.
 - There is some kind of scheduling system.

Obviously, if the target VM does not support these features, the target platform is still a reasonable choice if they can be emulated. The purpose of this VM is to make the Flabbergast compiler easier to implement, by pushing some of the repetitive code into KWS VM, and make it easier to target a new VM by having the KWS VM be more like the target VM. In particular, the KWS VM ensures:

 - Everything is statically typed.
 - Lookup semantics are encapsulated.

The name of the VM is for the person who inspired it, not an acronym.

## Purpose

This virtual machine does not exist. The compiler theoretically emits byte-code for such a VM, except that it does not. This is meant to serve as a mental model for execution. The compiler, conceptually, emits KWS VM byte-code, each of which could be translated to an available VM.

The purpose of this VM is to provide intermediate semantics that bridge between Flabbergast and the underlying VM. Ultimately, the CLR, JVM, and LLVM are much more like each other than Flabbergast, so this provides a bridge; something that has semantics usable by Flabbergast, but unified across target VMs.

## Design

The VM uses static single assignment with the note that variable resolution and errors can introduce non-local control flow. The KWS has more types than the Flabbergast language:

| Type Name | In Flabbergast? | Mutable? | Description | Notes |
|---|---|---|---|---|
| `Any` | N | N | a boxed Flabbergast value | A value containing one of `Bin`, `Bool`, `Float`, `Frame`, `Int`, `LookupHandler`, `Template`, or `Null` |
| `Bin` | Y | N | an array of bytes | Must be able to contain nul bytes |
| `Bool` | Y | N| a Boolean | |
| `Context` | N | N | a list of frames | hold a list of frames for lookup to search |
| `DefinitionBuilder` | N | Y | an attribute builder holding `Definition` and `OverrideDefinition` | a map where from identifiers to `Definition` or `OverrideDefinition` values|
| `Definition` | N | N | the body of an attribute | A function closure with the arguments: the context (`Context`), the self frame (`Frame`), and the containing frame (`Frame`) and it returns a boxed result (`Any`) |
| `Float` | Y | N | an IEEE-754 floating point number | |
| `Frame` | Y | N | a “frame” | A map from identifiers to values are future of `Any` |
| `FricasseeMerge` | N | Y | a Fricassée merged-frame source | Creates a Fricassée source by joining multiple frames together. It can be mutated and all Fricassée operations based on it will get the updated form |
| `Fricassee` | N | N | a step in a Fricassée operation | One operation in a chain to build a Fricassée expression. A `Fricassee` value can be reused multiple times in different chains. |
| `Int` | Y | N | a signed integer | Typically, this is a 64-bit signed integer. |
| `LookupHandler` | N | N | a name resolving scheme | A function closure with the arguments are: the context (`Context`), a list of names (`Str`) |
| `NameSource` | N | Y | names to be resolved | A collection of names to be resolved |
| `OverrideDefinition` | N | N | the body of an overriding attribute | A function closure with the arguments: the original value (`Any`), the context (`Context`), the self frame (`Frame`), and the containing frame (`Frame`) and it returns a boxed result (`Any`) |
| `Str` | Y | N | a string of text | This is a string of Unicode text and always addressed by codepoint. |
| `Template` | Y | N | a “template” | A map from identifiers to `Any`, `Definition`, or `OverrideDefinition` values |
| `ValueBuilder` | N | Y | an attribute builder holding `Any` | a map from identifiers to `Any` values |

A identifiers is a string that follows the attribute naming rules of the Flabbergast language.

Most operations produce immutable values. Frames and templates are created from mutable values, but at the point of creation, they read the current state of those values and act accordingly. Future changes to their inputs are ignored. Also, the functions in the language, `Definition` and `OverrideDefinition`, can only communicate with others by `Any`. This means that no mutable value can be shared outside of a `Definition`, so the system appears to only deal in immutable values.

Each operation is described as follows:

    return_variable : ReturnType = operation<compile-time arguments>(run-time arguments)

Operations are organised in basic blocks:

    name(parameter1 : type1, parameter2 : type2, ...):
      o1 = operation1(parameter1)
      o2 = operation2(o1)
      return(o2)

The pseudo-type `Block` is used for compile-time arguments that must be a block in the current `Definition` or `OverrideDefinition`.

The VM uses single static assignment with block parameters, similar to Swift Intermediate Language. Blocks each have a type signature that must match exactly when used. These are denoted by `Block(T1, T2, ...)`, (_e.g._, `Block(Any,Int)`, is a block that has two parameters: an `Any` and an `Int`).

The self-hosting Flabbergast compiler generates a compiler in a language that then emits instructions in the target VM format. The self-hosting compiler requires some other facilities beyond what is described here in order to generate the target compiler. These are described in the self-hosting compiler and include, principally, the parser.

## Definitions and Flow Control

Each `Definition` or `OverrideDefinition` may run continuously until a lookup is required. It must then wait until the lookup's target has finished executing before proceeding. Therefore, the VM divides everything into independently scheduled futures and the Task Master executes futures until it is finished. Futures always terminate and provide the exit scenarios: return or fail. Every future has one or more listeners, that is, other futures (or the user) which rely on the result of this future.

If deadlock occurs, then circular evaluation has been detected and the VM should dump all the in-flight lookups for inspection.

## Basic Blocks

Each basic block, denoted by a name, takes one or more parameters, contains a list of instructions and then terminates with one of the terminal instructions. Most are listed below.

### Return Value (Terminal)
Passes control to the listeners and provide the value given as an argument.

    return(value : Any)

### Raise an Error (Terminal)
Produce a user-visible error message. The listeners must never execute.

    error(message : Str)

### Jump to Block (Terminal)
Passes control to another block. The correct number and type of parameters must be specified for the block being called. A block has access to only the values passed as parameters and those values defined in the least common ancestor of all the blocks in its callers, excluding itself.

    branch<name : Block(type, ...)>(parameter : type, ...)

### Conditional Jump to Block (Terminal)
Switch to one of two blocks based on a conditional value. The target blocks must take no arguments.

    conditional<when_true : Block(), when_false : Block()>(cond: Bool)

## Operations

Below defines the behaviour for each operation in the VM.

### Any Operations
The `Any` type holds a value of another type for later retrieval.

### Convert to String
Convert a boxed value to a string, if possible.

     r : Str = any_str(value : Any)

This makes use of the appropriate `X_str` function on the value inside the `Any`. If the value is not `Bool`, `Float`, `Int` or `Str`, an error occurs.

### Create Any
Put a value in a box (i.e., convert a value of another type to the `Any` type).

     r : Any = any_of_bin(value : Bin)
     r : Any = any_of_bool(value : Bool)
     r : Any = any_of_float(value : Float)
     r : Any = any_of_frame(value : Frame)
     r : Any = any_of_int(value : Int)
     r : Any = any_of_lookup_handler(value : LookupHandler)
     r : Any = any_of_null()
     r : Any = any_of_str(value : Str)
     r : Any = any_of_template(value : Template)

### Dispatch Any (Terminal)
Branch to another block based on the type of an `Any` value. Blocks for all target types do not need to be provided. Any that are missing, will produce an error: Got value of type _provided_, but expected one of _types for provided blocks_.

     any_dispatch<Block(Bin), Block(Bool), Block(Float), Block(Frame), Block(Int), Block(LookupHandler), Block(Str), Block(Template), Block()>(value : Any)

## Dispatch Double Numeric (Terminal)
Unbox two `Any` types, which must be either `Float` or `Int` and dispatch based on their types. If one is `Float` and the other is `Int`, upgrade the `Int` to a `Float`.

    any_dispatch_numeric<when_int : Block(Int, Int), when_float : Block(Float, Float)>(left : Any, right : Any)

## Dispatch Single Numeric and Int (Terminal)
Unbox one `Any` type, which must be either `Float` or `Int` and dispatch based on the types. If the unboxed type is `Float`, upgrade the provided `Int` to a `Float`.

    any_dispatch_int<when_int : Block(Int, Int), when_float : Block(Float, Float)>(left : Int, right : Any)

## Dispatch Single Numeric and Float (Terminal)
Unbox one `Any` type, which must be either `Float` or `Int` and dispatch based on the types. If the unboxed type is `Int`, upgrade the unboxed `Int` to a `Float`.

    any_dispatch_float<target : Block(Float, Float)>(left : Float, right : Any)

### Bin Operations
The  `Bin` type stores binary data as a list of bytes. Almost all of the manipulation of this data is done through library calls.

#### Length
Returns the number of bytes in a Bin.

    r : Int = binary_length(b : Bin)

### Boolean Operations
A true/false type.

### Comparison
Compare `left` and `right`, returning 1 if `left` is false and `right` is true, -1 if `left` is true and `right` is false, or zero if they are identical.

    r : Int = bool_compare(left : Bool, right : Bool)

#### Constant: False
Set the result to Boolean false.

    r : Bool = bool_false()

#### Constant: True
Set the result to Boolean true.

    r : Bool = bool_true()

#### Complement
Set the result to Boolean complement.

    r : Bool = bool_not(b : Bool)

#### Convert to String
Create a string representation of a Boolean value.

    r : Str = bool_str(b : Bool)

### Context Operations
The context type is an ordered list of frames to be used by lookup to find values.

#### Append
Create a new list with all the elements of `first` followed by all the elements of `second`. If an element occurs multiple times, the first occurrence must be retained, but the subsequent occurrences may be removed from the output.

    r : Context = context_append(first : Context, second : Context)

#### Create Empty
Create an empty list.

    r : Context = context_empty()

#### Prepend
Create a new list containing the provided frame as the first element and the elements in the provided context as the remaining elements. If the `tail` contains the same frame as `head`, it may be removed from the output.

    r : Context = context_prepend(head : Frame, tail : Context)

### Definition Builder Operations
A definition builder is an intermediary used to create frames and templates.

### Create
Create a new empty definition builder.

    r : DefinitionBuilder = definition_builder_create()

### Drop Definition
Remove the definition of an attribute. If this builder is used sequentially after a builder that defines this name, the previous definition will be discarded. `name` must be a valid identifier.

    definition_builder_drop(builder : DefinitionBuilder, name : Str)

### Required Override Definition
Sets the definition of an attribute name of be an error requesting this attribute be overridden. If something is assigned to this name already, it is discarded. `name` must be a valid identifier.

    definition_builder_require(builder : DefinitionBuilder, name : Str)


### Set Definition
Sets the definition of an attribute name of be a definition. If something is assigned to this name already, it is discarded. `name` must be a valid identifier.

    definition_builder_set_definition<definition : Definition>(builder : DefinitionBuilder, name : Str)

### Set Definition Override
Sets the definition of an attribute name of be a definition override. If something is assigned to this name already, it is discarded. `name` must be a valid identifier.

    definition_builder_set_override<override : DefinitionOverride>(builder : DefinitionBuilder, name : Str)

### Floating-Point Number Operations
All floating point operations are in the standard IEEE way.

#### Add
Add `left` and `right`.

    r : Float = float_add(left : Float, right : Float)

#### Compare
Return the sign of the different of `left` and `right`, or zero is they are equal. Behaviour is implementation-defined if either value is not-a-number.

    r : Int = float_compare(left : Float, right : Float)

#### Constant
Returns the floating-point number provided at compile-time.

    r : Float = float_const<value : Float>()

#### Constant: Infinity
Produce a positive infinite value representable as a floating-point number.

    r : Float = float_infinity()

#### Constant: Maximum
Produce the largest value representable as a floating-point number.

    r : Float = float_max()

#### Constant: Minimum
Produce the smallest value representable as a floating-point number.

    r : Float = float_min()

#### Constant: Non-a-Number
Produce a not-a-number value representable as a floating-point value.

    r : Float = float_nan()

#### Convert to Integer
Convert a floating-point number to an integer by truncation.

    r : Int = float_to_int(value : Float)

#### Convert to String
Create a string representation of a floating-point number.

    r : Str = float_str(value : Float)

#### Divide
Divides `left` by `right`.

    r : Float = float_divide(left : Float, right : Float)

#### Finite Check
Check if a floating-point number is finite.

    r : Bool = float_is_finite(value : Float)

#### Not-a-number Check
Check if a floating-point value is a number.

    r : Bool = float_is_nan(value : Float)

#### Multiply
Multiply `left` and `right`.

    r : Float = float_multiply(left : Float, right : Float)

#### Negate
Produce the additive inverse of `value`.

    r : Float = float_negate(value : Float)

#### Subtract Floating-Point Numbers
Subtract `right` from `left`.

    r : Float = float_sub(left : Float, right : Float)

### Frame Operations
For all operations, the names provided must be valid Flabbergast identifiers.

#### Create from Builders
Create frame whose attributes will be those in the supplied builders. The builders are applied in order. That is, _builder2_ can override values from _builder1_. If the builders are mutated after this operation, the resulting frame does _not_ change.

    r : Frame = frame_new(context : Context, container : Frame, builder1 : (DefinitionBuilder | Template | ValueBuilder), builder2 : (DefinitionBuilder | Template | ValueBuilder), ...)

#### Create from Range
Create a frame containing integral numbers, over the range specified, assigned to keys from 1 to the number of items, translated through `string_ordinal`.

    r : Frame = frame_new_through(context : Context, container : Frame, start : Int, end : Int)

#### Context
Extract the context embedded in a frame. This is the context provided during creation prepended with the frame itself.

    r : Context = frame_context(frame : Frame)

#### Identifier
Extract a unique identifier from a frame. This string must be a valid identifier.

    r : Str = frame_id(frame : Frame)

### Fricassée Merge Operations
This operation zips frames together to provide the “For a : x, b : y, c : z, n : Name, o : Ordinal” Fricassée source.

The `FricasseeMerge` is mutable and changes to it will be visible to any chain of `Fricassee` values based on it.

#### Create
Create a new merge operation. The context provided is the context that all the 

    r : FricasseeMerge = fricsasee_merge_create()

#### Add Frame
Adds a frame to be included in the iteration process. The name must be a valid identifier. If the name is currently assigned to another frame, `Name`, or `Ordinal`, this replaces it.

     fricassee_merge_add(merge : FricasseeMerge, name : Str, frame : Frame)

### Add Name
Adds the current attribute name to be included in the iteration process. The name must be a valid identifier. If the name is currently assigned to another frame, `Name`, or `Ordinal`, this replaces it.

     fricassee_merge_add_name(merge : FricasseeMerge, name : Str)

### Add Ordinal
Adds the current iteration ordinal to be included in the iteration process. The name must be a valid identifier. If the name is currently assigned to another frame, `Name`, or `Ordinal`, this replaces it.

     fricassee_merge_add_ordinal(merge : FricasseeMerge, name : Str)

### Fricassée Operations
These operations create a chain of Fricassée operations. The operations are applied in order until a final operation is applied which yields a value. Operations fall into three types:

* sources: require no `Fricassee` values and produce a new `Fricassee` value
* transformations: require an existing `Fricassee` value and produce a new `Fricassee` value
* sinks: require an existing `Fricassee` chain and produce a non-Fricassee value

Once a chain is assembled, iteration will happen in a defined order. Some transformations rearrange the order. Flabbergast has rules about which transformations can be attached to which others based on the order preservation. KWS does not care. “Bad” orders may discard information but this is not incorrect.

#### Accumulate (Transformation)
Accumulate a value. This supplies Flabbergast's “Accumulate” clause. It works like a reduce operation, but yields the intermediate results. The name must be a valid identifier

    r : Fricassee = fricassee_acccumulate<reducer : Definition>(source : (Fricassee | FricasseeMerge), name : Str, initial : Any)

#### Create from Single Frame (Source)
This iterates over a frame of frames and put each inner frame as the head of the context in which the chain is evaluated.

    r : Fricassee = fricassee_foreach(source : Frame)

#### Concatenate Sources (Source)
This iterates over a frame of frames and put each inner frame as the head of the context in which the chain is evaluated.

    r : Fricassee = fricassee_concat(source1 : (Fricassee | FricasseeMerge), source2 : (Fricassee | FricasseeMerge), ...)

### Collect: Frame with Anonymous Attributes
Collect the items into a frame with the attribute names based on the order in which they were received using `string_ordinal`. The attribute value is computed using `compute_value`.

This `context`, `self`, and `container` provided to this sink will apply to all the `Definition` clauses in the entire `Fricassee` chain.

    r : Frame = fricassee_to_list<compute_value : Definition>(source : (Fricassee | FricasseeMerge), context : Context, self : Frame, container : Frame)

### Collect: Frame with Named Attributes
Collect the items into a frame with the attribute names provided. The attribute name generator, `compute_name`, may return either a `Str` which is a valid identifier, or an `Int`, which will be converted to an identifier using `string_ordinal`. If the same identifier is produced more than once, an error will occur. The attribute value is computed using `compute_value`.

This `context`, `self`, and `container` provided to this sink will apply to all the `Definition` clauses in the entire `Fricassee` chain.

    r : Frame = fricassee_to_frame<compute_name : Definition, compute_value : Definition>(source : (Fricassee | FricasseeMerge), context : Context, self : Frame, container : Frame)

#### Filter (Transformation)
Eliminate some elements from further processing. The result of `clause` must be `Bool`, otherwise an error occurs. If it returns true, the element is passed to downstream processors. If false, it is discarded from further processing.

    r : Fricassee = fricassee_where<clause : Definition>(source : (Fricassee | FricasseeMerge))

#### Let Clause (Transformation)
Adds new attributes to the downstream operations in a chain. This builder must not contain `DefinitionOverride` as it does not override the existing data.

     r : Fricassee fricassee_let(source : (Fricassee | FricasseeMerge), builder : DefinitionBuilder)

#### Order By Clause: Boolean (Transformation)
Reorder the results by computing a value, which must be `Bool` and then reordering the input provided by the source based on `bool_compare`. If `ascending` is true, it is given in this order; if `false`, the reverse of this order. If two items produce the same value, their relative order will be preserved.

     r : Fricassee = fricassee_order_bool(source : (Fricassee | FricasseeMerge), ascending : Bool, clause : Definition)

#### Order By Clause: Floating Point (Transformation)
Reorder the results by computing a value, which must be `Float` or `Int` and then reordering the input provided by the source based on `float_compare`. If `ascending` is true, it is given in this order; if `false`, the reverse of this order. If two items produce the same value, their relative order will be preserved.

     r : Fricassee = fricassee_order_float(source : (Fricassee | FricasseeMerge), ascending : Bool, clause : Definition)

#### Order By Clause: Integer (Transformation)
Reorder the results by computing a value, which must be `Bool` and then reordering the input provided by the source based on `int_compare`. If `ascending` is true, it is given in this order; if `false`, the reverse of this order. If two items produce the same value, their relative order will be preserved.

     r : Fricassee = fricassee_order_int(source : (Fricassee | FricasseeMerge), ascending : Bool, clause : Definition)

#### Order By Clause: String (Transformation)
Reorder the results by computing a value, which must be `Bool`, `Float`, `Int`, or `Str` and then reordering the input provided by the source based on `string_compare`. If `ascending` is true, it is given in this order; if `false`, the reverse of this order. If two items produce the same value, their relative order will be preserved.

     r : Fricassee = fricassee_order_str(source : (Fricassee | FricasseeMerge), ascending : Bool, clause : Definition)

#### Reduce (Sink)
Reduce the Fricassée chain to a single value. The name must be a valid identifier.

This `context`, `self`, and `container` provided to this sink will apply to all the `Definition` clauses in the entire `Fricassee` chain.

     r : Any = fricassee_reduce<reducer : Definition>(source : (Fricassee | FricasseeMerge), context : context, self : Frame, container : Frame, name : Str, initial : Any)

#### Reverse (Transformation)
Reverse the order in which items are processed.

    r : Fricassee = fricassee_reverse(source : (Fricassee | FricasseeMerge))

### Integral Number Operations

#### Add
Add `left` and `right`.

    r : Int = int_add(left : Int, right : Int)

#### Bit-wise AND
Produce the bit-wise AND of `left` and `right`.

    r : Int = int_and(left : Int, right : Int)

#### Bit-wise Complement
Produce the bit-wise NOT of `value`.

    r : Int = int_complement(value : Int)

#### Bit-wise Exclusive-OR
Produce the bit-wise XOR of `left` and `right`.

    r : Int = int_xor(left : Int, right : Int)

#### Bit-wise OR
Produce the bit-wise OR of `left` and `right`.

    r : Int = int_or(left : Int, right : Int)

#### Compare
Compare `left` and `right`, returning the sign of the difference, or zero if they are identical.

    r : Int = int_compare(left : Int, right : Int)

#### Constant
Returns the integral constant provided at compile-time.

    r : Int = int_const<value : Int>()

#### Constant: Maximum
Produce the largest value representable as a integral number.

    r : Int = int_max()

#### Constant: Minimum
Produce the smallest value representable as a integral number.

    r : Int = int_min()

#### Convert to Boolean
Convert integer `value` to Boolean by comparing it to `reference`. If `value` and `reference` are equal, true is returned, false otherwise.

    r : Bool = int_to_bool<reference : Int>(value : Int)

#### Convert to Floating-point
Create a floating-point representation of an integral number.

    r : Float = int_to_float(value : Int)

#### Convert to String
Create a string representation of an integral number.

    r : Str = int_str(value : Int)

#### Divide
Divides `left` by `right`, and set the result to the dividend.

    r : Int = int_divide(left : Int, right : Int)

#### Multiply
Multiple `left` and `right.

    r : Int = int_muliply(left : Int, right : Int)

#### Negate
Produce the additive inverse of `value`.

    r : Int = int_negate(value : Int)

#### Remainder
Divide `left` by `right` and set the result to the remainder.

    r : Int = int_modulus(left : Int, right : Int)

#### Shift Bits
Shift `value` to the left by `offset`. If `offset` is negative, shift to the right.

    r : Int = int_shift(value : Int, offset : Int)

#### Subtract
Subtract `right` from `left`.

    r : Int = int_subtract(left : Int, right : Int)

### Lookup Handler and Name Source Operations
A lookup handler is a pluggable variable resolution system for Flabbergast.

#### Add Names from Frame
Add names to a name source from a frame's values. The values in the frame are taken to be Flabbergast identifiers: either strings which are identifiers, or integers which are converted to valid identifiers as ordinals.

    name_source_add_frame(source : NameSource, frame : Frame)

The values in the frame are taken in order. If a value is not a valid identifier, an error occurs.

#### Add Literal Names
Add predefined names to a name source. The names must be valid Flabbergast identifiers.

    name_source_add_literal<name1 : Str, name2 : Str, ...>(prefix : NameSource)

#### Add Ordinal Name
Add an ordinal name to a name source.

    name_source_add_ordinal(prefix : NameSource, ordinal : Int)

This converts the number to an identifier in the same was as `string_ordinal`.

#### Create Name Source
Create a name source with no names in it.

    r : NameSource = name_source_create()

#### Perform Lookup
Perform lookup over a list of frames.

    r : Any = lookup_handler_lookup(handler : LookupHandler, context : Context, names : NameSource)

#### Perform Lookup: Contextual
Performs contextual lookup from a set of fixed names.

    r : Any = lookup<name1 : Str, name2 : Str, ...>(context : Context)

This would be the same as:

    s = name_source_create()
    name_source_add_literal<name1, name2, ...>(s)
    c = lookup_handler_contextual()
    r = lookup_handler_lookup(c, context, s)

It is provided for convenience and efficiency.

#### Scheme: Contextual Lookup
Gets the standard contextual lookup scheme.

    r : LookupHandler = lookup_handler_contextual()

If no attribute matches the names provided during lookup, an error occurs.

### String Operations

#### Concatenate
Create a new string of `first` followed by `second`.

    r : Str = string_concatenate(first : Str, second : Str)

#### Constant
Create a string constant.

    r : Str = string_constant<s : Str>()

#### Collate
Determine if `left` collates before, the same, or after `right` and set the result to be -1, 0, or 1, respectively.

    r : Int = string_compare(left : Str, right : Str)

#### Create from Ordinal
Create a string from an integer such that ordering of integers is preserved when using `string_compare` and the resulting string is a valid Flabbergast identifier.

    r : Str = string_ordinal(value : Int)

#### Length
Returns the number of Unicode characters in a string.

    r : Int = string_length(s : Str)

### Template Operations
For all operations, the names provided must be valid Flabbergast identifiers.

#### Create Template
Create a new definition builder from zero or more existing builders. Modification of the builders after this instruction will not affect the resulting builder. The builders are gathered in the order supplied. If one of the builders sets a `Definition`, a subsequent builder will be able to apply a `DefinitionOverride` to it. If a `DefinitionOverride` is encountered and no previous `Definition` or `Any` is available, then an error will be produced: Cannot override not existent attribute “_name_”.

    r : DefinitionBuilder = template_create(context : Context, container : Frame, builder : (DefinitionBuilder | Template | ValueBuilder), ...)

#### Template Container
Extract the container embedded in a template. This is the context provided during creation.

    r : Frame = tmpl_container(tmpl : Template)

#### Template Context
Extract the context embedded in a template. This is the context provided during creation.

    r : Context = tmpl_context(tmpl : Template)

### Value Builder
A value builder is a set of computed values for inclusion in a template. This corresponds to the Flabbergast “Now” attribute definitions.

#### Create
Create a new empty value builder.

    r : ValueBuilder = value_builder_create()

#### Check
Check if an attribute name is already present in the value builder.

    r : Bool = value_builder_has(builder : ValueBuilder, name : Str)

#### Set by Name
Sets a value in the value builder to an `Any` value. If something was assigned to this name already, it is discarded. `name` must be a valid identifier.

    value_builder_set_name(builder : ValueBuilder, name : Str, value : Any)

#### Set by Ordinal
Sets a value in the value builder to an `Any` value. If something was assigned to this name already, it is discarded.

    value_builder_set_ordinal(builder : ValueBuilder, ordinal : Int, value : Any)

### Miscellaneous Operations

#### Access Library
Load external data. Since the URI is fixed, this can be thought of as information to the dynamic loader rather than part of the execution.

    r : Any = external<uri : Str>()

#### Verify Identifier Name
Checks that a string is a valid Flabbergast identifier.

    r : Bool = verify_symbol(s : Str)
