// BusyThread.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.03.2008 on http://yacy.net
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


public interface BusyThread extends WorkflowThread {

    /**
     * sets a sleep time before execution of the job-loop
     * @param milliseconds
     */
    void setStartupSleep(long milliseconds);

    /**
     * sets a sleep time for pauses between two jobs if the job returns false (idle)
     * @param milliseconds
     * @return
     */
    long setIdleSleep(long milliseconds);

    /**
     * gets the sleep time for pauses between two jobs if the job returns false (idle)
     * @return milliseconds
     */
    long getIdleSleep();
    
    /**
     * sets a sleep time for pauses between two jobs if the job returns true (busy)
     * @param milliseconds
     * @return
     */
    long setBusySleep(long milliseconds);

    /**
     * gets the sleep time for pauses between two jobs if the job returns true (busy)
     * @return milliseconds
     */
    long getBusySleep();
    
    /**
     * sets minimum required amount of memory for the job execution
     * @param freeBytes
     */
    void setMemPreReqisite(long freeBytes);
    
    /**
     * sets maximimum load for the job execution
     * @param load
     */
    double setLoadPreReqisite(final double load);
 
    /**
     * defines if the thread should obey the intermission command
     * @param obey
     */
    void setObeyIntermission(boolean obey);
 
    /**
     * @return the total number of cycles of job execution with idle-result
     */
    long getIdleCycles();
 
    /**
     * @return the total number of cycles of job execution with busy-result
     */
    long getBusyCycles();
 
    /**
     * @return the total number of cycles where a job execution was omitted
     *         because of memory shortage
     */
    long getOutOfMemoryCycles();

    /**
     * @return the total number of cycles where a job execution was omitted
     *         because of too high CPU load
     */
    long getHighCPUCycles();
    
    /**
     * @return the total time that this thread has slept so far
     */
    long getSleepTime();
 
    /**
     * the thread is forced to pause for a specific time
     * if the thread is busy meanwhile, the pause is ommitted
     * @param pause
     */
    void intermission(long pause);
 
    /**
     * performes one job procedure; this loopes until terminate() is called
     * @return true if it has done something, false if it is idle and does not expect to work on more for a longer time
     */
    boolean job() throws Exception;

    /**
     * is called when an outOfMemoryCycle is performed
     * this method should try to free some memory, so that the job can be executed
     */
    void freemem();

}
