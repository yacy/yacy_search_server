/**
 *  MetadataVocabulary
 *  Copyright 2012 by Michael Peter Christen
 *  First released 12.4.2012 at http://yacy.net
 *
 *  This file is part of YaCy Content Integration
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.data.meta;

import java.util.Set;

import net.yacy.cora.lod.Literal;
import net.yacy.cora.lod.Vocabulary;

public enum MetadataVocabulary implements Vocabulary {
	
	moddate, url;

	public final static String IDENTIFIER = "http://yacy.net/metadata";
    public final static String PREFIX = "ym";

    private final String predicate;
    
    private MetadataVocabulary() {
        this.predicate = PREFIX + ":" +  this.name().toLowerCase();
    }
    
	@Override
	public String getURLStub() {
		return IDENTIFIER;
	}

	@Override
	public String getShortName() {
		return PREFIX;
	}

	@Override
	public String getPredicate() {
		return this.predicate;
	}

	@Override
	public Set<Literal> getLiterals() {
		return null;
	}
}
