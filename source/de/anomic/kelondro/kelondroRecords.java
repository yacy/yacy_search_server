// kelondroRecords.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2003, 2004
// last major change: 11.01.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
  The Kelondro Database

  Kelondro Records are the basis for the tree structures of the needed database
  Therefore, the name is inspired by the creek words 'fakelo'=file and 'dentro'=tree.
  We omitted the 'fa' and 'de' in 'fakelodentro',
  making it sounding better by replacing the 't' by 'd'.
  The Kelondro Records are also used for non-tree structures like the KelondroStack.
  The purpose of these structures are file-based storage of lists/stacks and
  indexeable information.

  We use the following structure:

  Handle : handles are simply the abstraction of integer indexe's.
           We don't want to mix up integer values as node pointers
	   with handles. This makes node indexes more robust against
	   manipulation that is too far away of thinking about records
	   Simply think that handles are like cardinals that are used
	   like pointers.
  Node   : The emelentary storage piece for one information fragment.
           All Records, which are essentially files with a definitive
	   structure, are constructed of a list of Node elements, but
	   the Node Handles that are carried within the Node overhead
	   prefix construct a specific structure, like trees or stacks.
*/


package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Map;
import java.util.Iterator;

public class kelondroRecords {

    // constants
    private static final int NUL = Integer.MIN_VALUE; // the meta value for the kelondroRecords' NUL abstraction
    public  static final long memBlock =   500000; // do not fill cache further if the amount of available memory is less that this
    public  static final long memKcolb = 10000000; // if the amount of available memory is greater than this, do not use cache size to block, simply use memory
    
    // memory calculation
    private static final int element_in_cache = 52;
    private static final int cache_control_entry = 96;
    
    // caching flags
    protected static final int CP_NONE   = -1; // cache priority none; entry shall not be cached
    protected static final int CP_LOW    =  0; // cache priority low; entry may be cached
    protected static final int CP_MEDIUM =  1; // cache priority medium; entry shall be cached
    protected static final int CP_HIGH   =  2; // cache priority high; entry must be cached
    
    // static seek pointers
    private static long POS_MAGIC      = 0;                     // 1 byte, byte: file type magic
    private static long POS_BUSY       = POS_MAGIC      + 1;    // 1 byte, byte: marker for synchronization
    private static long POS_PORT       = POS_BUSY       + 1;    // 2 bytes, short: hint for remote db access
    private static long POS_DESCR      = POS_PORT       + 2;    // 60 bytes, string: any description string
    private static long POS_COLUMNS    = POS_DESCR      + 60;   // 2 bytes, short: number of columns in one entry
    private static long POS_OHBYTEC    = POS_COLUMNS    + 2;    // 2 bytes, number of extra bytes on each Node
    private static long POS_OHHANDLEC  = POS_OHBYTEC    + 2;    // 2 bytes, number of Handles on each Node
    private static long POS_USEDC      = POS_OHHANDLEC  + 2;    // 4 bytes, int: used counter
    private static long POS_FREEC      = POS_USEDC      + 4;    // 4 bytes, int: free counter
    private static long POS_FREEH      = POS_FREEC      + 4;    // 4 bytes, int: free pointer (to free chain start)
    private static long POS_MD5PW      = POS_FREEH      + 4;    // 16 bytes, string (encrypted password to this file)
    private static long POS_ENCRYPTION = POS_MD5PW      + 16;   // 16 bytes, string (method description)
    private static long POS_OFFSET     = POS_ENCRYPTION + 16;   // 8 bytes, long (seek position of first record)
    private static long POS_INTPROPC   = POS_OFFSET     + 8;    // 4 bytes, int: number of INTPROP elements
    private static long POS_TXTPROPC   = POS_INTPROPC   + 4;    // 4 bytes, int: number of TXTPROP elements
    private static long POS_TXTPROPW   = POS_TXTPROPC   + 4;    // 4 bytes, int: width of TXTPROP elements
    private static long POS_COLWIDTHS  = POS_TXTPROPW   + 4;    // array of 4 bytes, int[]: sizes of columns
    // after this configuration field comes:
    // POS_HANDLES: INTPROPC * 4 bytes  : INTPROPC Integer properties, randomly accessible
    // POS_TXTPROPS: TXTPROPC * TXTPROPW : an array of TXTPROPC byte arrays of width TXTPROPW that can hold any string
    // POS_NODES : (USEDC + FREEC) * (overhead + sum(all: COLWIDTHS)) : Node Objects

    // values that are only present at run-time
    protected String     filename;     // the database's file name
    protected kelondroRA entryFile;    // the database file
    private   int        overhead;     // OHBYTEC + 4 * OHHANDLEC = size of additional control bytes
    private   int        headchunksize;// overheadsize + key element column size
    private   int        tailchunksize;// sum(all: COLWIDTHS) minus the size of the key element colum
    private   int        recordsize;   // (overhead + sum(all: COLWIDTHS)) = the overall size of a record

    // dynamic run-time seek pointers
    private long POS_HANDLES = 0; // starts after end of POS_COLWIDHS which is POS_COLWIDTHS + COLWIDTHS.length * 4
    private long POS_TXTPROPS = 0; // starts after end of POS_HANDLES which is POS_HANDLES + HANDLES.length * 4
    private long POS_NODES  = 0; // starts after end of POS_TXTPROPS which is POS_TXTPROPS + TXTPROPS.length * TXTPROPW

    // dynamic variables that are back-ups of stored values in file; read/defined on instantiation
    private   int               USEDC;       // counter of used elements
    private   int               FREEC;       // counter of free elements in list of free Nodes
    private   Handle            FREEH;       // pointer to first element in list of free Nodes, empty = NUL
    private   short             OHBYTEC;     // number of extra bytes in each node
    private   short             OHHANDLEC;   // number of handles in each node
    private   int               COLWIDTHS[]; // array with widths of columns
    private   Handle            HANDLES[];   // array with handles
    private   byte[]            TXTPROPS[];  // array with text properties
    private   int               TXTPROPW;    // size of a single TXTPROPS element

    // caching buffer
    private HashMap[]             XcacheHeaders; // the cache; holds overhead values and key element
    private int                   XcacheSize;    // number of cache records
    private long                  XcacheStartup; // startup time; for cache aging
    private kelondroMScoreCluster XcacheScore;   // controls cache aging


    public kelondroRecords(File file, long buffersize /* bytes */,
                           short ohbytec, short ohhandlec,
			   int[] columns, int FHandles, int txtProps, int txtPropWidth) throws IOException {
	// creates a new file
	// file: the file that shall be created
	// oha : overhead size array of four bytes: oha[0]=# of bytes, oha[1]=# of shorts, oha[2]=# of ints, oha[3]=# of longs, 
	// columns: array with size of column width; columns.length is number of columns
	// FHandles: number of integer properties
	// txtProps: number of text properties

	if (file.exists()) throw new IOException("kelondroRecords: file " + file + " already exist");
	this.filename   = file.getCanonicalPath();
        kelondroRA raf = new kelondroFileRA(this.filename);
        //kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename));
        //kelondroRA raf = new kelondroNIOFileRA(this.filename, false, 10000);
        init(raf, ohbytec, ohhandlec, columns, FHandles, txtProps, txtPropWidth);
        initCache(buffersize);
    }
    
    public kelondroRecords(kelondroRA ra, long buffersize /* bytes */,
                           short ohbytec, short ohhandlec,
			   int[] columns, int FHandles, int txtProps, int txtPropWidth) throws IOException {
        this.filename = null;
        init(ra, ohbytec, ohhandlec, columns, FHandles, txtProps, txtPropWidth);
        initCache(buffersize);
    }
   
    private void init(kelondroRA ra, short ohbytec, short ohhandlec,
                      int[] columns, int FHandles, int txtProps, int txtPropWidth) throws IOException {

	// store dynamic run-time data
	this.entryFile  = ra;
	this.overhead   = ohbytec + 4 * ohhandlec;
	this.recordsize = this.overhead;
	for (int i = 0; i < columns.length; i++) this.recordsize += columns[i];
        this.headchunksize = overhead + columns[0];
        this.tailchunksize = this.recordsize - this.headchunksize;

	// store dynamic run-time seek pointers 
	POS_HANDLES     =  POS_COLWIDTHS + columns.length * 4;
	POS_TXTPROPS    =  POS_HANDLES + FHandles * 4;
	POS_NODES       =  POS_TXTPROPS + txtProps * txtPropWidth;

	// store dynamic back-up variables
	USEDC           = 0;
	FREEC           = 0;
	FREEH           = new Handle(NUL);
	OHBYTEC         = ohbytec;
	OHHANDLEC       = ohhandlec;
	COLWIDTHS       = columns;
	HANDLES         = new Handle[FHandles]; for (int i = 0; i < FHandles; i++) HANDLES[i] = new Handle(NUL);
	TXTPROPS        = new byte[txtProps][];  for (int i = 0; i < txtProps; i++) TXTPROPS[i] = new byte[0];
	TXTPROPW        = txtPropWidth;

	// write data to file
	entryFile.seek(POS_MAGIC);       entryFile.writeByte(4);     // magic marker for this file type
	entryFile.seek(POS_BUSY);        entryFile.writeByte(0);     // unlock: default
	entryFile.seek(POS_PORT);        entryFile.writeShort(4444); // default port (not used yet)
	entryFile.seek(POS_DESCR);       entryFile.write("--AnomicRecords file structure--".getBytes());
	entryFile.seek(POS_COLUMNS);     entryFile.writeShort(this.COLWIDTHS.length);
	entryFile.seek(POS_OHBYTEC);	 entryFile.writeShort(OHBYTEC);
	entryFile.seek(POS_OHHANDLEC);	 entryFile.writeShort(OHHANDLEC);
	entryFile.seek(POS_USEDC);       entryFile.writeInt(this.USEDC);
	entryFile.seek(POS_FREEC);       entryFile.writeInt(this.FREEC);
	entryFile.seek(POS_FREEH);       entryFile.writeInt(this.FREEH.index);
	entryFile.seek(POS_MD5PW);       entryFile.write("PASSWORDPASSWORD".getBytes());
	entryFile.seek(POS_ENCRYPTION);  entryFile.write("ENCRYPTION!#$%&?".getBytes());
	entryFile.seek(POS_OFFSET);      entryFile.writeLong(POS_NODES);
	entryFile.seek(POS_INTPROPC);    entryFile.writeInt(FHandles);
	entryFile.seek(POS_TXTPROPC);    entryFile.writeInt(txtProps);
	entryFile.seek(POS_TXTPROPW);    entryFile.writeInt(txtPropWidth);

	// write configuration arrays
	for (int i = 0; i < this.COLWIDTHS.length; i++) {
	    entryFile.seek(POS_COLWIDTHS + 4 * i);
	    entryFile.writeInt(COLWIDTHS[i]);
	}
	for (int i = 0; i < this.HANDLES.length; i++) {
	    entryFile.seek(POS_HANDLES + 4 * i);
	    entryFile.writeInt(NUL);
	    HANDLES[i] = new Handle(NUL);
	}
	for (int i = 0; i < this.TXTPROPS.length; i++) {
	    entryFile.seek(POS_TXTPROPS + TXTPROPW * i);
	    for (int j = 0; j < TXTPROPW; j++) entryFile.writeByte(0);
	}

	// thats it!
    }

    public void clear() throws IOException {
        // Removes all mappings from this map
	//throw new UnsupportedOperationException("clear not supported");
        USEDC = 0;
	FREEC = 0;
	FREEH = new Handle(NUL);
	entryFile.seek(POS_USEDC); entryFile.writeInt(this.USEDC);
	entryFile.seek(POS_FREEC); entryFile.writeInt(this.FREEC);
	entryFile.seek(POS_FREEH); entryFile.writeInt(this.FREEH.index);
    }

    public kelondroRecords(File file, long buffersize) throws IOException{
	// opens an existing tree
	if (!file.exists()) throw new IOException("kelondroRecords: file " + file.getAbsoluteFile().toString() + " does not exist");

        this.filename = file.getCanonicalPath();
        kelondroRA raf = new kelondroFileRA(this.filename);
        //kelondroRA raf = new kelondroBufferedRA(new kelondroFileRA(this.filename));
        //kelondroRA raf = new kelondroCachedRA(new kelondroFileRA(this.filename), 5000000, 1000);
        //kelondroRA raf = new kelondroNIOFileRA(this.filename, (file.length() < 4000000), 10000);
        init(raf);
        initCache(buffersize);
    }
    
    public kelondroRecords(kelondroRA ra, long buffersize) throws IOException{
        this.filename = null;
        init(ra);
        initCache(buffersize);
    }

    private void init(kelondroRA ra) throws IOException {
        
        // assign values that are only present at run-time
	this.entryFile = ra;

	// read dynamic variables that are back-ups of stored values in file; read/defined on instantiation
	entryFile.seek(POS_USEDC); this.USEDC = entryFile.readInt();
	entryFile.seek(POS_FREEC); this.FREEC = entryFile.readInt();
	entryFile.seek(POS_FREEH); this.FREEH = new Handle(entryFile.readInt());

	entryFile.seek(POS_OHBYTEC); OHBYTEC = entryFile.readShort();
	entryFile.seek(POS_OHHANDLEC); OHHANDLEC = entryFile.readShort();

	entryFile.seek(POS_COLUMNS); this.COLWIDTHS = new int[entryFile.readShort()];
	entryFile.seek(POS_INTPROPC); this.HANDLES = new Handle[entryFile.readInt()];
	entryFile.seek(POS_TXTPROPC); this.TXTPROPS = new byte[entryFile.readInt()][];
	entryFile.seek(POS_TXTPROPW); this.TXTPROPW = entryFile.readInt();

        if (COLWIDTHS.length == 0) throw new kelondroException(filename, "init: zero columns; strong failure");
        
	// calculate dynamic run-time seek pointers
	POS_HANDLES = POS_COLWIDTHS + COLWIDTHS.length * 4;
	POS_TXTPROPS = POS_HANDLES  + HANDLES.length * 4;
	POS_NODES  = POS_TXTPROPS  + TXTPROPS.length * TXTPROPW;

	// read configuration arrays
	for (int i = 0; i < COLWIDTHS.length; i++) {
	    entryFile.seek(POS_COLWIDTHS + 4 * i);
	    COLWIDTHS[i] = entryFile.readInt();
	}
	for (int i = 0; i < HANDLES.length; i++) {
	    entryFile.seek(POS_HANDLES + 4 * i);
	    HANDLES[i] = new Handle(entryFile.readInt());
	}
	for (int i = 0; i < TXTPROPS.length; i++) {
	    entryFile.seek(POS_TXTPROPS + TXTPROPW * i);
	    TXTPROPS[i] = new byte[TXTPROPW];
	    entryFile.read(TXTPROPS[i], 0, TXTPROPS[i].length);
	}

	// assign remaining values that are only present at run-time
	this.overhead = OHBYTEC + 4 * OHHANDLEC;
	this.recordsize = this.overhead;
        for (int i = 0; i < COLWIDTHS.length; i++) this.recordsize += COLWIDTHS[i];
        this.headchunksize = this.overhead + COLWIDTHS[0];
        this.tailchunksize = this.recordsize - this.headchunksize;
    }
    
    private void initCache(long buffersize) {
        if (buffersize <= 0) {
            this.XcacheSize = 0;
            this.XcacheHeaders = null;
            this.XcacheScore = null;
        } else {
            if ((buffersize / cacheChunkSize(false)) > size()) {
                this.XcacheSize = (int) (buffersize / cacheChunkSize(false));
                this.XcacheScore = null;
            } else {
                this.XcacheSize = (int) (buffersize / cacheChunkSize(true));
                this.XcacheScore = new kelondroMScoreCluster();
            }
            this.XcacheHeaders = new HashMap[]{new HashMap(), new HashMap(), new HashMap()};
        }
        this.XcacheStartup = System.currentTimeMillis();
    }
    
    public File file() {
        if (filename == null) return null;
        return new File(filename);
    }
    
    private int cacheChunkSize(boolean cacheControl) {
        return this.headchunksize + element_in_cache + ((cacheControl) ? cache_control_entry : 0);
    }
    
    public int cacheChunkSize() {
        return cacheChunkSize(this.XcacheScore != null);
    }
    
    public int[] cacheFillStatus() {
        if (XcacheHeaders == null) return new int[]{0,0,0,0};
        return new int[]{XcacheSize - (XcacheHeaders[CP_HIGH].size() + XcacheHeaders[CP_MEDIUM].size() + XcacheHeaders[CP_LOW].size()), XcacheHeaders[CP_HIGH].size(), XcacheHeaders[CP_MEDIUM].size(), XcacheHeaders[CP_LOW].size()};
    }
    
   
    protected Node newNode() throws IOException {
        return new Node();
    }
    
    protected Node getNode(Handle handle) throws IOException {
        return getNode(handle, null, 0);
    }
    
    protected Node getNode(Handle handle, Node parentNode, int referenceInParent) throws IOException {
        return new Node(handle, parentNode, referenceInParent);
    }
    
    protected void deleteNode(Handle handle) throws IOException {
        if (XcacheSize != 0) {
            synchronized (XcacheHeaders) {
                if (XcacheScore == null) {
                    XcacheHeaders[0].remove(handle);
                    XcacheHeaders[1].remove(handle);
                    XcacheHeaders[2].remove(handle);
                } else if (XcacheHeaders[0].get(handle) != null) {
                    XcacheScore.deleteScore(handle);
                    XcacheHeaders[0].remove(handle);
                } else if (XcacheHeaders[1].get(handle) != null) {
                    XcacheScore.deleteScore(handle);
                    XcacheHeaders[1].remove(handle);
                } else if (XcacheHeaders[2].get(handle) != null) {
                    XcacheScore.deleteScore(handle);
                    XcacheHeaders[2].remove(handle);
                }
            }
        }
        dispose(handle);
    }
    
    private void checkCacheSpace(int forPriority) {
        // check for space in cache
        // should be only called within a synchronized(XcacheHeaders) environment
        if (XcacheSize == 0) return; // no caching
        if (XcacheScore == null) return; // no cache control (== no deletion)
        long free = Runtime.getRuntime().freeMemory();
        long cs = XcacheHeaders[0].size() + XcacheHeaders[1].size() + XcacheHeaders[2].size();
        if (cs == 0) return;
        if ((cs < XcacheSize) && (free >= memBlock)) return; // no need to flush cache space
        Handle delkey;
        // (free < memKcolb)) ??
        
        // delete one entry
        try {
            delkey = (Handle) XcacheScore.getMinObject(); // error (see below) here
            int cp = priorityOfCache(delkey);
            if (cp <= forPriority) {
                // deletion is ok, it affects an entry that has less priority
                XcacheScore.deleteScore(delkey);
                XcacheHeaders[cp].remove(delkey);
            } else {
                for (int i = 0; i < 3; i++) {
                    if (XcacheHeaders[i].size() > 0) {
                        delkey = (Handle) XcacheHeaders[i].keySet().iterator().next();
                        XcacheScore.deleteScore(delkey);
                        XcacheHeaders[i].remove(delkey);
                        break;
                    }
                }
            }
        } catch (NoSuchElementException e) {
            System.out.println("strange kelondroRecords error: " + e.getMessage() + "; cachesize=" + XcacheSize + ", cache.size()=[" + XcacheHeaders[0].size() + "," + XcacheHeaders[1].size() + "," + XcacheHeaders[2].size() + "], cacheScore.size()=" + XcacheScore.size());
            // this is a strange error and could be caused by internal java problems
            // we simply clear the cache
            this.XcacheScore = new kelondroMScoreCluster();
            this.XcacheHeaders[0] = new HashMap();
            this.XcacheHeaders[1] = new HashMap();
            this.XcacheHeaders[2] = new HashMap();
        }
        delkey = null;
    }
    
    private int priorityOfCache(Handle handle) {
        if (XcacheHeaders[CP_HIGH].get(handle) != null) return CP_HIGH;
        if (XcacheHeaders[CP_MEDIUM].get(handle) != null) return CP_MEDIUM;
        if (XcacheHeaders[CP_LOW].get(handle) != null) return CP_LOW;
        return CP_NONE;
    }
        
    public class Node {
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
	private Handle handle    = null; // index of the entry, by default NUL means undefined
        private byte[] headChunk = null; // contains ohBytes, ohHandles and the key value
        private byte[] tailChunk = null; // contains all values except the key value
        private boolean headChanged = false;
        private boolean tailChanged = false;
        
	private Node() throws IOException {
	    // create a new empty node and reserve empty space in file for it
            // use this method only if you want to extend the file with new entries
            // without the need to have content in it.
            this.handle = new Handle();
            
            // create empty chunks
            this.headChunk = new byte[headchunksize];
            this.tailChunk = new byte[tailchunksize];
            for (int i = 0; i < headchunksize; i++) this.headChunk[i] = 0;
            for (int i = 0; i < tailchunksize; i++) this.tailChunk[i] = 0;
            this.headChanged = true;
            this.tailChanged = true;
	}
        
	private Node(Handle handle) throws IOException {
	    // this creates an entry with an pre-reserved entry position
	    // values can be written using the setValues() method
	    // but we expect that values are already there in the file ready to be read which we do not here
	    if (handle == null) throw new IllegalArgumentException("INTERNAL ERROR: node handle is null.");
            if (handle.index >=	USEDC + FREEC) throw new kelondroException(filename, "INTERNAL ERROR: node handle index exceeds size.");  

            // use given handle
	    this.handle = new Handle(handle.index);
            
            // init the content
            initContent();
	}

        private Node(Handle handle, Node parentNode, int referenceInParent) throws IOException {
	    // this creates an entry with an pre-reserved entry position
	    // values can be written using the setValues() method
	    // but we expect that values are already there in the file ready to be read which we do not here
	    if (handle == null) throw new IllegalArgumentException("INTERNAL ERROR: node handle is null.");

            // the parentNode can be given if an auto-fix in the following case is wanted
            if (handle.index >= USEDC + FREEC) {
                if (parentNode == null) {
                    throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index exceeds size. No auto-fix node was submitted. This is a serious failure.");  
                } else {
                    try {
                        parentNode.setOHHandle(referenceInParent, null);
                        parentNode.commit(CP_NONE);
                        throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. The bad node has been auto-fixed");
                    } catch (IOException ee) {
                        throw new kelondroException(filename, "INTERNAL ERROR, Node/init: node handle index " + handle.index + " exceeds size. It was tried to fix the bad node, but failed with an IOException: " + ee.getMessage());
                    }
                }
            }

            // use given handle
	    this.handle = new Handle(handle.index);
            
            // init the content
            initContent();
	}
        
        private void initContent() throws IOException {
            // create chunks; read them from file or cache
            this.tailChunk = null;
            if (XcacheSize == 0) {
                // read overhead and key
                //System.out.println("**NO CACHE for " + this.handle.index + "**");
                this.headChunk = new byte[headchunksize];
                synchronized (entryFile) {
                    entryFile.seek(seekpos(this.handle));
                    entryFile.readFully(this.headChunk, 0, this.headChunk.length);
                }
                this.headChanged = false;
            } else synchronized(XcacheHeaders) {
                byte[] cacheEntry = (byte[]) XcacheHeaders[CP_HIGH].get(this.handle); // first try
                if (cacheEntry == null) cacheEntry = (byte[]) XcacheHeaders[CP_MEDIUM].get(this.handle); // second try
                if (cacheEntry == null) cacheEntry = (byte[]) XcacheHeaders[CP_LOW].get(this.handle); // third try
                if (cacheEntry == null) {
                    // read overhead and key
                    //System.out.println("**CACHE miss for " + this.handle.index + "**");
                    this.headChunk = new byte[headchunksize];
                    //this.tailChunk = new byte[tailchunksize];
                    synchronized (entryFile) {
                        entryFile.seek(seekpos(this.handle));
                        entryFile.readFully(this.headChunk, 0, this.headChunk.length);
                        //entryFile.read(this.tailChunk, 0, this.tailChunk.length);
                    }
                    this.headChanged = true; // provoke a cache store
                    int cp = CP_HIGH;
                    if (OHHANDLEC == 3) {
                        Handle l = getOHHandle(1);
                        Handle r = getOHHandle(2);
                        if ((l == null) && (r == null)) cp = CP_LOW;
                        else if ((l == null) || (r == null)) cp = CP_MEDIUM;
                    }
                    checkCacheSpace(cp);
                    updateNodeCache(cp);
                } else {
                    //System.out.println("**CACHE HIT for " + this.handle.index + "**");
                    // copy cache entry
                    this.headChunk = new byte[headchunksize];
                    System.arraycopy(cacheEntry, 0, this.headChunk, 0, headchunksize);
                    // update cache scores to announce this cache hit
                    if (XcacheScore != null) XcacheScore.setScore(this.handle, (int) ((System.currentTimeMillis() - XcacheStartup) / 1000));
                    this.headChanged = false;
                }
            }
        }
        
        private void setValue(byte[] value, int valuewidth, byte[] targetarray, int targetoffset) {
            if (value == null) {
                while (valuewidth-- > 0) targetarray[targetoffset + valuewidth] = 0;
            } else {
                System.arraycopy(value, 0, targetarray, targetoffset, Math.min(value.length, valuewidth)); // error?
                if (value.length < valuewidth)
                    while (valuewidth-- > value.length) targetarray[targetoffset + valuewidth] = 0;
            }
        }
        
        protected Handle handle() {
	    // if this entry has an index, return it
	    if (this.handle.index == NUL) throw new kelondroException(filename, "the entry has no index assigned");
	    return new Handle(this.handle.index);
	}
        
	protected void setOHByte(int i, byte b) throws IOException {
	    if (i >= OHBYTEC) throw new IllegalArgumentException("setOHByte: wrong index " + i);
	    if (this.handle.index == NUL) throw new kelondroException(filename, "setOHByte: no handle assigned");
            this.headChunk[i] = b;
            this.headChanged = true;
	}
        
        protected void setOHHandle(int i, Handle handle) throws IOException {
            if (i >= OHHANDLEC) throw new IllegalArgumentException("setOHHandle: wrong array size " + i);
            if (this.handle.index == NUL) throw new kelondroException(filename, "setOHHandle: no handle assigned");
            if (handle == null) {
                NUL2bytes(this.headChunk, OHBYTEC + 4 * i);
            } else {
                if (handle.index > USEDC + FREEC) throw new kelondroException(filename, "INTERNAL ERROR, setOHHandles: handle " + i + " exceeds file size (" + handle.index + " > " + (USEDC + FREEC) + ")");
                int2bytes(handle.index, this.headChunk, OHBYTEC + 4 * i);
            }
            this.headChanged = true;
        }
        
	protected byte getOHByte(int i) throws IOException {
	    if (i >= OHBYTEC) throw new IllegalArgumentException("getOHByte: wrong index " + i);
	    if (this.handle.index == NUL) throw new kelondroException(filename, "Cannot load OH values");
            return this.headChunk[i];
	}
        
        protected Handle getOHHandle(int i) throws IOException {
            if (this.handle.index == NUL) throw new kelondroException(filename, "Cannot load OH values");
            if (i >= OHHANDLEC) throw new kelondroException(filename, "handle index out of bounds: " + i);
            int h = bytes2int(this.headChunk, OHBYTEC + 4 * i);
            return (h == NUL) ? null : new Handle(h);
        }
        
	public byte[][] setValues(byte[][] row) throws IOException {
	    // if the index is defined, then write values directly to the file, else only to the object
	    byte[][] result = getValues(); // previous value (this loads the values if not already happened)
            
            // set values
            if (this.handle.index != NUL) {
                setValue(row[0], COLWIDTHS[0], headChunk, overhead);
                int offset = 0;
                for (int i = 1; i < row.length; i++) {
                    setValue(row[i], COLWIDTHS[i], tailChunk, offset);
                    offset +=COLWIDTHS[i];
                } 
            }
            this.headChanged = true;
            this.tailChanged = true;
	    return result; // return previous value
	}
        
	public byte[] getKey() throws IOException {
            // read key
            return trimCopy(headChunk, overhead, COLWIDTHS[0]);
	} 
        
	public byte[][] getValues() throws IOException {
            if (this.tailChunk == null) {
                // load all values from the database file
                this.tailChunk = new byte[tailchunksize];
                // read values
                synchronized (entryFile) {
                    entryFile.seek(seekpos(this.handle) + headchunksize);
                    entryFile.read(this.tailChunk, 0, this.tailChunk.length);
                }
            }
            
            // create return value
            byte[][] values = new byte[COLWIDTHS.length][];
            
            // read key
            values[0] = trimCopy(headChunk, overhead, COLWIDTHS[0]);
            
            // read remaining values
            int offset = 0;
            for (int i = 1; i < COLWIDTHS.length; i++) {
                values[i] = trimCopy(tailChunk, offset, COLWIDTHS[i]);
                offset += COLWIDTHS[i];
            }
            
            return values;
        }

        public synchronized void commit(int cachePriority) throws IOException {
            // this must be called after all write operations to the node are finished
            
            // place the data to the file
            
	    if (this.headChunk == null) {
		// there is nothing to save
		throw new kelondroException(filename, "no values to save (header missing)");
	    }
            
            // save head
            if (this.headChanged) {
                synchronized (entryFile) {
                    entryFile.seek(seekpos(this.handle));
                    //System.out.print("#write "); printChunk(this.handle, this.headChunk); System.out.println();
                    entryFile.write(this.headChunk);
                }
                updateNodeCache(cachePriority);
            }
            
            // save tail
            if ((this.tailChunk != null) && (this.tailChanged)) synchronized (entryFile) {
                entryFile.seek(seekpos(this.handle) + headchunksize);
                entryFile.write(this.tailChunk);
            }
        }
        
        public synchronized void collapse() {
            // this must be called after all write and read operations to the node are finished
            this.headChunk = null;
            this.tailChunk = null;
            this.handle = null;
        }
        
        private byte[] trimCopy(byte[] a, int offset, int length) {
            if (length > a.length - offset) length = a.length - offset;
            while ((length > 0) && (a[offset + length - 1] == 0)) length--;
            if (length == 0) return null;
            byte[] b = new byte[length];
            System.arraycopy(a, offset, b, 0, length);
            return b;
        }
        
	public String toString() {
	    if (this.handle.index == NUL) return "NULL";
	    String s = Integer.toHexString(this.handle.index);
            Handle h;
	    while (s.length() < 4) s = "0" + s;
	    try {
		for (int i = 0; i < OHBYTEC; i++) s = s + ":b" + getOHByte(i);
		for (int i = 0; i < OHHANDLEC; i++) {
                    h = getOHHandle(i);
                    if (h == null) s = s + ":hNULL"; else s = s + ":h" + h.toString();
                }
		byte[][] content = getValues();
		for (int i = 0; i < content.length; i++) s = s + ":" + ((content[i] == null) ? "NULL" : (new String(content[i])).trim());
	    } catch (IOException e) {
		s = s + ":***LOAD ERROR***:" + e.getMessage();
	    }
	    return s;
        }
        
        private void updateNodeCache(int priority) {
            if (this.handle == null) return; // wrong access
            if (this.headChunk == null) return; // nothing there to cache
            if (priority == CP_NONE) return; // it is not wanted that this shall be cached
            if (XcacheSize == 0) return; // we do not use the cache
            int cs = XcacheHeaders[CP_LOW].size() + XcacheHeaders[CP_MEDIUM].size() + XcacheHeaders[CP_HIGH].size();
            if ((cs >= XcacheSize) && (XcacheScore == null)) return; // no cache update if cache is full and not controlled
            if ((cs >= XcacheSize) && (priority == CP_LOW)) return; // no cache update if cache is full and new entry has low priority
            
            synchronized (XcacheHeaders) {
                // remember size to evaluate a cache size check need
                int sizeBefore = cs;
                //long memBefore = Runtime.getRuntime().freeMemory();
                // generate cache entry
                byte[] cacheEntry = new byte[headchunksize];
                System.arraycopy(headChunk, 0, cacheEntry, 0, headchunksize);
                Handle cacheHandle = new Handle(this.handle.index);
                
                // store the cache entry
                //XcacheHeaders.remove(cacheHandle);
                if (priority != CP_LOW) XcacheHeaders[CP_LOW].remove(cacheHandle);
                if (priority != CP_MEDIUM) XcacheHeaders[CP_MEDIUM].remove(cacheHandle);
                if (priority != CP_HIGH) XcacheHeaders[CP_HIGH].remove(cacheHandle);
                XcacheHeaders[priority].put(cacheHandle, cacheEntry);
                if (XcacheScore != null) XcacheScore.setScore(cacheHandle, (int) ((System.currentTimeMillis() - XcacheStartup) / 1000));
                
                // delete the cache entry buffer
                cacheEntry = null;
                cacheHandle = null;
                //System.out.println("kelondroRecords cache4" + filename + ": cache record size = " + (memBefore - Runtime.getRuntime().freeMemory()) + " bytes" + ((newentry) ? " new" : ""));
                // check cache size
                cs = XcacheHeaders[CP_LOW].size() + XcacheHeaders[CP_MEDIUM].size() + XcacheHeaders[CP_HIGH].size();
                if (cs > sizeBefore) checkCacheSpace(priority);
                //System.out.println("kelondroRecords cache4" + filename + ": " + XcacheHeaders.size() + " entries, " + XcacheSize + " allowed.");
                //printCache();
            }
        }
    }
    
    protected void printCache() {
        if (XcacheSize == 0) {
            System.out.println("### file report: " + size() + " entries");
            for (int i = 0; i < size() + 3; i++) {
                // print from  file to compare
                System.out.print("#F " + i + ": ");
                try {synchronized (entryFile) {
                    entryFile.seek(seekpos(new Handle(i)));
                    for (int j = 0; j < headchunksize; j++) System.out.print(entryFile.readByte() + ",");
                }} catch (IOException e) {}
                
                System.out.println();
            }
        } else {
            System.out.println("### cache report: [" + XcacheHeaders[0].size()  + "," + XcacheHeaders[0].size() + "," + XcacheHeaders[0].size() + "] entries");
            for (int cp = 0; cp < 3; cp++) {
                Iterator i = XcacheHeaders[cp].entrySet().iterator();
                Map.Entry entry;
                byte[] b;
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    
                    // print from cache
                    System.out.print("#C " + cp + " ");
                    printChunk((Handle) entry.getKey(), (byte[]) entry.getValue());
                    System.out.println();
                    
                    // print from  file to compare
                    System.out.print("#F " + cp + " " + ((Handle) entry.getKey()).index + ": ");
                    try {synchronized (entryFile) {
                        entryFile.seek(seekpos((Handle) entry.getKey()));
                        for (int j = 0; j < headchunksize; j++) System.out.print(entryFile.readByte() + ",");
                    }} catch (IOException e) {}
                    
                    System.out.println();
                }
            }
        }
        System.out.println("### end report");
    }
    
    private void printChunk(Handle handle, byte[] chunk) {
        System.out.print(handle.index + ": ");
        for (int j = 0; j < chunk.length; j++) System.out.print(chunk[j] + ",");
    }
    
    /*
    public class Node {
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
	private byte[]    ohBytes  = null;  // the overhead bytes, OHBYTEC values
	private Handle[]  ohHandle= null;  // the overhead handles, OHHANDLEC values
	private byte[][]  values  = null;  // an array of byte[] nodes is the value vector
	private Handle    handle  = new Handle(NUL); // index of the entry, by default NUL means undefined
        
	private Node() {
	    // create a new empty node and reserve empty space in file for it
            // use this method only if you want to extend the file with new entries
            // without the need to have content in it.
            try {
                this.handle = new Handle(NUL);
                this.values = new byte[COLWIDTHS.length][];
                for (int i = 0; i < COLWIDTHS.length; i++) this.values[i] = null;
                this.ohBytes = null;
                this.ohHandle = null;
                save();
            } catch (IOException e) {
                throw new kelondroException(filename, "kelondro file out of space");
            }
	}
        private Node(byte[][] v) {
	    // this defines an entry, but it does not lead to writing these entry values to the file
	    // storing this entry can be done using the 'save()' command
	    if (v == null) throw new IllegalArgumentException("Node values = NULL");
	    if ((v.length != COLWIDTHS.length) || (v.length < 1))
		throw new IllegalArgumentException("Node value vector has wrong length");
	    this.values = v;
	    this.handle = new Handle(NUL);
	    this.ohBytes = null;
            this.ohHandle = null;
	}
	private Node(Handle handle) {
	    // this creates an entry with an pre-reserved entry position
	    // values can be written using the setValues() method
	    // but we expect that values are already there in the file ready to be read which we do not here
	    if (handle == null) throw new IllegalArgumentException("INTERNAL ERROR: node handle is null.");
            if (handle.index > 	USEDC + FREEC) throw new kelondroException(filename, "INTERNAL ERROR: node handle index exceeds size.");  

            // set values and read node
            this.values = null;
	    this.handle.index  = handle.index;
	    this.ohBytes = null;
            this.ohHandle = null;
	}
        private Node(Handle handle, Node parentNode, int referenceInParent) {
	    // this creates an entry with an pre-reserved entry position
	    // values can be written using the setValues() method
	    // but we expect that values are already there in the file ready to be read which we do not here
	    if (handle == null) throw new IllegalArgumentException("INTERNAL ERROR: node handle is null.");

            // the parentNode can be given if an auto-fix in the following case is wanted
            if (handle.index > 	USEDC + FREEC) {
                if (parentNode == null) {
                    throw new kelondroException(filename, "INTERNAL ERROR: node handle index exceeds size. No auto-fix node was submitted. This is a serious failure.");  
                } else {
                    try {
                        Handle[] handles = parentNode.getOHHandle();
                        handles[referenceInParent] = null;
                        parentNode.setOHHandle(handles);
                        throw new kelondroException(filename, "INTERNAL ERROR: node handle index exceeds size. The bad node has been auto-fixed");
                    } catch (IOException ee) {
                        throw new kelondroException(filename, "INTERNAL ERROR: node handle index exceeds size. It was tried to fix the bad node, but failed with an IOException: " + ee.getMessage());
                    }
                }
            }

            // set values and read node
            this.values = null;
	    this.handle.index  = handle.index;
	    this.ohBytes = null;
            this.ohHandle = null;
	}
        
        public void finalize() {
            ohBytes  = null;
            ohHandle = null;
            values  = null;
            handle = null;
        }
        
	protected Handle handle() {
	    // if this entry has an index, return it
	    if (this.handle.index == NUL) throw new kelondroException(filename, "the entry has no index assigned");
	    return new Handle(this.handle.index);
	}
	protected void setOHByte(byte[] b) throws IOException {
	    if (b == null) throw new IllegalArgumentException("setOHByte: setting null value does not make any sense");
	    if (b.length != OHBYTEC) throw new IllegalArgumentException("setOHByte: wrong array size");
	    if (this.handle.index == NUL) throw new kelondroException(filename, "setOHByte: no handle assigned");
	    if (this.ohBytes == null) this.ohBytes = new byte[OHBYTEC];
	    synchronized (entryFile) {
		entryFile.seek(seekpos(this.handle));
		for (int j = 0; j < ohBytes.length; j++) {
		    ohBytes[j] = b[j];
		    entryFile.writeByte(b[j]);
		}
	    }
            updateNodeCache();
	}
	protected void setOHHandle(Handle[] i) throws IOException {
	    if (i == null) throw new IllegalArgumentException("setOHint: setting null value does not make any sense");
	    if (i.length != OHHANDLEC) throw new IllegalArgumentException("setOHHandle: wrong array size");
	    if (this.handle.index == NUL) throw new kelondroException(filename, "setOHHandle: no handle assigned");
	    if (this.ohHandle == null) this.ohHandle = new Handle[OHHANDLEC];
	    synchronized (entryFile) {
		entryFile.seek(seekpos(this.handle) + OHBYTEC);
		for (int j = 0; j < ohHandle.length; j++) {
		    ohHandle[j] = i[j];
		    if (i[j] == null) 
			entryFile.writeInt(NUL);
		    else
			entryFile.writeInt(i[j].index);
		}
	    }
            updateNodeCache();
	}
	protected byte[] getOHByte() throws IOException {
	    if (ohBytes == null) {
		if (this.handle.index == NUL) throw new kelondroException(filename, "Cannot load OH values");
		ohBytes = new byte[OHBYTEC];
		synchronized (entryFile) {
		    entryFile.seek(seekpos(this.handle));
		    for (int j = 0; j < ohBytes.length; j++) {
			ohBytes[j] = entryFile.readByte();
		    }
		}
	    }
	    return ohBytes;
	}
	protected Handle[] getOHHandle() throws IOException {
	    if (ohHandle == null) {
		if (this.handle.index == NUL) throw new kelondroException(filename, "Cannot load OH values");
		ohHandle = new Handle[OHHANDLEC];
		synchronized (entryFile) {
		    entryFile.seek(seekpos(this.handle) + OHBYTEC);
		    int i;
		    for (int j = 0; j < ohHandle.length; j++) {
			i = entryFile.readInt();
			ohHandle[j] = (i == NUL) ? null : new Handle(i);
		    }
		}
	    }
	    return ohHandle;
	}
	public byte[][] setValues(byte[][] row) throws IOException {
	    // if the index is defined, then write values directly to the file, else only to the object
	    byte[][] result = getValues(); // previous value (this loads the values if not already happened)
	    if (this.values == null) this.values = new byte[COLWIDTHS.length][];
            for (int i = 0; i < values.length; i++) {
                this.values[i] = row[i];
            }
            if (this.handle.index != NUL) {
		// store data directly to database
		synchronized (entryFile) {
		    long seek = seekpos(this.handle) + overhead;
		    for (int i = 0; i < values.length; i++) {
			entryFile.seek(seek);
			if (values[i] == null) {
			    for (int j = 0; j < COLWIDTHS[i]; j++)
				entryFile.writeByte(0);
			} else if (values[i].length >= COLWIDTHS[i]) {
			    entryFile.write(values[i], 0 , COLWIDTHS[i]);
			} else {
			    entryFile.write(values[i]);
			    for (int j = values[i].length; j < COLWIDTHS[i]; j++)
				entryFile.writeByte(0);
			}
			seek += COLWIDTHS[i];
		    }
		}
                updateNodeCache();
	    }
	    //System.out.print("setValues result: "); for (int i = 0; i < values.length; i++) System.out.print(new String(result[i]) + " "); System.out.println(".");
            return result; // return previous value
	}
        
	public byte[] getKey() throws IOException {
	    if ((values == null) || (values[0] == null)) {
		// load from database, but ONLY the key!
		if (this.handle.index == NUL) {
		    throw new kelondroException(filename, "Cannot load Key");
		} else {
		    values = new byte[COLWIDTHS.length][];
		    values[0] = new byte[COLWIDTHS[0]];
		    synchronized (entryFile) {
			entryFile.seek(seekpos(this.handle) + overhead);
			entryFile.read(values[0], 0, values[0].length);
		    }
		    for (int i = 1; i < COLWIDTHS.length; i++) values[i] = null;
		    return values[0];
		}
	    } else {
		return values[0];
	    }
	} 
        
	public byte[][] getValues() throws IOException {
	    if ((values == null) || (values[0] == null)) {
		// load ALL values from database
		if (this.handle.index == NUL) {
		    throw new kelondroException(filename, "Cannot load values");
		} else {
		    values = new byte[COLWIDTHS.length][];
		    synchronized (entryFile) {
			long seek = seekpos(this.handle) + overhead;
			for (int i = 0; i < COLWIDTHS.length; i++) {
			    entryFile.seek(seek);
			    values[i] = new byte[COLWIDTHS[i]];
			    entryFile.read(values[i], 0, values[i].length);
                            if (allZero(values[i])) values[i] = null;
			    seek += COLWIDTHS[i];
			}
		    }
		    return values;
		}
	    } else if ((values.length > 1) && (values[1] == null)) {
		// only the key has been read; load the remaining
		synchronized (entryFile) {
		    long seek = seekpos(this.handle) + overhead + COLWIDTHS[0];
		    for (int i = 1; i < COLWIDTHS.length; i++) {
			entryFile.seek(seek);
			values[i] = new byte[COLWIDTHS[i]];
			entryFile.read(values[i], 0, values[i].length);
                        if (allZero(values[i])) values[i] = null;
			seek += COLWIDTHS[i];
		    }
		}
		return values;
	    } else {
		return values;
	    }
	}
        
	protected synchronized void save() throws IOException {
	    // this is called when an entry was defined with values only and not by retrieving with an index
	    // if this happens, nothing of the internal array values have been written to the file
	    // then writing to the file is done here
	    // can only be called if the index has not been defined yet
	    if (this.handle.index != NUL) {
		throw new kelondroException(filename, "double assignement of handles");
	    }
	    // create new index by expanding the file at the end
	    // or by recycling used records
	    this.handle = new Handle();
	    // place the data to the file
	    //if ((values == null) || ((values != null) && (values.length > 1) && (values[1] == null))) {
            if (values == null) {
		// there is nothing to save
		throw new kelondroException(filename, "no values to save");
	    }
	    entryFile.seek(seekpos(this.handle));
	    if (ohBytes == null) {for (int i = 0; i < OHBYTEC; i++) entryFile.writeByte(0);}
	    else {for (int i = 0; i < OHBYTEC; i++) entryFile.writeByte(ohBytes[i]);}
	    if (ohHandle == null) {for (int i = 0; i < OHHANDLEC; i++) entryFile.writeInt(0);}
	    else {for (int i = 0; i < OHHANDLEC; i++) entryFile.writeInt(ohHandle[i].index);}
	    long seek = seekpos(this.handle) + overhead;
            for (int i = 0; i < values.length; i++) {
		entryFile.seek(seek);
		if (values[i] == null) {
		    for (int j = 0; j < COLWIDTHS[i]; j++) entryFile.writeByte(0);
		} else if (values[i].length >= COLWIDTHS[i]) {
		    entryFile.write(values[i], 0, COLWIDTHS[i]);
		} else {
		    entryFile.write(values[i]);
		    for (int j = values[i].length; j < COLWIDTHS[i]; j++) entryFile.writeByte(0);
		}
		seek = seek + COLWIDTHS[i];
	    }
	}
        private boolean allZero(byte[] a) {
            for (int i = 0; i < a.length; i++) if (a[i] != 0) return false;
            return true;
        }
	public String toString() {
	    if (this.handle.index == NUL) return "NULL";
	    String s = Integer.toHexString(this.handle.index);
	    while (s.length() < 4) s = "0" + s;
	    try {
		byte[] b = getOHByte();
		for (int i = 0; i < b.length; i++) s = s + ":b" + b[i];
		Handle[] h = getOHHandle();
		for (int i = 0; i < h.length; i++) if (h[i] == null) s = s + ":hNULL"; else s = s + ":h" + h[i].toString();
		byte[][] content = getValues();
		for (int i = 0; i < content.length; i++) s = s + ":" + (new String(content[i])).trim();
	    } catch (IOException e) {
		s = s + ":***LOAD ERROR***:" + e.getMessage();
	    }
	    return s;
        }
        private void updateNodeCache() {
            if (this.handle == null) return;
            if (this.values == null) return;
            if (this.ohBytes == null) return;
            if (this.ohHandle == null) return;
            if (XcacheSize != 0) {
                synchronized (XcacheHeaders) {
                    // remember size to evaluate a cache size check need
                    int sizeBefore = XcacheHeaders.size();
                    //long memBefore = Runtime.getRuntime().freeMemory();
                    // generate cache entry
                    byte[][] cacheValue;
                    if (values == null) {
                        cacheValue = null;
                    } else {
                        cacheValue = new byte[values.length][];
                        cacheValue[0] = values[0];
                        for (int i = 1; i < values.length; i++) cacheValue[i] = null;
                    }
                    Node cacheNode = new Node(cacheValue);
                    cacheNode.handle = this.handle;
                    cacheNode.ohBytes = this.ohBytes;
                    cacheNode.ohHandle = this.ohHandle;
                    // store the cache entry
                    boolean newentry = XcacheHeaders.put(cacheNode.handle, cacheNode) == null;
                    XcacheScore.setScore(cacheNode.handle, (int) ((System.currentTimeMillis() - XcacheStartup) / 1000));
                    // delete the cache entry
                    cacheNode = null;
                    //System.out.println("kelondroRecords cache4" + filename + ": cache record size = " + (memBefore - Runtime.getRuntime().freeMemory()) + " bytes" + ((newentry) ? " new" : ""));
                    // check cache size
                    if (XcacheHeaders.size() > sizeBefore) checkCacheSpace();
                    //System.out.println("kelondroRecords cache4" + filename + ": " + XcacheHeaders.size() + " entries, " + XcacheSize + " allowed.");
                }
            }
        }
    }
    */
    
    public synchronized int columns() {
	return this.COLWIDTHS.length;
    }
    
    public synchronized int columnSize(int column) {
	if ((column < 0) || (column >= this.COLWIDTHS.length)) return -1;
	return this.COLWIDTHS[column];
    }
    
    private long seekpos(Handle handle) {
	return POS_NODES + ((long) recordsize * handle.index);
    }
    
    // additional properties
    public synchronized int handles() {
	return this.HANDLES.length;
    }
    
    protected void setHandle(int pos, Handle handle) throws IOException {
	if (pos >= HANDLES.length) throw new IllegalArgumentException("setHandle: handle array exceeded");
	if (handle == null) handle = new Handle(NUL);
	HANDLES[pos] = handle;
	entryFile.seek(POS_HANDLES + 4 * pos);
	entryFile.writeInt(handle.index);
    }

    protected Handle getHandle(int pos) throws IOException {
	if (pos >= HANDLES.length) throw new IllegalArgumentException("getHandle: handle array exceeded");
	return (HANDLES[pos].index == NUL) ? null : HANDLES[pos];
    }

    // custom texts
    public void setText(int pos, byte[] text) throws IOException {
	if (pos >= TXTPROPS.length) throw new IllegalArgumentException("setText: text array exceeded");
	if (text.length > TXTPROPW) throw new IllegalArgumentException("setText: text lemgth exceeded");
	if (text == null) text = new byte[0];
	TXTPROPS[pos] = text;
	entryFile.seek(POS_TXTPROPS + TXTPROPW * pos);
	entryFile.write(text);
    }

    public byte[] getText(int pos) throws IOException {
	if (pos >= TXTPROPS.length) throw new IllegalArgumentException("getText: text array exceeded");
	return TXTPROPS[pos];
    }
    
    // Returns true if this map contains no key-value mappings.
    public boolean isEmpty() {
	return (USEDC == 0);
    }

    // Returns the number of key-value mappings in this map.
    public int size() {
	return this.USEDC;
    }

    protected int free() {
	return this.FREEC;
    }

    private void dispose(Handle h) throws IOException {
	// delete element with handle h
	// this element is then connected to the deleted-chain and can be re-used
	// change counter
	synchronized (entryFile) {
	    USEDC--; entryFile.seek(POS_USEDC); entryFile.writeInt(USEDC);
	    FREEC++; entryFile.seek(POS_FREEC); entryFile.writeInt(FREEC);
	    // change pointer
	    if (this.FREEH.index == NUL) {
		// the first entry
		entryFile.seek(seekpos(h)); entryFile.writeInt(NUL); // write null link at end of free-list
	    } else {
		// another entry
		entryFile.seek(seekpos(h)); entryFile.writeInt(this.FREEH.index); // extend free-list
	    }
	    // write new FREEH Handle link
	    this.FREEH = h;
	    entryFile.seek(POS_FREEH); entryFile.writeInt(this.FREEH.index);
	}
    }

    public void close() throws IOException {
	if (this.entryFile != null) this.entryFile.close();
	this.entryFile = null;
    }

    public void finalize() {
	try {
	    close();
	} catch (IOException e) {}
    }

    protected static String[] line2args(String line) {
	// parse the command line
	if ((line == null) || (line.length() == 0)) return null;
	String args[];
	StringTokenizer st = new StringTokenizer(line);
	
	args = new String[st.countTokens()];
	for (int i = 0; st.hasMoreTokens(); i++) {
	    args[i] = st.nextToken();
	}
	st = null;
	return args;
    }

    protected static boolean equals(byte[] a, byte[] b) {
	if (a == b) return true;
	if ((a == null) || (b == null)) return false;
	if (a.length != b.length) return false;
	for (int n = 0; n < a.length; n++) if (a[n] != b[n]) return false;
	return true;
    }
    
    public static byte[] long2bytes(long x, int length) {
        byte[] b = new byte[length];
        for (int i = length - 1; i >= 0; i--) {
            b[i] = (byte) (x & 0XFF);
            x >>= 8;
        }
        return b;
    }
    
    public static long bytes2long(byte[] b) {
        if (b == null) return 0;
        long x = 0;
        for (int i = 0; i < b.length; i++) x = (x << 8) | (0xff & (int) b[i]);
        return x;
    }
    
    public static void NUL2bytes(byte[] b, int offset) {
        b[offset    ] = (byte) (0XFF & (NUL >> 24));
        b[offset + 1] = (byte) (0XFF & (NUL >> 16));
        b[offset + 2] = (byte) (0XFF & (NUL >>  8));
        b[offset + 3] = (byte) (0XFF & NUL);
    }
    
    public static void int2bytes(long i, byte[] b, int offset) {
        b[offset    ] = (byte) (0XFF & (i >> 24));
        b[offset + 1] = (byte) (0XFF & (i >> 16));
        b[offset + 2] = (byte) (0XFF & (i >>  8));
        b[offset + 3] = (byte) (0XFF & i);
    }
    
    public static int bytes2int(byte[] b, int offset) {
        return (
            ((b[offset    ] & 0xff) << 24) |
            ((b[offset + 1] & 0xff) << 16) |
            ((b[offset + 2] & 0xff) << 8) |
             (b[offset + 3] & 0xff));  
    }

    public void print(boolean records) throws IOException {
	System.out.println("REPORT FOR FILE '" + this.filename + "':");
	System.out.println("--");
	System.out.println("CONTROL DATA");
	System.out.print(  "  HANDLES    : " + HANDLES.length + " int-values");
	if (HANDLES.length == 0) System.out.println(); else {
	    System.out.print("  {" + HANDLES[0].toString());
	    for (int i = 1; i < HANDLES.length; i++) System.out.print(", " + HANDLES[i].toString());
	    System.out.println("}");
	}
	System.out.print(  "  TXTPROPS   : " + TXTPROPS.length + " strings, max length " + TXTPROPW + " bytes");
	if (TXTPROPS.length == 0) System.out.println(); else {
	    System.out.print("  {'" + (new String(TXTPROPS[0])).trim()); System.out.print("'");
	    for (int i = 1; i < TXTPROPS.length; i++) System.out.print(", '" + (new String(TXTPROPS[i])).trim() + "'");
	    System.out.println("}");
	}
	System.out.println("  USEDC      : " + this.USEDC);
	System.out.println("  FREEC      : " + this.FREEC);
	System.out.println("  FREEH      : " + FREEH.toString());
	System.out.println("  Data Offset: 0x" + Long.toHexString(POS_NODES));
	System.out.println("--");
	System.out.println("RECORDS");
	System.out.print(  "  Columns    : " + columns() + " columns  {" + COLWIDTHS[0]);
	for (int i = 1; i < columns(); i++) System.out.print(", " + COLWIDTHS[i]);
	System.out.println("}");
	System.out.println("  Overhead   : " + this.overhead + " bytes  ("+ OHBYTEC + " OH bytes, " + OHHANDLEC + " OH Handles)");
	System.out.println("  Recordsize : " + this.recordsize + " bytes");
	System.out.println("--");
        printCache();
        System.out.println("--");
        
	if (!(records)) return;
	// print also all records
	for (int i = 0; i < USEDC + FREEC; i++) System.out.println("NODE: " + new Node(new Handle(i), null, 0).toString());
    }

    public String toString() {
	return size() + " RECORDS IN FILE " + filename;
    }
    
    protected class Handle implements Comparable {
	private int index;
	private Handle() throws IOException {
	    // reserves a new record and returns index of record
	    // the return value is not a seek position
	    // the seek position can be retrieved using the seekpos() function
	    if (FREEC == 0) {
		// generate new entry
		index = USEDC + FREEC;
		USEDC++; entryFile.seek(POS_USEDC); entryFile.writeInt(USEDC);
	    } else {
		// re-use record from free-list
		USEDC++; entryFile.seek(POS_USEDC); entryFile.writeInt(USEDC);
		FREEC--; entryFile.seek(POS_FREEC); entryFile.writeInt(FREEC);
		// take link
		if (FREEH.index == NUL) {
		    System.out.println("INTERNAL ERROR (DATA INCONSISTENCY): re-use of records failed, lost " + (FREEC + 1) + " records. Affected file: " + filename);
                    // try to heal..
                    USEDC = USEDC + FREEC + 1; entryFile.seek(POS_USEDC); entryFile.writeInt(USEDC);
                    FREEC = 0; entryFile.seek(POS_FREEC); entryFile.writeInt(FREEC);
                    index = USEDC - 1;
		} else {
		    index = FREEH.index;
		    // read link to next element to FREEH chain
		    entryFile.seek(seekpos(FREEH)); FREEH.index = entryFile.readInt();
		    // write new FREEH link
		    entryFile.seek(POS_FREEH); entryFile.writeInt(FREEH.index);
		}
	    }
	}
	protected Handle(int index) {
	    this.index = index;
	}
	public String toString() {
	    if (index == NUL) return "NULL";
	    String s = Integer.toHexString(index);
	    while (s.length() < 4) s = "0" + s;
	    return s;
	}
	public boolean equals(Handle h) {
	    return (this.index == h.index);
	}
        public boolean equals(Object h) {
	    return (this.index == ((Handle) h).index);
	}
        public int compare(Object h0, Object h1) {
            if (((Handle) h0).index < ((Handle) h1).index) return -1;
            if (((Handle) h0).index > ((Handle) h1).index) return 1;
            return 0;
        }
        public int compareTo(Object h) { // this is needed for a treeMap compare
            if (index < ((Handle) h).index) return -1;
            if (index > ((Handle) h).index) return 1;
            return 0;
        }
        public int hashCode() {
            return this.index;
        }
    }

}
