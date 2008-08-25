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

import de.anomic.http.httpRequestHeader;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.crypt;

public class Ranking_p {
	
	private static final int maxRankingRange = 16;
	
	private static final HashMap<String, String> rankingParameters = new HashMap<String, String>();
	static {
		rankingParameters.put(plasmaSearchRankingProfile.APP_DC_CREATOR, "Appearance In Author");
		rankingParameters.put(plasmaSearchRankingProfile.APP_DC_TITLE, "Appearance In Title");
		rankingParameters.put(plasmaSearchRankingProfile.APPEMPH, "Appearance In Emphasized Text");
		rankingParameters.put(plasmaSearchRankingProfile.APP_DC_DESCRIPTION, "Appearance In Reference/Anchor Name");
		rankingParameters.put(plasmaSearchRankingProfile.APP_DC_SUBJECT, "Appearance In Tags");
		rankingParameters.put(plasmaSearchRankingProfile.APPURL, "Appearance In URL");
		rankingParameters.put(plasmaSearchRankingProfile.AUTHORITY, "Authority of Domain");
		rankingParameters.put(plasmaSearchRankingProfile.CATHASAPP, "Category App, Appearance");
		rankingParameters.put(plasmaSearchRankingProfile.CATHASAUDIO, "Category Audio Appearance");
		rankingParameters.put(plasmaSearchRankingProfile.CATHASIMAGE, "Category Image Appearance");
		rankingParameters.put(plasmaSearchRankingProfile.CATHASVIDEO, "Category Video Appearance");
		rankingParameters.put(plasmaSearchRankingProfile.CATINDEXOF, "Category Index Page");
		rankingParameters.put(plasmaSearchRankingProfile.DATE, "Date");
		rankingParameters.put(plasmaSearchRankingProfile.DESCRCOMPINTOPLIST, "Description Comp. Appears In Toplist");
		rankingParameters.put(plasmaSearchRankingProfile.DOMLENGTH, "Domain Length");
		rankingParameters.put(plasmaSearchRankingProfile.HITCOUNT, "Hit Count");
		rankingParameters.put(plasmaSearchRankingProfile.LLOCAL, "Links To Local Domain");
		rankingParameters.put(plasmaSearchRankingProfile.LOTHER, "Links To Other Domain");
		rankingParameters.put(plasmaSearchRankingProfile.PHRASESINTEXT, "Phrases In Text");
		rankingParameters.put(plasmaSearchRankingProfile.POSINTEXT, "Position In Text");
		rankingParameters.put(plasmaSearchRankingProfile.POSOFPHRASE, "Position Of Phrase");
		rankingParameters.put(plasmaSearchRankingProfile.POSINPHRASE, "Position In Phrase");
		rankingParameters.put(plasmaSearchRankingProfile.PREFER, "Application Of Prefer Pattern");
		rankingParameters.put(plasmaSearchRankingProfile.TERMFREQUENCY, "Term Frequency");
        rankingParameters.put(plasmaSearchRankingProfile.URLCOMPINTOPLIST, "URL Component Appears In Toplist");
		rankingParameters.put(plasmaSearchRankingProfile.URLCOMPS, "URL Components");
		rankingParameters.put(plasmaSearchRankingProfile.URLLENGTH, "URL Length");
		rankingParameters.put(plasmaSearchRankingProfile.WORDDISTANCE, "Word Distance");
		rankingParameters.put(plasmaSearchRankingProfile.WORDSINTEXT, "Words In Text");
		rankingParameters.put(plasmaSearchRankingProfile.WORDSINTITLE, "Words In Title");
		rankingParameters.put(plasmaSearchRankingProfile.YBR, "YaCy Block Rank");
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
    
    private static void putRanking(final serverObjects prop, final plasmaSearchRankingProfile rankingProfile, final String prefix) {
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
    
    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        // clean up all search events
        plasmaSearchEvent.cleanupEvents(true);
        
        // case if no values are requested
        if ((post == null) || (sb == null)) {
            // we create empty entries for template strings
            final serverObjects prop = defaultValues();
            final plasmaSearchRankingProfile ranking;
            if(sb == null) ranking = new plasmaSearchRankingProfile(plasmaSearchQuery.CONTENTDOM_TEXT);
            else ranking = sb.getRanking();
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        if (post.containsKey("EnterRanking")) {
            final plasmaSearchRankingProfile ranking = new plasmaSearchRankingProfile("local", post.toString());
            sb.setConfig("rankingProfile", crypt.simpleEncode(ranking.toExternalString()));
            final serverObjects prop = defaultValues();
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        if (post.containsKey("ResetRanking")) {
            sb.setConfig("rankingProfile", "");
            final plasmaSearchRankingProfile ranking = new plasmaSearchRankingProfile(plasmaSearchQuery.CONTENTDOM_TEXT);
            final serverObjects prop = defaultValues();
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        final plasmaSearchRankingProfile localRanking = new plasmaSearchRankingProfile("local", post.toString());
        final serverObjects prop = new serverObjects();
        putRanking(prop, localRanking, "local");
        prop.putAll(localRanking.toExternalMap("local"));

        return prop;
    }

}
