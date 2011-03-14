// IODispatcher.java
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

package net.yacy.kelondro.rwi;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;

import net.yacy.kelondro.blob.ArrayStack;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;


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
    private   final Semaphore              termination;
    private   ArrayBlockingQueue<MergeJob> mergeQueue;
    private   ArrayBlockingQueue<DumpJob<? extends Reference>> dumpQueue;
    //private ReferenceFactory<ReferenceType> factory;
    private   boolean                      terminate;
    private int                          writeBufferSize;
    
    public IODispatcher(int dumpQueueLength, int mergeQueueLength, int writeBufferSize) {
        this.termination = new Semaphore(0);
        this.controlQueue = new Semaphore(0);
        this.dumpQueue = new ArrayBlockingQueue<DumpJob<? extends Reference>>(dumpQueueLength);
        this.mergeQueue = new ArrayBlockingQueue<MergeJob>(mergeQueueLength);
        this.writeBufferSize = writeBufferSize;
        this.terminate = false;
    }
    
    public void terminate() {
        if (termination != null && controlQueue != null && this.isAlive()) {
            this.terminate = true;
            this.controlQueue.release();
            // await termination
            try {
                termination.acquire();
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
	protected synchronized void dump(ReferenceContainerCache<? extends Reference> cache, File file, ReferenceContainerArray<? extends Reference> array) {
        if (dumpQueue == null || controlQueue == null || !this.isAlive()) {
            Log.logWarning("IODispatcher", "emergency dump of file " + file.getName());
             if (!cache.isEmpty()) cache.dump(file, (int) Math.min(MemoryControl.available() / 3, writeBufferSize), true);
        } else {
            @SuppressWarnings("rawtypes")
            DumpJob<? extends Reference> job = new DumpJob(cache, file, array);
            try {
                // check if the dispatcher is running
                if (this.isAlive()) {
                    this.dumpQueue.put(job);
                    this.controlQueue.release();
                    Log.logInfo("IODispatcher", "appended dump job for file " + file.getName());
                } else {
                    job.dump();
                    Log.logWarning("IODispatcher", "dispatcher is not alive, just dumped file " + file.getName());
                }
            } catch (InterruptedException e) {
                Log.logException(e);
                cache.dump(file, (int) Math.min(MemoryControl.available() / 3, writeBufferSize), true);
            }
        }
    }
    
    protected synchronized int queueLength() {
        return (controlQueue == null || !this.isAlive()) ? 0 : controlQueue.availablePermits();
    }
    
    protected synchronized void merge(File f1, File f2, ReferenceFactory<? extends Reference> factory, ArrayStack array, Row payloadrow, File newFile) {
        if (mergeQueue == null || controlQueue == null || !this.isAlive()) {
            if (f2 == null) {
                Log.logWarning("IODispatcher", "emergency rewrite of file " + f1.getName() + " to " + newFile.getName());
            } else {
                Log.logWarning("IODispatcher", "emergency merge of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
            }
            array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
        } else {
            MergeJob job = new MergeJob(f1, f2, factory, array, payloadrow, newFile);
            try {
                if (this.isAlive()) {
                    this.mergeQueue.put(job);
                    this.controlQueue.release();
                    if (f2 == null) {
                        Log.logInfo("IODispatcher", "appended rewrite job of file " + f1.getName() + " to " + newFile.getName());
                    } else {
                        Log.logInfo("IODispatcher", "appended merge job of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
                    }
                } else {
                    job.merge();
                    if (f2 == null) {
                        Log.logWarning("IODispatcher", "dispatcher not running, merged files " + f1.getName() + " to " + newFile.getName());
                    } else {
                        Log.logWarning("IODispatcher", "dispatcher not running, rewrote file " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
                    }
                }
            } catch (InterruptedException e) {
                Log.logWarning("IODispatcher", "interrupted: " + e.getMessage(), e);
                array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
            }
        }
    }
    
    @Override
    public void run() {
        MergeJob mergeJob;
        DumpJob<? extends Reference> dumpJob;
        try {
            loop: while (true) try {
                controlQueue.acquire();
                
                // prefer dump actions to flush memory to disc
                if (!dumpQueue.isEmpty()) {
                	File f = null;
                    try {
                        dumpJob = dumpQueue.take();
                        f = dumpJob.file;
                        dumpJob.dump();
                    } catch (InterruptedException e) {
                        Log.logSevere("IODispatcher", "main run job was interrupted (1)", e);
                        Log.logException(e);
                    } catch (Exception e) {
                        Log.logSevere("IODispatcher", "main run job had errors (1), dump to " + f + " failed.", e);
                        Log.logException(e);
                    }
                    continue loop;
                }
                
                // otherwise do a merge operation
                if (!mergeQueue.isEmpty()) {
                	File f = null, f1 = null, f2 = null;
                    try {
                        mergeJob = mergeQueue.take();
                        f = mergeJob.newFile;
                        f1 = mergeJob.f1;
                        f2 = mergeJob.f2;
                        mergeJob.merge();
                    } catch (InterruptedException e) {
                        Log.logSevere("IODispatcher", "main run job was interrupted (2)", e);
                        Log.logException(e);
                    } catch (Exception e) {
                        if (f2 == null) {
                        Log.logSevere("IODispatcher", "main run job had errors (2), dump to " + f + " failed. Input file is " + f1, e);
                        } else {
                            Log.logSevere("IODispatcher", "main run job had errors (2), dump to " + f + " failed. Input files are " + f1 + " and " + f2, e);
                        }
                        Log.logException(e);
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
            } catch (Exception e) {
                Log.logSevere("IODispatcher", "main run job failed (X)", e);
                Log.logException(e);
            }
            Log.logInfo("IODispatcher", "loop terminated");
        } catch (Exception e) {
            Log.logSevere("IODispatcher", "main run job failed (4)", e);
            Log.logException(e);
        } finally {
            Log.logInfo("IODispatcher", "terminating run job");
            controlQueue = null;
            dumpQueue = null;
            mergeQueue = null;
            termination.release();
        }
    }
    
    private class DumpJob<ReferenceType extends Reference> {
        private ReferenceContainerCache<ReferenceType> cache;
        private File file;
        private ReferenceContainerArray<ReferenceType> array;
        private DumpJob(ReferenceContainerCache<ReferenceType> cache, File file, ReferenceContainerArray<ReferenceType> array) {
            this.cache = cache;
            this.file = file;
            this.array = array;
        }
        private void dump() {
            try {
                if (!cache.isEmpty()) cache.dump(file, (int) Math.min(MemoryControl.available() / 3, writeBufferSize), true);
                array.mountBLOBFile(file);
            } catch (IOException e) {
                Log.logException(e);
            }
        }
    }
    
    private class MergeJob {

        private File f1, f2, newFile;
        private ArrayStack array;
        private Row payloadrow;
        private ReferenceFactory<? extends Reference> factory;
        
        private MergeJob(
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

        private File merge() {
        	if (!f1.exists()) {
        		Log.logWarning("IODispatcher", "merge of file (1) " + f1.getName() + " failed: file does not exists");
        		return null;
        	}
        	if (f2 != null && !f2.exists()) {
        		Log.logWarning("IODispatcher", "merge of file (2) " + f2.getName() + " failed: file does not exists");
        		return null;
        	}
            return array.mergeMount(f1, f2, factory, payloadrow, newFile, (int) Math.min(MemoryControl.available() / 3, writeBufferSize));
        }
    }

}
