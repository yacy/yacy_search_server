//
//  ProxyHandler
//  Copyright 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//  Copyright 2011 by Florian Richter
//  First released 2011 at http://yacy.net
//  
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//  
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//  
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program in the file lgpl21.txt
//  If not, see <http://www.gnu.org/licenses/>.
//

package net.yacy.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.TextParser;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.retrieval.Response;
import net.yacy.server.http.HTTPDProxyHandler;
import net.yacy.server.http.MultiOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.IO;

/**
 * jetty http handler
 * proxies request, caches responses and adds urls to crawler
 */
public class ProxyHandler extends AbstractRemoteHandler implements Handler {
	   
	public static RequestHeader convertHeaderFromJetty(HttpServletRequest request) {
		RequestHeader result = new RequestHeader();
		Enumeration<String> headerNames = request.getHeaderNames();
		while(headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			Enumeration<String> headers = request.getHeaders(headerName);
			while(headers.hasMoreElements()) {
				String header = headers.nextElement();
				result.add(headerName, header);
			}
		}
		return result;
	}
	
	static void convertHeaderToJetty(HttpResponse in, HttpServletResponse out) {
		for(Header h: in.getAllHeaders()) {
			out.addHeader(h.getName(), h.getValue());
		}
	}
	
	private void cleanResponseHeader(HttpResponse headers) {
		headers.removeHeaders(HeaderFramework.CONTENT_ENCODING);
		headers.removeHeaders(HeaderFramework.CONTENT_LENGTH);
	}

	@Override
	public void handleRemote(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {

            if (request.getMethod().equalsIgnoreCase(HeaderFramework.METHOD_CONNECT)) {
                handleConnect(request, response);
                return;
            }
            
            RequestHeader proxyHeaders = convertHeaderFromJetty(request);
            setProxyHeaderForClient(request, proxyHeaders);

            final HTTPClient client = new HTTPClient(ClientIdentification.yacyProxyAgent);
            int timeout = 10000;
            client.setTimout(timeout);
            client.setHeader(proxyHeaders.entrySet());
            client.setRedirecting(false);
            // send request
            try {
                String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
                String url = request.getRequestURL().toString() + queryString;
                if (request.getMethod().equals(HeaderFramework.METHOD_GET)) {
                    client.GET(url);
                } else if (request.getMethod().equals(HeaderFramework.METHOD_POST)) {
                    client.POST(url, request.getInputStream(), request.getContentLength());
                } else if (request.getMethod().equals(HeaderFramework.METHOD_HEAD)) {
                    client.HEADResponse(url);
                } else {
                    throw new ServletException("Unsupported Request Method");
                }
                HttpResponse clientresponse = client.getHttpResponse();
                int statusCode = clientresponse.getStatusLine().getStatusCode();
                final ResponseHeader responseHeaderLegacy = new ResponseHeader(statusCode, clientresponse.getAllHeaders());

                if (responseHeaderLegacy.isEmpty()) {
                    throw new SocketException(clientresponse.getStatusLine().toString());
                }
                cleanResponseHeader(clientresponse);

                // TODO: is this fast, if not, use value from ProxyCacheHandler
                DigestURL digestURI = new DigestURL(url);
                ResponseHeader cachedResponseHeader = Cache.getResponseHeader(digestURI.hash());

            // the cache does either not exist or is (supposed to be) stale
            long sizeBeforeDelete = -1;
            if (cachedResponseHeader != null) {
                // delete the cache
                ResponseHeader rh = Cache.getResponseHeader(digestURI.hash());
                if (rh != null && (sizeBeforeDelete = rh.getContentLength()) == 0) {
                    byte[] b = Cache.getContent(new DigestURL(url).hash());
                    if (b != null) sizeBeforeDelete = b.length;
                }
                Cache.delete(digestURI.hash());
                // log refresh miss 
            }
            
            // reserver cache entry
            final net.yacy.crawler.retrieval.Request yacyRequest = new net.yacy.crawler.retrieval.Request(
        			null, 
                    digestURI, 
                    null, //requestHeader.referer() == null ? null : new DigestURI(requestHeader.referer()).hash(), 
                    "", 
                    responseHeaderLegacy.lastModified(),
                    sb.crawler.defaultProxyProfile.handle(),
                    0, 
                    0, 
                    0,
                    0); //sizeBeforeDelete < 0 ? 0 : sizeBeforeDelete);
            final Response yacyResponse = new Response(
                    yacyRequest,
                    null,
                    responseHeaderLegacy,
                    sb.crawler.defaultProxyProfile,
                    false
            );
            
            final String storeError = yacyResponse.shallStoreCacheForProxy();
            final boolean storeHTCache = yacyResponse.profile().storeHTCache();
            final String supportError = TextParser.supports(yacyResponse.url(), yacyResponse.getMimeType());

            if (
                    /*
                     * Now we store the response into the htcache directory if
                     * a) the response is cacheable AND
                     */
                    (storeError == null) &&
                    /*
                     * b) the user has configured to use the htcache OR
                     * c) the content should be indexed
                     */
                    ((storeHTCache) || (supportError != null))
            ) {
                // we don't write actually into a file, only to RAM, and schedule writing the file.
            	int l = responseHeaderLegacy.size();
                final ByteArrayOutputStream byteStream = new ByteArrayOutputStream((l < 32) ? 32 : l);                
                final OutputStream toClientAndMemory = new MultiOutputStream(new OutputStream[] {response.getOutputStream(), byteStream});
                convertHeaderToJetty(clientresponse, response);
 		response.setStatus(statusCode);
                client.writeTo(toClientAndMemory);
                
             // cached bytes
                byte[] cacheArray;
                if (byteStream.size() > 0) {
                    cacheArray = byteStream.toByteArray();
                } else {
                    cacheArray = null;
                }
                //if (log.isFine()) log.logFine(reqID +" writeContent of " + url + " produced cacheArray = " + ((cacheArray == null) ? "null" : ("size=" + cacheArray.length)));

                if (sizeBeforeDelete == -1) {
                    // totally fresh file
                	yacyResponse.setContent(cacheArray);
                    try {
                        Cache.store(yacyResponse.url(), yacyResponse.getResponseHeader(), cacheArray);
                        sb.toIndexer(yacyResponse);
                    } catch (IOException e) {
                        //log.logWarning("cannot write " + response.url() + " to Cache (1): " + e.getMessage(), e);
                    }
                    // log cache miss
                } else if (cacheArray != null && sizeBeforeDelete == cacheArray.length) {
                	// TODO: what should happen here?
                    // before we came here we deleted a cache entry
                    cacheArray = null;
                    //cacheManager.push(cacheEntry); // unnecessary update
                    // log cache refresh fail miss
                } else {
                    // before we came here we deleted a cache entry
                	yacyResponse.setContent(cacheArray);
                    try {
                        Cache.store(yacyResponse.url(), yacyResponse.getResponseHeader(), cacheArray);
                        sb.toIndexer(yacyResponse);
                    } catch (IOException e) {
                        //log.logWarning("cannot write " + response.url() + " to Cache (2): " + e.getMessage(), e);
                    }
                    // log refresh cache miss
                }

            } else {
                // no caching
                /*if (log.isFine()) log.logFine(reqID +" "+ url.toString() + " not cached." +
                        " StoreError=" + ((storeError==null)?"None":storeError) +
                        " StoreHTCache=" + storeHTCache +
                        " SupportError=" + supportError);*/
    			convertHeaderToJetty(clientresponse, response);
    			response.setStatus(statusCode);
    			
    			client.writeTo(response.getOutputStream());
            }
		} catch(SocketException se) {
			throw new ServletException("Socket Exception: " + se.getMessage());
		} finally {
			client.finish();
		}
		
        // we handled this request, break out of handler chain
        logProxyAccess(request);
	baseRequest.setHandled(true);
        }
       
    /**
     * adds specific header elements for the connection of the internal
     * httpclient to the remote server according to local config
     *
     * @param header header für http client (already preset with headers from
     * original ServletRequest
     * @param origServletRequest original request/header
     */
    private void setProxyHeaderForClient(final HttpServletRequest origServletRequest, final HeaderFramework header) {
    
        header.remove(RequestHeader.KEEP_ALIVE);
        header.remove(HeaderFramework.CONTENT_LENGTH);
        
        // setting the X-Forwarded-For header
        if (sb.getConfigBool("proxy.sendXForwardedForHeader", true)) {
            String ip = origServletRequest.getRemoteAddr();
            if (!Domains.isThisHostIP(ip)) { // if originator is local host no user ip to forward (= request from localhost)
                header.put(HeaderFramework.X_FORWARDED_FOR, origServletRequest.getRemoteAddr());
            }
        }        

        String httpVersion = origServletRequest.getProtocol();
        HTTPDProxyHandler.modifyProxyHeaders(header, httpVersion);
    }
    
    public final static synchronized void logProxyAccess(HttpServletRequest request) {

        final StringBuilder logMessage = new StringBuilder(80);

        // Timestamp
        logMessage.append(GenericFormatter.SHORT_SECOND_FORMATTER.format(new Date()));
        logMessage.append(' ');

        // Remote Host
        final String clientIP = request.getRemoteAddr();
        logMessage.append(clientIP);
        logMessage.append(' ');

        // Method
        final String requestMethod = request.getMethod();
        logMessage.append(requestMethod);
        logMessage.append(' ');

        // URL
        logMessage.append(request.getRequestURL());
        final String requestArgs = request.getQueryString();
         if (requestArgs != null) {
            logMessage.append("?").append(requestArgs);
        }

        HTTPDProxyHandler.proxyLog.fine(logMessage.toString());

    }
    
    public void handleConnect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // taken from Jetty ProxyServlet                
        String uri = request.getRequestURI();

        String port = "";
        String host = "";

        int c = uri.indexOf(':');
        if (c >= 0) {
            port = uri.substring(c + 1);
            host = uri.substring(0, c);
            if (host.indexOf('/') > 0) {
                host = host.substring(host.indexOf('/') + 1);
}
        }

        // TODO - make this async!
        InetSocketAddress inetAddress = new InetSocketAddress(host, Integer.parseInt(port));

        // if (isForbidden(HttpMessage.__SSL_SCHEME,addrPort.getHost(),addrPort.getPort(),false))
        // {
        // sendForbid(request,response,uri);
        // }
        // else
        {
            InputStream in = request.getInputStream();
            OutputStream out = response.getOutputStream();

            Socket socket = new Socket(inetAddress.getAddress(), inetAddress.getPort());

            response.setStatus(200);
            response.setHeader("Connection", "close");
            response.flushBuffer();
            // TODO prevent real close!

            IO.copyThread(socket.getInputStream(), out);
            IO.copy(in, socket.getOutputStream());
        }
    } 
}
