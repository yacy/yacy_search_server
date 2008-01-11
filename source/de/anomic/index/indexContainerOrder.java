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

import de.anomic.kelondro.kelondroAbstractOrder;
import de.anomic.kelondro.kelondroOrder;

public class indexContainerOrder extends kelondroAbstractOrder<indexContainer> implements kelondroOrder<indexContainer> {

    private kelondroOrder<byte[]> embeddedOrder;

    public indexContainerOrder(kelondroOrder<byte[]> embedOrder) {
        this.embeddedOrder = embedOrder;
    }

    public boolean wellformed(indexContainer a) {
        return embeddedOrder.wellformed(a.getWordHash().getBytes());
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

    public void rotate(indexContainer zero) {
        this.embeddedOrder.rotate(zero.getWordHash().getBytes());
        this.zero = new indexContainer(new String(this.embeddedOrder.zero()), zero);
    }

    public kelondroOrder<indexContainer> clone() {
        return new indexContainerOrder(this.embeddedOrder.clone());
    }

    public String signature() {
        return this.embeddedOrder.signature();
    }

    public long cardinal(byte[] key) {
        return this.embeddedOrder.cardinal(key);
    }
    
    public boolean equals(kelondroOrder<indexContainer> otherOrder) {
        if (!(otherOrder instanceof indexContainerOrder)) return false;
        return this.embeddedOrder.equals(((indexContainerOrder) otherOrder).embeddedOrder);
    }

	public long cardinal(indexContainer key) {
		return this.embeddedOrder.cardinal(key.getWordHash().getBytes());
	}

}
