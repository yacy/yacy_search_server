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

package net.yacy.crawler.data;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.Domains;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.crawler.robots.RobotsTxtEntry;
import net.yacy.kelondro.util.MemoryControl;


public class Latency {

    private final static int DEFAULT_AVERAGE = 300;

    // the map is a mapping from host names to host configurations
    private static final ConcurrentHashMap<String, Host> map = new ConcurrentHashMap<String, Host>();

    /**
     * update the latency entry after a host was accessed to load a file
     * @param url
     * @param time the time to load the file in milliseconds
     */
    public static void updateAfterLoad(final MultiProtocolURI url, final long time) {
        final String host = url.getHost();
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

    /**
     * update the latency entry after a host was selected for queueing into the loader
     * @param url
     * @param robotsCrawlDelay the crawl-delay given by the robots; 0 if not exist
     */
    public static void updateAfterSelection(final MultiProtocolURI url, final long robotsCrawlDelay) {
        final String host = url.getHost();
        if (host == null) return;
        Host h = map.get(host);
        if (h == null) {
            h = new Host(host, DEFAULT_AVERAGE, robotsCrawlDelay);
            if (map.size() > 1000 || MemoryControl.shortStatus()) map.clear();
            map.put(host, h);
        } else {
            h.update();
        }
    }

    private static Host host(final MultiProtocolURI url) {
        final String host = url.getHost();
        if (host == null) return null;
        return map.get(host);
    }

    public static Iterator<Map.Entry<String, Host>> iterator() {
        return map.entrySet().iterator();
    }

    /**
     * Return the waiting time demanded by the robots.txt file of the target host.
     * A special case is, if the remote host has a special crawl-delay assignment for
     * this crawler with 0. This causes that a -1 is returned
     * @param url
     * @param robots
     * @param thisAgents
     * @return the waiting time in milliseconds; 0 if not known; -1 if host gives us special rights
     */
    public static long waitingRobots(final MultiProtocolURI url, final RobotsTxt robots, final Set<String> thisAgents) {
        long robotsDelay = 0;
        RobotsTxtEntry robotsEntry = robots.getEntry(url, thisAgents);
        robotsDelay = (robotsEntry == null) ? 0 : robotsEntry.getCrawlDelayMillis();
        if (robotsEntry != null && robotsDelay == 0 && robotsEntry.getAgentName() != null) return -1; // no limits if granted exclusively for this peer
        return robotsDelay;
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
    public static long waitingRemainingGuessed(final String hostname, final long minimumLocalDelta, final long minimumGlobalDelta) {
        if (hostname == null) return Integer.MIN_VALUE;

        // first check if the domain was _ever_ accessed before
        final Host host = map.get(hostname);
        if (host == null) return Integer.MIN_VALUE; // no delay if host is new; use Integer because there is a cast to int somewhere

        // find the minimum waiting time based on the network domain (local or global)
        final boolean local = Domains.isLocal(hostname, null);
        if (local) return minimumLocalDelta;
        long waiting =  minimumGlobalDelta;

        // if we have accessed the domain many times, get slower (the flux factor)
        waiting += host.flux(waiting);

        // the time since last access to the domain is the basis of the remaining calculation
        final long timeSinceLastAccess = System.currentTimeMillis() - host.lastacc();
        
        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        waiting = Math.max(waiting, host.average() * 2);

        return Math.min(60000, waiting) - timeSinceLastAccess;
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
     * @return the remaining waiting time in milliseconds. can be negative to reflect the due-time after a possible nex loading time
     */
    public static long waitingRemaining(final MultiProtocolURI url, final RobotsTxt robots, final Set<String> thisAgents, final long minimumLocalDelta, final long minimumGlobalDelta) {

        // first check if the domain was _ever_ accessed before
        final Host host = host(url);
        if (host == null) return Integer.MIN_VALUE; // no delay if host is new; use Integer because there is a cast to int somewhere

        // find the minimum waiting time based on the network domain (local or global)
        final boolean local = url.isLocal();
        if (local) return minimumLocalDelta;
        long waiting = minimumGlobalDelta;

        // for CGI accesses, we double the minimum time
        // mostly there is a database access in the background
        // which creates a lot of unwanted IO on target site
        if (url.isCGI()) waiting = waiting * 2;

        // if we have accessed the domain many times, get slower (the flux factor)
        waiting += host.flux(waiting);

        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        waiting = Math.max(waiting, host.average() * 2);

        // the time since last access to the domain is the basis of the remaining calculation
        final long timeSinceLastAccess = System.currentTimeMillis() - host.lastacc();
        
        // find the delay as given by robots.txt on target site
        long robotsDelay = waitingRobots(url, robots, thisAgents);
        if (robotsDelay < 0) return -timeSinceLastAccess; // no limits if granted exclusively for this peer

        waiting = Math.max(waiting, robotsDelay);
        return Math.min(60000, waiting) - timeSinceLastAccess;
    }


    public static String waitingRemainingExplain(final MultiProtocolURI url, final RobotsTxt robots, final Set<String> thisAgents, final long minimumLocalDelta, final long minimumGlobalDelta) {

        // first check if the domain was _ever_ accessed before
        final Host host = host(url);
        if (host == null) return "host " + host + " never accessed before -> Long.MIN_VALUE"; // no delay if host is new

        final StringBuilder s = new StringBuilder(50);

        // find the minimum waiting time based on the network domain (local or global)
        final boolean local = url.isLocal();
        if (local) return "local host -> minimum local: " + minimumLocalDelta;
        long waiting = minimumGlobalDelta;
        s.append("minimumDelta = ").append(waiting);

        // for CGI accesses, we double the minimum time
        // mostly there is a database access in the background
        // which creates a lot of unwanted IO on target site
        if (url.isCGI()) { waiting = waiting * 2; s.append(", isCGI = true -> double"); }

        // if we have accessed the domain many times, get slower (the flux factor)
        long flux = host.flux(waiting);
        waiting += flux;
        s.append(", flux = ").append(flux);

        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        s.append(", host.average = ").append(host.average());
        waiting = Math.max(waiting, host.average() * 2);

        // find the delay as given by robots.txt on target site
        long robotsDelay = waitingRobots(url, robots, thisAgents);
        if (robotsDelay < 0) return "no waiting for exclusive granted peer"; // no limits if granted exclusively for this peer

        waiting = Math.max(waiting, robotsDelay);
        s.append(", robots.delay = ").append(robotsDelay);

        // the time since last access to the domain is the basis of the remaining calculation
        final long timeSinceLastAccess = System.currentTimeMillis() - host.lastacc();
        s.append(", ((waitig = ").append(waiting);
        s.append(") - (timeSinceLastAccess = ").append(timeSinceLastAccess).append(")) = ");
        s.append(waiting - timeSinceLastAccess);
        return s.toString();
    }

    public static final class Host {
        private long timeacc;
        private long lastacc;
        private int count;
        private final String host;
        private long robotsMinDelay;
        private Host(final String host, final long time) {
            this(host, time, 0);
        }
        private Host(final String host, final long time, long robotsMinDelay) {
            this.host = host;
            this.timeacc = time;
            this.count = 1;
            this.lastacc = System.currentTimeMillis();
            this.robotsMinDelay = robotsMinDelay;
        }
        private void update(final long time) {
            this.lastacc = System.currentTimeMillis();
            this.timeacc += Math.min(30000, time);
            this.count++;
        }
        private void update() {
            this.lastacc = System.currentTimeMillis();
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
        public long robotsDelay() {
            return this.robotsMinDelay;
        }
        public long flux(final long range) {
            return this.count >= 10000 ? range * Math.min(5000, this.count) / 10000 : range / (10000 - this.count);
        }
    }

}
