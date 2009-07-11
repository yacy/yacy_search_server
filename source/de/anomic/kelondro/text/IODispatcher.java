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

import de.anomic.kelondro.blob.ArrayStack;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.util.MemoryControl;
import de.anomic.yacy.logging.Log;

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

    private   Semaphore                    controlQueue;
    private   Semaphore                    termination;
    private   ArrayBlockingQueue<MergeJob> mergeQueue;
    private   ArrayBlockingQueue<DumpJob<? extends Reference>> dumpQueue;
    //private ReferenceFactory<ReferenceType> factory;
    private   boolean                      terminate;
    protected int                          writeBufferSize;
    
    public IODispatcher(int dumpQueueLength, int mergeQueueLength, int writeBufferSize) {
        this.termination = new Semaphore(0);
        this.controlQueue = new Semaphore(0);
        this.dumpQueue = new ArrayBlockingQueue<DumpJob<? extends Reference>>(dumpQueueLength);
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
    
    public synchronized void dump(ReferenceContainerCache<? extends Reference> cache, File file, ReferenceContainerArray<? extends Reference> array) {
        if (dumpQueue == null || controlQueue == null || !this.isAlive()) {
            Log.logWarning("IODispatcher", "emergency dump of file " + file.getName());
            cache.dump(file, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
        } else {
            DumpJob<? extends Reference> job = (DumpJob<? extends Reference>)new DumpJob(cache, file, array);
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
    
    public synchronized void merge(File f1, File f2, ReferenceFactory<? extends Reference> factory, ArrayStack array, Row payloadrow, File newFile) {
        if (mergeQueue == null || controlQueue == null || !this.isAlive()) {
            try {
                Log.logWarning("IODispatcher", "emergency merge of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
                array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
            } catch (IOException e) {
                Log.logSevere("IODispatcher", "emergency merge failed: " + e.getMessage(), e);
            }
        } else {
            MergeJob job = new MergeJob(f1, f2, factory, array, payloadrow, newFile);
            try {
                this.mergeQueue.put(job);
                this.controlQueue.release();
                Log.logInfo("IODispatcher", "appended merge job of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
            } catch (InterruptedException e) {
                Log.logWarning("IODispatcher", "interrupted: " + e.getMessage(), e);
                try {
                    array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
                } catch (IOException ee) {
                    Log.logSevere("IODispatcher", "IO failed: " + e.getMessage(), ee);
                }
            }
        }
    }
    
    public void run() {
        MergeJob mergeJob;
        DumpJob<? extends Reference> dumpJob;
        try {
            loop: while (true) {
                controlQueue.acquire();
                
                // prefer dump actions to flush memory to disc
                if (dumpQueue.size() > 0) {
                	File f = null;
                    try {
                        dumpJob = dumpQueue.take();
                        f = dumpJob.file;
                        dumpJob.dump();
                    } catch (InterruptedException e) {
                        Log.logSevere("IODispatcher", "main run job was interrupted (1)", e);
                        e.printStackTrace();
                    } catch (Exception e) {
                        Log.logSevere("IODispatcher", "main run job had errors (1), dump to " + f + " failed.", e);
                    	e.printStackTrace();
                    }
                    continue loop;
                }
                
                // otherwise do a merge operation
                if (mergeQueue.size() > 0) {
                	File f = null, f1 = null, f2 = null;
                    try {
                        mergeJob = mergeQueue.take();
                        f = mergeJob.newFile;
                        f1 = mergeJob.f1;
                        f2 = mergeJob.f2;
                        mergeJob.merge();
                    } catch (InterruptedException e) {
                        Log.logSevere("IODispatcher", "main run job was interrupted (2)", e);
                        e.printStackTrace();
                    } catch (Exception e) {
                        Log.logSevere("IODispatcher", "main run job had errors (2), dump to " + f + " failed. Input files are " + f1 + " and " + f2, e);
                    	e.printStackTrace();
                    }
                    continue loop;
                }
                
                // check termination
                if (this.terminate) {
                    Log.logInfo("IODispatcher", "caught termination signal");
                    break;
                }

                Log.logSevere("IODispatcher", "main loop in bad state, dumpQueue.size() = " + dumpQueue.size() + ", mergeQueue.size() = " + mergeQueue.size() + ", controlQueue.availablePermits() = " + controlQueue.availablePermits());
                assert false : "this process statt should not be reached"; // this should never happen
            }
            Log.logInfo("IODispatcher", "loop terminated");
        } catch (InterruptedException e) {
            Log.logSevere("IODispatcher", "main run job was interrupted (3)", e);
            e.printStackTrace();
        } catch (Exception e) {
            Log.logSevere("IODispatcher", "main run job failed (4)", e);
            e.printStackTrace();
        } finally {
            Log.logInfo("IODispatcher", "terminating run job");
            controlQueue = null;
            dumpQueue = null;
            mergeQueue = null;
            termination.release();
        }
    }
    
    public class DumpJob <ReferenceType extends Reference> {
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
        ArrayStack array;
        Row payloadrow;
        ReferenceFactory<? extends Reference> factory;
        
        public MergeJob(
                File f1,
                File f2,
                ReferenceFactory<? extends Reference> factory,
                ArrayStack array,
                Row payloadrow,
                File newFile) {
            this.f1 = f1;
            this.f2 = f2;
            this.factory = factory;
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
                Log.logSevere("IODispatcher", "mergeMount failed: " + e.getMessage(), e);
            }
            return null;
        }
    }

}
