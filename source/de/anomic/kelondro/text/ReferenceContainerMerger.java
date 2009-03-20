// ReferenceContainerArray.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.03.2009 on http://yacy.net
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

package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import de.anomic.kelondro.blob.BLOBArray;
import de.anomic.kelondro.blob.HeapWriter;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.text.ReferenceContainerCache.blobFileEntries;

/**
 * merger class for files from ReferenceContainerArray.
 * this is a concurrent merger that can merge single files that are queued for merging.
 * when several ReferenceContainerArray classes host their ReferenceContainer file arrays,
 * they may share a single ReferenceContainerMerger object which does the sharing for all
 * of them. This is the best way to do the merging, because it does heavy IO access and
 * such access should not be performed concurrently, but queued. This class is the
 * manaagement class for queueing of merge jobs.
 *
 * to use this class, first instantiate a object and then start the concurrent execution
 * of merging with a call to the start() - method. To shut down all mergings, call terminate()
 * only once.
 */
public class ReferenceContainerMerger extends Thread {

    private Job poison;
    private ArrayBlockingQueue<Job> queue;
    private ArrayBlockingQueue<Job> termi;
    
    public ReferenceContainerMerger(int queueLength) {
        this.poison = new Job();
        this.queue = new ArrayBlockingQueue<Job>(queueLength);
        this.termi = new ArrayBlockingQueue<Job>(1);
    }
    
    public synchronized void terminate() {
        if (queue == null || !this.isAlive()) return;
        try {
            queue.put(poison);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // await termination
        try {
            termi.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public synchronized void merge(File f1, File f2, BLOBArray array, Row payloadrow, File newFile) {
        if (queue == null || !this.isAlive()) {
            try {
                mergeMount(f1, f2, array, payloadrow, newFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Job job = new Job(f1, f2, array, payloadrow, newFile);
            try {
                queue.put(job);
            } catch (InterruptedException e) {
                e.printStackTrace();
                try {
                    mergeMount(f1, f2, array, payloadrow, newFile);
                } catch (IOException ee) {
                    ee.printStackTrace();
                }
            }
        }
    }
    
    public void run() {
        Job job;
        try {
            while ((job = queue.take()) != poison) {
                job.merge();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                termi.put(poison);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public class Job {

        File f1, f2, newFile;
        BLOBArray array;
        Row payloadrow;
        
        public Job() {
            this.f1 = null;
            this.f2 = null;
            this.newFile = null;
            this.array = null;
            this.payloadrow = null;
        }
        
        public Job(File f1, File f2, BLOBArray array, Row payloadrow, File newFile) {
            this.f1 = f1;
            this.f2 = f2;
            this.newFile = newFile;
            this.array = array;
            this.payloadrow = payloadrow;
        }

        public File merge() {
            try {
                return mergeMount(f1, f2, array, payloadrow, newFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    public static File mergeMount(File f1, File f2, BLOBArray array, Row payloadrow, File newFile) throws IOException {
        System.out.println("*** DEBUG mergeOldest: vvvvvvvvv array has " + array.entries() + " entries vvvvvvvvv");
        System.out.println("*** DEBUG mergeOldest: unmounted " + f1.getName());
        System.out.println("*** DEBUG mergeOldest: unmounted " + f2.getName());
        File resultFile = mergeWorker(f1, f2, array, payloadrow, newFile);
        if (resultFile == null) return null;
        array.mountBLOB(resultFile);
        System.out.println("*** DEBUG mergeOldest:   mounted " + newFile.getName());
        System.out.println("*** DEBUG mergeOldest: ^^^^^^^^^^^ array has " + array.entries() + " entries ^^^^^^^^^^^");
        return resultFile;
    }
    
    private static File mergeWorker(File f1, File f2, BLOBArray array, Row payloadrow, File newFile) throws IOException {
        // iterate both files and write a new one
        
        CloneableIterator<ReferenceContainer> i1 = new blobFileEntries(f1, payloadrow);
        CloneableIterator<ReferenceContainer> i2 = new blobFileEntries(f2, payloadrow);
        if (!i1.hasNext()) {
            if (i2.hasNext()) {
                if (!f1.delete()) f1.deleteOnExit();
                if (f2.renameTo(newFile)) return newFile;
                return f2;
            } else {
                if (!f1.delete()) f1.deleteOnExit();
                if (!f2.delete()) f2.deleteOnExit();
                return null;
            }
        } else if (!i2.hasNext()) {
            if (!f2.delete()) f2.deleteOnExit();
            if (f1.renameTo(newFile)) return newFile;
            return f1;
        }
        assert i1.hasNext();
        assert i2.hasNext();
        HeapWriter writer = new HeapWriter(newFile, array.keylength(), array.ordering());
        merge(i1, i2, array.ordering(), writer);
        writer.close(true);
        // we don't need the old files any more
        if (!f1.delete()) f1.deleteOnExit();
        if (!f2.delete()) f2.deleteOnExit();
        return newFile;
    }
    
    private static void merge(CloneableIterator<ReferenceContainer> i1, CloneableIterator<ReferenceContainer> i2, ByteOrder ordering, HeapWriter writer) throws IOException {
        assert i1.hasNext();
        assert i2.hasNext();
        ReferenceContainer c1, c2, c1o, c2o;
        c1 = i1.next();
        c2 = i2.next();
        int e;
        while (true) {
            assert c1 != null;
            assert c2 != null;
            e = ordering.compare(c1.getWordHash().getBytes(), c2.getWordHash().getBytes());
            if (e < 0) {
                writer.add(c1.getWordHash().getBytes(), c1.exportCollection());
                if (i1.hasNext()) {
                    c1o = c1;
                    c1 = i1.next();
                    assert ordering.compare(c1.getWordHash().getBytes(), c1o.getWordHash().getBytes()) > 0;
                    continue;
                }
                break;
            }
            if (e > 0) {
                writer.add(c2.getWordHash().getBytes(), c2.exportCollection());
                if (i2.hasNext()) {
                    c2o = c2;
                    c2 = i2.next();
                    assert ordering.compare(c2.getWordHash().getBytes(), c2o.getWordHash().getBytes()) > 0;
                    continue;
                }
                break;
            }
            assert e == 0;
            // merge the entries
            writer.add(c1.getWordHash().getBytes(), (c1.merge(c2)).exportCollection());
            if (i1.hasNext() && i2.hasNext()) {
                c1 = i1.next();
                c2 = i2.next();
                continue;
            }
            if (i1.hasNext()) c1 = i1.next();
            if (i2.hasNext()) c2 = i2.next();
            break;
           
        }
        // catch up remaining entries
        assert !(i1.hasNext() && i2.hasNext());
        while (i1.hasNext()) {
            //System.out.println("FLUSH REMAINING 1: " + c1.getWordHash());
            writer.add(c1.getWordHash().getBytes(), c1.exportCollection());
            if (i1.hasNext()) {
                c1o = c1;
                c1 = i1.next();
                assert ordering.compare(c1.getWordHash().getBytes(), c1o.getWordHash().getBytes()) > 0;
                continue;
            }
            break;
        }
        while (i2.hasNext()) {
            //System.out.println("FLUSH REMAINING 2: " + c2.getWordHash());
            writer.add(c2.getWordHash().getBytes(), c2.exportCollection());
            if (i2.hasNext()) {
                c2o = c2;
                c2 = i2.next();
                assert ordering.compare(c2.getWordHash().getBytes(), c2o.getWordHash().getBytes()) > 0;
                continue;
            }
            break;
        }
        // finished with writing
    }

}
