// WorkflowThread.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 13.03.2005 on http://yacy.net
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

public interface WorkflowThread {
    
    // -------------------------------------------------------
    // methods inherited from Thread; needed for compatibility
    public void start();
    public boolean isAlive();
    
    // --------------------------------------------------------------------------
    // these method are implemented by serverThread and do not need to be altered
    // this includes also the run()-Method
    
    public void setDescription(String shortText, String longText, String monitorURL);
    // sets a visible description string
    
    public String getShortDescription();
    // returns short description string for online display
    
    public String getLongDescription();
    // returns long description string for online display
    
    public String getMonitorURL();
    // returns an URL that can be used to monitor the thread and it's queue
    
    public long getBlockTime();
    // returns the total time that this thread has been blocked so far
    
    public long getExecTime();
    // returns the total time that this thread has worked so far
    
    public long getMemoryUse();
    // returns the sum of all memory usage differences before and after one busy job
    
    public void jobExceptionHandler(Exception e);
    // handles any action necessary during job execution
    
    public boolean shutdownInProgress();
    
    public void terminate(boolean waitFor);
    // after calling this method, the thread shall terminate
    // if waitFor is true, the method waits until the process has died
    
    // ---------------------------------------------------------------------
    // the following methods are supposed to be implemented by customization
    
    public void open();
    // this is called right before the job queue is started
    
    public int getJobCount();
    // returns how many jobs are in the queue
    // can be used to calculate a busy-state
    
    public void close();
    // jobs that need to be done after termination
    // terminate must be called before
}
