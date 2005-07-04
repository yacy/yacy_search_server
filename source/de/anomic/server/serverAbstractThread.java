// serverAbstractThread.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.yacy.net
// Frankfurt, Germany, 2005
// last major change: 14.03.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
 an Implementation of a serverRunnable must only extend this class and impement
 the methods:
 open(),
 job() and
 close()
 */

package de.anomic.server;

import java.nio.channels.ClosedByInterruptException;

import de.anomic.server.logging.serverLog;

public abstract class serverAbstractThread extends Thread implements serverThread {

    private long startup = 0, idlePause = 0, busyPause = 0, blockPause = 0;
    private boolean running = true;
    private serverLog log = null;
    private long idletime = 0, busytime = 0, memprereq = 0;
    private String shortDescr = "", longDescr = "";
    private long threadBlockTimestamp = System.currentTimeMillis();
    private long idleCycles = 0, busyCycles = 0, outofmemoryCycles = 0;
    private Object syncObject = null;
    
    protected final void announceThreadBlockApply() {
        // shall only be used, if a thread blocks for an important reason
        // like a socket connect and must renew the timestamp to correct
        // statistics
        this.threadBlockTimestamp = System.currentTimeMillis();
    }
    
    protected final void announceThreadBlockRelease() {
        // shall only be used, if a thread blocks for an important reason
        // like a socket connect and must renew the timestamp to correct
        // statistics
        long thisBlockTime = (System.currentTimeMillis() - this.threadBlockTimestamp);
        this.blockPause += thisBlockTime;
        this.busytime -= thisBlockTime;
    }
    
    protected final void announceMoreExecTime(long millis) {
        this.busytime += millis;
    }
    
    protected final void announceMoreSleepTime(long millis) {
        this.idletime += millis;
    }
        
    public final void setDescription(String shortText, String longText) {
        // sets a visible description string
        this.shortDescr = shortText;
        this.longDescr  = longText;
    }
    
    public final void setStartupSleep(long milliseconds) {
        // sets a sleep time before execution of the job-loop
        startup = milliseconds;
    }
    
    public final void setIdleSleep(long milliseconds) {
        // sets a sleep time for pauses between two jobs
        idlePause = milliseconds;
    }
    
    public final void setBusySleep(long milliseconds) {
        // sets a sleep time for pauses between two jobs
        busyPause = milliseconds;
    }
    
    public void setMemPreReqisite(long freeBytes) {
        // sets minimum required amount of memory for the job execution
        memprereq = freeBytes;
    }
    
    public final String getShortDescription() {
        return this.shortDescr;
    }
    
    public final String getLongDescription() {
        return this.longDescr;
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
    
    public final long getBlockTime() {
        // returns the total time that this thread has been blocked so far
        return this.blockPause;
    }
    
    public final long getSleepTime() {
        // returns the total time that this thread has slept so far
        return this.idletime;
    }
    
    public final long getExecTime() {
        // returns the total time that this thread has worked so far
        return this.busytime;
    }
    
    public final void setLog(serverLog log) {
        // defines a log where process states can be written to
        this.log = log;
    }

    public void terminate(boolean waitFor) {
        // after calling this method, the thread shall terminate
        this.running = false;
        
        // interrupting the thread
        this.interrupt();
        
        // wait for termination
        if (waitFor) {
            // Busy waiting removed: while (this.isAlive()) try {this.sleep(100);} catch (InterruptedException e) {break;}
            try { this.join(3000); } catch (InterruptedException e) {return;}
        }
            
        // If we reach this point, the process is closed
    }
    
    private final void logError(String text) {
        if (log == null) serverLog.logError("THREAD-CONTROL", text);
        else log.logError(text);
    }    
    
    private final void logError(String text,Throwable thrown) {
        if (log == null) serverLog.logError("THREAD-CONTROL", text, thrown);
        else log.logError(text,thrown);
    }
    
    private void logSystem(String text) {
        if (log == null) serverLog.logSystem("THREAD-CONTROL", text);
        else log.logSystem(text);
    }
    
    private void logSystem(String text, Throwable thrown) {
        if (log == null) serverLog.logSystem("THREAD-CONTROL", text, thrown);
        else log.logSystem(text,thrown);
    }    
    
    public void jobExceptionHandler(Exception e) {
        if (!(e instanceof ClosedByInterruptException)) {
            // default handler for job exceptions. shall be overridden for own handler
            logError("thread '" + this.getName() + "': " + e.toString(),e);
        }
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
        int outerloop;
        long innerpause;
        long timestamp;
        boolean isBusy;
        Runtime rt = Runtime.getRuntime();
                
        while (running) {
            if (rt.freeMemory() > memprereq) try {
                // do job
                timestamp = System.currentTimeMillis();
                isBusy = this.job();
                busytime += (isBusy) ? System.currentTimeMillis() - timestamp : 0;
                // interrupt loop if this is supposed to be a one-time job
                if ((this.idlePause < 0) || (this.busyPause < 0)) break; // for one-time jobs
                // process scheduled pause
                timestamp = System.currentTimeMillis();
                ratz((isBusy) ? this.busyPause : this.idlePause);
                idletime += System.currentTimeMillis() - timestamp;
                if (isBusy) busyCycles++; else idleCycles++;
            } catch (Exception e) {
                // handle exceptions: thread must not die on any unexpected exceptions
                // if the exception is too bad it should call terminate()
                this.jobExceptionHandler(e);
                busyCycles++;
            } else {
                // omit job, not enough memory
                // process scheduled pause
                timestamp = System.currentTimeMillis();
                ratz(this.idlePause);
                idletime += System.currentTimeMillis() - timestamp;
                outofmemoryCycles++;
                if (rt.freeMemory() <= memprereq) System.gc(); // give next loop a chance
            }
        }
        this.close();
        logSystem("thread '" + this.getName() + "' terminated.");
    }
    
    private void ratz(long millis) {
//        int loop = 1;
//        while (millis > 1000) {
//            loop = loop * 2;
//            millis = millis / 2;
//        }
//        while ((loop-- > 0) && (running)) {
//            try {this.sleep(millis);} catch (InterruptedException e) {}
//        }
        try {
            if (this.syncObject != null) {
                synchronized(this.syncObject) {
                    this.syncObject.wait(millis);
                }
            } else {
                Thread.sleep(millis);
            }
        } catch (InterruptedException e) {
            if (this.log != null) this.log.logSystem("thread '" + this.getName() + "' interrupted because of shutdown.");
        }
    }
    
    public void open() {} // dummy definition; should be overriden
    public void close() {} // dummy definition; should be overriden
    
    public void setSyncObject(Object sync) {
        this.syncObject = sync;
    }
    
    public Object getSyncObject() {
        return this.syncObject;
    }
    
    public void notifyThread() {
        if (this.syncObject != null) {
            synchronized(this.syncObject) {
                if (this.log != null) this.log.logDebug("thread '" + this.getName() + "' has received a notification from thead '" + Thread.currentThread().getName() + "'.");
                this.syncObject.notifyAll();
            }
        }            
    }
}