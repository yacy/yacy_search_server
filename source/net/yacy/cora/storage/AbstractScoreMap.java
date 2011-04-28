/**
 *  AbstractScoreMap
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 28.04.2011 at http://yacy.net
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

package net.yacy.cora.storage;

public abstract class AbstractScoreMap<E> implements ScoreMap<E> {

    /**
     * apply all E/int mappings from an external ScoreMap to this ScoreMap
     */
    public void inc(ScoreMap<E> map) {
        if (map == null) return;
        for (E entry: map) {
            this.inc(entry, map.get(entry));
        }
    }
    
}
