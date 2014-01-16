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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.ISO639;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.EventChannel;
import net.yacy.peers.Network;
import net.yacy.peers.Protocol;
import net.yacy.peers.Seed;
import net.yacy.peers.graphics.ProfilingGraph;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.Segment;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.QueryParams;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.search.query.SearchEventType;
import net.yacy.search.ranking.RankingProfile;
import net.yacy.search.snippet.ResultEntry;
import net.yacy.server.serverCore;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.utils.crypt;

public final class search {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final Switchboard sb = (Switchboard) env;
        sb.remoteSearchLastAccess = System.currentTimeMillis();

        final serverObjects prop = new serverObjects();
        // set nice default values for error cases
        prop.put("searchtime", "0");
        prop.put("references", "");
        prop.put("joincount", "0");
        prop.put("linkcount", "0");
        prop.put("links", "");
        prop.put("indexcount", "");
        prop.put("indexabstract", "");

        if (post == null || env == null) return prop;
        if (!Protocol.authentifyRequest(post, env)) return prop;
        final String client = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP);

        //System.out.println("yacy: search received request = " + post.toString());

        final String  oseed  = post.get("myseed", ""); // complete seed of the requesting peer
//      final String  youare = post.get("youare", ""); // seed hash of the target peer, used for testing network stability
        final String  query  = post.get("query", "");  // a string of word hashes that shall be searched and combined
        final String  exclude= post.get("exclude", "");// a string of word hashes that shall not be within the search result
        final String  urls   = post.get("urls", "");         // a string of url hashes that are preselected for the search: no other may be returned
        final String  abstracts = post.get("abstracts", "");  // a string of word hashes for abstracts that shall be generated, or 'auto' (for maxcount-word), or '' (for none)
        final int     count  = Math.min((int) sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXCOUNT_DEFAULT, 100), post.getInt("count", 10)); // maximum number of wanted results
        final long    maxtime = Math.min((int) sb.getConfigLong(SwitchboardConstants.REMOTESEARCH_MAXTIME_DEFAULT, 3000), post.getLong("time", 3000)); // maximum waiting time
        final int     maxdist= post.getInt("maxdist", Integer.MAX_VALUE);
        final String  prefer = post.get("prefer", "");
        final String  contentdom = post.get("contentdom", "all");
        final String  filter = post.get("filter", ".*"); // a filter on the url
        QueryModifier modifier = new QueryModifier();
        modifier.sitehost = post.get("sitehost", ""); if (modifier.sitehost.isEmpty()) modifier.sitehost = null;
        modifier.sitehash = post.get("sitehash", ""); if (modifier.sitehash.isEmpty()) modifier.sitehash = null;
        modifier.author = post.get("author", ""); if (modifier.author.isEmpty()) modifier.author = null;
        modifier.filetype = post.get("filetype", ""); if (modifier.filetype.isEmpty()) modifier.filetype = null;
        modifier.protocol = post.get("protocol", ""); if (modifier.protocol.isEmpty()) modifier.protocol = null;
        modifier.parse(post.get("modifier", "").trim());
        String  language = post.get("language", "");
        if (language == null || language.isEmpty() || !ISO639.exists(language)) {
            // take language from the user agent
            String agent = header.get("User-Agent");
            if (agent == null) agent = System.getProperty("user.language");
            language = (agent == null) ? "en" : ISO639.userAgentLanguageDetection(agent);
            if (language == null) language = "en";
        }
        final int partitions = post.getInt("partitions", 30);
        String profile = post.get("profile", ""); // remote profile hand-over
        if (profile.length() > 0) profile = crypt.simpleDecode(profile);
        //final boolean includesnippet = post.get("includesnippet", "false").equals("true");
        Bitfield constraint = ((post.containsKey("constraint")) && (post.get("constraint", "").length() > 0)) ? new Bitfield(4, post.get("constraint", "______")) : null;
        if (constraint != null) {
        	// check bad handover parameter from older versions
            boolean allon = true;
            for (int i = 0; i < 32; i++) {
            	if (!constraint.get(i)) {allon = false; break;}
            }
            if (allon) constraint = null;
        }
//      Date remoteTime = yacyCore.parseUniversalDate((String) post.get(yacySeed.MYTIME));        // read remote time

        // test:
        // http://localhost:8090/yacy/search.html?query=4galTpdpDM5Q (search for linux)
        // http://localhost:8090/yacy/search.html?query=gh8DKIhGKXws (search for book)
        // http://localhost:8090/yacy/search.html?query=UEhMGfGv2vOE (search for kernel)
        // http://localhost:8090/yacy/search.html?query=ZX-LjaYo74PP (search for help)
        // http://localhost:8090/yacy/search.html?query=uDqIalxDfM2a (search for mail)
        // http://localhost:8090/yacy/search.html?query=4galTpdpDM5Qgh8DKIhGKXws&abstracts=auto (search for linux and book, generate abstract automatically)
        // http://localhost:8090/yacy/search.html?query=&abstracts=4galTpdpDM5Q (only abstracts for linux)

        if (sb.isRobinsonMode() && !sb.isPublicRobinson()) {
            // if we are a robinson cluster, answer only if this client is known by our network definition
        	return prop;
        }

        // check the search tracker
        TreeSet<Long> trackerHandles = sb.remoteSearchTracker.get(client);
        if (trackerHandles == null) trackerHandles = new TreeSet<Long>();
        boolean block = false;
        synchronized (trackerHandles) {
            if (trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() -   3000)).size() >  1) {
                block = true;
            }
        }
        if (!block) synchronized (trackerHandles) {
            if (trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() -  60000)).size() > 12) {
                block = true;
            }
        }
        if (!block) synchronized (trackerHandles) {
            if (trackerHandles.tailSet(Long.valueOf(System.currentTimeMillis() - 600000)).size() > 36) {
                block = true;
            }
        }
        if (block && Domains.isLocal(client, null)) block = false; // check isLocal here to prevent dns lookup for client
        if (block) {
            return prop;
        }

        // tell all threads to do nothing for a specific time
        sb.intermissionAllThreads(100);

        EventTracker.delete(EventTracker.EClass.SEARCH);
        final HandleSet abstractSet = (abstracts.isEmpty() || abstracts.equals("auto")) ? null : QueryParams.hashes2Set(abstracts);

        // store accessing peer
        Seed remoteSeed;
        try {
            remoteSeed = Seed.genRemoteSeed(oseed, false, client);
        } catch (final IOException e) {
            Network.log.info("yacy.search: access with bad seed: " + e.getMessage());
            remoteSeed = null;
        }
        if (sb.peers == null) {
            Network.log.severe("yacy.search: seed cache not initialized");
        } else {
            sb.peers.peerActions.peerArrival(remoteSeed, true);
        }

        // prepare search
        final HandleSet queryhashes = QueryParams.hashes2Set(query);
        final HandleSet excludehashes = (exclude.isEmpty()) ? new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0) : QueryParams.hashes2Set(exclude);
        final long timestamp = System.currentTimeMillis();

    	// prepare a search profile
        final RankingProfile rankingProfile = (profile.isEmpty()) ? new RankingProfile(Classification.ContentDomain.contentdomParser(contentdom)) : new RankingProfile("", profile);

        // prepare an abstract result
        final StringBuilder indexabstract = new StringBuilder(6000);
        int indexabstractContainercount = 0;
        QueryParams theQuery = null;
        SearchEvent theSearch = null;
        ArrayList<WeakPriorityBlockingQueue.Element<ResultEntry>> accu = null;
        if (query.isEmpty() && abstractSet != null) {
            // this is _not_ a normal search, only a request for index abstracts
            final Segment indexSegment = sb.index;
            QueryGoal qg = new QueryGoal(abstractSet, new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0));
            theQuery = new QueryParams(
                    qg,
                    modifier,
                    maxdist,
                    prefer,
                    ContentDomain.contentdomParser(contentdom),
                    language,
                    new HashSet<Tagging.Metatag>(),
                    null, // no snippet computation
                    count,
                    0,
                    filter,
                    null,
                    null,
                    QueryParams.Searchdom.LOCAL,
                    null,
                    false,
                    null,
                    MultiProtocolURL.TLD_any_zone_filter,
                    client,
                    false,
                    indexSegment,
                    rankingProfile,
                    header.get(HeaderFramework.USER_AGENT, ""),
                    false,
                    false,
                    0.0d,
                    0.0d,
                    0.0d,
                    new String[0]
                    );
            Network.log.info("INIT HASH SEARCH (abstracts only): " + QueryParams.anonymizedQueryHashes(theQuery.getQueryGoal().getIncludeHashes()) + " - " + theQuery.itemsPerPage() + " links");

            final long timer = System.currentTimeMillis();
            //final Map<byte[], ReferenceContainer<WordReference>>[] containers = sb.indexSegment.index().searchTerm(theQuery.queryHashes, theQuery.excludeHashes, plasmaSearchQuery.hashes2StringSet(urls));
            final TreeMap<byte[], ReferenceContainer<WordReference>> incc = indexSegment.termIndex() == null ? new TreeMap<byte[], ReferenceContainer<WordReference>>() : indexSegment.termIndex().searchConjunction(theQuery.getQueryGoal().getIncludeHashes(), QueryParams.hashes2Set(urls));

            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(theQuery.id(true), SearchEventType.COLLECTION, "", incc.size(), System.currentTimeMillis() - timer), false);
            if (incc != null) {
                final Iterator<Map.Entry<byte[], ReferenceContainer<WordReference>>> ci = incc.entrySet().iterator();
                Map.Entry<byte[], ReferenceContainer<WordReference>> entry;
                byte[] wordhash;
                while (ci.hasNext()) {
                    entry = ci.next();
                    wordhash = entry.getKey();
                    final ReferenceContainer<WordReference> container = entry.getValue();
                    indexabstractContainercount += container.size();
                    indexabstract.append("indexabstract.");
                    indexabstract.append(ASCII.String(wordhash));
                    indexabstract.append("=");
                    indexabstract.append(WordReferenceFactory.compressIndex(container, null, 1000).toString());
                    indexabstract.append(serverCore.CRLF_STRING);
                }
            }

            prop.put("indexcount", "");
            prop.put("joincount", "0");
            prop.put("references", "");

        } else {
            // retrieve index containers from search request
            RowHandleSet allHashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            try {allHashes.putAll(queryhashes);} catch (final SpaceExceededException e) {}
            try {allHashes.putAll(excludehashes);} catch (final SpaceExceededException e) {}
            QueryGoal qg = new QueryGoal(queryhashes, excludehashes);
            theQuery = new QueryParams(
                    qg,
                    modifier,
                    maxdist,
                    prefer,
                    ContentDomain.contentdomParser(contentdom),
                    language,
                    new HashSet<Tagging.Metatag>(),
                    null, // no snippet computation
                    count,
                    0,
                    filter,
                    null,
                    null,
                    QueryParams.Searchdom.LOCAL,
                    constraint,
                    false,
                    null,
                    MultiProtocolURL.TLD_any_zone_filter,
                    client,
                    false,
                    sb.index,
                    rankingProfile,
                    header.get(HeaderFramework.USER_AGENT, ""),
                    false,
                    false,
                    0.0d,
                    0.0d,
                    0.0d,
                    new String[0]
                    );
            Network.log.info("INIT HASH SEARCH (query-" + abstracts + "): " + QueryParams.anonymizedQueryHashes(theQuery.getQueryGoal().getIncludeHashes()) + " - " + theQuery.itemsPerPage() + " links");
            EventChannel.channels(EventChannel.REMOTESEARCH).addMessage(new RSSMessage("Remote Search Request from " + ((remoteSeed == null) ? "unknown" : remoteSeed.getName()), QueryParams.anonymizedQueryHashes(theQuery.getQueryGoal().getIncludeHashes()), ""));

            // make event
            theSearch = SearchEventCache.getEvent(theQuery, sb.peers, sb.tables, null, abstracts.length() > 0, sb.loader, count, maxtime);
            if (theSearch.rwiProcess != null && theSearch.rwiProcess.isAlive()) try {theSearch.rwiProcess.join();} catch (final InterruptedException e) {}

            // set statistic details of search result and find best result index set
            prop.put("joincount", Integer.toString(theSearch.getResultCount()));
            if (theSearch.getResultCount() > 0) {
                accu = theSearch.completeResults(maxtime);
            }
            if (theSearch.getResultCount() <= 0 || abstracts.isEmpty()) {
                prop.put("indexcount", "");
            } else {
                // attach information about index abstracts
                final StringBuilder indexcount = new StringBuilder(6000);
                Map.Entry<byte[], Integer> entry;
                final Iterator<Map.Entry<byte[], Integer>> i = theSearch.abstractsCount();
                while (i.hasNext()) {
                    entry = i.next();
                    indexcount.append("indexcount.").append(ASCII.String(entry.getKey())).append('=').append((entry.getValue()).toString()).append(serverCore.CRLF_STRING);
                }
                if (abstractSet != null) {
                    // if a specific index-abstract is demanded, attach it here
                    final Iterator<byte[]> j = abstractSet.iterator();
                    byte[] wordhash;
                    while (j.hasNext()) {
                        wordhash = j.next();
                        indexabstractContainercount += theSearch.abstractsCount(wordhash);
                        indexabstract.append("indexabstract.").append(ASCII.String(wordhash)).append("=").append(theSearch.abstractsString(wordhash)).append(serverCore.CRLF_STRING);
                    }
                }
                prop.put("indexcount", indexcount.toString());

                // generate compressed index for maxcounthash
                // this is not needed if the search is restricted to specific
                // urls, because it is a re-search
                if ((theSearch.getAbstractsMaxCountHash() == null) || (urls.length() != 0) || (queryhashes.size() <= 1) || (abstracts.isEmpty())) {
                    prop.put("indexabstract", "");
                } else if (abstracts.equals("auto")) {
                    // automatically attach the index abstract for the index that has the most references. This should be our target dht position
                    indexabstractContainercount += theSearch.abstractsCount(theSearch.getAbstractsMaxCountHash());
                    indexabstract.append("indexabstract.").append(ASCII.String(theSearch.getAbstractsMaxCountHash())).append("=").append(theSearch.abstractsString(theSearch.getAbstractsMaxCountHash())).append(serverCore.CRLF_STRING);
                    if ((theSearch.getAbstractsNearDHTHash() != null) && (!(ByteBuffer.equals(theSearch.getAbstractsNearDHTHash(), theSearch.getAbstractsMaxCountHash())))) {
                        // in case that the neardhthash is different from the maxcounthash attach also the neardhthash-container
                        indexabstractContainercount += theSearch.abstractsCount(theSearch.getAbstractsNearDHTHash());
                        indexabstract.append("indexabstract.").append(ASCII.String(theSearch.getAbstractsNearDHTHash())).append("=").append(theSearch.abstractsString(theSearch.getAbstractsNearDHTHash())).append(serverCore.CRLF_STRING);
                    }
                    //System.out.println("DEBUG-ABSTRACTGENERATION: maxcounthash = " + maxcounthash);
                    //System.out.println("DEBUG-ABSTRACTGENERATION: neardhthash  = "+ neardhthash);
                    //yacyCore.log.logFine("DEBUG HASH SEARCH: " + indexabstract);
                }
            }
            if (partitions > 0) sb.searchQueriesGlobal += 1d / partitions; // increase query counter

            // prepare reference hints
            final long timer = System.currentTimeMillis();
            final ScoreMap<String> topicNavigator = sb.index.connectedRWI() ? theSearch.getTopics(5, 100) : new ConcurrentScoreMap<String>();
            final StringBuilder refstr = new StringBuilder(6000);
            final Iterator<String> navigatorIterator = topicNavigator.keys(false);
            int i = 0;
            String name;
            while (i < 5 && navigatorIterator.hasNext()) {
                name = navigatorIterator.next();
                refstr.append(",").append(name);
                i++;
            }
            prop.put("references", (refstr.length() > 0) ? refstr.substring(1) : refstr.toString());
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(theQuery.id(true), SearchEventType.REFERENCECOLLECTION, "", i, System.currentTimeMillis() - timer), false);
        }
        prop.put("indexabstract", indexabstract.toString());

        // prepare result
        int resultCount = theSearch == null ? 0 : theSearch.getResultCount(); // theSearch may be null if we searched only for abstracts
        if (resultCount == 0 || accu == null || accu.isEmpty()) {

            // no results
            prop.put("links", "");
            prop.put("linkcount", "0");
            prop.put("references", "");

        } else {
            // result is a List of urlEntry elements
            final long timer = System.currentTimeMillis();
            final StringBuilder links = new StringBuilder(6000);
            String resource = null;
            WeakPriorityBlockingQueue.Element<ResultEntry> entry;
            for (int i = 0; i < accu.size(); i++) {
                entry = accu.get(i);
                resource = entry.getElement().resource();
                if (resource != null) {
                    links.append("resource").append(i).append('=').append(resource).append(serverCore.CRLF_STRING);
                }
            }
            theQuery.transmitcount = accu.size() + 1;
            prop.put("links", links.toString());
            prop.put("linkcount", accu.size());
            EventTracker.update(EventTracker.EClass.SEARCH, new ProfilingGraph.EventSearch(theQuery.id(true), SearchEventType.RESULTLIST, "", accu.size(), System.currentTimeMillis() - timer), false);
        }

        // prepare search statistics
        theQuery.remotepeer = client == null ? null : sb.peers.lookupByIP(Domains.dnsResolve(client), -1, true, false, false);
        theQuery.searchtime = System.currentTimeMillis() - timestamp;
        theQuery.urlretrievaltime = (theSearch == null) ? 0 : theSearch.getURLRetrievalTime();
        theQuery.snippetcomputationtime = (theSearch == null) ? 0 : theSearch.getSnippetComputationTime();
        AccessTracker.add(AccessTracker.Location.remote, theQuery, resultCount);

        // update the search tracker
        synchronized (trackerHandles) {
            trackerHandles.add(theQuery.starttime); // thats the time when the handle was created
            // we don't need too much entries in the list; remove superfluous
            while (trackerHandles.size() > 36) if (!trackerHandles.remove(trackerHandles.first())) break;
        }
        sb.remoteSearchTracker.put(client, trackerHandles);
        if (MemoryControl.shortStatus()) sb.remoteSearchTracker.clear();

        // log
        Network.log.info("EXIT HASH SEARCH: " +
                QueryParams.anonymizedQueryHashes(theQuery.getQueryGoal().getIncludeHashes()) + " - " + resultCount + " links found, " +
                prop.get("linkcount", "?") + " links selected, " +
                indexabstractContainercount + " index abstracts, " +
                (System.currentTimeMillis() - timestamp) + " milliseconds");

        prop.put("searchtime", System.currentTimeMillis() - timestamp);

        final int links = prop.getInt("linkcount",0);
        sb.peers.mySeed().incSI(links);
        sb.peers.mySeed().incSU(links);
        return prop;
    }

}
