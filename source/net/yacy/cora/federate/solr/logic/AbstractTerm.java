/**
 *  AbstractTerm
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

public abstract class AbstractTerm implements Term {

    /**
     * create a hit subset of the given SolrDocumentList according to the conjunction defined
     * in this object
     * @param sdl the SolrDocumentList
     * @return a manufactured subset-clone of the given SolrDocumentList where document match with the term as given in this object
     */
    @Override
    public SolrDocumentList apply(SolrDocumentList sdl) {
        SolrDocumentList r = new SolrDocumentList();
        int numFound = 0;
        for (SolrDocument d: sdl) {
            if (matches(d)) {r.add(d); numFound++;}
        }
        r.setNumFound(numFound);
        return r;
    }
}
