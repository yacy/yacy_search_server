// ArrayStack.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.08.2008 on http://yacy.net
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

package net.yacy.kelondro.blob;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceFactory;
import net.yacy.kelondro.rwi.ReferenceIterator;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.MergeIterator;
import net.yacy.kelondro.util.NamePrefixThreadFactory;


public class ArrayStack implements BLOB {

    /*
     * This class implements a BLOB using a set of Heap objects
     * In addition to a Heap this BLOB can delete large amounts of data using a given time limit.
     * This is realized by creating separate BLOB files. New Files are created when either
     * - a given time limit is reached
     * - a given space limit is reached
     * To organize such an array of BLOB files, the following file name structure is used:
     * <BLOB-Name>/<YYYYMMDDhhmm>.blob
     * That means all BLOB files are inside a directory that has the name of the BLOBArray.
     * To delete content that is out-dated, one special method is implemented that deletes content by a given
     * time-out. Deletions are not made automatically, they must be triggered using this method.
     */

    private static final long maxFileSize = Integer.MAX_VALUE;
    public  static final long oneMonth    = 1000L * 60L * 60L * 24L * 365L / 12L;

    private       int            keylength;
    private       ByteOrder      ordering;
    private final File           heapLocation;
    private       long           fileAgeLimit;
    private       long           fileSizeLimit;
    private       long           repositoryAgeMax;
    private       long           repositorySizeMax;
    private       List<blobItem> blobs;
    private final String         prefix;
    private final int            buffersize;
    private final boolean        trimall;

    // the thread pool for the keeperOf executor service
    private final ExecutorService executor;

    // use our own formatter to prevent concurrency locks with other processes
    private final static GenericFormatter my_SHORT_MILSEC_FORMATTER  = new GenericFormatter(GenericFormatter.FORMAT_SHORT_MILSEC, 1);


    public ArrayStack(
            final File heapLocation,
            final String prefix,
            final ByteOrder ordering,
            final int keylength,
            final int buffersize,
            final boolean trimall,
            final boolean deleteonfail) throws IOException {
        this.keylength = keylength;
        this.prefix = prefix;
        this.ordering = ordering;
        this.buffersize = buffersize;
        this.heapLocation = heapLocation;
        this.fileAgeLimit = oneMonth;
        this.fileSizeLimit = maxFileSize;
        this.repositoryAgeMax = Long.MAX_VALUE;
        this.repositorySizeMax = Long.MAX_VALUE;
        this.trimall = trimall;

        // init the thread pool for the keeperOf executor service
        this.executor = new ThreadPoolExecutor(
        		1,
        		Runtime.getRuntime().availableProcessors(), 100,
        		TimeUnit.MILLISECONDS,
        		new LinkedBlockingQueue<Runnable>(),
        		new NamePrefixThreadFactory(this.prefix));

        // check existence of the heap directory
        if (heapLocation.exists()) {
            if (!heapLocation.isDirectory()) throw new IOException("the BLOBArray directory " + heapLocation.toString() + " does not exist (is blocked by a file with same name)");
        } else {
            if(!heapLocation.mkdirs()) throw new IOException("the BLOBArray directory " + heapLocation.toString() + " does not exist (can not be created)");
        }

        // register all blob files inside this directory
        String[] files = heapLocation.list();
        final HashSet<String> fh = new HashSet<String>();
        for (final String file : files)
            fh.add(file);
        // delete unused temporary files
        boolean deletions = false;
        for (final String file : files) {
            if (file.endsWith(".tmp") || file.endsWith(".prt")) {
                FileUtils.deletedelete(new File(heapLocation, file));
                deletions = true;
            }
            if (file.endsWith(".idx") || file.endsWith(".gap")) {
                final String s = file.substring(0, file.length() - 17);
                if (!fh.contains(s)) {
                    FileUtils.deletedelete(new File(heapLocation, file));
                    deletions = true;
                }
            }
        }
        if (deletions) files = heapLocation.list(); // make a fresh list
        // migrate old file names
        Date d;
        long time;
        deletions = false;
        for (final String file : files) {
            if (file.length() >= 19 && file.endsWith(".blob")) {
               final File f = new File(heapLocation, file);
               if (f.length() == 0) {
                   f.delete();
                   deletions = true;
               } else try {
                   d = GenericFormatter.SHORT_SECOND_FORMATTER.parse(file.substring(0, 14));
                   f.renameTo(newBLOB(d));
                   deletions = true;
               } catch (final ParseException e) {continue;}
            }
        }
        if (deletions) files = heapLocation.list(); // make a fresh list

        // find maximum time: the file with this time will be given a write buffer
        final TreeMap<Long, blobItem> sortedItems = new TreeMap<Long, blobItem>();
        BLOB oneBlob;
        File f;
        long maxtime = 0;
        for (final String file : files) {
            if (file.length() >= 22 && file.charAt(this.prefix.length()) == '.' && file.endsWith(".blob")) {
               try {
                   d = my_SHORT_MILSEC_FORMATTER.parse(file.substring(this.prefix.length() + 1, this.prefix.length() + 18));
                   time = d.getTime();
                   if (time > maxtime) maxtime = time;
               } catch (final ParseException e) {continue;}
            }
        }

        // open all blob files
        for (final String file : files) {
            if (file.length() >= 22 && file.charAt(this.prefix.length()) == '.' && file.endsWith(".blob")) {
                try {
                   d = my_SHORT_MILSEC_FORMATTER.parse(file.substring(this.prefix.length() + 1, this.prefix.length() + 18));
                   f = new File(heapLocation, file);
                   time = d.getTime();
                   try {
                       if (time == maxtime && !trimall) {
                           oneBlob = new Heap(f, keylength, ordering, buffersize);
                       } else {
                           oneBlob = new HeapModifier(f, keylength, ordering);
                           oneBlob.optimize(); // no writings here, can be used with minimum memory
                       }
                       sortedItems.put(Long.valueOf(time), new blobItem(d, f, oneBlob));
                   } catch (final IOException e) {
                       if (deleteonfail) {
                           ConcurrentLog.warn("ArrayStack", "cannot read file " + f.getName() + ", deleting it (smart fail; alternative would be: crash; required user action would be same as deletion)");
                           f.delete();
                       } else {
                           throw new IOException(e.getMessage(), e);
                       }
                   }
               } catch (final ParseException e) {continue;}
            }
        }

        // read the blob tree in a sorted way and write them into an array
        this.blobs = new CopyOnWriteArrayList<blobItem>();
        for (final blobItem bi : sortedItems.values()) {
            this.blobs.add(bi);
        }
    }

    @Override
    public long mem() {
        long m = 0;
        if (this.blobs != null) for (final blobItem b: this.blobs) m += b.blob.mem();
        return m;
    }

    @Override
    public void optimize() {
        // trim shall not be called for ArrayStacks because the characteristics of an ArrayStack is that the 'topmost' BLOB on the stack
        // is used for write operations and all other shall be trimmed automatically since they are not used for writing. And the
        // topmost BLOB must not be trimmed to support fast writings.
        throw new UnsupportedOperationException();
    }

    /**
     * add a blob file to the array.
     * note that this file must be generated with a file name from newBLOB()
     * @param location
     * @throws IOException
     */
    public synchronized void mountBLOB(final File location, final boolean full) throws IOException {
        Date d;
        try {
            d = my_SHORT_MILSEC_FORMATTER.parse(location.getName().substring(this.prefix.length() + 1, this.prefix.length() + 18));
        } catch (final ParseException e) {
            throw new IOException("date parse problem with file " + location.toString() + ": " + e.getMessage());
        }
        BLOB oneBlob;
        if (full && this.buffersize > 0 && !this.trimall) {
            oneBlob = new Heap(location, this.keylength, this.ordering, this.buffersize);
        } else {
            oneBlob = new HeapModifier(location, this.keylength, this.ordering);
            oneBlob.optimize();
        }
        this.blobs.add(new blobItem(d, location, oneBlob));
    }

    private synchronized void unmountBLOB(final File location, final boolean writeIDX) {
        blobItem b;
        for (int i = 0; i < this.blobs.size(); i++) {
            b = this.blobs.get(i);
            if (b.location.getAbsolutePath().equals(location.getAbsolutePath())) {
                this.blobs.remove(i);
                b.blob.close(writeIDX);
                b.blob = null;
                b.location = null;
                return;
            }
        }
        ConcurrentLog.severe("BLOBArray", "file " + location + " cannot be unmounted. The file " + ((location.exists()) ? "exists." : "does not exist."));
    }

    private File unmount(final int idx) {
        final blobItem b = this.blobs.remove(idx);
        b.blob.close(false);
        b.blob = null;
        final File f = b.location;
        b.location = null;
        return f;
    }

    public synchronized File[] unmountBestMatch(final float maxq, long maxResultSize) {
    	if (this.blobs.size() < 2) return null;
        long l, r, m;
        File lf, rf;
        float min = Float.MAX_VALUE;
        final File[] bestMatch = new File[2];
        maxResultSize = maxResultSize >> 1;
    	int loopcount = 0;
        mainloop: for (int i = 0; i < this.blobs.size() - 1; i++) {
            for (int j = i + 1; j < this.blobs.size(); j++) {
                loopcount++;
            	lf = this.blobs.get(i).location;
            	rf = this.blobs.get(j).location;
            	m = this.blobs.get(i).blob.mem();
            	m += this.blobs.get(j).blob.mem();
                l = 1 + (lf.length() >> 1);
                r = 1 + (rf.length() >> 1);
                if (l + r > maxResultSize) continue;
                if (!MemoryControl.request(m, true)) continue;
                final float q = Math.max((float) l, (float) r) / Math.min((float) l, (float) r);
                if (q < min) {
                    min = q;
                    bestMatch[0] = lf;
                    bestMatch[1] = rf;
                }
                if (loopcount > 1000 && min <= maxq && min != Float.MAX_VALUE) break mainloop;
            }
        }
        if (min > maxq) return null;
        unmountBLOB(bestMatch[1], false);
        unmountBLOB(bestMatch[0], false);
        return bestMatch;
    }

    public synchronized File unmountOldest() {
        if (this.blobs.isEmpty()) return null;
        if (System.currentTimeMillis() - this.blobs.get(0).creation.getTime() < this.fileAgeLimit) return null;
        final File f = this.blobs.get(0).location;
        unmountBLOB(f, false);
        return f;
    }

    public synchronized File[] unmountSmallest(final long maxResultSize) {
    	if (this.blobs.size() < 2) return null;
    	final File f0 = smallestBLOB(null, maxResultSize);
        if (f0 == null) return null;
        final File f1 = smallestBLOB(f0, maxResultSize - f0.length());
        if (f1 == null) return null;

        unmountBLOB(f0, false);
        unmountBLOB(f1, false);
        return new File[]{f0, f1};
    }

    private synchronized File smallestBLOB(final File excluding, final long maxsize) {
        if (this.blobs.isEmpty()) return null;
        File bestFile = null;
        long smallest = Long.MAX_VALUE;
        File f = null;
        for (int i = 0; i < this.blobs.size(); i++) {
        	f = this.blobs.get(i).location;
            if (excluding != null && f.getAbsolutePath().equals(excluding.getAbsolutePath())) continue;
            if (f.length() < smallest) {
                smallest = f.length();
                bestFile = f;
            }
            if (i > 70 && smallest <= maxsize && smallest != Long.MAX_VALUE) break;
        }
        if (smallest > maxsize) return null;
        return bestFile;
    }

    public synchronized File unmountOldestBLOB(final boolean smallestFromFirst2) {
        if (this.blobs.isEmpty()) return null;
        int idx = 0;
        if (smallestFromFirst2 && this.blobs.get(1).location.length() < this.blobs.get(0).location.length()) idx = 1;
        return unmount(idx);
    }

    /**
     * return the number of BLOB files in this array
     * @return
     */
    public synchronized int entries() {
        return (this.blobs == null) ? 0 : this.blobs.size();
    }

    /**
     * generate a new BLOB file name with a given date.
     * This method is needed to generate a file name that matches to the name structure that is needed for parts of the array
     * @param creation
     * @return
     */
    public synchronized File newBLOB(final Date creation) {
        //return new File(heapLocation, DateFormatter.formatShortSecond(creation) + "." + blobSalt + ".blob");
        return new File(this.heapLocation, this.prefix + "." + my_SHORT_MILSEC_FORMATTER.format(creation) + ".blob");
    }

    @Override
    public String name() {
        return this.heapLocation.getName();
    }

    public void setMaxAge(final long maxAge) {
        this.repositoryAgeMax = maxAge;
        this.fileAgeLimit = Math.min(oneMonth, maxAge / 10);
    }

    public void setMaxSize(final long maxSize) {
        this.repositorySizeMax = maxSize;
        this.fileSizeLimit = Math.min(maxFileSize, maxSize / 100L);
        executeLimits();
    }

    private void executeLimits() {
        // check if storage limits are reached and execute consequences
        if (this.blobs.isEmpty()) return;

        // age limit:
        while (!this.blobs.isEmpty() && System.currentTimeMillis() - this.blobs.get(0).creation.getTime() - this.fileAgeLimit > this.repositoryAgeMax) {
            // too old
            final blobItem oldestBLOB = this.blobs.remove(0);
            oldestBLOB.blob.close(false);
            oldestBLOB.blob = null;
            FileUtils.deletedelete(oldestBLOB.location);
        }

        // size limit
        while (!this.blobs.isEmpty() && length() > this.repositorySizeMax) {
            // too large
            final blobItem oldestBLOB = this.blobs.remove(0);
            oldestBLOB.blob.close(false);
            FileUtils.deletedelete(oldestBLOB.location);
        }
    }

    /*
     * return the size of the repository (in bytes)
     */
    @Override
    public synchronized long length() {
        long s = 0;
        for (int i = 0; i < this.blobs.size(); i++) s += this.blobs.get(i).location.length();
        return s;
    }

    @Override
    public ByteOrder ordering() {
        return this.ordering;
    }

    private class blobItem {
        Date creation;
        File location;
        BLOB blob;
        public blobItem(final Date creation, final File location, final BLOB blob) {
            assert blob != null;
            this.creation = creation;
            this.location = location;
            this.blob = blob;
        }
        public blobItem(final int buffer) throws IOException {
            // make a new blob file and assign it in this item
            this.creation = new Date();
            this.location = newBLOB(this.creation);
            this.blob = (buffer == 0) ? new HeapModifier(this.location, ArrayStack.this.keylength, ArrayStack.this.ordering) : new Heap(this.location, ArrayStack.this.keylength, ArrayStack.this.ordering, buffer);
        }
    }

    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    @Override
    public int keylength() {
        return this.keylength;
    }

    /**
     * clears the content of the database
     * @throws IOException
     */
    @Override
    public synchronized void clear() throws IOException {
        for (final blobItem bi: this.blobs) {
            bi.blob.clear();
            bi.blob.close(false);
            HeapWriter.delete(bi.location);
        }
        this.blobs.clear();
    }

    /**
     * ask for the number of blob entries
     * @return the number of entries in the table
     */
    @Override
    public synchronized int size() {
        int s = 0;
        for (final blobItem bi: this.blobs) s += bi.blob.size();
        return s;
    }

    @Override
    public synchronized boolean isEmpty() {
        for (final blobItem bi: this.blobs) if (!bi.blob.isEmpty()) return false;
        return true;
    }

    /**
     * ask for the number of blob entries in each blob of the blob array
     * @return the number of entries in each blob
     */
    public synchronized int[] sizes() {
        if (this.blobs == null) return new int[0];
        final int[] s = new int[this.blobs.size()];
        int c = 0;
        for (final blobItem bi: this.blobs) s[c++] = bi.blob.size();
        return s;
    }

    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    @Override
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        assert rotating == false;
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(this.blobs.size());
        final Iterator<blobItem> i = this.blobs.iterator();
        while (i.hasNext()) {
            c.add(i.next().blob.keys(up, rotating));
        }
        return MergeIterator.cascade(c, this.ordering, MergeIterator.simpleMerge, up);
    }

    /**
     * iterate over all keys
     * @param up
     * @param firstKey
     * @return
     * @throws IOException
     */
    @Override
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(this.blobs.size());
        final Iterator<blobItem> i = this.blobs.iterator();
        while (i.hasNext()) {
            c.add(i.next().blob.keys(up, firstKey));
        }
        return MergeIterator.cascade(c, this.ordering, MergeIterator.simpleMerge, up);
    }

    /**
     * check if a specific key is in the database
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    @Override
    public synchronized boolean containsKey(final byte[] key) {
    	final blobItem bi = keeperOf(key);
    	return bi != null;
        //for (blobItem bi: blobs) if (bi.blob.has(key)) return true;
        //return false;
    }

    /**
     * find the blobItem that holds the key
     * if no blobItem is found, then return null
     * @param key
     * @return the blobItem that holds the key or null if no blobItem is found
     */
    private blobItem keeperOf(final byte[] key) {
        if (this.blobs.isEmpty()) return null;
        if (this.blobs.size() == 1) {
            final blobItem bi = this.blobs.get(0);
            if (bi.blob.containsKey(key)) return bi;
            return null;
        }

        // first check the current blob only because that has most probably the key if any has that key
        int bs1 = this.blobs.size() - 1;
        blobItem bi = this.blobs.get(bs1);
        if (bi.blob.containsKey(key)) return bi;
        if (this.blobs.size() == 2) {
            // this should not be done concurrently
            bi = this.blobs.get(0);
            if (bi.blob.containsKey(key)) return bi;
            return null;
        }

        // start a concurrent query to database tables
        final CompletionService<blobItem> cs = new ExecutorCompletionService<blobItem>(this.executor);
        int accepted = 0;
        for (int i = 0; i < bs1; i++) {
            final blobItem b = this.blobs.get(i);
            try {
                cs.submit(new Callable<blobItem>() {
                    @Override
                    public blobItem call() {
                        if (b.blob.containsKey(key)) return b;
                        return null;
                    }
                });
                accepted++;
            } catch (final RejectedExecutionException e) {
                // the executor is either shutting down or the blocking queue is full
                // execute the search direct here without concurrency
                if (b.blob.containsKey(key)) return b;
            }
        }

        // read the result
        try {
            for (int i = 0; i < accepted; i++) {
                final Future<blobItem> f = cs.take();
                //hash(System.out.println("**********accepted = " + accepted + ", i =" + i);
                if (f == null) continue;
                final blobItem index = f.get();
                if (index != null) {
                    //System.out.println("*DEBUG SplitTable success.time = " + (System.currentTimeMillis() - start) + " ms");
                	return index;
                }
            }
            //System.out.println("*DEBUG SplitTable fail.time = " + (System.currentTimeMillis() - start) + " ms");
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            ConcurrentLog.severe("ArrayStack", "", e);
            throw new RuntimeException(e.getCause());
        }
        //System.out.println("*DEBUG SplitTable fail.time = " + (System.currentTimeMillis() - start) + " ms");
        return null;
    }

    /**
     * retrieve the whole BLOB from the table
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    @Override
    public byte[] get(final byte[] key) throws IOException, SpaceExceededException {
        if (this.blobs == null || this.blobs.isEmpty()) return null;
        if (this.blobs.size() == 1) {
            final blobItem bi = this.blobs.get(0);
            return bi.blob.get(key);
        }

        final blobItem bi = keeperOf(key);
    	return (bi == null) ? null : bi.blob.get(key);

    	/*
    	byte[] b;
        for (blobItem bi: blobs) {
            b = bi.blob.get(key);
            if (b != null) return b;
        }
        return null;
        */
    }

    @Override
    public byte[] get(final Object key) {
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

    /**
     * get all BLOBs in the array.
     * this is useful when it is not clear if an entry is unique in all BLOBs in this array.
     * @param key
     * @return
     * @throws IOException
     */
    public Iterable<byte[]> getAll(final byte[] key) throws IOException {
        return new BlobValues(key);
    }

    private class BlobValues extends LookAheadIterator<byte[]> {

        private final Iterator<blobItem> bii;
        private final byte[] key;

        public BlobValues(final byte[] key) {
            this.bii = ArrayStack.this.blobs.iterator();
            this.key = key;
        }

        @Override
        protected byte[] next0() {
            while (this.bii.hasNext()) {
                final BLOB b = this.bii.next().blob;
                if (b == null) continue;
                try {
                    final byte[] n = b.get(this.key);
                    if (n != null) return n;
                } catch (final IOException e) {
                    ConcurrentLog.severe("ArrayStack", "BlobValues - IOException: " + e.getMessage(), e);
                    return null;
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.severe("ArrayStack", "BlobValues - RowSpaceExceededException: " + e.getMessage(), e);
                    break;
                }
            }
            return null;
        }
    }

    /**
     * retrieve the size of the BLOB
     * @param key
     * @return the size of the BLOB or -1 if the BLOB does not exist
     * @throws IOException
     */
    @Override
    public synchronized long length(final byte[] key) throws IOException {
        long l;
        for (final blobItem bi: this.blobs) {
            l = bi.blob.length(key);
            if (l >= 0) return l;
        }
        return -1;
    }

    /**
     * get all BLOBs in the array.
     * this is useful when it is not clear if an entry is unique in all BLOBs in this array.
     * @param key
     * @return
     * @throws IOException
     */
    public Iterable<Long> lengthAll(final byte[] key) throws IOException {
        return new BlobLengths(key);
    }

    private class BlobLengths extends LookAheadIterator<Long> {

        private final Iterator<blobItem> bii;
        private final byte[] key;

        public BlobLengths(final byte[] key) {
            this.bii = ArrayStack.this.blobs.iterator();
            this.key = key;
        }

        @Override
        protected Long next0() {
            while (this.bii.hasNext()) {
                final BLOB b = this.bii.next().blob;
                if (b == null) continue;
                try {
                    final long l = b.length(this.key);
                    if (l >= 0) return Long.valueOf(l);
                } catch (final IOException e) {
                    ConcurrentLog.severe("ArrayStack", "", e);
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * retrieve the sizes of all BLOB
     * @param key
     * @return the size of the BLOB or -1 if the BLOB does not exist
     * @throws IOException
     */
    public synchronized long lengthAdd(final byte[] key) throws IOException {
        long l = 0;
        for (final blobItem bi: this.blobs) {
            l += bi.blob.length(key);
        }
        return l;
    }

    /**
     * write a whole byte array as BLOB to the table
     * @param key  the primary key
     * @param b
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public synchronized void insert(final byte[] key, final byte[] b) throws IOException {
        blobItem bi = (this.blobs.isEmpty()) ? null : this.blobs.get(this.blobs.size() - 1);
        /*
        if (bi == null)
            System.out.println("bi == null");
        else if (System.currentTimeMillis() - bi.creation.getTime() > this.fileAgeLimit)
            System.out.println("System.currentTimeMillis() - bi.creation.getTime() > this.maxage");
        else if (bi.location.length() > this.fileSizeLimit)
            System.out.println("bi.location.length() > this.maxsize");
        */
        if ((bi == null) || (System.currentTimeMillis() - bi.creation.getTime() > this.fileAgeLimit) || (bi.location.length() > this.fileSizeLimit && this.fileSizeLimit >= 0)) {
            // add a new blob to the array
            bi = new blobItem(this.buffersize);
            this.blobs.add(bi);
        }
        assert bi.blob instanceof Heap;
        bi.blob.insert(key, b);
        executeLimits();
    }

    /**
     * replace a BLOB entry with another
     * @param key  the primary key
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public synchronized int replace(final byte[] key, final Rewriter rewriter) throws IOException, SpaceExceededException {
        int d = 0;
        for (final blobItem bi: this.blobs) {
            d += bi.blob.replace(key, rewriter);
        }
        return d;
    }

    /**
     * replace a BLOB entry with another which must be smaller or same size
     * @param key  the primary key
     * @throws IOException
     * @throws SpaceExceededException
     */
    @Override
    public synchronized int reduce(final byte[] key, final Reducer reduce) throws IOException, SpaceExceededException {
        int d = 0;
        for (final blobItem bi: this.blobs) {
            d += bi.blob.reduce(key, reduce);
        }
        return d;
    }

    /**
     * delete a BLOB
     * @param key the primary key
     * @throws IOException
     */
    @Override
    public synchronized void delete(final byte[] key) throws IOException {
        final long m = mem();
        if (this.blobs.isEmpty()) {
            // do nothing
        } else if (this.blobs.size() == 1) {
            final blobItem bi = this.blobs.get(0);
            bi.blob.delete(key);
        } else {
            @SuppressWarnings("unchecked")
            final FutureTask<Boolean>[] t = new FutureTask[this.blobs.size() - 1];
            int i = 0;
            for (final blobItem bi: this.blobs) {
                if (i < t.length) {
                    // run this in a concurrent thread
                    final blobItem bi0 = bi;
                    t[i] = new FutureTask<Boolean>(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            try { bi0.blob.delete(key); } catch (final IOException e) {}
                            return true;
                        }
                    });
                    DELETE_EXECUTOR.execute(t[i]);
                } else {
                    // no additional thread, run in this thread
                    try { bi.blob.delete(key); } catch (final IOException e) {}
                }
                i++;
            }
            // wait for termination
            for (final FutureTask<Boolean> s: t) try {s.get();} catch (final InterruptedException e) {} catch (final ExecutionException e) {}
        }
        assert mem() <= m : "m = " + m + ", mem() = " + mem();
    }

    private static final ExecutorService DELETE_EXECUTOR = Executors.newFixedThreadPool(128);

    /**
     * close the BLOB
     */
    @Override
    public synchronized void close(final boolean writeIDX) {
        for (final blobItem bi: this.blobs) bi.blob.close(writeIDX);
        this.blobs.clear();
        this.blobs = null;
    }

    /**
     * merge two blob files into one. If the second file is given as null,
     * then the first file is only rewritten into a new one.
     * @param f1
     * @param f2 (may also be null)
     * @param factory
     * @param payloadrow
     * @param newFile
     * @param writeBuffer
     * @return the target file where the given files are merged in
     */
    public File mergeMount(final File f1, final File f2,
            final ReferenceFactory<? extends Reference> factory,
            final File newFile, final int writeBuffer) {
        if (f2 == null) {
            // this is a rewrite
            ConcurrentLog.info("BLOBArray", "rewrite of " + f1.getName());
            final File resultFile = rewriteWorker(factory, this.keylength, this.ordering, f1, newFile, writeBuffer);
            if (resultFile == null) {
                ConcurrentLog.warn("BLOBArray", "rewrite of file " + f1 + " returned null. newFile = " + newFile);
                return null;
            }
            try {
                mountBLOB(resultFile, false);
            } catch (final IOException e) {
                ConcurrentLog.warn("BLOBArray", "rewrite of file " + f1 + " successfull, but read failed. resultFile = " + resultFile);
                return null;
            }
            ConcurrentLog.info("BLOBArray", "rewrite of " + f1.getName() + " into " + resultFile);
            return resultFile;
        }
        ConcurrentLog.info("BLOBArray", "merging " + f1.getName() + " with " + f2.getName());
        final File resultFile = mergeWorker(factory, this.keylength, this.ordering, f1, f2, newFile, writeBuffer);
        if (resultFile == null) {
            ConcurrentLog.warn("BLOBArray", "merge of files " + f1 + ", " + f2 + " returned null. newFile = " + newFile);
            return null;
        }
        try {
            mountBLOB(resultFile, false);
        } catch (final IOException e) {
            ConcurrentLog.warn("BLOBArray", "merge of files " + f1 + ", " + f2 + " successfull, but read failed. resultFile = " + resultFile);
            return null;
        }
        ConcurrentLog.info("BLOBArray", "merged " + f1.getName() + " with " + f2.getName() + " into " + resultFile);
        return resultFile;
    }

    private static <ReferenceType extends Reference> File mergeWorker(
                    final ReferenceFactory<ReferenceType> factory,
                    final int keylength, final ByteOrder order, final File f1, final File f2, final File newFile, final int writeBuffer) {
        // iterate both files and write a new one
        ReferenceIterator<ReferenceType> i1 = null;
        try {
            i1 = new ReferenceIterator<ReferenceType>(f1, factory);
            ReferenceIterator<ReferenceType> i2 = null;
            try {
                i2 = new ReferenceIterator<ReferenceType>(f2, factory);
                if (!i1.hasNext()) {
                    if (i2.hasNext()) {
                        HeapWriter.delete(f1);
                        if (f2.renameTo(newFile)) return newFile;
                        return f2;
                    }
                    HeapWriter.delete(f1);
                    HeapWriter.delete(f2);
                    return null;
                } else if (!i2.hasNext()) {
                    HeapWriter.delete(f2);
                    if (f1.renameTo(newFile)) return newFile;
                    return f1;
                }
                assert i1.hasNext();
                assert i2.hasNext();
                final File tmpFile = new File(newFile.getParentFile(), newFile.getName() + ".prt");
                try {
                    final HeapWriter writer = new HeapWriter(tmpFile, newFile, keylength, order, writeBuffer);
                    merge(i1, i2, order, writer);
                    writer.close(true);
                } catch (final IOException e) {
                    ConcurrentLog.severe("ArrayStack", "cannot writing or close writing merge, newFile = " + newFile.toString() + ", tmpFile = " + tmpFile.toString() + ": " + e.getMessage(), e);
                    HeapWriter.delete(tmpFile);
                    HeapWriter.delete(newFile);
                    return null;
                } catch (final SpaceExceededException e) {
                    ConcurrentLog.severe("ArrayStack", "cannot merge because of memory failure: " + e.getMessage(), e);
                    HeapWriter.delete(tmpFile);
                    HeapWriter.delete(newFile);
                    return null;
                }
                // we don't need the old files any more
                HeapWriter.delete(f1);
                HeapWriter.delete(f2);
                return newFile;
            } catch (final IOException e) {
                ConcurrentLog.severe("ArrayStack", "cannot merge because input files cannot be read, f2 = " + f2.toString() + ": " + e.getMessage(), e);
                return null;
            } finally {
                if (i2 != null) i2.close();
            }
        } catch (final IOException e) {
            ConcurrentLog.severe("ArrayStack", "cannot merge because input files cannot be read, f1 = " + f1.toString() + ": " + e.getMessage(), e);
            return null;
        } finally {
            if (i1 != null) i1.close();
        }
    }

    private static <ReferenceType extends Reference> File rewriteWorker(
            final ReferenceFactory<ReferenceType> factory,
            final int keylength, final ByteOrder order, final File f, final File newFile, final int writeBuffer) {
        // iterate both files and write a new one

        CloneableIterator<ReferenceContainer<ReferenceType>> i = null;
        try {
            i = new ReferenceIterator<ReferenceType>(f, factory);
        } catch (final IOException e) {
            ConcurrentLog.severe("ArrayStack", "cannot rewrite because input file cannot be read, f = " + f.toString() + ": " + e.getMessage(), e);
            return null;
        }
        if (!i.hasNext()) {
            FileUtils.deletedelete(f);
            return null;
        }
        assert i.hasNext();
        final File tmpFile = new File(newFile.getParentFile(), newFile.getName() + ".prt");
        try {
            final HeapWriter writer = new HeapWriter(tmpFile, newFile, keylength, order, writeBuffer);
            rewrite(i, order, writer);
            writer.close(true);
            i.close();
        } catch (final IOException e) {
            ConcurrentLog.severe("ArrayStack", "cannot writing or close writing rewrite, newFile = " + newFile.toString() + ", tmpFile = " + tmpFile.toString() + ": " + e.getMessage(), e);
            FileUtils.deletedelete(tmpFile);
            FileUtils.deletedelete(newFile);
            return null;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.severe("ArrayStack", "cannot rewrite because of memory failure: " + e.getMessage(), e);
            FileUtils.deletedelete(tmpFile);
            FileUtils.deletedelete(newFile);
            return null;
        }
        // we don't need the old files any more
        FileUtils.deletedelete(f);
        return newFile;
    }

    private static <ReferenceType extends Reference> void merge(
            final CloneableIterator<ReferenceContainer<ReferenceType>> i1,
            final CloneableIterator<ReferenceContainer<ReferenceType>> i2,
            final ByteOrder ordering, final HeapWriter writer) throws IOException, SpaceExceededException {
        assert i1.hasNext();
        assert i2.hasNext();
        byte[] c1lh, c2lh;
        ReferenceContainer<ReferenceType> c1, c2;
        c1 = i1.next();
        c2 = i2.next();
        int e, s;
        while (true) {
            assert c1 != null;
            assert c2 != null;
            e = ordering.compare(c1.getTermHash(), c2.getTermHash());
            if (e < 0) {
            	s = c1.shrinkReferences();
            	if (s > 0) ConcurrentLog.info("ArrayStack", "shrinking index for " + ASCII.String(c1.getTermHash()) + " by " + s + " to " + c1.size() + " entries");
                writer.add(c1.getTermHash(), c1.exportCollection());
                if (i1.hasNext()) {
                    c1lh = c1.getTermHash();
                    c1 = i1.next();
                    assert ordering.compare(c1.getTermHash(), c1lh) > 0;
                    continue;
                }
                c1 = null;
                break;
            }
            if (e > 0) {
                s = c2.shrinkReferences();
                if (s > 0) ConcurrentLog.info("ArrayStack", "shrinking index for " + ASCII.String(c2.getTermHash()) + " by " + s + " to " + c2.size() + " entries");
                writer.add(c2.getTermHash(), c2.exportCollection());
                if (i2.hasNext()) {
                    c2lh = c2.getTermHash();
                    c2 = i2.next();
                    assert ordering.compare(c2.getTermHash(), c2lh) > 0;
                    continue;
                }
                c2 = null;
                break;
            }
            assert e == 0;
            // merge the entries
            c1 = c1.merge(c2);
            s = c1.shrinkReferences();
            if (s > 0) ConcurrentLog.info("ArrayStack", "shrinking index for " + ASCII.String(c1.getTermHash()) + " by " + s + " to " + c1.size() + " entries");
            writer.add(c1.getTermHash(), c1.exportCollection());
            c1lh = c1.getTermHash();
            c2lh = c2.getTermHash();
            if (i1.hasNext() && i2.hasNext()) {
                c1 = i1.next();
                assert ordering.compare(c1.getTermHash(), c1lh) > 0;
                c2 = i2.next();
                assert ordering.compare(c2.getTermHash(), c2lh) > 0;
                continue;
            }
            c1 = null;
            c2 = null;
            if (i1.hasNext()) {
                c1 = i1.next();
                assert ordering.compare(c1.getTermHash(), c1lh) > 0;
            }
            if (i2.hasNext()) {
                c2 = i2.next();
                assert ordering.compare(c2.getTermHash(), c2lh) > 0;
            }
            break;

        }
        // catch up remaining entries
        assert !(i1.hasNext() && i2.hasNext());
        assert (c1 == null) || (c2 == null);
        while (c1 != null) {
            //System.out.println("FLUSH REMAINING 1: " + c1.getWordHash());
            s = c1.shrinkReferences();
            if (s > 0) ConcurrentLog.info("ArrayStack", "shrinking index for " + ASCII.String(c1.getTermHash()) + " by " + s + " to " + c1.size() + " entries");
            writer.add(c1.getTermHash(), c1.exportCollection());
            if (i1.hasNext()) {
                c1lh = c1.getTermHash();
                c1 = i1.next();
                assert ordering.compare(c1.getTermHash(), c1lh) > 0;
            } else {
                c1 = null;
            }
        }
        while (c2 != null) {
            //System.out.println("FLUSH REMAINING 2: " + c2.getWordHash());
            s = c2.shrinkReferences();
            if (s > 0) ConcurrentLog.info("ArrayStack", "shrinking index for " + ASCII.String(c2.getTermHash()) + " by " + s + " to " + c2.size() + " entries");
            writer.add(c2.getTermHash(), c2.exportCollection());
            if (i2.hasNext()) {
                c2lh = c2.getTermHash();
                c2 = i2.next();
                assert ordering.compare(c2.getTermHash(), c2lh) > 0;
            } else {
                c2 = null;
            }
        }
        // finished with writing
    }

    private static <ReferenceType extends Reference> void rewrite(
            final CloneableIterator<ReferenceContainer<ReferenceType>> i,
            final ByteOrder ordering, final HeapWriter writer) throws IOException, SpaceExceededException {
        assert i.hasNext();
        byte[] clh;
        ReferenceContainer<ReferenceType> c;
        c = i.next();
        int s;
        while (true) {
            assert c != null;
            s = c.shrinkReferences();
            if (s > 0) ConcurrentLog.info("ArrayStack", "shrinking index for " + ASCII.String(c.getTermHash()) + " by " + s + " to " + c.size() + " entries");
            writer.add(c.getTermHash(), c.exportCollection());
            if (i.hasNext()) {
                clh = c.getTermHash();
                c = i.next();
                assert ordering.compare(c.getTermHash(), clh) > 0;
                continue;
            }
            break;
        }
        // finished with writing
    }

    public static void main(final String[] args) {
        final File f = new File("/Users/admin/blobarraytest");
        try {
            //f.delete();
            final ArrayStack heap = new ArrayStack(f, "test", NaturalOrder.naturalOrder, 12, 512 * 1024, false, true);
            heap.insert("aaaaaaaaaaaa".getBytes(), "eins zwei drei".getBytes());
            heap.insert("aaaaaaaaaaab".getBytes(), "vier fuenf sechs".getBytes());
            heap.insert("aaaaaaaaaaac".getBytes(), "sieben acht neun".getBytes());
            heap.insert("aaaaaaaaaaad".getBytes(), "zehn elf zwoelf".getBytes());
            // iterate over keys
            final Iterator<byte[]> i = heap.keys(true, false);
            while (i.hasNext()) {
                System.out.println("key_b: " + UTF8.String(i.next()));
            }
            heap.delete("aaaaaaaaaaab".getBytes());
            heap.delete("aaaaaaaaaaac".getBytes());
            heap.insert("aaaaaaaaaaaX".getBytes(), "WXYZ".getBytes());
            heap.close(true);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }

}
