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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.kelondro.logging.Log;

public class serverAccessTracker {

    private static final long cleanupCycle = 60000; // 1 minute
    
    private final long  maxTrackingTime;
    private final int   maxTrackingCount;
    private final int   maxHostCount;
    private final ConcurrentHashMap<String, List<Track>> accessTracker; // mappings from requesting host to an ArrayList of serverTrack-entries
    private long lastCleanup;
    
    public static class Track {
        private long time;
        private String path;
        public Track(long time, String path) {
            this.time = time;
            this.path = path;
        }
        public long getTime() {
            return this.time;
        }
        public String getPath() {
            return this.path;
        }
    }
    
    public serverAccessTracker(long maxTrackingTime, int maxTrackingCount, int maxTrackingHostCount) {
        this.maxTrackingTime = maxTrackingTime;
        this.maxTrackingCount = maxTrackingCount;
        this.maxHostCount = maxTrackingHostCount;
        this.accessTracker = new ConcurrentHashMap<String, List<Track>>();
    }
    
    /*
     * remove all entries from the access tracker where the age of the last access is greater than the given timeout
     */
    private synchronized void cleanupAccessTracker() {

        if (System.currentTimeMillis() - this.lastCleanup < cleanupCycle) return;
        
        // clear entries which had no entry for the maxTrackingTime time
        final Iterator<Map.Entry<String, List<Track>>> i = accessTracker.entrySet().iterator();
        List<Track> track;
        while (i.hasNext()) {
            track = i.next().getValue();
            if (tailList(track, Long.valueOf(System.currentTimeMillis() - maxTrackingTime)).isEmpty()) {
                // all entries are too old. delete the whole track
                i.remove();
            } else {
                // check if the maxTrackingCount is exceeded
                while (track.size() > this.maxTrackingCount) {
                    // delete the oldest entries
                    track.remove(0);
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
    
    public static List<Track> tailList(List<Track> timeList, long time) {
        List<Track> t = new LinkedList<Track>();
        for (Track l: timeList) if (l.getTime() > time) t.add(l);
        return t;
    }

    private List<Track> clearTooOldAccess(final List<Track> access) {
        try {
            return tailList(access, Long.valueOf(System.currentTimeMillis() - maxTrackingTime));
        } catch (IllegalArgumentException e) {
            Log.logException(e);
            return new LinkedList<Track>();
        }
    }
    
    public void track(final String host, String accessPath) {
        // check storage size
        if (System.currentTimeMillis() - this.lastCleanup > cleanupCycle) synchronized (this) {
            if (System.currentTimeMillis() - this.lastCleanup > cleanupCycle) {
                cleanupAccessTracker();
                this.lastCleanup = System.currentTimeMillis();
            }
        }
        
        // learn that a specific host has accessed a specific path
        if (accessPath == null) accessPath="NULL";
        List<Track> track = accessTracker.get(host);
        if (track == null) track = new LinkedList<Track>();
        
        synchronized (track) {
            track.add(new Track(System.currentTimeMillis(), accessPath));
            // write back to tracker
            accessTracker.put(host, clearTooOldAccess(track));
        }
    }
    
    public List<Track> accessTrack(final String host) {
        // returns mapping from Long(accesstime) to path
        
        List<Track> access = accessTracker.get(host);
        if (access == null) return null;
        // clear too old entries
        synchronized (access) {
            if ((access = clearTooOldAccess(access)).size() != access.size()) {
                // write back to tracker
                if (access.isEmpty()) {
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
        final HashMap<String, List<Track>> accessTrackerClone = new HashMap<String, List<Track>>();
        accessTrackerClone.putAll(accessTracker);
        return accessTrackerClone.keySet().iterator();
    }
}
