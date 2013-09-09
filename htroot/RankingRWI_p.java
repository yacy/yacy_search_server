// Ranking_p.java
// --------------
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 05.02.2006 on http://yacy.net
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;

public class RankingRWI_p {

	private static final int maxRankingRange = 16;

	private static final LinkedHashMap<String, String> rankingParameters = new LinkedHashMap<String, String>();
	static {
        rankingParameters.put(RankingProfile.APPEMPH, "Appearance In Emphasized Text;a higher ranking level prefers documents where the search word is emphasized");
        rankingParameters.put(RankingProfile.APPURL, "Appearance In URL;a higher ranking level prefers documents with urls that match the search word");
        rankingParameters.put(RankingProfile.APP_DC_CREATOR, "Appearance In Author;a higher ranking level prefers documents with authors that match the search word");
        rankingParameters.put(RankingProfile.APP_DC_DESCRIPTION, "Appearance In Reference/Anchor Name;a higher ranking level prefers documents where the search word matches in the description text");
        rankingParameters.put(RankingProfile.APP_DC_SUBJECT, "Appearance In Tags;a higher ranking level prefers documents where the search word is part of subject tags");
        rankingParameters.put(RankingProfile.APP_DC_TITLE, "Appearance In Title;a higher ranking level prefers documents with titles that match the search word");
        rankingParameters.put(RankingProfile.AUTHORITY, "Authority of Domain;a higher ranking level prefers documents from domains with a large number of matching documents");
        rankingParameters.put(RankingProfile.CATHASAPP, "Category App, Appearance;a higher ranking level prefers documents with embedded links to applications");
        rankingParameters.put(RankingProfile.CATHASAUDIO, "Category Audio Appearance;a higher ranking level prefers documents with embedded links to audio content");
        rankingParameters.put(RankingProfile.CATHASIMAGE, "Category Image Appearance;a higher ranking level prefers documents with embedded images");
        rankingParameters.put(RankingProfile.CATHASVIDEO, "Category Video Appearance;a higher ranking level prefers documents with embedded links to video files");
        rankingParameters.put(RankingProfile.CATINDEXOF, "Category Index Page;a higher ranking level prefers 'index of' (directory listings) pages");
        rankingParameters.put(RankingProfile.DATE, "Date;a higher ranking level prefers younger documents. The age of a document is measured using the date submitted by the remote server as document date");
        rankingParameters.put(RankingProfile.DOMLENGTH, "Domain Length;a higher ranking level prefers documents with a short domain name");
        rankingParameters.put(RankingProfile.HITCOUNT, "Hit Count;a higher ranking level prefers documents with a large number of matchings for the search word(s)");
        rankingParameters.put(RankingProfile.LANGUAGE, "Preferred Language;a higher ranking level prefers documents with a language that matches the browser language.");
        rankingParameters.put(RankingProfile.LLOCAL, "Links To Local Domain;a higher ranking level prefers documents with a high number of hyperlinks to the same domain as the matching document.");
        rankingParameters.put(RankingProfile.LOTHER, "Links To Other Domain;a higher ranking level prefers documents with a high number of hyperlinks to domains other than the matching document domain");
        rankingParameters.put(RankingProfile.PHRASESINTEXT, "Phrases In Text;a higher ranking level prefers documents with a large number of phrases (sentences) in the matching document.");
        rankingParameters.put(RankingProfile.POSINPHRASE, "Position In Phrase;a higher ranking level prefers documents with a word match position high in the matching phrase. The phrase match is the phrase (sentence) where the matching word appears first.");
        rankingParameters.put(RankingProfile.POSINTEXT, "Position In Text;a higher ranking level prefers documents with a word match position high in the document. This prefers documents where the search wort is at the beginning of a text.");
        rankingParameters.put(RankingProfile.POSOFPHRASE, "Position Of Phrase;a higher ranking level prefers documents with a phrase match position high in the document. The phrase match is the phrase (sentence) where the matching word appears first. This prefers documents where the search wort is at the beginning of a text.");
        rankingParameters.put(RankingProfile.TERMFREQUENCY, "Term Frequency;a higher ranking level prefers documents with a high (number of matching words)/(number of words in document) ratio. This is same ranking as used in lucene and old-age search engines as existed before the year 2000.");
        rankingParameters.put(RankingProfile.URLCOMPS, "URL Components;a higher ranking level prefers documents with a short number of url components. The number of url components is the number of (sub-) domains plus the number of (sub-) path elements in the file path.");
        rankingParameters.put(RankingProfile.URLLENGTH, "URL Length;a higher ranking level prefers documents with a short url (domain plus path)");
        rankingParameters.put(RankingProfile.WORDDISTANCE, "Word Distance;a higher ranking level prefers documents where the search words appear close together. This ranking parameter works like a NEAR operator in more-than-one word searches.");
        rankingParameters.put(RankingProfile.WORDSINTEXT, "Words In Text;a higher ranking level prefers documents with a large number of words. Be aware that this is a compensation of the term frequency parameter.");
        rankingParameters.put(RankingProfile.WORDSINTITLE, "Words In Title;a higher ranking level prefers documents with a large number of words in the document title.");
        
        rankingParameters.put(RankingProfile.URLCOMPINTOPLIST, "URL Component Appears In Toplist;a higher ranking level prefers documents with words in the url path that match words in the toplist. The toplist is generated dynamically from the search results using a statistic of the most used words. The toplist is a top-10 list of the most used words in URLs and document titles.");
        rankingParameters.put(RankingProfile.DESCRCOMPINTOPLIST, "Description Comp. Appears In Toplist;a higher ranking level prefers documents with words in the document description that match words in the toplist. The toplist is generated dynamically from the search results using a statistic of the most used words. The toplist is a top-10 list of the most used words in URLs and document titles.");
        rankingParameters.put(RankingProfile.PREFER, "Application Of Prefer Pattern;a higher ranking level prefers documents where the url matches the prefer pattern given in a search request.");
        rankingParameters.put(RankingProfile.CITATION, "Citation Rank;the more incoming links and the less outgoing links the better the ranking.");
	}

    private static serverObjects defaultValues() {
        final serverObjects prop = new serverObjects();
        prop.put("search", "");
        prop.put("num-results", "0");
        prop.put("excluded", "0");
        prop.put("combine", "0");
        prop.put("resultbottomline", "0");
        prop.put("localCount", "10");
        prop.put("localWDist", "999");
        //prop.put("globalChecked", "checked");
        prop.put("globalChecked", "0");
        prop.put("postsortChecked", "1");
        prop.put("localTime", "6");
        prop.put("results", "");
        prop.put("urlmaskoptions", "0");
        prop.putHTML("urlmaskoptions_urlmaskfilter", ".*");
        prop.put("jumpToCursor", "1");
        return prop;
    }

    private static void putRanking(final serverObjects prop, final RankingProfile rankingProfile, final String prefix) {
    	putRanking(prop, rankingProfile.preToExternalMap(prefix), prefix, "Pre");
    	putRanking(prop, rankingProfile.postToExternalMap(prefix), prefix, "Post");
    }

    private static void putRanking(final serverObjects prop, final Map<String, String> map, final String prefix, final String attrExtension) {
    	prop.put("attr" + attrExtension, map.size());
    	String key, description, name, info;
    	int i, j = 0, p;
    	for (final Entry<String, String> entry: map.entrySet()) {
            key = entry.getKey();
            description = rankingParameters.get(key.substring(prefix.length()));
            p = description.indexOf(';',0);
            if (p >= 0) {
                name = description.substring(0, p);
                info = description.substring(p + 1);
            } else {
                name = description;
                info = "";
            }
            prop.put("attr" + attrExtension + "_" + j + "_name", name);
            prop.put("attr" + attrExtension + "_" + j + "_info", info);
            prop.put("attr" + attrExtension + "_" + j + "_nameorg", key);
            prop.put("attr" + attrExtension + "_" + j + "_select", maxRankingRange);
            for (i=0; i<maxRankingRange; i++) {
                prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_nameorg", key);
                prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_value", i);
                try {
                    prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_checked",
                            (i == Integer.parseInt(entry.getValue())) ? "1" : "0");
                } catch (final NumberFormatException e) {
                    prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_checked", "0");
                }
            }
            prop.put("attr" + attrExtension + "_" + j + "_value",
                    Integer.parseInt(map.get(key)));
            j++;
        }
    }

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // clean up all search events
        SearchEventCache.cleanupEvents(true);

        // case if no values are requested
        if (post == null || sb == null) {
            // we create empty entries for template strings
            final serverObjects prop = defaultValues();
            final RankingProfile ranking;
            if (sb == null) ranking = new RankingProfile(Classification.ContentDomain.TEXT);
            else ranking = sb.getRanking();
            putRanking(prop, ranking, "local");
            return prop;
        }

        if (post.containsKey("EnterRanking")) {
            final RankingProfile ranking = new RankingProfile("local", post.toString());
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_RWI_PROFILE, crypt.simpleEncode(ranking.toExternalString()));
            final serverObjects prop = defaultValues();
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }

        if (post.containsKey("ResetRanking")) {
            sb.setConfig(SwitchboardConstants.SEARCH_RANKING_RWI_PROFILE, "");
            final RankingProfile ranking = new RankingProfile(Classification.ContentDomain.TEXT);
            final serverObjects prop = defaultValues();
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }

        final RankingProfile localRanking = new RankingProfile("local", post.toString());
        final serverObjects prop = new serverObjects();
        putRanking(prop, localRanking, "local");
        prop.putAll(localRanking.toExternalMap("local"));

        return prop;
    }

}
