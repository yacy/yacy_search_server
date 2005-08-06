// wikiCode.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Alexander Schier
// last major change: 09.08.2004
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

package de.anomic.data;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.HashMap;

import de.anomic.data.wikiBoard;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyCore;

public class wikiCode {
    private String numListLevel="";
    private String ListLevel="";
    public wikiCode(){
    }

    public String transform(byte[] content, plasmaSwitchboard switchboard) {
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

    public String transformLine(String result, plasmaSwitchboard switchboard) {
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
	String kl, kv, alt, align;
        int p;
        // internal links and images
        while ((p0 = result.indexOf("[[")) >= 0) {
	    p1 = result.indexOf("]]", p0 + 2);
	    if (p1 <= p0) break; else; {
		kl = result.substring(p0 + 2, p1);
		
		// this is the part of the code that's responsible for images
		// contibuted by [MN]
		if(kl.startsWith("Image:")){
			alt = "";
			align = "";
			kv = "";
			kl = kl.substring(6);
			
			// are there any arguments for the image?
			if ((p = kl.indexOf("|")) > 0) {
                	    kv = kl.substring(p+1);
                	    kl = kl.substring(0, p);
			
			    	// if there are 2 arguments, write them into ALIGN and ALT
				if ((p = kv.indexOf("|")) > 0) {
				    align = " align=\"" + kv.substring(0, p) +"\"";
                		    alt = " alt=\"" + kv.substring(p + 1) +"\"";
                		}
				
				// if there is just one, put it into ALT
				else alt = " alt=\"" + kv +"\"";
			}
			
			result = result.substring(0, p0) + "<img src=\"" + kl + "\"" + align + alt +">" + result.substring(p1 + 2);
		}
		// end contrib [MN]
		
		// if it's no image, it might be an internal link
                else {
		
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
