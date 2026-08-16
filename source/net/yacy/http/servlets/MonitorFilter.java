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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
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
 * the configured maximum number of active incoming requests is reached.
 *
 * This is a plain servlet filter (former Jetty handler MonitorHandler). A
 * synchronous request is removed when its filter chain exits; an asynchronous
 * request is removed by a listener when its asynchronous lifecycle finishes.
 */
public class MonitorFilter implements Filter {

    /** Supplies a distinct tracking identity for every incoming request. */
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
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
                NEXT_REQUEST_ID.getAndIncrement(),
                System.currentTimeMillis(),
                -1);

        final boolean limitExempt = Domains.isLocal(hrequest.getRemoteAddr(), null);
        if (!ConnectionInfo.tryAddServerConnection(info, limitExempt)) {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "maximum active HTTP requests reached (increase Incoming HTTP Requests on "
                            + "/PerformanceQueues_p.html).");
            return;
        }

        final AtomicBoolean removed = new AtomicBoolean();
        final Runnable removeTracking = () -> {
            if (removed.compareAndSet(false, true)) {
                ConnectionInfo.removeServerConnection(info);
            }
        };
        boolean asyncCleanupRegistered = false;
        try {
            chain.doFilter(request, response);

            if (hrequest.isAsyncStarted()) {
                hrequest.getAsyncContext().addListener(new AsyncListener() {
                    @Override
                    public void onComplete(final AsyncEvent event) {
                        removeTracking.run();
                    }

                    @Override
                    public void onTimeout(final AsyncEvent event) {
                        removeTracking.run();
                    }

                    @Override
                    public void onError(final AsyncEvent event) {
                        removeTracking.run();
                    }

                    @Override
                    public void onStartAsync(final AsyncEvent event) {
                        event.getAsyncContext().addListener(this);
                    }
                });
                asyncCleanupRegistered = true;
            }
        } finally {
            if (!asyncCleanupRegistered) {
                removeTracking.run();
            }
        }
    }
}
