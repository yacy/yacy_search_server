// indexTreeMapContainer.java
// (C) 2005, 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 07.05.2005 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

/*
    an indexContainer is a bag of indexEntries for a single word
    such an container represents a RWI snippet:
    it collects a new RWI until it is so big that it should be flushed to either
    - an indexAssortment: collection of indexContainers of same size or
    - the backend storage
 
    the creationTime is necessary to organize caching of containers
*/

package de.anomic.index;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroOrder;

public final class indexTreeMapContainer extends indexAbstractContainer implements indexContainer {

    private String wordHash;
    private final TreeMap container; // urlHash/plasmaWordIndexEntry - Mapping
    private long updateTime;
    private kelondroOrder ordering;
    private int order_column;
    
    public indexTreeMapContainer(String wordHash) {
        this(wordHash, new kelondroNaturalOrder(true), 0);
    }
    
    public indexTreeMapContainer(String wordHash, kelondroOrder ordering, int column) {
        this.wordHash = wordHash;
        this.updateTime = 0;
        this.ordering = ordering;
        this.order_column = column;
        container = new TreeMap(ordering); // a urlhash/plasmaWordIndexEntry - relation
    }
    
    public indexContainer topLevelClone() {
        indexContainer newContainer = new indexTreeMapContainer(this.wordHash, this.ordering, this.order_column);
        newContainer.add(this, -1);
        return newContainer;
    }
    
    public void setWordHash(String newWordHash) {
        // this is used to replicate a container for different word indexes during global search
        this.wordHash = newWordHash;
    }
    
    public void clear() {
        container.clear();
    }
    
    public int size() {
        return container.size();
    }
    
    public long updated() {
        return updateTime;
    }
    
    public void setOrdering(kelondroOrder newOrder, int newColumn) {
        this.ordering = newOrder;
        this.order_column = newColumn;
    }
    
    public kelondroOrder getOrdering() {
        return this.ordering;
    }
    
    public int getOrderColumn() {
        return this.order_column;
    }
    
    public String getWordHash() {
        return wordHash;
    }

    public int add(indexEntry entry) {
        return add(entry, System.currentTimeMillis());
    }
    
    public int add(indexEntry entry, long updateTime) {
        this.updateTime = java.lang.Math.max(this.updateTime, updateTime);
        return (addi(entry)) ? 1 : 0;
    }
    
    public int add(indexEntry[] entries, long updateTime) {
        int c = 0;
        for (int i = 0; i < entries.length; i++) if (addi(entries[i])) c++;
        this.updateTime = java.lang.Math.max(this.updateTime, updateTime);
        return c;
    }
    
    public int add(indexContainer c, long maxTime) {
        // returns the number of new elements
        long startTime = System.currentTimeMillis();
        if (c == null) return 0;
        int x = 0;
        synchronized (c) {
            Iterator i = c.entries();
            while ((i.hasNext()) && ((maxTime < 0) || ((startTime + maxTime) > System.currentTimeMillis()))) {
                try {
                    if (addi((indexURLEntry) i.next())) x++;
                } catch (ConcurrentModificationException e) {}
            }
        }
        this.updateTime = java.lang.Math.max(this.updateTime, c.updated());
        return x;
    }

    private boolean addi(indexEntry entry) {
        // returns true if the new entry was added, false if it already existed
        indexURLEntry oldEntry = (indexURLEntry) container.put(entry.urlHash(), entry);
        if ((oldEntry != null) && (entry.isOlder(oldEntry))) { // A more recent Entry is already in this container
            container.put(entry.urlHash(), oldEntry); // put it back
            return false;
        }
        return (oldEntry == null);
    }

    public boolean contains(String urlHash) {
        return container.containsKey(urlHash);
    }

    public indexEntry get(String urlHash) {
        return (indexURLEntry) container.get(urlHash);
    }
    
    public indexEntry[] getEntryArray() {
        return (indexURLEntry[]) container.values().toArray();
    }

    public indexEntry remove(String urlHash) {
        return (indexURLEntry) container.remove(urlHash);
    }

    public boolean removeEntry(String wordHash, String urlHash, boolean deleteComplete) {
        if (!wordHash.equals(this.wordHash)) return false;
        return remove(urlHash) != null;
    }

    public int removeEntries(String wordHash, Set urlHashes, boolean deleteComplete) {
        if (!wordHash.equals(this.wordHash)) return 0;
        int count = 0;
        Iterator i = urlHashes.iterator();
        while (i.hasNext()) count += (remove((String) i.next()) == null) ? 0 : 1;
        return count;
    }

    public Iterator entries() {
        // returns an iterator of indexEntry objects
        return container.values().iterator();
    }

    public String toString() {
        return "C[" + wordHash + "] has " + container.size() + " entries";
    }
    
    public int hashCode() {
        return (int) kelondroBase64Order.enhancedCoder.decodeLong(this.wordHash.substring(0, 4));
    }
    
    public static indexContainer joinContainer(Set containers, long time, int maxDistance) {
        
        long stamp = System.currentTimeMillis();
        
        // order entities by their size
        TreeMap map = new TreeMap();
        indexTreeMapContainer singleContainer;
        Iterator i = containers.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleContainer = (indexTreeMapContainer) i.next();
            
            // check result
            if ((singleContainer == null) || (singleContainer.size() == 0)) return new indexTreeMapContainer(null); // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(new Long(singleContainer.size() * 1000 + count), singleContainer);
            count++;
        }
        
        // check if there is any result
        if (map.size() == 0) return new indexTreeMapContainer(null); // no result, nothing found
        
        // the map now holds the search results in order of number of hits per word
        // we now must pairwise build up a conjunction of these sets
        Long k = (Long) map.firstKey(); // the smallest, which means, the one with the least entries
        indexContainer searchA, searchB, searchResult = (indexContainer) map.remove(k);
        while ((map.size() > 0) && (searchResult.size() > 0)) {
            // take the first element of map which is a result and combine it with result
            k = (Long) map.firstKey(); // the next smallest...
            time -= (System.currentTimeMillis() - stamp); stamp = System.currentTimeMillis();
            searchA = searchResult;
            searchB = (indexContainer) map.remove(k);
            searchResult = indexTreeMapContainer.joinConstructive(searchA, searchB, 2 * time / (map.size() + 1), maxDistance);
            // free resources
            searchA = null;
            searchB = null;
        }

        // in 'searchResult' is now the combined search result
        if (searchResult.size() == 0) return new indexTreeMapContainer(null);
        return searchResult;
    }
    
    // join methods
    private static int log2(int x) {
        int l = 0;
        while (x > 0) {x = x >> 1; l++;}
        return l;
    }
    
    public static indexContainer joinConstructive(indexContainer i1, indexContainer i2, long time, int maxDistance) {
        if ((i1 == null) || (i2 == null)) return null;
        if ((i1.size() == 0) || (i2.size() == 0)) return new indexTreeMapContainer(null);
        
        // decide which method to use
        int high = ((i1.size() > i2.size()) ? i1.size() : i2.size());
        int low  = ((i1.size() > i2.size()) ? i2.size() : i1.size());
        int stepsEnum = 10 * (high + low - 1);
        int stepsTest = 12 * log2(high) * low;
        
        // start most efficient method
        if (stepsEnum > stepsTest) {
            if (i1.size() < i2.size())
                return joinConstructiveByTest(i1, i2, time, maxDistance);
            else
                return joinConstructiveByTest(i2, i1, time, maxDistance);
        } else {
            return joinConstructiveByEnumeration(i1, i2, time, maxDistance);
        }
    }
    
    private static indexContainer joinConstructiveByTest(indexContainer small, indexContainer large, long time, int maxDistance) {
        System.out.println("DEBUG: JOIN METHOD BY TEST");
        indexTreeMapContainer conj = new indexTreeMapContainer(null); // start with empty search result
        Iterator se = small.entries();
        indexEntry ie0, ie1;
        long stamp = System.currentTimeMillis();
            while ((se.hasNext()) && ((System.currentTimeMillis() - stamp) < time)) {
                ie0 = (indexEntry) se.next();
                ie1 = large.get(ie0.urlHash());
                if (ie1 != null) {
                    // this is a hit. Calculate word distance:
                    ie0.combineDistance(ie1);
                    if (ie0.worddistance() <= maxDistance) conj.add(ie0);
                }
            }
        return conj;
    }
    
    private static indexContainer joinConstructiveByEnumeration(indexContainer i1, indexContainer i2, long time, int maxDistance) {
        System.out.println("DEBUG: JOIN METHOD BY ENUMERATION");
        indexTreeMapContainer conj = new indexTreeMapContainer(null); // start with empty search result
        if (!((i1.getOrdering().signature().equals(i2.getOrdering().signature())) &&
              (i1.getOrderColumn() == i2.getOrderColumn()))) return conj; // ordering must be equal
        Iterator e1 = i1.entries();
        Iterator e2 = i2.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            indexURLEntry ie1;
            indexURLEntry ie2;
            ie1 = (indexURLEntry) e1.next();
            ie2 = (indexURLEntry) e2.next();

            long stamp = System.currentTimeMillis();
            while ((System.currentTimeMillis() - stamp) < time) {
                c = i1.getOrdering().compare(ie1.urlHash(), ie2.urlHash());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = (indexURLEntry) e1.next(); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = (indexURLEntry) e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1.combineDistance(ie2);
                    if (ie1.worddistance() <= maxDistance) conj.add(ie1);
                    if (e1.hasNext()) ie1 = (indexURLEntry) e1.next(); else break;
                    if (e2.hasNext()) ie2 = (indexURLEntry) e2.next(); else break;
                }
            }
        }
        return conj;
    }

    public Set urlHashes() {
        return container.keySet();
    }

}
