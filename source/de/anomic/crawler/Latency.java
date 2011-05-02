// Latency.java
// ------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published 19.03.2009 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.crawler;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;
import net.yacy.kelondro.util.MemoryControl;

import de.anomic.search.Switchboard;

public class Latency {

    // the map is a mapping from host names to host configurations
    private static final ConcurrentHashMap<String, Host> map = new ConcurrentHashMap<String, Host>();
    
    public static void update(MultiProtocolURI url, long time) {
        String host = url.getHost();
        if (host == null) return;
        Host h = map.get(host);
        if (h == null) {
            h = new Host(host, time);
            if (map.size() > 1000 || MemoryControl.shortStatus()) map.clear();
            map.put(host, h);
        } else {
            h.update(time);
        }
    }
    
    public static void update(MultiProtocolURI url) {
        String host = url.getHost();
        if (host == null) return;
        Host h = map.get(host);
        if (h == null) {
            h = new Host(host, 3000);
            if (map.size() > 1000 || MemoryControl.shortStatus()) map.clear();
            map.put(host, h);
        } else {
            h.update();
        }
    }
    
    public static void slowdown(MultiProtocolURI url) {
        String host = url.getHost();
        if (host == null) return;
        Host h = map.get(host);
        if (h == null) {
            h = new Host(host, 3000);
            if (map.size() > 1000 || MemoryControl.shortStatus()) map.clear();
            map.put(host, h);
        } else {
            h.slowdown();
        }
    }
    
    public static Host host(MultiProtocolURI url) {
        String host = url.getHost();
        if (host == null) return null;
        return map.get(host);
    }
    
    public static int average(MultiProtocolURI url) {
        String host = url.getHost();
        if (host == null) return 0;
        Host h = map.get(host);
        if (h == null) return 0;
        return h.average();
    }
    
    public static Iterator<Map.Entry<String, Host>> iterator() {
        return map.entrySet().iterator();
    }

    
    /**
     * calculate the time since the last access of the domain as referenced by the url hash
     * @param urlhash
     * @return a time in milliseconds since last access of the domain or Long.MAX_VALUE if the domain was not accessed before
     */
    public static long lastAccessDelta(MultiProtocolURI url) {
        final Latency.Host host = Latency.host(url);
        if (host == null) return Long.MAX_VALUE; // never accessed
        return System.currentTimeMillis() - host.lastacc();
    }
    

    
    /**
     * guess a minimum waiting time
     * the time is not correct, because if the domain was not checked yet by the robots.txt delay value, it is too low
     * also the 'isCGI' property is missing, because the full text of the domain is unknown here
     * @param hostname
     * @param minimumLocalDelta
     * @param minimumGlobalDelta
     * @return the remaining waiting time in milliseconds. The return value may be negative
     *         which expresses how long the time is over the minimum waiting time.
     */
    public static long waitingRemainingGuessed(String hostname, final long minimumLocalDelta, final long minimumGlobalDelta) {
        if (hostname == null) return 0;
        Host host = map.get(hostname);
        if (host == null) return 0;
        
        // the time since last access to the domain is the basis of the remaining calculation
        final long timeSinceLastAccess = System.currentTimeMillis() - host.lastacc();
        
        // find the minimum waiting time based on the network domain (local or global)
        final boolean local = Domains.isLocal(hostname);
        long waiting = (local) ? minimumLocalDelta : minimumGlobalDelta;
        
        // if we have accessed the domain many times, get slower (the flux factor)
        if (!local) waiting += host.flux(waiting);
        
        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        waiting = Math.max(waiting, (local) ? host.average() / 2 : host.average() * 2);
        
        // prevent that that a robots file can stop our indexer completely
        waiting = Math.min(60000, waiting);
        
        // return time that is remaining
        //System.out.println("Latency: " + (waiting - timeSinceLastAccess));
        return waiting - timeSinceLastAccess;
    }
    
    /**
     * calculates how long should be waited until the domain can be accessed again
     * this follows from:
     * - given minimum access times
     * - the fact that an url is a CGI url or not
     * - the times that the domain was accessed (flux factor)
     * - the response latency of the domain
     * - and a given minimum access time as given in robots.txt
     * @param minimumLocalDelta
     * @param minimumGlobalDelta
     * @return the remaining waiting time in milliseconds
     */
    public static long waitingRemaining(MultiProtocolURI url, final Set<String> thisAgents, final long minimumLocalDelta, final long minimumGlobalDelta) {

        // first check if the domain was _ever_ accessed before
        Host host = host(url);
        if (host == null) return Long.MIN_VALUE; // no delay if host is new
        
        // find the minimum waiting time based on the network domain (local or global)
        final boolean local = url.isLocal();
        if (local) return minimumLocalDelta;
        long waiting = (local) ? minimumLocalDelta : minimumGlobalDelta;
        
        // the time since last access to the domain is the basis of the remaining calculation
        final long timeSinceLastAccess = System.currentTimeMillis() - host.lastacc();
        
        // for CGI accesses, we double the minimum time
        // mostly there is a database access in the background
        // which creates a lot of unwanted IO on target site
        if (url.isCGI()) waiting = waiting * 2;

        // if we have accessed the domain many times, get slower (the flux factor)
        if (!local && host != null) waiting += host.flux(waiting);
        
        // find the delay as given by robots.txt on target site
        long robotsDelay = 0;
        if (!local) {
            RobotsTxtEntry robotsEntry;
            try {
                robotsEntry = Switchboard.getSwitchboard().robots.getEntry(url, thisAgents);
            } catch (IOException e) {
                robotsEntry = null;
            }
            robotsDelay = (robotsEntry == null) ? 0 : robotsEntry.getCrawlDelayMillis();
            if (robotsEntry != null && robotsDelay == 0 && robotsEntry.getAgentName() != null) return 0; // no limits if granted exclusively for this peer
        }
        waiting = Math.max(waiting, robotsDelay);
        
        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        waiting = Math.max(waiting, (local) ? host.average() / 2 : host.average() * 2);
        
        // prevent that that a robots file can stop our indexer completely
        waiting = Math.min(60000, waiting);
        
        // return time that is remaining
        //System.out.println("Latency: " + (waiting - timeSinceLastAccess));
        return Math.max(0, waiting - timeSinceLastAccess);
    }
    
    
    public static String waitingRemainingExplain(MultiProtocolURI url, final Set<String> thisAgents, final long minimumLocalDelta, final long minimumGlobalDelta) {
        
        // first check if the domain was _ever_ accessed before
        Host host = host(url);
        if (host == null) return "host " + host + " never accessed before -> 0"; // no delay if host is new
        
        StringBuilder s = new StringBuilder(50);
        
        // find the minimum waiting time based on the network domain (local or global)
        final boolean local = url.isLocal();
        long waiting = (local) ? minimumLocalDelta : minimumGlobalDelta;
        s.append("minimumDelta = ").append(waiting);
        
        // the time since last access to the domain is the basis of the remaining calculation
        final long timeSinceLastAccess = (host == null) ? 0 : System.currentTimeMillis() - host.lastacc();
        s.append(", timeSinceLastAccess = ").append(timeSinceLastAccess);
        
        // for CGI accesses, we double the minimum time
        // mostly there is a database access in the background
        // which creates a lot of unwanted IO on target site
        if (url.isCGI()) s.append(", isCGI = true -> double");

        // if we have accessed the domain many times, get slower (the flux factor)
        if (!local && host != null) s.append(", flux = ").append(host.flux(waiting));
        
        // find the delay as given by robots.txt on target site
        long robotsDelay = 0;
        if (!local) {
            RobotsTxtEntry robotsEntry;
            try {
                robotsEntry = Switchboard.getSwitchboard().robots.getEntry(url, thisAgents);
            } catch (IOException e) {
                robotsEntry = null;
            }
            robotsDelay = (robotsEntry == null) ? 0 : robotsEntry.getCrawlDelayMillis();
            if (robotsEntry != null && robotsDelay == 0 && robotsEntry.getAgentName() != null)  return "no waiting for exclusive granted peer"; // no limits if granted exclusively for this peer
        }
        s.append(", robots.delay = ").append(robotsDelay);
        
        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        if (host != null) s.append(", host.average = ").append(host.average());
        
        return s.toString();
    }
    
    public static final class Host {
        private long timeacc;
        private long lastacc;
        private int count;
        private final String host;
        private long robotsMinDelay;
        public Host(String host, long time) {
            this.host = host;
            this.timeacc = time;
            this.count = 1;
            this.lastacc = System.currentTimeMillis();
            this.robotsMinDelay = 0;
        }
        public void update(long time) {
            this.lastacc = System.currentTimeMillis();
            this.timeacc += Math.min(30000, time);
            this.count++;
        }
        public void update() {
            this.lastacc = System.currentTimeMillis();
        }
        public void slowdown() {
            this.lastacc = System.currentTimeMillis();
            this.timeacc = Math.min(60000, average() * 2);
            this.count = 1;
        }
        public int count() {
            return this.count;
        }
        public int average() {
            return (int) (this.timeacc / this.count);
        }
        public long lastacc() {
            return this.lastacc;
        }
        public String host() {
            return this.host;
        }
        public void robotsDelay(long ur) {
            this.robotsMinDelay = ur;
        }
        public long robotsDelay() {
            return this.robotsMinDelay;
        }
        public long flux(long range) {
            return count >= 1000 ? range * Math.min(5000, count) / 1000 : range / (1000 - count);
        }
    }
    
}
