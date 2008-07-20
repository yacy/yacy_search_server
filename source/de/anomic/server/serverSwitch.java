// serverSwitch.java
// -------------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
  this is an interface for possible switchboard implementations
  Its purpose is to provide a mechanism which cgi pages can use
  to influence the behavior of a concurrntly running application
*/

package de.anomic.server;

import java.io.File;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.server.logging.serverLog;

public interface serverSwitch<E> {

    // the root path for the application
    public File getRootPath();

    // a logger for this switchboard
    public void setLog(serverLog log);
    public serverLog getLog();

    // access tracker
    public void track(String host, String accessPath); // learn that a specific host has accessed a specific path
    public TreeMap<Long, String> accessTrack(String host); // returns mapping from Long(accesstime) to path
    public Iterator<String> accessHosts(); // returns an iterator of hosts in tracker (String)
        
    // a switchboard can have action listener
    // these listeners are hooks for numerous methods below
    public void deployAction(String actionName,
                             String actionShortDescription,
                             String actionLongDescription,
                             serverSwitchAction action); 
    public void undeployAction(String actionName); 

    // the switchboard can manage worker threads
    public void deployThread(String threadName,
                             String threadShortDescription,
                             String threadLongDescription,
                             String threadMonitorURL,
                             serverBusyThread newThread,
                             long startupDelay,
                             long initialIdleSleep, long initialBusySleep,
                             long initialMemoryPreRequisite);
    public serverBusyThread getThread(String threadName);
    public void setThreadPerformance(String threadName, long idleMillis, long busyMillis, long memprereq);
    public void terminateThread(String threadName, boolean waitFor);
    public void intermissionAllThreads(long pause);
    public void terminateAllThreads(boolean waitFor);

    public Iterator<String> /*of serverThread-Names (String)*/ threadNames();

    // the switchboard can be used to set and read properties
    public void setConfig(Map<String, String> otherConfigs);
    public void setConfig(String key, long value);
    public void setConfig(String key, String value);
    public String getConfig(String key, String dflt);
    public long getConfigLong(String key, long dflt);
    public boolean getConfigBool(String key, boolean dflt);
    public File getConfigPath(String key, String dflt);
    public void removeConfig(String key);
    public Iterator<String> configKeys();
    public Map<String, String> getRemoved();
        
    // the switchboard also shall maintain a job list
    // jobs can be queued by submitting a job object
    // to work off a queue job, use deQueue, which is meant to
    // work off exactly only one job, not all
    public int queueSize();
    public E queuePeek(); // looks for next element in the queue, returns but not deletes it if there is one; returns null othervise
    public void enQueue(E job);
    public E deQueue() throws InterruptedException; // returns a FIFO entry from the stack, blocks until one element is available

    // authentification routines: sets and reads access attributes according to host addresses
    public void    setAuthentify(InetAddress host, String user, String rigth);
    public void    removeAuthentify(InetAddress host);
    public String  getAuthentifyUser(InetAddress host);
    public String  getAuthentifyRights(InetAddress host);
    public void    addAuthentifyRight(InetAddress host, String right);
    public boolean hasAuthentifyRight(InetAddress host, String right);

    // ask the switchboard to perform an action
    // the result is a properties structure with the result of the action
    // The actionName selects an action
    // the actionInput is an input for the selected action
    public serverObjects action(String actionName, serverObjects actionInput);

    // performance control: the server can announce busy and idle status to the switchboard
    // these announcements can be used to trigger events or interrupts
    public void handleBusyState(int jobs);

}
