// indexContainer.java
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
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;

public class indexContainer extends kelondroRowSet {

    private String wordHash;

    public indexContainer(String wordHash, kelondroRowSet collection) {
        super(collection);
        this.wordHash = wordHash;
    }
    
    public indexContainer(String wordHash, kelondroRow rowdef, int objectCount) {
        super(rowdef, objectCount);
        this.wordHash = wordHash;
        this.lastTimeWrote = 0;
    }
    
    public indexContainer topLevelClone() {
        indexContainer newContainer = new indexContainer(this.wordHash, this.rowdef, this.size());
        newContainer.addAllUnique(this);
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
    
    public void add(indexRWIEntry entry) {
        // add without double-occurrence test
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize();
        this.addUnique(entry.toKelondroEntry());
    }
    
    public void add(indexRWIEntry entry, long updateTime) {
        // add without double-occurrence test
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize();
        this.add(entry);
        this.lastTimeWrote = updateTime;
    }
    

    public static final indexContainer mergeUnique(indexContainer a, boolean aIsClone, indexContainer b, boolean bIsClone) {
        if ((aIsClone) && (bIsClone)) {
            if (a.size() > b.size()) return (indexContainer) mergeUnique(a, b); else return (indexContainer) mergeUnique(b, a);
        }
        if (aIsClone) return (indexContainer) mergeUnique(a, b);
        if (bIsClone) return (indexContainer) mergeUnique(b, a);
        if (a.size() > b.size()) return (indexContainer) mergeUnique(a, b); else return (indexContainer) mergeUnique(b, a);
    }
    
    public static Object mergeUnique(Object a, Object b) {
        indexContainer c = (indexContainer) a;
        c.addAllUnique((indexContainer) b);
        return c;
    }
    
    public indexRWIEntry put(indexRWIEntry entry) {
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize();
        kelondroRow.Entry r = super.put(entry.toKelondroEntry());
        if (r == null) return null;
        return new indexRWIEntry(r);
    }
    
    public boolean putRecent(indexRWIEntry entry) {
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize();
        // returns true if the new entry was added, false if it already existed
        kelondroRow.Entry oldEntryRow = this.put(entry.toKelondroEntry());
        if (oldEntryRow == null) {
            return true;
        } else {
            indexRWIEntry oldEntry = new indexRWIEntry(oldEntryRow);
            if (entry.isOlder(oldEntry)) { // A more recent Entry is already in this container
                this.put(oldEntry.toKelondroEntry()); // put it back
                return false;
            } else {
                return true;
            }
        }
    }

    public int putAllRecent(indexContainer c) {
        // adds all entries in c and checks every entry for double-occurrence
        // returns the number of new elements
        if (c == null) return 0;
        int x = 0;
        synchronized (c) {
            Iterator i = c.entries();
            while (i.hasNext()) {
                try {
                    if (putRecent((indexRWIEntry) i.next())) x++;
                } catch (ConcurrentModificationException e) {
                    e.printStackTrace();
                }
            }
        }
        this.lastTimeWrote = java.lang.Math.max(this.lastTimeWrote, c.updated());
        return x;
    }
    
    public indexRWIEntry get(String urlHash) {
        kelondroRow.Entry entry = this.get(urlHash.getBytes());
        if (entry == null) return null;
        return new indexRWIEntry(entry);
    }

    public indexRWIEntry remove(String urlHash) {
        kelondroRow.Entry entry = this.remove(urlHash.getBytes());
        if (entry == null) return null;
        return new indexRWIEntry(entry);
    }

    public boolean removeEntry(String wordHash, String urlHash) {
        if (!wordHash.equals(this.wordHash)) return false;
        return remove(urlHash) != null;
    }

    public int removeEntries(String wordHash, Set urlHashes) {
        if (!wordHash.equals(this.wordHash)) return 0;
        int count = 0;
        Iterator i = urlHashes.iterator();
        while (i.hasNext()) count += (remove((String) i.next()) == null) ? 0 : 1;
        return count;
    }

    public void removeEntriesMultiple(Set wordHashes, Set urlHashes) {
        Iterator i = wordHashes.iterator();
        while (i.hasNext()) {
            removeEntries((String) i.next(), urlHashes);
        }
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
            return new indexRWIEntry(rentry);
        }

        public void remove() {
            rowEntryIterator.remove();
        }
        
    }
    
    public static Method containerMergeMethod = null;
    static {
        try {
            Class c = Class.forName("de.anomic.index.indexContainer");
            containerMergeMethod = c.getMethod("mergeUnique", new Class[]{Object.class, Object.class});
        } catch (SecurityException e) {
            System.out.println("Error while initializing containerMerge.SecurityException: " + e.getMessage());
            containerMergeMethod = null;
        } catch (ClassNotFoundException e) {
            System.out.println("Error while initializing containerMerge.ClassNotFoundException: " + e.getMessage());
            containerMergeMethod = null;
        } catch (NoSuchMethodException e) {
            System.out.println("Error while initializing containerMerge.NoSuchMethodException: " + e.getMessage());
            containerMergeMethod = null;
        }
    }

    public static indexContainer joinContainers(Collection containers, long time, int maxDistance) {
        
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
            if ((singleContainer == null) || (singleContainer.size() == 0)) return null; // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(new Long(singleContainer.size() * 1000 + count), singleContainer);
            count++;
        }
        
        // check if there is any result
        if (map.size() == 0) return null; // no result, nothing found
        
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
            searchResult = indexContainer.joinConstructive(searchA, searchB, 2 * time / (map.size() + 1), maxDistance);
            // free resources
            searchA = null;
            searchB = null;
        }

        // in 'searchResult' is now the combined search result
        if (searchResult.size() == 0) return null;
        return searchResult;
    }
    
    public static indexContainer excludeContainers(indexContainer pivot, Collection containers, long time) {
        
        long stamp = System.currentTimeMillis();
        
        // check if there is any result
        if ((containers == null) || (containers.size() == 0)) return pivot; // no result, nothing found
        
        Iterator i = containers.iterator();
        while (i.hasNext()) {
        	time -= (System.currentTimeMillis() - stamp); stamp = System.currentTimeMillis();
        	pivot = excludeDestructive(pivot, (indexContainer) i.next(), time);
        	if ((pivot == null) || (pivot.size() == 0)) return null;
        }
        
        return pivot;
    }
    
    // join methods
    private static int log2(int x) {
        int l = 0;
        while (x > 0) {x = x >> 1; l++;}
        return l;
    }
    
    public static indexContainer joinConstructive(indexContainer i1, indexContainer i2, long time, int maxDistance) {
        if ((i1 == null) || (i2 == null)) return null;
        if ((i1.size() == 0) || (i2.size() == 0)) return null;
        
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
        assert small.rowdef.equals(large.rowdef) : "small = " + small.rowdef.toString() + "; large = " + large.rowdef.toString();
        int keylength = small.rowdef.width(0);
        assert (keylength == large.rowdef.width(0));
        indexContainer conj = new indexContainer(null, small.rowdef, 0); // start with empty search result
        Iterator se = small.entries();
        indexRWIEntry ie0, ie1;
        long stamp = System.currentTimeMillis();
            while ((se.hasNext()) && ((System.currentTimeMillis() - stamp) < time)) {
                ie0 = (indexRWIEntry) se.next();
                ie1 = large.get(ie0.urlHash());
                if ((ie0 != null) && (ie1 != null)) {
                    assert (ie0.urlHash().length() == keylength) : "ie0.urlHash() = " + ie0.urlHash();
                    assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                    // this is a hit. Calculate word distance:
                    ie0.combineDistance(ie1);
                    if (ie0.worddistance() <= maxDistance) conj.add(ie0);
                }
            }
        return conj;
    }
    
    private static indexContainer joinConstructiveByEnumeration(indexContainer i1, indexContainer i2, long time, int maxDistance) {
        System.out.println("DEBUG: JOIN METHOD BY ENUMERATION");
        assert i1.rowdef.equals(i2.rowdef) : "i1 = " + i1.rowdef.toString() + "; i2 = " + i2.rowdef.toString();
        int keylength = i1.rowdef.width(0);
        assert (keylength == i2.rowdef.width(0));
        indexContainer conj = new indexContainer(null, i1.rowdef, 0); // start with empty search result
        if (!((i1.rowdef.getOrdering().signature().equals(i2.rowdef.getOrdering().signature())) &&
              (i1.rowdef.primaryKey() == i2.rowdef.primaryKey()))) return conj; // ordering must be equal
        Iterator e1 = i1.entries();
        Iterator e2 = i2.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            indexRWIEntry ie1;
            indexRWIEntry ie2;
            ie1 = (indexRWIEntry) e1.next();
            ie2 = (indexRWIEntry) e2.next();

            long stamp = System.currentTimeMillis();
            while ((System.currentTimeMillis() - stamp) < time) {
                assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                assert (ie2.urlHash().length() == keylength) : "ie2.urlHash() = " + ie2.urlHash();
                c = i1.rowdef.getOrdering().compare(ie1.urlHash(), ie2.urlHash());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = (indexRWIEntry) e1.next(); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = (indexRWIEntry) e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1.combineDistance(ie2);
                    if (ie1.worddistance() <= maxDistance) conj.add(ie1);
                    if (e1.hasNext()) ie1 = (indexRWIEntry) e1.next(); else break;
                    if (e2.hasNext()) ie2 = (indexRWIEntry) e2.next(); else break;
                }
            }
        }
        return conj;
    }
    
    public static indexContainer excludeDestructive(indexContainer pivot, indexContainer excl, long time) {
        if (pivot == null) return null;
        if (excl == null) return pivot;
        if (pivot.size() == 0) return null;
        if (excl.size() == 0) return pivot;
        
        // decide which method to use
        int high = ((pivot.size() > excl.size()) ? pivot.size() : excl.size());
        int low  = ((pivot.size() > excl.size()) ? excl.size() : pivot.size());
        int stepsEnum = 10 * (high + low - 1);
        int stepsTest = 12 * log2(high) * low;
        
        // start most efficient method
        if (stepsEnum > stepsTest) {
            return excludeDestructiveByTest(pivot, excl, time);
        } else {
            return excludeDestructiveByEnumeration(pivot, excl, time);
        }
    }
    
    private static indexContainer excludeDestructiveByTest(indexContainer pivot, indexContainer excl, long time) {
        assert pivot.rowdef.equals(excl.rowdef) : "small = " + pivot.rowdef.toString() + "; large = " + excl.rowdef.toString();
        int keylength = pivot.rowdef.width(0);
        assert (keylength == excl.rowdef.width(0));
        boolean iterate_pivot = pivot.size() < excl.size();
        Iterator se = (iterate_pivot) ? pivot.entries() : excl.entries();
        indexRWIEntry ie0, ie1;
        long stamp = System.currentTimeMillis();
            while ((se.hasNext()) && ((System.currentTimeMillis() - stamp) < time)) {
                ie0 = (indexRWIEntry) se.next();
                ie1 = excl.get(ie0.urlHash());
                if ((ie0 != null) && (ie1 != null)) {
                    assert (ie0.urlHash().length() == keylength) : "ie0.urlHash() = " + ie0.urlHash();
                    assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                    if (iterate_pivot) se.remove(); pivot.remove(ie0.urlHash().getBytes());
                }
            }
        return pivot;
    }
    
    private static indexContainer excludeDestructiveByEnumeration(indexContainer pivot, indexContainer excl, long time) {
        assert pivot.rowdef.equals(excl.rowdef) : "i1 = " + pivot.rowdef.toString() + "; i2 = " + excl.rowdef.toString();
        int keylength = pivot.rowdef.width(0);
        assert (keylength == excl.rowdef.width(0));
        if (!((pivot.rowdef.getOrdering().signature().equals(excl.rowdef.getOrdering().signature())) &&
              (pivot.rowdef.primaryKey() == excl.rowdef.primaryKey()))) return pivot; // ordering must be equal
        Iterator e1 = pivot.entries();
        Iterator e2 = excl.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            indexRWIEntry ie1;
            indexRWIEntry ie2;
            ie1 = (indexRWIEntry) e1.next();
            ie2 = (indexRWIEntry) e2.next();

            long stamp = System.currentTimeMillis();
            while ((System.currentTimeMillis() - stamp) < time) {
                assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                assert (ie2.urlHash().length() == keylength) : "ie2.urlHash() = " + ie2.urlHash();
                c = pivot.rowdef.getOrdering().compare(ie1.urlHash(), ie2.urlHash());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = (indexRWIEntry) e1.next(); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = (indexRWIEntry) e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1.combineDistance(ie2);
                    e1.remove();
                    if (e1.hasNext()) ie1 = (indexRWIEntry) e1.next(); else break;
                    if (e2.hasNext()) ie2 = (indexRWIEntry) e2.next(); else break;
                }
            }
        }
        return pivot;
    }

    public String toString() {
        return "C[" + wordHash + "] has " + this.size() + " entries";
    }
    
    public int hashCode() {
        return (int) kelondroBase64Order.enhancedCoder.decodeLong(this.wordHash.substring(0, 4));
    }
    
}
