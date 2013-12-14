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

import java.util.ArrayList;
import java.util.Map;

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
        int maxcount = Math.min(LISTMAX, post == null ? Integer.MAX_VALUE : post.getInt("maxcount", Integer.MAX_VALUE));
        float minversion = Math.min(LISTMAX, post == null ? 0.0f : post.getFloat("minversion", 0.0f));
        boolean nodeonly = post == null || !post.containsKey("node") ? false : post.getBoolean("node");
        boolean includeme = post == null || !post.containsKey("me") ? true : post.getBoolean("me");
        boolean addressonly = post == null || !post.containsKey("address") ? false : post.getBoolean("address");
        final ArrayList<Seed> v = sb.peers.getSeedlist(maxcount, includeme, nodeonly, minversion);
        final serverObjects prop = new serverObjects();
        
        // write simple-encoded seed lines or json
        String EXT = header.get("EXT");
        boolean json = EXT != null && EXT.equals("json");
        
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
            for (int i = 0; i < v.size(); i++) {
                prop.putJSON("peers_" + i + "_map_0_k", Seed.HASH);
                prop.putJSON("peers_" + i + "_map_0_v", v.get(i).hash);
                prop.put("peers_" + i + "_map_0_c", 1);
                Seed seed = v.get(i);
                Map<String, String> map = seed.getMap();
                int c = 1;
                if (!addressonly) {
                    for (Map.Entry<String, String> m: map.entrySet()) {
                        prop.putJSON("peers_" + i + "_map_" + c + "_k", m.getKey());
                        prop.putJSON("peers_" + i + "_map_" + c + "_v", m.getValue());
                        prop.put("peers_" + i + "_map_" + c + "_c", 1);
                        c++;
                    }
                }
                prop.putJSON("peers_" + i + "_map_" + c + "_k", "Address");
                prop.putJSON("peers_" + i + "_map_" + c + "_v", seed.getPublicAddress());
                prop.put("peers_" + i + "_map_" + c + "_c", 0);
                prop.put("peers_" + i + "_map", c + 1);
                prop.put("peers_" + i + "_c", i < v.size() - 1 ? 1 : 0);
            }
            prop.put("peers", v.size());
        } else {
            final StringBuilder encoded = new StringBuilder(1024);
            for (Seed seed: v) {
                encoded.append(seed.genSeedStr(null)).append(serverCore.CRLF_STRING);
            }        
            prop.put("encoded", encoded.toString());
        }
        
        // return rewrite properties
        return prop;
    }

}
