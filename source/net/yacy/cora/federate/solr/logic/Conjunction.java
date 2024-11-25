/**
 *  Conjunction
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

/**
 * A Conjunction is a conjunction of terms to Solr. The purpose of this class is,
 * to provide a mechanism to reduce the calls to Solr when calling Solr several times with sets of
 * terms which are all conjunctive.
 */
public class Conjunction extends AbstractOperations implements Operations {
    
    public Conjunction() {
        super("AND");
    }

    public Conjunction(final Term t1, final Term t2) {
        super("AND");
        this.addOperand(t1);
        this.addOperand(t2);
    }
    
    @Override
    public Object clone() {
        Conjunction c = new Conjunction();
        for (Term t: this.terms) c.addOperand(t);
        return c;
    }
    
    @Override
    public boolean equals(Object otherTerm) {
        if (!(otherTerm instanceof Conjunction)) return false;
        Conjunction o = (Conjunction) otherTerm;
        for (Term t: this.terms) {
            if (!TermTools.isIn(t, o.getOperands())) return false;
        }
        return true;
    }
 
    /**
     * check if this conjunction matches with a given SolrDocument
     * @param doc the SolrDocument to match to
     * @return true, if all literals of this conjunction match with the terms of the document
     */
    @Override
    public boolean matches(SolrDocument doc) {
        for (Term term: this.terms) {
            if (!term.matches(doc)) return false;
        }
        return true;
    }

}
