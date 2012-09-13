// ConfigHeuristics_p.java
// --------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 26.06.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2010-02-09 18:14:16 +0100 (Di, 09 Feb 2010) $
// $LastChangedRevision: 6658 $
// $LastChangedBy: lotus $
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
import net.yacy.search.Switchboard;
import de.anomic.data.WorkTables;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class ConfigHeuristics_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if (post != null) {

            // store this call as api call
            sb.tables.recordAPICall(post, "ConfigHeuristics.html", WorkTables.TABLE_API_TYPE_CONFIGURATION, "heuristic settings");

            if (post.containsKey("site_on")) sb.setConfig("heuristic.site", true);
            if (post.containsKey("site_off")) sb.setConfig("heuristic.site", false);
            if (post.containsKey("searchresult_on")) sb.setConfig("heuristic.searchresults", true);
            if (post.containsKey("searchresult_off")) sb.setConfig("heuristic.searchresults", false);
            if (post.containsKey("searchresultglobal_on")) sb.setConfig("heuristic.searchresults.crawlglobal", true);
            if (post.containsKey("searchresultglobal_off")) sb.setConfig("heuristic.searchresults.crawlglobal", false);
            if (post.containsKey("blekko_on")) sb.setConfig("heuristic.blekko", true);
            if (post.containsKey("blekko_off")) sb.setConfig("heuristic.blekko", false);
            if (post.containsKey("twitter_on")) sb.setConfig("heuristic.twitter", true);
            if (post.containsKey("twitter_off")) sb.setConfig("heuristic.twitter", false);
        }

        prop.put("site.checked", sb.getConfigBool("heuristic.site", false) ? 1 : 0);
        prop.put("searchresult.checked", sb.getConfigBool("heuristic.searchresults", false) ? 1 : 0);
        prop.put("searchresultglobal.checked", sb.getConfigBool("heuristic.searchresults.crawlglobal", false) ? 1 : 0);
        prop.put("blekko.checked", sb.getConfigBool("heuristic.blekko", false) ? 1 : 0);
        prop.put("twitter.checked", sb.getConfigBool("heuristic.twitter", false) ? 1 : 0);

        return prop;
    }
}
