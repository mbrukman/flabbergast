" Vim syntax file
" Language:	Flabbergast
" Maintainer:	Andre Masella <andre@masella.name>
" Filenames:	*.o_0, *.?o_0

" Quit when a syntax file was already loaded
if exists("b:current_syntax")
    finish
endif

let s:flabbergast_cpo_save = &cpo
set cpo&vim

syn case match

syn region flabbergastString matchgroup=flabbergastDelimiter start='"' end='"' skip='\\"' contains=flabbergastEscape,flabbergastEscapeError,flabbergastInterpolation,@Spell
syn match flabbergastEscapeError contained "\\." display
syn match flabbergastEscapeError "\\)" display
syn match flabbergastEscape contained containedin=flabbergastString '\\[abfnvrt"\\]' display
syn match flabbergastEscape contained containedin=flabbergastString "\\\(\o\{3}\|x\x\{2}\|u\x\{4}\|U\x\{8}\)" display
syn region flabbergastInterpolation contained contains=TOP matchgroup=flabbergastDelimiter start="\\(" end=")"

syn match flabbergastTrailingWhite "[ \t]\+$"
syn match flabbergastTrailingWhite "[ \t]\+$" containedin=flabbergastDefinition
syn match flabbergastKeyword "+\|:" containedin=ALL
syn keyword flabbergastKeyword Drop Now Required Used
syn match flabbergastIdentifier "\<args\>" display
syn match flabbergastIdentifier "\<value\>" display
syn match flabbergastIdentifier "\<bin\>" display
syn match flabbergastIdentifier "\<bool\>" display
syn match flabbergastIdentifier "\<float\>" display
syn match flabbergastIdentifier "\<frame\>" display
syn match flabbergastIdentifier "\<lookup_handler\>" display
syn match flabbergastIdentifier "\<int\>" display
syn match flabbergastIdentifier "\<template\>" display
syn match flabbergastIdentifier "\<str\>" display
syn match flabbergastDefinition "\<[a-z][A-Za-z0-9_]*\>[ \t\n]*\(+\([a-z][A-Za-z0-9_]*\)\)\?:" display
syn region flabbergastDefinition display contains=TOP matchgroup=flabbergastAttribute start="\(Attribute\|TypeOf\)(" end=")[ \t\n]*\(+\([a-z][A-Za-z0-9_]*\)\?\)\?:"
syn match flabbergastComment "#.*$" contains=flabbergastTodo,@Spell display
syn keyword flabbergastInt IntMax IntMin
syn keyword flabbergastFloat FloatMax FloatMin Infinity NaN
syn keyword flabbergastBool False True
syn keyword flabbergastConstant Null Contextual
syn match flabbergastBadKeyword "\<[A-WYZ][^ \t#]*" display
syn match flabbergastIdentifierString "\$[a-z][a-zA-Z0-9_]*\>" display
syn match flabbergastFrom "[A-Za-z0-9.+-]\+:[A-Za-z0-9~!*'_;@&=+$,/?%#.+-:]\+" display
syn keyword flabbergastFrom From
syn keyword flabbergastFricassee Accumulate By Each Flatten For Group Name Named Order Ordinal Reduce Reverse Select Shuffle Single Skip Or Until With Where
syn keyword flabbergastConditional If Then Else
syn keyword flabbergastKeyword Append Attribute Container Enforce Error Finite Function GenerateId Here Id In Is Length Let Lookup Template This Through To TypeOf Using Within
syn match flabbergastOperators '\(<=\=\|<=>\|>=\=\|==\|||\|-\|!=\=\|/\|\*\|&\|&&\|%\|+\|??\|B|\|B^\|B&\|B!\)' display
syn keyword flabbergastTodo contained containedin=flabbergastComment TODO FIXME XXX
syn keyword flabbergastType Bin Bool Float Frame Int LookupHandler Str
syn match flabbergastImplDefined "\<X[a-zA-Z0-9]*\>" display
syn match flabbergastConstant "\<\d\+[kMG]i\=\>" display
syn match flabbergastConstant "\<\(\d\+[dhms]\)\+\>" display

syn region flabbergastDescription matchgroup=flabbergastDescDelimiter start="\(Introduction\)\?{{{" end="}}}" contains=flabbergastDescDelimiter,flabbergastDescEscape,@Spell
syn match flabbergastDescDelimiter contained "\\}" display
syn region flabbergastDescEscape contained containedin=flabbergastDescription matchgroup=flabbergastDescDelimiter start="\\Emph{" end="}" skip="\\}" contains=@Spell
syn region flabbergastDescEscape contained containedin=flabbergastDescription matchgroup=flabbergastDescDelimiter start="\\\(From\|Link\|Mono\)\?{" end="}" skip="\\}"
syn keyword flabbergastDescDelimiter contained containedin=flabbergastDescription Info Introduction Emph From Link Mono

hi def link flabbergastAttribute	Statement
hi def link flabbergastBadKeyword	Error
hi def link flabbergastBool		Boolean
hi def link flabbergastComment		Comment
hi def link flabbergastConditional	Conditional
hi def link flabbergastConstant		Constant
hi def link flabbergastDefinition	Identifier
hi def link flabbergastDelimiter	Delimiter
hi def link flabbergastDescDelimiter	Delimiter
hi def link flabbergastDescription	Comment
hi def link flabbergastEscape		SpecialChar
hi def link flabbergastEscapeError	Error
hi def link flabbergastFloat		Float
hi def link flabbergastFricassee	Repeat
hi def link flabbergastFrom		Include
hi def link flabbergastIdentifier	Identifier
hi def link flabbergastIdentifierString	String
hi def link flabbergastImplDefined	Debug
hi def link flabbergastInt		Number
hi def link flabbergastKeyword		Statement
hi def link flabbergastOperators	Operator
hi def link flabbergastString		String
hi def link flabbergastTodo		Todo
hi def link flabbergastTrailingWhite	DiffDelete
hi def link flabbergastType		Type

let b:current_syntax = "flabbergast"

let &cpo = s:flabbergast_cpo_save
unlet s:flabbergast_cpo_save

" vim: ts=8
