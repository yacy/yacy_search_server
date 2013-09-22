// yacyUpdateLocation.java 
// ----------------
// (C) 2009 by Florian Richter
// first published 5.03.2009 on http://yacy.net
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

package net.yacy.peers.operation;

import java.security.PublicKey;

import net.yacy.cora.document.id.DigestURL;


/**
 * Holds a update location with url and public key
 *
 */
public class yacyUpdateLocation {
    private final DigestURL locationURL;
    private final PublicKey publicKey;

    public yacyUpdateLocation(DigestURL locationURL, PublicKey publicKey) {
	this.locationURL = locationURL;
	this.publicKey = publicKey;
    }

    public DigestURL getLocationURL() {
	return this.locationURL;
    }
    public PublicKey getPublicKey() {
	return this.publicKey;
    }
}
