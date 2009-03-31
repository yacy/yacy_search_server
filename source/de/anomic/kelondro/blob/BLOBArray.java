// kelondroBLOBArray.java
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
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.order.MergeIterator;
import de.anomic.kelondro.util.DateFormatter;
import de.anomic.kelondro.util.FileUtils;

public class BLOBArray implements BLOB {

    /*
     * This class implements a BLOB using a set of kelondroBLOBHeap objects
     * In addition to a kelondroBLOBHeap this BLOB can delete large amounts of data using a given time limit.
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
    public static final long oneGigabyte = 1024 * 1024 * 1024;
    
    private int keylength;
    private ByteOrder ordering;
    private File heapLocation;
    private long fileAgeLimit;
    private long fileSizeLimit;
    private long repositoryAgeMax;
    private long repositorySizeMax;
    private List<blobItem> blobs;
    private String blobSalt;
    private int buffersize;
    
    public BLOBArray(
            final File heapLocation,
            final String blobSalt,
            final int keylength,
            final ByteOrder ordering,
            final int buffersize) throws IOException {
        this.keylength = keylength;
        this.blobSalt = blobSalt;
        this.ordering = ordering;
        this.buffersize = buffersize;
        this.heapLocation = heapLocation;
        this.fileAgeLimit = oneMonth;
        this.fileSizeLimit = oneGigabyte;
        this.repositoryAgeMax = Long.MAX_VALUE;
        this.repositorySizeMax = Long.MAX_VALUE;

        // check existence of the heap directory
        if (heapLocation.exists()) {
            if (!heapLocation.isDirectory()) throw new IOException("the BLOBArray directory " + heapLocation.toString() + " does not exist (is blocked by a file with same name)");
        } else {
            if(!heapLocation.mkdirs()) throw new IOException("the BLOBArray directory " + heapLocation.toString() + " does not exist (can not be created)");
        }

        // register all blob files inside this directory
        String[] files = heapLocation.list();
        Date d;
        TreeMap<Long, blobItem> sortedItems = new TreeMap<Long, blobItem>();
        BLOB oneBlob;
        File f;
        long time, maxtime = 0;
        // first find maximum time: the file with this time will be given a write buffer
        for (int i = 0; i < files.length; i++) {
            if (files[i].length() >= 19 && files[i].endsWith(".blob")) {
               try {
                   d = DateFormatter.parseShortSecond(files[i].substring(0, 14));
                   time = d.getTime();
                   if (time > maxtime) maxtime = time;
               } catch (ParseException e) {continue;}
            }
        }
        for (int i = 0; i < files.length; i++) {
            if (files[i].length() >= 19 && files[i].endsWith(".blob")) {
               try {
                   d = DateFormatter.parseShortSecond(files[i].substring(0, 14));
                   f = new File(heapLocation, files[i]);
                   time = d.getTime();
                   oneBlob = (time == maxtime && buffersize > 0) ? new BLOBHeap(f, keylength, ordering, buffersize) : new BLOBHeapModifier(f, keylength, ordering);
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
    public synchronized void mountBLOB(File location) throws IOException {
        Date d;
        try {
            d = DateFormatter.parseShortSecond(location.getName().substring(0, 14));
        } catch (ParseException e) {
            throw new IOException("date parse problem with file " + location.toString() + ": " + e.getMessage());
        }
        BLOB oneBlob = (buffersize > 0) ? new BLOBHeap(location, keylength, ordering, buffersize) : new BLOBHeapModifier(location, keylength, ordering);
        blobs.add(new blobItem(d, location, oneBlob));
    }
    
    public synchronized void unmountBLOB(File location, boolean writeIDX) {
        Iterator<blobItem> i = this.blobs.iterator();
        blobItem b;
        while (i.hasNext()) {
            b = i.next();
            if (b.location.equals(location)) {
                i.remove();
                b.blob.close(writeIDX);
                b.blob = null;
                b.location = null;
                return;
            }
        }
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
        long l, r;
        double min = Double.MAX_VALUE;
        int[] idx = new int[2];
        maxResultSize = maxResultSize >> 1;
        for (int i = 0; i < this.blobs.size() - 1; i++) {
            for (int j = i + 1; j < this.blobs.size(); j++) {
                l = 1 + (this.blobs.get(i).location.length() >> 1);
                r = 1 + (this.blobs.get(j).location.length() >> 1);
                if (l + r > maxResultSize) continue;
                double q = Math.max(((double) l)/((double) r), ((double) r)/((double) l));
                if (q < min) {
                    min = q;
                    idx[0] = i;
                    idx[1] = j;
                }
            }
        }
        if (min > maxq) return null;
        File[] bestmatch = new File[]{this.blobs.get(idx[0]).location, this.blobs.get(idx[1]).location};
        unmount(idx[1]);
        unmount(idx[0]);
        return bestmatch;
    }
    
    public synchronized File[] unmountSmallest(long maxResultSize) {
        File f0 = smallestBLOB(null);
        if (f0 == null) return null;
        File f1 = smallestBLOB(f0);
        if (f1 == null) return null;
        
        unmountBLOB(f0, false);
        unmountBLOB(f1, false);
        return new File[]{f0, f1};
    }
    
    public synchronized File unmountSmallestBLOB() {
        return smallestBLOB(null);
    }
    
    public synchronized File smallestBLOB(File excluding) {
        if (this.blobs.size() == 0) return null;
        int bestIndex = -1;
        long smallest = Long.MAX_VALUE;
        for (int i = 0; i < this.blobs.size(); i++) {
            if (excluding != null && this.blobs.get(i).location.getAbsolutePath().equals(excluding.getAbsoluteFile())) continue;
            if (this.blobs.get(i).location.length() < smallest) {
                smallest = this.blobs.get(i).location.length();
                bestIndex = i;
            }
        }
        if (bestIndex == -1) return null;
        return this.blobs.get(bestIndex).location;
    }
    
    public synchronized File unmountOldestBLOB(boolean smallestFromFirst2) {
        if (this.blobs.size() == 0) return null;
        int idx = 0;
        if (smallestFromFirst2 && this.blobs.get(1).location.length() < this.blobs.get(0).location.length()) idx = 1;
        return unmount(idx);
    }
    
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
    
    /**
     * return the number of BLOB files in this array
     * @return
     */
    public synchronized int entries() {
        return this.blobs.size();
    }
    
    /**
     * generate a new BLOB file name with a given date.
     * This method is needed to generate a file name that matches to the name structure that is needed for parts of the array
     * @param creation
     * @return
     */
    public synchronized File newBLOB(Date creation) {
        return new File(heapLocation, DateFormatter.formatShortSecond(creation) + "." + blobSalt + ".blob");
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
        this.fileSizeLimit = Math.min(oneGigabyte, maxSize / 10);
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
            this.blob = (buffer == 0) ? new BLOBHeapModifier(location, keylength, ordering) : new BLOBHeap(location, keylength, ordering, buffer);
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
     * iterator over all keys
     * @param up
     * @param rotating
     * @return
     * @throws IOException
     */
    public synchronized CloneableIterator<byte[]> keys(boolean up, boolean rotating) throws IOException {
        assert rotating = false;
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
        for (blobItem bi: blobs) if (bi.blob.has(key)) return true;
        return false;
    }
    
    /**
     * retrieve the whole BLOB from the table
     * @param key  the primary key
     * @return
     * @throws IOException
     */
    public synchronized byte[] get(byte[] key) throws IOException {
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
    public synchronized List<byte[]> getAll(byte[] key) throws IOException {
        byte[] b;
        ArrayList<byte[]> l = new ArrayList<byte[]>(blobs.size());
        for (blobItem bi: blobs) {
            b = bi.blob.get(key);
            if (b != null) l.add(b);
        }
        return l;
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
        assert bi.blob instanceof BLOBHeap;
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
    

    public static void main(final String[] args) {
        final File f = new File("/Users/admin/blobarraytest");
        try {
            //f.delete();
            final BLOBArray heap = new BLOBArray(f, "test", 12, NaturalOrder.naturalOrder, 512 * 1024);
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
