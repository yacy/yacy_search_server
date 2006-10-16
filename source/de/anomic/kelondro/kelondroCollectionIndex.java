package de.anomic.kelondro;

// a collectionIndex is an index to kelondroRowCollection objects
// such a collection ist defined by the following parameters
// - chunksize
// - chunkcount
// each of such a collection is stored in a byte[] which may or may not have space for more chunks
// than already exists in such an array. To store these arrays, we reserve entries in kelondroArray
// database files. There will be a set of array files for different sizes of the collection arrays.
// the 1st file has space for <loadfactor> chunks, the 2nd file for <loadfactor> * <loadfactor> chunks,
// the 3rd file for <loadfactor>^^3 chunks, and the n-th file for <loadfactor>^^n chunks.
// if the loadfactor is 4, then we have the following capacities:
// file 0:    4
// file 1:   16
// file 2:   64
// file 3:  256
// file 4: 1024
// file 5: 4096
// file 6:16384
// file 7:65536
// the maximum number of such files is called the partitions number.
// we don't want that these files grow too big, an kelondroOutOfLimitsException is throws if they
// are oversized.
// the collection arrays may be migration to another size during run-time, which means that not only the
// partitions as mentioned above are maintained, but also a set of "shadow-partitions", that represent old
// partitions and where data is read only and slowly migrated to the default partitions.

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

public class kelondroCollectionIndex {

    protected kelondroIndex index;
    int keylength;
    private File          path;
    private String        filenameStub;
    private int           loadfactor;
    private Map           arrays; // Map of (partitionNumber"-"chunksize)/kelondroFixedWidthArray - Objects
    private kelondroRow   playloadrow; // definition of the payload (chunks inside the collections)
    //  private int partitions;  // this is the maxmimum number of array files; yet not used
    
    private static final int idx_col_key        = 0;  // the index
    private static final int idx_col_chunksize  = 1;  // chunksize (number of bytes in a single chunk, needed for migration option)
    private static final int idx_col_chunkcount = 2;  // chunkcount (number of chunks in this collection) needed to identify array file that has the chunks
    private static final int idx_col_clusteridx = 3;  // selector for right cluster file, must be >= arrayIndex(chunkcount)
    private static final int idx_col_flags      = 4;  // flags (for future use)
    private static final int idx_col_indexpos   = 5;  // indexpos (position in index file)
    private static final int idx_col_lastread   = 6;  // a time stamp, update time in days since 1.1.2000
    private static final int idx_col_lastwrote  = 7;  // a time stamp, update time in days since 1.1.2000

    private kelondroRow indexRow() {
        return new kelondroRow(
            "byte[] key-" + keylength + "," +
            "int chunksize-4 {b256}," +
            "int chunkcount-4 {b256}," +
            "byte clusteridx-1 {b256}," +
            "byte flags-1 {b256}," +
            "int indexpos-4 {b256}," +
            "short lastread-2 {b256}, " +
            "short lastwrote-2 {b256}"
            );
    }
    
    private static String fillZ(String s, int len) {
        while (s.length() < len) s = "0" + s;
        return s;
    }
    
    private static File arrayFile(File path, String filenameStub, int loadfactor, int chunksize, int partitionNumber, int serialNumber) {
        String lf = fillZ(Integer.toHexString(loadfactor).toUpperCase(), 2);
        String cs = fillZ(Integer.toHexString(chunksize).toUpperCase(), 4);
        String pn = fillZ(Integer.toHexString(partitionNumber).toUpperCase(), 2);
        String sn = fillZ(Integer.toHexString(serialNumber).toUpperCase(), 2);
        return new File(path, filenameStub + "." + lf + "." + cs + "." + pn + "." + sn + ".kca"); // kelondro collection array
    }
   
    private static File propertyFile(File path, String filenameStub, int loadfactor, int chunksize) {
        String lf = fillZ(Integer.toHexString(loadfactor).toUpperCase(), 2);
        String cs = fillZ(Integer.toHexString(chunksize).toUpperCase(), 4);
        return new File(path, filenameStub + "." + lf + "." + cs + ".properties");
    }
    
    public kelondroCollectionIndex(File path, String filenameStub, int keyLength, kelondroOrder indexOrder,
                                   long buffersize, long preloadTime,
                                   int loadfactor, kelondroRow rowdef) throws IOException {
        // the buffersize is number of bytes that are only used if the kelondroFlexTable is backed up with a kelondroTree
        this.path = path;
        this.filenameStub = filenameStub;
        this.keylength = keyLength;
        this.playloadrow = rowdef;
        this.loadfactor = loadfactor;

        boolean ramIndexGeneration = false;
        boolean fileIndexGeneration = !(new File(path, filenameStub + ".index").exists());
        if (ramIndexGeneration) index = new kelondroRAMIndex(indexOrder, indexRow());
        if (fileIndexGeneration) index = new kelondroFlexTable(path, filenameStub + ".index", buffersize, preloadTime, indexRow(), indexOrder);
                   
        // open array files
        this.arrays = new HashMap(); // all entries will be dynamically created with getArray()
        if (((fileIndexGeneration) || (ramIndexGeneration))) {
            serverLog.logFine("STARTUP", "STARTED INITIALIZATION OF NEW COLLECTION INDEX. THIS WILL TAKE SOME TIME");
            openAllArrayFiles(((fileIndexGeneration) || (ramIndexGeneration)), indexOrder);
        }
        
        // open/create index table
        if (index == null) index = openIndexFile(path, filenameStub, indexOrder, buffersize, preloadTime, loadfactor, rowdef);
    }
    
    private void openAllArrayFiles(boolean indexGeneration, kelondroOrder indexOrder) throws IOException {
        String[] list = this.path.list();
        kelondroFixedWidthArray array;
        
        kelondroRow irow = indexRow();
        int t = kelondroRowCollection.daysSince2000(System.currentTimeMillis());
        for (int i = 0; i < list.length; i++) if (list[i].endsWith(".kca")) {

            // open array
            int pos = list[i].indexOf('.');
            if (pos < 0) continue;
            int chunksize       = Integer.parseInt(list[i].substring(pos +  4, pos +  8), 16);
            int partitionNumber = Integer.parseInt(list[i].substring(pos +  9, pos + 11), 16);
            int serialNumber    = Integer.parseInt(list[i].substring(pos + 12, pos + 14), 16);
            try {
                array = openArrayFile(partitionNumber, serialNumber, true);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            
            // remember that we opened the array
            arrays.put(partitionNumber + "-" + chunksize, array);
            
            if ((index != null) && (indexGeneration)) {
                // loop over all elements in array and create index entry for each row
                kelondroRow.Entry aentry, ientry;
                byte[] key;
                long start = System.currentTimeMillis();
                long lastlog = start;
                for (int j = 0; j < array.USAGE.allCount(); j++) {
                    aentry = array.get(j);
                    key = aentry.getColBytes(0);
                    if (key == null) continue; // skip deleted entries
                    kelondroRowSet indexrows = new kelondroRowSet(this.playloadrow, aentry.getColBytes(1));
                    ientry = irow.newEntry();
                    ientry.setCol(idx_col_key,        key);
                    ientry.setCol(idx_col_chunksize,  chunksize);
                    ientry.setCol(idx_col_chunkcount, indexrows.size());
                    ientry.setCol(idx_col_clusteridx, (byte) partitionNumber);
                    ientry.setCol(idx_col_flags,      (byte) 0);
                    ientry.setCol(idx_col_indexpos,   j);
                    ientry.setCol(idx_col_lastread,   t);
                    ientry.setCol(idx_col_lastwrote,  t);
                    index.put(ientry);
                    
                    // write a log
                    if (System.currentTimeMillis() - lastlog > 30000) {
                        serverLog.logFine("STARTUP", "created " + j + " RWI index entries. " + (((System.currentTimeMillis() - start) * (array.USAGE.allCount() - j) / j) / 60000) + " minutes remaining for this array");
                        lastlog = System.currentTimeMillis();
                    }
                }
            }
        }
    }
    
    private kelondroIndex openIndexFile(File path, String filenameStub, kelondroOrder indexOrder,
            long buffersize, long preloadTime,
            int loadfactor, kelondroRow rowdef) throws IOException {
        // open/create index table
        kelondroFlexTable theindex = new kelondroFlexTable(path, filenameStub + ".index", buffersize, preloadTime, indexRow(), indexOrder);

        // save/check property file for this array
        File propfile = propertyFile(path, filenameStub, loadfactor, rowdef.objectsize());
        Map props = new HashMap();
        if (propfile.exists()) {
            props = serverFileUtils.loadHashMap(propfile);
            String stored_rowdef = (String) props.get("rowdef");
            if ((stored_rowdef == null) || (!(rowdef.subsumes(new kelondroRow(stored_rowdef))))) {
                System.out.println("FATAL ERROR: stored rowdef '" + stored_rowdef + "' does not match with new rowdef '" + 
                        rowdef + "' for array cluster '" + path + "/" + filenameStub + "'");
                System.exit(-1);
            }
        }
        props.put("rowdef", rowdef.toString());
        serverFileUtils.saveMap(propfile, props, "CollectionIndex properties");
        
        return theindex;
    }
    
    private kelondroFixedWidthArray openArrayFile(int partitionNumber, int serialNumber, boolean create) throws IOException {
        File f = arrayFile(path, filenameStub, loadfactor, playloadrow.objectsize(), partitionNumber, serialNumber);
        int load = arrayCapacity(partitionNumber);
        kelondroRow rowdef = new kelondroRow(
                "byte[] key-" + keylength + "," +
                "byte[] collection-" + (kelondroRowCollection.exportOverheadSize + load * this.playloadrow.objectsize())
                );
        if ((!(f.exists())) && (!create)) return null;
        kelondroFixedWidthArray a = new kelondroFixedWidthArray(f, rowdef, 0);
        serverLog.logFine("STARTUP", "opened array file " + f + " with " + a.size() + " RWIs");
        return a;
    }
    
    private kelondroFixedWidthArray getArray(int partitionNumber, int serialNumber, int chunksize) {
        String accessKey = partitionNumber + "-" + chunksize;
        kelondroFixedWidthArray array = (kelondroFixedWidthArray) arrays.get(accessKey);
        if (array != null) return array;
        try {
            array = openArrayFile(partitionNumber, serialNumber, true);
        } catch (IOException e) {
            return null;
        }
        arrays.put(accessKey, array);
        return array;
    }
    
    private int arrayCapacity(int arrayCounter) {
        int load = this.loadfactor;
        for (int i = 0; i < arrayCounter; i++) load = load * this.loadfactor;
        return load;
    }
    
    private int arrayIndex(int requestedCapacity) throws kelondroOutOfLimitsException{
        // the requestedCapacity is the number of wanted chunks
        int load = 1, i = 0;
        while (true) {
            load = load * this.loadfactor;
            if (load >= requestedCapacity) return i;
            i++;
        }
    }
    
    public int size() throws IOException {
        return index.size();
    }
    
    
    public void put(byte[] key, kelondroRowCollection collection) throws IOException, kelondroOutOfLimitsException {
        // this replaces an old collection by a new one
        // this method is not approriate to extend an existing collection with another collection
        putmergeremove(key, collection, false, null, false);
    }
    
    public void merge(byte[] key, kelondroRowCollection collection) throws IOException, kelondroOutOfLimitsException {
        putmergeremove(key, collection, true, null, false);
    }       

    public int remove(byte[] key, Set removekeys, boolean deletecomplete) throws IOException, kelondroOutOfLimitsException {
        return putmergeremove(key, null, false, removekeys, deletecomplete);
    }

    private int putmergeremove(byte[] key, kelondroRowCollection collection, boolean merge, Set removekeys, boolean deletecomplete) throws IOException, kelondroOutOfLimitsException {
        //if (collection.size() > maxChunks) throw new kelondroOutOfLimitsException(maxChunks, collection.size());

        if ((!merge) && (removekeys != null) && (collection != null) && (collection.size() == 0)) {
            // this is not a replacement, it is a deletion
            delete(key);
            return 0;
        }
        
        synchronized (index) {
            // first find an old entry, if one exists
            kelondroRow.Entry indexrow = index.get(key);
        
            if (indexrow == null) {
                if ((collection != null) && (collection.size() > 0)) {
                    // the collection is new
                    overwrite(key, collection, arrayIndex(collection.size()), index.row().newEntry());
                }
                return 0;
            }
            
            // overwrite the old collection
            // read old information
            int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize); // needed only for migration
            int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount);
            int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);
            int oldPartitionNumber = (int) indexrow.getColByte(idx_col_clusteridx);
            assert (oldPartitionNumber >= arrayIndex(oldchunkcount));
            int oldSerialNumber = 0;

            if (merge) {
                // load the old collection and join it
                kelondroRowSet oldcollection = getwithparams(indexrow, oldchunksize, oldchunkcount, oldPartitionNumber, oldrownumber, oldSerialNumber, false, false);
                
                // join with new collection
                oldcollection.addAll(collection);
                oldcollection.shape();
                collection = oldcollection;
            }

            int removed = 0;
            if (removekeys != null) {
                // load the old collection and remove keys
                kelondroRowSet oldcollection = getwithparams(indexrow, oldchunksize, oldchunkcount, oldPartitionNumber, oldrownumber, oldSerialNumber, false, false);

                // remove the keys from the set
                Iterator i = removekeys.iterator();
                Object k;
                while (i.hasNext()) {
                    k = i.next();
                    if ((k instanceof byte[]) && (oldcollection.remove((byte[]) k) != null)) removed++;
                    if ((k instanceof String) && (oldcollection.remove(((String) k).getBytes()) != null)) removed++;
                }
                oldcollection.shape();
                collection = oldcollection;
            }

            if (collection.size() == 0) {
                if (deletecomplete) {
                    kelondroFixedWidthArray array = getArray(oldPartitionNumber, oldSerialNumber, oldchunksize);
                    array.remove(oldrownumber);
                    index.remove(key);
                } else {
                    // update the index entry
                    indexrow.setCol(idx_col_chunkcount, 0);
                    indexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
                    index.put(indexrow);
                }
                return removed;
            }

            int newPartitionNumber = arrayIndex(collection.size());
            int newSerialNumber = 0;

            // see if we need new space or if we can overwrite the old space
            if (oldPartitionNumber == newPartitionNumber) {
                // we don't need a new slot, just write into the old one

                // find array file
                kelondroFixedWidthArray array = getArray(newPartitionNumber, newSerialNumber, this.playloadrow.objectsize());

                // define row
                kelondroRow.Entry arrayEntry = array.row().newEntry();
                arrayEntry.setCol(0, key);
                arrayEntry.setCol(1, collection.exportCollection());

                // overwrite entry in this array
                array.set(oldrownumber, arrayEntry);

                // update the index entry
                indexrow.setCol(idx_col_chunkcount, collection.size());
                indexrow.setCol(idx_col_clusteridx, (byte) oldPartitionNumber);
                indexrow.setCol(idx_col_flags, (byte) 0);
                indexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
                index.put(indexrow);
            } else {
                // we need a new slot, that means we must first delete the old entry
                // find array file
                kelondroFixedWidthArray array = getArray(oldPartitionNumber, oldSerialNumber, oldchunksize);

                // delete old entry
                array.remove(oldrownumber);

                // write a new entry in the other array
                overwrite(key, collection, newPartitionNumber, indexrow);
            }
            return removed;
        }
    }

    private void overwrite(byte[] key, kelondroRowCollection collection, int targetpartition, kelondroRow.Entry indexEntry) throws IOException {
        // helper method, should not be called directly and only within a synchronized(index) environment
        // simply store a collection without check if the collection existed before
        
        // find array file
        kelondroFixedWidthArray array = getArray(targetpartition, 0, this.playloadrow.objectsize());
        
        // define row
        kelondroRow.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());
        
        // write a new entry in this array
        int newRowNumber = array.add(arrayEntry);
        
        // store the new row number in the index
        indexEntry.setCol(idx_col_key, key);
        indexEntry.setCol(idx_col_chunksize, this.playloadrow.objectsize());
        indexEntry.setCol(idx_col_chunkcount, collection.size());
        indexEntry.setCol(idx_col_clusteridx, (byte) targetpartition);
        indexEntry.setCol(idx_col_flags, (byte) 0);
        indexEntry.setCol(idx_col_indexpos, (long) newRowNumber);
        indexEntry.setCol(idx_col_lastread, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
        indexEntry.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
        index.put(indexEntry);
    }
    
    public int indexSize(byte[] key) throws IOException {
        synchronized (index) {
            kelondroRow.Entry indexrow = index.get(key);
            if (indexrow == null) return 0;
            return (int) indexrow.getColLong(idx_col_chunkcount);
        }
    }
    
    public kelondroRowSet get(byte[] key, boolean deleteIfEmpty) throws IOException {
        // find an entry, if one exists
        synchronized (index) {
            kelondroRow.Entry indexrow = index.get(key);
            if (indexrow == null) return null;
            return getdelete(indexrow, false, deleteIfEmpty);
        }
    }
    
    public kelondroRowSet delete(byte[] key) throws IOException {
        // find an entry, if one exists
        synchronized (index) {
            kelondroRow.Entry indexrow = index.get(key);
            if (indexrow == null) return null;
            kelondroRowSet removedCollection = getdelete(indexrow, true, false);
            index.remove(key);
            return removedCollection;
        }
    }

    protected kelondroRowSet getdelete(kelondroRow.Entry indexrow, boolean remove, boolean deleteIfEmpty) throws IOException {
        // call this only within a synchronized(index) environment
        
        // read values
        int chunksize       = (int) indexrow.getColLong(idx_col_chunksize);
        int chunkcount      = (int) indexrow.getColLong(idx_col_chunkcount);
        int rownumber       = (int) indexrow.getColLong(idx_col_indexpos);
        int partitionnumber = (int) indexrow.getColByte(idx_col_clusteridx);
        assert(partitionnumber >= arrayIndex(chunkcount));
        int serialnumber = 0;
        
        return getwithparams(indexrow, chunksize, chunkcount, partitionnumber, rownumber, serialnumber, remove, deleteIfEmpty);
    }

    private kelondroRowSet getwithparams(kelondroRow.Entry indexrow, int chunksize, int chunkcount, int clusteridx, int rownumber, int serialnumber, boolean remove, boolean deleteIfEmpty) throws IOException {
        // open array entry
        kelondroFixedWidthArray array = getArray(clusteridx, serialnumber, chunksize);
        kelondroRow.Entry arrayrow = array.get(rownumber);
        if (arrayrow == null) throw new kelondroException(arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, clusteridx, serialnumber).toString(), "array does not contain expected row");

        // read the row and define a collection
        kelondroRowSet collection = new kelondroRowSet(this.playloadrow, arrayrow.getColBytes(1)); // FIXME: this does not yet work with different rowdef in case of several rowdef.objectsize()
        if (index.order().compare(arrayrow.getColBytes(0), indexrow.getColBytes(idx_col_key)) != 0) {
            // check if we got the right row; this row is wrong. Fix it:
            index.remove(indexrow.getColBytes(idx_col_key)); // the wrong row cannot be fixed
            // store the row number in the index; this may be a double-entry, but better than nothing
            kelondroRow.Entry indexEntry = index.row().newEntry();
            indexEntry.setCol(idx_col_key, arrayrow.getColBytes(0));
            indexEntry.setCol(idx_col_chunksize, this.playloadrow.objectsize());
            indexEntry.setCol(idx_col_chunkcount, collection.size());
            indexEntry.setCol(idx_col_clusteridx, (byte) clusteridx);
            indexEntry.setCol(idx_col_flags, (byte) 0);
            indexEntry.setCol(idx_col_indexpos, (long) rownumber);
            indexEntry.setCol(idx_col_lastread, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
            indexEntry.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
            index.put(indexEntry);
            throw new kelondroException(array.filename, "array contains wrong row '" + new String(arrayrow.getColBytes(0)) + "', expected is '" + new String(indexrow.getColBytes(idx_col_key)) + "', the row has been fixed");
        }
        int chunkcountInArray = collection.size();
        if (chunkcountInArray != chunkcount) {
            // fix the entry in index
            indexrow.setCol(idx_col_chunkcount, chunkcountInArray);
            index.put(indexrow);
            array.logFailure("INCONSISTENCY in " + arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, clusteridx, serialnumber).toString() + ": array has different chunkcount than index: index = " + chunkcount + ", array = " + chunkcountInArray + "; the index has been auto-fixed");
        }
        if ((remove) || ((collection.size() == 0) && (deleteIfEmpty))) array.remove(rownumber);
        return collection;
    }
    
    public Iterator keycollections(byte[] startKey, boolean rot) {
        // returns an iteration of {byte[], kelondroRowSet} Objects
        try {
            return new keycollectionIterator(startKey, rot);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public class keycollectionIterator implements Iterator {
        
        Iterator indexRowIterator;
        
        public keycollectionIterator(byte[] startKey, boolean rot) throws IOException {
            // iterator of {byte[], kelondroRowSet} Objects
            indexRowIterator = index.rows(true, rot, startKey);
        }
        
        public boolean hasNext() {
            return indexRowIterator.hasNext();
        }

        public Object next() {
            kelondroRow.Entry indexrow = (kelondroRow.Entry) indexRowIterator.next();
            if (indexrow == null) return null;
            try {
                return new Object[]{indexrow.getColBytes(0), getdelete(indexrow, false, false)};
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void remove() {
            indexRowIterator.remove();
        }
        
    }
    
    public void close() throws IOException {
        synchronized (index) {
            this.index.close();
            Iterator i = arrays.values().iterator();
            while (i.hasNext()) {
                ((kelondroFixedWidthArray) i.next()).close();
            }
        }
    }
    
    public static void main(String[] args) {

        // define payload structure
        kelondroRow rowdef = new kelondroRow("byte[] a-10, byte[] b-80");
        
        File path = new File(args[0]);
        String filenameStub = args[1];
        long buffersize = 10000000;
        long preloadTime = 10000;
        try {
            // initialize collection index
            kelondroCollectionIndex collectionIndex  = new kelondroCollectionIndex(
                        path, filenameStub, 9 /*keyLength*/,
                        kelondroNaturalOrder.naturalOrder, buffersize, preloadTime,
                        4 /*loadfactor*/, rowdef);
            
            // fill index with values
            kelondroRowSet collection = new kelondroRowSet(rowdef);
            collection.add(rowdef.newEntry(new byte[][]{"abc".getBytes(), "efg".getBytes()}));
            collectionIndex.put("erstes".getBytes(), collection);
            
            for (int i = 0; i <= 17; i++) {
                collection = new kelondroRowSet(rowdef);
                for (int j = 0; j < i; j++) {
                    collection.add(rowdef.newEntry(new byte[][]{("abc" + j).getBytes(), "xxx".getBytes()}));
                }
                System.out.println("put key-" + i + ": " + collection.toString());
                collectionIndex.put(("key-" + i).getBytes(), collection);
            }
            
            // extend collections with more values
            for (int i = 0; i <= 17; i++) {
                collection = new kelondroRowSet(rowdef);
                for (int j = 0; j < i; j++) {
                    collection.add(rowdef.newEntry(new byte[][]{("def" + j).getBytes(), "xxx".getBytes()}));
                }
                collectionIndex.merge(("key-" + i).getBytes(), collection);
            }
            
            // printout of index
            collectionIndex.close();
            kelondroFlexTable index = new kelondroFlexTable(path, filenameStub + ".index", buffersize, preloadTime, collectionIndex.indexRow(), kelondroNaturalOrder.naturalOrder);
            index.print();
            index.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
