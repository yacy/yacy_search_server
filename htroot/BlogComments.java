// BlogComments.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Jan Sandbrink
//
//$LastChangedDate$
//$LastChangedRevision$
//$LastChangedBy$
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

// You must compile this file with
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.BlogBoard;
import net.yacy.data.BlogBoardComments;
import net.yacy.data.MessageBoard;
import net.yacy.data.UserDB;
import net.yacy.data.BlogBoard.BlogEntry;
import net.yacy.peers.Network;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

import com.google.common.io.Files;


public class BlogComments {

    private static final String DEFAULT_PAGE = "blog_default";

    public static String dateString(final Date date) {
        return Blog.dateString(date);
    }

    public static serverObjects respond(final RequestHeader header, serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        boolean hasRights = sb.verifyAuthentication(header);

        prop.put("mode_admin", hasRights ? "1" : "0");

        if (post == null) {
            post = new serverObjects();
            post.put("page", "blog_default");
        }

        if (!hasRights) {
            final UserDB.Entry userentry = sb.userDB.proxyAuth(header.get(RequestHeader.AUTHORIZATION, "xxxxxx"));
            if (userentry != null && userentry.hasRight(UserDB.AccessRight.BLOG_RIGHT)) {
                hasRights = true;
            } else if (post.containsKey("login")) {
                //opens login window if login link is clicked
            	prop.authenticationRequired();
            }
        }

        String pagename = post.get("page", DEFAULT_PAGE);
        final String ip = post.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, Domains.LOCALHOST);

        String strAuthor = post.get("author", "anonymous");

        if ("anonymous".equals(strAuthor)) {
            strAuthor = sb.blogDB.guessAuthor(ip);

            if (strAuthor == null || strAuthor.isEmpty()) {
                if (sb.peers.mySeed() == null) {
                    strAuthor = "anonymous";
                } else {
                    strAuthor = sb.peers.mySeed().get(Seed.NAME, "anonymous");
                }
            }
        }

        byte[] author;
        author = UTF8.getBytes(strAuthor);

        final BlogBoard.BlogEntry page = sb.blogDB.readBlogEntry(pagename); //maybe "if(page == null)"
        final boolean pageExists = sb.blogDB.contains(pagename);

        // comments not allowed
        prop.put("mode_allow", (page.getCommentMode() == 0) ? 0 : 1);

        if (post.containsKey("submit") && page.getCommentMode() != 0 && pageExists) {
            // store a new/edited blog-entry
            byte[] content;
            if (!"".equals(post.get("content", ""))) {
                if ("".equals(post.get("subject", ""))) {
                    post.putHTML("subject", "no title");
                }
                content = UTF8.getBytes(post.get("content", ""));

                final Date date = null;

                //set name for new entry or date for old entry
                final String StrSubject = post.get("subject", "");
                byte[] subject;
                subject = UTF8.getBytes(StrSubject);
                final String commentID = String.valueOf(System.currentTimeMillis());
                final BlogEntry blogEntry = sb.blogDB.readBlogEntry(pagename);
                blogEntry.addComment(commentID);
                sb.blogDB.writeBlogEntry(blogEntry);
                sb.blogCommentDB.write(sb.blogCommentDB.newEntry(commentID, subject, author, ip, date, content));
                prop.putHTML(serverObjects.ACTION_LOCATION,"BlogComments.html?page=" + pagename);

                MessageBoard.entry msgEntry = sb.messageDB.newEntry(
                        "blogComment",
                        strAuthor,
                        sb.peers.mySeed().hash,
                        sb.peers.mySeed().getName(), sb.peers.mySeed().hash,
                        "new blog comment: " + UTF8.String(blogEntry.getSubject()), content);
                sb.messageDB.write(msgEntry);

                messageForwardingViaEmail(sb, msgEntry);

                // finally write notification
                final File notifierSource = new File(sb.getAppPath(), sb.getConfig("htRootPath","htroot") + "/env/grafics/message.gif");
                final File notifierDest   = new File(sb.getDataPath("htDocsPath", "DATA/HTDOCS"), "notifier.gif");
                try {
                    Files.copy(notifierSource, notifierDest);
                } catch (final IOException e) {
                    ConcurrentLog.severe("MESSAGE", "NEW MESSAGE ARRIVED! (error: " + e.getMessage() + ")");

                }
            }
        }

        if (hasRights && post.containsKey("delete") && post.containsKey("page") &&
                post.containsKey("comment") && page.removeComment(post.get("comment"))) {
            sb.blogCommentDB.delete(post.get("comment"));
        }

        if (hasRights && post.containsKey("allow") && post.containsKey("page") && post.containsKey("comment")) {
            final BlogBoardComments.CommentEntry entry = sb.blogCommentDB.read(post.get("comment"));
            entry.allow();
            sb.blogCommentDB.write(entry);
        }

        if (post.containsKey("preview") && page.getCommentMode() != 0) {
            //preview the page
            prop.put("mode", "1");//preview
            prop.putHTML("mode_pageid", pagename);
            prop.putHTML("mode_allow_pageid", pagename);
            prop.putHTML("mode_author", UTF8.String(author));
            prop.putHTML("mode_allow_author", UTF8.String(author));
            prop.putHTML("mode_subject", post.get("subject",""));
            prop.put("mode_date", dateString(new Date()));
            prop.putWiki(sb.peers.mySeed().getClusterAddress(), "mode_page", post.get("content", ""));
            prop.put("mode_page-code", post.get("content", ""));
        } else {
            // show blog-entry/entries
            prop.put("mode", "0"); //viewing
            if("blog_default".equals(pagename)) {
                prop.put(serverObjects.ACTION_LOCATION,"Blog.html");
            } else {
                //show 1 blog entry
                prop.put("mode_pageid", page.getKey());
                prop.putHTML("mode_allow_pageid", pagename);
                prop.putHTML("mode_subject", UTF8.String(page.getSubject()));
                prop.putHTML("mode_author", UTF8.String(page.getAuthor()));
                prop.putHTML("mode_allow_author", UTF8.String(author));
                prop.put("mode_comments", page.getCommentsSize());
                prop.put("mode_date", dateString(page.getDate()));
                prop.putWiki(sb.peers.mySeed().getClusterAddress(), "mode_page", page.getPage());
                if (hasRights) {
                    prop.put("mode_admin", "1");
                    prop.put("mode_admin_pageid", page.getKey());
                }
                final Iterator<String> i = page.getComments().iterator();
                final int commentMode = page.getCommentMode();
                String pageid;
                BlogBoardComments.CommentEntry entry;
                final boolean xml = post.containsKey("xml");
                int count = 0; //counts how many entries are shown to the user
                int start = post.getInt("start",0); //indicates from where entries should be shown
                int num   = post.getInt("num",10);  //indicates how many entries should be shown

                if (xml) {
                    num = 0;
                }
                if (start < 0) {
                    start = 0;
                }

                final int nextstart = start + num;  //indicates the starting offset for next results
                int prevstart = start - num;        //indicates the starting offset for previous results
                while (i.hasNext() && count < num) {

                    pageid = i.next();

                    if(start > 0) {
                        start--;
                        continue;
                    }

                    entry = sb.blogCommentDB.read(pageid);

                    if (commentMode == 2 && !hasRights && !entry.isAllowed()) {
                        continue;
                    }

                    prop.put("mode", "0");
                    prop.put("mode_entries_"+count+"_pageid", entry.getKey());
                    if (!xml) {
                        prop.putHTML("mode_entries_"+count+"_subject", UTF8.String(entry.getSubject()));
                        prop.putHTML("mode_entries_"+count+"_author", UTF8.String(entry.getAuthor()));
                        prop.putWiki(sb.peers.mySeed().getClusterAddress(), "mode_entries_"+count+"_page", entry.getPage());
                    } else {
                        prop.putHTML("mode_entries_"+count+"_subject", UTF8.String(entry.getSubject()));
                        prop.putHTML("mode_entries_"+count+"_author", UTF8.String(entry.getAuthor()));
                        prop.put("mode_entries_"+count+"_page", entry.getPage());
                        prop.put("mode_entries_"+count+"_timestamp", entry.getTimestamp());
                    }
                    prop.put("mode_entries_"+count+"_date", dateString(entry.getDate()));
                    prop.put("mode_entries_"+count+"_ip", entry.getIp());
                    if(hasRights) {
                        prop.put("mode_entries_"+count+"_admin", "1");
                        prop.put("mode_entries_"+count+"_admin_pageid", page.getKey());
                        prop.put("mode_entries_"+count+"_admin_commentid", pageid);
                        if(page.getCommentMode() == 2 && !entry.isAllowed()) {
                            prop.put("mode_entries_"+count+"_admin_moderate", "1");
                            prop.put("mode_entries_"+count+"_admin_moderate_pageid", page.getKey());
                            prop.put("mode_entries_"+count+"_admin_moderate_commentid", pageid);

                        }
                    }
                    else prop.put("mode_entries_"+count+"_admin", 0);
                    ++count;
                }
                prop.put ("mode_entries", count);
                if (i.hasNext()) {
                    prop.put("mode_moreentries", "1"); //more entries are availible
                    prop.put("mode_moreentries_start", nextstart);
                    prop.put("mode_moreentries_num", num);
                    prop.put("mode_moreentries_pageid", page.getKey());
                }
                else prop.put("mode_moreentries", "0");
                if (start > 1) {
                    prop.put("mode_preventries", "1");
                    if (prevstart < 0) prevstart = 0;
                    prop.put("mode_preventries_start", prevstart);
                    prop.put("mode_preventries_num", num);
                    prop.put("mode_preventries_pageid", page.getKey());
                } else prop.put("mode_preventries", "0");
            }
        }

        // return rewrite properties
        return prop;
    }

    private static void messageForwardingViaEmail(final Switchboard sb, final MessageBoard.entry msgEntry) {
        try {
            if (!sb.getConfigBool("msgForwardingEnabled",false)) {
                return;
            }

            // get the recipient address
            final String sendMailTo = sb.getConfig("msgForwardingTo","root@localhost").trim();

            // get the sendmail configuration
            final String sendMailStr = sb.getConfig("msgForwardingCmd","/usr/bin/sendmail")+" "+sendMailTo;
            final String[] sendMail = sendMailStr.trim().split(" ");

            // build the message text
            final StringBuilder emailTxt = new StringBuilder();
            emailTxt.append("To: ")
            .append(sendMailTo)
            .append("\nFrom: ")
            .append("yacy@")
            .append(sb.peers.mySeed().getName())
            .append("\nSubject: [YaCy] ")
            .append(msgEntry.subject().replace('\n', ' '))
            .append("\nDate: ")
            .append(msgEntry.date())
            .append("\n")
            .append("\nMessage from: ")
            .append(msgEntry.author())
            .append("/")
            .append(msgEntry.authorHash())
            .append("\nMessage to:   ")
            .append(msgEntry.recipient())
            .append("/")
            .append(msgEntry.recipientHash())
            .append("\nCategory:     ")
            .append(msgEntry.category())
            .append("\n===================================================================\n")
            .append(UTF8.String(msgEntry.message()));

            final Process process=Runtime.getRuntime().exec(sendMail);
            final PrintWriter email = new PrintWriter(process.getOutputStream());
            email.print(new String(emailTxt));
            email.close();
        } catch (final Exception e) {
            Network.log.warn("message: message forwarding via email failed. ",e);
        }
    }
}
