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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.util.MemoryControl;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.data.WorkTables;
import de.anomic.yacy.yacySeedDB;

public class SearchEventCache {

    private static ConcurrentMap<String, SearchEvent> lastEvents = new ConcurrentHashMap<String, SearchEvent>(); // a cache for objects from this class: re-use old search requests
    public static final long eventLifetimeBigMem = 600000; // the time an event will stay in the cache when available memory is high, 10 Minutes
    public static final long eventLifetimeMediumMem = 60000; // the time an event will stay in the cache when available memory is medium, 1 Minute
    public static final long eventLifetimeShortMem = 10000; // the time an event will stay in the cache when memory is low, 10 seconds
    public static final long memlimitHigh = 600 * 1024 * 1024; // 400 MB
    public static final long memlimitMedium = 200 * 1024 * 1024; // 100 MB
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
        final List<SearchEvent> delete = new ArrayList<SearchEvent>();
        // the less memory is there, the less time is acceptable for elements in the cache
        long memx = MemoryControl.available();
        long acceptTime = memx > memlimitHigh ? eventLifetimeBigMem : memx > memlimitMedium ? eventLifetimeMediumMem : eventLifetimeShortMem;
        Map.Entry<String, SearchEvent> event;
        Iterator<Map.Entry<String, SearchEvent>> i = lastEvents.entrySet().iterator();
        while (i.hasNext()) {
            event = i.next();
            if (all || event.getValue().getEventTime() + acceptTime < System.currentTimeMillis()) {
                delete.add(event.getValue());
                i.remove();
                cacheDelete++;
            }
        }
        /*
         * thread to remove the events;
         * this process may take time because it applies index modifications
         * in case of failed words 
         */
        new Thread(){
            @Override
            public void run() {
                for (SearchEvent k: delete) {
                    k.cleanup();
                }
            }
        }.start();
    }
    
    public static SearchEvent getEvent(final String eventID) {
        SearchEvent event = lastEvents.get(eventID);
        if (event == null) cacheMiss++; else cacheHit++;
        return event;
    }
    
    public static SearchEvent getEvent(
            final QueryParams query,
            final yacySeedDB peers,
            final WorkTables workTables,
            final SortedMap<byte[], String> preselectedPeerHashes,
            final boolean generateAbstracts,
            final LoaderDispatcher loader,
            final int remote_maxcount,
            final long remote_maxtime,
            final int burstRobinsonPercent,
            final int burstMultiwordPercent) {
        
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
            boolean delete = Switchboard.getSwitchboard() == null | Switchboard.getSwitchboard().getConfigBool("search.verify.delete", true);
            event = new SearchEvent(query, peers, workTables, preselectedPeerHashes, generateAbstracts, loader, remote_maxcount, remote_maxtime, burstRobinsonPercent, burstMultiwordPercent, delete);
        }
    
        return event;
    }
}
