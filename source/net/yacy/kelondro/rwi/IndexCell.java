// IndexCell.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 1.3.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package net.yacy.kelondro.rwi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.Order;
import net.yacy.cora.sorting.Rating;
import net.yacy.cora.storage.ComparableARC;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.MergeIterator;
import net.yacy.search.EventTracker;
import net.yacy.search.Switchboard;


/*
 * an index cell is a part of the horizontal index in the new segment-oriented index
 * data structure of YaCy. If there is no filter in front of a cell, it might also be
 * the organization for a complete segment index. Each cell consists of a number of BLOB files, that
 * must be merged to represent a single index. In fact these index files are only merged on demand
 * if there are too many of them. An index merge can be done with a stream read and stream write operation.
 * in normal operation, there are only a number of read-only BLOB files and a single RAM cache that is
 * kept in the RAM as long as a given limit of entries is reached. Then the cache is flushed and becomes
 * another BLOB file in the index array.
 */

public final class IndexCell<ReferenceType extends Reference> extends AbstractBufferedIndex<ReferenceType> implements BufferedIndex<ReferenceType>, Iterable<ReferenceContainer<ReferenceType>> {

    private static final long cleanupCycle =  60000;
    private static final long dumpCycle    = 600000;

    // class variables
    private final ReferenceContainerArray<ReferenceType> array;
    private       ReferenceContainerCache<ReferenceType> ram;
    private final ComparableARC<byte[], Integer>         countCache;
    private       int                                    maxRamEntries;
    private final IODispatcher                           merger;
    private       long                                   lastCleanup;
    private long  lastDump;
    private final long                                   targetFileSize, maxFileSize;
    private final int                                    writeBufferSize;
    private final Map<byte[], HandleSet>                 removeDelayedURLs; // mapping from word hashes to a list of url hashes
    private       boolean                                flushShallRun;
    private final Thread                                 flushThread;

    public IndexCell(
            final File cellPath,
            final String prefix,
            final ReferenceFactory<ReferenceType> factory,
            final ByteOrder termOrder,
            final int termSize,
            final int maxRamEntries,
            final long targetFileSize,
            final long maxFileSize,
            final int writeBufferSize
            ) throws IOException {
        super(factory);

        this.merger = new IODispatcher(1, 1, writeBufferSize);
        this.array = new ReferenceContainerArray<ReferenceType>(cellPath, prefix, factory, termOrder, termSize);
        this.ram = new ReferenceContainerCache<ReferenceType>(factory, termOrder, termSize);
        this.countCache = new ComparableARC<byte[], Integer>(1000, termOrder);
        this.maxRamEntries = maxRamEntries;
        this.merger.start();
        this.lastCleanup = System.currentTimeMillis();
        this.lastDump = System.currentTimeMillis();
        this.targetFileSize = targetFileSize;
        this.maxFileSize = maxFileSize;
        this.writeBufferSize = writeBufferSize;
        this.removeDelayedURLs = new TreeMap<byte[], HandleSet>(Word.commonHashOrder);
        this.flushShallRun = true;
        this.flushThread = new FlushThread(cellPath.toString());
        this.flushThread.start();
    }

    private class FlushThread extends Thread {
        public FlushThread(String name) {
            this.setName("IndexCell.FlushThread(" + name + ")");
        }
        @Override
        public void run() {
            while (IndexCell.this.flushShallRun) {
                try {
                    flushBuffer();
                } catch (final Throwable e) {
                    ConcurrentLog.logException(e);
                }
                try { Thread.sleep(3000); } catch (final InterruptedException e) {}
            }
        }

        private void flushBuffer() {

            // dump the cache if necessary
            final long t = System.currentTimeMillis();
            if ((IndexCell.this.ram.size() >= IndexCell.this.maxRamEntries ||
                (IndexCell.this.ram.size() > 3000 && !MemoryControl.request(80L * 1024L * 1024L, false)) ||
                (!IndexCell.this.ram.isEmpty() && IndexCell.this.lastDump + dumpCycle < t))) {
                synchronized (IndexCell.this.merger) {
                    if (IndexCell.this.ram.size() >= IndexCell.this.maxRamEntries ||
                        (IndexCell.this.ram.size() > 3000 && !MemoryControl.request(80L * 1024L * 1024L, false)) ||
                        (!IndexCell.this.ram.isEmpty() && IndexCell.this.lastDump + dumpCycle < t)) try {
                            IndexCell.this.lastDump = System.currentTimeMillis();
                        // removed delayed
                        try {removeDelayed();} catch (final IOException e) {}
                        // dump the ram
                        final File dumpFile = IndexCell.this.array.newContainerBLOBFile();
                        // a critical point: when the ram is handed to the dump job,
                        // don't write into it any more. Use a fresh one instead
                        ReferenceContainerCache<ReferenceType> ramdump;
                        final ByteOrder termOrder = IndexCell.this.ram.termKeyOrdering();
                        final int termSize = IndexCell.this.ram.termKeyLength();
                        synchronized (this) {
                            ramdump = IndexCell.this.ram;
                            // get a fresh ram cache
                            IndexCell.this.ram = new ReferenceContainerCache<ReferenceType>(IndexCell.this.factory, termOrder, termSize);
                        }
                        // dump the buffer
                        IndexCell.this.merger.dump(ramdump, dumpFile, IndexCell.this.array);
                        IndexCell.this.lastDump = System.currentTimeMillis();
                    } catch (final Throwable e) {
                        // catch all exceptions
                        ConcurrentLog.logException(e);
                    }
                }
            }

            // clean-up the cache
            if ((IndexCell.this.array.entries() > 50 ||
                 IndexCell.this.lastCleanup + cleanupCycle < t)) {
                synchronized (IndexCell.this.array) {
                    if (IndexCell.this.array.entries() > 50 || (IndexCell.this.lastCleanup + cleanupCycle < System.currentTimeMillis())) try {
                        IndexCell.this.lastCleanup = System.currentTimeMillis(); // set time to prevent that this is called to soon again
                        IndexCell.this.shrink(IndexCell.this.targetFileSize, IndexCell.this.maxFileSize);
                        IndexCell.this.lastCleanup = System.currentTimeMillis(); // set again to mark end of procedure
                    } catch (final Throwable e) {
                        // catch all exceptions
                        ConcurrentLog.logException(e);
                    }
                }
            }
        }

    }

    private boolean shrink(final long targetFileSize, final long maxFileSize) {
        if (this.array.entries() < 2) return false;
        boolean donesomething = false;

        // first try to merge small files that match
        int term = 10;
        while (term-- > 0 && (this.merger.queueLength() < 3 || this.array.entries() >= 50)) {
            if (!this.array.shrinkBestSmallFiles(this.merger, targetFileSize)) break;
            donesomething = true;
        }

        // then try to merge simply any small file
        term = 10;
        while (term-- > 0 && (this.merger.queueLength() < 2)) {
            if (!this.array.shrinkAnySmallFiles(this.merger, targetFileSize)) break;
            donesomething = true;
        }

        // if there is no small file, then merge matching files up to limit
        term = 10;
        while (term-- > 0 && (this.merger.queueLength() < 1)) {
            if (!this.array.shrinkUpToMaxSizeFiles(this.merger, maxFileSize)) break;
            donesomething = true;
        }

        // rewrite old files (hack from sixcooler, see http://forum.yacy-websuche.de/viewtopic.php?p=15004#p15004)
        term = 10;
        while (term-- > 0 && (this.merger.queueLength() < 1)) {
            if (!this.array.shrinkOldFiles(this.merger)) break;
            donesomething = true;
        }

        return donesomething;
    }

    public int deleteOld(int minsize, long maxtime) throws IOException {
        long timeout = System.currentTimeMillis() + maxtime;
        Collection<byte[]> keys = keys4LargeReferences(minsize, maxtime / 3);
        int c = 0;
        int oldShrinkMaxsize = ReferenceContainer.maxReferences;
        ReferenceContainer.maxReferences = minsize;
        for (byte[] key: keys) {
            ReferenceContainer<ReferenceType> container = this.get(key, null);
            container.shrinkReferences();
            try {this.add(container); c++;} catch (SpaceExceededException e) {}
            if (System.currentTimeMillis() > timeout) break;
        }
        ReferenceContainer.maxReferences = oldShrinkMaxsize;
        return c;
    }
    
    private Collection<byte[]> keys4LargeReferences(int minsize, long maxtime) throws IOException {
        long timeout = System.currentTimeMillis() + maxtime;
        ArrayList<byte[]> keys = new ArrayList<byte[]>();
        Iterator<ByteArray> ci = this.ram.keys();
        while (ci.hasNext()) {
            byte[] k = ci.next().asBytes();
            if (this.ram.count(k) >= minsize) keys.add(k);
        }
        CloneableIterator<byte[]> ki = this.array.keys(true, false);
        while (ki.hasNext()) {
            byte[] k = ki.next();
            if (this.array.count(k) >= minsize) keys.add(k);
            if (System.currentTimeMillis() > timeout) break;
        }
        return keys;
    }
    
    
    /*
     * methods to implement Index
     */

    /**
     * every index entry is made for a term which has a fixed size
     * @return the size of the term
     */
    @Override
    public int termKeyLength() {
        return this.ram.termKeyLength();
    }

    /**
     * add entries to the cell: this adds the new entries always to the RAM part, never to BLOBs
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public void add(final ReferenceContainer<ReferenceType> newEntries) throws IOException, SpaceExceededException {
        try {
            this.ram.add(newEntries);
            final long t = System.currentTimeMillis();
            if (this.ram.size() % 1000 == 0 || this.lastCleanup + cleanupCycle < t || this.lastDump + dumpCycle < t) {
                EventTracker.update(EventTracker.EClass.WORDCACHE, Long.valueOf(this.ram.size()), true);
            }
        } catch (final SpaceExceededException e) {
            EventTracker.update(EventTracker.EClass.WORDCACHE, Long.valueOf(this.ram.size()), true);
            this.ram.add(newEntries);
        }

    }

    @Override
    public void add(final byte[] termHash, final ReferenceType entry) throws IOException, SpaceExceededException {
        try {
            this.ram.add(termHash, entry);
            final long t = System.currentTimeMillis();
            if (this.ram.size() % 1000 == 0 || this.lastCleanup + cleanupCycle < t || this.lastDump + dumpCycle < t) {
                EventTracker.update(EventTracker.EClass.WORDCACHE, Long.valueOf(this.ram.size()), true);
            }
        } catch (final SpaceExceededException e) {
            EventTracker.update(EventTracker.EClass.WORDCACHE, Long.valueOf(this.ram.size()), true);
            this.ram.add(termHash, entry);
        }
    }

    /**
     * checks if there is any container for this termHash, either in RAM or any BLOB
     */
    @Override
    public boolean has(final byte[] termHash) {
        if (this.ram.has(termHash)) return true;
        return this.array.has(termHash);
    }

    /**
     * count number of references for a given term
     * this method may cause strong IO load if called too frequently.
     */
    @Override
    public int count(final byte[] termHash) {
        final Integer cachedCount = this.countCache.get(termHash);
        if (cachedCount != null) return cachedCount.intValue();

        int countFile = 0;
        // read fresh values from file
        try {
            countFile = this.array.count(termHash);
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
        assert countFile >= 0;

        // count from container in ram
        final ReferenceContainer<ReferenceType> countRam = this.ram.get(termHash, null);
        assert countRam == null || countRam.size() >= 0;
        int c = countRam == null ? countFile : countFile + countRam.size();
        // exclude entries from delayed remove
        synchronized (this.removeDelayedURLs) {
            final HandleSet s = this.removeDelayedURLs.get(termHash);
            if (s != null) c -= s.size();
            if (c < 0) c = 0;
        }
        // put count result into cache
        if (MemoryControl.shortStatus()) this.countCache.clear();
        this.countCache.insert(termHash, c);
        return c;
    }

    /**
     * all containers in the BLOBs and the RAM are merged and returned.
     * Please be aware that the returned values may be top-level cloned ReferenceContainers or direct links to containers
     * If the containers are modified after they are returned, they MAY alter the stored index.
     * @throws IOException
     * @return a container with merged ReferenceContainer from RAM and the file array or null if there is no data to be returned
     */
    @Override
    public ReferenceContainer<ReferenceType> get(final byte[] termHash, final HandleSet urlselection) throws IOException {
        final ReferenceContainer<ReferenceType> c0 = this.ram.get(termHash, null);
        ReferenceContainer<ReferenceType> c1 = null;
        try {
            c1 = this.array.get(termHash);
        } catch (final SpaceExceededException e2) {
            ConcurrentLog.logException(e2);
        }
        ReferenceContainer<ReferenceType> result = null;
        if (c0 != null && c1 != null) {
            try {
                result = c1.merge(c0);
            } catch (final SpaceExceededException e) {
                // try to free some ram
                try {
                    result = c1.merge(c0);
                } catch (final SpaceExceededException e1) {
                    // go silently over the problem
                    result = (c1.size() > c0.size()) ? c1: c0;
                }
            }
        } else if (c0 != null) {
            result = c0;
        } else if (c1 != null) {
            result = c1;
        }
        if (result == null) return null;
        // remove the failed urls
        synchronized (this.removeDelayedURLs) {
            final HandleSet s = this.removeDelayedURLs.get(termHash);
            if (s != null) result.removeEntries(s);
        }
        return result;
    }

    /**
     * deleting a container affects the containers in RAM and all the BLOB files
     * the deleted containers are merged and returned as result of the method
     * @throws IOException
     */
    @Override
    public ReferenceContainer<ReferenceType> remove(final byte[] termHash) throws IOException {
        removeDelayed();
        ReferenceContainer<ReferenceType> c1 = null;
        try {
            c1 = this.array.get(termHash);
        } catch (final SpaceExceededException e2) {
            ConcurrentLog.logException(e2);
        }
        if (c1 != null) {
            this.array.delete(termHash);
        }
        final ReferenceContainer<ReferenceType> c0 = this.ram.remove(termHash);
        if (c1 == null) return c0;
        if (c0 == null) return c1;
        try {
            return c1.merge(c0);
        } catch (final SpaceExceededException e) {
            // try to free some ram
            try {
                return c1.merge(c0);
            } catch (final SpaceExceededException e1) {
                // go silently over the problem
                return (c1.size() > c0.size()) ? c1: c0;
            }
        }
    }

    @Override
    public void delete(final byte[] termHash) throws IOException {
        removeDelayed();
        ReferenceContainer<ReferenceType> c1 = null;
        try {
            c1 = this.array.get(termHash);
        } catch (final SpaceExceededException e2) {
            ConcurrentLog.logException(e2);
        }
        if (c1 != null) {
            this.array.delete(termHash);
        }
        this.ram.delete(termHash);
        return;
    }

    @Override
    public void removeDelayed(final byte[] termHash, final byte[] urlHashBytes) {
        HandleSet r;
        synchronized (this.removeDelayedURLs) {
            r = this.removeDelayedURLs.get(termHash);
        }
        if (r == null) {
            r = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0);
        }
        try {
            r.put(urlHashBytes);
        } catch (final SpaceExceededException e) {
            try {remove(termHash, urlHashBytes);} catch (final IOException e1) {}
            return;
        }
        synchronized (this.removeDelayedURLs) {
            this.removeDelayedURLs.put(termHash, r);
        }
    }

    @Override
    public void removeDelayed() throws IOException {
        final HandleSet words = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0); // a set of url hashes where a worker thread tried to work on, but failed.
        synchronized (this.removeDelayedURLs) {
            for (final byte[] b: this.removeDelayedURLs.keySet()) try {words.put(b);} catch (final SpaceExceededException e) {}
        }

        synchronized (this.removeDelayedURLs) {
            for (final byte[] b: words) {
                final HandleSet urls = this.removeDelayedURLs.remove(b);
                if (urls != null) remove(b, urls);
            }
        }
        this.countCache.clear();
    }

    /**
     * remove url references from a selected word hash. this deletes also in the BLOB
     * files, which means that there exists new gap entries after the deletion
     * The gaps are never merged in place, but can be eliminated when BLOBs are merged into
     * new BLOBs. This returns the sum of all url references that have been removed
     * @throws IOException
     */
    @Override
    public int remove(final byte[] termHash, final HandleSet urlHashes) throws IOException {
        this.countCache.remove(termHash);
        final int removed = this.ram.remove(termHash, urlHashes);
        int reduced;
        //final long am = this.array.mem();
        try {
            reduced = this.array.reduce(termHash, new RemoveReducer<ReferenceType>(urlHashes));
        } catch (final SpaceExceededException e) {
            reduced = 0;
            ConcurrentLog.warn("IndexCell", "not possible to remove urlHashes from a RWI because of too low memory. Remove was not applied. Please increase RAM assignment");
        }
        //assert this.array.mem() <= am : "am = " + am + ", array.mem() = " + this.array.mem();
        return removed + (reduced / this.array.rowdef().objectsize);
    }

    @Override
    public boolean remove(final byte[] termHash, final byte[] urlHashBytes) throws IOException {
        this.countCache.remove(termHash);
        final boolean removed = this.ram.remove(termHash, urlHashBytes);
        int reduced;
        //final long am = this.array.mem();
        try {
            reduced = this.array.reduce(termHash, new RemoveReducer<ReferenceType>(urlHashBytes));
        } catch (final SpaceExceededException e) {
            reduced = 0;
            ConcurrentLog.warn("IndexCell", "not possible to remove urlHashes from a RWI because of too low memory. Remove was not applied. Please increase RAM assignment");
        }
        //assert this.array.mem() <= am : "am = " + am + ", array.mem() = " + this.array.mem();
        return removed || (reduced > 0);
    }

    private static class RemoveReducer<ReferenceType extends Reference> implements ReferenceContainerArray.ContainerReducer<ReferenceType> {

        HandleSet urlHashes;

        public RemoveReducer(final HandleSet urlHashes) {
            this.urlHashes = urlHashes;
        }

        public RemoveReducer(final byte[] urlHashBytes) {
            this.urlHashes = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 0);
            try {
                this.urlHashes.put(urlHashBytes);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
        }

        @Override
        public ReferenceContainer<ReferenceType> reduce(final ReferenceContainer<ReferenceType> container) {
            container.sort();
            container.removeEntries(this.urlHashes);
            return container;
        }

    }

    @Override
    public Iterator<ReferenceContainer<ReferenceType>> iterator() {
        return referenceContainerIterator(null, false, false);
    }

    @Override
    public CloneableIterator<Rating<byte[]>> referenceCountIterator(final byte[] starttermHash, final boolean rot, final boolean excludePrivate) {
        return this.array.referenceCountIterator(starttermHash, false, excludePrivate);
    }

    @Override
    public CloneableIterator<ReferenceContainer<ReferenceType>> referenceContainerIterator(final byte[] startTermHash, final boolean rot, final boolean excludePrivate) {
        return referenceContainerIterator(startTermHash, rot, excludePrivate, false);
    }

    @Override
    public CloneableIterator<ReferenceContainer<ReferenceType>> referenceContainerIterator(final byte[] startTermHash, final boolean rot, final boolean excludePrivate, final boolean ram) {
        final Order<ReferenceContainer<ReferenceType>> containerOrder = new ReferenceContainerOrder<ReferenceType>(this.factory, this.ram.rowdef().getOrdering().clone());
        containerOrder.rotate(new ReferenceContainer<ReferenceType>(this.factory, startTermHash));
        if (ram) {
            return this.ram.referenceContainerIterator(startTermHash, rot, excludePrivate);
        }
        return new MergeIterator<ReferenceContainer<ReferenceType>>(
            this.ram.referenceContainerIterator(startTermHash, rot, excludePrivate),
            new MergeIterator<ReferenceContainer<ReferenceType>>(
                this.ram.referenceContainerIterator(startTermHash, false, excludePrivate),
                this.array.referenceContainerIterator(startTermHash, false, excludePrivate),
                containerOrder,
                ReferenceContainer.containerMergeMethod,
                true),
            containerOrder,
            ReferenceContainer.containerMergeMethod,
            true);
    }

    /**
     * clear the RAM and BLOB part, deletes everything in the cell
     * @throws IOException
     */
    @Override
    public synchronized void clear() throws IOException {
        this.countCache.clear();
        this.removeDelayedURLs.clear();
        this.ram.clear();
        this.array.clear();
        if (Switchboard.getSwitchboard() != null &&
                Switchboard.getSwitchboard().peers != null &&
                Switchboard.getSwitchboard().peers.mySeed() != null) Switchboard.getSwitchboard().peers.mySeed().resetCounters();
    }
    
    public synchronized void clearCache() {
        this.countCache.clear();
    }

    /**
     * when a cell is closed, the current RAM is dumped to a file which will be opened as
     * BLOB file the next time a cell is opened. A name for the dump is automatically generated
     * and is composed of the current date and the cell salt
     */
    @Override
    public synchronized void close() {
        this.countCache.clear();
        try {removeDelayed();} catch (final IOException e) {}
        if (!this.ram.isEmpty()) this.ram.dump(this.array.newContainerBLOBFile(), (int) Math.min(MemoryControl.available() / 3, this.writeBufferSize), true);
        // close all
        this.flushShallRun = false;
        if (this.flushThread != null) try { this.flushThread.join(); } catch (final InterruptedException e) {}
        this.merger.terminate();
        this.ram.close();
        this.array.close();
    }

    public boolean isEmpty() {
        if (this.ram.size() > 0) return false;
        for (int s: this.array.sizes()) if (s > 0) return false;
        return true;
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("an accumulated size of index entries would not reflect the real number of words, which cannot be computed easily");
        //int size = this.ram.size();
        //for (int s: this.array.sizes()) size += s;
        //return size;
    }

    private int[] sizes() {
        final int[] as = this.array.sizes();
        final int[] asr = new int[as.length + 1];
        System.arraycopy(as, 0, asr, 0, as.length);
        asr[as.length] = this.ram.size();
        return asr;
    }

    public int sizesMax() {
        int m = 0;
        final int[] s = sizes();
        for (final int element : s)
            if (element > m) m = element;
        return m;
    }

    public int getSegmentCount() {
        return this.array.entries();
    }

    @Override
    public int minMem() {
        return 10 * 1024 * 1024;
    }

    @Override
    public ByteOrder termKeyOrdering() {
        return this.array.ordering();
    }

    @Override
    public long getBufferMaxAge() {
        return System.currentTimeMillis();
    }

    @Override
    public int getBufferMaxReferences() {
        return this.ram.maxReferences();
    }

    @Override
    public long getBufferMinAge() {
        return System.currentTimeMillis();
    }

    @Override
    public int getBufferSize() {
        return this.ram.size();
    }

    @Override
    public long getBufferSizeBytes() {
        return this.ram.usedMemory();
    }

    @Override
    public void setBufferMaxWordCount(final int maxWords) {
        this.maxRamEntries = maxWords;
    }

}
