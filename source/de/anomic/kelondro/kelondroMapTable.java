// kelondroapTable.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 13.03.2005
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


// this is mainly a convenience class to bundle many kelondroMap Objects

package de.anomic.kelondro;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class kelondroMapTable {
    
    HashMap mTables, tTables, sTables;
    File tablesPath;

    public kelondroMapTable(File tablesPath) {
        this.mTables = new HashMap();
        this.tTables = new HashMap();
        this.sTables = new HashMap();
        this.tablesPath = tablesPath;
        if (!(tablesPath.exists())) tablesPath.mkdirs();
    }
    
    public void declareMaps(
            String tablename, int keysize, int nodesize, int cacheslots,
            char fillChar) throws IOException {
        declareMaps(tablename, keysize, nodesize, cacheslots, null, null, null, fillChar);
    }
    
    public void declareMaps(
            String tablename, int keysize, int nodesize, int cacheslots,
            String[] sortfields, String[] longaccfields, String[] doubleaccfields, char fillChar) throws IOException {
        declareMaps(tablename, keysize, nodesize, cacheslots, sortfields, longaccfields, doubleaccfields, fillChar, 0x800, 0);
    }
    
    public void declareMaps(
            String tablename, int keysize, int nodesize, int cacheslots,
            String[] sortfields, String[] longaccfields, String[] doubleaccfields, char fillChar,
            long buffersize /*bytes*/, long preloadTime) throws IOException {
        if (mTables.containsKey(tablename)) throw new RuntimeException("kelondroTables.declareMap: table '" + tablename + "' declared twice.");
        if (tTables.containsKey(tablename)) throw new RuntimeException("kelondroTables.declareMap: table '" + tablename + "' declared already in other context.");
        File tablefile = new File(tablesPath, "table." + tablename + ".mdb");
        kelondroDyn dyn;
        if (!(tablefile.exists())) tablefile.getParentFile().mkdirs();
        dyn = new kelondroDyn(tablefile, buffersize, preloadTime, keysize, nodesize, fillChar, true, false);
        kelondroMapObjects map = new kelondroMapObjects(dyn, cacheslots, sortfields, longaccfields, doubleaccfields);
        mTables.put(tablename, map);
    }
    
    public void declareTree(String tablename, kelondroRow rowdef, long buffersize /*bytes*/, long preloadTime) throws IOException  {
        if (mTables.containsKey(tablename)) throw new RuntimeException("kelondroTables.declareTree: table '" + tablename + "' declared already in other context.");
        if (tTables.containsKey(tablename)) throw new RuntimeException("kelondroTables.declareTree: table '" + tablename + "' declared twice.");
        File tablefile = new File(tablesPath, "table." + tablename + ".tdb");
        kelondroIndex Tree = new kelondroCache(kelondroTree.open(tablefile, buffersize / 2, preloadTime, rowdef), buffersize / 2, true, false);
        tTables.put(tablename, Tree);
    }

    public synchronized void update(String tablename, String key, Map map) throws IOException {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table == null) throw new RuntimeException("kelondroTables.update: map table '" + tablename + "' does not exist.");
        if (key.length() > table.keySize()) key = key.substring(0, table.keySize());
        table.set(key, map);
        mTables.put(tablename, table);
    }
    
    public synchronized void update(String tablename, kelondroRow.Entry row /* first element is the unique key = index */) throws IOException {
        kelondroIndex tree = (kelondroIndex) tTables.get(tablename);
        if (tree == null) throw new RuntimeException("kelondroTables.update: tree table '" + tablename + "' does not exist.");
        tree.put(row);
        tTables.put(tablename, tree);
    }
    
    public synchronized Map selectMap(String tablename, String key) throws IOException {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table == null) throw new RuntimeException("kelondroTables.selectMap: map table '" + tablename + "' does not exist.");
        if (key.length() > table.keySize()) key = key.substring(0, table.keySize());
        return table.getMap(key);
    }

    public synchronized kelondroRow.Entry selectByte(String tablename, String key) throws IOException {
        kelondroIndex tree = (kelondroIndex) tTables.get(tablename);
        if (tree == null) throw new RuntimeException("kelondroTables.selectByte: tree table '" + tablename + "' does not exist.");
        return tree.get(key.getBytes());
    }

    public synchronized kelondroMapObjects.mapIterator /* of Map-Elements */ maps(String tablename, boolean up, boolean rotating) throws IOException {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table == null) throw new RuntimeException("kelondroTables.maps: map table '" + tablename + "' does not exist.");
        return table.maps(up, rotating);
    }
    
    public synchronized kelondroMapObjects.mapIterator /* of Map-Elements */ maps(String tablename, boolean up, boolean rotating, byte[] firstKey) throws IOException {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table == null) throw new RuntimeException("kelondroTables.maps: map table '" + tablename + "' does not exist.");
        return table.maps(up, rotating, firstKey);
    }
    
    public synchronized kelondroMapObjects.mapIterator /* of Map-Elements */ maps(String tablename, boolean up, String field) {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table == null) throw new RuntimeException("kelondroTables.maps: map table '" + tablename + "' does not exist.");
        return table.maps(up, field);
    }
    
    public synchronized Iterator /* of kelondroRow.Entry-Elements */ rows(String tablename, boolean up, boolean rotating, byte[] firstKey) throws IOException {
       kelondroIndex tree = (kelondroIndex) tTables.get(tablename);
        if (tree == null) throw new RuntimeException("kelondroTables.bytes: tree table '" + tablename + "' does not exist.");
        return tree.rows(up, rotating, firstKey);
    }
    
    // if you need the long-values from a row-iteration, please use kelondroRecords.bytes2long to convert from byte[] to long
    
    public synchronized void delete(String tablename, String key) throws IOException {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (key.length() > table.keySize()) key = key.substring(0, table.keySize());
        if (table != null) {table.remove(key); mTables.put(tablename, table); return;}
      
        kelondroIndex Tree = (kelondroIndex) tTables.get(tablename);
        if (Tree != null) {Tree.remove(key.getBytes()); tTables.put(tablename, Tree); return;}
        
        throw new RuntimeException("kelondroTables.delete: table '" + tablename + "' does not exist.");
    }
    
    public synchronized long longAccumulator(String tablename, String field) {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table == null) throw new RuntimeException("kelondroTables.accumulator: map table '" + tablename + "' does not exist.");
        return table.getLongAcc(field);
    }
    
    public synchronized double doubleAccumulator(String tablename, String field) {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table == null) throw new RuntimeException("kelondroTables.accumulator: map table '" + tablename + "' does not exist.");
        return table.getDoubleAcc(field);
    }
    
    public synchronized int size(String tablename) {
        kelondroMapObjects table = (kelondroMapObjects) mTables.get(tablename);
        if (table != null) return table.size();
        
        kelondroIndex Tree = (kelondroIndex) tTables.get(tablename);
        if (Tree != null) try { return Tree.size(); } catch (IOException e) {return 0;}
        
        throw new RuntimeException("kelondroTables.accumulator: table '" + tablename + "' does not exist.");
    }
    
    public void close() throws IOException {
        Iterator tablesIt = mTables.values().iterator();
        while (tablesIt.hasNext()) ((kelondroMapObjects) tablesIt.next()).close();
        mTables = null;
        
        Iterator TreeIt = tTables.values().iterator();
        while (TreeIt.hasNext()) ((kelondroIndex) TreeIt.next()).close();
        tTables = null;
    }
    
}
