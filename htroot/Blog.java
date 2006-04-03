// Blog.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Jan Sandbrink
// Contains contributions from Marc Nause [MN]
// last change: 03.04.2006
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

// You must compile this file with
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import de.anomic.data.userDB;
import de.anomic.data.blogBoard;
import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class Blog {

	private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//	TODO: make userdefined date/time-strings (localisation)
	
    public static String dateString(Date date) {
    	return SimpleFormatter.format(date);
    }
	
	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
		plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
		serverObjects prop = new serverObjects();
		
		boolean hasRights = switchboard.verifyAuthentication(header, true);

        if(hasRights) prop.put("mode_admin",1);
        else prop.put("mode_admin",0);

        if (post == null) {
            post = new serverObjects();
            post.put("page", "blog_default");
        }

        if(!hasRights){
            userDB.Entry userentry = switchboard.userDB.proxyAuth((String)header.get("Authorization", "xxxxxx"));
            if(userentry != null && userentry.hasBlogRight()){
                hasRights=true;
            }
            //opens login window if login link is clicked - contrib [MN]
            else if(post.containsKey("login")){
                prop.put("AUTHENTICATE","admin log-in");
            }
		}

		String pagename = post.get("page", "blog_default");
	    String ip = post.get("CLIENTIP", "127.0.0.1");
	    String author = post.get("author", "anonymous");
		if (author.equals("anonymous")) {
	    	author = switchboard.blogDB.guessAuthor(ip);
	    	if (author == null) {
	    		if (de.anomic.yacy.yacyCore.seedDB.mySeed == null)
	    			author = "anonymous";
	        	else
	        		author = de.anomic.yacy.yacyCore.seedDB.mySeed.get("Name", "anonymous");
	        }
	    }

		if(hasRights && post.containsKey("delete") && post.get("delete").equals("sure")) {
			switchboard.blogDB.delete(pagename);
		}

		if (post.containsKey("submit") && (hasRights)) {
			// store a new/edited blog-entry
			byte[] content;
			try {
				content = post.get("content", "").getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				content = post.get("content", "").getBytes();
			}

			//set name for new entry
			if(pagename.equals("blog_default"))
				pagename = String.valueOf(System.currentTimeMillis());

			try {
				switchboard.blogDB.write(switchboard.blogDB.newEntry(pagename, post.get("subject",""), author, ip, content));
			} catch (IOException e) {}
		}

		blogBoard.entry page = switchboard.blogDB.read(pagename);

		if (post.containsKey("edit")) {
		    //edit an entry
			if(hasRights) {
				try {
			        prop.put("mode", 1); //edit
			        prop.put("mode_author", author);
			        prop.put("mode_pageid", page.key());
			        prop.put("mode_subject", page.subject());
			        prop.put("mode_page-code", new String(page.page(), "UTF-8").replaceAll("<","&lt;").replaceAll(">","&gt;"));
			    } catch (UnsupportedEncodingException e) {}
			}
			else {
				prop.put("mode",3); //access denied (no rights)
			}
		}
		else if(post.containsKey("preview")) {
			//preview the page
			if(hasRights) {
				wikiCode wikiTransformer=new wikiCode(switchboard);
	            prop.put("mode", 2);//preview
	            prop.put("mode_pageid", pagename);
	            prop.put("mode_author", author);
	            prop.put("mode_subject", post.get("subject",""));
	            prop.put("mode_date", dateString(new Date()));
	            prop.put("mode_page", wikiTransformer.transform(post.get("content", "")));
	            prop.put("mode_page-code", post.get("content", "").replaceAll("<","&lt;").replaceAll(">","&gt;"));
			}
			else prop.put("mode",3); //access denied (no rights)
		}
		else if(post.containsKey("delete") && post.get("delete").equals("try")) {
			if(hasRights) {
				prop.put("mode",4);
				prop.put("mode_pageid",pagename);
				prop.put("mode_author",page.author());
				prop.put("mode_subject",page.subject());
			}
			else prop.put("mode",3); //access denied (no rights)
		}
		else {
	        wikiCode wikiTransformer=new wikiCode(switchboard);
		    // show blog-entry/entries
	        prop.put("mode", 0); //viewing
	        if(pagename.equals("blog_default")) {
	        	//index all entries
	        	try {
	        		Iterator i = switchboard.blogDB.keys(false);
	        		String pageid;
	        		blogBoard.entry entry;
	        		int count = 0; //counts how many entries are shown to the user
	        		int start = post.getInt("start",0); //indicates from where entries should be shown
	        		int num   = post.getInt("num",20);  //indicates how many entries should be shown
	        		int nextstart = start+num;		//indicates the starting offset for next results
	        		while(i.hasNext()) {
	        			if(count >= num && num > 0)
	        				break;
	        			pageid = (String) i.next();
	        			if(0 < start--)
	        				continue;
	        			entry = switchboard.blogDB.read(pageid);
	        			prop.put("mode_entries_"+count+"_pageid",entry.key());
	        			prop.put("mode_entries_"+count+"_subject", entry.subject());
	        			prop.put("mode_entries_"+count+"_author", entry.author());
	        			prop.put("mode_entries_"+count+"_date", dateString(entry.date()));
	        			prop.put("mode_entries_"+count+"_page", wikiTransformer.transform(entry.page()));
	        			if(hasRights) {
	        				prop.put("mode_entries_"+count+"_admin", 1);
	        				prop.put("mode_entries_"+count+"_admin_pageid",entry.key());
	        			}
	        			else prop.put("mode_entries_"+count+"_admin", 0);
	        			++count;
	        		}
	        		prop.put("mode_entries",count);
	        		if(i.hasNext()) {
	        			prop.put("mode_moreentries",1); //more entries are availible
	        			prop.put("mode_moreentries_start",nextstart);
	        			prop.put("mode_moreentries_num",num);
	        		}
	        		else prop.put("moreentries",0);
	        	} catch (IOException e) {

	        	}
	        }
	        else {
	        	//only show 1 entry
	        	prop.put("mode_entries",1);
	        	prop.put("mode_entries_0_pageid", page.key());
	        	prop.put("mode_entries_0_subject", page.subject());
	        	prop.put("mode_entries_0_author", page.author());
	        	prop.put("mode_entries_0_date", dateString(page.date()));
	        	prop.put("mode_entries_0_page", wikiTransformer.transform(page.page()));
	        	if(hasRights) {
    				prop.put("mode_entries_0_admin", 1);
    				prop.put("mode_entries_0_admin_pageid",page.key());
    			}
	        }
		}

		// return rewrite properties
		return prop;
	}
}
