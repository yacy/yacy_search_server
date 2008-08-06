// serverAbstractSwitch.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import de.anomic.server.logging.serverLog;

public abstract class serverAbstractSwitch<E> implements serverSwitch<E> {
    
    private static final long maxTrackingTimeDefault = 1000 * 60 * 60; // store only access data from the last hour to save ram space
    
    // configuration management
    private final   File      configFile;
    private final   String    configComment;
    private final   File      rootPath;
    protected serverLog log;
    protected int       serverJobs;
    protected long      maxTrackingTime;
    private   Map<String, String>                    configProps;
    private final   Map<String, String>                    configRemoved;
    private final   HashMap<InetAddress, String>           authorization;
    private final   TreeMap<String, serverBusyThread>      workerThreads;
    private final   TreeMap<String, serverSwitchAction>    switchActions;
    protected ConcurrentHashMap<String, TreeMap<Long, String>> accessTracker; // mappings from requesting host to an ArrayList of serverTrack-entries
    private final   LinkedBlockingQueue<E> cacheStack;
    
    public serverAbstractSwitch(final File rootPath, final String initPath, final String configPath, final boolean applyPro) {
        // we initialize the switchboard with a property file,
        // but maintain these properties then later in a new 'config' file
        // to reset all changed configs, the config file must
        // be deleted, but not the init file
        // the only attribute that will always be read from the init is the
        // file name of the config file
        this.cacheStack = new LinkedBlockingQueue<E>();
    	this.rootPath = rootPath;
    	this.configComment = "This is an automatically generated file, updated by serverAbstractSwitch and initialized by " + initPath;
        final File initFile = new File(rootPath, initPath);
        this.configFile = new File(rootPath, configPath); // propertiesFile(config);
        new File(configFile.getParent()).mkdir();

        // predefine init's
        Map<String, String> initProps;
        if (initFile.exists())
            initProps = serverFileUtils.loadHashMap(initFile);
        else
            initProps = new HashMap<String, String>();
        
        // if 'pro'-version is selected, overload standard settings with 'pro'-settings
        Iterator<String> i;
        String prop;
    	if (applyPro) {
        	i = new HashMap<String, String>(initProps).keySet().iterator(); // clone the map to avoid concurrent modification exceptions
        	while (i.hasNext()) {
        		prop = i.next();
        		if (prop.endsWith("__pro")) {
        			initProps.put(prop.substring(0, prop.length() - 5), initProps.get(prop));
        		}
        	}
        }
        // delete the 'pro' init settings
        i = initProps.keySet().iterator();
        while (i.hasNext()) {
        	prop = i.next();
        	if (prop.endsWith("__pro")) {
        		i.remove();
        	}
        }
        
        // load config's from last save
        if (configFile.exists())
            configProps = serverFileUtils.loadHashMap(configFile);
        else
            configProps = new HashMap<String, String>();

        // remove all values from config that do not appear in init
        configRemoved = new HashMap<String, String>();
        synchronized (configProps) {
            i = configProps.keySet().iterator();
            String key;
            while (i.hasNext()) {
                key = i.next();
                if (!(initProps.containsKey(key))) {
                    configRemoved.put(key, this.configProps.get(key));
                    i.remove();
                }
            }

            // doing a config settings migration
            //HashMap migratedSettings = migrateSwitchConfigSettings((HashMap) removedProps);
            //if (migratedSettings != null) configProps.putAll(migratedSettings);

            // merge new props from init to config
            // this is necessary for migration, when new properties are attached
            initProps.putAll(configProps);
            configProps = initProps;

            // save result; this may initially create a config file after
            // initialization
            saveConfig();
        }

        // other settings
        authorization = new HashMap<InetAddress, String>();
        accessTracker = new ConcurrentHashMap<String, TreeMap<Long, String>>();

        // init thread control
        workerThreads = new TreeMap<String, serverBusyThread>();

        // init switch actions
        switchActions = new TreeMap<String, serverSwitchAction>();

        // init busy state control
        serverJobs = 0;
        
        // init server tracking
        maxTrackingTime = getConfigLong("maxTrackingTime", maxTrackingTimeDefault);
    }
    
    // a logger for this switchboard
    public void setLog(final serverLog log) {
	this.log = log;
    }

    public serverLog getLog() {
	return log;
    }
    
    /*
     * remove all entries from the access tracker where the age of the last access is greater than the given timeout
     */
    public void cleanupAccessTracker(final long timeout) {
        final Iterator<Map.Entry<String, TreeMap<Long, String>>> i = accessTracker.entrySet().iterator();
        while (i.hasNext()) {
            if (i.next().getValue().tailMap(Long.valueOf(System.currentTimeMillis() - timeout)).size() == 0) i.remove();
        }
    }
    
    public void track(final String host, String accessPath) {
        // learn that a specific host has accessed a specific path
        if (accessPath == null) accessPath="NULL";
        TreeMap<Long, String> access = accessTracker.get(host);
        if (access == null) access = new TreeMap<Long, String>();
        
        synchronized (access) {
            access.put(Long.valueOf(System.currentTimeMillis()), accessPath);
            // write back to tracker
            accessTracker.put(host, clearTooOldAccess(access));
        }
    }
    
    public TreeMap<Long, String> accessTrack(final String host) {
        // returns mapping from Long(accesstime) to path
        
        TreeMap<Long, String> access = accessTracker.get(host);
        if (access == null) return null;
        // clear too old entries
        synchronized (access) {
            if ((access = clearTooOldAccess(access)).size() != access.size()) {
                // write back to tracker
                if (access.size() == 0) {
                    accessTracker.remove(host);
                } else {
                    accessTracker.put(host, access);
                }
            }
        }
        return access;
    }
    
    private TreeMap<Long, String> clearTooOldAccess(final TreeMap<Long, String> access) {
        return new TreeMap<Long, String>(access.tailMap(Long.valueOf(System.currentTimeMillis() - maxTrackingTime)));
    }
    
    public Iterator<String> accessHosts() {
        // returns an iterator of hosts in tracker (String)
    	final HashMap<String, TreeMap<Long, String>> accessTrackerClone = new HashMap<String, TreeMap<Long, String>>();
    	accessTrackerClone.putAll(accessTracker);
    	return accessTrackerClone.keySet().iterator();
    }

    public void setConfig(final Map<String, String> otherConfigs) {
        final Iterator<Map.Entry<String, String>> i = otherConfigs.entrySet().iterator();
        Map.Entry<String, String> entry;
        while (i.hasNext()) {
            entry = i.next();
            setConfig(entry.getKey(), entry.getValue());
        }
    }

    public void setConfig(final String key, final boolean value) {
        setConfig(key, (value) ? "true" : "false");
    }

    public void setConfig(final String key, final long value) {
        setConfig(key, Long.toString(value));
    }

    public void setConfig(final String key, final double value) {
        setConfig(key, Double.toString(value));
    }

    public void setConfig(final String key, final String value) {
        // perform action before setting new value
        final Iterator<serverSwitchAction> bevore = switchActions.values().iterator();
        final Iterator<serverSwitchAction> after  = switchActions.values().iterator();
        synchronized (configProps) {
            serverSwitchAction action;
            
            while (bevore.hasNext()) {
                action = bevore.next();
                try {
                    action.doBevoreSetConfig(key, value);
                } catch (final Exception e) {
                    log.logSevere("serverAction bevoreSetConfig '" + action.getShortDescription() + "' failed with exception: " + e.getMessage());
                }
            }

            // set the value
            final Object oldValue = configProps.put(key, value);
            saveConfig();

            // perform actions afterwards
            while (after.hasNext()) {
                action = after.next();
                try {
                    action.doAfterSetConfig(key, value, (oldValue == null) ? null : (String) oldValue);
                } catch (final Exception e) {
                    log.logSevere("serverAction afterSetConfig '" + action.getShortDescription() + "' failed with exception: " + e.getMessage());
                }
            }
        }
    }

    public void removeConfig(final String key) {
    	configProps.remove(key);
    }
    
    /* (non-Javadoc)
     * @see de.anomic.server.serverSwitch#getConfig(java.lang.String, java.lang.String)
     */
    public String getConfig(final String key, final String dflt) {
        final Iterator<serverSwitchAction> i = switchActions.values().iterator();
        synchronized (configProps) {
            // get the value
            final Object s = configProps.get(key);

            // do action
            serverSwitchAction action;
            while (i.hasNext()) {
                action = i.next();
                try {
                    action.doWhenGetConfig(key, (s == null) ? null : (String) s, dflt);
                } catch (final Exception e) {
                    log.logSevere("serverAction whenGetConfig '" + action.getShortDescription() + "' failed with exception: " + e.getMessage());
                }
            }

            // return value
            if (s == null) return dflt;
            return (String) s;
        }
    }
    
    public long getConfigLong(final String key, final long dflt) {
        try {
            return Long.parseLong(getConfig(key, Long.toString(dflt)));
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }
    
    public double getConfigDouble(final String key, final double dflt) {
        try {
            return Double.parseDouble(getConfig(key, Double.toString(dflt)));
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }
    
    public boolean getConfigBool(final String key, final boolean dflt) {
        return Boolean.valueOf(getConfig(key, Boolean.toString(dflt))).booleanValue();
    }
    
    /**
     * Create a File instance for a configuration setting specifying a path.
     * @param key   config key
     * @param dflt  default path value, that is used when there is no value
     *              <code>key</code> in the configuration.
     * @return if the value of the setting is an absolute path String, then the
     * returned File is derived from this setting only. Otherwise the path's file
     * is constructed from the applications root path + the relative path setting.
     */
    public File getConfigPath(final String key, final String dflt) {
        File ret;
        final String path = getConfig(key, dflt).replace('\\', '/');
        final File f = new File(path);
        if (f == null) {
            ret = null;
        } else {
            ret = (f.isAbsolute() ? f : new File(this.rootPath, path));
        }
        
        return ret;
    }

    public Iterator<String> configKeys() {
        return configProps.keySet().iterator();
    }

    private void saveConfig() {
        try {
            synchronized (configProps) {
                serverFileUtils.saveMap(configFile, configProps, configComment);
            }
        } catch (final IOException e) {
            System.out.println("ERROR: cannot write config file " + configFile.toString() + ": " + e.getMessage());
        }
    }

    public Map<String, String> getRemoved() {
        // returns configuration that had been removed during initialization
        return configRemoved;
    }
    
    // add/remove action listener
    public void deployAction(final String actionName, final String actionShortDescription, final String actionLongDescription,
			     final serverSwitchAction newAction) {
        newAction.setLog(log);
        newAction.setDescription(actionShortDescription, actionLongDescription);
        switchActions.put(actionName, newAction);
        log.logInfo("Deployed Action '" + actionShortDescription + "', (" + switchActions.size() + " actions registered)");
    }

    public void undeployAction(final String actionName) {
	final serverSwitchAction action = switchActions.get(actionName);
	action.close();
	switchActions.remove(actionName);
	log.logInfo("Undeployed Action '" + action.getShortDescription() + "', (" + switchActions.size() + " actions registered)");
    }

    public void deployThread(
            final String threadName,
            final String threadShortDescription,
            final String threadLongDescription,
            final String threadMonitorURL,
            final serverBusyThread newThread,
            final long startupDelay) {
        deployThread(threadName, threadShortDescription, threadLongDescription, threadMonitorURL,
                     newThread, startupDelay,
                     Long.parseLong(getConfig(threadName + "_idlesleep" ,     "100")), 
                     Long.parseLong(getConfig(threadName + "_busysleep" ,    "1000")),
                     Long.parseLong(getConfig(threadName + "_memprereq" , "1000000")));
    }

    public void deployThread(
            final String threadName,
            final String threadShortDescription,
            final String threadLongDescription,
            final String threadMonitorURL,
            final serverBusyThread newThread,
            final long startupDelay,
            final long initialIdleSleep,
            final long initialBusySleep,
            final long initialMemoryPreRequisite) {
        if (newThread.isAlive()) throw new RuntimeException("undeployed threads must not live; they are started as part of the deployment");
        newThread.setStartupSleep(startupDelay);
        long x;
        try {
            x = Long.parseLong(getConfig(threadName + "_idlesleep" , "novalue"));
            newThread.setIdleSleep(x);
        } catch (final NumberFormatException e) {
            newThread.setIdleSleep(initialIdleSleep);
            setConfig(threadName + "_idlesleep", initialIdleSleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_busysleep" , "novalue"));
            newThread.setBusySleep(x);
        } catch (final NumberFormatException e) {
            newThread.setBusySleep(initialBusySleep);
            setConfig(threadName + "_busysleep", initialBusySleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_memprereq" , "novalue"));
            newThread.setMemPreReqisite(x);
        } catch (final NumberFormatException e) {
            newThread.setMemPreReqisite(initialMemoryPreRequisite);
            setConfig(threadName + "_memprereq", initialMemoryPreRequisite);
        }
        newThread.setLog(log);
        newThread.setDescription(threadShortDescription, threadLongDescription, threadMonitorURL);
        workerThreads.put(threadName, newThread);
        // start the thread
        if (workerThreads.containsKey(threadName)) newThread.start();
    }

    public serverBusyThread getThread(final String threadName) {
        return workerThreads.get(threadName);
    }
    
    public void setThreadPerformance(final String threadName, final long idleMillis, final long busyMillis, final long memprereqBytes) {
        final serverBusyThread thread = workerThreads.get(threadName);
        if (thread != null) {
            thread.setIdleSleep(idleMillis);
            thread.setBusySleep(busyMillis);
            thread.setMemPreReqisite(memprereqBytes);
        }
    }
    
    public synchronized void terminateThread(final String threadName, final boolean waitFor) {
        if (workerThreads.containsKey(threadName)) {
            ((serverThread) workerThreads.get(threadName)).terminate(waitFor);
            workerThreads.remove(threadName);
        }
    }

    public void intermissionAllThreads(final long pause) {
        final Iterator<String> e = workerThreads.keySet().iterator();
        while (e.hasNext()) {
            workerThreads.get(e.next()).intermission(pause);
        }
    }
    
    public synchronized void terminateAllThreads(final boolean waitFor) {
        Iterator<String> e = workerThreads.keySet().iterator();
        while (e.hasNext()) {
            ((serverThread) workerThreads.get(e.next())).terminate(false);
        }
        if (waitFor) {
            e = workerThreads.keySet().iterator();
            while (e.hasNext()) {
                ((serverThread) workerThreads.get(e.next())).terminate(true);
                e.remove();
            }
        }
    }
    
    public Iterator<String> /*of serverThread-Names (String)*/ threadNames() {
        return workerThreads.keySet().iterator();
    }
    
    public int queueSize() {
        return cacheStack.size();
    }
    
    public E queuePeek() {
        return cacheStack.peek();
    }
    
    public void enQueue(final E job) {
        cacheStack.add(job);
    }
    
    public E deQueue() throws InterruptedException {
        return cacheStack.take();
    }

    // authentification routines:
    
    public void setAuthentify(final InetAddress host, final String user, final String rights) {
        // sets access attributes according to host addresses
        authorization.put(host, user + "@" + rights);
    }

    public void removeAuthentify(final InetAddress host) {
        // remove access attributes according to host addresses
        authorization.remove(host);
    }

    public String getAuthentifyUser(final InetAddress host) {
	// read user name according to host addresses
	final String a = authorization.get(host);
	if (a == null) return null;
	final int p = a.indexOf("@");
	if (p < 0) return null;
	return a.substring(0, p);
    }

    public String getAuthentifyRights(final InetAddress host) {
	// read access rigths according to host addresses
	final String a = authorization.get(host);
	if (a == null) return null;
	final int p = a.indexOf("@");
	if (p < 0) return null;
	return a.substring(p + 1);
    }

    public void addAuthentifyRight(final InetAddress host, final String right) {
	final String rights = getAuthentifyRights(host);
	if (rights == null) {
	    // create new authentification
	    setAuthentify(host, "unknown", right);
	} else {
	    // add more authentification
	    final String user = getAuthentifyUser(host);
	    setAuthentify(host, user, rights + right);
	}	
    }

    public boolean hasAuthentifyRight(final InetAddress host, final String right) {
	final String rights = getAuthentifyRights(host);
	if (rights == null) return false;
	return rights.indexOf(right) >= 0;
    }

    public abstract serverObjects action(String actionName, serverObjects actionInput);

    public File getRootPath() {
       return rootPath;
    }
    
    public String toString() {
	return configProps.toString();
    }

    public void handleBusyState(final int jobs) {
        serverJobs = jobs;
    }
}
