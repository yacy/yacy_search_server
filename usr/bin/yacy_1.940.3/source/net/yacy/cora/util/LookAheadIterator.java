/**
 *  LookAheadIterator
 *  Copyright 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 04.02.2010 on http://yacy.net
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

package net.yacy.cora.util;

import java.util.Iterator;

/**
 * convenience class for iterator implementations that naturally terminate
 * when the next() method would return null. This is usually implemented using
 * a next0() method and a wrapper for next() and hasNext() that evaluates the
 * latest return value of next0()
 * To use this class just implement the next0() method
 */
public abstract class LookAheadIterator<A> implements Iterator<A>, Iterable<A> {

    private boolean fresh = true;
    private A next = null;

    public LookAheadIterator() {
    }

    @Override
    public final Iterator<A> iterator() {
        return this;
    }

    /**
     * the internal next-method
     * @return a value of type A if available or null if no more value are available
     */
    protected abstract A next0() ;

    private final void checkInit() {
        if (this.fresh) {
            this.next = next0();
            this.fresh = false;
        }
    }

    @Override
    public final boolean hasNext() {
        checkInit();
        return this.next != null;
    }

    @Override
    public final A next() {
        checkInit();
        final A n = this.next;
        this.next = next0();
        return n;
    }

    /**
     * a remove is not possible with this implementation
     */
    @Override
    public final void remove() {
        throw new UnsupportedOperationException();
    }

}
