/**
 *  ProxySettings
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.05.2010 at https://yacy.net
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
// Modified 16 dec 2025 by smokingwheels

package net.yacy.cora.protocol.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import net.yacy.cora.util.ConcurrentLog;

import org.apache.http.protocol.HttpContext;

public final class ProxySettings {

    private static final Object PRESENT = new Object();

    public static enum Protocol {
        HTTP, HTTPS
    }

    private static boolean use = false, use4ssl = false;
    public static String host = null, user = "", password = "";
    public static int port = 0;
    public static String[] noProxy = new String[0];

    public static final Map<String, Object> allowProxy    = new ConcurrentHashMap<>();
    public static final Map<String, Object> disallowProxy = new ConcurrentHashMap<>();

    public static void setProxyUse4HTTP(boolean use4http0) {
        use = use4http0;
    }

    public static void setProxyUse4HTTPS(boolean use4https0) {
        use4ssl = use4https0;
    }

    public static HttpHost getProxyHost() {
        if (!use) return null;
        return new HttpHost(host, port);
    }

 public static HttpRoutePlanner RoutePlanner = new HttpRoutePlanner() {

    @Override
    public HttpRoute determineRoute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException {

        final String th = (target != null) ? target.getHostName() : null;

        if (th != null && th.endsWith(".i2p")) {
            ConcurrentLog.info("ProxySettings",
                "I2P PROXY USED FOR: " + th + " -> 127.0.0.1:4444");

            HttpHost i2pProxy = new HttpHost("127.0.0.1", 4444);
            boolean isSSL = "https".equalsIgnoreCase(target.getSchemeName());
            return new HttpRoute(target, null, i2pProxy, isSSL);
        }

        if (use) {
            final Protocol protocol =
                "https".equalsIgnoreCase(target.getSchemeName())
                    ? Protocol.HTTPS
                    : Protocol.HTTP;

            if (useForHost(target.getHostName(), protocol)) {
                return new HttpRoute(
                    target, null, getProxyHost(), protocol == Protocol.HTTPS);
            }
        }

        return new HttpRoute(target); // direct
    }
};


    // =================================================
    // CREDENTIALS PROVIDER
    // =================================================
    public static final CredentialsProvider CredsProvider = new CredentialsProvider() {

        @Override
        public void clear() {}

        @Override
        public Credentials getCredentials(AuthScope scope) {
            if (host != null && host.equals(scope.getHost()) && port == scope.getPort()) {
                return new UsernamePasswordCredentials(user, password);
            }
            return null;
        }

        @Override
        public void setCredentials(AuthScope scope, Credentials creds) {}
    };

    // =================================================
    // PROXY FILTER LOGIC
    // =================================================
    public static boolean useForHost(final String host, Protocol protocol) {
        if (!use) return false;
        if (protocol == Protocol.HTTPS && !use4ssl) return false;
        if (allowProxy.containsKey(host)) return true;
        if (disallowProxy.containsKey(host)) return false;

        for (String pattern : noProxy) {
            if (host.matches(pattern)) {
                disallowProxy.put(host, PRESENT);
                return false;
            }
        }

        allowProxy.put(host, PRESENT);
        return true;
    }
}
