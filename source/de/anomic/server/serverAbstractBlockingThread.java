// serverAbstractBlockingThread.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.03.2008 on http://yacy.net
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

import java.util.concurrent.BlockingQueue;

import de.anomic.server.logging.serverLog;

public abstract class serverAbstractBlockingThread<I, O> extends serverAbstractThread implements serverBlockingThread<I, O> {

    private BlockingQueue<I> input = null;
    private BlockingQueue<O> output = null;

    public void setInputQueue(BlockingQueue<I> queue) {
        this.input = queue;
    }
    public void setOutputQueue(BlockingQueue<O> queue) {
        this.output = queue;
    }
    public BlockingQueue<I> getInputQueue() {
        return this.input;
    }
    public BlockingQueue<O> getOutputQueue() {
        return this.output;
    }

    public void run() {
        this.open();
        if (log != null) {
            logSystem("thread '" + this.getName() + "' deployed, starting loop.");
        }
        long timestamp;
        long memstamp0, memstamp1;
        long busyCycles = 0;
        
        while (running) {
            try {
                // do job
                timestamp = System.currentTimeMillis();
                memstamp0 = serverMemory.used();
                O out = this.job(this.input.take());
                if (out != null) this.output.add(out);
                // do memory and busy/idle-count/time monitoring
                memstamp1 = serverMemory.used();
                if (memstamp1 >= memstamp0) {
                    // no GC in between. this is not shure but most probable
                    memuse += memstamp1 - memstamp0;
                } else {
                    // GC was obviously in between. Add an average as simple heuristic
                    if (busyCycles > 0) memuse += memuse / busyCycles;
                }
                busytime += System.currentTimeMillis() - timestamp;
            } catch (Exception e) {
                // handle exceptions: thread must not die on any unexpected exceptions
                // if the exception is too bad it should call terminate()
                this.jobExceptionHandler(e);
            } finally {
                busyCycles++;
            }
        }
        this.close();
        logSystem("thread '" + this.getName() + "' terminated.");
    }
    
    private void logSystem(String text) {
        if (log == null) serverLog.logConfig("THREAD-CONTROL", text);
        else log.logConfig(text);
    }
}
