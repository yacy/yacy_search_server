// search.java
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


// You must compile this file with
// javac -classpath .:../../Classes search.java
// if the shell's current path is htroot/yacy

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.index.indexContainer;
import de.anomic.net.natLib;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaSearchEvent;
import de.anomic.plasma.plasmaSearchQuery;
import de.anomic.plasma.plasmaSearchRankingProfile;
import de.anomic.plasma.plasmaSearchPostOrder;
import de.anomic.plasma.plasmaSearchTimingProfile;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyDHTAction;
import de.anomic.yacy.yacySeed;
import de.anomic.tools.crypt;

public final class search {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch ss) {
        if (post == null || ss == null) { return null; }

        // return variable that accumulates replacements
        final plasmaSwitchboard sb = (plasmaSwitchboard) ss;
        
        //System.out.println("yacy: search received request = " + post.toString());

        final String  oseed  = post.get("myseed", ""); // complete seed of the requesting peer
//      final String  youare = post.get("youare", ""); // seed hash of the target peer, used for testing network stability
        final String  key    = post.get("key", "");    // transmission key for response
        final String  query  = post.get("query", "");  // a string of word hashes that shall be searched and combined
        String  urls   = post.get("urls", "");         // a string of url hashes that are preselected for the search: no other may be returned
        String abstracts = post.get("abstracts", "");  // a string of word hashes for abstracts that shall be generated, or 'auto' (for maxcount-word), or '' (for none)
//      final String  fwdep  = post.get("fwdep", "");  // forward depth. if "0" then peer may NOT ask another peer for more results
//      final String  fwden  = post.get("fwden", "");  // forward deny, a list of seed hashes. They may NOT be target of forward hopping
        final long    duetime= post.getLong("duetime", 3000);
        final int     count  = post.getInt("count", 10); // maximum number of wanted results
        final int     maxdist= post.getInt("maxdist", Integer.MAX_VALUE);
        final String  prefer = post.get("prefer", "");
        final String  contentdom = post.get("contentdom", "text");
        final String  filter = post.get("filter", ".*");
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
        
        // tell all threads to do nothing for a specific time
        sb.intermissionAllThreads(2 * duetime);

        Set abstractSet = ((abstracts.length() == 0) || (abstracts.equals("auto"))) ? null : plasmaSearchQuery.hashes2Set(abstracts);
        
        // store accessing peer
        if (yacyCore.seedDB == null) {
            yacyCore.log.logSevere("yacy.search: seed cache not initialized");
        } else {
            yacyCore.peerActions.peerArrival(yacySeed.genRemoteSeed(oseed, key, true), true);
        }

        // prepare search
        final Set keyhashes = plasmaSearchQuery.hashes2Set(query);
        final long timestamp = System.currentTimeMillis();
        serverObjects prop = new serverObjects();
        
        // prepare an abstract result
        StringBuffer indexabstract = new StringBuffer();
        int joincount = 0;
        plasmaSearchPostOrder acc = null;
        plasmaSearchQuery squery = null;
        plasmaSearchEvent theSearch = null;
        if ((query.length() == 0) && (abstractSet != null)) {
            // this is _not_ a normal search, only a request for index abstracts
            squery = new plasmaSearchQuery(abstractSet, maxdist, prefer, plasmaSearchQuery.contentdomParser(contentdom), count, duetime, filter, plasmaSearchQuery.catchall_constraint);
            squery.domType = plasmaSearchQuery.SEARCHDOM_LOCAL;
            yacyCore.log.logInfo("INIT HASH SEARCH (abstracts only): " + plasmaSearchQuery.anonymizedQueryHashes(squery.queryHashes) + " - " + squery.wantedResults + " links");

            // prepare a search profile
            plasmaSearchRankingProfile rankingProfile = (profile.length() == 0) ? new plasmaSearchRankingProfile(contentdom) : new plasmaSearchRankingProfile("", profile);
            plasmaSearchTimingProfile localTiming  = new plasmaSearchTimingProfile(squery.maximumTime, squery.wantedResults);
            plasmaSearchTimingProfile remoteTiming = null;

            theSearch = new plasmaSearchEvent(squery, rankingProfile, localTiming, remoteTiming, true, yacyCore.log, sb.wordIndex, sb.wordIndex.loadedURL, sb.snippetCache);
            Map containers = theSearch.localSearchContainers(plasmaSearchQuery.hashes2Set(urls));
            if (containers != null) {
                Iterator ci = containers.entrySet().iterator();
                Map.Entry entry;
                String wordhash;
                while (ci.hasNext()) {
                    entry = (Map.Entry) ci.next();
                    wordhash = (String) entry.getKey();
                    indexContainer container = (indexContainer) entry.getValue();
                    indexabstract.append("indexabstract." + wordhash + "=").append(plasmaURL.compressIndex(container, null, 1000).toString()).append(serverCore.crlfString);                
                }
            }
            
            prop.putASIS("indexcount", "");
            prop.put("joincount", 0);
        } else {
            // retrieve index containers from search request
            squery = new plasmaSearchQuery(keyhashes, maxdist, prefer, plasmaSearchQuery.contentdomParser(contentdom), count, duetime, filter, constraint);
            squery.domType = plasmaSearchQuery.SEARCHDOM_LOCAL;
            yacyCore.log.logInfo("INIT HASH SEARCH (query-" + abstracts + "): " + plasmaSearchQuery.anonymizedQueryHashes(squery.queryHashes) + " - " + squery.wantedResults + " links");

            // prepare a search profile
            plasmaSearchRankingProfile rankingProfile = (profile.length() == 0) ? new plasmaSearchRankingProfile(contentdom) : new plasmaSearchRankingProfile("", profile);
            plasmaSearchTimingProfile localTiming  = new plasmaSearchTimingProfile(squery.maximumTime, squery.wantedResults);
            plasmaSearchTimingProfile remoteTiming = null;

            theSearch = new plasmaSearchEvent(squery,
                    rankingProfile, localTiming, remoteTiming, true,
                    yacyCore.log, sb.wordIndex, sb.wordIndex.loadedURL,
                    sb.snippetCache);
            Map containers = theSearch.localSearchContainers(plasmaSearchQuery.hashes2Set(urls));

            // set statistic details of search result and find best result index set
            if (containers == null) {
                prop.putASIS("indexcount", "");
                prop.putASIS("joincount", "0");
            } else {
                Iterator ci = containers.entrySet().iterator();
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
                        mindhtdistance = d;
                        neardhthash = wordhash;
                    }
                    indexcount.append("indexcount.").append(container.getWordHash()).append('=').append(Integer.toString(container.size())).append(serverCore.crlfString);
                    if ((abstractSet != null) && (abstractSet.contains(wordhash))) {
                        indexabstract.append("indexabstract." + wordhash + "=").append(plasmaURL.compressIndex(container, null,1000).toString()).append(serverCore.crlfString);
                    }
                }
                prop.putASIS("indexcount", new String(indexcount));

                // join and order the result
                indexContainer localResults = theSearch.localSearchJoin(containers.values());
                if (localResults == null) {
                    joincount = 0;
                    prop.put("joincount", 0);
                    acc = null;
                } else {
                    joincount = localResults.size();
                    prop.putASIS("joincount", Integer.toString(joincount));
                    acc = theSearch.orderFinal(localResults);
                }
                // generate compressed index for maxcounthash
                // this is not needed if the search is restricted to specific
                // urls, because it is a re-search
                if ((maxcounthash == null) || (urls.length() != 0) || (keyhashes.size() == 1) || (abstracts.length() == 0)) {
                    prop.putASIS("indexabstract", "");
                } else if (abstracts.equals("auto")) {
                    indexabstract.append("indexabstract." + maxcounthash + "=").append(plasmaURL.compressIndex(((indexContainer) containers.get(maxcounthash)),localResults, 1000).toString()).append(serverCore.crlfString);
                    if ((neardhthash != null)
                            && (!(neardhthash.equals(maxcounthash)))) {
                        indexabstract.append("indexabstract." + neardhthash + "=").append(plasmaURL.compressIndex(((indexContainer) containers.get(neardhthash)), localResults, 1000).toString()).append(serverCore.crlfString);
                    }
                    //System.out.println("DEBUG-ABSTRACTGENERATION: maxcounthash = " + maxcounthash);
                    //System.out.println("DEBUG-ABSTRACTGENERATION: neardhthash  = "+ neardhthash);
                    //yacyCore.log.logFine("DEBUG HASH SEARCH: " + indexabstract);
                }
            }
        }
        prop.putASIS("indexabstract", indexabstract.toString());
        
        // prepare search statistics
        Long trackerHandle = new Long(System.currentTimeMillis());
        HashMap searchProfile = theSearch.resultProfile();
        searchProfile.put("resulttime", new Long(System.currentTimeMillis() - timestamp));
        searchProfile.put("resultcount", new Integer(joincount));
        String client = (String) header.get("CLIENTIP");
        searchProfile.put("host", client);
        yacySeed remotepeer = yacyCore.seedDB.lookupByIP(natLib.getInetAddress(client), true, false, false);
        searchProfile.put("peername", (remotepeer == null) ? "unknown" : remotepeer.getName());
        sb.remoteSearches.put(trackerHandle, searchProfile);
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
                    snippet = sb.snippetCache.retrieveTextSnippet(urlentry.comp().url(), squery.queryHashes, false, urlentry.flags().get(plasmaCondenser.flag_cat_indexof), 260, 1000);
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
            prop.putASIS("references", (refstr.length() > 0) ? refstr.substring(1) : refstr.toString());
        }
        
        // add information about forward peers
        prop.putASIS("fwhop", ""); // hops (depth) of forwards that had been performed to construct this result
        prop.putASIS("fwsrc", ""); // peers that helped to construct this result
        prop.putASIS("fwrec", ""); // peers that would have helped to construct this result (recommendations)
        
        // log
        yacyCore.log.logInfo("EXIT HASH SEARCH: " + plasmaSearchQuery.anonymizedQueryHashes(squery.queryHashes) + " - " + joincount + " links found, " + prop.get("linkcount", "?") + " links selected, " + ((System.currentTimeMillis() - timestamp) / 1000) + " seconds");
 
        prop.putASIS("searchtime", Long.toString(System.currentTimeMillis() - timestamp));

        final int links = Integer.parseInt(prop.get("linkcount","0"));
        yacyCore.seedDB.mySeed.incSI(links);
        yacyCore.seedDB.mySeed.incSU(links);
        return prop;
    }

}
