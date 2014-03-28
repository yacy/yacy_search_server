/**
 *  AbstractOrder
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.08.2011 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.order;


public abstract class AbstractOrder<A> implements Order<A> {

    protected A zero = null;
    protected boolean asc = true;

    @Override
    abstract public Order<A> clone();

    @Override
    public A zero() {
    	return this.zero;
    }

    @Override
    public void direction(final boolean ascending) {
        this.asc = ascending;
    }

    @Override
    public long partition(final A key, final int forks) {
        final long d = (Long.MAX_VALUE / forks) + ((Long.MAX_VALUE % forks) + 1) / forks;
        return cardinal(key) / d;
    }

    @Override
    public void rotate(final A newzero) {
        this.zero = newzero;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Order<?>)) return false;
        final Order<A> other = (Order<A>) obj;
        final String thisSig = signature();
        final String otherSig = other.signature();
        if ((thisSig == null) || (otherSig == null)) return false;
        return thisSig.equals(otherSig);
    }

    @Override
    public int hashCode() {
        return signature().hashCode();
    }

    public A smallest(final A a, final A b) {
        return (compare(a, b) > 0) ? b : a;
    }

    public A largest(final A a, final A b) {
        return (compare(a, b) > 0) ? a : b;
    }
}
