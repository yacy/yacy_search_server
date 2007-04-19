// kelondroIntBytesMap.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 08.06.2006 on http://www.anomic.de
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

public class kelondroIntBytesMap {

	// we use two indexes: one for initialization, and one for data aquired during runtime
	// this has a gread advantage, if the setup-data is large. Then a re-organisation of
	// the run-time data does not need much memory and is done faster.
	// we distinguish two phases: the init phase where data can only be written
	// to index0 with addb, and a runtime-phase where data can only be written
	// to index1 with putb.
	
    private kelondroRowSet index0, index1; 
    private kelondroRow rowdef;
    private boolean initPhase;
    
    public kelondroIntBytesMap(int payloadSize, int initSize) {
    	rowdef = new kelondroRow("Cardinal key-4 {b256}, byte[] payload-" + payloadSize, kelondroNaturalOrder.naturalOrder, 0);
        index0 = new kelondroRowSet(rowdef, initSize);
        index1 = new kelondroRowSet(rowdef, 0);
        initPhase = true;
    }
    
    public int size() {
        return index0.size() + index1.size();
    }
    
    public long memoryNeededForGrow() {
        return index1.memoryNeededForGrow();
    }
    
    public byte[] getb(int ii) {
    	byte[] key = kelondroNaturalOrder.encodeLong((long) ii, 4);
        kelondroRow.Entry indexentry = index0.get(key);
        if (indexentry == null) indexentry = index1.get(key);
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
    public void addb(int ii, byte[] value) {
    	assert initPhase;
        kelondroRow.Entry newentry = index0.row().newEntry();
        newentry.setCol(0, (long) ii);
        newentry.setCol(1, value);
        index0.addUnique(newentry);
    }
    
    public byte[] putb(int ii, byte[] value) {
    	initPhase = false;
    	kelondroRow.Entry newentry = index1.row().newEntry();
        newentry.setCol(0, (long) ii);
        newentry.setCol(1, value);
        kelondroRow.Entry indexentry = index0.get(kelondroNaturalOrder.encodeLong((long) ii, 4));
    	if (indexentry != null) {
    		index0.put(newentry);
    		return indexentry.getColBytes(1);
    	}
        kelondroRow.Entry oldentry = index1.put(newentry);
        if (oldentry == null) return null;
        return oldentry.getColBytes(1);
    }

    public byte[] removeb(int ii) {
        if ((index0.size() == 0) && (index1.size() == 0)) return null;
        byte[] key = kelondroNaturalOrder.encodeLong((long) ii, 4);
        kelondroRow.Entry indexentry = index0.remove(key);
        if (indexentry != null) {
        	return indexentry.getColBytes(1);
        }
        indexentry = index1.remove(key);
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
    public byte[] removeoneb() {
    	if ((index0.size() == 0) && (index1.size() == 0)) return null;
    	if (index1.size() == 0) {
    		kelondroRow.Entry indexentry = index0.removeOne();
            if (indexentry == null) return null;
            return indexentry.getColBytes(1);
    	}
        kelondroRow.Entry indexentry = index1.removeOne();
        if (indexentry == null) return null;
        return indexentry.getColBytes(1);
    }
    
    public Iterator rows() {
    	return new kelondroMergeIterator(
    				index0.rows(true, null),
    				index1.rows(true, null),
    				rowdef.objectOrder,
    				kelondroMergeIterator.simpleMerge,
                    true);
    }
    
    public void flush() {
        index0.sort();
        index0.trim(true);
        index1.sort();
        index1.trim(true);
    }
    
    public kelondroProfile profile() {
    	return kelondroProfile.consolidate(index0.profile(), index1.profile());
    }
    
    public static void main(String[] args) {
    	boolean assertEnabled = false;
    	assert  assertEnabled = true;
    	System.out.println((assertEnabled) ? "asserts are enabled" : "enable asserts with 'java -ea'; not enabled yet");
		long start = System.currentTimeMillis();
		long randomstart = 0;
		Random random = new Random(randomstart);
		long r;
		Long R;
		int p, rc = 0;
		ArrayList ra = new ArrayList();
		HashSet jcontrol = new HashSet();
		kelondroIntBytesMap kcontrol = new kelondroIntBytesMap(1, 0);
		for (int i = 0; i < 1000000; i++) {
			r = Math.abs(random.nextLong() % 10000);
			//System.out.println("add " + r);
			jcontrol.add(new Long(r));
			kcontrol.putb((int) r, "x".getBytes());
			if (random.nextLong() % 5 == 0) ra.add(new Long(r));
			if ((ra.size() > 0) && (random.nextLong() % 7 == 0)) {
				rc++;
				p = Math.abs(random.nextInt()) % ra.size();
				R = (Long) ra.get(p);
				//System.out.println("remove " + R.longValue());
				jcontrol.remove(R);
				kcontrol.removeb((int) R.longValue());
				assert kcontrol.removeb((int) R.longValue()) == null;
			}
			assert jcontrol.size() == kcontrol.size();
		}
		System.out.println("removed: " + rc + ", size of jcontrol set: "
				+ jcontrol.size() + ", size of kcontrol set: "
				+ kcontrol.size());
		System.out.println("Time: "
				+ ((System.currentTimeMillis() - start) / 1000) + " seconds");
	}
    
}
