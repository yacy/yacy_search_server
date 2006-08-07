// indexRowSetContainer.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 04.07.2006 on http://www.anomic.de
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

package de.anomic.index;

import java.lang.reflect.Method;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;

public class indexRowSetContainer extends kelondroRowSet implements indexContainer {

    private String wordHash;

    public indexRowSetContainer(String wordHash) {
        this(wordHash, new kelondroNaturalOrder(true), 0);
    }
    
    public indexRowSetContainer(String wordHash, kelondroRowSet collection) {
        super(collection);
        this.wordHash = wordHash;
    }
    
    public indexRowSetContainer(String wordHash, kelondroOrder ordering, int column) {
        super(indexURLEntry.urlEntryRow);
        this.wordHash = wordHash;
        this.lastTimeWrote = 0;
        this.setOrdering(ordering, column);
    }
    
    public indexContainer topLevelClone() {
        indexContainer newContainer = new indexRowSetContainer(this.wordHash, this.sortOrder, this.sortColumn);
        newContainer.add(this, -1);
        return newContainer;
    }
    
    public void setWordHash(String newWordHash) {
        this.wordHash = newWordHash;
    }

    public long updated() {
        return super.lastWrote();
    }

    public String getWordHash() {
        return wordHash;
    }

    public int add(indexEntry entry) {
        this.add(entry.toKelondroEntry());
        return 1;
    }

    public int add(indexEntry entry, long updateTime) {
        this.add(entry);
        this.lastTimeWrote = updateTime;
        return 1;
    }

    public int add(indexEntry[] entries, long updateTime) {
        for (int i = 0; i < entries.length; i++) this.add(entries[i], updateTime);
        return entries.length;
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
                    if (addi((indexEntry) i.next())) x++;
                } catch (ConcurrentModificationException e) {}
            }
        }
        this.lastTimeWrote = java.lang.Math.max(this.lastTimeWrote, c.updated());
        return x;
    }
    
    private boolean addi(indexEntry entry) {
        // returns true if the new entry was added, false if it already existed
        kelondroRow.Entry oldEntryRow = this.put(entry.toKelondroEntry());
        if (oldEntryRow == null) {
            return true;
        } else {
            indexEntry oldEntry = new indexURLEntry(oldEntryRow); // FIXME: see if cloning is necessary
            if (entry.isOlder(oldEntry)) { // A more recent Entry is already in this container
                this.put(oldEntry.toKelondroEntry()); // put it back
                return false;
            } else {
                return true;
            }
        }
    }

    public indexEntry get(String urlHash) {
        kelondroRow.Entry entry = this.get(urlHash.getBytes());
        if (entry == null) return null;
        return new indexURLEntry(entry);
    }

    public indexEntry remove(String urlHash) {
        kelondroRow.Entry entry = this.remove(urlHash.getBytes());
        if (entry == null) return null;
        return new indexURLEntry(entry);
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
        return new entryIterator();
    }

    public class entryIterator implements Iterator {

        Iterator rowEntryIterator;
        
        public entryIterator() {
            rowEntryIterator = rows();
        }
        
        public boolean hasNext() {
            return rowEntryIterator.hasNext();
        }

        public Object next() {
            kelondroRow.Entry rentry = (kelondroRow.Entry) rowEntryIterator.next();
            if (rentry == null) return null;
            return new indexURLEntry(rentry);
        }

        public void remove() {
            rowEntryIterator.remove();
        }
        
    }
    
    public static Method containerMergeMethod = null;
    static {
        try {
            Class c = Class.forName("de.anomic.index.indexRowSetContainer");
            containerMergeMethod = c.getMethod("containerMerge", new Class[]{Object.class, Object.class});
        } catch (SecurityException e) {
            System.out.println("Error while initializing containerMerge: " + e.getMessage());
            containerMergeMethod = null;
        } catch (ClassNotFoundException e) {
            System.out.println("Error while initializing containerMerge: " + e.getMessage());
            containerMergeMethod = null;
        } catch (NoSuchMethodException e) {
            System.out.println("Error while initializing containerMerge: " + e.getMessage());
            containerMergeMethod = null;
        }
    }

    public static Object containerMerge(Object a, Object b) {
        indexContainer c = (indexContainer) a;
        c.add((indexContainer) b, -1);
        return c;
    }
    
    public static indexContainer joinContainer(Set containers, long time, int maxDistance) {
        
        long stamp = System.currentTimeMillis();
        
        // order entities by their size
        TreeMap map = new TreeMap();
        indexContainer singleContainer;
        Iterator i = containers.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleContainer = (indexContainer) i.next();
            
            // check result
            if ((singleContainer == null) || (singleContainer.size() == 0)) return new indexRowSetContainer(null); // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(new Long(singleContainer.size() * 1000 + count), singleContainer);
            count++;
        }
        
        // check if there is any result
        if (map.size() == 0) return new indexRowSetContainer(null); // no result, nothing found
        
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
            searchResult = indexRowSetContainer.joinConstructive(searchA, searchB, 2 * time / (map.size() + 1), maxDistance);
            // free resources
            searchA = null;
            searchB = null;
        }

        // in 'searchResult' is now the combined search result
        if (searchResult.size() == 0) return new indexRowSetContainer(null);
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
        if ((i1.size() == 0) || (i2.size() == 0)) return new indexRowSetContainer(null);
        
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
        indexContainer conj = new indexRowSetContainer(null); // start with empty search result
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
        indexContainer conj = new indexRowSetContainer(null); // start with empty search result
        if (!((i1.order().signature().equals(i2.order().signature())) &&
              (i1.orderColumn() == i2.orderColumn()))) return conj; // ordering must be equal
        Iterator e1 = i1.entries();
        Iterator e2 = i2.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            indexEntry ie1;
            indexEntry ie2;
            ie1 = (indexEntry) e1.next();
            ie2 = (indexEntry) e2.next();

            long stamp = System.currentTimeMillis();
            while ((System.currentTimeMillis() - stamp) < time) {
                c = i1.order().compare(ie1.urlHash(), ie2.urlHash());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = (indexEntry) e1.next(); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = (indexEntry) e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1.combineDistance(ie2);
                    if (ie1.worddistance() <= maxDistance) conj.add(ie1);
                    if (e1.hasNext()) ie1 = (indexEntry) e1.next(); else break;
                    if (e2.hasNext()) ie2 = (indexEntry) e2.next(); else break;
                }
            }
        }
        return conj;
    }

    public String toString() {
        return "C[" + wordHash + "] has " + this.size() + " entries";
    }
    
    public int hashCode() {
        return (int) kelondroBase64Order.enhancedCoder.decodeLong(this.wordHash.substring(0, 4));
    }
    
}
