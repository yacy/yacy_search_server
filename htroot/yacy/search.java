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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.index.indexContainer;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverProfiling;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
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
        //final boolean includesnippet = post.get("includesnippet", "false").equals("true");
        kelondroBitfield constraint = ((post.containsKey("constraint")) && (post.get("constraint", "").length() > 0)) ? new kelondroBitfield(4, post.get("constraint", "______")) : null;
        if (constraint != null) {
        	// check bad handover parameter from older versions
            boolean allon = true;
            for (int i = 0; i < 32; i++) {
            	if (!constraint.get(i)) {allon = false; break;}
            }
            if (allon) constraint = null;
        }
//      final boolean global = ((String) post.get("resource", "global")).equals("global"); // if true, then result may consist of answers from other peers
//      Date remoteTime = yacyCore.parseUniversalDate((String) post.get(yacySeed.MYTIME));        // read remote time

        // test:
        // http://localhost:8080/yacy/search.html?query=4galTpdpDM5Q (search for linux)
        // http://localhost:8080/yacy/search.html?query=gh8DKIhGKXws (search for book)
        // http://localhost:8080/yacy/search.html?query=UEhMGfGv2vOE (search for kernel)
        // http://localhost:8080/yacy/search.html?query=ZX-LjaYo74PP (search for help)
        // http://localhost:8080/yacy/search.html?query=uDqIalxDfM2a (search for mail)
        // http://localhost:8080/yacy/search.html?query=4galTpdpDM5Qgh8DKIhGKXws&abstracts=auto (search for linux and book, generate abstract automatically)
        // http://localhost:8080/yacy/search.html?query=&abstracts=4galTpdpDM5Q (only abstracts for linux)

        if ((sb.isRobinsonMode()) &&
             	 (!((sb.isPublicRobinson()) ||
             	    (sb.isInMyCluster((String)header.get(httpHeader.CONNECTION_PROP_CLIENTIP)))))) {
                 // if we are a robinson cluster, answer only if this client is known by our network definition
        	prop.put("links", "");
            prop.put("linkcount", "0");
            prop.put("references", "");
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
        plasmaSearchQuery theQuery = null;
        serverProfiling localProfiling = null;
        ArrayList accu = null;
        long urlRetrievalAllTime = 0, snippetComputationAllTime = 0;
        if ((query.length() == 0) && (abstractSet != null)) {
            // this is _not_ a normal search, only a request for index abstracts
            theQuery = new plasmaSearchQuery(null, abstractSet, new TreeSet(kelondroBase64Order.enhancedCoder), maxdist, prefer, plasmaSearchQuery.contentdomParser(contentdom), false, count, 0, duetime, filter, plasmaSearchQuery.SEARCHDOM_LOCAL, null, -1, null, false);
            theQuery.domType = plasmaSearchQuery.SEARCHDOM_LOCAL;
            yacyCore.log.logInfo("INIT HASH SEARCH (abstracts only): " + plasmaSearchQuery.anonymizedQueryHashes(theQuery.queryHashes) + " - " + theQuery.displayResults() + " links");

            // prepare a search profile
            localProfiling  = new serverProfiling();
            
            //theSearch = new plasmaSearchEvent(squery, rankingProfile, localTiming, remoteTiming, true, sb.wordIndex, null);
            localProfiling.startTimer();
            Map[] containers = sb.wordIndex.localSearchContainers(theQuery, plasmaSearchQuery.hashes2Set(urls));
            localProfiling.yield(plasmaSearchEvent.COLLECTION, containers[0].size());
            if (containers != null) {
                Iterator ci = containers[0].entrySet().iterator();
                Map.Entry entry;
                String wordhash;
                while (ci.hasNext()) {
                    entry = (Map.Entry) ci.next();
                    wordhash = (String) entry.getKey();
                    indexContainer container = (indexContainer) entry.getValue();
                    indexabstractContainercount += container.size();
                    indexabstract.append("indexabstract." + wordhash + "=").append(indexContainer.compressIndex(container, null, 1000).toString()).append(serverCore.crlfString);                
                }
            }
            
            prop.put("indexcount", "");
            prop.put("joincount", "0");
            prop.put("references", "");
            
        } else {
            
            // retrieve index containers from search request
            theQuery = new plasmaSearchQuery(null, queryhashes, excludehashes, maxdist, prefer, plasmaSearchQuery.contentdomParser(contentdom), false, count, 0, duetime, filter, plasmaSearchQuery.SEARCHDOM_LOCAL, null, -1, constraint, false);
            theQuery.domType = plasmaSearchQuery.SEARCHDOM_LOCAL;
            yacyCore.log.logInfo("INIT HASH SEARCH (query-" + abstracts + "): " + plasmaSearchQuery.anonymizedQueryHashes(theQuery.queryHashes) + " - " + theQuery.displayResults() + " links");
            
            // prepare a search profile
            plasmaSearchRankingProfile rankingProfile = (profile.length() == 0) ? new plasmaSearchRankingProfile(plasmaSearchQuery.contentdomParser(contentdom)) : new plasmaSearchRankingProfile("", profile);
            localProfiling  = new serverProfiling();
            plasmaSearchEvent theSearch = plasmaSearchEvent.getEvent(theQuery, rankingProfile, localProfiling, sb.wordIndex, null, true, abstractSet);
            urlRetrievalAllTime = theSearch.getURLRetrievalTime();
            snippetComputationAllTime = theSearch.getSnippetComputationTime();
            
            // set statistic details of search result and find best result index set
            if (theSearch.getLocalCount() == 0) {
                prop.put("indexcount", "");
                prop.put("joincount", "0");
            } else {
                // attach information about index abstracts
                StringBuffer indexcount = new StringBuffer();
                Map.Entry entry;
                Iterator i = theSearch.IACount.entrySet().iterator();
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    indexcount.append("indexcount.").append((String) entry.getKey()).append('=').append(((Integer) entry.getValue()).toString()).append(serverCore.crlfString);
                }
                if (abstractSet != null) {
                    // if a specific index-abstract is demanded, attach it here
                    i = abstractSet.iterator();
                    String wordhash;
                    while (i.hasNext()) {
                        wordhash = (String) i.next();
                        indexabstractContainercount += ((Integer) theSearch.IACount.get(wordhash)).intValue();
                        indexabstract.append("indexabstract." + wordhash + "=").append((String) theSearch.IAResults.get(wordhash)).append(serverCore.crlfString);
                    }
                }
                prop.put("indexcount", indexcount.toString());
                
                if (theSearch.getLocalCount() == 0) {
                    joincount = 0;
                    prop.put("joincount", "0");
                } else {
                    joincount = theSearch.getLocalCount();
                    prop.put("joincount", Integer.toString(joincount));
                    accu = theSearch.completeResults(duetime);
                }
                
                // generate compressed index for maxcounthash
                // this is not needed if the search is restricted to specific
                // urls, because it is a re-search
                if ((theSearch.IAmaxcounthash == null) || (urls.length() != 0) || (queryhashes.size() <= 1) || (abstracts.length() == 0)) {
                    prop.put("indexabstract", "");
                } else if (abstracts.equals("auto")) {
                    // automatically attach the index abstract for the index that has the most references. This should be our target dht position
                    indexabstractContainercount += ((Integer) theSearch.IACount.get(theSearch.IAmaxcounthash)).intValue();
                    indexabstract.append("indexabstract." + theSearch.IAmaxcounthash + "=").append((String) theSearch.IAResults.get(theSearch.IAmaxcounthash)).append(serverCore.crlfString);
                    if ((theSearch.IAneardhthash != null) && (!(theSearch.IAneardhthash.equals(theSearch.IAmaxcounthash)))) {
                        // in case that the neardhthash is different from the maxcounthash attach also the neardhthash-container
                        indexabstractContainercount += ((Integer) theSearch.IACount.get(theSearch.IAneardhthash)).intValue();
                        indexabstract.append("indexabstract." + theSearch.IAneardhthash + "=").append((String) theSearch.IAResults.get(theSearch.IAneardhthash)).append(serverCore.crlfString);
                    }
                    //System.out.println("DEBUG-ABSTRACTGENERATION: maxcounthash = " + maxcounthash);
                    //System.out.println("DEBUG-ABSTRACTGENERATION: neardhthash  = "+ neardhthash);
                    //yacyCore.log.logFine("DEBUG HASH SEARCH: " + indexabstract);
                }
            }
            if (partitions > 0) sb.requestedQueries = sb.requestedQueries + 1d / partitions; // increase query counter
            
            // prepare reference hints
            localProfiling.startTimer();
            Set ws = theSearch.references(10);
            StringBuffer refstr = new StringBuffer();
            Iterator j = ws.iterator();
            while (j.hasNext()) {
                refstr.append(",").append((String) j.next());
            }
            prop.put("references", (refstr.length() > 0) ? refstr.substring(1) : refstr.toString());
            localProfiling.yield("reference collection", ws.size());
        }
        prop.put("indexabstract", indexabstract.toString());
        
        // prepare result
        if ((joincount == 0) || (accu == null)) {
            
            // no results
            prop.put("links", "");
            prop.put("linkcount", "0");
            prop.put("references", "");

        } else {
            // result is a List of urlEntry elements
            localProfiling.startTimer();
            StringBuffer links = new StringBuffer();
            String resource = null;
            plasmaSearchEvent.ResultEntry entry;
            for (int i = 0; i < accu.size(); i++) {
                entry = (plasmaSearchEvent.ResultEntry) accu.get(i);
                resource = entry.resource();
                if (resource != null) {
                    links.append("resource").append(i).append('=').append(resource).append(serverCore.crlfString);
                }
            }
            prop.put("links", links.toString());
            prop.put("linkcount", accu.size());
            localProfiling.yield("result list preparation", accu.size());
        }
        
        // add information about forward peers
        prop.put("fwhop", ""); // hops (depth) of forwards that had been performed to construct this result
        prop.put("fwsrc", ""); // peers that helped to construct this result
        prop.put("fwrec", ""); // peers that would have helped to construct this result (recommendations)

        // prepare search statistics
        Long trackerHandle = new Long(System.currentTimeMillis());
        HashMap searchProfile = theQuery.resultProfile(joincount, System.currentTimeMillis() - timestamp, urlRetrievalAllTime, snippetComputationAllTime);
        String client = (String) header.get("CLIENTIP");
        searchProfile.put("host", client);
        yacySeed remotepeer = yacyCore.seedDB.lookupByIP(natLib.getInetAddress(client), true, false, false);
        searchProfile.put("peername", (remotepeer == null) ? "unknown" : remotepeer.getName());
        searchProfile.put("time", trackerHandle);
        sb.remoteSearches.add(searchProfile);
        TreeSet handles = (TreeSet) sb.remoteSearchTracker.get(client);
        if (handles == null) handles = new TreeSet();
        handles.add(trackerHandle);
        sb.remoteSearchTracker.put(client, handles);
        
        // log
        yacyCore.log.logInfo("EXIT HASH SEARCH: " +
                plasmaSearchQuery.anonymizedQueryHashes(theQuery.queryHashes) + " - " + joincount + " links found, " +
                prop.get("linkcount", "?") + " links selected, " +
                indexabstractContainercount + " index abstracts, " +
                (System.currentTimeMillis() - timestamp) + " milliseconds");
 
        prop.put("searchtime", System.currentTimeMillis() - timestamp);

        final int links = Integer.parseInt(prop.get("linkcount","0"));
        yacyCore.seedDB.mySeed().incSI(links);
        yacyCore.seedDB.mySeed().incSU(links);
        return prop;
    }

}
