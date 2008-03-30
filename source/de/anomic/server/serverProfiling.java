// serverProfiling.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.11.2007 on http://yacy.net
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

package de.anomic.server;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class serverProfiling extends Thread {
    
    private static Map<String, ConcurrentLinkedQueue<Event>> historyMaps; // key=name of history, value=TreeMap of Long/Event
    private static Map<String, Integer> eventCounter; // key=name of history, value=Integer of event counter
    private static serverProfiling systemProfiler;
    
    static {
        // initialize profiling
        historyMaps = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Event>>();
        eventCounter = new ConcurrentHashMap<String, Integer>();
        //lastCompleteCleanup = System.currentTimeMillis();
        systemProfiler = null;
    }
    
    public static void startSystemProfiling() {
    	systemProfiler = new serverProfiling(1000);
    	systemProfiler.start();
    }
    
    public static void stopSystemProfiling() {
    	systemProfiler.running = false;
    }

    private long delaytime;
    private boolean running;
    
    public serverProfiling(long time) {
    	this.delaytime = time;
    	running = true;
    }
    
    public void run() {
    	while (running) {
    		update("memory", new Long(serverMemory.used()));
    		try {
				Thread.sleep(this.delaytime);
			} catch (InterruptedException e) {
				this.running = false;
			}
    	}
    }
    
    public static void update(String eventName, Object eventPayload) {
    	// get event history container
    	int counter = eventCounter.containsKey(eventName) ? ((Integer) eventCounter.get(eventName)).intValue() : 0;
    	if (historyMaps.containsKey(eventName)) {
    	    ConcurrentLinkedQueue<Event> history = historyMaps.get(eventName);

            // update entry
            history.add(new Event(counter, eventPayload));
            counter++;
            eventCounter.put(eventName, new Integer(counter));
            
            // clean up too old entries
            Event e;
            long now = System.currentTimeMillis();
            while (history.size() > 0) {
                e = history.peek();
                if (now - e.time < 600000) break;
                history.poll();
            }
    	} else {
    	    ConcurrentLinkedQueue<Event> history = new ConcurrentLinkedQueue<Event>();

            // update entry
            history.add(new Event(counter, eventPayload));
            counter++;
            eventCounter.put(eventName, new Integer(counter));
            
            // store map
            historyMaps.put(eventName, history);
    	}
    }
    
    public static Iterator<Event> history(String eventName) {
    	return (historyMaps.containsKey(eventName) ? (historyMaps.get(eventName)) : new ConcurrentLinkedQueue<Event>()).iterator();
    }

    public static class Event {
        public int count;
        public Object payload;
        public long time;

        public Event(int count, Object payload) {
            this.count = count;
            this.payload = payload;
            this.time = System.currentTimeMillis();
        }
    }

}
