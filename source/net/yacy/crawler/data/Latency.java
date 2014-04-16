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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.crawler.robots.RobotsTxtEntry;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;


public class Latency {

    // the map is a mapping from host names to host configurations
    private static final int mapMaxSize = 1000;
    private static final ConcurrentHashMap<String, Host> map = new ConcurrentHashMap<String, Host>();

    /**
     * update the latency entry after a host was selected for queueing into the loader
     * @param url
     * @param robotsCrawlDelay the crawl-delay given by the robots; 0 if not exist
     */
    public static void updateAfterSelection(final DigestURL url, final long robotsCrawlDelay) {
        final String host = url.getHost();
        if (host == null) return;
        String hosthash = url.hosthash();
        Host h = map.get(hosthash);
        if (h == null) {
            h = new Host(host, Switchboard.getSwitchboard().getConfigInt("crawler.defaultAverageLatency", 500), robotsCrawlDelay);
            if (map.size() > mapMaxSize || MemoryControl.shortStatus()) map.clear();
            map.put(hosthash, h);
        }
    }

    /**
     * update the latency entry before a host is accessed
     * @param url
     * @param time the time to load the file in milliseconds
     */
    public static void updateBeforeLoad(final DigestURL url) {
        final String host = url.getHost();
        if (host == null) return;
        String hosthash = url.hosthash();
        Host h = map.get(hosthash);
        if (h == null) {
            h = new Host(host, 500, 0);
            if (map.size() > mapMaxSize || MemoryControl.shortStatus()) map.clear();
            map.put(hosthash, h);
        } else {
            h.update();
        }
    }

    /**
     * update the latency entry after a host was accessed to load a file
     * @param url
     * @param time the time to load the file in milliseconds
     */
    public static void updateAfterLoad(final DigestURL url, final long time) {
        final String host = url.getHost();
        if (host == null) return;
        String hosthash = url.hosthash();
        Host h = map.get(hosthash);
        if (h == null) {
            h = new Host(host, time, 0);
            if (map.size() > mapMaxSize || MemoryControl.shortStatus()) map.clear();
            map.put(hosthash, h);
        } else {
            h.update(time);
        }
    }

    private static Host host(final DigestURL url) {
        final String host = url.getHost();
        if (host == null) return null;
        return map.get(url.hosthash());
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
    public static int waitingRobots(final MultiProtocolURL url, final RobotsTxt robots, final ClientIdentification.Agent agent) {
        int robotsDelay = 0;
        RobotsTxtEntry robotsEntry = robots.getEntry(url, agent);
        robotsDelay = (robotsEntry == null) ? 0 : robotsEntry.getCrawlDelayMillis();
        if (robotsEntry != null && robotsDelay == 0 && robotsEntry.getAgentName() != null) return -1; // no limits if granted exclusively for this peer
        return robotsDelay;
    }
    
    private static int waitingRobots(final String hostport, final RobotsTxt robots, final ClientIdentification.Agent agent, final boolean fetchOnlineIfNotAvailableOrNotFresh) {
        int robotsDelay = 0;
        RobotsTxtEntry robotsEntry = robots.getEntry(hostport, agent, fetchOnlineIfNotAvailableOrNotFresh);
        robotsDelay = (robotsEntry == null) ? 0 : robotsEntry.getCrawlDelayMillis();
        if (robotsEntry != null && robotsDelay == 0 && robotsEntry.getAgentName() != null) return -1; // no limits if granted exclusively for this peer
        return robotsDelay;
    }

    /**
     * guess a minimum waiting time
     * the time is not correct, because if the domain was not checked yet by the robots.txt delay value, it is too low
     * @param hostname
     * @param hosthash
     * @param robots
     * @param agent
     * @return the remaining waiting time in milliseconds. The return value may be negative
     *         which expresses how long the time is over the minimum waiting time.
     */
    public static int waitingRemainingGuessed(final String hostname, final String hosthash, final RobotsTxt robots, final ClientIdentification.Agent agent) {

        // first check if the domain was _ever_ accessed before
        final Host host = map.get(hosthash);
        if (host == null) return Integer.MIN_VALUE; // no delay if host is new; use Integer because there is a cast to int somewhere

        // find the minimum waiting time based on the network domain (local or global)
        int waiting = agent.minimumDelta;

        // if we have accessed the domain many times, get slower (the flux factor)
        waiting += host.flux(waiting);

        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        waiting = Math.max(waiting, (int) (host.average() * Switchboard.getSwitchboard().getConfigFloat(SwitchboardConstants.CRAWLER_LATENCY_FACTOR, 0.5f)));

        // if the number of same hosts as in the url in the loading queue is greater than MaxSameHostInQueue, then increase waiting
        if (Switchboard.getSwitchboard().crawlQueues.hostcount(hostname) > Switchboard.getSwitchboard().getConfigInt(SwitchboardConstants.CRAWLER_MAX_SAME_HOST_IN_QUEUE, 20)) waiting += 3000;
        
        // the time since last access to the domain is the basis of the remaining calculation
        final int timeSinceLastAccess = (int) (System.currentTimeMillis() - host.lastacc());
        
        // find the delay as given by robots.txt on target site
        if (robots != null) {
            int robotsDelay = waitingRobots(hostname + ":80", robots, agent, false);
            if (robotsDelay < 0) return -timeSinceLastAccess; // no limits if granted exclusively for this peer
            waiting = Math.max(waiting, robotsDelay);
        }

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
     * @param agent
     * @return the remaining waiting time in milliseconds. can be negative to reflect the due-time after a possible nex loading time
     */
    public static int waitingRemaining(final DigestURL url, final RobotsTxt robots, final ClientIdentification.Agent agent) {

        // first check if the domain was _ever_ accessed before
        final Host host = host(url);
        if (host == null) return Integer.MIN_VALUE; // no delay if host is new; use Integer because there is a cast to int somewhere

        // find the minimum waiting time based on the network domain (local or global)
        boolean local = url.isLocal();
        int waiting = agent.minimumDelta;

        // if we have accessed the domain many times, get slower (the flux factor)
        if (!local) waiting += host.flux(waiting);

        // use the access latency as rule how fast we can access the server
        waiting = Math.max(waiting, (int) (host.average() * Switchboard.getSwitchboard().getConfigFloat(SwitchboardConstants.CRAWLER_LATENCY_FACTOR, 0.5f)));
        
        // if the number of same hosts as in the url in the loading queue is greater than MaxSameHostInQueue, then increase waiting
        if (Switchboard.getSwitchboard().crawlQueues.hostcount(url.getHost()) > Switchboard.getSwitchboard().getConfigInt(SwitchboardConstants.CRAWLER_MAX_SAME_HOST_IN_QUEUE, 20)) waiting += 3000;

        // the time since last access to the domain is the basis of the remaining calculation
        final int timeSinceLastAccess = (int) (System.currentTimeMillis() - host.lastacc());
        
        // find the delay as given by robots.txt on target site
        int robotsDelay = waitingRobots(url, robots, agent);
        if (robotsDelay < 0) return -timeSinceLastAccess; // no limits if granted exclusively for this peer

        waiting = Math.max(waiting, robotsDelay);
        return Math.min(60000, waiting) - timeSinceLastAccess;
    }
    
    public static String waitingRemainingExplain(final DigestURL url, final RobotsTxt robots, final ClientIdentification.Agent agent) {

        // first check if the domain was _ever_ accessed before
        final Host host = host(url);
        if (host == null) return "host " + host + " never accessed before -> Integer.MIN_VALUE"; // no delay if host is new

        // find the minimum waiting time based on the network domain (local or global)
        boolean local = url.isLocal();
        final StringBuilder s = new StringBuilder(50);

        // find the minimum waiting time based on the network domain (local or global)
        int waiting = agent.minimumDelta;
        s.append("minimumDelta = ").append(waiting);

        // if we have accessed the domain many times, get slower (the flux factor)
        if (!local) {
            int flux = host.flux(waiting);
            waiting += flux;
            s.append(", flux = ").append(flux);
        }
        
        // use the access latency as rule how fast we can access the server
        // this applies also to localhost, but differently, because it is not necessary to
        // consider so many external accesses
        s.append(", host.average = ").append(host.average());
        waiting = Math.max(waiting, (int) (host.average() * Switchboard.getSwitchboard().getConfigFloat(SwitchboardConstants.CRAWLER_LATENCY_FACTOR, 0.5f)));
        
        // if the number of same hosts as in the url in the loading queue is greater than MaxSameHostInQueue, then increase waiting
        int hostcount = Switchboard.getSwitchboard().crawlQueues.hostcount(url.getHost());
        if (hostcount > Switchboard.getSwitchboard().getConfigInt(SwitchboardConstants.CRAWLER_MAX_SAME_HOST_IN_QUEUE, 20)) {
            s.append(", hostcount = ").append(hostcount);
            waiting += 5000;
        }

        // find the delay as given by robots.txt on target site
        int robotsDelay = waitingRobots(url, robots, agent);
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

    /**
     * Get the minimum sleep time for a given url. The result can also be negative to reflect the time since the last access
     * The time can be as low as Integer.MIN_VALUE to show that there should not be any limitation at all.
     * @param robots
     * @param profileEntry
     * @param crawlURL
     * @return the sleep time in milliseconds; may be negative for no sleep time
     */
    public static long getDomainSleepTime(final RobotsTxt robots, final CrawlProfile profileEntry, final DigestURL crawlURL) {
        if (profileEntry == null) return 0;
        long sleeptime = (
            profileEntry.cacheStrategy() == CacheStrategy.CACHEONLY ||
            (profileEntry.cacheStrategy() == CacheStrategy.IFEXIST && Cache.has(crawlURL.hash()))
            ) ? Integer.MIN_VALUE : waitingRemaining(crawlURL, robots, profileEntry.getAgent()); // this uses the robots.txt database and may cause a loading of robots.txt from the server
        return sleeptime;
    }
    
    /**
     * load a robots.txt to get the robots time.
     * ATTENTION: this method causes that a robots.txt is loaded from the web which may cause a longer delay in execution.
     * This shall therefore not be called in synchronized environments.
     * @param robots
     * @param profileEntry
     * @param crawlURL
     * @return
     */
    public static long getRobotsTime(final RobotsTxt robots, final DigestURL crawlURL, ClientIdentification.Agent agent) {
        long sleeptime = waitingRobots(crawlURL, robots, agent); // this uses the robots.txt database and may cause a loading of robots.txt from the server
        return sleeptime < 0 ? 0 : sleeptime;
    }
    
    public static final class Host {
        private AtomicLong timeacc;
        private AtomicLong lastacc;
        private AtomicInteger count;
        private final String host;
        private long robotsMinDelay;
        private Host(final String host, final long time, long robotsMinDelay) {
            this.host = host;
            this.timeacc = new AtomicLong(time);
            this.count = new AtomicInteger(1);
            this.lastacc = new AtomicLong(System.currentTimeMillis());
            this.robotsMinDelay = robotsMinDelay;
        }
        private void update(final long time) {
            if (this.count.get() > 100) {
                synchronized(this) {
                    // faster adoption to new values
                    this.timeacc.set(this.timeacc.get() / this.count.get());
                    this.count.set(1);
                }
            }
            this.lastacc.set(System.currentTimeMillis());
            this.timeacc.addAndGet(Math.min(30000, time));
            this.count.incrementAndGet();
        }
        private void update() {
            this.lastacc.set(System.currentTimeMillis());
        }
        public int count() {
            return this.count.get();
        }
        public int average() {
            return (int) (this.timeacc.get() / this.count.get());
        }
        public long lastacc() {
            return this.lastacc.get();
        }
        public String host() {
            return this.host;
        }
        public long robotsDelay() {
            return this.robotsMinDelay;
        }
        public int flux(final int range) {
            return this.count.get() >= 10000 ? range * Math.min(5000, this.count.get()) / 10000 : range / (10000 - this.count.get());
        }
    }

}
