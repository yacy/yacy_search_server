/**
 *  RatingOrder.java
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.08.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
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


package net.yacy.cora.order;

import net.yacy.cora.sorting.Rating;



public class RatingOrder<A> extends AbstractOrder<Rating<A>> implements Order<Rating<A>> {

    Order<A> ordering;

    public RatingOrder(final Order<A> ordering) {
        this.ordering = ordering;
    }

    @Override
    public int compare(final Rating<A> a, final Rating<A> b) {
        return this.ordering.compare(a.getObject(), b.getObject());
    }

    @Override
    public boolean wellformed(final Rating<A> a) {
        return true;
    }

    @Override
    public String signature() {
        return "RA";
    }

    @Override
    public long cardinal(final Rating<A> key) {
        return key.getScore();
    }

    @Override
    public boolean equal(final Rating<A> a, final Rating<A> b) {
        return this.ordering.compare(a.getObject(), b.getObject()) == 1;
    }

    @Override
    public Order<Rating<A>> clone() {
        return this;
    }
}
