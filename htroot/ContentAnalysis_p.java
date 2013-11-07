/**
 *  ContentAnalysis_p
 *  Copyright 2013 by Michael Peter Christen
 *  First released 12.03.2013 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ContentAnalysis_p {


    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // clean up all search events
        SearchEventCache.cleanupEvents(true);
        sb.index.clearCaches(); // every time the ranking is changed we need to remove old orderings

        if (post != null && post.containsKey("EnterDoublecheck")) {
            Ranking.setMinTokenLen(post.getInt("minTokenLen", 3));
            Ranking.setQuantRate(post.getFloat("quantRate", 0.5f));
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_MINLENGTH, Ranking.getMinTokenLen());
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_QUANTRATE, Ranking.getQuantRate());
        }

        if (post != null && post.containsKey("ResetDoublecheck")) {
            Ranking.setMinTokenLen(3);
            Ranking.setQuantRate(0.5f);
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_MINLENGTH, Ranking.getMinTokenLen());
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_QUANTRATE, Ranking.getQuantRate());
        }

        if (post != null && post.containsKey("ResetRanking")) {
            Ranking.setMinTokenLen(3);
            Ranking.setQuantRate(0.5f);
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_MINLENGTH, Ranking.getMinTokenLen());
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_DOUBLEDETECTION_QUANTRATE, Ranking.getQuantRate());
        }

        final serverObjects prop = new serverObjects();
        prop.put("minTokenLen", Ranking.getMinTokenLen());
        prop.put("quantRate", Ranking.getQuantRate());


        return prop;
    }

}
