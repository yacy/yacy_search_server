// Blog.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
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

// You must compile this file with
// javac -classpath .:../Classes Blacklist_p.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import de.anomic.data.blogBoard;
import de.anomic.data.blogBoardComments;
import de.anomic.data.messageBoard;
import de.anomic.data.userDB;
import de.anomic.data.blogBoard.BlogEntry;
import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;

public class BlogComments {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    // TODO: make userdefined date/time-strings (localisation)

    public static String dateString(final Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(final httpRequestHeader header, serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();
        blogBoard.BlogEntry page = null;
        boolean hasRights = sb.verifyAuthentication(header, true);

        if (hasRights) prop.put("mode_admin", "1");
        else prop.put("mode_admin", "0");

        if (post == null) {
            post = new serverObjects();
            post.put("page", "blog_default");
        }

        if(!hasRights){
            final userDB.Entry userentry = sb.userDB.proxyAuth((String)header.get(httpRequestHeader.AUTHORIZATION, "xxxxxx"));
            if(userentry != null && userentry.hasRight(userDB.Entry.BLOG_RIGHT)){
                hasRights=true;
            }
            //opens login window if login link is clicked - contrib [MN]
            else if(post.containsKey("login")){
                prop.put("AUTHENTICATE","admin log-in");
            }
        }

        final String pagename = post.get("page", "blog_default");
        final String ip = post.get(httpRequestHeader.CONNECTION_PROP_CLIENTIP, "127.0.0.1");

        String StrAuthor = post.get("author", "anonymous");

        if (StrAuthor.equals("anonymous")) {
            StrAuthor = sb.blogDB.guessAuthor(ip);

            if (StrAuthor == null || StrAuthor.length() == 0) {
                if (sb.webIndex.seedDB.mySeed() == null) {
                    StrAuthor = "anonymous";
                }
                else {
                    StrAuthor = sb.webIndex.seedDB.mySeed().get("Name", "anonymous");
                }
            }
        }

        byte[] author;
        try {
            author = StrAuthor.getBytes("UTF-8");
        } catch (final UnsupportedEncodingException e) {
            author = StrAuthor.getBytes();
        }

        page = sb.blogDB.readBlogEntry(pagename); //maybe "if(page == null)"
        
        // comments not allowed
        if (page.getCommentMode() == 0) {
            prop.put("mode_allow", 0);
        } else {
            prop.put("mode_allow", 1);
        } 

        if (post.containsKey("submit") && page.getCommentMode() != 0) {
            // store a new/edited blog-entry
            byte[] content;
            if(!post.get("content", "").equals(""))
            {
                if(post.get("subject", "").equals("")) post.putHTML("subject", "no title");
                try {
                    content = post.get("content", "").getBytes("UTF-8");
                } catch (final UnsupportedEncodingException e) {
                    content = post.get("content", "").getBytes();
                }

                final Date date = null;

                //set name for new entry or date for old entry
                final String StrSubject = post.get("subject", "");
                byte[] subject;
                try {
                    subject = StrSubject.getBytes("UTF-8");
                } catch (final UnsupportedEncodingException e) {
                    subject = StrSubject.getBytes();
                }
                final String commentID = String.valueOf(System.currentTimeMillis());
                final BlogEntry blogEntry = sb.blogDB.readBlogEntry(pagename);
                blogEntry.addComment(commentID);
                sb.blogDB.writeBlogEntry(blogEntry);
                sb.blogCommentDB.write(sb.blogCommentDB.newEntry(commentID, subject, author, ip, date, content));
                prop.put("LOCATION","BlogComments.html?page=" + pagename);

                messageBoard.entry msgEntry = null;
                try {
                    sb.messageDB.write(msgEntry = sb.messageDB.newEntry(
                            "blogComment",
                            StrAuthor,
                            sb.webIndex.seedDB.mySeed().hash,
                            sb.webIndex.seedDB.mySeed().getName(), sb.webIndex.seedDB.mySeed().hash,
                            "new blog comment: " + new String(blogEntry.getSubject(),"UTF-8"), content));
                } catch (final UnsupportedEncodingException e1) {
                    sb.messageDB.write(msgEntry = sb.messageDB.newEntry(
                            "blogComment",
                            StrAuthor,
                            sb.webIndex.seedDB.mySeed().hash,
                            sb.webIndex.seedDB.mySeed().getName(), sb.webIndex.seedDB.mySeed().hash,
                            "new blog comment: " + new String(blogEntry.getSubject()), content));
                }

                messageForwardingViaEmail(sb, msgEntry);

                // finally write notification
                final File notifierSource = new File(sb.getRootPath(), sb.getConfig("htRootPath","htroot") + "/env/grafics/message.gif");
                final File notifierDest   = new File(sb.getConfigPath("htDocsPath", "DATA/HTDOCS"), "notifier.gif");
                try {
                    serverFileUtils.copy(notifierSource, notifierDest);
                } catch (final IOException e) {
                    serverLog.logSevere("MESSAGE", "NEW MESSAGE ARRIVED! (error: " + e.getMessage() + ")");

                }
            }
        }

        if(hasRights && post.containsKey("delete") && post.containsKey("page") && post.containsKey("comment")) {
            if(page.removeComment(post.get("comment"))) {
                sb.blogCommentDB.delete(post.get("comment"));
            }
        }

        if(hasRights && post.containsKey("allow") && post.containsKey("page") && post.containsKey("comment")) {
            final blogBoardComments.CommentEntry entry = sb.blogCommentDB.read(post.get("comment"));
            entry.allow();
            sb.blogCommentDB.write(entry);
        }

        if(post.containsKey("preview") && page.getCommentMode() != 0) {
            //preview the page
            prop.put("mode", "1");//preview
            prop.put("mode_pageid", pagename);
            prop.put("mode_allow_pageid", pagename);
            try {
                prop.putHTML("mode_author", new String(author, "UTF-8"));
                prop.putHTML("mode_allow_author", new String(author, "UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                prop.putHTML("mode_author", new String(author));
                prop.putHTML("mode_allow_author", new String(author));
            }
            prop.putHTML("mode_subject", post.get("subject",""));
            prop.put("mode_date", dateString(new Date()));
            prop.putWiki("mode_page", post.get("content", ""));
            prop.put("mode_page-code", post.get("content", ""));
        } else {
            // show blog-entry/entries
            prop.put("mode", "0"); //viewing
            if(pagename.equals("blog_default")) {
                prop.put("LOCATION","Blog.html");
            } else {
                //show 1 blog entry
                prop.put("mode_pageid", page.getKey());
                prop.put("mode_allow_pageid", pagename);
                try {
                    prop.putHTML("mode_subject", new String(page.getSubject(),"UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    prop.putHTML("mode_subject", new String(page.getSubject()));
                }
                try {
                    prop.putHTML("mode_author", new String(page.getAuthor(),"UTF-8"));
                    prop.putHTML("mode_allow_author", new String(author, "UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    prop.putHTML("mode_author", new String(page.getAuthor()));
                    prop.putHTML("mode_allow_author", new String(author));
                }
                prop.put("mode_comments", page.getCommentsSize());
                prop.put("mode_date", dateString(page.getDate()));
                prop.putWiki("mode_page", page.getPage());
                if(hasRights) {
                    prop.put("mode_admin", "1");
                    prop.put("mode_admin_pageid", page.getKey());
                }
                //show all commments
                try {
                    final Iterator<String> i = page.getComments().iterator();
                    final int commentMode = page.getCommentMode();
                    String pageid;
                    blogBoardComments.CommentEntry entry;
                    boolean xml = false;
                    if(post.containsKey("xml")) {
                        xml = true;
                    }
                    int count = 0; //counts how many entries are shown to the user
                    int start = post.getInt("start",0); //indicates from where entries should be shown
                    int num   = post.getInt("num",10);  //indicates how many entries should be shown
                    boolean prev = false;               //indicates if there were previous comments to the ones that are dispalyed
                    if(xml) num = 0;
                    if (start < 0) start = 0;       
                    if (start > 1) prev = true;
                    final int nextstart = start+num;      //indicates the starting offset for next results
                    int prevstart = start-num;      //indicates the starting offset for previous results
                    while(i.hasNext() && count < num) {

                        pageid = i.next();
                        
                        if(start > 0) {
                            start--;
                            continue;
                        }
                            
                        entry = sb.blogCommentDB.read(pageid);

                        if (commentMode == 2 && !hasRights && !entry.isAllowed())
                            continue;

                        prop.put("mode", "0");
                        prop.put("mode_entries_"+count+"_pageid", entry.getKey());
                        if(!xml) {
                            prop.putHTML("mode_entries_"+count+"_subject", new String(entry.getSubject(),"UTF-8"));
                            prop.putHTML("mode_entries_"+count+"_author", new String(entry.getAuthor(),"UTF-8"));
                            prop.putWiki("mode_entries_"+count+"_page", entry.getPage());
                        }
                        else {
                            prop.putHTML("mode_entries_"+count+"_subject", new String(entry.getSubject(),"UTF-8"));
                            prop.putHTML("mode_entries_"+count+"_author", new String(entry.getAuthor(),"UTF-8"));
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
                    prop.put("mode_entries", count);
                    if(i.hasNext()) {
                        prop.put("mode_moreentries", "1"); //more entries are availible
                        prop.put("mode_moreentries_start", nextstart);
                        prop.put("mode_moreentries_num", num);
                        prop.put("mode_moreentries_pageid", page.getKey());
                    }
                    else prop.put("mode_moreentries", "0");
                    if(prev) {
                        prop.put("mode_preventries", "1");
                        if (prevstart < 0) prevstart = 0;
                        prop.put("mode_preventries_start", prevstart);
                        prop.put("mode_preventries_num", num);
                        prop.put("mode_preventries_pageid", page.getKey());
                    } else prop.put("mode_preventries", "0");
                } catch (final IOException e) {

                }
            }
        }

        // return rewrite properties
        return prop;
    }

    private static void messageForwardingViaEmail(final plasmaSwitchboard sb, final messageBoard.entry msgEntry) {
        try {
            if (!Boolean.valueOf(sb.getConfig("msgForwardingEnabled","false")).booleanValue()) return;

            // getting the recipient address
            final String sendMailTo = sb.getConfig("msgForwardingTo","root@localhost").trim();

            // getting the sendmail configuration
            final String sendMailStr = sb.getConfig("msgForwardingCmd","/usr/bin/sendmail")+" "+sendMailTo;
            final String[] sendMail = sendMailStr.trim().split(" ");

            // building the message text
            final StringBuffer emailTxt = new StringBuffer();
            emailTxt.append("To: ")
            .append(sendMailTo)
            .append("\nFrom: ")
            .append("yacy@")
            .append(sb.webIndex.seedDB.mySeed().getName())
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
            .append(new String(msgEntry.message()));

            final Process process=Runtime.getRuntime().exec(sendMail);
            final PrintWriter email = new PrintWriter(process.getOutputStream());
            email.print(new String(emailTxt));
            email.close();
        } catch (final Exception e) {
            yacyCore.log.logWarning("message: message forwarding via email failed. ",e);
        }
    }
}
