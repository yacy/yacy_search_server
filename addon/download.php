<?php
/*
This file serves the latest Java version locations for the Windows installer. 

JAVA VERSION
http://www.java.com/de/download/manual.jsp BundleId +1 / +2

$_GET['what']		should be 'jre'
$_GET['version']	32 or 64 (bit)
$_GET['yacyrevnr']	not used yet

*/


if ($_GET['what'] == 'jre') {
	
	if($_GET['version'] == 32)
		// jre-6u18-windows-i586.exe
		header('Location: http://javadl.sun.com/webapps/download/AutoDL?BundleId=37718');

	if($_GET['version'] == 64)
		// jre-6u18-windows-x64.exe
		header('Location: http://javadl.sun.com/webapps/download/AutoDL?BundleId=37401');

}

?>
