// serverAbstractBusyThread.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.03.2005 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

package de.anomic.server;

import de.anomic.server.logging.serverLog;

public abstract class serverAbstractBusyThread extends serverAbstractThread implements serverBusyThread {

    private long startup = 0, intermission = 0, idlePause = 0, busyPause = 0;
    private long idletime = 0, memprereq = 0;
    private long idleCycles = 0, busyCycles = 0, outofmemoryCycles = 0;
    private boolean intermissionObedient = true;
    
    protected final void announceMoreSleepTime(long millis) {
        this.idletime += millis;
    }

    public final void setStartupSleep(long milliseconds) {
        // sets a sleep time before execution of the job-loop
        startup = milliseconds;
    }
    
    public final long setIdleSleep(long milliseconds) {
        // sets a sleep time for pauses between two jobs
        idlePause = milliseconds;
        return milliseconds;
    }
    
    public final long setBusySleep(long milliseconds) {
        // sets a sleep time for pauses between two jobs
        busyPause = milliseconds;
        return milliseconds;
    }
    
    public void setMemPreReqisite(long freeBytes) {
        // sets minimum required amount of memory for the job execution
        memprereq = freeBytes;
    }
    
    public void setObeyIntermission(boolean obey) {
        // defines if the thread should obey the intermission command
        intermissionObedient = obey;
    }
    
    public final long getIdleCycles() {
        // returns the total number of cycles of job execution with idle-result
        return this.idleCycles;
    }
    
    public final long getBusyCycles() {
        // returns the total number of cycles of job execution with busy-result
        return this.busyCycles;
    }

    public long getOutOfMemoryCycles() {
        // returns the total number of cycles where
        // a job execution was omitted because of memory shortage
        return this.outofmemoryCycles;
    }
    
    public final long getSleepTime() {
        // returns the total time that this thread has slept so far
        return this.idletime;
    }
    
    public void intermission(long pause) {
        if (pause == Long.MAX_VALUE)
            this.intermission = Long.MAX_VALUE;
        else
            this.intermission = System.currentTimeMillis() + pause;
    }


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
            } else if (serverMemory.request(memprereq, false)) try {
                // do job
                timestamp = System.currentTimeMillis();
                memstamp0 = serverMemory.used();
                isBusy = this.job();
                // do memory and busy/idle-count/time monitoring
                if (isBusy) {
                    memstamp1 = serverMemory.used();
                    if (memstamp1 >= memstamp0) {
                        // no GC in between. this is not shure but most probable
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
            } catch (Exception e) {
                // handle exceptions: thread must not die on any unexpected exceptions
                // if the exception is too bad it should call terminate()
                this.jobExceptionHandler(e);
                busyCycles++;
            } else {
                log.logWarning("Thread '" + this.getName() + "' runs short memory cycle. Free mem: " +
                        (serverMemory.available() / 1024) + " KB, needed: " + (memprereq / 1024) + " KB");
                // omit job, not enough memory
                // process scheduled pause
                timestamp = System.currentTimeMillis();
                // do a clean-up
                this.freemem();
                // sleep a while
                ratz(this.idlePause);
                idletime += System.currentTimeMillis() - timestamp;
                outofmemoryCycles++;
            }
        }
        this.close();
        logSystem("thread '" + this.getName() + "' terminated.");
    }
    
    private void ratz(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            if (this.log != null) this.log.logConfig("thread '" + this.getName() + "' interrupted because of shutdown.");
        }
    }
    
    private void logSystem(String text) {
        if (log == null) serverLog.logConfig("THREAD-CONTROL", text);
        else log.logConfig(text);
    }
}
