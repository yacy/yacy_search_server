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
import java.util.HashMap;
import java.util.Iterator;

public class kelondroDynTree {
    
    // basic data structures
    private kelondroRow rowdef;
    private kelondroDyn table;
    private HashMap<String, kelondroRA> treeRAHandles;
    private File file;

    // some properties to control caching and buffering
    //private int maxcountCache = 1000, maxsizeCache = 100;
    private int maxcountBuffer = 1000, maxsizeBuffer = 100;
    //private long maxageCache = 60000, cycletimeCache = 10000;
    private long maxageBuffer = 60000, cycletimeBuffer = 10000;

    // data structures for the cache and buffer
    private HashMap<String, treeBuffer> buffer;
    private HashMap<String, treeCache> cache;
    private long cycleBuffer;
    
    public kelondroDynTree(File file, int keylength, int nodesize, kelondroRow rowdef, char fillChar, boolean resetOnFail) {
        // creates or opens a DynTree
        this.file = file;
        this.rowdef = rowdef;
        this.buffer = new HashMap<String, treeBuffer>();
        this.cache = new HashMap<String, treeCache>();
        //this.cycleCache = Long.MIN_VALUE;
        this.cycleBuffer = Long.MIN_VALUE;
        this.table = new kelondroDyn(file, true, true, keylength, nodesize, fillChar, rowdef.objectOrder, true, false, resetOnFail);
        this.treeRAHandles = new HashMap<String, kelondroRA>();
    }
    
    public void close() throws IOException {
        Iterator<String> e = treeRAHandles.keySet().iterator();
        while (e.hasNext()) closeTree((String) e.next());
        int size = table.sizeDyn();
        table.close();
        if (size == 0) this.file.delete();
    }
    
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
        try {
            return new kelondroTree(ra, this.file.getCanonicalPath() + "#" + key, true, 0, rowdef, false);
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    protected kelondroTree getTree(String key) throws IOException {
        if (table.existsDyn(key)) {
            kelondroRA ra = table.getRA(key);
            treeRAHandles.put(key, ra);
            return new kelondroTree(ra, this.file.getCanonicalPath() + "#" + key, true, 0);
        }
        return null;
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
        private HashMap<String, kelondroRow.Entry> tcache;
        public long timestamp;
        
        treeCache(String tablename) {
            this.tablename = tablename;
            this.tcache = new HashMap<String, kelondroRow.Entry>(); // for key-row relations
            this.timestamp = Long.MAX_VALUE; // to flag no-update
        }
        
        public kelondroRow.Entry get(byte[] key) throws IOException {
            kelondroRow.Entry entry = (kelondroRow.Entry) tcache.get(new String(key));
            if (entry == null) {
                kelondroTree t = getTree(this.tablename);
                entry = t.get(key);
                t.close();
                this.tcache.put(new String(key), entry);
                this.timestamp = System.currentTimeMillis();
            } 
            return entry;
        }
        
        protected void put(kelondroRow.Entry entry) { // this is only used internal
            this.tcache.put(entry.getColString(0, null), entry);
            this.timestamp = System.currentTimeMillis();
        }
        
        protected void remove(byte[] key) {
            this.tcache.remove(new String(key));
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    protected class treeBuffer {
        
        private String tablename;
        protected HashMap<String, kelondroRow.Entry> tbuffer;
        public long timestamp;
        
        treeBuffer(String tablename) {
            this.tablename = tablename;
            this.tbuffer = new HashMap<String, kelondroRow.Entry>(); // for key-row relations
            this.timestamp = Long.MAX_VALUE; // to flag no-update
        }
        
        public void put(kelondroRow.Entry entry) {
            this.tbuffer.put(entry.getColString(0, null), entry);
            this.timestamp = System.currentTimeMillis();
        }
        
        public void remove(byte[] key) {
            this.tbuffer.remove(new String(key));
            this.timestamp = System.currentTimeMillis();
        }

        protected void flush() throws IOException {
            this.timestamp = System.currentTimeMillis();
            if (this.tbuffer.size() == 0) return;
            Iterator<String> e = this.tbuffer.keySet().iterator();
            kelondroTree t = getTree(this.tablename);
            kelondroRow.Entry entry;
            String key;
            while (e.hasNext()) {
                key = e.next();
                entry = (kelondroRow.Entry) this.tbuffer.get(key);
                t.put(entry);
            }
            t.close();
        }
    }
    
    /*******************************************************/
    
   
    // read cached
    public synchronized kelondroRow.Entry get(String tablename, byte[] key) throws IOException {
        treeCache tc = (treeCache) cache.get(table);
        if (tc == null) {
            tc = new treeCache(tablename);
            cache.put(tablename, tc);
        }
        return tc.get(key);
    }
    
    // write buffered
    public synchronized void put(String tablename, kelondroRow.Entry newrow) {
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
        Iterator<String> e = buffer.keySet().iterator();
        String tablename;
        treeBuffer tb;
        while (e.hasNext()) {
            tablename = e.next();
            tb = (treeBuffer) buffer.get(tablename);
            if ((System.currentTimeMillis() - tb.timestamp > this.maxageBuffer) ||
                (tb.tbuffer.size() > this.maxsizeBuffer) ||
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
                kelondroDynTree dt = new kelondroDynTree(file, 16, 512, new kelondroRow("byte[] a-10, byte[] b-20, byte[] c-30", kelondroNaturalOrder.naturalOrder, 0), '_', true);
                System.out.println("opened: table keylength=" + dt.table.row().width(0) + ", sectorsize=" + dt.table.row().width(1) + ", " + dt.table.sizeDyn() + " entries.");
            } else {
                kelondroDynTree dt = new kelondroDynTree(file, 16, 512, new kelondroRow("byte[] a-10, byte[] b-20, byte[] c-30", kelondroNaturalOrder.naturalOrder, 0), '_', true);
                String name;
                kelondroTree t;
                kelondroRow.Entry line;
                for (int i = 1; i < 100; i++) {
                    name = "test" + i;
                    t = dt.newTree(name);
                    line = t.row().newEntry(new byte[][] {"".getBytes(), "abc".getBytes(), "def".getBytes()});
                    for (int j = 1; j < 10; j++) {
                        line.setCol(0, ("entry" + j).getBytes());
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
