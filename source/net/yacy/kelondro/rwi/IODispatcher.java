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

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.blob.ArrayStack;
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

    private static final ConcurrentLog log = new ConcurrentLog("IODispatcher");

    private   Semaphore                    controlQueue;
    private   final Semaphore              termination;
    private   ArrayBlockingQueue<MergeJob> mergeQueue;
    private   ArrayBlockingQueue<DumpJob<? extends Reference>> dumpQueue;
    //private ReferenceFactory<ReferenceType> factory;
    private   boolean                      terminate;
    private final int                          writeBufferSize;

    public IODispatcher(final int dumpQueueLength, final int mergeQueueLength, final int writeBufferSize) {
        this.termination = new Semaphore(0);
        this.controlQueue = new Semaphore(0);
        this.dumpQueue = new ArrayBlockingQueue<DumpJob<? extends Reference>>(dumpQueueLength);
        this.mergeQueue = new ArrayBlockingQueue<MergeJob>(mergeQueueLength);
        this.writeBufferSize = writeBufferSize;
        this.terminate = false;
        this.setName("IODispatcher");
    }

    public void terminate() {
        if (this.termination != null && this.controlQueue != null && isAlive()) {
            this.terminate = true;
            this.controlQueue.release();
            // await termination
            try {
                this.termination.acquire();
            } catch (final InterruptedException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
	protected synchronized void dump(final ReferenceContainerCache<? extends Reference> cache, final File file, final ReferenceContainerArray<? extends Reference> array) {
        if (this.dumpQueue == null || this.controlQueue == null || !isAlive()) {
            log.warn("emergency dump of file " + file.getName());
             if (!cache.isEmpty()) cache.dump(file, (int) Math.min(MemoryControl.available() / 3, this.writeBufferSize), true);
        } else {
            @SuppressWarnings("rawtypes")
            final
            DumpJob<? extends Reference> job = new DumpJob(cache, file, array);
            // check if the dispatcher is running
            if (isAlive()) {
                try {
                    this.dumpQueue.put(job);
                    log.info("appended dump job for file " + file.getName());
                } catch (final InterruptedException e) {
                    ConcurrentLog.logException(e);
                    cache.dump(file, (int) Math.min(MemoryControl.available() / 3, this.writeBufferSize), true);
                } finally {
                    this.controlQueue.release();
                }
            } else {
                job.dump();
                log.warn("dispatcher is not alive, just dumped file " + file.getName());
            }
        }
    }

    protected synchronized int queueLength() {
        return (this.controlQueue == null || !isAlive()) ? 0 : this.controlQueue.availablePermits();
    }

    protected synchronized void merge(final File f1, final File f2, final ReferenceFactory<? extends Reference> factory, final ArrayStack array, final File newFile) {
        if (this.mergeQueue == null || this.controlQueue == null || !isAlive()) {
            if (f2 == null) {
                log.warn("emergency rewrite of file " + f1.getName() + " to " + newFile.getName());
            } else {
                log.warn("emergency merge of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
            }
            array.mergeMount(f1, f2, factory, newFile, (int) Math.min(MemoryControl.available() / 3, this.writeBufferSize));
        } else {
            final MergeJob job = new MergeJob(f1, f2, factory, array, newFile);
            if (isAlive()) {
                try {
                    this.mergeQueue.put(job);
                    if (f2 == null) {
                        log.info("appended rewrite job of file " + f1.getName() + " to " + newFile.getName());
                    } else {
                        log.info("appended merge job of files " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
                    }
                } catch (final InterruptedException e) {
                    log.warn("interrupted: " + e.getMessage(), e);
                    array.mergeMount(f1, f2, factory, newFile, (int) Math.min(MemoryControl.available() / 3, this.writeBufferSize));
                } finally {
                    this.controlQueue.release();
                }
            } else {
                job.merge();
                if (f2 == null) {
                    log.warn("dispatcher not running, merged files " + f1.getName() + " to " + newFile.getName());
                } else {
                    log.warn("dispatcher not running, rewrote file " + f1.getName() + ", " + f2.getName() + " to " + newFile.getName());
                }
            }
        }
    }

    @Override
    public void run() {
        MergeJob mergeJob;
        DumpJob<? extends Reference> dumpJob;
        try {
            loop: while (true) try {
                this.controlQueue.acquire();

                // prefer dump actions to flush memory to disc
                if (!this.dumpQueue.isEmpty()) {
                	File f = null;
                    try {
                        dumpJob = this.dumpQueue.take();
                        f = dumpJob.file;
                        dumpJob.dump();
                    } catch (final InterruptedException e) {
                        log.severe("main run job was interrupted (1)", e);
                        ConcurrentLog.logException(e);
                    } catch (final Throwable e) {
                        log.severe("main run job had errors (1), dump to " + f + " failed.", e);
                        ConcurrentLog.logException(e);
                    }
                    continue loop;
                }

                // otherwise do a merge operation
                if (!this.mergeQueue.isEmpty() && !MemoryControl.shortStatus()) {
                	File f = null, f1 = null, f2 = null;
                    try {
                        mergeJob = this.mergeQueue.take();
                        f = mergeJob.newFile;
                        f1 = mergeJob.f1;
                        f2 = mergeJob.f2;
                        mergeJob.merge();
                    } catch (final InterruptedException e) {
                        log.severe("main run job was interrupted (2)", e);
                        ConcurrentLog.logException(e);
                    } catch (final Throwable e) {
                        if (f2 == null) {
                            log.severe("main run job had errors (2), dump to " + f + " failed. Input file is " + f1, e);
                        } else {
                            log.severe("main run job had errors (2), dump to " + f + " failed. Input files are " + f1 + " and " + f2, e);
                        }
                        ConcurrentLog.logException(e);
                    }
                    continue loop;
                }

                // check termination
                if (this.terminate) {
                    log.info("caught termination signal");
                    break;
                }

                log.severe("main loop in bad state, dumpQueue.size() = " + this.dumpQueue.size() + ", mergeQueue.size() = " + this.mergeQueue.size() + ", controlQueue.availablePermits() = " + this.controlQueue.availablePermits());
                assert false : "this process statt should not be reached"; // this should never happen
            } catch (final Throwable e) {
                log.severe("main run job failed (X)", e);
                ConcurrentLog.logException(e);
            }
        log.info("loop terminated");
        } catch (final Throwable e) {
            log.severe("main run job failed (4)", e);
            ConcurrentLog.logException(e);
        } finally {
            log.info("terminating run job");
            this.controlQueue = null;
            this.dumpQueue = null;
            this.mergeQueue = null;
            this.termination.release();
        }
    }

    private class DumpJob<ReferenceType extends Reference> {
        private final ReferenceContainerCache<ReferenceType> cache;
        private final File file;
        private final ReferenceContainerArray<ReferenceType> array;
        private DumpJob(final ReferenceContainerCache<ReferenceType> cache, final File file, final ReferenceContainerArray<ReferenceType> array) {
            this.cache = cache;
            this.file = file;
            this.array = array;
        }
        private void dump() {
            try {
                if (!this.cache.isEmpty()) this.cache.dump(this.file, (int) Math.min(MemoryControl.available() / 3, IODispatcher.this.writeBufferSize), true);
                this.array.mountBLOBFile(this.file);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    private class MergeJob {

        private final File f1, f2, newFile;
        private final ArrayStack array;
        private final ReferenceFactory<? extends Reference> factory;

        private MergeJob(
                final File f1,
                final File f2,
                final ReferenceFactory<? extends Reference> factory,
                final ArrayStack array,
                final File newFile) {
            this.f1 = f1;
            this.f2 = f2;
            this.factory = factory;
            this.newFile = newFile;
            this.array = array;
        }

        private File merge() {
        	if (!this.f1.exists()) {
        	    log.warn("merge of file (1) " + this.f1.getName() + " failed: file does not exists");
        		return null;
        	}
        	if (this.f2 != null && !this.f2.exists()) {
        	    log.warn("merge of file (2) " + this.f2.getName() + " failed: file does not exists");
        		return null;
        	}
            return this.array.mergeMount(this.f1, this.f2, this.factory, this.newFile, (int) Math.min(MemoryControl.available() / 3, IODispatcher.this.writeBufferSize));
        }
    }

}
