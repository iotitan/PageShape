PageShape: Templating System
============================
Revision #27 (#38 with extended goals)
Copyright (C) 2013 Matthew D Jones
(8/18/2013 - 9/8/2013)


Experiment with compile-once-use-anywhere templates with client and server-side renderers. Uses handlebars style tags but allows simple if/elseif/else logic with expression parsing.

Sections:
1. Description
1.1. Concept
1.2. Duration
1.3. Initial Thoughts
2. Goals
3. Results
4. Usage
4.1. Paths
4.2. Tags
5. Instruction Definitions & Technical Specifications
6. Compiler Usage
7. Further Development



SECTION 1: Description:

NOTE: I frequently use the term "context" as synonymous with "root scope".

SECTION 1.1: Concept

This was an experimental project to create compiled templates that were both 
smaller and faster than existing technologies. I chose to base my 
implementation and syntax off of the handlebars template library 1.0.0 
(http://handlebarsjs.com) due to its popularity and simplicity of use. The 
final result has some syntactical differences but supports most of the 
functionality; particularly what I found most useful.

Unlike other template systems, compiling templates on-the-fly is not an option.
In order to use the system, the template must be compiled in advance. The 
compiler is written in Java so it is inherently cross-platform, enabling 
distribution of a single binary. However, the compiler is written in such a 
way that it can easily be converted to C++ to be native or to JavaScript if 
someone really wanted the performance hit.

SECTION 1.2: Duration

This project ended up taking about 2 weeks of working in my spare time to 
achieve the basic goals and 1 extra week to do extended goals. This includes 
design, implementation, and some testing.

SECTION 1.3: Initial Thoughts:

The supporting idea and motivation was to leverage array traversal speed 
(albeit arrays in JavaScript aren't usually true arrays) and do a true compile 
of a template by breaking it into a list of simple executable commands. My 
system uses an idea similar to assembly instructions; a command is read and it 
can move to a new location in the code or modify the current variable stack.

Templates are compiled in such a way that they can be used by implementations 
in any language (compile once, use anywhere) that supports JSON.



SECTION 2: Goals:

-	Smaller compiled templates than handlebars
-	Similar or equal functionality when rendered
-	Renderer code less than 100 lines uncompressed
-	Easy implementation for server-side rendering (Java, PHP, etc.)
-	Form better understanding of parsing and compiling techniques
Extended: (rev. 28 - 38)
-	Implement simple, single-expression, if/elseif/else logic



SECTION 3: Results:

Tests with functionally equivalent templates resulted in about a 50% reduction 
in template size compared to handlebars with smaller templates. The renderer 
ended up weighing in at just over 100 lines excluding comments (almost 200 
including comments for revision 38), though I may add more functionality in the 
future. The renderer is very simple to implement and I am convinced that it can 
be done in any language that has a usable JSON parser. Very limited timing 
tests showed a decrease in render time.

In my limited knowledge of compiler implementation, I decided to implement one 
that was single-pass. This is fine for simple languages, but when I decided to 
implement if/else chains, the project became much more complicated. Using a two 
pass parser would have simplified this because I could have tokenized files into 
lists and then looked for patterns in the list. Instead, I am handling different 
tags as I go.



SECTION 4: Usage:

SECTION 4.1: Paths:

A path is a variable that can move around contexts. For example "../" refers to 
the parent context and "element.attribute" refers to the attribute "attribute" 
of variable "element". Paths can be as compound as necessary 
(ex. "../../elem.attrib.etc"). When compiled, each variable is parsed as a path.

SECTION 4.2: Tags:

Print a variable:
{{VAR_NAME}} 

Each statement:
Iterate over a list:
{{#each VAR_NAME}}
	HTML content and or more tags
	{{this}} is the element at {{@index}}
	{{@length}} is the length of the current array context
	NOTE: @index displays 1 to n, NOT 0 to n-1
{{#else}}
	This section executes if VAR_NAME is not an array, has length of 0, 
	or is undefined
{{/each}}

If statement:
	Simple conditional block:
{{#if VAR_NAME}}
	HTML content and or more tags
{{#else}}
	This section executes if VAR_NAME is an empty string, false, null, 
	or undefined
{{/if}}

If/ElseIf statement::
{{#if VAR_NAME == VALUE}}
	This runs if VAR_NAME is equal to VALUE
{{#elseif OTHER_VAR == VALUE2}}
	This runs if OTHER_VAR is equal to VALUE2
{{#else}}
	This block executes if the former two do not
{{/if}}

With statement:
	With statements are used for moving into a new context:
{{#with VAR_NAME}}
	Move into a new context
	HTML content and or more tags
{{/with}}

Functions:
	Functions are called by name with a "#" prefix:
{{#FUNCTION_NAME VAR_NAME}}

Adding functions to PageShape for use in the templates can be done with the 
following function:

PageShape.addFunction(name:string, func:function);
name - string: The name of the function
func - function: The function definition. The function must have only one 
	parameter; the current context.



SECTION 5: Instruction Definitions & Technical Specifications:

P: Print plaintext
	Arguments: 	<TEXT_TO_PRINT>
	Example: 	[["P"],["Hello world!"]]

V: Print variable
	Arguments: 	<PATH_ID>
	Example: 	[["V"],["1"]]
	Description: 	The path ID is an index in the path table used for pre-
	parsed paths.

L: Each block (or loop)
	Arguments: 	<PATH_ID> <ELSE_LOCATION> <END_BLOCK_LOCATION>
	Example: 	[["L"],["1"],["5"],["10"]]
	Description: 	The path ID is an index in the path table used for pre-
	parsed paths. The else location is the location that the else block starts 
	(this is equal to end block location if there is no else). The end block 
	location is the end of the code block.

I: If block
	Arguments: 	<VARIATION> <PATH_ID1> <PATH_ID2> <GOTO_LINE>
	Example: 	[["I"],["1"],["5"],["10"]]
	Description: 	Variation can be a number from 0 to 6 representing the following: 
		0: check for existence/false/empty
		1: <
		2: >
		3: <=
		4: >=
		5: ==
		6: !=
		Path IDs 1 and 2 are the arguments to compare. Arguments can be other 
		variables, numbers, strings, or Booleans. In case 0, the second path 
		is not necessary and a "-" is put in its place. The goto line is where 
		the code will start execution from if the expression is false.

J: Jump to line
	Arguments: 	<LINE_NO>
	Example: 	[["J"],["10"]]
	Description: 	Start execution from the specified line.

W: With block
	Arguments: 	<PATH_ID> <END_BLOCK_LOCATION>
	Example: 	[["W"],["1"],["5"]]
	Description: 	The path ID is an index in the path table. The end block 
	location is where the system will stop using the specified path.

F: Function call on specified context
Arguments: 	<FUNCTION_NAME> <PATH_ID>
	Example: 	[["F"],["doStuff"],["2"]]
	Description: 	The path ID is an index in the path table. The end block 
	location is where the system will stop using the specified path.



SECTION 6: Compiler usage

Presently the compiler is in Java. To run the compiler use the following command:
java TemplateCompiler IN_FILE [OUT_FILE]



SECTION 7: Further Development

Using this scheme, it would not be difficult to implement ELSEIF, IFEQ (if 
equal to), or PARTIAL (for rendering partial templates). Support for both JSON 
and XML would be nice since both are easily handled by almost all languages.

On completion of revision 38:

Only XML interpretation needs to be implemented. Partials can be done with user 
specified functions. IF statements now support simple logic.

