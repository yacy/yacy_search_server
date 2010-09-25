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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class serverAccessTracker {

    private static final long cleanupCycle = 60000; // 1 minute
    
    private final long  maxTrackingTime;
    private final int   maxTrackingCount;
    private final int   maxHostCount;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Track>> accessTracker; // mappings from requesting host to an ArrayList of serverTrack-entries
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
        this.accessTracker = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Track>>();
    }
    
    /*
     * remove all entries from the access tracker where the age of the last access is greater than the given timeout
     */
    private synchronized void cleanupAccessTracker() {

        if (System.currentTimeMillis() - this.lastCleanup < cleanupCycle) return; // avoid too many scans of the queues
        this.lastCleanup = System.currentTimeMillis();
        
        // clear entries which had no entry for the maxTrackingTime time
        final Iterator<Map.Entry<String, ConcurrentLinkedQueue<Track>>> i = accessTracker.entrySet().iterator();
        ConcurrentLinkedQueue<Track> track;
        while (i.hasNext()) {
            track = i.next().getValue();
            clearTooOldAccess(track);
            if (track.isEmpty()) {
                // all entries are too old. delete the whole track
                i.remove();
            } else {
                // check if the maxTrackingCount is exceeded
                while (track.size() > this.maxTrackingCount) try {
                    // delete the oldest entries
                    track.remove();
                } catch (NoSuchElementException e) { break; } // concurrency may cause that the track is already empty
            }
        }
        
        // if there are more entries left than maxTrackingCount, delete some.
        while (accessTracker.size() > this.maxHostCount) {
            // delete just any
            String key = accessTracker.keys().nextElement();
            if (key == null) break; // may occur because of concurrency effects
            accessTracker.remove(key);
        }
    }

    /**
     * compute the number of accesses to a given host in the latest time
     * @param host the host that was accessed
     * @param delta the time delta from now to the past where the access times shall be computed
     * @return the number of accesses to the host in the given time span
     */
    public int latestAccessCount(final String host, long delta) {
        Collection<Track> timeList = accessTrack(host);
        if (timeList == null) return 0;
        long time = System.currentTimeMillis() - delta;
        int c = 0;
        for (Track l: timeList) if ( l != null && l.getTime() > time) c++;
        return c;
    }
    
    private void clearTooOldAccess(final ConcurrentLinkedQueue<Track> access) {
        Long time = Long.valueOf(System.currentTimeMillis() - maxTrackingTime);
        Iterator<Track> e = access.iterator();
        Track l;
        while (e.hasNext()) {
            l = e.next();
            if (l.getTime() <= time) e.remove();
        }
    }
    
    public void track(final String host, String accessPath) {
        // check storage size
        if (System.currentTimeMillis() - this.lastCleanup > cleanupCycle) {
            cleanupAccessTracker();
        }
        
        // learn that a specific host has accessed a specific path
        if (accessPath == null) accessPath="NULL";
        ConcurrentLinkedQueue<Track> track = accessTracker.get(host);
        if (track == null) {
            track = new ConcurrentLinkedQueue<Track>();
            track.add(new Track(System.currentTimeMillis(), accessPath));
            // add to tracker
            accessTracker.put(host, track);
        } else {
            track.add(new Track(System.currentTimeMillis(), accessPath));
            clearTooOldAccess(track);
        }
    }
    
    public Collection<Track> accessTrack(final String host) {
        // returns mapping from Long(accesstime) to path
        
        ConcurrentLinkedQueue<Track> access = accessTracker.get(host);
        if (access == null) return null;
        // clear too old entries
        clearTooOldAccess(access);
        if (access.isEmpty()) {
            accessTracker.remove(host);
        }
        return access;
    }
    
    public Iterator<String> accessHosts() {
        // returns an iterator of hosts in tracker (String)
        final Map<String, ConcurrentLinkedQueue<Track>> accessTrackerClone = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Track>>();
        accessTrackerClone.putAll(accessTracker);
        return accessTrackerClone.keySet().iterator();
    }
}
