// serverBusyThread.java
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

public interface serverBusyThread extends serverThread {

    public void setStartupSleep(long milliseconds);
    // sets a sleep time before execution of the job-loop

    public long setIdleSleep(long milliseconds);
    // sets a sleep time for pauses between two jobs if the job returns false (idle)

    public long setBusySleep(long milliseconds);
    // sets a sleep time for pauses between two jobs if the job returns true (busy)
 
    public void setMemPreReqisite(long freeBytes);
    // sets minimum required amount of memory for the job execution
 
    public void setObeyIntermission(boolean obey);
    // defines if the thread should obey the intermission command
 
    public long getIdleCycles();
    // returns the total number of cycles of job execution with idle-result
 
    public long getBusyCycles();
    // returns the total number of cycles of job execution with busy-result
 
    public long getOutOfMemoryCycles();
    // returns the total number of cycles where
    // a job execution was omitted because of memory shortage
 
    public long getSleepTime();
    // returns the total time that this thread has slept so far
 
    public void intermission(long pause);
    // the thread is forced to pause for a specific time
    // if the thread is busy meanwhile, the pause is ommitted
 
    public boolean job() throws Exception;
    // performes one job procedure; this loopes until terminate() is called
    // job returns true if it has done something
    // it returns false if it is idle and does not expect to work on more for a longer time

    public void freemem();
    // is called when an outOfMemoryCycle is performed
    // this method should try to free some memory, so that the job can be executed

    public void notifyThread();
}
