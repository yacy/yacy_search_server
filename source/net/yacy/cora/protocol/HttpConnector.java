/**
 *  HttpConnector
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.05.2010 at http://yacy.net
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file COPYING.LESSER.
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package net.yacy.cora.protocol;

import java.io.IOException;
import java.util.List;

import org.apache.commons.httpclient.methods.multipart.Part;

import de.anomic.crawler.retrieval.HTTPLoader;
import de.anomic.http.client.Client;
import de.anomic.http.client.RemoteProxyConfig;
import de.anomic.http.server.HeaderFramework;
import de.anomic.http.server.RequestHeader;
import de.anomic.http.server.ResponseContainer;

public class HttpConnector {

    /**
     * send data to the server named by vhost
     * 
     * @param address address of the server
     * @param vhost name of the server at address which should respond
     * @param post data to send (name-value-pairs)
     * @param timeout in milliseconds
     * @return response body
     * @throws IOException
     */
    public static byte[] wput(final String url, final String vhost, final List<Part> post, final int timeout) throws IOException {
        return wput(url, vhost, post, timeout, false);
    }
    
    /**
     * send data to the server named by vhost
     * 
     * @param address address of the server
     * @param vhost name of the server at address which should respond
     * @param post data to send (name-value-pairs)
     * @param timeout in milliseconds
     * @param gzipBody send with content gzip encoded
     * @return response body
     * @throws IOException
     */
    public static byte[] wput(final String url, final String vhost, final List<Part> post, final int timeout, final boolean gzipBody) throws IOException {
        final RequestHeader header = new RequestHeader();
        header.put(HeaderFramework.USER_AGENT, HTTPLoader.yacyUserAgent);
        header.put(HeaderFramework.HOST, vhost);
        final Client client = new Client(timeout, header);
        client.setProxy(proxyConfig());
        
        ResponseContainer res = null;
        byte[] content = null;
        try {
            // send request/data
            res = client.POST(url, post, gzipBody);
            content = res.getData();
        } finally {
            if(res != null) {
                // release connection
                res.closeStream();
            }
        }
        return content;
    }


    private static final RemoteProxyConfig proxyConfig() {
        final RemoteProxyConfig p = RemoteProxyConfig.getRemoteProxyConfig();
        return ((p != null) && (p.useProxy()) && (p.useProxy4Yacy())) ? p : null;
    }
}
