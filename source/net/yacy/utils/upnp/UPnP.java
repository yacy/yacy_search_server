// UPnP.java
// (C) 2009 by David Wieditz; d.wieditz@gmx.de
// first published 14.02.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.utils.upnp;

import java.io.IOException;
import java.net.InetAddress;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

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

	private static final Map<UPnPMappingType, UPnPMapping> MAPPINGS = new EnumMap<>(
			UPnPMappingType.class);
	static {
		MAPPINGS.put(UPnPMappingType.HTTP, new UPnPMapping("port", null, "TCP",
				"YaCy HTTP"));
		MAPPINGS.put(UPnPMappingType.HTTPS, new UPnPMapping("port.ssl",
				"server.https", "TCP", "YaCy HTTPS"));
	}

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
	 * Add port mappings for configured ports.
	 */
	public static void addPortMappings() {
		if (SB == null) {
			return;
		}

		UPnPMapping mapping;
		for (final Entry<UPnPMappingType, UPnPMapping> entry : MAPPINGS
				.entrySet()) {
			mapping = entry.getValue();

			addPortMapping(entry.getKey(), mapping,
					SB.getConfigInt(mapping.getConfigPortKey(), 0));
		}
	}

	/**
	 * Remove all port mappings.
	 */
	public static void deletePortMappings() {

		if (SB == null) {
			return;
		}

		UPnPMapping mapping;
		for (final Entry<UPnPMappingType, UPnPMapping> entry : MAPPINGS
				.entrySet()) {
			mapping = entry.getValue();
			deletePortMapping(mapping);
		}
	}

	/**
	 * Add port mapping to all gateway devices on the network.<br/>
	 * Latest port mapping will be removed.
	 * 
	 * TODO: don't try to map already mapped port again, find alternative
	 * 
	 * @param type
	 *            mapping type
	 * @param mapping
	 *            contains data about mapping
	 * @param port
	 *            port number to map
	 */
	public static void addPortMapping(final UPnPMappingType type,
			final UPnPMapping mapping, final int port) {

		if (port < 1) {
			return;
		}

		if (mapping.getPort() > 0) {
			deletePortMapping(mapping); // delete old mapping first
		}

		if ((mapping.isConfigEnabledKeyEmpty() || SB.getConfigBool(
				mapping.getConfigEnabledKey(), false))
				&& mapping.getPort() == 0
				&& ((GATEWAY_DEVICES != null) || init())) {

			String localHostIP;
			boolean mapped;
			String msg;
			for (final GatewayDevice gatewayDevice : GATEWAY_DEVICES.values()) {

				try {
					localHostIP = toString(gatewayDevice.getLocalAddress());

					mapped = gatewayDevice.addPortMapping(port, port,
							localHostIP, mapping.getProtocol(),
							mapping.getDescription());

					msg = "port " + port + " on device "
							+ gatewayDevice.getFriendlyName();

					if (mapped) {
						LOG.info("mapped " + msg);
						mapping.setPort(port);
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
	 * 
	 * @param mapping
	 *            to delete
	 */
	public static void deletePortMapping(final UPnPMapping mapping) {
		if (mapping.getPort() > 0 && GATEWAY_DEVICES != null) {

			boolean unmapped;
			String msg;
			for (final GatewayDevice gatewayDevice : GATEWAY_DEVICES.values()) {

				try {
					unmapped = gatewayDevice.deletePortMapping(
							mapping.getPort(), mapping.getProtocol());

					msg = "port " + mapping.getPort() + " on device "
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

			mapping.setPort(0); // reset mapped port
		}
	}

	/**
	 * Gets currently mapped port.
	 * 
	 * @param type
	 *            mapping type
	 * 
	 * @return mapped port or 0 if no port is mapped
	 */
	public static int getMappedPort(final UPnPMappingType type) {

		if (type == null) {
			return 0;
		}

		return MAPPINGS.get(type).getPort();
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
