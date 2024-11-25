/**
 *  HostQueue
 *  SPDX-FileCopyrightText: 2013 Michael Peter Christen <mc@yacy.net)>
 *  SPDX-License-Identifier: GPL-2.0-or-later
 *  First released 24.09.2013 at https://yacy.net
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

import static net.yacy.kelondro.util.FileUtils.deletedelete;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

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

    private final File          hostPath; // path to the stack files
    private final String        hostName;
    private final String        hostHash;
    private final int           port;
    private final boolean       exceed134217727;
    private final boolean       onDemand;
    private final NavigableMap<Integer, Index> depthStacks;

    /**
     * Create or open host queue. The host part of the hostUrl parameter is used
     * to calculate the stack directory name.
     *
     * @param hostsPath
     * @param hostUrl
     * @param onDemand
     * @param exceed134217727
     * @throws MalformedURLException
     */
    public HostQueue (
            final File hostsPath,
            final DigestURL hostUrl, // any url from that host (only host data are extracted)
            final boolean onDemand,
            final boolean exceed134217727) throws MalformedURLException {
        this.onDemand = onDemand;
        this.exceed134217727 = exceed134217727;
        this.hostName = (hostUrl.getHost() == null)  ? "localhost" : hostUrl.getHost(); // might be null (file://) but hostqueue needs a name (for queue file)
        this.port = hostUrl.getPort();
        this.hostHash = hostUrl.hosthash(); // hosthash is calculated by protocol + hostname + port
        // hostName/port included just for human readability (& historically), "-#" marker used to define begin of hosthash in directoryname
        if(this.hostName.startsWith("[") && this.hostName.endsWith("]") && this.hostName.contains(":")) {
            /* Percent-encode the host name when it is an IPV6 address, as the ':' character is illegal in a file name on MS Windows FAT32 and NTFS file systems */
            File path;
            try {
                path = new File(hostsPath, URLEncoder.encode(this.hostName, StandardCharsets.UTF_8.name()) + "-#"+ this.hostHash + "." + this.port);
            } catch (final UnsupportedEncodingException e) {
                /* This should not happen has UTF-8 encoding support is required for any JVM implementation */
                path = new File(hostsPath, this.hostName + "-#"+ this.hostHash + "." + this.port);
            }
            this.hostPath = path;
        } else {
            this.hostPath = new File(hostsPath, this.hostName + "-#"+ this.hostHash + "." + this.port);
        }
        this.depthStacks = new ConcurrentSkipListMap<>();
        this.init();
    }

    /**
     * Initializes host queue from cache files. The internal id of the queue is
     * extracted form the path name an must match the key initially generated
     * currently the hosthash is used as id.
     * @param hostPath path of the stack directory (containing the primary key/id of the queue)
     * @param onDemand
     * @param exceed134217727
     * @throws MalformedURLException
     */
    public HostQueue (
            final File hostPath,
            final boolean onDemand,
            final boolean exceed134217727) throws MalformedURLException {
        this.onDemand = onDemand;
        this.exceed134217727 = exceed134217727;
        this.hostPath = hostPath;
        // parse the hostName and port from the file name
        final String filename = hostPath.getName();
        final int pdot = filename.lastIndexOf('.');
        if (pdot < 0) throw new RuntimeException("hostPath name must contain a dot: " + filename);
        this.port = Integer.parseInt(filename.substring(pdot + 1)); // consider "host.com" contains dot but no required port -> will throw exception
        final int p1 = filename.lastIndexOf("-#");
        if (p1 >= 0) {
            String hostNameInFile = filename.substring(0,p1);
            if(hostNameInFile.startsWith("%5B") && hostNameInFile.endsWith("%5D") && hostNameInFile.contains("%3A")) {
                /* Host name is a percent-encoded IPV6 address */
                try {
                    hostNameInFile = URLDecoder.decode(hostNameInFile, StandardCharsets.UTF_8.name());
                } catch (final UnsupportedEncodingException | RuntimeException ignored) {
                    /* This should not happen has UTF-8 encoding support is required for any JVM implementation */
                }
                this.hostName = hostNameInFile;
            } else {
                this.hostName = hostNameInFile;
            }
            this.hostHash = filename.substring(p1+2,pdot);
        } else throw new RuntimeException("hostPath name must contain -# followd by hosthash: " + filename);
        this.depthStacks = new ConcurrentSkipListMap<>();
        this.init();
    }

    /**
     * Opens and initializes the host queue
     * @throws MalformedURLException if directory for the host could not be created
     */
    private final void init() throws MalformedURLException {
        if (!(this.hostPath.exists())) {
            this.hostPath.mkdirs();
            if (!this.hostPath.exists()) { // check if directory created (if not, likely a name violation)
                throw new MalformedURLException("hostPath could not be created: " + this.hostPath.toString());
            }
        }
        final int size = this.openAllStacks();
        if (log.isInfo()) log.info("opened HostQueue " + this.hostPath.getAbsolutePath() + " with " + size + " urls.");
    }

    public String getHost() {
        return this.hostName;
    }

    public int getPort() {
        return this.port;
    }

    /**
     * Get the hosthash of this queue determined during init.
     *
     * @return
     */
    public String getHostHash() {
        return this.hostHash;
    }

    private int openAllStacks() {
        final String[] l = this.hostPath.list();
        int c = 0;
        if (l != null) for (final String s: l) {
            if (s.endsWith(indexSuffix)) try {
                final int depth = Integer.parseInt(s.substring(0, s.length() - indexSuffix.length()));
                final File stackFile = new File(this.hostPath, s);
                final Index depthStack = this.openStack(stackFile);
                if (depthStack != null) {
                    final int sz = depthStack.size();
                    if (sz == 0) {
                        depthStack.close();
                        deletedelete(stackFile);
                    } else {
                        this.depthStacks.put(depth, depthStack);
                        c += sz;
                    }
                }
            } catch (final NumberFormatException e) {}
            }
        return c;
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
                deletedelete(this.getFile(entry.getKey()));
                this.depthStacks.remove(entry.getKey());
                continue;
            }
            return entry.getValue();
        }
        // this should not happen
        //assert false;
        return null;
    }

    /**
     * Get existing url stack with crawl depth or create a new (empty) stack
     *
     * @param depth
     * @return existing or new/empty stack
     */
    private Index getStack(final int depth) {
        Index depthStack;
        // create a new stack
        synchronized (this) {
            depthStack = this.depthStacks.get(depth);
            if (depthStack != null) return depthStack;
            // now actually create a new stack
            final File f = this.getFile(depth);
            depthStack = this.openStack(f);
            if (depthStack != null) this.depthStacks.put(depth, depthStack);
        }
        return depthStack;
    }

    private File getFile(final int depth) {
        String name = Integer.toString(depth);
        while (name.length() < 4) name = "0" + name;
        final File f = new File(this.hostPath, name + indexSuffix);
        return f;
    }

    private Index openStack(final File f) {
        for (int i = 0; i < 10; i++) {
            // we try that again if it fails because it shall not fail
            if (this.onDemand && (!f.exists() || f.length() < 10000)) {
                try {
                    return new BufferedObjectIndex(new OnDemandOpenFileIndex(f, Request.rowdef, this.exceed134217727), objectIndexBufferSize);
                } catch (final kelondroException e) {
                    // possibly the file was closed meanwhile
                    ConcurrentLog.logException(e);
                }
            } else {
                    try {
                    return new BufferedObjectIndex(new Table(f, Request.rowdef, EcoFSBufferSize, 0, false, this.exceed134217727, true), objectIndexBufferSize);
                } catch (final SpaceExceededException e) {
                    try {
                        return new BufferedObjectIndex(new Table(f, Request.rowdef, 0, 0, false, this.exceed134217727, true), objectIndexBufferSize);
                    } catch (final SpaceExceededException e1) {
                        ConcurrentLog.logException(e1);
                    }
                } catch (final kelondroException e) {
                    // possibly the file was closed meanwhile
                    ConcurrentLog.logException(e);
            }
        }
        }
        return null;
    }

    @Override
    public synchronized void close() {
        log.info("closing HostQueue, closing " + this.depthStacks.size() + " depthStacks for host " + this.hostName);
        for (final Map.Entry<Integer, Index> entry: this.depthStacks.entrySet()) {
            final int size = entry.getValue().size();
            entry.getValue().close();
            if (size == 0) deletedelete(this.getFile(entry.getKey()));
        }
        this.depthStacks.clear();
        final String[] l = this.hostPath.list();
        if ((l == null || l.length == 0) && this.hostPath != null) deletedelete(this.hostPath);
    }

    @Override
    public void clear() {
        final Set<Integer> keys = this.depthStacks.keySet(); // make a copy to be able to delete those concurrently
        for (final Integer key: keys) {
            final Index index = this.depthStacks.get(key);
            index.close();
            deletedelete(this.getFile(key));
            this.depthStacks.remove(key);
        }
        final String[] l = this.hostPath.list();
        if (l != null) for (final String s: l) {
            deletedelete(new File(this.hostPath, s));
        }
        deletedelete(this.hostPath);
    }

    @Override
    public Request get(final byte[] urlhash) throws IOException {
        assert urlhash != null;
        if (this.depthStacks == null) return null; // case occurs during shutdown
        for (final Index depthStack: this.depthStacks.values()) {
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
            for (final Index depthStack: this.depthStacks.values()) {
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
        for (final String h: hosthashes) {
            if (this.hostHash.equals(h)) {
                final int s = this.size();
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
        for (final Index depthStack: this.depthStacks.values()) {
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
        for (int retry = 0; retry < 3; retry++) {
            try {
                for (final Index depthStack: this.depthStacks.values()) {
                    if (depthStack.has(urlhashb)) return true;
                }
                return false;
            } catch (final ConcurrentModificationException e) {}
        }
        return false;
    }

    @Override
    public int size() {
        int size = 0;
        for (final Index depthStack: this.depthStacks.values()) {
            size += depthStack.size();
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (final Index depthStack: this.depthStacks.values()) {
            if (!depthStack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public String push(final Request entry, final CrawlProfile profile, final RobotsTxt robots) throws IOException, SpaceExceededException {
        assert entry != null;
        final byte[] hash = entry.url().hash();
        if (this.has(hash)) return "double occurrence in urlFileIndex";
        synchronized (this) {
            // double-check
            if (this.has(hash)) return "double occurrence in urlFileIndex";

            // increase dom counter
            if (profile != null) {
                final int maxPages = profile.domMaxPages();
                if (maxPages != Integer.MAX_VALUE && maxPages > 0) {
                    final String host = entry.url().getHost();
                    profile.domInc(host);
                }
            }

            // add to index
            final Index depthStack = this.getStack(entry.depth());
            final int s = depthStack.size();
            depthStack.put(entry.toRow());
            assert s < depthStack.size() : "hash = " + ASCII.String(hash) + ", s = " + s + ", size = " + depthStack.size();
            assert depthStack.has(hash) : "hash = " + ASCII.String(hash);
        }
        return null;
    }


    @Override
    public Request pop(final boolean delay, final CrawlSwitchboard cs, final RobotsTxt robots) throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times
        long sleeptime = 0;
        Request crawlEntry = null;
        CrawlProfile profileEntry = null;
        synchronized (this) {
            mainloop: while (true) {
                final Index depthStack = this.getLowestStack();
                if (depthStack == null) return null;
                Row.Entry rowEntry = null;
                depthstack: while (depthStack.size() > 0) {
                    rowEntry = depthStack.removeOne();
                    if (rowEntry != null) break depthstack;
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
                break mainloop;
            }
        }
        if (crawlEntry == null) return null;
        final ClientIdentification.Agent agent = profileEntry == null ? ClientIdentification.yacyInternetCrawlerAgent : profileEntry.getAgent();
        final long robotsTime = Latency.getRobotsTime(robots, crawlEntry.url(), agent);
        Latency.updateAfterSelection(crawlEntry.url(), profileEntry == null ? 0 : robotsTime);

        // the following case should be avoided by selection previously
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
            final String tname = Thread.currentThread().getName();
            Thread.currentThread().setName("Balancer waiting for " + crawlEntry.url().getHost() + ": " + sleeptime + " milliseconds");
            if (rest > 0) {try {Thread.sleep(rest);} catch (final InterruptedException e) {}}
            for (int i = 0; i < loops; i++) {
                if (log.isInfo()) log.info("waiting for " + crawlEntry.url().getHost() + ": " + (loops - i) + " seconds remaining...");
                try {Thread.sleep(1000); } catch (final InterruptedException e) {}
            }
            Thread.currentThread().setName(tname); // restore the name so we do not see this in the thread dump as a waiting thread
            Latency.updateAfterSelection(crawlEntry.url(), robotsTime);
        }
        return crawlEntry;
    }

    @Override
    public Iterator<Request> iterator() throws IOException {
        final Iterator<Map.Entry<Integer, Index>> depthIterator = this.depthStacks.entrySet().iterator();
        @SuppressWarnings("unchecked")
        final Iterator<Row.Entry>[] rowIterator = (Iterator<Row.Entry>[]) Array.newInstance(Iterator.class, 1);
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
                            final Map.Entry<Integer, Index> entry = depthIterator.next();
                            rowIterator[0] = entry.getValue().iterator();
                        }
                        if (!rowIterator[0].hasNext()) return null;
                        final Row.Entry rowEntry = rowIterator[0].next();
                        if (rowEntry == null) return null;
                        return new Request(rowEntry);
                    } catch (final Throwable e) {
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
    public Map<String, Integer[]> getDomainStackHosts(final RobotsTxt robots) {
        final Map<String, Integer[]> map = new TreeMap<>();
        final int delta = Latency.waitingRemainingGuessed(this.hostName, this.port, this.hostHash, robots, ClientIdentification.yacyInternetCrawlerAgent);
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
    public List<Request> getDomainStackReferences(final String host, final int maxcount, final long maxtime) {
        if (host == null) return new ArrayList<>(0);
        if (!this.hostName.equals(host)) return new ArrayList<>(0);
        final ArrayList<Request> cel = new ArrayList<>(maxcount);
        final long timeout = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        Iterator<Request> i;
        try {
            i = this.iterator();
            while (i.hasNext()) {
                final Request r = i.next();
                if (r != null) cel.add(r);
                if (System.currentTimeMillis() > timeout || cel.size() >= maxcount) break;
            }
        } catch (final IOException e) {
        }
        return cel;
    }

    @Override
    public int getOnDemandLimit() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean getExceed134217727() {
        return this.exceed134217727;
    }

}
