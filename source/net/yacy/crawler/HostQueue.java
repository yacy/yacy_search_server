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
import java.util.Iterator;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
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

public class HostQueue {

    public  static final String indexSuffix           = ".stack";
    private static final int    EcoFSBufferSize       = 1000;
    private static final int    objectIndexBufferSize = 1000;
    private static final int    MAX_DOUBLE_PUSH_CHECK = 100000;

    private final String               hostHash;
    private final File                 queuesPath;
    private       BufferedObjectIndex  requestStack;
    private       HandleSet            urlHashDoubleCheck;

    public HostQueue(
            final File queuesPath,
            final String hostHash,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.hostHash = hostHash;
        this.queuesPath = queuesPath;
        this.urlHashDoubleCheck = new RowHandleSet(URIMetadataRow.rowdef.primaryKeyLength, URIMetadataRow.rowdef.objectOrder, 0);
        
        // create a stack for newly entered entries
        if (!(this.queuesPath.exists())) this.queuesPath.mkdir(); // make the path
        this.queuesPath.mkdirs();
        final File f = new File(this.queuesPath, this.hostHash + indexSuffix);
        try {
            this.requestStack = new BufferedObjectIndex(new Table(f, Request.rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727, true), objectIndexBufferSize);
        } catch (final SpaceExceededException e) {
            try {
                this.requestStack = new BufferedObjectIndex(new Table(f, Request.rowdef, 0, 0, false, exceed134217727, true), objectIndexBufferSize);
            } catch (final SpaceExceededException e1) {
                ConcurrentLog.logException(e1);
            }
        }
        ConcurrentLog.info("Balancer", "opened balancer file with " + this.requestStack.size() + " entries from " + f.toString());
    }

    public synchronized void close() {
        int sizeBeforeClose = this.size();
        if (this.urlHashDoubleCheck != null) {
            this.urlHashDoubleCheck.clear();
            this.urlHashDoubleCheck = null;
        }
        if (this.requestStack != null) {
            this.requestStack.close();
            this.requestStack = null;
        }
        if (sizeBeforeClose == 0) {
            // clean up
            new File(this.queuesPath, this.hostHash + indexSuffix).delete();
        }
    }

    public void clear() {
        try {
            this.requestStack.clear();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        this.urlHashDoubleCheck.clear();
    }

    public Request get(final byte[] urlhash) throws IOException {
        assert urlhash != null;
        if (this.requestStack == null) return null; // case occurs during shutdown
        final Row.Entry entry = this.requestStack.get(urlhash, false);
        if (entry == null) return null;
        return new Request(entry);
    }

    public int removeAllByProfileHandle(final String profileHandle, final long timeout) throws IOException, SpaceExceededException {
        // first find a list of url hashes that shall be deleted
        final HandleSet urlHashes = new RowHandleSet(this.requestStack.row().primaryKeyLength, Base64Order.enhancedCoder, 100);
        final long terminate = timeout == Long.MAX_VALUE ? Long.MAX_VALUE : (timeout > 0) ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
        synchronized (this) {
            final Iterator<Row.Entry> i = this.requestStack.rows();
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
     * remove urls from the queue
     * @param urlHashes, a list of hashes that shall be removed
     * @return number of entries that had been removed
     * @throws IOException
     */
    public synchronized int remove(final HandleSet urlHashes) throws IOException {
        final int s = this.requestStack.size();
        int removedCounter = 0;
        for (final byte[] urlhash: urlHashes) {
            final Row.Entry entry = this.requestStack.remove(urlhash);
            if (entry != null) removedCounter++;

            // remove from double-check caches
            this.urlHashDoubleCheck.remove(urlhash);
        }
        if (removedCounter == 0) return 0;
        assert this.requestStack.size() + removedCounter == s : "urlFileIndex.size() = " + this.requestStack.size() + ", s = " + s;
        return removedCounter;
    }

    public boolean has(final byte[] urlhashb) {
        return this.requestStack.has(urlhashb) || this.urlHashDoubleCheck.has(urlhashb);
    }

    public int size() {
        return this.requestStack.size();
    }

    public boolean isEmpty() {
        return this.requestStack.isEmpty();
    }

    public String push(final Request entry, CrawlProfile profile, final RobotsTxt robots) throws IOException, SpaceExceededException {
        assert entry != null;
        final byte[] hash = entry.url().hash();
        synchronized (this) {
            // double-check
            if (this.urlHashDoubleCheck.has(hash)) return "double occurrence in double_push_check";
            if (this.requestStack.has(hash)) return "double occurrence in urlFileIndex";

            if (this.urlHashDoubleCheck.size() > MAX_DOUBLE_PUSH_CHECK || MemoryControl.shortStatus()) this.urlHashDoubleCheck.clear();
            this.urlHashDoubleCheck.put(hash);

            // increase dom counter
            if (profile != null && profile.domMaxPages() != Integer.MAX_VALUE && profile.domMaxPages() > 0) {
                profile.domInc(entry.url().getHost());
            }
            
            // add to index
            final int s = this.requestStack.size();
            this.requestStack.put(entry.toRow());
            assert s < this.requestStack.size() : "hash = " + ASCII.String(hash) + ", s = " + s + ", size = " + this.requestStack.size();
            assert this.requestStack.has(hash) : "hash = " + ASCII.String(hash);

            // add the hash to a queue if the host is unknown to get this fast into the balancer
            // now disabled to prevent that a crawl 'freezes' to a specific domain which hosts a lot of pages; the queues are filled anyway
            //if (!this.domainStacks.containsKey(entry.url().getHost())) pushHashToDomainStacks(entry.url().getHost(), entry.url().hash());
        }
        robots.ensureExist(entry.url(), profile.getAgent(), true); // concurrently load all robots.txt
        return null;
    }
    
    public Request pop() throws IOException {
        // returns a crawl entry from the stack and ensures minimum delta times

        Request crawlEntry = null;
        while (!this.requestStack.isEmpty()) {
            synchronized (this) {
                Row.Entry rowEntry = this.requestStack.removeOne();
                if (rowEntry == null) return null;
                crawlEntry = new Request(rowEntry);
                
                // check blacklist (again) because the user may have created blacklist entries after the queue has been filled
                if (Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, crawlEntry.url())) {
                    ConcurrentLog.fine("CRAWLER", "URL '" + crawlEntry.url() + "' is in blacklist.");
                    continue;
                }
                break;
            }
        }
        if (crawlEntry == null) return null;
        return crawlEntry;
    }

    public Iterator<Request> iterator() throws IOException {
        return new EntryIterator();
    }

    private class EntryIterator implements Iterator<Request> {

        private Iterator<Row.Entry> rowIterator;

        public EntryIterator() throws IOException {
            this.rowIterator = HostQueue.this.requestStack.rows();
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

}
