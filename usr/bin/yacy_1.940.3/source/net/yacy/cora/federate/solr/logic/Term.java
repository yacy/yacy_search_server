/**
 *  Term
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
import org.apache.solr.common.SolrDocumentList;

public interface Term {
    
    /**
     * Equal method which returns true if the terms are logically equal.
     * It is advised to create minimum-weight variants of the terms using lightestRewrite() before comparing because
     * the equals method should not apply rewrite rules. If two terms are equal, then also their minimum weight rewrite is equal.
     * @param otherTerm
     * @return true if the interpretation (apply method) of the term is equal to the interpretation (apply method) of otherTerm on any document
     */
    @Override
    public boolean equals(Object otherTerm);
    
    /**
     * the weight attribute of a term shows if rewritten terms
     * (using rules of replacement as allowed for propositional logic)
     * are shorter and therefore more efficient.
     * @return the number of operators plus the number of operands plus one
     */
    public int weight();

    /**
     * toString produces the Solr Query representation of the term
     * @return the Solr Query String
     */
    @Override
    public String toString();
    
    /**
     * check if this term matches the SolrDocument
     * @param doc the document to match to this term
     * @return true, if this term matches with the document
     */
    public boolean matches(SolrDocument doc);
    
    /**
     * Create a hit subset of the given SolrDocumentList according to the conjunction defined
     * in this object. This is the interpretation of the term on a 'world object' (the Solr document).
     * @param sdl the SolrDocumentList
     * @return a manufactured subset-clone of the given SolrDocumentList where document match with the term
     */
    public SolrDocumentList apply(SolrDocumentList sdl);
    
    /**
     * Applying a rewrite rule to the term should not change the logical expression of the term.
     * The possible set of rewrites of the term is computed and the ligtest rewrite of the underlying terms
     * are used to compare all rewrites to each other. Then the lightest term is returned.
     * @return the lightest term that is logically equivalent to the given term
     */
    public Term lightestRewrite();
}
