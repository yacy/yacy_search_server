// serverSwitch.java
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.kelondro.workflow.WorkflowThread;

import de.anomic.server.serverAccessTracker.Track;
import de.anomic.server.serverCore.Session;

public class serverSwitch {
    
    // configuration management
    private   final File    configFile;
    private   final String  configComment;
    private   final File    dataPath;
    private   final File    appPath;
    protected       boolean firstInit;
    protected       Log     log;
    protected       int     serverJobs;
    private         ConcurrentMap<String, String>      configProps;
    private   final ConcurrentMap<String, String>      configRemoved;
    private   final ConcurrentMap<InetAddress, String> authorization;
    private   final NavigableMap<String, BusyThread>   workerThreads;
    private   final serverAccessTracker                accessTracker;
    
    public serverSwitch(final File dataPath, final File appPath, final String initPath, final String configPath) {
        // we initialize the switchboard with a property file,
        // but maintain these properties then later in a new 'config' file
        // to reset all changed configs, the config file must
        // be deleted, but not the init file
        // the only attribute that will always be read from the init is the
        // file name of the config file
        this.dataPath = dataPath;
        this.appPath = appPath;
    	this.configComment = "This is an automatically generated file, updated by serverAbstractSwitch and initialized by " + initPath;
        final File initFile = new File(appPath, initPath);
        this.configFile = new File(dataPath, configPath); // propertiesFile(config);
        firstInit = !configFile.exists(); // this is true if the application was started for the first time
        new File(configFile.getParent()).mkdir();

        // predefine init's
        final ConcurrentMap<String, String> initProps;
        if (initFile.exists())
            initProps = FileUtils.loadMap(initFile);
        else
            initProps = new ConcurrentHashMap<String, String>();
        
        // if 'pro'-version is selected, overload standard settings with 'pro'-settings
        Iterator<String> i;
        String prop;
        
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
            configProps = FileUtils.loadMap(configFile);
        else
            configProps = new ConcurrentHashMap<String, String>();

        // remove all values from config that do not appear in init
        configRemoved = new ConcurrentHashMap<String, String>();
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
        authorization = new ConcurrentHashMap<InetAddress, String>();

        // init thread control
        workerThreads = new TreeMap<String, BusyThread>();

        // init busy state control
        serverJobs = 0;
        
        // init server tracking
        this.accessTracker = new serverAccessTracker(
            getConfigLong("server.maxTrackingTime", 60 * 60 * 1000),
            (int) getConfigLong("server.maxTrackingCount", 1000),
            (int) getConfigLong("server.maxTrackingHostCount", 100)
        );        
    }
    
    public String myPublicIP() {
        // if a static IP was configured, we have to return it here ...
        final String staticIP = getConfig("staticIP", "");
        if (staticIP.length() > 0) {
            return staticIP;
        }

        // otherwise we return the real IP address of this host
        final InetAddress pLIP = Domains.myPublicLocalIP();
        if (pLIP != null) return pLIP.getHostAddress();
        return null;
    }
    
    // a logger for this switchboard
    public void setLog(final Log log) {
	this.log = log;
    }

    public Log getLog() {
	return log;
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

    public void setConfig(final String key, final float value) {
        setConfig(key, Float.toString(value));
    }

    public void setConfig(final String key, final String value) {
        // set the value
        final String oldValue = configProps.put(key, value);
        if (oldValue == null || !value.equals(oldValue)) saveConfig();
    }

    public void removeConfig(final String key) {
    	configProps.remove(key);
    }
    
    /**
     * Gets a configuration parameter from the properties.
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be
     * found or if it is invalid
     * @return value if the parameter or default value
     */
    public String getConfig(final String key, final String dflt) {
        // get the value
        final String s = configProps.get(key);

        // return value
        if (s == null) return dflt;
        return s;
    }

    /**
     * Gets a configuration parameter from the properties.
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be
     * found or if it is invalid
     * @return value if the parameter or default value
     */
    public long getConfigLong(final String key, final long dflt) {
        try {
            return Long.parseLong(getConfig(key, Long.toString(dflt)));
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    /**
     * Gets a configuration parameter from the properties.
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be
     * found or if it is invalid
     * @return value if the parameter or default value
     */
    public double getConfigFloat(final String key, final float dflt) {
        try {
            return Float.parseFloat(getConfig(key, Float.toString(dflt)));
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    /**
     * Gets a configuration parameter from the properties.
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be
     * found or if it is invalid
     * @return value if the parameter or default value
     */
    public int getConfigInt(final String key, final int dflt) {
        try {
            return Integer.parseInt(getConfig(key, Integer.toString(dflt)));
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    /**
     * Gets a configuration parameter from the properties.
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be
     * found or if it is invalid
     * @return value if the parameter or default value
     */
    public boolean getConfigBool(final String key, final boolean dflt) {
        return Boolean.parseBoolean(getConfig(key, Boolean.toString(dflt)));
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
    public File getDataPath(final String key, final String dflt) {
        File ret;
        final String path = getConfig(key, dflt).replace('\\', '/');
        final File f = new File(path);
        ret = (f.isAbsolute() ? new File(f.getAbsolutePath()) : new File(this.dataPath, path));
        return ret;
    }
    
    public File getAppPath(final String key, final String dflt) {
        File ret;
        final String path = getConfig(key, dflt).replace('\\', '/');
        final File f = new File(path);
        ret = (f.isAbsolute() ? new File(f.getAbsolutePath()) : new File(this.appPath, path));
        return ret;
    }

    public Iterator<String> configKeys() {
        return configProps.keySet().iterator();
    }

    private void saveConfig() {
        try {
            ConcurrentMap<String, String> configPropsCopy = new ConcurrentHashMap<String, String>();
            configPropsCopy.putAll(configProps); // avoid concurrency problems
            FileUtils.saveMap(configFile, configPropsCopy, configComment);
        } catch (final IOException e) {
        	log.logSevere("CONFIG: Cannot write config file " + configFile.toString() + ": " + e.getMessage(), e);
            //System.out.println("ERROR: cannot write config file " + configFile.toString() + ": " + e.getMessage());
        }
    }

    /**
     * Gets configuration parameters which have been removed during initialization.
     * @return contains parameter name as key and parameter value as value
     */
    public ConcurrentMap<String, String> getRemoved() {
        return configRemoved;
    }

    public void deployThread(
            final String threadName,
            final String threadShortDescription,
            final String threadLongDescription,
            final String threadMonitorURL,
            final BusyThread newThread,
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
            final BusyThread newThread,
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
        newThread.setDescription(threadShortDescription, threadLongDescription, threadMonitorURL);
        workerThreads.put(threadName, newThread);
        // start the thread
        if (workerThreads.containsKey(threadName)) newThread.start();
    }

    public BusyThread getThread(final String threadName) {
        return workerThreads.get(threadName);
    }
    
    public void setThreadPerformance(final String threadName, final long idleMillis, final long busyMillis, final long memprereqBytes) {
        final BusyThread thread = workerThreads.get(threadName);
        if (thread != null) {
            setConfig(threadName + "_idlesleep", thread.setIdleSleep(idleMillis));
            setConfig(threadName + "_busysleep", thread.setBusySleep(busyMillis));
            setConfig(threadName + "_memprereq", memprereqBytes);
            thread.setMemPreReqisite(memprereqBytes);
        }
    }
    
    public synchronized void terminateThread(final String threadName, final boolean waitFor) {
        if (workerThreads.containsKey(threadName)) {
            ((WorkflowThread) workerThreads.get(threadName)).terminate(waitFor);
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
            ((WorkflowThread) workerThreads.get(e.next())).terminate(false);
        }
        if (waitFor) {
            e = workerThreads.keySet().iterator();
            while (e.hasNext()) {
                ((WorkflowThread) workerThreads.get(e.next())).terminate(true);
                e.remove();
            }
        }
    }
    
    public String[] sessionsOlderThan(String threadName, long timeout) {
        final List<String> list = new ArrayList<String>();
        final WorkflowThread st = getThread(threadName);
        
        for (final Session s: ((serverCore) st).getJobList()) {
            if (!s.isAlive()) continue;
            if (s.getTime() > timeout) {
                list.add(s.getName());
            }
        }
        return (String[]) list.toArray();
    }
    
    public void closeSessions(String threadName, String sessionName) {
        if (sessionName == null) return;
        final WorkflowThread st = getThread(threadName);
        
        for (final Session s: ((serverCore) st).getJobList()) {
            if (
                (s.isAlive()) &&
                (s.getName().equals(sessionName))
            ) {
                // try to stop session
                s.setStopped(true);
                try { Thread.sleep(100); } catch (final InterruptedException ex) {}
                
                // try to interrupt session
                s.interrupt();
                try { Thread.sleep(100); } catch (final InterruptedException ex) {}
                
                // try to close socket
                if (s.isAlive()) {
                    s.close();
                }
                
                // wait for session to finish
                if (s.isAlive()) {
                    try { s.join(500); } catch (final InterruptedException ex) {}
                }
            }
        }
    }
    
    public Iterator<String> /*of serverThread-Names (String)*/ threadNames() {
        return workerThreads.keySet().iterator();
    }

    // authentication routines:
    
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
	final int p = a.indexOf('@');
	if (p < 0) return null;
	return a.substring(0, p);
    }

    public String getAuthentifyRights(final InetAddress host) {
	// read access rigths according to host addresses
	final String a = authorization.get(host);
	if (a == null) return null;
	final int p = a.indexOf('@');
	if (p < 0) return null;
	return a.substring(p + 1);
    }

    public void addAuthentifyRight(final InetAddress host, final String right) {
	final String rights = getAuthentifyRights(host);
	if (rights == null) {
	    // create new authentication
	    setAuthentify(host, "unknown", right);
	} else {
	    // add more authentication
	    final String user = getAuthentifyUser(host);
	    setAuthentify(host, user, rights + right);
	}	
    }

    public boolean hasAuthentifyRight(final InetAddress host, final String right) {
	final String rights = getAuthentifyRights(host);
	if (rights == null) return false;
	return rights.indexOf(right) >= 0;
    }

    public File getDataPath() {
       return this.dataPath;
    }

    public File getAppPath() {
       return this.appPath;
    }
    
    @Override
    public String toString() {
	return configProps.toString();
    }

    public void handleBusyState(final int jobs) {
        serverJobs = jobs;
    }
    
    public void track(final String host, final String accessPath) {
        this.accessTracker.track(host, accessPath);
    }
    
    public Collection<Track> accessTrack(final String host) {
        return this.accessTracker.accessTrack(host);
    } 

    public int latestAccessCount(final String host, final long timedelta) {
        return this.accessTracker.latestAccessCount(host, timedelta);
    } 
    
    public Iterator<String> accessHosts() {
        return this.accessTracker.accessHosts();
    }
    
    /**
     * Retrieve text data (e. g. config file) from file
     * 
     * file may be an url or a filename with path relative to rootPath parameter
     * @param file url or filename
     * @param rootPath searchpath for file
     * @param file file to use when remote fetching fails (null if unused)
     */
    public Reader getConfigFileFromWebOrLocally(final String uri,
            final String rootPath, final File file) throws IOException, FileNotFoundException {
    	if (uri.startsWith("http://") || uri.startsWith("https://")) {
            final String[] uris = uri.split(",");
            for (String netdef: uris) {
                netdef = netdef.trim();
                try {
                    final RequestHeader reqHeader = new RequestHeader();
                    reqHeader.put(HeaderFramework.USER_AGENT, ClientIdentification.getUserAgent());
                    final HTTPClient client = new HTTPClient();
                    client.setHeader(reqHeader.entrySet());
                    byte[] data = client.GETbytes(uri);
                    if (data == null || data.length == 0) continue;
                    // save locally in case next fetch fails
                    if (file != null) {
                    	FileOutputStream f = new FileOutputStream(file);
                    	f.write(data);
                    	f.close();
                    }
                    return new InputStreamReader(new BufferedInputStream(new ByteArrayInputStream(data)));
                } catch (final Exception e) {
                    continue;
                }
            }
            if (file != null && file.exists()) {
            	return new FileReader(file);
            } else {
            	throw new FileNotFoundException();
            }
    	} else {
            final File f = (uri.length() > 0 && uri.startsWith("/")) ? new File(uri) : new File(rootPath, uri);
            if (f.exists()) {
            	return new FileReader(f);
            } else {
            	throw new FileNotFoundException(f.toString());
            }
    	}
    }
    
    private static Random pwGenerator = new Random();

    /**
     * Generates a random password.
     * @return random password which is 20 characters long.
     */
    public String genRandomPassword() {
    	return genRandomPassword(20);
    }

    /**
     * Generates a random password of a given length.
     * @param length length o password
     * @return password of given length
     */
    public String genRandomPassword(final int length) {
    	byte[] bytes = new byte[length];
    	pwGenerator.nextBytes(bytes);
    	return Digest.encodeMD5Hex(bytes);
    }
    
}
