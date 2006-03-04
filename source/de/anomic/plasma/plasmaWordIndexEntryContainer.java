// plasmaIndexEntryContainer.java 
// ------------------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 07.05.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


/*
    an indexContainer is a bag of indexEntries for a single word
    such an container represents a RWI snippet:
    it collects a new RWI until it is so big that it should be flushed to either
    - an indexAssortment: collection of indexContainers of same size or
    - the backend storage
 
    the creationTime is necessary to organize caching of containers
*/

package de.anomic.plasma;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroOrder;

public final class plasmaWordIndexEntryContainer {

    private String wordHash;
    private final TreeMap container; // urlHash/plasmaWordIndexEntry - Mapping
    private long updateTime;
    private kelondroOrder ordering;
    
    public plasmaWordIndexEntryContainer(String wordHash) {
        this(wordHash, new kelondroNaturalOrder(true));
    }
    
    public plasmaWordIndexEntryContainer(String wordHash, kelondroOrder ordering) {
        this.wordHash = wordHash;
        this.updateTime = 0;
        this.ordering = ordering;
        container = new TreeMap(ordering); // a urlhash/plasmaWordIndexEntry - relation
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
    
    public String wordHash() {
        return wordHash;
    }

    public int add(plasmaWordIndexEntry entry) {
        return add(entry, System.currentTimeMillis());
    }
    
    public int add(plasmaWordIndexEntry entry, long updateTime) {
        this.updateTime = java.lang.Math.max(this.updateTime, updateTime);
        return (addi(entry)) ? 1 : 0;
    }
    
    public int add(plasmaWordIndexEntry[] entries, long updateTime) {
        int c = 0;
        for (int i = 0; i < entries.length; i++) if (addi(entries[i])) c++;
        this.updateTime = java.lang.Math.max(this.updateTime, updateTime);
        return c;
    }
    
    public int add(plasmaWordIndexEntryContainer c) {
        // returns the number of new elements
        if (c == null) return 0;
        Iterator i = c.entries();
        int x = 0;
        while (i.hasNext()) {
            try {
                if (addi((plasmaWordIndexEntry) i.next())) x++;
            } catch (ConcurrentModificationException e) {}
        }
        this.updateTime = java.lang.Math.max(this.updateTime, c.updateTime);
        return x;
    }

    private boolean addi(plasmaWordIndexEntry entry) {
        // returns true if the new entry was added, false if it already existet
        return (container.put(entry.getUrlHash(), entry) == null);
    }

    public boolean contains(String urlHash) {
        return container.containsKey(urlHash);
    }

    public plasmaWordIndexEntry get(String urlHash) {
        return (plasmaWordIndexEntry) container.get(urlHash);
    }
    
    public plasmaWordIndexEntry[] getEntryArray() {
        return (plasmaWordIndexEntry[]) container.values().toArray();
    }

    public plasmaWordIndexEntry remove(String urlHash) {
        return (plasmaWordIndexEntry) container.remove(urlHash);
    }

    public int removeEntries(String wordHash, String[] urlHashes, boolean deleteComplete) {
        if (!wordHash.equals(this.wordHash)) return 0;
        int count = 0;
        for (int i = 0; i < urlHashes.length; i++) count += (remove(urlHashes[i]) == null) ? 0 : 1;
        return count;
    }

    public Iterator entries() {
        // returns an iterator of plasmaWordIndexEntry objects
        return container.values().iterator();
    }

    public String toString() {
        return "C[" + wordHash + "] has " + container.size() + " entries";
    }
    
    public int hashCode() {
        return (int) kelondroBase64Order.enhancedCoder.decodeLong(this.wordHash.substring(0, 4));
    }
    
    public static plasmaWordIndexEntryContainer joinContainer(Set containers, long time, int maxDistance) {
        
        long stamp = System.currentTimeMillis();
        
        // order entities by their size
        TreeMap map = new TreeMap();
        plasmaWordIndexEntryContainer singleContainer;
        Iterator i = containers.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleContainer = (plasmaWordIndexEntryContainer) i.next();
            
            // check result
            if ((singleContainer == null) || (singleContainer.size() == 0)) return new plasmaWordIndexEntryContainer(null); // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(new Long(singleContainer.size() * 1000 + count), singleContainer);
            count++;
        }
        
        // check if there is any result
        if (map.size() == 0) return new plasmaWordIndexEntryContainer(null); // no result, nothing found
        
        // the map now holds the search results in order of number of hits per word
        // we now must pairwise build up a conjunction of these sets
        Long k = (Long) map.firstKey(); // the smallest, which means, the one with the least entries
        plasmaWordIndexEntryContainer searchA, searchB, searchResult = (plasmaWordIndexEntryContainer) map.remove(k);
        while ((map.size() > 0) && (searchResult.size() > 0)) {
            // take the first element of map which is a result and combine it with result
            k = (Long) map.firstKey(); // the next smallest...
            time -= (System.currentTimeMillis() - stamp); stamp = System.currentTimeMillis();
            searchA = searchResult;
            searchB = (plasmaWordIndexEntryContainer) map.remove(k);
            searchResult = plasmaWordIndexEntryContainer.joinConstructive(searchA, searchB, 2 * time / (map.size() + 1), maxDistance);
            // free resources
            searchA = null;
            searchB = null;
        }

        // in 'searchResult' is now the combined search result
        if (searchResult.size() == 0) return new plasmaWordIndexEntryContainer(null);
        return searchResult;
    }
    
    // join methods
    private static int log2(int x) {
        int l = 0;
        while (x > 0) {x = x >> 1; l++;}
        return l;
    }
    
    public static plasmaWordIndexEntryContainer joinConstructive(plasmaWordIndexEntryContainer i1, plasmaWordIndexEntryContainer i2, long time, int maxDistance) {
        if ((i1 == null) || (i2 == null)) return null;
        if ((i1.size() == 0) || (i2.size() == 0)) return new plasmaWordIndexEntryContainer(null);
        
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
    
    private static plasmaWordIndexEntryContainer joinConstructiveByTest(plasmaWordIndexEntryContainer small, plasmaWordIndexEntryContainer large, long time, int maxDistance) {
        System.out.println("DEBUG: JOIN METHOD BY TEST");
        plasmaWordIndexEntryContainer conj = new plasmaWordIndexEntryContainer(null); // start with empty search result
        Iterator se = small.entries();
        plasmaWordIndexEntry ie0, ie1;
        long stamp = System.currentTimeMillis();
            while ((se.hasNext()) && ((System.currentTimeMillis() - stamp) < time)) {
                ie0 = (plasmaWordIndexEntry) se.next();
                ie1 = large.get(ie0.getUrlHash());
                if (ie1 != null) {
                    // this is a hit. Calculate word distance:
                    ie0.combineDistance(ie1);
                    if (ie0.worddistance() <= maxDistance) conj.add(ie0);
                }
            }
        return conj;
    }
    
    private static plasmaWordIndexEntryContainer joinConstructiveByEnumeration(plasmaWordIndexEntryContainer i1, plasmaWordIndexEntryContainer i2, long time, int maxDistance) {
        System.out.println("DEBUG: JOIN METHOD BY ENUMERATION");
        plasmaWordIndexEntryContainer conj = new plasmaWordIndexEntryContainer(null); // start with empty search result
        if (!(i1.ordering.signature().equals(i2.ordering.signature()))) return conj; // ordering must be equal
        Iterator e1 = i1.entries();
        Iterator e2 = i2.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            plasmaWordIndexEntry ie1;
            plasmaWordIndexEntry ie2;
            ie1 = (plasmaWordIndexEntry) e1.next();
            ie2 = (plasmaWordIndexEntry) e2.next();

            long stamp = System.currentTimeMillis();
            while ((System.currentTimeMillis() - stamp) < time) {
                c = i1.ordering.compare(ie1.getUrlHash(), ie2.getUrlHash());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = (plasmaWordIndexEntry) e1.next(); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = (plasmaWordIndexEntry) e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1.combineDistance(ie2);
                    if (ie1.worddistance() <= maxDistance) conj.add(ie1);
                    if (e1.hasNext()) ie1 = (plasmaWordIndexEntry) e1.next(); else break;
                    if (e2.hasNext()) ie2 = (plasmaWordIndexEntry) e2.next(); else break;
                }
            }
        }
        return conj;
    }

}
