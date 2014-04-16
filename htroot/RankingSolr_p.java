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
        sb.index.clearCaches(); // every time the ranking is changed we need to remove old orderings

        int profileNr = 0;
        if (post != null) profileNr = post.getInt("profileNr", profileNr);
        
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
                    } catch (final NumberFormatException e) {
                        continue;
                    }
                }
            }
            if (boostString.length() > 0) {
                String s = boostString.toString();
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_ + profileNr, s);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).updateBoosts(s);
            }
        }
        if (post != null && post.containsKey("ResetBoosts")) {
            String s = "url_paths_sxt^3.0,synonyms_sxt^0.5,title^5.0,text_t^1.0,host_s^6.0,h1_txt^5.0,url_file_name_tokens_t^4.0,h2_txt^3.0";
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_ + profileNr, s);
            sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).updateBoosts(s);
        }

        if (post != null && post.containsKey("EnterBQ")) {
            String bq = post.get("bq");
            if (bq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_ + profileNr, bq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostQuery(bq);
            }
        }
        if (post != null && post.containsKey("ResetBQ")) {
            String bq = "crawldepth_i:0^0.8 crawldepth_i:1^0.4";
            if (bq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_ + profileNr, bq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostQuery(bq);
            }
        }

        if (post != null && post.containsKey("EnterBF")) {
            String bf = post.get("bf");
            if (bf != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_ + profileNr, bf);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostFunction(bf);
            }
        }
        if (post != null && post.containsKey("ResetBF")) {
            String bf = "";
            if (bf != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_ + profileNr, bf);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostFunction(bf);
            }
        }

        final serverObjects prop = new serverObjects();
        int i = 0;
        
        Ranking ranking = sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr);
        for (SchemaDeclaration field: CollectionSchema.values()) {
            if (!field.isSearchable()) continue;
            prop.put("boosts_" + i + "_field", field.getSolrFieldName());
            Float boost = ranking.getFieldBoost(field);
            if (boost == null || boost.floatValue() <= 0.0f) {
                prop.put("boosts_" + i + "_checked", 0);
                prop.put("boosts_" + i + "_boost", "");
                prop.put("boosts_" + i + "_notinindexwarning", "0");
            } else {
                prop.put("boosts_" + i + "_checked", 1);
                prop.put("boosts_" + i + "_boost", boost.toString());
                prop.put("boosts_" + i + "_notinindexwarning", (sb.index.fulltext().getDefaultConfiguration().contains(field.name())? "0" : "1") );
            }
            prop.putHTML("boosts_" + i + "_comment", field.getComment());
            i++;
        }
        prop.put("boosts", i);
        prop.put("bq", ranking.getBoostQuery());
        prop.put("bf", ranking.getBoostFunction());

        for (int j = 0; j < 4; j++) {
            prop.put("profiles_" + j + "_nr", j);
            prop.put("profiles_" + j + "_name", sb.getConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTNAME_ + j, "N.N."));
            prop.put("profiles_" + j + "_selected", profileNr == j ? 1 : 0);
        }
        prop.put("profiles", 4);
        prop.put("profileNr", profileNr);
        
        return prop;
    }

}
