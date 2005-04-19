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

import java.io.*;
import java.net.*;
import java.util.*;

public abstract class serverAbstractSwitch implements serverSwitch {

    // configuration management
    private final File      configFile;
    private Hashtable configProps;
    private final String    configComment;
    private final Hashtable authorization;
    private String    rootPath;
    private final TreeMap   workerThreads;
    
    public serverAbstractSwitch(String rootPath, String initPath, String configPath) throws IOException {
	// we initialize the switchboard with a property file,
	// but maintain these properties then later in a new 'config' file
	// to reset all changed configs, the config file must
	// be deleted, but not the init file
	// the only attribute that will always be read from the init is the
	// file name of the config file
        this.rootPath = rootPath;
	configComment = "this is an automaticaly generated file, updated by serverAbstractSwitch and initialized by " + initPath;
	File initFile = new File(rootPath, initPath);
	configFile = new File(rootPath, configPath); //propertiesFile(config);
	new File(configFile.getParent()).mkdir();

	// predefine init's
	Hashtable initProps;
	if (initFile.exists()) initProps = loadHashtable(initFile); else initProps = new Hashtable();

	// load config's from last save
	if (configFile.exists()) configProps = loadHashtable(configFile); else configProps = new Hashtable();

        // remove all values from config that do not appear in init (out-dated settings)
        Enumeration e = configProps.keys();
        String key;
        while (e.hasMoreElements()) {
            key = (String) e.nextElement();
            //System.out.println("TESTING " + key);
            if (!(initProps.containsKey(key))) {
                //System.out.println("MIGRATE: removing out-dated property '" + key + "'");
                configProps.remove(key);
            }
        }

	// merge new props from init to config
	// this is necessary for migration, when new properties are attached
	initProps.putAll(configProps);
	configProps = initProps;
        
	// save result; this may initially create a config file after initialization
	saveConfig();

	// other settings
	authorization = new Hashtable();
        
        // init thread control
        workerThreads = new TreeMap();
    }

    public static Hashtable loadHashtable(File f) {
	// load props
	Properties prop = new Properties();
	try {
	    prop.load(new FileInputStream(f));
	} catch (IOException e1) {
	    System.err.println("ERROR: " + f.toString() + " not found in settings path");
	    prop = null;
	}
	return (Hashtable) prop;
    }

    public static void saveHashtable(File f, Hashtable props, String comment) throws IOException {
	PrintWriter pw = new PrintWriter(new FileOutputStream(f));
	pw.println("# " + comment);
	Enumeration e = props.keys();
	String key, value;
	while (e.hasMoreElements()) {
	    key = (String) e.nextElement();
	    //value = (String) props.get(key);
	    value = ((String) props.get(key)).replaceAll("\n", "\\\\n");
	    pw.println(key + "=" + value);
	}
	pw.println("# EOF");
	pw.close();
    }

    public void setConfig(String key, long value) {
        setConfig(key, "" + value);
    }
    
    public void setConfig(String key, String value) {
	configProps.put(key, value);
	saveConfig();
    }

    public String getConfig(String key, String dflt) {
	String s = (String) configProps.get(key);
	if (s == null) return dflt; else return s;
    }

    public Enumeration configKeys() {
	return configProps.keys();
    }

    private void saveConfig() {
	try {
	    saveHashtable(configFile, configProps, configComment);
	} catch (IOException e) {
	    System.out.println("ERROR: cannot write config file " + configFile.toString() + ": " + e.getMessage());
	}
    }

    public void deployThread(String threadName, String threadShortDescription, String threadLongDescription, serverThread newThread, serverLog log, long startupDelay) {
        deployThread(threadName, threadShortDescription, threadLongDescription,
                     newThread, log, startupDelay,
                     Long.parseLong(getConfig(threadName + "_idlesleep" , "novalue")), 
                     Long.parseLong(getConfig(threadName + "_busysleep" , "novalue")));
    }

    public void deployThread(String threadName, String threadShortDescription, String threadLongDescription, serverThread newThread, serverLog log, long startupDelay, long initialIdleSleep, long initialBusySleep) {
        if (newThread.isAlive()) throw new RuntimeException("undeployed threads must not live; they are started as part of the deployment");
        newThread.setStartupSleep(startupDelay);
        long sleep;
        try {
            sleep = Long.parseLong(getConfig(threadName + "_idlesleep" , "novalue"));
            newThread.setIdleSleep(sleep);
        } catch (NumberFormatException e) {
            newThread.setIdleSleep(initialIdleSleep);
            setConfig(threadName + "_idlesleep", initialIdleSleep);
        }
        try {
            sleep = Long.parseLong(getConfig(threadName + "_busysleep" , "novalue"));
            newThread.setBusySleep(sleep);
        } catch (NumberFormatException e) {
            newThread.setBusySleep(initialBusySleep);
            setConfig(threadName + "_busysleep", initialBusySleep);
        }
        newThread.setLog(log);
        newThread.setDescription(threadShortDescription, threadLongDescription);
        workerThreads.put(threadName, newThread);
        // start the thread
        if (workerThreads.containsKey(threadName)) newThread.start();
    }

    public serverThread getThread(String threadName) {
        return (serverThread) workerThreads.get(threadName);
    }
    
    public void setThreadSleep(String threadName, long idleMillis, long busyMillis) {
        serverThread thread = (serverThread) workerThreads.get(threadName);
        if (thread != null) {
            thread.setIdleSleep(idleMillis);
            thread.setBusySleep(busyMillis);
        }
    }
    
    public synchronized void terminateThread(String threadName, boolean waitFor) {
        if (workerThreads.containsKey(threadName)) {
            ((serverThread) workerThreads.get(threadName)).terminate(waitFor);
            workerThreads.remove(threadName);
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
    abstract public void deQueue();


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
        // do nothing here; should be overridden
    }
}
