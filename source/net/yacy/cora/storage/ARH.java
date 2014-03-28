/**
 *  ARH
 *  an interface for Adaptive Replacement Handles
 *  Copyright 2014 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 15.01.2014 at http://yacy.net
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

import java.util.Iterator;
import java.util.Set;


public interface ARH<K> extends Iterable<K> {

    /**
     * get the size of the ARH. this returns the sum of main and ghost cache
     * @return the complete number of entries in the ARH cache
     */
    public int size();
    
    /**
     * add a value to the cache.
     * do not return a previous content value
     * @param s
     * @return true if this set did not already contain the specified element 
     */
    public boolean add(K s);

    /**
     * check if a value in the cache exist
     * @param s
     * @return true if the value exist
     */
    public boolean contains(Object s);

    /**
     * delete an entry from the cache
     * @param s
     */
    public void delete(K s);
    
    /**
     * clear the cache
     */
    public void clear();
    
    /**
     * iterator implements the Iterable interface
     */
    @Override
    public Iterator<K> iterator();

    /**
     * Return a Set view of the mappings contained in this map.
     * This method is the basis for all methods that are implemented
     * by a AbstractMap implementation
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<K> set();
    
    /**
     * a hash code for this ARH
     * @return a hash code
     */
    @Override
    int hashCode();
}
