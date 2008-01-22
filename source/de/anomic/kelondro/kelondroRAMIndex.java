// kelondroRAMIndex.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.01.2008 on http://yacy.net
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

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import de.anomic.kelondro.kelondroRow.Entry;

public class kelondroRAMIndex implements kelondroIndex {
    
    private kelondroRow rowdef;
    private kelondroRowSet index0, index1;
    private kelondroRow.EntryComparator entryComparator;
    
    public kelondroRAMIndex(kelondroRow rowdef, int initialspace) {
    	this.rowdef = rowdef;
    	this.entryComparator = new kelondroRow.EntryComparator(rowdef.objectOrder);
        reset(initialspace);
    }
    
    public void reset() {
		reset(0);
	}
    
	public void reset(int initialspace) {
	    this.index0 = null; // first flush RAM to make room
		this.index0 = new kelondroRowSet(rowdef, initialspace);
        this.index1 = null; // to show that this is the initialization phase
	}
    
    public kelondroRow row() {
        return index0.row();
    }
    
    private final void finishInitialization() {
    	if (index1 == null) {
            // finish initialization phase
            index0.sort();
            index0.uniq();
            index1 = new kelondroRowSet(rowdef, 0);
        }
    }
    
    public synchronized kelondroRow.Entry get(byte[] key) {
        assert (key != null);
        finishInitialization();
        kelondroRow.Entry indexentry = index0.get(key);
        if (indexentry != null) return indexentry;
        return index1.get(key);
    }

	public boolean has(byte[] key) {
		assert (key != null);
        finishInitialization();
        if (index0.has(key)) return true;
        return index1.has(key);
	}
    
    public synchronized kelondroRow.Entry put(kelondroRow.Entry entry) {
    	assert (entry != null);
    	finishInitialization();
        // if the new entry is within the initialization part, just overwrite it
        kelondroRow.Entry indexentry = index0.get(entry.getPrimaryKeyBytes());
        if (indexentry != null) {
        	index0.put(entry);
            return indexentry;
        }
        // else place it in the index1
        return index1.put(entry);
    }
    
	public Entry put(Entry row, Date entryDate) {
		return put(row);
	}
	
	public void putMultiple(List<Entry> rows) {
		Iterator<Entry> i = rows.iterator();
		while (i.hasNext()) {
			put(i.next());
		}
	}

	public synchronized void addUnique(kelondroRow.Entry entry) {    	
    	assert (entry != null);
        if (index1 == null) {
            // we are in the initialization phase
        	index0.addUnique(entry);
        } else {
        	// initialization is over, add to secondary index
        	index1.addUnique(entry);
        }
    }

	public void addUniqueMultiple(List<Entry> rows) {
		Iterator<Entry> i = rows.iterator();
		while (i.hasNext()) {
			addUnique(i.next());
		}
	}
	
	public synchronized ArrayList<kelondroRowSet> removeDoubles() {
	    // finish initialization phase explicitely
	    if (index1 == null) index1 = new kelondroRowSet(rowdef, 0);
	    return index0.removeDoubles();
	}
	
    public synchronized kelondroRow.Entry remove(byte[] key, boolean keepOrder) {
        assert keepOrder == true; // if this is false, the index must be re-ordered so many times which will cause a major CPU usage
    	finishInitialization();
        // if the new entry is within the initialization part, just delete it
        kelondroRow.Entry indexentry = index0.remove(key, keepOrder);
        if (indexentry != null) {
            assert index0.remove(key, true) == null; // check if remove worked
            return indexentry;
        }
        // else remove it from the index1
        return index1.remove(key, keepOrder);
    }

    public synchronized kelondroRow.Entry removeOne() {
        if ((index1 != null) && (index1.size() != 0)) {
            return index1.removeOne();
        }
        if ((index0 != null) && (index0.size() != 0)) {
        	return index0.removeOne();
        }
        return null;
    }
    
    public synchronized int size() {
        if ((index0 != null) && (index1 == null)) {
            return index0.size();
        }
        if ((index0 == null) && (index1 != null)) {
            return index1.size();
        }
        assert ((index0 != null) && (index1 != null));
        return index0.size() + index1.size();
    }
    
    public synchronized kelondroCloneableIterator<byte[]> keys(boolean up, byte[] firstKey) {
        // returns the key-iterator of the underlying kelondroIndex
        if (index1 == null) {
            // finish initialization phase
            index0.sort();
            index0.uniq();
            index1 = new kelondroRowSet(rowdef, 0);
            return index0.keys(up, firstKey);
        }
        assert (index1 != null);
        if (index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index1.keys(up, firstKey);
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        index1.sort();
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return new kelondroMergeIterator<byte[]>(
                index0.keys(up, firstKey),
                index1.keys(up, firstKey),
                rowdef.objectOrder,
                kelondroMergeIterator.simpleMerge,
                true);
    }

    public synchronized kelondroCloneableIterator<kelondroRow.Entry> rows(boolean up, byte[] firstKey) {
        // returns the row-iterator of the underlying kelondroIndex
        if (index1 == null) {
            // finish initialization phase
            index0.sort();
            index0.uniq();
            index1 = new kelondroRowSet(rowdef, 0);
            return index0.rows(up, firstKey);
        }
        assert (index1 != null);
        if (index0 == null) {
            //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
            return index1.rows(up, firstKey);
        }
        // index0 should be sorted
        // sort index1 to enable working of the merge iterator
        index1.sort();
        //assert consistencyAnalysis0() : "consistency problem: " + consistencyAnalysis();
        return new kelondroMergeIterator<kelondroRow.Entry>(
                index0.rows(up, firstKey),
                index1.rows(up, firstKey),
                entryComparator,
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

	public String filename() {
		return null; // this does not have a file name
	}

}
