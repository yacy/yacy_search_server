// AbstractThread.java
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

/*
 an Implementation of a serverThread must only extend this class and implement
 the methods:
 open(),
 job() and
 close()
 */

package net.yacy.kelondro.workflow;

import java.nio.channels.ClosedByInterruptException;

import net.yacy.cora.util.ConcurrentLog;


public abstract class AbstractThread extends Thread implements WorkflowThread {

    private static ConcurrentLog log = new ConcurrentLog("AbstractThread");
    protected boolean running = true;
    private boolean announcedShutdown = false;
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

    @Override
    public final void setDescription(final String shortText, final String longText, final String monitorURL) {
        // sets a visible description string
        this.shortDescr = shortText;
        this.longDescr  = longText;
        this.monitorURL = monitorURL;
    }

    @Override
    public final String getShortDescription() {
        return this.shortDescr;
    }

    @Override
    public final String getLongDescription() {
        return this.longDescr;
    }

    @Override
    public String getMonitorURL() {
        return this.monitorURL;
    }

    @Override
    public final long getBlockTime() {
        // returns the total time that this thread has been blocked so far
        return this.blockPause;
    }

    @Override
    public final long getExecTime() {
        // returns the total time that this thread has worked so far
        return this.busytime;
    }

    @Override
    public long getMemoryUse() {
        // returns the sum of all memory usage differences before and after one busy job
        return this.memuse;
    }

    @Override
    public boolean shutdownInProgress() {
        return !this.running || this.announcedShutdown || Thread.currentThread().isInterrupted();
    }

    @Override
    public void terminate(final boolean waitFor) {
        // after calling this method, the thread shall terminate
        this.running = false;

        // interrupting the thread
        interrupt();

        // wait for termination
        if (waitFor) {
            try { this.join(3000); } catch (final InterruptedException e) { return; }
        }

        // If we reach this point, the process is closed
    }

    private final void logError(final String text,final Throwable thrown) {
        if (log == null) {
            ConcurrentLog.severe("THREAD-CONTROL", text, thrown);
        } else {
            log.severe(text,thrown);
        }
    }

    @Override
    public void jobExceptionHandler(final Exception e) {
        if (!(e instanceof ClosedByInterruptException)) {
            // default handler for job exceptions. shall be overridden for own handler
            logError("thread '" + getName() + "': " + e.toString(),e);
        }
    }

    @Override
    public void open() {} // dummy definition; should be overriden
    @Override
    public void close() {} // dummy definition; should be overriden

}
