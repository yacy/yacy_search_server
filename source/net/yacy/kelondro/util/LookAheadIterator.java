// LookAheadIterator.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.02.2010 on http://yacy.net
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

package net.yacy.kelondro.util;

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

    public Iterator<A> iterator() {
        return this;
    }
    
    /**
     * the internal next-method
     * @return a value of type A if available or null if no more value are available
     */
    protected abstract A next0() ;
    
    private final void checkInit() {
        if (fresh) {
            next = next0();
            fresh = false;
        }
    }
    
    public final boolean hasNext() {
        checkInit();
        return next != null;
    }

    public final A next() {
        checkInit();
        A n = next;
        next = next0();
        return n;
    }
    
    /**
     * a remove is not possible with this implementation
     */
    public final void remove() {
        throw new UnsupportedOperationException();
    }
    
}
