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

public class kelondroCollectionIndex {

    protected kelondroIndex index;
    private File          path;
    private String        filenameStub;
    private int           loadfactor;
    private Map           arrays; // Map of (partitionNumber"-"chunksize)/kelondroFixedWidthArray - Objects
    private kelondroRow   playloadrow; // definition of the payload (chunks inside the collections)
    //  private int partitions;  // this is the maxmimum number of array files; yet not used
    
    private static final int idx_col_key        = 0;  // the index
    private static final int idx_col_chunksize  = 1;  // chunksize (number of bytes in a single chunk, needed for migration option)
    private static final int idx_col_chunkcount = 2;  // chunkcount (number of chunks in this collection) needed to identify array file that has the chunks
    private static final int idx_col_indexpos   = 3;  // indexpos (position in index file)
    private static final int idx_col_lastread   = 4;  // a time stamp, update time in days since 1.1.2000
    private static final int idx_col_lastwrote  = 5;  // a time stamp, update time in days since 1.1.2000

    private static kelondroRow indexRow(int keylen) {
        return new kelondroRow(
            "byte[] key-" + keylen + "," +
            "int chunksize-4 {b256}," +
            "int chunkcount-4 {b256}," +
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
        return new File(path, filenameStub + "." + lf + "." + cs + ".properties"); // kelondro collection array
    }
    
    public kelondroCollectionIndex(File path, String filenameStub, int keyLength, kelondroOrder indexOrder,
                                   long buffersize, long preloadTime,
                                   int loadfactor, kelondroRow rowdef) throws IOException {
        // the buffersize is number of bytes that are only used if the kelondroFlexTable is backed up with a kelondroTree
        this.path = path;
        this.filenameStub = filenameStub;
        this.playloadrow = rowdef;
        this.loadfactor = loadfactor;

        // create index table
        index = new kelondroFlexTable(path, filenameStub + ".index.table", indexOrder, buffersize, preloadTime, indexRow(keyLength));

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
        
        // open array files
        this.arrays = new HashMap(); // all entries will be dynamically created with getArray()
    }
    
    private kelondroFixedWidthArray openArrayFile(int partitionNumber, int serialNumber, boolean create) throws IOException {
        File f = arrayFile(path, filenameStub, loadfactor, playloadrow.objectsize(), partitionNumber, serialNumber);
        int load = arrayCapacity(partitionNumber);
        kelondroRow rowdef = new kelondroRow(
                "byte[] key-" + index.row().width(0) + "," +
                "byte[] collection-" + (kelondroRowCollection.exportOverheadSize + load * this.playloadrow.objectsize())
                );
        if ((!(f.exists())) && (!create)) return null;
        return new kelondroFixedWidthArray(f, rowdef, 0);
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
            kelondroRow.Entry oldindexrow = index.get(key);
        
            if (oldindexrow == null) {
                if ((collection != null) && (collection.size() > 0)) {
                    // the collection is new
                    overwrite(key, collection);
                }
                return 0;
            }
                // overwrite the old collection
                // read old information
                int oldchunksize  = (int) oldindexrow.getColLong(idx_col_chunksize); // needed only for migration
                int oldchunkcount = (int) oldindexrow.getColLong(idx_col_chunkcount);
                int oldrownumber  = (int) oldindexrow.getColLong(idx_col_indexpos);
                int oldPartitionNumber = arrayIndex(oldchunkcount);
                int oldSerialNumber = 0;
            
                if (merge) {
                    // load the old collection and join it with the old
                    kelondroRowSet oldcollection = getdelete(oldindexrow, false, false);
                    
                    // join with new collection
                    oldcollection.addAll(collection);
                    collection = oldcollection;
                }
            
                int removed = 0;
                if (removekeys != null) {
                    // load the old collection and remove keys
                    kelondroRowSet oldcollection = getdelete(oldindexrow, false, false);

                    // remove the keys from the set
                    Iterator i = removekeys.iterator();
                    Object k;
                    while (i.hasNext()) {
                        k = i.next();
                        if (k instanceof byte[]) {if (oldcollection.remove((byte[]) k) != null) removed++;}
                        if (k instanceof String) {if (oldcollection.remove(((String) k).getBytes()) != null) removed++;}
                    }
                    collection = oldcollection;
                }
            
                if (collection.size() == 0) {
                    if (deletecomplete) {
                        kelondroFixedWidthArray array = getArray(oldPartitionNumber, oldSerialNumber, oldchunksize);
                        array.remove(oldrownumber);
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
                    oldindexrow.setCol(idx_col_chunkcount, collection.size());
                    oldindexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
                    index.put(oldindexrow);
                } else {
                    // we need a new slot, that means we must first delete the old entry
                    // find array file
                    kelondroFixedWidthArray array = getArray(oldPartitionNumber, oldSerialNumber, oldchunksize);
                
                    // delete old entry
                    array.remove(oldrownumber);
                
                    // write a new entry in the other array
                    overwrite(key, collection);
                }
                return removed;
        }
    }

    private void overwrite(byte[] key, kelondroRowCollection collection) throws IOException {
        // helper method, should not be called directly and only within a synchronized(index) environment
        // simply store a collection without check if the collection existed before
        
        // find array file
        kelondroFixedWidthArray array = getArray(arrayIndex(collection.size()), 0, this.playloadrow.objectsize());
        
        // define row
        kelondroRow.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());
        
        // write a new entry in this array
        int newRowNumber = array.add(arrayEntry);
        
        // store the new row number in the index
        kelondroRow.Entry indexEntry = index.row().newEntry();
        indexEntry.setCol(idx_col_key, key);
        indexEntry.setCol(idx_col_chunksize, this.playloadrow.objectsize());
        indexEntry.setCol(idx_col_chunkcount, collection.size());
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
            return getdelete(indexrow, true, false);
        }
    }
    
    protected kelondroRowSet getdelete(kelondroRow.Entry indexrow, boolean remove, boolean deleteIfEmpty) throws IOException {
        // call this only within a synchronized(index) environment
        
        // read values
        int chunksize  = (int) indexrow.getColLong(idx_col_chunksize);
        int chunkcount = (int) indexrow.getColLong(idx_col_chunkcount);
        int rownumber  = (int) indexrow.getColLong(idx_col_indexpos);
        int partitionnumber = arrayIndex(chunkcount);
        int serialnumber = 0;
        
        // open array entry
        kelondroFixedWidthArray array = getArray(partitionnumber, serialnumber, chunksize);
        kelondroRow.Entry arrayrow = array.get(rownumber);
        if (arrayrow == null) throw new kelondroException(arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, partitionnumber, serialnumber).toString(), "array does not contain expected row");

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
            indexEntry.setCol(idx_col_indexpos, (long) rownumber);
            indexEntry.setCol(idx_col_lastread, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
            indexEntry.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
            index.put(indexEntry);
            throw new kelondroException(arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, partitionnumber, serialnumber).toString(), "array contains wrong row '" + new String(arrayrow.getColBytes(0)) + "', expected is '" + new String(indexrow.getColBytes(idx_col_key)) + "', the row has been fixed");
        }
        int chunkcountInArray = collection.size();
        if (chunkcountInArray != chunkcount) {
            // fix the entry in index
            indexrow.setCol(idx_col_chunkcount, chunkcountInArray);
            index.put(indexrow);
            array.logFailure("INCONSISTENCY in " + arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, partitionnumber, serialnumber).toString() + ": array has different chunkcount than index: index = " + chunkcount + ", array = " + chunkcountInArray + "; the index has been auto-fixed");
        }
        
        if ((remove) || ((chunkcountInArray == 0) && (deleteIfEmpty))) array.remove(rownumber);
        
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
        this.index.close();
        Iterator i = arrays.values().iterator();
        while (i.hasNext()) {
            ((kelondroFixedWidthArray) i.next()).close();
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
            
            collectionIndex.close();
            
            // printout of index
            kelondroFlexTable index = new kelondroFlexTable(path, filenameStub + ".index", kelondroNaturalOrder.naturalOrder, buffersize, preloadTime, indexRow(9));
            index.print();
            index.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
