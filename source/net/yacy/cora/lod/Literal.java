/**
 *  Literal
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 18.12.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.cora.lod;

import java.util.regex.Pattern;

import net.yacy.cora.document.id.MultiProtocolURL;

/**
 * A literal is the possible value for a predicate.
 * A set of literals is the norm of a predicate.
 * Each literal can have an attached explanation which we express
 * as a link to the resource that explains the literal.
 */
public interface Literal {

    /**
     * the terminal is the actual content of the property and also
     * the visual representation of the content of a property if the
     * literal is assigned to that property.
     * @return a string representing the literal
     */
    public String getTerminal();
    
    /**
     * the subject of a literal is a reference to a resource that
     * explains the literal. If an object has attached properties
     * from different vocabularies and properties assigned to the
     * object have actual literal instances assigned, then the set
     * of subjects of these literals explain the object as a co-notation
     * to knowledge. Subjects of literals shall therefore be
     * knowledge authorities for the predicates where the literal is
     * assigned.
     * @return an url to a knowledge authority for the literal
     */
    public MultiProtocolURL getSubject();
    
    /**
     * if a resource is poorly annotated with metadata an it shall
     * be automatically annotated, then the terminal of a literal
     * may be too weak to discover literals in the resource. An additional
     * discovery pattern may help to reduce the set of literals that can 
     * be discovered automatically. A discovery pattern is then not
     * a replacement of the terminal itself, it is an additional pattern
     * that must occur also in the resource where also the terminal of
     * the literal appears. If the terminal itself is sufficient to discover
     * the literal, then the discovery pattern may be a catch-all '.*' pattern.
     * @return the discovery pattern to identify the literal in the resource.
     */
    public Pattern getDiscoveryPattern();
    
}
