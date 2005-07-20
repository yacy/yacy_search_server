// serverThread.java 
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

package de.anomic.server;

import de.anomic.server.logging.serverLog;

public interface serverThread {
    
    // -------------------------------------------------------
    // methods inherited from Thread; needed for compatibility
    public void start();
    public boolean isAlive();
    
    // --------------------------------------------------------------------------
    // these method are implemented by serverThread and do not need to be altered
    // this includes also the run()-Method
    
    public void setDescription(String shortText, String longText, String monitorURL);
    // sets a visible description string
    
    public void setStartupSleep(long milliseconds);
    // sets a sleep time before execution of the job-loop

    public void setIdleSleep(long milliseconds);
    // sets a sleep time for pauses between two jobs if the job returns false (idle)

    public void setBusySleep(long milliseconds);
    // sets a sleep time for pauses between two jobs if the job returns true (busy)
    
    public void setMemPreReqisite(long freeBytes);
    // sets minimum required amount of memory for the job execution
    
    public String getShortDescription();
    // returns short description string for online display
    
    public String getLongDescription();
    // returns long description string for online display
    
    public String getMonitorURL();
    // returns an URL that can be used to monitor the thread and it's queue
    
    public long getIdleCycles();
    // returns the total number of cycles of job execution with idle-result
    
    public long getBusyCycles();
    // returns the total number of cycles of job execution with busy-result
    
    public long getOutOfMemoryCycles();
    // returns the total number of cycles where
    // a job execution was omitted because of memory shortage
    
    public long getBlockTime();
    // returns the total time that this thread has been blocked so far
    
    public long getSleepTime();
    // returns the total time that this thread has slept so far
    
    public long getExecTime();
    // returns the total time that this thread has worked so far
    
    public void setLog(serverLog log);
    // defines a log where process states can be written to

    public void jobExceptionHandler(Exception e);
    // handles any action necessary during job execution
    
    public void terminate(boolean waitFor);
    // after calling this method, the thread shall terminate
    // if waitFor is true, the method waits until the process has died
    
    // ---------------------------------------------------------------------
    // the following methods are supposed to be implemented by customization
    
    public void open();
    // this is called right befor the job queue is started
    
    public boolean job() throws Exception;
    // performes one job procedure; this loopes until terminate() is called
    // job returns true if it has done something
    // it returns false if it is idle and does not expect to work on more for a longer time
    
    public int getJobCount();
    // returns how many jobs are in the queue
    // can be used to calculate a busy-state
    
    public void close();
    // jobs that need to be done after termination
    // terminate must be called before
    
    
    public void setSyncObject(Object sync);    
    public Object getSyncObject();    
    public void notifyThread();
    
}
