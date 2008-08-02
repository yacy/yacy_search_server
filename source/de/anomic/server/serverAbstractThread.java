// serverAbstractThread.java
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

/*
 an Implementation of a serverThread must only extend this class and implement
 the methods:
 open(),
 job() and
 close()
 */

package de.anomic.server;

import java.nio.channels.ClosedByInterruptException;

import de.anomic.server.logging.serverLog;

public abstract class serverAbstractThread extends Thread implements serverThread {

    protected boolean running = true;
    protected serverLog log = null;
    protected long busytime = 0, memuse = 0;
    private   long blockPause = 0;
    private   String shortDescr = "", longDescr = "";
    private   String monitorURL = null;
    private   long threadBlockTimestamp = System.currentTimeMillis();
    
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
        final long thisBlockTime = (System.currentTimeMillis() - this.threadBlockTimestamp);
        this.blockPause += thisBlockTime;
        this.busytime -= thisBlockTime;
    }
    
    protected final void announceMoreExecTime(final long millis) {
        this.busytime += millis;
    }
    
    public final void setDescription(final String shortText, final String longText, final String monitorURL) {
        // sets a visible description string
        this.shortDescr = shortText;
        this.longDescr  = longText;
        this.monitorURL = monitorURL;
    }
    
    public final String getShortDescription() {
        return this.shortDescr;
    }
    
    public final String getLongDescription() {
        return this.longDescr;
    }
    
    public String getMonitorURL() {
        return this.monitorURL;
    }
    
    public final long getBlockTime() {
        // returns the total time that this thread has been blocked so far
        return this.blockPause;
    }
    
    public final long getExecTime() {
        // returns the total time that this thread has worked so far
        return this.busytime;
    }
    
    public long getMemoryUse() {
        // returns the sum of all memory usage differences before and after one busy job
        return memuse;
    }

    public final void setLog(final serverLog log) {
        // defines a log where process states can be written to
        this.log = log;
    }

    
    public boolean shutdownInProgress() {
        return !this.running || Thread.currentThread().isInterrupted();
    }    
    
    public void terminate(final boolean waitFor) {
        // after calling this method, the thread shall terminate
        this.running = false;
        
        // interrupting the thread
        this.interrupt();
        
        // wait for termination
        if (waitFor) {
            // Busy waiting removed: while (this.isAlive()) try {this.sleep(100);} catch (InterruptedException e) {break;}
            try { this.join(3000); } catch (final InterruptedException e) {return;}
        }
            
        // If we reach this point, the process is closed
    }
    
    private final void logError(final String text,final Throwable thrown) {
        if (log == null) serverLog.logSevere("THREAD-CONTROL", text, thrown);
        else log.logSevere(text,thrown);
    }
    
    public void jobExceptionHandler(final Exception e) {
        if (!(e instanceof ClosedByInterruptException)) {
            // default handler for job exceptions. shall be overridden for own handler
            logError("thread '" + this.getName() + "': " + e.toString(),e);
        }
    }
    
    public void open() {} // dummy definition; should be overriden
    public void close() {} // dummy definition; should be overriden
    
}
