// News.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 29.07.2005
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
// javac -classpath .:../classes Network.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;

public class News {
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        boolean overview = (post == null) || (post.get("page", "0").equals("0"));
        int tableID = (overview) ? -1 : Integer.parseInt(post.get("page", "0")) - 1;

        // execute commands
        if (post != null) {
            
            if ((post.containsKey("deletespecific")) && (tableID >= 0)) {
                if (switchboard.adminAuthenticated(header) < 2) {
                    prop.put("AUTHENTICATE", "admin log-in");
                    return prop; // this button needs authentication, force log-in
                }
                Enumeration e = post.keys();
                String check;
                String id;
                while (e.hasMoreElements()) {
                    check = (String) e.nextElement();
                    if ((check.startsWith("del_")) && (post.get(check, "off").equals("on"))) {
                        id = check.substring(4);
                        try {
                            yacyCore.newsPool.moveOff(tableID, id);
                        } catch (IOException ee) {ee.printStackTrace();}
                    }
                }
            }
            
            if ((post.containsKey("deleteall")) && (tableID >= 0)) {
                if (switchboard.adminAuthenticated(header) < 2) {
                    prop.put("AUTHENTICATE", "admin log-in");
                    return prop; // this button needs authentication, force log-in
                }
                try {
                    if ((tableID == yacyNewsPool.PROCESSED_DB) || (tableID == yacyNewsPool.PUBLISHED_DB)) {
                        yacyCore.newsPool.clear(tableID);
                    } else {
                        yacyCore.newsPool.moveOffAll(tableID);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        // generate properties for output
        if (overview) {
            // show overview
            prop.put("table", "0");
            prop.put("page", "0");
            prop.putNum("table_insize", yacyCore.newsPool.size(yacyNewsPool.INCOMING_DB));
            prop.putNum("table_prsize", yacyCore.newsPool.size(yacyNewsPool.PROCESSED_DB));
            prop.putNum("table_ousize", yacyCore.newsPool.size(yacyNewsPool.OUTGOING_DB));
            prop.putNum("table_pusize", yacyCore.newsPool.size(yacyNewsPool.PUBLISHED_DB));
        } else {
            // generate table
            prop.put("table", "1");
            prop.put("page", tableID + 1);
            prop.put("table_page", tableID + 1);
            
            if (yacyCore.seedDB == null) {
                
            } else {
                int maxCount = Math.min(1000, yacyCore.newsPool.size(tableID));
                Iterator recordIterator = yacyCore.newsPool.recordIterator(tableID, false);
                yacyNewsRecord record;
                yacySeed seed;
                int i = 0;
                while ((recordIterator.hasNext()) && (i < maxCount)) {
                    record = (yacyNewsRecord) recordIterator.next();
                    if (record == null) continue;
                    
                    seed = yacyCore.seedDB.getConnected(record.originator());
                    if (seed == null) seed = yacyCore.seedDB.getDisconnected(record.originator());
                    String category = record.category();
                    prop.put("table_list_" + i + "_id", record.id());
                    prop.putHTML("table_list_" + i + "_ori", (seed == null) ? record.originator() : seed.getName());
                    prop.put("table_list_" + i + "_cre", serverDate.shortSecondTime(record.created()));
                    prop.put("table_list_" + i + "_crerfcdate", httpc.dateString(record.created()));
                    prop.put("table_list_" + i + "_cat", category);
                    prop.put("table_list_" + i + "_rec", (record.received() == null) ? "-" : serverDate.shortSecondTime(record.received()));
                    prop.put("table_list_" + i + "_dis", record.distributed());
                    
                    Map attributeMap = record.attributes();
                    prop.putHTML("table_list_" + i + "_att", attributeMap.toString());
                    int j = 0;
                    if (attributeMap.size() > 0) {
	                    Iterator attributeKeys = attributeMap.keySet().iterator();
	                    while (attributeKeys.hasNext()) {
	                    	String key = (String) attributeKeys.next();
	                    	String value = (String) attributeMap.get(key);
	                    	prop.put("table_list_" + i + "_attributes_" + j + "_name",key);
	                    	prop.putHTML("table_list_" + i + "_attributes_" + j + "_value",value);
	                    	j++;
	                    }
                    }
                    prop.put("table_list_" + i + "_attributes", j);
                                        
                    // generating link / title / description (taken over from Surftips.java)
                    String link, title, description;
                    if (category.equals(yacyNewsPool.CATEGORY_CRAWL_START)) {
                    	link = record.attribute("startURL", "");
                    	title = (record.attribute("intention", "").length() == 0) ? link : record.attribute("intention", "");
                    	description = "Crawl Start Point";
                    } else if (category.equals(yacyNewsPool.CATEGORY_PROFILE_UPDATE)) {
                    	link = record.attribute("homepage", "");
                    	title = "Home Page of " + record.attribute("nickname", "");
                    	description = "Profile Update";
                    } else if (category.equals(yacyNewsPool.CATEGORY_BOOKMARK_ADD)) {
                    	link = record.attribute("url", "");
                    	title = record.attribute("title", "");
                    	description = "Bookmark: " + record.attribute("description", "");
                    } else if (category.equals(yacyNewsPool.CATEGORY_SURFTIPP_ADD)) {
                    	link = record.attribute("url", "");
                    	title = record.attribute("title", "");
                    	description = "Surf Tipp: " + record.attribute("description", "");
                    } else if (category.equals(yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD)) {
                    	link = record.attribute("url", "");
                    	title = record.attribute("title", "");
                    	description = record.attribute("url", "");
                    } else if (category.equals(yacyNewsPool.CATEGORY_WIKI_UPDATE)) {
                    	link = (seed==null)?"":"http://" + seed.getPublicAddress() + "/Wiki.html?page=" + record.attribute("page", "");
                    	title = record.attribute("author", "Anonymous") + ": " + record.attribute("page", "");
                    	description = "Wiki Update: " + record.attribute("description", "");
                    } else if (category.equals(yacyNewsPool.CATEGORY_BLOG_ADD)) {
                    	link = (seed==null)?"":"http://" + seed.getPublicAddress() + "/Blog.html?page=" + record.attribute("page", "");
                    	title = record.attribute("author", "Anonymous") + ": " + record.attribute("page", "");
                    	description = "Blog Entry: " + record.attribute("subject", "");
                    } else {
                    	link = "";             
                    	title = ""; 
                    	description = "";
                    }
                    prop.put("table_list_" + i + "_link", link);
                    prop.putHTML("table_list_" + i + "_title", title);
                    prop.putHTML("table_list_" + i + "_description", description);
                    
                    i++;
                }
                prop.put("table_list", i);
            }
        }
        
        // adding the peer address
        prop.put("address",yacyCore.seedDB.mySeed().getPublicAddress());
        
        // return rewrite properties
        return prop;
    }
}
