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
import java.net.InetAddress;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.xml.sax.SAXException;

public class UPnP {

	private static final ConcurrentLog LOG = new ConcurrentLog("UPNP");
	private static final Switchboard SB = Switchboard.getSwitchboard();

	private static Map<InetAddress, GatewayDevice> GATEWAY_DEVICES = null;

	// mapping variables
	private static final String MAPPED_NAME = "YaCy";
	private static final String MAPPED_PROTOCOL = "TCP";
	private static int mappedPort;

	private static boolean init() {
		boolean init = true;

		try {
			if (GATEWAY_DEVICES == null) {
				GATEWAY_DEVICES = new GatewayDiscover().discover();
			}
		} catch (IOException | SAXException | ParserConfigurationException e) {
			init = false;
		}
		if (GATEWAY_DEVICES != null) {
			for (final GatewayDevice gatewayDevice : GATEWAY_DEVICES.values()) {
				LOG.info("found device: " + gatewayDevice.getFriendlyName());
			}
		} else {
			LOG.info("no device found");
			init = false;
		}

		return init;
	}

	/**
	 * Add port mapping for configured port.
	 */
	public static void addPortMapping() {
		if (SB == null) {
			return;
		}
		addPortMapping(Integer.parseInt(SB.getConfig("port", "0")));
	}

	/**
	 * Add TCP port mapping to all gateway devices on the network.<br/>
	 * Latest port mapping will be removed.
	 * 
	 * @param port
	 */
	public static void addPortMapping(final int port) { // TODO: don't map
														// already mapped port
														// again
		if (port < 1) {
			return;
		}

		if (mappedPort > 0) {
			deletePortMapping(); // delete old mapping first
		}

		if (mappedPort == 0 && ((GATEWAY_DEVICES != null) || init())) {

			String localHostIP;
			boolean mapped;
			String msg;
			for (final GatewayDevice gatewayDevice : GATEWAY_DEVICES.values()) {

				try {
					localHostIP = toString(gatewayDevice.getLocalAddress());

					mapped = gatewayDevice.addPortMapping(port, port,
							localHostIP, MAPPED_PROTOCOL, MAPPED_NAME);

					msg = "port " + port + " on device "
							+ gatewayDevice.getFriendlyName();

					if (mapped) {
						LOG.info("mapped " + msg);
						mappedPort = port;
					} else {
						LOG.warn("could not map " + msg);
					}
				} catch (IOException | SAXException e) {
					LOG.severe("mapping error: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Delete current port mapping.
	 */
	public static void deletePortMapping() {
		if (mappedPort > 0 && GATEWAY_DEVICES != null) {

			boolean unmapped;
			String msg;
			for (final GatewayDevice gatewayDevice : GATEWAY_DEVICES.values()) {

				try {
					unmapped = gatewayDevice.deletePortMapping(mappedPort,
							MAPPED_PROTOCOL);

					msg = "port " + mappedPort + " on device "
							+ gatewayDevice.getFriendlyName();

					if (unmapped) {
						LOG.info("unmapped " + msg);
					} else {
						LOG.warn("could not unmap " + msg);
					}

				} catch (SAXException | IOException e) {
					LOG.severe("unmapping error: " + e.getMessage());
				}
			}

			mappedPort = 0; // reset mapped port
		}
	}

	/**
	 * Gets currently mapped port.
	 * 
	 * @return mapped port or 0 if no port is mapped
	 */
	public static int getMappedPort() {
		return mappedPort;
	}

	private static String toString(final InetAddress inetAddress) {

		final String localHostIP;

		if (inetAddress != null) {

			localHostIP = inetAddress.getHostAddress();

			if (!inetAddress.isSiteLocalAddress()
					|| localHostIP.startsWith("127.")) {
				LOG.warn("found odd local address: " + localHostIP
						+ "; UPnP may fail");
			}
		} else {

			localHostIP = "";
			LOG.warn("unknown local address, UPnP may fail");
		}

		return localHostIP;
	}

}
