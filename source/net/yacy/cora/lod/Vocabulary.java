/**
 *  Vocabulary
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 16.12.2011 at http://yacy.net
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

import java.util.Set;


/*
 * A Vocabulary is an interface to an 'extensible enum pattern'.
 * We want to have an kind of extensible enum for vocabularies.
 * Since enum classes cannot be extended we use a hack as explained in
 * http://blogs.oracle.com/darcy/entry/enums_and_mixins .
 * For an example for 'extensible enum pattern' see
 * http://stackoverflow.com/questions/1414755/java-extend-enum
 */
public interface Vocabulary {

    /**
     * get the RDF identifier as an URL stub
     * @return
     */
    public String getNamespace();

    /**
     * get the prefix for the predicates of this vocabulary
     * @return
     */
    public String getNamespacePrefix();

    /**
     * get the predicate name which already contains the prefix url stub
     * @return
     */
    public String getPredicate();

    /**
     * The URI Reference as defined in http://www.w3.org/TR/rdf-concepts/ 2.2.3
     * This is a combination of the namespace prefic and the constant name,
     * concatenated with ':'.
     * @return
     */
    public String getURIref();

    /**
     * get a set of literals that are allowed for the predicate as values
     * @return
     */
    public Set<Literal> getLiterals();

    /**
     * the name method is identical to the java.lang.Enum method.
     * If an Enum class for vocabularies
     * implements this interface, the name() method is automatically implemented
     *
     * @return Returns the name of the enum constant as declared in the enum declaration.
     */
    public String name();
}
