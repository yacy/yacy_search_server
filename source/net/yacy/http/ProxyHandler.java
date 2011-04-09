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

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.util.FileUtils;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class ProxyHandler extends AbstractHandler implements Handler {
	
	private List<String> localVirtualHostNames;
	
	protected void doStart() {
		localVirtualHostNames = new LinkedList<String>();
		localVirtualHostNames.add("localpeer");
		localVirtualHostNames.add("localhost");
	}
	
	private RequestHeader convertHeaderFromJetty(HttpServletRequest request) {
		RequestHeader result = new RequestHeader();
		@SuppressWarnings("unchecked")
		Enumeration<String> headerNames = request.getHeaderNames();
		while(headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			@SuppressWarnings("unchecked")
			Enumeration<String> headers = request.getHeaders(headerName);
			while(headers.hasMoreElements()) {
				String header = headers.nextElement();
				result.add(headerName, header);
			}
		}
		return result;
	}
	
	private void convertHeaderToJetty(HttpResponse in, HttpServletResponse out) {
		for(Header h: in.getAllHeaders()) {
			out.addHeader(h.getName(), h.getValue());
		}
	}
	
	private void cleanResponseHeader(HttpResponse headers) {
		headers.removeHeaders("Content-Encoding");
		headers.removeHeaders("Content-Length");
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		String host = request.getHeader("Host");
		if(host == null) return; // no proxy request, continue processing by handlers
		int hostSplitPos = host.indexOf(':');
		String hostOnly =  hostSplitPos<0 ? host : host.substring(0, hostSplitPos);
		
		if(localVirtualHostNames.contains(hostOnly)) return; // no proxy request, continue processing by handlers

		RequestHeader proxyHeaders = convertHeaderFromJetty(request);
		proxyHeaders.add(RequestHeader.VIA, "YaCy");
		proxyHeaders.remove(RequestHeader.KEEP_ALIVE);
		proxyHeaders.remove(RequestHeader.CONTENT_LENGTH);

		final HTTPClient client = new HTTPClient();
		int timeout = 60000;
		client.setTimout(timeout);
		client.setHeader(proxyHeaders.entrySet());
		client.setRedirecting(false);
		// send request
		try {
			String queryString = request.getQueryString()!=null ? "?" + request.getQueryString() : "";
			if (request.getMethod().equals("GET")) {
				client.GET(request.getRequestURL().toString() + queryString);
			} else if (request.getMethod().equals("POST")) {
				client.POST(request.getRequestURL().toString() + queryString, request.getInputStream(), request.getContentLength());
			} else if (request.getMethod().equals("HEAD")) {
				client.HEADResponse(request.getRequestURL().toString() + queryString);
			} else {
				throw new ServletException("Unsupported Request Method");
			}
			HttpResponse responseHeader = client.getHttpResponse();
			cleanResponseHeader(responseHeader);
			convertHeaderToJetty(responseHeader, response);
			//response.setContentType(responseHeader.getFirstHeader(HeaderFramework.CONTENT_TYPE).getValue());
			response.setStatus(responseHeader.getStatusLine().getStatusCode());
			
			client.writeTo(response.getOutputStream());
		} catch(SocketException se) {
			throw new ServletException("Socket Exception: " + se.getMessage());
		} finally {
			client.finish();
		}
		
        // we handled this request, break out of handler chain
		Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
		base_request.setHandled(true);
	}

}
