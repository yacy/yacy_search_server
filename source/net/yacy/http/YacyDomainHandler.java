//
//  YacyDomainHandler
//  Copyright 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//  Copyright 2011 by Florian Richter
//  First released 13.04.2011 at http://yacy.net
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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import de.anomic.http.server.AlternativeDomainNames;

public class YacyDomainHandler extends AbstractHandler implements Handler {
	
	private AlternativeDomainNames alternativeResolvers;
	
	public void setAlternativeResolver(AlternativeDomainNames resolver) {
		this.alternativeResolvers = resolver;
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		String host = request.getServerName();
		String resolved = alternativeResolvers.resolve(host);
		if(resolved != null) {
			// split resolved into host, port and path
			int posPath = resolved.indexOf('/');
			String path = resolved.substring(posPath);
			String hostPort = resolved.substring(0, posPath);
			int posPort = hostPort.lastIndexOf(':');
			String newHost = hostPort.substring(0, posPort);
			int newPort = Integer.parseInt(hostPort.substring(posPort + 1));
			
        	RequestDispatcher dispatcher = request.getRequestDispatcher(path + target);
        	dispatcher.forward(new DomainRequestWrapper(request, newHost, newPort), response);
		}
	}
	
	private class DomainRequestWrapper extends HttpServletRequestWrapper {
		
		private String newServerName;
		private int newServerPort;

		public DomainRequestWrapper(HttpServletRequest request, String serverName, int serverPort) {
			super(request);
			this.newServerName = serverName;
			this.newServerPort = serverPort;
		}
		
		@Override
		public String getServerName() {
			return newServerName;
		}
		
		public int getServerPort() {
			return newServerPort;
		}
		
	}

}
