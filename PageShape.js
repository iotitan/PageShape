/**
 * Copyright (C) 2013 Matthew D Jones
 *
 * File: PageShape.js
 * Author: Matthew Jones
 * Date: 8/19/13
 * Version: 1.0.38
 * Desc: see README.txt
 */
var PageShape = 
{
	contextStack: null,
	paths: null,
	indexStack: null,
	lengthStack: null,
	templates: {},
	functions: {},
	/**
	 * Add template to available list
	 * @param n Template name
	 * @param c Template content
	 */
	addTemplate:function(n,c)
	{
		this.templates[n] = c;
	},
	/**
	 * Add function to available list
	 * @param n Function name
	 * @param c Function content
	 */
	addFunction:function(n,c)
	{
		this.functions[n] = c;
	},
	/**
	 * Render the template
	 * @param t Template to use
	 * @param d Data to fill template with
	 */
	render:function(t, d)
	{
		var templ = this.templates[t];
		if(!templ){
			console.log("PageShape: Error: Attempt to call undefined template " + t);
			return "";
		}
		this.paths = templ.paths;
		this.contextStack=new Array();
		this.indexStack=new Array();
		this.lengthStack=new Array();
		return this.renderer(templ.template,d,0,templ.template.length);
	},
	/**
	 * Render template
	 * @param t Template object
	 * @param d Data to fill the template with
	 * @param s Start line
	 * @param e End line
	 */
	renderer:function(t, d, s, e)
	{
		var i;
		var output = "";
		this.contextStack.push(d);
		for(i = s; i < e; i++)
		{
			switch(t[i][0]) // TODO: possibly break the following cases into functions (more code but less confusion)
			{
			case "P": // Print plaintext
					output += t[i][1];
					break;
			case "V": // Variable/path print
					var v = this.selectContext(t[i][1]);
					output += v!=undefined?v:"";
					break;
			case "L": // Each block
					var cx = this.selectContext(t[i][1]);
					if(cx && cx.length){
						var cl = cx.length;
						this.lengthStack.push(cx.length);
						for(var j = 0; j < cl; j++){
							this.indexStack.push(j);
							output += this.renderer(t,cx[j],i+1,parseInt(t[i][2]));
							this.indexStack.pop();
						}
						this.lengthStack.pop();
					} else {
						output += this.renderer(t,d,parseInt(t[i][2]),parseInt(t[i][3]));
					}
					i = parseInt(t[i][3])-1;
					break;
			case "I": // If block; NOTE: do not move into new context
					if(this.testCondition(t[i]) == false)
						i = parseInt(t[i][4])-1;
					break;
			case "J": // jump to new position
					i = parseInt(t[i][1])-1;
					break;
			case "W": // With block, simply move into new context
					var cx = this.selectContext(t[i][1]);
					var cont = parseInt(t[i][2]);
					if(cx)
						output += this.renderer(t,cx,i+1,cont);
					i = cont-1;
					break;
			case "F": // Call a function defined by the user
					var cx = this.selectContext(t[i][2]);
					var fnc = this.functions[t[i][1]];
					if(cx && fnc.call)
						output += fnc.call(this,cx);
					break;
			}
		}
		this.contextStack.pop();
		return output;
	},
	/**
	 * Test one of seven condition types
	 * @param a Current template command
	 **/
	testCondition:function(a)
	{
		var cx = this.selectContext(a[2]);
		switch(a[1]){
			case "0":
				if(!cx || cx == false || cx == "" || cx == null)
					return false;
				return true;
			case "1":
				return (cx < this.selectContext(a[3]));
			case "2":
				return (cx > this.selectContext(a[3]));
			case "3":
				return (cx <= this.selectContext(a[3]));
			case "4":
				return (cx >= this.selectContext(a[3]));
			case "5":
				return (cx == this.selectContext(a[3]));
			case "6":
				return (cx != this.selectContext(a[3]));
		}
		return false;
	},
	/**
	 * Parse a variable/path and return the appropriate context
	 * @param v Path ID
	 **/
	selectContext:function(v)
	{
		var p = this.paths[v];
		switch(p[0])
		{
			case "V":
					var k = this.contextStack[this.contextStack.length - (parseInt(p[1])+1)];
					var pl = p.length;
					for(var i = 2; i < pl; i++)
					{
						if(p[i] == "$") {
							return k;
						} else if(p[i] == "^") { // add 1 (indexing from 0 isn't as useful when displaying output)
							return this.indexStack[this.indexStack.length-(parseInt(p[1])+1)] + 1;
						} else if(p[i] == "*") {
							return this.lengthStack[this.lengthStack.length-(parseInt(p[1])+1)];
						} else {
							k = k[p[i]];
							if(k == null || k == undefined){
								console.log("PageShape: Error: Attempt to call undefined variable " + p[i]);
								return undefined;
							}
						}
					}
					return k;
			case "S":
					return p[1];
			case "N":
					return parseFloat(p[1]);
			case "B":
					return p[1]=="true"?true:false;
		}
		return undefined;
	}
}
