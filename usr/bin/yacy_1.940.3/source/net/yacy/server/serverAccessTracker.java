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

package net.yacy.server;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.protocol.Domains;

public class serverAccessTracker {

    private static final long cleanupCycle = 60000; // 1 minute
    private static long  maxTrackingTime  = 3600000;
    private static int   maxTrackingCount = 1000;
    private static int   maxHostCount     = 100;
    private static final ConcurrentHashMap<String, Queue<Track>> accessTracker = new ConcurrentHashMap<String, Queue<Track>>(); // mappings from requesting host to an ArrayList of serverTrack-entries
    private static long  lastCleanup;
    private static long  lastLocalhostAccess = 0;

    public static class Track {
        private final long time;
        private final String path;
        public Track(final long time, final String path) {
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

    public static void init(final long mtt, final int mtc, final int mthc) {
        maxTrackingTime = mtt;
        maxTrackingCount = mtc;
        maxHostCount = mthc;
    }

    /*
     * remove all entries from the access tracker where the age of the last access is greater than the given timeout
     */
    private static void cleanupAccessTracker() {

       if (System.currentTimeMillis() - lastCleanup < cleanupCycle) return; // avoid too many scans of the queues
       lastCleanup = System.currentTimeMillis();

        // clear entries which had no entry for the maxTrackingTime time
        final Iterator<Map.Entry<String, Queue<Track>>> i = accessTracker.entrySet().iterator();
        Queue<Track> track;
        while (i.hasNext()) {
            track = i.next().getValue();
            clearTooOldAccess(track);
            if (track.isEmpty()) {
                // all entries are too old. delete the whole track
                i.remove();
            } else {
                // check if the maxTrackingCount is exceeded
                while (track.size() > maxTrackingCount) try {
                    // delete the oldest entries
                    track.remove();
                } catch (final NoSuchElementException e) { break; } // concurrency may cause that the track is already empty
            }
        }

        // if there are more entries left than maxTrackingCount, delete some.
        while (accessTracker.size() > maxHostCount) {
            // delete just any
            final String key = accessTracker.keys().nextElement();
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
    public static int latestAccessCount(final String host, final long delta) {
        final Collection<Track> timeList = accessTrack(host);
        if (timeList == null) return 0;
        final long time = System.currentTimeMillis() - delta;
        int c = 0;
        for (final Track l: timeList) if ( l != null && l.getTime() > time) c++;
        return c;
    }

    private static void clearTooOldAccess(final Queue<Track> access) {
        final long time = System.currentTimeMillis() - maxTrackingTime;
        final Iterator<Track> e = access.iterator();
        Track l;
        int max = access.size(); // ensure termination
        while (e.hasNext() && max-- > 0) {
            l = e.next();
            if (l.getTime() <= time) e.remove();
        }
    }

    public static void track(final String host, String accessPath) {
        // check storage size
        if (System.currentTimeMillis() - lastCleanup > cleanupCycle) {
            cleanupAccessTracker();
        }

        // learn that a specific host has accessed a specific path
        if (accessPath == null) accessPath="NULL";
        Queue<Track> track = accessTracker.get(host);
        if (track == null) {
            track = new LinkedBlockingQueue<Track>();
            track.add(new Track(System.currentTimeMillis(), accessPath));
            // add to tracker
            accessTracker.put(host, track);
        } else {
            track.add(new Track(System.currentTimeMillis(), accessPath));
            clearTooOldAccess(track);
        }
        if (Domains.isLocalhost(host)) lastLocalhostAccess = System.currentTimeMillis();
    }

    public static Collection<Track> accessTrack(final String host) {
        // returns mapping from Long(accesstime) to path

        final Queue<Track> access = accessTracker.get(host);
        if (access == null) return null;
        // clear too old entries
        clearTooOldAccess(access);
        if (access.isEmpty()) {
            accessTracker.remove(host);
        }
        return access;
    }

    public static Iterator<String> accessHosts() {
        // returns an iterator of hosts in tracker (String)
        final Map<String, Queue<Track>> accessTrackerClone = new ConcurrentHashMap<String, Queue<Track>>();
        accessTrackerClone.putAll(accessTracker);
        return accessTrackerClone.keySet().iterator();
    }
    
    public static long timeSinceAccessFromLocalhost() {
        return System.currentTimeMillis() - lastLocalhostAccess;
    }
}
