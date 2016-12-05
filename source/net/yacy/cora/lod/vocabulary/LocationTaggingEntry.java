// LocationTaggingEntry.java
// Copyright 2016 by luccioman; https://github.com/luccioman
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

package net.yacy.cora.lod.vocabulary;

import net.yacy.cora.geo.GeoLocation;

/**
 * Entry with a synonym and a location for a term in the {@link Tagging} class.
 */
class LocationTaggingEntry extends SynonymTaggingEntry {
	
	/** Geographical location of the object */
	private GeoLocation location;

	/**
	 * 
	 * @param synonym term synonym
	 * @param location geographical location of the object. Must not be null.
	 * @throws IllegalArgumentException when a parameter is null
	 */
	public LocationTaggingEntry(String synonym, GeoLocation location) {
		super(synonym);
		if(location == null) {
			throw new IllegalArgumentException("location must not be null");
		}
		this.location = location;
	}

	@Override
	public String getObjectLink() {
		return "http://www.openstreetmap.org/?lat=" + location.lat() + "&lon=" + location.lon() + "&zoom=16";
	}

}
