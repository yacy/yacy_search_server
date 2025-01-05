/**
 *  TermTools
 *  Copyright 2014 by Michael Peter Christen
 *  First released 04.08.2014 at https://yacy.net
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

import java.util.List;

/**
 * static methods for term comparison, term order, term weights, permutations etc.
 */
public class TermTools {

    public static boolean isIn(final Term a, final List<Term> termlist) {
        for (Term t: termlist) {
            if (a.equals(t)) return true;
        }
        return false;
    }
    /*
    public static ArrayList<Operations> permutations(final Operations operations) {
        List<Term> ops = operations.getOperands();
        int os = ops.size();
        ArrayList<Operations> permutation = new ArrayList<Operations>();
        if (ops.size() < 2) {
            permutation.add(operations);
            return permutation;
        }
        Term head = ops.get(0);
        ops.remove(0);
        ArrayList<Operations> p1 = permutations(operations);
        for (Operations pt: p1) {
            // insert head into each position from pt
            for (int i = 0; i < os; i++) {
                
            }
        }
        return 
    }
    */
}
