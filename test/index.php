<!DOCTYPE html>
<html>

	<head>
		<style>
			body {
				margin: 0px;
				font-family: helvetica;
				font-size: 14px;
			}
			h1 {
				width: 100%;
				display: block;
				background-color: rgb(200,200,200);
				color: rgb(255,255,255);
				margin-top: 0px;
				padding: 10px;
				letter-spacing: 5px;
			}
			.elements {
				padding: 10px;
			}
		</style>
	</head>
	
	<body>

<?php

// Include PageShape class
include("PageShape.php");

// get data to fill template; must be JSON
$myData = json_decode('{"name":"Matt","elements":[		{"name":"copper","prop":["red","metal"]},	{"name":"iron","prop":["silver","metal"]},		{"name":"helium","prop":["colorless","gas"]}],"hasGems":true,"hasManyGems":true,"hasTooManyGems":false,"gems":	{"red":["ruby","red agate"],	 "blue":["sapphire","azurite"]}}',true);

// create PageShape object
$ps = new PageShape();

// add template file to object
$ps->addTemplate("template.js");
// add user function (optional)
$ps->addFunction("arrayLengthStars",function($data)
{
	$output = "";
	for($i = 0; $i < count($data); $i++)
		$output .= "*";
	return $output;
});

// render the template
echo $ps->render("template.html",$myData);

?>

	</body>

</html>
