/**
 *  AnnoteaB
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
 * Annotea [Annotea] is a W3C Semantic Web Advanced Development project that 
 * provides a framework for rich communication about Web pages through shared RDF metadata.
 * 
 * The Annotea Bookmark schema [BookmarkNS] provides the basic concepts found in common browser bookmark implementations. 
 * These basic concepts are also captured in the XML Bookmark Exchange Language [XBEL]. 
 * The use of RDF in Annotea permits bookmarks to express additional semantics. 
 * XBEL can be easily mapped into this schema.
 * 
 * http://www.w3.org/2003/07/Annotea/BookmarkSchema-20030707
 */
public enum AnnoteaB implements Vocabulary {
	
    Bookmark,		// The class to which all bookmarks belong
    
    Shortcut,		// Specifies a behavior; when the object of type 'Shortcut' is activated, the client follows the 'recalls' property 
    				// and activates the object at the end of that 'recalls' property. The target object may be another Bookmark or may be a Topic.
    				
    Topic,			// 
    
    bookmarks,		// This corresponds to XBEL:href an object of type Bookmark is expected to have a 'recalls' relationship to the document being bookmarked. 
    				// The 'bookmarks' property is an older name for the 'recalls' relationship.
    
    hasTopic,		// relates a bookmark to a topic. A bookmark must have at least one hasTopic property. The typical user operation of following a bookmark link 
    				// will use the value of the b:recalls property. This property corresponds to XBEL:href property.An instance of  
    leadsTo,		// connects a Shortcut to the bookmark or topic that is being included by reference in some other topic
    
    recalls,		// Relates a bookmark with the resource that has been bookmarked. This corresponds to XBEL:href; 
    				// an object of type Bookmark is expected to have a 'recalls' relationship to the document being bookmarked  
    
    subTopicOf;		// Describes a relationship between Topics. When a topic T is a sub-topic of a topic U then all bookmarks that have topic T are also considered to have topic U. 
    				// A topic may be a sub-topic of one or more topics; trivially, every topic is a sub-topic of itself. 
    				// More formally; for all B, T, and U: b b:hasTopic T, T b:subTopicOf U implies B b:hasTopic U.  
    
    public final static String NAMESPACE = "http://www.w3.org/2002/01/bookmark#";
    public final static String PREFIX = "b";
    
    private final String predicate;

    private AnnoteaB() {
        this.predicate = NAMESPACE + this.name();
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
