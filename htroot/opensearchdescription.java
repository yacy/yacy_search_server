// opensearchdescription.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 12.08.2006 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class opensearchdescription {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        // generate message content for open search description
        String promoteSearchPageGreeting = env.getConfig(SwitchboardConstants.GREETING, "");
        if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");

        String thisaddress = header.get("Host", Domains.LOCALHOST);
        if (thisaddress.indexOf(':',0) == -1) thisaddress += ":" + env.getConfig("port", "8090");

        final serverObjects prop = new serverObjects();
        prop.put("compareyacy", post != null && post.getBoolean("compare_yacy") ? 1 : 0);
        prop.putXML("compareyacy_thisaddress", thisaddress);
        prop.putXML("thisaddress", thisaddress);
        prop.putXML("SearchPageGreeting", promoteSearchPageGreeting);
        prop.putXML("clientname", sb.peers.mySeed().getName());
        prop.putXML("compareyacy_search_left", post == null ? compare_yacy.defaultsearchL : post.get("left", compare_yacy.defaultsearchL));
        prop.putXML("compareyacy_search_right", post == null ? compare_yacy.defaultsearchR : post.get("right", compare_yacy.defaultsearchR));

        // return rewrite properties
        return prop;
    }

}
