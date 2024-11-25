/**
 *  Negation
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

import org.apache.solr.common.SolrDocument;

public class Negation extends AbstractTerm implements Term {

    private Term term;
    
    public Negation(final Term term) {
        this.term = term;
    }

    @Override
    public Object clone() {
        return new Negation(this.term);
    }

    @Override
    public boolean equals(Object otherTerm) {
        if (!(otherTerm instanceof Negation)) return false;
        Negation o = (Negation) otherTerm;
        return this.term.equals(o.term);
    }

    @Override
    public int hashCode() {
        return -this.term.hashCode();
    }

    /**
     * the length attribute of a term shows if rewritten terms
     * (using rules of replacement as allowed for propositional logic)
     * are shorter and therefore more efficient.
     * @return the number of operators plus the number of operands plus one
     */
    @Override
    public int weight() {
        return term.weight() + 1;
    }
    
    /**
     * create a Solr query string from this literal
     * @return a string which is a Solr query string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('(').append('-').append(this.term.toString()).append(')');
        return sb.toString();
    }
    
    /**
     * check if the key/value pair of this literal occurs in the SolrDocument
     * @param doc the document to match to this literal
     * @return true, if the key of this literal is contained in the document and the
     *   value equals (does not equal) with the value if this literal (if the signature is false)
     */
    @Override
    public boolean matches(SolrDocument doc) {
        return !this.term.matches(doc);
    }
    
    @Override
    public Term lightestRewrite() {
        // TODO: this can be enhanced if negations are not attached to atoms
        Term t = this.term.lightestRewrite();
        return new Negation(t);
    }
}
