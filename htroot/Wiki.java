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

// Contains contributions from Alexander Schier [AS]
// and Marc Nause [MN]

// You must compile this file with
// javac -classpath .:../classes Wiki.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;

import de.anomic.data.wikiBoard;
import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyCore;

public class Wiki {

    //private static String ListLevel = "";
    //private static String numListLevel = "";

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(Date date) {
	return SimpleFormatter.format(date);
    }


    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) throws IOException {
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
	        byte[] content;
	        try {
	            content = post.get("content", "").getBytes("UTF-8");
	        } catch (UnsupportedEncodingException e) {
	            content = post.get("content", "").getBytes();
	        }
            switchboard.wikiDB.write(switchboard.wikiDB.newEntry(pagename, author, ip, post.get("reason", "edit"), content));
            // create a news message
            HashMap map = new HashMap();
            map.put("page", pagename);
            map.put("author", author);
            map.put("ip", ip);
            try {
                yacyCore.newsPool.publishMyNews(new yacyNewsRecord("wiki_upd", map));
            } catch (IOException e) {}
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
			 "<input type=\"submit\" name=\"preview\" value=\"Preview\">" +
			 "<input type=\"submit\" name=\"view\" value=\"Discard\">" +
			 "</form>");
	    } catch (UnsupportedEncodingException e) {}
	} 

	//contributed by [MN]
	else if (post.containsKey("preview")) {
		// preview the page
		wikiCode wikiTransformer=new wikiCode(switchboard);
		
		prop.put("pagecontent",
		     "<h2>Preview</h2><p>No changes have been submitted so far!</p>" +
		     "<table width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
		     "<tr class=\"TableHeader\" width=\"100%\" ><td height=\"10\" class=\"TableHeader\" class=\"black\"><font size=\"1\">&nbsp;<b>" +
                     "yacyWiki page: " + pagename + ",&nbsp;&nbsp;&nbsp;last edited by " + author + 
		     ",&nbsp;&nbsp;&nbsp;change date " + dateString(new Date()) +
                     "</b></font></td></tr>" +
		     "<tr class=\"WikiBackground\"><td>" + 
		     "<table width=\"100%\" border=\"0\" cellpadding=\"5\" cellspacing=\"0\"><tr><td>" +
                     wikiTransformer.transform((post.get("content", ""))) +
                     "</td></tr></table>" +
		     "</td></tr></table>");
		     
		prop.put("pageedit",
			 "<form action=\"Wiki.html\" method=\"post\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\">" +
			 //"<form action=\"Wiki.html\" method=\"post\" enctype=\"application/x-www-form-urlencoded\">" +
			 "<input type=\"submit\" name=\"submit\" value=\"Submit\">" +
			 "<input type=\"submit\" name=\"view\" value=\"Discard\"><br><br><br>" +
			 "<h2>Edit</h2>"+
			 "<p>Author:<br><input name=\"author\" type=\"text\" size=\"80\" maxlength=\"80\" value=\"" + author + "\"></p>" +
			 "<p>Text:<br><textarea name=\"content\" cols=\"80\" rows=\"24\">" + (post.get("content", "")) + "</textarea></p>" +
			 "<input type=\"hidden\" name=\"page\" value=\"" + pagename + "\">" +
			 "<input type=\"hidden\" name=\"reason\" value=\"edit\">" +
			 "<input type=\"submit\" name=\"submit\" value=\"Submit\">" +
			 "<input type=\"submit\" name=\"preview\" value=\"Preview\">" +
			 "<input type=\"submit\" name=\"view\" value=\"Discard\">" +
			 "</form>");
	} 
	//end contrib of [MN]
			
	else if (post.containsKey("index")) {
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
	} 
	
	else {
        wikiCode wikiTransformer=new wikiCode(switchboard);
	    // show page
	    prop.put("pagecontent",
		     "<table width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">" +
		     "<tr class=\"TableHeader\" width=\"100%\" ><td height=\"10\" class=\"TableHeader\" class=\"black\"><font size=\"1\">&nbsp;<b>" +
                     "yacyWiki page: " + pagename + ",&nbsp;&nbsp;&nbsp;last edited by " + page.author() + ",&nbsp;&nbsp;&nbsp;change date " + dateString(page.date()) +
                     "</b></font></td></tr>" +
		     "<tr class=\"WikiBackground\"><td>" + 
		     "<table width=\"100%\" border=\"0\" cellpadding=\"5\" cellspacing=\"0\"><tr><td>" +
                     wikiTransformer.transform(page.page()) +
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


}
