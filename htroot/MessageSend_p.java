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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.apache.http.entity.mime.content.ContentBody;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.Network;
import net.yacy.peers.Protocol;
import net.yacy.peers.Protocol.Post;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;

public class MessageSend_p {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
    public static String dateString(final Date date) {
        return SimpleFormatter.format(date);
    }


    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if ((post == null) || (post.get("hash","").isEmpty())) {
            prop.put("mode", "3");
            return prop;
        }

        final String hash = post.get("hash", "");
        String subject = post.get("subject", "");
        String message = post.get("message", "");

        if ((message.isEmpty()) || (post.containsKey("preview"))) {
            if (post.containsKey("preview")) {
                prop.put("mode", "1");
            } else {
                prop.put("mode", "0");
            }

            // open an editor page for the message
            // first ask if the other peer is online, and also what kind of document it accepts
            Seed seed = sb.peers.getConnected(ASCII.getBytes(hash));
            if (seed != null) {
                for (String ip : seed.getIPs()) {
                    final Map<String, String> result = Protocol.permissionMessage(seed.getPublicAddress(ip), hash);
                    //System.out.println("DEBUG: permission request result = " + result.toString());
                    String peerName;
                    Seed targetPeer = null;
                    if (hash.equals(sb.peers.mySeed().hash)) {
                        peerName = sb.peers.mySeed().get(Seed.NAME, "nameless");
                    } else {
                        targetPeer = sb.peers.getConnected(hash);
                        if (targetPeer == null) {
                            peerName = "nameless";
                        } else {
                            peerName = targetPeer.get(Seed.NAME, "nameless");
                        }
                    }

                    prop.putXML("mode_permission_peerName", peerName);
                    final String response = (result == null) ? null : result.get("response");
                    if (response == null || result == null) {
                        // we don't have permission or other peer does not exist
                        prop.put("mode_permission", "0");
                        if (targetPeer != null) {
                            sb.peers.peerActions.interfaceDeparture(targetPeer, ip);
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
                }
            } else { // seed == null
                prop.put("mode_permission", "0");
                prop.putXML("mode_permission_peerName", "a passive peer");
            }
        } else {
            prop.put("mode", "2");
            // send written message to peer
            try {
                prop.put("mode_status", "0");
                int messagesize = post.getInt("messagesize", 0);
                //int attachmentsize = Integer.parseInt(post.get("attachmentsize", "0"));

                if (messagesize < 1000) messagesize = 1000; // debug
                if (subject.length() > 100) subject = subject.substring(0, 100);
                if (message.length() > messagesize) message = message.substring(0, messagesize);
                final byte[] mb = UTF8.getBytes(message);
                SeedDB seedDB = sb.peers;
                // prepare request
                final String salt = crypt.randomSalt();

                // send request
                final Map<String, ContentBody> parts = Protocol.basicRequestParts(Switchboard.getSwitchboard(), hash, salt);
                parts.put("process", UTF8.StringBody("post"));
                parts.put("myseed", UTF8.StringBody(seedDB.mySeed().genSeedStr(salt)));
                parts.put("subject", UTF8.StringBody(subject));
                parts.put("message", UTF8.StringBody(mb));
                Seed seed = seedDB.getConnected(ASCII.getBytes(hash));
                Post post1 = null;
                for (String ip: seed.getIPs()) {
                    try {
                        post1 = new Post(seed.getPublicAddress(ip), hash, "/yacy/message.html", parts, 20000);
                    } catch (IOException e) {
                        Network.log.warn("yacyClient.postMessage error:" + e.getMessage());
                        post1 = null;
                    }
                    if (post1 != null) break;
                    seedDB.peerActions.interfaceDeparture(seed, ip);
                }
                final Map<String, String> result1 = post1 == null ? null : FileUtils.table(post1.result);
                final Map<String, String> result = result1;

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
