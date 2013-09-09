/**
 *  Owl
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 11.06.2011 at http://yacy.net
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

public enum Owl implements Vocabulary {

    SameAs("sameAs");

    public final static String IDENTIFIER = "http://www.w3.org/2002/07/owl#";
    public final static String PREFIX = "owl";

    private final String predicate;

    private Owl() {
        this.predicate = IDENTIFIER + this.name().toLowerCase();
    }

    private Owl(String name) {
        this.predicate = IDENTIFIER + name;
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
        return PREFIX + ':' + this.name();
    }
}
