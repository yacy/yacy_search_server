// serverAbstractSwitch.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
// last major change: 24.03.2005
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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import de.anomic.server.logging.serverLog;

public abstract class serverAbstractSwitch implements serverSwitch {

    // configuration management
    private final File      configFile;
    private Map             configProps;
    private final String    configComment;
    private final HashMap   authorization;
    private String          rootPath;
    private final TreeMap   workerThreads;
    private final TreeMap   switchActions;
    protected serverLog     log;
    protected int           serverJobs;
    
    public serverAbstractSwitch(String rootPath, String initPath, String configPath) throws IOException {
	// we initialize the switchboard with a property file,
	// but maintain these properties then later in a new 'config' file
	// to reset all changed configs, the config file must
	// be deleted, but not the init file
	// the only attribute that will always be read from the init is the
	// file name of the config file
        this.rootPath = rootPath;
	configComment = "This is an automatically generated file, updated by serverAbstractSwitch and initialized by " + initPath;
	File initFile = new File(rootPath, initPath);
	configFile = new File(rootPath, configPath); //propertiesFile(config);
	new File(configFile.getParent()).mkdir();

	// predefine init's
	Map initProps;
	if (initFile.exists()) initProps = serverFileUtils.loadHashMap(initFile); else initProps = new HashMap();

	// load config's from last save
	if (configFile.exists()) configProps = serverFileUtils.loadHashMap(configFile); else configProps = new HashMap();
	
	synchronized (configProps) {

	    // remove all values from config that do not appear in init (out-dated settings)
	    Iterator i = configProps.keySet().iterator();
	    String key;
	    while (i.hasNext()) {
		key = (String) i.next();
		if (!(initProps.containsKey(key))) i.remove();
	    }

	    // merge new props from init to config
	    // this is necessary for migration, when new properties are attached
	    initProps.putAll(configProps);
	    configProps = initProps;
	    
	    // save result; this may initially create a config file after initialization
	    saveConfig();
	}

	// other settings
	authorization = new HashMap();
        
        // init thread control
        workerThreads = new TreeMap();
        
        // init switch actions
	switchActions = new TreeMap();

        // init busy state control
        serverJobs = 0;
    }

    // a logger for this switchboard
    public void setLog(serverLog log) {
	this.log = log;
    }

    public serverLog getLog() {
	return log;
    }

    public void setConfig(String key, long value) {
        setConfig(key, Long.toString(value));
    }

    public void setConfig(String key, String value) {
	// perform action before setting new value
	Map.Entry entry;
	serverSwitchAction action;
	Iterator i = switchActions.entrySet().iterator();
	while (i.hasNext()) {
	    entry = (Map.Entry) i.next();
	    action = (serverSwitchAction) entry.getValue();
	    try {
		action.doBevoreSetConfig(key, value);
	    } catch (Exception e) {
		log.logSevere("serverAction bevoreSetConfig '" + action.getShortDescription() + "' failed with exception: " + e.getMessage());
	    }
	}

	// set the value
	Object oldValue = configProps.put(key, value);
	saveConfig();

	// perform actions afterwards
	i = switchActions.entrySet().iterator();
	while (i.hasNext()) {
	    entry = (Map.Entry) i.next();
	    action = (serverSwitchAction) entry.getValue();
	    try {
		action.doAfterSetConfig(key, value, (oldValue==null)?null:(String)oldValue);
	    } catch (Exception e) {
		log.logSevere("serverAction afterSetConfig '" + action.getShortDescription() + "' failed with exception: " + e.getMessage());
	    }
	}
    }

    public String getConfig(String key, String dflt) {
	// get the value
	Object s = configProps.get(key);

	// do action
	Map.Entry entry;
	serverSwitchAction action;
	Iterator i = switchActions.entrySet().iterator();
	while (i.hasNext()) {
	    entry = (Map.Entry) i.next();
	    action = (serverSwitchAction) entry.getValue();
	    try {
		action.doWhenGetConfig(key, (s==null)?null:(String)s, dflt);
	    } catch (Exception e) {
		log.logSevere("serverAction whenGetConfig '" + action.getShortDescription() + "' failed with exception: " + e.getMessage());
	    }
	}

	// return value
	if (s == null) return dflt; else return (String)s;
    }
    
    public long getConfigLong(String key, long dflt) {
        try {
            return Long.parseLong(getConfig(key, Long.toString(dflt)));
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    public Iterator configKeys() {
	return configProps.keySet().iterator();
    }

    private void saveConfig() {
	try {
	    serverFileUtils.saveMap(configFile, configProps, configComment);
	} catch (IOException e) {
	    System.out.println("ERROR: cannot write config file " + configFile.toString() + ": " + e.getMessage());
	}
    }


    // add/remove action listener
    public void deployAction(String actionName, String actionShortDescription, String actionLongDescription,
			     serverSwitchAction newAction) {
        newAction.setLog(log);
        newAction.setDescription(actionShortDescription, actionLongDescription);
        switchActions.put(actionName, newAction);
	log.logInfo("Deployed Action '" + actionShortDescription + "', (" + switchActions.size() + " actions registered)");
    }

    public void undeployAction(String actionName) {
	serverSwitchAction action = (serverSwitchAction) switchActions.get(actionName);
	action.close();
	switchActions.remove(actionName);
	log.logInfo("Undeployed Action '" + action.getShortDescription() + "', (" + switchActions.size() + " actions registered)");
    }

    public void deployThread(
            String threadName,
            String threadShortDescription,
            String threadLongDescription,
            String threadMonitorURL,
            serverThread newThread,
            long startupDelay) {
        deployThread(threadName, threadShortDescription, threadLongDescription, threadMonitorURL,
                     newThread, startupDelay,
                     Long.parseLong(getConfig(threadName + "_idlesleep" ,     "100")), 
                     Long.parseLong(getConfig(threadName + "_busysleep" ,    "1000")),
                     Long.parseLong(getConfig(threadName + "_memprereq" , "1000000")));
    }

    public void deployThread(
            String threadName,
            String threadShortDescription,
            String threadLongDescription,
            String threadMonitorURL,
            serverThread newThread,
            long startupDelay,
            long initialIdleSleep,
            long initialBusySleep,
            long initialMemoryPreRequisite) {
        if (newThread.isAlive()) throw new RuntimeException("undeployed threads must not live; they are started as part of the deployment");
        newThread.setStartupSleep(startupDelay);
        long x;
        try {
            x = Long.parseLong(getConfig(threadName + "_idlesleep" , "novalue"));
            newThread.setIdleSleep(x);
        } catch (NumberFormatException e) {
            newThread.setIdleSleep(initialIdleSleep);
            setConfig(threadName + "_idlesleep", initialIdleSleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_busysleep" , "novalue"));
            newThread.setBusySleep(x);
        } catch (NumberFormatException e) {
            newThread.setBusySleep(initialBusySleep);
            setConfig(threadName + "_busysleep", initialBusySleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_memprereq" , "novalue"));
            newThread.setMemPreReqisite(x);
        } catch (NumberFormatException e) {
            newThread.setMemPreReqisite(initialMemoryPreRequisite);
            setConfig(threadName + "_memprereq", initialMemoryPreRequisite);
        }
        newThread.setLog(log);
        newThread.setDescription(threadShortDescription, threadLongDescription, threadMonitorURL);
        workerThreads.put(threadName, newThread);
        // start the thread
        if (workerThreads.containsKey(threadName)) newThread.start();
    }

    public serverThread getThread(String threadName) {
        return (serverThread) workerThreads.get(threadName);
    }
    
    public void setThreadPerformance(String threadName, long idleMillis, long busyMillis, long memprereqBytes) {
        serverThread thread = (serverThread) workerThreads.get(threadName);
        if (thread != null) {
            thread.setIdleSleep(idleMillis);
            thread.setBusySleep(busyMillis);
            thread.setMemPreReqisite(memprereqBytes);
        }
    }
    
    public synchronized void terminateThread(String threadName, boolean waitFor) {
        if (workerThreads.containsKey(threadName)) {
            ((serverThread) workerThreads.get(threadName)).terminate(waitFor);
            workerThreads.remove(threadName);
        }
    }

    public void intermissionAllThreads(long pause) {
        Iterator e = workerThreads.keySet().iterator();
        while (e.hasNext()) {
            ((serverThread) workerThreads.get((String) e.next())).intermission(pause);
        }
    }
    
    public synchronized void terminateAllThreads(boolean waitFor) {
        Iterator e = workerThreads.keySet().iterator();
        while (e.hasNext()) {
            ((serverThread) workerThreads.get((String) e.next())).terminate(false);
        }
        if (waitFor) {
            e = workerThreads.keySet().iterator();
            while (e.hasNext()) {
                ((serverThread) workerThreads.get((String) e.next())).terminate(true);
                e.remove();
            }
        }
    }
    
    public Iterator /*of serverThread-Names (String)*/ threadNames() {
        return workerThreads.keySet().iterator();
    }
    
    abstract public int queueSize();
    abstract public void enQueue(Object job);
    abstract public boolean deQueue();


    // authentification routines:
    
    public void setAuthentify(InetAddress host, String user, String rights) {
	// sets access attributes according to host addresses
	authorization.put(host, user + "@" + rights);
    }

    public void removeAuthentify(InetAddress host) {
	// remove access attributes according to host addresses
	authorization.remove(host);
    }

    public String getAuthentifyUser(InetAddress host) {
	// read user name according to host addresses
	String a = (String) authorization.get(host);
	if (a == null) return null;
	int p = a.indexOf("@");
	if (p < 0) return null;
	return a.substring(0, p);
    }

    public String getAuthentifyRights(InetAddress host) {
	// read access rigths according to host addresses
	String a = (String) authorization.get(host);
	if (a == null) return null;
	int p = a.indexOf("@");
	if (p < 0) return null;
	return a.substring(p + 1);
    }

    public void addAuthentifyRight(InetAddress host, String right) {
	String rights = getAuthentifyRights(host);
	if (rights == null) {
	    // create new authentification
	    setAuthentify(host, "unknown", right);
	} else {
	    // add more authentification
	    String user = getAuthentifyUser(host);
	    setAuthentify(host, user, rights + right);
	}	
    }

    public boolean hasAuthentifyRight(InetAddress host, String right) {
	String rights = getAuthentifyRights(host);
	if (rights == null) return false;
	return rights.indexOf(right) >= 0;
    }

    public abstract serverObjects action(String actionName, serverObjects actionInput);

    public String getRootPath() {
       return rootPath;
    }
    
    public String toString() {
	return configProps.toString();
    }

    public void handleBusyState(int jobs) {
        serverJobs = jobs;
    }
}
