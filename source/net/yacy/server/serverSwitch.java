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

package net.yacy.server;

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
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.http.YaCyHttpServer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.kelondro.workflow.WorkflowThread;
import net.yacy.search.SwitchboardConstants;

public class serverSwitch
{

    // configuration management
    private final File configFile;
    private final String configComment;
    public final File dataPath;
    public final File appPath;
    protected boolean firstInit;
    public ConcurrentLog log;
    protected int serverJobs;
    private ConcurrentMap<String, String> configProps;
    private final ConcurrentMap<String, String> configRemoved;
    private final ConcurrentMap<InetAddress, String> authorization;
    private final NavigableMap<String, BusyThread> workerThreads;
    private YaCyHttpServer httpserver; // implemented HttpServer
    
    public serverSwitch(
        final File dataPath,
        final File appPath,
        final String initPath,
        final String configPath) {
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
        this.firstInit = !this.configFile.exists(); // this is true if the application was started for the first time
        new File(this.configFile.getParent()).mkdir();

        // predefine init's
        final ConcurrentMap<String, String> initProps;
        if ( initFile.exists() ) {
            initProps = FileUtils.loadMap(initFile);
        } else {
            initProps = new ConcurrentHashMap<String, String>();
        }
        
        // load config's from last save
        if ( this.configFile.exists() ) {
            this.configProps = FileUtils.loadMap(this.configFile);
        } else {
            this.configProps = new ConcurrentHashMap<String, String>();
        }

        // remove all values from config that do not appear in init
        this.configRemoved = new ConcurrentHashMap<String, String>();
        synchronized ( this.configProps ) {
            Iterator<String> i = this.configProps.keySet().iterator();
            String key;
            while ( i.hasNext() ) {
                key = i.next();
                if ( !(initProps.containsKey(key)) ) {
                    this.configRemoved.put(key, this.configProps.get(key));
                    i.remove();
                }
            }

            // merge new props from init to config
            // this is necessary for migration, when new properties are attached
            initProps.putAll(this.configProps);
            this.configProps = initProps;

            // save result; this may initially create a config file after initialization
            saveConfig();
        }

        // other settings
        this.authorization = new ConcurrentHashMap<InetAddress, String>();

        // init thread control
        this.workerThreads = new TreeMap<String, BusyThread>();

        // init busy state control
        this.serverJobs = 0;

        // init server tracking
        serverAccessTracker.init(
                getConfigLong("server.maxTrackingTime", 60 * 60 * 1000),
                (int) getConfigLong("server.maxTrackingCount", 1000),
                (int) getConfigLong("server.maxTrackingHostCount", 100));
    }

    /**
     * get my public IP, either set statically or figure out dynamic
     * @return
     */
    public String myPublicIP() {
        // if a static IP was configured, we have to return it here ...
        final String staticIP = getConfig("staticIP", "");
        if ( staticIP.length() > 0 ) return staticIP;

        // otherwise we return the real IP address of this host
        final InetAddress pLIP = Domains.myPublicLocalIP();
        if ( pLIP != null ) return pLIP.getHostAddress();
        return null;
    }

    // a logger for this switchboard
    public void setLog(final ConcurrentLog log) {
        this.log = log;
    }

    public ConcurrentLog getLog() {
        return this.log;
    }

    /**
     * add whole map of key-value pairs to config
     * @param otherConfigs
     */
    public void setConfig(final Map<String, String> otherConfigs) {
        final Iterator<Map.Entry<String, String>> i = otherConfigs.entrySet().iterator();
        Map.Entry<String, String> entry;
        while ( i.hasNext() ) {
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

    public void setConfig(final String key, final double value) {
        setConfig(key, Double.toString(value));
    }

    public void setConfig(final String key, final String value) {
        // set the value
        final String oldValue = this.configProps.put(key, value);
        if ( oldValue == null || !value.equals(oldValue) ) {
            saveConfig();
        }
    }

    public void removeConfig(final String key) {
        this.configProps.remove(key);
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be found or if it is invalid
     * @return value if the parameter or default value
     */
    public String getConfig(final String key, final String dflt) {
        // get the value
        final String s = this.configProps.get(key);

        // return value
        if ( s == null ) {
            return dflt;
        }
        return s;
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be found or if it is invalid
     * @return value if the parameter or default value
     */
    public long getConfigLong(final String key, final long dflt) {
        try {
            return Long.parseLong(getConfig(key, Long.toString(dflt)));
        } catch (final NumberFormatException e ) {
            return dflt;
        }
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be found or if it is invalid
     * @return value if the parameter or default value
     */
    public float getConfigFloat(final String key, final float dflt) {
        try {
            return Float.parseFloat(getConfig(key, Float.toString(dflt)));
        } catch (final NumberFormatException e ) {
            return dflt;
        }
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be found or if it is invalid
     * @return value if the parameter or default value
     */
    public int getConfigInt(final String key, final int dflt) {
        try {
            return Integer.parseInt(getConfig(key, Integer.toString(dflt)));
        } catch (final NumberFormatException e ) {
            return dflt;
        }
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key name of the configuration parameter
     * @param dflt default value which will be used in case parameter can not be found or if it is invalid
     * @return value if the parameter or default value
     */
    public boolean getConfigBool(final String key, final boolean dflt) {
        return Boolean.parseBoolean(getConfig(key, Boolean.toString(dflt)));
    }

    /**
     * Create a File instance for a configuration setting specifying a path.
     *
     * @param key config key
     * @param dflt default path value, that is used when there is no value <code>key</code> in the
     *        configuration.
     * @return if the value of the setting is an absolute path String, then the returned File is derived from
     *         this setting only. Otherwise the path's file is constructed from the applications root path +
     *         the relative path setting.
     */
    public File getDataPath(final String key, final String dflt) {
        return getFileByPath(key, dflt, this.dataPath);
    }

    /**
     * return file at path from config entry "key", or fallback to default dflt
     * @param key
     * @param dflt
     * @return
     */
    public File getAppPath(final String key, final String dflt) {
        return getFileByPath(key, dflt, this.appPath);
    }

    private File getFileByPath(String key, String dflt, File prefix) {
        final String path = getConfig(key, dflt).replace('\\', '/');
        final File f = new File(path);
        return (f.isAbsolute() ? new File(f.getAbsolutePath()) : new File(prefix, path));
    }

    public Iterator<String> configKeys() {
        return this.configProps.keySet().iterator();
    }

    /**
     * write the changes to permanent storage (File)
     */
    private void saveConfig() {
        ConcurrentMap<String, String> configPropsCopy = new ConcurrentHashMap<String, String>();
        configPropsCopy.putAll(this.configProps); // avoid concurrency problems
        FileUtils.saveMap(this.configFile, configPropsCopy, this.configComment);
    }

    /**
     * Gets configuration parameters which have been removed during initialization.
     *
     * @return contains parameter name as key and parameter value as value
     */
    public ConcurrentMap<String, String> getRemoved() {
        return this.configRemoved;
    }

    public void deployThread(
        final String threadName,
        final String threadShortDescription,
        final String threadLongDescription,
        final String threadMonitorURL,
        final BusyThread newThread,
        final long startupDelay) {
        deployThread(
            threadName,
            threadShortDescription,
            threadLongDescription,
            threadMonitorURL,
            newThread,
            startupDelay,
            Long.parseLong(getConfig(threadName + "_idlesleep", "100")),
            Long.parseLong(getConfig(threadName + "_busysleep", "1000")),
            Long.parseLong(getConfig(threadName + "_memprereq", "1000000")),
            Double.parseDouble(getConfig(threadName + "_loadprereq", "9.0")));
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
        final long initialMemoryPreRequisite,
        final double initialLoadPreRequisite) {
        if ( newThread.isAlive() ) {
            throw new RuntimeException(
                "undeployed threads must not live; they are started as part of the deployment");
        }
        newThread.setStartupSleep(startupDelay);
        long x;
        try {
            x = Long.parseLong(getConfig(threadName + "_idlesleep", "novalue"));
            newThread.setIdleSleep(x);
        } catch (final NumberFormatException e ) {
            newThread.setIdleSleep(initialIdleSleep);
            setConfig(threadName + "_idlesleep", initialIdleSleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_busysleep", "novalue"));
            newThread.setBusySleep(x);
        } catch (final NumberFormatException e ) {
            newThread.setBusySleep(initialBusySleep);
            setConfig(threadName + "_busysleep", initialBusySleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_memprereq", "novalue"));
            newThread.setMemPreReqisite(x);
        } catch (final NumberFormatException e ) {
            newThread.setMemPreReqisite(initialMemoryPreRequisite);
            setConfig(threadName + "_memprereq", initialMemoryPreRequisite);
        }
        try {
            final double load = Double.parseDouble(getConfig(threadName + "_loadprereq", "novalue"));
            newThread.setLoadPreReqisite(load);
        } catch (final NumberFormatException e ) {
            newThread.setLoadPreReqisite(initialLoadPreRequisite);
            setConfig(threadName + "_loadprereq", (float)initialLoadPreRequisite);
        }
        newThread.setDescription(threadShortDescription, threadLongDescription, threadMonitorURL);
        this.workerThreads.put(threadName, newThread);
        // start the thread
        if ( this.workerThreads.containsKey(threadName) ) {
            newThread.start();
        }
    }

    public BusyThread getThread(final String threadName) {
        return this.workerThreads.get(threadName);
    }

    public void setThreadPerformance(
        final String threadName,
        final long idleMillis,
        final long busyMillis,
        final long memprereqBytes,
        final double loadprereq) {
        final BusyThread thread = this.workerThreads.get(threadName);
        if ( thread != null ) {
            setConfig(threadName + "_idlesleep", thread.setIdleSleep(idleMillis));
            setConfig(threadName + "_busysleep", thread.setBusySleep(busyMillis));
            setConfig(threadName + "_memprereq", memprereqBytes);
            thread.setMemPreReqisite(memprereqBytes);
            setConfig(threadName + "_loadprereq", (float)loadprereq);
            thread.setLoadPreReqisite(loadprereq);
        }
    }

    public synchronized void terminateThread(final String threadName, final boolean waitFor) {
        if ( this.workerThreads.containsKey(threadName) ) {
            ((WorkflowThread) this.workerThreads.get(threadName)).terminate(waitFor);
            this.workerThreads.remove(threadName);
        }
    }

    public void intermissionAllThreads(final long pause) {
        final Iterator<String> e = this.workerThreads.keySet().iterator();
        while ( e.hasNext() ) {
            this.workerThreads.get(e.next()).intermission(pause);
        }
    }

    public synchronized void terminateAllThreads(final boolean waitFor) {
        Iterator<String> e = this.workerThreads.keySet().iterator();
        while ( e.hasNext() ) {
            ((WorkflowThread) this.workerThreads.get(e.next())).terminate(false);
        }
        if ( waitFor ) {
            e = this.workerThreads.keySet().iterator();
            while ( e.hasNext() ) {
                ((WorkflowThread) this.workerThreads.get(e.next())).terminate(true);
                e.remove();
            }
        }
    }

    public Iterator<String> /*of serverThread-Names (String)*/threadNames() {
        return this.workerThreads.keySet().iterator();
    }

    // authentication routines:

    public void setAuthentify(final InetAddress host, final String user, final String rights) {
        // sets access attributes according to host addresses
        this.authorization.put(host, user + "@" + rights);
    }

    public void removeAuthentify(final InetAddress host) {
        // remove access attributes according to host addresses
        this.authorization.remove(host);
    }

    public String getAuthentifyUser(final InetAddress host) {
        // read user name according to host addresses
        final String a = this.authorization.get(host);
        if ( a == null ) {
            return null;
        }
        final int p = a.indexOf('@');
        if ( p < 0 ) {
            return null;
        }
        return a.substring(0, p);
    }

    public String getAuthentifyRights(final InetAddress host) {
        // read access rigths according to host addresses
        final String a = this.authorization.get(host);
        if ( a == null ) {
            return null;
        }
        final int p = a.indexOf('@');
        if ( p < 0 ) {
            return null;
        }
        return a.substring(p + 1);
    }

    public void addAuthentifyRight(final InetAddress host, final String right) {
        final String rights = getAuthentifyRights(host);
        if ( rights == null ) {
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
        if ( rights == null ) {
            return false;
        }
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
        return this.configProps.toString();
    }

    public void handleBusyState(final int jobs) {
        this.serverJobs = jobs;
    }

    /**
     * Retrieve text data (e. g. config file) from file file may be an url or a filename with path relative to
     * rootPath parameter
     *
     * @param file url or filename
     * @param rootPath searchpath for file
     * @param file file to use when remote fetching fails (null if unused)
     */
    public Reader getConfigFileFromWebOrLocally(final String uri, final String rootPath, final File file)
        throws IOException,
        FileNotFoundException {
        if ( uri.startsWith("http://") || uri.startsWith("https://") ) {
            final String[] uris = uri.split(",");
            for ( String netdef : uris ) {
                netdef = netdef.trim();
                try {
                    final RequestHeader reqHeader = new RequestHeader();
                    reqHeader.put(HeaderFramework.USER_AGENT, ClientIdentification.yacyInternetCrawlerAgent.userAgent);
                    final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
                    client.setHeader(reqHeader.entrySet());
                    byte[] data = client.GETbytes(uri, getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""), false);
                    if ( data == null || data.length == 0 ) {
                        continue;
                    }
                    // save locally in case next fetch fails
                    if ( file != null ) {
                        FileOutputStream f = new FileOutputStream(file);
                        f.write(data);
                        f.close();
                    }
                    return new InputStreamReader(new BufferedInputStream(new ByteArrayInputStream(data)));
                } catch (final Exception e ) {
                    continue;
                }
            }
            if ( file != null && file.exists() ) {
                return new FileReader(file);
            }
            throw new FileNotFoundException();
        }
        final File f = (uri.length() > 0 && uri.startsWith("/")) ? new File(uri) : new File(rootPath, uri);
        if (f.exists()) return new FileReader(f);
        throw new FileNotFoundException(f.toString());
    }

    private static Random pwGenerator = new Random();

    /**
     * Generates a random password.
     *
     * @return random password which is 20 characters long.
     */
    public String genRandomPassword() {
        return genRandomPassword(20);
    }

    /**
     * Generates a random password of a given length.
     *
     * @param length length o password
     * @return password of given length
     */
    public String genRandomPassword(final int length) {
        byte[] bytes = new byte[length];
        pwGenerator.nextBytes(bytes);
        return Digest.encodeMD5Hex(bytes);
    }
    /**
     * set/remember jetty server
     * @param jettyserver 
     */
    public void setHttpServer(YaCyHttpServer jettyserver) {
        this.httpserver = jettyserver;
    }
    public YaCyHttpServer getHttpServer() {
        return httpserver;
    }

}
