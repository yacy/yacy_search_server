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

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.repository.LoaderDispatcher;
import de.anomic.data.WorkTables;
import de.anomic.search.ResultFetcher.Worker;
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

    public static void put(final String eventID, final SearchEvent event) {
        if (MemoryControl.shortStatus()) cleanupEvents(true);
        lastEventID = eventID;
        final SearchEvent oldEvent = lastEvents.put(eventID, event);
        if (oldEvent == null) cacheInsert++;
    }

    public static void cleanupEvents(boolean all) {
        // remove old events in the event cache
        if (MemoryControl.shortStatus()) all = true;
        // the less memory is there, the less time is acceptable for elements in the cache
        final long memx = MemoryControl.available();
        final long acceptTime = memx > memlimitHigh ? eventLifetimeBigMem : memx > memlimitMedium ? eventLifetimeMediumMem : eventLifetimeShortMem;
        Map.Entry<String, SearchEvent> event;
        final Iterator<Map.Entry<String, SearchEvent>> i = lastEvents.entrySet().iterator();
        while (i.hasNext()) {
            event = i.next();
            if (all || event.getValue().getEventTime() + acceptTime < System.currentTimeMillis()) {
                if (workerAlive(event.getValue())) {
                    event.getValue().cleanup();
                } else {
                    i.remove();
                    cacheDelete++;
                }
            }
        }
    }

    public static SearchEvent getEvent(final String eventID) {
        final SearchEvent event = lastEvents.get(eventID);
        if (event == null) cacheMiss++; else cacheHit++;
        return event;
    }

    public static int countAliveThreads() {
        int alive = 0;
        for (final SearchEvent e: SearchEventCache.lastEvents.values()) {
            if (workerAlive(e)) alive++;
        }
        return alive;
    }

    private static boolean workerAlive(final SearchEvent e) {
        if (e == null || e.result() == null || e.result().workerThreads == null) return false;
        for (final Worker w: e.result().workerThreads) if (w != null && w.isAlive()) return true;
        return false;
    }

    private static SearchEvent dummyEvent = null;

    private static SearchEvent getDummyEvent(final WorkTables workTables, final LoaderDispatcher loader, final Segment indexSegment) {
        if (dummyEvent != null) return dummyEvent;
        final QueryParams query = new QueryParams("", 0, null, indexSegment, new RankingProfile(ContentDomain.TEXT), "");
        dummyEvent = new SearchEvent(query, null, workTables, null, false, loader, 0, 0, 0, 0, false);
        return dummyEvent;
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

        final String id = query.id(false);
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

            // throttling in case of too many search requests

            int waitcount = 0;
            throttling : while (true) {
                final int allowedThreads = (int) Math.max(1, MemoryControl.available() / (query.snippetCacheStrategy == null ? 10 : 100) / 1024 / 1024);
                // make room if there are too many search events (they need a lot of RAM)
                if (SearchEventCache.lastEvents.size() > allowedThreads) {
                    Log.logWarning("SearchEventCache", "throttling phase 1: " + SearchEventCache.lastEvents.size() + " in cache; " + countAliveThreads() + " alive; " + allowedThreads + " allowed");
                    cleanupEvents(false);
                } else break throttling;
                // if there are still some then delete just all
                if (SearchEventCache.lastEvents.size() > allowedThreads) {
                    Log.logWarning("SearchEventCache", "throttling phase 2: " + SearchEventCache.lastEvents.size() + " in cache; " + countAliveThreads() + " alive; " + allowedThreads + " allowed");
                    cleanupEvents(true);
                } else break throttling;
                // now there might be still events left that are alive
                if (countAliveThreads() < allowedThreads) break throttling;
                // finally we just wait some time until we get access
                Log.logWarning("SearchEventCache", "throttling phase 3: " + SearchEventCache.lastEvents.size() + " in cache; " + countAliveThreads() + " alive; " + allowedThreads + " allowed");
                try { Thread.sleep(100); } catch (final InterruptedException e) { }
                waitcount++;
                if (waitcount >= 10) return getDummyEvent(workTables, loader, query.getSegment());
            }

            // check if there are too many other searches alive now
            Log.logInfo("SearchEventCache", "getEvent: " + SearchEventCache.lastEvents.size() + " in cache; " + countAliveThreads() + " alive");

            // start a new event
            final boolean delete = Switchboard.getSwitchboard() == null | Switchboard.getSwitchboard().getConfigBool("search.verify.delete", true);
            event = new SearchEvent(query, peers, workTables, preselectedPeerHashes, generateAbstracts, loader, remote_maxcount, remote_maxtime, burstRobinsonPercent, burstMultiwordPercent, delete);
            MemoryControl.request(100 * 1024 * 1024, false); // this may trigger a short memory status which causes a reducing of cache space of other threads
        }

        return event;
    }
}
