/**
 *  Array
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 16.07.2011 at http://yacy.net
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;



/**
 * an abstraction of the quicksort from the java.util.Array class
 * @author admin
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class Array {

    private final static int SORT_JOBS = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private final static SortJob<?> POISON_JOB_WORKER = new SortJob(null, 0, 0, 0, 0, null);
    private static BlockingQueue<SortJob<?>> sortJobs = null;

    static {
        if (SORT_JOBS > 1) {
            for (int i = 0; i < SORT_JOBS; i++) {
                new SortJobWorker().start();
            }
            sortJobs = new LinkedBlockingQueue();
        }
    }

    public static void terminate() {
        if (SORT_JOBS > 1 && sortJobs != null) {
            for (int i = 0; i < SORT_JOBS; i++) {
                try {
                    sortJobs.put(POISON_JOB_WORKER);
                } catch (final InterruptedException e) {}
            }
        }
    }

    private static class SortJobWorker extends Thread {
        @Override
        public void run() {
            this.setName("Array.SortJobWorker");
            SortJob<?> job;
            try {
                while ((job = sortJobs.take()) != POISON_JOB_WORKER) {
                    sort(job, job.depth < 8);
                    job.latch.countDown();
                }
            } catch (final InterruptedException e) {
            }
        }
    }

    public static <A> void sort(final Sortable<A> x) {
        UpDownLatch latch;
        final boolean threaded = false;//x.size() > 100000;
        sort(new SortJob<A>(x, 0, x.size(), x.buffer(), 0, latch = new UpDownLatch(0)), threaded);
        //for (int i = 0; i < 100; i++) {System.out.println("latch = " + latch.getCount());try {Thread.sleep(10);} catch (final InterruptedException e) {}}
        if (threaded) try {latch.await();} catch (final InterruptedException e) {}
    }

    private static class SortJob<A> {
        final Sortable<A> x; final int o; final int l; final A f; final int depth; UpDownLatch latch;
        public SortJob(final Sortable<A> x, final int o, final int l, final A f, final int depth, final UpDownLatch latch) {
            this.x = x; this.o = o; this.l = l; this.f = f; this.depth = depth; this.latch = latch;
        }
    }

    private static <A> void sort(final SortJob<A> job, final boolean threaded) {

        // in case of small arrays we do not need a quicksort
        if (job.l < 7) {
            for (int i = job.o; i < job.l + job.o; i++) {
                for (int j = i; j > job.o && job.x.compare(job.x.get(j, false), job.x.get(j - 1, false)) < 0; j--) job.x.swap(j, j - 1, job.f);
            }
            return;
        }

        // find the pivot element
        int m = job.o + (job.l >> 1);
        if (job.l > 7) {
            int k = job.o;
            int n = job.o + job.l - 1;
            if (job.l > 40) {
                final int s = job.l / 8;
                k = med3(job.x, k        , k + s, k + 2 * s);
                m = med3(job.x, m - s    , m    , m + s    );
                n = med3(job.x, n - 2 * s, n - s, n        );
            }
            m = med3(job.x, k, m, n);
        }
        final A p = job.x.get(m, true);

        // do a partitioning of the sequence
        int a = job.o, b = a, c = job.o + job.l - 1, d = c;
        A _v;
        while (true) {
            while (c >= b && job.x.compare(p, (_v = job.x.get(b, false))) >= 0) {
                if (job.x.compare(_v, p) == 0) job.x.swap(a++, b, job.f);
                b++;
            }
            while (c >= b && job.x.compare((_v = job.x.get(c, false)), p) >= 0) {
                if (job.x.compare(_v, p) == 0) job.x.swap(c, d--, job.f);
                c--;
            }
            if (b > c) break;
            job.x.swap(b++, c--, job.f);
        }

        // swap all
        int s;
        final int n = job.o + job.l;
        s = Math.min(a - job.o, b - a );
        swap(job.x, job.o, b - s, s, job.f);
        s = Math.min(d - c, n - d - 1);
        swap(job.x, b, n - s, s, job.f);

        // recursively sort partitions
        if ((s = b - a) > 1) {
            final SortJob<A> nextJob = new SortJob<A>(job.x, job.o, s, job.f, job.depth + 1, job.latch);
            if (threaded) try {
                sortJobs.put(nextJob);
                job.latch.countUp();
            } catch (final InterruptedException e) {
            } else {
                sort(nextJob, false);
            }
        }
        if ((s = d - c) > 1) {
            final SortJob<A> nextJob = new SortJob<A>(job.x, n - s, s, job.x.buffer(), job.depth + 1, job.latch);
            if (threaded) try {
                sortJobs.put(nextJob);
                job.latch.countUp();
            } catch (final InterruptedException e) {
            } else {
                sort(nextJob, false);
            }
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

    private static class P extends ArrayList<Integer> implements Sortable<Integer> {

        private static final long serialVersionUID = 1L;

        public P() {
            super();
        }

        @Override
        public int compare(final Integer o1, final Integer o2) {
            return o1.compareTo(o2);
        }

        @Override
        public Integer buffer() {
            return new Integer(0);
        }

        @Override
        public void swap(final int i, final int j, Integer buffer) {
            buffer = get(i);
            set(i, get(j));
            set(j, buffer);
        }

        @Override
        public void delete(final int i) {
            this.remove(i);
        }

        @Override
        public Integer get(final int i, final boolean clone) {
            return get(i);
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
        //uniq(test);
        final long t2 = System.currentTimeMillis();
        System.out.println("uniq = " + (t2 - t1) + "ms");
        System.out.println("result: " + test.size());
        terminate();
    }

}
