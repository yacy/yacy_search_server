/**
 *  Sortable
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 16.07.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-05-30 10:53:58 +0200 (Mo, 30 Mai 2011) $
 *  $LastChangedRevision: 7759 $
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

package net.yacy.cora.sorting;

import java.util.Comparator;

public interface Sortable<A> extends Comparator<A> {

    public int size();

    public A get(final int index, final boolean clone);

    public void delete(int i);

    public A buffer();

    public void swap(int i, int j, A buffer);

}
