// interactivesearch.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 06.01.2009 on http://yacy.net
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

import net.yacy.cora.protocol.RequestHeader;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

// this is a dummy class. Without it, the templates in interactivesearch.html do not load

public class yacyinteractive {

    public static serverObjects respond(final RequestHeader header, serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("topmenu", sb.getConfigBool("publicTopmenu", true) ? 1 : 0);
        final String promoteSearchPageGreeting =
                (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) ?
                    env.getConfig("network.unit.description", "") :
                    env.getConfig(SwitchboardConstants.GREETING, "");
        prop.put("promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put("promoteSearchPageGreeting.homepage", sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.put("promoteSearchPageGreeting.smallImage", sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        
        final String query = (post == null) ? "" : post.get("query", "");
        prop.putHTML("query", query);
        prop.putHTML("querys", query.replaceAll(" ", "+"));
        return prop;
    }
}