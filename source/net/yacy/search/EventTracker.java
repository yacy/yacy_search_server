// EventTracker.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.11.2007 on http://yacy.net
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

package net.yacy.search;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.peers.graphics.ProfilingGraph;


public class EventTracker {

    private final static int  maxQueueSize = 30000;
    private final static long maxQueueAge = ProfilingGraph.maxTime;

    public enum EClass {
        WORDCACHE,
        MEMORY,
        PPM,
        PEERPING,
        DHT,
        INDEX,
        SEARCH;
    }

    private final static Map<EClass, Queue<Event>> historyMaps = new ConcurrentHashMap<EClass, Queue<Event>>();
    private final static Map<EClass, Long> eventAccess = new ConcurrentHashMap<EClass, Long>(); // value: last time when this was accessed

    public final static void delete(final EClass eventName) {
        historyMaps.remove(eventName);
        eventAccess.remove(eventName);
    }

    public final static void update(final EClass eventName, final Object eventPayload, final boolean useProtection) {
        // check protection against too heavy access
        if (useProtection) {
            final Long lastAcc = eventAccess.get(eventName);
            if (lastAcc == null) {
                eventAccess.put(eventName, Long.valueOf(System.currentTimeMillis()));
            } else {
                final long time = System.currentTimeMillis();
                if (time - lastAcc.longValue() < 1000) {
                    return; // protect against too heavy load
                }
                eventAccess.put(eventName, Long.valueOf(time));
            }
        }

        // get event history container
        Queue<Event> history = historyMaps.get(eventName);

        // create history
        if (history == null) {
            history = new LinkedBlockingQueue<Event>();

            // update entry
            history.offer(new Event(eventPayload));

            // store map
            historyMaps.put(eventName, history);
            return;
        }

        // update history
        history.offer(new Event(eventPayload));

        // clean up too old entries
        int tp = history.size() - maxQueueSize;
        while (tp-- > 0) history.poll();
        if (history.size() % 10 == 0) { // reduce number of System.currentTimeMillis() calls
            synchronized (history) {
                if (history.size() % 10 == 0) { // check again
                    Event e;
                    final long now = System.currentTimeMillis();
                    while (!history.isEmpty()) {
                        e = history.peek();
                        if (now - e.time < maxQueueAge) break;
                        history.poll();
                    }
                }
            }
        }
    }

    public final static Iterator<Event> getHistory(final EClass eventName) {
        final Queue<Event> list = historyMaps.get(eventName);
        if (list == null) return null;
        return list.iterator();
    }

    public final static int countEvents(final EClass eventName, final long time) {
        final Iterator<Event> event = getHistory(eventName);
        if (event == null) return 0;
        final long now = System.currentTimeMillis();
        int count = 0;
        while (event.hasNext()) {
            if (now - event.next().time < time) count++;
        }
        return count;
    }

    public final static class Event {
        public Object payload;
        public long time;

        public Event(final Object payload) {
            this.payload = payload;
            this.time = System.currentTimeMillis();
        }
    }

}
