// kelondroCachedRecords.java
// (C) 2003 - 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2003 on http://yacy.net
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

package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.server.serverMemory;

public class kelondroCachedRecords extends kelondroAbstractRecords implements kelondroRecords {

    // memory calculation
    private   static final int element_in_cache = 4; // for kelondroCollectionObjectMap: 4; for HashMap: 52
    
    // static supervision objects: recognize and coordinate all activites
    private static TreeMap<String, kelondroCachedRecords> recordTracker = new TreeMap<String, kelondroCachedRecords>();
    private static long memStopGrow    = 10000000; // a limit for the node cache to stop growing if less than this memory amount is available
    private static long memStartShrink =  6000000; // a limit for the node cache to start with shrinking if less than this memory amount is available
    
    // caching buffer
    kelondroIntBytesMap   cacheHeaders; // the cache; holds overhead values and key element
    int readHit;

    int readMiss;

    int writeUnique;

    int writeDouble;

    int cacheDelete;

    int cacheFlush;
    
    
    public kelondroCachedRecords(
            File file, boolean useNodeCache, long preloadTime,
            short ohbytec, short ohhandlec,
            kelondroRow rowdef, int FHandles, int txtProps, int txtPropWidth) throws IOException {
        super(file, useNodeCache, ohbytec, ohhandlec, rowdef, FHandles, txtProps, txtPropWidth);
        initCache(useNodeCache, preloadTime);
        if (useNodeCache) recordTracker.put(this.filename, this);
    }
    
    public kelondroCachedRecords(
            kelondroRA ra, String filename, boolean useNodeCache, long preloadTime,
            short ohbytec, short ohhandlec,
            kelondroRow rowdef, int FHandles, int txtProps, int txtPropWidth,
            boolean exitOnFail) {
        super(ra, filename, useNodeCache, ohbytec, ohhandlec, rowdef, FHandles, txtProps, txtPropWidth, exitOnFail);
        initCache(useNodeCache, preloadTime);
        if (useNodeCache) recordTracker.put(this.filename, this);
    }
    
    public kelondroCachedRecords(
            kelondroRA ra, String filename, boolean useNodeCache, long preloadTime) throws IOException{
        super(ra, filename, useNodeCache);
        initCache(useNodeCache, preloadTime);
        if (useNodeCache) recordTracker.put(this.filename, this);
    }
    
    private void initCache(boolean useNodeCache, long preloadTime) {
        if (useNodeCache) {
            this.cacheHeaders = new kelondroIntBytesMap(this.headchunksize, 0);
        } else {
            this.cacheHeaders = null;
        }
        this.readHit = 0;
        this.readMiss = 0;
        this.writeUnique = 0;
        this.writeDouble = 0;
        this.cacheDelete = 0;
        this.cacheFlush = 0;
        // pre-load node cache
        if ((preloadTime > 0) && (useNodeCache)) {
            long stop = System.currentTimeMillis() + preloadTime;
            int count = 0;
            try {
                Iterator<kelondroNode> i = contentNodes(preloadTime);
                CacheNode n;
                while ((System.currentTimeMillis() < stop) && (cacheGrowStatus() == 2) && (i.hasNext())) {
                    n = (CacheNode) i.next();
                    cacheHeaders.addb(n.handle().index, n.headChunk);
                    count++;
                }
                cacheHeaders.flush();
                logFine("preloaded " + count + " records into cache");
            } catch (kelondroException e) {
                // the contentNodes iterator had a time-out; we don't do a preload
                logFine("could not preload records: " + e.getMessage());
            }
            
        }
    }

    int cacheGrowStatus() {
        long available = serverMemory.available();
        if ((cacheHeaders != null) && (available < cacheHeaders.memoryNeededForGrow())) return 0;
        return cacheGrowStatus(available, memStopGrow, memStartShrink);
    }
    
    public static final int cacheGrowStatus(long available, long stopGrow, long startShrink) {
        // returns either 0, 1 or 2:
        // 0: cache is not allowed to grow, but shall shrink
        // 1: cache is allowed to grow, but need not to shrink
        // 2: cache is allowed to grow and must not shrink
        if (available > stopGrow) return 2;
        if (available > startShrink) {
            serverMemory.gc(30000, "kelendroCacheRecords.cacheGrowStatus(...) 1"); // thq
            return 1;
        }
        serverMemory.gc(3000, "kelendroCacheRecords.cacheGrowStatus(...) 0"); // thq
        return 0;
    }
    
    public static void setCacheGrowStati(long memStopGrowNew, long memStartShrinkNew) {
        memStopGrow = memStopGrowNew;
        memStartShrink =  memStartShrinkNew;
    }
    
    public static long getMemStopGrow() {
        return memStopGrow ;
    }
    
    public static long getMemStartShrink() {
        return memStartShrink ;
    }
    
    public static final Iterator<String> filenames() {
        // iterates string objects; all file names from record tracker
        return recordTracker.keySet().iterator();
    }

    public static final Map<String, String> memoryStats(String filename) {
        // returns a map for each file in the tracker;
        // the map represents properties for each record oobjects,
        // i.e. for cache memory allocation
        kelondroCachedRecords theRecord = (kelondroCachedRecords) recordTracker.get(filename);
        return theRecord.memoryStats();
    }
    
    private final Map<String, String> memoryStats() {
        // returns statistical data about this object
        if (cacheHeaders == null) return null;
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("nodeChunkSize", Integer.toString(this.headchunksize + element_in_cache));
        map.put("nodeCacheCount", Integer.toString(cacheHeaders.size()));
        map.put("nodeCacheMem", Integer.toString(cacheHeaders.size() * (this.headchunksize + element_in_cache)));
        map.put("nodeCacheReadHit", Integer.toString(readHit));
        map.put("nodeCacheReadMiss", Integer.toString(readMiss));
        map.put("nodeCacheWriteUnique", Integer.toString(writeUnique));
        map.put("nodeCacheWriteDouble", Integer.toString(writeDouble));
        map.put("nodeCacheDeletes", Integer.toString(cacheDelete));
        map.put("nodeCacheFlushes", Integer.toString(cacheFlush));
        return map;
    }
    
    protected synchronized void deleteNode(kelondroHandle handle) throws IOException {
        if (cacheHeaders == null) {
            super.deleteNode(handle);
        } else synchronized (cacheHeaders) {
            if (cacheHeaders.size() == 0) {
                super.deleteNode(handle);
            } else {
                cacheHeaders.removeb(handle.index);
                cacheDelete++;
                super.deleteNode(handle);
            }
        }
    }
    
    protected void printCache() {
        if (cacheHeaders == null) {
            System.out.println("### file report: " + size() + " entries");
            for (int i = 0; i < USAGE.allCount(); i++) {
                // print from  file to compare
                System.out.print("#F " + i + ": ");
                try {
                    for (int j = 0; j < headchunksize; j++) 
                        System.out.print(Integer.toHexString(0xff & entryFile.readByte(j + seekpos(new kelondroHandle(i)))) + " ");
                } catch (IOException e) {}
                
                System.out.println();
            }
        } else {
            System.out.println("### cache report: " + cacheHeaders.size() + " entries");
            
                Iterator<kelondroRow.Entry> i = cacheHeaders.rows();
                kelondroRow.Entry entry;
                while (i.hasNext()) {
                    entry = i.next();
                    
                    // print from cache
                    System.out.print("#C ");
                    printChunk(entry);
                    System.out.println();
                    
                    // print from  file to compare
                    /*
                    System.out.print("#F " + cp + " " + ((Handle) entry.getKey()).index + ": ");
                    try {
                        for (int j = 0; j < headchunksize; j++)
                            System.out.print(entryFile.readByte(j + seekpos((Handle) entry.getKey())) + ",");
                    } catch (IOException e) {}
                    */
                    System.out.println();
                }
        }
        System.out.println("### end report");
    }
    
    public synchronized void close() {
        if (cacheHeaders == null) {
            if (recordTracker.get(this.filename) != null) {
                theLogger.severe("close(): file '" + this.filename + "' was tracked with record tracker, but it should not.");
            }
        } else {
            if (recordTracker.remove(this.filename) == null) {
                theLogger.severe("close(): file '" + this.filename + "' was not tracked with record tracker.");
            }
        }
        super.close();
        this.cacheHeaders = null;
    }

    public kelondroProfile[] profiles() {
        return new kelondroProfile[]{
                (cacheHeaders == null) ? new kelondroProfile() :
                cacheHeaders.profile(),
                entryFile.profile()
        };
    }
    
    public kelondroProfile profile() {
        return kelondroProfile.consolidate(profiles());
    }
    
    public void print() throws IOException {
        super.print();
        
        // print also all records
        System.out.println("CACHE");
        printCache();
        System.out.println("--");
        System.out.println("NODES");
        Iterator<kelondroNode> i = new contentNodeIterator(-1);
        kelondroNode n;
        while (i.hasNext()) {
            n = (kelondroNode) i.next();
            System.out.println("NODE: " + n.toString());
        }
    }
    
    public kelondroNode newNode(kelondroHandle handle, byte[] bulk, int offset) throws IOException {
        return new CacheNode(handle, bulk, offset);
    }
    
    public final class CacheNode implements kelondroNode {
        // an Node holds all information of one row of data. This includes the key to the entry
        // which is stored as entry element at position 0
        // an Node object can be created in two ways:
        // 1. instantiation with an index number. After creation the Object does not hold any
        //    value information until such is retrieved using the getValue() method
        // 2. instantiation with a value array. the values are not directly written into the
        //    file. Expanding the tree structure is then done using the save() method. at any
        //    time it is possible to verify the save state using the saved() predicate.
        // Therefore an entry object has three modes:
        // a: holding an index information only (saved() = true)
        // b: holding value information only (saved() = false)
        // c: holding index and value information at the same time (saved() = true)
        //    which can be the result of one of the two processes as follow:
        //    (i)  created with index and after using the getValue() method, or
        //    (ii) created with values and after calling the save() method
        // the method will therefore throw an IllegalStateException when the following
        // process step is performed:
        //    - create the Node with index and call then the save() method
        // this case can be decided with
        //    ((index != NUL) && (values == null))
        // The save() method represents the insert function for the tree. Balancing functions
        // are applied automatically. While balancing, the Node does never change its index key,
        // but its parent/child keys.
        //private byte[]    ohBytes  = null;  // the overhead bytes, OHBYTEC values
        //private Handle[]  ohHandle= null;  // the overhead handles, OHHANDLEC values
        //private byte[][]  values  = null;  // an array of byte[] nodes is the value vector
        private kelondroHandle handle = null; // index of the entry, by default NUL means undefined
        byte[] headChunk = null; // contains ohBytes, ohHandles and the key value
        private byte[] tailChunk = null; // contains all values except the key value
        private boolean headChanged = false;
        private boolean tailChanged = false;

        public CacheNode(byte[] rowinstance) throws IOException {
            // this initializer is used to create nodes from bulk-read byte arrays
            assert ((rowinstance == null) || (rowinstance.length == ROW.objectsize)) : "bulkchunk.length = " + rowinstance.length + ", ROW.width(0) = " + ROW.width(0);
            this.handle = new kelondroHandle(USAGE.allocatePayload(rowinstance));
            
            // create empty chunks
            this.headChunk = new byte[headchunksize];
            this.tailChunk = new byte[tailchunksize];
            
            // write content to chunks
            if (rowinstance == null) {
                for (int i = headchunksize - 1; i >= 0; i--) this.headChunk[i] = (byte) 0xff;
                for (int i = tailchunksize - 1; i >= 0; i--) this.tailChunk[i] = (byte) 0xff;
            } else {
                for (int i = overhead - 1; i >= 0; i--) this.headChunk[i] = (byte) 0xff;
                System.arraycopy(rowinstance, 0, this.headChunk, overhead, ROW.width(0));
                System.arraycopy(rowinstance, ROW.width(0), this.tailChunk, 0, tailchunksize);
            }
            
            if (cacheHeaders != null) synchronized (cacheHeaders) {
                updateNodeCache();
            }
            
            // mark chunks as changed
            // if the head/tail chunks come from a file system read, setChanged should be false
            // if the chunks come from a overwrite attempt, it should be true
            this.headChanged = false; // we wrote the head already during allocate
            this.tailChanged = false; // we write the tail already during allocate
        }
        
        public CacheNode(kelondroHandle handle, byte[] bulkchunk, int offset) throws IOException {
            // this initializer is used to create nodes from bulk-read byte arrays
            // if write is true, then the chunk in bulkchunk is written to the file
            // othervise it is considered equal to what is stored in the file
            // (that is ensured during pre-loaded enumeration)
            this.handle = handle;
            boolean changed;
            if (handle.index >= USAGE.allCount()) {
                // this causes only a write action if we create a node beyond the end of the file
                USAGE.allocateRecord(handle.index, bulkchunk, offset);
                changed = false; // we have already wrote the record, so it is considered as unchanged
            } else {
                changed = true;
            }
            assert ((bulkchunk == null) || (bulkchunk.length - offset >= recordsize)) : "bulkchunk.length = " + bulkchunk.length + ", offset = " + offset + ", recordsize = " + recordsize;
            
            // create empty chunks
            this.headChunk = new byte[headchunksize];
            this.tailChunk = new byte[tailchunksize];
            
            // write content to chunks
            if (bulkchunk != null) {
                System.arraycopy(bulkchunk, offset, this.headChunk, 0, headchunksize);
                System.arraycopy(bulkchunk, offset + headchunksize, this.tailChunk, 0, tailchunksize);
            }
            
            // mark chunks as changed
            this.headChanged = changed;
            this.tailChanged = changed;
        }
        
        public CacheNode(kelondroHandle handle, boolean fillTail) throws IOException {
            this(handle, null, 0, fillTail);
        }
        
        public CacheNode(kelondroHandle handle, CacheNode parentNode, int referenceInParent, boolean fillTail) throws IOException {
            // this creates an entry with an pre-reserved entry position.
            // values can be written using the setValues() method,
            // but we expect that values are already there in the file.
            assert (handle != null): "node handle is null";
            assert (handle.index >= 0): "node handle too low: " + handle.index;
            //assert (handle.index < USAGE.allCount()) : "node handle too high: " + handle.index + ", USEDC=" + USAGE.USEDC + ", FREEC=" + USAGE.FREEC;
            
            // the parentNode can be given if an auto-fix in the following case is wanted
            if (handle == null) throw new kelondroException(filename, "INTERNAL ERROR: node handle is null.");
            if (handle.index >= USAGE.allCount()) {
                if (parentNode == null) throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. No auto-fix node was submitted. This is a serious failure.");
                try {
                    parentNode.setOHHandle(referenceInParent, null);
                    parentNode.commit();
                    logWarning("INTERNAL ERROR, Node/init in " + filename + ": node handle index " + handle.index + " exceeds size. The bad node has been auto-fixed");
                } catch (IOException ee) {
                    throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. It was tried to fix the bad node, but failed with an IOException: " + ee.getMessage());
                }
            }

            // use given handle
            this.handle = new kelondroHandle(handle.index);

            // check for memory availability when fillTail is requested
            if ((fillTail) && (tailchunksize > 10000)) fillTail = false; // this is a fail-safe 'short version' of a memory check
            
            // init the content
            // create chunks; read them from file or cache
            this.tailChunk = null;
            if (cacheHeaders == null) {
                if (fillTail) {
                    // read complete record
                    byte[] chunkbuffer = new byte[recordsize];
                    entryFile.readFully(seekpos(this.handle), chunkbuffer, 0, recordsize);
                    this.headChunk = new byte[headchunksize];
                    this.tailChunk = new byte[tailchunksize];
                    System.arraycopy(chunkbuffer, 0, this.headChunk, 0, headchunksize);
                    System.arraycopy(chunkbuffer, headchunksize, this.tailChunk, 0, tailchunksize);
                    chunkbuffer = null;
                } else {
                    // read overhead and key
                    this.headChunk = new byte[headchunksize];
                    this.tailChunk = null;
                    entryFile.readFully(seekpos(this.handle), this.headChunk, 0, headchunksize);
                }
            } else synchronized(cacheHeaders) {
                byte[] cacheEntry = null;
                cacheEntry = cacheHeaders.getb(this.handle.index);
                if (cacheEntry == null) {
                    // cache miss, we read overhead and key from file
                    readMiss++;
                    if (fillTail) {
                        // read complete record
                        byte[] chunkbuffer = new byte[recordsize];
                        entryFile.readFully(seekpos(this.handle), chunkbuffer, 0, recordsize);
                        this.headChunk = new byte[headchunksize];
                        this.tailChunk = new byte[tailchunksize];
                        System.arraycopy(chunkbuffer, 0, this.headChunk, 0, headchunksize);
                        System.arraycopy(chunkbuffer, headchunksize, this.tailChunk, 0, tailchunksize);
                        chunkbuffer = null;
                    } else {
                        // read overhead and key
                        this.headChunk = new byte[headchunksize];
                        this.tailChunk = null;
                        entryFile.readFully(seekpos(this.handle), this.headChunk, 0, headchunksize);
                    }
                    
                    // if space left in cache, copy these value to the cache
                    updateNodeCache();
                } else {
                    readHit++;
                    this.headChunk = cacheEntry;
                }
            }
        }
        
        private void setValue(byte[] value, int valueoffset, int valuewidth, byte[] targetarray, int targetoffset) {
            if (value == null) {
                while (valuewidth-- > 0) targetarray[targetoffset++] = 0;
            } else {
                assert ((valueoffset >= 0) && (valueoffset < value.length)) : "valueoffset = " + valueoffset;
                assert ((valueoffset + valuewidth <= value.length)) : "valueoffset = " + valueoffset + ", valuewidth = " + valuewidth + ", value.length = " + value.length;
                assert ((targetoffset >= 0) && (targetoffset < targetarray.length)) : "targetoffset = " + targetoffset;
                assert ((targetoffset + valuewidth <= targetarray.length)) : "targetoffset = " + targetoffset + ", valuewidth = " + valuewidth + ", targetarray.length = " + targetarray.length;
                System.arraycopy(value, valueoffset, targetarray, targetoffset, Math.min(value.length, valuewidth)); // error?
                while (valuewidth-- > value.length) targetarray[targetoffset + valuewidth] = 0;
            }
        }
        
        public kelondroHandle handle() {
            // if this entry has an index, return it
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "the entry has no index assigned");
            return this.handle;
        }

        public void setOHByte(int i, byte b) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("setOHByte: wrong index " + i);
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "setOHByte: no handle assigned");
            this.headChunk[i] = b;
            this.headChanged = true;
        }
        
        public void setOHHandle(int i, kelondroHandle otherhandle) {
            assert (i < OHHANDLEC): "setOHHandle: wrong array size " + i;
            assert (this.handle.index != kelondroHandle.NUL): "setOHHandle: no handle assigned ind file" + filename;
            if (otherhandle == null) {
                NUL2bytes(this.headChunk, OHBYTEC + 4 * i);
            } else {
                if (otherhandle.index >= USAGE.allCount()) throw new kelondroException(filename, "INTERNAL ERROR, setOHHandles: handle " + i + " exceeds file size (" + handle.index + " >= " + USAGE.allCount() + ")");
                int2bytes(otherhandle.index, this.headChunk, OHBYTEC + 4 * i);
            }
            this.headChanged = true;
        }
        
        public byte getOHByte(int i) {
            if (i >= OHBYTEC) throw new IllegalArgumentException("getOHByte: wrong index " + i);
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "Cannot load OH values");
            return this.headChunk[i];
        }

        public kelondroHandle getOHHandle(int i) {
            if (this.handle.index == kelondroHandle.NUL) throw new kelondroException(filename, "Cannot load OH values");
            assert (i < OHHANDLEC): "handle index out of bounds: " + i + " in file " + filename;
            int h = bytes2int(this.headChunk, OHBYTEC + 4 * i);
            return (h == kelondroHandle.NUL) ? null : new kelondroHandle(h);
        }

        public synchronized void setValueRow(byte[] row) throws IOException {
            // if the index is defined, then write values directly to the file, else only to the object
            if ((row != null) && (row.length != ROW.objectsize)) throw new IOException("setValueRow with wrong (" + row.length + ") row length instead correct: " + ROW.objectsize);
            
            // set values
            if (this.handle.index != kelondroHandle.NUL) {
                setValue(row, 0, ROW.width(0), headChunk, overhead);
                if (ROW.columns() > 1) setValue(row, ROW.width(0), tailchunksize, tailChunk, 0);
            }
            this.headChanged = true;
            this.tailChanged = true;
        }

        public synchronized boolean valid() {
            // returns true if the key starts with non-zero byte
            // this may help to detect deleted entries
            return (headChunk[overhead] != 0) && ((headChunk[overhead] != -128) || (headChunk[overhead + 1] != 0));
        }

        public synchronized byte[] getKey() {
            // read key
            return trimCopy(headChunk, overhead, ROW.width(0));
        }

        public synchronized byte[] getValueRow() throws IOException {
            
            if (this.tailChunk == null) {
                // load all values from the database file
                this.tailChunk = new byte[tailchunksize];
                // read values
                entryFile.readFully(seekpos(this.handle) + (long) headchunksize, this.tailChunk, 0, this.tailChunk.length);
            }

            // create return value
            byte[] row = new byte[ROW.objectsize];

            // read key
            System.arraycopy(headChunk, overhead, row, 0, ROW.width(0));

            // read remaining values
            System.arraycopy(tailChunk, 0, row, ROW.width(0), tailchunksize);

            return row;
        }

        public synchronized void commit() throws IOException {
            // this must be called after all write operations to the node are
            // finished

            // place the data to the file

            if (this.headChunk == null) {
                // there is nothing to save
                throw new kelondroException(filename, "no values to save (header missing)");
            }

            boolean doCommit = this.headChanged || this.tailChanged;
            
            // save head
            synchronized (entryFile) {
            if (this.headChanged) {
                //System.out.println("WRITEH(" + filename + ", " + seekpos(this.handle) + ", " + this.headChunk.length + ")");
                assert (headChunk == null) || (headChunk.length == headchunksize);
                entryFile.write(seekpos(this.handle), (this.headChunk == null) ? new byte[headchunksize] : this.headChunk);
                updateNodeCache();
                this.headChanged = false;
            }

            // save tail
            if ((this.tailChunk != null) && (this.tailChanged)) {
                //System.out.println("WRITET(" + filename + ", " + (seekpos(this.handle) + headchunksize) + ", " + this.tailChunk.length + ")");
                assert (tailChunk == null) || (tailChunk.length == tailchunksize);
                entryFile.write(seekpos(this.handle) + headchunksize, (this.tailChunk == null) ? new byte[tailchunksize] : this.tailChunk);
                this.tailChanged = false;
            }
            
            if (doCommit) entryFile.commit();
            }
        }
        
        public String toString() {
            if (this.handle.index == kelondroHandle.NUL) return "NULL";
            String s = Integer.toHexString(this.handle.index);
            kelondroHandle h;
            while (s.length() < 4) s = "0" + s;
            try {
                for (int i = 0; i < OHBYTEC; i++) s = s + ":b" + getOHByte(i);
                for (int i = 0; i < OHHANDLEC; i++) {
                    h = getOHHandle(i);
                    if (h == null) s = s + ":hNULL"; else s = s + ":h" + h.toString();
                }
                kelondroRow.Entry content = row().newEntry(getValueRow());
                for (int i = 0; i < row().columns(); i++) s = s + ":" + ((content.empty(i)) ? "NULL" : content.getColString(i, "UTF-8").trim());
            } catch (IOException e) {
                s = s + ":***LOAD ERROR***:" + e.getMessage();
            }
            return s;
        }
        
        private boolean cacheSpace() {
            // check for space in cache
            // should be only called within a synchronized(cacheHeaders) environment
            // returns true if it is allowed to add another entry to the cache
            // returns false if the cache is considered to be full
            if (cacheHeaders == null) return false; // no caching
            if (cacheHeaders.size() == 0) return true; // nothing there to flush
            if (cacheGrowStatus() == 2) return true; // no need to flush cache space
            
            // just delete any of the entries
            if (cacheGrowStatus() <= 1) synchronized (cacheHeaders) {
                cacheHeaders.removeoneb();
                cacheFlush++;
            }
            return cacheGrowStatus() > 0;
        }
        
        private void updateNodeCache() {
            if (this.handle == null) return; // wrong access
            if (this.headChunk == null) return; // nothing there to cache
            if (cacheHeaders == null) return; // we do not use the cache
            if (cacheSpace()) synchronized (cacheHeaders) {
                // generate cache entry
                //byte[] cacheEntry = new byte[headchunksize];
                //System.arraycopy(headChunk, 0, cacheEntry, 0, headchunksize);
                
                // store the cache entry
                boolean upd = false;
                upd = (cacheHeaders.putb(this.handle.index, headChunk) != null);
                if (upd) writeDouble++; else writeUnique++;
                
                //System.out.println("kelondroRecords cache4" + filename + ": cache record size = " + (memBefore - Runtime.getRuntime().freeMemory()) + " bytes" + ((newentry) ? " new" : ""));
                //printCache();
            } else {
                // there shall be no entry in the cache. If one exists, we remove it
                boolean rem = false;
                rem = (cacheHeaders.removeb(this.handle.index) != null);
                if (rem) cacheDelete++;
            }
        }
    }
    
}
