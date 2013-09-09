/**
 *  AnnoteaA
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
 * The Annotea Annotation schema [AnnotationNS] defines properties for identifying 
 * the document being annotated, a specific context within that document to which 
 * the body of the annotation refers, the author of the annotation, and more.
 * 
 * http://www.w3.org/2003/07/Annotea/BookmarkSchema-20030707
 */
public enum AnnoteaA implements Vocabulary {
	
	Annotation,		// The target type of a annotation resource.
	
	annotates,		// Relates an Annotation to the resource to which the Annotation applies. The inverse relation is 'hasAnnotation'
    
	author,			// The name of the person or organization most responsible for creating the Annotation. Sub property of dc:creator
    
	body,			// Relates the resource representing the 'content' of an Annotation to the Annotation resourceSub property of related
    
	context,		// The context within the resource named in 'annotates' to which the Annotation most directly applies
    
	created,		// The date and time on which the Annotation was created. yyyy-mm-ddThh:mm:ssZ format recommended.Sub property of dc:date
    
	modified,		// The date and time on which the Annotation was modified. yyyy-mm-ddThh:mm:ssZ format recommended.Sub property of dc:date
    
	related;		// A relationship between an annotation and additional resources that is less specific than 'body'. 
    				// The 'related' property is expected to be subclassed by more specific relationships
    
    public final static String NAMESPACE = "http://www.w3.org/2000/10/annotation-ns#";
    public final static String PREFIX = "a";

    private final String predicate;

    private AnnoteaA() {
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
