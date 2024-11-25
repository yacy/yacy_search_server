/**
 *  ScoreMap
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.10.2010 at https://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public interface ScoreMap<E> extends Iterable<E> {

    public void clear();
    
    /**
     * shrink the cluster to a demanded size
     * @param maxsize the new expected size
     * @return the number of deleted entries, zero when maxsize is greater than the current map size
     */
    public int shrinkToMaxSize(int maxsize);
    
    /**
     * shrink the cluster in such a way that the smallest score is equal or greater than a given minScore
     * @param minScore the new expected minimum score
     * @return the number of deleted entries or zero when minScore is lower than the currently minimum score of the map
     */
    public int shrinkToMinScore(int minScore);
    

    /**
     * divide the map into two halve parts using the count of the entries
     * @return the objects of the smaller entries from at least 1/2 of the list
     */
    public List<E> lowerHalf();
    
    public int size();
    public boolean sizeSmaller(int size);    
    public boolean isEmpty();
    
    public void set(final E obj, final int newScore);
    
    /**
     * Delete a given entry and return its previous score
     * @param obj the entry to delete
     * @return the score of the deleted entry, or zero when the map did not contain that entry
     */
    public int delete(final E obj);

    public boolean containsKey(final E obj);
    
    public int get(final E obj);
    
    @Override
    public String toString();

    public Iterator<E> keys(final boolean up);
    
    public Collection<E> keyList(final boolean up);

    public void inc(final E obj);
    public void inc(final E obj, final int incrementScore);
    
    public void dec(final E obj);
    public void dec(final E obj, final int incrementScore);

    public void inc(ScoreMap<E> map);
}
