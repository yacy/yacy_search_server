// AbstractReference.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.04.2009 on http://yacy.net
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

package net.yacy.kelondro.rwi;

import java.util.Collection;
import java.util.Iterator;


public abstract class AbstractReference implements Reference {

    protected static void a(Collection<Integer> a, int i) {
        assert a != null;
        if (i == Integer.MAX_VALUE || i == Integer.MIN_VALUE) return; // signal for 'do nothing'
        a.clear();
        a.add(i);
    }
    
    protected static int max(Collection<Integer> a, Collection<Integer> b) {
        if (a == null || a.isEmpty()) return max(b);
        if (b == null || b.isEmpty()) return max(a);
        int ma = max(a);
        int mb = max(b);
        if (ma == Integer.MIN_VALUE) return mb;
        if (mb == Integer.MIN_VALUE) return ma;
        return Math.max(ma, mb);
    }
    
    protected static int min(Collection<Integer> a, Collection<Integer> b) {
        assert a != null;
        if (a == null || a.isEmpty()) return min(b);
        if (b == null || b.isEmpty()) return min(a);
        int ma = min(a);
        int mb = min(b);
        if (ma == Integer.MAX_VALUE) return mb;
        if (mb == Integer.MAX_VALUE) return ma;
        return Math.min(ma, mb);
    }

    private static int max(Collection<Integer> a) {
        if (a == null || a.isEmpty()) return Integer.MIN_VALUE;
        Iterator<Integer> i = a.iterator();
        /*
        expirienced concurrency issue with this short cut 2016-09-06
        on i.next w/o test of hasNext before
        java.util.NoSuchElementException at java.util.concurrent.LinkedBlockingQueue$Itr.next(LinkedBlockingQueue.java:828)

        if (a.size() == 1) return i.next();
        if (a.size() == 2) return Math.max(i.next(), i.next());
        int r = i.next();
        */
        int r = Integer.MIN_VALUE;

        int s;
        while (i.hasNext()) {
            s = i.next();
            if (s > r) r = s;
        }
        return r;
    }
    
    private static int min(Collection<Integer> a) {
        if (a == null || a.isEmpty()) return Integer.MAX_VALUE;
        Iterator<Integer> i = a.iterator();
        /* concurrency issue (see max())
        if (a.size() == 1) return i.next();
        if (a.size() == 2) return Math.min(i.next(), i.next());
        int r = i.next();
        */
        int r = Integer.MAX_VALUE;
        int s;
        while (i.hasNext()) {
            s = i.next();
            if (s < r) r = s;
        }
        return r;
    }

   /**
     * max position of search query words for multi word queries
     * @return
     */
    @Override
    public int maxposition() {
        return max(positions());
    }

    /**
     * min word position of search query words for multi word queries
     * @return
     */
    @Override
    public int minposition() {
        return min(positions());
    }

    /**
     * The average distance (in words) between search query terms for multi word searches.
     * @return word distance
     */
    @Override
    public int distance() {
        if (positions().size() < 2) return 0;
        int d = 0;
        Iterator<Integer> i = positions().iterator();
        // int s0 = i.next(), s1; // concurrency issue see max()
        int s0 = -1, s1;
        while (i.hasNext()) {
            s1 = i.next();
            if (s0 > 0) d += Math.abs(s0 - s1);
            s0 = s1;
        }
        return d / (positions().size() - 1);
    }
    
    @Override
    public boolean isOlder(final Reference other) {
        if (other == null) return false;
        if (this.lastModified() < other.lastModified()) return true;
        return false;
    }
}
