/**
 *  DMOZ
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 16.12.2011 at http://yacy.net
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


package net.yacy.cora.lod.vocabulary;

import java.util.Set;

import net.yacy.cora.lod.Literal;
import net.yacy.cora.lod.Vocabulary;

/**
 * The Open Directory Project is the largest, most comprehensive human-edited directory of the Web. 
 * It is constructed and maintained by a vast, global community of volunteer editors.
 * 
 * RDF dumps of the Open Directory database are available for download at http://www.dmoz.org/rdf.html * 
 * An overview of the vocabulary can be found at http://rdf.dmoz.org/rdf/tags.html
 */
public enum DMOZ implements Vocabulary {

	// Content
	ExternalPage,
	atom,
	link,
	link1,
	mediadate,
	pdf,
	pdf1,
	priority,
	rss,
	rss1,
	topic,
	type,
	
	// Structure
	Alias,
	Target,
	Topic,
	altlang,
	altlang1,
	catid,
	editor,
	lastUpdate,
	letterbar,
	narrow,
	narrow1,
	narrow2,
	newsgroup,
	related,
	symbolic,
	symbolic1,
	symbolic2;	
	
    public final static String NAMESPACE = "http://dmoz.org/rdf/";
    public final static String PREFIX = "dmoz";

    private final String predicate;

    private DMOZ() {
        this.predicate = NAMESPACE +  this.name().toLowerCase();
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getNamespacePrefix() {
        return PREFIX;
    }

    @Override
    public Set<Literal> getLiterals() {
        return null;
    }

    @Override
    public String getPredicate() {
        return this.predicate;
    }

	@Override
	public String getURIref() {
        return PREFIX + ':' + this.name();
	}
}
