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
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import de.anomic.index.indexContainer;
import de.anomic.kelondro.kelondroRow.EntryIndex;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class kelondroCollectionIndex {

    private static final int serialNumber = 0;
    private static final long minimumRAM4Eco = 200 * 1024 * 1024;
    private static final int EcoFSBufferSize = 1000;
    
    private kelondroIndex index;
    private int           keylength;
    private File          path;
    private String        filenameStub;
    private File          commonsPath;
    private int           loadfactor;
    private Map<String, kelondroFixedWidthArray> arrays; // Map of (partitionNumber"-"chunksize)/kelondroFixedWidthArray - Objects
    private kelondroRow   payloadrow; // definition of the payload (chunks inside the collections)
    private int           maxPartitions;  // this is the maxmimum number of array files
    
    private static final int idx_col_key        = 0;  // the index
    private static final int idx_col_chunksize  = 1;  // chunksize (number of bytes in a single chunk, needed for migration option)
    private static final int idx_col_chunkcount = 2;  // chunkcount (number of chunks in this collection)
    private static final int idx_col_clusteridx = 3;  // selector for right cluster file, must be >= arrayIndex(chunkcount)
    private static final int idx_col_flags      = 4;  // flags (for future use)
    private static final int idx_col_indexpos   = 5;  // indexpos (position in array file)
    private static final int idx_col_lastread   = 6;  // a time stamp, update time in days since 1.1.2000
    private static final int idx_col_lastwrote  = 7;  // a time stamp, update time in days since 1.1.2000
    
    private static kelondroRow indexRow(int keylength, kelondroByteOrder payloadOrder) {
        return new kelondroRow(
            "byte[] key-" + keylength + "," +
            "int chunksize-4 {b256}," +
            "int chunkcount-4 {b256}," +
            "byte clusteridx-1 {b256}," +
            "byte flags-1 {b256}," +
            "int indexpos-4 {b256}," +
            "short lastread-2 {b256}, " +
            "short lastwrote-2 {b256}",
            payloadOrder, 0
            );
    }
    
    public kelondroRow payloadRow() {
        return this.payloadrow;
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
    
    public kelondroCollectionIndex(File path, String filenameStub, int keyLength, kelondroByteOrder indexOrder,
                                   long preloadTime, int loadfactor, int maxpartitions, kelondroRow rowdef) throws IOException {
        // the buffersize is number of bytes that are only used if the kelondroFlexTable is backed up with a kelondroTree
        this.path = path;
        this.filenameStub = filenameStub;
        this.keylength = keyLength;
        this.payloadrow = rowdef;
        this.loadfactor = loadfactor;
        this.maxPartitions = maxpartitions;
        this.commonsPath = new File(path, filenameStub + "." + fillZ(Integer.toHexString(rowdef.objectsize).toUpperCase(), 4) + ".commons");
        this.commonsPath.mkdirs();
        File f = new File(path, filenameStub + ".index");
        
        if (f.exists()) {
            serverLog.logFine("STARTUP", "OPENING COLLECTION INDEX");
            
            // open index and array files
            this.arrays = new HashMap<String, kelondroFixedWidthArray>(); // all entries will be dynamically created with getArray()
            index = openIndexFile(path, filenameStub, indexOrder, preloadTime, loadfactor, rowdef, 0);
            openAllArrayFiles(false, indexOrder);
        } else {
            // calculate initialSpace
            String[] list = this.path.list();
            kelondroFixedWidthArray array;
            int initialSpace = 0;
            for (int i = 0; i < list.length; i++) if (list[i].endsWith(".kca")) {
                // open array
                int pos = list[i].indexOf('.');
                if (pos < 0) continue;
                int partitionNumber = Integer.parseInt(list[i].substring(pos +  9, pos + 11), 16);
                int serialNumber    = Integer.parseInt(list[i].substring(pos + 12, pos + 14), 16);
                try {
                    array = openArrayFile(partitionNumber, serialNumber, indexOrder, true);
                    initialSpace += array.size();
                    array.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            serverLog.logFine("STARTUP", "STARTED INITIALIZATION OF NEW COLLECTION INDEX WITH " + initialSpace + " ENTRIES. THIS WILL TAKE SOME TIME");
            kelondroRow indexRowdef = indexRow(keyLength, indexOrder);
            long necessaryRAM4fullTable = minimumRAM4Eco + (indexRowdef.objectsize + 4) * initialSpace * 3 / 2;
            long necessaryRAM4fullIndex = minimumRAM4Eco + (indexRowdef.primaryKeyLength + 4) * initialSpace * 3 / 2;
            
            // initialize (new generation) index table from file
            if (serverMemory.request(necessaryRAM4fullTable, false)) {
                index = new kelondroEcoTable(f, indexRowdef, kelondroEcoTable.tailCacheUsageAuto, EcoFSBufferSize);
            } else if (serverMemory.request(necessaryRAM4fullIndex, false)) {
                index = new kelondroEcoTable(f, indexRowdef, kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize);
            } else {
                index = new kelondroFlexTable(path, filenameStub + ".index", preloadTime, indexRowdef, initialSpace, true);
            }
            
            // open array files
            this.arrays = new HashMap<String, kelondroFixedWidthArray>(); // all entries will be dynamically created with getArray()
            openAllArrayFiles(true, indexOrder);
        }
    }
    
    private void openAllArrayFiles(boolean indexGeneration, kelondroByteOrder indexOrder) throws IOException {
        
        String[] list = this.path.list();
        kelondroFixedWidthArray array;
        
        kelondroRow irow = indexRow(keylength, indexOrder);
        int t = kelondroRowCollection.daysSince2000(System.currentTimeMillis());
        for (int i = 0; i < list.length; i++) if (list[i].endsWith(".kca")) {

            // open array
            int pos = list[i].indexOf('.');
            if (pos < 0) continue;
            int chunksize       = Integer.parseInt(list[i].substring(pos +  4, pos +  8), 16);
            int partitionNumber = Integer.parseInt(list[i].substring(pos +  9, pos + 11), 16);
            int serialNumber    = Integer.parseInt(list[i].substring(pos + 12, pos + 14), 16);
            try {
                array = openArrayFile(partitionNumber, serialNumber, indexOrder, true);
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            
            // remember that we opened the array
            arrays.put(partitionNumber + "-" + chunksize, array);
            
            if ((index != null) && (indexGeneration)) {
                // loop over all elements in array and create index entry for each row
                kelondroRow.EntryIndex aentry;
                kelondroRow.Entry      ientry;
                Iterator<EntryIndex> ei = array.contentRows(-1);
                byte[] key;
                long start = System.currentTimeMillis();
                long lastlog = start;
                int count = 0;
                while (ei.hasNext()) {
                    aentry = (kelondroRow.EntryIndex) ei.next();
                    key = aentry.getColBytes(0);
                    assert (key != null);
                    if (key == null) continue; // skip deleted entries
                    ientry = irow.newEntry();
                    ientry.setCol(idx_col_key,        key);
                    ientry.setCol(idx_col_chunksize,  chunksize);
                    ientry.setCol(idx_col_chunkcount, kelondroRowCollection.sizeOfExportedCollectionRows(aentry, 1));
                    ientry.setCol(idx_col_clusteridx, (byte) partitionNumber);
                    ientry.setCol(idx_col_flags,      (byte) 0);
                    ientry.setCol(idx_col_indexpos,   aentry.index());
                    ientry.setCol(idx_col_lastread,   t);
                    ientry.setCol(idx_col_lastwrote,  t);
                    index.addUnique(ientry); // FIXME: this should avoid doubles
                    count++;
                    
                    // write a log
                    if (System.currentTimeMillis() - lastlog > 30000) {
                        serverLog.logFine("STARTUP", "created " + count + " RWI index entries. " + (((System.currentTimeMillis() - start) * (array.size() + array.free() - count) / count) / 60000) + " minutes remaining for this array");
                        lastlog = System.currentTimeMillis();
                    }
                }
            }
        }
        // care for double entries
        ArrayList<kelondroRowSet> del = index.removeDoubles();
        Iterator<kelondroRowSet> j = del.iterator();
        kelondroRowSet rowset;
        Iterator<kelondroRow.Entry> rowiter;
        int partition, maxpartition;
        kelondroRow.Entry entry, maxentry;
        int doublecount = 0;
        while (j.hasNext()) {
            rowset = j.next();
            // for each entry in row set choose one which we want to keep
            rowiter = rowset.rows();
            maxentry = null;
            maxpartition = -1;
            while (rowiter.hasNext()) {
                entry = rowiter.next();
                partition = (int) entry.getColLong(idx_col_clusteridx);
                if (partition > maxpartition) {
                    maxpartition = partition;
                    maxentry = entry;
                }
            }
            if (maxentry != null) {
                // put back a single entry to the index, which is then not double to any other entry
                index.put(maxentry);
                doublecount++;
            }
        }
        if (doublecount > 0) serverLog.logWarning("STARTUP", "found " + doublecount + " RWI entries with references to several collections. All have been fixed (zombies still exists).");
    }
    
    private kelondroIndex openIndexFile(File path, String filenameStub, kelondroByteOrder indexOrder,
            long preloadTime, int loadfactor, kelondroRow rowdef, int initialSpace) throws IOException {
        // open/create index table
        File f = new File(path, filenameStub + ".index");
        kelondroRow indexRowdef = indexRow(keylength, indexOrder);
        
        if (f.isDirectory()) {
            // use a flextable
            kelondroIndex theindex = new kelondroCache(new kelondroFlexTable(path, filenameStub + ".index", preloadTime, indexRowdef, initialSpace, true));
        
            // save/check property file for this array
            File propfile = propertyFile(path, filenameStub, loadfactor, rowdef.objectsize);
            Map<String, String> props = new HashMap<String, String>();
            if (propfile.exists()) {
                props = serverFileUtils.loadHashMap(propfile);
                String stored_rowdef = (String) props.get("rowdef");
                if ((stored_rowdef == null) || (!(rowdef.subsumes(new kelondroRow(stored_rowdef, rowdef.objectOrder, 0))))) {
                    System.out.println("FATAL ERROR: stored rowdef '" + stored_rowdef + "' does not match with new rowdef '" + 
                            rowdef + "' for array cluster '" + path + "/" + filenameStub + "'");
                    System.exit(-1);
                }
            }
            props.put("rowdef", rowdef.toString());
            serverFileUtils.saveMap(propfile, props, "CollectionIndex properties");
        
            return theindex;
        } else {
            // open a ecotable
            long records = f.length() / indexRowdef.objectsize;
            long necessaryRAM4fullTable = minimumRAM4Eco + (indexRowdef.objectsize + 4) * records * 3 / 2;
            return new kelondroEcoTable(f, indexRowdef, (serverMemory.request(necessaryRAM4fullTable, false)) ? kelondroEcoTable.tailCacheUsageAuto : kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize);
        }
    }
    
    private kelondroFixedWidthArray openArrayFile(int partitionNumber, int serialNumber, kelondroByteOrder indexOrder, boolean create) throws IOException {
        File f = arrayFile(path, filenameStub, loadfactor, payloadrow.objectsize, partitionNumber, serialNumber);
        int load = arrayCapacity(partitionNumber);
        kelondroRow rowdef = new kelondroRow(
                "byte[] key-" + keylength + "," +
                "byte[] collection-" + (kelondroRowCollection.exportOverheadSize + load * this.payloadrow.objectsize),
                indexOrder,
                0
                );
        if ((!(f.exists())) && (!create)) return null;
        kelondroFixedWidthArray a = new kelondroFixedWidthArray(f, rowdef, 0);
        serverLog.logFine("STARTUP", "opened array file " + f + " with " + a.size() + " RWIs");
        return a;
    }
    
    private kelondroFixedWidthArray getArray(int partitionNumber, int serialNumber, kelondroByteOrder indexOrder, int chunksize) {
        String accessKey = partitionNumber + "-" + chunksize;
        kelondroFixedWidthArray array = (kelondroFixedWidthArray) arrays.get(accessKey);
        if (array != null) return array;
        try {
            array = openArrayFile(partitionNumber, serialNumber, indexOrder, true);
        } catch (IOException e) {
            return null;
        }
        arrays.put(accessKey, array);
        return array;
    }
    
    private int arrayCapacity(int arrayCounter) {
        if (arrayCounter < 0) return 0;
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
    
    public int size() {
        return index.size();
    }
    
    public int minMem() {
        // calculate a minimum amount of memory that is necessary to use the collection
        // during runtime (after the index was initialized)
        
        // caclculate an upper limit (not the correct size) of the maximum number of indexes for a wordHash
        // this is computed by the size of the biggest used collection
        // this must be multiplied with the payload size
        // and doubled for necessary memory transformation during sort operation
        return (int) (arrayCapacity(arrays.size() - 1) * this.payloadrow.objectsize * kelondroRowSet.growfactor);
    }
    
    private void array_remove(
            int oldPartitionNumber, int serialNumber, int chunkSize,
            int oldRownumber) throws IOException {
        // we need a new slot, that means we must first delete the old entry
        // find array file
        kelondroFixedWidthArray array = getArray(oldPartitionNumber, serialNumber, index.row().objectOrder, chunkSize);

        // delete old entry
        array.remove(oldRownumber);
    }
    
    private kelondroRow.Entry array_new(
            byte[] key, kelondroRowCollection collection) throws IOException {
        // the collection is new
        int partitionNumber = arrayIndex(collection.size());
        kelondroRow.Entry indexrow = index.row().newEntry();
        kelondroFixedWidthArray array = getArray(partitionNumber, serialNumber, index.row().objectOrder, this.payloadrow.objectsize);

        // define row
        kelondroRow.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());

        // write a new entry in this array
        int newRowNumber = array.add(arrayEntry);

        // store the new row number in the index
        indexrow.setCol(idx_col_key, key);
        indexrow.setCol(idx_col_chunksize, this.payloadrow.objectsize);
        indexrow.setCol(idx_col_chunkcount, collection.size());
        indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
        indexrow.setCol(idx_col_flags, (byte) 0);
        indexrow.setCol(idx_col_indexpos, (long) newRowNumber);
        indexrow.setCol(idx_col_lastread, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
        indexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));

        // after calling this method there must be an index.addUnique(indexrow);
        return indexrow;
    }
    
    private void array_add(
            byte[] key, kelondroRowCollection collection, kelondroRow.Entry indexrow,
            int partitionNumber, int serialNumber, int chunkSize) throws IOException {

        // write a new entry in the other array
        kelondroFixedWidthArray array = getArray(partitionNumber, serialNumber, index.row().objectOrder, chunkSize);
        
        // define new row
        kelondroRow.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());
        
        // write a new entry in this array
        int rowNumber = array.add(arrayEntry);
        
        // store the new row number in the index
        indexrow.setCol(idx_col_chunkcount, collection.size());
        indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
        indexrow.setCol(idx_col_indexpos, (long) rowNumber);
        indexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));

        // after calling this method there must be a index.put(indexrow);
    }
    
    private ArrayList<kelondroRow.Entry> array_add_multiple(TreeMap<Integer, ArrayList<Object[]>> array_add_map, int serialNumber, int chunkSize) throws IOException {
        // returns a List of kelondroRow.Entry entries for indexrow storage
        Map.Entry<Integer, ArrayList<Object[]>> entry;
        Iterator<Map.Entry<Integer, ArrayList<Object[]>>> i = array_add_map.entrySet().iterator();
        Iterator<Object[]> j;
        ArrayList<Object[]> actionList;
        int partitionNumber;
        kelondroFixedWidthArray array;
        Object[] objs;
        byte[] key;
        kelondroRowCollection collection;
        kelondroRow.Entry indexrow;
        ArrayList<kelondroRow.Entry> indexrows = new ArrayList<kelondroRow.Entry>();
        while (i.hasNext()) {
            entry = i.next();
            actionList = entry.getValue();
            partitionNumber = entry.getKey().intValue();
            array = getArray(partitionNumber, serialNumber, index.row().objectOrder, chunkSize);
            j = actionList.iterator();
            while (j.hasNext()) {
                objs = (Object[]) j.next();
                key = (byte[]) objs[0];
                collection = (kelondroRowCollection) objs[1];
                indexrow = (kelondroRow.Entry) objs[2];
                
                // define new row
                kelondroRow.Entry arrayEntry = array.row().newEntry();
                arrayEntry.setCol(0, key);
                arrayEntry.setCol(1, collection.exportCollection());
        
                // write a new entry in this array
                int rowNumber = array.add(arrayEntry);
        
                // store the new row number in the index
                indexrow.setCol(idx_col_chunkcount, collection.size());
                indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
                indexrow.setCol(idx_col_indexpos, (long) rowNumber);
                indexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
                indexrows.add(indexrow);
            }
        }
        // after calling this method there must be a index.put(indexrow);
        return indexrows;
    }
    
    private void array_replace(
            byte[] key, kelondroRowCollection collection, kelondroRow.Entry indexrow,
            int partitionNumber, int serialNumber, int chunkSize,
            int rowNumber) throws IOException {
        // we don't need a new slot, just write collection into the old one

        // find array file
        kelondroFixedWidthArray array = getArray(partitionNumber, serialNumber, index.row().objectOrder, chunkSize);

        // define new row
        kelondroRow.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());

        // overwrite entry in this array
        array.set(rowNumber, arrayEntry);

        // update the index entry
        final int collectionsize = collection.size(); // extra variable for easier debugging
        indexrow.setCol(idx_col_chunkcount, collectionsize);
        indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
        indexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
        
        // after calling this method there must be a index.put(indexrow);
    }
    
    private ArrayList<kelondroRow.Entry> array_replace_multiple(TreeMap<Integer, TreeMap<Integer, Object[]>> array_replace_map, int serialNumber, int chunkSize) throws IOException {
        Map.Entry<Integer, TreeMap<Integer, Object[]>> entry;
        Map.Entry<Integer, Object[]> e;
        Iterator<Map.Entry<Integer, TreeMap<Integer, Object[]>>> i = array_replace_map.entrySet().iterator();
        Iterator<Map.Entry<Integer, Object[]>> j;
        TreeMap<Integer, Object[]> actionMap;
        int partitionNumber;
        kelondroFixedWidthArray array;
        ArrayList<kelondroRow.Entry> indexrows = new ArrayList<kelondroRow.Entry>();
        Object[] objs;
        int rowNumber;
        byte[] key;
        kelondroRowCollection collection;
        kelondroRow.Entry indexrow;
        while (i.hasNext()) {
            entry = i.next();
            actionMap = entry.getValue();
            partitionNumber = ((Integer) entry.getKey()).intValue();
            array = getArray(partitionNumber, serialNumber, index.row().objectOrder, chunkSize);
        
            j = actionMap.entrySet().iterator();
            while (j.hasNext()) {
                e = j.next();
                rowNumber = ((Integer) e.getKey()).intValue();
                objs = (Object[]) e.getValue();
                key = (byte[]) objs[0];
                collection = (kelondroRowCollection) objs[1];
                indexrow = (kelondroRow.Entry) objs[2];
                
                // define new row
                kelondroRow.Entry arrayEntry = array.row().newEntry();
                arrayEntry.setCol(0, key);
                arrayEntry.setCol(1, collection.exportCollection());

                // overwrite entry in this array
                array.set(rowNumber, arrayEntry);

                // update the index entry
                indexrow.setCol(idx_col_chunkcount, collection.size());
                indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
                indexrow.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
                indexrows.add(indexrow);
            }
        }        
        // after calling this method there mus be a index.put(indexrow);
        return indexrows;
    }
    
    public synchronized void put(byte[] key, kelondroRowCollection collection) throws IOException, kelondroOutOfLimitsException {
    	assert (key != null);
    	assert (collection != null);
    	assert (collection.size() != 0);
    	
        // first find an old entry, if one exists
        kelondroRow.Entry indexrow = index.get(key);
        
        if (indexrow == null) {
            // create new row and index entry
            if ((collection != null) && (collection.size() > 0)) {
                indexrow = array_new(key, collection); // modifies indexrow
                index.addUnique(indexrow);
            }
            return;
        }
            
        // overwrite the old collection
        // read old information
        //int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize);  // needed only for migration
        int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount); // the number if rows in the collection
        int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);   // index of the entry in array
        int oldPartitionNumber = (int) indexrow.getColByte(idx_col_clusteridx); // points to array file
        assert (oldPartitionNumber >= arrayIndex(oldchunkcount));

        int newPartitionNumber = arrayIndex(collection.size());

        // see if we need new space or if we can overwrite the old space
        if (oldPartitionNumber == newPartitionNumber) {
            array_replace(
                    key, collection, indexrow,
                    oldPartitionNumber, serialNumber, this.payloadrow.objectsize,
                    oldrownumber); // modifies indexrow
        } else {
            array_remove(
                    oldPartitionNumber, serialNumber, this.payloadrow.objectsize,
                    oldrownumber);
            array_add(
                    key, collection, indexrow,
                    newPartitionNumber, serialNumber, this.payloadrow.objectsize); // modifies indexrow
        }
        
        if ((int) indexrow.getColLong(idx_col_chunkcount) != collection.size())
        	serverLog.logSevere("kelondroCollectionIndex", "UPDATE (put) ERROR: array has different chunkcount than index after merge: index = " + (int) indexrow.getColLong(idx_col_chunkcount) + ", collection.size() = " + collection.size());
        
        index.put(indexrow); // write modified indexrow
    }
    
    public synchronized void mergeMultiple(List<indexContainer> containerList) throws IOException, kelondroOutOfLimitsException {
        // merge a bulk of index containers
        // this method should be used to optimize the R/W head path length
        
        // separate the list in two halves:
        // - containers that do not exist yet in the collection
        // - containers that do exist in the collection and must be merged
        Iterator<indexContainer> i = containerList.iterator();
        indexContainer container;
        byte[] key;
        ArrayList<Object[]> newContainer = new ArrayList<Object[]>();
        TreeMap<Integer, TreeMap<Integer, Object[]>> existingContainer = new TreeMap<Integer, TreeMap<Integer, Object[]>>(); // a mapping from Integer (partition) to a TreeMap (mapping from index to object triple)
        TreeMap<Integer, Object[]> containerMap; // temporary map; mapping from index position to object triple with {key, container, indexrow}
        kelondroRow.Entry indexrow;
        int oldrownumber1;       // index of the entry in array
        int oldPartitionNumber1; // points to array file
        while (i.hasNext()) {
            container = (indexContainer) i.next();
            
            if ((container == null) || (container.size() == 0)) continue;
            key = container.getWordHash().getBytes();
            
            // first find an old entry, if one exists
            indexrow = index.get(key);
            if (indexrow == null) {
                newContainer.add(new Object[]{key, container});
            } else {
                oldrownumber1       = (int) indexrow.getColLong(idx_col_indexpos);
                oldPartitionNumber1 = (int) indexrow.getColByte(idx_col_clusteridx);
                containerMap = existingContainer.get(new Integer(oldPartitionNumber1));
                if (containerMap == null) containerMap = new TreeMap<Integer, Object[]>();
                containerMap.put(new Integer(oldrownumber1), new Object[]{key, container, indexrow});
                existingContainer.put(new Integer(oldPartitionNumber1), containerMap);
            }
        }
        
        // now iterate through the container lists and execute merges
        // this is done in such a way, that there is a optimized path for the R/W head
        
        // merge existing containers
        Map.Entry<Integer, Object[]> tripleEntry;
        Object[] record;
        ArrayList<kelondroRow.Entry> indexrows_existing = new ArrayList<kelondroRow.Entry>();
        kelondroRowCollection collection;
        TreeMap<Integer, TreeMap<Integer, Object[]>> array_replace_map = new TreeMap<Integer, TreeMap<Integer, Object[]>>();
        TreeMap<Integer, ArrayList<Object[]>> array_add_map = new TreeMap<Integer, ArrayList<Object[]>>();
        ArrayList<Object[]> actionList;
        TreeMap<Integer, Object[]> actionMap;
        //boolean madegc = false;
        //System.out.println("DEBUG existingContainer: " + existingContainer.toString());
        while (existingContainer.size() > 0) {
            oldPartitionNumber1 = ((Integer) existingContainer.lastKey()).intValue();
            containerMap = existingContainer.remove(new Integer(oldPartitionNumber1));
            Iterator<Map.Entry<Integer, Object[]>> j = containerMap.entrySet().iterator();
            while (j.hasNext()) {
                tripleEntry = j.next();
                oldrownumber1 = ((Integer) tripleEntry.getKey()).intValue();
                record = (Object[]) tripleEntry.getValue(); // {byte[], indexContainer, kelondroRow.Entry}
            
                // merge with the old collection
                key = (byte[]) record[0];
                collection = (kelondroRowCollection) record[1];
                indexrow = (kelondroRow.Entry) record[2];

                // read old information
                int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize);  // needed only for migration
                int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount); // the number if rows in the collection
                int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);   // index of the entry in array
                int oldPartitionNumber = (int) indexrow.getColByte(idx_col_clusteridx); // points to array file
                assert oldPartitionNumber1 == oldPartitionNumber : "oldPartitionNumber1 = " + oldPartitionNumber1 + ", oldPartitionNumber = " + oldPartitionNumber + ", containerMap = " + containerMap + ", existingContainer: " + existingContainer.toString();
                assert oldrownumber1 == oldrownumber : "oldrownumber1 = " + oldrownumber1 + ", oldrownumber = " + oldrownumber + ", containerMap = " + containerMap + ", existingContainer: " + existingContainer.toString();
                assert (oldPartitionNumber >= arrayIndex(oldchunkcount));
                int oldSerialNumber = 0;

                // load the old collection and join it
                collection.addAllUnique(getwithparams(indexrow, oldchunksize, oldchunkcount, oldPartitionNumber, oldrownumber, oldSerialNumber, false));
                collection.sort();
                collection.uniq(); // FIXME: not clear if it would be better to insert the collection with put to avoid double-entries
                collection.trim(false);
                
                // check for size of collection:
                // if necessary shrink the collection and dump a part of that collection
                // to avoid that this grows too big
                if (arrayIndex(collection.size()) > maxPartitions) {
                    shrinkCollection(key, collection, arrayCapacity(maxPartitions));
                }
                
                // determine new partition position
                int newPartitionNumber = arrayIndex(collection.size());

                // see if we need new space or if we can overwrite the old space
                if (oldPartitionNumber == newPartitionNumber) {
                    actionMap = array_replace_map.get(new Integer(oldPartitionNumber));
                    if (actionMap == null) actionMap = new TreeMap<Integer, Object[]>();
                    actionMap.put(new Integer(oldrownumber), new Object[]{key, collection, indexrow});
                    array_replace_map.put(new Integer(oldPartitionNumber), actionMap);
                    /*
                    array_replace(
                            key, collection, indexrow,
                            oldPartitionNumber, oldSerialNumber, this.payloadrow.objectsize(),
                            oldrownumber); // modifies indexrow
                    indexrows_existing.add(indexrow); // indexrows are collected and written later as block
                     */
                } else {
                    array_remove(
                            oldPartitionNumber, oldSerialNumber, this.payloadrow.objectsize,
                            oldrownumber);
                
                    actionList = array_add_map.get(new Integer(newPartitionNumber));
                    if (actionList == null) actionList = new ArrayList<Object[]>();
                    actionList.add(new Object[]{key, collection, indexrow});
                    array_add_map.put(new Integer(newPartitionNumber), actionList);
                    /*
                    array_add(
                            key, collection, indexrow,
                            newPartitionNumber, oldSerialNumber, this.payloadrow.objectsize()); // modifies indexrow
                    indexrows_existing.add(indexrow); // indexrows are collected and written later as block
                    */
                }
                
                // memory protection: flush collected collections
                if (serverMemory.available() < minMem()) {
                    // emergency flush
                    indexrows_existing.addAll(array_replace_multiple(array_replace_map, 0, this.payloadrow.objectsize));
                    array_replace_map = new TreeMap<Integer, TreeMap<Integer, Object[]>>(); // delete references
                    indexrows_existing.addAll(array_add_multiple(array_add_map, 0, this.payloadrow.objectsize));
                    array_add_map = new TreeMap<Integer, ArrayList<Object[]>>(); // delete references
                    //if (!madegc) {
                    //    prevent that this flush is made again even when there is enough memory
                    serverMemory.gc(10000, "kelendroCollectionIndex.mergeMultiple(...)"); // thq
                    //    prevent that this gc happens more than one time
                    //    madegc = true;
                    //}
                }
            }
        }
        
        // finallly flush the collected collections
        indexrows_existing.addAll(array_replace_multiple(array_replace_map, 0, this.payloadrow.objectsize));
        array_replace_map = new TreeMap<Integer, TreeMap<Integer, Object[]>>(); // delete references
        indexrows_existing.addAll(array_add_multiple(array_add_map, 0, this.payloadrow.objectsize));
        array_add_map = new TreeMap<Integer, ArrayList<Object[]>>(); // delete references
        
        // write new containers
        Iterator<Object[]> k = newContainer.iterator();
        ArrayList<kelondroRow.Entry> indexrows_new = new ArrayList<kelondroRow.Entry>();
        while (k.hasNext()) {
            record = k.next(); // {byte[], indexContainer}
            key = (byte[]) record[0];
            collection = (indexContainer) record[1];
            indexrow = array_new(key, collection); // modifies indexrow
            indexrows_new.add(indexrow); // collect new index rows
        }
        
        // write index entries
        index.putMultiple(indexrows_existing); // write modified indexrows in optimized manner
        index.addUniqueMultiple(indexrows_new); // write new indexrows in optimized manner
    }
    
    public synchronized void merge(indexContainer container) throws IOException, kelondroOutOfLimitsException {
        if ((container == null) || (container.size() == 0)) return;
        byte[] key = container.getWordHash().getBytes();
        
        // first find an old entry, if one exists
        kelondroRow.Entry indexrow = index.get(key);
        if (indexrow == null) {
            indexrow = array_new(key, container); // modifies indexrow
            index.addUnique(indexrow); // write modified indexrow
        } else {
            // merge with the old collection
            // attention! this modifies the indexrow entry which must be written with index.put(indexrow) afterwards!
            kelondroRowCollection collection = (kelondroRowCollection) container;
            
            // read old information
            int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize);  // needed only for migration
            int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount); // the number if rows in the collection
            int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);   // index of the entry in array
            int oldPartitionNumber = (int) indexrow.getColByte(idx_col_clusteridx); // points to array file
            assert (oldPartitionNumber >= arrayIndex(oldchunkcount)) : "oldPartitionNumber = " + oldPartitionNumber + ", arrayIndex(oldchunkcount) = " + arrayIndex(oldchunkcount);
            int oldSerialNumber = 0;

            // load the old collection and join it
            collection.addAllUnique(getwithparams(indexrow, oldchunksize, oldchunkcount, oldPartitionNumber, oldrownumber, oldSerialNumber, false));
            collection.sort();
            collection.uniq(); // FIXME: not clear if it would be better to insert the collection with put to avoid double-entries
            collection.trim(false);
            
            // check for size of collection:
            // if necessary shrink the collection and dump a part of that collection
            // to avoid that this grows too big
            if (arrayIndex(collection.size()) > maxPartitions) {
                shrinkCollection(key, collection, arrayCapacity(maxPartitions));
            }
            
            // determine new partition location
            int newPartitionNumber = arrayIndex(collection.size());

            // see if we need new space or if we can overwrite the old space
            if (oldPartitionNumber == newPartitionNumber) {
                array_replace(
                        key, collection, indexrow,
                        oldPartitionNumber, oldSerialNumber, this.payloadrow.objectsize,
                        oldrownumber); // modifies indexrow
            } else {
                array_remove(
                        oldPartitionNumber, oldSerialNumber, this.payloadrow.objectsize,
                        oldrownumber);
                array_add(
                        key, collection, indexrow,
                        newPartitionNumber, oldSerialNumber, this.payloadrow.objectsize); // modifies indexrow
            }
            
            final int collectionsize = collection.size(); // extra variable for easier debugging
            final int indexrowcount = (int) indexrow.getColLong(idx_col_chunkcount);
            if (indexrowcount != collectionsize)
            	serverLog.logSevere("kelondroCollectionIndex", "UPDATE (merge) ERROR: array has different chunkcount than index after merge: index = " + indexrowcount + ", collection.size() = " + collectionsize);
            
            index.put(indexrow); // write modified indexrow
        }
    }
    
    private void shrinkCollection(byte[] key, kelondroRowCollection collection, int targetSize) {
        //TODO Remove timing before release
        // removes entries from collection
        // the removed entries are stored in a 'commons' dump file

        if (key.length != 12) return;
        // check if the collection is already small enough
        int oldsize = collection.size();
        if (oldsize <= targetSize) return;
        kelondroRowSet newcommon = new kelondroRowSet(collection.rowdef, 0);
        long sadd1 = 0, srem1 = 0, sadd2 = 0, srem2 = 0, tot1 = 0, tot2 = 0;
        long t1 = 0, t2 = 0;
        
        // delete some entries, which are bad rated
        Iterator<kelondroRow.Entry> i = collection.rows();
        kelondroRow.Entry entry;
        byte[] ref;
        t1 = System.currentTimeMillis();
        while (i.hasNext()) {
            entry = i.next();
            ref = entry.getColBytes(0);
            if ((ref.length != 12) || (!yacyURL.probablyRootURL(new String(ref)))) {
                t2 = System.currentTimeMillis();
                newcommon.addUnique(entry);
                sadd1 += System.currentTimeMillis() - t2;
                t2 = System.currentTimeMillis();
                i.remove();
                srem1 += System.currentTimeMillis() - t2;
            }
        }
        int firstnewcommon = newcommon.size();
        tot1 = System.currentTimeMillis() - t1;
        
        // check if we shrinked enough
        Random rand = new Random(System.currentTimeMillis());
        t1 = System.currentTimeMillis();
        while (collection.size() > targetSize) {
            // now delete randomly more entries from the survival collection
            i = collection.rows();
            while (i.hasNext()) {
                entry = (kelondroRow.Entry) i.next();
                ref = entry.getColBytes(0);
                if (rand.nextInt() % 4 != 0) {
                    t2 = System.currentTimeMillis();
                    newcommon.addUnique(entry);
                    sadd2 += System.currentTimeMillis() - t2;
                    t2 = System.currentTimeMillis();
                    i.remove();
                    srem2 += System.currentTimeMillis() - t2;
                }
            }
        }
        tot2 = System.currentTimeMillis() - t1;
        collection.trim(false);
        
        serverLog.logFine("kelondroCollectionIndex", "tot= "+tot1+'/'+tot2+" # add/rem(1)= "+sadd1+'/'+srem1+" # add/rem(2)= "+sadd2+'/'+srem2);
        serverLog.logInfo("kelondroCollectionIndex", "shrinked common word " + new String(key) + "; old size = " + oldsize + ", new size = " + collection.size() + ", maximum size = " + targetSize + ", newcommon size = " + newcommon.size() + ", first newcommon = " + firstnewcommon);
        
        // finally dump the removed entries to a file
        newcommon.sort();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        String filename = serverCodings.encodeHex(kelondroBase64Order.enhancedCoder.decode(new String(key), "de.anomic.kelondro.kelondroCollectionIndex.shrinkCollection(...)")) + "_" + formatter.format(new Date()) + ".collection";
        File storagePath = new File(commonsPath, filename.substring(0, 2)); // make a subpath
        storagePath.mkdirs();
        File file = new File(storagePath, filename);
        try {
            newcommon.saveCollection(file);
            serverLog.logInfo("kelondroCollectionIndex", "dumped common word " + new String(key) + " to " + file.toString() + "; size = " + newcommon.size());
        } catch (IOException e) {
            e.printStackTrace();
            serverLog.logWarning("kelondroCollectionIndex", "failed to dump common word " + new String(key) + " to " + file.toString() + "; size = " + newcommon.size());
        }
        
    }
    
    public synchronized int remove(byte[] key, Set<String> removekeys) throws IOException, kelondroOutOfLimitsException {
        
        if ((removekeys == null) || (removekeys.size() == 0)) return 0;
        
        // first find an old entry, if one exists
        kelondroRow.Entry indexrow = index.get(key);
        
        if (indexrow == null) return 0;
            
        // overwrite the old collection
        // read old information
        int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize);  // needed only for migration
        int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount); // the number if rows in the collection
        int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);   // index of the entry in array
        int oldPartitionNumber = (int) indexrow.getColByte(idx_col_clusteridx); // points to array file
        assert (oldPartitionNumber >= arrayIndex(oldchunkcount));

        int removed = 0;
        assert (removekeys != null);
        // load the old collection and remove keys
        kelondroRowSet oldcollection = getwithparams(indexrow, oldchunksize, oldchunkcount, oldPartitionNumber, oldrownumber, serialNumber, false);

        // remove the keys from the set
        Iterator<String> i = removekeys.iterator();
        while (i.hasNext()) {
            if (oldcollection.remove(i.next().getBytes(), false) != null) removed++;
        }
        oldcollection.sort();
        oldcollection.trim(false);

        /* in case that the new array size is zero we dont delete the array, just allocate a minimal chunk
         * 

        if (oldcollection.size() == 0) {
            // delete the index entry and the array
            kelondroFixedWidthArray array = getArray(oldPartitionNumber, serialNumber, oldchunksize);
            array.remove(oldrownumber, false);
            index.remove(key);
            return removed;
        }
         */
        int newPartitionNumber = arrayIndex(oldcollection.size());

        // see if we need new space or if we can overwrite the old space
        if (oldPartitionNumber == newPartitionNumber) {
            array_replace(
                    key, oldcollection, indexrow,
                    oldPartitionNumber, serialNumber, this.payloadrow.objectsize,
                    oldrownumber); // modifies indexrow
        } else {
            array_remove(
                    oldPartitionNumber, serialNumber, this.payloadrow.objectsize,
                    oldrownumber);
            array_add(
                    key, oldcollection, indexrow,
                    newPartitionNumber, serialNumber, this.payloadrow.objectsize); // modifies indexrow
        }
        index.put(indexrow); // write modified indexrow
        return removed;
    }
    
    public synchronized int indexSize(byte[] key) throws IOException {
        kelondroRow.Entry indexrow = index.get(key);
        if (indexrow == null) return 0;
        return (int) indexrow.getColLong(idx_col_chunkcount);
    }
    
    public synchronized boolean has(byte[] key) throws IOException {
        return index.has(key);
    }
    
    public synchronized kelondroRowSet get(byte[] key) throws IOException {
        // find an entry, if one exists
        kelondroRow.Entry indexrow = index.get(key);
        if (indexrow == null) return null;
        kelondroRowSet col = getdelete(indexrow, false);
        assert (col != null);
        return col;
    }
    
    public synchronized kelondroRowSet delete(byte[] key) throws IOException {
        // find an entry, if one exists
        kelondroRow.Entry indexrow = index.remove(key, false);
        if (indexrow == null) return null;
        kelondroRowSet removedCollection = getdelete(indexrow, true);
        assert (removedCollection != null);
        return removedCollection;
    }

    protected kelondroRowSet getdelete(kelondroRow.Entry indexrow, boolean remove) throws IOException {
        // call this only within a synchronized(index) environment
        
        // read values
        int chunksize       = (int) indexrow.getColLong(idx_col_chunksize);
        int chunkcount      = (int) indexrow.getColLong(idx_col_chunkcount);
        int rownumber       = (int) indexrow.getColLong(idx_col_indexpos);
        int partitionnumber = (int) indexrow.getColByte(idx_col_clusteridx);
        assert(partitionnumber >= arrayIndex(chunkcount)) : "partitionnumber = " + partitionnumber + ", arrayIndex(chunkcount) = " + arrayIndex(chunkcount);
        int serialnumber = 0;
        
        return getwithparams(indexrow, chunksize, chunkcount, partitionnumber, rownumber, serialnumber, remove);
    }

    private synchronized kelondroRowSet getwithparams(kelondroRow.Entry indexrow, int chunksize, int chunkcount, int clusteridx, int rownumber, int serialnumber, boolean remove) throws IOException {
        // open array entry
        kelondroFixedWidthArray array = getArray(clusteridx, serialnumber, index.row().objectOrder, chunksize);
        kelondroRow.Entry arrayrow = array.get(rownumber);
        if (arrayrow == null) throw new kelondroException(arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, clusteridx, serialnumber).toString(), "array does not contain expected row");

        // read the row and define a collection
        byte[] indexkey = indexrow.getColBytes(idx_col_key);
        byte[] arraykey = arrayrow.getColBytes(0);
        if (!(index.row().objectOrder.wellformed(arraykey))) {
            // cleanup for a bad bug that corrupted the database
            index.remove(indexkey, false); // the RowCollection must be considered lost
            array.remove(rownumber); // loose the RowCollection (we don't know how much is lost)
            serverLog.logSevere("kelondroCollectionIndex." + array.filename, "lost a RowCollection because of a bad arraykey");
            return new kelondroRowSet(this.payloadrow, 0);
        }
        kelondroRowSet collection = new kelondroRowSet(this.payloadrow, arrayrow, 1); // FIXME: this does not yet work with different rowdef in case of several rowdef.objectsize()
        if ((!(index.row().objectOrder.wellformed(indexkey))) || (index.row().objectOrder.compare(arraykey, indexkey) != 0)) {
            // check if we got the right row; this row is wrong. Fix it:
            index.remove(indexkey, false); // the wrong row cannot be fixed
            // store the row number in the index; this may be a double-entry, but better than nothing
            kelondroRow.Entry indexEntry = index.row().newEntry();
            indexEntry.setCol(idx_col_key, arrayrow.getColBytes(0));
            indexEntry.setCol(idx_col_chunksize, this.payloadrow.objectsize);
            indexEntry.setCol(idx_col_chunkcount, collection.size());
            indexEntry.setCol(idx_col_clusteridx, (byte) clusteridx);
            indexEntry.setCol(idx_col_flags, (byte) 0);
            indexEntry.setCol(idx_col_indexpos, (long) rownumber);
            indexEntry.setCol(idx_col_lastread, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
            indexEntry.setCol(idx_col_lastwrote, kelondroRowCollection.daysSince2000(System.currentTimeMillis()));
            index.put(indexEntry);
            serverLog.logSevere("kelondroCollectionIndex." + array.filename, "array contains wrong row '" + new String(arrayrow.getColBytes(0)) + "', expected is '" + new String(indexrow.getColBytes(idx_col_key)) + "', the row has been fixed");
        }
        int chunkcountInArray = collection.size();
        if (chunkcountInArray != chunkcount) {
            // fix the entry in index
            indexrow.setCol(idx_col_chunkcount, chunkcountInArray);
            index.put(indexrow);
            array.logFailure("INCONSISTENCY (get) in " + arrayFile(this.path, this.filenameStub, this.loadfactor, chunksize, clusteridx, serialnumber).toString() + ": array has different chunkcount than index: index = " + chunkcount + ", array = " + chunkcountInArray + "; the index has been auto-fixed");
        }
        if (remove) array.remove(rownumber); // index is removed in calling method
        return collection;
    }
    
    public synchronized Iterator<Object[]> keycollections(byte[] startKey, byte[] secondKey, boolean rot) {
        // returns an iteration of {byte[], kelondroRowSet} Objects
        try {
            return new keycollectionIterator(startKey, secondKey, rot);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public class keycollectionIterator implements Iterator<Object[]> {
        
        Iterator<kelondroRow.Entry> indexRowIterator;
        
        public keycollectionIterator(byte[] startKey, byte[] secondKey, boolean rot) throws IOException {
            // iterator of {byte[], kelondroRowSet} Objects
            kelondroCloneableIterator<kelondroRow.Entry> i = index.rows(true, startKey);
            indexRowIterator = (rot) ? new kelondroRotateIterator<kelondroRow.Entry>(i, secondKey) : i;
        }
        
        public boolean hasNext() {
            return indexRowIterator.hasNext();
        }

        public Object[] next() {
            kelondroRow.Entry indexrow = (kelondroRow.Entry) indexRowIterator.next();
            assert (indexrow != null);
            if (indexrow == null) return null;
            try {
                return new Object[]{indexrow.getColBytes(0), getdelete(indexrow, false)};
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        public void remove() {
            indexRowIterator.remove();
        }
        
    }
    
    public synchronized void close() {
        this.index.close();
        Iterator<kelondroFixedWidthArray> i = arrays.values().iterator();
        while (i.hasNext()) i.next().close();
    }
    
    public static void main(String[] args) {

        // define payload structure
        kelondroRow rowdef = new kelondroRow("byte[] a-10, byte[] b-80", kelondroNaturalOrder.naturalOrder, 0);
        
        File path = new File(args[0]);
        String filenameStub = args[1];
        long preloadTime = 10000;
        try {
            // initialize collection index
            kelondroCollectionIndex collectionIndex  = new kelondroCollectionIndex(
                        path, filenameStub, 9 /*keyLength*/,
                        kelondroNaturalOrder.naturalOrder, preloadTime,
                        4 /*loadfactor*/, 7, rowdef);
            
            // fill index with values
            kelondroRowSet collection = new kelondroRowSet(rowdef, 0);
            collection.addUnique(rowdef.newEntry(new byte[][]{"abc".getBytes(), "efg".getBytes()}));
            collectionIndex.put("erstes".getBytes(), collection);
            
            for (int i = 1; i <= 170; i++) {
                collection = new kelondroRowSet(rowdef, 0);
                for (int j = 0; j < i; j++) {
                    collection.addUnique(rowdef.newEntry(new byte[][]{("abc" + j).getBytes(), "xxx".getBytes()}));
                }
                System.out.println("put key-" + i + ": " + collection.toString());
                collectionIndex.put(("key-" + i).getBytes(), collection);
            }
            
            // extend collections with more values
            for (int i = 0; i <= 170; i++) {
                collection = new kelondroRowSet(rowdef, 0);
                for (int j = 0; j < i; j++) {
                    collection.addUnique(rowdef.newEntry(new byte[][]{("def" + j).getBytes(), "xxx".getBytes()}));
                }
                collectionIndex.merge(new indexContainer("key-" + i, collection));
            }
            
            // printout of index
            collectionIndex.close();
            kelondroFlexTable index = new kelondroFlexTable(path, filenameStub + ".index", preloadTime, kelondroCollectionIndex.indexRow(9, kelondroNaturalOrder.naturalOrder), 0, true);
            index.print();
            index.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
