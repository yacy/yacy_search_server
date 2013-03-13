/**
 *  RankingSolr_p
 *  Copyright 2012 by Michael Peter Christen
 *  First released 30.11.2012 at http://yacy.net
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

import java.util.Map;

import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class RankingSolr_p {


    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // clean up all search events
        SearchEventCache.cleanupEvents(true);
        sb.index.fulltext().clearCache(); // every time the ranking is changed we need to remove old orderings
        
        if (post != null && post.containsKey("EnterBoosts")) {
            StringBuilder boostString = new StringBuilder(); // SwitchboardConstants.SEARCH_RANKING_SOLR_BOOST;
            for (Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("boost")) {
                    String fieldName = entry.getKey().substring(6);
                    CollectionSchema field = CollectionSchema.valueOf(fieldName);
                    if (field == null) continue;
                    String fieldValue = entry.getValue();
                    if (fieldValue == null || fieldValue.length() == 0) continue;
                    try {
                        float boost = Float.parseFloat(fieldValue);
                        if (boostString.length() > 0) boostString.append(',');
                        boostString.append(field.getSolrFieldName()).append('^').append(Float.toString(boost));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
            }
            if (boostString.length() > 0) {
                String s = boostString.toString();
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_ + "0", s);
                sb.index.fulltext().getDefaultConfiguration().getRanking(0).updateBoosts(s);
            }
        }
        if (post != null && post.containsKey("ResetBoosts")) {
            String s = "text_t^2.0,url_paths_sxt^20.0,title^100.0,synonyms_sxt^1.0";
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_ + "0", s);
            sb.index.fulltext().getDefaultConfiguration().getRanking(0).updateBoosts(s);
        }

        if (post != null && post.containsKey("EnterBQ")) {
            String bq = post.get("bq");
            if (bq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_ + "0", bq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(0).setBoostQuery(bq);
            }
        }
        if (post != null && post.containsKey("ResetBQ")) {
            String bq = "fuzzy_signature_unique_b:true^100000.0";
            if (bq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_ + "0", bq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(0).setBoostQuery(bq);
            }
        }

        if (post != null && post.containsKey("EnterBF")) {
            String bf = post.get("bf");
            String mode = post.get("mode");
            if (bf != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_ + "0", bf);
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTIONMODE_ + "0", mode);
                sb.index.fulltext().getDefaultConfiguration().getRanking(0).setBoostFunction(bf);
                sb.index.fulltext().getDefaultConfiguration().getRanking(0).setMode(Ranking.BoostFunctionMode.valueOf(mode));
            }
        }
        if (post != null && post.containsKey("ResetBF")) {
            String bf = ""; //"div(add(1,references_i),pow(add(1,inboundlinkscount_i),1.6))";
            String mode = "add";
            if (bf != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_ + "0", bf);
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTIONMODE_ + "0", mode);
                sb.index.fulltext().getDefaultConfiguration().getRanking(0).setBoostFunction(bf);
                sb.index.fulltext().getDefaultConfiguration().getRanking(0).setMode(Ranking.BoostFunctionMode.valueOf(mode));
            }
        }

        final serverObjects prop = new serverObjects();
        int i = 0;
        
        Ranking ranking = sb.index.fulltext().getDefaultConfiguration().getRanking(0);
        for (SchemaDeclaration field: CollectionSchema.values()) {
            if (!field.isSearchable()) continue;
            prop.put("boosts_" + i + "_field", field.getSolrFieldName());
            Float boost = ranking.getFieldBoost(field);
            if (boost == null || boost.floatValue() <= 0.0f) {
                prop.put("boosts_" + i + "_checked", 0);
                prop.put("boosts_" + i + "_boost", "");
            } else {
                prop.put("boosts_" + i + "_checked", 1);
                prop.put("boosts_" + i + "_boost", boost.toString());
            }
            i++;
        }
        prop.put("boosts", i);
        prop.put("bq", ranking.getBoostQuery());
        prop.put("bf", ranking.getBoostFunction());
        prop.put("modeKey", ranking.getMethod() == Ranking.BoostFunctionMode.add ? "bf" : "boost");
        prop.put("add.checked", ranking.getMethod() == Ranking.BoostFunctionMode.add ? 1 : 0);
        prop.put("multiply.checked", ranking.getMethod() == Ranking.BoostFunctionMode.add ? 0 : 1);

        return prop;
    }

}
