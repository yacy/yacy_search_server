// kelondroDynTree.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 20.09.2004
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
 */

package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

public class kelondroDynTree {
    
    // basic data structures
    private int[] columns;
    private kelondroDyn table;
    private Hashtable treeRAHandles;
    private File file;

    // some properties to control caching and buffering
    //private int maxcountCache = 1000, maxsizeCache = 100;
    private int maxcountBuffer = 1000, maxsizeBuffer = 100;
    //private long maxageCache = 60000, cycletimeCache = 10000;
    private long maxageBuffer = 60000, cycletimeBuffer = 10000;
    private long buffersize = 0;

    // data structures for the cache and buffer
    private Hashtable buffer, cache;
    private long cycleBuffer;
    
    public kelondroDynTree(File file, long buffersize, int keylength, int nodesize, int[] columns) throws IOException {
        // creates a new DynTree
        this.file = file;
        this.columns = columns;
        this.buffer = new Hashtable();
        this.cache = new Hashtable();
        //this.cycleCache = Long.MIN_VALUE;
        this.cycleBuffer = Long.MIN_VALUE;
        if (file.exists()) throw new IOException("DynTree " + file.toString() + " already exists");
        this.table = new kelondroDyn(file, buffersize, keylength, nodesize);
        this.treeRAHandles = new Hashtable();
    }

    public kelondroDynTree(File file, long buffersize) throws IOException {
        // opens an existing DynTree
        this.file = file;
        this.buffer = new Hashtable();
        this.cache = new Hashtable();
        //this.cycleCache = Long.MIN_VALUE;
        this.cycleBuffer = Long.MIN_VALUE;
        if (!(file.exists())) throw new IOException("DynTree " + file.toString() + " does not exist");
        this.table = new kelondroDyn(file, buffersize);
        // read one element to measure the size of columns
        if (table.size() == 0) throw new IOException("DynTree " + file.toString() + " is empty. Should not.");
        this.treeRAHandles = new Hashtable();
        Iterator i = table.dynKeys(true, false);
        String onekey = (String) i.next();
        kelondroTree onetree = getTree(onekey);
        this.columns = new int[onetree.columns()];
        for (int j = 0; j < columns.length; j++) columns[j] = onetree.columnSize(j);
        closeTree(onekey);
    }
    
    public void close() throws IOException {
        Enumeration e = treeRAHandles.keys();
        while (e.hasMoreElements()) closeTree((String) e.nextElement());
        int size = table.size();
        table.close();
        if (size == 0) this.file.delete();
    }

    /*
    public void setReadCacheAttr(int maxcount, int maxsize, long maxage, long cycletime) {
        maxcountCache = maxcount;
        maxsizeCache = maxsize;
        maxageCache = maxage;
        cycletimeCache = cycletime;
    }
    */
    
    public void setWriteBufferAttr(int maxcount, int maxsize, long maxage, long cycletime) {
        maxcountBuffer = maxcount;
        maxsizeBuffer = maxsize;
        maxageBuffer = maxage;
        cycletimeBuffer = cycletime;
    }
    
    protected boolean existsTree(String key) throws IOException {
        return table.existsDyn(key);
    }
    
    protected kelondroTree newTree(String key) throws IOException {
        if (table.existsDyn(key)) throw new IOException("table " + key + " already exists.");
        kelondroRA ra = table.getRA(key); // works always, even with no-existing entry
        treeRAHandles.put(key, ra);
        return new kelondroTree(ra, buffersize, columns);
    }
    
    protected kelondroTree getTree(String key) throws IOException {
        if (table.existsDyn(key)) {
            kelondroRA ra = table.getRA(key);
            treeRAHandles.put(key, ra);
            return new kelondroTree(ra, buffersize);
        } else {
            return null;
        }
    }
    
    protected void closeTree(String key) throws IOException {
        kelondroRA ra = (kelondroRA) treeRAHandles.get(key);
        if (ra != null) {
            ra.close();
            treeRAHandles.remove(key);
        }
    }
    
    protected void removeTree(String key) throws IOException {
        kelondroRA ra = (kelondroRA) treeRAHandles.get(key);
        if (ra != null) {
            ra.close();
            treeRAHandles.remove(key);
        }
        table.remove(key);
    }
    

    /*******************************************************/

    protected class treeCache {

        private String tablename;
        private Hashtable cache;
        public long timestamp;
        
        treeCache(String tablename) {
            this.tablename = tablename;
            this.cache = new Hashtable(); // for key-row relations
            this.timestamp = Long.MAX_VALUE; // to flag no-update
        }
        
        public byte[][] get(byte[] key) throws IOException {
            byte[][] entry = (byte[][]) cache.get(key);
            if (entry == null) {
                kelondroTree t = getTree(this.tablename);
                entry = t.get(key);
                t.close();
                this.cache.put(key, entry);
                this.timestamp = System.currentTimeMillis();
            } 
            return entry;
        }
        
        protected void put(byte[][] entry) { // this is only used internal
            this.cache.put(entry[0], entry);
            this.timestamp = System.currentTimeMillis();
        }
        
        protected void remove(byte[] key) {
            this.cache.remove(key);
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    protected class treeBuffer {
        
        private String tablename;
        private Hashtable buffer;
        public long timestamp;
        
        treeBuffer(String tablename) {
            this.tablename = tablename;
            this.buffer = new Hashtable(); // for key-row relations
            this.timestamp = Long.MAX_VALUE; // to flag no-update
        }
        
        public void put(byte[][] entry) {
            this.buffer.put(entry[0], entry);
            this.timestamp = System.currentTimeMillis();
        }
        
        public void remove(byte[] key) {
            this.buffer.remove(key);
            this.timestamp = System.currentTimeMillis();
        }

        protected void flush() throws IOException {
            this.timestamp = System.currentTimeMillis();
            if (this.buffer.size() == 0) return;
            Enumeration e = this.buffer.keys();
            kelondroTree t = getTree(this.tablename);
            byte[][] entry;
            byte[] key;
            while (e.hasMoreElements()) {
                key = (byte[]) e.nextElement();
                entry = (byte[][]) this.buffer.get(key);
                t.put(entry);
            }
            t.close();
        }
    }
    
    /*******************************************************/
    
   
    // read cached
    public synchronized byte[][] get(String tablename, byte[] key) throws IOException {
        treeCache tc = (treeCache) cache.get(table);
        if (tc == null) {
            tc = new treeCache(tablename);
            cache.put(tablename, tc);
        }
        return tc.get(key);
    }

    /*
    // clean-up method for cache:
    private void flushCache() {
        if ((System.currentTimeMillis() - this.cycleCache < this.cycletimeCache) &&
            (cache.size() < this.maxcountCache))  return;
        this.cycleCache = System.currentTimeMillis();
        // collect all caches which have a time > maxagecache
        Enumeration e = cache.keys();
        String tablename;
        treeCache tc;
        while (e.hasMoreElements()) {
            tablename = (String) e.nextElement();
            tc = (treeCache) cache.get(tablename);
            if ((System.currentTimeMillis() - tc.timestamp > this.maxageCache) ||
                (tc.cache.size() > this.maxsizeCache) ||
                (cache.size() > this.maxcountCache)) {
                cache.remove(tablename);
            }
        }
    }
    */
    
    // write buffered
    public synchronized void put(String tablename, byte[][] newrow) {
        treeBuffer tb = (treeBuffer) buffer.get(tablename);
        if (tb == null) {
            tb = new treeBuffer(tablename);
        }
        treeCache tc = (treeCache) cache.get(table);
        if (tc == null) {
            tc = new treeCache(tablename);
            cache.put(tablename, tc);
        }
        tb.put(newrow);
        tc.put(newrow);
        flushBuffer();
    }
    
    public synchronized void remove(String tablename, byte[] key) {
        treeBuffer tb = (treeBuffer) buffer.get(tablename);
        if (tb == null) {
            tb = new treeBuffer(tablename);
        }
        treeCache tc = (treeCache) cache.get(table);
        if (tc == null) {
            tc = new treeCache(tablename);
            cache.put(tablename, tc);
        }
        tb.remove(key);
        tc.remove(key);
        flushBuffer();
    }
    
    public synchronized void removeAll(String tablename) throws IOException {
        buffer.remove(table);
        cache.remove(table);
        kelondroTree t = getTree(tablename);
        t.removeAll();
        flushBuffer();
    }

    // clean-up method for buffer:
    private void flushBuffer() {
        if ((System.currentTimeMillis() - this.cycleBuffer < this.cycletimeBuffer) &&
            (buffer.size() < this.maxcountBuffer))  return;
        this.cycleBuffer = System.currentTimeMillis();
        // collect all buffers which have a time > maxageBuffer
        Enumeration e = buffer.keys();
        String tablename;
        treeBuffer tb;
        while (e.hasMoreElements()) {
            tablename = (String) e.nextElement();
            tb = (treeBuffer) buffer.get(tablename);
            if ((System.currentTimeMillis() - tb.timestamp > this.maxageBuffer) ||
                (tb.buffer.size() > this.maxsizeBuffer) ||
                (buffer.size() > this.maxcountBuffer)) {
                try {tb.flush();} catch (IOException ee) {}
                tb = null;
                buffer.remove(tablename);
            }
        }
    }
    
    
    /*******************************************************/
    
    public static void main(String[] args) {
        // test app
        try {
            System.out.println("start");
            File file = new File("D:\\bin\\testDyn.db");
            if (file.exists()) {
                kelondroDynTree dt = new kelondroDynTree(file, 0x100000L);
                System.out.println("opened: table keylength=" + dt.table.columnSize(0) + ", sectorsize=" + dt.table.columnSize(1) + ", " + dt.table.size() + " entries.");
            } else {
                kelondroDynTree dt = new kelondroDynTree(file, 0x100000L, 16, 512, new int[] {10,20,30});
                String name;
                kelondroTree t;
                byte[][] line = new byte[][] {"".getBytes(), "abc".getBytes(), "def".getBytes()};
                for (int i = 1; i < 100; i++) {
                    name = "test" + i;
                    t = dt.newTree(name);
                    for (int j = 1; j < 10; j++) {
                        line[0] = ("entry" + j).getBytes();
                        t.put(line);
                    }
                    dt.closeTree(name);
                }
            }
            System.out.println("finished");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
