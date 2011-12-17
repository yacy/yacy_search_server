/**
 *  MapStore
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 16.12.2011 at http://yacy.net
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

import java.util.Map;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;

/**
 * this is a placeholder interface
 * for the complex expressionMap<byte[], Map<String, byte[]>>
 * 
 */
public interface MapStore extends Map<byte[], Map<String, byte[]>>, Iterable<Map.Entry<byte[], Map<String, byte[]>>> {

    /**
     * the map should have an ordering on the key elements
     * @return a byte order on the key elements
     */
    public ByteOrder getOrdering();

    /**
     * the keys of the map should be iterable
     * @return an iterator on the map keys
     */
    public CloneableIterator<byte[]> keyIterator();
    
    /**
     * most of the MapStore implementations are file-based, so we should consider a close method
     */
    public void close();
}
