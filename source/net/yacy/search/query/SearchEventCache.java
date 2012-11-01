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

package net.yacy.search.query;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.data.WorkTables;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.peers.SeedDB;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

public class SearchEventCache {

    private volatile static Map<String, SearchEvent> lastEvents = new ConcurrentHashMap<String, SearchEvent>(); // a cache for objects from this class: re-use old search requests
    private static final long eventLifetimeBigMem = 600000; // the time an event will stay in the cache when available memory is high, 10 Minutes
    private static final long eventLifetimeMediumMem = 60000; // the time an event will stay in the cache when available memory is medium, 1 Minute
    private static final long eventLifetimeShortMem = 10000; // the time an event will stay in the cache when memory is low, 10 seconds
    private static final long memlimitHigh = 600 * 1024 * 1024; // 400 MB
    private static final long memlimitMedium = 200 * 1024 * 1024; // 100 MB
    public volatile static String lastEventID = "";
    public static long cacheInsert = 0, cacheHit = 0, cacheMiss = 0, cacheDelete = 0;

    public static int size() {
        return lastEvents.size();
    }

    protected static void put(final String eventID, final SearchEvent event) {
        if (MemoryControl.shortStatus()) cleanupEvents(false);
        lastEventID = eventID;
        final SearchEvent oldEvent = lastEvents.put(eventID, event);
        if (oldEvent == null) cacheInsert++;
    }

    public static boolean delete(final String urlhash) {
        for (final SearchEvent event: lastEvents.values()) {
            if (event.result().delete(urlhash)) return true;
        }
        return false;
    }

    public static void cleanupEvents(boolean all) {
        // remove old events in the event cache
        if (MemoryControl.shortStatus()) all = true;
        // the less memory is there, the less time is acceptable for elements in the cache
        final long memx = MemoryControl.available();
        final long acceptTime = memx > memlimitHigh ? eventLifetimeBigMem : memx > memlimitMedium ? eventLifetimeMediumMem : eventLifetimeShortMem;
        Map.Entry<String, SearchEvent> eventEntry;
        final Iterator<Map.Entry<String, SearchEvent>> i = lastEvents.entrySet().iterator();
        SearchEvent event;
        while (i.hasNext()) {
            eventEntry = i.next();
            event = eventEntry.getValue();
            if (event == null) continue;
            if (all || event.getEventTime() + acceptTime < System.currentTimeMillis()) {
                if (event.workerAlive()) {
                    event.cleanup();
                }
                i.remove();
                cacheDelete++;
            }
        }
    }

    public static SearchEvent getEvent(final String eventID) {
        SearchEvent event = lastEvents.get(eventID);
        if (event == null) {
            synchronized (lastEvents) {
                event = lastEvents.get(eventID);
                if (event == null) cacheMiss++; else cacheHit++;
            }
            cacheMiss++;
        } else {
            cacheHit++;
        }
        return event;
    }

    private static int countAliveThreads() {
        int alive = 0;
        for (final SearchEvent e: lastEvents.values()) {
            if (e.workerAlive()) alive++;
        }
        return alive;
    }

    public static SearchEvent getEvent(
            final QueryParams query,
            final SeedDB peers,
            final WorkTables workTables,
            final SortedMap<byte[], String> preselectedPeerHashes,
            final boolean generateAbstracts,
            final LoaderDispatcher loader,
            final int remote_maxcount,
            final long remote_maxtime,
            final int burstRobinsonPercent,
            final int burstMultiwordPercent) {

        final String id = query.id(false);
        SearchEvent event = getEvent(id);
        if (Switchboard.getSwitchboard() != null && !Switchboard.getSwitchboard().crawlQueues.noticeURL.isEmpty() && event != null && System.currentTimeMillis() - event.getEventTime() > 60000) {
            // if a local crawl is ongoing, don't use the result from the cache to use possibly more results that come from the current crawl
            // to prevent that this happens during a person switches between the different result pages, a re-search happens no more than
            // once a minute
            lastEvents.remove(id);
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
            // check if there are too many other searches alive now
            Log.logInfo("SearchEventCache", "getEvent: " + lastEvents.size() + " in cache; " + countAliveThreads() + " alive");

            // start a new event
            final boolean delete = Switchboard.getSwitchboard() == null || Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.SEARCH_VERIFY_DELETE, true);
            event = new SearchEvent(query, peers, workTables, preselectedPeerHashes, generateAbstracts, loader, remote_maxcount, remote_maxtime, burstRobinsonPercent, burstMultiwordPercent, delete);
            MemoryControl.request(100 * 1024 * 1024, false); // this may trigger a short memory status which causes a reducing of cache space of other threads
        }

        return event;
    }
}
