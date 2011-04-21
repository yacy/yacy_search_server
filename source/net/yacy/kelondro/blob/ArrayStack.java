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
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceFactory;
import net.yacy.kelondro.rwi.ReferenceIterator;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.LookAheadIterator;
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
    
    public static final long maxFileSize = Integer.MAX_VALUE;
    public static final long oneMonth    = 1000L * 60L * 60L * 24L * 365L / 12L;
    
    protected     int            keylength;
    protected     ByteOrder      ordering;
    private final File           heapLocation;
    private       long           fileAgeLimit;
    private       long           fileSizeLimit;
    private       long           repositoryAgeMax;
    private       long           repositorySizeMax;
    protected     List<blobItem> blobs;
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
            final int keylength,
            final ByteOrder ordering,
            final int buffersize,
            final boolean trimall) throws IOException {
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
        		Runtime.getRuntime().availableProcessors() + 1, 
        		Runtime.getRuntime().availableProcessors() + 1, 10, 
        		TimeUnit.SECONDS, 
        		new LinkedBlockingQueue<Runnable>(), 
        		new NamePrefixThreadFactory(prefix));
        
        // check existence of the heap directory
        if (heapLocation.exists()) {
            if (!heapLocation.isDirectory()) throw new IOException("the BLOBArray directory " + heapLocation.toString() + " does not exist (is blocked by a file with same name)");
        } else {
            if(!heapLocation.mkdirs()) throw new IOException("the BLOBArray directory " + heapLocation.toString() + " does not exist (can not be created)");
        }

        // register all blob files inside this directory
        String[] files = heapLocation.list();
        HashSet<String> fh = new HashSet<String>();
        for (int i = 0; i < files.length; i++) fh.add(files[i]);
        // delete unused temporary files
        boolean deletions = false;
        for (int i = 0; i < files.length; i++) {
            if (files[i].endsWith(".tmp") || files[i].endsWith(".prt")) {
                FileUtils.deletedelete(new File(heapLocation, files[i]));
                deletions = true;
            }
            if (files[i].endsWith(".idx") || files[i].endsWith(".gap")) {
                String s = files[i].substring(0, files[i].length() - 17);
                if (!fh.contains(s)) {
                    FileUtils.deletedelete(new File(heapLocation, files[i]));
                    deletions = true;
                }
            }
        }
        if (deletions) files = heapLocation.list(); // make a fresh list
        // migrate old file names
        Date d;
        long time;
        deletions = false;
        for (int i = 0; i < files.length; i++) {
            if (files[i].length() >= 19 && files[i].endsWith(".blob")) {
               File f = new File(heapLocation, files[i]);
               if (f.length() == 0) {
                   f.delete();
                   deletions = true;
               } else try {
                   d = GenericFormatter.SHORT_SECOND_FORMATTER.parse(files[i].substring(0, 14));
                   f.renameTo(newBLOB(d));
                   deletions = true;
               } catch (ParseException e) {continue;}
            }
        }
        if (deletions) files = heapLocation.list(); // make a fresh list
        
        // find maximum time: the file with this time will be given a write buffer
        TreeMap<Long, blobItem> sortedItems = new TreeMap<Long, blobItem>();
        BLOB oneBlob;
        File f;
        long maxtime = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i].length() >= 22 && files[i].startsWith(prefix) && files[i].endsWith(".blob")) {
               try {
                   d = my_SHORT_MILSEC_FORMATTER.parse(files[i].substring(prefix.length() + 1, prefix.length() + 18));
                   time = d.getTime();
                   if (time > maxtime) maxtime = time;
               } catch (ParseException e) {continue;}
            }
        }
        
        // open all blob files
        for (int i = 0; i < files.length; i++) {
            if (files[i].length() >= 22 && files[i].startsWith(prefix) && files[i].endsWith(".blob")) {
                try {
                   d = my_SHORT_MILSEC_FORMATTER.parse(files[i].substring(prefix.length() + 1, prefix.length() + 18));
                   f = new File(heapLocation, files[i]);
                   time = d.getTime();
                   if (time == maxtime && !trimall) {
                       oneBlob = new Heap(f, keylength, ordering, buffersize);
                   } else {
                       oneBlob = new HeapModifier(f, keylength, ordering);
                       oneBlob.trim(); // no writings here, can be used with minimum memory
                   }
                   sortedItems.put(Long.valueOf(time), new blobItem(d, f, oneBlob));
               } catch (ParseException e) {continue;}
            }
        }
        
        // read the blob tree in a sorted way and write them into an array
        blobs = new CopyOnWriteArrayList<blobItem>();
        for (blobItem bi : sortedItems.values()) {
            blobs.add(bi);
        }
    }
    
    public long mem() {
        long m = 0;
        if (this.blobs != null) for (blobItem b: this.blobs) m += b.blob.mem();
        return m;
    }
    
    public void trim() {
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
    public synchronized void mountBLOB(File location, boolean full) throws IOException {
        Date d;
        try {
            d = my_SHORT_MILSEC_FORMATTER.parse(location.getName().substring(prefix.length() + 1, prefix.length() + 18));
        } catch (ParseException e) {
            throw new IOException("date parse problem with file " + location.toString() + ": " + e.getMessage());
        }
        BLOB oneBlob;
        if (full && buffersize > 0 && !trimall) {
            oneBlob = new Heap(location, keylength, ordering, buffersize);
        } else {
            oneBlob = new HeapModifier(location, keylength, ordering);
            oneBlob.trim();
        }
        blobs.add(new blobItem(d, location, oneBlob));
    }
    
    public synchronized void unmountBLOB(File location, boolean writeIDX) {
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
        Log.logSevere("BLOBArray", "file " + location + " cannot be unmounted. The file " + ((location.exists()) ? "exists." : "does not exist."));
    }
    
    private File unmount(int idx) {
        blobItem b = this.blobs.remove(idx);
        b.blob.close(false);
        b.blob = null;
        File f = b.location;
        b.location = null;
        return f;
    }
    
    public synchronized File[] unmountBestMatch(float maxq, long maxResultSize) {
    	if (this.blobs.size() < 2) return null;
        long l, r;
        File lf, rf;
        float min = Float.MAX_VALUE;
        File[] bestMatch = new File[2];
        maxResultSize = maxResultSize >> 1;
    	int loopcount = 0;
        mainloop: for (int i = 0; i < this.blobs.size() - 1; i++) {
            for (int j = i + 1; j < this.blobs.size(); j++) {
                loopcount++;
            	lf = this.blobs.get(i).location;
            	rf = this.blobs.get(j).location;
                l = 1 + (lf.length() >> 1);
                r = 1 + (rf.length() >> 1);
                if (l + r > maxResultSize) continue;
                float q = Math.max((float) l, (float) r) / Math.min((float) l, (float) r);
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
        if (this.blobs.size() == 0) return null;
        if (System.currentTimeMillis() - this.blobs.get(0).creation.getTime() < this.fileAgeLimit) return null;
        File f = this.blobs.get(0).location;
        unmountBLOB(f, false);
        return f;
    }
    
    public synchronized File[] unmountSmallest(long maxResultSize) {
    	if (this.blobs.size() < 2) return null;
    	File f0 = smallestBLOB(null, maxResultSize);
        if (f0 == null) return null;
        File f1 = smallestBLOB(f0, maxResultSize - f0.length());
        if (f1 == null) return null;
        
        unmountBLOB(f0, false);
        unmountBLOB(f1, false);
        return new File[]{f0, f1};
    }
    
    public synchronized File unmountSmallestBLOB(long maxResultSize) {
        return smallestBLOB(null, maxResultSize);
    }
    
    public synchronized File smallestBLOB(File excluding, long maxsize) {
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
    
    public synchronized File unmountOldestBLOB(boolean smallestFromFirst2) {
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
    public synchronized File newBLOB(Date creation) {
        //return new File(heapLocation, DateFormatter.formatShortSecond(creation) + "." + blobSalt + ".blob");
        return new File(heapLocation, prefix + "." + my_SHORT_MILSEC_FORMATTER.format(creation) + ".blob");
    }
    
    public String name() {
        return this.heapLocation.getName();
    }
    
    public void setMaxAge(long maxAge) {
        this.repositoryAgeMax = maxAge;
        this.fileAgeLimit = Math.min(oneMonth, maxAge / 10);
    }
    
    public void setMaxSize(long maxSize) {
        this.repositorySizeMax = maxSize;
        this.fileSizeLimit = Math.min(maxFileSize, maxSize / 100L);
        executeLimits();
    }
    
    private void executeLimits() {
        // check if storage limits are reached and execute consequences
        if (blobs.isEmpty()) return;
        
        // age limit:
        while (!blobs.isEmpty() && System.currentTimeMillis() - blobs.get(0).creation.getTime() - this.fileAgeLimit > this.repositoryAgeMax) {
            // too old
            blobItem oldestBLOB = blobs.remove(0);
            oldestBLOB.blob.close(false);
            oldestBLOB.blob = null;
            FileUtils.deletedelete(oldestBLOB.location);
        }
        
        // size limit
        while (!blobs.isEmpty() && length() > this.repositorySizeMax) {
            // too large
            blobItem oldestBLOB = blobs.remove(0);
            oldestBLOB.blob.close(false);
            FileUtils.deletedelete(oldestBLOB.location);
        }
    }
    
    /*
     * return the size of the repository (in bytes)
     */
    public synchronized long length() {
        long s = 0;
        for (int i = 0; i < blobs.size(); i++) s += blobs.get(i).location.length();
        return s;
    }
    
    public ByteOrder ordering() {
        return this.ordering;
    }
    
    private class blobItem {
        Date creation;
        File location;
        BLOB blob;
        public blobItem(Date creation, File location, BLOB blob) {
            assert blob != null;
            this.creation = creation;
            this.location = location;
            this.blob = blob;
        }
        public blobItem(int buffer) throws IOException {
            // make a new blob file and assign it in this item
            this.creation = new Date();
            this.location = newBLOB(this.creation);
            this.blob = (buffer == 0) ? new HeapModifier(location, keylength, ordering) : new Heap(location, keylength, ordering, buffer);
        }
    }

    /**
     * ask for the length of the primary key
     * @return the length of the key
     */
    public int keylength() {
        return this.keylength;
    }
    
    /**
     * clears the content of the database
     * @throws IOException
     */
    public synchronized void clear() throws IOException {
        for (blobItem bi: blobs) {
            bi.blob.clear();
            bi.blob.close(false);
            HeapWriter.delete(bi.location);
        }
        blobs.clear();
    }
    
    /**
     * ask for the number of blob entries
     * @return the number of entries in the table
     */
    public synchronized int size() {
        int s = 0;
        for (blobItem bi: blobs) s += bi.blob.size();
        return s;
    }
    
    public synchronized boolean isEmpty() {
        for (blobItem bi: blobs) if (!bi.blob.isEmpty()) return false;
        return true;
    }
    
    /**
     * ask for the number of blob entries in each blob of the blob array
     * @return the number of entries in each blob
     */
    public synchronized int[] sizes() {
        if (blobs == null) return new int[0];
        int[] s = new int[blobs.size()];
        int c = 0;
        for (blobItem bi: blobs) s[c++] = bi.blob.size();
        return s;
    }
    
    /**
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public synchronized CloneableIterator<byte[]> keys(boolean up, boolean rotating) throws IOException {
        assert rotating == false;
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(blobs.size());
        final Iterator<blobItem> i = blobs.iterator();
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
    public synchronized CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        final List<CloneableIterator<byte[]>> c = new ArrayList<CloneableIterator<byte[]>>(blobs.size());
        final Iterator<blobItem> i = blobs.iterator();
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
    public synchronized boolean containsKey(byte[] key) {
    	blobItem bi = keeperOf(key);
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
        if (blobs.size() == 0) return null;
        if (blobs.size() == 1) {
            blobItem bi = blobs.get(0);
            if (bi.blob.containsKey(key)) return bi;
            return null;
        }
        
        // start a concurrent query to database tables
        final CompletionService<blobItem> cs = new ExecutorCompletionService<blobItem>(executor);
        int accepted = 0;
        for (final blobItem bi : blobs) {
            try {
                cs.submit(new Callable<blobItem>() {
                    public blobItem call() {
                        if (bi.blob.containsKey(key)) return bi;
                        return null;
                    }
                });
                accepted++;
            } catch (final RejectedExecutionException e) {
                // the executor is either shutting down or the blocking queue is full
                // execute the search direct here without concurrency
                if (bi.blob.containsKey(key)) return bi;
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
            Log.logSevere("ArrayStack", "", e);
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
    public synchronized byte[] get(byte[] key) throws IOException, RowSpaceExceededException {
        if (blobs.size() == 0) return null;
        if (blobs.size() == 1) {
            blobItem bi = blobs.get(0);
            return bi.blob.get(key);
        }
        
        blobItem bi = keeperOf(key);
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
    
    public byte[] get(Object key) {
        if (!(key instanceof byte[])) return null;
        try {
            return get((byte[]) key);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
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
    public Iterable<byte[]> getAll(byte[] key) throws IOException {
        return new BlobValues(key);
    }
    
    public class BlobValues extends LookAheadIterator<byte[]> {

        private final Iterator<blobItem> bii;
        private final byte[] key;
        
        public BlobValues(byte[] key) {
            this.bii = blobs.iterator();
            this.key = key;
        }
        
        protected byte[] next0() {
            while (this.bii.hasNext()) {
                BLOB b = this.bii.next().blob;
                if (b == null) continue;
                try {
                    byte[] n = b.get(key);
                    if (n != null) return n;
                } catch (IOException e) {
                    Log.logSevere("ArrayStack", "", e);
                    return null;
                } catch (RowSpaceExceededException e) {
                    continue;
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
    public synchronized long length(byte[] key) throws IOException {
        long l;
        for (blobItem bi: blobs) {
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
    public Iterable<Long> lengthAll(byte[] key) throws IOException {
        return new BlobLengths(key);
    }
    
    public class BlobLengths extends LookAheadIterator<Long> {

        private final Iterator<blobItem> bii;
        private final byte[] key;
        
        public BlobLengths(byte[] key) {
            this.bii = blobs.iterator();
            this.key = key;
        }
        
        protected Long next0() {
            while (this.bii.hasNext()) {
                BLOB b = this.bii.next().blob;
                if (b == null) continue;
                try {
                    long l = b.length(key);
                    if (l >= 0) return Long.valueOf(l);
                } catch (IOException e) {
                    Log.logSevere("ArrayStack", "", e);
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
    public synchronized long lengthAdd(byte[] key) throws IOException {
        long l = 0;
        for (blobItem bi: blobs) {
            l += bi.blob.length(key);
        }
        return l;
    }
    
    /**
     * write a whole byte array as BLOB to the table
     * @param key  the primary key
     * @param b
     * @throws IOException
     * @throws RowSpaceExceededException 
     */
    public synchronized void insert(byte[] key, byte[] b) throws IOException {
        blobItem bi = (blobs.isEmpty()) ? null : blobs.get(blobs.size() - 1);
        /*
        if (bi == null)
            System.out.println("bi == null");
        else if (System.currentTimeMillis() - bi.creation.getTime() > this.fileAgeLimit)
            System.out.println("System.currentTimeMillis() - bi.creation.getTime() > this.maxage");
        else if (bi.location.length() > this.fileSizeLimit)
            System.out.println("bi.location.length() > this.maxsize");
        */
        if ((bi == null) || (System.currentTimeMillis() - bi.creation.getTime() > this.fileAgeLimit) || (bi.location.length() > this.fileSizeLimit)) {
            // add a new blob to the array
            bi = new blobItem(buffersize);
            blobs.add(bi);
        }
        assert bi.blob instanceof Heap;
        bi.blob.insert(key, b);
        executeLimits();
    }
    
    /**
     * replace a BLOB entry with another
     * @param key  the primary key
     * @throws IOException
     * @throws RowSpaceExceededException 
     */
    public synchronized int replace(byte[] key, Rewriter rewriter) throws IOException, RowSpaceExceededException {
        int d = 0;
        for (blobItem bi: blobs) {
            d += bi.blob.replace(key, rewriter);
        }
        return d;
    }
    
    /**
     * replace a BLOB entry with another which must be smaller or same size
     * @param key  the primary key
     * @throws IOException
     * @throws RowSpaceExceededException 
     */
    public synchronized int reduce(byte[] key, Reducer reduce) throws IOException, RowSpaceExceededException {
        int d = 0;
        for (blobItem bi: blobs) {
            d += bi.blob.reduce(key, reduce);
        }
        return d;
    }
    
    /**
     * delete a BLOB
     * @param key the primary key
     * @throws IOException
     */
    public synchronized void delete(final byte[] key) throws IOException {
        long m = this.mem();
        if (blobs.size() == 0) {
            // do nothing
        } else if (blobs.size() == 1) {
            blobItem bi = blobs.get(0);
            bi.blob.delete(key);
        } else {
            Thread[] t = new Thread[blobs.size() - 1];
            int i = 0;
            for (blobItem bi: blobs) {
                if (i < t.length) {
                    // run this in a concurrent thread
                    final blobItem bi0 = bi;
                    t[i] = new Thread() {
                        public void run() {
                            try { bi0.blob.delete(key); } catch (IOException e) {}
                        }
                    };
                    t[i].start();
                } else {
                    // no additional thread, run in this thread
                    try { bi.blob.delete(key); } catch (IOException e) {}
                }
                i++;
            }
            for (Thread s: t) try {s.join();} catch (InterruptedException e) {}
        }
        assert this.mem() <= m : "m = " + m + ", mem() = " + mem();
    }
    
    /**
     * close the BLOB
     */
    public synchronized void close(boolean writeIDX) {
        for (blobItem bi: blobs) bi.blob.close(writeIDX);
        blobs.clear();
        blobs = null;
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
    public File mergeMount(File f1, File f2,
            ReferenceFactory<? extends Reference> factory,
            Row payloadrow, File newFile, int writeBuffer) {
        if (f2 == null) {
            // this is a rewrite
            Log.logInfo("BLOBArray", "rewrite of " + f1.getName());
            File resultFile = rewriteWorker(factory, this.keylength, this.ordering, f1, payloadrow, newFile, writeBuffer);
            if (resultFile == null) {
                Log.logWarning("BLOBArray", "rewrite of file " + f1 + " returned null. newFile = " + newFile);
                return null;
            }
            try {
                mountBLOB(resultFile, false);
            } catch (IOException e) {
                Log.logWarning("BLOBArray", "rewrite of file " + f1 + " successfull, but read failed. resultFile = " + resultFile);
                return null;
            }
            Log.logInfo("BLOBArray", "rewrite of " + f1.getName() + " into " + resultFile);
            return resultFile;
        } else {
            Log.logInfo("BLOBArray", "merging " + f1.getName() + " with " + f2.getName());
            File resultFile = mergeWorker(factory, this.keylength, this.ordering, f1, f2, payloadrow, newFile, writeBuffer);
            if (resultFile == null) {
                Log.logWarning("BLOBArray", "merge of files " + f1 + ", " + f2 + " returned null. newFile = " + newFile);
                return null;
            }
            try {
                mountBLOB(resultFile, false);
            } catch (IOException e) {
                Log.logWarning("BLOBArray", "merge of files " + f1 + ", " + f2 + " successfull, but read failed. resultFile = " + resultFile);
                return null;
            }
            Log.logInfo("BLOBArray", "merged " + f1.getName() + " with " + f2.getName() + " into " + resultFile);
            return resultFile;
        }
    }
    
    private static <ReferenceType extends Reference> File mergeWorker(
            ReferenceFactory<ReferenceType> factory,
            int keylength, ByteOrder order, File f1, File f2, Row payloadrow, File newFile, int writeBuffer) {
        // iterate both files and write a new one
        
        CloneableIterator<ReferenceContainer<ReferenceType>> i1 = null, i2 = null;
        try {
            i1 = new ReferenceIterator<ReferenceType>(f1, factory, payloadrow);
        } catch (IOException e) {
            Log.logSevere("ArrayStack", "cannot merge because input files cannot be read, f1 = " + f1.toString() + ": " + e.getMessage(), e);
            return null;
        }
        try {
            i2 = new ReferenceIterator<ReferenceType>(f2, factory, payloadrow);
        } catch (IOException e) {
            Log.logSevere("ArrayStack", "cannot merge because input files cannot be read, f2 = " + f2.toString() + ": " + e.getMessage(), e);
            return null;
        }
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
        File tmpFile = new File(newFile.getParentFile(), newFile.getName() + ".prt");
        try {
            HeapWriter writer = new HeapWriter(tmpFile, newFile, keylength, order, writeBuffer);
            merge(i1, i2, order, writer);
            writer.close(true);
        } catch (IOException e) {
            Log.logSevere("ArrayStack", "cannot writing or close writing merge, newFile = " + newFile.toString() + ", tmpFile = " + tmpFile.toString() + ": " + e.getMessage(), e);
            HeapWriter.delete(tmpFile);
            HeapWriter.delete(newFile);
            return null;
        } catch (RowSpaceExceededException e) {
            Log.logSevere("ArrayStack", "cannot merge because of memory failure: " + e.getMessage(), e);
            HeapWriter.delete(tmpFile);
            HeapWriter.delete(newFile);
            return null;
        }
        // we don't need the old files any more
        HeapWriter.delete(f1);
        HeapWriter.delete(f2);
        return newFile;
    }
    
    private static <ReferenceType extends Reference> File rewriteWorker(
            ReferenceFactory<ReferenceType> factory,
            int keylength, ByteOrder order, File f, Row payloadrow, File newFile, int writeBuffer) {
        // iterate both files and write a new one
        
        CloneableIterator<ReferenceContainer<ReferenceType>> i = null;
        try {
            i = new ReferenceIterator<ReferenceType>(f, factory, payloadrow);
        } catch (IOException e) {
            Log.logSevere("ArrayStack", "cannot rewrite because input file cannot be read, f = " + f.toString() + ": " + e.getMessage(), e);
            return null;
        }
        if (!i.hasNext()) {
            FileUtils.deletedelete(f);
            return null;
        }
        assert i.hasNext();
        File tmpFile = new File(newFile.getParentFile(), newFile.getName() + ".prt");
        try {
            HeapWriter writer = new HeapWriter(tmpFile, newFile, keylength, order, writeBuffer);
            rewrite(i, order, writer);
            writer.close(true);
        } catch (IOException e) {
            Log.logSevere("ArrayStack", "cannot writing or close writing rewrite, newFile = " + newFile.toString() + ", tmpFile = " + tmpFile.toString() + ": " + e.getMessage(), e);
            FileUtils.deletedelete(tmpFile);
            FileUtils.deletedelete(newFile);
            return null;
        } catch (RowSpaceExceededException e) {
            Log.logSevere("ArrayStack", "cannot rewrite because of memory failure: " + e.getMessage(), e);
            FileUtils.deletedelete(tmpFile);
            FileUtils.deletedelete(newFile);
            return null;
        }
        // we don't need the old files any more
        FileUtils.deletedelete(f);
        return newFile;
    }
    
    private static <ReferenceType extends Reference> void merge(
            CloneableIterator<ReferenceContainer<ReferenceType>> i1,
            CloneableIterator<ReferenceContainer<ReferenceType>> i2,
            ByteOrder ordering, HeapWriter writer) throws IOException, RowSpaceExceededException {
        assert i1.hasNext();
        assert i2.hasNext();
        byte[] c1lh, c2lh;
        ReferenceContainer<ReferenceType> c1, c2;
        c1 = i1.next();
        c2 = i2.next();
        int e;
        while (true) {
            assert c1 != null;
            assert c2 != null;
            e = ordering.compare(c1.getTermHash(), c2.getTermHash());
            if (e < 0) {
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
            CloneableIterator<ReferenceContainer<ReferenceType>> i,
            ByteOrder ordering, HeapWriter writer) throws IOException, RowSpaceExceededException {
        assert i.hasNext();
        byte[] clh;
        ReferenceContainer<ReferenceType> c;
        c = i.next();
        while (true) {
            assert c != null;
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
            final ArrayStack heap = new ArrayStack(f, "test", 12, NaturalOrder.naturalOrder, 512 * 1024, false);
            heap.insert("aaaaaaaaaaaa".getBytes(), "eins zwei drei".getBytes());
            heap.insert("aaaaaaaaaaab".getBytes(), "vier fuenf sechs".getBytes());
            heap.insert("aaaaaaaaaaac".getBytes(), "sieben acht neun".getBytes());
            heap.insert("aaaaaaaaaaad".getBytes(), "zehn elf zwoelf".getBytes());
            // iterate over keys
            Iterator<byte[]> i = heap.keys(true, false);
            while (i.hasNext()) {
                System.out.println("key_b: " + UTF8.String(i.next()));
            }
            heap.delete("aaaaaaaaaaab".getBytes());
            heap.delete("aaaaaaaaaaac".getBytes());
            heap.insert("aaaaaaaaaaaX".getBytes(), "WXYZ".getBytes());
            heap.close(true);
        } catch (final IOException e) {
            Log.logException(e);
        }
    }
    
}
