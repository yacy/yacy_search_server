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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.http.YaCyHttpServer;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.kelondro.workflow.WorkflowThread;
import net.yacy.peers.Seed;
import net.yacy.search.SwitchboardConstants;

public class serverSwitch {

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
    private final NavigableMap<String, BusyThread> workerThreads;
    private YaCyHttpServer httpserver; // implemented HttpServer
    private final ConcurrentMap<String, Integer> upnpPortMap = new ConcurrentHashMap<>();
    private boolean isConnectedViaUpnp;

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
        this.firstInit = !this.configFile.exists(); // this is true if the application was started for the first time
        new File(this.configFile.getParent()).mkdir();

        // predefine init's
        final ConcurrentMap<String, String> initProps;
        if (initFile.exists()) {
            initProps = FileUtils.loadMap(initFile);
        } else {
            initProps = new ConcurrentHashMap<String, String>();
        }

        // load config's from last save
        if (this.configFile.exists()) {
            this.configProps = FileUtils.loadMap(this.configFile);
        } else {
            this.configProps = new ConcurrentHashMap<String, String>();
        }

        // overwrite configs with values from environment variables that start with "yacy_"
        final Properties sysprops = System.getProperties();
        sysprops.forEach((key, value) -> {
            final String k = (String) key;
            if (k.startsWith("yacy.")) {
                this.configProps.put(k.substring(5), (String) value);
            }
        });

        // remove all values from config that do not appear in init
        this.configRemoved = new ConcurrentHashMap<String, String>();
        final Iterator<String> i = this.configProps.keySet().iterator();
        String key;
        while (i.hasNext()) {
            key = i.next();
            if (!(initProps.containsKey(key))) {
                this.configRemoved.put(key, this.configProps.get(key));
                i.remove();
            }
        }

        // merge new props from init to config
        // this is necessary for migration, when new properties are attached
        initProps.putAll(this.configProps);
        this.configProps = initProps;

        // Read system properties and set all variables that have a prefix "yacy.".
        // This will make it possible that settings can be overwritten with environment variables.
        // Do this i.e. with "export YACY_PORT=8091 && ./startYACY.sh"
        for (final Map.Entry<Object, Object> entry: System.getProperties().entrySet()) {
            final String yacykey = (String) entry.getKey();
            if (yacykey.startsWith("YACY_")) {
                key = yacykey.substring(5).toLowerCase().replace('_', '.');
                if (this.configProps.containsKey(key)) this.configProps.put(key, (String) entry.getValue());
            }
        }
        for (final Map.Entry<String, String> entry: System.getenv().entrySet()) {
            final String yacykey = entry.getKey();
            if (yacykey.startsWith("YACY_")) {
                key = yacykey.substring(5).toLowerCase().replace('_', '.');
                if (this.configProps.containsKey(key)) this.configProps.put(key, entry.getValue());
            }
        }

        // save result; this may initially create a config file after
        // initialization
        saveConfig();

        // init thread control
        this.workerThreads = new TreeMap<String, BusyThread>();

        // init busy state control
        // this.serverJobs = 0;

        // init server tracking
        serverAccessTracker.init(
                getConfigLong("server.maxTrackingTime", 60 * 60 * 1000),
                (int) getConfigLong("server.maxTrackingCount", 1000),
                (int) getConfigLong("server.maxTrackingHostCount", 100));
    }

    /**
     * get my public IP, either set statically or figure out dynamic This method
     * is deprecated because there may be more than one public IPs of this peer,
     * i.e. one IPv4 and one IPv6. Please use myPublicIPs() instead
     *
     * @return the public IP of this peer, if known
     */
    public String myPublicIP() {
        // if a static IP was configured, we have to return it here ...
        final String staticIP = getConfig(SwitchboardConstants.SERVER_STATICIP, "");
        if (staticIP.length() > 0)
            return staticIP;

        // otherwise we return the real IP address of this host
        final InetAddress pLIP = Domains.myPublicLocalIP();
        if (pLIP != null)
            return pLIP.getHostAddress();
        return null;
    }

    /**
     * Get all my public IPs. If there was a static IP assignment, only one,
     * that IP is returned.
     *
     * @return a set of IPs which are supposed to be my own public IPs
     */
    public Set<String> myPublicIPs() {
        // if a static IP was configured, we have to return it here ...
        final String staticIP = getConfig(SwitchboardConstants.SERVER_STATICIP, "");
        if (staticIP.length() > 0) {
            final HashSet<String> h = new HashSet<>();
            h.add(staticIP);
            return h;
        }

        final Set<String> h = new LinkedHashSet<>();
        for (final InetAddress i : Domains.myPublicIPv6()) {
            final String s = i.getHostAddress();
            if (Seed.isProperIP(s))
                h.add(Domains.chopZoneID(s));
        }
        for (final InetAddress i : Domains.myPublicIPv4()) {
            final String s = i.getHostAddress();
            if (Seed.isProperIP(s))
                h.add(Domains.chopZoneID(s));
        }
        return h;
    }

    /**
     * Gets public port. May differ from local port due to NATting. This method
     * will eventually removed once nobody used IPv4 anymore, but until then we
     * have to live with it.
     *
     * @param key
     *            original key from config (for example "port" or "port.ssl")
     * @param dflt
     *            default value which will be used if no value is found
     * @return the public port of this system on its IPv4 address
     *
     * @see #getLocalPort()
     */
    public int getPublicPort(final String key, final int dflt) {

        if (this.isConnectedViaUpnp && this.upnpPortMap.containsKey(key)) {
            return this.upnpPortMap.get(key).intValue();
        }

        // TODO: add way of setting and retrieving port for manual NAT

        return getConfigInt(key, dflt);
    }

    /**
     * Wrapper for {@link #getConfigInt(String, int)} to have a more consistent
     * API.
     *
     * Default value 8090 will be used if no value is found in configuration.
     *
     * @return the local http port of this system
     * @see #getPublicPort(String, int)
     */
    public int getLocalPort() {
        return getConfigInt(SwitchboardConstants.SERVER_PORT, 8090);
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
     *
     * @param otherConfigs
     */
    public void setConfig(final Map<String, String> otherConfigs) {
        final Iterator<Map.Entry<String, String>> i = otherConfigs.entrySet()
                .iterator();
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

    public void setConfig(final String key, final double value) {
        setConfig(key, Double.toString(value));
    }

    public void setConfig(final String key, final String value) {
        // set the value
        final String oldValue = this.configProps.put(key, value);
        if (oldValue == null || !value.equals(oldValue)) {
            saveConfig();
        }
    }

    public void setConfig(final String key, final String[] value) {
        final StringBuilder sb = new StringBuilder();
        if (value != null) for (final String s: value) sb.append(',').append(s);
        setConfig(key, sb.length() > 0 ? sb.substring(1) : "");
    }

    public void setConfig(final String key, final Set<String> value) {
        final String[] a = new String[value.size()];
        int c = 0;
        for (final String s: value) a[c++] = s;
        setConfig(key, a);
    }

    public void removeConfig(final String key) {
        this.configProps.remove(key);
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key
     *            name of the configuration parameter
     * @param dflt
     *            default value which will be used in case parameter can not be
     *            found or if it is invalid
     * @return value if the parameter or default value
     */
    public String getConfig(final String key, final String dflt) {
        // get the value
        final String s = this.configProps.get(key);

        // return value
        if (s == null) {
            return dflt;
        }
        return s;
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key
     *            name of the configuration parameter
     * @param dflt
     *            default value which will be used in case parameter can not be
     *            found or if it is invalid
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
     *
     * @param key
     *            name of the configuration parameter
     * @param dflt
     *            default value which will be used in case parameter can not be
     *            found or if it is invalid
     * @return value if the parameter or default value
     */
    public float getConfigFloat(final String key, final float dflt) {
        try {
            return Float.parseFloat(getConfig(key, Float.toString(dflt)));
        } catch (final NumberFormatException e) {
            return dflt;
        }
    }

    public boolean isConnectedViaUpnp() {

        return this.isConnectedViaUpnp;
    }

    public void setConnectedViaUpnp(final boolean isConnectedViaUpnp) {

        this.isConnectedViaUpnp = isConnectedViaUpnp;

        if (!isConnectedViaUpnp) {
            this.upnpPortMap.clear();
        }
    }

    public void setUpnpPorts(final String key, final int port) {

        this.upnpPortMap.put(key, Integer.valueOf(port));
    }

    public void removeUpnpPort(final String key) {
        this.upnpPortMap.remove(key);
    }

    /**
     * Gets a configuration parameter from the properties.
     *
     * @param key
     *            name of the configuration parameter
     * @param dflt
     *            default value which will be used in case parameter can not be
     *            found or if it is invalid
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
     *
     * @param key
     *            name of the configuration parameter
     * @param dflt
     *            default value which will be used in case parameter can not be
     *            found or if it is invalid
     * @return value if the parameter or default value
     */
    public boolean getConfigBool(final String key, final boolean dflt) {
        return Boolean.parseBoolean(getConfig(key, Boolean.toString(dflt)));
    }

    /**
     * get a configuration parameter list
     * @param key
     * @param dflt a default list
     * @return a list of strings which had been separated by comma in the setting
     */
    public String[] getConfigArray(final String key, final String dflt) {
        return CommonPattern.COMMA.split(this.getConfig(key, dflt));
    }

    /**
     * get a configuration parameter set
     * @param key name of the configuration parameter
     * @return a set of strings which had been separated by comma in the setting
     */
    public Set<String> getConfigSet(final String key) {
        final Set<String> h = new LinkedHashSet<>();
        for (String s: getConfigArray(key, "")) {s = s.trim(); if (s.length() > 0) h.add(s.trim());}
        return h;
    }

    /**
     * Create a File instance for a configuration setting specifying a path.
     *
     * @param key
     *            config key
     * @param dflt
     *            default path value, that is used when there is no value
     *            <code>key</code> in the configuration.
     * @return if the value of the setting is an absolute path String, then the
     *         returned File is derived from this setting only. Otherwise the
     *         path's file is constructed from the applications root path + the
     *         relative path setting.
     */
    public File getDataPath(final String key, final String dflt) {
        return getFileByPath(key, dflt, this.dataPath);
    }

    /**
     * return file at path from config entry "key", or fallback to default dflt
     *
     * @param key
     * @param dflt
     * @return
     */
    public File getAppPath(final String key, final String dflt) {
        return getFileByPath(key, dflt, this.appPath);
    }

    private File getFileByPath(final String key, final String dflt, final File prefix) {
        final String path = getConfig(key, dflt).replace('\\', '/');
        final File f = new File(path);
        return (f.isAbsolute() ? new File(f.getAbsolutePath()) : new File(
                prefix, path));
    }

    public Iterator<String> configKeys() {
        return this.configProps.keySet().iterator();
    }

    /**
     * write the changes to permanent storage (File)
     */
    private void saveConfig() {
        final ConcurrentMap<String, String> configPropsCopy = new ConcurrentHashMap<String, String>();
        configPropsCopy.putAll(this.configProps); // avoid concurrency problems
        FileUtils.saveMap(this.configFile, configPropsCopy, this.configComment);
    }

    /**
     * Gets configuration parameters which have been removed during
     * initialization.
     *
     * @return contains parameter name as key and parameter value as value
     */
    public ConcurrentMap<String, String> getRemoved() {
        return this.configRemoved;
    }

    /**
     * @return the default configuration properties loaded form the
     *         defaults/yacy.init file. The properties are empty when the file can
     *         not be read for some reason.
     */
    public Properties loadDefaultConfig() {
        final Properties config = new Properties();
        try (final FileInputStream fis = new FileInputStream(new File(this.appPath, "defaults/yacy.init"))) {
            config.load(fis);
        } catch (final FileNotFoundException e) {
            this.log.severe("Could not find default configuration file defaults/yacy.init.");
        } catch (final IOException | IllegalArgumentException e) {
            this.log.severe("Could not read configuration file.");
        }
        return config;
    }

    public void deployThread(final String threadName,
            final String threadShortDescription,
            final String threadLongDescription, final String threadMonitorURL,
            final BusyThread newThread, final long startupDelay) {
        deployThread(
                threadName,
                threadShortDescription,
                threadLongDescription,
                threadMonitorURL,
                newThread,
                startupDelay,
                Long.parseLong(getConfig(threadName + "_idlesleep", "1000")),
                Long.parseLong(getConfig(threadName + "_busysleep", "100")),
                Long.parseLong(getConfig(threadName + "_memprereq", "1048576")),
                Double.parseDouble(getConfig(threadName + "_loadprereq", "9.0")));
    }

    public void deployThread(final String threadName,
            final String threadShortDescription,
            final String threadLongDescription, final String threadMonitorURL,
            final BusyThread newThread, final long startupDelay,
            final long initialIdleSleep, final long initialBusySleep,
            final long initialMemoryPreRequisite,
            final double initialLoadPreRequisite) {
        if (newThread.isAlive()) {
            throw new RuntimeException(
                    "undeployed threads must not live; they are started as part of the deployment");
        }
        newThread.setStartupSleep(startupDelay);
        long x;
        try {
            x = Long.parseLong(getConfig(threadName + "_idlesleep", "novalue"));
            newThread.setIdleSleep(x);
        } catch (final NumberFormatException e) {
            newThread.setIdleSleep(initialIdleSleep);
            setConfig(threadName + "_idlesleep", initialIdleSleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_busysleep", "novalue"));
            newThread.setBusySleep(x);
        } catch (final NumberFormatException e) {
            newThread.setBusySleep(initialBusySleep);
            setConfig(threadName + "_busysleep", initialBusySleep);
        }
        try {
            x = Long.parseLong(getConfig(threadName + "_memprereq", "novalue"));
            newThread.setMemPreReqisite(x);
        } catch (final NumberFormatException e) {
            newThread.setMemPreReqisite(initialMemoryPreRequisite);
            setConfig(threadName + "_memprereq", initialMemoryPreRequisite);
        }
        try {
            final double load = Double.parseDouble(getConfig(threadName
                    + "_loadprereq", "novalue"));
            newThread.setLoadPreReqisite(load);
        } catch (final NumberFormatException e) {
            newThread.setLoadPreReqisite(initialLoadPreRequisite);
            setConfig(threadName + "_loadprereq",
                    (float) initialLoadPreRequisite);
        }
        newThread.setDescription(threadShortDescription, threadLongDescription,
                threadMonitorURL);
        this.workerThreads.put(threadName, newThread);
        // start the thread
        if (this.workerThreads.containsKey(threadName)) {
            newThread.start();
        }
    }

    public BusyThread getThread(final String threadName) {
        return this.workerThreads.get(threadName);
    }

    public void setThreadPerformance(final String threadName,
            final long idleMillis, final long busyMillis,
            final long memprereqBytes, final double loadprereq) {
        final BusyThread thread = this.workerThreads.get(threadName);
        if (thread != null) {
            setConfig(threadName + "_idlesleep",
                    thread.setIdleSleep(idleMillis));
            setConfig(threadName + "_busysleep",
                    thread.setBusySleep(busyMillis));
            setConfig(threadName + "_memprereq", memprereqBytes);
            thread.setMemPreReqisite(memprereqBytes);
            setConfig(threadName + "_loadprereq", (float) loadprereq);
            thread.setLoadPreReqisite(loadprereq);
        }
    }

    public synchronized void terminateThread(final String threadName,
            final boolean waitFor) {
        if (this.workerThreads.containsKey(threadName)) {
            ((WorkflowThread) this.workerThreads.get(threadName))
                    .terminate(waitFor);
            this.workerThreads.remove(threadName);
        }
    }

    public void intermissionAllThreads(final long pause) {
        final Iterator<String> e = this.workerThreads.keySet().iterator();
        while (e.hasNext()) {
            this.workerThreads.get(e.next()).intermission(pause);
        }
    }

    public synchronized void terminateAllThreads(final boolean waitFor) {
        Iterator<String> e = this.workerThreads.keySet().iterator();
        while (e.hasNext()) {
            ((WorkflowThread) this.workerThreads.get(e.next()))
                    .terminate(false);
        }
        if (waitFor) {
            e = this.workerThreads.keySet().iterator();
            while (e.hasNext()) {
                ((WorkflowThread) this.workerThreads.get(e.next()))
                        .terminate(true);
                e.remove();
            }
        }
    }

    public Iterator<String> /* of serverThread-Names (String) */threadNames() {
        return this.workerThreads.keySet().iterator();
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
     * Retrieve text data (e. g. config file) from file file may be an url or a
     * filename with path relative to rootPath parameter
     *
     * @param file
     *            url or filename
     * @param rootPath
     *            searchpath for file
     * @param file
     *            file to use when remote fetching fails (null if unused)
     */
    public Reader getConfigFileFromWebOrLocally(final String uri,
            final String rootPath, final File file) throws IOException,
            FileNotFoundException {
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            final String[] uris = CommonPattern.COMMA.split(uri);
            for (String netdef : uris) {
                netdef = netdef.trim();
                try (final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent)) {
                    final RequestHeader reqHeader = new RequestHeader();
                    reqHeader.put(HeaderFramework.USER_AGENT, ClientIdentification.yacyInternetCrawlerAgent.userAgent);
                    client.setHeader(reqHeader.entrySet());
                    final byte[] data = client.GETbytes(uri,
                                    getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"),
                                    getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""), false);
                    if (data == null || data.length == 0) {
                        continue;
                    }
                    // save locally in case next fetch fails
                    if (file != null) {
                        try(/* Automatically closed by this try-with-resource statement */
                            FileOutputStream f = new FileOutputStream(file);
                        ) {
                            f.write(data);
                        }
                    }
                    return new InputStreamReader(new BufferedInputStream(
                            new ByteArrayInputStream(data)));
                } catch (final Exception e) {
                    continue;
                }
            }
            if (file != null && file.exists()) {
                return new FileReader(file);
            }
            throw new FileNotFoundException();
        }
        final File f = (uri.length() > 0 && uri.startsWith("/")) ? new File(uri)
                : new File(rootPath, uri);
        if (f.exists())
            return new FileReader(f);
        throw new FileNotFoundException(f.toString());
    }

    /**
     * set/remember jetty server
     *
     * @param jettyserver
     */
    public void setHttpServer(final YaCyHttpServer jettyserver) {
        this.httpserver = jettyserver;
    }

    public YaCyHttpServer getHttpServer() {
        return this.httpserver;
    }

}
