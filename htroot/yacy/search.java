// search.java
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published on http://yacy.net
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

// You must compile this file with
// javac -classpath .:../../Classes search.java
// if the shell's current path is htroot/yacy

import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.index.indexContainer;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSearchPreOrder;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchPostOrder;
import de.anomic.plasma.plasmaSearchProcessing;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.yacy.yacyNetwork;
import de.anomic.yacy.yacySeed;
import de.anomic.tools.crypt;

public final class search {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        if ((post == null) || (env == null)) return prop;
        if (!yacyNetwork.authentifyRequest(post, env)) return prop;
        
        //System.out.println("yacy: search received request = " + post.toString());

        final String  oseed  = post.get("myseed", ""); // complete seed of the requesting peer
//      final String  youare = post.get("youare", ""); // seed hash of the target peer, used for testing network stability
        final String  key    = post.get("key", "");    // transmission key for response
        final String  query  = post.get("query", "");  // a string of word hashes that shall be searched and combined
        final String  exclude= post.get("exclude", "");// a string of word hashes that shall not be within the search result
        String  urls   = post.get("urls", "");         // a string of url hashes that are preselected for the search: no other may be returned
        String abstracts = post.get("abstracts", "");  // a string of word hashes for abstracts that shall be generated, or 'auto' (for maxcount-word), or '' (for none)
//      final String  fwdep  = post.get("fwdep", "");  // forward depth. if "0" then peer may NOT ask another peer for more results
//      final String  fwden  = post.get("fwden", "");  // forward deny, a list of seed hashes. They may NOT be target of forward hopping
        final long    duetime= Math.min(60000, post.getLong("duetime", 3000));
        final int     count  = Math.min(100, post.getInt("count", 10)); // maximum number of wanted results
        final int     maxdist= post.getInt("maxdist", Integer.MAX_VALUE);
        final String  prefer = post.get("prefer", "");
        final String  contentdom = post.get("contentdom", "text");
        final String  filter = post.get("filter", ".*");
        final int     partitions = post.getInt("partitions", 30);
        String  profile = post.get("profile", ""); // remote profile hand-over
        if (profile.length() > 0) profile = crypt.simpleDecode(profile, null);
        final boolean includesnippet = post.get("includesnippet", "false").equals("true");
        final kelondroBitfield constraint = new kelondroBitfield(4, post.get("constraint", "______"));
//      final boolean global = ((String) post.get("resource", "global")).equals("global"); // if true, then result may consist of answers from other peers
//      Date remoteTime = yacyCore.parseUniversalDate((String) post.get(yacySeed.MYTIME));        // read remote time

        // test:
        // http://localhost:8080/yacy/search.html?query=4galTpdpDM5Q (search for linux)
        // http://localhost:8080/yacy/search.html?query=gh8DKIhGKXws (search for book)
        // http://localhost:8080/yacy/search.html?query=4galTpdpDM5Qgh8DKIhGKXws&abstracts=auto (search for linux and book, generate abstract automatically)
        // http://localhost:8080/yacy/search.html?query=&abstracts=4galTpdpDM5Q (only abstracts for linux)

        if ((sb.isRobinsonMode()) &&
             	 (!((sb.isPublicRobinson()) ||
             	    (sb.isInMyCluster((String)header.get(httpHeader.CONNECTION_PROP_CLIENTIP)))))) {
                 // if we are a robinson cluster, answer only if this client is known by our network definition
        	prop.putASIS("links", "");
            prop.putASIS("linkcount", "0");
            prop.putASIS("references", "");
        	return prop;
        }
        
        // tell all threads to do nothing for a specific time
        sb.intermissionAllThreads(2 * duetime);

        TreeSet abstractSet = ((abstracts.length() == 0) || (abstracts.equals("auto"))) ? null : plasmaSearchQuery.hashes2Set(abstracts);
        
        // store accessing peer
        if (yacyCore.seedDB == null) {
            yacyCore.log.logSevere("yacy.search: seed cache not initialized");
        } else {
            yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(oseed, key, true), true);
        }

        // prepare search
        final TreeSet queryhashes = plasmaSearchQuery.hashes2Set(query);
        final TreeSet excludehashes = (exclude.length() == 0) ? new TreeSet(kelondroBase64Order.enhancedCoder) : plasmaSearchQuery.hashes2Set(exclude);
        final long timestamp = System.currentTimeMillis();
        
        // prepare an abstract result
        StringBuffer indexabstract = new StringBuffer();
        int indexabstractContainercount = 0;
        int joincount = 0;
        plasmaSearchPostOrder acc = null;
        plasmaSearchQuery squery = null;
        //plasmaSearchEvent theSearch = null;
        if ((query.length() == 0) && (abstractSet != null)) {
            // this is _not_ a normal search, only a request for index abstracts
            squery = new plasmaSearchQuery(abstractSet, new TreeSet(kelondroBase64Order.enhancedCoder), maxdist, prefer, plasmaSearchQuery.contentdomParser(contentdom), count, duetime, filter, plasmaSearchQuery.catchall_constraint);
            squery.domType = plasmaSearchQuery.SEARCHDOM_LOCAL;
            yacyCore.log.logInfo("INIT HASH SEARCH (abstracts only): " + plasmaSearchQuery.anonymizedQueryHashes(squery.queryHashes) + " - " + squery.wantedResults + " links");

            // prepare a search profile
            //plasmaSearchRankingProfile rankingProfile = (profile.length() == 0) ? new plasmaSearchRankingProfile(contentdom) : new plasmaSearchRankingProfile("", profile);
            plasmaSearchProcessing localTiming  = new plasmaSearchProcessing(squery.maximumTime, squery.wantedResults);
            //plasmaSearchProcessing remoteTiming = null;

            //theSearch = new plasmaSearchEvent(squery, rankingProfile, localTiming, remoteTiming, true, sb.wordIndex, null);
            Map[] containers = localTiming.localSearchContainers(squery, sb.wordIndex, plasmaSearchQuery.hashes2Set(urls));
            if (containers != null) {
                Iterator ci = containers[0].entrySet().iterator();
                Map.Entry entry;
                String wordhash;
                while (ci.hasNext()) {
                    entry = (Map.Entry) ci.next();
                    wordhash = (String) entry.getKey();
                    indexContainer container = (indexContainer) entry.getValue();
                    indexabstractContainercount += container.size();
                    indexabstract.append("indexabstract." + wordhash + "=").append(plasmaURL.compressIndex(container, null, 1000).toString()).append(serverCore.crlfString);                
                }
            }
            
            prop.putASIS("indexcount", "");
            prop.put("joincount", 0);
        } else {
            // retrieve index containers from search request
            squery = new plasmaSearchQuery(queryhashes, excludehashes, maxdist, prefer, plasmaSearchQuery.contentdomParser(contentdom), count, duetime, filter, constraint);
            squery.domType = plasmaSearchQuery.SEARCHDOM_LOCAL;
            yacyCore.log.logInfo("INIT HASH SEARCH (query-" + abstracts + "): " + plasmaSearchQuery.anonymizedQueryHashes(squery.queryHashes) + " - " + squery.wantedResults + " links");

            // prepare a search profile
            plasmaSearchRankingProfile rankingProfile = (profile.length() == 0) ? new plasmaSearchRankingProfile(contentdom) : new plasmaSearchRankingProfile("", profile);
            plasmaSearchProcessing localProcess  = new plasmaSearchProcessing(squery.maximumTime, squery.wantedResults);
            //plasmaSearchProcessing remoteProcess = null;

            //theSearch = new plasmaSearchEvent(squery, rankingProfile, localProcess, remoteProcess, true, sb.wordIndex, null);
            Map[] containers = localProcess.localSearchContainers(squery, sb.wordIndex, plasmaSearchQuery.hashes2Set(urls));
            // set statistic details of search result and find best result index set
            if (containers == null) {
                prop.putASIS("indexcount", "");
                prop.putASIS("joincount", "0");
            } else {
                Iterator ci = containers[0].entrySet().iterator();
                StringBuffer indexcount = new StringBuffer();
                Map.Entry entry;
                int maxcount = -1;
                double mindhtdistance = 1.1, d;
                String wordhash;
                String maxcounthash = null, neardhthash = null;
                while (ci.hasNext()) {
                    entry = (Map.Entry) ci.next();
                    wordhash = (String) entry.getKey();
                    indexContainer container = (indexContainer) entry.getValue();
                    if (container.size() > maxcount) {
                        maxcounthash = wordhash;
                        maxcount = container.size();
                    }
                    d = yacyDHTAction.dhtDistance(yacyCore.seedDB.mySeed.hash, wordhash);
                    if (d < mindhtdistance) {
                        // calculate the word hash that is closest to our dht position
                        mindhtdistance = d;
                        neardhthash = wordhash;
                    }
                    indexcount.append("indexcount.").append(container.getWordHash()).append('=').append(Integer.toString(container.size())).append(serverCore.crlfString);
                    if ((abstractSet != null) && (abstractSet.contains(wordhash))) {
                        // if a specific index-abstract is demanded, attach it here
                        indexabstractContainercount += container.size();
                        indexabstract.append("indexabstract." + wordhash + "=").append(plasmaURL.compressIndex(container, null,1000).toString()).append(serverCore.crlfString);
                    }
                }
                prop.putASIS("indexcount", new String(indexcount));

                // join and order the result
                indexContainer localResults =
                    (containers == null) ?
                      plasmaWordIndex.emptyContainer(null) :
                          localProcess.localSearchJoinExclude(
                              containers[0].values(),
                              containers[1].values(),
                              (squery.queryHashes.size() == 0) ?
                                0 :
                                localProcess.getTargetTime(plasmaSearchProcessing.PROCESS_JOIN) * squery.queryHashes.size() / (squery.queryHashes.size() + squery.excludeHashes.size()),
                              squery.maxDistance);
                if (localResults == null) {
                    joincount = 0;
                    prop.put("joincount", 0);
                    acc = null;
                } else {
                    joincount = localResults.size();
                    prop.putASIS("joincount", Integer.toString(joincount));
                    plasmaSearchPreOrder pre = localProcess.preSort(squery, rankingProfile, localResults);
                    acc = localProcess.urlFetch(squery, rankingProfile, sb.wordIndex, pre);
                    acc.localContributions = (localResults == null) ? 0 : localResults.size();
                    localProcess.postSort(true, acc);
                    localProcess.applyFilter(acc);
                }
                
                // generate compressed index for maxcounthash
                // this is not needed if the search is restricted to specific
                // urls, because it is a re-search
                if ((maxcounthash == null) || (urls.length() != 0) || (queryhashes.size() == 1) || (abstracts.length() == 0)) {
                    prop.putASIS("indexabstract", "");
                } else if (abstracts.equals("auto")) {
                    // automatically attach the index abstract for the index that has the most references. This should be our target dht position
                    indexContainer container = (indexContainer) containers[0].get(maxcounthash);
                    indexabstractContainercount += container.size();
                    indexabstract.append("indexabstract." + maxcounthash + "=").append(plasmaURL.compressIndex(container,localResults, 1000).toString()).append(serverCore.crlfString);
                    if ((neardhthash != null) && (!(neardhthash.equals(maxcounthash)))) {
                        // in case that the neardhthash is different from the maxcounthash attach also the neardhthash-container
                        container = (indexContainer) containers[0].get(neardhthash);
                        indexabstractContainercount += container.size();
                        indexabstract.append("indexabstract." + neardhthash + "=").append(plasmaURL.compressIndex(container, localResults, 1000).toString()).append(serverCore.crlfString);
                    }
                    //System.out.println("DEBUG-ABSTRACTGENERATION: maxcounthash = " + maxcounthash);
                    //System.out.println("DEBUG-ABSTRACTGENERATION: neardhthash  = "+ neardhthash);
                    //yacyCore.log.logFine("DEBUG HASH SEARCH: " + indexabstract);
                }
            }
            if (partitions > 0) sb.requestedQueries = sb.requestedQueries + 1d / (double) partitions; // increase query counter
        }
        prop.putASIS("indexabstract", new String(indexabstract));
        
        // prepare search statistics
        Long trackerHandle = new Long(System.currentTimeMillis());
        String client = (String) header.get("CLIENTIP");
        /*
        HashMap searchProfile = theSearch.resultProfile();
        searchProfile.put("resulttime", new Long(System.currentTimeMillis() - timestamp));
        searchProfile.put("resultcount", new Integer(joincount));
        searchProfile.put("host", client);
        yacySeed remotepeer = yacyCore.seedDB.lookupByIP(natLib.getInetAddress(client), true, false, false);
        searchProfile.put("peername", (remotepeer == null) ? "unknown" : remotepeer.getName());
        searchProfile.put("time", trackerHandle);
        sb.remoteSearches.add(searchProfile);
        */
        TreeSet handles = (TreeSet) sb.remoteSearchTracker.get(client);
        if (handles == null) handles = new TreeSet();
        handles.add(trackerHandle);
        sb.remoteSearchTracker.put(client, handles);
        
        // prepare result
        if ((joincount == 0) || (acc == null)) {
            
            // no results
            prop.putASIS("links", "");
            prop.putASIS("linkcount", "0");
            prop.putASIS("references", "");

        } else {
            // result is a List of urlEntry elements
            int i = 0;
            StringBuffer links = new StringBuffer();
            String resource = null;
            indexURLEntry urlentry;
            plasmaSnippetCache.TextSnippet snippet;
            while ((acc.hasMoreElements()) && (i < squery.wantedResults)) {
                urlentry = (indexURLEntry) acc.nextElement();
                if (includesnippet) {
                    snippet = plasmaSnippetCache.retrieveTextSnippet(urlentry.comp().url(), squery.queryHashes, false, urlentry.flags().get(plasmaCondenser.flag_cat_indexof), 260, 1000);
                } else {
                    snippet = null;
                }
                if ((snippet != null) && (snippet.exists())) {
                    resource = urlentry.toString(snippet.getLineRaw());
                } else {
                    resource = urlentry.toString();
                }
                if (resource != null) {
                    links.append("resource").append(i).append('=').append(resource).append(serverCore.crlfString);
                    i++;
                }
            }
            prop.putASIS("links", new String(links));
            prop.putASIS("linkcount", Integer.toString(i));

            // prepare reference hints
            Object[] ws = acc.getReferences(16);
            StringBuffer refstr = new StringBuffer();
            for (int j = 0; j < ws.length; j++)
                refstr.append(",").append((String) ws[j]);
            prop.putASIS("references", (refstr.length() > 0) ? refstr.substring(1) : new String(refstr));
        }
        
        // add information about forward peers
        prop.putASIS("fwhop", ""); // hops (depth) of forwards that had been performed to construct this result
        prop.putASIS("fwsrc", ""); // peers that helped to construct this result
        prop.putASIS("fwrec", ""); // peers that would have helped to construct this result (recommendations)
        
        // log
        yacyCore.log.logInfo("EXIT HASH SEARCH: " +
                plasmaSearchQuery.anonymizedQueryHashes(squery.queryHashes) + " - " + joincount + " links found, " +
                prop.get("linkcount", "?") + " links selected, " +
                indexabstractContainercount + " index abstract references attached, " +
                (System.currentTimeMillis() - timestamp) + " milliseconds");
 
        prop.putASIS("searchtime", Long.toString(System.currentTimeMillis() - timestamp));

        final int links = Integer.parseInt(prop.get("linkcount","0"));
        yacyCore.seedDB.mySeed.incSI(links);
        yacyCore.seedDB.mySeed.incSU(links);
        return prop;
    }

}
