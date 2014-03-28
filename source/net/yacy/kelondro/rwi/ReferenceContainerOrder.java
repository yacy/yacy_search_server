// indexContainerOrder.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.rwi;

import net.yacy.cora.order.AbstractOrder;
import net.yacy.cora.order.Order;

public class ReferenceContainerOrder<ReferenceType extends Reference> extends AbstractOrder<ReferenceContainer<ReferenceType>> implements Order<ReferenceContainer<ReferenceType>>, Cloneable {

    private final ReferenceFactory<ReferenceType> factory;
    private final Order<byte[]> embeddedOrder;

    public ReferenceContainerOrder(ReferenceFactory<ReferenceType> factory, final Order<byte[]> embedOrder) {
        this.embeddedOrder = embedOrder;
        this.factory = factory;
    }

    @Override
    public boolean wellformed(final ReferenceContainer<ReferenceType> a) {
        return embeddedOrder.wellformed(a.getTermHash());
    }
    
    @Override
    public void direction(final boolean ascending) {
        this.embeddedOrder.direction(ascending);
    }

    public long partition(final byte[] key, final int forks) {
        return this.embeddedOrder.partition(key, forks);
    }

    @Override
    public int compare(final ReferenceContainer<ReferenceType> a, final ReferenceContainer<ReferenceType> b) {
        return this.embeddedOrder.compare(a.getTermHash(), b.getTermHash());
    }
    
    @Override
    public boolean equal(ReferenceContainer<ReferenceType> a, ReferenceContainer<ReferenceType> b) {
        return this.embeddedOrder.equal(a.getTermHash(), b.getTermHash());
    }
    
    @Override
    public void rotate(final ReferenceContainer<ReferenceType> zero) {
        this.embeddedOrder.rotate(zero.getTermHash());
        this.zero = new ReferenceContainer<ReferenceType>(this.factory, this.embeddedOrder.zero(), zero);
    }

    @Override
    public Order<ReferenceContainer<ReferenceType>> clone() {
        return new ReferenceContainerOrder<ReferenceType>(this.factory, this.embeddedOrder.clone());
    }

    @Override
    public String signature() {
        return this.embeddedOrder.signature();
    }

    public long cardinal(final byte[] key) {
        return this.embeddedOrder.cardinal(key);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof ReferenceContainerOrder<?>)) return false;
        ReferenceContainerOrder<ReferenceType> other = (ReferenceContainerOrder<ReferenceType>) obj;
        return this.embeddedOrder.equals(other.embeddedOrder);
    }

	@Override
    public long cardinal(final ReferenceContainer<ReferenceType> key) {
		return this.embeddedOrder.cardinal(key.getTermHash());
	}

}
