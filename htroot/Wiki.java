// Wiki.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 01.07.2003
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// Contains contributions from Alexandier Schier [AS]

// you must compile this file with
// javac -classpath .:../classes Wiki.java
// if the shell's current path is HTROOT

import java.util.*;
import java.text.*;
import java.io.*;
import de.anomic.tools.*;
import de.anomic.server.*;
//import de.anomic.yacy.*;
import de.anomic.data.*;
import de.anomic.plasma.*;
import de.anomic.http.*;

public class Wiki {

    private static String ListLevel = "";
    private static String numListLevel = "";

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(Date date) {
	return SimpleFormatter.format(date);
    }


    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();
	if (post == null) {
	    post = new serverObjects();
	    post.put("page", "start");
	}

	String pagename = post.get("page", "start");
        String ip = post.get("CLIENTIP", "127.0.0.1");
        String author = post.get("author", "anonymous");
	if (author.equals("anonymous")) {
            author = switchboard.wikiDB.guessAuthor(ip);
            if (author == null) {
		if (de.anomic.yacy.yacyCore.seedDB.mySeed == null)
                    author = "anonymous";
                else
                    author = de.anomic.yacy.yacyCore.seedDB.mySeed.get("Name", "anonymous");
            }
        }
        
	if (post.containsKey("submit")) {
	    // store a new page
	    switchboard.wikiDB.write(switchboard.wikiDB.newEntry(pagename, author, ip,
								 post.get("reason", "edit"),
								 ((String) post.get("content", "")).getBytes()));
	}

	wikiBoard.entry page = switchboard.wikiDB.read(pagename);

	if (post.containsKey("edit")) {
	    // edit the page
	    try {
		prop.put("pagecontent", "");
		prop.put("pageedit",
			 "<form action=\"Wiki.html\" method=\"post\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\">" +
			 //"<form action=\"Wiki.html\" method=\"post\" enctype=\"application/x-www-form-urlencoded\">" +
			 "<p>Author:<br><input name=\"author\" type=\"text\" size=\"80\" maxlength=\"80\" value=\"" + author + "\"></p>" +
			 "<p>Text:<br><textarea name=\"content\" cols=\"80\" rows=\"24\">" + new String(page.page(), "ISO-8859-1") + "</textarea></p>" +
			 "<input type=\"hidden\" name=\"page\" value=\"" + pagename + "\">" +
			 "<input type=\"hidden\" name=\"reason\" value=\"edit\">" +
			 "<input type=\"submit\" name=\"submit\" value=\"Submit\">" +
			 "<input type=\"submit\" name=\"view\" value=\"Discard\">" +
			 "</form>");
	    } catch (UnsupportedEncodingException e) {}
	} else if (post.containsKey("index")) {
	    // view an index
	    String index = "<table border=\"0\" cellpadding=\"2\" cellspacing=\"0\">" +
                "<tr class=\"TableHeader\"><td>Subject</td><td>Change Date</td><td>Author</td></tr>";

            String subject;
            try {
	    Iterator i = switchboard.wikiDB.keys(true);
            wikiBoard.entry entry;
            while (i.hasNext()) {
		subject = (String) i.next();
                entry = switchboard.wikiDB.read(subject);
                index += "<tr class=\"TableCellLight\">";
		index += "<td><a href=\"Wiki.html?page=" + wikiBoard.webalize(subject) + "\">" + subject + "</a></td>";
		index += "<td>" + dateString(entry.date()) + "</td>";
		index += "<td>" + entry.author() + "</td>";
                index += "</tr>";
	    }
            } catch (IOException e) {
                index += "IO Error reading wiki database: " + e.getMessage();
            }
            index += "</table>";
	    prop.put("pagecontent", index);
	    prop.put("pageedit",
		     "<form action=\"Wiki.html\" method=\"post\" enctype=\"multipart/form-data\">" +
		     "<input type=\"hidden\" name=\"page\" value=\"" + pagename + "\">" +
		     "<input type=\"button\" name=\"demo\" value=\"Start Page\" onClick=\"self.location.href='Wiki.html'\">" + 
		     "</form>");
	} else {
	    // show page
	    prop.put("pagecontent",
		     "<table width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
		     "<tr class=\"TableHeader\" width=\"100%\" ><td height=\"10\" class=\"TableHeader\" class=\"black\"><font size=\"1\">&nbsp;<b>" +
                     "yacyWiki page: " + pagename + ",&nbsp;&nbsp;&nbsp;last edited by " + page.author() + ",&nbsp;&nbsp;&nbsp;change date " + dateString(page.date()) +
                     "</b></font></td></tr>" +
		     "<tr class=\"WikiBackground\"><td>" + 
		     "<table width=\"100%\" border=\"0\" cellpadding=\"5\" cellspacing=\"0\"><tr><td>" +
                     transform(page.page(), switchboard) +
                     "</td></tr></table>" +
		     "</td></tr></table>");

	    prop.put("pageedit",
		     "<form action=\"Wiki.html\" method=\"get\">" +
		     "<input type=\"hidden\" name=\"page\" value=\"" + pagename + "\">" +
		     "<input type=\"button\" name=\"demo\" value=\"Start Page\" onClick=\"self.location.href='Wiki.html'\">" + 
		     "<input type=\"submit\" name=\"index\" value=\"Index\">" +
		     "<input type=\"submit\" name=\"edit\" value=\"Edit\">" +
		     "</form>");
	}

	// return rewrite properties
	return prop;
    }


    public static String transform(byte[] content, plasmaSwitchboard switchboard) {
        ByteArrayInputStream bais = new ByteArrayInputStream(content);
        BufferedReader br = new BufferedReader(new InputStreamReader(bais));
        String line;
        String out = "";
        try {
        while ((line = br.readLine()) != null) {
            out += transformLine(new String(line), switchboard) + serverCore.crlfString;
        }
	return out;
        } catch (IOException e) {
            return "internal error: " + e.getMessage();
        }
    }

    public static String transformLine(String result, plasmaSwitchboard switchboard) {
	// transform page
	int p0, p1;
        
	// avoide html inside
	//p0 = 0; while ((p0 = result.indexOf("&", p0+1)) >= 0) result = result.substring(0, p0) + "&amp;" + result.substring(p0 + 1);
	p0 = 0; while ((p0 = result.indexOf('"', p0+1)) >= 0) result = result.substring(0, p0) + "&quot;" + result.substring(p0 + 1);
	p0 = 0; while ((p0 = result.indexOf("<", p0+1)) >= 0) result = result.substring(0, p0) + "&lt;" + result.substring(p0 + 1);
	p0 = 0; while ((p0 = result.indexOf(">", p0+1)) >= 0) result = result.substring(0, p0) + "&gt;" + result.substring(p0 + 1);
	//p0 = 0; while ((p0 = result.indexOf("*", p0+1)) >= 0) result = result.substring(0, p0) + "&#149;" + result.substring(p0 + 1);
	p0 = 0; while ((p0 = result.indexOf("(C)", p0+1)) >= 0) result = result.substring(0, p0) + "&copy;" + result.substring(p0 + 3);
        
        // format lines
        if (result.startsWith(" ")) result = "<tt>" + result + "</tt>";
        if (result.startsWith("----")) result = "<hr>";

        // format headers
        if ((p0 = result.indexOf("====")) >= 0) {
            p1 = result.indexOf("====", p0 + 4);
            if (p1 >= 0) result = result.substring(0, p0) + "<h4>" +
                                  result.substring(p0 + 4, p1) + "</h4>" +
                                  result.substring(p1 + 4);
        }
        if ((p0 = result.indexOf("===")) >= 0) {
            p1 = result.indexOf("===", p0 + 3);
            if (p1 >= 0) result = result.substring(0, p0) + "<h3>" +
                                  result.substring(p0 + 3, p1) + "</h3>" +
                                  result.substring(p1 + 3);
        }
        if ((p0 = result.indexOf("==")) >= 0) {
            p1 = result.indexOf("==", p0 + 2);
            if (p1 >= 0) result = result.substring(0, p0) + "<h2>" +
                                  result.substring(p0 + 2, p1) + "</h2>" +
                                  result.substring(p1 + 2);
        }

        if ((p0 = result.indexOf("''''")) >= 0) {
            p1 = result.indexOf("''''", p0 + 4);
            if (p1 >= 0) result = result.substring(0, p0) + "<b><i>" +
                                  result.substring(p0 + 4, p1) + "</i></b>" +
                                  result.substring(p1 + 4);
        }
        if ((p0 = result.indexOf("'''")) >= 0) {
            p1 = result.indexOf("'''", p0 + 3);
            if (p1 >= 0) result = result.substring(0, p0) + "<b>" +
                                  result.substring(p0 + 3, p1) + "</b>" +
                                  result.substring(p1 + 3);
        }
        if ((p0 = result.indexOf("''")) >= 0) {
            p1 = result.indexOf("''", p0 + 2);
            if (p1 >= 0) result = result.substring(0, p0) + "<i>" +
                                  result.substring(p0 + 2, p1) + "</i>" +
                                  result.substring(p1 + 2);
        }

	//* unorderd Lists contributed by [AS]
	//** Sublist
	if(result.startsWith(ListLevel + "*")){ //more stars
		p0 = result.indexOf(ListLevel);
		p1 = result.length();
		result = "<ul>" + serverCore.crlfString +
			 "<li>" +
			 result.substring(ListLevel.length() + 1, p1) +
			 "</li>";
		ListLevel += "*";
	}else if(ListLevel.length() > 0 && result.startsWith(ListLevel)){ //equal number of stars
		p0 = result.indexOf(ListLevel);
		p1 = result.length();
		result = "<li>" +
			 result.substring(ListLevel.length(), p1) +
			 "</li>";
	}else if(ListLevel.length() > 0){ //less stars
		int i = ListLevel.length();
		String tmp = "";
		
		while(! result.startsWith(ListLevel.substring(0,i)) ){
			tmp += "</ul>";
			i--;
		}
		ListLevel = ListLevel.substring(0,i);
		p0 = ListLevel.length();
		p1 = result.length();
		
		if(ListLevel.length() > 0){
			result = tmp +
				 "<li>" +
				 result.substring(p0, p1) +
				 "</li>";
		}else{
			result = tmp + result.substring(p0, p1);
		}
	}


	//# sorted Lists contributed by [AS]
	//## Sublist
	if(result.startsWith(numListLevel + "#")){ //more #
		p0 = result.indexOf(numListLevel);
		p1 = result.length();
		result = "<ol>" + serverCore.crlfString +
			 "<li>" +
			 result.substring(numListLevel.length() + 1, p1) +
			 "</li>";
		numListLevel += "#";
	}else if(numListLevel.length() > 0 && result.startsWith(numListLevel)){ //equal number of #
		p0 = result.indexOf(numListLevel);
		p1 = result.length();
		result = "<li>" +
			 result.substring(numListLevel.length(), p1) +
			 "</li>";
	}else if(numListLevel.length() > 0){ //less #
		int i = numListLevel.length();
		String tmp = "";
		
		while(! result.startsWith(numListLevel.substring(0,i)) ){
			tmp += "</ol>";
			i--;
		}
		numListLevel = numListLevel.substring(0,i);
		p0 = numListLevel.length();
		p1 = result.length();
		
		if(numListLevel.length() > 0){
			result = tmp +
				 "<li>" +
				 result.substring(p0, p1) +
				 "</li>";
		}else{
			result = tmp + result.substring(p0, p1);
		}
	}
	// end contrib [AS]


        // create  links
	String kl, kv;
        int p;
        // internal links
        while ((p0 = result.indexOf("[[")) >= 0) {
	    p1 = result.indexOf("]]", p0 + 2);
	    if (p1 <= p0) break; else; {
		kl = result.substring(p0 + 2, p1);
                if ((p = kl.indexOf("|")) > 0) {
                    kv = kl.substring(p + 1);
                    kl = kl.substring(0, p);
                } else {
                    kv = kl;
                }
		if (switchboard.wikiDB.read(kl) != null)
		    result = result.substring(0, p0) +
			"<a class=\"known\" href=\"Wiki.html?page=" + kl + "\">" + kv + "</a>" +
			result.substring(p1 + 2);
		else
		    result = result.substring(0, p0) +
			"<a class=\"unknown\" href=\"Wiki.html?page=" + kl + "&edit=Edit\">" + kv + "</a>" +
			result.substring(p1 + 2);
	    }
	}
        
        // external links
        while ((p0 = result.indexOf("[")) >= 0) {
	    p1 = result.indexOf("]", p0 + 1);
            if (p1 <= p0) break; else {
		kl = result.substring(p0 + 1, p1);
                if ((p = kl.indexOf(" ")) > 0) {
                    kv = kl.substring(p + 1);
                    kl = kl.substring(0, p);
                } else {
                    kv = kl;
                }
                if (!(kl.startsWith("http://"))) kl = "http://" + kl;
		result = result.substring(0, p0) +
		    "<a class=\"extern\" href=\"" + kl + "\">" + kv + "</a>" +
		    result.substring(p1 + 1);
	    }
	}
        
        if (result.endsWith("</li>")) return result; else return result + "<br>";
    }

    /*
      what we need:

      == New section ==
      === Subsection ===
      ==== Sub-subsection ====
      link colours: existent=green, non-existent=red
      ----
      [[wikipedia FAQ|answers]]  (first element is wiki page name, second is link print name)
      [http://www.nupedia.com Nupedia] (external link)
      [http://www.nupedia.com] (un-named external link)      
      ''Emphasize'', '''strongly''', '''''very strongly''''' (italics, bold, bold-italics)
   
      * Lists are easy to do:
      ** start every line with a star
      *** more stars means deeper levels
      # Numbered lists are also good
      ## very organized
      ## easy to follow
      ; Definition list : list of definitions
      ; item : the item's definition
      : A colon indents a line or paragraph.
      A manual newline starts a new paragraph.

      A picture: [[Image:Wiki.png]]
      [[Image:Wiki.png|right|jigsaw globe]] (floating right-side with caption)

    */

}
