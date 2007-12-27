// indexContainerOrder.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2007 on http://yacy.net
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

package de.anomic.index;

import de.anomic.kelondro.kelondroNode;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRow.Entry;

public class indexContainerOrder implements kelondroOrder {

    private kelondroOrder embeddedOrder;

    public indexContainerOrder(kelondroOrder embedOrder) {
        this.embeddedOrder = embedOrder;
    }

    public boolean wellformed(byte[] a) {
        return embeddedOrder.wellformed(a);
    }
    
    public boolean wellformed(byte[] a, int astart, int alength) {
        return embeddedOrder.wellformed(a, astart, alength);
    }
    
    public void direction(boolean ascending) {
        this.embeddedOrder.direction(ascending);
    }

    public long partition(byte[] key, int forks) {
        return this.embeddedOrder.partition(key, forks);
    }

    public int compare(indexContainer a, indexContainer b) {
        return this.embeddedOrder.compare(a.getWordHash().getBytes(), b.getWordHash().getBytes());
    }

    public byte[] zero() {
        return this.embeddedOrder.zero();
    }

    public void rotate(byte[] zero) {
        this.embeddedOrder.rotate(zero);
    }

    public Object clone() {
        return new indexContainerOrder((kelondroOrder) this.embeddedOrder.clone());
    }

    public String signature() {
        return this.embeddedOrder.signature();
    }

    public long cardinal(byte[] key) {
        return this.embeddedOrder.cardinal(key);
    }

    public int compare(Object a, Object b) {
    	if ((a instanceof byte[]) && (b instanceof byte[])) {
    		return this.embeddedOrder.compare((byte[]) a, (byte[]) b);
    	}
    	if ((a instanceof Integer) && (b instanceof Integer)) {
    		return ((Integer) a).compareTo((Integer) b);
    	}
    	if ((a instanceof String) && (b instanceof String)) {
    		return this.embeddedOrder.compare(((String) a).getBytes(), ((String) b).getBytes());
    	}
    	if ((a instanceof kelondroNode) && (b instanceof kelondroNode)) {
    		return this.embeddedOrder.compare((kelondroNode) a, (kelondroNode) b);
    	}
    	if ((a instanceof kelondroRow.Entry) && (b instanceof kelondroRow.Entry)) {
    		return this.embeddedOrder.compare((kelondroRow.Entry) a, (kelondroRow.Entry) b);
    	}
    	if ((a instanceof indexContainer) && (b instanceof indexContainer)) {
    		return this.embeddedOrder.compare((indexContainer) a, (indexContainer) b); 
    	}
    	Class<? extends Object> ac = a.getClass();
    	Class<? extends Object> bc = b.getClass();
    	if (ac.equals(bc)) {
    		throw new UnsupportedOperationException("compare not implemented for this object type: " + ac);
    	} else {
    		throw new UnsupportedOperationException("compare must be applied to same object classes; here a = " + ac + ", b = " + bc);
    	}
    }
    
    public int compare(String a, String b) {
        return this.embeddedOrder.compare(a, b);
    }

	public int compare(kelondroNode a, kelondroNode b) {
		return this.embeddedOrder.compare(a, b);
	}

	public int compare(Entry a, Entry b) {
		return this.embeddedOrder.compare(a, b);
	}
	
    public int compare(byte[] a, byte[] b) {
        return this.embeddedOrder.compare(a, b);
    }

    public int compare(byte[] a, int aoffset, int alength, byte[] b, int boffset, int blength) {
        return this.embeddedOrder.compare(a, aoffset, alength, b, boffset, blength);
    }

    public boolean equals(kelondroOrder otherOrder) {
        if (!(otherOrder instanceof indexContainerOrder)) return false;
        return this.embeddedOrder.equals(((indexContainerOrder) otherOrder).embeddedOrder);
    }

}
