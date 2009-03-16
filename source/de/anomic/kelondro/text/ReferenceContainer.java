// ReferenceContainer.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.07.2006 on http://yacy.net
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

package de.anomic.kelondro.text;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.RowSet;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.util.ByteBuffer;

/**
 * A ReferenceContainer is a set of ReferenceRows entries. Since ReferenceRow entries are special
 * Row entries, a collection of ReferenceRows can be contained in a RowSet. This class extends
 * the RowSet with methods for the handling of special ReferenceRow Row entry objects.
 */
public class ReferenceContainer extends RowSet {

    private String wordHash;

    public ReferenceContainer(final String wordHash, final RowSet collection) {
        super(collection);
        this.wordHash = wordHash;
    }
    
    public ReferenceContainer(String wordHash, ReferenceRow entry) {
        super(ReferenceRow.urlEntryRow, 1);
        this.add(entry);
        this.wordHash = wordHash;
    }
    
    public ReferenceContainer(final String wordHash, final Row rowdef, final int objectCount) {
        super(rowdef, objectCount);
        this.wordHash = wordHash;
        this.lastTimeWrote = 0;
    }
    
    public ReferenceContainer topLevelClone() {
        final ReferenceContainer newContainer = new ReferenceContainer(this.wordHash, this.rowdef, this.size());
        newContainer.addAllUnique(this);
        return newContainer;
    }
    
    public static ReferenceContainer emptyContainer(final String wordHash, final int elementCount) {
        return new ReferenceContainer(wordHash, ReferenceRow.urlEntryRow, elementCount);
    }

    public void setWordHash(final String newWordHash) {
        this.wordHash = newWordHash;
    }

    public long updated() {
        return super.lastWrote();
    }

    public String getWordHash() {
        return wordHash;
    }
    
    public void add(final ReferenceRow entry) {
        // add without double-occurrence test
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize;
        this.addUnique(entry.toKelondroEntry());
    }
    
    public void add(final Reference entry, final long updateTime) {
        // add without double-occurrence test
        if (entry instanceof ReferenceRow) {
            assert ((ReferenceRow) entry).toKelondroEntry().objectsize() == super.rowdef.objectsize;
            this.add((ReferenceRow) entry);
        } else {
            this.add(((ReferenceVars) entry).toRowEntry()); 
        }
        this.lastTimeWrote = updateTime;
    }
    
    public static final ReferenceContainer mergeUnique(final ReferenceContainer a, final boolean aIsClone, final ReferenceContainer b, final boolean bIsClone) {
        if ((aIsClone) && (bIsClone)) {
            if (a.size() > b.size()) return (ReferenceContainer) mergeUnique(a, b); else return (ReferenceContainer) mergeUnique(b, a);
        }
        if (aIsClone) return (ReferenceContainer) mergeUnique(a, b);
        if (bIsClone) return (ReferenceContainer) mergeUnique(b, a);
        if (a.size() > b.size()) return (ReferenceContainer) mergeUnique(a, b); else return (ReferenceContainer) mergeUnique(b, a);
    }
    
    public static Object mergeUnique(final Object a, final Object b) {
        final ReferenceContainer c = (ReferenceContainer) a;
        c.addAllUnique((ReferenceContainer) b);
        return c;
    }
    
    public ReferenceContainer merge(final ReferenceContainer c) {
    	return new ReferenceContainer(this.wordHash, this.merge(c));
    }
    
    public Reference put(final ReferenceRow entry) {
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize;
        final Row.Entry r = super.replace(entry.toKelondroEntry());
        if (r == null) return null;
        return new ReferenceRow(r);
    }
    
    public boolean putRecent(final ReferenceRow entry) {
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize;
        // returns true if the new entry was added, false if it already existed
        final Row.Entry oldEntryRow = this.replace(entry.toKelondroEntry());
        if (oldEntryRow == null) {
            return true;
        }
        final ReferenceRow oldEntry = new ReferenceRow(oldEntryRow);
        if (entry.isOlder(oldEntry)) { // A more recent Entry is already in this container
            this.replace(oldEntry.toKelondroEntry()); // put it back
            return false;
        }
        return true;
    }

    public int putAllRecent(final ReferenceContainer c) {
        // adds all entries in c and checks every entry for double-occurrence
        // returns the number of new elements
        if (c == null) return 0;
        int x = 0;
        synchronized (c) {
            final Iterator<ReferenceRow> i = c.entries();
            while (i.hasNext()) {
                try {
                    if (putRecent(i.next())) x++;
                } catch (final ConcurrentModificationException e) {
                    e.printStackTrace();
                }
            }
        }
        this.lastTimeWrote = java.lang.Math.max(this.lastTimeWrote, c.updated());
        return x;
    }
    
    public Reference get(final String urlHash) {
        final Row.Entry entry = this.get(urlHash.getBytes());
        if (entry == null) return null;
        return new ReferenceRow(entry);
    }

    /**
     * remove a url reference from the container.
     * if the url hash was found, return the entry, but delete the entry from the container
     * if the entry was not found, return null.
     */
    public Reference remove(final String urlHash) {
        final Row.Entry entry = remove(urlHash.getBytes());
        if (entry == null) return null;
        return new ReferenceRow(entry);
    }

    public int removeEntries(final Set<String> urlHashes) {
        int count = 0;
        final Iterator<String> i = urlHashes.iterator();
        while (i.hasNext()) count += (remove(i.next()) == null) ? 0 : 1;
        return count;
    }

    public Iterator<ReferenceRow> entries() {
        // returns an iterator of indexRWIEntry objects
        return new entryIterator();
    }

    public class entryIterator implements Iterator<ReferenceRow> {

        Iterator<Row.Entry> rowEntryIterator;
        
        public entryIterator() {
            rowEntryIterator = iterator();
        }
        
        public boolean hasNext() {
            return rowEntryIterator.hasNext();
        }

        public ReferenceRow next() {
            final Row.Entry rentry = rowEntryIterator.next();
            if (rentry == null) return null;
            return new ReferenceRow(rentry);
        }

        public void remove() {
            rowEntryIterator.remove();
        }
        
    }
    
    public static final Method containerMergeMethod;
    static {
        Method meth = null;
        try {
            final Class<?> c = Class.forName("de.anomic.kelondro.text.ReferenceContainer");
            meth = c.getMethod("mergeUnique", new Class[]{Object.class, Object.class});
        } catch (final SecurityException e) {
            System.out.println("Error while initializing containerMerge.SecurityException: " + e.getMessage());
            meth = null;
        } catch (final ClassNotFoundException e) {
            System.out.println("Error while initializing containerMerge.ClassNotFoundException: " + e.getMessage());
            meth = null;
        } catch (final NoSuchMethodException e) {
            System.out.println("Error while initializing containerMerge.NoSuchMethodException: " + e.getMessage());
            meth = null;
        }
        containerMergeMethod = meth;
    }

    public static ReferenceContainer joinExcludeContainers(
            final Collection<ReferenceContainer> includeContainers,
            final Collection<ReferenceContainer> excludeContainers,
            final int maxDistance) {
        // join a search result and return the joincount (number of pages after join)

        // since this is a conjunction we return an empty entity if any word is not known
        if (includeContainers == null) return ReferenceContainer.emptyContainer(null, 0);

        // join the result
        final ReferenceContainer rcLocal = ReferenceContainer.joinContainers(includeContainers, maxDistance);
        if (rcLocal == null) return ReferenceContainer.emptyContainer(null, 0);
        excludeContainers(rcLocal, excludeContainers);
        
        return rcLocal;
    }
    
    public static ReferenceContainer joinContainers(final Collection<ReferenceContainer> containers, final int maxDistance) {
        
        // order entities by their size
        final TreeMap<Long, ReferenceContainer> map = new TreeMap<Long, ReferenceContainer>();
        ReferenceContainer singleContainer;
        final Iterator<ReferenceContainer> i = containers.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleContainer = i.next();
            
            // check result
            if ((singleContainer == null) || (singleContainer.size() == 0)) return null; // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(Long.valueOf(singleContainer.size() * 1000 + count), singleContainer);
            count++;
        }
        
        // check if there is any result
        if (map.size() == 0) return null; // no result, nothing found
        
        // the map now holds the search results in order of number of hits per word
        // we now must pairwise build up a conjunction of these sets
        Long k = map.firstKey(); // the smallest, which means, the one with the least entries
        ReferenceContainer searchA, searchB, searchResult = map.remove(k);
        while ((map.size() > 0) && (searchResult.size() > 0)) {
            // take the first element of map which is a result and combine it with result
            k = map.firstKey(); // the next smallest...
            searchA = searchResult;
            searchB = map.remove(k);
            searchResult = ReferenceContainer.joinConstructive(searchA, searchB, maxDistance);
            // free resources
            searchA = null;
            searchB = null;
        }

        // in 'searchResult' is now the combined search result
        if (searchResult.size() == 0) return null;
        return searchResult;
    }
    
    public static ReferenceContainer excludeContainers(ReferenceContainer pivot, final Collection<ReferenceContainer> containers) {
        
        // check if there is any result
        if ((containers == null) || (containers.size() == 0)) return pivot; // no result, nothing found
        
        final Iterator<ReferenceContainer> i = containers.iterator();
        while (i.hasNext()) {
        	pivot = excludeDestructive(pivot, i.next());
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
    
    public static ReferenceContainer joinConstructive(final ReferenceContainer i1, final ReferenceContainer i2, final int maxDistance) {
        if ((i1 == null) || (i2 == null)) return null;
        if ((i1.size() == 0) || (i2.size() == 0)) return null;
        
        // decide which method to use
        final int high = ((i1.size() > i2.size()) ? i1.size() : i2.size());
        final int low  = ((i1.size() > i2.size()) ? i2.size() : i1.size());
        final int stepsEnum = 10 * (high + low - 1);
        final int stepsTest = 12 * log2(high) * low;
        
        // start most efficient method
        if (stepsEnum > stepsTest) {
            if (i1.size() < i2.size())
                return joinConstructiveByTest(i1, i2, maxDistance);
            else
                return joinConstructiveByTest(i2, i1, maxDistance);
        }
        return joinConstructiveByEnumeration(i1, i2, maxDistance);
    }
    
    private static ReferenceContainer joinConstructiveByTest(final ReferenceContainer small, final ReferenceContainer large, final int maxDistance) {
        System.out.println("DEBUG: JOIN METHOD BY TEST, maxdistance = " + maxDistance);
        assert small.rowdef.equals(large.rowdef) : "small = " + small.rowdef.toString() + "; large = " + large.rowdef.toString();
        final int keylength = small.rowdef.width(0);
        assert (keylength == large.rowdef.width(0));
        final ReferenceContainer conj = new ReferenceContainer(null, small.rowdef, 0); // start with empty search result
        final Iterator<ReferenceRow> se = small.entries();
        ReferenceVars ie0;
        Reference ie1;
        while (se.hasNext()) {
            ie0 = new ReferenceVars(se.next());
            ie1 = large.get(ie0.urlHash());
            if ((ie0 != null) && (ie1 != null)) {
                assert (ie0.urlHash().length() == keylength) : "ie0.urlHash() = " + ie0.urlHash();
                assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                // this is a hit. Calculate word distance:
                ie0.join(ie1);
                if (ie0.worddistance() <= maxDistance) conj.add(ie0.toRowEntry());
            }
        }
        return conj;
    }
    
    private static ReferenceContainer joinConstructiveByEnumeration(final ReferenceContainer i1, final ReferenceContainer i2, final int maxDistance) {
        System.out.println("DEBUG: JOIN METHOD BY ENUMERATION, maxdistance = " + maxDistance);
        assert i1.rowdef.equals(i2.rowdef) : "i1 = " + i1.rowdef.toString() + "; i2 = " + i2.rowdef.toString();
        final int keylength = i1.rowdef.width(0);
        assert (keylength == i2.rowdef.width(0));
        final ReferenceContainer conj = new ReferenceContainer(null, i1.rowdef, 0); // start with empty search result
        if (!((i1.rowdef.getOrdering().signature().equals(i2.rowdef.getOrdering().signature())))) return conj; // ordering must be equal
        final Iterator<ReferenceRow> e1 = i1.entries();
        final Iterator<ReferenceRow> e2 = i2.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            ReferenceVars ie1;
            Reference ie2;
            ie1 = new ReferenceVars(e1.next());
            ie2 = e2.next();

            while (true) {
                assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                assert (ie2.urlHash().length() == keylength) : "ie2.urlHash() = " + ie2.urlHash();
                c = i1.rowdef.getOrdering().compare(ie1.urlHash().getBytes(), ie2.urlHash().getBytes());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = new ReferenceVars(e1.next()); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1.join(ie2);
                    if (ie1.worddistance() <= maxDistance) conj.add(ie1.toRowEntry());
                    if (e1.hasNext()) ie1 = new ReferenceVars(e1.next()); else break;
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                }
            }
        }
        return conj;
    }
    
    public static ReferenceContainer excludeDestructive(final ReferenceContainer pivot, final ReferenceContainer excl) {
        if (pivot == null) return null;
        if (excl == null) return pivot;
        if (pivot.size() == 0) return null;
        if (excl.size() == 0) return pivot;
        
        // decide which method to use
        final int high = ((pivot.size() > excl.size()) ? pivot.size() : excl.size());
        final int low  = ((pivot.size() > excl.size()) ? excl.size() : pivot.size());
        final int stepsEnum = 10 * (high + low - 1);
        final int stepsTest = 12 * log2(high) * low;
        
        // start most efficient method
        if (stepsEnum > stepsTest) {
            return excludeDestructiveByTest(pivot, excl);
        }
        return excludeDestructiveByEnumeration(pivot, excl);
    }
    
    private static ReferenceContainer excludeDestructiveByTest(final ReferenceContainer pivot, final ReferenceContainer excl) {
        assert pivot.rowdef.equals(excl.rowdef) : "small = " + pivot.rowdef.toString() + "; large = " + excl.rowdef.toString();
        final int keylength = pivot.rowdef.width(0);
        assert (keylength == excl.rowdef.width(0));
        final boolean iterate_pivot = pivot.size() < excl.size();
        final Iterator<ReferenceRow> se = (iterate_pivot) ? pivot.entries() : excl.entries();
        Reference ie0, ie1;
            while (se.hasNext()) {
                ie0 = se.next();
                ie1 = excl.get(ie0.urlHash());
                if ((ie0 != null) && (ie1 != null)) {
                    assert (ie0.urlHash().length() == keylength) : "ie0.urlHash() = " + ie0.urlHash();
                    assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                    if (iterate_pivot) se.remove(); pivot.remove(ie0.urlHash().getBytes());
                }
            }
        return pivot;
    }
    
    private static ReferenceContainer excludeDestructiveByEnumeration(final ReferenceContainer pivot, final ReferenceContainer excl) {
        assert pivot.rowdef.equals(excl.rowdef) : "i1 = " + pivot.rowdef.toString() + "; i2 = " + excl.rowdef.toString();
        final int keylength = pivot.rowdef.width(0);
        assert (keylength == excl.rowdef.width(0));
        if (!((pivot.rowdef.getOrdering().signature().equals(excl.rowdef.getOrdering().signature())))) return pivot; // ordering must be equal
        final Iterator<ReferenceRow> e1 = pivot.entries();
        final Iterator<ReferenceRow> e2 = excl.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            ReferenceVars ie1;
            Reference ie2;
            ie1 = new ReferenceVars(e1.next());
            ie2 = e2.next();

            while (true) {
                assert (ie1.urlHash().length() == keylength) : "ie1.urlHash() = " + ie1.urlHash();
                assert (ie2.urlHash().length() == keylength) : "ie2.urlHash() = " + ie2.urlHash();
                c = pivot.rowdef.getOrdering().compare(ie1.urlHash().getBytes(), ie2.urlHash().getBytes());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = new ReferenceVars(e1.next()); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1.join(ie2);
                    e1.remove();
                    if (e1.hasNext()) ie1 = new ReferenceVars(e1.next()); else break;
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                }
            }
        }
        return pivot;
    }

    public String toString() {
        return "C[" + wordHash + "] has " + this.size() + " entries";
    }
    
    public int hashCode() {
        return (int) Base64Order.enhancedCoder.decodeLong(this.wordHash.substring(0, 4));
    }
    

    public static final ByteBuffer compressIndex(final ReferenceContainer inputContainer, final ReferenceContainer excludeContainer, final long maxtime) {
        // collect references according to domains
        final long timeout = (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        final TreeMap<String, String> doms = new TreeMap<String, String>();
        synchronized (inputContainer) {
            final Iterator<ReferenceRow> i = inputContainer.entries();
            Reference iEntry;
            String dom, paths;
            while (i.hasNext()) {
                iEntry = i.next();
                if ((excludeContainer != null) && (excludeContainer.get(iEntry.urlHash()) != null)) continue; // do not include urls that are in excludeContainer
                dom = iEntry.urlHash().substring(6);
                if ((paths = doms.get(dom)) == null) {
                    doms.put(dom, iEntry.urlHash().substring(0, 6));
                } else {
                    doms.put(dom, paths + iEntry.urlHash().substring(0, 6));
                }
                if (System.currentTimeMillis() > timeout)
                    break;
            }
        }
        // construct a result string
        final ByteBuffer bb = new ByteBuffer(inputContainer.size() * 6);
        bb.append('{');
        final Iterator<Map.Entry<String, String>> i = doms.entrySet().iterator();
        Map.Entry<String, String> entry;
        while (i.hasNext()) {
            entry = i.next();
            bb.append(entry.getKey());
            bb.append(':');
            bb.append(entry.getValue());
            if (System.currentTimeMillis() > timeout)
                break;
            if (i.hasNext())
                bb.append(',');
        }
        bb.append('}');
        return bb;
    }

    public static final void decompressIndex(final TreeMap<String, String> target, ByteBuffer ci, final String peerhash) {
        // target is a mapping from url-hashes to a string of peer-hashes
        if ((ci.byteAt(0) == '{') && (ci.byteAt(ci.length() - 1) == '}')) {
            //System.out.println("DEBUG-DECOMPRESS: input is " + ci.toString());
            ci = ci.trim(1, ci.length() - 2);
            String dom, url, peers;
            while ((ci.length() >= 13) && (ci.byteAt(6) == ':')) {
                assert ci.length() >= 6 : "ci.length() = " + ci.length();
                dom = ci.toString(0, 6);
                ci.trim(7);
                while ((ci.length() > 0) && (ci.byteAt(0) != ',')) {
                    assert ci.length() >= 6 : "ci.length() = " + ci.length();
                    url = ci.toString(0, 6) + dom;
                    ci.trim(6);
                    peers = target.get(url);
                    if (peers == null) {
                        target.put(url, peerhash);
                    } else {
                        target.put(url, peers + peerhash);
                    }
                    //System.out.println("DEBUG-DECOMPRESS: " + url + ":" + target.get(url));
                }
                if (ci.byteAt(0) == ',') ci.trim(1);
            }
        }
    }
}
