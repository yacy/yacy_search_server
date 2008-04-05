//httpRemoteProxyConfig.java 
//-----------------------
//part of the AnomicHTTPD caching proxy
//(C) by Michael Peter Christen; mc@anomic.de
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

//You must compile this file with
//javac -classpath .:../Classes Settings_p.java
//if the shell's current path is HTROOT

package de.anomic.http;

import java.util.Arrays;
import java.util.HashSet;

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
    
    public final HashSet<String> remoteProxyAllowProxySet = new HashSet<String>();
    public final HashSet<String> remoteProxyDisallowProxySet = new HashSet<String>();

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
    
    public String[] getProxyNoProxyPatterns() {
        return this.remoteProxyNoProxyPatterns;
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
    
    public static httpRemoteProxyConfig init(
            String proxyHostName,
            int proxyHostPort
    ) {
        httpRemoteProxyConfig newConfig = new httpRemoteProxyConfig();
        
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
    
    public static httpRemoteProxyConfig init(plasmaSwitchboard sb) {
        httpRemoteProxyConfig newConfig = new httpRemoteProxyConfig();
        
        // determining if remote proxy usage is enabled
        newConfig.remoteProxyUse = sb.getConfig("remoteProxyUse", "false").equalsIgnoreCase("true");
        
        // determining if remote proxy should be used for yacy -> yacy communication
        newConfig.remoteProxyUse4Yacy = sb.getConfig("remoteProxyUse4Yacy", "true").equalsIgnoreCase("true");
        
        // determining if remote proxy should be used for ssl connections
        newConfig.remoteProxyUse4SSL = sb.getConfig("remoteProxyUse4SSL", "true").equalsIgnoreCase("true");        
        
        // reading the proxy host name
        newConfig.remoteProxyHost = sb.getConfig("remoteProxyHost", "").trim();
        if ((newConfig.remoteProxyHost == null) || (newConfig.remoteProxyHost.length() == 0)) {
            newConfig.remoteProxyUse = false;
        }
        
        // reading the proxy host port
        try {
            newConfig.remoteProxyPort = Integer.parseInt(sb.getConfig("remoteProxyPort", "3128"));
        } catch (NumberFormatException e) {
            newConfig.remoteProxyPort = 3128;
        }
        
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
        
        return newConfig;
    }
}
