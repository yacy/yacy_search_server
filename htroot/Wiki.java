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

import de.anomic.data.wikiBoard;
import de.anomic.data.wikiCode;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
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

        String access = switchboard.getConfig("WikiAccess", "admin");
        String pagename = post.get("page", "start");
        String ip = post.get("CLIENTIP", "127.0.0.1");
        String author = post.get("author", "anonymous");
        if (author.equals("anonymous")) {
            author = wikiBoard.guessAuthor(ip);
            if (author == null) {
                if (de.anomic.yacy.yacyCore.seedDB.mySeed == null) author = "anonymous";
                else author = de.anomic.yacy.yacyCore.seedDB.mySeed.get("Name", "anonymous");
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
        if (access.equals("admin")) prop.put("mode_access", 0);
        if (access.equals("all"))   prop.put("mode_access", 1);
        
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
            switchboard.wikiDB.write(switchboard.wikiDB.newEntry(pagename, author, ip, post.get("reason", "edit"), content));
            // create a news message
            HashMap map = new HashMap();
            map.put("page", pagename);
            map.put("author", author.replace(',', ' '));
            yacyCore.newsPool.publishMyNews(new yacyNewsRecord("wiki_upd", map));
        }

        wikiBoard.entry page = switchboard.wikiDB.read(pagename);

        if (post.containsKey("edit")) {
            if ((access.equals("admin") && (!switchboard.verifyAuthentication(header, true)))) {
                // check access right for admin
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            
            // edit the page
            try {
                prop.put("mode", 1); //edit
                prop.put("mode_author", author);
                prop.put("mode_page-code", new String(page.page(), "UTF-8").replaceAll("<","&lt;").replaceAll(">","&gt;"));
                prop.put("mode_pagename", pagename);
            } catch (UnsupportedEncodingException e) {}
        }

        //contributed by [MN]
        else if (post.containsKey("preview")) {
            // preview the page
            wikiCode wikiTransformer=new wikiCode(switchboard);
            prop.put("mode", 2);//preview
            prop.put("mode_pagename", pagename);
            prop.put("mode_author", author);
            prop.put("mode_date", dateString(new Date()));
            prop.putASIS("mode_page", wikiTransformer.transform(post.get("content", "")));
            prop.put("mode_page-code", post.get("content", "").replaceAll("<","&lt;").replaceAll(">","&gt;"));
        }
        //end contrib of [MN]

        else if (post.containsKey("index")) {
            // view an index
            prop.put("mode", 3); //Index
            String subject;
            try {
                Iterator i = switchboard.wikiDB.keys(true);
                wikiBoard.entry entry;
                int count=0;
                while (i.hasNext()) {
                    subject = (String) i.next();
                    entry = switchboard.wikiDB.read(subject);
                    prop.put("mode_pages_"+count+"_name",wikiBoard.webalize(subject));
                    prop.put("mode_pages_"+count+"_subject", subject);
                    prop.put("mode_pages_"+count+"_date", dateString(entry.date()));
                    prop.put("mode_pages_"+count+"_author", entry.author());
                    count++;
                }
                prop.put("mode_pages", count);
            } catch (IOException e) {
                prop.put("mode_error", 1); //IO Error reading Wiki
                prop.put("mode_error_message", e.getMessage());
            }
            prop.put("mode_pagename", pagename);
        }

        else {
            wikiCode wikiTransformer=new wikiCode(switchboard);
            // show page
            prop.put("mode", 0); //viewing
            prop.put("mode_pagename", pagename);
            prop.put("mode_author", page.author());
            prop.put("mode_date", dateString(page.date()));
            prop.putASIS("mode_page", wikiTransformer.transform(page.page()));

            prop.put("controls", 0);
            prop.put("controls_pagename", pagename);
        }

        // return rewrite properties
        return prop;
    }


}
