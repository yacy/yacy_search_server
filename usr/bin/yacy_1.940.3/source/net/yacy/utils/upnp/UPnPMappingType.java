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
 * Contains types of mappings for UPnP. At the moment we only distinguish
 * between different protocols. At some point in the future we may serve the
 * same protocols vie different ports for different services, but until then,
 * this should be good enough.
 * 
 * @author Marc Nause
 */
public enum UPnPMappingType {

	HTTP, HTTPS

}
