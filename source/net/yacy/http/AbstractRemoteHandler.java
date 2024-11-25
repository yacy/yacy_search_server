//
//  AbstractRemoteHandler
//  Copyright 2011 by Florian Richter
//  First released 2011 at https://yacy.net
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;

import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;

/**
 * abstract jetty http handler
 * only request to remote hosts (proxy requests) are processed by derived classes 
 */
abstract public class AbstractRemoteHandler extends ConnectHandler implements Handler {
	
    protected Switchboard sb = null;
    private final Set<String> localVirtualHostNames = new HashSet<String>(); // list for quick check for req to local peer
    
    @Override
    protected void doStart() throws Exception {
    	super.doStart();
        this.sb = Switchboard.getSwitchboard();
        this.localVirtualHostNames.add("localhost");
        this.localVirtualHostNames.add(sb.getConfig("fileHost", "localpeer"));

        // Add some other known local host names
        // The remote DNS sometimes takes very long when it is waiting for timeout, therefore we do this concurrently
        new Thread(AbstractRemoteHandler.class.getSimpleName() + ".doStart") {
            @Override
            public void run() {
                for (InetAddress localInetAddress : Domains.myPublicIPv4()) {
                    if (localInetAddress != null) {
                        if (!localVirtualHostNames.contains(localInetAddress.getHostName())) {
                            localVirtualHostNames.add(localInetAddress.getHostName());
                            localVirtualHostNames.add(localInetAddress.getHostAddress());  // same as getServer().getURI().getHost()
                        }

                        if (!localVirtualHostNames.contains(localInetAddress.getCanonicalHostName())) {
                            localVirtualHostNames.add(localInetAddress.getCanonicalHostName());
                        }
                    }
                }
                for (InetAddress localInetAddress : Domains.myPublicIPv6()) {
                    if (localInetAddress != null) {
                        if (!localVirtualHostNames.contains(localInetAddress.getHostName())) {
                            localVirtualHostNames.add(localInetAddress.getHostName());
                            localVirtualHostNames.add(localInetAddress.getHostAddress());  // same as getServer().getURI().getHost()
                        }

                        if (!localVirtualHostNames.contains(localInetAddress.getCanonicalHostName())) {
                            localVirtualHostNames.add(localInetAddress.getCanonicalHostName());
                        }
                    }
                }
                if (sb.peers != null) {
                    localVirtualHostNames.addAll(sb.peers.mySeed().getIPs());
                    localVirtualHostNames.add(sb.peers.myAlternativeAddress()); // add the "peername.yacy" address
                    localVirtualHostNames.add(sb.peers.mySeed().getHexHash() + ".yacyh"); // bugfix by P. Dahl
                }
            }
        }.start();
    }
	
    abstract public void handleRemote(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException;

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {

        String host = request.getHeader("Host");
        if (host == null) return; // no proxy request, continue processing by handlers
        String hostOnly = Domains.stripToHostName(host);
        
        if (localVirtualHostNames.contains(hostOnly)) return; // no proxy request (quick check), continue processing by handlers        
        if (Domains.isLocal(hostOnly, null)) return; // no proxy, continue processing by handlers
        if (sb.peers.myIPs().contains(hostOnly)) { // remote access to my external IP, continue processing by handlers
            localVirtualHostNames.addAll(sb.peers.myIPs()); // not available on init, add it now for quickcheck
            return;
        }
        
        InetAddress resolvedIP = Domains.dnsResolve(hostOnly); // during testing isLocal() failed to resolve domain against publicIP  
        if (resolvedIP != null && sb.myPublicIPs().contains(resolvedIP.getHostAddress())) {
            localVirtualHostNames.add(resolvedIP.getHostName()); // remember resolved hostname
            //localVirtualHostNames.add(resolved.getHostAddress()); // might change ?
            return;  
        }

        // from here we can assume it is a proxy request
        // should check proxy use permission        
 
        if (!Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.PROXY_TRANSPARENT_PROXY, false)) {
            // transparent proxy not swiched on
            response.sendError(HttpServletResponse.SC_FORBIDDEN,"proxy use not allowed (see System Administration -> Advanced Settings -> Proxy Access Settings -> Transparent Proxy; switched off).");
            baseRequest.setHandled(true);
            return;
        }
        
        final String remoteHost = request.getRemoteHost();
        if (!proxyippatternmatch(remoteHost)) {
            // TODO: handle proxy account
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "proxy use not granted for IP " + remoteHost + " (see Advanced Settings -> Proxy Access Settings -> IP-Number filter).");
            baseRequest.setHandled(true);
            return;
        }
        
        // check the blacklist
        if (Switchboard.urlBlacklist.isListed(BlacklistType.PROXY, hostOnly.toLowerCase(Locale.ROOT), request.getPathInfo())) {
        	response.sendError(HttpServletResponse.SC_FORBIDDEN,
        			"URL '" + hostOnly + "' blocked by yacy proxy (blacklisted)");
            baseRequest.setHandled(true);
            return;
        }

        if (request.getMethod().equalsIgnoreCase(HeaderFramework.METHOD_CONNECT)) {
            // will be done by the ConnectHandler
        	super.handle(target, baseRequest, request, response);
        	return;
        }
        
        handleRemote(target, baseRequest, request, response);

    }
    
    /**
     * helper for proxy IP config pattern check
     */
    private boolean proxyippatternmatch(final String key) {
        // the cfgippattern is a comma-separated list of patterns
        // each pattern may contain one wildcard-character '*' which matches anything
        final String cfgippattern = Switchboard.getSwitchboard().getConfig("proxyClient", "*");
        if (cfgippattern.equals("*")) {
            return true;
        }
        final StringTokenizer st = new StringTokenizer(cfgippattern, ",");
        String pattern;
        while (st.hasMoreTokens()) {
            pattern = st.nextToken();
            if (key.matches(pattern)) {
                return true;
            }
        }
        return false;
    }
}
