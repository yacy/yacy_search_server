/**
 *  Array
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 16.07.2011 at https://yacy.net
 *
 *  $LastChangedDate: 2011-05-30 10:53:58 +0200 (Mo, 30 Mai 2011) $
 *  $LastChangedRevision: 7759 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.sorting;

import java.util.ArrayList;
import java.util.Random;



/**
 * an abstraction of the quicksort from the java.util.Array class
 * @author admin
 *
 */
public class Array {

    public static <A> void sort(final Sortable<A> x) {
        sort(x, 0, x.size(), x.buffer(), 0);
    }


    private static <A> void sort(final Sortable<A> x, final int o, final int l, final A f, final int depth) {

        // in case of small arrays we do not need a quicksort
        if (l < 7) {
            for (int i = o; i < l + o; i++) {
                for (int j = i; j > o && x.compare(x.get(j, false), x.get(j - 1, false)) < 0; j--) x.swap(j, j - 1, f);
            }
            return;
        }

        // find the pivot element
        int m = o + (l >> 1);
        if (l > 7) {
            int k = o;
            int n = o + l - 1;
            if (l > 40) {
                final int s = l / 8;
                k = med3(x, k        , k + s, k + 2 * s);
                m = med3(x, m - s    , m    , m + s    );
                n = med3(x, n - 2 * s, n - s, n        );
            }
            m = med3(x, k, m, n);
        }
        final A p = x.get(m, true);

        // do a partitioning of the sequence
        int a = o, b = a, c = o + l - 1, d = c;
        A _v;
        while (true) {
            while (c >= b && x.compare(p, (_v = x.get(b, false))) >= 0) {
                if (x.compare(_v, p) == 0) x.swap(a++, b, f);
                b++;
            }
            while (c >= b && x.compare((_v = x.get(c, false)), p) >= 0) {
                if (x.compare(_v, p) == 0) x.swap(c, d--, f);
                c--;
            }
            if (b > c) break;
            x.swap(b++, c--, f);
        }

        // swap all
        int s;
        final int n = o + l;
        s = Math.min(a - o, b - a );
        swap(x, o, b - s, s, f);
        s = Math.min(d - c, n - d - 1);
        swap(x, b, n - s, s, f);

        // recursively sort partitions
        final int s0 = b - a;
        if (s0 > 1) {
            sort(x, o, s0, f, depth + 1);
        }
        final int s1 = d - c;
        if (s1 > 1) {
            sort(x, n - s1, s1, x.buffer(), depth + 1);
        }
    }

    private static <A> void swap(final Sortable<A> x, int a, int b, final int n, final A buffer) {
        if (n == 1) {
            x.swap(a, b, buffer);
        } else {
            for (int i = 0; i < n; i++, a++, b++) x.swap(a, b, buffer);
        }
    }

    private static <A> int med3(final Sortable<A> x, final int a, final int b, final int c) {
        final A _a = x.get(a, false);
        final A _b = x.get(b, false);
        final A _c = x.get(c, false);
        return (x.compare(_a, _b) < 0 ?
                (x.compare(_b, _c) < 0 ? b : x.compare(_a, _c) < 0 ? c : a) :
                (x.compare(_c, _b) < 0 ? b : x.compare(_c, _a) < 0 ? c : a));
    }

    private static class P implements Sortable<Integer> {

        private ArrayList<Integer> list;

        public P() {
            this.list = new ArrayList<Integer>();
        }

        @Override
        public int compare(final Integer o1, final Integer o2) {
            return o1.compareTo(o2);
        }

        @Override
        public Integer buffer() {
            return Integer.valueOf(0);
        }

        @Override
        public void swap(final int i, final int j, Integer buffer) {
            buffer = this.list.get(i);
            this.list.set(i, this.list.get(j));
            this.list.set(j, buffer);
        }

        @Override
        public void delete(final int i) {
            this.list.remove(i);
        }

        @Override
        public Integer get(final int i, final boolean clone) {
            return this.list.get(i);
        }

		@Override
		public int size() {
			return list.size();
		}

		public void add(int nextInt) {
			this.list.add(nextInt);
		}

    }

    public static <A> void uniq(final Sortable<A> x) {
        if (x.size() < 2) return;
        int i = x.size() - 1;
        A a = x.get(i--, true), b;
        while (i >= 0) {
            b = x.get(i, true);
            if (x.compare(a, b) == 0) {
                x.delete(i);
            } else {
                a = b;
            }
            i--;
        }
    }

    public static void main(final String[] args) {
        final int count = 1000000;
        final P test = new P();
        Random r = new Random(0);
        for (int i = 0; i < count; i++) {
            test.add(r.nextInt());
        }
        r = new Random(0);
        for (int i = 0; i < count; i++) {
            test.add(r.nextInt());
        }
        final long t0 = System.currentTimeMillis();
        sort(test);
        final long t1 = System.currentTimeMillis();
        System.out.println("sort = " + (t1 - t0) + "ms");
        uniq(test);
        final long t2 = System.currentTimeMillis();
        System.out.println("uniq = " + (t2 - t1) + "ms");
        System.out.println("result: " + test.size());
    }

}
