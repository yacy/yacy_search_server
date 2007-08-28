// yacysearch.java
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
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
// javac -classpath .:../classes yacysearch.java
// if the shell's current path is HTROOT

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.http.httpHeader;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchImages;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchProcessing;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverDate;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.yacy.yacySeed;

public class yacysearch {

    public static final int MAX_TOPWORDS = 24;

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;

        boolean searchAllowed = sb.getConfigBool("publicSearchpage", true) || sb.verifyAuthentication(header, false);
        
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int display = (post == null) ? 0 : post.getInt("display", 0);
        if ((display == 1) && (!authenticated)) display = 0;
        int input = (post == null) ? 2 : post.getInt("input", 2);
        String promoteSearchPageGreeting = env.getConfig("promoteSearchPageGreeting", "");
        if (promoteSearchPageGreeting.length() == 0) promoteSearchPageGreeting = "P2P WEB SEARCH";

        // case if no values are requested
        final String referer = (String) header.get("Referer");
        String querystring = (post == null) ? "" : post.get("search", "").trim();
        
        if ((post == null) || (env == null) || (querystring.length() == 0) || (!searchAllowed)) {

            // save referrer
            // System.out.println("HEADER=" + header.toString());
            if (referer != null) {
                URL url;
                try { url = new URL(referer); } catch (MalformedURLException e) { url = null; }
                if ((url != null) && (!serverDomains.isLocal(url))) {
                    final HashMap referrerprop = new HashMap();
                    referrerprop.put("count", "1");
                    referrerprop.put("clientip", header.get("CLIENTIP"));
                    referrerprop.put("useragent", header.get("User-Agent"));
                    referrerprop.put("date", (new serverDate()).toShortString(false));
                    if (sb.facilityDB != null) try { sb.facilityDB.update("backlinks", referer, referrerprop); } catch (IOException e) {}
                }
            }

            // we create empty entries for template strings
            final serverObjects prop = new serverObjects();
            prop.put("searchagain", 0);
            prop.put("input", input);
            prop.put("display", display);
            prop.put("input_input", input);
            prop.put("input_display", display);
            prop.putASIS("input_promoteSearchPageGreeting", promoteSearchPageGreeting);
            prop.put("input_former", "");
            prop.put("former", "");
            prop.put("input_count", 10);
            prop.put("input_resource", "global");
            prop.put("input_time", 6);
            prop.put("input_urlmaskfilter", ".*");
            prop.put("input_prefermaskfilter", "");
            prop.put("input_indexof", "off");
            prop.put("input_constraint", plasmaSearchQuery.catchall_constraint.exportB64());
            prop.put("input_cat", "href");
            prop.put("input_depth", "0");
            prop.put("input_contentdom", "text");
            prop.put("input_contentdomCheckText", 1);
            prop.put("input_contentdomCheckAudio", 0);
            prop.put("input_contentdomCheckVideo", 0);
            prop.put("input_contentdomCheckImage", 0);
            prop.put("input_contentdomCheckApp", 0);
            prop.put("type", 0);
            prop.put("type_excluded", 0);
            prop.put("type_combine", 0);
            prop.put("type_results", "");
            prop.put("num-results", (searchAllowed) ? 0 : 6);
            
            return prop;
        }

        // collect search attributes
        int maxDistance = Integer.MAX_VALUE;
        
        if ((querystring.length() > 2) && (querystring.charAt(0) == '"') && (querystring.charAt(querystring.length() - 1) == '"')) {
            querystring = querystring.substring(1, querystring.length() - 1).trim();
            maxDistance = 1;
        }
        if (sb.facilityDB != null) try { sb.facilityDB.update("zeitgeist", querystring, post); } catch (Exception e) {}

        int count = Integer.parseInt(post.get("count", "10"));
        boolean global = (post == null) ? true : post.get("resource", "global").equals("global");
        final boolean indexof = post.get("indexof","").equals("on"); 
        final long searchtime = 1000 * Long.parseLong(post.get("time", "10"));
        String urlmask = "";
        if (post.containsKey("urlmask") && post.get("urlmask").equals("no")) {
            urlmask = ".*";
        } else {
            urlmask = (post.containsKey("urlmaskfilter")) ? (String) post.get("urlmaskfilter") : ".*";
        }
        String prefermask = post.get("prefermaskfilter", "");
        if ((prefermask.length() > 0) && (prefermask.indexOf(".*") < 0)) prefermask = ".*" + prefermask + ".*";

        kelondroBitfield constraint = post.containsKey("constraint") ? new kelondroBitfield(4, post.get("constraint", "______")) : plasmaSearchQuery.catchall_constraint;
        if (indexof) {
            constraint = new kelondroBitfield();
            constraint.set(plasmaCondenser.flag_cat_indexof, true);
        }
        
        // SEARCH
        final boolean indexDistributeGranted = sb.getConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, "true").equals("true");
        final boolean indexReceiveGranted = sb.getConfig("allowReceiveIndex", "true").equals("true");
        final boolean offline = yacyCore.seedDB.mySeed.isVirgin();
        final boolean clustersearch = sb.isRobinsonMode() &&
    									(sb.getConfig("cluster.mode", "").equals("privatecluster") ||
    									 sb.getConfig("cluster.mode", "").equals("publiccluster"));
        if (offline || !indexDistributeGranted || !indexReceiveGranted) { global = false; }
        if (clustersearch) global = true; // switches search on, but search target is limited to cluster nodes
        
        // find search domain
        int contentdomCode = plasmaSearchQuery.contentdomParser(post.get("contentdom", "text"));
        
        // patch until better search profiles are available
        if ((contentdomCode != plasmaSearchQuery.CONTENTDOM_TEXT) && (count <= 10)) count = 30;
        
        serverObjects prop = new serverObjects();
        if (post.get("cat", "href").equals("href")) {

        final TreeSet[] query = plasmaSearchQuery.cleanQuery(querystring); // converts also umlaute
        // filter out stopwords
        final TreeSet filtered = kelondroMSetTools.joinConstructive(query[0], plasmaSwitchboard.stopwords);
        if (filtered.size() > 0) {
            kelondroMSetTools.excludeDestructive(query[0], plasmaSwitchboard.stopwords);
        }

        // if a minus-button was hit, remove a special reference first
        if (post.containsKey("deleteref")) {
            if (!sb.verifyAuthentication(header, true)) {
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
                
            // delete the index entry locally
            final String delHash = post.get("deleteref", ""); // urlhash
            sb.wordIndex.removeWordReferences(query[0], delHash);

            // make new news message with negative voting
            HashMap map = new HashMap();
            map.put("urlhash", delHash);
            map.put("vote", "negative");
            map.put("refid", "");
            yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map));
        }

        // if a plus-button was hit, create new voting message
        if (post.containsKey("recommendref")) {
            if (!sb.verifyAuthentication(header, true)) {
                prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                return prop;
            }
            final String recommendHash = post.get("recommendref", ""); // urlhash
            indexURLEntry urlentry = sb.wordIndex.loadedURL.load(recommendHash, null);
            if (urlentry != null) {
                indexURLEntry.Components comp = urlentry.comp();
                plasmaParserDocument document;
                document = plasmaSnippetCache.retrieveDocument(comp.url(), true, 5000, true);
                if (document != null) {
                    // create a news message
                    HashMap map = new HashMap();
                    map.put("url", comp.url().toNormalform(false, true).replace(',', '|'));
                    map.put("title", comp.title().replace(',', ' '));
                    map.put("description", ((document == null) ? comp.title() : document.getTitle()).replace(',', ' '));
                    map.put("author", ((document == null) ? "" : document.getAuthor()));
                    map.put("tags", ((document == null) ? "" : document.getKeywords(' ')));
                    yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_SURFTIPP_ADD, map));
                    document.close();
                }
            }
        }

        // prepare search properties
        final boolean yacyonline = ((yacyCore.seedDB != null) && (yacyCore.seedDB.mySeed != null) && (yacyCore.seedDB.mySeed.getPublicAddress() != null));
        final boolean globalsearch = (global) && (yacyonline);
        
        // do the search
        TreeSet queryHashes = plasmaCondenser.words2hashes(query[0]);
        plasmaSearchQuery theQuery = new plasmaSearchQuery(
        			querystring,
        			queryHashes,
        			plasmaCondenser.words2hashes(query[1]),
                    maxDistance,
                    prefermask,
                    contentdomCode,
                    count,
                    searchtime,
                    urlmask,
                    (clustersearch && globalsearch) ? plasmaSearchQuery.SEARCHDOM_CLUSTERALL :
                    ((globalsearch) ? plasmaSearchQuery.SEARCHDOM_GLOBALDHT : plasmaSearchQuery.SEARCHDOM_LOCAL),
                    "",
                    20,
                    constraint);
        plasmaSearchRankingProfile ranking = (sb.getConfig("rankingProfile", "").length() == 0) ? new plasmaSearchRankingProfile(contentdomCode) : new plasmaSearchRankingProfile("", crypt.simpleDecode(sb.getConfig("rankingProfile", ""), null));
        plasmaSearchProcessing localTiming = new plasmaSearchProcessing(4 * theQuery.maximumTime / 10, theQuery.wantedResults);
        plasmaSearchProcessing remoteTiming = new plasmaSearchProcessing(6 * theQuery.maximumTime / 10, theQuery.wantedResults);

        //**
        //searchFromLocal(thisSearch, ranking, localTiming, remoteTiming, true, (String) header.get("CLIENTIP"))
        String client = (String) header.get("CLIENTIP"); // the search client who initiated the search
        
        // tell all threads to do nothing for a specific time
        sb.intermissionAllThreads(2 * theQuery.maximumTime);
        
        // filter out words that appear in bluelist
        theQuery.filterOut(plasmaSwitchboard.blueList);
            
        // log
        serverLog.logInfo("LOCAL_SEARCH", "INIT WORD SEARCH: " + theQuery.queryString + ":" + theQuery.queryHashes + " - " + theQuery.wantedResults + " links, " + (theQuery.maximumTime / 1000) + " seconds");
        long timestamp = System.currentTimeMillis();

        // create a new search event
        String wrongregex = null;
        plasmaSearchEvent theSearch = plasmaSearchEvent.getEvent(theQuery, ranking, localTiming, remoteTiming, sb.wordIndex, (sb.isRobinsonMode()) ? sb.clusterhashes : null, false, null);
            
        // generate result object
        serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER ORDERING OF SEARCH RESULTS: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
        ArrayList accu = theSearch.computeResults(plasmaSwitchboard.blueList, true);
        serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER RESULT PREPARATION: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
                
        // calc some more cross-reference
        long remainingTime = theQuery.maximumTime - (System.currentTimeMillis() - timestamp);
        if (remainingTime < 0) remainingTime = 1000;
        //Object[] ws = acc.getReferences(16);
        serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER XREF PREPARATION: " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");

        // log
        serverLog.logInfo("LOCAL_SEARCH", "EXIT WORD SEARCH: " + theQuery.queryString + " - " +
                    (theSearch.getLocalCount() + theSearch.getGlobalCount()) + " links found, " +
                    theSearch.filteredCount() + " links filtered, " +
                    accu.size() + " links ordered, " +
                    ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");

        // prepare search statistics
        Long trackerHandle = new Long(System.currentTimeMillis());
        HashMap searchProfile = theQuery.resultProfile(accu.size(), System.currentTimeMillis() - timestamp);
        searchProfile.put("querystring", theQuery.queryString);
        searchProfile.put("time", trackerHandle);
        searchProfile.put("host", client);
        searchProfile.put("offset", new Integer(0));
        searchProfile.put("results", accu);
        sb.localSearches.add(searchProfile);
        TreeSet handles = (TreeSet) sb.localSearchTracker.get(client);
        if (handles == null) handles = new TreeSet();
        handles.add(trackerHandle);
        sb.localSearchTracker.put(client, handles);
        
        //prop=sb.searchFromLocal(thisSearch, ranking, localTiming, remoteTiming, true, (String) header.get("CLIENTIP"));
        prop = new serverObjects();
        //prop.put("references", 0);
        prop.put("num-results_totalcount", theSearch.getLocalCount() + theSearch.getGlobalCount());
        prop.put("num-results_filteredcount", theSearch.filteredCount());
        prop.put("num-results_orderedcount", accu.size());
        prop.put("num-results_globalresults", (theSearch.getGlobalCount() == 0) ? 0 : 1);
        prop.put("num-results_globalresults_globalcount", theSearch.getGlobalCount());
        prop.put("num-results_linkcount", 0);
        
        /*
        for (int i = 0; i < theQuery.wantedResults; i++) {
            prop.put("type_results_" + i + "_item", i);
            prop.put("type_results_" + i + "_eventID", theQuery.id());
        }
        prop.put("type_results", theQuery.wantedResults);
        */
        //------------------------
        
        prop.put("type_results", 0);
        URL wordURL=null;
        for (int i = 0; i < accu.size(); i++) {
            plasmaSearchEvent.Entry result = (plasmaSearchEvent.Entry) accu.get(i);
            prop.put("type_results_" + i + "_authorized_recommend", (yacyCore.newsPool.getSpecific(yacyNewsPool.OUTGOING_DB, yacyNewsPool.CATEGORY_SURFTIPP_ADD, "url", result.urlstring()) == null) ? 1 : 0);
            prop.put("type_results_" + i + "_authorized_recommend_deletelink", "/yacysearch.html?search=" + theQuery.queryString + "&Enter=Search&count=" + theQuery.wantedResults + "&order=" + crypt.simpleEncode(ranking.toExternalString()) + "&resource=local&time=3&deleteref=" + result.hash() + "&urlmaskfilter=.*");
            prop.put("type_results_" + i + "_authorized_recommend_recommendlink", "/yacysearch.html?search=" + theQuery.queryString + "&Enter=Search&count=" + theQuery.wantedResults + "&order=" + crypt.simpleEncode(ranking.toExternalString()) + "&resource=local&time=3&recommendref=" + result.hash() + "&urlmaskfilter=.*");
            prop.put("type_results_" + i + "_authorized_urlhash", result.hash());
            prop.put("type_results_" + i + "_description", result.title());
            prop.put("type_results_" + i + "_url", result.urlstring());
            int port=result.url().getPort();
            URL faviconURL;
            try {
                faviconURL = new URL(result.url().getProtocol() + "://" + result.url().getHost() + ((port != -1) ? (":" + String.valueOf(port)) : "") + "/favicon.ico");
            } catch (MalformedURLException e1) {
                continue;
            }
            prop.put("type_results_" + i + "_faviconCode", sb.licensedURLs.aquireLicense(faviconURL)); // aquire license for favicon url loading
            prop.put("type_results_" + i + "_urlhash", result.hash());
                prop.put("type_results_" + i + "_urlhexhash", yacySeed.b64Hash2hexHash(result.hash()));
                prop.put("type_results_" + i + "_urlname", nxTools.shortenURLString(result.urlname(), 120));
                prop.put("type_results_" + i + "_date", plasmaSwitchboard.dateString(result.modified()));
                prop.put("type_results_" + i + "_ybr", plasmaSearchPreOrder.ybr(result.hash()));
                prop.put("type_results_" + i + "_size", Long.toString(result.filesize()));
                try {
                    prop.put("type_results_" + i + "_words", URLEncoder.encode(query[0].toString(),"UTF-8"));
                } catch (UnsupportedEncodingException e) {}
                prop.put("type_results_" + i + "_former", querystring);
                prop.put("type_results_" + i + "_rankingprops", result.word().toPropertyForm() + ", domLengthEstimated=" + plasmaURL.domLengthEstimation(result.hash()) +
                        ((plasmaURL.probablyRootURL(result.hash())) ? ", probablyRootURL" : "") + 
                        (((wordURL = plasmaURL.probablyWordURL(result.hash(), query[0])) != null) ? ", probablyWordURL=" + wordURL.toNormalform(false, true) : ""));
                // adding snippet if available
                if (result.hasSnippet()) {
                    prop.put("type_results_" + i + "_snippet", 1);
                    prop.putASIS("type_results_" + i + "_snippet_text", result.textSnippet().getLineMarked(theQuery.queryHashes));//FIXME: the ASIS should not be needed, if there is no html in .java
                } else {                	
                	if (post.containsKey("fetchSnippet")) {
                		// snippet fetch timeout
                        int textsnippet_timeout = Integer.parseInt(env.getConfig("timeout_media", "10000"));
                        
                        // boolean line_end_with_punctuation
                        boolean pre = post.get("pre", "false").equals("true");
                        
                        // if 'remove' is set to true, then RWI references to URLs that do not have the snippet are removed
                        boolean remove = post.get("remove", "false").equals("true");
                        
                        plasmaSnippetCache.TextSnippet snippet = plasmaSnippetCache.retrieveTextSnippet(
                        		result.url(), 
                        		queryHashes, 
                        		true, 
                        		pre, 
                        		260, 
                        		textsnippet_timeout
                        );
                                                	
                        if (snippet.getErrorCode() < 11) {
                            // no problems occurred
                            //prop.put("text", (snippet.exists()) ? snippet.getLineMarked(queryHashes) : "unknown");
                            prop.putASIS("type_results_" + i + "_snippet_text", (snippet.exists()) ? snippet.getLineMarked(queryHashes) : "unknown");
                        } else {
                            // problems with snippet fetch
                           prop.put("type_results_" + i + "_snippet_text", (remove) ? plasmaSnippetCache.failConsequences(snippet, theQuery.id()) : snippet.getError());
                        }          
                        prop.put("type_results_" + i + "_snippet", 1);
                	} else {
                		// no snippet available (will be fetched later via ajax)
                		prop.put("type_results_" + i + "_snippet", 0);
                		prop.put("type_results_" + i + "_snippet_text", "");
                	}
                }
                prop.put("type_results", accu.size());
                prop.put("num-results_linkcount", Integer.toString(accu.size()));
            }
            
            //------------------------
        
            // process result of search
            if (filtered.size() > 0) {
                prop.put("excluded", 1);
                prop.put("excluded_stopwords", filtered.toString());
            } else {
                prop.put("excluded", 0);
            }

            if (prop == null || prop.size() == 0) {
                if (post.get("search", "").length() < 3) {
                    prop.put("num-results", 2); // no results - at least 3 chars
                } else {
                    prop.put("num-results", 1); // no results
                }
            } else {
                final int totalcount = prop.getInt("num-results_totalcount", 0);
                if (totalcount >= 10) {
                    final Object[] references = (Object[]) prop.get( "references", new String[0]);
                    prop.put("num-results", 5);
                    int hintcount = references.length;
                    if (hintcount > 0) {
                        prop.put("type_combine", 1);
                        // get the topwords
                        final TreeSet topwords = new TreeSet(kelondroNaturalOrder.naturalOrder);
                        String tmp = "";
                        for (int i = 0; i < hintcount; i++) {
                            tmp = (String) references[i];
                            if (tmp.matches("[a-z]+")) {
                                topwords.add(tmp);
                            }
                        }

                        // filter out the badwords
                        final TreeSet filteredtopwords = kelondroMSetTools.joinConstructive(topwords, plasmaSwitchboard.badwords);
                        if (filteredtopwords.size() > 0) {
                            kelondroMSetTools.excludeDestructive(topwords, plasmaSwitchboard.badwords);
                        }

                        // avoid stopwords being topwords
                        if (env.getConfig("filterOutStopwordsFromTopwords", "true").equals("true")) {
                            if ((plasmaSwitchboard.stopwords != null) && (plasmaSwitchboard.stopwords.size() > 0)) {
                                kelondroMSetTools.excludeDestructive(topwords, plasmaSwitchboard.stopwords);
                            }
                        }
		 				
                        String word;
                        hintcount = 0;
                        final Iterator iter = topwords.iterator();
                        while (iter.hasNext()) {
                            word = (String) iter.next();
                            if (word != null) {
                                prop.put("type_combine_words_" + hintcount + "_word", word);
                                prop.put("type_combine_words_" + hintcount + "_newsearch", post.get("search", "").replace(' ', '+') + "+" + word);
                                prop.put("type_combine_words_" + hintcount + "_count", count);
                                prop.put("type_combine_words_" + hintcount + "_resource", ((global) ? "global" : "local"));
                                prop.put("type_combine_words_" + hintcount + "_time", (searchtime / 1000));
                            }
                            prop.put("type_combine_words", hintcount);
                            if (hintcount++ > MAX_TOPWORDS) {
                                break;
                            }
                        }
                    }
                } else {
                    if (wrongregex != null) {
                        prop.put("num-results_wrong_regex", wrongregex);
                        prop.put("num-results", 4);
                    } else if (totalcount == 0) {
                        prop.put("num-results", 3); // long
                    } else {
                        prop.put("num-results", 5);
                    }
                }
            }

            prop.put("type", (theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_TEXT) ? 0 : ((theQuery.contentdom == plasmaSearchQuery.CONTENTDOM_IMAGE) ? 2 : 1));
            if (prop.getInt("type", 0) == 1) prop.put("type_mediatype", post.get("contentdom", "text"));
            prop.put("input_cat", "href");
            prop.put("input_depth", "0");
            prop.put("type_eventID", theQuery.id()); 

            // adding some additional properties needed for the rss feed
            String hostName = (String) header.get("Host", "localhost");
            if (hostName.indexOf(":") == -1) hostName += ":" + serverCore.getPortNr(env.getConfig("port", "8080"));
            prop.put("rssYacyImageURL", "http://" + hostName + "/env/grafics/yacy.gif");
        }

        if (post.get("cat", "href").equals("image")) {

            int depth = post.getInt("depth", 0);
            int columns = post.getInt("columns", 6);
            URL url = null;
            try {url = new URL(post.get("url", ""));} catch (MalformedURLException e) {}
            plasmaSearchImages si = new plasmaSearchImages(6000, url, depth);
            Iterator i = si.entries();
            htmlFilterImageEntry ie;
            int line = 0;
            while (i.hasNext()) {
                int col = 0;
                for (col = 0; col < columns; col++) {
                    if (!i.hasNext()) break;
                    ie = (htmlFilterImageEntry) i.next();
                    String urls = ie.url().toString();
                    String name = "";
                    int p = urls.lastIndexOf('/');
                    if (p > 0) name = urls.substring(p + 1);
                    prop.put("type_results_" + line + "_line_" + col + "_url", urls);
                    prop.put("type_results_" + line + "_line_" + col + "_name", name);
                }
                prop.put("type_results_" + line + "_line", col);
                line++;
            }
            prop.put("type_results", line);

            prop.put("type", 3); // set type of result: image list
            prop.put("input_cat", "href");
            prop.put("input_depth", depth);
        }

        // if user is not authenticated, he may not vote for URLs
        int linkcount = Integer.parseInt(prop.get("num-results_linkcount", "0"));
        for (int i=0; i<linkcount; i++) {
            prop.put("type_results_" + i + "_authorized", (authenticated) ? 1 : 0);
        }
        
        prop.put("searchagain", (global) ? 1 : 0);
        prop.put("input", input);
        prop.put("display", display);
        prop.put("input_input", input);
        prop.put("input_display", display);
        prop.putASIS("input_promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put("input_former", post.get("search", ""));
        prop.put("former", post.get("search", ""));
        prop.put("input_count", count);
        prop.put("input_resource", (global) ? "global" : "local");
        prop.put("input_time", searchtime / 1000);
        prop.put("input_urlmaskfilter", urlmask);
        prop.put("input_prefermaskfilter", prefermask);
        prop.put("input_indexof", (indexof) ? "on" : "off");
        prop.put("input_constraint", constraint.exportB64());
        prop.put("input_contentdom", post.get("contentdom", "text"));
        prop.put("input_contentdomCheckText", (contentdomCode == plasmaSearchQuery.CONTENTDOM_TEXT) ? 1 : 0);
        prop.put("input_contentdomCheckAudio", (contentdomCode == plasmaSearchQuery.CONTENTDOM_AUDIO) ? 1 : 0);
        prop.put("input_contentdomCheckVideo", (contentdomCode == plasmaSearchQuery.CONTENTDOM_VIDEO) ? 1 : 0);
        prop.put("input_contentdomCheckImage", (contentdomCode == plasmaSearchQuery.CONTENTDOM_IMAGE) ? 1 : 0);
        prop.put("input_contentdomCheckApp", (contentdomCode == plasmaSearchQuery.CONTENTDOM_APP) ? 1 : 0);
        prop.put("type_former", post.get("search", "")); //the query-string used to get the snippets
        
        // return rewrite properties
        return prop;
    }

}
