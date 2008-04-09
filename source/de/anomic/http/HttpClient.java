// HttpClient.java
// (C) 2008 by Daniel Raap; danielr@users.berlios.de
// first published 2.4.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2008-03-14 01:16:04 +0100 (Fr, 14 Mrz 2008) $
// $LastChangedRevision: 4558 $
// $LastChangedBy: orbiter $
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
package de.anomic.http;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import de.anomic.server.logging.serverLog;

/**
 * Client who does http requests
 * 
 * some methods must be implemented (the "socket-layer")
 */
public abstract class HttpClient {
    private static final String systemOST;
    static {
        // provide system information for client identification
        final String loc = generateLocation();
        systemOST = System.getProperty("os.arch", "no-os-arch") + " " + System.getProperty("os.name", "no-os-name") +
                " " + System.getProperty("os.version", "no-os-version") + "; " + "java " +
                System.getProperty("java.version", "no-java-version") + "; " + loc;
    }

    /**
     * generating the location string
     * 
     * @return
     */
    public static String generateLocation() {
        String loc = System.getProperty("user.timezone", "nowhere");
        final int p = loc.indexOf("/");
        if (p > 0) {
            loc = loc.substring(0, p);
        }
        loc = loc + "/" + System.getProperty("user.language", "dumb");
        return loc;
    }

    /**
     * @return the systemOST
     */
    public static String getSystemOST() {
        return systemOST;
    }

    /**
     * "This method can be used for obtaining metainformation about the entity implied by the request without
     * transferring the entity-body itself" [RFC 2616, sect. 9.4]
     * 
     * @param uri
     * @return response header
     * @throws IOException if data cannot be read
     */
    public abstract HttpResponse HEAD(String uri) throws IOException;

    /**
     * "The GET method means retrieve whatever information [...] is identified by the Request-URI" [RFC 2616, sect. 9.3]
     * 
     * @param uri
     * @return response body
     * @throws IOException if data cannot be read
     */
    public abstract HttpResponse GET(String uri) throws IOException;

    /**
     * "Providing a block of data [...] to a data-handling process;" [RFC 2616, sect. 9.5]
     * 
     * @param uri
     * @param nameDataPairs
     * @return response body
     * @throws IOException if data cannot be read
     */
    public abstract HttpResponse POST(String uri, Map<String, ?> nameDataPairs) throws IOException;

    /**
     * "for use with a proxy that can dynamically switch to being a tunnel" [RFC 2616, sect. 9.9]
     * 
     * @param host
     * @param port
     * @return
     * @throws IOException
     */
    public abstract HttpResponse CONNECT(String host, int port) throws IOException;

    /**
     * use proxyConfig to establish the connection
     * 
     * @param proxyConfig
     */
    public abstract void setProxy(httpRemoteProxyConfig proxyConfig);

    /**
     * sets the header for all coming requests
     * 
     * @param header may be null
     */
    public abstract void setHeader(httpHeader header);

    /**
     * sets the timeout in milliseconds
     * 
     * @param timeout
     */
    public abstract void setTimeout(int timeout);

    /**
     * gives the user-agent used by this http-client
     * 
     * @return
     */
    public abstract String getUserAgent();

    /**
     * for easy access
     * 
     * @see date2String(Date)
     * @param date
     * @return
     */
    public static String dateString(final Date date) {
        return JakartaCommonsHttpClient.date2String(date);
    }

    /**
     * Gets a page (as raw bytes)
     * 
     * @param uri
     * @return
     */
    public static byte[] wget(final String uri) {
        return wget(uri, null, null);
    }

    /**
     * Gets a page (as raw bytes) addressing vhost at host in uri
     * 
     * @param uri
     * @param vhost used if host in uri cannot be resolved (yacy tld)
     * @return
     */
    public static byte[] wget(final String uri, final String vhost) {
        return wget(uri, null, vhost);
    }
    
    /**
     * Gets a page (as raw bytes) aborting after timeout
     * 
     * @param uri
     * @param timeout in milliseconds
     * @return
     */
    public static byte[] wget(final String uri, final int timeout) {
        return wget(uri, null, null, timeout);
    }

    /**
     * Gets a page (as raw bytes) with specified header
     * 
     * @param uri
     * @param header
     * @return
     */
    public static byte[] wget(final String uri, final httpHeader header) {
        return wget(uri, header, null);
    }

    /**
     * Gets a page (as raw bytes) addressing vhost at host in uri with specified header
     * 
     * @param uri
     * @param header
     * @param vhost
     * @return
     * @assert uri != null
     */
    public static byte[] wget(final String uri, httpHeader header, final String vhost) {
        return wget(uri, header, vhost, 10000);
    }
    
    /**
     * Gets a page (as raw bytes) addressing vhost at host in uri with specified header and timeout
     * 
     * @param uri
     * @param header
     * @param vhost
     * @param timeout in milliseconds
     * @return
     */
    public static byte[] wget(final String uri, httpHeader header, final String vhost, int timeout) {
        assert uri != null : "precondition violated: uri != null";
        final HttpClient client = new JakartaCommonsHttpClient(timeout, null, null);

        // set header
        header = addHostHeader(header, vhost);
        client.setHeader(header);

        // do the request
        try {
            final HttpResponse response = client.GET(uri);
            return response.getData();
        } catch (final IOException e) {
            serverLog.logWarning("HTTPC", "wget(" + uri + ") failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * adds a Host-header to the header if vhost is not null
     * 
     * @param header
     * @param vhost
     * @return
     */
    private static httpHeader addHostHeader(httpHeader header, final String vhost) {
        if (vhost != null) {
            if (header != null) {
                header = new httpHeader();
            }
            // set host-header
            header.add(httpHeader.HOST, vhost);
        }
        return header;
    }

    /**
     * Gets a page-header
     * 
     * @param uri
     * @return
     */
    public static httpHeader whead(final String uri) {
        return whead(uri, null);
    }

    /**
     * Gets a page-header
     * 
     * @param uri
     * @param header request header
     * @return
     */
    public static httpHeader whead(final String uri, final httpHeader header) {
        final de.anomic.http.HttpClient client = new JakartaCommonsHttpClient(10000, header, null);
        try {
            final HttpResponse response = client.HEAD(uri);
            return response.getResponseHeader();
        } catch (final IOException e) {
            serverLog.logWarning("HTTPC", "whead(" + uri + ") failed: " + e.getMessage());
        }
        return null;
    }
}
