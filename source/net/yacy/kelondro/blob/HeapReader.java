// HeapReader.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.12.2008 on http://yacy.net
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

package net.yacy.kelondro.blob;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.Digest;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.HandleMap;
import net.yacy.cora.storage.ImmutableHandleMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.RowHandleMap;
import net.yacy.kelondro.index.SSTableHandleMap;
import net.yacy.kelondro.io.CachedFileWriter;
import net.yacy.kelondro.io.Writer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.RotateIterator;


public abstract class HeapReader<I extends ImmutableHandleMap> {
    //public final static long keepFreeMem = 20 * 1024 * 1024;

	private final static ConcurrentLog log = new ConcurrentLog("KELONDRO");

    private final HeapIndexFactory<I> indexFactory;

    // input values
    protected int                keylength;  // the length of the primary key
    protected File               heapFile;   // the file of the heap
    protected final ByteOrder    ordering;   // the ordering on keys

    // computed values
    protected Writer             file;       // a random access to the file
    protected I                  index;      // key/seek relation for used records
    protected Gap                free;       // set of {seek, size} pairs denoting space and position of free records
    private   File               fingerprintFileIdx, fingerprintFileGap; // files with dumped indexes. Will be deleted if file is written
    private   Date               closeDate;  // records a time when the file was closed; used for debugging


    interface HeapIndexFactory<I extends ImmutableHandleMap> {
        I load(
                int keylength,
                ByteOrder ordering,
                File dump)
                throws IOException, SpaceExceededException;

        HeapIndexBuilder<I> newBuilder(
                File heapFile,
                int keylength,
                ByteOrder ordering,
                int expectedspace) throws IOException;

        void prepareForHeapMutation(I index) throws IOException;
    }

    interface HeapIndexBuilder<I extends ImmutableHandleMap> extends AutoCloseable {
        void consume(byte[] key, long offset) throws IOException;

        I finish() throws IOException;

        @Override
        void close() throws IOException;
    }

    @FunctionalInterface
    interface HeapFileFactory {
        Writer open(File heapFile) throws IOException;
    }

    static HeapIndexFactory<HandleMap> mutable() {
        return new HeapIndexFactory<HandleMap>() {
            @Override
            public HandleMap load(
                    final int keylength,
                    final ByteOrder ordering,
                    final File dump)
                    throws IOException, SpaceExceededException {
                return new RowHandleMap(keylength, ordering, 8, dump);
            }

            @Override
            public HeapIndexBuilder<HandleMap> newBuilder(
                    final File heapFile,
                    final int keylength,
                    final ByteOrder ordering,
                    final int expectedspace) {
                return new HeapIndexBuilder<HandleMap>() {
                    private RowHandleMap.initDataConsumer building =
                            RowHandleMap.asynchronusInitializer(
                                    heapFile.getName() + ".initializer",
                                    keylength, ordering, 8, expectedspace);
                    private boolean finishRequested = false;

                    @Override
                    public void consume(final byte[] key, final long offset) {
                        if (this.building == null) throw new IllegalStateException("index builder is closed");
                        this.building.consume(key, offset);
                    }

                    @Override
                    public HandleMap finish() throws IOException {
                        if (this.building == null) throw new IllegalStateException("index builder is closed");
                        requestFinish();
                        final RowHandleMap result = awaitResult();
                        this.building = null;
                        return result;
                    }

                    @Override
                    public void close() throws IOException {
                        if (this.building == null) return;
                        requestFinish();
                        try {
                            awaitResultForClose();
                        } finally {
                            this.building.close();
                            this.building = null;
                        }
                    }

                    private void requestFinish() {
                        if (this.finishRequested) return;
                        this.building.finish();
                        this.finishRequested = true;
                    }

                    private RowHandleMap awaitResult() throws IOException {
                        try {
                            return this.building.result();
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while rebuilding mutable heap index", e);
                        } catch (final ExecutionException e) {
                            throw new IOException("Cannot rebuild mutable heap index", e.getCause());
                        }
                    }

                    private RowHandleMap awaitResultForClose() throws IOException {
                        boolean interrupted = false;
                        try {
                            while (true) {
                                try {
                                    return this.building.result();
                                } catch (final InterruptedException e) {
                                    interrupted = true;
                                } catch (final ExecutionException e) {
                                    throw new IOException("Cannot rebuild mutable heap index", e.getCause());
                                }
                            }
                        } finally {
                            if (interrupted) Thread.currentThread().interrupt();
                        }
                    }
                };
            }

            @Override
            public void prepareForHeapMutation(final HandleMap index) {
                /* RowHandleMap has no mapped fingerprint backing to detach. */
            }
        };
    }

    /**
     * Index strategy for heaps which never add or replace keys.
     *
     * <p>The concrete generic type is intentional: this is not merely a factory
     * returning some {@link ImmutableHandleMap}. Both loading a fingerprint and
     * rebuilding from the heap are guaranteed to produce an
     * {@link SSTableHandleMap}. A future accidental RowHandleMap fallback would
     * therefore fail at compile time instead of silently restoring the RAM index.</p>
     */
    static HeapIndexFactory<SSTableHandleMap> immutable() {
        return new HeapIndexFactory<SSTableHandleMap>() {
            @Override
            public SSTableHandleMap load(
                    final int keylength,
                    final ByteOrder ordering,
                    final File dump)
                    throws IOException, SpaceExceededException {
                /*
                 * Fingerprint dumps are published snapshots. SSTable removals write
                 * negative offsets, so the dump must never be mapped writable itself.
                 * openWorkingCopy() maps it read-only and promotes it to an owned
                 * temporary table only when the heap or index is first mutated.
                 */
                return SSTableHandleMap.openWorkingCopy(
                        keylength, ordering, 8, dump);
            }

            @Override
            public HeapIndexBuilder<SSTableHandleMap> newBuilder(
                    final File heapFile,
                    final int keylength,
                    final ByteOrder ordering,
                    final int expectedspace) throws IOException {
                final SSTableHandleMap.Builder builder = new SSTableHandleMap.Builder(
                        keylength, ordering, 8, heapFile.getParentFile(), heapFile.getName());
                return new HeapIndexBuilder<SSTableHandleMap>() {
                    @Override
                    public void consume(final byte[] key, final long offset) throws IOException {
                        builder.consume(key, offset);
                    }

                    @Override
                    public SSTableHandleMap finish() throws IOException {
                        return builder.finish();
                    }

                    @Override
                    public void close() throws IOException {
                        builder.close();
                    }
                };
            }

            @Override
            public void prepareForHeapMutation(final SSTableHandleMap index)
                    throws IOException {
                index.prepareForMutation();
            }
        };
    }

    protected HeapReader(
            final File heapFile,
            final int keylength,
            final ByteOrder ordering,
            final HeapIndexFactory<I> indexFactory) throws IOException {
        this(heapFile, keylength, ordering, indexFactory, CachedFileWriter::new);
    }

    /** Constructor variant for an alternate heap-file ownership implementation. */
    protected HeapReader(
            final File heapFile,
            final int keylength,
            final ByteOrder ordering,
            final HeapIndexFactory<I> indexFactory,
            final HeapFileFactory heapFileFactory) throws IOException {
        if (indexFactory == null) throw new IllegalArgumentException("indexFactory must not be null");
        if (heapFileFactory == null) throw new IllegalArgumentException("heapFileFactory must not be null");
        this.indexFactory = indexFactory;
        this.ordering = ordering;
        this.heapFile = heapFile;
        this.keylength = keylength;
        this.index = null; // will be created as result of initialization process
        this.free = null; // will be initialized later depending on existing idx/gap file
        this.heapFile.getParentFile().mkdirs();
        this.file = heapFileFactory.open(this.heapFile);
        this.closeDate = null;

        try {
            // read or initialize the index
            this.fingerprintFileIdx = null;
            this.fingerprintFileGap = null;
            if (initIndexReadDump()) {
                if (!verifyLoadedIndex()) {
                    log.warn("HeapReader: verification of idx file for " + heapFile.toString() + " failed, re-building index");
                    /*
                     * Close the working index before invalidating its source fingerprint.
                     * This matters for mapped files on Windows and also guarantees that a
                     * rejected dump cannot leave a temporary SSTable behind.
                     */
                    discardIndexState();
                    deleteFingerprint();
                    initIndexReadFromHeap();
                } else {
                    log.info("HeapReader: using a dump of the index of " + heapFile.toString() + ".");
                }
            } else {
                // if we did not have a dump, create a new index
                initIndexReadFromHeap();
            }

            // merge gaps that follow directly
            mergeFreeEntries();

            // after the initial initialization of the heap, we close the file again
            // to make more room to file pointers which may run out if the number
            // of file descriptors is too low and the number of files is too high
            this.file.close();
            // the file will be opened again automatically when the next access to it comes.
        } catch (final IOException | RuntimeException | Error failure) {
            cleanupFailedInitialization(failure);
            throw failure;
        }
    }

    /** Release every resource acquired by a constructor which cannot return. */
    private void cleanupFailedInitialization(final Throwable failure) {
        if (this.index != null) {
            try {
                this.index.close();
            } catch (final Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                this.index = null;
            }
        }
        if (this.free != null) {
            try {
                this.free.clear();
            } catch (final Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                this.free = null;
            }
        }
        if (this.file != null) {
            try {
                this.file.close();
            } catch (final Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                this.file = null;
            }
        }
        this.closeDate = new Date();
    }

    /**
     * Verify a small sample before the loaded fingerprint becomes normal runtime state.
     *
     * <p>The index and gap dump form a cache, while the heap file remains the source of
     * truth. A key mismatch, an invalid offset, or a short heap read therefore rejects
     * the complete cached generation and lets the constructor rebuild it from the heap.
     * The iterator is closed here so this validation phase owns all resources it opens.</p>
     */
    private boolean verifyLoadedIndex() {
        final CloneableIterator<byte[]> keys = this.index.keys(true, null);
        try {
            int remaining = 3;
            final byte[] storedKey = new byte[this.keylength];
            while (keys.hasNext() && remaining-- > 0) {
                final byte[] indexedKey = keys.next();
                final long position = this.index.get(indexedKey);
                this.file.seek(position + 4);
                this.file.readFully(storedKey, 0, storedKey.length);
                if (!this.ordering.equal(indexedKey, storedKey)) return false;
            }
            return true;
        } catch (final IOException | RuntimeException e) {
            log.warn("HeapReader: cannot verify loaded index for " + this.heapFile, e);
            return false;
        } finally {
            keys.close();
        }
    }

    public long mem() {
        return this.index.mem(); // don't add the memory for free here since then the asserts for memory management don't work
    }

    public void optimize() {
        this.index.optimize();
    }

    /** Detach a mapped fingerprint before changing the heap generation. */
    protected final void prepareIndexForHeapMutation() throws IOException {
        this.indexFactory.prepareForHeapMutation(this.index);
    }

    protected byte[] normalizeKey(byte[] key) {
        // check size of key: zero-filled keys are only possible of the ordering is
        // an instance of the natural ordering. Base64-orderings cannot use zeros in keys.
        assert key.length >= this.keylength || this.ordering instanceof NaturalOrder;
        return normalizeKey(key, this.keylength);
    }

    private static final byte zero = 0;

    protected static byte[] normalizeKey(byte[] key, int keylength) {
        if (key.length == keylength) return key;
        byte[] k = new byte[keylength];
        if (key.length < keylength) {
            System.arraycopy(key, 0, k, 0, key.length);
            for (int i = key.length; i < keylength; i++) k[i] = zero;
        } else {
            System.arraycopy(key, 0, k, 0, keylength);
        }
        return k;
    }

    private boolean initIndexReadDump() {
        // look for an index dump and read it if it exist
        // if this is successful, return true; otherwise false
        String fingerprint = fingerprintFileHash(this.heapFile);
        if (fingerprint == null) {
            log.severe("HeapReader: cannot generate a fingerprint for " + this.heapFile + ": null");
            return false;
        }
        this.fingerprintFileIdx = HeapWriter.fingerprintIndexFile(this.heapFile, fingerprint);
        if (!this.fingerprintFileIdx.exists()) this.fingerprintFileIdx = new File(this.fingerprintFileIdx.getAbsolutePath() + ".gz");
        this.fingerprintFileGap = HeapWriter.fingerprintGapFile(this.heapFile, fingerprint);
        if (!this.fingerprintFileGap.exists()) this.fingerprintFileGap = new File(this.fingerprintFileGap.getAbsolutePath() + ".gz");
        if (!this.fingerprintFileIdx.exists() || !this.fingerprintFileGap.exists()) {
            deleteAllFingerprints(this.heapFile, this.fingerprintFileIdx.getName(), this.fingerprintFileGap.getName());
            /* An incomplete pair must not be mistaken for a publishable snapshot. */
            deleteFingerprint();
            return false;
        }

        /*
         * Load both parts transactionally. In the immutable mode indexFactory.load()
         * maps the published fingerprint read-only; it creates a working copy only
         * on a later mutation. The loaded index must be closed immediately when
         * loading the gap file or validating the pair fails; assigning fields early
         * would leak that mapping when the heap scan starts its replacement builder.
         */
        I loadedIndex = null;
        try {
            loadedIndex = this.indexFactory.load(
                    this.keylength, this.ordering, this.fingerprintFileIdx);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            deleteFingerprint();
            return false;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            deleteFingerprint();
            return false;
        }

        // check saturation
        if (loadedIndex instanceof RowHandleMap) {
            final int[] saturation = ((RowHandleMap) loadedIndex).saturation(); // {<the maximum length of consecutive equal-beginning bytes in the key>, <the minimum number of leading zeros in the second column>}
            log.info("HeapReader: saturation of " + this.fingerprintFileIdx.getName() + ": keylength = " + saturation[0] + ", vallength = " + saturation[1] + ", size = " + loadedIndex.size() +
                        ", maximum saving for index-compression = " + (saturation[0] * loadedIndex.size() / 1024 / 1024) + " MB" +
                        ", exact saving for value-compression = " + (saturation[1] * loadedIndex.size() / 1024 / 1024) + " MB");
        }

        final Gap loadedFree;
        try {
            loadedFree = new Gap(this.fingerprintFileGap);
        } catch (final IOException e) {
            loadedIndex.close();
            ConcurrentLog.logException(e);
            deleteFingerprint();
            return false;
        }

        if (loadedIndex.isEmpty() && this.heapFile.length() > 0) {
            loadedIndex.close();
            loadedFree.clear();
            deleteFingerprint();
            return false;
        }

        this.index = loadedIndex;
        this.free = loadedFree;
        return true;
    }

    private void discardIndexState() {
        if (this.index != null) {
            this.index.close();
            this.index = null;
        }
        if (this.free != null) {
            this.free.clear();
            this.free = null;
        }
    }

    /**
     * deletion of the fingerprint: this should happen if the heap is written or entries are deleted
     * if the files are not deleted then it may be possible that they are not used anyway because the
     * fingerprint hash does not fit with the heap dump file hash. But since the hash is not computed
     * from all the data and just some key bytes it may be possible that the hash did not change.
     */
    public void deleteFingerprint() {
        if (this.fingerprintFileIdx != null) {
            FileUtils.deletedelete(this.fingerprintFileIdx);
            this.fingerprintFileIdx = null;
        }
        if (this.fingerprintFileGap != null) {
            FileUtils.deletedelete(this.fingerprintFileGap);
            this.fingerprintFileGap = null;
        }
    }

    protected static String fingerprintFileHash(File f) {
        assert f != null;
        assert f.exists() : "file = " + f.toString();
        String fp = Digest.fastFingerprintB64(f, false);
        assert fp != null : "file = " + f.toString();
        if (fp == null) return null;
        return fp.substring(0, 12);
    }

    private static void deleteAllFingerprints(File f, String exception1, String exception2) {
        File d = f.getParentFile();
        String n = f.getName();
        String[] l = d.list();
        for (int i = 0; i < l.length; i++) {
            if (!l[i].startsWith(n)) continue;
            if (exception1 != null && l[i].equals(exception1)) continue;
            if (exception2 != null && l[i].equals(exception2)) continue;
            if (l[i].endsWith(".idx") ||
                l[i].endsWith(".gap") ||
                l[i].endsWith(".idx.gz") ||
                l[i].endsWith(".gap.gz")
               ) FileUtils.deletedelete(new File(d, l[i]));
        }
    }

    private void initIndexReadFromHeap() throws IOException {
        // this initializes the this.index object by reading positions from the heap file
        log.info("HeapReader: generating index for " + this.heapFile.toString() + ", " + (this.file.length() / 1024 / 1024) + " MB. Please wait.");

        this.free = new Gap();
        final int expectedspace = Math.max(10,
                (int) (Runtime.getRuntime().freeMemory() / (10 * 1024 * 1024)));
        try (HeapIndexBuilder<I> indexBuilder = this.indexFactory.newBuilder(
                this.heapFile, this.keylength, this.ordering, expectedspace)) {
            byte[] key = new byte[this.keylength];
            int reclen;
            long seek = 0;
            if (this.file.length() > 0) {
            loop: while (true) { // don't test available() here because this does not work for files > 2GB

                try {
                    // go to seek position
                    this.file.seek(seek);

                    // read length of the following record without the length of the record size bytes
                    reclen = this.file.readInt();
                    //assert reclen > 0 : " reclen == 0 at seek pos " + seek;
                    if (reclen == 0) {
                        // very bad file inconsistency
                        log.severe("HeapReader: reclen == 0 at seek pos " + seek + " in file " + this.heapFile);
                        this.file.setLength(seek); // delete everything else at the remaining of the file :-(
                        break loop;
                    }

                    // read key
                    this.file.readFully(key, 0, key.length);

                } catch (final IOException e) {
                    // EOF reached
                    break loop; // terminate loop
                }

                // check if this record is empty
                if (key == null || key[0] == 0) {
                    // it is an empty record, store to free list
                    if (reclen > 0) this.free.put(seek, reclen);
                } else {
                    if (this.ordering.wellformed(key)) {
                        indexBuilder.consume(key, seek);
                        key = new byte[this.keylength];
                    } else {
                        // free the lost space
                        this.free.put(seek, reclen);
                        this.file.seek(seek + 4);
                        Arrays.fill(key, (byte) 0);
                        this.file.write(key); // mark the place as empty record
                        log.warn("HeapReader: BLOB " + this.heapFile.getName() + ": skiped not wellformed key " + UTF8.String(key) + " at seek pos " + seek);
                    }
                }
                // new seek position
                seek += 4L + reclen;
            }
            }
            this.index = indexBuilder.finish();
        }
        log.info("HeapReader: finished index generation for " + this.heapFile.toString() + ", " + this.index.size() + " entries, " + this.free.size() + " gaps.");
    }

    private void mergeFreeEntries() throws IOException {

        // try to merge free entries
        if (this.free.size() > 1) {
            int merged = 0;
            Map.Entry<Long, Integer> lastFree, nextFree;
            final Iterator<Map.Entry<Long, Integer>> i = this.free.entrySet().iterator();
            lastFree = i.next();
            while (i.hasNext()) {
                nextFree = i.next();
                //System.out.println("*** DEBUG BLOB: free-seek = " + nextFree.seek + ", size = " + nextFree.size);
                // check if they follow directly
                if (lastFree.getKey() + lastFree.getValue() + 4 == nextFree.getKey()) {
                    if (merged == 0) prepareIndexForHeapMutation();
                    // merge those records
                    this.file.seek(lastFree.getKey());
                    lastFree.setValue(lastFree.getValue() + nextFree.getValue() + 4); // this updates also the free map
                    this.file.writeInt(lastFree.getValue());
                    this.file.seek(nextFree.getKey());
                    this.file.writeInt(0);
                    i.remove();
                    merged++;
                } else {
                    lastFree = nextFree;
                }
            }
            log.info("HeapReader: BLOB " + this.heapFile.toString() + ": merged " + merged + " free records");
            if (merged > 0) deleteFingerprint();
        }
    }

    public String name() {
        return this.heapFile.toString();
    }

    public File location() {
        return this.heapFile;
    }

    /**
     * the number of BLOBs in the heap
     * @return the number of BLOBs in the heap
     */
    public int size() {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in size(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return 0;
        }
        return (this.index == null) ? 0 : this.index.size();
    }

    public boolean isEmpty() {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in isEmpty(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return true;
        }
        return this.index.isEmpty();
    }

    /**
     * test if a key is in the heap file. This does not need any IO, because it uses only the ram index
     * @param key
     * @return true if the key exists, false otherwise
     */
    public boolean containsKey(byte[] key) {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in containsKey(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return false;
        }
        key = normalizeKey(key);

        synchronized (this.index) {
            // check if the file index contains the key
            return this.index.get(key) >= 0;
        }
    }

    public ByteOrder ordering() {
        return this.ordering;
    }

    /**
     * find a special key in the heap: the one with the smallest key
     * this method is useful if the entries are ordered using their keys.
     * then the key with the smallest key denotes the first entry
     * @return the smallest key in the heap
     * @throws IOException
     */
    protected synchronized byte[] firstKey() throws IOException {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in firstKey(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        synchronized (this.index) {
            return this.index.smallestKey();
        }
    }

    /**
     * find a special blob in the heap: one that has the smallest key
     * this method is useful if the entries are ordered using their keys.
     * then the key with the smallest key denotes the first entry
     * @return the entry which key is the smallest in the heap
     * @throws IOException
     */
    protected byte[] first() throws IOException, SpaceExceededException {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in first(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        synchronized (this.index) {
            byte[] key = this.index.smallestKey();
            if (key == null) return null;
            return get(key);
        }
    }

    /**
     * find a special key in the heap: the one with the largest key
     * this method is useful if the entries are ordered using their keys.
     * then the key with the largest key denotes the last entry
     * @return the largest key in the heap
     * @throws IOException
     */
    protected byte[] lastKey() throws IOException {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in lastKey(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        if (this.index == null) return null;
        synchronized (this.index) {
            return this.index.largestKey();
        }
    }

    /**
     * find a special blob in the heap: one that has the largest key
     * this method is useful if the entries are ordered using their keys.
     * then the key with the largest key denotes the last entry
     * @return the entry which key is the smallest in the heap
     * @throws IOException
     */
    protected byte[] last() throws IOException, SpaceExceededException {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in last(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        synchronized (this.index) {
            byte[] key = this.index.largestKey();
            if (key == null) return null;
            return get(key);
        }
    }

    /**
     * read a blob from the heap
     * @param key
     * @return
     * @throws IOException
     */
    public byte[] get(byte[] key) throws IOException, SpaceExceededException {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in get(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        key = normalizeKey(key);

        synchronized (this.index) {
            // check if the index contains the key
            final long pos = this.index.get(key);
            if (pos < 0) return null;

            // access the file and read the container
            this.file.seek(pos);
            final int len = this.file.readInt() - this.keylength;
            if (len < 0) {
                // database file may be corrupted and should be deleted :-((
                log.severe("HeapReader: file " + this.file.file() + " corrupted at " + pos + ": negative len. len = " + len + ", pk.len = " + this.keylength);
                // to get lazy over that problem (who wants to tell the user to stop operation and delete the file???) we work on like the entry does not exist
                this.index.remove(key);
                deleteFingerprint();
                return null;
            }
            long memr = len + this.keylength + 64;
            if (MemoryControl.available() < memr) {
                if (!MemoryControl.request(memr, true)) throw new SpaceExceededException(memr, "HeapReader.get()/check"); // not enough memory available for this blob
            }

            // read the key
            byte[] keyf;
            try {
                keyf = new byte[this.keylength];
            } catch (final OutOfMemoryError e) {
                throw new SpaceExceededException(this.keylength, "HeapReader.get()/keyf");
            }
            this.file.readFully(keyf, 0, keyf.length);
            if (!this.ordering.equal(key, keyf)) {
                // verification of the indexed access failed. we must re-read the index
                log.severe("HeapReader: indexed verification access failed for " + this.heapFile.toString());
                // this is a severe operation, it should never happen.
                // remove entry from index because keeping that element in the index would not make sense
                this.index.remove(key);
                deleteFingerprint();
                // nothing to return
                return null;
                // but if the process ends in this state, it would completely fail
                // if the index is not rebuild now at once
                //initIndexReadFromHeap();
            }

            // read the blob
            byte[] blob;
            try {
                blob = new byte[len];
            } catch (final OutOfMemoryError e) {
                // try once again after GC
                MemoryControl.gc(1000, "HeapReader.get()/blob");
                try {
                    blob = new byte[len];
                } catch (final OutOfMemoryError ee) {
                    throw new SpaceExceededException(len, "HeapReader.get()/blob");
                }
            }
            this.file.readFully(blob, 0, blob.length);

            return blob;
        }
    }

    public byte[] get(Object key) {
        if (!(key instanceof byte[])) return null;
        try {
            return get((byte[]) key);
        } catch (final IOException e) {
        	ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
        	ConcurrentLog.logException(e);
        }
        return null;
    }

    protected boolean checkKey(byte[] key, final long pos) throws IOException {
        key = normalizeKey(key);
        this.file.seek(pos);
        this.file.readInt(); // skip the size value

        // read the key
        final byte[] keyf = new byte[this.keylength];
        this.file.readFully(keyf, 0, keyf.length);
        return this.ordering.equal(key, keyf);
    }

    /**
     * retrieve the size of the BLOB. This should not be used excessively, because it depends on IO operations.
     * @param key
     * @return the size of the BLOB or -1 if the BLOB does not exist
     * @throws IOException
     */
    public long length(byte[] key) throws IOException {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in length(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return 0;
        }
        key = normalizeKey(key);

        synchronized (this.index) {
            // check if the index contains the key
            final long pos = this.index.get(key);
            if (pos < 0) return -1;

            // access the file and read the size of the container
            this.file.seek(pos);
            return this.file.readInt() - this.keylength;
        }
    }

    /**
     * close the BLOB table
     * @throws UncheckedIOException when the heap cannot be closed or its complete
     *         fingerprint pair cannot be published
     */
    public void close(boolean writeIDX) {
        final I closingIndex = this.index;
        if (closingIndex == null) return;
        Throwable failure = null;
        synchronized (closingIndex) {
            try {
                if (this.file != null) this.file.close();
                this.file = null;
                if (writeIDX && this.free != null
                        && (closingIndex.size() > 3 || this.free.size() > 3)) {
                    publishFingerprintGeneration(
                            this.heapFile, closingIndex, this.free,
                            this.fingerprintFileIdx, this.fingerprintFileGap,
                            "HeapReader");
                }
            } catch (final Throwable e) {
                failure = e;
            } finally {
                this.file = null;
                if (this.free != null) this.free.clear();
                this.free = null;
                try {
                    closingIndex.close();
                } catch (final Throwable closeFailure) {
                    if (failure == null) failure = closeFailure;
                    else failure.addSuppressed(closeFailure);
                }
                if (this.index == closingIndex) this.index = null;
                this.closeDate = new Date();
            }

            log.info("HeapReader: close HeapFile " + this.heapFile.getName());
            log.fine("trace: " + ConcurrentLog.stackTrace());
        }

        if (failure instanceof IOException) {
            final IOException ioFailure = (IOException) failure;
            log.severe("HeapReader: cannot close and publish fingerprint for "
                    + this.heapFile, ioFailure);
            throw new UncheckedIOException(
                    "Cannot close and publish fingerprint for " + this.heapFile,
                    ioFailure);
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) {
            throw new IllegalStateException("Cannot close heap " + this.heapFile, failure);
        }
    }

    /**
     * Publish one coherent cache generation. The index is installed first and the
     * gap file last; the latter acts as the commit marker because readers require
     * both files. A reported failure removes every part installed by this attempt.
     */
    static void publishFingerprintGeneration(
            final File heapFile, final ImmutableHandleMap index, final Gap free,
            final File reusableIndex, final File reusableGap,
            final String publisher) throws IOException {
        final String fingerprint = fingerprintFileHash(heapFile);
        if (fingerprint == null) {
            throw new IOException(publisher + ": cannot publish fingerprint for "
                    + heapFile + ": fingerprint is null");
        }

        final File targetIndex = HeapWriter.fingerprintIndexFile(heapFile, fingerprint);
        final File targetGap = HeapWriter.fingerprintGapFile(heapFile, fingerprint);
        final boolean reusePair = sameFingerprintFile(reusableIndex, targetIndex)
                && sameFingerprintFile(reusableGap, targetGap);
        if (reusePair) {
            log.info(publisher + ": using existing fingerprint pair for "
                    + heapFile.getName());
            return;
        }

        final long start = System.currentTimeMillis();
        boolean indexPublished = false;
        boolean gapPublished = false;
        try {
            index.dump(targetIndex);
            indexPublished = true;
            free.dump(targetGap);
            gapPublished = true;
            log.info(publisher + ": published a fingerprint with " + index.size()
                    + " index entries and " + free.size() + " gap entries for "
                    + heapFile.getName() + " in "
                    + (System.currentTimeMillis() - start) + " milliseconds.");
        } catch (final IOException | RuntimeException e) {
            final IOException failure = new IOException(
                    publisher + ": cannot publish complete fingerprint pair for "
                            + heapFile, e);
            if (gapPublished) deleteFailedPublish(targetGap, failure);
            if (indexPublished) deleteFailedPublish(targetIndex, failure);
            throw failure;
        }
    }

    private static boolean sameFingerprintFile(
            final File reusable, final File target) {
        return reusable != null && reusable.exists()
                && reusable.getAbsoluteFile().equals(target.getAbsoluteFile());
    }

    private static void deleteFailedPublish(
            final File file, final IOException failure) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (final IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    public synchronized void close() {
        close(true);
    }

    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    public int keylength() {
        return this.keylength;
    }

    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public CloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in keys(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        synchronized (this.index) {
            return new RotateIterator<byte[]>(this.index.keys(up, null), null, this.index.size());
        }
    }

    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in keys(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        synchronized (this.index) {
            return this.index.keys(up, firstKey);
        }
    }

    public long length() {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("HeapReader: this.index == null in length(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return 0;
        }
        synchronized (this.index) {
            return this.heapFile.length();
        }
    }

    /**
     * static iterator of entries in BLOBHeap files:
     * this is used to import heap dumps into a write-enabled index heap
     */
    public static class entries extends LookAheadIterator<Map.Entry<byte[], byte[]>> implements
        CloneableIterator<Map.Entry<byte[], byte[]>>,
        Iterator<Map.Entry<byte[], byte[]>>,
        Iterable<Map.Entry<byte[], byte[]>> {

        private DataInputStream is;
        int keylen;
        private final File blobFile;

        public entries(final File blobFile, final int keylen) throws IOException {
            if (!(blobFile.exists())) throw new IOException("file " + blobFile + " does not exist");
            FileInputStream fis = null;
            try {
            	fis = new FileInputStream(blobFile);
                this.is = new DataInputStream(new BufferedInputStream(fis, 256 * 1024));
            } catch (final OutOfMemoryError e) {
            	if(fis != null) {
            		/* Reuse if possible the already created FileInputStream */
            		this.is = new DataInputStream(fis);
            	} else {
            		this.is = new DataInputStream(new FileInputStream(blobFile));
            	}
            }
            this.keylen = keylen;
            this.blobFile = blobFile;
        }

        @Override
        public CloneableIterator<Entry<byte[], byte[]>> clone(Object modifier) {
            // if the entries iterator is cloned, close the file!
            if (this.is != null) try { this.is.close(); } catch (final IOException e) {}
            this.is = null;
            try {
                return new entries(this.blobFile, this.keylen);
            } catch (final IOException e) {
            	ConcurrentLog.logException(e);
                return null;
            }
        }

        @Override
        public Map.Entry<byte[], byte[]> next0() {
            if (this.is == null) return null;
            try {
                byte b;
                int len;
                byte[] payload;
                byte[] key;
                final int keylen1 = this.keylen - 1;
                while (true) {
                    len = this.is.readInt();
                    if (len == 0) continue; // rare, but possible: zero length record (takes 4 bytes)
                    b = this.is.readByte();      // read a single by te to check for empty record
                    if (b == 0) {
                        // this is empty
                        // read some more bytes to consume the empty record
                        if (len > 1) {
                        	if (len - 1 != this.is.skipBytes(len - 1)) {   // all that is remaining
	                            log.warn("HeapReader: problem skiping " +  + len + " bytes in " + this.blobFile.getName());
	                            try {this.is.close();} catch (final IOException e) {}
	                            return null;
                        	}
                        }
                        continue;
                    }
                    // we are now ahead of remaining this.keylen - 1 bytes of the key
                    key = new byte[this.keylen];
                    key[0] = b;             // the first entry that we know already
                    if (this.is.read(key, 1, keylen1) < keylen1) {
                        try {this.is.close();} catch (final IOException e) {}
                        return null; // read remaining key bytes
                    }
                    // so far we have read this.keylen - 1 + 1 = this.keylen bytes.
                    // there must be a remaining number of len - this.keylen bytes left for the BLOB
                    if (len < this.keylen) {
                        try {this.is.close();} catch (final IOException e) {}
                        return null;    // a strange case that can only happen in case of corrupted data
                    }
                    try {
                        payload = new byte[len - this.keylen]; // the remaining record entries
                        if (this.is.read(payload) < payload.length) {
                            try {this.is.close();} catch (final IOException e) {}
                            return null;
                        }
                        return new entry(key, payload);
                    } catch (final OutOfMemoryError ee) {
                        // the allocation of memory for the payload may fail
                        // this is bad because we must interrupt the iteration here but the
                        // process that uses the iteration may think that the iteraton has just been completed
                        log.severe("HeapReader: out of memory in LookAheadIterator.next0 for file " + this.blobFile.toString(), ee);
                        try {this.is.close();} catch (final IOException e) {}
                        return null;
                    }
                }
            } catch (final IOException e) {
                return null;
            }
        }

        @Override
        public synchronized void close() {
            if (this.is != null) try { this.is.close(); } catch (final IOException e) {ConcurrentLog.logException(e);}
            this.is = null;
        }
    }

    public static class entry implements Map.Entry<byte[], byte[]> {
        private final byte[] s;
        private byte[] b;

        public entry(final byte[] s, final byte[] b) {
            this.s = s;
            this.b = b;
        }

        @Override
        public byte[] getKey() {
            return this.s;
        }

        @Override
        public byte[] getValue() {
            return this.b;
        }

        @Override
        public byte[] setValue(byte[] value) {
            byte[] b1 = this.b;
            this.b = value;
            return b1;
        }
    }

    public static void main(final String args[]) {
        File f = new File(args[0]);
        try {
            entries hr = new HeapReader.entries(f, 12);
            Map.Entry<byte[], byte[]> entry;
            while (hr.hasNext()) {
                entry = hr.next();
                System.out.println(ASCII.String(entry.getKey()) + ":" + UTF8.String(entry.getValue()));
            }
        } catch (final IOException e) {
        	ConcurrentLog.logException(e);
        }

    }

}
