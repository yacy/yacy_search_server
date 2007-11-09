// kelondroBytesIntMap.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 18.06.2006 on http://www.anomic.de
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

package de.anomic.kelondro;

import java.io.IOException;
import java.util.Iterator;

public class kelondroBytesIntMap {
    
    private kelondroRow rowdef;
    private kelondroIndex index0, index1;
    
    public kelondroBytesIntMap(kelondroIndex ki) {
        assert (ki.row().columns() == 2); // must be a key/index relation
        assert (ki.row().width(1) == 4);  // the value must be a b256-encoded int, 4 bytes long
        this.index0 = null; // not used
        this.index1 = ki;
        this.rowdef = ki.row();
    }
    
    public kelondroBytesIntMap(int keylength, kelondroOrder objectOrder, int space) {
        this.rowdef = new kelondroRow(new kelondroColumn[]{new kelondroColumn("key", kelondroColumn.celltype_binary, kelondroColumn.encoder_bytes, keylength, "key"), new kelondroColumn("int c-4 {b256}")}, objectOrder, 0);
        this.index0 = new kelondroRowSet(rowdef, space);
        this.index1 = null; // to show that this is the initialization phase
    }
    
    public kelondroRow row() {
        return index0.row();
    }
    
    public synchronized int geti(byte[] key) throws IOException {
        assert (key != null);
        //assert (!(serverLog.allZero(key)));
        if (index0 != null) {
            if (index1 == null) {
                // finish initialization phase
                if (index0 instanceof kelondroRowSet) {
                    ((kelondroRowSet) index0).sort();
                    ((kelondroRowSet) index0).uniq();
                }
                index1 = new kelondroRowSet(rowdef, 0);
                //System.out.println("finished initialization phase at size = " + index0.size() + " in geti");
            }
            kelondroRow.Entry indexentry = index0.get(key);
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            if (indexentry != null) return (int) indexentry.getColLong(1);
        }
        assert (index1 != null);
        kelondroRow.Entry indexentry = index1.get(key);
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        if (indexentry == null) return -1;
        return (int) indexentry.getColLong(1);
    }
    
    public synchronized int puti(byte[] key, int i) throws IOException {
    	assert i >= 0 : "i = " + i;
        assert (key != null);
        //assert (!(serverLog.allZero(key)));
        if (index0 != null) {
            if (index1 == null) {
                // finish initialization phase
                if (index0 instanceof kelondroRowSet) {
                    ((kelondroRowSet) index0).sort();
                    ((kelondroRowSet) index0).uniq();
                }
                index1 = new kelondroRowSet(rowdef, 0);
                //System.out.println("finished initialization phase at size = " + index0.size() + " in puti");
            }
            // if the new entry is within the initialization part, just overwrite it
            kelondroRow.Entry indexentry = index0.get(key);
            if (indexentry != null) {
                int oldi = (int) indexentry.getColLong(1);
                indexentry.setCol(0, key);
                indexentry.setCol(1, i);
                index0.put(indexentry);
                //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
                return oldi;
            }
            // else place it in the index1
        }
        // at this point index1 cannot be null
        assert (index1 != null);
        kelondroRow.Entry newentry = index1.row().newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        kelondroRow.Entry oldentry = index1.put(newentry);
        if (oldentry == null) return -1;
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return (int) oldentry.getColLong(1);
    }
    
    public synchronized void addi(byte[] key, int i) throws IOException {
    	assert i >= 0 : "i = " + i;
        assert (key != null);
        assert index0 != null;
        //assert index1 == null;
        if (index1 != null) {
            // the initialization phase is over, put this entry to the secondary index
            puti(key, i);
            return;
        }
        //assert (!(serverLog.allZero(key)));
        kelondroRow.Entry newentry = this.rowdef.newEntry();
        newentry.setCol(0, key);
        newentry.setCol(1, i);
        index0.addUnique(newentry);
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
    }
    
    public synchronized int removei(byte[] key) throws IOException {
        assert (key != null);
        //assert (!(serverLog.allZero(key)));
        // returns the integer index of the key, if the key can be found and was removed
        // and -1 if the key was not found.
        if (index0 != null) {
            if (index1 == null) {
                // finish initialization phase
                if (index0 instanceof kelondroRowSet) {
                    ((kelondroRowSet) index0).sort();
                    ((kelondroRowSet) index0).uniq();
                }
                index1 = new kelondroRowSet(rowdef, 0);
                //System.out.println("finished initialization phase at size = " + index0.size() + " in removei");
            }
            // if the new entry is within the initialization part, just overwrite it
            kelondroRow.Entry indexentry = index0.remove(key, true);
            if (indexentry != null) {
                assert index0.remove(key, true) == null; // check if remove worked
                //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
                return (int) indexentry.getColLong(1);
            }
            // else remove it from the index1
        }
        // at this point index1 cannot be null
        assert (index1 != null);
        if (index1.size() == 0) return -1;
        kelondroRow.Entry indexentry = index1.remove(key, true);
        if (indexentry == null) return -1;
        assert index1.remove(key, true) == null; // check if remove worked
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return (int) indexentry.getColLong(1);
    }

    public synchronized int removeonei() throws IOException {
        if ((index1 != null) && (index1.size() != 0)) {
            kelondroRow.Entry indexentry = index1.removeOne();
            assert (indexentry != null);
            if (indexentry == null) return -1;
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return (int) indexentry.getColLong(1);
        }
        if ((index0 != null) && (index0.size() != 0)) {
            kelondroRow.Entry indexentry = index0.removeOne();
            assert (indexentry != null);
            if (indexentry == null) return -1;
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return (int) indexentry.getColLong(1);
        }
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return -1;
    }
    
    public synchronized int size() {
        if ((index0 != null) && (index1 == null)) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index0.size();
        }
        if ((index0 == null) && (index1 != null)) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index1.size();
        }
        assert ((index0 != null) && (index1 != null));
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return index0.size() + index1.size();
    }
    
    public synchronized kelondroCloneableIterator rows(boolean up, byte[] firstKey) throws IOException {
        // returns the row-iterator of the underlying kelondroIndex
        // col[0] = key
        // col[1] = integer as {b265}
        if ((index0 != null) && (index1 == null)) {
            // finish initialization phase
            if (index0 instanceof kelondroRowSet) {
                ((kelondroRowSet) index0).sort();
                ((kelondroRowSet) index0).uniq();
            }
            index1 = new kelondroRowSet(rowdef, 0);
            //System.out.println("finished initialization phase at size = " + index0.size() + " in rows");
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index0.rows(up, firstKey);
        }
        assert (index1 != null);
        if (index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index1.rows(up, firstKey);
        }
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return new kelondroMergeIterator(
                index0.rows(up, firstKey),
                index1.rows(up, firstKey),
                rowdef.objectOrder,
                kelondroMergeIterator.simpleMerge,
                true);
    }
    
    public kelondroProfile profile() {
        if (index0 == null) return index1.profile();
        if (index1 == null) return index0.profile();
        return kelondroProfile.consolidate(index0.profile(), index1.profile());
    }
    
    public synchronized void close() {
        if (index0 != null) index0.close();
        if (index1 != null) index1.close();
    }

    public synchronized String consistencyAnalysis() {
        String s0 = (index0 == null) ? "index0: is NULL" : ("index0: " + singleConsistency((kelondroRowSet) index0));
        String s1 = (index1 == null) ? "index1: is NULL" : ("index1: " + singleConsistency((kelondroRowSet) index1));
        String combined = "";
        if ((index0 == null) && (index1 == null)) return "all null";
        if ((index0 != null) && (index1 != null)) {
            Iterator i;
            try {
                i = index0.rows(true, null);
                kelondroRow.Entry entry;
                while (i.hasNext()) {
                    entry = (kelondroRow.Entry) i.next();
                    if (index1.has(entry.getColBytes(0))) {
                        combined = combined + ", common = " + new String(entry.getColBytes(0));
                    }
                }
            } catch (IOException e) {}
        }
        return s0 + ", " + s1 + combined;
    }
    
    public synchronized boolean consistencyAnalysis0() {
        boolean s0 = ((index0 == null) || (!(index0 instanceof kelondroRowSet))) ? true : singleConsistency0((kelondroRowSet) index0);
        boolean s1 = ((index1 == null) || (!(index1 instanceof kelondroRowSet))) ? true : singleConsistency0((kelondroRowSet) index1);
        if (!(s0 && s1)) return false;
        if ((index0 == null) && (index1 == null)) return true;
        if ((index0 != null) && (index1 != null)) {
            Iterator i;
            try {
                i = index0.rows(true, null);
                kelondroRow.Entry entry;
                while (i.hasNext()) {
                    entry = (kelondroRow.Entry) i.next();
                    if (index1.has(entry.getColBytes(0))) return false;
                }
            } catch (IOException e) {}
        }
        return true;
    }
    
    private String singleConsistency(kelondroRowSet rs) {
        int s = rs.size();
        rs.sort();
        rs.uniq();
        if (rs.size() == s) return "set is sound"; else return "set has " + (rs.size() - s) + " double-entries";
    }
    private boolean singleConsistency0(kelondroRowSet rs) {
        int s = rs.size();
        rs.sort();
        rs.uniq();
        return rs.size() == s;
    }
}
