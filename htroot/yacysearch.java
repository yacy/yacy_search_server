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

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.util.Domains;
import net.yacy.kelondro.util.Formatter;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.SetTools;
import net.yacy.kelondro.util.ISO639;

import de.anomic.content.RSSMessage;
import de.anomic.crawler.retrieval.LoaderDispatcher;
import de.anomic.data.DidYouMean;
import de.anomic.data.LibraryProvider;
import de.anomic.data.Location;
import de.anomic.document.Condenser;
import de.anomic.document.Document;
import de.anomic.document.parser.xml.RSSFeed;
import de.anomic.http.metadata.HeaderFramework;
import de.anomic.http.metadata.RequestHeader;
import de.anomic.search.QueryParams;
import de.anomic.search.RankingProfile;
import de.anomic.search.SearchEvent;
import de.anomic.search.SearchEventCache;
import de.anomic.search.Segment;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverProfiling;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;
import de.anomic.ymage.ProfilingGraph;

public class yacysearch {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        sb.localSearchLastAccess = System.currentTimeMillis();
        
        final boolean searchAllowed = sb.getConfigBool("publicSearchpage", true) || sb.verifyAuthentication(header, false);
        
        final boolean authenticated = sb.adminAuthenticated(header) >= 2;
        int display = (post == null) ? 0 : post.getInt("display", 0);
        if (!authenticated) display = 2;
        // display == 0: shop top menu
        // display == 1: show top and left menu
        // display == 2: do not show any menu
        final boolean browserPopUpTrigger = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_TRIGGER, "true").equals("true");
        if (browserPopUpTrigger) {
            final String  browserPopUpPage = sb.getConfig(SwitchboardConstants.BROWSER_POP_UP_PAGE, "ConfigBasic.html");
            if (browserPopUpPage.startsWith("index") || browserPopUpPage.startsWith("yacysearch")) display = 2;
        }
        String promoteSearchPageGreeting = env.getConfig(SwitchboardConstants.GREETING, "");
        if (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) promoteSearchPageGreeting = env.getConfig("network.unit.description", "");
        final String client = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP); // the search client who initiated the search
        
        // get query
        String originalquerystring = (post == null) ? "" : post.get("query", post.get("search", "")).trim(); // SRU compliance
        String querystring = originalquerystring;
        boolean fetchSnippets = (post != null && post.get("verify", "false").equals("true"));
        final serverObjects prop = new serverObjects();

        // get segment
        Segment indexSegment = null;
        if (post != null && post.containsKey("segment")) {
            String segmentName = post.get("segment");
            if (sb.indexSegments.segmentExist(segmentName)) {
                indexSegment = sb.indexSegments.segment(segmentName);
            }
        } else {
            // take default segment
            indexSegment = sb.indexSegments.segment(Segments.Process.PUBLIC);
        }
        
        //final boolean rss = (post == null) ? false : post.get("rss", "false").equals("true");
        prop.put("promoteSearchPageGreeting", promoteSearchPageGreeting);
        prop.put("promoteSearchPageGreeting.homepage", sb.getConfig(SwitchboardConstants.GREETING_HOMEPAGE, ""));
        prop.put("promoteSearchPageGreeting.smallImage", sb.getConfig(SwitchboardConstants.GREETING_SMALL_IMAGE, ""));
        if (post == null || indexSegment == null || env == null || !searchAllowed) {
            // we create empty entries for template strings
            prop.put("searchagain", "0");
            prop.put("display", display);
            prop.put("display", display);
            prop.put("former", "");
            prop.put("count", "10");
            prop.put("offset", "0");
            prop.put("resource", "global");
            prop.put("urlmaskfilter", (post == null) ? ".*" : post.get("urlmaskfilter", ".*"));
            prop.put("prefermaskfilter", (post == null) ? "" : post.get("prefermaskfilter", ""));
            prop.put("tenant", (post == null) ? "" : post.get("tenant", ""));
            prop.put("indexof", "off");
            prop.put("constraint", "");
            prop.put("cat", "href");
            prop.put("depth", "0");
            prop.put("verify", (post == null) ? "true" : post.get("verify", "true"));
            prop.put("contentdom", "text");
            prop.put("contentdomCheckText", "1");
            prop.put("contentdomCheckAudio", "0");
            prop.put("contentdomCheckVideo", "0");
            prop.put("contentdomCheckImage", "0");
            prop.put("contentdomCheckApp", "0");
            prop.put("excluded", "0");
            prop.put("results", "");
            prop.put("resultTable", "0");
            prop.put("num-results", searchAllowed ? "0" : "4");
            prop.put("num-results_totalcount", 0);
            prop.put("num-results_offset", 0);
            prop.put("num-results_itemsPerPage", 10);
            prop.put("geoinfo", "0");
            prop.put("rss_queryenc", "");
            
            return prop;
        }
        
        // check for JSONP
        if (post.containsKey("callback")) {
        	final String jsonp = post.get("callback")+ "([";
        	prop.put("jsonp-start", jsonp);
        	prop.put("jsonp-end", "])");
        } else {
        	prop.put("jsonp-start", "");
        	prop.put("jsonp-end", "");
        }
        
        // collect search attributes
        boolean newsearch = post.hasValue("query") && post.hasValue("former") && !post.get("query","").equalsIgnoreCase(post.get("former","")); //new search term
        
        int itemsPerPage = Math.min((authenticated) ? 1000 : 10, post.getInt("maximumRecords", post.getInt("count", 10))); // SRU syntax with old property as alternative
        int offset = (newsearch) ? 0 : post.getInt("startRecord", post.getInt("offset", 0));
        
        boolean global = post.get("resource", "local").equals("global");
        final boolean indexof = (post != null && post.get("indexof","").equals("on")); 
        
        String urlmask = null;
        String originalUrlMask = null;
        if (post.containsKey("urlmask") && post.get("urlmask").equals("no")) { // option search all
            originalUrlMask = ".*";
        } else if (!newsearch && post.containsKey("urlmaskfilter")) {
            originalUrlMask = post.get("urlmaskfilter", ".*");
        } else {
            originalUrlMask = ".*";
        }

        String prefermask = (post == null ? "" : post.get("prefermaskfilter", ""));
        if ((prefermask.length() > 0) && (prefermask.indexOf(".*") < 0)) prefermask = ".*" + prefermask + ".*";

        Bitfield constraint = (post != null && post.containsKey("constraint") && post.get("constraint", "").length() > 0) ? new Bitfield(4, post.get("constraint", "______")) : null;
        if (indexof) {
            constraint = new Bitfield(4);
            constraint.set(Condenser.flag_cat_indexof, true);
        }
        
        // SEARCH
        final boolean indexReceiveGranted = sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, true);
        global = global && indexReceiveGranted; // if the user does not want indexes from remote peers, it cannot be a global search
        //final boolean offline = yacyCore.seedDB.mySeed().isVirgin();
        
        final boolean clustersearch = sb.isRobinsonMode() &&
    									(sb.getConfig("cluster.mode", "").equals("privatecluster") ||
    									 sb.getConfig("cluster.mode", "").equals("publiccluster"));
        //if (offline || !indexDistributeGranted || !indexReceiveGranted) { global = false; }
        if (clustersearch) global = true; // switches search on, but search target is limited to cluster nodes
        
        // find search domain
        final int contentdomCode = QueryParams.contentdomParser((post == null ? "text" : post.get("contentdom", "text")));
        
        // patch until better search profiles are available
        if ((contentdomCode != QueryParams.CONTENTDOM_TEXT) && (itemsPerPage <= 32)) itemsPerPage = 32;
        
        // check the search tracker
        TreeSet<Long> trackerHandles = sb.localSearchTracker.get(client);
        if (trackerHandles == null) trackerHandles = new TreeSet<Long>();
        boolean block = false;
        if (Domains.matchesList(client, sb.networkBlacklist)) {
        	global = false;
            fetchSnippets = false;
            block = true;
            Log.logWarning("LOCAL_SEARCH", "ACCECC CONTROL: BLACKLISTED CLIENT FROM " + client + " gets no permission to search");
        } else if (Domains.matchesList(client, sb.networkWhitelist)) {
        	Log.logInfo("LOCAL_SEARCH", "ACCECC CONTROL: WHITELISTED CLIENT FROM " + client + " gets no search restrictions");
        } else if (global || fetchSnippets) {
            // in case that we do a global search or we want to fetch snippets, we check for DoS cases
            synchronized (trackerHandles) {
                int accInOneSecond = trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 1000)).size();
                int accInThreeSeconds = trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 3000)).size();
                int accInOneMinute = trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 60000)).size();
                int accInTenMinutes = trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 600000)).size();
                if (accInTenMinutes > 600) {
                    global = false;
                    fetchSnippets = false;
                    block = true;
                    Log.logWarning("LOCAL_SEARCH", "ACCECC CONTROL: CLIENT FROM " + client + ": " + accInTenMinutes + " searches in ten minutes, fully blocked (no results generated)");
                } else if (accInOneMinute > 200) {
                    global = false;
                    fetchSnippets = false;
                    block = true;
                    Log.logWarning("LOCAL_SEARCH", "ACCECC CONTROL: CLIENT FROM " + client + ": " + accInOneMinute + " searches in one minute, fully blocked (no results generated)");
                } else if (accInThreeSeconds > 1) {
                    global = false;
                    fetchSnippets = false;
                    Log.logWarning("LOCAL_SEARCH", "ACCECC CONTROL: CLIENT FROM " + client + ": " + accInThreeSeconds + " searches in three seconds, blocked global search and snippets");
                } else if (accInOneSecond > 2) {
                    global = false;
                    fetchSnippets = false;
                    Log.logWarning("LOCAL_SEARCH", "ACCECC CONTROL: CLIENT FROM " + client + ": " + accInOneSecond + " searches in one second, blocked global search and snippets");
                }
            }
        }
        
        if ((!block) && (post == null || post.get("cat", "href").equals("href"))) {
            
            // check available memory and clean up if necessary
            if (!MemoryControl.request(8000000L, false)) {
                indexSegment.urlMetadata().clearCache();
                SearchEventCache.cleanupEvents(true);
            }
            
            final RankingProfile ranking = sb.getRanking();

            if (querystring.indexOf("NEAR") >= 0) {
            	querystring = querystring.replace("NEAR", "");
            	ranking.coeff_worddistance = RankingProfile.COEFF_MAX;
            }
            if (querystring.indexOf("RECENT") >= 0) {
            	querystring = querystring.replace("RECENT", "");
                ranking.coeff_date = RankingProfile.COEFF_MAX;
            }
            int lrp = querystring.indexOf("LANGUAGE:");
            String lr = "";
            if (lrp >= 0) {
                if (querystring.length() >= (lrp + 11))
                	lr = querystring.substring(lrp + 9, lrp + 11);
                querystring = querystring.replace("LANGUAGE:" + lr, "");
                lr = lr.toLowerCase();
            }
            int inurl = querystring.indexOf("inurl:");
            if (inurl >= 0) {
                int ftb = querystring.indexOf(' ', inurl);
                if (ftb == -1) ftb = querystring.length();
                String urlstr = querystring.substring(inurl + 6, ftb);
                querystring = querystring.replace("inurl:" + urlstr, "");
                if(urlstr.length() > 0) urlmask = ".*" + urlstr + ".*";
            }
            int filetype = querystring.indexOf("filetype:");
            if (filetype >= 0) {
                int ftb = querystring.indexOf(' ', filetype);
                if (ftb == -1) ftb = querystring.length();
                String ft = querystring.substring(filetype + 9, ftb);
                querystring = querystring.replace("filetype:" + ft, "");
                while(ft.startsWith(".")) ft = ft.substring(1);
                if(ft.length() > 0) {
                    if (urlmask == null) {
                        urlmask = ".*\\." + ft;
                    } else {
                        urlmask = urlmask + ".*\\." + ft;
                    }
                }
            }
            String tenant = null;
            if (post.containsKey("tenant")) {
                tenant = post.get("tenant");
                if (tenant != null && tenant.length() == 0) tenant = null;
                if (tenant != null) {
                	if (urlmask == null) urlmask = ".*" + tenant + ".*"; else urlmask = ".*" + tenant + urlmask;
                }
            }
            int site = querystring.indexOf("site:");
            String sitehash = null;
            if (site >= 0) {
                int ftb = querystring.indexOf(' ', site);
                if (ftb == -1) ftb = querystring.length();
                String domain = querystring.substring(site + 5, ftb);
                querystring = querystring.replace("site:" + domain, "");
                while(domain.startsWith(".")) domain = domain.substring(1);
                while(domain.endsWith(".")) domain = domain.substring(0, domain.length() - 1);
                sitehash = DigestURI.domhash(domain);
            }
            int authori = querystring.indexOf("author:");
        	String authorhash = null;
            if (authori >= 0) {
            	// check if the author was given with single quotes or without
            	boolean quotes = false;
            	if (querystring.charAt(authori + 7) == (char) 39) {
            		quotes = true;
            	}
            	String author;
            	if (quotes) {
            		int ftb = querystring.indexOf((char) 39, authori + 8);
                    if (ftb == -1) ftb = querystring.length() + 1;
                    author = querystring.substring(authori + 8, ftb);
                    querystring = querystring.replace("author:'" + author + "'", "");
            	} else {
            		int ftb = querystring.indexOf(' ', authori);
            		if (ftb == -1) ftb = querystring.length();
            		author = querystring.substring(authori + 7, ftb);
                    querystring = querystring.replace("author:" + author, "");
            	}
            	authorhash = new String(Word.word2hash(author));
            }
            int tld = querystring.indexOf("tld:");
            if (tld >= 0) {
                int ftb = querystring.indexOf(' ', tld);
                if (ftb == -1) ftb = querystring.length();
                String domain = querystring.substring(tld + 4, ftb);
                querystring = querystring.replace("tld:" + domain, "");
                while(domain.startsWith(".")) domain = domain.substring(1);
                if (domain.indexOf(".") < 0) domain = "\\." + domain; // is tld
                if (domain.length() > 0) {
                    if (urlmask == null) {
                        urlmask = "[a-zA-Z]*://[^/]*" + domain + "/.*";
                    } else {
                        urlmask = "[a-zA-Z]*://[^/]*" + domain + "/.*" + urlmask;
                    }
                }
            }
            if (urlmask == null || urlmask.length() == 0) urlmask = originalUrlMask; //if no urlmask was given
           
            // read the language from the language-restrict option 'lr'
            // if no one is given, use the user agent or the system language as default
            String language = (post == null) ? lr : post.get("lr", lr);
            if (language.startsWith("lang_")) language = language.substring(5);
            if (!ISO639.exists(language)) {
                // find out language of the user by reading of the user-agent string
                String agent = header.get(HeaderFramework.ACCEPT_LANGUAGE);
                if (agent == null) agent = System.getProperty("user.language");
                language = (agent == null) ? "en" : ISO639.userAgentLanguageDetection(agent);
                if (language == null) language = "en";
            }
            
            // navigation
            String navigation = (post == null) ? "" : post.get("nav", "");
            
            // the query
            final TreeSet<String>[] query = QueryParams.cleanQuery(querystring.trim()); // converts also umlaute
            
            int maxDistance = (querystring.indexOf('"') >= 0) ? maxDistance = query.length - 1 : Integer.MAX_VALUE;

            // filter out stopwords
            final TreeSet<String> filtered = SetTools.joinConstructive(query[0], Switchboard.stopwords);
            if (filtered.size() > 0) {
                SetTools.excludeDestructive(query[0], Switchboard.stopwords);
            }

            // if a minus-button was hit, remove a special reference first
            if (post != null && post.containsKey("deleteref")) try {
                if (!sb.verifyAuthentication(header, true)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                
                // delete the index entry locally
                final String delHash = post.get("deleteref", ""); // urlhash
                indexSegment.termIndex().remove(Word.words2hashes(query[0]), delHash);

                // make new news message with negative voting
                final HashMap<String, String> map = new HashMap<String, String>();
                map.put("urlhash", delHash);
                map.put("vote", "negative");
                map.put("refid", "");
                sb.peers.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.peers.mySeed(), yacyNewsPool.CATEGORY_SURFTIPP_VOTE_ADD, map));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // if a plus-button was hit, create new voting message
            if (post != null && post.containsKey("recommendref")) {
                if (!sb.verifyAuthentication(header, true)) {
                    prop.put("AUTHENTICATE", "admin log-in"); // force log-in
                    return prop;
                }
                final String recommendHash = post.get("recommendref", ""); // urlhash
                final URIMetadataRow urlentry = indexSegment.urlMetadata().load(recommendHash, null, 0);
                if (urlentry != null) {
                    final URIMetadataRow.Components metadata = urlentry.metadata();
                    Document document;
                    document = LoaderDispatcher.retrieveDocument(metadata.url(), true, 5000, true, false);
                    if (document != null) {
                        // create a news message
                        final HashMap<String, String> map = new HashMap<String, String>();
                        map.put("url", metadata.url().toNormalform(false, true).replace(',', '|'));
                        map.put("title", metadata.dc_title().replace(',', ' '));
                        map.put("description", document.dc_title().replace(',', ' '));
                        map.put("author", document.dc_creator());
                        map.put("tags", document.dc_subject(' '));
                        sb.peers.newsPool.publishMyNews(yacyNewsRecord.newRecord(sb.peers.mySeed(), yacyNewsPool.CATEGORY_SURFTIPP_ADD, map));
                        document.close();
                    }
                }
            }

            // prepare search properties
            //final boolean yacyonline = ((sb.webIndex.seedDB != null) && (sb.webIndex.seedDB.mySeed() != null) && (sb.webIndex.seedDB.mySeed().getPublicAddress() != null));
            final boolean globalsearch = (global) /* && (yacyonline)*/ && (sb.getConfigBool(SwitchboardConstants.INDEX_RECEIVE_ALLOW, false));
        
            // do the search
            final TreeSet<byte[]> queryHashes = Word.words2hashes(query[0]);
            final QueryParams theQuery = new QueryParams(
        			originalquerystring,
        			queryHashes,
        			Word.words2hashes(query[1]),
        			Word.words2hashes(query[2]),
        			tenant,
                    ranking,
                    maxDistance,
                    prefermask,
                    contentdomCode,
                    language,
                    navigation,
                    fetchSnippets,
                    itemsPerPage,
                    offset,
                    urlmask,
                    (clustersearch && globalsearch) ? QueryParams.SEARCHDOM_CLUSTERALL :
                    ((globalsearch) ? QueryParams.SEARCHDOM_GLOBALDHT : QueryParams.SEARCHDOM_LOCAL),
                    20,
                    constraint,
                    true,
                    sitehash,
                    authorhash,
                    DigestURI.TLD_any_zone_filter,
                    client,
                    authenticated);
            serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(theQuery.id(true), SearchEvent.INITIALIZATION, 0, 0), false);
            
            // tell all threads to do nothing for a specific time
            sb.intermissionAllThreads(10000);
        
            // filter out words that appear in bluelist
            theQuery.filterOut(Switchboard.blueList);
            
            // log
            Log.logInfo("LOCAL_SEARCH", "INIT WORD SEARCH: " + theQuery.queryString + ":" + QueryParams.hashSet2hashString(theQuery.queryHashes) + " - " + theQuery.neededResults() + " links to be computed, " + theQuery.displayResults() + " lines to be displayed");
            RSSFeed.channels(RSSFeed.LOCALSEARCH).addMessage(new RSSMessage("Local Search Request", theQuery.queryString, ""));
            final long timestamp = System.currentTimeMillis();

            // create a new search event
            if (SearchEventCache.getEvent(theQuery.id(false)) == null) {
                theQuery.setOffset(0); // in case that this is a new search, always start without a offset 
                offset = 0;
            }
            final SearchEvent theSearch = SearchEventCache.getEvent(theQuery, indexSegment, sb.peers, sb.crawlResults, (sb.isRobinsonMode()) ? sb.clusterhashes : null, false);
            
            // generate result object
            //serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER ORDERING OF SEARCH RESULTS: " + (System.currentTimeMillis() - timestamp) + " ms");
            //serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER RESULT PREPARATION: " + (System.currentTimeMillis() - timestamp) + " ms");
                
            // calc some more cross-reference
            //serverLog.logFine("LOCAL_SEARCH", "SEARCH TIME AFTER XREF PREPARATION: " + (System.currentTimeMillis() - timestamp) + " ms");

            // log
            Log.logInfo("LOCAL_SEARCH", "EXIT WORD SEARCH: " + theQuery.queryString + " - " +
                    (theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize()) + " links found, " +
                    (System.currentTimeMillis() - timestamp) + " ms");

            // prepare search statistics
            theQuery.resultcount = theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize();
            theQuery.searchtime = System.currentTimeMillis() - timestamp;
            theQuery.urlretrievaltime = theSearch.result().getURLRetrievalTime();
            theQuery.snippetcomputationtime = theSearch.result().getSnippetComputationTime();
            sb.localSearches.add(theQuery);
                        
            // check suggestions
            int meanMax = 0;
            if (post != null && post.containsKey("meanCount")) {
            	meanMax = Integer.parseInt(post.get("meanCount"));            	
            }
            prop.put("meanCount", meanMax);
            if (meanMax > 0) {
                DidYouMean didYouMean = new DidYouMean(indexSegment.termIndex());
            	Iterator<String> meanIt = didYouMean.getSuggestions(querystring, 300, 10).iterator();
                int meanCount = 0;
                String suggestion;
                while(meanCount<meanMax && meanIt.hasNext()) {
                	suggestion = meanIt.next();
                	prop.put("didYouMean_suggestions_"+meanCount+"_word", suggestion);
                	prop.put("didYouMean_suggestions_"+meanCount+"_url",
                		"/yacysearch.html" + "?display=" + display +
    	                "&search=" + suggestion +
    	                "&maximumRecords="+ theQuery.displayResults() +
    	                "&startRecord=" + (0 * theQuery.displayResults()) +
    	                "&resource=" + ((theQuery.isLocal()) ? "local" : "global") +
    	                "&verify=" + ((theQuery.onlineSnippetFetch) ? "true" : "false") +
    	                "&nav=" + theQuery.navigators +
    	                "&urlmaskfilter=" + originalUrlMask +
    	                "&prefermaskfilter=" + theQuery.prefer +
    	                "&cat=href&amp;constraint=" + ((theQuery.constraint == null) ? "" : theQuery.constraint.exportB64()) +
    	                "&contentdom=" + theQuery.contentdom() +
    	                "&former=" + theQuery.queryString(true) +
    	                "&meanCount=" + meanMax
    	             );
                	prop.put("didYouMean_suggestions_"+meanCount+"_sep","|");
                	meanCount++;
                }
                prop.put("didYouMean_suggestions_"+(meanCount-1)+"_sep","");
                prop.put("didYouMean", meanCount>0 ? 1:0);
                prop.put("didYouMean_suggestions", meanCount);
            } else {
                prop.put("didYouMean", 0);
            }
            
            // find geographic info
            Set<Location> coordinates = LibraryProvider.geoDB.find(originalquerystring);
            if (coordinates == null || coordinates.size() == 0 || offset > 0) {
                prop.put("geoinfo", "0");
            } else {
                int i = 0;
                for (Location c: coordinates) {
                    prop.put("geoinfo_loc_" + i + "_lon", Math.round(c.lon() * 10000.0) / 10000.0);
                    prop.put("geoinfo_loc_" + i + "_lat", Math.round(c.lat() * 10000.0) / 10000.0);
                    prop.put("geoinfo_loc_" + i + "_name", c.getName());
                    i++;
                    if (i >= 5) break;
                }
                prop.put("geoinfo_loc", i);
                prop.put("geoinfo", "1");
            }
            
            // update the search tracker
            try {
                synchronized (trackerHandles) {
                	trackerHandles.add(theQuery.handle);
                	if (trackerHandles.size() > 1000) trackerHandles.remove(trackerHandles.first());
                	sb.localSearchTracker.put(client, trackerHandles);
                }
            	if (sb.localSearchTracker.size() > 1000) sb.localSearchTracker.remove(sb.localSearchTracker.keys().nextElement());
            } catch (Exception e) {
            	e.printStackTrace();
            }
            
            final int totalcount = theSearch.getRankingResult().getLocalResourceSize() + theSearch.getRankingResult().getRemoteResourceSize();
            prop.put("num-results_offset", offset);
            prop.put("num-results_itemscount", "0");
            prop.put("num-results_itemsPerPage", itemsPerPage);
            prop.put("num-results_totalcount", Formatter.number(totalcount, true));
            prop.put("num-results_globalresults", (globalsearch) ? "1" : "0");
            prop.put("num-results_globalresults_localResourceSize", Formatter.number(theSearch.getRankingResult().getLocalResourceSize(), true));
            prop.put("num-results_globalresults_remoteResourceSize", Formatter.number(theSearch.getRankingResult().getRemoteResourceSize(), true));
            prop.put("num-results_globalresults_remoteIndexCount", Formatter.number(theSearch.getRankingResult().getRemoteIndexCount(), true));
            prop.put("num-results_globalresults_remotePeerCount", Formatter.number(theSearch.getRankingResult().getRemotePeerCount(), true));
            
            // compose page navigation
            final StringBuilder resnav = new StringBuilder();
            final int thispage = offset / theQuery.displayResults();
            if (thispage == 0) {
            	resnav.append("<img src=\"env/grafics/navdl.gif\" width=\"16\" height=\"16\">&nbsp;");
            } else {
            	resnav.append("<a href=\"");
                resnav.append(QueryParams.navurl("html", thispage - 1, display, theQuery, originalUrlMask, null, navigation));
            	resnav.append("\"><img src=\"env/grafics/navdl.gif\" width=\"16\" height=\"16\"></a>&nbsp;");
            }
            final int numberofpages = Math.min(10, Math.min(thispage + 2, totalcount / theQuery.displayResults()));
            for (int i = 0; i < numberofpages; i++) {
                if (i == thispage) {
                    resnav.append("<img src=\"env/grafics/navs");
                    resnav.append(i + 1);
                    resnav.append(".gif\" width=\"16\" height=\"16\">&nbsp;");
                } else {
                    resnav.append("<a href=\"");
                    resnav.append(QueryParams.navurl("html", i, display, theQuery, originalUrlMask, null, navigation));
                    resnav.append("\"><img src=\"env/grafics/navd");
                	resnav.append(i + 1);
                	resnav.append(".gif\" width=\"16\" height=\"16\"></a>&nbsp;");
                }
            }
            if (thispage >= numberofpages) {
            	resnav.append("<img src=\"env/grafics/navdr.gif\" width=\"16\" height=\"16\">");
            } else {
                resnav.append("<a href=\"");
                resnav.append(QueryParams.navurl("html", thispage + 1, display, theQuery, originalUrlMask, null, navigation));
                resnav.append("\"><img src=\"env/grafics/navdr.gif\" width=\"16\" height=\"16\"></a>");
            }
            prop.put("num-results_resnav", resnav.toString());
        
            // generate the search result lines; the content will be produced by another servlet
            for (int i = 0; i < theQuery.displayResults(); i++) {
                prop.put("results_" + i + "_item", offset + i);
                prop.put("results_" + i + "_eventID", theQuery.id(false));
                prop.put("results_" + i + "_display", display);
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

            prop.put("cat", "href");
            prop.put("depth", "0");

            // adding some additional properties needed for the rss feed
            String hostName = header.get("Host", "localhost");
            if (hostName.indexOf(":") == -1) hostName += ":" + serverCore.getPortNr(env.getConfig("port", "8080"));
            prop.put("searchBaseURL", "http://" + hostName + "/yacysearch.html");
            prop.put("rssYacyImageURL", "http://" + hostName + "/env/grafics/yacy.gif");
        }
        
        prop.put("searchagain", global ? "1" : "0");
        prop.put("display", display);
        prop.put("display", display);
        prop.putHTML("former", originalquerystring);
        prop.put("count", itemsPerPage);
        prop.put("offset", offset);
        prop.put("resource", global ? "global" : "local");
        prop.putHTML("urlmaskfilter", originalUrlMask);
        prop.putHTML("prefermaskfilter", prefermask);
        prop.put("indexof", (indexof) ? "on" : "off");
        prop.put("constraint", (constraint == null) ? "" : constraint.exportB64());
        prop.put("verify", (fetchSnippets) ? "true" : "false");
        prop.put("contentdom", (post == null ? "text" : post.get("contentdom", "text")));
        prop.put("contentdomCheckText", (contentdomCode == QueryParams.CONTENTDOM_TEXT) ? "1" : "0");
        prop.put("contentdomCheckAudio", (contentdomCode == QueryParams.CONTENTDOM_AUDIO) ? "1" : "0");
        prop.put("contentdomCheckVideo", (contentdomCode == QueryParams.CONTENTDOM_VIDEO) ? "1" : "0");
        prop.put("contentdomCheckImage", (contentdomCode == QueryParams.CONTENTDOM_IMAGE) ? "1" : "0");
        prop.put("contentdomCheckApp", (contentdomCode == QueryParams.CONTENTDOM_APP) ? "1" : "0");
        
        // for RSS: don't HTML encode some elements
        prop.putXML("rss_query", originalquerystring);
        prop.put("rss_queryenc", originalquerystring.replace(' ', '+'));
                
        sb.localSearchLastAccess = System.currentTimeMillis();
        
        // return rewrite properties
        return prop;
    }
}
