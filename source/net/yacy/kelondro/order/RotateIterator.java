// RotateIterator.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 08.03.2007 on http://www.anomic.de
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

package net.yacy.kelondro.order;


public class RotateIterator<E> implements CloneableIterator<E> {
    
    CloneableIterator<E> a, clone;
    Object modifier;
    boolean nempty;
    int terminationCount;
    
    public RotateIterator(final CloneableIterator<E> a, final Object modifier, final int terminationCount) {
        // this works currently only for String-type key iterations
        this.a = a;
        this.modifier = modifier;
        this.terminationCount = terminationCount;
        this.clone = a.clone(modifier);
        this.nempty = this.clone.hasNext();
    }
    
	public RotateIterator<E> clone(final Object modifier) {
        return new RotateIterator<E>(a, modifier, terminationCount - 1);
    }
    
    public boolean hasNext() {
        return (terminationCount > 0) && (this.nempty);
    }
    
    public E next() {
    	// attention: this iterator has no termination - on purpose.
    	// it must be taken care that a calling method has a termination predicate different
    	// from the hasNext() method
        if (!(a.hasNext())) {
            a = clone.clone(modifier);
            assert a.hasNext();
        }
        terminationCount--;
        return a.next();
    }
    
    public void remove() {
        a.remove();
    }
    
}
