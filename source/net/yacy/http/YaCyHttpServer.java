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
 * all container specific code (currently Jetty 9, see {@link Jetty9HttpServerImpl})
 * stays behind this interface to ease migration to newer container versions.
 */
public interface YaCyHttpServer {

    /**
     * start the http server
     */
    void startupServer() throws Exception;

    /**
     * stop the http server
     */
    void stop() throws Exception;

    /**
     * reconnect with new port settings (after waiting milsec) - routine returns immediately
     * @param milsec wait time
     */
    void reconnect(int milsec);

    /**
     * @return true if the server runs a ssl/https connector
     */
    boolean withSSL();

    /**
     * @return the ssl/https port or -1 if not active
     */
    int getSslPort();

    /**
     * forces loginservice to reload user credentials
     * @param username
     */
    void resetUser(String username);

    /**
     * removes user from the loginservice
     * @param username
     */
    void removeUser(String username);

    /**
     * @return version string of the servlet container
     */
    String getVersion();

    /**
     * @return the number of currently active (busy) server threads
     */
    int getServerThreads();
}
