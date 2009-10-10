// ArrayStack.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.08.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.kelondro.blob;

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

import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.MergeIterator;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceFactory;
import net.yacy.kelondro.rwi.ReferenceIterator;

import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.NamePrefixThreadFactory;

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
    
    public static final long oneMonth    = 1000L * 60L * 60L * 24L * 365L / 12L;
    
    protected int keylength;
    protected ByteOrder ordering;
    private   File heapLocation;
    private   long fileAgeLimit;
    private   long fileSizeLimit;
    private   long repositoryAgeMax;
    private   long repositorySizeMax;
    protected List<blobItem> blobs;
    private   String prefix;
    private   int buffersize;
    
    // the thread pool for the keeperOf executor service
    private ExecutorService executor;
    
    public ArrayStack(
            final File heapLocation,
            final String prefix,
            final int keylength,
            final ByteOrder ordering,
            final int buffersize) throws IOException {
        this.keylength = keylength;
        this.prefix = prefix;
        this.ordering = ordering;
        this.buffersize = buffersize;
        this.heapLocation = heapLocation;
        this.fileAgeLimit = oneMonth;
        this.fileSizeLimit = (long) Integer.MAX_VALUE;
        this.repositoryAgeMax = Long.MAX_VALUE;
        this.repositorySizeMax = Long.MAX_VALUE;

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
                   d = DateFormatter.parseShortSecond(files[i].substring(0, 14));
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
                   d = DateFormatter.parseShortMilliSecond(files[i].substring(prefix.length() + 1, prefix.length() + 18));
                   time = d.getTime();
                   if (time > maxtime) maxtime = time;
               } catch (ParseException e) {continue;}
            }
        }
        
        // open all blob files
        for (int i = 0; i < files.length; i++) {
            if (files[i].length() >= 22 && files[i].startsWith(prefix) && files[i].endsWith(".blob")) {
                try {
                   d = DateFormatter.parseShortMilliSecond(files[i].substring(prefix.length() + 1, prefix.length() + 18));
                   f = new File(heapLocation, files[i]);
                   time = d.getTime();
                   oneBlob = (time == maxtime) ? new Heap(f, keylength, ordering, buffersize) : new HeapModifier(f, keylength, ordering);
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
    
    /**
     * add a blob file to the array.
     * note that this file must be generated with a file name from newBLOB()
     * @param location
     * @throws IOException
     */
    public synchronized void mountBLOB(File location, boolean full) throws IOException {
        Date d;
        try {
            d = DateFormatter.parseShortMilliSecond(location.getName().substring(prefix.length() + 1, prefix.length() + 18));
        } catch (ParseException e) {
            throw new IOException("date parse problem with file " + location.toString() + ": " + e.getMessage());
        }
        BLOB oneBlob = (full && buffersize > 0) ? new Heap(location, keylength, ordering, buffersize) : new HeapModifier(location, keylength, ordering);
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
    
    public synchronized File[] unmountBestMatch(double maxq, long maxResultSize) {
    	if (this.blobs.size() < 2) return null;
        long l, r;
        File lf, rf;
        double min = Double.MAX_VALUE;
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
                double q = Math.max((double) l, (double) r) / Math.min((double) l, (double) r);
                if (q < min) {
                    min = q;
                    bestMatch[0] = lf;
                    bestMatch[1] = rf;
                }
                if (loopcount > 1000 && min <= maxq && min != Double.MAX_VALUE) break mainloop;
            }
        }
        if (min > maxq) return null;
        unmountBLOB(bestMatch[1], false);
        unmountBLOB(bestMatch[0], false);
        return bestMatch;
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
        if (this.blobs.size() == 0) return null;
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
        if (this.blobs.size() == 0) return null;
        int idx = 0;
        if (smallestFromFirst2 && this.blobs.get(1).location.length() < this.blobs.get(0).location.length()) idx = 1;
        return unmount(idx);
    }
    
    /*
    public synchronized File unmountSimilarSizeBLOB(long otherSize) {
        if (this.blobs.size() == 0 || otherSize == 0) return null;
        blobItem b;
        double delta, bestDelta = Double.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < this.blobs.size(); i++) {
            b = this.blobs.get(i);
            if (b.location.length() == 0) continue;
            delta = ((double) b.location.length()) / ((double) otherSize);
            if (delta < 1.0) delta = 1.0 / delta;
            if (delta < bestDelta) {
                bestDelta = delta;
                bestIndex = i;
            }
        }
        return unmount(bestIndex);
    }
    */
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
        return new File(heapLocation, prefix + "." + DateFormatter.formatShortMilliSecond(creation) + ".blob");
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
        this.fileSizeLimit = Math.min((long) Integer.MAX_VALUE, maxSize / 10L);
    }
    
    private void executeLimits() {
        // check if storage limits are reached and execute consequences
        if (blobs.size() == 0) return;
        
        // age limit:
        while (blobs.size() > 0 && System.currentTimeMillis() - blobs.get(0).creation.getTime() - this.fileAgeLimit > this.repositoryAgeMax) {
            // too old
            blobItem oldestBLOB = blobs.remove(0);
            oldestBLOB.blob.close(false);
            oldestBLOB.blob = null;
            FileUtils.deletedelete(oldestBLOB.location);
        }
        
        // size limit
        while (blobs.size() > 0 && length() > this.repositorySizeMax) {
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
            FileUtils.deletedelete(bi.location);
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
    public synchronized boolean has(byte[] key) {
    	blobItem bi = keeperOf(key);
    	return bi != null;
        //for (blobItem bi: blobs) if (bi.blob.has(key)) return true;
        //return false;
    }
    
    public synchronized blobItem keeperOf(final byte[] key) {
        // because the index is stored only in one table,
        // and the index is completely in RAM, a concurrency will create
        // not concurrent File accesses
        //long start = System.currentTimeMillis();
        
        // start a concurrent query to database tables
        final CompletionService<blobItem> cs = new ExecutorCompletionService<blobItem>(executor);
        int accepted = 0;
        for (final blobItem bi : blobs) {
            try {
                cs.submit(new Callable<blobItem>() {
                    public blobItem call() {
                        if (bi.blob.has(key)) return bi;
                        return null;
                    }
                });
                accepted++;
            } catch (final RejectedExecutionException e) {
                // the executor is either shutting down or the blocking queue is full
                // execute the search direct here without concurrency
                if (bi.blob.has(key)) return bi;
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
    public synchronized byte[] get(byte[] key) throws IOException {
    	//blobItem bi = keeperOf(key);
    	//return (bi == null) ? null : bi.blob.get(key);
        
    	byte[] b;
        for (blobItem bi: blobs) {
            b = bi.blob.get(key);
            if (b != null) return b;
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
        /*
        byte[] b;
        ArrayList<byte[]> l = new ArrayList<byte[]>(blobs.size());
        for (blobItem bi: blobs) {
            b = bi.blob.get(key);
            if (b != null) l.add(b);
        }
        return l;
        */
        return new BlobValues(key);
    }
    
    public class BlobValues implements Iterator<byte[]>, Iterable<byte[]> {

        private Iterator<blobItem> bii;
        private byte[] next;
        private byte[] key;
        
        public BlobValues(byte[] key) {
            this.bii = blobs.iterator();
            this.key = key;
            this.next = null;
            next0();
        }
        
        private void next0() {
            while (this.bii.hasNext()) {
                BLOB b = this.bii.next().blob;
                try {
                    this.next = b.get(key);
                    if (this.next != null) return;
                } catch (IOException e) {
                    Log.logSevere("ArrayStack", "", e);
                    this.next = null;
                    return;
                }
            }
            this.next = null;
        }
        
        public Iterator<byte[]> iterator() {
            return this;
        }

        public boolean hasNext() {
            return this.next != null;
        }

        public byte[] next() {
            byte[] n = this.next;
            next0();
            return n;
        }

        public void remove() {
            throw new UnsupportedOperationException("no remove in BlobValues");
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
     */
    public synchronized void put(byte[] key, byte[] b) throws IOException {
        blobItem bi = (blobs.size() == 0) ? null : blobs.get(blobs.size() - 1);
        if (bi == null)
            System.out.println("bi == null");
        else if (System.currentTimeMillis() - bi.creation.getTime() > this.fileAgeLimit)
            System.out.println("System.currentTimeMillis() - bi.creation.getTime() > this.maxage");
        else if (bi.location.length() > this.fileSizeLimit)
            System.out.println("bi.location.length() > this.maxsize");
        if ((bi == null) || (System.currentTimeMillis() - bi.creation.getTime() > this.fileAgeLimit) || (bi.location.length() > this.fileSizeLimit)) {
            // add a new blob to the array
            bi = new blobItem(buffersize);
            blobs.add(bi);
        }
        assert bi.blob instanceof Heap;
        bi.blob.put(key, b);
        executeLimits();
    }
    
    /**
     * replace a BLOB entry with another which must be smaller or same size
     * @param key  the primary key
     * @throws IOException
     */
    public synchronized int replace(byte[] key, Rewriter rewriter) throws IOException {
        int d = 0;
        for (blobItem bi: blobs) {
            d += bi.blob.replace(key, rewriter);
        }
        return d;
    }
    
    /**
     * remove a BLOB
     * @param key  the primary key
     * @throws IOException
     */
    public synchronized void remove(byte[] key) throws IOException {
        for (blobItem bi: blobs) bi.blob.remove(key);
    }
    
    /**
     * close the BLOB
     */
    public synchronized void close(boolean writeIDX) {
        for (blobItem bi: blobs) bi.blob.close(writeIDX);
        blobs.clear();
        blobs = null;
    }
    
    public File mergeMount(File f1, File f2, ReferenceFactory<? extends Reference> factory, Row payloadrow, File newFile, int writeBuffer) throws IOException {
        Log.logInfo("BLOBArray", "merging " + f1.getName() + " with " + f2.getName());
        File resultFile = mergeWorker(factory, this.keylength, this.ordering, f1, f2, payloadrow, newFile, writeBuffer);
        if (resultFile == null) {
            Log.logWarning("BLOBArray", "merge of files " + f1 + ", " + f2 + " returned null. newFile = " + newFile);
            return null;
        }
        mountBLOB(resultFile, false);
        Log.logInfo("BLOBArray", "merged " + f1.getName() + " with " + f2.getName() + " into " + resultFile);
        return resultFile;
    }
    
    private static <ReferenceType extends Reference> File mergeWorker(ReferenceFactory<ReferenceType> factory, int keylength, ByteOrder order, File f1, File f2, Row payloadrow, File newFile, int writeBuffer) throws IOException {
        // iterate both files and write a new one
        
        CloneableIterator<ReferenceContainer<ReferenceType>> i1 = new ReferenceIterator<ReferenceType>(f1, factory, payloadrow);
        CloneableIterator<ReferenceContainer<ReferenceType>> i2 = new ReferenceIterator<ReferenceType>(f2, factory, payloadrow);
        if (!i1.hasNext()) {
            if (i2.hasNext()) {
                FileUtils.deletedelete(f1);
                if (f2.renameTo(newFile)) return newFile;
                return f2;
            }
            FileUtils.deletedelete(f1);
            FileUtils.deletedelete(f2);
            return null;
        } else if (!i2.hasNext()) {
            FileUtils.deletedelete(f2);
            if (f1.renameTo(newFile)) return newFile;
            return f1;
        }
        assert i1.hasNext();
        assert i2.hasNext();
        File tmpFile = new File(newFile.getParentFile(), newFile.getName() + ".prt");
        HeapWriter writer = new HeapWriter(tmpFile, newFile, keylength, order, writeBuffer);
        merge(i1, i2, order, writer);
        try {
            writer.close(true);
            // we don't need the old files any more
            FileUtils.deletedelete(f1);
            FileUtils.deletedelete(f2);
            return newFile;
        } catch (IOException e) {
            Log.logSevere("ArrayStack", "cannot close writing: " + e.getMessage(), e);
            FileUtils.deletedelete(tmpFile);
            FileUtils.deletedelete(newFile);
            return null;
        }
    }
    
    private static <ReferenceType extends Reference> void merge(CloneableIterator<ReferenceContainer<ReferenceType>> i1, CloneableIterator<ReferenceContainer<ReferenceType>> i2, ByteOrder ordering, HeapWriter writer) throws IOException {
        assert i1.hasNext();
        assert i2.hasNext();
        ReferenceContainer<ReferenceType> c1, c2, c1o, c2o;
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
                    c1o = c1;
                    c1 = i1.next();
                    assert ordering.compare(c1.getTermHash(), c1o.getTermHash()) > 0;
                    continue;
                }
                break;
            }
            if (e > 0) {
                writer.add(c2.getTermHash(), c2.exportCollection());
                if (i2.hasNext()) {
                    c2o = c2;
                    c2 = i2.next();
                    assert ordering.compare(c2.getTermHash(), c2o.getTermHash()) > 0;
                    continue;
                }
                break;
            }
            assert e == 0;
            // merge the entries
            writer.add(c1.getTermHash(), (c1.merge(c2)).exportCollection());
            if (i1.hasNext() && i2.hasNext()) {
                c1 = i1.next();
                c2 = i2.next();
                continue;
            }
            if (i1.hasNext()) c1 = i1.next();
            if (i2.hasNext()) c2 = i2.next();
            break;
           
        }
        // catch up remaining entries
        assert !(i1.hasNext() && i2.hasNext());
        while (i1.hasNext()) {
            //System.out.println("FLUSH REMAINING 1: " + c1.getWordHash());
            writer.add(c1.getTermHash(), c1.exportCollection());
            if (i1.hasNext()) {
                c1o = c1;
                c1 = i1.next();
                assert ordering.compare(c1.getTermHash(), c1o.getTermHash()) > 0;
                continue;
            }
            break;
        }
        while (i2.hasNext()) {
            //System.out.println("FLUSH REMAINING 2: " + c2.getWordHash());
            writer.add(c2.getTermHash(), c2.exportCollection());
            if (i2.hasNext()) {
                c2o = c2;
                c2 = i2.next();
                assert ordering.compare(c2.getTermHash(), c2o.getTermHash()) > 0;
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
            final ArrayStack heap = new ArrayStack(f, "test", 12, NaturalOrder.naturalOrder, 512 * 1024);
            heap.put("aaaaaaaaaaaa".getBytes(), "eins zwei drei".getBytes());
            heap.put("aaaaaaaaaaab".getBytes(), "vier fuenf sechs".getBytes());
            heap.put("aaaaaaaaaaac".getBytes(), "sieben acht neun".getBytes());
            heap.put("aaaaaaaaaaad".getBytes(), "zehn elf zwoelf".getBytes());
            // iterate over keys
            Iterator<byte[]> i = heap.keys(true, false);
            while (i.hasNext()) {
                System.out.println("key_b: " + new String(i.next()));
            }
            heap.remove("aaaaaaaaaaab".getBytes());
            heap.remove("aaaaaaaaaaac".getBytes());
            heap.put("aaaaaaaaaaaX".getBytes(), "WXYZ".getBytes());
            heap.close(true);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
    
}
