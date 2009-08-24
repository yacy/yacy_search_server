// Ranking_p.java 
// --------------
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 05.02.2006 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2007-07-19 22:11:48 +0000 (Do, 19 Jul 2007) $
// $LastChangedRevision: 3995 $
// $LastChangedBy: orbiter $
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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import de.anomic.http.metadata.RequestHeader;
import de.anomic.search.QueryParams;
import de.anomic.search.RankingProfile;
import de.anomic.search.SearchEventCache;
import de.anomic.search.Switchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;

public class Ranking_p {
	
	private static final int maxRankingRange = 16;
	
	private static final HashMap<String, String> rankingParameters = new HashMap<String, String>();
	static {
		rankingParameters.put(RankingProfile.APP_DC_CREATOR, "Appearance In Author");
		rankingParameters.put(RankingProfile.APP_DC_TITLE, "Appearance In Title");
		rankingParameters.put(RankingProfile.APPEMPH, "Appearance In Emphasized Text");
		rankingParameters.put(RankingProfile.APP_DC_DESCRIPTION, "Appearance In Reference/Anchor Name");
		rankingParameters.put(RankingProfile.APP_DC_SUBJECT, "Appearance In Tags");
		rankingParameters.put(RankingProfile.APPURL, "Appearance In URL");
		rankingParameters.put(RankingProfile.AUTHORITY, "Authority of Domain");
		rankingParameters.put(RankingProfile.CATHASAPP, "Category App, Appearance");
		rankingParameters.put(RankingProfile.CATHASAUDIO, "Category Audio Appearance");
		rankingParameters.put(RankingProfile.CATHASIMAGE, "Category Image Appearance");
		rankingParameters.put(RankingProfile.CATHASVIDEO, "Category Video Appearance");
		rankingParameters.put(RankingProfile.CATINDEXOF, "Category Index Page");
		rankingParameters.put(RankingProfile.DATE, "Date");
		rankingParameters.put(RankingProfile.DESCRCOMPINTOPLIST, "Description Comp. Appears In Toplist");
		rankingParameters.put(RankingProfile.DOMLENGTH, "Domain Length");
		rankingParameters.put(RankingProfile.HITCOUNT, "Hit Count");
		rankingParameters.put(RankingProfile.LLOCAL, "Links To Local Domain");
		rankingParameters.put(RankingProfile.LOTHER, "Links To Other Domain");
		rankingParameters.put(RankingProfile.PHRASESINTEXT, "Phrases In Text");
		rankingParameters.put(RankingProfile.POSINTEXT, "Position In Text");
		rankingParameters.put(RankingProfile.POSOFPHRASE, "Position Of Phrase");
		rankingParameters.put(RankingProfile.POSINPHRASE, "Position In Phrase");
		rankingParameters.put(RankingProfile.PREFER, "Application Of Prefer Pattern");
		rankingParameters.put(RankingProfile.TERMFREQUENCY, "Term Frequency");
        rankingParameters.put(RankingProfile.URLCOMPINTOPLIST, "URL Component Appears In Toplist");
		rankingParameters.put(RankingProfile.URLCOMPS, "URL Components");
		rankingParameters.put(RankingProfile.URLLENGTH, "URL Length");
		rankingParameters.put(RankingProfile.WORDDISTANCE, "Word Distance");
		rankingParameters.put(RankingProfile.WORDSINTEXT, "Words In Text");
		rankingParameters.put(RankingProfile.WORDSINTITLE, "Words In Title");
		rankingParameters.put(RankingProfile.YBR, "YaCy Block Rank");
		rankingParameters.put(RankingProfile.LANGUAGE, "Preferred Language");
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
    	String key;
    	int i, j = 0;
    	for (final Entry<String, String> entry: map.entrySet()) {
    		key = entry.getKey();
    		prop.put("attr" + attrExtension + "_" + j + "_name", rankingParameters.get(key.substring(prefix.length())));
    		prop.put("attr" + attrExtension + "_" + j + "_nameorg", key);
    		prop.put("attr" + attrExtension + "_" + j + "_select", maxRankingRange);
    		for (i=0; i<maxRankingRange; i++) {
    			prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_nameorg", key);
    			prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_value", i);
    			try {
					prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_checked",
							(i == Integer.valueOf(entry.getValue()).intValue()) ? "1" : "0");
				} catch (final NumberFormatException e) {
					prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_checked", "0");
				}
    		}
    		prop.put("attr" + attrExtension + "_" + j + "_value",
    				Integer.valueOf(map.get(key)).intValue());
    		j++;
    	}
    }
    
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        // clean up all search events
        SearchEventCache.cleanupEvents(true);
        
        // case if no values are requested
        if ((post == null) || (sb == null)) {
            // we create empty entries for template strings
            final serverObjects prop = defaultValues();
            final RankingProfile ranking;
            if(sb == null) ranking = new RankingProfile(QueryParams.CONTENTDOM_TEXT);
            else ranking = sb.getRanking();
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        if (post.containsKey("EnterRanking")) {
            final RankingProfile ranking = new RankingProfile("local", post.toString());
            sb.setConfig("rankingProfile", crypt.simpleEncode(ranking.toExternalString()));
            final serverObjects prop = defaultValues();
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        if (post.containsKey("ResetRanking")) {
            sb.setConfig("rankingProfile", "");
            final RankingProfile ranking = new RankingProfile(QueryParams.CONTENTDOM_TEXT);
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
