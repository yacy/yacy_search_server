//
//  AbstractRemoteHandler
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
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.yacy.cora.protocol.Domains;

import net.yacy.search.Switchboard;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * abstract jetty http handler
 * only request to remote hosts (proxy requests) are processed by derived classes 
 */
abstract public class AbstractRemoteHandler extends AbstractHandler implements Handler {
	
    protected Switchboard sb = null;
    private List<String> localVirtualHostNames; // list for quick check for req to local peer
    
    @Override
    protected void doStart() {
        sb = Switchboard.getSwitchboard();

        localVirtualHostNames = new LinkedList<String>();
        localVirtualHostNames.add("localhost");
        localVirtualHostNames.add(sb.getConfig("fileHost", "localpeer"));

        // add some other known local host names
        InetAddress localInetAddress = Domains.myPublicLocalIP();
        if (localInetAddress != null) {
            if (!localVirtualHostNames.contains(localInetAddress.getHostName())) {
                localVirtualHostNames.add(localInetAddress.getHostName());
            }

            if (!localVirtualHostNames.contains(localInetAddress.getCanonicalHostName())) {
                localVirtualHostNames.add(localInetAddress.getCanonicalHostName());
            }
        }
        if (sb.peers != null) {
            localVirtualHostNames.add(sb.peers.mySeed().getIP());
            localVirtualHostNames.add(sb.peers.myAlternativeAddress()); // add the "peername.yacy" address
        }
    }
	
    abstract public void handleRemote(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException;

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
        String host = request.getHeader("Host");
        if (host == null) return; // no proxy request, continue processing by handlers
        int hostSplitPos = host.indexOf(':');
        String hostOnly = hostSplitPos < 0 ? host : host.substring(0, hostSplitPos);

        if (localVirtualHostNames.contains(hostOnly)) return; // no proxy request (quick check), continue processing by handlers        
        if (Domains.isLocal(hostOnly, null)) return; // no proxy, continue processing by handlers
        if (hostOnly.startsWith(sb.peers.myIP())) { // remote access to my external IP, continue processing by handlers
            localVirtualHostNames.add(sb.peers.myIP()); // not available on init, add it now for quickcheck
            return;
        }
        handleRemote(target, baseRequest, request, response);
    }

}
