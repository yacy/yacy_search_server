var appname  = "YACY: a Java Freeware P2P-Based Search Engine with Caching HTTP Proxy";
var thismenu = new Array(
    "index","FAQ","Details","Technology","Platforms","News","Demo","License","Download",
    "Installation","Volunteers","Deutsches Forum@http://www.yacy-forum.de","English Forum@http://sourceforge.net/forum/?group_id=116142","Material","Links","Contact","","Impressum");
var mainmenu = new Array(
    "YACY Home@http://www.yacy.net/index.html",
    "Products@http://www.yacy.net/Products/index.html",
    "Consulting@http://www.yacy.net/Consulting/index.html",
    "Profile@http://www.yacy.net/Profile/index.html",
    "Impressum@http://www.yacy.net/Impressum/index.html");
var root = "http://www.yacy.net/";

function headline() {
  document.writeln("<table bgcolor=\"#4070A0\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">");
  document.writeln(
    "<tr>" +
      	"<td width=\"230\" height=\"80\" rowspan=\"2\"><a href=\"" + root + "\">" +
	"<img border=\"0\" src=\"grafics/yacy.gif\" align=\"top\"></a></td>" +
	"<!--<td align=\"center\" valign=\"bottom\"><font size=\"3\" face=\"Helvetica, Arial\" color=\"#ffffff\"><b>N&nbsp;E&nbsp;T&nbsp;W&nbsp;O&nbsp;R&nbsp;K&nbsp;&nbsp;&nbsp;&nbsp;A&nbsp;P&nbsp;P&nbsp;L&nbsp;I&nbsp;A&nbsp;N&nbsp;C&nbsp;E&nbsp;S&nbsp;&nbsp;&nbsp;&amp;&nbsp;&nbsp;C&nbsp;O&nbsp;N&nbsp;S&nbsp;U&nbsp;L&nbsp;T&nbsp;I&nbsp;N&nbsp;G</b></font></td>-->" +
	"<td align=\"center\" valign=\"top\"><font size=\"3\" face=\"Helvetica, Arial\" color=\"#ffffff\"><br><br><b>Y&nbsp;A&nbsp;C&nbsp;Y&nbsp;&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;D&nbsp;I&nbsp;S&nbsp;T&nbsp;R&nbsp;I&nbsp;B&nbsp;U&nbsp;T&nbsp;E&nbsp;D&nbsp;&nbsp;&nbsp;&nbsp;P&nbsp;2&nbsp;P&nbsp;-&nbsp;B&nbsp;A&nbsp;S&nbsp;E&nbsp;D&nbsp;&nbsp;&nbsp;&nbsp;W&nbsp;E&nbsp;B&nbsp;&nbsp;&nbsp;I&nbsp;N&nbsp;D&nbsp;E&nbsp;X&nbsp;I&nbsp;N&nbsp;G</b></font></td>" +
	"<td width=\"140\"></td>" +
    "</tr>" +
    "<tr>" +
     	"<td colspan=\"3\" align=\"right\">");
	//tmenu();
  document.writeln("<br>");
  document.writeln("</td></tr></table>");
}

function filename() {
  var p = window.location.pathname;
  return p.substring(p.lastIndexOf("/") + 1);
}

function docname() {
  var f = filename()
  return f.substring(0, f.indexOf("."));
}

function lmenu() {
  document.writeln("<table width=\"100%\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\">");
  var dn = docname();
  var printname;
  var pos;
  for (var i = 0; i < thismenu.length; ++i) {
    document.writeln("<tr><td height=\"2\"></td></tr>"); 
    if (thismenu[i] == "index") printname = "About"; else printname = thismenu[i];
    if (thismenu[i] == "") {
      document.writeln("<tr><td height=\"20\" class=\"white\" >&nbsp;</td></tr>"); 
    } else if (dn == thismenu[i]) {
      document.writeln("<tr><td height=\"20\" class=\"white\" bgcolor=\"#4070A0\" valign=\"middle\">&nbsp;" + printname + "</td></tr>"); 
    } else {
      pos = thismenu[i].indexOf("@");
      if (pos >= 0)
      	 document.writeln("<tr><td height=\"20\" bgcolor=\"#BDCDD4\" valign=\"middle\">&nbsp;<a href=\"" + thismenu[i].substring(pos + 1) + "\" class=\"dark\">" + thismenu[i].substring(0, pos) + "</b></td></tr>");
      else
	document.writeln("<tr><td height=\"20\" bgcolor=\"#BDCDD4\" valign=\"middle\">&nbsp;<a href=\"" + thismenu[i] + ".html\" class=\"dark\">" + printname + "</b></td></tr>"); 
    }
  }
  document.writeln("</table>");
}

function tmenu() {
  //document.writeln("<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">");

  //document.writeln("<tr><td height=\"20\" class=\"white\" bgcolor=\"#BDCDD4\" align=\"center\" valign=\"middle\">"); 
  var linkpath;
  var printname;
  var pos;
    pos = mainmenu[0].indexOf("@");
    linkpath = mainmenu[0].substring(pos + 1);
    printname = mainmenu[0].substring(0, pos);
    document.writeln("<a href=\"" + linkpath + "\" class=\"white\"><font size=\"1\">" + printname + "</font></a>&nbsp;"); 
  for (var i = 1; i < mainmenu.length; ++i) {
    pos = mainmenu[i].indexOf("@");
    linkpath = mainmenu[i].substring(pos + 1);
    printname = mainmenu[i].substring(0, pos);
    document.writeln("<font class=\"white\" size=\"2\">&middot;</font>&nbsp;&nbsp;<a href=\"" + linkpath + "\" class=\"white\"><font size=\"1\">" + printname + "</font></a>&nbsp;"); 
  }
  //document.writeln("</td></tr>"); 
  //document.writeln("</table>"); 
}


function globalheader() {
  document.writeln("<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">");

  document.writeln("<tr><td>");
  //tmenu();
  document.writeln("</td></tr>");
  //document.writeln("<tr><td height=\"1\" bgcolor=\"#000000\"></td></tr>");
  document.writeln("<tr><td>"); headline(); document.writeln("</td></tr>");
  //document.writeln("<tr><td height=\"1\" bgcolor=\"#000000\"></td></tr>");
  document.writeln("<tr><td height=\"2\"></td></tr>");

  document.writeln("<tr><td>" +
		   "<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">" +
		   "  <tr>" +
                   "  <td width=\"100\" valign=\"top\">");
  lmenu();
  document.writeln("  </td>" +
                   "  <td width=\"10\" valign=\"top\"></td>" +
		   "  <td valign=\"top\">");
  document.writeln("  <table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">");
  document.writeln("  <tr><td height=\"2\"></td></tr>");

  //if ((docname() != "index") && (docname() != "indexd")) {
  //   document.writeln("  <tr><td height=\"20\" class=\"black\" align=\"center\" valign=\"middle\">" + appname + "</td></tr>");
  //   document.writeln("  <tr><td height=\"1\" bgcolor=\"#000000\"></td></tr>");
  //}

  document.writeln("  <tr><td><br>");
}

function globalfooter() {
  document.writeln("  <br><br></td></tr></table>");
  document.writeln("  </td>" + 
                   "  <td width=\"10\" valign=\"top\">");
  document.writeln("  </td>" +
                   "  </tr></table>" +
                   "</td></tr></table>");
}
