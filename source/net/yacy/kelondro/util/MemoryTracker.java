// MemoryTracker.java
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

package net.yacy.kelondro.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



public class MemoryTracker extends Thread {
    
    private static final Map<String, ArrayList<Event>> historyMaps = new ConcurrentHashMap<String, ArrayList<Event>>();
    private static final Map<String, Long> eventAccess = new ConcurrentHashMap<String, Long>(); // value: last time when this was accessed
    private static MemoryTracker systemProfiler = null;
    
    public static void startSystemProfiling() {
    	systemProfiler = new MemoryTracker(1500);
    	systemProfiler.start();
    }
    
    public static void stopSystemProfiling() {
    	systemProfiler.running = false;
    }

    private final long delaytime;
    private boolean running;
    
    public MemoryTracker(final long time) {
    	this.delaytime = time;
    	running = true;
    }
    
    public void run() {
        try {
        	while (running) {
        		update("memory", Long.valueOf(MemoryControl.used()), true);
        		try {
    				Thread.sleep(this.delaytime);
    			} catch (final InterruptedException e) {
    				this.running = false;
    			}
        	}
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void update(final String eventName, final Object eventPayload, boolean useProtection) {
    	// get event history container
        Long lastAcc = eventAccess.get(eventName);
        if (lastAcc == null) {
            eventAccess.put(eventName, Long.valueOf(System.currentTimeMillis()));
        } else {
            long time = System.currentTimeMillis();
            if (!useProtection || time - lastAcc.longValue() > 1000) {
                eventAccess.put(eventName, Long.valueOf(time));
            } else {
                return; // protect against too heavy load
            }
        }
        ArrayList<Event> history = historyMaps.get(eventName);
    	if (history != null) synchronized (history) {

            // update entry
            history.add(new Event(eventPayload));
            
            // clean up too old entries
            int tp = history.size() - 30000;
            while (tp-- > 0) history.remove(0);
            if (history.size() % 10 == 0) { // reduce number of System.currentTimeMillis() calls
                Event e;
                final long now = System.currentTimeMillis();
                while (history.size() > 0) {
                    e = history.get(0);
                    if (now - e.time < 600000) break;
                    history.remove(0);
                }
            }
    	} else {
    	    history = new ArrayList<Event>(100);

            // update entry
            history.add(new Event(eventPayload));
            
            // store map
            historyMaps.put(eventName, history);
    	}
    }
    
    public static ArrayList<Event> history(final String eventName) {
        return historyMaps.get(eventName);
    }

    public static int countEvents(final String eventName, long time) {
        ArrayList<Event> event = history(eventName);
        if (event == null) return 0;
        long now = System.currentTimeMillis();
        int count = 0;
        synchronized (event) {
            Iterator<Event> i = event.iterator();
            while (i.hasNext()) {
                if (now - i.next().time < time) count++;
            }
        }
        return count;
    }
    
    public static class Event {
        public Object payload;
        public long time;

        public Event(final Object payload) {
            this.payload = payload;
            this.time = System.currentTimeMillis();
        }
    }

}
