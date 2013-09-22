/**
 *  CrawlQueue
 *  Copyright 2013 by Michael Christen
 *  First released 30.08.2013 at http://yacy.net
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
import java.util.Iterator;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.Latency;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.index.BufferedObjectIndex;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;

public class CrawlQueue {

    private static final int    EcoFSBufferSize       = 1000;
    private static final int    objectIndexBufferSize = 1000;
    private static final int    MAX_DOUBLE_PUSH_CHECK = 100000;
    
    private BufferedObjectIndex  urlFileIndex;
    private final HandleSet      double_push_check;

    public CrawlQueue(
            final File cachePath,
            final String filename,
            final boolean useTailCache,
            final boolean exceed134217727) {

        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        cachePath.mkdirs();
        final File f = new File(cachePath, filename);
        try {
            this.urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727, true), objectIndexBufferSize);
        } catch (final SpaceExceededException e) {
            try {
                this.urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, 0, 0, false, exceed134217727, true), objectIndexBufferSize);
            } catch (final SpaceExceededException e1) {
                ConcurrentLog.logException(e1);
            }
        }
        this.double_push_check = new RowHandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
        ConcurrentLog.info("CrawlQueue", "opened queue file with " + this.urlFileIndex.size() + " entries from " + f.toString());
    }

    public synchronized void close() {
        if (this.urlFileIndex != null) {
            this.urlFileIndex.close();
            this.urlFileIndex = null;
        }
    }

    public void clear() {
        ConcurrentLog.info("CrawlQueue", "cleaning CrawlQueue with " + this.urlFileIndex.size() + " entries from " + this.urlFileIndex.filename());
        try {
            this.urlFileIndex.clear();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        this.double_push_check.clear();
    }

    public Request get(final byte[] urlhash) throws IOException {
        assert urlhash != null;
        if (this.urlFileIndex == null) return null; // case occurs during shutdown
        final Row.Entry entry = this.urlFileIndex.get(urlhash, false);
        if (entry == null) return null;
        return new Request(entry);
    }

    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, SpaceExceededException {
        // removes all entries with a specific profile hash.
        // this may last some time
        // returns number of deletions

        // first find a list of url hashes that shall be deleted
        final HandleSet urlHashes = new RowHandleSet(this.urlFileIndex.row().primaryKeyLength, Base64Order.enhancedCoder, 100);
        final long terminate = timeout == Long.MAX_VALUE ? Long.MAX_VALUE : (timeout > 0) ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
        synchronized (this) {
            final Iterator<Row.Entry> i = this.urlFileIndex.rows();
            Row.Entry rowEntry;
            Request crawlEntry;
            while (i.hasNext() && (System.currentTimeMillis() < terminate)) {
                rowEntry = i.next();
                crawlEntry = new Request(rowEntry);
                if (crawlEntry.profileHandle().equals(profileHandle)) {
                    urlHashes.put(crawlEntry.url().hash());
                }
            }
        }

        // then delete all these urls from the queues and the file index
        return remove(urlHashes);
    }

    /**
     * this method is only here, because so many import/export methods need it
       and it was implemented in the previous architecture
       however, usage is not recommended
     * @param urlHashes, a list of hashes that shall be removed
     * @return number of entries that had been removed
     * @throws IOException
     */
    public synchronized int remove(final HandleSet urlHashes) throws IOException {
        final int s = this.urlFileIndex.size();
        int removedCounter = 0;
        for (final byte[] urlhash: urlHashes) {
            final Row.Entry entry = this.urlFileIndex.remove(urlhash);
            if (entry != null) removedCounter++;

            // remove from double-check caches
            this.double_push_check.remove(urlhash);
        }
        if (removedCounter == 0) return 0;
        assert this.urlFileIndex.size() + removedCounter == s : "urlFileIndex.size() = " + this.urlFileIndex.size() + ", s = " + s;

        return removedCounter;
    }

    public boolean has(final byte[] urlhashb) {
        return this.urlFileIndex.has(urlhashb) || this.double_push_check.has(urlhashb);
    }

    public int size() {
        return this.urlFileIndex.size();
    }

    public boolean isEmpty() {
        return this.urlFileIndex.isEmpty();
    }

    /**
     * push a crawl request on the balancer stack
     * @param entry
     * @return null if this was successful or a String explaining what went wrong in case of an error
     * @throws IOException
     * @throws SpaceExceededException
     */
    public String push(final Request entry, CrawlProfile profile, final RobotsTxt robots) throws IOException, SpaceExceededException {
        assert entry != null;
        final byte[] hash = entry.url().hash();
        synchronized (this) {
            // double-check
            if (this.double_push_check.has(hash)) return "double occurrence in double_push_check";
            if (this.urlFileIndex.has(hash)) return "double occurrence in urlFileIndex";

            if (this.double_push_check.size() > MAX_DOUBLE_PUSH_CHECK || MemoryControl.shortStatus()) this.double_push_check.clear();
            this.double_push_check.put(hash);

            // increase dom counter
            if (profile != null && profile.domMaxPages() != Integer.MAX_VALUE && profile.domMaxPages() > 0) {
                profile.domInc(entry.url().getHost());
            }
            
            // add to index
            final int s = this.urlFileIndex.size();
            this.urlFileIndex.put(entry.toRow());
            assert s < this.urlFileIndex.size() : "hash = " + ASCII.String(hash) + ", s = " + s + ", size = " + this.urlFileIndex.size();
            assert this.urlFileIndex.has(hash) : "hash = " + ASCII.String(hash);

            // add the hash to a queue if the host is unknown to get this fast into the balancer
            // now disabled to prevent that a crawl 'freezes' to a specific domain which hosts a lot of pages; the queues are filled anyway
            //if (!this.domainStacks.containsKey(entry.url().getHost())) pushHashToDomainStacks(entry.url().getHost(), entry.url().hash());
        }
        robots.ensureExist(entry.url(), profile.getAgent(), true); // concurrently load all robots.txt
        return null;
    }

    /**
     * Get the minimum sleep time for a given url. The result can also be negative to reflect the time since the last access
     * The time can be as low as Integer.MIN_VALUE to show that there should not be any limitation at all.
     * @param robots
     * @param profileEntry
     * @param crawlURL
     * @return the sleep time in milliseconds; may be negative for no sleep time
     */
    private long getDomainSleepTime(final RobotsTxt robots, final CrawlProfile profileEntry, final DigestURL crawlURL) {
        if (profileEntry == null) return 0;
        long sleeptime = (
            profileEntry.cacheStrategy() == CacheStrategy.CACHEONLY ||
            (profileEntry.cacheStrategy() == CacheStrategy.IFEXIST && Cache.has(crawlURL.hash()))
            ) ? Integer.MIN_VALUE : Latency.waitingRemaining(crawlURL, robots, profileEntry.getAgent()); // this uses the robots.txt database and may cause a loading of robots.txt from the server
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
    private long getRobotsTime(final RobotsTxt robots, final DigestURL crawlURL, ClientIdentification.Agent agent) {
        long sleeptime = Latency.waitingRobots(crawlURL, robots, agent); // this uses the robots.txt database and may cause a loading of robots.txt from the server
        return sleeptime < 0 ? 0 : sleeptime;
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
    public Request pop(final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times

        if (this.urlFileIndex.isEmpty()) return null;
        long sleeptime = 0;
        Request crawlEntry = null;
        CrawlProfile profileEntry = null;
        while (this.urlFileIndex.size() > 0) {
            synchronized (this) {
                Row.Entry rowEntry = this.urlFileIndex.removeOne();
                if (rowEntry == null) return null;
                crawlEntry = new Request(rowEntry);
                profileEntry = cs.getActive(UTF8.getBytes(crawlEntry.profileHandle()));
                if (profileEntry == null) {
                    ConcurrentLog.warn("CrawlQueue", "no profile entry for handle " + crawlEntry.profileHandle());
                    return null;
                }
                
                // check blacklist (again) because the user may have created blacklist entries after the queue has been filled
                if (Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, crawlEntry.url())) {
                    ConcurrentLog.fine("CrawlQueue", "URL '" + crawlEntry.url() + "' is in blacklist.");
                    continue;
                }
        
                // at this point we must check if the crawlEntry has relevance because the crawl profile still exists
                // if not: return null. A calling method must handle the null value and try again
                profileEntry = cs.getActive(UTF8.getBytes(crawlEntry.profileHandle()));
                if (profileEntry == null) {
                    ConcurrentLog.warn("CrawlQueue", "no profile entry for handle " + crawlEntry.profileHandle());
                    continue;
                }
            }
        }
        // depending on the caching policy we need sleep time to avoid DoS-like situations
        sleeptime = getDomainSleepTime(robots, profileEntry, crawlEntry.url());

        ClientIdentification.Agent agent = profileEntry == null ? ClientIdentification.yacyInternetCrawlerAgent : profileEntry.getAgent();
        long robotsTime = getRobotsTime(robots, crawlEntry.url(), agent);
        Latency.updateAfterSelection(crawlEntry.url(), profileEntry == null ? 0 : robotsTime);
        if (delay && sleeptime > 0) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works propertly
            // this is only to protection against the worst case, where the crawler could
            // behave in a DoS-manner
            ConcurrentLog.info("CrawlQueue", "forcing crawl-delay of " + sleeptime + " milliseconds for " + crawlEntry.url().getHost() + ": " + Latency.waitingRemainingExplain(crawlEntry.url(), robots, agent));
            long loops = sleeptime / 1000;
            long rest = sleeptime % 1000;
            if (loops < 3) {
                rest = rest + 1000 * loops;
                loops = 0;
            }
            Thread.currentThread().setName("CrawlQueue waiting for " +crawlEntry.url().getHost() + ": " + sleeptime + " milliseconds");
            synchronized(this) {
                // must be synchronized here to avoid 'takeover' moves from other threads which then idle the same time which would not be enough
                if (rest > 0) {try {this.wait(rest);} catch (final InterruptedException e) {}}
                for (int i = 0; i < loops; i++) {
                    ConcurrentLog.info("CrawlQueue", "waiting for " + crawlEntry.url().getHost() + ": " + (loops - i) + " seconds remaining...");
                    try {this.wait(1000); } catch (final InterruptedException e) {}
                }
            }
            Latency.updateAfterSelection(crawlEntry.url(), robotsTime);
        }
        return crawlEntry;
    }
}
