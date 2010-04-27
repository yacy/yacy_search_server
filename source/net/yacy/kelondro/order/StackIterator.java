// StackIterator.java
// --------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2009
// last major change: 23.02.2009
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

import java.util.ConcurrentModificationException;


public class StackIterator<E> implements CloneableIterator<E> {
    
    private final CloneableIterator<E> a, b;
    private E na, nb;
    
    public StackIterator(
            final CloneableIterator<E> a,
            final CloneableIterator<E> b) {
        // this works currently only for String-type key iterations
        this.a = a;
        this.b = b;
        nexta();
        nextb();
    }

    public StackIterator<E> clone(final Object modifier) {
        return new StackIterator<E>(a.clone(modifier), b.clone(modifier));
    }
    
    private void nexta() {
        try {
            if ((a != null) && (a.hasNext())) na = a.next(); else na = null;
        } catch (final ConcurrentModificationException e) {
            na = null;
        }
    }
    private void nextb() {
        try {
            if ((b != null) && (b.hasNext())) nb = b.next(); else nb = null;
        } catch (final ConcurrentModificationException e) {
            nb = null;
        }
    }
    
    public boolean hasNext() {
        return (na != null) || (nb != null);
    }
    
    public E next() {
        E s;
        if (na == null) {
            s = nb;
            nextb();
            return s;
        }
        if (nb == null) {
            s = na;
            nexta();
            return s;
        }
        // just stack the Objects
        s = na;
        nexta();
        return s;
    }
    
    public void remove() {
        throw new java.lang.UnsupportedOperationException("merge does not support remove");
    }
    
    @SuppressWarnings("unchecked")
    public static <A> CloneableIterator<A> stack(final CloneableIterator<A>[] iterators) {
        // this extends the ability to combine two iterators
        // to the ability of combining a set of iterators
        if (iterators == null) return null;
        if (iterators.length == 0) return null;
        if (iterators.length == 1) {
            return iterators[0];
        }
        if (iterators.length == 2) {
            if (iterators[0] == null) return iterators[1];
            if (iterators[1] == null) return iterators[0];
            return new StackIterator<A>(iterators[0], iterators[1]);
        }
        CloneableIterator<A> a = iterators[0];
        final CloneableIterator<A>[] iterators0 = new CloneableIterator[iterators.length - 1];
        System.arraycopy(iterators, 1, iterators0, 0, iterators.length - 1);
        if (a == null) return stack(iterators0);
        return new StackIterator<A>(a, stack(iterators0));
    }
}
