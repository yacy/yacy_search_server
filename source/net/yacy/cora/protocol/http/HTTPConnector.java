/**
 *  HttpConnector
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.05.2010 at http://yacy.net
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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.MultiProtocolURI;

import org.apache.http.entity.mime.content.ContentBody;

/**
 * This Connector is a convenience class to access the protocol-specific http client class.
 */
public class HTTPConnector {
    
    private static final Map<String, HTTPConnector> cons = new ConcurrentHashMap<String, HTTPConnector>();
    private String userAgent;
    
    private HTTPConnector(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public static final HTTPConnector getConnector(String userAgent) {
        HTTPConnector c = cons.get(userAgent);
        if (c != null) return c;
        c = new HTTPConnector(userAgent);
        return c;
    }
    
    /**
     * send data to the server named by vhost
     * 
     * @param url address of the server
     * @param vhost name of the server at address which should respond
     * @param post data to send (name-value-pairs)
     * @param timeout in milliseconds
     * @return response body
     * @throws IOException
     */
    public byte[] post(final MultiProtocolURI url, final int timeout, final String vhost, LinkedHashMap<String, ContentBody> post) throws IOException {
		final HTTPClient client = new HTTPClient();
		client.setTimout(timeout);
		client.setUserAgent(this.userAgent);
		client.setHost(vhost);
		
		return client.POSTbytes(url.toNormalform(false, false), post);
	}

}
