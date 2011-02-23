// AbstractReference.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.04.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-04-07 11:34:41 +0200 (Di, 07 Apr 2009) $
// $LastChangedRevision: 5783 $
// $LastChangedBy: orbiter $
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
        if (i < 0) return; // signal for 'do nothing'
        a.clear();
        a.add(i);
    }
    
    protected static int max(Collection<Integer> a, Collection<Integer> b) {
        assert a != null;
        if (a.size() == 0) return max(b);
        if (b.size() == 0) return max(a);
        return Math.max(max(a), max(b));
    }
    
    protected static int min(Collection<Integer> a, Collection<Integer> b) {
        assert a != null;
        if (a.size() == 0) return min(b);
        if (b.size() == 0) return min(a);
        int ma = min(a);
        int mb = min(b);
        if (ma == -1) return mb;
        if (mb == -1) return ma;
        return Math.min(ma, mb);
    }

    private static int max(Collection<Integer> a) {
        assert a != null;
        if (a.size() == 0) return -1;
        Iterator<Integer> i = a.iterator();
        if (a.size() == 1) return i.next();
        if (a.size() == 2) return Math.max(i.next(), i.next());
        int r = i.next();
        int s;
        while (i.hasNext()) {
            s = i.next();
            if (s > r) r = s;
        }
        return r;
    }
    
    private static int min(Collection<Integer> a) {
        assert a != null;
        if (a.size() == 0) return -1;
        Iterator<Integer> i = a.iterator();
        if (a.size() == 1) return i.next();
        if (a.size() == 2) return Math.min(i.next(), i.next());
        int r = i.next();
        int s;
        while (i.hasNext()) {
            s = i.next();
            if (s <r) r = s;
        }
        return r;
    }
    
    public int maxposition() {
        assert positions().size() > 0;
        return max(positions());
    }
    
    public int minposition() {
        assert positions().size() > 0;
        return min(positions());
    }
    
    public int distance() {
        if (positions().size() < 2) return 0;
        int d = 0;
        Iterator<Integer> i = positions().iterator();
        int s0 = i.next(), s1;
        while (i.hasNext()) {
            s1 = i.next();
            d += Math.abs(s0 - s1);
            s0 = s1;
        }
        return d / (positions().size() - 1);
    }
}
