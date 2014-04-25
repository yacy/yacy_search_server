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
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.RowHandleMap;
import net.yacy.kelondro.io.CachedFileWriter;
import net.yacy.kelondro.io.Writer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.RotateIterator;


public class HeapReader {

    //public final static long keepFreeMem = 20 * 1024 * 1024;

	private final static ConcurrentLog log = new ConcurrentLog("HeapReader");

    // input values
    protected int                keylength;  // the length of the primary key
    protected File               heapFile;   // the file of the heap
    protected final ByteOrder    ordering;   // the ordering on keys

    // computed values
    protected Writer             file;       // a random access to the file
    protected HandleMap          index;      // key/seek relation for used records
    protected Gap                free;       // set of {seek, size} pairs denoting space and position of free records
    private   File               fingerprintFileIdx, fingerprintFileGap; // files with dumped indexes. Will be deleted if file is written
    private   Date               closeDate;  // records a time when the file was closed; used for debugging

    public HeapReader(
            final File heapFile,
            final int keylength,
            final ByteOrder ordering) throws IOException {
        this.ordering = ordering;
        this.heapFile = heapFile;
        this.keylength = keylength;
        this.index = null; // will be created as result of initialization process
        this.free = null; // will be initialized later depending on existing idx/gap file
        this.heapFile.getParentFile().mkdirs();
        this.file = new CachedFileWriter(this.heapFile);
        this.closeDate = null;

        // read or initialize the index
        this.fingerprintFileIdx = null;
        this.fingerprintFileGap = null;
        if (initIndexReadDump()) {
            // verify that everything worked just fine
            // pick some elements of the index
            Iterator<byte[]> i = this.index.keys(true, null);
            int c = 3;
            byte[] b, b1 = new byte[this.keylength];
            long pos;
            boolean ok = true;
            while (i.hasNext() && c-- > 0) {
                b = i.next();
                pos = this.index.get(b);
                this.file.seek(pos + 4);
                this.file.readFully(b1, 0, b1.length);
                if (!this.ordering.equal(b, b1)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                log.warn("verification of idx file for " + heapFile.toString() + " failed, re-building index");
                initIndexReadFromHeap();
            } else {
                log.info("using a dump of the index of " + heapFile.toString() + ".");
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
    }

    public long mem() {
        return this.index.mem(); // don't add the memory for free here since then the asserts for memory management don't work
    }

    public void optimize() {
        this.index.optimize();
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
            log.severe("cannot generate a fingerprint for " + this.heapFile + ": null");
            return false;
        }
        this.fingerprintFileIdx = HeapWriter.fingerprintIndexFile(this.heapFile, fingerprint);
        if (!this.fingerprintFileIdx.exists()) this.fingerprintFileIdx = new File(this.fingerprintFileIdx.getAbsolutePath() + ".gz");
        this.fingerprintFileGap = HeapWriter.fingerprintGapFile(this.heapFile, fingerprint);
        if (!this.fingerprintFileGap.exists()) this.fingerprintFileGap = new File(this.fingerprintFileGap.getAbsolutePath() + ".gz");
        if (!this.fingerprintFileIdx.exists() || !this.fingerprintFileGap.exists()) {
            deleteAllFingerprints(this.heapFile, this.fingerprintFileIdx.getName(), this.fingerprintFileGap.getName());
            return false;
        }

        // there is an index and a gap file:
        // read the index file:
        try {
            this.index = new RowHandleMap(this.keylength, this.ordering, 8, this.fingerprintFileIdx);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return false;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return false;
        }

        // check saturation
        if (this.index instanceof RowHandleMap) {
        int[] saturation = ((RowHandleMap) this.index).saturation(); // {<the maximum length of consecutive equal-beginning bytes in the key>, <the minimum number of leading zeros in the second column>}
        log.info("saturation of " + this.fingerprintFileIdx.getName() + ": keylength = " + saturation[0] + ", vallength = " + saturation[1] + ", size = " + this.index.size() +
                    ", maximum saving for index-compression = " + (saturation[0] * this.index.size() / 1024 / 1024) + " MB" +
                    ", exact saving for value-compression = " + (saturation[1] * this.index.size() / 1024 / 1024) + " MB");
        }

        // read the gap file:
        try {
            this.free = new Gap(this.fingerprintFileGap);
        } catch (final IOException e) {
        	ConcurrentLog.logException(e);
            return false;
        }

        // everything is fine now
        return !this.index.isEmpty();
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
        log.info("generating index for " + this.heapFile.toString() + ", " + (this.file.length() / 1024 / 1024) + " MB. Please wait.");

        this.free = new Gap();
        RowHandleMap.initDataConsumer indexready = RowHandleMap.asynchronusInitializer(this.name() + ".initializer", this.keylength, this.ordering, 8, Math.max(10, (int) (Runtime.getRuntime().freeMemory() / (10 * 1024 * 1024))));
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
                    log.severe("reclen == 0 at seek pos " + seek + " in file " + this.heapFile);
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
                    indexready.consume(key, seek);
                    key = new byte[this.keylength];
                } else {
                    // free the lost space
                    this.free.put(seek, reclen);
                    this.file.seek(seek + 4);
                    Arrays.fill(key, (byte) 0);
                    this.file.write(key); // mark the place as empty record
                    log.warn("BLOB " + this.heapFile.getName() + ": skiped not wellformed key " + UTF8.String(key) + " at seek pos " + seek);
                }
            }
            // new seek position
            seek += 4L + reclen;
        }
        }
        indexready.finish();

        // finish the index generation
        try {
            this.index = indexready.result();
        } catch (final InterruptedException e) {
        	ConcurrentLog.logException(e);
        } catch (final ExecutionException e) {
        	ConcurrentLog.logException(e);
        }
        log.info("finished index generation for " + this.heapFile.toString() + ", " + this.index.size() + " entries, " + this.free.size() + " gaps.");
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
            log.info("BLOB " + this.heapFile.toString() + ": merged " + merged + " free records");
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
            log.severe("this.index == null in size(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return 0;
        }
        return (this.index == null) ? 0 : this.index.size();
    }

    public boolean isEmpty() {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("this.index == null in isEmpty(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            log.severe("this.index == null in containsKey(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            log.severe("this.index == null in firstKey(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            log.severe("this.index == null in first(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            log.severe("this.index == null in lastKey(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            log.severe("this.index == null in last(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            log.severe("this.index == null in get(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
                log.severe("file " + this.file.file() + " corrupted at " + pos + ": negative len. len = " + len + ", pk.len = " + this.keylength);
                // to get lazy over that problem (who wants to tell the user to stop operation and delete the file???) we work on like the entry does not exist
                this.index.remove(key);
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
                log.severe("indexed verification access failed for " + this.heapFile.toString());
                // this is a severe operation, it should never happen.
                // remove entry from index because keeping that element in the index would not make sense
                this.index.remove(key);
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
            log.severe("this.index == null in length(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
     */
    public void close(boolean writeIDX) {
        if (this.index == null) return;
        synchronized (this.index) {
            if (this.file != null)
    			try {
    				this.file.close();
    			} catch (final IOException e) {
    				ConcurrentLog.logException(e);
    			}
            this.file = null;
            if (writeIDX && this.index != null && this.free != null && (this.index.size() > 3 || this.free.size() > 3)) {
                // now we can create a dump of the index and the gap information
                // to speed up the next start
                try {
                    String fingerprint = fingerprintFileHash(this.heapFile);
                    if (fingerprint == null) {
                        log.severe("cannot write a dump for " + this.heapFile.getName()+ ": fingerprint is null");
                    } else {
                        File newFingerprintFileGap = HeapWriter.fingerprintGapFile(this.heapFile, fingerprint);
                        if (this.fingerprintFileGap != null &&
                            this.fingerprintFileGap.getName().equals(newFingerprintFileGap.getName()) &&
                            this.fingerprintFileGap.exists()) {
                            log.info("using existing gap dump instead of writing a new one: " + this.fingerprintFileGap.getName());
                        } else {
                            long start = System.currentTimeMillis();
                            this.free.dump(newFingerprintFileGap);
                            log.info("wrote a dump for the " + this.free.size() +  " gap entries of " + this.heapFile.getName()+ " in " + (System.currentTimeMillis() - start) + " milliseconds.");
                        }
                    }
                    this.free.clear();
                    this.free = null;
                    if (fingerprint != null) {
                        File newFingerprintFileIdx = HeapWriter.fingerprintIndexFile(this.heapFile, fingerprint);
                        if (this.fingerprintFileIdx != null &&
                            this.fingerprintFileIdx.getName().equals(newFingerprintFileIdx.getName()) &&
                            this.fingerprintFileIdx.exists()) {
                            log.info("using existing idx dump instead of writing a new one: " + this.fingerprintFileIdx.getName());
                        } else {
                            long start = System.currentTimeMillis();
                            this.index.dump(newFingerprintFileIdx);
                            log.info("wrote a dump for the " + this.index.size() +  " index entries of " + this.heapFile.getName()+ " in " + (System.currentTimeMillis() - start) + " milliseconds.");
                        }
                    }
                    this.index.close();
                    this.index = null;
                } catch (final IOException e) {
                	ConcurrentLog.logException(e);
                }
            }
            if (this.free != null) this.free.clear();
            this.free = null;
            if (this.index != null) this.index.close();
            this.index = null;
            this.closeDate = new Date();
            log.info("close HeapFile " + this.heapFile.getName() + "; trace: " + ConcurrentLog.stackTrace());
        }
    }

    public synchronized void close() {
        close(true);
    }

    @Override
    public void finalize() {
        this.close();
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
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("this.index == null in keys(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            log.severe("this.index == null in keys(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
            return null;
        }
        synchronized (this.index) {
            return this.index.keys(up, firstKey);
        }
    }

    public long length() {
        assert (this.index != null) : "index == null; closeDate=" + this.closeDate + ", now=" + new Date();
        if (this.index == null) {
            log.severe("this.index == null in length(); closeDate=" + this.closeDate + ", now=" + new Date() + this.heapFile == null ? "" : (" file = " + this.heapFile.toString()));
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
            try {
                this.is = new DataInputStream(new BufferedInputStream(new FileInputStream(blobFile), 256 * 1024));
            } catch (final OutOfMemoryError e) {
                this.is = new DataInputStream(new FileInputStream(blobFile));
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
	                            log.warn("problem skiping " +  + len + " bytes in " + this.blobFile.getName());
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
                        log.severe("out of memory in LookAheadIterator.next0", ee);
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
