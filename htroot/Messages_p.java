// Messages_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.06.2003
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
// javac -classpath .:../Classes Message.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TreeMap;

import de.anomic.data.messageBoard;
import de.anomic.http.HttpClient;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class Messages_p {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final String PEERSKNOWN = "peersKnown_";

    public static String dateString(Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();

        // set peer address / name
        final String peerAddress = yacyCore.seedDB.mySeed().getPublicAddress();
        final String peerName = yacyCore.seedDB.mySeed().getName();
        prop.put("peerAddress", peerAddress);
        prop.putHTML("peerName", peerName, true);

        // List known hosts for message sending (from Blacklist_p.java)
        if (yacyCore.seedDB != null && yacyCore.seedDB.sizeConnected() > 0) {
            prop.put("peersKnown", "1");
            int peerCount = 0;
            try {
                TreeMap<String, String> hostList = new TreeMap<String, String>();
                final Iterator<yacySeed> e = yacyCore.seedDB.seedsConnected(true, false, null, (float) 0.0);
                while (e.hasNext()) {
                    yacySeed seed = e.next();
                    if (seed != null) hostList.put(seed.get(yacySeed.NAME, "nameless"),seed.hash);
                }

                String peername;
                while ((peername = hostList.firstKey()) != null) {
                    final String Hash = hostList.get(peername);
                    prop.put(PEERSKNOWN + "peers_" + peerCount + "_hash", Hash);
                    prop.putHTML(PEERSKNOWN + "peers_" + peerCount + "_name", peername, true);
                    hostList.remove(peername);
                    peerCount++;
                }
            } catch (Exception e) {/* */}
            prop.put(PEERSKNOWN + "peers", peerCount);
        } else {
            prop.put("peersKnown", "0");
        }

        prop.put("mode", "0");
        prop.put("mode_error", "0");

        String action = ((post == null) ? "list" : post.get("action", "list"));
        messageBoard.entry message;

        // first reset notification
        File notifierSource = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath", "htroot") + "/env/grafics/empty.gif");
        File notifierDest = new File(switchboard.getConfigPath("htDocsPath", "DATA/HTDOCS"), "notifier.gif");
        try {
            serverFileUtils.copy(notifierSource, notifierDest);
        } catch (IOException e) {
        }

        if (action.equals("delete")) {
            String key = post.get("object", "");
            switchboard.messageDB.remove(key);
            action = "list";
        }

        if (action.equals("list")) {
            prop.put("mode", "0"); //list
            try {
                Iterator<String> i = switchboard.messageDB.keys(null, true);
                String key;

                boolean dark = true;
                int count=0;
                while (i.hasNext()) {
                    key = i.next();
                    message = switchboard.messageDB.read(key);
                    prop.put("mode_messages_"+count+"_dark", ((dark) ? "1" : "0") );
                    prop.put("mode_messages_"+count+"_date", dateString(message.date()));
                    prop.putHTML("mode_messages_"+count+"_from", message.author(), true);
                    prop.putHTML("mode_messages_"+count+"_to", message.recipient(), true);
                    prop.putHTML("mode_messages_"+count+"_subject", message.subject(), true);
                    prop.putHTML("mode_messages_"+count+"_category", message.category(), true);
                    prop.putHTML("mode_messages_"+count+"_key", key, true);
                    prop.put("mode_messages_"+count+"_hash", message.authorHash());

                    if (((String)header.get(httpHeader.CONNECTION_PROP_PATH)).endsWith(".rss")) {
                    	// set the peer address
                    	prop.put("mode_messages_"+count+"_peerAddress", peerAddress);

                    	// set the rfc822 date
                    	prop.put("mode_messages_"+count+"_rfc822Date",HttpClient.dateString(message.date()));

                    	// also write out the message body (needed for the RSS feed)
                        try {
                        	prop.putHTML("mode_messages_"+count+"_body",new String(message.message(), "UTF-8"), true);
                        } catch (UnsupportedEncodingException e) {
                            // can not happen, because UTF-8 must be supported by every JVM
                        }
                    }

                    dark = !dark;
                    count++;
                }
                prop.put("mode_messages", count);
            } catch (IOException e) {
                prop.put("mode_error", "1");//I/O error reading message table
                prop.putHTML("mode_error_message", e.getMessage());
            }
        }

        if (action.equals("view")) {
            prop.put("mode", "1"); //view
            String key = post.get("object", "");
            message = switchboard.messageDB.read(key);
            if (message == null) throw new NullPointerException("Message with ID " + key + " does not exist");

            prop.putHTML("mode_from", message.author(), true);
            prop.putHTML("mode_to", message.recipient(), true);
            prop.put("mode_date", dateString(message.date()));
            prop.putHTML("mode_subject", message.subject(), true);
            String theMessage = null;
            try {
                theMessage = new String(message.message(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // can not happen, because UTF-8 must be supported by every JVM
            }
            prop.putWiki("mode_message", theMessage);
            prop.put("mode_hash", message.authorHash());
            prop.putHTML("mode_key", key, true);
        }

        // return rewrite properties
        return prop;
    }
}
