// serverPortForwardingUpnp.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


package de.anomic.server.portForwarding.upnp;

import java.io.IOException;
import java.net.InetAddress;

import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.ActionResponse;
import net.sbbi.upnp.messages.UPNPResponseException;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.server.portForwarding.serverPortForwarding;

public class serverPortForwardingUpnp implements serverPortForwarding {
    
    private InternetGatewayDevice gateway;
    private String localHost;
    private int localHostPort;    
    private String externalAddress = null;
    private serverLog log;    
    
    public serverPortForwardingUpnp() {
        super();
        this.log = new serverLog("PORT_FORWARDING_UPNP");        
    }
    
    public void connect() throws IOException {
        try {
            if (this.gateway != null)
                throw new IOException("Session already connected");

            int timeout = 8000;
            try {
                // trying to get all internet gateways on the local network
                this.log.logInfo("Trying to find all available internet gateways.");               
                InternetGatewayDevice[] IGDs = InternetGatewayDevice.getDevices(timeout);
                boolean mapped = false;
                if ( IGDs != null ) {
                    for ( int i = 0; i < IGDs.length; i++ ) {
                        
                        this.gateway = IGDs[i];
                        this.log.logInfo("Found device " + this.gateway.getIGDRootDevice().getModelName() );                                      
                        this.log.logInfo("NAT table size is " + this.gateway.getNatTableSize() );
                        
                        // now let's open the port
                        this.log.logInfo("Adding port mapping ...");
                        mapped = this.gateway.addPortMapping(
                                "YaCy port forwarding", 
                                null, 
                                this.localHostPort, 
                                this.localHostPort,
                                this.localHost, 
                                0, 
                                "TCP"
                        );
                        
                        if ( mapped ) {
                            this.log.logInfo("Gateway port " + this.localHostPort + " mapped to " + this.localHost );
                            this.log.logInfo("Current mappings count is " + this.gateway.getNatMappingsCount() );                            
                            mapped = isConnected();
                        }                        
                        if (mapped) break; 
                    }
                    if (!mapped) {
                        throw new IOException("Unable to configure the port mapping.");
                    }
                } else {
                    throw new IOException("No internet gateway device found.");
                }

            } catch ( IOException ex ) {
                throw new Exception("IOException occured during discovery or ports mapping. " + ex.getMessage());
            } catch( UPNPResponseException respEx ) {
                throw new Exception("UPNP device unhappy " + respEx.getDetailErrorCode() + " " + respEx.getDetailErrorDescription() );
            }
        } catch (Exception e) {
            this.gateway = null;
            this.log.logSevere("Unable to connect to remote port forwarding host. ",e);
            throw new IOException(e.getMessage());            
        }
    }
    
    public boolean routerAvailable(int timeout) {       
        try {
            InternetGatewayDevice[] IGDs = InternetGatewayDevice.getDevices(timeout);
            return (IGDs != null) && (IGDs.length > 0);
        } catch (Exception e) {
            this.log.logSevere("Unable to determine available routers: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() throws IOException {
        if (this.gateway == null) throw new IOException("No connection established.");
        
        boolean unmapped;
        try {
            this.log.logInfo("Trying to disable port mapping ...");
            unmapped = this.gateway.deletePortMapping( null, this.localHostPort, "TCP" );
            if ( unmapped ) {
                this.log.logInfo("Port mapping disabled");
              }            
        } catch (UPNPResponseException e) {
            new IOException("Unable to disable port forwarding. " + e.getMessage());
        }
    }

    public String getHost() {
        if (this.gateway == null) return "";
        if (this.externalAddress == null) {
            try {
                this.externalAddress = this.gateway.getExternalIPAddress();
            } catch (Exception e) {
                this.log.logWarning("Unable to get the external address of the gateway");
            }
        }
        return this.externalAddress;
    }

    public int getPort() {
        return this.localHostPort;
    }

    public void init(serverSwitch<?> switchboard, String localHost, int localPort)
            throws Exception {
        try {
            this.log.logFine("Initializing port forwarding via UPnP ...");
            
            if (localHost.equals("0.0.0.0")) {
                this.localHost = InetAddress.getLocalHost().getHostAddress();
            } else {
                this.localHost = localHost;
            }
            this.localHostPort = localPort;            
            
            // checking if all needed libs are availalbe
            String javaClassPath = System.getProperty("java.class.path");
            if (javaClassPath.indexOf("sbbi-upnplib") == -1) {
                throw new IllegalStateException("Missing library.");
            }                        
            
            // setting the proper xml parser
//            if (System.getProperty("javax.xml.parsers.DocumentBuilderFactory", "").equals("")) {
//                System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "");
//            }
            
        } catch (Exception e) {
            this.log.logSevere("Unable to initialize port forwarding.",e);
            throw e;
        }
    }

    public boolean isConnected() {
        if (this.gateway == null) return false;
        try {
            ActionResponse resp = this.gateway.getSpecificPortMappingEntry( null, this.localHostPort, "TCP" );
            return ( resp != null );
        } catch (Exception e) {
            this.log.logSevere("Unable to determine the connection status");
            return false;
        }
    }

    public boolean reconnect() throws IOException {
        if (!this.isConnected()) {
            this.log.logFine("Trying to reconnect to port forwarding host.");
            this.disconnect();
            this.connect();
            return this.isConnected();
        }
        return false;
    }

}
