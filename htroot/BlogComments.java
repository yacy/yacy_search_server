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
import de.anomic.data.blogBoard.entry;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyCore;

public class BlogComments {

	private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
//	TODO: make userdefined date/time-strings (localisation)
	
    public static String dateString(Date date) {
    	return SimpleFormatter.format(date);
    }
	
	public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
		plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
		serverObjects prop = new serverObjects();
		blogBoard.entry page = null;
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
	    
		String StrAuthor = post.get("author", "anonymous");
		
		if (StrAuthor.equals("anonymous")) {
			StrAuthor = switchboard.blogDB.guessAuthor(ip);
			
	    	if (StrAuthor == null || StrAuthor.length() == 0) {
	    		if (de.anomic.yacy.yacyCore.seedDB.mySeed == null)
	    			StrAuthor = "anonymous";
	        	else {
	        		StrAuthor = de.anomic.yacy.yacyCore.seedDB.mySeed.get("Name", "anonymous");
	        	}
	        }
	    }
		
		byte[] author;
		try {
			author = StrAuthor.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			author = StrAuthor.getBytes();
		}

		if (post.containsKey("submit")) {
			// store a new/edited blog-entry
			byte[] content;
            if(!post.get("content", "").equals(""))
            {
                if(post.get("subject", "").equals("")) post.put("subject", "no title");
                try {
                    content = post.get("content", "").getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    content = post.get("content", "").getBytes();
                }

                Date date = null;
                
                //set name for new entry or date for old entry
                
                String StrSubject = post.get("subject", "");
                byte[] subject;
                try {
                    subject = StrSubject.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    subject = StrSubject.getBytes();
                }
                String commentID = String.valueOf(System.currentTimeMillis());
                entry blogEntry = switchboard.blogDB.read(pagename);
                blogEntry.addComment(commentID);
                switchboard.blogDB.write(blogEntry);
                switchboard.blogCommentDB.write(switchboard.blogCommentDB.newEntry(commentID, subject, author, ip, date, content));
                prop.put("LOCATION","BlogComments.html?page=" + pagename);
                
                messageBoard.entry msgEntry = null;
                try {
                    switchboard.messageDB.write(msgEntry = switchboard.messageDB.newEntry(
                            "blogComment",
                            StrAuthor,
                            yacyCore.seedDB.mySeed.hash,
                            yacyCore.seedDB.mySeed.getName(), yacyCore.seedDB.mySeed.hash,
                            "new blog comment: " + new String(blogEntry.subject(),"UTF-8"), content));
                } catch (UnsupportedEncodingException e1) {
                    switchboard.messageDB.write(msgEntry = switchboard.messageDB.newEntry(
                            "blogComment",
                            StrAuthor,
                            yacyCore.seedDB.mySeed.hash,
                            yacyCore.seedDB.mySeed.getName(), yacyCore.seedDB.mySeed.hash,
                            "new blog comment: " + new String(blogEntry.subject()), content));
                }

                messageForwardingViaEmail(env, msgEntry);
                
                // finally write notification
                File notifierSource = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath","htroot") + "/env/grafics/message.gif");
                File notifierDest   = new File(switchboard.getConfig("htDocsPath", "DATA/HTDOCS"), "notifier.gif");
                try {
                    serverFileUtils.copy(notifierSource, notifierDest);
                } catch (IOException e) {
                    serverLog.logSevere("MESSAGE", "NEW MESSAGE ARRIVED! (error: " + e.getMessage() + ")");
                  
                }
            }
		}

		page = switchboard.blogDB.read(pagename); //maybe "if(page == null)"
        if(hasRights && post.containsKey("delete") && post.containsKey("page") && post.containsKey("comment")) {
            if(page.removeComment((String) post.get("comment"))) {
                switchboard.blogCommentDB.delete((String) post.get("comment"));
            }
        }   

		if(post.containsKey("preview")) {
			//preview the page
            prop.put("mode", 1);//preview
            prop.put("mode_pageid", pagename);
            try {
                prop.put("mode_author", new String(author, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                prop.put("mode_author", new String(author));
            }
            prop.put("mode_subject", post.get("subject",""));
            prop.put("mode_date", dateString(new Date()));
            prop.putWiki("mode_page", post.get("content", ""));
            prop.put("mode_page-code", post.get("content", ""));
		}
		else {
		    // show blog-entry/entries
	        prop.put("mode", 0); //viewing
	        if(pagename.equals("blog_default")) {
                prop.put("LOCATION","Blog.html");
	        }
	        else {
	        	//show 1 blog entry
	        	prop.put("mode_pageid", page.key());
	        	try {
					prop.put("mode_subject", new String(page.subject(),"UTF-8"));
				} catch (UnsupportedEncodingException e) {
					prop.put("mode_subject", new String(page.subject()));
				}
	        	try {
					prop.put("mode_author", new String(page.author(),"UTF-8"));
				} catch (UnsupportedEncodingException e) {
					prop.put("mode_author", new String(page.author()));
				}
                try {
                    prop.put("mode_comments", new String(page.commentsSize(),"UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    prop.put("mode_comments", new String(page.commentsSize()));
                }
	        	prop.put("mode_date", dateString(page.date()));
	        	prop.putWiki("mode_page", page.page());
	        	if(hasRights) {
    				prop.put("mode_admin", 1);
    				prop.put("mode_admin_pageid",page.key());
    			}
                //show all commments
                try {
                    Iterator i = page.comments().iterator();
                    String pageid;
                    blogBoardComments.CommentEntry entry;
                    boolean xml = false;
                    if(post.containsKey("xml"))
                        xml = true;
                    int count = 0; //counts how many entries are shown to the user
                    int start = post.getInt("start",0); //indicates from where entries should be shown
                    int num   = post.getInt("num",20);  //indicates how many entries should be shown
                    if(xml) num = 0;
                    int nextstart = start+num;      //indicates the starting offset for next results
                    while(i.hasNext()) {
                        if(count >= num && num > 0)
                            break;
                        pageid = (String) i.next();
                        if(0 < start--)
                            continue;
                        entry = switchboard.blogCommentDB.read(pageid);

                        prop.put("mode", 0);
                        prop.put("mode_entries_"+count+"_pageid",entry.key());
                        if(!xml) {
                            prop.put("mode_entries_"+count+"_subject", new String(entry.subject(),"UTF-8"));
                            prop.put("mode_entries_"+count+"_author", new String(entry.author(),"UTF-8"));
                            prop.putWiki("mode_entries_"+count+"_page", entry.page());
                        }
                        else {
                            prop.put("mode_entries_"+count+"_subject", new String(entry.subject(),"UTF-8"));
                            prop.put("mode_entries_"+count+"_author", new String(entry.author(),"UTF-8"));
                            prop.putASIS("mode_entries_"+count+"_page", entry.page());
                            prop.put("mode_entries_"+count+"_timestamp", entry.timestamp());
                        }
                        prop.put("mode_entries_"+count+"_date", dateString(entry.date()));
                        prop.put("mode_entries_"+count+"_ip", entry.ip());
                        if(hasRights) {
                            prop.put("mode_entries_"+count+"_admin", 1);
                            prop.put("mode_entries_"+count+"_admin_pageid",page.key());
                            prop.put("mode_entries_"+count+"_admin_commentid",pageid);
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
		}
        prop.put("blogSize", switchboard.blogDB.size());
        prop.put("blogCommentsSize", switchboard.blogCommentDB.size());

		// return rewrite properties
		return prop;
	}
    
    private static void messageForwardingViaEmail(serverSwitch env, messageBoard.entry msgEntry) {
        try {
            if (!Boolean.valueOf(env.getConfig("msgForwardingEnabled","false")).booleanValue()) return;

            // getting the recipient address
            String sendMailTo = env.getConfig("msgForwardingTo","root@localhost").trim();
            
            // getting the sendmail configuration
            String sendMailStr = env.getConfig("msgForwardingCmd","/usr/bin/sendmail")+" "+sendMailTo;
            String[] sendMail = sendMailStr.trim().split(" ");

            // building the message text
            StringBuffer emailTxt = new StringBuffer();
            emailTxt.append("To: ")
            .append(sendMailTo)
            .append("\nFrom: ")
            .append("yacy@")
            .append(yacyCore.seedDB.mySeed.getName())
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

            Process process=Runtime.getRuntime().exec(sendMail);
            PrintWriter email = new PrintWriter(process.getOutputStream());
            email.print(new String(emailTxt));
            email.close();                        
        } catch (Exception e) {
            yacyCore.log.logWarning("message: message forwarding via email failed. ",e);
        }

    }
}
