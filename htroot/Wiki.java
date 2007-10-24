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
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.data.diff;
import de.anomic.data.wikiBoard;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;

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

        final boolean authenticated = switchboard.adminAuthenticated(header) >= 2;
        final int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        prop.put("display", display);
        
        String access = switchboard.getConfig("WikiAccess", "admin");
        String pagename = post.get("page", "start");
        String ip = post.get("CLIENTIP", "127.0.0.1");
        String author = post.get("author", "anonymous");
        if (author.equals("anonymous")) {
            author = wikiBoard.guessAuthor(ip);
            if (author == null) {
                if (de.anomic.yacy.yacyCore.seedDB.mySeed() == null) author = "anonymous";
                else author = de.anomic.yacy.yacyCore.seedDB.mySeed().get("Name", "anonymous");
            }
        }
        
        if (post.containsKey("access")) {
            // only the administrator may change the access right
            if (!switchboard.verifyAuthentication(header, true)) {
                // check access right for admin
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            
            access = post.get("access", "admin");
            switchboard.setConfig("WikiAccess", access);
        }
        if (access.equals("admin")) prop.put("mode_access", "0");
        if (access.equals("all"))   prop.put("mode_access", "1");

        wikiBoard.entry page = switchboard.wikiDB.read(pagename);
        
        if (post.containsKey("submit")) {
            
            if ((access.equals("admin") && (!switchboard.verifyAuthentication(header, true)))) {
                // check access right for admin
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            
            // store a new page
            byte[] content;
            try {
                content = post.get("content", "").getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                content = post.get("content", "").getBytes();
            }
            wikiBoard.entry newEntry = switchboard.wikiDB.newEntry(pagename, author, ip, post.get("reason", "edit"), content);
            switchboard.wikiDB.write(newEntry);
            // create a news message
            HashMap map = new HashMap();
            map.put("page", pagename);
            map.put("author", author.replace(',', ' '));
            if (post.get("content", "").trim().length() > 0 && !page.page().equals(content))
                yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_WIKI_UPDATE, map));
            page = newEntry;
            prop.put("LOCATION", "/Wiki.html?page=" + pagename);
        }

        if (post.containsKey("edit")) {
            if ((access.equals("admin") && (!switchboard.verifyAuthentication(header, true)))) {
                // check access right for admin
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            
            // edit the page
            try {
                prop.put("mode", "1"); //edit
                prop.putHTML("mode_author", author);
                prop.putHTML("mode_page-code", new String(page.page(), "UTF-8"));
                prop.putHTML("mode_pagename", pagename);
            } catch (UnsupportedEncodingException e) {}
        }

        //contributed by [MN]
        else if (post.containsKey("preview")) {
            // preview the page
            prop.put("mode", "2");//preview
            prop.putHTML("mode_pagename", pagename);
            prop.putHTML("mode_author", author);
            prop.put("mode_date", dateString(new Date()));
            prop.putWiki("mode_page", post.get("content", ""));
            prop.putHTML("mode_page-code", post.get("content", ""));
        }
        //end contrib of [MN]

        else if (post.containsKey("index")) {
            // view an index
            prop.put("mode", "3"); //Index
            String subject;
            try {
                Iterator i = switchboard.wikiDB.keys(true);
                wikiBoard.entry entry;
                int count=0;
                while (i.hasNext()) {
                    subject = (String) i.next();
                    entry = switchboard.wikiDB.read(subject);
                    prop.putHTML("mode_pages_"+count+"_name",wikiBoard.webalize(subject));
                    prop.putHTML("mode_pages_"+count+"_subject", subject);
                    prop.put("mode_pages_"+count+"_date", dateString(entry.date()));
                    prop.putHTML("mode_pages_"+count+"_author", entry.author());
                    count++;
                }
                prop.put("mode_pages", count);
            } catch (IOException e) {
                prop.put("mode_error", "1"); //IO Error reading Wiki
                prop.putHTML("mode_error_message", e.getMessage());
            }
            prop.putHTML("mode_pagename", pagename);
        }
        
        else if (post.containsKey("diff")) {
            // Diff
            prop.put("mode", "4");
            prop.putHTML("mode_page", pagename);
            prop.putHTML("mode_error_page", pagename);
            
            try {
                Iterator it = switchboard.wikiDB.keysBkp(true);
                wikiBoard.entry entry;
                wikiBoard.entry oentry = null;
                wikiBoard.entry nentry = null;
                int count = 0;
                boolean oldselected = false, newselected = false;
                while (it.hasNext()) {
                    entry = switchboard.wikiDB.readBkp((String)it.next());
                    prop.put("mode_error_versions_" + count + "_date", wikiBoard.dateString(entry.date()));
                    prop.put("mode_error_versions_" + count + "_fdate", dateString(entry.date()));
                    if (wikiBoard.dateString(entry.date()).equals(post.get("old", null))) {
                        prop.put("mode_error_versions_" + count + "_oldselected", "1");
                        oentry = entry;
                        oldselected = true;
                    } else if (wikiBoard.dateString(entry.date()).equals(post.get("new", null))) {
                        prop.put("mode_error_versions_" + count + "_newselected", "1");
                        nentry = entry;
                        newselected = true;
                    }
                    count++;
                }
                count--;    // don't show current version
                
                if (!oldselected)   // select latest old entry
                    prop.put("mode_error_versions_" + (count - 1) + "_oldselected", "1");
                if (!newselected)   // select latest new entry (== current)
                    prop.put("mode_error_curselected", "1");
                
                if (count == 0) {
                    prop.put("mode_error", "2"); // no entries found
                } else {
                    prop.put("mode_error_versions", count);
                }
                
                entry = switchboard.wikiDB.read(pagename);
                if (entry != null) {
                    prop.put("mode_error_curdate", wikiBoard.dateString(entry.date()));
                    prop.put("mode_error_curfdate", dateString(entry.date()));
                }
                
                if (nentry == null) nentry = entry;
                if (post.containsKey("compare") && oentry != null && nentry != null) {
                    // TODO: split into paragraphs and compare them with the same diff-algo
                    diff diff = new diff(
                            new String(oentry.page(), "UTF-8"),
                            new String(nentry.page(), "UTF-8"), 3);
                    prop.put("mode_versioning_diff", de.anomic.data.diff.toHTML(new diff[] { diff }));
                    prop.put("mode_versioning", "1");
                } else if (post.containsKey("viewold") && oentry != null) {
                    prop.put("mode_versioning", "2");
                    prop.putHTML("mode_versioning_pagename", pagename);
                    prop.putHTML("mode_versioning_author", oentry.author());
                    prop.put("mode_versioning_date", dateString(oentry.date()));
                    prop.putWiki("mode_versioning_page", oentry.page());
                    prop.putHTML("mode_versioning_page-code", new String(oentry.page(), "UTF-8"));
                }
            } catch (IOException e) {
                prop.put("mode_error", "1"); //IO Error reading Wiki
                prop.putHTML("mode_error_message", e.getMessage());
            }
        }

        else {
            // show page
            prop.put("mode", "0"); //viewing
            prop.putHTML("mode_pagename", pagename);
            prop.putHTML("mode_author", page.author());
            prop.put("mode_date", dateString(page.date()));
            prop.putWiki("mode_page", page.page());

            prop.put("controls", "0");
            prop.putHTML("controls_pagename", pagename);
        }

        // return rewrite properties
        return prop;
    }
}
