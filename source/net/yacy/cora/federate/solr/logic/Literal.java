/**
 *  Literal
 *  Copyright 2014 by Michael Peter Christen
 *  First released 03.08.2014 at https://yacy.net
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

package net.yacy.cora.federate.solr.logic;

public abstract class Literal extends AbstractTerm implements Term {

    public Literal() {
    }
    
    /**
     * the length attribute of a term shows if rewritten terms
     * (using rules of replacement as allowed for propositional logic)
     * are shorter and therefore more efficient.
     * @return the number of operators plus the number of operands plus one
     */
    @Override
    public int weight() {
        return 1;
    }
    
    @Override
    public Term lightestRewrite() {
        return this;
    }
}
