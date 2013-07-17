// UPnP.java
// (C) 2009 by David Wieditz; d.wieditz@gmx.de
// first published 14.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.upnp.DiscoveryAdvertisement;
import net.yacy.upnp.DiscoveryEventHandler;
import net.yacy.upnp.devices.UPNPRootDevice;
import net.yacy.upnp.impls.InternetGatewayDevice;
import net.yacy.upnp.messages.UPNPResponseException;

public class UPnP {
	
	public final static ConcurrentLog log = new ConcurrentLog("UPNP");
	private static Switchboard sb = Switchboard.getSwitchboard();
	
	private final static int discoveryTimeout = 5000; // seconds to receive a response from devices
	private static InternetGatewayDevice[] IGDs = null;
	
	// mapping variables
	private final static String mappedName = "YaCy";
	private final static String mappedProtocol = "TCP";
	private static int mappedPort = 0;
	private static String localHostIP = null;
	
	/* Discovery message sender IP /10.100.100.2 does not match device description IP /192.168.1.254 skipping message,
	set the net.yacy.upnp.ddos.matchip system property to false to avoid this check
	static {
		System.setProperty("net.yacy.upnp.ddos.matchip", "false");
	} */
	
	public static boolean setIGDs(InternetGatewayDevice[] igds) {
		if(IGDs == null) {
			IGDs = igds; // set only once to prevent many same devices by advertisement events
			return true;
		}
		return false;
	}
	
	private static boolean init() {
		boolean init = true;
		try {
			if (IGDs == null) IGDs = InternetGatewayDevice.getDevices(discoveryTimeout);
			localHostIP = Domains.myPublicLocalIP().getHostAddress();
			if (localHostIP.startsWith("127.")) log.warn("found odd local address: " + localHostIP + "; UPnP may fail");
		} catch (final IOException e) {
			init = false;
		}
		if (IGDs != null) {
			for (InternetGatewayDevice IGD : IGDs) {
				log.info("found device: " + IGD.getIGDRootDevice().getFriendlyName());
			}
		} else {
			log.info("no device found");
			init = false;
			log.info("listening for device");
			Listener.register();
		}
		
		return init;
	}
	
	private static String getRemoteHost() {
		if (sb == null) return null;
		return sb.getConfig(SwitchboardConstants.UPNP_REMOTEHOST, "");
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
			for (InternetGatewayDevice IGD : IGDs) {
				try {
					boolean mapped = IGD.addPortMapping(mappedName, getRemoteHost(), port, port, localHostIP, 0, mappedProtocol);
					String msg = "port " + port + " on device "+ IGD.getIGDRootDevice().getFriendlyName();
					if (mapped) {
						log.info("mapped " + msg);
						mappedPort = port;
					}
					else log.warn("could not map " + msg);
				} catch (final IOException e) {} catch (final UPNPResponseException e) { log.severe("mapping error: " + e.getMessage()); }
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
					boolean unmapped = IGD.deletePortMapping(getRemoteHost(), mappedPort, mappedProtocol);
					String msg = "port " + mappedPort + " on device "+ IGD.getIGDRootDevice().getFriendlyName();
					if (unmapped) log.info("unmapped " + msg);
					else log.warn("could not unmap " + msg);
				} catch (final IOException e) {} catch (final UPNPResponseException e) { log.severe("unmapping error: " + e.getMessage()); }
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
	
	/**
	 * register devices that do not respond to discovery but advertise themselves
	 */
	public static class Listener {
		
		private final static Handler handler = new Handler();
		private final static String devicetype = "urn:schemas-upnp-org:device:InternetGatewayDevice:1";
		
		public static void register() {
			try {
				DiscoveryAdvertisement.getInstance().registerEvent(DiscoveryAdvertisement.EVENT_SSDP_ALIVE, devicetype, handler);
//				DiscoveryAdvertisement.getInstance().registerEvent(DiscoveryAdvertisement.EVENT_SSDP_BYE_BYE, devicetype, handler);
			} catch (final IOException e) {}
		}
		
		public static void unregister() {
			DiscoveryAdvertisement.getInstance().unRegisterEvent(DiscoveryAdvertisement.EVENT_SSDP_ALIVE, devicetype, handler);
//			DiscoveryAdvertisement.getInstance().unRegisterEvent(DiscoveryAdvertisement.EVENT_SSDP_BYE_BYE, devicetype, handler);
		}
		
		protected static class Handler implements DiscoveryEventHandler {
		
			@Override
            public void eventSSDPAlive(String usn, String udn, String nt, String maxAge, URL location) {
				InternetGatewayDevice[] newIGD = { null };
				boolean error = false;
				String errorMsg = null;
				try {
					newIGD[0] = new InternetGatewayDevice(new UPNPRootDevice(location, maxAge, "", usn, udn));
				} catch (final UnsupportedOperationException e) {
					error = true;
					errorMsg = e.getMessage();
				} catch (final MalformedURLException e) {
					error = true;
					errorMsg = e.getMessage();
				} catch (final IllegalStateException e) {
					error = true;
					errorMsg = e.getMessage();
				}
				if (error && errorMsg != null)
					log.severe("eventSSDPAlive: " + errorMsg);
				if (newIGD[0] == null) return;
				log.info("discovered device: " + newIGD[0].getIGDRootDevice().getFriendlyName());
				if (UPnP.setIGDs(newIGD) &&
					Switchboard.getSwitchboard().getConfigBool(SwitchboardConstants.UPNP_ENABLED, false))
						UPnP.addPortMapping();
				Listener.unregister();
			}
		
			@Override
            public void eventSSDPByeBye(String usn, String udn, String nt) {}
			
		}
		
	}

}
