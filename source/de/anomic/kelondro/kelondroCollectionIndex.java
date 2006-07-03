package de.anomic.kelondro;

// a collectionIndex is an index to collection (kelondroCollection) objects
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

public class kelondroCollectionIndex {

    private kelondroIndex index;
    private File path;
    private String filenameStub;
    private int loadfactor;
    //private int partitions;
    private int maxChunks;
    private kelondroFixedWidthArray[] array;
    private int[] arrayCapacity;
    private kelondroRow rowdef;
    
    private static File arrayFile(File path, String filenameStub, int loadfactor, int chunksize, int partitionNumber) {

        String lf = Integer.toHexString(loadfactor).toUpperCase();
        while (lf.length() < 2) lf = "0" + lf;
        String cs = Integer.toHexString(chunksize).toUpperCase();
        while (cs.length() < 4) cs = "0" + cs;
        String pn = Integer.toHexString(partitionNumber).toUpperCase();
        while (pn.length() < 2) pn = "0" + pn;
        return new File(path, filenameStub + "." + lf + "." + cs + "." + pn + ".kca"); // kelondro collection array
    }

    private static final long day = 1000 * 60 * 60 * 24;
    
    private static int daysSince2000(long time) {
        return (int) (time / day) - 10957;
    }
    
    public kelondroCollectionIndex(File path, String filenameStub, int keyLength, kelondroOrder indexOrder, long buffersize,
                                   int loadfactor, kelondroRow rowdef, int partitions) throws IOException {
        this.path = path;
        this.filenameStub = filenameStub;
        this.rowdef = rowdef;
        //this.partitions = partitions;
        this.loadfactor = loadfactor;

        // create index file(s)
        int[] columns;
        columns = new int[3];
        columns[0] = keyLength;
        columns[1] = 4; // chunksize (number of bytes in a single chunk, needed for migration option)
        columns[2] = 4; // chunkcount (number of chunks in this collection)
        columns[3] = 4; // index (position in index file)
        columns[4] = 2; // update time in days since 1.1.2000
        index = new kelondroSplittedTree(path, filenameStub, indexOrder, buffersize, 8, new kelondroRow(columns), 1, 80, true);

        // create array files
        this.array = new kelondroFixedWidthArray[partitions];
        this.arrayCapacity = new int[partitions];
        
        // open array files
        int load = 1;
        
        for (int i = 0; i < partitions; i++) {
            load = load * loadfactor;
            array[i] = openArrayFile(i);
            arrayCapacity[i] = load;
        }
        this.maxChunks = load;
    }
    
    private kelondroFixedWidthArray openArrayFile(int partitionNumber) throws IOException {
        File f = arrayFile(path, filenameStub, loadfactor, rowdef.objectsize(), partitionNumber);
        
        if (f.exists()) {
            return new kelondroFixedWidthArray(f);
        } else {
            int load = 1; for (int i = 0; i < partitionNumber; i++) load = load * loadfactor;
            int[] columns = new int[4];
            columns[0] = index.row().width(0); // add always the key
            columns[1] = 4; // chunkcount (raw format)
            columns[2] = 2; // last time read
            columns[3] = 2; // last time wrote
            columns[4] = 2; // flag string, assigns collection order as currently stored in table
            columns[5] = load * rowdef.objectsize();
            return new kelondroFixedWidthArray(f, new kelondroRow(columns), 0, true);
        }
    }
    
    private int arrayIndex(int requestedCapacity) throws kelondroOutOfLimitsException{
        // the requestedCapacity is the number of wanted chunks
        for (int i = 0; i < arrayCapacity.length; i++) {
            if (arrayCapacity[i] >= requestedCapacity) return i;
        }
        throw new kelondroOutOfLimitsException(maxChunks, requestedCapacity);
    }
    
    public int size() throws IOException {
        return index.size();
    }
    
    public void put(byte[] key, kelondroRowCollection collection) throws IOException, kelondroOutOfLimitsException {
        if (collection.size() > maxChunks) throw new kelondroOutOfLimitsException(maxChunks, collection.size());

        // first find an old entry, if one exists
        kelondroRow.Entry oldindexrow = index.get(key);
        
        // define the new storage array
        byte[][] newarrayrow = new byte[][]{key,
                                            kelondroNaturalOrder.encodeLong((long) collection.size(), 4),
                                            null /*collection.getOrderingSignature().getBytes()*/,
                                            collection.toByteArray()};
        if (oldindexrow == null) {
            // the collection is new
            // find appropriate partition for the collection:
            int part = arrayIndex(collection.size());
            
            // write a new entry in this array
            int newRowNumber = array[part].add(array[part].row().newEntry(newarrayrow));
            // store the new row number in the index
            kelondroRow.Entry e = index.row().newEntry();
            e.setCol(0, key);
            e.setColLongB256(1, this.rowdef.objectsize());
            e.setColLongB256(2, collection.size());
            e.setColLongB256(3, (long) newRowNumber);
            e.setColLongB256(4, daysSince2000(System.currentTimeMillis()));
            index.put(e);
        } else {
            // overwrite the old collection
            // read old information
            //int chunksize  = (int) kelondroNaturalOrder.decodeLong(oldindexrow[1]); // needed only for migration
            int chunkcount = (int) oldindexrow.getColLongB256(2);
            int rownumber  = (int) oldindexrow.getColLongB256(3);
            int oldPartitionNumber = arrayIndex(chunkcount);
            int newPartitionNumber = arrayIndex(collection.size());
            
            // see if we need new space or if we can overwrite the old space
            if (oldPartitionNumber == newPartitionNumber) {
                // we don't need a new slot, just write in the old one
                array[oldPartitionNumber].set(rownumber, array[oldPartitionNumber].row().newEntry(newarrayrow));
                // update the index entry
                kelondroRow.Entry e = index.row().newEntry();
                e.setCol(0, key);
                e.setColLongB256(1, this.rowdef.objectsize());
                e.setColLongB256(2, collection.size());
                e.setColLongB256(3, (long) rownumber);
                e.setColLongB256(4, daysSince2000(System.currentTimeMillis()));
                index.put(e);
            } else {
                // we need a new slot, that means we must first delete the old entry
                array[oldPartitionNumber].remove(rownumber);
                // write a new entry in the other array
                int newRowNumber = array[newPartitionNumber].add(array[newPartitionNumber].row().newEntry(newarrayrow));
                // store the new row number in the index
                kelondroRow.Entry e = index.row().newEntry();
                e.setCol(0, key);
                e.setColLongB256(1, this.rowdef.objectsize());
                e.setColLongB256(2, collection.size());
                e.setColLongB256(3, (long) newRowNumber);
                e.setColLongB256(4, daysSince2000(System.currentTimeMillis()));
                index.put(e);
            }
        }
    }
    
    public kelondroRowCollection get(byte[] key) throws IOException {
        // find an entry, if one exists
        kelondroRow.Entry indexrow = index.get(key);
        if (indexrow == null) return null;
        // read values
        int chunksize  = (int) indexrow.getColLongB256(1);
        int chunkcount = (int) indexrow.getColLongB256(2);
        int rownumber  = (int) indexrow.getColLongB256(3);
        int partitionnumber = arrayIndex(chunkcount);
        // open array entry
        kelondroRow.Entry arrayrow = array[partitionnumber].get(rownumber);
        if (arrayrow == null) throw new kelondroException(arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, partitionnumber).toString(), "array does not contain expected row");
        // read the row and define a collection
        int chunkcountInArray = (int) arrayrow.getColLongB256(1);
        if (chunkcountInArray != chunkcount) throw new kelondroException(arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, partitionnumber).toString(), "array has different chunkcount than index: index = " + chunkcount + ", array = " + chunkcountInArray);
        return new kelondroRowCollection(rowdef, chunkcount, arrayrow.getColBytes(3));
    }
    
    public void remove(byte[] key) throws IOException {
        // find an entry, if one exists
        kelondroRow.Entry indexrow = index.get(key);
        if (indexrow == null) return;
        // read values
        //int chunksize  = (int) kelondroNaturalOrder.decodeLong(indexrow[1]);
        int chunkcount = (int) indexrow.getColLongB256(2);
        int rownumber  = (int) indexrow.getColLongB256(3);
        int partitionnumber = arrayIndex(chunkcount);
        // remove array entry
        array[partitionnumber].remove(rownumber);
    }

    
    public static void main(String[] args) {
        System.out.println(new java.util.Date(10957 * day));
        System.out.println(new java.util.Date(0));
        System.out.println(daysSince2000(System.currentTimeMillis()));
    }
}
