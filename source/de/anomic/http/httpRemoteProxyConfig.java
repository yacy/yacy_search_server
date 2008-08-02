//httpRemoteProxyConfig.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//this file was contributed by Martin Thelian
//$LastChangedDate$ 
//$LastChangedBy$
//$LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

//You must compile this file with
//javac -classpath .:../Classes Settings_p.java
//if the shell's current path is HTROOT

package de.anomic.http;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.anomic.plasma.plasmaSwitchboard;

public final class httpRemoteProxyConfig {
    
    /*
     * Remote Proxy configuration
     */
    private boolean  remoteProxyUse;
    private boolean  remoteProxyUse4Yacy;
    private boolean  remoteProxyUse4SSL;
    
    private String   remoteProxyHost;
    private int      remoteProxyPort;
    private String   remoteProxyUser;
    private String   remoteProxyPwd;
    
    private String[] remoteProxyNoProxyPatterns = null;
    
    private final Set<String> remoteProxyAllowProxySet = new HashSet<String>();
    private final Set<String> remoteProxyDisallowProxySet = new HashSet<String>();
    
    /**
     * *The* remote Proxy configuration
     */
    private static httpRemoteProxyConfig remoteProxyConfig = null;

    /**
     * @param remoteProxyConfig the remoteProxyConfig to set
     */
    public static synchronized void setRemoteProxyConfig(final httpRemoteProxyConfig remoteProxyConfig) {
        httpRemoteProxyConfig.remoteProxyConfig = remoteProxyConfig;
    }

    /**
     * @return the remoteProxyConfig
     */
    public static httpRemoteProxyConfig getRemoteProxyConfig() {
        return remoteProxyConfig;
    }

    /**
     * creates a new remoteProxyConfig for given proxy
     * 
     * @param proxyHostName
     * @param proxyHostPort
     * @return
     */
    public static httpRemoteProxyConfig createRemoteProxyConfig(
            final String proxyHostName,
            final int proxyHostPort
    ) {
        final httpRemoteProxyConfig newConfig = new httpRemoteProxyConfig();
        
        newConfig.remoteProxyUse  = true;
        newConfig.remoteProxyUse4SSL = true;
        newConfig.remoteProxyUse4Yacy = true;
        newConfig.remoteProxyPort = proxyHostPort;
        newConfig.remoteProxyHost = proxyHostName;
        if ((newConfig.remoteProxyHost == null) || (newConfig.remoteProxyHost.length() == 0)) {
            newConfig.remoteProxyUse = false;
        }
        
        return newConfig;
    }

    /**
     * initializes the global remoteProxyConfig with values from plasmaSwitchboard (config)
     * 
     * @param sb
     * @return
     */
    public static void init(final plasmaSwitchboard sb) {
        // reading the proxy host name
        final String proxyHostName = sb.getConfig("remoteProxyHost", "").trim();
        // reading the proxy host port
        int proxyHostPort;
        try {
            proxyHostPort = Integer.parseInt(sb.getConfig("remoteProxyPort", "3128"));
        } catch (final NumberFormatException e) {
            proxyHostPort = 3128;
        }
        // create new config
        final httpRemoteProxyConfig newConfig = createRemoteProxyConfig(proxyHostName, proxyHostPort);
        
        // determining if remote proxy usage is enabled
        newConfig.remoteProxyUse = newConfig.remoteProxyUse && sb.getConfigBool("remoteProxyUse", false);
        
        // determining if remote proxy should be used for yacy -> yacy communication
        newConfig.remoteProxyUse4Yacy = sb.getConfig("remoteProxyUse4Yacy", "true").equalsIgnoreCase("true");
        
        // determining if remote proxy should be used for ssl connections
        newConfig.remoteProxyUse4SSL = sb.getConfig("remoteProxyUse4SSL", "true").equalsIgnoreCase("true");        
        
        newConfig.remoteProxyUser = sb.getConfig("remoteProxyUser", "").trim();
        newConfig.remoteProxyPwd = sb.getConfig("remoteProxyPwd", "").trim();
        
        // determining addresses for which the remote proxy should not be used
        final String remoteProxyNoProxy = sb.getConfig("remoteProxyNoProxy","").trim();
        newConfig.remoteProxyNoProxyPatterns = remoteProxyNoProxy.split(",");
        // trim split entries
        int i = 0;
        for(final String pattern: newConfig.remoteProxyNoProxyPatterns) {
            newConfig.remoteProxyNoProxyPatterns[i] = pattern.trim();
            i++;
        }
        
        setRemoteProxyConfig(newConfig);
        sb.getLog().logConfig("Remote proxy configuration:\n" + getRemoteProxyConfig().toString());
    }

    public boolean useProxy() {
        return this.remoteProxyUse;
    }
    
    public boolean useProxy4Yacy() {
        return this.remoteProxyUse4Yacy;
    }
    
    public boolean useProxy4SSL() {
        return this.remoteProxyUse4SSL;
    }
    
    public String getProxyHost() {
        return this.remoteProxyHost;
    }
    
    public int getProxyPort() {
        return this.remoteProxyPort;
    }
    
    public String getProxyUser() {
        return this.remoteProxyUser;
    }
    
    public String getProxyPwd() {
        return this.remoteProxyPwd;
    }
    
    /**
     * checks if server matches the noProxyPattern and caches result as (Dis-)AllowedHost
     * 
     * @param server
     * @return
     */
    public boolean matchesProxyNoProxyPatterns(final String server) {
        boolean noProxy = false;
        for (final String pattern :remoteProxyNoProxyPatterns) {
            if (server.matches(pattern)) {
                noProxy = true;
                break;
            }
        }
        // set either remoteProxyAllowProxySet or remoteProxyDisallowProxySet accordingly
        if(noProxy) {
            addDisallowedHost(server);
        } else {
            addAllowedHost(server);
        }
        return noProxy;
    }

    public String toString() {
        final StringBuilder toStrBuf = new StringBuilder(50);
        
        toStrBuf
        .append("Status: ").append(this.remoteProxyUse?"ON":"OFF").append(" | ")
        .append("Host: ");
        if ((this.remoteProxyUser != null) && (this.remoteProxyUser.length() > 0)) {
            toStrBuf.append(this.remoteProxyUser)
                    .append("@");
        }
        toStrBuf
        .append((this.remoteProxyHost==null)?"unknown":this.remoteProxyHost).append(":").append(this.remoteProxyPort).append(" | ")
        .append("Usage: HTTP");
        if (this.remoteProxyUse4Yacy) toStrBuf.append(" YACY");
        if (this.remoteProxyUse4SSL) toStrBuf.append(" SSL");
        toStrBuf.append(" | ")
        .append("No Proxy for: ")
        .append(Arrays.toString(this.remoteProxyNoProxyPatterns));
        
        
        return toStrBuf.toString();
    }

    /**
     * @param host
     * @return
     */
    public boolean isHostAllowedToUseProxy(final String host) {
        return remoteProxyAllowProxySet.contains(host);
    }

    /**
     * @param host
     * @return
     */
    public boolean isHostNotAllowedToUseProxy(final String host) {
        return remoteProxyDisallowProxySet.contains(host);
    }
    
    /**
     * @param host
     */
    public void addAllowedHost(final String host) {
        remoteProxyAllowProxySet.add(host);
    }
    
    /**
     * @param host
     */
    public void addDisallowedHost(final String host) {
        remoteProxyDisallowProxySet.add(host);
    }

    /**
     * checks if proxy-config is allowed and not denied
     * 
     * @param server
     * @param remProxyConfig
     * @return proxy which should be used or null if no proxy should be used for server
     */
    public boolean useForHost(final String server) {
        // possible remote proxy
        // check no-proxy rule
        boolean useProxy;
        if (useProxy()) {
            useProxy = true;
            if (!isHostAllowedToUseProxy(server)) {
                if (isHostNotAllowedToUseProxy(server)) {
                    useProxy = false;
                } else {
                    // analyse remoteProxyNoProxy;
                    if(matchesProxyNoProxyPatterns(server)) {
                        useProxy = false;
                    }
                }
            }
        } else {
            // not enabled
            useProxy = false;
        }
    
        return useProxy;
    }

    /**
     * checks if proxy-config exists for server (is allowed and not denied)
     * 
     * @param server
     * @return proxy which should be used or null if no proxy should be used
     */
    public static httpRemoteProxyConfig getProxyConfigForHost(final String server) {
        final httpRemoteProxyConfig proxyConfig = getRemoteProxyConfig();
        if(proxyConfig == null || !proxyConfig.useForHost(server)) {
            return null;
        }
        return proxyConfig;
    }

    /**
     * gets proxy config for full adress (without protocol) ie. "localhost:8080/path/file.html"
     * 
     * @param address full address with host and optionally port or path <host>:<port>/<path>
     * @return null if no proxy should be used
     */
    public static httpRemoteProxyConfig getProxyConfigForURI(final String address) {
        // host goes to port
        int p = address.indexOf(":");
        if (p < 0) {
            // no port, so host goes to path
            p = address.indexOf("/");
        }
        // host should have minimum 1 character ;) 
        if(p > 0) {
            final String server = address.substring(0, p);
            return getProxyConfigForHost(server);
        }
        // check if full address is in allow/deny-list
        return getProxyConfigForHost(address);
    }
}
