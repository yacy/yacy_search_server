// SearchEventCache.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
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

package de.anomic.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.crawler.ResultURLs;
import de.anomic.yacy.yacySeedDB;

public class SearchEventCache {

    private static ARC<String, SearchEvent> lastEvents = new ConcurrentARC<String, SearchEvent>(1000, Runtime.getRuntime().availableProcessors() * 4); // a cache for objects from this class: re-use old search requests
    public static final long eventLifetime = 600000; // the time an event will stay in the cache, 10 Minutes
    public static final long memlimit = 100 * 1024 * 1024; // 100 MB
    public static String lastEventID = "";
    public static long cacheInsert = 0, cacheHit = 0, cacheMiss = 0, cacheDelete = 0;
    
    public static int size() {
        return lastEvents.size();
    }
    
    public static void put(String eventID, SearchEvent event) {
        lastEventID = eventID;
        SearchEvent oldEvent = lastEvents.put(eventID, event);
        if (oldEvent == null) cacheInsert++;
    }
    
    public static void cleanupEvents(final boolean all) {
        // remove old events in the event cache
        List<String> delete = new ArrayList<String>();
        // the less memory is there, the less time is acceptable for elements in the cache
        long memx = MemoryControl.available();
        long acceptTime = memx > memlimit ? eventLifetime : memx  * eventLifetime / memlimit;
        for (Map.Entry<String, SearchEvent> event: lastEvents) {
            if (all || event.getValue().getEventTime() + acceptTime < System.currentTimeMillis()) {
                event.getValue().cleanup();
                delete.add(event.getKey());
            }
        }
        // remove the events
        cacheDelete += delete.size();
        for (String k: delete) lastEvents.remove(k);
    }

    public static SearchEvent getEvent(final String eventID) {
        SearchEvent event = lastEvents.get(eventID);
        if (event == null) cacheMiss++; else cacheHit++;
        return event;
    }
    
    public static SearchEvent getEvent(
            final QueryParams query,
            final yacySeedDB peers,
            final ResultURLs crawlResults,
            final TreeMap<byte[], String> preselectedPeerHashes,
            final boolean generateAbstracts,
            final LoaderDispatcher loader) {
        
        String id = query.id(false);
        SearchEvent event = SearchEventCache.lastEvents.get(id);
        if (event == null) cacheMiss++; else cacheHit++;
        if (Switchboard.getSwitchboard() != null && !Switchboard.getSwitchboard().crawlQueues.noticeURL.isEmpty() && event != null && System.currentTimeMillis() - event.getEventTime() > 60000) {
            // if a local crawl is ongoing, don't use the result from the cache to use possibly more results that come from the current crawl
            // to prevent that this happens during a person switches between the different result pages, a re-search happens no more than
            // once a minute
            SearchEventCache.lastEvents.remove(id);
            cacheDelete++;
            event = null;
        } else {
            if (event != null) {
                //re-new the event time for this event, so it is not deleted next time too early
                event.resetEventTime();
                // replace the query, because this contains the current result offset
                event.setQuery(query);
            }
        }
        if (event == null) {
            // start a new event
            event = new SearchEvent(query, peers, crawlResults, preselectedPeerHashes, generateAbstracts, loader);
        }
    
        return event;
    }
}
