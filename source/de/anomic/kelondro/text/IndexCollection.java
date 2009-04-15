// iIndexCollection.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.07.2006 on http://yacy.net
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

package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

import de.anomic.kelondro.index.IntegerHandleIndex;
import de.anomic.kelondro.index.ObjectIndex;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowCollection;
import de.anomic.kelondro.index.RowSet;
import de.anomic.kelondro.index.Row.EntryIndex;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.Digest;
import de.anomic.kelondro.order.NaturalOrder;
import de.anomic.kelondro.order.RotateIterator;
import de.anomic.kelondro.table.EcoTable;
import de.anomic.kelondro.table.FixedWidthArray;
import de.anomic.kelondro.table.FlexTable;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.kelondro.util.kelondroException;
import de.anomic.kelondro.util.kelondroOutOfLimitsException;
import de.anomic.kelondro.util.Log;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.yacy.yacyURL;

public class IndexCollection<ReferenceType extends Reference> extends AbstractIndex<ReferenceType> implements Index<ReferenceType> {

	private static final int loadfactor = 4;
    private static final int serialNumber = 0;
    private static final long minimumRAM4Eco = 20 * 1024 * 1024;
    private static final int EcoFSBufferSize = 1000;
    private static final int errorLimit = 500; // if the index exceeds this number of errors, it is re-built next time the application starts
    
    private ObjectIndex     index;
    private final int       keylength;
    private final File      path;
    private final String    filenameStub;
    private final File      commonsPath;
    private final Row       payloadrow;    // definition of the payload (chunks inside the collections)
    private final int       maxPartitions; // this is the maxmimum number of array files
    private int             indexErrors;   // counter for exceptions when index returned wrong value
    private Map<String, FixedWidthArray> arrays; // Map of (partitionNumber"-"chunksize)/kelondroFixedWidthArray - Objects
    
    private static final int idx_col_key        = 0;  // the index
    private static final int idx_col_chunksize  = 1;  // chunksize (number of bytes in a single chunk, needed for migration option)
    private static final int idx_col_chunkcount = 2;  // chunkcount (number of chunks in this collection)
    private static final int idx_col_clusteridx = 3;  // selector for right cluster file, must be >= arrayIndex(chunkcount)
    private static final int idx_col_flags      = 4;  // flags (for future use)
    private static final int idx_col_indexpos   = 5;  // indexpos (position in array file)
    private static final int idx_col_lastread   = 6;  // a time stamp, update time in days since 1.1.2000
    private static final int idx_col_lastwrote  = 7;  // a time stamp, update time in days since 1.1.2000
    
    public IndexCollection(
    		final File path, 
    		final String filenameStub,
    		final ReferenceFactory<ReferenceType> factory,
    		final int keyLength,
    		final ByteOrder wordOrder,
            final int maxpartitions, 
            final Row payloadrow, 
            boolean useCommons) throws IOException {
        super(factory);
        
        // the buffersize is number of bytes that are only used if the kelondroFlexTable is backed up with a kelondroTree
        indexErrors = 0;
        this.path = path;
        this.filenameStub = filenameStub;
        this.keylength = keyLength;
        this.payloadrow = payloadrow;
        this.maxPartitions = maxpartitions;
        File cop = new File(path, filenameStub + "." + fillZ(Integer.toHexString(payloadrow.objectsize).toUpperCase(), 4) + ".commons");
        this.commonsPath = (useCommons) ? cop : null;
        if (this.commonsPath == null) {
            FileUtils.deletedelete(cop);
        } else {
            this.commonsPath.mkdirs();
        }
        final File f = new File(path, filenameStub + ".index");
        if (f.isDirectory()) {
            FlexTable.delete(path, filenameStub + ".index");
        }
        if (f.exists()) {
            Log.logFine("COLLECTION INDEX STARTUP", "OPENING COLLECTION INDEX");
            
            // open index and array files
            this.arrays = new HashMap<String, FixedWidthArray>(); // all entries will be dynamically created with getArray()
            index = openIndexFile(path, this.keylength, filenameStub, wordOrder, loadfactor, payloadrow, 0);
            openAllArrayFiles(false, wordOrder);
        } else {
            // calculate initialSpace
            final String[] list = this.path.list();
            FixedWidthArray array;
            int initialSpace = 0;
            for (int i = 0; i < list.length; i++) if (list[i].endsWith(".kca")) {
                // open array
                final int pos = list[i].indexOf('.');
                if (pos < 0) continue;
                final int partitionNumber = Integer.parseInt(list[i].substring(pos +  9, pos + 11), 16);
                final int serialNumber    = Integer.parseInt(list[i].substring(pos + 12, pos + 14), 16);
                try {
                    array = openArrayFile(this.path, this.filenameStub, this.keylength, partitionNumber, serialNumber, wordOrder, this.payloadrow.objectsize, true);
                    initialSpace += array.size();
                    array.close();
                } catch (final IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            Log.logFine("COLLECTION INDEX STARTUP", "STARTED INITIALIZATION OF NEW COLLECTION INDEX WITH " + initialSpace + " ENTRIES.  THIS WILL TAKE SOME TIME. " + (MemoryControl.available() / 1024 / 1024) + "MB AVAILABLE.");
            final Row indexRowdef = indexRow(keyLength, wordOrder);
            final long necessaryRAM4fullTable = minimumRAM4Eco + (indexRowdef.objectsize + 4) * initialSpace * 3 / 2;
            
            // initialize (new generation) index table from file
            index = new EcoTable(f, indexRowdef, (MemoryControl.request(necessaryRAM4fullTable, false)) ? EcoTable.tailCacheUsageAuto : EcoTable.tailCacheDenyUsage, EcoFSBufferSize, initialSpace);
            
            // open array files
            this.arrays = new HashMap<String, FixedWidthArray>(); // all entries will be dynamically created with getArray()
            openAllArrayFiles(true, wordOrder);
            Log.logFine("COLLECTION INDEX STARTUP", "FINISHED INITIALIZATION OF NEW COLLECTION INDEX.");            
        }
    }

    public ByteOrder ordering() {
        return index.row().objectOrder;
    }
    
    public synchronized CloneableIterator<ReferenceContainer<ReferenceType>> references(final String startWordHash, final boolean rot) {
        return new wordContainersIterator(startWordHash, rot);
    }

    public class wordContainersIterator implements CloneableIterator<ReferenceContainer<ReferenceType>> {

        private final Iterator<Object[]> wci;
        private final boolean rot;
        
        public wordContainersIterator(final String startWordHash, final boolean rot) {
            this.rot = rot;
            this.wci = keycollections(startWordHash.getBytes(), Base64Order.zero(startWordHash.length()), rot);
        }
        
        public wordContainersIterator clone(final Object secondWordHash) {
            return new wordContainersIterator((String) secondWordHash, rot);
        }
        
        public boolean hasNext() {
            return wci.hasNext();
        }

        public ReferenceContainer<ReferenceType> next() {
            final Object[] oo = wci.next();
            if (oo == null) return null;
            final byte[] key = (byte[]) oo[0];
            final RowSet collection = (RowSet) oo[1];
            if (collection == null) return null;
            return new ReferenceContainer<ReferenceType>(factory, new String(key), collection);
        }
        
        public void remove() {
            wci.remove();
        }

    }

    public boolean has(final String wordHash) {
        return this.has(wordHash.getBytes());
    }
    
    public ReferenceContainer<ReferenceType> get(final String wordHash, final Set<String> urlselection) {
        try {
            final RowSet collection = this.get(wordHash.getBytes());
            if (collection != null) collection.select(urlselection);
            if ((collection == null) || (collection.size() == 0)) return null;
            return new ReferenceContainer<ReferenceType>(factory, wordHash, collection);
        } catch (final IOException e) {
            return null;
        }
    }

    public ReferenceContainer<ReferenceType> delete(final String wordHash) {
        try {
            final RowSet collection = this.delete(wordHash.getBytes());
            if (collection == null) return null;
            return new ReferenceContainer<ReferenceType>(factory, wordHash, collection);
        } catch (final IOException e) {
            return null;
        }
    }
    
    public boolean remove(final String wordHash, final String urlHash) {
        final HashSet<String> hs = new HashSet<String>();
        hs.add(urlHash);
        return remove(wordHash, hs) == 1;
    }
    
    public int remove(final String wordHash, final Set<String> urlHashes) {
        try {
            return this.remove(wordHash.getBytes(), urlHashes);
        } catch (final kelondroOutOfLimitsException e) {
            e.printStackTrace();
            return 0;
        } catch (final IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void add(final ReferenceContainer<ReferenceType> newEntries) {
    	if (newEntries == null) return;
        try {
            this.merge(newEntries);
        } catch (final kelondroOutOfLimitsException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void add(String wordhash, ReferenceType entry) {
        if (entry == null) return;
        try {
            ReferenceContainer<ReferenceType> container = new ReferenceContainer<ReferenceType>(factory, wordhash, this.payloadrow, 1);
            container.add(entry);
            this.merge(container);
        } catch (final kelondroOutOfLimitsException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public int count(String key) {
        try {
            final RowSet collection = this.get(key.getBytes());
            if (collection == null) return 0;
            return collection.size();
        } catch (final IOException e) {
            return 0;
        }
    }
    
    //----------------------------------------------------------------------------------
    

    private static Row indexRow(final int keylength, final ByteOrder payloadOrder) {
        return new Row(
            "byte[] key-" + keylength + "," +
            "int chunksize-4 {b256}," +
            "int chunkcount-4 {b256}," +
            "byte clusteridx-1 {b256}," +
            "byte flags-1 {b256}," +
            "int indexpos-4 {b256}," +
            "short lastread-2 {b256}, " +
            "short lastwrote-2 {b256}",
            payloadOrder
            );
    }
    
    public Row payloadRow() {
        return this.payloadrow;
    }
    
    private static String fillZ(String s, final int len) {
        while (s.length() < len) s = "0" + s;
        return s;
    }
    
    private static File arrayFile(final File path, final String filenameStub, final int loadfactor, final int chunksize, final int partitionNumber, final int serialNumber) {
        final String lf = fillZ(Integer.toHexString(loadfactor).toUpperCase(), 2);
        final String cs = fillZ(Integer.toHexString(chunksize).toUpperCase(), 4);
        final String pn = fillZ(Integer.toHexString(partitionNumber).toUpperCase(), 2);
        final String sn = fillZ(Integer.toHexString(serialNumber).toUpperCase(), 2);
        return new File(path, filenameStub + "." + lf + "." + cs + "." + pn + "." + sn + ".kca"); // kelondro collection array
    }
    
    public void clear() throws IOException {
        index.clear();
        for (final FixedWidthArray array: arrays.values()) {
            array.clear();
        }
    }
    
    public void deleteIndexOnExit() {
    	// will be rebuilt on next start
    	this.index.deleteOnExit();
    }
    
    private void openAllArrayFiles(final boolean indexGeneration, final ByteOrder wordOrder) throws IOException {
        
        final String[] list = this.path.list();
        FixedWidthArray array;
        
        final Row irow = indexRow(keylength, wordOrder);
        final int t = RowCollection.daysSince2000(System.currentTimeMillis());
        for (int i = 0; i < list.length; i++) if (list[i].endsWith(".kca")) {

            // open array
            final int pos = list[i].indexOf('.');
            if (pos < 0) continue;
            final int chunksize       = Integer.parseInt(list[i].substring(pos +  4, pos +  8), 16);
            final int partitionNumber = Integer.parseInt(list[i].substring(pos +  9, pos + 11), 16);
            final int serialNumber    = Integer.parseInt(list[i].substring(pos + 12, pos + 14), 16);
            try {
                array = openArrayFile(this.path, this.filenameStub, this.keylength, partitionNumber, serialNumber, wordOrder, this.payloadrow.objectsize, true);
            } catch (final IOException e) {
                e.printStackTrace();
                continue;
            }
            
            // remember that we opened the array
            arrays.put(partitionNumber + "-" + chunksize, array);
            
            if ((index != null) && (indexGeneration)) {
                // loop over all elements in array and create index entry for each row
                Row.EntryIndex aentry;
                Row.Entry      ientry;
                final Iterator<EntryIndex> ei = array.contentRows(10000);
                byte[] key;
                final long start = System.currentTimeMillis();
                long lastlog = start;
                int count = 0;
                int chunkcount;
                while (ei.hasNext()) {
                    aentry = ei.next();
                    key = aentry.getColBytes(0);
                    assert (key != null);
                    if (key == null) continue; // skip deleted entries
                    chunkcount = RowCollection.sizeOfExportedCollectionRows(aentry, 1);
                    assert chunkcount > 0;
                    if (chunkcount == 0) continue;
                    ientry = irow.newEntry();
                    ientry.setCol(idx_col_key,        key);
                    ientry.setCol(idx_col_chunksize,  chunksize);
                    ientry.setCol(idx_col_chunkcount, chunkcount);
                    ientry.setCol(idx_col_clusteridx, (byte) partitionNumber);
                    ientry.setCol(idx_col_flags,      (byte) 0);
                    ientry.setCol(idx_col_indexpos,   aentry.index());
                    ientry.setCol(idx_col_lastread,   t);
                    ientry.setCol(idx_col_lastwrote,  t);
                    index.addUnique(ientry); // FIXME: this should avoid doubles
                    count++;
                    
                    // write a log
                    if (System.currentTimeMillis() - lastlog > 30000) {
                        Log.logInfo("COLLECTION INDEX STARTUP", "created " + count + " RWI index entries. " + (((System.currentTimeMillis() - start) * (array.size() + array.free() - count) / count) / 60000) + " minutes remaining for this array");
                        lastlog = System.currentTimeMillis();
                    }
                }
            }
        }
        // care for double entries
        int partition, maxpartition;
        Row.Entry maxentry;
        int doublecount = 0;
        ArrayList<RowCollection> doubles = index.removeDoubles();
        if (doubles.size() > 0) Log.logWarning("COLLECTION INDEX STARTUP", "found " + doubles.size() + " doubles in collections, removing them in arrays");
        for (final RowCollection doubleset: doubles) {
            // for each entry in doubleset choose one which we want to keep
            maxentry = null;
            maxpartition = -1;
            for (Row.Entry entry: doubleset) {
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
        if (doublecount > 0) Log.logWarning("STARTUP", "found " + doublecount + " RWI entries with references to several collections. All have been fixed (zombies still exists).");
    }
    
    /**
     * enumerate all index files and return a set of reference hashes
     * @param path
     * @param filenameStub
     * @param keylength
     * @param wordOrder
     * @param payloadrow
     * @return
     * @throws IOException
     */
    public static IntegerHandleIndex referenceHashes(
            final File path, 
            final String filenameStub, 
            final int keylength, 
            final ByteOrder wordOrder,
            final Row payloadrow) throws IOException {
       
        final String[] list = path.list();
        FixedWidthArray array;
        System.out.println("COLLECTION INDEX REFERENCE COLLECTION startup");
        IntegerHandleIndex references = new IntegerHandleIndex(keylength, wordOrder, 0, 1000000);
        for (int i = 0; i < list.length; i++) if (list[i].endsWith(".kca")) {
            // open array
            final int pos = list[i].indexOf('.');
            if (pos < 0) continue;
            final int partitionNumber = Integer.parseInt(list[i].substring(pos +  9, pos + 11), 16);
            final int serialNumber    = Integer.parseInt(list[i].substring(pos + 12, pos + 14), 16);
            System.out.println("COLLECTION INDEX REFERENCE COLLECTION opening partition " + partitionNumber + ", " + i + " of " + list.length);
            try {
                array = openArrayFile(path, filenameStub, keylength, partitionNumber, serialNumber, wordOrder, payloadrow.objectsize, true);
            } catch (final IOException e) {
                e.printStackTrace();
                continue;
            }
            System.out.println("COLLECTION INDEX REFERENCE COLLECTION opened partition " + partitionNumber + ", initializing iterator");            
            // loop over all elements in array and collect reference hashes
            Row.EntryIndex arrayrow;
            final Iterator<EntryIndex> ei = array.contentRows(10000);
            System.out.println("COLLECTION INDEX REFERENCE COLLECTION opened partition " + partitionNumber + ", starting reference scanning");
            final long start = System.currentTimeMillis();
            long lastlog = start - 27000;
            int count = 0;
            while (ei.hasNext()) {
                arrayrow = ei.next();
                if (arrayrow == null) continue;
                final RowSet collection = new RowSet(payloadrow, arrayrow);
                final int chunkcountInArray = collection.size();
                for (int j = 0; j < chunkcountInArray; j++) {
                    references.inc(collection.get(j, false).getColBytes(0), 1);
                }
                count++;
                // write a log
                if (System.currentTimeMillis() - lastlog > 30000) {
                    System.out.println("COLLECTION INDEX REFERENCE COLLECTION scanned " + count + " RWI index entries. " + (((System.currentTimeMillis() - start) * (array.size() + array.free() - count) / count) / 60000) + " minutes remaining for this array");
                    //Log.logInfo("COLLECTION INDEX REFERENCE COLLECTION", "scanned " + count + " RWI index entries. " + (((System.currentTimeMillis() - start) * (array.size() + array.free() - count) / count) / 60000) + " minutes remaining for this array");
                    lastlog = System.currentTimeMillis();
                }
            }
        }
        System.out.println("COLLECTION INDEX REFERENCE COLLECTION finished with reference collection");
        return references;
    }
    
    private static ObjectIndex openIndexFile(
            final File path, int keylength, final String filenameStub, final ByteOrder indexOrder,
            final int loadfactor, final Row rowdef, final int initialSpace) throws IOException {
        // open/create index table
        final File f = new File(path, filenameStub + ".index");
        final Row indexRowdef = indexRow(keylength, indexOrder);
        ObjectIndex theindex;
        if (f.isDirectory()) {
            FlexTable.delete(path, filenameStub + ".index");
        }
        // open a ecotable
        final long records = f.length() / indexRowdef.objectsize;
        final long necessaryRAM4fullTable = minimumRAM4Eco + (indexRowdef.objectsize + 4) * records * 3 / 2;
        final boolean fullCache = MemoryControl.request(necessaryRAM4fullTable, false);
        if (fullCache) {
            theindex = new EcoTable(f, indexRowdef, EcoTable.tailCacheUsageAuto, EcoFSBufferSize, initialSpace);
            //if (!((kelondroEcoTable) theindex).usesFullCopy()) theindex = new kelondroCache(theindex);
        } else {
            //theindex = new kelondroCache(new kelondroEcoTable(f, indexRowdef, kelondroEcoTable.tailCacheDenyUsage, EcoFSBufferSize, initialSpace));
            theindex = new EcoTable(f, indexRowdef, EcoTable.tailCacheDenyUsage, EcoFSBufferSize, initialSpace);
        }
        return theindex;
    }
    
    private static FixedWidthArray openArrayFile(
            File path, String filenameStub, int keylength,
            final int partitionNumber, final int serialNumber,
            final ByteOrder wordOrder, int objectsize,
            final boolean create) throws IOException {
        final File f = arrayFile(path, filenameStub, loadfactor, objectsize, partitionNumber, serialNumber);
        final int load = arrayCapacity(partitionNumber);
        final Row rowdef = new Row(
                "byte[] key-" + keylength + "," +
                "byte[] collection-" + (RowCollection.exportOverheadSize + load * objectsize),
                wordOrder                );
        if ((!(f.exists())) && (!create)) return null;
        final FixedWidthArray a = new FixedWidthArray(f, rowdef, 0);
        Log.logFine("STARTUP", "opened array file " + f + " with " + a.size() + " RWIs");
        return a;
    }
    
    private FixedWidthArray getArray(final int partitionNumber, final int serialNumber, final ByteOrder wordOrder, final int chunksize) {
        final String accessKey = partitionNumber + "-" + chunksize;
        FixedWidthArray array = arrays.get(accessKey);
        if (array != null) return array;
        try {
            array = openArrayFile(this.path, this.filenameStub, this.keylength, partitionNumber, serialNumber, wordOrder, this.payloadrow.objectsize, true);
        } catch (final IOException e) {
        	e.printStackTrace();
            return null;
        }
        arrays.put(accessKey, array);
        return array;
    }
    
    private static int arrayCapacity(final int arrayCounter) {
        if (arrayCounter < 0) return 0;
        int load = loadfactor;
        for (int i = 0; i < arrayCounter; i++) load = load * loadfactor;
        return load;
    }
    
    private static int arrayIndex(final int requestedCapacity) throws kelondroOutOfLimitsException{
        // the requestedCapacity is the number of wanted chunks
        int load = 1, i = 0;
        while (true) {
            load = load * loadfactor;
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
        return (int) (arrayCapacity(arrays.size() - 1) * this.payloadrow.objectsize * RowCollection.growfactor);
    }
    
    private void array_remove(
            final int oldPartitionNumber, final int serialNumber, final int chunkSize,
            final int oldRownumber) throws IOException {
        // we need a new slot, that means we must first delete the old entry
        // find array file
        final FixedWidthArray array = getArray(oldPartitionNumber, serialNumber, index.row().objectOrder, chunkSize);

        // delete old entry
        array.remove(oldRownumber);
    }
    
    private Row.Entry array_new(
            final byte[] key, final RowCollection collection) throws IOException {
        // the collection is new
        final int partitionNumber = arrayIndex(collection.size());
        final Row.Entry indexrow = index.row().newEntry();
        final FixedWidthArray array = getArray(partitionNumber, serialNumber, index.row().objectOrder, this.payloadrow.objectsize);

        // define row
        final Row.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());

        // write a new entry in this array
        try {
            final int newRowNumber = array.add(arrayEntry);

            // store the new row number in the index
            indexrow.setCol(idx_col_key, key);
            indexrow.setCol(idx_col_chunksize, this.payloadrow.objectsize);
            indexrow.setCol(idx_col_chunkcount, collection.size());
            indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
            indexrow.setCol(idx_col_flags, (byte) 0);
            indexrow.setCol(idx_col_indexpos, newRowNumber);
            indexrow.setCol(idx_col_lastread, RowCollection.daysSince2000(System.currentTimeMillis()));
            indexrow.setCol(idx_col_lastwrote, RowCollection.daysSince2000(System.currentTimeMillis()));

            // after calling this method there must be an index.addUnique(indexrow);
            return indexrow;
        } catch (Exception e) {
            // the index appears to be corrupted at a particular point
            Log.logWarning("kelondroCollectionIndex", "array " + arrayFile(this.path, this.filenameStub, loadfactor, this.payloadrow.objectsize, partitionNumber, serialNumber).toString() + " has errors \"" + e.getMessage() + "\" (error #" + indexErrors + ")");
            return null;
        }
    }
    
    private void array_add(
            final byte[] key, final RowCollection collection, final Row.Entry indexrow,
            final int partitionNumber, final int serialNumber, final int chunkSize) throws IOException {

        // write a new entry in the other array
        final FixedWidthArray array = getArray(partitionNumber, serialNumber, index.row().objectOrder, chunkSize);
        
        // define new row
        final Row.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());
        
        // write a new entry in this array
        final int rowNumber = array.add(arrayEntry);
        
        // store the new row number in the index
        indexrow.setCol(idx_col_chunkcount, collection.size());
        indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
        indexrow.setCol(idx_col_indexpos, rowNumber);
        indexrow.setCol(idx_col_lastwrote, RowCollection.daysSince2000(System.currentTimeMillis()));

        // after calling this method there must be a index.put(indexrow);
    }
    
    private void array_replace(
            final byte[] key, final RowCollection collection, final Row.Entry indexrow,
            final int partitionNumber, final int serialNumber, final int chunkSize,
            final int rowNumber) throws IOException {
        // we don't need a new slot, just write collection into the old one

        // find array file
        final FixedWidthArray array = getArray(partitionNumber, serialNumber, index.row().objectOrder, chunkSize);

        // define new row
        final Row.Entry arrayEntry = array.row().newEntry();
        arrayEntry.setCol(0, key);
        arrayEntry.setCol(1, collection.exportCollection());

        // overwrite entry in this array
        array.set(rowNumber, arrayEntry);

        // update the index entry
        final int collectionsize = collection.size(); // extra variable for easier debugging
        indexrow.setCol(idx_col_chunkcount, collectionsize);
        indexrow.setCol(idx_col_clusteridx, (byte) partitionNumber);
        indexrow.setCol(idx_col_lastwrote, RowCollection.daysSince2000(System.currentTimeMillis()));
        
        // after calling this method there must be a index.put(indexrow);
    }
    
    public synchronized void put(final byte[] key, final RowCollection collection) throws IOException, kelondroOutOfLimitsException {
    	assert (key != null);
    	assert (collection != null);
    	assert (collection.size() != 0);
    	
        // first find an old entry, if one exists
        Row.Entry indexrow = index.get(key);
        
        if (indexrow == null) {
            // create new row and index entry
            if ((collection != null) && (collection.size() > 0)) {
                indexrow = array_new(key, collection); // modifies indexrow
                if (indexrow != null) index.addUnique(indexrow);
            }
            return;
        }
            
        // overwrite the old collection
        // read old information
        //int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize);  // needed only for migration
        final int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount); // the number if rows in the collection
        final int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);   // index of the entry in array
        final int oldPartitionNumber = indexrow.getColByte(idx_col_clusteridx); // points to array file
        assert (oldPartitionNumber >= arrayIndex(oldchunkcount));

        final int newPartitionNumber = arrayIndex(collection.size());

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
        
        if ((int) indexrow.getColLong(idx_col_chunkcount) != collection.size()) {
            this.indexErrors++;
            if (this.indexErrors == errorLimit) deleteIndexOnExit(); // delete index on exit for rebuild
        	Log.logSevere("kelondroCollectionIndex", "UPDATE (put) ERROR: array has different chunkcount than index after merge: index = " + (int) indexrow.getColLong(idx_col_chunkcount) + ", collection.size() = " + collection.size() + " (error #" + indexErrors + ")");
        }
        index.put(indexrow); // write modified indexrow
    }
    
    private synchronized void merge(final ReferenceContainer<ReferenceType> container) throws IOException, kelondroOutOfLimitsException {
        if ((container == null) || (container.size() == 0)) return;
        final byte[] key = container.getTermHash().getBytes();
        
        // first find an old entry, if one exists
        Row.Entry indexrow = index.get(key);
        if (indexrow == null) {
            indexrow = array_new(key, container); // modifies indexrow
            if (indexrow != null) index.addUnique(indexrow); // write modified indexrow
        } else {
            // merge with the old collection
            // attention! this modifies the indexrow entry which must be written with index.put(indexrow) afterwards!
            RowSet collection = container;
            
            // read old information
            final int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize);  // needed only for migration
            final int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount); // the number if rows in the collection
            final int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);   // index of the entry in array
            final int oldPartitionNumber = indexrow.getColByte(idx_col_clusteridx); // points to array file
            assert (oldPartitionNumber >= arrayIndex(oldchunkcount)) : "oldPartitionNumber = " + oldPartitionNumber + ", arrayIndex(oldchunkcount) = " + arrayIndex(oldchunkcount);
            final int oldSerialNumber = 0;

            // load the old collection and join it
            try {
                RowSet krc = getwithparams(indexrow, oldchunksize, oldchunkcount, oldPartitionNumber, oldrownumber, oldSerialNumber, false);
                //System.out.println("***DEBUG kelondroCollectionIndex.merge before merge*** krc.size = " + krc.size() + ", krc.sortbound = " + krc.sortBound + ", collection.size = " + collection.size() + ", collection.sortbound = " + collection.sortBound);
                collection = collection.merge(krc);
                //System.out.println("***DEBUG kelondroCollectionIndex.merge  after merge*** collection.size = " + collection.size() + ", collection.sortbound = " + collection.sortBound);
                
            } catch (kelondroException e) {
                // an error like "array does not contain expected row" may appear here. Just go on like if the collection does not exist
                e.printStackTrace();
            }
            collection.trim(false);
            
            // check for size of collection:
            // if necessary shrink the collection and dump a part of that collection
            // to avoid that this grows too big
            if (arrayIndex(collection.size()) > maxPartitions) {
                shrinkCollection(key, collection, arrayCapacity(maxPartitions));
            }
            
            // determine new partition location
            final int newPartitionNumber = arrayIndex(collection.size());

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
            if (indexrowcount != collectionsize) {
                this.indexErrors++;
                if (this.indexErrors == errorLimit) deleteIndexOnExit(); // delete index on exit for rebuild
            	Log.logSevere("kelondroCollectionIndex", "UPDATE (merge) ERROR: array has different chunkcount than index after merge: index = " + indexrowcount + ", collection.size() = " + collectionsize + " (error #" + indexErrors + ")");
            }
            index.put(indexrow); // write modified indexrow
        }
    }
    
    private void shrinkCollection(final byte[] key, final RowCollection collection, final int targetSize) {
        // removes entries from collection
        // the removed entries are stored in a 'commons' dump file

        if (key.length != 12) return;
        // check if the collection is already small enough
        final int oldsize = collection.size();
        if (oldsize <= targetSize) return;
        final RowSet newcommon = new RowSet(collection.rowdef, 0);
        
        // delete some entries, which are bad rated
        Iterator<Row.Entry> i = collection.iterator();
        Row.Entry entry;
        byte[] ref;
        while (i.hasNext()) {
            entry = i.next();
            ref = entry.getColBytes(0);
            if ((ref.length != 12) || (!yacyURL.probablyRootURL(new String(ref)))) {
                newcommon.addUnique(entry);
                i.remove();
            }
        }
        final int firstnewcommon = newcommon.size();
        
        // check if we shrinked enough
        final Random rand = new Random(System.currentTimeMillis());
        while (collection.size() > targetSize) {
            // now delete randomly more entries from the survival collection
            i = collection.iterator();
            while (i.hasNext()) {
                entry = i.next();
                ref = entry.getColBytes(0);
                if (rand.nextInt() % 4 != 0) {
                    newcommon.addUnique(entry);
                    i.remove();
                }
            }
        }
        collection.trim(false);
        
        Log.logInfo("kelondroCollectionIndex", "shrinked common word " + new String(key) + "; old size = " + oldsize + ", new size = " + collection.size() + ", maximum size = " + targetSize + ", newcommon size = " + newcommon.size() + ", first newcommon = " + firstnewcommon);
        
        // finally dump the removed entries to a file
        if (commonsPath != null) {
            newcommon.sort();
            final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
            final String filename = Digest.encodeHex(Base64Order.enhancedCoder.decode(new String(key), "de.anomic.kelondro.kelondroCollectionIndex.shrinkCollection(...)")) + "_" + formatter.format(new Date()) + ".collection";
            final File storagePath = new File(commonsPath, filename.substring(0, 2)); // make a subpath
            storagePath.mkdirs();
            final File file = new File(storagePath, filename);
            try {
                newcommon.saveCollection(file);
                Log.logInfo("kelondroCollectionIndex", "dumped common word " + new String(key) + " to " + file.toString() + "; size = " + newcommon.size());
            } catch (final IOException e) {
                e.printStackTrace();
                Log.logWarning("kelondroCollectionIndex", "failed to dump common word " + new String(key) + " to " + file.toString() + "; size = " + newcommon.size());
            }
        }        
    }
    
    private synchronized int remove(final byte[] key, final Set<String> removekeys) throws IOException, kelondroOutOfLimitsException {
        
        if ((removekeys == null) || (removekeys.size() == 0)) return 0;
        
        // first find an old entry, if one exists
        final Row.Entry indexrow = index.get(key);
        
        if (indexrow == null) return 0;
            
        // overwrite the old collection
        // read old information
        final int oldchunksize       = (int) indexrow.getColLong(idx_col_chunksize);  // needed only for migration
        final int oldchunkcount      = (int) indexrow.getColLong(idx_col_chunkcount); // the number if rows in the collection
        final int oldrownumber       = (int) indexrow.getColLong(idx_col_indexpos);   // index of the entry in array
        final int oldPartitionNumber = indexrow.getColByte(idx_col_clusteridx); // points to array file
        assert (oldPartitionNumber >= arrayIndex(oldchunkcount));

        int removed = 0;
        assert (removekeys != null);
        // load the old collection and remove keys
        RowSet oldcollection = null;
        try {
            oldcollection = getwithparams(indexrow, oldchunksize, oldchunkcount, oldPartitionNumber, oldrownumber, serialNumber, false);
        } catch (kelondroException e) {
            // some internal problems occurred. however, go on like there was no old collection
            e.printStackTrace();
        }
        // the oldcollection may be null here!
        
        if (oldcollection != null && oldcollection.size() > 0) {
            // remove the keys from the set
            final Iterator<String> i = removekeys.iterator();
            while (i.hasNext()) {
                if (oldcollection.remove(i.next().getBytes()) != null) removed++;
            }
            oldcollection.sort();
            oldcollection.trim(false);
        }
        
        // in case of an error or an empty collection, remove the collection:
        if (oldcollection == null || oldcollection.size() == 0) {
            // delete the index entry and the array
            array_remove(
                    oldPartitionNumber, serialNumber, this.payloadrow.objectsize,
                    oldrownumber);
            index.remove(key);
            return removed;
        }
        
        // now write to a new partition (which may be the same partition as the old one)
        final int newPartitionNumber = arrayIndex(oldcollection.size());

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
    
    private synchronized boolean has(final byte[] key) {
        return index.has(key);
    }
    
    private synchronized RowSet get(final byte[] key) throws IOException {
        // find an entry, if one exists
        final Row.Entry indexrow = index.get(key);
        if (indexrow == null) return null;
        final RowSet col = getdelete(indexrow, false);
        assert (col != null);
        return col;
    }
    
    private synchronized RowSet delete(final byte[] key) throws IOException {
        // find an entry, if one exists
        final Row.Entry indexrow = index.remove(key);
        if (indexrow == null) return null;
        final RowSet removedCollection = getdelete(indexrow, true);
        assert (removedCollection != null);
        return removedCollection;
    }

    private RowSet getdelete(final Row.Entry indexrow, final boolean remove) throws IOException {
        // call this only within a synchronized(index) environment
        
        // read values
        final int chunksize       = (int) indexrow.getColLong(idx_col_chunksize);
        final int chunkcount      = (int) indexrow.getColLong(idx_col_chunkcount);
        final int rownumber       = (int) indexrow.getColLong(idx_col_indexpos);
        final int partitionnumber = indexrow.getColByte(idx_col_clusteridx);
        assert(partitionnumber >= arrayIndex(chunkcount)) : "partitionnumber = " + partitionnumber + ", arrayIndex(chunkcount) = " + arrayIndex(chunkcount);
        final int serialnumber = 0;
        
        return getwithparams(indexrow, chunksize, chunkcount, partitionnumber, rownumber, serialnumber, remove);
    }

    private synchronized RowSet getwithparams(final Row.Entry indexrow, final int chunksize, final int chunkcount, final int clusteridx, final int rownumber, final int serialnumber, final boolean remove) throws IOException {
        // open array entry
        final FixedWidthArray array = getArray(clusteridx, serialnumber, index.row().objectOrder, chunksize);
        final Row.Entry arrayrow = array.get(rownumber);
        if (arrayrow == null) {
            // the index appears to be corrupted
            this.indexErrors++;
            if (this.indexErrors == errorLimit) deleteIndexOnExit(); // delete index on exit for rebuild
            Log.logWarning("kelondroCollectionIndex", "array " + arrayFile(this.path, this.filenameStub, loadfactor, chunksize, clusteridx, serialnumber).toString() + " does not contain expected row (error #" + indexErrors + ")");
            return new RowSet(this.payloadrow, 0);
        }

        // read the row and define a collection
        final byte[] indexkey = indexrow.getColBytes(idx_col_key);
        final byte[] arraykey = arrayrow.getColBytes(0);
        if (!(index.row().objectOrder.wellformed(arraykey))) {
            // cleanup for a bad bug that corrupted the database
            index.remove(indexkey); // the RowCollection must be considered lost
            array.remove(rownumber); // loose the RowCollection (we don't know how much is lost)
            this.indexErrors++;
            if (this.indexErrors == errorLimit) deleteIndexOnExit(); // delete index on exit for rebuild
            Log.logSevere("kelondroCollectionIndex." + array.filename, "lost a RowCollection because of a bad arraykey (error #" + indexErrors + ")");
            return new RowSet(this.payloadrow, 0);
        }
        
        final RowSet collection = new RowSet(this.payloadrow, arrayrow); // FIXME: this does not yet work with different rowdef in case of several rowdef.objectsize()
        if ((!(index.row().objectOrder.wellformed(indexkey))) || (!index.row().objectOrder.equal(arraykey, indexkey))) {
            // check if we got the right row; this row is wrong. Fix it:
            index.remove(indexkey); // the wrong row cannot be fixed
            // store the row number in the index; this may be a double-entry, but better than nothing
            final Row.Entry indexEntry = index.row().newEntry();
            indexEntry.setCol(idx_col_key, arrayrow.getColBytes(0));
            indexEntry.setCol(idx_col_chunksize, this.payloadrow.objectsize);
            indexEntry.setCol(idx_col_chunkcount, collection.size());
            indexEntry.setCol(idx_col_clusteridx, (byte) clusteridx);
            indexEntry.setCol(idx_col_flags, (byte) 0);
            indexEntry.setCol(idx_col_indexpos, rownumber);
            indexEntry.setCol(idx_col_lastread, RowCollection.daysSince2000(System.currentTimeMillis()));
            indexEntry.setCol(idx_col_lastwrote, RowCollection.daysSince2000(System.currentTimeMillis()));
            index.put(indexEntry);
            this.indexErrors++;
            if (this.indexErrors == errorLimit) deleteIndexOnExit(); // delete index on exit for rebuild
            Log.logSevere("kelondroCollectionIndex." + array.filename, "array contains wrong row '" + new String(arrayrow.getColBytes(0)) + "', expected is '" + new String(indexrow.getColBytes(idx_col_key)) + "', the row has been fixed (error #" + indexErrors + ")");
        }
        final int chunkcountInArray = collection.size();
        if (chunkcountInArray != chunkcount) {
            // fix the entry in index
            indexrow.setCol(idx_col_chunkcount, chunkcountInArray);
            index.put(indexrow);
            this.indexErrors++;
            if (this.indexErrors == errorLimit) deleteIndexOnExit(); // delete index on exit for rebuild
            array.logFailure("INCONSISTENCY (get) in " + arrayFile(this.path, this.filenameStub, loadfactor, chunksize, clusteridx, serialnumber).toString() + ": array has different chunkcount than index: index = " + chunkcount + ", array = " + chunkcountInArray + "; the index has been auto-fixed (error #" + indexErrors + ")");
        }
        if (remove) array.remove(rownumber); // index is removed in calling method
        return collection;
    }
    
    protected synchronized Iterator<Object[]> keycollections(final byte[] startKey, final byte[] secondKey, final boolean rot) {
        // returns an iteration of {byte[], kelondroRowSet} Objects
        try {
            return new keycollectionIterator(startKey, secondKey, rot);
        } catch (final IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public class keycollectionIterator implements Iterator<Object[]> {
        
        Iterator<Row.Entry> indexRowIterator;
        
        public keycollectionIterator(final byte[] startKey, final byte[] secondKey, final boolean rot) throws IOException {
            // iterator of {byte[], kelondroRowSet} Objects
            final CloneableIterator<Row.Entry> i = index.rows(true, startKey);
            indexRowIterator = (rot) ? new RotateIterator<Row.Entry>(i, secondKey, index.size()) : i;
        }
        
        public boolean hasNext() {
            return indexRowIterator.hasNext();
        }

        public Object[] next() {
            final Row.Entry indexrow = indexRowIterator.next();
            assert (indexrow != null);
            if (indexrow == null) return null;
            try {
                return new Object[]{indexrow.getColBytes(0), getdelete(indexrow, false)};
            } catch (final Exception e) {
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
        this.index = null;
        final Iterator<FixedWidthArray> i = arrays.values().iterator();
        while (i.hasNext()) i.next().close();
        this.arrays = null;
    }
    
    public static void main(final String[] args) {

        // define payload structure
        final Row rowdef = new Row("byte[] a-10, byte[] b-80", NaturalOrder.naturalOrder);
        
        final File path = new File(args[0]);
        final String filenameStub = args[1];
        try {
            // initialize collection index
            final IndexCollection<WordReference> collectionIndex  = new IndexCollection<WordReference>(
                        path, filenameStub,
                        plasmaWordIndex.wordReferenceFactory,
                        9 /*keyLength*/,
                        NaturalOrder.naturalOrder,
                        7, rowdef, false);
            
            // fill index with values
            RowSet collection = new RowSet(rowdef, 0);
            collection.addUnique(rowdef.newEntry(new byte[][]{"abc".getBytes(), "efg".getBytes()}));
            collectionIndex.put("erstes".getBytes(), collection);
            
            for (int i = 1; i <= 170; i++) {
                collection = new RowSet(rowdef, 0);
                for (int j = 0; j < i; j++) {
                    collection.addUnique(rowdef.newEntry(new byte[][]{("abc" + j).getBytes(), "xxx".getBytes()}));
                }
                System.out.println("put key-" + i + ": " + collection.toString());
                collectionIndex.put(("key-" + i).getBytes(), collection);
            }
            
            // extend collections with more values
            for (int i = 0; i <= 170; i++) {
                collection = new RowSet(rowdef, 0);
                for (int j = 0; j < i; j++) {
                    collection.addUnique(rowdef.newEntry(new byte[][]{("def" + j).getBytes(), "xxx".getBytes()}));
                }
                collectionIndex.merge(new ReferenceContainer<WordReference>(plasmaWordIndex.wordReferenceFactory,"key-" + i, collection));
            }
            
            // printout of index
            collectionIndex.close();
            final EcoTable index = new EcoTable(new File(path, filenameStub + ".index"), IndexCollection.indexRow(9, NaturalOrder.naturalOrder), EcoTable.tailCacheUsageAuto, 0, 0);
            index.print();
            index.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }

    }
    
}
