/**
 *  HostQueues
 *  Copyright 2013 by Michael Christen
 *  First released 24.09.2013 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.crawler;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.storage.HandleMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.Latency;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleMap;
import net.yacy.kelondro.index.RowHandleSet;

/**
 * wrapper for single HostQueue queues; this is a collection of such queues.
 * All these queues are stored in a common directory for the queue stacks.
 * 
 * ATTENTION: the order of urls returned by this balancer must strictly follow the clickdepth order.
 * That means that all links from a given host must be returned from the lowest crawldepth only.
 * The crawldepth is interpreted as clickdepth and the crawler is producing that semantic using a
 * correct crawl ordering.
 */
public class HostBalancer implements Balancer {

    private final static ConcurrentLog log = new ConcurrentLog("HostBalancer");
    public final static HandleMap depthCache = new RowHandleMap(Word.commonHashLength, Word.commonHashOrder, 2, 8 * 1024 * 1024, "HostBalancer.DepthCache");
    
    private final File hostsPath;
    private final boolean exceed134217727;
    private final Map<String, HostQueue> queues;
    private final Set<String> roundRobinHostHashes;

    public HostBalancer(
            final File hostsPath,
            final boolean exceed134217727) {
        this.hostsPath = hostsPath;
        this.exceed134217727 = exceed134217727;
        
        // create a stack for newly entered entries
        if (!(hostsPath.exists())) hostsPath.mkdirs(); // make the path
        this.queues = new ConcurrentHashMap<String, HostQueue>();
        String[] list = this.hostsPath.list();
        for (String address: list) try {
            File queuePath = new File(this.hostsPath, address);
            HostQueue queue = new HostQueue(queuePath, this.queues.size() > 100, this.exceed134217727);
            if (queue.size() == 0) {
                queue.close();
                queuePath.delete();
            } else {
                this.queues.put(DigestURL.hosthash(queue.getHost(), queue.getPort()), queue);
            }
        } catch (MalformedURLException e) {
            ConcurrentLog.logException(e);
        }
        this.roundRobinHostHashes = new HashSet<String>();
    }

    @Override
    public synchronized void close() {
        if (depthCache != null) {
            depthCache.clear();
        }
        for (HostQueue queue: this.queues.values()) queue.close();
        this.queues.clear();
    }

    @Override
    public void clear() {
        if (depthCache != null) {
            depthCache.clear();
        }
        for (HostQueue queue: this.queues.values()) queue.clear();
        this.queues.clear();
    }

    @Override
    public Request get(final byte[] urlhash) throws IOException {
        String hosthash = ASCII.String(urlhash, 6, 6);
        HostQueue queue = this.queues.get(hosthash);
        if (queue == null) return null;
        return queue.get(urlhash);
    }

    @Override
    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, SpaceExceededException {
        int c = 0;
        for (HostQueue queue: this.queues.values()) c += queue.removeAllByProfileHandle(profileHandle, timeout);
        return c;
    }
    
    /**
     * delete all urls which are stored for given host hashes
     * @param hosthashes
     * @return number of deleted urls
     */
    @Override
    public int removeAllByHostHashes(final Set<String> hosthashes) {
        int c = 0;
        for (String h: hosthashes) {
            HostQueue hq = this.queues.get(h);
            if (hq != null) c += hq.removeAllByHostHashes(hosthashes);
        }
        // remove from cache
        Iterator<Map.Entry<byte[], Long>> i = depthCache.iterator();
        ArrayList<String> deleteHashes = new ArrayList<String>();
        while (i.hasNext()) {
            String h = ASCII.String(i.next().getKey());
            if (hosthashes.contains(h.substring(6))) deleteHashes.add(h);
        }
        for (String h: deleteHashes) depthCache.remove(ASCII.getBytes(h));
        return c;
    }

    @Override
    public synchronized int remove(final HandleSet urlHashes) throws IOException {
        Map<String, HandleSet> removeLists = new ConcurrentHashMap<String, HandleSet>();
        for (byte[] urlhash: urlHashes) {
            depthCache.remove(urlhash);
            String hosthash = ASCII.String(urlhash, 6, 6);
            HandleSet removeList = removeLists.get(hosthash);
            if (removeList == null) {
                removeList = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 100);
                removeLists.put(hosthash, removeList);
            }
            try {removeList.put(urlhash);} catch (SpaceExceededException e) {}
        }
        int c = 0;
        for (Map.Entry<String, HandleSet> entry: removeLists.entrySet()) {
            HostQueue queue = this.queues.get(entry.getKey());
            if (queue != null) c += queue.remove(entry.getValue());
        }
        return c;
    }

    @Override
    public boolean has(final byte[] urlhashb) {
        if (depthCache.has(urlhashb)) return true;
        String hosthash = ASCII.String(urlhashb, 6, 6);
        HostQueue queue = this.queues.get(hosthash);
        if (queue == null) return false;
        return queue.has(urlhashb);
    }

    @Override
    public int size() {
        int c = 0;
        for (HostQueue queue: this.queues.values()) c += queue.size();
        return c;
    }

    @Override
    public boolean isEmpty() {
        for (HostQueue queue: this.queues.values()) if (!queue.isEmpty()) return false;
        return true;
    }
    
    /**
     * push a request to one of the host queues. If the queue does not exist, it is created
     * @param entry
     * @param profile
     * @param robots
     * @return null if everything is ok or a string with an error message if the push is not allowed according to the crawl profile or robots
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public String push(final Request entry, CrawlProfile profile, final RobotsTxt robots) throws IOException, SpaceExceededException {
        if (this.has(entry.url().hash())) return "double occurrence";
        depthCache.put(entry.url().hash(), entry.depth());
        String hosthash = ASCII.String(entry.url().hash(), 6, 6);
        synchronized (this) {
            HostQueue queue = this.queues.get(hosthash);
            if (queue == null) {
                queue = new HostQueue(this.hostsPath, entry.url().getHost(), entry.url().getPort(), this.queues.size() > 100, this.exceed134217727);
                this.queues.put(hosthash, queue);
                // profile might be null when continue crawls after YaCy restart
                robots.ensureExist(entry.url(), profile == null ? ClientIdentification.yacyInternetCrawlerAgent : profile.getAgent(), true); // concurrently load all robots.txt
            }
            return queue.push(entry, profile, robots);
        }
    }

    /**
     * get the next entry in this crawl queue in such a way that the domain access time delta is maximized
     * and always above the given minimum delay time. An additional delay time is computed using the robots.txt
     * crawl-delay time which is always respected. In case the minimum time cannot ensured, this method pauses
     * the necessary time until the url is released and returned as CrawlEntry object. In case that a profile
     * for the computed Entry does not exist, null is returned
     * @param delay true if the requester demands forced delays using explicit thread sleep
     * @param profile
     * @return a url in a CrawlEntry object
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public Request pop(boolean delay, CrawlSwitchboard cs, RobotsTxt robots) throws IOException {
        tryagain: while (true) try {
            HostQueue rhq = null;
            String rhh = null;
        
            synchronized (this) {
                if (this.roundRobinHostHashes.size() == 0) {
                    // refresh the round-robin cache
                    this.roundRobinHostHashes.addAll(this.queues.keySet());
                    // quickly get rid of small stacks to reduce number of files:
                    // remove all stacks with more than 10 entries
                    // this shall kick out small stacks to prevent that too many files are opened for very wide crawls
                    boolean smallStacksExist = false;
                    boolean singletonStacksExist = false;
                    smallsearch: for (String s: this.roundRobinHostHashes) {
                        HostQueue hq = this.queues.get(s);
                        if (hq != null) {
                            int size = hq.size();
                            if (size ==  1) {singletonStacksExist = true; break smallsearch;}
                            if (size <= 10) {smallStacksExist = true; break smallsearch;}
                        }
                    }
                    if (singletonStacksExist || smallStacksExist) {
                        Iterator<String> i = this.roundRobinHostHashes.iterator();
                        smallstacks: while (i.hasNext()) {
                            if (this.roundRobinHostHashes.size() <= 10) break smallstacks; // don't shrink the hosts until nothing is left
                            String s = i.next();
                            HostQueue hq = this.queues.get(s);
                            if (hq == null) {i.remove(); continue smallstacks;}
                            int delta = Latency.waitingRemainingGuessed(hq.getHost(), s, robots, ClientIdentification.yacyInternetCrawlerAgent);
                            if (delta < 0) continue; // keep all non-waiting stacks; they are useful to speed up things
                            // to protect all small stacks which have a fast throughput, remove all with long waiting time
                            if (delta >= 1000) {i.remove(); continue smallstacks;}
                            int size = hq.size();
                            if (singletonStacksExist) {
                                if (size != 1) {i.remove(); continue smallstacks;} // remove all non-singletons
                            } else /*smallStacksExist*/ {
                                if (size > 10) {i.remove(); continue smallstacks;} // remove all large stacks
                            }
                        }
                    }
                    if (this.roundRobinHostHashes.size() == 1) {
                        if (log.isFine()) log.fine("(re-)initialized the round-robin queue with one host");
                    } else {
                        log.info("(re-)initialized the round-robin queue; " + this.roundRobinHostHashes.size() + " hosts.");
                    }
                }
                if (this.roundRobinHostHashes.size() == 0) return null;
                
                // first strategy: get one entry which does not need sleep time
                Iterator<String> nhhi = this.roundRobinHostHashes.iterator();
                nosleep: while (nhhi.hasNext()) {
                    rhh = nhhi.next();
                    rhq = this.queues.get(rhh);
                    if (rhq == null) {
                        nhhi.remove();
                        continue nosleep;
                    }
                    int delta = Latency.waitingRemainingGuessed(rhq.getHost(), rhh, robots, ClientIdentification.yacyInternetCrawlerAgent);
                    if (delta <= 10 || this.roundRobinHostHashes.size() == 1 || rhq.size() == 1) {
                        nhhi.remove();
                        break nosleep;
                    }
                }
                
                if (rhq == null) {
                    // second strategy: take from the largest stack
                    int largest = Integer.MIN_VALUE;
                    for (String h: this.roundRobinHostHashes) {
                        HostQueue hq = this.queues.get(h);
                        if (hq != null) {
                            int s = hq.size();
                            if (s > largest) {
                                largest = s;
                                rhh = h;
                            }
                        }
                    }
                    rhq = this.queues.get(rhh);
                }
            }
            
            if (rhq == null) continue tryagain;
            long timestamp = System.currentTimeMillis();
            Request request = rhq.pop(delay, cs, robots); // this pop is outside of synchronization to prevent blocking of pushes
            long actualwaiting = System.currentTimeMillis() - timestamp;
            
            if (actualwaiting > 1000) {
                synchronized (this) {
                    // to prevent that this occurs again, remove all stacks with positive delay times (which may be less after that waiting)
                    Iterator<String> i = this.roundRobinHostHashes.iterator();
                    protectcheck: while (i.hasNext()) {
                        if (this.roundRobinHostHashes.size() <= 3) break protectcheck; // don't shrink the hosts until nothing is left
                        String s = i.next();
                        HostQueue hq = this.queues.get(s);
                        if (hq == null) {i.remove(); continue protectcheck;}
                        int delta = Latency.waitingRemainingGuessed(hq.getHost(), s, robots, ClientIdentification.yacyInternetCrawlerAgent);
                        if (delta >= 0) {i.remove();}
                    }
                }
            }
            
            int size = rhq.size();
            if (size == 0) {
                synchronized (this) {
                    this.queues.remove(rhh);
                }
                rhq.close();
            }
            if (request == null) continue tryagain;
            return request;
        } catch (ConcurrentModificationException e) {
            continue tryagain;
        } catch (Throwable e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public Iterator<Request> iterator() throws IOException {
        final Iterator<HostQueue> hostsIterator = this.queues.values().iterator();
        @SuppressWarnings("unchecked")
        final Iterator<Request>[] hostIterator = new Iterator[1];
        hostIterator[0] = null;
        return new Iterator<Request>() {
            @Override
            public boolean hasNext() {
                return hostsIterator.hasNext() || (hostIterator[0] != null && hostIterator[0].hasNext());
            }
            @Override
            public Request next() {
                synchronized (HostBalancer.this) {
                    while (hostIterator[0] == null || !hostIterator[0].hasNext()) try {
                        HostQueue entry = hostsIterator.next();
                        hostIterator[0] = entry.iterator();
                    } catch (IOException e) {}
                    if (!hostIterator[0].hasNext()) return null;
                    return hostIterator[0].next();
                }
            }
            @Override
            public void remove() {
                hostIterator[0].remove();
            }
        };
    }

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names to an integer array: {the size of the domain stack, guessed delta waiting time}
     */
    @Override
    public Map<String, Integer[]> getDomainStackHosts(RobotsTxt robots) {
        Map<String, Integer[]> map = new TreeMap<String, Integer[]>(); // we use a tree map to get a stable ordering
        for (HostQueue hq: this.queues.values()) try {
            int delta = Latency.waitingRemainingGuessed(hq.getHost(), DigestURL.hosthash(hq.getHost(), hq.getPort()), robots, ClientIdentification.yacyInternetCrawlerAgent);
            map.put(hq.getHost() + ":" + hq.getPort(), new Integer[]{hq.size(), delta});
        } catch (MalformedURLException e) {
            ConcurrentLog.logException(e);
        }
        return map;
    }

    /**
     * get lists of crawl request entries for a specific host
     * @param host
     * @param maxcount
     * @param maxtime
     * @return a list of crawl loader requests
     */
    @Override
    public List<Request> getDomainStackReferences(String host, int maxcount, long maxtime) {
        try {
            HostQueue hq = this.queues.get(DigestURL.hosthash(host, host.startsWith("ftp.") ? 21 : 80));
            if (hq == null) hq = this.queues.get(DigestURL.hosthash(host, 443));
            return hq == null ? new ArrayList<Request>(0) : hq.getDomainStackReferences(host, maxcount, maxtime);
        } catch (MalformedURLException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

}
