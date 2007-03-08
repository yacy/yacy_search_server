// kelondroMergeIterator.java
// --------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.kelondro;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;

public class kelondroMergeIterator implements kelondroCloneableIterator {
    
    Comparator comp;
    kelondroCloneableIterator a, b;
    Object na, nb;
    Method merger;
    boolean up;
    
    public kelondroMergeIterator(kelondroCloneableIterator a, kelondroCloneableIterator b, Comparator c, Method m, boolean up) {
        // this works currently only for String-type key iterations
        this.a = a;
        this.b = b;
        this.up = up;
        this.comp = c;
        this.merger = m;
        nexta();
        nextb();
    }
    
    public Object clone(Object modifier) {
        return new kelondroMergeIterator((kelondroCloneableIterator) a.clone(modifier), (kelondroCloneableIterator) b.clone(modifier), comp, merger, up);
    }
    
    public void finalize() {
        // call finalizer of embedded objects
        a = null;
        b = null;
        na = null;
        nb = null;
        comp = null;
    }
    
    private void nexta() {
        try {
            if ((a != null) && (a.hasNext())) na = a.next(); else na = null;
        } catch (ConcurrentModificationException e) {
            na = null;
        }
    }
    private void nextb() {
        try {
            if ((b != null) && (b.hasNext())) nb = b.next(); else nb = null;
        } catch (ConcurrentModificationException e) {
            nb = null;
        }
    }
    
    public boolean hasNext() {
        return (na != null) || (nb != null);
    }
    
    public Object next() {
        Object s;
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
        int c = comp.compare(na, nb);
        if (c == 0) {
            try {
                //System.out.print("MERGE OF " + na.toString() + " AND " + nb.toString() + ": ");
                s = this.merger.invoke(null, new Object[]{na, nb});
                //System.out.println("RESULT IS " + s.toString());
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                s = null;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                s = null;
            } catch (InvocationTargetException e) {
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
    
    public static Iterator cascade(Set /*of*/ iterators, Comparator c, Method merger, boolean up) {
        // this extends the ability to combine two iterators
        // to the abiliy of combining a set of iterators
        if (iterators == null) return null;
        if (iterators.size() == 0) return null;
        return cascade(iterators.iterator(), c, merger, up);
    }
    
    private static kelondroCloneableIterator cascade(Iterator /*of*/ iiterators, Comparator c, Method merger, boolean up) {
        if (iiterators == null) return null;
        if (!(iiterators.hasNext())) return null;
        kelondroCloneableIterator one = (kelondroCloneableIterator) iiterators.next();
        if (!(iiterators.hasNext())) return one;
        return new kelondroMergeIterator(one, cascade(iiterators, c, merger, up), c, merger, up);
    }
    
    public static Method simpleMerge = null;
    static {
        try {
            Class c = Class.forName("de.anomic.kelondro.kelondroMergeIterator");
            simpleMerge = c.getMethod("mergeEqualByReplace", new Class[]{Object.class, Object.class});
        } catch (SecurityException e) {
            System.out.println("Error while initializing simpleMerge: " + e.getMessage());
            simpleMerge = null;
        } catch (ClassNotFoundException e) {
            System.out.println("Error while initializing simpleMerge: " + e.getMessage());
            simpleMerge = null;
        } catch (NoSuchMethodException e) {
            System.out.println("Error while initializing simpleMerge: " + e.getMessage());
            simpleMerge = null;
        }
    }
    
    public static Object mergeEqualByReplace(Object a, Object b) {
        return a;
    }
    
}
