# Flabbergast Syntax Quick Reference

## Types
`Bin` – a blob of arbitrary data.

`Bool` – a Boolean value.

`Float` – IEEE floating point number.

`Frame` – a collection of attributes with values.

`Int` – integral number.

`LookupHandler` – algorithm for resolving names.

`Null` – nothing much.

`Str` – a Unicode text string

`Template` – a collection of attributes with expressions.

## Expressions
This are ordered from low precedence to high precedence

Let: `Let x : 3, y : 4 In x + y`

Fricassée: `For x : args Where x > 5 Select x` See [selectors and results](#fricassée-selectors).

Conditional: `If c Then t Else f`

String concatenation: `x & y` Works for `Bool`, `Float`, `Int`, and `Str`

Frame concatenation: `x Append y`

Logical OR: `x || y`

Logical AND: `x && y`

Order/spaceship: `x <=> y` Works for `Bool`, `Float`, `Int`, and `Str`

Comparison: `x == y` `x < y` `x <= y` `x > y` `x >= y` `x != y` Works for `Bool`, `Float`, `Int`, and `Str`

Bitwise OR: `x B| y`

Bitwise exclusive-OR: `x B^ y`

Bitwise AND: `x B& y`

Addition and Subtraction: `x + y` `x - y`

Multiplication, Division and Modulus: `x * y` `x / y` `x % y`

Range of integers: `x Through y`

Type assertion: `x Enforce t` (_e.g._, `x Enforce Float`)

Type coercion: `x To t` (_e.g._, `3 To Float`)

Type check: `x Is t` (_e.g._, `3 Is Bool`, `a Is Null`)

Finite: `x Is Finite`

Not-a-number: `x Is NaN`

Raise an error: `Error "Make it stop."`

String length: `Length "hello"`

Remote contextual lookup: `Lookup x.y In someframe`

Remote custom lookup: `Lookup x.y Using handler In someframe`

Custom lookup: `Lookup x.y Using handler Here`

Type-directed lookup: `Lookup foo.TypeOf(x) Here` (_e.g._, `Lookup foo.TypeOf("x") Here` will `foo.str`)

Instantiate a template: `tmpl { overrides... }` (_e.g._, `some_template { x : 3 }`)

Null coalescence: `x ?? y`

Nullable lookup: `x?.y` which is sugar for `If x Is Null Then Null Else (x).y`

Literal frame: `{ attr : 3 }` See [attribute definitions](#attribute-definitions).

New template: `Template { attr : x }` See [attribute definitions](#attribute-definitions).

Amend a template: `Template original { attr : y }` See [attribute definitions](#attribute-definitions).

New function-like template: `Function (x, y) x + y`

Bit-wise not:  `B! x`

Logical not: `! x`

Negation: `-x`

Unique identifier generation: `GenerateId frame`

Call template with arguments: `fun(a, b, x : 3)`

Access external data: `From lib:utils`

Contextual lookup: `x.y.z`

Direct lookup: `(x).y.z`

Literal list: `[ 1, 2, 3 ]`

Identifier-like string: `$x`

Specials frames: Current is `This` and nested parent is `Container`

Boolean literals: `True` and `False`

Float literals: `FloatMax` `FloatMin` `Infinity` `NaN`

Integer literals: `IntMax` `IntMin`

Unique identifier: `Id`

Null literal: `Null`

## Attribute Definitions

Plain: `name : expr`

Needs override: `name : Required`

Expected value: `name : Used` (This does nothing; it's just information for documentation.)

Amend sub-template: `name +: { overrides ... }`

Override with original: `name +original: original + 1`

Remove: `name : Drop`

Eager evaluation: `name : Now expr` This causes the expression to be evaluated immediately, instead of upon instantiation.

## Fricassée Selectors

Pass-through selector: `For Each x`

Merged attribute selector: `For x : expr1, y : expr2, n : Name, o : Ordinal`

Accumulating value: `... Accumulate expr With name : initial` (_e.g._, running product `For x : args Accumulate product * x With product : 1 Select product`)

Flattening: `... Flatten x : expr1, y : expr2, n : Name, o : Ordinal`

Grouping (anonymous): `... Group x : exprX, y : exprY By a : exprA, b : exprB` (_e.g._, `For x : args Group target : x.target By x.port Enforce Int`)

Grouping (named): `... Group x : exprX, y : exprY Named exprN By a : exprA, b : exprB` (_e.g._, `For x : args, n : Name Group target : x.target Named n By x.port Enforce Int`)

Name binding: `... Let a : expr, b : expr` (_e.g._, `For x : args Let x_squared : x * x Reduce x_squared * acc + x_squared With acc : 0`

Ordering: `... Order By expr` (_e.g._, `For x : args Order By x To Str Select x`)

Reverse: `... Reverse` (_e.g._, `For x : args Reverse Select x`)

Randomisation: `... Shuffle`

Discard input before: `... Skip expr` (_e.g._, `For x : args, p : Ordinal Skip p < 3 Select x)

Discard input after: `... Until expr` (_e.g._, `For x : args, p : Ordinal Until p > 3 Select x)

Filter: `... Where condition` (_e.g._, `For x : args Where x > 5 Select x`)

## Fricassée Results

Reducer (fold): `Reduce expr With name : initial` (_e.g._, `For x : args Reduce x + acc With acc : 0`)

To list (map): `Select expr` (_e.g._, `For x : arg Select x * 2`)

To frame (map): `Select nameexpr : expr` (_e.g._, `For x : arg, name : Name Select name : x * 2`)

Single result: `Single x` (_e.g._, `For x : arg Where x.id == 3 Single x`)

Single result: `Single x Or default` (_e.g._, `For x : arg Where x.id == 3 Single x Or Null`)

## String Parts

Audible bell: `\a`

Backspace: `\b`

Form feed: `\f`

New line: `\n`

Carriage return: `\r`

Horizontal tab: `\t`

Vertical tab: `\v`

Quotation mark: `\"`

Octal escape: `\777`

ASCII hex escape: `\xFF`

Unicode hex escape: `\uFFFF`

Astral Unicode hex escape: `\U0001F525`

Embedded expression: `\(expr)`


### Documentation

Before an attribute, `{{{Your documentation here.}}}` which may contain:

Emphasis: `\Emph{text}`

Monospace: `\Mono{text}`

Hyperlink: `\Link{http://www.flabbergast.org|Flabbergast}`

Lookups: `\{x.y}`

Library reference: `\From{lib:utils}`
