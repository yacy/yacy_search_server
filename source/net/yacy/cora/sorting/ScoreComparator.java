/**
 *  ScoreComparator
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 23.08.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-03-08 02:51:51 +0100 (Di, 08 Mrz 2011) $
 *  $LastChangedRevision: 7567 $
 *  $LastChangedBy: low012 $
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

package net.yacy.cora.sorting;

import java.util.Comparator;


public class ScoreComparator<E extends Comparable<E>> implements Comparator<E>
{
    private final ScoreMap<E> scoreMap;
    private final boolean reverse;

    public ScoreComparator(final ScoreMap<E> scoreMap, final boolean reverse) {
        this.scoreMap = scoreMap;
        this.reverse = reverse;
    }

    @Override
    public int compare(final E arg0, final E arg1) {
        if ( arg0.equals(arg1) ) {
            return 0;
        }
        final int i0 = this.scoreMap.get(arg0);
        final int i1 = this.scoreMap.get(arg1);
        if ( i0 < i1 ) {
            return this.reverse ? 1 : -1;
        }
        if ( i0 > i1 ) {
            return this.reverse ? -1 : 1;
        }
        return arg0.compareTo(arg1);
    }

}
