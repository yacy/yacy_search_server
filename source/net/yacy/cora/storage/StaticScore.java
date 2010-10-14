/**
 *  StaticScore
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.10.2010 at http://yacy.net
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

public interface StaticScore<E> {

    public void clear();
    
    /**
     * shrink the cluster to a demanded size
     * @param maxsize
     */
    public void shrinkToMaxSize(int maxsize);
    
    /**
     * shrink the cluster in such a way that the smallest score is equal or greater than a given minScore
     * @param minScore
     */
    public void shrinkToMinScore(int minScore);
    
    public long totalCount();
    
    public int size();
    
    public boolean isEmpty();
    
    public void setScore(final E obj, final int newScore);
    
    public int deleteScore(final E obj);

    public boolean existsScore(final E obj);
    
    public int getScore(final E obj);
    
    public int getMaxScore();

    public int getMinScore();

    public E getMaxObject();
    
    public E getMinObject();
    
    public String toString();
    
    public Iterator<E> scores(final boolean up);
    
}
