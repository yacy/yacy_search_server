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

package net.yacy.kelondro.util;

import java.util.ConcurrentModificationException;

import net.yacy.cora.order.CloneableIterator;


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

    @Override
    public StackIterator<E> clone(final Object modifier) {
        return new StackIterator<E>(this.a.clone(modifier), this.b.clone(modifier));
    }

    private void nexta() {
        try {
            if ((this.a != null) && (this.a.hasNext())) this.na = this.a.next(); else this.na = null;
        } catch (final ConcurrentModificationException e) {
            this.na = null;
        }
    }
    private void nextb() {
        try {
            if ((this.b != null) && (this.b.hasNext())) this.nb = this.b.next(); else this.nb = null;
        } catch (final ConcurrentModificationException e) {
            this.nb = null;
        }
    }

    @Override
    public boolean hasNext() {
        return (this.na != null) || (this.nb != null);
    }

    @Override
    public E next() {
        E s;
        if (this.na == null) {
            s = this.nb;
            nextb();
            return s;
        }
        if (this.nb == null) {
            s = this.na;
            nexta();
            return s;
        }
        // just stack the Objects
        s = this.na;
        nexta();
        return s;
    }

    @Override
    public void remove() {
        throw new java.lang.UnsupportedOperationException("merge does not support remove");
    }

    @SuppressWarnings("unchecked")
    public static <A> CloneableIterator<A> stack(final CloneableIterator<A>[] iterators) {
        // this extends the ability to combine two iterators
        // to the ability of combining a set of iterators
        if (iterators == null || iterators.length == 0) return new CloneableIterator<A>() {
            @Override
            public boolean hasNext() {
                return false;
            }
            @Override
            public A next() {
                return null;
            }
            @Override
            public void remove() {
            }
            @Override
            public CloneableIterator<A> clone(Object modifier) {
                return null;
            }
            @Override
            public void close() {
            }
        };
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

    @Override
    public void close() {
        this.a.close();
        this.b.close();
    }
}
