var thismenu = new Array(
    "index","FAQ","Details","Technology","Platforms","News","Demo","License","Download",
    "Installation","Volunteers","Material","Links","Contact","",
    "Deutsches Forum@http://www.yacy-forum.de","English Forum@http://sourceforge.net/forum/?group_id=116142","",
    "Impressum");
var root = "http://www.yacy.net/";

function headline() {
  document.writeln("<table bgcolor=\"#4070A0\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">");
  document.writeln(
    "<tr>" +
      	"<td width=\"180\" height=\"80\" rowspan=\"3\"><a href=\"" + root + "\">" +
	"<img border=\"0\" src=\"grafics/yacy.gif\" align=\"top\"></a></td>" +
	"<td align=\"center\"><br><H1 class=\"white\"><font size=\"5\">YACY&nbsp;- DISTRIBUTED&nbsp;P2P-BASED WEB&nbsp;INDEXING</font></H1></td>" +
	"<td width=\"120\"></td>" +
    "</tr>" +
    "<tr><td align=\"center\" class=\"white\">" +
        "<a href=\"http://www.yacy.net/index.html\" class=\"white\">Anomic + YaCy Home</a>&nbsp;&nbsp;|&nbsp;" +
        "<a href=\"http://www.yacy.net/Products/index.html\" class=\"white\">Products</a>&nbsp;&nbsp;|&nbsp;" +
        "<a href=\"http://www.yacy.net/Consulting/index.html\" class=\"white\">Consulting</a>&nbsp;&nbsp;|&nbsp;" +
        "<a href=\"http://www.yacy.net/Profile/index.html\" class=\"white\">Profile</a>&nbsp;&nbsp;|&nbsp;" +
        "<a href=\"http://www.yacy.net/Impressum/index.html\" class=\"white\">Impressum</a>" +
    "</td><td></td></tr>" +
    "<tr><td colspan=\"3\">&nbsp;</td></tr>" +
    "</table>");
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

function globalheader() {
  document.writeln("<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"100%\">");
  document.writeln("<tr><td>");
  document.writeln("</td></tr>");
  document.writeln("<tr><td>"); headline(); document.writeln("</td></tr>");
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
