// SynonymTaggingEntry.java
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

/**
 * Synonym entry for a term in the {@link Tagging} class
 */
class SynonymTaggingEntry implements TaggingEntry {
	
	/** Term synonym */
	protected String synonym;
	

	/**
	 * @param synonym a term synonym
	 * @throws IllegalArgumentException when synonym is null
	 */
	public SynonymTaggingEntry(String synonym) {
		if(synonym == null) {
			throw new IllegalArgumentException("synonym must not be null");
		}
		this.synonym = synonym;
	}

	@Override
	public String getSynonym() {
		return synonym;
	}
	
	@Override
	public String getObjectLink() {
		return null;
	}

}
