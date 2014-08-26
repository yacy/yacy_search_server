// UPnPMapping.java
// (C) 2014 by Marc Nause; marc.nause@gmx.de
// first published 26.08.2014 on http://yacy.net
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

/**
 * Simple value holder class for all values required for UPnP mapping. Class is
 * package private since we only need to create new instances in the UPnP
 * package.
 * 
 * @author Marc Nause
 */
class UPnPMapping {

	private final String PROTOCOL;
	private final String DESCRIPTION;
	private final String CONFIG_PORT_KEY;
	private final String CONFIG_ENABLED_KEY;
	private int port;

	/**
	 * Constructor.
	 * 
	 * @param configPortKey
	 *            key for port in yacy.config
	 * @param configEnabledKey
	 *            key for flag if port is enabled in yacy.config (optional, set
	 *            to empty or <code>null</code> if none is required
	 * @param protocol
	 *            {"TCP"|"UDP"}
	 * @param description
	 *            human readable description, may be displayed in router
	 */
	UPnPMapping(final String configPortKey, final String configEnabledKey,
			final String protocol, final String description) {
		PROTOCOL = protocol;
		DESCRIPTION = description;
		CONFIG_PORT_KEY = configPortKey;
		CONFIG_ENABLED_KEY = configEnabledKey;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getProtocol() {
		return PROTOCOL;
	}

	public String getDescription() {
		return DESCRIPTION;
	}

	public String getConfigPortKey() {
		return CONFIG_PORT_KEY;
	}

	public String getConfigEnabledKey() {
		return CONFIG_ENABLED_KEY;
	}

	public boolean isConfigEnabledKeyEmpty() {
		return CONFIG_ENABLED_KEY == null || CONFIG_ENABLED_KEY.isEmpty();
	}

}
