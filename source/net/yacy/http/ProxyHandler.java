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
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.document.TextParser;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;

import net.yacy.crawler.data.Cache;
import net.yacy.crawler.retrieval.Response;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.server.http.MultiOutputStream;

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

		RequestHeader proxyHeaders = convertHeaderFromJetty(request);
                final String httpVer = request.getHeader(HeaderFramework.CONNECTION_PROP_HTTP_VER);
                setViaHeader (proxyHeaders, httpVer);
		proxyHeaders.remove(RequestHeader.KEEP_ALIVE);
		proxyHeaders.remove(HeaderFramework.CONTENT_LENGTH);

		final HTTPClient client = new HTTPClient(ClientIdentification.yacyProxyAgent);
		int timeout = 60000;
		client.setTimout(timeout);
		client.setHeader(proxyHeaders.entrySet());
		client.setRedirecting(false);
		// send request
		try {
			String queryString = request.getQueryString()!=null ? "?" + request.getQueryString() : "";
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
			HttpResponse responseHeader = client.getHttpResponse();
            final ResponseHeader responseHeaderLegacy = new ResponseHeader(200, client.getHttpResponse().getAllHeaders());
            
			cleanResponseHeader(responseHeader);
			
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
    			convertHeaderToJetty(responseHeader, response);
    			//response.setContentType(responseHeader.getFirstHeader(HeaderFramework.CONTENT_TYPE).getValue());
    			response.setStatus(responseHeader.getStatusLine().getStatusCode());
    			
    			client.writeTo(response.getOutputStream());
            }
		} catch(SocketException se) {
			throw new ServletException("Socket Exception: " + se.getMessage());
		} finally {
			client.finish();
		}
		
        // we handled this request, break out of handler chain
		baseRequest.setHandled(true);
	}
        
    private void setViaHeader(final HeaderFramework header, final String httpVer) {
        if (!sb.getConfigBool("proxy.sendViaHeader", true)) return;
        
        final String myAddress = (sb.peers == null) ? null : sb.peers.myAlternativeAddress();
        if (myAddress != null) {

            // getting header set by other proxies in the chain
            final StringBuilder viaValue = new StringBuilder(80);
            if (header.containsKey(HeaderFramework.VIA)) {
                viaValue.append(header.get(HeaderFramework.VIA));
            }
            if (viaValue.length() > 0) {
                viaValue.append(", ");
            }

            // appending info about this peer
            viaValue
                    .append(httpVer).append(" ")
                    .append(myAddress).append(" ")
                    .append("(YaCy ").append(yacyBuildProperties.getVersion()).append(")");

            // storing header back
            header.put(HeaderFramework.VIA, viaValue.toString());
        }
    }

}
