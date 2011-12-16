// MapMap.java
// (C) 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.11.2011 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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


package net.yacy.kelondro.blob;

import java.util.Map;

import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;

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
     * most of the MapMap implementations are file-based, so we should consider a close method
     */
    public void close();
}
