// AbstractBusyThread.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.03.2005 on http://yacy.net
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

package net.yacy.kelondro.workflow;

import java.net.SocketException;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.Memory;
import net.yacy.kelondro.util.MemoryControl;


public abstract class AbstractBusyThread extends AbstractThread implements BusyThread {

    private final static ConcurrentLog log = new ConcurrentLog("BusyThread");
    private long startup = 0, intermission = 0, idlePause = 0, busyPause = 0;
    private long idletime = 0, memprereq = 0;
    private long idleCycles = 0, busyCycles = 0, outofmemoryCycles = 0;
    private double loadprereq = 9;
    private boolean intermissionObedient = true;
    private final Object syncObject = new Object();
    
    private long maxIdleSleep = Long.MAX_VALUE, minIdleSleep = Long.MIN_VALUE;
    private long maxBusySleep = Long.MAX_VALUE, minBusySleep = Long.MIN_VALUE;
    
    public AbstractBusyThread(
            long minIdleSleep,
            long maxIdleSleep,
            long minBusySleep,
            long maxBusySleep) {
        this.minIdleSleep = minIdleSleep;
        this.maxIdleSleep = maxIdleSleep;
        this.minBusySleep = minBusySleep;
        this.maxBusySleep = maxBusySleep;
    }

    @Override
    public final void setStartupSleep(final long milliseconds) {
        // sets a sleep time before execution of the job-loop
        startup = milliseconds;
    }
    
    @Override
    public final long setIdleSleep(final long milliseconds) {
        // sets a sleep time for pauses between two jobs
        idlePause = Math.min(this.maxIdleSleep, Math.max(this.minIdleSleep, milliseconds));
        return idlePause;
    }
    
    @Override
    public final long getIdleSleep() {
        return idlePause;
    }
    
    @Override
    public final long setBusySleep(final long milliseconds) {
        // sets a sleep time for pauses between two jobs
        busyPause = Math.min(this.maxBusySleep, Math.max(this.minBusySleep, milliseconds));
        return busyPause;
    }
    
    @Override
    public final long getBusySleep() {
        return busyPause;
    }
    
    @Override
    public void setMemPreReqisite(final long freeBytes) {
        // sets minimum required amount of memory for the job execution
        memprereq = freeBytes;
    }
    
    @Override
    public double setLoadPreReqisite(final double load) {
        // sets minimum required amount of memory for the job execution
        loadprereq = load;
        return load;
    }
    
    @Override
    public void setObeyIntermission(final boolean obey) {
        // defines if the thread should obey the intermission command
        intermissionObedient = obey;
    }
    
    @Override
    public final long getIdleCycles() {
        // returns the total number of cycles of job execution with idle-result
        return this.idleCycles;
    }
    
    @Override
    public final long getBusyCycles() {
        // returns the total number of cycles of job execution with busy-result
        return this.busyCycles;
    }

    @Override
    public long getOutOfMemoryCycles() {
        // returns the total number of cycles where
        // a job execution was omitted because of memory shortage
        return this.outofmemoryCycles;
    }
    
    @Override
    public final long getSleepTime() {
        // returns the total time that this thread has slept so far
        return this.idletime;
    }
    
    @Override
    public void intermission(final long pause) {
        if (pause == Long.MAX_VALUE)
            this.intermission = Long.MAX_VALUE;
        else
            this.intermission = System.currentTimeMillis() + pause;
    }


    @Override
    public void run() {
        if (startup > 0) {
            // do a startup-delay
            logSystem("thread '" + this.getName() + "' deployed, delaying start-up.");
            ratz(startup);
            if (!(running)) return;
        }
        this.open();
        if (log != null) {
            if (startup > 0) 
                logSystem("thread '" + this.getName() + "' delayed, " + ((this.busyPause < 0) ? "starting now job." : "starting now loop."));
            else
                logSystem("thread '" + this.getName() + "' deployed, " + ((this.busyPause < 0) ? "starting job." : "starting loop."));
        }
        long timestamp;
        long memstamp0, memstamp1;
        boolean isBusy;
        //Runtime rt = Runtime.getRuntime();
        
        while (running) {
            if ((this.intermissionObedient) && (this.intermission > 0) && (this.intermission != Long.MAX_VALUE)) {
                long itime = this.intermission - System.currentTimeMillis();
                if (itime > 0) {
                    if (itime > this.idlePause) itime = this.idlePause;
                    logSystem("thread '" + this.getName()
                            + "' breaks for intermission: " + (itime / 1000)
                            + " seconds");
                    ratz(itime);
                }
                this.intermission = 0;
            }

            if (this.intermission == Long.MAX_VALUE) {
                // omit Job, paused
                logSystem("thread '" + this.getName() + "' paused");
                timestamp = System.currentTimeMillis();
                ratz(this.idlePause);
                idletime += System.currentTimeMillis() - timestamp;
            //} else if ((memnow = serverMemory.available()) > memprereq) try {
            } else if (Memory.load() > loadprereq) {
            	logSystem("Thread '" + this.getName() + "' runs high load cycle. current: " + Memory.load() + " max.: " + loadprereq);
                timestamp = System.currentTimeMillis();
                ratz(this.idlePause);
                idletime += System.currentTimeMillis() - timestamp;
            } else if (MemoryControl.request(memprereq, false)) try {
                // do job
                timestamp = System.currentTimeMillis();
                memstamp0 = MemoryControl.used();
                isBusy = this.job();
                // do memory and busy/idle-count/time monitoring
                if (isBusy) {
                    memstamp1 = MemoryControl.used();
                    if (memstamp1 >= memstamp0) {
                        // no GC in between. this is not sure but most probable
                        memuse += memstamp1 - memstamp0;
                    } else {
                        // GC was obviously in between. Add an average as simple heuristic
                        if (busyCycles > 0) memuse += memuse / busyCycles;
                    }
                    busytime += System.currentTimeMillis() - timestamp;
                    busyCycles++;
                } else {
                    idleCycles++;
                }
                // interrupt loop if this is interrupted or supposed to be a one-time job
                if ((!running) || (this.isInterrupted())) break;
                if ((this.idlePause < 0) || (this.busyPause < 0)) break; // for one-time jobs
                // process scheduled pause
                timestamp = System.currentTimeMillis();
                ratz((isBusy) ? this.busyPause : this.idlePause);
                idletime += System.currentTimeMillis() - timestamp;
            } catch (final SocketException e) {
                // in case that a socket is interrupted, this method must die silently (shutdown)
                log.fine("socket-job interrupted: " + e.getMessage());
            } catch (final Exception e) {
                // handle exceptions: thread must not die on any unexpected exceptions
                // if the exception is too bad it should call terminate()
                this.jobExceptionHandler(e);
                busyCycles++;
            } else {
                log.warn("Thread '" + this.getName() + "' runs short memory cycle. Free mem: " +
                        (MemoryControl.available() / 1024) + " KB, needed: " + (memprereq / 1024) + " KB");
                // omit job, not enough memory
                // process scheduled pause
                timestamp = System.currentTimeMillis();
                // do a clean-up
                this.freemem();
                // sleep a while
                ratz(this.idlePause + 1000*(outofmemoryCycles++));
                idletime += System.currentTimeMillis() - timestamp;
            }
        }
        this.close();
        logSystem("thread '" + this.getName() + "' terminated.");
    }

    // ratzen: German for to sleep (coll.)
    private void ratz(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            if (log != null)
                log.config("thread '" + this.getName() + "' interrupted because of shutdown.");
        }
    }
    
    public void notifyThread() {
        if (this.syncObject != null) {
            synchronized (this.syncObject) {
                if (log != null)
                    if (log.isFine()) log.fine("thread '" + this.getName()
                            + "' has received a notification from thread '"
                            + Thread.currentThread().getName() + "'.");
                this.syncObject.notifyAll();
            }
        }
    }
    
    private void logSystem(final String text) {
        if (log == null) ConcurrentLog.config("THREAD-CONTROL", text);
        else log.config(text);
    }
}
