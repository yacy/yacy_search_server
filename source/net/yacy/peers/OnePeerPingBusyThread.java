// OnePeerPingBusyThread.java
// ---------------------------
// Copyright 2017 by luccioman; https://github.com/luccioman
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

package net.yacy.peers;

import net.yacy.kelondro.workflow.OneTimeBusyThread;

/**
 * A busy thread running peer ping only once.
 */
public class OnePeerPingBusyThread extends OneTimeBusyThread {
	
	/** Access to the peer to peer network */
	final net.yacy.peers.Network peersNetwork;
	
	/**
	 * @param peersNetwork a net.yacy.peers.Network instance
	 * @throws IllegalArgumentException when peersNetwork is null
	 */
	public OnePeerPingBusyThread(final net.yacy.peers.Network peersNetwork) {
		super("Network.peerPing");
		if(peersNetwork == null) {
			throw new IllegalArgumentException("peersNetwork parameter must not be null");
		}
		this.peersNetwork = peersNetwork;
	}

	@Override
	public boolean jobImpl() throws Exception {
		this.peersNetwork.peerPing();
		return true;
	}
	
}