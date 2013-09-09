// MergeIterator.java
// --------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 08.05.2005
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;

import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.Order;
import net.yacy.cora.util.ConcurrentLog;


public class MergeIterator<E> implements CloneableIterator<E> {

    private final Comparator<E> comp;
    private final CloneableIterator<E> a;
    private final CloneableIterator<E> b;
    private E na, nb;
    private final Method merger;
    private final boolean up;

    public MergeIterator(
            final CloneableIterator<E> a,
            final CloneableIterator<E> b,
            final Comparator<E> c,
            final Method m,
            final boolean up) {
        // this works currently only for String-type key iterations
        assert a != null;
        assert b != null;
        this.a = a;
        this.b = b;
        this.up = up;
        this.comp = c;
        this.merger = m;
        nexta();
        nextb();
    }

    @Override
    public void close() {
        this.a.close();
        this.b.close();
    }

    @Override
    public MergeIterator<E> clone(final Object modifier) {
        assert this.a != null;
        assert this.b != null;
        assert this.merger != null;
        return new MergeIterator<E>(this.a.clone(modifier), this.b.clone(modifier), this.comp, this.merger, this.up);
    }

    private void nexta() {
        try {
            if (this.a != null && this.a.hasNext()) this.na = this.a.next(); else this.na = null;
        } catch (final ConcurrentModificationException e) {
            this.na = null;
        }
    }
    private void nextb() {
        try {
            if (this.b != null && this.b.hasNext()) this.nb = this.b.next(); else this.nb = null;
        } catch (final ConcurrentModificationException e) {
            this.nb = null;
        }
    }

    @Override
    public boolean hasNext() {
        return (this.na != null) || (this.nb != null);
    }

	@Override
    @SuppressWarnings("unchecked")
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
        // compare the Objects
        final int c = this.comp.compare(this.na, this.nb);
        if (c == 0) {
            try {
                //System.out.print("MERGE OF " + na.toString() + " AND " + nb.toString() + ": ");
                s = (E) this.merger.invoke(null, new Object[]{this.na, this.nb});
                //System.out.println("RESULT IS " + s.toString());
            } catch (final IllegalArgumentException e) {
                ConcurrentLog.logException(e);
                s = null;
            } catch (final IllegalAccessException e) {
                ConcurrentLog.logException(e);
                s = null;
            } catch (final InvocationTargetException e) {
                ConcurrentLog.logException(e);
                ConcurrentLog.logException(e.getCause());
                s = null;
            }
            nexta();
            nextb();
            return s;
        } else if ((this.up && c < 0) || (!this.up && c > 0)) {
            s = this.na;
            nexta();
            return s;
        } else {
            s = this.nb;
            nextb();
            return s;
        }
    }

    @Override
    public void remove() {
        throw new java.lang.UnsupportedOperationException("merge does not support remove");
    }

    public static <A> CloneableIterator<A> cascade(final Collection<CloneableIterator<A>> iterators, final Order<A> c, final Method merger, final boolean up) {
        // this extends the ability to combine two iterators
        // to the ability of combining a set of iterators
        if (iterators == null || iterators.isEmpty()) return new CloneableIterator<A>(){
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
                return this;
            }
            @Override
            public void close() {
            }
        };
        return cascade(iterators.iterator(), c, merger, up);
    }

    private static <A> CloneableIterator<A> cascade(final Iterator<CloneableIterator<A>> iiterators, final Order<A> c, final Method merger, final boolean up) {
        if (iiterators == null || !(iiterators.hasNext())) return new CloneableIterator<A>(){
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
                return this;
            }
            @Override
            public void close() {
            }
        };
        final CloneableIterator<A> one = iiterators.next();
        if (!(iiterators.hasNext())) return one;
        assert merger != null;
        return new MergeIterator<A>(one, cascade(iiterators, c, merger, up), c, merger, up);
    }

    public static final Method simpleMerge;
    static {
        Method meth = null;
        try {
            final Class<?> c = net.yacy.kelondro.util.MergeIterator.class;
            meth = c.getMethod("mergeEqualByReplace", new Class[]{Object.class, Object.class});
        } catch (final SecurityException e) {
            System.out.println("Error while initializing simpleMerge (1): " + e.getMessage());
            meth = null;
        } catch (final NoSuchMethodException e) {
            System.out.println("Error while initializing simpleMerge (3): " + e.getMessage());
            meth = null;
        }
        assert meth != null;
        simpleMerge = meth;
    }

    // do not remove the following method, it is not reference anywhere directly but indirectly using reflection
    // please see initialization of simpleMerge above
    public static Object mergeEqualByReplace(final Object a, @SuppressWarnings("unused") final Object b) {
        return a;
    }
}
