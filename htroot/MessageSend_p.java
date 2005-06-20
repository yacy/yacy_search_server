// MessageSend_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// Last major change: 28.06.2003
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
// javac -classpath .:../Classes MessageSend_p.java
// if the shell's current path is HTROOT

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class MessageSend_p {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(Date date) {
	return SimpleFormatter.format(date);
    }


    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
	serverObjects prop = new serverObjects();

	String body = "";
	if ((post == null) || (post.get("hash","").length() == 0)) {
	    prop.put("body", "<p>You cannot call this page directly. Instead, use a link on the <a href=\"Network.html\">Network</a> page.</p>");
	    return prop;
	}
	
	String hash    = post.get("hash", "");
	String subject = post.get("subject", "");
	String message = post.get("message", "");
            
	if (message.length() == 0) {
	    // open an editor page for the message
	    // first ask if the other peer is online, and also what kind of document it accepts
	    HashMap result = yacyClient.permissionMessage(hash);
            //System.out.println("DEBUG: permission request result = " + result.toString());
	    String peerName;
            yacySeed targetPeer = null;
	    if (hash.equals(yacyCore.seedDB.mySeed.hash)) {
		peerName = yacyCore.seedDB.mySeed.get("Name","nameless");
            } else {
                targetPeer = yacyCore.seedDB.getConnected(hash);
                if (targetPeer == null)
                    peerName = "nameless";
                else
                    peerName = targetPeer.get("Name","nameless");
            }
            String response = (result == null) ? "-1" : (String) result.get("response");
	    if ((response == null) || (response.equals("-1"))) {
		// we don't have permission or other peer does not exist
		body += "<p>You cannot send a message to '" + peerName + "'. The peer does not respond. It was now removed from the peer-list.</p>";
                if (targetPeer != null) {
                    yacyCore.peerActions.disconnectPeer(targetPeer);
                }
	    } else {
                // write input form
                try {
                    int messagesize = Integer.parseInt((String) result.get("messagesize"));
                    int attachmentsize = Integer.parseInt((String) result.get("attachmentsize"));
                    body += "<p>The peer '" + peerName + "' is alive and responded:<br>";
                    body += "'" + response + " You are allowed to send me a message &le; " + messagesize + " kb and an attachment &le; " + attachmentsize + ".'</p>";
                    body += "<form action=\"MessageSend_p.html\" method=\"post\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\"><br><br>";
                    body += "<p><h3>Your Message</h3></p>";
                    body += "<p>Subject:<br><input name=\"subject\" type=\"text\" size=\"80\" maxlength=\"80\" value=\"" + subject + "\"></p>";
                    body += "<p>Text:<br><textarea name=\"message\" cols=\"80\" rows=\"8\"></textarea></p>";
                    body += "<input type=\"hidden\" name=\"hash\" value=\"" + hash + "\">";
                    body += "<input type=\"hidden\" name=\"messagesize\" value=\"" + messagesize + "\">";
                    body += "<input type=\"hidden\" name=\"attachmentsize\" value=\"" + attachmentsize + "\">";
                    body += "<input name=\"new\" type=\"submit\" value=\"Enter\"></form>";
                } catch (NumberFormatException e) {
                    // "unresolved pattern", the remote peer is alive but had an exception
                    body += "<p>The peer '" + peerName + "' is alive but cannot respond. Sorry..</p>";
                }
	    }
	} else {
	// send written message to peer
            try {
                int messagesize = Integer.parseInt(post.get("messagesize", "0"));
                int attachmentsize = Integer.parseInt(post.get("attachmentsize", "0"));
                
                if (messagesize < 1000) messagesize = 1000; // debug
                if (subject.length() > 100) subject = subject.substring(0, 100);
                if (message.length() > messagesize) message = message.substring(0, messagesize);
                HashMap result = yacyClient.postMessage(hash, subject, message.getBytes());
                body += "<p>Your message has been sent. The target peer respondet:</p>";
                body += "<p><i>" + result.get("response") + "</i></p>";
            } catch (NumberFormatException e) {
                // "unresolved pattern", the remote peer is alive but had an exception
                body += "<p>The target peer is alive but did not receive your message. Sorry..</p>";
                body += "<p>Here is a copy of your message, so you can copy it to save it for further attempts:<br>";
                body += message;
                body += "</p>";
            }
	}

	// return rewrite properties
	prop.put("body", body);
	return prop;
    }

}
