/**
 *  MonitorHandler
 *  Copyright 2014 by Sebastian Gaebel
 *  First released 15.05.2014 at http://yacy.net
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

package net.yacy.http;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.Domains;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class MonitorHandler extends AbstractHandler {
	
	private final Connection.Listener remover = new Connection.Listener() {

		@Override
		public void onClosed(Connection c) {
			ConnectionInfo.removeServerConnection(c.hashCode());
		}

		@Override
		public void onOpened(Connection c) {
		}
	};

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
		
		final Connection connection = baseRequest.getHttpChannel().getEndPoint().getConnection();
		HttpURI uri = baseRequest.getHttpURI();
		final ConnectionInfo info = new ConnectionInfo(
				baseRequest.getProtocol(),
				baseRequest.getRemoteAddr() + ":" + baseRequest.getRemotePort(),
				baseRequest.getMethod() + " " + uri.getPathQuery(),
				connection.hashCode(),
				baseRequest.getTimeStamp(),
				-1);
		
		if (ConnectionInfo.getServerConnections().contains(info)) {
			ConnectionInfo.removeServerConnection(info);
		} else {
			connection.addListener(remover);
		}
		ConnectionInfo.addServerConnection(info);
		
		if (ConnectionInfo.isServerCountReached()) {
			if (Domains.isLocal(baseRequest.getRemoteAddr(), baseRequest.getRemoteInetSocketAddress().getAddress())) return;
			response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,"max. server connections reached (increase /PerformanceQueues_p.html -> httpd Session Pool).");
            baseRequest.setHandled(true);
		}
	}
}
