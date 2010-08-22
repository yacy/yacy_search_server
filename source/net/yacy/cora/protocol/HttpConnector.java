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


package net.yacy.cora.protocol;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map.Entry;
//import java.util.List;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.http.HTTPClient;

//import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.http.entity.mime.content.ContentBody;

import de.anomic.crawler.retrieval.HTTPLoader;
// import de.anomic.http.client.Client;
//import de.anomic.http.server.HeaderFramework;
//import de.anomic.http.server.RequestHeader;
//import de.anomic.http.server.ResponseContainer;

public class HttpConnector {

//    /**
//     * send data to the server named by vhost
//     * 
//     * @param address address of the server
//     * @param vhost name of the server at address which should respond
//     * @param post data to send (name-value-pairs)
//     * @param timeout in milliseconds
//     * @return response body
//     * @throws IOException
//     */
//    public static byte[] wput(final String url, final String vhost, final List<Part> post, final int timeout) throws IOException {
//        return wput(url, vhost, post, timeout, false);
//    }
    
//    /**
//     * send data to the server named by vhost
//     * 
//     * @param address address of the server
//     * @param vhost name of the server at address which should respond
//     * @param post data to send (name-value-pairs)
//     * @param timeout in milliseconds
//     * @param gzipBody send with content gzip encoded
//     * @return response body
//     * @throws IOException
//     */
//    public static byte[] wput(final String url, final String vhost, final List<Part> post, final int timeout, final boolean gzipBody) throws IOException {
//        final RequestHeader header = new RequestHeader();
//        header.put(HeaderFramework.USER_AGENT, HTTPLoader.yacyUserAgent);
//        header.put(HeaderFramework.HOST, vhost);
//        final de.anomic.http.client.Client client = new de.anomic.http.client.Client(timeout, header);
//        
//        ResponseContainer res = null;
//        byte[] content = null;
//        try {
//            // send request/data
//            res = client.POST(url, post, gzipBody);
//            content = res.getData();
//        } finally {
//            if(res != null) {
//                // release connection
//                res.closeStream();
//            }
//        }
//        return content;
//    }
    
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
    public static byte[] wput(final String url, final String vhost, LinkedHashMap<String,ContentBody> post, final int timeout) throws IOException {
		final HTTPClient client = new HTTPClient();
		client.setTimout(timeout);
		client.setUserAgent(HTTPLoader.yacyUserAgent);
		client.setHost(vhost);
		
		return client.POSTbytes(url, post);
	}
        
    
    /**
     * get data from the server named by url
     * 
     * @param url address of the server
     * @param timeout in milliseconds
     * @return response body
     * @throws IOException
     */
    public static byte[] wget(final MultiProtocolURI url, final int timeout) throws IOException {
        return wget(url.toNormalform(false, false), url.getHost(), timeout);
    }
	
    /**
     * get data from the server named by vhost
     * 
     * @param url address of the server
     * @param vhost name of the server at address which should respond
     * @param timeout in milliseconds
     * @return response body
     * @throws IOException
     */
    public static byte[] wget(final String url, final String vhost, final int timeout) throws IOException {
		final HTTPClient client = new HTTPClient();
        client.setTimout(timeout);
		client.setUserAgent(HTTPLoader.yacyUserAgent);
        client.setHost(vhost);
        
        return client.GETbytes(url);
	}
    
    /**
     * get data from the server named by vhost
     * 
     * @param url address of the server
     * @param entrys of RequestHeader
     * @param timeout in milliseconds
     * @return response body
     * @throws IOException
     */
    public static byte[] wget(final String url, final Set<Entry<String, String>> entrys, final int timeout) throws IOException {
    	return wget(url, entrys, timeout, null);
    }
    
    /**
     * get data from the server named by url
     * 
     * @param url address of the server
     * @param entrys of RequestHeader
     * @param timeout in milliseconds
     * @param vhost name of the server at address which should respond
     * @return response body
     * @throws IOException
     */
    public static byte[] wget(final String url, final Set<Entry<String, String>> entrys, final int timeout, final String vhost) throws IOException {
    	final HTTPClient client = new HTTPClient();
    	client.setHeader(entrys);
        client.setTimout(timeout);
        client.setHost(vhost);
        
        return client.GETbytes(url);
    }
    
//    public static byte[] wget(final String url, final String vhost, final int timeout) throws IOException {
//        final RequestHeader header = new RequestHeader();
//        header.put(HeaderFramework.USER_AGENT, HTTPLoader.yacyUserAgent);
//        header.put(HeaderFramework.HOST, vhost);
//        final de.anomic.http.client.Client client = new de.anomic.http.client.Client(timeout, header);
//        
//        ResponseContainer res = null;
//        byte[] content = null;
//        try {
//            // send request/data
//            res = client.GET(url);
//            content = res.getData();
//        } finally {
//            if(res != null) {
//                // release connection
//                res.closeStream();
//            }
//        }
//        return content;
//    }

}
