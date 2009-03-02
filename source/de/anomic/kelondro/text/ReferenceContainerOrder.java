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

package de.anomic.kelondro.text;

import de.anomic.kelondro.order.AbstractOrder;
import de.anomic.kelondro.order.Order;

public class ReferenceContainerOrder extends AbstractOrder<ReferenceContainer> implements Order<ReferenceContainer>, Cloneable {

    private final Order<byte[]> embeddedOrder;

    public ReferenceContainerOrder(final Order<byte[]> embedOrder) {
        this.embeddedOrder = embedOrder;
    }

    public boolean wellformed(final ReferenceContainer a) {
        return embeddedOrder.wellformed(a.getWordHash().getBytes());
    }
    
    public void direction(final boolean ascending) {
        this.embeddedOrder.direction(ascending);
    }

    public long partition(final byte[] key, final int forks) {
        return this.embeddedOrder.partition(key, forks);
    }

    public int compare(final ReferenceContainer a, final ReferenceContainer b) {
        return this.embeddedOrder.compare(a.getWordHash().getBytes(), b.getWordHash().getBytes());
    }

    public void rotate(final ReferenceContainer zero) {
        this.embeddedOrder.rotate(zero.getWordHash().getBytes());
        this.zero = new ReferenceContainer(new String(this.embeddedOrder.zero()), zero);
    }

    public Order<ReferenceContainer> clone() {
        return new ReferenceContainerOrder(this.embeddedOrder.clone());
    }

    public String signature() {
        return this.embeddedOrder.signature();
    }

    public long cardinal(final byte[] key) {
        return this.embeddedOrder.cardinal(key);
    }
    
    public boolean equals(final Order<ReferenceContainer> otherOrder) {
        if (!(otherOrder instanceof ReferenceContainerOrder)) return false;
        return this.embeddedOrder.equals(((ReferenceContainerOrder) otherOrder).embeddedOrder);
    }

	public long cardinal(final ReferenceContainer key) {
		return this.embeddedOrder.cardinal(key.getWordHash().getBytes());
	}

}
