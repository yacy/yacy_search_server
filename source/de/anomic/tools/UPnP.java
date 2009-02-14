// UPnP.java
// (C) 2009 by David Wieditz; d.wieditz@gmx.de
// first published 14.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate:  $
// $LastChangedRevision:  $
// $LastChangedBy:  $
//
// LICENSE
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

package de.anomic.tools;

import java.io.IOException;
import java.net.InetAddress;

import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.UPNPResponseException;
import de.anomic.kelondro.util.Log;
import de.anomic.plasma.plasmaSwitchboard;

public class UPnP {
	
	public final static Log log = new Log("UPNP");
	private static plasmaSwitchboard sb;
	
	private final static int discoveryTimeout = 5000; // seconds to receive a response from devices
	private static InternetGatewayDevice[] IGDs = null;
	
	// mapping variables
	private final static String mappedName = "YaCy";
	private final static String mappedProtocol = "TCP";
	private static int mappedPort = 0;
	private static String localHostIP = null;;
	
	public static void setSb(plasmaSwitchboard switchboard) {
		sb = switchboard;
	}
	
	private static boolean init() {
		boolean init = true;
		try {
			IGDs = InternetGatewayDevice.getDevices(discoveryTimeout);
			localHostIP = InetAddress.getLocalHost().getHostAddress();
		} catch (IOException e) {
			init = false;
		}
		if (IGDs != null) {
			for (InternetGatewayDevice IGD : IGDs) {
				log.logInfo("found device: " + IGD.getIGDRootDevice().getFriendlyName());
			}
		} else {
			log.logInfo("no device found");
			init = false;
		}
		
		return init;
	}
	
	/**
	 * add port mapping for configured port
	 */
	public static void addPortMapping() {
		if (sb == null) return;
		addPortMapping(Integer.parseInt(sb.getConfig("port", "0")));
	}
	
	/**
	 * add TCP port mapping to all IGDs on the network<br/>
	 * latest port mapping will be removed
	 * @param port
	 */
	public static void addPortMapping(final int port) { //TODO: don't map already mapped port again
		if (port < 1) return;
		if (mappedPort > 0) deletePortMapping(); // delete old mapping first
		if (mappedPort == 0 && ((IGDs != null && localHostIP != null) || init())) {
			mappedPort = port;
			for (InternetGatewayDevice IGD : IGDs) {
				try {
					boolean mapped = IGD.addPortMapping(mappedName, null, mappedPort, mappedPort, localHostIP, 0, mappedProtocol);
					String msg = "port " + mappedPort + " on device "+ IGD.getIGDRootDevice().getFriendlyName();
					if (mapped) log.logInfo("mapped " + msg);
					else log.logWarning("could not map " + msg);
				} catch (IOException e) {} catch (UPNPResponseException e) { log.logSevere("mapping error: " + e.getMessage()); }
			}
		}
	}
	
	/**
	 * delete current port mapping
	 */
	public static void deletePortMapping() {
		if (mappedPort > 0 && IGDs != null && localHostIP != null) {
			for (InternetGatewayDevice IGD : IGDs) {
				try {
					boolean unmapped = IGD.deletePortMapping(null, mappedPort, mappedProtocol);
					String msg = "port " + mappedPort + " on device "+ IGD.getIGDRootDevice().getFriendlyName();
					if (unmapped) log.logInfo("unmapped " + msg);
					else log.logWarning("could not unmap " + msg);
				} catch (IOException e) {} catch (UPNPResponseException e) { log.logSevere("unmapping error: " + e.getMessage()); }
			}
			mappedPort = 0; // reset mapped port
		}
	}
	
	/**
	 * @return mapped port or 0
	 */
	public static int getMappedPort() {
		return mappedPort;
	}
			
	public static void main(String[] args) {
		deletePortMapping(); // nothing
		addPortMapping(40000); // map
		addPortMapping(40000); // unmap, map
		deletePortMapping(); // unmap
		deletePortMapping(); // nothing
	}

}
