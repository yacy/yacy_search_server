// serverAccessTracker.java
// -------------------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 20.02.2009
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class serverAccessTracker {

    private long cleanupCycle = 60000; // 1 minute
    
    private long  maxTrackingTime;
    private int   maxTrackingCount;
    private int   maxHostCount;
    private final ConcurrentHashMap<String, SortedMap<Long, String>> accessTracker; // mappings from requesting host to an ArrayList of serverTrack-entries
    private long lastCleanup;
    
    public serverAccessTracker(long maxTrackingTime, int maxTrackingCount, int maxTrackingHostCount) {
        this.maxTrackingTime = maxTrackingTime;
        this.maxTrackingCount = maxTrackingCount;
        this.maxHostCount = maxTrackingHostCount;
        this.accessTracker = new ConcurrentHashMap<String, SortedMap<Long, String>>();
    }
    
    /*
     * remove all entries from the access tracker where the age of the last access is greater than the given timeout
     */
    private synchronized void cleanupAccessTracker() {

        if (System.currentTimeMillis() - this.lastCleanup < cleanupCycle) return;
        
        // clear entries which had no entry for the maxTrackingTime time
        final Iterator<Map.Entry<String, SortedMap<Long, String>>> i = accessTracker.entrySet().iterator();
        SortedMap<Long, String> track;
        while (i.hasNext()) {
            track = i.next().getValue();
            if (track.tailMap(Long.valueOf(System.currentTimeMillis() - maxTrackingTime)).size() == 0) {
                // all entries are too old. delete the whole track
                i.remove();
            } else {
                // check if the maxTrackingCount is exceeded
                while (track.size() > this.maxTrackingCount) {
                    // delete the oldest entries
                    track.remove(track.firstKey());
                }
            }
        }
        
        // if there are more entries left than maxTrackingCount, delete some.
        while (accessTracker.size() > this.maxHostCount) {
            // delete just any
            accessTracker.remove(accessTracker.keys().nextElement());
        }

        this.lastCleanup = System.currentTimeMillis();
    }

    private SortedMap<Long, String> clearTooOldAccess(final SortedMap<Long, String> access) {
        return access.tailMap(Long.valueOf(System.currentTimeMillis() - maxTrackingTime));
    }
    
    public void track(final String host, String accessPath) {
        // check storage size
        if (System.currentTimeMillis() - this.lastCleanup > cleanupCycle) {
            cleanupAccessTracker();
            this.lastCleanup = System.currentTimeMillis();
        }
        
        // learn that a specific host has accessed a specific path
        if (accessPath == null) accessPath="NULL";
        SortedMap<Long, String> track = accessTracker.get(host);
        if (track == null) track = new TreeMap<Long, String>();
        
        synchronized (track) {
            track.put(Long.valueOf(System.currentTimeMillis()), accessPath);
            // write back to tracker
            accessTracker.put(host, clearTooOldAccess(track));
        }
    }
    
    public SortedMap<Long, String> accessTrack(final String host) {
        // returns mapping from Long(accesstime) to path
        
        SortedMap<Long, String> access = accessTracker.get(host);
        if (access == null) return null;
        // clear too old entries
        synchronized (access) {
            if ((access = clearTooOldAccess(access)).size() != access.size()) {
                // write back to tracker
                if (access.size() == 0) {
                    accessTracker.remove(host);
                } else {
                    accessTracker.put(host, access);
                }
            }
        }
        return access;
    }
    
    public Iterator<String> accessHosts() {
        // returns an iterator of hosts in tracker (String)
        final HashMap<String, SortedMap<Long, String>> accessTrackerClone = new HashMap<String, SortedMap<Long, String>>();
        accessTrackerClone.putAll(accessTracker);
        return accessTrackerClone.keySet().iterator();
    }
}
