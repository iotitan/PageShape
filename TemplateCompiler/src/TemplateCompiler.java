/**
 * Copyright (C) 2013 Matthew D Jones
 * 
 * File: TemplateCompiler.java
 * Author: Matt Jones
 * Date: 8.15.13
 * Desc: Template compiler for web.
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Stack;

public class TemplateCompiler
{
	public enum KeyWord {EACH, IF, _IF, ELSEIF, ELSE, WITH};
	// for use in parsing
	private static Stack<KeyWord> kwStack;
	private static Stack<String[]> ifElseChain;
	// keeping track of input line
	private static int curInLine;
	// output variables
	private static ArrayList<String> commands;
	private static ArrayList<String> paths;
	
	// Major version, minor version, revision number (from version control)
	private static final String COMPILER_VERSION = "1.0.42";

	public static void main(String[] args)
	{
		if(args.length < 1)
		{
			System.out.println("usage: TemplateCompiler <HTML_TEMPLATE> [<OUTPUT_NAME>]");
			return;
		}
		
		String inFileName = args[0];
		// UNIX-like
		if(inFileName.indexOf("/") != -1)
			inFileName = inFileName.substring(inFileName.lastIndexOf("/")+1);
		// windows
		else if(inFileName.indexOf("\\") != -1)
			inFileName = inFileName.substring(inFileName.lastIndexOf("\\")+1);
		
		String outName;
		if(args.length > 1)
			outName = args[1];
		else
			outName = inFileName.substring(0,args[0].lastIndexOf('.')) + ".js";
			
		Scanner in = null;
		StringBuilder comp = new StringBuilder();
		
		try
		{
			in = new Scanner(new File(args[0]));
		}
		catch(IOException e)
		{
			System.out.println("TemplateCompiler: Could not open input file.");
		}
		
		String tInput = "";
		while(in.hasNextLine())
			tInput += in.nextLine()+"\n";
		
		curInLine = 0;
		kwStack = new Stack<KeyWord>(); // how we're detecting syntax errors (non-matching tags)
		ifElseChain = new Stack<String[]>(); // used for if-elseif chains
		commands = new ArrayList<String>(); // used to perform actual actions in the VM/renderer
		paths = new ArrayList<String>(); // pre-parsed paths used as arguments in above (referenced by index #)
		
		parse(tInput,0);
		
		comp.append("PageShape.addTemplate(\"" + inFileName + "\",{");
		//commands
		comp.append("\"version\":\""+COMPILER_VERSION+"\",\"template\":[");
		for(int i = 0; i < commands.size()-1; i++)
			comp.append(commands.get(i)+",");
		comp.append(commands.get(commands.size()-1));
		comp.append("],");
		// paths
		comp.append("\"paths\":[");
		if(paths.size() > 0)
		{
			for(int i = 0; i < paths.size()-1; i++)
				comp.append(paths.get(i)+",");
			comp.append(paths.get(paths.size()-1));
		}
		comp.append("]");
		
		comp.append("});");
		
		if(!kwStack.isEmpty())
			printError("parse error: expected {{/"+kwStack.pop().name()+"}}",curInLine);
		else
			System.out.println("TemplateCompiler: Template compiled successfully! Sending output to \""+outName+"\"");
		
		PrintWriter out;
		try
		{
			out = new PrintWriter(outName);
			out.print(comp.toString());
			out.close();
		}
		catch(IOException e)
		{
			System.out.println("TemplateCompiler: Could not write output.");
		}
	}
	
	/**
	 * Parse the template and generate output
	 * @param in Input string
	 * @param varDepth The current sub-object/variable level (i.e. object.element.subelement...)
	 * @param start Where to start searching in the input
	 * @return The location of the last exit tag
	 */
	public static int parse(String in, int start)
	{
		int bLoc = start;
		int lastPos = start;
		int bLocEnd = 0;
		String print;
		// find brace locations
		while((bLoc = in.indexOf("{{",lastPos)) != -1)
		{
			bLocEnd = in.indexOf("}}",bLoc+2);
			// determine what is in the braces
			String term = in.substring(bLoc+2,bLocEnd).trim();
			String[] split = term.split(" ");
			
			print = removeWhitespace(escapeQuotes(in.substring(lastPos,bLoc)));
			
			// print what came before the tag, don't add command if there was no space between tags
			if(print.trim().length() > 0)
			{
				commands.add(JSONObj(new String[]{"P",print}));
			}
			
			// parse open block tags
			if(split[0].trim().charAt(0) == '#')
			{
				if(checkTag(split[0],"#EACH"))
				{
					lastPos = parse_EACH(in,bLocEnd+2,split);
				}
				else if(checkTag(split[0],"#IF"))
				{
					lastPos = parse_IF(in,bLocEnd+2,split);
				}
				else if(checkTag(split[0],"#WITH"))
				{
					lastPos = parse_WITH(in,bLocEnd+2,split);
				}
				// blocks with interesting behavior
				else if(checkTag(split[0],"#ELSE"))
				{
					// return (to the calling function; each or if) because the else is actually handled there
					return parse_ELSE(in,bLocEnd+2,split);
				}
				else if(checkTag(split[0],"#ELSEIF"))
				{
					// similar concept to ELSE; is handled in parse_IF
					return parse_ELSEIF(in,bLocEnd+2,split);
				}
				// attempt to call user defined function
				else
				{
					// no position modification here, just a single tag
					parse_FUNCTION(split);
					lastPos = bLocEnd+2;
				}
			}
			// now for end-blocks
			else if(split[0].trim().charAt(0) == '/')
			{
				if(checkTag(split[0],"/EACH"))
				{
					if(kwStack.pop() != KeyWord.EACH)
						printError("parse error: expected {{/EACH}}",curInLine);
					return bLocEnd+2;
				}
				else if(checkTag(split[0],"/IF"))
				{
					if(kwStack.pop() != KeyWord.IF)
						printError("parse error: expected {{/IF}}",curInLine);
					return bLocEnd+2;
				}
				else if(checkTag(split[0],"/WITH"))
				{
					if(kwStack.pop() != KeyWord.WITH)
						printError("parse error: expected {{/WITH}}",curInLine);
					return bLocEnd+2;
				}
			}
			// simple variable print
			else
			{
				int pTable = parse_PATH(term);
				commands.add(JSONObj(new String[]{"V",pTable+""}));
				lastPos = bLocEnd+2;
			}
		}
		
		// if no more brackets, take the rest of the input and print it
		// THIS SHOULD NOT HAPPEN UNLESS EOF
		print = removeWhitespace(escapeQuotes(in.substring(lastPos)));
		if(print.trim().length() > 0)
		{
			commands.add(JSONObj(new String[]{"P",print}));
		}
		
		return lastPos+2;
	}
	
	
	/**
	 * Compile a variable name into easier to parse data (assuming "../" is used)
	 * [upContextNum,[dotPathList]]
	 * @param in Compiler input
	 * @param start Position to start parsing at
	 * @param args Arguments retrieved from the tag
	 */
	private static int parse_PATH(String var)
	{
		ArrayList<String> components = new ArrayList<String>();
		int myIndex = paths.size();
		
		int count = 0;
		int i;
		char type = detectLiteralType(var);
		
		// type was a variable
		if(type == 'V')
		{
			components.add(type+"");
			
			String[] spl = var.split("/");
			String[] dotSpl = spl[spl.length-1].split("\\.");
			
		
			// find how many contexts we need to go up
			for(i = 0; i < spl.length; i++)
			{
				if(spl[i].equals(".."))
					count++;
			}
			
			// add the total "up" to the final JSON data
			components.add(count+"");
			
			// split the rest of the name on the dots
			for(i = 0; i < dotSpl.length; i++)
			{
				// use illegal var symbols to represent common var names (cut computation/render time)
				if(dotSpl[i].toLowerCase().trim().equals("this"))
				{
					components.add("$");
				}
				else if(dotSpl[i].toLowerCase().trim().equals("@index"))
				{
					components.add("^");
				}
				else if(dotSpl[i].toLowerCase().trim().equals("@length"))
				{
					components.add("*");
				}
				else
				{
					// else make sure name is valid and add to list
					if(legalName(dotSpl[i].trim()) && dotSpl[i].trim().length() > 0)
						components.add(dotSpl[i].trim());
					else
						printError("illegal path name or syntax",curInLine);
				}
			}
		}
		// otherwise nothing special needs to be done
		else
		{
			components.add(type+"");
			components.add(var.trim());
		}
		
		// build path entry and make sure we're not duplicated before adding
		String obj = JSONObj(components.toArray(new String[components.size()]));
		int existing = paths.indexOf(obj);
		
		if(existing < 0)
		{
			paths.add(obj);
			return myIndex;
		}
		else
		{
			return existing;
		}
	}
	
	/**
	 * Determine the type of variable:
	 * S: String
	 * B: Boolean
	 * N: Number
	 * V: Path to external value
	 * @param value Value to interpret
	 * @return char identifying the type
	 */
	private static char detectLiteralType(String value)
	{
		String s = value.trim();
		if(s.charAt(0) == '\'' && s.charAt(s.length()-1) == '\'')
		{
			return 'S';
		}
		if(s.charAt(0) == '"' && s.charAt(s.length()-1) == '"')
		{
			return 'S';
		}
		if(s.toLowerCase().equals("true") || s.toLowerCase().equals("false"))
		{
			return 'B';
		}
		
		// simple way to detect number
		try
		{
			Float.parseFloat(s);
		}
		catch(NumberFormatException e)
		{
			return 'V';
		}
		
		return 'N';
	}
	
	/**
	 * Check if the name contains only legal characters
	 * @param in Var name
	 * @return true if legal
	 */
	private static boolean legalName(String in)
	{
		String sym = "!@#%^&*()-+=[]{}\"':;,<>? \t";
		for(int i = 0; i < sym.length(); i++)
			if(in.indexOf(sym.charAt(i)) > -1)
				return false;
		return true;
	}
	
	/**
	 * Parse an EACH block (loop with potential else)
	 * @param in Compiler input
	 * @param start Position to start parsing at
	 * @param args Arguments retrieved from the tag
	 * @return Position after the loop ends
	 */
	private static int parse_EACH(String in, int start, String[] args)
	{
		if(args.length > 1)
		{
			int myLine = commands.size(); // record start of loop
			commands.add(""); 	// add a position in the list initially, we do not know the end lines or internal
								// loop commands until it is read through, this saves the correct position
			int endLine;
			int returnPos;
			
			kwStack.push(KeyWord.EACH); // push EACH to stack so we know what block we're in
			returnPos = parse(in,start);
			endLine = commands.size(); // get the line that the block ended or the else was found
			
			// was there an else? If so, pop the ELSE and update returnPos
			if(!kwStack.isEmpty() && kwStack.peek() == KeyWord.ELSE)
			{
				kwStack.pop();
				returnPos = parse(in,returnPos);
			}

			// update the instruction we added earlier, endLine = comLine if no ELSE was found
			commands.set(myLine, JSONObj(new String[]{"L",parse_PATH(args[1])+"",endLine+"",commands.size()+""}));
			
			return returnPos;
		}

		printError("parse error: {{#EACH}} requires arguments", curInLine);
		return -1;
	}
	
	/**
	 * Parse an IF block (with potential else). More or less implemented same as EACH.
	 * 
	 * NOTE: Support IFEQ IFNE IFLT IFGT IFLE IFGE; 7 total variations including IF; see readExpression
	 * 
	 * @param in Compiler input
	 * @param start Position to start parsing at
	 * @param args Arguments retrieved from the tag
	 * @return Position after the loop ends
	 */
	private static int parse_IF(String in, int start, String[] args)
	{
		if(args.length > 1)
		{
			String temp;
			int myLine = commands.size();
			commands.add("");
			
			String[] curArgs = args;
			
			int returnPos;
			ArrayList<Integer> jumpCommands = new ArrayList<Integer>();			
			
			kwStack.push(KeyWord.IF);
			returnPos = parse(in,start);
			
			// was there an elseif to read?
			while(!kwStack.isEmpty() && kwStack.peek() == KeyWord.ELSEIF)
			{ 
				// finish existing command; join args starting with second element
				kwStack.pop();
				temp = "";
				for(int i = 1; i < curArgs.length; i++)
					temp += curArgs[i]+" ";
				commands.set(myLine, JSONObj(readExpression("I",temp,commands.size()+1)));
				
				// set jump placeholder
				jumpCommands.add(commands.size());
				commands.add("");
				
				// add a new space for ELSEIF
				myLine = commands.size();
				commands.add("");
				
				returnPos = parse(in,returnPos);
				// ready new argument set
				curArgs = ifElseChain.pop();
			}
			
			// finish off if or last elseif
			temp = "";
			for(int i = 1; i < curArgs.length; i++)
				temp += curArgs[i]+" ";
			commands.set(myLine, JSONObj(readExpression("I",temp,commands.size()+1)));
			
			// finally handle else
			if(!kwStack.isEmpty() && kwStack.peek() == KeyWord.ELSE)
			{
				// add final jump position from last if
				jumpCommands.add(commands.size());
				commands.add("");
								
				kwStack.pop();
				returnPos = parse(in,returnPos);
			}
			
			// update all jump positions from jumpCommands list
			for(int i = 0; i < jumpCommands.size(); i++)
				commands.set(jumpCommands.get(i), JSONObj(new String[]{"J",commands.size()+""}));
			
			return returnPos;
		}

		printError("parse error: {{#IF}} requires arguments", curInLine);
		return -1;
	}
	
	/**
	 * Read an expression for if statement
	 * @param commandSym The symbol to be placed in front of the rest of the arguments
	 * @param exp The expression to parse
	 * @return list of command args for renderer
	 */
	private static String[] readExpression(String commandSym, String exp, int jumpLine)
	{
		/* Different forms of IF:
		 * 0: check for existence/false/empty
		 * 1: <
		 * 2: >
		 * 3: <=
		 * 4: >=
		 * 5: ==
		 * 6: !=
		 */
		String[] syms = {"<",">","<=",">=","==","!="};
		int symNum = -1;
		for(int i = 0; i < syms.length; i++)
		{
			if(exp.indexOf(syms[i]) != -1)
			{
				symNum = i;
				break;
			}
		}
		
		if(symNum != -1)
		{
			String[] split = exp.split(syms[symNum]);
			if(split.length < 2)
				printError("parse error: malformed {{IF}} expression",curInLine);
			
			// does not support compound expressions (&& and ||)
			
			return new String[]{commandSym,(symNum+1)+"",parse_PATH(split[0].trim())+"",parse_PATH(split[1].trim())+"",jumpLine+""};
		}
		// otherwise we are simply checking for existence/false/empty
		return new String[]{commandSym,"0",parse_PATH(exp)+"","-",jumpLine+""};
	}
	
	/**
	 * Parse an ELSE tag
	 * NOTE: ELSE is more completely handled in parse_IF and parse_EACH
	 * @param in Compiler input
	 * @param start Position to start parsing at
	 * @param args Arguments retrieved from the tag
	 * @return Position after the block ends
	 */
	private static int parse_ELSE(String in, int start, String[] args)
	{
		// EACH and IF are legal predecessors to ELSE
		if(kwStack.peek() == KeyWord.EACH || kwStack.peek() == KeyWord.IF || kwStack.peek() == KeyWord.ELSEIF)
		{
			kwStack.push(KeyWord.ELSE);
			return start;
		}
		printError("parse error: {{/ELSE}} with no opening condition",curInLine);
		return -1;
	}
	
	/**
	 * Parse an ELSEIF tag
	 * NOTE: ELSEIF is more completely handled in parse_IF
	 * @param in Compiler input
	 * @param start Position to start parsing at
	 * @param args Arguments retrieved from the tag
	 * @return Position after the loop ends
	 */
	private static int parse_ELSEIF(String in, int start, String[] args)
	{
		// IF and ELSEIF are legal predecessors to ELSEIF
		if(kwStack.peek() == KeyWord.IF || kwStack.peek() == KeyWord.ELSEIF)
		{
			ifElseChain.add(args);
			kwStack.push(KeyWord.ELSEIF);
			return start;
		}
		printError("parse error: {{/ELSEIF}} with no opening condition",curInLine);
		return -1;
	}
	
	/**
	 * Parse an with block. Same idea as each and if but simplified
	 * 
	 * @param in Compiler input
	 * @param start Position to start parsing at
	 * @param args Arguments retrieved from the tag
	 * @return Position after the loop ends
	 */
	private static int parse_WITH(String in, int start, String[] args)
	{
		if(args.length > 1)
		{
			int myLine = commands.size();
			commands.add("");
			
			int returnPos;
			
			kwStack.push(KeyWord.WITH);
			returnPos = parse(in,start);

			commands.set(myLine, JSONObj(new String[]{"W",parse_PATH(args[1])+"",commands.size()+""}));
			
			return returnPos;
		}

		printError("parse error: {{#WITH}} requires arguments", curInLine);
		return -1;
	}
	
	/**
	 * Parse a function tag
	 * 
	 * @param args Arguments retrieved from the tag
	 */
	private static void parse_FUNCTION(String[] args)
	{
		if(args.length > 1)
			commands.add(JSONObj(new String[]{"F",args[0].substring(1),parse_PATH(args[1])+"",}));
	}
	
	/**
	 * Generate a JSON object from calculated values
	 * @param com Command
	 * @param args Command arguments
	 * @return
	 */
	private static String JSONObj(String[] args)
	{
		String a = "";
		for(int i = 0; i < args.length-1; i++)
			a += "\""+args[i]+"\",";
		a += "\""+args[args.length-1]+"\"";
		return "["+a+"]";//     " + (curInLine+1);
	}
	
	/**
	 * Remove whitespace from simple print strings. Also a way of tracking the current input line.
	 * @param in Input string
	 * @return String with common whitespace removed
	 */
	private static String removeWhitespace(String in)
	{
		int last = 0;
		while((last = in.indexOf('\n', last)) > -1)
		{
			last++;
			curInLine++;
		}
		return in.replaceAll("\n","").replaceAll("\r","").replaceAll("\t","");
	}
	
	/**
	 * Escape all quotes to avoid output JSON conflicts. Could not use replace all easily due to regex and slashes
	 * @param in
	 * @return String with spaces escaped
	 */
	private static String escapeQuotes(String in)
	{
		StringBuilder sb = new StringBuilder();
		char temp;
		for(int i = 0; i < in.length(); i++)
		{
			temp = in.charAt(i);
			if(temp == '"')
				sb.append("\\\"");
			//else if(temp == '\'')
			//	sb.append("\\'");
			else
				sb.append(temp);
		}
		return sb.toString();
	}
	
	/**
	 * Performs all text transformations to check if a tag is detected
	 * @param in Detected tag name
	 * @param comp Desired tag name
	 * @return True if a match
	 */
	private static boolean checkTag(String in, String comp)
	{
		return in.trim().toUpperCase().equals(comp);
	}
	
	/**
	 * Print an error message and kill the compiler
	 * @param message Message to display
	 */
	private static void printError(String message, int lineNum)
	{
		System.out.println("TemplateCompiler: "+message/*+" at line "+lineNum*/);
		System.exit(1);
	}

}
