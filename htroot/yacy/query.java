// query.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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
// javac -classpath .:../../classes query.java
// if the shell's current path is HTROOT

import java.io.IOException;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.Network;
import net.yacy.peers.Protocol;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class query {

    // example:
    // http://localhost:8090/yacy/query.html?youare=sCJ6Tq8T0N9x&object=lurlcount

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch ss) {
        if (post == null || ss == null) { return null; }

        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) ss;

        // remember the peer contact for peer statistics
        final String clientip = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "<unknown>"); // read an artificial header addendum
        final String userAgent = header.get(HeaderFramework.USER_AGENT, "<unknown>");
        sb.peers.peerActions.setUserAgent(clientip, userAgent);

        final serverObjects prop = new serverObjects();
        prop.put("magic", Network.magic);

        if ((post == null) || (ss == null) || !Protocol.authentifyRequest(post, ss)) {
            prop.put("response", "-1"); // request rejected
            return prop;
        }

        if ((sb.isRobinsonMode()) &&
            (!sb.isPublicRobinson()) &&
            (!sb.isInMyCluster(header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP)))) {
        	// if we are a robinson cluster, answer only if we are public robinson peers,
        	// or we are a private cluster and the requester is in our cluster.
          	// if we don't answer, the remote peer will recognize us as junior peer,
          	// what would mean that our peer ping does not work
        	prop.put("response", "-1"); // request rejected
            return prop;
        }

//      System.out.println("YACYQUERY: RECEIVED POST = " + ((post == null) ? "NULL" : post.toString()));

//      final String iam    = post.get("iam", "");    // complete seed of the requesting peer
        final String youare = post.get("youare", ""); // seed hash of the target peer, used for testing network stability
//      final String key    = post.get("key", "");    // transmission key for response
        final String obj    = post.get("object", ""); // keyword for query subject
        final String env    = post.get("env", "");    // argument to query

        prop.put("mytime", GenericFormatter.SHORT_SECOND_FORMATTER.format());

        // check if we are the right target and requester has correct information about this peer
        if (sb.peers.mySeed() == null || !sb.peers.mySeed().hash.equals(youare)) {
            // this request has a wrong target
            prop.put("response", "-1"); // request rejected
            return prop;
        }

        // requests about environment
        if (obj.equals("rwiurlcount")) try {
            // the total number of different urls in the rwi is returned
            // <env> shall contain a word hash, the number of assigned lurls to this hash is returned
            prop.put("response", sb.index.termIndex() == null ? 0 : sb.index.termIndex().get(env.getBytes(), null).size());
            return prop;
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        if (obj.equals("rwicount")) {
            // return the total number of available word indexes
            prop.put("response", sb.index.RWICount());
            return prop;
        }

        if (obj.equals("lurlcount")) {
            // return the number of all available l-url's
            prop.put("response", 1 /*sb.index.fulltext().collectionSize()*/); // patched to not call collectionSize() any more because the acutal size is not needed. Instead, rwicount should be called
            return prop;
        }

        // requests about requirements

        if (obj.equals("wantedlurls")) {
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedpurls")) {
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedword")) {
            // response returns a list of wanted word hashes
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedrwi")) {
            // <env> shall contain a word hash, the number of wanted lurls for this hash is returned
            prop.put("response", "0"); // dummy response
            return prop;
        }

        if (obj.equals("wantedseeds")) {
            // return a number of wanted seed
            prop.put("response", "0"); // dummy response
            return prop;
        }

        // return rewrite properties
        return prop;
    }

}
