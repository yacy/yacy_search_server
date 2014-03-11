//
//  ProxyCacheHandler
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

import java.io.IOException;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;

import net.yacy.crawler.data.Cache;
import net.yacy.crawler.retrieval.Response;

/**
 * jetty http handler serves pages from cache if available and valid
 */
public class ProxyCacheHandler extends AbstractRemoteHandler implements Handler {

    private void handleRequestFromCache(@SuppressWarnings("unused") HttpServletRequest request, HttpServletResponse response, ResponseHeader cachedResponseHeader, byte[] content) throws IOException {

        // TODO: check if-modified
        for (Entry<String, String> entry : cachedResponseHeader.entrySet()) {
            response.addHeader(entry.getKey(), entry.getValue());
        }
        response.setStatus(HttpServletResponse.SC_NON_AUTHORITATIVE_INFORMATION);
        response.getOutputStream().write(content);
        // we handled this request, break out of handler chain
    }
    
    @Override
    public void handleRemote(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (request.getMethod().equals("GET")) {
            String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
            DigestURL url = new DigestURL(request.getRequestURL().toString() + queryString);
            ResponseHeader cachedResponseHeader = Cache.getResponseHeader(url.hash());

            if (cachedResponseHeader != null) {
                RequestHeader proxyHeaders = ProxyHandler.convertHeaderFromJetty(request);
                // TODO: this convertion is only necessary
                final net.yacy.crawler.retrieval.Request yacyRequest = new net.yacy.crawler.retrieval.Request(
                        null,
                        url,
                        proxyHeaders.referer() == null ? null : new DigestURL(proxyHeaders.referer().toString()).hash(),
                        "",
                        cachedResponseHeader.lastModified(),
                        sb.crawler.defaultProxyProfile.handle(),
                        0,
                        0,
                        0);

                final Response cachedResponse = new Response(
                        yacyRequest,
                        proxyHeaders,
                        cachedResponseHeader,
                        sb.crawler.defaultProxyProfile,
                        false);
                byte[] cacheContent = Cache.getContent(url.hash());
                if (cacheContent != null && cachedResponse.isFreshForProxy()) {
                    handleRequestFromCache(request, response, cachedResponseHeader, cacheContent);
                    baseRequest.setHandled(true);
                }
            }

        }
    }

}
