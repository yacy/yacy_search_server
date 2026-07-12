/**
 *  MonitorFilter
 *  Copyright 2014 by Sebastian Gaebel
 *  First released 15.05.2014 at https://yacy.net
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

package net.yacy.http.servlets;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;

/**
 * Records incoming server requests into {@link ConnectionInfo} (displayed on
 * Connections_p.html) and rejects remote requests with http status 503 when
 * the configured maximum number of server connections is reached.
 *
 * This is a plain servlet filter (former Jetty handler MonitorHandler); the
 * tracking entry of a connection is removed on connection close by a
 * servlet-container specific listener, see Jetty12HttpServer.
 */
public class MonitorFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    /**
     * @return the ConnectionInfo id of the tcp connection identified by client address and port
     */
    public static int connectionId(String remoteAddr, final int remotePort) {
        // the servlet API reports IPv6 addresses in bracketed form ("[::1]"), the
        // connection endpoint in plain form: normalize to the plain form
        if (remoteAddr.startsWith("[") && remoteAddr.endsWith("]")) {
            remoteAddr = remoteAddr.substring(1, remoteAddr.length() - 1);
        }
        return (remoteAddr + ":" + remotePort).hashCode();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        final HttpServletRequest hrequest = (HttpServletRequest) request;
        final String query = hrequest.getQueryString();
        final ConnectionInfo info = new ConnectionInfo(
                hrequest.getScheme(),
                RequestHeader.client(hrequest) + ":" + hrequest.getRemotePort(),
                hrequest.getMethod() + " " + hrequest.getRequestURI() + (query == null ? "" : "?" + query),
                connectionId(hrequest.getRemoteAddr(), hrequest.getRemotePort()),
                System.currentTimeMillis(),
                -1);

        // a keep-alive connection reuses the id: remove a previous entry to show the latest request
        ConnectionInfo.removeServerConnection(info);
        ConnectionInfo.addServerConnection(info);

        if (ConnectionInfo.isServerCountReached()
                && !Domains.isLocal(hrequest.getRemoteAddr(), null)) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "max. server connections reached (increase /PerformanceQueues_p.html -> httpd Session Pool).");
            return;
        }
        chain.doFilter(request, response);
    }
}
