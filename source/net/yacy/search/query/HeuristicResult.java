/**
 *  HeuristicResult
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 01.11.2012 at http://yacy.net
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

package net.yacy.search.query;

import net.yacy.cora.order.Base64Order;

public class HeuristicResult implements Comparable<HeuristicResult> {
    private final byte[] urlhash;
    public final String heuristicName;
    public final boolean redundant;

    public HeuristicResult(final byte[] urlhash, final String heuristicName, final boolean redundant) {
        this.urlhash = urlhash;
        this.heuristicName = heuristicName;
        this.redundant = redundant;
    }

    @Override
    public int compareTo(HeuristicResult o) {
        return Base64Order.enhancedCoder.compare(this.urlhash, o.urlhash);
     }

    @Override
    public int hashCode() {
        return (int) Base64Order.enhancedCoder.cardinal(this.urlhash);
    }

    @Override
    public boolean equals(Object o) {
        return Base64Order.enhancedCoder.equal(this.urlhash, ((HeuristicResult) o).urlhash);
    }
}
