// Messages_p.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
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

// You must compile this file with
// javac -classpath .:../Classes Message.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.MessageBoard;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

import com.google.common.io.Files;


public class Messages_p {

    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
    private static final String PEERSKNOWN = "peersKnown_";

    public static String dateString(final Date date) {
        return SimpleFormatter.format(date);
    }

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        // set peer address / name
        final String peerAddress = sb.peers.mySeed().getPublicAddress();
        final String peerName = sb.peers.mySeed().getName();
        prop.put("peerAddress", peerAddress);
        prop.putXML("peerName", peerName);

        // List known hosts for message sending (from Blacklist_p.java)
        if (sb.peers != null && sb.peers.sizeConnected() > 0) {
            prop.put("peersKnown", "1");
            int peerCount = 0;
            try {
                final TreeMap<String, String> hostList = new TreeMap<String, String>();
                final Iterator<Seed> e = sb.peers.seedsConnected(true, false, null, (float) 0.0);
                while (e.hasNext()) {
                    final Seed seed = e.next();
                    if (seed != null) hostList.put(seed.get(Seed.NAME, "nameless"),seed.hash);
                }

                String peername;
                while ((peername = hostList.firstKey()) != null) {
                    final String Hash = hostList.get(peername);
                    prop.put(PEERSKNOWN + "peers_" + peerCount + "_hash", Hash);
                    prop.putXML(PEERSKNOWN + "peers_" + peerCount + "_name", peername);
                    hostList.remove(peername);
                    peerCount++;
                }
            } catch (final Exception e) {/* */}
            prop.put(PEERSKNOWN + "peers", peerCount);
        } else {
            prop.put("peersKnown", "0");
        }

        prop.put("mode", "0");
        prop.put("mode_error", "0");

        String action = ((post == null) ? "list" : post.get("action", "list"));
        MessageBoard.entry message;

        // first reset notification
        final File notifierSource = new File(sb.getAppPath(), sb.getConfig("htRootPath", "htroot") + "/env/grafics/empty.gif");
        final File notifierDest = new File(sb.getDataPath("htDocsPath", "DATA/HTDOCS"), "notifier.gif");
        try {
            Files.copy(notifierSource, notifierDest);
        } catch (final IOException e) {
        }

        if (action.equals("delete")) {
            final String key = (post == null ? "" : post.get("object", ""));
            sb.messageDB.remove(key);
            action = "list";
        }

        if (action.equals("list")) {
            prop.put("mode", "0"); //list
            try {
                final Iterator<String> i = sb.messageDB.keys(null, true);
                String key;

                boolean dark = true;
                int count=0;
                while (i.hasNext()) {
                    key = i.next();
                    message = sb.messageDB.read(key);
                    prop.put("mode_messages_"+count+"_dark", ((dark) ? "1" : "0") );
                    prop.put("mode_messages_"+count+"_date", dateString(message.date()));
                    prop.putXML("mode_messages_"+count+"_from", message.author());
                    prop.putXML("mode_messages_"+count+"_to", message.recipient());
                    prop.putXML("mode_messages_"+count+"_subject", message.subject());
                    prop.putXML("mode_messages_"+count+"_category", message.category());
                    prop.putXML("mode_messages_"+count+"_key", key);
                    prop.put("mode_messages_"+count+"_hash", message.authorHash());

                    if ((header.get(HeaderFramework.CONNECTION_PROP_PATH)).endsWith(".rss")) {
                    	// set the peer address
                    	prop.put("mode_messages_"+count+"_peerAddress", peerAddress);

                    	// set the rfc822 date
                    	prop.put("mode_messages_"+count+"_rfc822Date", HeaderFramework.formatRFC1123(message.date()));

                    	prop.putXML("mode_messages_"+count+"_body",UTF8.String(message.message()));
                    }

                    dark = !dark;
                    count++;
                }
                prop.put("mode_messages", count);
            } catch (final IOException e) {
                prop.put("mode_error", "1");//I/O error reading message table
                prop.putHTML("mode_error_message", e.getMessage());
            }
        }

        if (action.equals("view")) {
            prop.put("mode", "1"); //view
            final String key = (post == null ? "" : post.get("object", ""));
            message = sb.messageDB.read(key);
            if (message == null) throw new NullPointerException("Message with ID " + key + " does not exist");

            prop.putXML("mode_from", message.author());
            prop.putXML("mode_to", message.recipient());
            prop.put("mode_date", dateString(message.date()));
            prop.putXML("mode_subject", message.subject());
            String theMessage = null;
            theMessage = UTF8.String(message.message());
            prop.putWiki(sb.peers.mySeed().getClusterAddress(), "mode_message", theMessage);
            prop.put("mode_hash", message.authorHash());
            prop.putXML("mode_key", key);
        }

        // return rewrite properties
        return prop;
    }
}
