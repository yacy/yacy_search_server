// kelondroAbstractOrder.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 29.12.2005
//
// $LastChangedDate: 2005-09-22 22:01:26 +0200 (Thu, 22 Sep 2005) $
// $LastChangedRevision: 774 $
// $LastChangedBy: orbiter $
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


public abstract class kelondroAbstractOrder<A> implements kelondroOrder<A> {

    protected A zero = null;
    protected boolean asc = true;
    
    abstract public kelondroOrder<A> clone();

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
    
    public boolean equals(final kelondroOrder<A> otherOrder) {
        if (otherOrder == null) return false;
        final String thisSig = this.signature();
        final String otherSig = otherOrder.signature();
        if ((thisSig == null) || (otherSig == null)) return false;
        return thisSig.equals(otherSig);
    }
}
