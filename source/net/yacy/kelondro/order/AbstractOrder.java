// AbstractOrder.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 29.12.2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.order;

public abstract class AbstractOrder<A> implements Order<A> {

    protected A zero = null;
    protected boolean asc = true;
    
    @Override
    abstract public Order<A> clone();

    public A zero() {
    	return zero;
    }
    
    public void direction(final boolean ascending) {
        asc = ascending;
    }
    
    public long partition(final A key, final int forks) {
        final long d = (Long.MAX_VALUE / forks) + ((Long.MAX_VALUE % forks) + 1) / forks;
        return cardinal(key) / d;
    }
    
    public void rotate(final A newzero) {
        this.zero = newzero;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Order<?>)) return false;
        Order<A> other = (Order<A>) obj;
        final String thisSig = this.signature();
        final String otherSig = other.signature();
        if ((thisSig == null) || (otherSig == null)) return false;
        return thisSig.equals(otherSig);
    }
    
    @Override
    public int hashCode() {
        return this.signature().hashCode();
    }
    
    public A smallest(A a, A b) {
        return (compare(a, b) > 0) ? b : a;
    }

    public A largest(A a, A b) {
        return (compare(a, b) > 0) ? a : b;
    }
}
