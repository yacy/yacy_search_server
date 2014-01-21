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
    public void setStartupSleep(long milliseconds);

    /**
     * sets a sleep time for pauses between two jobs if the job returns false (idle)
     * @param milliseconds
     * @return
     */
    public long setIdleSleep(long milliseconds);

    /**
     * gets the sleep time for pauses between two jobs if the job returns false (idle)
     * @return milliseconds
     */
    public long getIdleSleep();
    
    /**
     * sets a sleep time for pauses between two jobs if the job returns true (busy)
     * @param milliseconds
     * @return
     */
    public long setBusySleep(long milliseconds);

    /**
     * gets the sleep time for pauses between two jobs if the job returns true (busy)
     * @return milliseconds
     */
    public long getBusySleep();
    
    /**
     * sets minimum required amount of memory for the job execution
     * @param freeBytes
     */
    public void setMemPreReqisite(long freeBytes);
    
    /**
     * sets maximimum load for the job execution
     * @param load
     */
    public double setLoadPreReqisite(final double load);
 
    /**
     * defines if the thread should obey the intermission command
     * @param obey
     */
    public void setObeyIntermission(boolean obey);
 
    /**
     * @return the total number of cycles of job execution with idle-result
     */
    public long getIdleCycles();
 
    /**
     * @return the total number of cycles of job execution with busy-result
     */
    public long getBusyCycles();
 
    /**
     * @return the total number of cycles where a job execution was omitted
     *         because of memory shortage
     */
    public long getOutOfMemoryCycles();
 
    /**
     * @return the total time that this thread has slept so far
     */
    public long getSleepTime();
 
    /**
     * the thread is forced to pause for a specific time
     * if the thread is busy meanwhile, the pause is ommitted
     * @param pause
     */
    public void intermission(long pause);
 
    /**
     * performes one job procedure; this loopes until terminate() is called
     * @return true if it has done something, false if it is idle and does not expect to work on more for a longer time
     */
    public boolean job() throws Exception;

    /**
     * is called when an outOfMemoryCycle is performed
     * this method should try to free some memory, so that the job can be executed
     */
    public void freemem();

}
