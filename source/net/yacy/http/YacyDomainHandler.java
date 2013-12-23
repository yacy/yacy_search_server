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
import java.util.Enumeration;
import java.util.Vector;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.protocol.Domains;
import net.yacy.server.http.AlternativeDomainNames;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;


/**
 * handling of request to virtual ".yacy" domain determines public adress from
 * seedlist and forwards modified/wrapped request to it
 */
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
        if (resolved != null) {
            // split resolved into host, port and path
            String path;
            String hostPort;
            int posPath = resolved.indexOf('/');
            if (posPath >= 0) {
                path = resolved.substring(posPath);
                hostPort = resolved.substring(0, posPath);
            } else {
                path = "";
                hostPort = resolved;
            }
            int posPort = hostPort.lastIndexOf(':');
            String newHost;
            int newPort;
            if (posPort >= 0) {
                newHost = hostPort.substring(0, posPort);
                newPort = Integer.parseInt(hostPort.substring(posPort + 1));
            } else {
                newHost = hostPort;
                newPort = 80;
            }
            if (newHost.equals(alternativeResolvers.myIP())) return;  
            if (Domains.isLocal(newHost, null)) return;
            RequestDispatcher dispatcher = request.getRequestDispatcher(path + target);
            dispatcher.forward(new DomainRequestWrapper(request, newHost, newPort), response);
            baseRequest.setHandled(true);
        }
    }

    private class DomainRequestWrapper extends HttpServletRequestWrapper {

        final private String newServerName;
        final private int newServerPort;

        public DomainRequestWrapper(HttpServletRequest request, String serverName, int serverPort) {
            super(request);
            this.newServerName = serverName;
            this.newServerPort = serverPort;
        }

        @Override
        public String getServerName() {
            return newServerName;
        }

        @Override
        public int getServerPort() {
            return newServerPort;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer buf = new StringBuffer(this.getScheme() + "://" + newServerName + ":" + newServerPort + this.getPathInfo());
            return buf;
        }

        @Override
        public String getHeader(String name) {
            if (name.equals("Host")) {
                return newServerName + (newServerPort != 80 ? ":" + newServerPort : "");
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name.equals("Host")) {
                Vector<String> header = new Vector<String>();
                header.add(newServerName + (newServerPort != 80 ? ":" + newServerPort : ""));
                return header.elements();
            }
            return super.getHeaders(name);
        }
    }

}
