/**
 *  HostQueue
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.Latency;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.BufferedObjectIndex;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.OnDemandOpenFileIndex;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.kelondroException;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;

public class HostQueue implements Balancer {

    private final static ConcurrentLog log = new ConcurrentLog("HostQueue");
    
    public  static final String indexSuffix           = ".stack";
    private static final int    EcoFSBufferSize       = 1000;
    private static final int    objectIndexBufferSize = 1000;

    private final File          hostPath;
    private final String        hostName;
    private       String        hostHash;
    private final int           port;
    private final boolean       exceed134217727;
    private final boolean       onDemand;
    private       TreeMap<Integer, Index> depthStacks;

    public HostQueue (
            final File hostsPath,
            final String hostName,
            final int port,
            final boolean onDemand,
            final boolean exceed134217727) {
        this.onDemand = onDemand;
        this.exceed134217727 = exceed134217727;
        this.hostName = hostName;
        this.port = port;
        this.hostPath = new File(hostsPath, this.hostName + "." + this.port);
        init();
    }

    public HostQueue (
            final File hostPath,
            final boolean onDemand,
            final boolean exceed134217727) {
        this.onDemand = onDemand;
        this.exceed134217727 = exceed134217727;
        this.hostPath = hostPath;
        // parse the hostName and port from the file name
        String filename = hostPath.getName();
        int p = filename.lastIndexOf('.');
        this.hostName = filename.substring(0, p);
        this.port = Integer.parseInt(filename.substring(p + 1));
        init();
    }
    
    private final void init() {
        try {
            this.hostHash = DigestURL.hosthash(this.hostName, this.port);
        } catch (MalformedURLException e) {
            this.hostHash = "";
        }
        if (!(this.hostPath.exists())) this.hostPath.mkdirs();
        this.depthStacks = new TreeMap<Integer, Index>();
        int size = openAllStacks();
        if (log.isInfo()) log.info("opened HostQueue " + this.hostPath.getAbsolutePath() + " with " + size + " urls.");
    }
    
    public String getHost() {
        return this.hostName;
    }
    
    public int getPort() {
        return this.port;
    }
    
    private int openAllStacks() {
        String[] l = this.hostPath.list();
        int c = 0;
        if (l != null) for (String s: l) {
            if (s.endsWith(indexSuffix)) try {
                int depth = Integer.parseInt(s.substring(0, s.length() - indexSuffix.length()));
                File stackFile = new File(this.hostPath, s);
                Index depthStack = openStack(stackFile, depth);
                if (depthStack != null) {
                    int sz = depthStack.size();
                    if (sz == 0) {
                        depthStack.close();
                        stackFile.delete();
                    } else {
                        this.depthStacks.put(depth, depthStack);
                        c += sz;
                    }
                }
            } catch (NumberFormatException e) {}
        }
        return c;
    }

    public synchronized int getLowestStackDepth() {
        while (this.depthStacks.size() > 0) {
            Map.Entry<Integer, Index> entry;
            synchronized (this) {
                entry = this.depthStacks.firstEntry();
            }
            if (entry == null) return 0; // happens only if map is empty
            if (entry.getValue().size() == 0) {
                entry.getValue().close();
                getFile(entry.getKey()).delete();
                this.depthStacks.remove(entry.getKey());
                continue;
            }
            return entry.getKey();
        }
        // this should not happen but it happens if a deletion is done
        //assert false;
        return 0;
    }

    private Index getLowestStack() {
        while (this.depthStacks.size() > 0) {
            Map.Entry<Integer, Index> entry;
            synchronized (this) {
                entry = this.depthStacks.firstEntry();
            }
            if (entry == null) return null; // happens only if map is empty
            if (entry.getValue().size() == 0) {
                entry.getValue().close();
                getFile(entry.getKey()).delete();
                this.depthStacks.remove(entry.getKey());
                continue;
            }
            return entry.getValue();
        }
        // this should not happen
        //assert false;
        return null;
    }
    
    private Index getStack(int depth) {
        Index depthStack;
        synchronized (this) {
            depthStack = this.depthStacks.get(depth);
            if (depthStack != null) return depthStack;
        }
        // create a new stack
        synchronized (this) {
            // check again 
            depthStack = this.depthStacks.get(depth);
            if (depthStack != null) return depthStack;
            // now actually create a new stack
            final File f = getFile(depth);
            depthStack = openStack(f, depth);
            if (depthStack != null) this.depthStacks.put(depth, depthStack);
        }
        return depthStack;
    }
    
    private File getFile(int depth) {
        String name = Integer.toString(depth);
        while (name.length() < 4) name = "0" + name;
        final File f = new File(this.hostPath, name + indexSuffix);
        return f;
    }
    
    private Index openStack(File f, int depth) {
        for (int i = 0; i < 10; i++) {
            // we try that again if it fails because it shall not fail
            if (this.onDemand && depth > 3 && (!f.exists() || f.length() < 10000)) {
                try {
                    return new BufferedObjectIndex(new OnDemandOpenFileIndex(f, Request.rowdef, exceed134217727), objectIndexBufferSize);
                } catch (kelondroException e) {
                    // possibly the file was closed meanwhile
                    ConcurrentLog.logException(e);
                }
            } else {
                try {
                    return new BufferedObjectIndex(new Table(f, Request.rowdef, EcoFSBufferSize, 0, false, exceed134217727, true), objectIndexBufferSize);
                } catch (final SpaceExceededException e) {
                    try {
                        return new BufferedObjectIndex(new Table(f, Request.rowdef, 0, 0, false, exceed134217727, true), objectIndexBufferSize);
                    } catch (final SpaceExceededException e1) {
                        ConcurrentLog.logException(e1);
                    }
                } catch (kelondroException e) {
                    // possibly the file was closed meanwhile
                    ConcurrentLog.logException(e);
                }
            }
        }
        return null;
    }

    @Override
    public synchronized void close() {
        for (Map.Entry<Integer, Index> entry: this.depthStacks.entrySet()) {
            int size = entry.getValue().size();
            entry.getValue().close();
            if (size == 0) getFile(entry.getKey()).delete();
        }
        this.depthStacks.clear();
        String[] l = this.hostPath.list();
        if ((l == null || l.length == 0) && this.hostPath != null) this.hostPath.delete();
    }

    @Override
    public synchronized void clear() {
        for (Map.Entry<Integer, Index> entry: this.depthStacks.entrySet()) {
            entry.getValue().close();
            getFile(entry.getKey()).delete();
        }
        this.depthStacks.clear();
        String[] l = this.hostPath.list();
        if (l != null) for (String s: l) {
            new File(this.hostPath, s).delete();
        }
        this.hostPath.delete();
    }

    @Override
    public Request get(final byte[] urlhash) throws IOException {
        assert urlhash != null;
        if (this.depthStacks == null) return null; // case occurs during shutdown
        for (Index depthStack: this.depthStacks.values()) {
            final Row.Entry entry = depthStack.get(urlhash, false);
            if (entry == null) return null;
            return new Request(entry);
        }
        return null;
    }

    @Override
    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, SpaceExceededException {
        // first find a list of url hashes that shall be deleted
        final long terminate = timeout == Long.MAX_VALUE ? Long.MAX_VALUE : (timeout > 0) ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
        int count = 0;
        synchronized (this) {
            for (Index depthStack: this.depthStacks.values()) {
                final HandleSet urlHashes = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 100);
                final Iterator<Row.Entry> i = depthStack.rows();
                Row.Entry rowEntry;
                Request crawlEntry;
                while (i.hasNext() && (System.currentTimeMillis() < terminate)) {
                    rowEntry = i.next();
                    crawlEntry = new Request(rowEntry);
                    if (crawlEntry.profileHandle().equals(profileHandle)) {
                        urlHashes.put(crawlEntry.url().hash());
                    }
                    if (System.currentTimeMillis() > terminate) break;
                }
                for (final byte[] urlhash: urlHashes) {
                    depthStack.remove(urlhash);
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * delete all urls which are stored for given host hashes
     * @param hosthashes
     * @return number of deleted urls
     */
    @Override
    public int removeAllByHostHashes(final Set<String> hosthashes) {
        for (String h: hosthashes) {
            if (this.hostHash.equals(h)) {
                int s = this.size();
                this.clear();
                return s;
            }
        }
        return 0;
    }

    /**
     * remove urls from the queue
     * @param urlHashes, a list of hashes that shall be removed
     * @return number of entries that had been removed
     * @throws IOException
     */
    @Override
    public synchronized int remove(final HandleSet urlHashes) throws IOException {
        int removedCounter = 0;
        for (Index depthStack: this.depthStacks.values()) {
            final int s = depthStack.size();
            for (final byte[] urlhash: urlHashes) {
                final Row.Entry entry = depthStack.remove(urlhash);
                if (entry != null) removedCounter++;
            }
            if (removedCounter == 0) return 0;
            assert depthStack.size() + removedCounter == s : "urlFileIndex.size() = " + depthStack.size() + ", s = " + s;
        }
        return removedCounter;
    }

    @Override
    public boolean has(final byte[] urlhashb) {
        for (Index depthStack: this.depthStacks.values()) {
            if (depthStack.has(urlhashb)) return true;
        }
        return false;
    }

    @Override
    public int size() {
        int size = 0;
        for (Index depthStack: this.depthStacks.values()) {
            size += depthStack.size();
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (Index depthStack: this.depthStacks.values()) {
            if (!depthStack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public String push(final Request entry, CrawlProfile profile, final RobotsTxt robots) throws IOException, SpaceExceededException {
        assert entry != null;
        final byte[] hash = entry.url().hash();
        synchronized (this) {
            // double-check
            if (this.has(hash)) return "double occurrence in urlFileIndex";

            // increase dom counter
            if (profile != null && profile.domMaxPages() != Integer.MAX_VALUE && profile.domMaxPages() > 0) {
                profile.domInc(entry.url().getHost());
            }
            
            // add to index
            Index depthStack = getStack(entry.depth());
            final int s = depthStack.size();
            depthStack.put(entry.toRow());
            assert s < depthStack.size() : "hash = " + ASCII.String(hash) + ", s = " + s + ", size = " + depthStack.size();
            assert depthStack.has(hash) : "hash = " + ASCII.String(hash);
        }
        return null;
    }


    @Override
    public Request pop(boolean delay, CrawlSwitchboard cs, RobotsTxt robots) throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times
        long sleeptime = 0;
        Request crawlEntry = null;
        CrawlProfile profileEntry = null;
        synchronized (this) {
            mainloop: while (true) {
                Index depthStack = getLowestStack();
                if (depthStack == null) return null;
                Row.Entry rowEntry = null;
                while (depthStack.size() > 0) {
                    rowEntry = depthStack.removeOne();
                    if (rowEntry != null) break;
                }
                if (rowEntry == null) continue mainloop;
                crawlEntry = new Request(rowEntry);

                // check blacklist (again) because the user may have created blacklist entries after the queue has been filled
                if (Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, crawlEntry.url())) {
                    if (log.isFine()) log.fine("URL '" + crawlEntry.url() + "' is in blacklist.");
                    continue mainloop;
                }

                // at this point we must check if the crawlEntry has relevance because the crawl profile still exists
                // if not: return null. A calling method must handle the null value and try again
                profileEntry = cs.get(UTF8.getBytes(crawlEntry.profileHandle()));
                if (profileEntry == null) {
                    if (log.isFine()) log.fine("no profile entry for handle " + crawlEntry.profileHandle());
                    continue mainloop;
                }
                
                // depending on the caching policy we need sleep time to avoid DoS-like situations
                sleeptime = Latency.getDomainSleepTime(robots, profileEntry, crawlEntry.url());
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
            if (log.isInfo()) log.info("forcing crawl-delay of " + sleeptime + " milliseconds for " + crawlEntry.url().getHost() + ": " + Latency.waitingRemainingExplain(crawlEntry.url(), robots, agent));
            long loops = sleeptime / 1000;
            long rest = sleeptime % 1000;
            if (loops < 3) {
                rest = rest + 1000 * loops;
                loops = 0;
            }
            Thread.currentThread().setName("Balancer waiting for " + crawlEntry.url().getHost() + ": " + sleeptime + " milliseconds");
            synchronized(this) {
                // must be synchronized here to avoid 'takeover' moves from other threads which then idle the same time which would not be enough
                if (rest > 0) {try {this.wait(rest);} catch (final InterruptedException e) {}}
                for (int i = 0; i < loops; i++) {
                    if (log.isInfo()) log.info("waiting for " + crawlEntry.url().getHost() + ": " + (loops - i) + " seconds remaining...");
                    try {this.wait(1000); } catch (final InterruptedException e) {}
                }
            }
            Latency.updateAfterSelection(crawlEntry.url(), robotsTime);
        }
        return crawlEntry;
    }

    @Override
    public Iterator<Request> iterator() throws IOException {
        final Iterator<Map.Entry<Integer, Index>> depthIterator = this.depthStacks.entrySet().iterator();
        @SuppressWarnings("unchecked")
        final Iterator<Row.Entry>[] rowIterator = new Iterator[1];
        rowIterator[0] = null;
        return new Iterator<Request>() {
            @Override
            public boolean hasNext() {
                return depthIterator.hasNext() || (rowIterator[0] != null && rowIterator[0].hasNext());
            }
            @Override
            public Request next() {
                synchronized (HostQueue.this) {
                    try {
                        while (rowIterator[0] == null || !rowIterator[0].hasNext()) {
                            Map.Entry<Integer, Index> entry = depthIterator.next();
                            rowIterator[0] = entry.getValue().iterator();
                        }
                        if (!rowIterator[0].hasNext()) return null;
                        Row.Entry rowEntry = rowIterator[0].next();
                        if (rowEntry == null) return null;
                        return new Request(rowEntry);
                    } catch (Throwable e) {
                        return null;
                    }
                }
            }
            @Override
            public void remove() {
                rowIterator[0].remove();
            }
        };
    }

    /**
     * get a list of domains that are currently maintained as domain stacks
     * @return a map of clear text strings of host names to an integer array: {the size of the domain stack, guessed delta waiting time}
     */
    @Override
    public Map<String, Integer[]> getDomainStackHosts(RobotsTxt robots) {
        Map<String, Integer[]> map = new TreeMap<String, Integer[]>();
        int delta = Latency.waitingRemainingGuessed(this.hostName, this.hostHash, robots, ClientIdentification.yacyInternetCrawlerAgent);
        map.put(this.hostName, new Integer[]{this.size(), delta});
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
        if (host == null) return new ArrayList<Request>(0);
        if (!this.hostName.equals(host)) return new ArrayList<Request>(0);
        final ArrayList<Request> cel = new ArrayList<Request>(maxcount);
        long timeout = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        Iterator<Request> i;
        try {
            i = this.iterator();
            while (i.hasNext()) {
                Request r = i.next();
                if (r != null) cel.add(r);
                if (System.currentTimeMillis() > timeout || cel.size() >= maxcount) break;
            }
        } catch (IOException e) {
        }
        return cel;
    }

}
