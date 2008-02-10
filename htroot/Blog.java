// Blog.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Jan Sandbrink
// Contains contributions from Marc Nause [MN]
// last change: 06.05.2006
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
// javac -classpath .:../classes Blog.java
// if the shell's current path is HTROOT

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import de.anomic.data.blogBoard;
import de.anomic.data.userDB;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;

public class Blog {

    private static final String DEFAULT_PAGE = "blog_default";

        private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        // TODO: make userdefined date/time-strings (localisation)

    public static String dateString(Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        blogBoard.BlogEntry page = null;

        boolean hasRights = switchboard.verifyAuthentication(header, true);
        
        //final int display = (hasRights || post == null) ? 1 : post.getInt("display", 0);
        //prop.put("display", display);   
        prop.put("display", 1); // Fixed to 1

        
        final boolean xml = ((String)header.get(httpHeader.CONNECTION_PROP_PATH)).endsWith(".xml");
        final String address = yacyCore.seedDB.mySeed().getPublicAddress();

        if(hasRights) {
            prop.put("mode_admin", "1");
        } else {
            prop.put("mode_admin", "0");
        }

        if (post == null) {
            prop.putHTML("peername", yacyCore.seedDB.mySeed().getName());
            prop.put("address", address);
            return putBlogDefault(prop, switchboard, address, 0, 10, hasRights, xml);
        }

        final int start = post.getInt("start",0); //indicates from where entries should be shown
        final int num   = post.getInt("num",10);  //indicates how many entries should be shown

        if(!hasRights){
            final userDB.Entry userentry = switchboard.userDB.proxyAuth((String)header.get("Authorization", "xxxxxx"));
            if(userentry != null && userentry.hasRight(userDB.Entry.BLOG_RIGHT)){
                hasRights=true;
            } else if(post.containsKey("login")) {
                //opens login window if login link is clicked - contrib [MN]
                prop.put("AUTHENTICATE","admin log-in");
            }
        }

        String pagename = post.get("page", DEFAULT_PAGE);
        final String ip = (String)header.get(httpHeader.CONNECTION_PROP_CLIENTIP, "127.0.0.1");

        String StrAuthor = post.get("author", "");

        if (StrAuthor.equals("anonymous")) {
            StrAuthor = switchboard.blogDB.guessAuthor(ip);

            if (StrAuthor == null || StrAuthor.length() == 0) {
                if (de.anomic.yacy.yacyCore.seedDB.mySeed() == null) {
                    StrAuthor = "anonymous";
                } else {
                    StrAuthor = de.anomic.yacy.yacyCore.seedDB.mySeed().get("Name", "anonymous");
                }
            }
        }

        byte[] author;
        try {
            author = StrAuthor.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            author = StrAuthor.getBytes();
        }

        if(hasRights && post.containsKey("delete") && post.get("delete").equals("sure")) {
            page = switchboard.blogDB.readBlogEntry(pagename);
            final Iterator<String> i = page.getComments().iterator();
            while(i.hasNext()) {
                switchboard.blogCommentDB.delete(i.next());
            }
            switchboard.blogDB.deleteBlogEntry(pagename);
            pagename = DEFAULT_PAGE;
        }

        if (post.containsKey("discard")) {
            pagename = DEFAULT_PAGE;
        }

        if (post.containsKey("submit") && (hasRights)) {
            // store a new/edited blog-entry
            byte[] content;
            try {
                content = post.get("content", "").getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                content = post.get("content", "").getBytes();
            }

            Date date = null;
            ArrayList<String> comments = null;

            //set name for new entry or date for old entry
            if(pagename.equals(DEFAULT_PAGE)) {
                pagename = String.valueOf(System.currentTimeMillis());
            } else {
                page = switchboard.blogDB.readBlogEntry(pagename);
                comments = page.getComments();
                date = page.getDate();
            }
            final String commentMode = post.get("commentMode", "1");
            final String StrSubject = post.get("subject", "");
            byte[] subject;
            try {
                subject = StrSubject.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                subject = StrSubject.getBytes();
            }

            switchboard.blogDB.writeBlogEntry(switchboard.blogDB.newEntry(pagename, subject, author, ip, date, content, comments, commentMode));

            // create a news message
            final HashMap<String, String> map = new HashMap<String, String>();
            map.put("page", pagename);
            map.put("subject", StrSubject.replace(',', ' '));
            map.put("author", StrAuthor.replace(',', ' '));
            yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_BLOG_ADD, map));
        }

        page = switchboard.blogDB.readBlogEntry(pagename); //maybe "if(page == null)"

        if (post.containsKey("edit")) {
            //edit an entry
            if(hasRights) {
                try {
                    prop.put("mode", "1"); //edit
                    prop.put("mode_commentMode", page.getCommentMode());
                    prop.putHTML("mode_author", new String(page.getAuthor(),"UTF-8"), xml);
                    prop.put("mode_pageid", page.getKey());
                    prop.putHTML("mode_subject", new String(page.getSubject(), "UTF-8"), xml);
                    prop.put("mode_page-code", new String(page.getPage(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {}
            }
            else {
                prop.put("mode", "3"); //access denied (no rights)
            }
        }
        else if(post.containsKey("preview")) {
            //preview the page
            if(hasRights) {
                prop.put("mode", "2");//preview
                prop.put("mode_commentMode", post.getInt("commentMode", 1));
                prop.putHTML("mode_pageid", pagename, xml);
                try {
                    prop.putHTML("mode_author", new String(author, "UTF-8"), xml);
                } catch (UnsupportedEncodingException e) {
                    prop.putHTML("mode_author", new String(author), xml);
                }
                prop.putHTML("mode_subject", post.get("subject",""), xml);
                prop.put("mode_date", dateString(new Date()));
                prop.putWiki("mode_page", post.get("content", ""));
                prop.putHTML("mode_page-code", post.get("content", ""), xml);
            }
            else {
                prop.put("mode", "3"); //access denied (no rights)
            }
        }
        else if(post.get("delete", "").equals("try")) {
            if(hasRights) {
                prop.put("mode", "4");
                prop.put("mode_pageid", pagename);
                try {
                    prop.putHTML("mode_author",new String(page.getAuthor(), "UTF-8"), xml);
                } catch (UnsupportedEncodingException e) {
                    prop.putHTML("mode_author",new String(page.getAuthor()), xml);
                }
                try {
                    prop.putHTML("mode_subject",new String(page.getSubject(),"UTF-8"), xml);
                } catch (UnsupportedEncodingException e) {
                    prop.putHTML("mode_subject",new String(page.getSubject()), xml);
                }
            }
            else prop.put("mode", "3"); //access denied (no rights)
        }
        else if (post.containsKey("import")) {
            prop.put("mode", "5");
            prop.put("mode_state", "0");
        }
        else if (post.containsKey("xmlfile")) {
            prop.put("mode", "5");
            if(switchboard.blogDB.importXML(post.get("xmlfile$file"))) {
                prop.put("mode_state", "1");
            }
            else {
                prop.put("mode_state", "2");
            }
        }
        else {
            // show blog-entry/entries
            prop.put("mode", "0"); //viewing
            if(pagename.equals(DEFAULT_PAGE)) {
                // XXX: where are "peername" and "address" used in the template?
                // XXX: "clientname" is already set to the peername, no need for a new setting
                prop.putHTML("peername", yacyCore.seedDB.mySeed().getName(), xml);
                prop.put("address", address);
                //index all entries
                putBlogDefault(prop, switchboard, address, start, num, hasRights, xml);
            }
            else {
                //only show 1 entry
                prop.put("mode_entries", "1");
                putBlogEntry(prop, page, address, 0, hasRights, xml);
            }
        }

        // return rewrite properties
        return prop;
    }

    private static serverObjects putBlogDefault(
            final serverObjects prop,
            final plasmaSwitchboard switchboard,
            final String address,
            int start,
            int num,
            final boolean hasRights,
            final boolean xml) 
    {
            //final Iterator<String> i = switchboard.blogDB.keys(false);
            final Iterator<String> i = switchboard.blogDB.getBlogIterator(false);
            String pageid;
            int count = 0;                        //counts how many entries are shown to the user
            if(xml) num = 0;
            final int nextstart = start+num;      //indicates the starting offset for next results
            int prevstart = start-num;            //indicates the starting offset for previous results
            boolean prev = false;                 //indicates if there were previous comments to the ones that are dispalyed
            if (start > 0) prev = true;
            while(i.hasNext() && (num == 0 || num > count)) {
                pageid = i.next();
                if(0 < start--) continue;
                putBlogEntry(
                        prop,
                        switchboard.blogDB.readBlogEntry(pageid),
                        address,
                        count++,
                        hasRights,
                        xml);
            }
            prop.put("mode_entries", count);

            if(i.hasNext()) {
                prop.put("mode_moreentries", "1"); //more entries are availible
                prop.put("mode_moreentries_start", nextstart);
                prop.put("mode_moreentries_num", num);
            } else {
                prop.put("moreentries", "0");
            }
            
            if(prev) {
                prop.put("mode_preventries", "1");
                if (prevstart < 0) prevstart = 0;
                prop.put("mode_preventries_start", prevstart);
                prop.put("mode_preventries_num", num);
            } else prop.put("mode_preventries", "0");
            
            
        return prop;
    }

    private static serverObjects putBlogEntry(
            final serverObjects prop,
            final blogBoard.BlogEntry entry,
            final String address,
            final int number,
            final boolean hasRights,
            final boolean xml) 
    {
        // subject
        try {
            prop.putHTML("mode_entries_" + number + "_subject", new String(entry.getSubject(),"UTF-8"), xml);
        } catch (UnsupportedEncodingException e) {
            prop.putHTML("mode_entries_" + number + "_subject", new String(entry.getSubject()), xml);
        }

        // author
        try {
            prop.putHTML("mode_entries_" + number + "_author", new String(entry.getAuthor(),"UTF-8"), xml);
        } catch (UnsupportedEncodingException e) {
            prop.putHTML("mode_entries_" + number + "_author", new String(entry.getAuthor()), xml);
        }

        // comments
        if(entry.getCommentMode() == 0) {
            prop.put("mode_entries_" + number + "_commentsactive", "0");
        } else {
            prop.put("mode_entries_" + number + "_commentsactive", "1");
            prop.put("mode_entries_" + number + "_commentsactive_pageid", entry.getKey());
            prop.put("mode_entries_" + number + "_commentsactive_address", address);
            prop.put("mode_entries_" + number + "_commentsactive_comments", entry.getCommentsSize());
        }

        prop.put("mode_entries_" + number + "_date", dateString(entry.getDate()));
        prop.put("mode_entries_" + number + "_rfc822date", httpc.dateString(entry.getDate()));
        prop.put("mode_entries_" + number + "_pageid", entry.getKey());
        prop.put("mode_entries_" + number + "_address", address);
        prop.put("mode_entries_" + number + "_ip", entry.getIp());

        if(xml) {
            prop.put("mode_entries_" + number + "_page", entry.getPage());
            prop.put("mode_entries_" + number + "_timestamp", entry.getTimestamp());
        } else {
            prop.putWiki("mode_entries_" + number + "_page", entry.getPage());
        }

        if(hasRights) {
            prop.put("mode_entries_" + number + "_admin", "1");
            prop.put("mode_entries_" + number + "_admin_pageid",entry.getKey());
        } else {
            prop.put("mode_entries_" + number + "_admin", "0");
        }

        return prop;
    }
}
