//
//  YaCyHttpServer
//  Copyright 2011 by Florian Richter
//  First released 13.04.2011 at https://yacy.net
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

/**
 * Servlet-container neutral interface to YaCy's embedded http server.
 * This is the only view the rest of the code base has on the server;
 * all container-specific code in {@link Jetty12HttpServer} and
 * {@link Jetty12ProxyChain} stays behind this interface.
 */
public interface YaCyHttpServer {

    /** Start all configured connectors and handlers before returning. */
    void startupServer() throws Exception;

    /** Stop all connectors and handlers and wait for complete termination. */
    void stop() throws Exception;

    /**
     * Apply current HTTP and HTTPS port settings asynchronously after a delay.
     * Existing connectors are reused; implementations must not rebuild the handler graph.
     * @param milsec non-negative delay before applying current configuration
     */
    void reconnect(int milsec);

    /**
     * @return true when a usable HTTPS connector was configured
     */
    boolean withSSL();

    /**
     * @return the bound HTTPS port, or -1 when HTTPS is not active
     */
    int getSslPort();

    /**
     * Evict and immediately reload the named administrator identity from configuration.
     * @param username
     */
    void resetUser(String username);

    /**
     * Evict the named administrator identity from the container login cache.
     * @param username
     */
    void removeUser(String username);

    /**
     * @return human-readable name and version of the servlet container
     */
    String getVersion();

    /**
     * @return current number of non-idle container worker threads
     */
    int getServerThreads();
}
