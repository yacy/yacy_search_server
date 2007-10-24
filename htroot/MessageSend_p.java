//MessageSend_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//Last major change: 28.06.2003

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.

//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


//You must compile this file with
//javac -classpath .:../Classes MessageSend_p.java
//if the shell's current path is HTROOT

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import de.anomic.http.httpHeader;
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
        serverObjects prop = new serverObjects();

        if ((post == null) || (post.get("hash","").length() == 0)) {
            prop.put("mode", "2");
            return prop;
        }

        String hash    = post.get("hash", "");
        String subject = post.get("subject", "");
        String message = post.get("message", "");

        if ((message.length() == 0) || (post.containsKey("preview"))) {
            if (post.containsKey("preview")) {
                prop.put("mode", "1");
            } else {
                prop.put("mode", "0");
            }

            // open an editor page for the message
            // first ask if the other peer is online, and also what kind of document it accepts
            HashMap result = yacyClient.permissionMessage(hash);
            //System.out.println("DEBUG: permission request result = " + result.toString());
            String peerName;
            yacySeed targetPeer = null;
            if (hash.equals(yacyCore.seedDB.mySeed().hash)) {
                peerName = yacyCore.seedDB.mySeed().get(yacySeed.NAME,"nameless");
            } else {
                targetPeer = yacyCore.seedDB.getConnected(hash);
                if (targetPeer == null)
                    peerName = "nameless";
                else
                    peerName = targetPeer.get(yacySeed.NAME,"nameless");
            }

            prop.putHTML("mode_permission_peerName", peerName, true);
            String response = (result == null) ? "-1" : (String) result.get("response");
            if ((response == null) || (response.equals("-1"))) {
                // we don't have permission or other peer does not exist
                prop.put("mode_permission", "0");

                if (targetPeer != null) {
                    yacyCore.peerActions.peerDeparture(targetPeer, "peer responded upon message send request: " + response);
                }
            } else {
                prop.put("mode_permission", "1");

                // write input form
                try {
                    int messagesize = Integer.parseInt((String) result.get("messagesize"));
                    int attachmentsize = Integer.parseInt((String) result.get("attachmentsize"));

                    prop.putHTML("mode_permission_response", response, true);
                    prop.put("mode_permission_messagesize", messagesize);
                    prop.put("mode_permission_attachmentsize", attachmentsize);
                    prop.putHTML("mode_permission_subject", subject, true);
                    prop.putHTML("mode_permission_message", message, true);
                    prop.put("mode_permission_hash", hash);
                    if (post.containsKey("preview")) {
                        prop.putWiki("mode_permission_previewmessage", message);

                    }

                } catch (NumberFormatException e) {
                    // "unresolved pattern", the remote peer is alive but had an exception
                    prop.put("mode_permission", "2");
                }
            }
        } else {
            prop.put("mode", "2");
            // send written message to peer
            try {
                prop.put("mode_status", "0");
                int messagesize = Integer.parseInt(post.get("messagesize", "0"));
                //int attachmentsize = Integer.parseInt(post.get("attachmentsize", "0"));

                if (messagesize < 1000) messagesize = 1000; // debug
                if (subject.length() > 100) subject = subject.substring(0, 100);
                if (message.length() > messagesize) message = message.substring(0, messagesize);
                byte[] mb;
                try {
                    mb = message.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    mb = message.getBytes();
                }
                HashMap result = yacyClient.postMessage(hash, subject, mb);

                //message has been sent
                prop.put("mode_status_response", result.get("response"));

            } catch (NumberFormatException e) {
                prop.put("mode_status", "1");

                // "unresolved pattern", the remote peer is alive but had an exception
                prop.putHTML("mode_status_message", message, true);
            }
        }
        return prop;
    }
}
