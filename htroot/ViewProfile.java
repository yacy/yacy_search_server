// ViewProfile_p.java 
// -----------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.04.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// This File is contributed by Alexander Schier
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;

import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsDB;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacySeed;

public class ViewProfile {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final int display = ((post == null) || (!authenticated)) ? 0 : post.getInt("display", 0);
        prop.put("display", display);
        prop.put("edit", authenticated ? 1 : 0);
        final String hash = (post == null) ? null : post.get("hash");
        
        if ((hash == null) || (sb.peers == null)) {
            // wrong access
            prop.put("success", "0");
            return prop;
        }
        prop.put("hash", hash);
        
        // get the profile
        Map<String, String> profile = null;
        if (hash.equals("localhash")) {
            // read the profile from local peer
            final Properties p = new Properties();
            FileInputStream fileIn = null;
            try {
                fileIn = new FileInputStream(new File("DATA/SETTINGS/profile.txt"));
                p.load(fileIn);        
            } catch(final IOException e) {} finally {
                if (fileIn != null) try { fileIn.close(); fileIn = null; } catch (final Exception e) {}
            }
            profile = new HashMap<String, String>();
            for (Map.Entry<Object, Object> e: p.entrySet()) profile.put((String) e.getKey(), (String) e.getValue());
            prop.put("success", "3"); // everything ok
            prop.put("localremotepeer", "0");
            prop.putHTML("success_peername", sb.peers.mySeed().getName());
            prop.put("success_peerhash", sb.peers.mySeed().hash);
        } else {
            // read the profile from remote peer
            yacySeed seed = sb.peers.getConnected(hash);
            if (seed == null) seed = sb.peers.getDisconnected(hash);
            if (seed == null) {
                prop.put("success", "1"); // peer unknown
            } else {
                // process news if existent
                try {
                    final yacyNewsDB.Record record = sb.peers.newsPool.getByOriginator(yacyNewsPool.INCOMING_DB, yacyNewsPool.CATEGORY_PROFILE_UPDATE, seed.hash);
                    if (record != null) sb.peers.newsPool.moveOff(yacyNewsPool.INCOMING_DB, record.id());
                } catch (final Exception e) {
                    Log.logException(e);
                }
                
                // try to get the profile from remote peer
                if (sb.clusterhashes != null) seed.setAlternativeAddress(sb.clusterhashes.get(seed.hash.getBytes()));
                profile = yacyClient.getProfile(seed);
                
                // if profile did not arrive, say that peer is disconnected
                if (profile == null) {
                    prop.put("success", "2"); // peer known, but disconnected
                } else {
                    yacyCore.log.logInfo("fetched profile:" + profile);
                    prop.put("success", "3"); // everything ok
                }
                prop.putHTML("success_peername", seed.getName());
                prop.put("success_peerhash", seed.hash);
            }
            prop.put("localremotepeer", "1");
        }
        Iterator<Map.Entry<String, String>> i;
        if (profile != null) {
            i = profile.entrySet().iterator();
        } else {
            i = (new HashMap<String, String>()).entrySet().iterator();
        }
        Map.Entry<String, String> entry;
        // all known keys which should be set as they are
        final HashSet<String> knownKeys = new HashSet<String>();
        knownKeys.add("name");
        knownKeys.add("nickname");
        // knownKeys.add("homepage");//+http
        knownKeys.add("email");
        knownKeys.add("icq");
        knownKeys.add("jabber");
        knownKeys.add("yahoo");
        knownKeys.add("msn");
        knownKeys.add("skype");
        knownKeys.add("comment");        

        //empty values
        final Iterator<String> it = knownKeys.iterator();
        while (it.hasNext()) {
            prop.put("success_" + it.next(), "0");
        }
        
        //number of not explicitly recognized but displayed items
        int numUnknown = 0;
        while (i.hasNext()) {
            entry = i.next();
            final String key = entry.getKey();
            String value;

            // this prevents broken links ending in <br>
            value = entry.getValue().replaceAll("\r", "").replaceAll("\\\\n", "\n");

            //all known Keys which should be set as they are
            if (knownKeys.contains(key)) {
                if (value.length() > 0) {
                    prop.put("success_" + key, "1");
                    // only comments get "wikified"
                    if(key.equals("comment")){
                        prop.putWiki(sb.peers.mySeed().getClusterAddress(), 
                                "success_" + key + "_value",
                                entry.getValue().replaceAll("\r", "").replaceAll("\\\\n", "\n"));
                        prop.put("success_" + key + "_b64value", Base64Order.standardCoder.encodeString(entry.getValue()));
                    }else{
                        prop.putHTML("success_" + key + "_value", value); //put replaces HTML Chars by entities.
                    }
                }
            } else if (key.equals("homepage")) {
                if (value.length() > 0) {
                    if (!(value.startsWith("http"))) {
                        value = "http://" + value;
                    }
                    prop.put("success_" + key, "1");
                    prop.putHTML("success_" + key + "_value", value);
                }
                //This will display unknown items(of newer versions) as plaintext
            } else {
                //unknown
                prop.putHTML("success_other_" + numUnknown + "_key", key);
                prop.putHTML("success_other_" + numUnknown + "_value", value);
                numUnknown++;
            }
        }
        prop.put("success_other", numUnknown);

        return prop;
    }
}
