/**
 *  DublinCore
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


package net.yacy.cora.lod.vocabulary;

import java.util.Set;

import net.yacy.cora.lod.Literal;
import net.yacy.cora.lod.Vocabulary;

public enum DublinCore implements Vocabulary {

    Contributor,
    Coverage,
    Creator,
    Date,
    Description,
    Format,
    Identifier,
    Language,
    Publisher,
    Relation,
    Rights,
    Source,
    Subject,
    Title,
    Type;

    public final static String IDENTIFIER = "http://dublincore.org/documents/2010/10/11/dces/";
    public final static String PREFIX = "dc";

    private final String predicate, uriref;

    private DublinCore() {
        this.predicate = IDENTIFIER +  this.name().toLowerCase();
        this.uriref = PREFIX + ':' + this.name().toLowerCase();
    }

    @Override
    public String getNamespace() {
        return IDENTIFIER;
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
        return this.uriref;
    }
}
