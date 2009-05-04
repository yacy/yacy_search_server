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
import java.util.concurrent.Semaphore;

import de.anomic.kelondro.blob.BLOBArray;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.util.Log;
import de.anomic.kelondro.util.MemoryControl;

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
public class IODispatcher <ReferenceType extends Reference> extends Thread {

    private Semaphore                    controlQueue;
    private Semaphore                    termination;
    private ArrayBlockingQueue<MergeJob> mergeQueue;
    private ArrayBlockingQueue<DumpJob>  dumpQueue;
    private ReferenceFactory<ReferenceType> factory;
    private boolean                      terminate;
    private int                          writeBufferSize;
    
    public IODispatcher(ReferenceFactory<ReferenceType> factory, int dumpQueueLength, int mergeQueueLength, int writeBufferSize) {
        this.factory = factory;
        this.termination = new Semaphore(0);
        this.controlQueue = new Semaphore(0);
        this.dumpQueue = new ArrayBlockingQueue<DumpJob>(dumpQueueLength);
        this.mergeQueue = new ArrayBlockingQueue<MergeJob>(mergeQueueLength);
        this.writeBufferSize = writeBufferSize;
        this.terminate = false;
    }
    
    public synchronized void terminate() {
        if (termination != null && controlQueue != null && this.isAlive()) {
            this.terminate = true;
            this.controlQueue.release();
            // await termination
            try {
                termination.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public synchronized void dump(ReferenceContainerCache<ReferenceType> cache, File file, ReferenceContainerArray<ReferenceType> array) {
        if (dumpQueue == null || controlQueue == null || !this.isAlive()) {
            Log.logWarning("IODispatcher", "emergency dump of file " + file.getName());
            cache.dump(file, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
        } else {
            DumpJob job = new DumpJob(cache, file, array);
            try {
                this.dumpQueue.put(job);
                this.controlQueue.release();
                Log.logInfo("IODispatcher", "appended dump job for file " + file.getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
                cache.dump(file, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
            }
        }
    }
    
    public synchronized int queueLength() {
        return (controlQueue == null || !this.isAlive()) ? 0 : controlQueue.availablePermits();
    }
    
    public synchronized void merge(File f1, File f2, BLOBArray array, Row payloadrow, File newFile) {
        if (mergeQueue == null || controlQueue == null || !this.isAlive()) {
            try {
                Log.logWarning("IODispatcher", "emergency merge of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
                array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            MergeJob job = new MergeJob(f1, f2, array, payloadrow, newFile);
            try {
                this.mergeQueue.put(job);
                this.controlQueue.release();
                Log.logInfo("IODispatcher", "appended merge job of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
                try {
                    array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
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
            loop: while (true) {
                controlQueue.acquire();
                
                // prefer dump actions to flush memory to disc
                if (dumpQueue.size() > 0) {
                    try {
                        dumpJob = dumpQueue.take();
                        dumpJob.dump();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.logSevere("IODispatcher", "main run job was interrupted (1)", e);
                    }
                    continue loop;
                }
                
                // otherwise do a merge operation
                if (mergeQueue.size() > 0) {
                    try {
                        mergeJob = mergeQueue.take();
                        mergeJob.merge();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.logSevere("IODispatcher", "main run job was interrupted (2)", e);
                    }
                    continue loop;
                }
                
                // check termination
                if (this.terminate) {
                    Log.logInfo("IODispatcher", "catched termination signal");
                    break;
                }

                Log.logSevere("IODispatcher", "main loop in bad state, dumpQueue.size() = " + dumpQueue.size() + ", mergeQueue.size() = " + mergeQueue.size() + ", controlQueue.availablePermits() = " + controlQueue.availablePermits());
                assert false : "this process statt should not be reached"; // this should never happen
            }
            Log.logInfo("IODispatcher", "loop terminated");
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.logSevere("IODispatcher", "main run job was interrupted (3)", e);
        } catch (Exception e) {
            e.printStackTrace();
            Log.logSevere("IODispatcher", "main run job failed (4)", e);
        } finally {
            Log.logInfo("IODispatcher", "terminating run job");
            controlQueue = null;
            dumpQueue = null;
            mergeQueue = null;
            termination.release();
        }
    }
    
    public class DumpJob {
        ReferenceContainerCache<ReferenceType> cache;
        File file;
        ReferenceContainerArray<ReferenceType> array;
        public DumpJob(ReferenceContainerCache<ReferenceType> cache, File file, ReferenceContainerArray<ReferenceType> array) {
            this.cache = cache;
            this.file = file;
            this.array = array;
        }
        public void dump() {
            try {
                cache.dump(file, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
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
        	if (!f1.exists()) {
        		Log.logWarning("IODispatcher", "merge of file (1) " + f1.getName() + " failed: file does not exists");
        		return null;
        	}
        	if (!f2.exists()) {
        		Log.logWarning("IODispatcher", "merge of file (2) " + f2.getName() + " failed: file does not exists");
        		return null;
        	}
            try {
                return array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}
