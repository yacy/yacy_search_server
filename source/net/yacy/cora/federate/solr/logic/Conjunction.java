/**
 *  Conjunction
 *  Copyright 2014 by Michael Peter Christen
 *  First released 03.08.2014 at http://yacy.net
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

import java.util.ArrayList;
import java.util.List;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

/**
 * A Concunction is a conjunction of atomic key/value pairs to Solr. The purpose of this class is,
 * to provide a mechanism to reduce the calls to Solr when calling Solr several times with sets of
 * key/value pairs which are all conjunctive. A combined query for a set of disjunctive conjunctions
 * is provided by the DNF class. The result of a DNF class query to solr must be separated again using
 * the original conjunctive terms which is represented by this class. The SolrDocumentList which are
 * results from individual calls is then the same as a SolrDocument list which can be computed with the
 * method apply() in this class on the DNF of the Solr result.
 */
public class Conjunction {

    private final List<Literal> literals;
    
    public Conjunction() {
        this.literals = new ArrayList<>();
    }
    
    public void addLiteral(Literal literal) {
        this.literals.add(literal);
    }
 
    /**
     * create a Solr query string from this conjunction
     * @return a string which is a Solr query string
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Literal l: this.literals) {
            if (sb.length() > 0) sb.append(" AND ");
            sb.append(l.toString());
        }
        return sb.toString();
    }
    
    /**
     * check if this conjunction matches with a given SolrDocument
     * @param doc the SolrDocument to match to
     * @return true, if all literals of this conjunction match with the key/value pairs of the document
     */
    public boolean matches(SolrDocument doc) {
        for (Literal literal: this.literals) {
            if (!literal.matches(doc)) return false;
        }
        return true;
    }

    /**
     * create a hit subset of the given SolrDocumentList according to the conjunction defined
     * in this object
     * @param sdl the SolrDocumentList
     * @return a manufactured subset-clone of the given SolrDocumentList where document match with the Conjunction as given in this object
     */
    public SolrDocumentList apply(SolrDocumentList sdl) {
        SolrDocumentList r = new SolrDocumentList();
        int numFound = 0;
        for (SolrDocument d: r) {
            if (matches(d)) {r.add(d); numFound++;}
        }
        r.setNumFound(numFound);
        return r;
    }
}
