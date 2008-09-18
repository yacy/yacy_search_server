// kelondroMergeIterator.java
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

package de.anomic.kelondro;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;

public class kelondroMergeIterator<E> implements kelondroCloneableIterator<E> {
    
    private Comparator<E> comp;
    private kelondroCloneableIterator<E> a, b;
    private E na, nb;
    private final Method merger;
    private final boolean up;
    
    public kelondroMergeIterator(final kelondroCloneableIterator<E> a, final kelondroCloneableIterator<E> b, final Comparator<E> c, final Method m, final boolean up) {
        // this works currently only for String-type key iterations
        this.a = a;
        this.b = b;
        this.up = up;
        this.comp = c;
        this.merger = m;
        nexta();
        nextb();
    }
    
	public kelondroMergeIterator<E> clone(final Object modifier) {
        return new kelondroMergeIterator<E>(a.clone(modifier), b.clone(modifier), comp, merger, up);
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
    
	@SuppressWarnings("unchecked")
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
        // compare the Objects
        final int c = comp.compare(na, nb);
        if (c == 0) {
            try {
                //System.out.print("MERGE OF " + na.toString() + " AND " + nb.toString() + ": ");
                s = (E) this.merger.invoke(null, new Object[]{na, nb});
                //System.out.println("RESULT IS " + s.toString());
            } catch (final IllegalArgumentException e) {
                e.printStackTrace();
                s = null;
            } catch (final IllegalAccessException e) {
                e.printStackTrace();
                s = null;
            } catch (final InvocationTargetException e) {
                e.printStackTrace();
                s = null;
            }
            nexta();
            nextb();
            return s;
        } else if (((up) && (c < 0)) || ((!(up)) && (c > 0))) {
            s = na;
            nexta();
            return s;
        } else {
            s = nb;
            nextb();
            return s;
        }
    }
    
    public void remove() {
        throw new java.lang.UnsupportedOperationException("merge does not support remove");
    }
    
    public static <A> kelondroCloneableIterator<A> cascade(final Collection<kelondroCloneableIterator<A>> iterators, final kelondroOrder<A> c, final Method merger, final boolean up) {
        // this extends the ability to combine two iterators
        // to the ability of combining a set of iterators
        if (iterators == null) return null;
        if (iterators.size() == 0) return null;
        return cascade(iterators.iterator(), c, merger, up);
    }
    
    private static <A> kelondroCloneableIterator<A> cascade(final Iterator<kelondroCloneableIterator<A>> iiterators, final kelondroOrder<A> c, final Method merger, final boolean up) {
        if (iiterators == null) return null;
        if (!(iiterators.hasNext())) return null;
        final kelondroCloneableIterator<A> one = iiterators.next();
        if (!(iiterators.hasNext())) return one;
        return new kelondroMergeIterator<A>(one, cascade(iiterators, c, merger, up), c, merger, up);
    }
    
    public static final Method simpleMerge;
    static {
        Method meth = null;
        try {
            final Class<?> c = Class.forName("de.anomic.kelondro.kelondroMergeIterator");
            meth = c.getMethod("mergeEqualByReplace", new Class[]{Object.class, Object.class});
        } catch (final SecurityException e) {
            System.out.println("Error while initializing simpleMerge (1): " + e.getMessage());
            meth = null;
        } catch (final ClassNotFoundException e) {
            System.out.println("Error while initializing simpleMerge (2): " + e.getMessage());
            meth = null;
        } catch (final NoSuchMethodException e) {
            System.out.println("Error while initializing simpleMerge (3): " + e.getMessage());
            meth = null;
        }
        simpleMerge = meth; 
    }
    
    // do not remove the following method, it is not reference anywhere directly but indirectly using reflection
    // please see initialization of simpleMerge above
    public static Object mergeEqualByReplace(final Object a, final Object b) {
        return a;
    }
}
