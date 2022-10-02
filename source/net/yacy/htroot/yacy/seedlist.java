// seedlist.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2013
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

package net.yacy.htroot.yacy;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * this servlet generates the same file as the principal peers upload to a bootstrap position
 * you can call it either with
 * http://localhost:8090/yacy/seedlist.html
 * or to generate json (or jsonp) with
 * http://localhost:8090/yacy/seedlist.json
 * http://localhost:8090/yacy/seedlist.json?callback=seedlist
 * http://localhost:8090/yacy/seedlist.json?node=true&me=false&address=true
 */
public final class seedlist {

    private static final int LISTMAX = 1000;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        final int maxcount = Math.min(LISTMAX, post == null ? Integer.MAX_VALUE : post.getInt("maxcount", Integer.MAX_VALUE));
        final float minversion = Math.min(LISTMAX, post == null ? 0.0f : post.getFloat("minversion", 0.0f));
        final boolean nodeonly = post == null || !post.containsKey("node") ? false : post.getBoolean("node");
        final boolean includeme = post == null || !post.containsKey("me") ? true : post.getBoolean("me");
        final boolean addressonly = post == null || !post.containsKey("address") ? false : post.getBoolean("address");
        final String peername = post == null ? null : post.containsKey("my") ? sb.peers.myName() : post.get("peername");
        final ArrayList<Seed> v;
        if (post != null && post.containsKey("my")) {
            v = new ArrayList<Seed>(1);
            v.add(sb.peers.mySeed());
        } else if (post != null && post.containsKey("id")) {
            v = new ArrayList<Seed>(1);
            final Seed s = sb.peers.get(post.get("id"));
            if (s != null) v.add(s);
        } else if (post != null && post.containsKey("name")) {
            v = new ArrayList<Seed>(1);
            final Seed s = sb.peers.lookupByName(post.get("name"));
            if (s != null) v.add(s);
        } else {
            v= sb.peers.getSeedlist(maxcount, includeme, nodeonly, minversion);
        }
        final serverObjects prop = new serverObjects();

        // write simple-encoded seed lines or json
        final String EXT = header.get(HeaderFramework.CONNECTION_PROP_EXT);
        final boolean json = EXT != null && EXT.equals("json");
        final boolean xml = EXT != null && EXT.equals("xml");

        if (json) {
            // check for JSONP
            if ( post != null && post.containsKey("callback") ) {
                prop.put("jsonp-start", post.get("callback") + "([");
                prop.put("jsonp-end", "]);");
            } else {
                prop.put("jsonp-start", "");
                prop.put("jsonp-end", "");
            }
            // construct json property lists
            int count = 0;
            for (int i = 0; i < v.size(); i++) {
                final Seed seed = v.get(i);
                if (peername != null && !peername.equals(seed.getName())) continue;
                final Set<String> ips = seed.getIPs();
                if (ips == null || ips.size() == 0) continue;
                prop.putJSON("peers_" + count + "_map_0_k", Seed.HASH);
                prop.put("peers_" + count + "_map_0_v", JSONObject.quote(seed.hash));
                prop.put("peers_" + count + "_map_0_c", 1);
                final Map<String, String> map = seed.getMap();
                int c = 1;
                if (!addressonly) {
                    for (final Map.Entry<String, String> m: map.entrySet()) {
                        prop.putJSON("peers_" + count + "_map_" + c + "_k", m.getKey());
                        prop.put("peers_" + count + "_map_" + c + "_v", JSONObject.quote(m.getValue()));
                        prop.put("peers_" + count + "_map_" + c + "_c", 1);
                        c++;
                    }
                }
                // construct a list of ips
                final StringBuilder a = new StringBuilder();
                a.append('[');
                for (final String ip: ips) a.append(JSONObject.quote(seed.getPublicAddress(ip))).append(',');
                a.setCharAt(a.length()-1, ']');
                prop.putJSON("peers_" + count + "_map_" + c + "_k", "Address");
                prop.put("peers_" + count + "_map_" + c + "_v", a.toString());
                prop.put("peers_" + count + "_map_" + c + "_c", 0);
                prop.put("peers_" + count + "_map", c + 1);
                prop.put("peers_" + count + "_c", 1);
                count++;
            }

            prop.put("peers_" + (count - 1) + "_c", 0);
            prop.put("peers", count);
        } else if (xml) {
            int count = 0;
            for (int i = 0; i < v.size(); i++) {
                final Seed seed = v.get(i);
                if (peername != null && !peername.equals(seed.getName())) continue;
                final Set<String> ips = seed.getIPs();
                if (ips == null || ips.size() == 0) continue;
                prop.putXML("peers_" + count + "_map_0_k", Seed.HASH);
                prop.putXML("peers_" + count + "_map_0_v", seed.hash);
                final Map<String, String> map = seed.getMap();
                int c = 1;
                if (!addressonly) {
                    for (final Map.Entry<String, String> m: map.entrySet()) {
                        prop.putXML("peers_" + count + "_map_" + c + "_k", m.getKey());
                        prop.putXML("peers_" + count + "_map_" + c + "_v", m.getValue());
                        c++;
                    }
                }
                for (final String ip: ips) {
                    prop.putXML("peers_" + count + "_map_" + c + "_k", "Address");
                    prop.putXML("peers_" + count + "_map_" + c + "_v", seed.getPublicAddress(ip));
                    c++;
                }
                prop.put("peers_" + count + "_map", c);
                count++;
            }

            prop.put("peers_" + (count - 1) + "_c", 0);
            prop.put("peers", count);
        } else {
            final StringBuilder encoded = new StringBuilder(1024);
            for (final Seed seed: v) {
                encoded.append(seed.genSeedStr(null)).append(serverCore.CRLF_STRING);
            }
            prop.put("encoded", encoded.toString());
        }

        // return rewrite properties
        return prop;
    }

}
