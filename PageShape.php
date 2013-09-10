<?PHP
/**
 * Copyright (C) 2013 Matthew D Jones
 *
 * File: PageShape.php
 * Author: Matthew Jones
 * Date: 9/8/13
 * Version: 1.0.38
 * Usage: PHP 5.2+
 * Desc: see README.txt
 */

class PageShape
{
	public $contextStack = null;
	public $paths = null;
	public $indexStack = null;
	public $lengthStack = null;
	public $templates = null; // map/array
	public $functions = null; // map/array
	
	/**
	 * Add template to available list
	 * @param n Template file
	 */
	public function addTemplate($n)
	{
		$fh = fopen($n,'r');
		$templateContent = fread($fh,filesize($n));
		fclose($fh);

		// the following makes a big assumption that the original template file name contains no quotes
		$startQ = strpos($templateContent,"\"")+1;
		$endQ = strpos($templateContent,"\"",$startQ);
		$name = substr($templateContent,$startQ,$endQ-$startQ);
		$tJSON = json_decode(substr($templateContent,$endQ+2,strlen($templateContent)-($endQ+4)),true);

		$this->templates[$name] = $tJSON;
	}
	
	/**
	 * Add function to available list
	 * @param n Function name
	 * @param c Function content
	 */
	public function addFunction($n,$c)
	{
		$this->functions[$n] = $c;
	}
	
	/**
	 * Render the template
	 * @param t Template to use
	 * @param d Data to fill template with
	 */
	public function render($t, $d)
	{
		$templ = $this->templates[$t];
		if(!$templ){
			//console.log("PageShape: Error: Attempt to call null template " + $t);
			return "";
		}
		$this->paths = $templ["paths"];
		$this->contextStack=array();
		$this->indexStack=array();
		$this->lengthStack=array();
		return $this->renderer($templ["template"],$d,0,count($templ["template"]));
	}
	
	/**
	 * Render template
	 * @param t Template object
	 * @param d Data to fill the template with
	 * @param s Start line
	 * @param e End line
	 */
	public function renderer($t, $d, $s, $e)
	{
		$i;
		$output = "";
		array_push($this->contextStack,$d);
		for($i = $s; $i < $e; $i++)
		{
			switch($t[$i][0]) // TODO: possibly break the following cases into functions (more code but less confusion)
			{
			case "P": // Print plaintext
					$output .= $t[$i][1];
					break;
			case "V": // Variable/path print
					$v = $this->selectContext($t[$i][1]);
					$output .= $v!=null?$v:"";
					break;
			case "L": // Each block
					$cx = $this->selectContext($t[$i][1]);
					if(isset($cx) && is_array($cx)){
						$cl = count($cx);
						array_push($this->lengthStack,count($cx));
						for($j = 0; $j < $cl; $j++){
							array_push($this->indexStack,$j);
							$output .= $this->renderer($t,$cx[$j],$i+1,intval($t[$i][2]));
							array_pop($this->indexStack);
						}
						array_pop($this->lengthStack);
					} else {
						$output .= $this->renderer($t,$d,intval($t[$i][2]),intval($t[$i][3]));
					}
					$i = intval($t[$i][3])-1;
					break;
			case "I": // If block; NOTE: do not move into new context
					if($this->testCondition($t[$i]) == false)
						$i = intval($t[$i][4])-1;
					break;
			case "J": // jump to new position
					$i = intval($t[$i][1])-1;
					break;
			case "W": // With block, simply move into new context
					$cx = $this->selectContext($t[$i][1]);
					$cont = intval($t[$i][2]);
					if(isset($cx))
						$output .= $this->renderer($t,$cx,$i+1,$cont);
					$i = $cont-1;
					break;
			case "F": // Call a function defined by the user
					$cx = $this->selectContext($t[$i][2]);
					$fnc = $this->functions[$t[$i][1]];
					if(isset($cx) && isset($fnc))
						$output .= $fnc($cx);
					break;
			}
		}
		array_pop($this->contextStack);
		return $output;
	}
	
	/**
	 * Test one of seven condition types
	 * @param a Current template command
	 **/
	public function testCondition($a)
	{
		$cx = $this->selectContext($a[2]);
		switch($a[1]){
			case "0":
				if(!isset($cx) || $cx == false || $cx == "" || $cx == null)
					return false;
				return true;
			case "1":
				return ($cx < $this->selectContext($a[3]));
			case "2":
				return ($cx > $this->selectContext($a[3]));
			case "3":
				return ($cx <= $this->selectContext($a[3]));
			case "4":
				return ($cx >= $this->selectContext($a[3]));
			case "5":
				return ($cx == $this->selectContext($a[3]));
			case "6":
				return ($cx != $this->selectContext($a[3]));
		}
		return false;
	}
	
	/**
	 * Parse a variable/path and return the appropriate context
	 * @param v Path ID
	 **/
	public function selectContext($v)
	{
		$p = $this->paths[$v];
		switch($p[0])
		{
			case "V":
					$k = $this->contextStack[count($this->contextStack) - (intval($p[1])+1)];
					$pl = count($p);
					for($i = 2; $i < $pl; $i++)
					{
						if($p[$i] == "$") {
							return $k;
						} else if($p[$i] == "^") { // add 1 (indexing from 0 isn't as useful when displaying output)
							return $this->indexStack[count($this->indexStack)-(intval($p[1])+1)] + 1;
						} else if($p[$i] == "*") {
							return $this->lengthStack[count($this->lengthStack)-(intval($p[1])+1)];
						} else {
							$k = $k[$p[$i]];
							if(!isset($k) || $k == null){
								//console.log("PageShape: Error: Attempt to call null variable " + $p[$i]);
								return null;
							}
						}
					}
					return $k;
			case "S":
					return $p[1];
			case "N":
					return floatval($p[1]);
			case "B":
					return $p[1]=="true"?true:false;
		}
		return null;
	}
}
?>
