//MessageSend_p.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
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


//You must compile this file with
//javac -classpath .:../Classes MessageSend_p.java
//if the shell's current path is HTROOT

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacySeed;

public class MessageSend_p {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static String dateString(final Date date) {
        return SimpleFormatter.format(date);
    }


    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        if ((post == null) || (post.get("hash","").length() == 0)) {
            prop.put("mode", "2");
            return prop;
        }

        final String hash    = post.get("hash", "");
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
            final HashMap<String, String> result = yacyClient.permissionMessage(sb.webIndex.seedDB, hash);
            //System.out.println("DEBUG: permission request result = " + result.toString());
            String peerName;
            yacySeed targetPeer = null;
            if (hash.equals(sb.webIndex.seedDB.mySeed().hash)) {
                peerName = sb.webIndex.seedDB.mySeed().get(yacySeed.NAME,"nameless");
            } else {
                targetPeer = sb.webIndex.seedDB.getConnected(hash);
                if (targetPeer == null)
                    peerName = "nameless";
                else
                    peerName = targetPeer.get(yacySeed.NAME,"nameless");
            }

            prop.putXML("mode_permission_peerName", peerName);
            final String response = (result == null) ? null : (String) result.get("response");
            if (response == null || result == null) {
                // we don't have permission or other peer does not exist
                prop.put("mode_permission", "0");

                if (targetPeer != null) {
                    sb.webIndex.peerActions.peerDeparture(targetPeer, "peer responded upon message send request: " + response);
                }
            } else {
                prop.put("mode_permission", "1");

                // write input form
                try {
                    final int messagesize = Integer.parseInt(result.get("messagesize"));
                    final int attachmentsize = Integer.parseInt(result.get("attachmentsize"));

                    prop.putXML("mode_permission_response", response);
                    prop.put("mode_permission_messagesize", messagesize);
                    prop.put("mode_permission_attachmentsize", attachmentsize);
                    prop.putXML("mode_permission_subject", subject);
                    prop.putXML("mode_permission_message", message);
                    prop.putHTML("mode_permission_hash", hash);
                    if (post.containsKey("preview")) {
                        prop.putWiki("mode_permission_previewmessage", message);

                    }

                } catch (final NumberFormatException e) {
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
                } catch (final UnsupportedEncodingException e) {
                    mb = message.getBytes();
                }
                final HashMap<String, String> result = yacyClient.postMessage(sb.webIndex.seedDB, hash, subject, mb);

                //message has been sent
                prop.put("mode_status_response", result.get("response"));

            } catch (final NumberFormatException e) {
                prop.put("mode_status", "1");

                // "unresolved pattern", the remote peer is alive but had an exception
                prop.putXML("mode_status_message", message);
            }
        }
        return prop;
    }
}
