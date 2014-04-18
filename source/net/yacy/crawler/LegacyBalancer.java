// LegacyBalancer.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 24.09.2005
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

package net.yacy.crawler;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.sorting.OrderedScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.Latency;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.BufferedObjectIndex;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;

public class LegacyBalancer implements Balancer {

    private static final String indexSuffix           = "A.db";
    private static final int    EcoFSBufferSize       = 1000;
    private static final int    objectIndexBufferSize = 1000;
    private static final int    MAX_DOUBLE_PUSH_CHECK = 100000;

    // class variables filled with external values
    private final File                 cacheStacksPath;
    private       BufferedObjectIndex  urlFileIndex;

    // class variables computed during operation
    private final ConcurrentMap<String, HostHandles> domainStacks; // a map from host name to lists with url hashs
    private final HandleSet                        double_push_check; // for debugging
    private long                                   lastDomainStackFill;
    private int                                    domStackInitSize;
    private final List<Map.Entry<String, byte[]>>  zeroWaitingCandidates;
    private final Random random; // used to alternate between choose-from-maxstack or choose from any zero-waiting

    private static class HostHandles {
        public String hosthash;
        public HandleSet handleSet;
        public HostHandles(final String hosthash, final HandleSet handleSet) {
            this.hosthash = hosthash;
            this.handleSet = handleSet;
        }
    }
    
    public LegacyBalancer(
            final File cachePath,
            final String stackname,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.cacheStacksPath = cachePath;
        this.domainStacks = new ConcurrentHashMap<String, HostHandles>();
        this.domStackInitSize = Integer.MAX_VALUE;
        this.double_push_check = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0);
        this.zeroWaitingCandidates = new ArrayList<Map.Entry<String, byte[]>>();
        this.random = new Random(System.currentTimeMillis());
        
        // create a stack for newly entered entries
        if (!(cachePath.exists())) cachePath.mkdir(); // make the path
        this.cacheStacksPath.mkdirs();
        final File f = new File(this.cacheStacksPath, stackname + indexSuffix);
        try {
            this.urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727, true), objectIndexBufferSize);
        } catch (final SpaceExceededException e) {
            try {
                this.urlFileIndex = new BufferedObjectIndex(new Table(f, Request.rowdef, 0, 0, false, exceed134217727, true), objectIndexBufferSize);
            } catch (final SpaceExceededException e1) {
                ConcurrentLog.logException(e1);
            }
        }
        this.lastDomainStackFill = 0;
        ConcurrentLog.info("Balancer", "opened balancer file with " + this.urlFileIndex.size() + " entries from " + f.toString());
    }

    @Override
    public synchronized void close() {
        if (this.urlFileIndex != null) {
            this.urlFileIndex.close();
            this.urlFileIndex = null;
        }
    }

    @Override
    public void clear() {
        ConcurrentLog.info("Balancer", "cleaning balancer with " + this.urlFileIndex.size() + " entries from " + this.urlFileIndex.filename());
        try {
            this.urlFileIndex.clear();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        this.domainStacks.clear();
        this.double_push_check.clear();
    }

    @Override
    public Request get(final byte[] urlhash) throws IOException {
        assert urlhash != null;
        if (this.urlFileIndex == null) return null; // case occurs during shutdown
        final Row.Entry entry = this.urlFileIndex.get(urlhash, false);
        if (entry == null) return null;
        return new Request(entry);
    }

    @Override
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
    @Override
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

        // iterate through the domain stacks
        final Iterator<Map.Entry<String, HostHandles>> q = this.domainStacks.entrySet().iterator();
        HandleSet stack;
        while (q.hasNext()) {
            stack = q.next().getValue().handleSet;
            for (final byte[] handle: urlHashes) stack.remove(handle);
            if (stack.isEmpty()) q.remove();
        }
        
        // iterate through zero-waiting map
        final Iterator<Map.Entry<String, byte[]>> i = this.zeroWaitingCandidates.iterator();
        while (i.hasNext()) {
            if (urlHashes.has(i.next().getValue())) i.remove();
        }

        return removedCounter;
    }

    @Override
    public boolean has(final byte[] urlhashb) {
        return this.urlFileIndex.has(urlhashb) || this.double_push_check.has(urlhashb);
    }

    @Override
    public int size() {
        return this.urlFileIndex.size();
    }

    @Override
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
    @Override
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
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names to an integer array: {the size of the domain stack, guessed delta waiting time}
     */
    @Override
    public Map<String, Integer[]> getDomainStackHosts(RobotsTxt robots) {
        Map<String, Integer[]> map = new TreeMap<String, Integer[]>(); // we use a tree map to get a stable ordering
        for (Map.Entry<String, HostHandles> entry: this.domainStacks.entrySet()) {
            final String hostname = entry.getKey();
            final HostHandles hosthandles = entry.getValue();
            int size = hosthandles.handleSet.size();
            int delta = Latency.waitingRemainingGuessed(hostname, hosthandles.hosthash, robots, ClientIdentification.yacyInternetCrawlerAgent);
            map.put(hostname, new Integer[]{size, delta});
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
    public List<Request> getDomainStackReferences(final String host, int maxcount, final long maxtime) {
        final HostHandles hh = this.domainStacks.get(host);
        if (hh == null) return new ArrayList<Request>(0);
        final HandleSet domainList = hh.handleSet;
        if (domainList.isEmpty()) return new ArrayList<Request>(0);
        maxcount = Math.min(maxcount, domainList.size());
        final ArrayList<Request> cel = new ArrayList<Request>(maxcount);
        long timeout = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        for (int i = 0; i < maxcount; i++) {
            final byte[] urlhash = domainList.getOne(i);
            if (urlhash == null) continue;
            Row.Entry rowEntry;
            try {
                rowEntry = this.urlFileIndex.get(urlhash, true);
            } catch (final IOException e) {
                continue;
            }
            if (rowEntry == null) continue;
            Request crawlEntry;
            try {
                crawlEntry = new Request(rowEntry);
            } catch (final IOException e) {
                continue;
            }
            cel.add(crawlEntry);
            if (System.currentTimeMillis() > timeout) break;
        }
        return cel;
    }

    private void pushHashToDomainStacks(String host, String hosthash, final byte[] urlhash) throws SpaceExceededException {
        // extend domain stack
        if (host == null) host = Domains.LOCALHOST;
        HostHandles hh = this.domainStacks.get(host);
        if (hh == null) {
            // create new list
            HandleSet domainList = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 1);
            domainList.put(urlhash);
            this.domainStacks.put(host, new HostHandles(hosthash, domainList));
        } else {
            HandleSet domainList = hh.handleSet;
            // extend existent domain list
            domainList.put(urlhash);
        }
    }

    private void removeHashFromDomainStacks(String host, final byte[] urlhash) {
        // reduce domain stack
        if (host == null) host = Domains.LOCALHOST;
        HostHandles hh = this.domainStacks.get(host);
        if (hh == null) {
            this.domainStacks.remove(host);
            return;
        }
        HandleSet domainList = hh.handleSet;
        domainList.remove(urlhash);
        if (domainList.isEmpty()) this.domainStacks.remove(host);
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
    public Request pop(final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times

        long sleeptime = 0;
        Request crawlEntry = null;
        CrawlProfile profileEntry = null;
        byte[] failhash = null;
        while (!this.urlFileIndex.isEmpty()) {
            byte[] nexthash = getbest(robots, cs);
            if (nexthash == null) return null;
            
            synchronized (this) {
                Row.Entry rowEntry = (nexthash == null) ? null : this.urlFileIndex.remove(nexthash);
                if (rowEntry == null) continue;
                
                crawlEntry = new Request(rowEntry);
                //Log.logInfo("Balancer", "fetched next url: " + crawlEntry.url().toNormalform(true, false));
    
                // check blacklist (again) because the user may have created blacklist entries after the queue has been filled
                if (Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, crawlEntry.url())) {
                    ConcurrentLog.fine("CRAWLER", "URL '" + crawlEntry.url() + "' is in blacklist.");
                    continue;
                }
    
                // at this point we must check if the crawlEntry has relevance because the crawl profile still exists
                // if not: return null. A calling method must handle the null value and try again
                profileEntry = cs.get(UTF8.getBytes(crawlEntry.profileHandle()));
                if (profileEntry == null) {
                    ConcurrentLog.fine("Balancer", "no profile entry for handle " + crawlEntry.profileHandle());
                    continue;
                }
                // depending on the caching policy we need sleep time to avoid DoS-like situations
                sleeptime = Latency.getDomainSleepTime(robots, profileEntry, crawlEntry.url());
    
                assert Base64Order.enhancedCoder.equal(nexthash, rowEntry.getPrimaryKeyBytes()) : "result = " + ASCII.String(nexthash) + ", rowEntry.getPrimaryKeyBytes() = " + ASCII.String(rowEntry.getPrimaryKeyBytes());
                assert Base64Order.enhancedCoder.equal(nexthash, crawlEntry.url().hash()) : "result = " + ASCII.String(nexthash) + ", crawlEntry.url().hash() = " + ASCII.String(crawlEntry.url().hash());
    
                if (failhash != null && Base64Order.enhancedCoder.equal(failhash, nexthash)) break; // prevent endless loops
                break;
            }
        }
        if (crawlEntry == null) return null;
        ClientIdentification.Agent agent = profileEntry == null ? ClientIdentification.yacyInternetCrawlerAgent : profileEntry.getAgent();
        long robotsTime = Latency.getRobotsTime(robots, crawlEntry.url(), agent);
        Latency.updateAfterSelection(crawlEntry.url(), profileEntry == null ? 0 : robotsTime);
        if (delay && sleeptime > 0) {
            // force a busy waiting here
            // in best case, this should never happen if the balancer works properly
            // this is only to protection against the worst case, where the crawler could
            // behave in a DoS-manner
            ConcurrentLog.info("BALANCER", "forcing crawl-delay of " + sleeptime + " milliseconds for " + crawlEntry.url().getHost() + ": " + Latency.waitingRemainingExplain(crawlEntry.url(), robots, agent) + ", domainStacks.size() = " + this.domainStacks.size() + ", domainStacksInitSize = " + this.domStackInitSize);
            long loops = sleeptime / 1000;
            long rest = sleeptime % 1000;
            if (loops < 3) {
                rest = rest + 1000 * loops;
                loops = 0;
            }
            Thread.currentThread().setName("Balancer waiting for " +crawlEntry.url().getHost() + ": " + sleeptime + " milliseconds");
            synchronized(this) {
                // must be synchronized here to avoid 'takeover' moves from other threads which then idle the same time which would not be enough
                if (rest > 0) {try {this.wait(rest);} catch (final InterruptedException e) {}}
                for (int i = 0; i < loops; i++) {
                    ConcurrentLog.info("BALANCER", "waiting for " + crawlEntry.url().getHost() + ": " + (loops - i) + " seconds remaining...");
                    try {this.wait(1000); } catch (final InterruptedException e) {}
                }
            }
            Latency.updateAfterSelection(crawlEntry.url(), robotsTime);
        }
        return crawlEntry;
    }

    private byte[] getbest(final RobotsTxt robots, final CrawlSwitchboard cs) {

        synchronized (this.zeroWaitingCandidates) {
            if (this.zeroWaitingCandidates.size() > 0) {
                byte[] urlhash = pickFromZeroWaiting();
                if (urlhash != null) return urlhash;
            }
            this.zeroWaitingCandidates.clear();
            
            // check if we need to get entries from the file index
            try {
                fillDomainStacks();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
    
            // iterate over the domain stacks
            final Iterator<Map.Entry<String, HostHandles>> i = this.domainStacks.entrySet().iterator();
            Map.Entry<String, HostHandles> entry;
            OrderedScoreMap<Map.Entry<String, byte[]>> nextZeroCandidates = new OrderedScoreMap<Map.Entry<String, byte[]>>(null);
            OrderedScoreMap<Map.Entry<String, byte[]>> failoverCandidates = new OrderedScoreMap<Map.Entry<String, byte[]>>(null);
            int newCandidatesForward = 1;
            while (i.hasNext() && nextZeroCandidates.size() < 1000) {
                entry = i.next();
                final String hostname = entry.getKey();
                final HostHandles hosthandles = entry.getValue();
    
                // clean up empty entries
                if (hosthandles.handleSet.isEmpty()) {
                    i.remove();
                    continue;
                }
    
                final byte[] urlhash = hosthandles.handleSet.getOne(0);
                if (urlhash == null) continue;
    
                int w;
                Row.Entry rowEntry;
                try {
                    rowEntry = this.urlFileIndex.get(urlhash, false);
                    if (rowEntry == null) continue; // may have been deleted there manwhile
                    Request crawlEntry = new Request(rowEntry);
                    CrawlProfile profileEntry = cs.get(UTF8.getBytes(crawlEntry.profileHandle()));
                    if (profileEntry == null) {
                        ConcurrentLog.warn("Balancer", "no profile entry for handle " + crawlEntry.profileHandle());
                        continue;
                    }
                    w = Latency.waitingRemaining(crawlEntry.url(), robots, profileEntry.getAgent());
                } catch (final IOException e1) {
                    ConcurrentLog.warn("Balancer", e1.getMessage(), e1);
                    continue;
                }

                if (w <= 0) {
                    if (w == Integer.MIN_VALUE) {
                        if (newCandidatesForward-- > 0) {
                            nextZeroCandidates.set(new AbstractMap.SimpleEntry<String, byte[]>(hostname, urlhash), 10000);
                        } else {
                            failoverCandidates.set(new AbstractMap.SimpleEntry<String, byte[]>(hostname, urlhash), 0);
                        }
                    } else {
                        nextZeroCandidates.set(new AbstractMap.SimpleEntry<String, byte[]>(hostname, urlhash), hosthandles.handleSet.size());
                    }
                } else {
                    failoverCandidates.set(new AbstractMap.SimpleEntry<String, byte[]>(hostname, urlhash), w);
                }
            }
            //Log.logInfo("Balancer", "*** getbest: created new nextZeroCandidates-list, size = " + nextZeroCandidates.size() + ", domainStacks.size = " + this.domainStacks.size());
            
            if (!nextZeroCandidates.isEmpty()) {
                // take some of the nextZeroCandidates and put the best into the zeroWaitingCandidates
                int pick = nextZeroCandidates.size() <= 10 ? nextZeroCandidates.size() : Math.max(1, nextZeroCandidates.size() / 3);
                Iterator<Map.Entry<String, byte[]>> k = nextZeroCandidates.keys(false);
                while (k.hasNext() && pick-- > 0) {
                    this.zeroWaitingCandidates.add(k.next());
                }
                //Log.logInfo("Balancer", "*** getbest: created new zeroWaitingCandidates-list, size = " + zeroWaitingCandidates.size() + ", domainStacks.size = " + this.domainStacks.size());
                
                return pickFromZeroWaiting();
            }

            if (!failoverCandidates.isEmpty()) {
                // bad luck: just take that one with least waiting
                Iterator<Map.Entry<String, byte[]>> k = failoverCandidates.keys(true);
                String besthost;
                byte[] besturlhash;
                Map.Entry<String, byte[]> hosthash;
                while (k.hasNext()) {
                    hosthash = k.next();
                    //if (failoverCandidates.get(hosthash) > 2000) break; // thats too long; we want a second chance for this!
                    besthost = hosthash.getKey();
                    besturlhash = hosthash.getValue();
                    removeHashFromDomainStacks(besthost, besturlhash);
                    //Log.logInfo("Balancer", "*** getbest: no zero waiting candidates, besthost = " + besthost);
                    return besturlhash;
                }
            }
            
            //Log.logInfo("Balancer", "*** getbest: besturlhash == null");
            return null; // this should never happen
        }
    }

    private byte[] pickFromZeroWaiting() {
        // by random we choose now either from the largest stack or from any of the other stacks
        String host = null;
        byte[] hash = null;
        while (this.zeroWaitingCandidates.size() > 0) {
            Map.Entry<String, byte[]> z = this.zeroWaitingCandidates.remove(this.random.nextInt(this.zeroWaitingCandidates.size()));
            HostHandles hh = this.domainStacks.get(z.getKey());
            if (hh == null) continue;
            host = z.getKey(); if (host == null) continue;
            hash = z.getValue(); if (hash == null) continue;
            removeHashFromDomainStacks(host, hash);
            ConcurrentLog.info("Balancer", "// getbest: picked a random from the zero-waiting stack: " + host + ", zeroWaitingCandidates.size = " + this.zeroWaitingCandidates.size());
            return hash;
        }

        //Log.logInfo("Balancer", "*** getbest: picking from zero-waiting stack failed!" + " zeroWaitingCandidates.size = " + this.zeroWaitingCandidates.size());
        this.zeroWaitingCandidates.clear();
        return null;
    }
    
    private void fillDomainStacks() throws IOException {
        if (!this.domainStacks.isEmpty() && System.currentTimeMillis() - this.lastDomainStackFill < 60000L) return;
        this.domainStacks.clear();
        this.lastDomainStackFill = System.currentTimeMillis();
        final HandleSet blackhandles = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 10);
        String host;
        Request request;
        int count = 0;
        long timeout = System.currentTimeMillis() + 5000;
        for (Row.Entry entry: this.urlFileIndex.random(10000)) {
            if (entry == null) continue;
            request = new Request(entry);
            
            // check blacklist (again) because the user may have created blacklist entries after the queue has been filled
            if (Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, request.url())) {
                ConcurrentLog.fine("CRAWLER", "URL '" + request.url() + "' is in blacklist.");
                try {blackhandles.put(entry.getPrimaryKeyBytes());} catch (final SpaceExceededException e) {}
                continue;
            }
            
            host = request.url().getHost();
            try {
                pushHashToDomainStacks(host, request.url().hosthash(), entry.getPrimaryKeyBytes());
            } catch (final SpaceExceededException e) {
                break;
            }
            count++;
            if (this.domainStacks.size() >= 1000 || count >= 100000 || System.currentTimeMillis() > timeout) break;
        }
        
        // if we collected blacklist entries then delete them now
        for (byte[] blackhandle: blackhandles) this.urlFileIndex.remove(blackhandle);
        
        ConcurrentLog.info("BALANCER", "re-fill of domain stacks; fileIndex.size() = " + this.urlFileIndex.size() + ", domainStacks.size = " + this.domainStacks.size() + ", blackhandles = " + blackhandles.size() + ", collection time = " + (System.currentTimeMillis() - this.lastDomainStackFill) + " ms");
        this.domStackInitSize = this.domainStacks.size();
    }

    @Override
    public Iterator<Request> iterator() throws IOException {
        return new EntryIterator();
    }

    private class EntryIterator implements Iterator<Request> {

        private Iterator<Row.Entry> rowIterator;

        public EntryIterator() throws IOException {
            this.rowIterator = LegacyBalancer.this.urlFileIndex.rows();
        }

        @Override
        public boolean hasNext() {
            return (this.rowIterator == null) ? false : this.rowIterator.hasNext();
        }

        @Override
        public Request next() {
            final Row.Entry entry = this.rowIterator.next();
            try {
                return (entry == null) ? null : new Request(entry);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                this.rowIterator = null;
                return null;
            }
        }

        @Override
        public void remove() {
            if (this.rowIterator != null) this.rowIterator.remove();
        }

    }

    @Override
    public int removeAllByHostHashes(Set<String> hosthashes) {
        return 0;
    }

}
