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

package net.yacy.kelondro.util;

import net.yacy.cora.order.CloneableIterator;


public class RotateIterator<E> implements CloneableIterator<E> {

    private CloneableIterator<E> a, clone;
    Object modifier;
    private boolean nempty;
    int terminationCount;

    public RotateIterator(final CloneableIterator<E> a, final Object modifier, final int terminationCount) {
        // this works currently only for String-type key iterations
        this.a = a;
        this.modifier = modifier;
        this.terminationCount = terminationCount;
        this.clone = a.clone(modifier);
        this.nempty = this.clone.hasNext();
    }

	@Override
    public RotateIterator<E> clone(final Object modifier) {
        return new RotateIterator<E>(this.a, modifier, this.terminationCount - 1);
    }

    @Override
    public boolean hasNext() {
        return (this.terminationCount > 0) && (this.nempty);
    }

    @Override
    public E next() {
    	// attention: this iterator has no termination - on purpose.
    	// it must be taken care that a calling method has a termination predicate different
    	// from the hasNext() method
        if (!(this.a.hasNext())) {
            this.a = this.clone.clone(this.modifier);
            assert this.a.hasNext();
        }
        this.terminationCount--;
        return this.a.next();
    }

    @Override
    public void remove() {
        this.a.remove();
    }

    @Override
    public void close() {
        this.a.close();
        this.clone.close();
    }

}
