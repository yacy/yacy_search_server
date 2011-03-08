/**
 *  ProxySettings
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.05.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.protocol.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.AbstractHttpClient;

/**
 * settings for a remote proxy
 *
 */
public final class ProxySettings {

    // Dummy value to associate with an Object in the backing Map
    private static final Object PRESENT = new Object();
    
    public static       boolean     use = false, use4YaCy = false, use4ssl = false;
    public static       String      host = null, user = "", password = "";
    public static       int         port = 0;
    public static       String[]    noProxy  = null;
    public static final Map<String, Object> allowProxy    = new ConcurrentHashMap<String, Object>();
    public static final Map<String, Object> disallowProxy = new ConcurrentHashMap<String, Object>();
    
//    /**
//     * produce a HostConfiguration (apache object) with the proxy access information included
//     * @param apacheHttpClient
//     * @return a host configuration with proxy config if the proxy shall be used; a cloned configuration otherwise
//     */
//    public static HostConfiguration getProxyHostConfig(HttpClient apacheHttpClient) {
//        final HostConfiguration hostConfig;
//        if (!use) return null;
//        hostConfig = new HostConfiguration(apacheHttpClient.getHostConfiguration());
//        hostConfig.setProxy(host, port);
//        return hostConfig;
//    }
    
    /**
     * 
     * @return the HttpHost to be used as proxy
     */
    public static HttpHost getProxyHost() {
    	if (!use) return null;
    	return new HttpHost(host, port);
    }
    
    public static void setProxyCreds(AbstractHttpClient httpClient) {
    	if (!use) return;
    	httpClient.getCredentialsProvider().setCredentials(
    			new AuthScope(host, port),
    			new UsernamePasswordCredentials(user, password));
    }
    
    /**
     * tell if a remote proxy will be used for the given host
     * @param host
     * @return true, if the proxy shall be used for the given host
     */
    public static boolean useForHost(final String host) {
        if (!use) return false;
        if (allowProxy.containsKey(host)) return true;
        if (disallowProxy.containsKey(host)) return false;
        for (String pattern: noProxy) {
            if (host.matches(pattern)) {
                disallowProxy.put(host, PRESENT);
                return false;
            }
        }
        allowProxy.put(host, PRESENT);
        return true;
    }

}
