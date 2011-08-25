// RatingOrder.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2011
// created 25.08.2011
//
// $LastChangedDate: 2011-03-08 02:51:51 +0100 (Di, 08 Mrz 2011) $
// $LastChangedRevision: 7567 $
// $LastChangedBy: low012 $
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


package net.yacy.cora.ranking;


public class RatingOrder<A> extends AbstractOrder<Rating<A>> implements Order<Rating<A>> {

    Order<A> ordering;

    public RatingOrder(final Order<A> ordering) {
        this.ordering = ordering;
    }

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
