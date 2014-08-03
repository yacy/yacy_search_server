/**
 *  DNF
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

/**
 * This is the implementation of a disjunctive normal form, which is the disjunction of conjunctions.
 * See: http://en.wikipedia.org/wiki/Disjunctive_normal_form
 * We use a DNF to combine several solr queries into one if that is applicable.
 * When caling Solr with a DNF, we need only one http request (if this is done with a remote Solr)
 * and thus saving the network overhead for each single (conjunctive) query. To filter out the conjunctions
 * from the bundled query result, you must apply  the apply() method from the Conjunction class.
 */
public class DNF {

    private final List<Conjunction> dnf;
    
    public DNF() {
        this.dnf = new ArrayList<>();
    }
    
    public void addConjunction(Conjunction conjunction) {
        this.dnf.add(conjunction);
    }

    /**
     * create a Solr query string from this DNF
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Conjunction c: this.dnf) {
            if (sb.length() > 0) sb.append(" OR ");
            sb.append('(').append(c.toString()).append(')');
        }
        return sb.toString();
    }
    
}
