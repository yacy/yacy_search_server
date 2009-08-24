// SearchEventCache.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

package de.anomic.search;

import java.io.IOException;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.crawler.ResultURLs;
import de.anomic.kelondro.text.Segment;
import de.anomic.search.SearchEvent.SnippetFetcher;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.logging.Log;

public class SearchEventCache {

    protected static ConcurrentHashMap<String, SearchEvent> lastEvents = new ConcurrentHashMap<String, SearchEvent>(); // a cache for objects from this class: re-use old search requests
    public static final long eventLifetime = 60000; // the time an event will stay in the cache, 1 Minute
    
    public static void cleanupEvents(final boolean all) {
        // remove old events in the event cache
        final Iterator<SearchEvent> i = lastEvents.values().iterator();
        SearchEvent cleanEvent;
        while (i.hasNext()) {
            cleanEvent = i.next();
            if ((all) || (cleanEvent.eventTime + eventLifetime < System.currentTimeMillis())) {
                // execute deletion of failed words
                int rw = cleanEvent.failedURLs.size();
                if (rw > 0) {
                    final TreeSet<byte[]> removeWords = cleanEvent.query.queryHashes;
                    removeWords.addAll(cleanEvent.query.excludeHashes);
                    try {
                        final Iterator<byte[]> j = removeWords.iterator();
                        // remove the same url hashes for multiple words
                        while (j.hasNext()) {
                            cleanEvent.indexSegment.termIndex().remove(j.next(), cleanEvent.failedURLs.keySet());
                        }                    
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.logInfo("SearchEvents", "cleaning up event " + cleanEvent.query.id(true) + ", removed " + rw + " URL references on " + removeWords.size() + " words");
                }                
                
                // remove the event
                i.remove();
            }
        }
    }

    public static SearchEvent getEvent(final String eventID) {
        return lastEvents.get(eventID);
    }
    
    public static SearchEvent getEvent(
            final QueryParams query,
            final Segment indexSegment,
            final yacySeedDB peers,
            final ResultURLs crawlResults,
            final TreeMap<byte[], String> preselectedPeerHashes,
            final boolean generateAbstracts) {
        
        String id = query.id(false);
        SearchEvent event = SearchEventCache.lastEvents.get(id);
        if (Switchboard.getSwitchboard().crawlQueues.noticeURL.size() > 0 && event != null && System.currentTimeMillis() - event.eventTime > 60000) {
            // if a local crawl is ongoing, don't use the result from the cache to use possibly more results that come from the current crawl
            // to prevent that this happens during a person switches between the different result pages, a re-search happens no more than
            // once a minute
            SearchEventCache.lastEvents.remove(id);
            event = null;
        } else {
            if (event != null) {
                //re-new the event time for this event, so it is not deleted next time too early
                event.eventTime = System.currentTimeMillis();
                // replace the query, because this contains the current result offset
                event.query = query;
            }
        }
        if (event == null) {
            // generate a new event
            event = new SearchEvent(query, indexSegment, peers, crawlResults, preselectedPeerHashes, generateAbstracts);
        } else {
            // if worker threads had been alive, but did not succeed, start them again to fetch missing links
            if ((!event.anyWorkerAlive()) &&
                (((query.contentdom == QueryParams.CONTENTDOM_IMAGE) && (event.images.size() + 30 < query.neededResults())) ||
                 (event.result.size() < query.neededResults() + 10)) &&
                 //(event.query.onlineSnippetFetch) &&
                (event.getRankingResult().getLocalResourceSize() + event.getRankingResult().getRemoteResourceSize() > event.result.size())) {
                // set new timeout
                event.eventTime = System.currentTimeMillis();
                // start worker threads to fetch urls and snippets
                event.workerThreads = new SnippetFetcher[SearchEvent.workerThreadCount];
                SnippetFetcher worker;
                for (int i = 0; i < event.workerThreads.length; i++) {
                    worker = event.new SnippetFetcher(i, 6000, (query.onlineSnippetFetch) ? 2 : 0);
                    worker.start();
                    event.workerThreads[i] = worker;
                }
            }
        }
    
        return event;
    }
}
