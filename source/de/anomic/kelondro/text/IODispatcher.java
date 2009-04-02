// IODespatcher.java
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
import de.anomic.kelondro.index.Row;

/**
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
public class IODispatcher extends Thread {

    private final Boolean poison, vita;
    private ArrayBlockingQueue<Boolean>  controlQueue;
    private ArrayBlockingQueue<MergeJob> mergeQueue;
    private ArrayBlockingQueue<DumpJob>  dumpQueue;
    private ArrayBlockingQueue<Boolean>  termQueue;
    
    public IODispatcher(int dumpQueueLength, int mergeQueueLength) {
        this.poison = new Boolean(false);
        this.vita = new Boolean(true);
        this.controlQueue = new ArrayBlockingQueue<Boolean>(dumpQueueLength + mergeQueueLength + 1);
        this.dumpQueue = new ArrayBlockingQueue<DumpJob>(dumpQueueLength);
        this.mergeQueue = new ArrayBlockingQueue<MergeJob>(mergeQueueLength);
        this.termQueue = new ArrayBlockingQueue<Boolean>(1);
    }
    
    public synchronized void terminate() {
        if (termQueue != null && this.isAlive()) {
            try {
                controlQueue.put(poison);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // await termination
            try {
                termQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public synchronized void dump(ReferenceContainerCache cache, File file, ReferenceContainerArray array) {
        if (dumpQueue == null || !this.isAlive()) {
            cache.dump(file, true);
        } else {
            DumpJob job = new DumpJob(cache, file, array);
            try {
                dumpQueue.put(job);
                controlQueue.put(vita);
            } catch (InterruptedException e) {
                e.printStackTrace();
                cache.dump(file, true);
            }
        }
    }
    
    public synchronized int queueLength() {
        return controlQueue.size();
    }
    
    public synchronized void merge(File f1, File f2, BLOBArray array, Row payloadrow, File newFile) {
        if (mergeQueue == null || !this.isAlive()) {
            try {
                array.mergeMount(f1, f2, payloadrow, newFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            MergeJob job = new MergeJob(f1, f2, array, payloadrow, newFile);
            try {
                mergeQueue.put(job);
                controlQueue.put(vita);
            } catch (InterruptedException e) {
                e.printStackTrace();
                try {
                    array.mergeMount(f1, f2, payloadrow, newFile);
                } catch (IOException ee) {
                    ee.printStackTrace();
                }
            }
        }
    }
    
    public void run() {
        MergeJob mergeJob;
        DumpJob dumpJob;
        try {
            loop: while (controlQueue.take() != poison) {
                // prefer dump actions to flush memory to disc
                if (dumpQueue.size() > 0) {
                    dumpJob = dumpQueue.take();
                    dumpJob.dump();
                    continue loop;
                }
                // otherwise do a merge operation
                if (mergeQueue.size() > 0) {
                    mergeJob = mergeQueue.take();
                    mergeJob.merge();
                    continue loop;
                }
                assert false; // this should never happen
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                termQueue.put(poison);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public class DumpJob {
        ReferenceContainerCache cache;
        File file;
        ReferenceContainerArray array;
        public DumpJob(ReferenceContainerCache cache, File file, ReferenceContainerArray array) {
            this.cache = cache;
            this.file = file;
            this.array = array;
        }
        public void dump() {
            try {
                cache.dump(file, true);
                array.mountBLOBFile(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public class MergeJob {

        File f1, f2, newFile;
        BLOBArray array;
        Row payloadrow;
        
        public MergeJob(File f1, File f2, BLOBArray array, Row payloadrow, File newFile) {
            this.f1 = f1;
            this.f2 = f2;
            this.newFile = newFile;
            this.array = array;
            this.payloadrow = payloadrow;
        }

        public File merge() {
            try {
                return array.mergeMount(f1, f2, payloadrow, newFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}
