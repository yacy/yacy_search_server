/**
 *  RankingSolr_p
 *  Copyright 2012 by Michael Peter Christen
 *  First released 30.11.2012 at https://yacy.net
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

package net.yacy.htroot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;

import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.schema.CollectionConfiguration;
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
            final StringBuilder boostString = new StringBuilder(); // SwitchboardConstants.SEARCH_RANKING_SOLR_BOOST;
            for (final Map.Entry<String, String> entry: post.entrySet()) {
                if (entry.getKey().startsWith("boost")) {
                    final String fieldName = entry.getKey().substring(6);
                    final CollectionSchema field = CollectionSchema.valueOf(fieldName);
                    if (field == null) continue;
                    final String fieldValue = entry.getValue();
                    if (fieldValue == null || fieldValue.length() == 0) continue;
                    try {
                        final float boost = Float.parseFloat(fieldValue);
                        if (boost > 0.0f) { // don't allow <= 0
                            if (boostString.length() > 0) boostString.append(',');
                            boostString.append(field.getSolrFieldName()).append('^').append(Float.toString(boost));
                        }
                    } catch (final NumberFormatException e) {
                        continue;
                    }
                }
            }
            if (boostString.length() > 0) {
                final String s = boostString.toString();
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_ + profileNr, s);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).updateBoosts(s);
            }
        }
        if (post != null && post.containsKey("ResetBoosts")) {
            final String s = "url_paths_sxt^3.0,synonyms_sxt^0.5,title^5.0,text_t^1.0,host_s^6.0,h1_txt^5.0,url_file_name_tokens_t^4.0,h2_txt^3.0,keywords^2.0,description_txt^1.5,author^1.0";
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFIELDS_ + profileNr, s);
            sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).updateBoosts(s);
        }

        if (post != null && post.containsKey("EnterBQ")) {
            final String bq = post.get(DisMaxParams.BQ);
            if (bq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_ + profileNr, bq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostQuery(bq);
            }
        }
        if (post != null && post.containsKey("ResetBQ")) {
            final String bq = "crawldepth_i:0^0.8\ncrawldepth_i:1^0.4";
            if (bq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTQUERY_ + profileNr, bq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostQuery(bq);
            }
        }

        if (post != null && post.containsKey("EnterFQ")) {
            final String fq = post.get(CommonParams.FQ);
            if (fq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_FILTERQUERY_ + profileNr, fq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setFilterQuery(fq);
            }
        }
        if (post != null && post.containsKey("ResetFQ")) {
            final String fq = ""; // i.e. "http_unique_b:true AND www_unique_b:true"
            if (fq != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_FILTERQUERY_ + profileNr, fq);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setFilterQuery(fq);
            }
        }

        if (post != null && post.containsKey("EnterBF")) {
            final String bf = post.get(DisMaxParams.BF);
            if (bf != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_ + profileNr, bf);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostFunction(bf);
            }
        }
        if (post != null && post.containsKey("ResetBF")) {
            final String bf = "";
            if (bf != null) {
                sb.setConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTFUNCTION_ + profileNr, bf);
                sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr).setBoostFunction(bf);
            }
        }

        final serverObjects prop = new serverObjects();
        int i = 0;

        final CollectionConfiguration colcfg = sb.index.fulltext().getDefaultConfiguration();
        final Ranking ranking = colcfg.getRanking(profileNr);
        for (final SchemaDeclaration field: CollectionSchema.values()) {
            if (!field.isSearchable()) continue;
            final Float boost = ranking.getFieldBoost(field);
            if (boost != null || colcfg.contains(field)) { // show only available or configured boost fields
                prop.put("boosts_" + i + "_field", field.getSolrFieldName());
                if (boost == null || boost.floatValue() <= 0.0f) {
                    prop.put("boosts_" + i + "_checked", 0);
                    prop.put("boosts_" + i + "_boost", "");
                    prop.put("boosts_" + i + "_notinindexwarning", "0");
                } else {
                    prop.put("boosts_" + i + "_checked", 1);
                    prop.put("boosts_" + i + "_boost", boost.toString());
                    prop.put("boosts_" + i + "_notinindexwarning", (colcfg.contains(field.name()) ? "0" : "1"));
                }
                prop.putHTML("boosts_" + i + "_comment", field.getComment());
                i++;
            }
        }
        prop.put("boosts", i);
        prop.put(CommonParams.FQ, ranking.getFilterQuery());
        prop.put(DisMaxParams.BQ, ranking.getBoostQuery());
        prop.put(DisMaxParams.BF, ranking.getBoostFunction());

        for (int j = 0; j < 4; j++) {
            prop.put("profiles_" + j + "_nr", j);
            prop.put("profiles_" + j + "_name", sb.getConfig(SwitchboardConstants.SEARCH_RANKING_SOLR_COLLECTION_BOOSTNAME_ + j, "N.N."));
            prop.put("profiles_" + j + "_selected", profileNr == j ? 1 : 0);
        }
        prop.put("profiles", 4);
        prop.put("profileNr", profileNr);

        // make boost hints for vocabularies
        Map<String, ReversibleScoreMap<String>> vocabularyFacet;
        try {
            vocabularyFacet = sb.index.fulltext().getDefaultConnector().getFacets(CollectionSchema.vocabularies_sxt.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM, 100, CollectionSchema.vocabularies_sxt.getSolrFieldName());
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            vocabularyFacet = new HashMap<>();
        }
        if (vocabularyFacet.size() == 0) {
            prop.put("boosthint", 0);
        } else {
            prop.put("boosthint", 1);
            prop.putHTML("boosthint_vocabulariesfield", CollectionSchema.vocabularies_sxt.getSolrFieldName());
            final ReversibleScoreMap<String> vokcounts = vocabularyFacet.values().iterator().next();
            final Collection<String> vocnames = vokcounts.keyList(true);
            prop.putHTML("boosthint_vocabulariesavailable", vocnames.toString());
            final ArrayList<String> voccountFields = new ArrayList<>();
            final ArrayList<String> voclogcountFields = new ArrayList<>();
            final ArrayList<String> voclogcountsFields = new ArrayList<>();
            final ArrayList<String> ff = new ArrayList<>();
            for (final String vocname: vocnames) {
                voccountFields.add(CollectionSchema.VOCABULARY_PREFIX + vocname + CollectionSchema.VOCABULARY_COUNT_SUFFIX);
                voclogcountFields.add(CollectionSchema.VOCABULARY_PREFIX + vocname + CollectionSchema.VOCABULARY_LOGCOUNT_SUFFIX);
                voclogcountsFields.add(CollectionSchema.VOCABULARY_PREFIX + vocname + CollectionSchema.VOCABULARY_LOGCOUNTS_SUFFIX);
            }
            ff.addAll(voclogcountFields);
            ff.addAll(voclogcountsFields);
            prop.putHTML("boosthint_vocabulariesvoccount", voccountFields.toString());
            prop.putHTML("boosthint_vocabulariesvoclogcount", voclogcountFields.toString());
            prop.putHTML("boosthint_vocabulariesvoclogcounts", voclogcountsFields.toString());
            final String[] facetfields = ff.toArray(new String[ff.size()]);
            int fc = 0;
            if (facetfields.length > 0) try {
                final LinkedHashMap<String, ReversibleScoreMap<String>> facets = sb.index.fulltext().getDefaultConnector().getFacets("*:*", 100, facetfields);
                facets.put(CollectionSchema.vocabularies_sxt.getSolrFieldName(), vokcounts);
                for (final Map.Entry<String, ReversibleScoreMap<String>> facetentry: facets.entrySet()) {
                    final ReversibleScoreMap<String> facetfieldmap = facetentry.getValue();
                    if (facetfieldmap.size() == 0) continue;
                    final TreeMap<String, Integer> statMap = new TreeMap<>();
                    for (final String k: facetfieldmap) statMap.put(k, facetfieldmap.get(k));
                    prop.put("boosthint_facets_" + fc + "_facetname", facetentry.getKey());
                    int c = 0; for (final Entry<String, Integer> entry: statMap.entrySet()) {
                        prop.put("boosthint_facets_" + fc + "_facet_" + c + "_key", entry.getKey());
                        prop.put("boosthint_facets_" + fc + "_facet_" + c + "_count", entry.getValue());
                        c++;
                    }
                    prop.put("boosthint_facets_" + fc + "_facet", c);
                    fc++;
                }
            } catch (final IOException e) {
            }
            prop.put("boosthint_facets", fc);
        }

        return prop;
    }

}
