
// DetailedSearch.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// $LastChangedDate: 2006-02-05 16:38:31 +0100 (So, 05 Feb 2006) $
// $LastChangedRevision: 1548 $
// $LastChangedBy: orbiter $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.
//
// You must compile this file with
// javac -classpath .:../classes index.java
// if the shell's current path is HTROOT

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.Map;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchTimingProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaSearchResults;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacySeed;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;

public class DetailedSearch {
	
	private static final int maxRankingRange = 16;
	
	private static final HashMap rankingParameters = new HashMap();
	static {
		rankingParameters.put(plasmaSearchRankingProfile.APPAUTHOR, "Appearance In Author");
		rankingParameters.put(plasmaSearchRankingProfile.APPDESCR, "Appearance In Description");
		rankingParameters.put(plasmaSearchRankingProfile.APPEMPH, "Appearance In Emphasized Text");
		rankingParameters.put(plasmaSearchRankingProfile.APPREF, "Appearance In Reference");
		rankingParameters.put(plasmaSearchRankingProfile.APPTAGS, "Appearance In Tags");
		rankingParameters.put(plasmaSearchRankingProfile.APPURL, "Appearance In URL");
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
		rankingParameters.put(plasmaSearchRankingProfile.PREFER, "Application Of Prefer Pattern");
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
        prop.put("num-results", 0);
        prop.put("excluded", 0);
        prop.put("combine", 0);
        prop.put("resultbottomline", 0);
        prop.put("localCount", 10);
        prop.put("localWDist", 999);
        //prop.put("globalChecked", "checked");
        prop.put("globalChecked", 0);
        prop.put("postsortChecked", 1);
        prop.put("localTime", 6);
        prop.put("results", "");
        prop.put("urlmaskoptions", 0);
        prop.put("urlmaskoptions_urlmaskfilter", ".*");
        prop.put("jumpToCursor", 1);
        return prop;
    }
    
    private static void putRanking(final serverObjects prop, final plasmaSearchRankingProfile rankingProfile, final String prefix) {
    	putRanking(prop, rankingProfile.preToExternalMap(prefix), prefix, "Pre");
    	putRanking(prop, rankingProfile.postToExternalMap(prefix), prefix, "Post");
    }
    
    private static void putRanking(final serverObjects prop, final Map ranking, final String prefix, final String attrExtension) {
    	prop.put("attr" + attrExtension, ranking.size());
    	Iterator it = ranking.keySet().iterator();
    	String key;
    	int i, j = 0;
    	while (it.hasNext()) {
    		key = (String)it.next();
    		prop.put("attr" + attrExtension + "_" + j + "_name", rankingParameters.get(key.substring(prefix.length())));
    		prop.put("attr" + attrExtension + "_" + j + "_nameorg", key);
    		prop.put("attr" + attrExtension + "_" + j + "_select", maxRankingRange);
    		for (i=0; i<maxRankingRange; i++) {
    			prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_nameorg", key);
    			prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_value", i);
    			try {
					prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_checked",
							(i == Integer.valueOf((String)ranking.get(key)).intValue()) ? 1 : 0);
				} catch (NumberFormatException e) {
					prop.put("attr" + attrExtension + "_" + j + "_select_" + i + "_checked", 0);
				}
    		}
    		prop.put("attr" + attrExtension + "_" + j + "_value",
    				Integer.valueOf((String)ranking.get(key)).intValue());
    		j++;
    	}
    }
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        boolean searchAllowed = sb.getConfigBool("publicSearchpage", true) || sb.verifyAuthentication(header, false);
        
        // case if no values are requested
        if ((post == null) || (env == null) || (!searchAllowed)) {
            // we create empty entries for template strings
            final serverObjects prop = defaultValues();
            plasmaSearchRankingProfile ranking = (sb.getConfig("rankingProfile", "").length() == 0) ? new plasmaSearchRankingProfile("text") : new plasmaSearchRankingProfile("", crypt.simpleDecode(sb.getConfig("rankingProfile", ""), null));
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        if (post.containsKey("EnterRanking")) {
            plasmaSearchRankingProfile ranking = new plasmaSearchRankingProfile("local", post.toString());
            sb.setConfig("rankingProfile", crypt.simpleEncode(ranking.toExternalString()));
            final serverObjects prop = defaultValues();
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        if (post.containsKey("ResetRanking")) {
            sb.setConfig("rankingProfile", "");
            plasmaSearchRankingProfile ranking = new plasmaSearchRankingProfile("text");
            final serverObjects prop = defaultValues();
            //prop.putAll(ranking.toExternalMap("local"));
            putRanking(prop, ranking, "local");
            return prop;
        }
        
        boolean global = post.get("global", "").equals("on");
        boolean postsort = post.get("postsort", "").equals("on");
        final boolean indexDistributeGranted = sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, "true").equals("true");
        final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
        if (!indexDistributeGranted || !indexReceiveGranted) { global = false; }
        
        int wdist = Integer.parseInt(post.get("localWDist", "999"));
        String querystring = post.get("search", "").trim();
        if ((querystring.length() > 2) && (querystring.charAt(0) == '"') && (querystring.charAt(querystring.length() - 1) == '"')) {
            querystring = querystring.substring(1, querystring.length() - 1).trim();
            wdist = 1;
        }
        if (sb.facilityDB != null) try { sb.facilityDB.update("zeitgeist", querystring, post); } catch (Exception e) {}
        final TreeSet[] query = plasmaSearchQuery.cleanQuery(querystring);
        // filter out stopwords
        final TreeSet filtered = kelondroMSetTools.joinConstructive(query[0], plasmaSwitchboard.stopwords);
        if (filtered.size() > 0) {
            kelondroMSetTools.excludeDestructive(query[0], plasmaSwitchboard.stopwords);
        }
        
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        // if a minus-button was hit, remove a special reference first
        if (post.containsKey("deleteref")) {
            if (!sb.verifyAuthentication(header, true)) {
                final serverObjects prop = new serverObjects();
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            final String delHash = post.get("deleteref", "");
            sb.wordIndex.removeWordReferences(query[0], delHash);
        }
        
        // prepare search order
        final int count = Integer.parseInt(post.get("localCount", "10"));
        final long searchtime = 1000 * Long.parseLong(post.get("localTime", "10"));
        final boolean yacyonline = ((yacyCore.seedDB != null) &&
                                    (yacyCore.seedDB.mySeed != null) &&
                                    (yacyCore.seedDB.mySeed.getPublicAddress() != null));

        String urlmask = "";
        if (post.containsKey("urlmask") && post.get("urlmask").equals("no")) {
            urlmask = ".*";
        } else {
            urlmask = (post.containsKey("urlmaskfilter")) ? (String) post.get("urlmaskfilter") : ".*";
        }

        // do the search
        plasmaSearchQuery thisSearch = new plasmaSearchQuery(querystring, plasmaCondenser.words2hashes(query[0]), plasmaCondenser.words2hashes(query[1]), wdist, "", plasmaSearchQuery.CONTENTDOM_TEXT, count, searchtime, urlmask,
                                                             ((global) && (yacyonline) && (!(env.getConfig("last-search","").equals(querystring)))) ? plasmaSearchQuery.SEARCHDOM_GLOBALDHT : plasmaSearchQuery.SEARCHDOM_LOCAL,
                                                             "", 20, plasmaSearchQuery.catchall_constraint);
        plasmaSearchRankingProfile localRanking = new plasmaSearchRankingProfile("local", post.toString());
        plasmaSearchTimingProfile localTiming  = new plasmaSearchTimingProfile(4 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
        plasmaSearchTimingProfile remoteTiming = new plasmaSearchTimingProfile(6 * thisSearch.maximumTime / 10, thisSearch.wantedResults);
        
        final serverObjects prop = new serverObjects();//sb.searchFromLocal(thisSearch, localRanking, localTiming, remoteTiming, postsort, (String) header.get("CLIENTIP"));
        plasmaSearchResults results = sb.searchFromLocal(thisSearch, localRanking, localTiming, remoteTiming, postsort, (String) header.get("CLIENTIP"));
        //prop.put("references", 0);
        URL wordURL=null;
        prop.put("num-results_totalcount", results.getTotalcount());
        prop.put("num-results_filteredcount", results.getFilteredcount());
        prop.put("num-results_orderedcount", results.getOrderedcount());
        prop.put("num-results_linkcount", results.getLinkcount());
        prop.put("type_results", 0);
        if(results.numResults()!=0){
            //we've got results
            prop.put("num-results_totalcount", results.getTotalcount());
            prop.put("num-results_filteredcount", results.getFilteredcount());
            prop.put("num-results_orderedcount", Integer.toString(results.getOrderedcount())); //why toString?
            prop.put("num-results_globalresults", results.getGlobalresults());
            for(int i=0;i<results.numResults();i++){
                plasmaSearchResults.searchResult result=results.getResult(i);
                prop.put("type_results_" + i + "_authorized_recommend", (yacyCore.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, yacyNewsPool.CATEGORY_SURFTIPP_ADD, "url", result.getUrl()) == null) ? 1 : 0);
                prop.put("type_results_" + i + "_authorized_recommend_deletelink", "/yacysearch.html?search=" + results.getFormerSearch() + "&Enter=Search&count=" + results.getQuery().wantedResults + "&order=" + crypt.simpleEncode(results.getRanking().toExternalString()) + "&resource=local&time=3&deleteref=" + result.getUrlhash() + "&urlmaskfilter=.*");
                prop.put("type_results_" + i + "_authorized_recommend_recommendlink", "/yacysearch.html?search=" + results.getFormerSearch() + "&Enter=Search&count=" + results.getQuery().wantedResults + "&order=" + crypt.simpleEncode(results.getRanking().toExternalString()) + "&resource=local&time=3&recommendref=" + result.getUrlhash() + "&urlmaskfilter=.*");
                prop.put("type_results_" + i + "_authorized_urlhash", result.getUrlhash());
                prop.put("type_results_" + i + "_description", result.getUrlentry().comp().title());
                prop.put("type_results_" + i + "_url", result.getUrl());
                prop.put("type_results_" + i + "_urlhash", result.getUrlhash());
                prop.put("type_results_" + i + "_urlhexhash", yacySeed.b64Hash2hexHash(result.getUrlhash()));
                prop.put("type_results_" + i + "_urlname", nxTools.shortenURLString(result.getUrlname(), 120));
                prop.put("type_results_" + i + "_date", plasmaSwitchboard.dateString(result.getUrlentry().moddate()));
                prop.put("type_results_" + i + "_ybr", plasmaSearchPreOrder.ybr(result.getUrlentry().hash()));
                prop.put("type_results_" + i + "_size", Long.toString(result.getUrlentry().size()));
                try {
                    prop.put("type_results_" + i + "_words", URLEncoder.encode(query[0].toString(),"UTF-8"));
                } catch (UnsupportedEncodingException e) {}
                prop.put("type_results_" + i + "_former", results.getFormerSearch());
                prop.put("type_results_" + i + "_rankingprops", result.getUrlentry().word().toPropertyForm() + ", domLengthEstimated=" + plasmaURL.domLengthEstimation(result.getUrlhash()) +
                        ((plasmaURL.probablyRootURL(result.getUrlhash())) ? ", probablyRootURL" : "") + 
                        (((wordURL = plasmaURL.probablyWordURL(result.getUrlhash(), query[0])) != null) ? ", probablyWordURL=" + wordURL.toNormalform() : ""));
                // adding snippet if available
                if (result.hasSnippet()) {
                    prop.put("type_results_" + i + "_snippet", 1);
                    prop.putASIS("type_results_" + i + "_snippet_text", result.getSnippet().getLineMarked(results.getQuery().queryHashes));//FIXME: the ASIS should not be needed, if there is no html in .java
                } else {
                    prop.put("type_results_" + i + "_snippet", 0);
                    prop.put("type_results_" + i + "_snippet_text", "");
                }
                prop.put("type_results", results.numResults());
                prop.put("references", results.getReferences());
                prop.put("num-results_linkcount", Integer.toString(results.numResults()));
            }
        }

        putRanking(prop, localRanking, "local");
        // remember the last search expression
        env.setConfig("last-search", querystring);
        // process result of search
        prop.put("type_resultbottomline", 0);
        if (filtered.size() > 0){
            prop.put("excluded", 1);
            prop.put("excluded_stopwords", filtered.toString());
        } else {
            prop.put("excluded", 0);
        }

        if (prop == null || prop.size() == 0) {
            prop.put("num-results", 0);
        } else {
            final Object[] references = (Object[]) prop.get("type_references", new String[0]);
            prop.put("num-results", 1);
            int hintcount = references.length;
            if (hintcount > 0) {
                if (hintcount > 16) { hintcount = 16; }
                prop.put("type_combine", 1);
                String word;
                for (int i = 0; i < hintcount; i++) {
                    word = (String) references[i];
                    if (word != null) {
                        prop.put("type_combine_words_" + i + "_word", word);
                        prop.put("type_combine_words_" + i + "_newsearch", post.get("search", "").replace(' ', '+') + "+" + word);
                        prop.put("type_combine_words_" + i + "_count", count);
                        prop.put("type_combine_words_" + i + "_ranking", localRanking.toExternalURLGet("local").toString());
                        prop.put("type_combine_words_" + i + "_resource", ((global) ? "global" : "local"));
                        prop.put("type_combine_words_" + i + "_time", (searchtime / 1000));
                    }
                    prop.put("type_combine_words", i);
                }
            }
        }

        if (urlmask.equals(".*")) {
            prop.put("urlmaskoptions", 0);
        } else {
            prop.put("urlmaskoptions", 1);
        }
        
        // if user is not authenticated, he may not vote for URLs
        int linkcount = Integer.parseInt(prop.get("num-results_linkcount", "0"));
        for (int i=0; i<linkcount; i++)
            prop.put("type_results_" + i + "_authorized", (authenticated) ? 1 : 0);

        prop.put("jumpToCursor", (linkcount > 0) ? 0 : 1);
        prop.put("urlmaskoptions_urlmaskfilter", urlmask);
        prop.put("type", "0");
        prop.put("localCount", count);
        prop.put("localWDist", wdist);
        prop.put("globalChecked", (global) ? 1 : 0);
        prop.put("postsortChecked", (postsort) ? 1 : 0);
        prop.put("localTime", searchtime/1000);
        prop.put("search", post.get("search", ""));
        prop.putAll(localRanking.toExternalMap("local"));
        
        // 'enrich search' variables
        prop.put("num-results_former", post.get("search", ""));
        prop.put("num-results_time", searchtime / 1000);
        prop.put("num-results_count", count);
        prop.put("num-results_resource", (global) ? "global" : "local");
        prop.put("num-results_ranking", localRanking.toExternalURLGet("local").toString());

        // return rewrite properties
        prop.putASIS("promoteSearchPageGreeting", env.getConfig("promoteSearchPageGreeting", ""));

        // adding some additional properties needed for the rss feed
        String hostName = (String) header.get("Host","localhost");
        if (hostName.indexOf(":") == -1) hostName += ":" + serverCore.getPortNr(env.getConfig("port","8080"));
        prop.put("rssYacyImageURL","http://" + hostName + "/env/grafics/yacy.gif");

        return prop;
    }

}
