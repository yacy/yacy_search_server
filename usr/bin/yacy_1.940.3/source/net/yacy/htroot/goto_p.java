// goto_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
// javac -classpath .:../Classes goto_p.java
// if the shell's current path is HTROOT

package net.yacy.htroot;

import java.util.Set;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.Seed;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * forwards caller/browser to remote peer
 */
public class goto_p {

    /**
     * url parameter
     *
     * hash= of remote peer
     * path= path part to forward to
     */
    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        String hash = null;
        if (post != null) {
            hash = post.get("hash", null); // get peers hash
        }

        if (hash != null) {
            final Seed seed = sb.peers.getConnected(hash);

            if (seed != null) {
            	final Set<String> ips = seed.getIPs();
            	String peersUrl = null;
            	if(!ips.isEmpty()) {
					peersUrl = seed.getPublicURL(ips.iterator().next(), false);
            	}
                if (peersUrl != null) {
                    String path = post.get("path", "/");
                    if (!path.startsWith("/")) {
                    	path = "/" + path;
                    }
                    prop.put(serverObjects.ACTION_LOCATION, peersUrl + path); // redirect command
                } else {
                    prop.put("msg", "peer not available");
                }
            } else {
                prop.put("msg", "peer not available");
            }

        } else {
            prop.put("msg", "parameter missing");
        }
        return prop;
    }
}
