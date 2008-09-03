// yacysearch.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
// You must compile this file with
// javac -classpath .:../classes yacysearch.java
// if the shell's current path is HTROOT

import java.util.HashMap;
import java.util.TreeSet;

import de.anomic.http.httpRequestHeader;
import de.anomic.index.indexURLReference;
import de.anomic.index.indexWord;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaProfiling;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverMemory;
import de.anomic.server.serverObjects;
import de.anomic.server.serverProfiling;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.yFormatter;
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacyURL;

public class yacysearch {

    public static serverObjects respond(final httpRequestHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        sb.localSearchLastAccess = System.currentTimeMillis();
        
        final boolean searchAllowed = sb.getConfigBool("publicSearchpage", true) || sb.verifyAuthentication(header, false);
        
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int display = (post == null) ? 0 : post.getInt("display", 0);
        if ((display == 1) && (!authenticated)) display = 0;
        final int input = (post == null) ? 2 : post.getInt("input", 2);
        String promoteSearchPageGreeting = env.getConfig("promoteSearchPageGreeting", "");
        if (env.getConfigBool("promoteSearchPageGreeting.useNetworkName", false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
        if (promoteSearchPageGreeting.length() == 0) promoteSearchPageGreeting = "P2P Web Search";
        final String client = header.get(httpRequestHeader.CONNECTION_PROP_CLIENTIP); // the search client who initiated the search
        
        // get query
        String querystring = (post == null) ? "" : post.get("query", post.get("search", "")).trim(); // SRU compliance
        final boolean fetchSnippets = (post != null && post.get("verify", "false").equals("true"));
        final serverObjects prop = new serverObjects();
        
        final boolean rss = (post == null) ? false : post.get("rss", "false").equals("true");
        prop.put("input_promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put("input_promoteSearchPageGreeting.homepage", sb.getConfig("promoteSearchPageGreeting.homepage", ""));
        prop.put("input_promoteSearchPageGreeting.smallImage", sb.getConfig("promoteSearchPageGreeting.smallImage", ""));
        if ((post == null) || (env == null) || (querystring.length() == 0) || (!searchAllowed)) {
            // we create empty entries for template strings
            prop.put("searchagain", "0");
            prop.put("input", input);
            prop.put("display", display);
            prop.put("input_input", input);
            prop.put("input_display", display);
            prop.put("input_former", "");
            prop.put("former", "");
            prop.put("input_count", "10");
            prop.put("input_offset", "0");
            prop.put("input_resource", "global");
            prop.put("input_urlmaskfilter", ".*");
            prop.put("input_prefermaskfilter", "");
            prop.put("input_indexof", "off");
            prop.put("input_constraint", "");
            prop.put("input_cat", "href");
            prop.put("input_depth", "0");
            prop.put("input_verify", "true");
            prop.put("input_contentdom", "text");
            prop.put("input_contentdomCheckText", "1");
            prop.put("input_contentdomCheckAudio", "0");
            prop.put("input_contentdomCheckVideo", "0");
            prop.put("input_contentdomCheckImage", "0");
            prop.put("input_contentdomCheckApp", "0");
            prop.put("excluded", "0");
            prop.put("results", "");
            prop.put("resultTable", "0");
            prop.put("num-results", searchAllowed ? "0" : "4");
            
            return prop;
        }

        // collect search attributes
        int itemsPerPage = Math.min((authenticated) ? 1000 : 10, post.getInt("maximumRecords", post.getInt("count", 10))); // SRU syntax with old property as alternative
        int offset = (post.hasValue("query") && post.hasValue("former") && !post.get("query","").equalsIgnoreCase(post.get("former",""))) ? 0 : post.getInt("startRecord", post.getInt("offset", 0));
        
        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean indexof = (post != null && post.get("indexof","").equals("on")); 
        String urlmask = "";
        if (post != null && post.containsKey("urlmask") && post.get("urlmask").equals("no")) {
            urlmask = ".*";
        } else {
            urlmask = (post != null && post.containsKey("urlmaskfilter")) ? (String) post.get("urlmaskfilter") : ".*";
        }
        String prefermask = (post == null ? "" : post.get("prefermaskfilter", ""));
        if ((prefermask.length() > 0) && (prefermask.indexOf(".*") < 0)) prefermask = ".*" + prefermask + ".*";

        kelondroBitfield constraint = (post != null && post.containsKey("constraint") && post.get("constraint", "").length() > 0) ? new kelondroBitfield(4, post.get("constraint", "______")) : null;
        if (indexof) {
            constraint = new kelondroBitfield(4);
            constraint.set(plasmaCondenser.flag_cat_indexof, true);
        }
        
        // SEARCH
        //final boolean indexDistributeGranted = sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, "true").equals("true");
        //final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
        //final boolean offline = yacyCore.seedDB.mySeed().isVirgin();
        final boolean clustersearch = sb.isRobinsonMode() &&
    									(sb.getConfig("cluster.mode", "").equals("privatecluster") ||
    									 sb.getConfig("cluster.mode", "").equals("publiccluster"));
        //if (offline || !indexDistributeGranted || !indexReceiveGranted) { global = false; }
        if (clustersearch) global = true; // switches search on, but search target is limited to cluster nodes
        
        // find search domain
        final int contentdomCode = plasmaSearchQuery.contentdomParser((post == null ? "text" : post.get("contentdom", "text")));
        
        // patch until better search profiles are available
        if ((contentdomCode != plasmaSearchQuery.CONTENTDOM_TEXT) && (itemsPerPage <= 32)) itemsPerPage = 32;
        
        // check the search tracker
        TreeSet<Long> trackerHandles = sb.localSearchTracker.get(client);
        if (trackerHandles == null) trackerHandles = new TreeSet<Long>();
        boolean block = false;
        if (global || fetchSnippets) {
            // in case that we do a global search or we want to fetch snippets, we check for DoS cases
            if (trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 3000)).size() > 1) try {
                Thread.sleep(3000);
                block = true;
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            if (trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 60000)).size() > 12) try {
                Thread.sleep(10000);
                block = true;
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            if (trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 600000)).size() > 36) try {
                Thread.sleep(30000);
                block = true;
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        if ((!block) && (post == null || post.get("cat", "href").equals("href"))) {
            
            // check available memory and clean up if necessary
            if (!serverMemory.request(8000000L, false)) {
                sb.webIndex.clearCache();
                plasmaSearchEvent.cleanupEvents(true);
            }
            
            final plasmaSearchRankingProfile ranking = sb.getRanking();
            final TreeSet<String>[] query = plasmaSearchQuery.cleanQuery(querystring); // converts also umlaute
            if ((query[0].contains("near")) && (querystring.indexOf("NEAR") >= 0)) {
            	query[0].remove("near");
            	ranking.coeff_worddistance = plasmaSearchRankingProfile.COEFF_MAX;
            }
            if ((query[0].contains("recent")) && (querystring.indexOf("RECENT") >= 0)) {
                query[0].remove("recent");
                ranking.coeff_date = plasmaSearchRankingProfile.COEFF_MAX;
            }
           
            int maxDistance = (querystring.indexOf('"') >= 0) ? maxDistance = query.length - 1 : Integer.MAX_VALUE;

            // filter out stopwords
            final TreeSet<String> filtered = kelondroMSetTools.joinConstructive(query[0], plasmaSwitchboard.stopwords);
            if (filtered.size() > 0) {
                kelondroMSetTools.excludeDestructive(query[0], plasmaSwitchboard.stopwords);
            }

            // if a minus-button was hit, remove a special reference first
            if (post != null && post.containsKey("deleteref")) {
                if (!sb.verifyAuthentication(header, true)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                
                // delete the index entry locally
                final String delHash = post.get("deleteref", ""); // urlhash
                sb.webIndex.removeWordReferences(query[0], delHash);

                // make new news message with negative voting
                final HashMap<String, String> map = new HashMap<String, String>();
                map.put("urlhash", delHash);
                map.put("vote", "negative");
                map.put("refid", "");
                sb.webIndex.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map));
            }

            // if a plus-button was hit, create new voting message
            if (post != null && post.containsKey("recommendref")) {
                if (!sb.verifyAuthentication(header, true)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                final String recommendHash = post.get("recommendref", ""); // urlhash
                final indexURLReference urlentry = sb.webIndex.getURL(recommendHash, null, 0);
                if (urlentry != null) {
                    final indexURLReference.Components comp = urlentry.comp();
                    plasmaParserDocument document;
                    document = plasmaSnippetCache.retrieveDocument(comp.url(), true, 5000, true, false);
                    if (document != null) {
                        // create a news message
                        final HashMap<String, String> map = new HashMap<String, String>();
                        map.put("url", comp.url().toNormalform(false, true).replace(',', '|'));
                        map.put("title", comp.dc_title().replace(',', ' '));
                        map.put("description", document.dc_title().replace(',', ' '));
                        map.put("author", document.dc_creator());
                        map.put("tags", document.dc_subject(' '));
                        sb.webIndex.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.webIndex.seedDB.mySeed(), yacyNewsPool.CATEGORY_SURFTIPP_ADD, map));
                        document.close();
                    }
                }
            }

            // prepare search properties
            //final boolean yacyonline = ((sb.webIndex.seedDB != null) && (sb.webIndex.seedDB.mySeed() != null) && (sb.webIndex.seedDB.mySeed().getPublicAddress() != null));
            final boolean globalsearch = (global) /* && (yacyonline)*/ && (sb.getConfigBool(plasmaSwitchboardConstants.INDEX_RECEIVE_ALLOW, false));
        
            // do the search
            final TreeSet<String> queryHashes = indexWord.words2hashes(query[0]);
            final plasmaSearchQuery theQuery = new plasmaSearchQuery(
        			querystring,
        			queryHashes,
        			indexWord.words2hashes(query[1]),
        			ranking,
                    maxDistance,
                    prefermask,
                    contentdomCode,
                    fetchSnippets,
                    itemsPerPage,
                    offset,
                    urlmask,
                    (clustersearch && globalsearch) ? plasmaSearchQuery.SEARCHDOM_CLUSTERALL :
                    ((globalsearch) ? plasmaSearchQuery.SEARCHDOM_GLOBALDHT : plasmaSearchQuery.SEARCHDOM_LOCAL),
                    "",
                    20,
                    constraint,
                    true,
                    yacyURL.TLD_any_zone_filter,
                    client,
                    authenticated);
            serverProfiling.update("SEARCH", new plasmaProfiling.searchEvent(theQuery.id(true), plasmaSearchEvent.INITIALIZATION, 0, 0));
            
            // tell all threads to do nothing for a specific time
            sb.intermissionAllThreads(10000);
        
            // filter out words that appear in bluelist
            theQuery.filterOut(plasmaSwitchboard.blueList);
            
            // log
            serverLog.logInfo("LOCAL_SEARCH", "INIT WORD SEARCH: " + theQuery.queryString + ":" + theQuery.queryHashes + " - " + theQuery.neededResults() + " links to be computed, " + theQuery.displayResults() + " lines to be displayed");
            RSSFeed.channels(RSSFeed.LOCALSEARCH).addMessage(new RSSMessage("Local Search Request", theQuery.queryString, ""));
            final long timestamp = System.currentTimeMillis();

            // create a new search event
            if (plasmaSearchEvent.getEvent(theQuery.id(false)) == null) {
                theQuery.setOffset(0); // in case that this is a new search, always start without a offset 
                offset = 0;
            }
            final plasmaSearchEvent theSearch = plasmaSearchEvent.getEvent(theQuery, ranking, sb.webIndex, sb.crawlResults, (sb.isRobinsonMode()) ? sb.clusterhashes : null, false);
            
            // generate result object
            //serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER ORDERING OF SEARCH RESULTS: " + (System.currentTimeMillis() - timestamp) + " ms");
            //serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER RESULT PREPARATION: " + (System.currentTimeMillis() - timestamp) + " ms");
                
            // calc some more cross-reference
            //serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER XREF PREPARATION: " + (System.currentTimeMillis() - timestamp) + " ms");

            // log
            serverLog.logInfo("LOCAL_SEARCH", "EXIT WORD SEARCH: " + theQuery.queryString + " - " +
                    (theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize()) + " links found, " +
                    (System.currentTimeMillis() - timestamp) + " ms");

            // prepare search statistics
            theQuery.resultcount = theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize();
            theQuery.searchtime = System.currentTimeMillis() - timestamp;
            theQuery.urlretrievaltime = theSearch.getURLRetrievalTime();
            theQuery.snippetcomputationtime = theSearch.getSnippetComputationTime();
            sb.localSearches.add(theQuery);
            
            // update the search tracker
            trackerHandles.add(theQuery.handle);
            sb.localSearchTracker.put(client, trackerHandles);
            
            final int totalcount = theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize();
            prop.put("num-results_offset", offset);
            prop.put("num-results_itemscount", "0");
            prop.put("num-results_itemsPerPage", itemsPerPage);
            prop.put("num-results_totalcount", yFormatter.number(totalcount, !rss));
            prop.put("num-results_globalresults", (globalsearch) ? "1" : "0");
            prop.put("num-results_globalresults_localResourceSize", yFormatter.number(theSearch.getRankingResult().getLocalResourceSize(), !rss));
            prop.put("num-results_globalresults_remoteResourceSize", yFormatter.number(theSearch.getRankingResult().getRemoteResourceSize(), !rss));
            prop.put("num-results_globalresults_remoteIndexCount", yFormatter.number(theSearch.getRankingResult().getRemoteIndexCount(), !rss));
            prop.put("num-results_globalresults_remotePeerCount", yFormatter.number(theSearch.getRankingResult().getRemotePeerCount(), !rss));
            
            // compose page navigation
            final StringBuffer resnav = new StringBuffer();
            final int thispage = offset / theQuery.displayResults();
            if (thispage == 0) resnav.append("&lt;&nbsp;"); else {
                resnav.append(navurla(thispage - 1, display, theQuery));
                resnav.append("<strong>&lt;</strong></a>&nbsp;");
            }
            final int numberofpages = Math.min(10, Math.max(thispage + 2, totalcount / theQuery.displayResults()));
            for (int i = 0; i < numberofpages; i++) {
                if (i == thispage) {
                    resnav.append("<strong>");
                    resnav.append(i + 1);
                    resnav.append("</strong>&nbsp;");
                } else {
                    resnav.append(navurla(i, display, theQuery));
                    resnav.append(i + 1);
                    resnav.append("</a>&nbsp;");
                }
            }
            if (thispage >= numberofpages) resnav.append("&gt;"); else {
                resnav.append(navurla(thispage + 1, display, theQuery));
                resnav.append("<strong>&gt;</strong></a>");
            }
            prop.put("num-results_resnav", resnav.toString());
        
            // generate the search result lines; they will be produced by another servlet
            for (int i = 0; i < theQuery.displayResults(); i++) {
                prop.put("results_" + i + "_item", offset + i);
                prop.put("results_" + i + "_eventID", theQuery.id(false));
            }
            prop.put("results", theQuery.displayResults());
            prop.put("resultTable", (contentdomCode <= 1) ? "0" : "1");
            prop.put("eventID", theQuery.id(false)); // for bottomline
            
            // process result of search
            if (filtered.size() > 0) {
                prop.put("excluded", "1");
                prop.putHTML("excluded_stopwords", filtered.toString());
            } else {
                prop.put("excluded", "0");
            }

            if (prop == null || prop.size() == 0) {
                if (post == null || post.get("query", post.get("search", "")).length() < 3) {
                    prop.put("num-results", "2"); // no results - at least 3 chars
                } else {
                    prop.put("num-results", "1"); // no results
                }
            } else {
                prop.put("num-results", "3");
            }

            prop.put("input_cat", "href");
            prop.put("input_depth", "0");

            // adding some additional properties needed for the rss feed
            String hostName = (String) header.get("Host", "localhost");
            if (hostName.indexOf(":") == -1) hostName += ":" + serverCore.getPortNr(env.getConfig("port", "8080"));
            prop.put("searchBaseURL", "http://" + hostName + "/yacysearch.html");
            prop.put("rssYacyImageURL", "http://" + hostName + "/env/grafics/yacy.gif");
        }
        
        prop.put("searchagain", global ? "1" : "0");
        prop.put("input", input);
        prop.put("display", display);
        prop.put("input_input", input);
        prop.put("input_display", display);
        prop.putHTML("input_former", querystring);
        prop.put("input_count", itemsPerPage);
        prop.put("input_offset", offset);
        prop.put("input_resource", global ? "global" : "local");
        prop.putHTML("input_urlmaskfilter", urlmask);
        prop.putHTML("input_prefermaskfilter", prefermask);
        prop.put("input_indexof", (indexof) ? "on" : "off");
        prop.put("input_constraint", (constraint == null) ? "" : constraint.exportB64());
        prop.put("input_verify", (fetchSnippets) ? "true" : "false");
        prop.put("input_contentdom", (post == null ? "text" : post.get("contentdom", "text")));
        prop.put("input_contentdomCheckText", (contentdomCode == plasmaSearchQuery.CONTENTDOM_TEXT) ? "1" : "0");
        prop.put("input_contentdomCheckAudio", (contentdomCode == plasmaSearchQuery.CONTENTDOM_AUDIO) ? "1" : "0");
        prop.put("input_contentdomCheckVideo", (contentdomCode == plasmaSearchQuery.CONTENTDOM_VIDEO) ? "1" : "0");
        prop.put("input_contentdomCheckImage", (contentdomCode == plasmaSearchQuery.CONTENTDOM_IMAGE) ? "1" : "0");
        prop.put("input_contentdomCheckApp", (contentdomCode == plasmaSearchQuery.CONTENTDOM_APP) ? "1" : "0");
        
        // for RSS: don't HTML encode some elements
        prop.putHTML("rss_query", querystring, true);
        prop.put("rss_queryenc", yacyURL.escape(querystring.replace(' ', '+')));

        sb.localSearchLastAccess = System.currentTimeMillis();
        
        // return rewrite properties
        return prop;
    }

    /**
     * generates the page navigation bar
     */
    private static String navurla(final int page, final int display, final plasmaSearchQuery theQuery) {
        return
        "<a href=\"yacysearch.html?display=" + display +
        "&amp;search=" + theQuery.queryString(true) +
        "&amp;maximumRecords="+ theQuery.displayResults() +
        "&amp;startRecord=" + (page * theQuery.displayResults()) +
        "&amp;resource=" + ((theQuery.isLocal()) ? "local" : "global") +
        "&amp;verify=" + ((theQuery.onlineSnippetFetch) ? "true" : "false") +
        "&amp;urlmaskfilter=" + theQuery.urlMask +
        "&amp;prefermaskfilter=" + theQuery.prefer +
        "&amp;cat=href&amp;constraint=" + ((theQuery.constraint == null) ? "" : theQuery.constraint.exportB64()) +
        "&amp;contentdom=" + theQuery.contentdom() +
        "&amp;former=" + theQuery.queryString(true) + "\">";
    }
}
