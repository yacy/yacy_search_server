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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.crawler.ResultURLs;
import de.anomic.yacy.yacySeedDB;

public class SearchEventCache {

    private static ConcurrentHashMap<String, SearchEvent> lastEvents = new ConcurrentHashMap<String, SearchEvent>(); // a cache for objects from this class: re-use old search requests
    public static final long eventLifetime = 60000; // the time an event will stay in the cache, 1 Minute

    public static String lastEventID = "";
    
    public static void put(String eventID, SearchEvent event) {
        lastEventID = eventID;
        lastEvents.put(eventID, event);
    }
    
    public static void cleanupEvents(final boolean all) {
        // remove old events in the event cache
        final Iterator<SearchEvent> i = lastEvents.values().iterator();
        SearchEvent event;
        while (i.hasNext()) {
            event = i.next();
            if ((all) || (event.getEventTime() + eventLifetime < System.currentTimeMillis())) {
                event.cleanup();
                
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
            final yacySeedDB peers,
            final ResultURLs crawlResults,
            final TreeMap<byte[], String> preselectedPeerHashes,
            final boolean generateAbstracts) {
        
        String id = query.id(false);
        SearchEvent event = SearchEventCache.lastEvents.get(id);
        if (Switchboard.getSwitchboard() != null && !Switchboard.getSwitchboard().crawlQueues.noticeURL.isEmpty() && event != null && System.currentTimeMillis() - event.getEventTime() > 60000) {
            // if a local crawl is ongoing, don't use the result from the cache to use possibly more results that come from the current crawl
            // to prevent that this happens during a person switches between the different result pages, a re-search happens no more than
            // once a minute
            SearchEventCache.lastEvents.remove(id);
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
            event = new SearchEvent(query, peers, crawlResults, preselectedPeerHashes, generateAbstracts);
        }
    
        return event;
    }
}
