// ReferenceContainer.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.07.2006 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.kelondro.rwi;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.TreeMap;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.ByteOrder;


/**
 * A ReferenceContainer is a set of ReferenceRows entries for a specific term.
 * Since ReferenceRow entries are special Row entries, a collection of ReferenceRows
 * can be contained in a RowSet.
 * This class extends the RowSet with methods for the handling of
 * special ReferenceRow Row entry objects.
 */
public class ReferenceContainer<ReferenceType extends Reference> extends RowSet {

    private   byte[] termHash;
    protected ReferenceFactory<ReferenceType> factory;
    
    public ReferenceContainer(final ReferenceFactory<ReferenceType> factory, final byte[] termHash, final RowSet collection) {
        super(collection);
        assert termHash == null || (termHash[2] != '@' && termHash.length == this.rowdef.primaryKeyLength);
        this.factory = factory;
        this.termHash = termHash;
    }
    
    public ReferenceContainer(final ReferenceFactory<ReferenceType> factory, final byte[] termHash) {
        super(factory.getRow());
        assert termHash == null || (termHash[2] != '@' && termHash.length == this.rowdef.primaryKeyLength);
        this.termHash = termHash;
        this.factory = factory;
        this.lastTimeWrote = 0;
    }
    
    public ReferenceContainer(final ReferenceFactory<ReferenceType> factory, final byte[] termHash, final int objectCount) throws RowSpaceExceededException {
        super(factory.getRow(), objectCount);
        assert termHash == null || (termHash[2] != '@' && termHash.length == this.rowdef.primaryKeyLength);
        this.termHash = termHash;
        this.factory = factory;
        this.lastTimeWrote = 0;
    }
    
    public ReferenceContainer<ReferenceType> topLevelClone() throws RowSpaceExceededException {
        final ReferenceContainer<ReferenceType> newContainer = new ReferenceContainer<ReferenceType>(this.factory, this.termHash, this.size());
        newContainer.addAllUnique(this);
        return newContainer;
    }
    
    public static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> emptyContainer(final ReferenceFactory<ReferenceType> factory, final byte[] termHash) {
        assert termHash == null || (termHash[2] != '@' && termHash.length == factory.getRow().primaryKeyLength);
        return new ReferenceContainer<ReferenceType>(factory, termHash);
    }

    public static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> emptyContainer(final ReferenceFactory<ReferenceType> factory, final byte[] termHash, final int elementCount) throws RowSpaceExceededException {
        assert termHash == null || (termHash[2] != '@' && termHash.length == factory.getRow().primaryKeyLength);
        return new ReferenceContainer<ReferenceType>(factory, termHash, elementCount);
    }

    public void setWordHash(final byte[] newTermHash) {
    	assert termHash == null || (termHash[2] != '@' && termHash.length == this.rowdef.primaryKeyLength);
        this.termHash = newTermHash;
    }

    public long updated() {
        return super.lastWrote();
    }

    public byte[] getTermHash() {
        return termHash;
    }
    
    public void add(final Reference entry) throws RowSpaceExceededException {
        // add without double-occurrence test
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize;
        this.addUnique(entry.toKelondroEntry());
    }
    
    public ReferenceContainer<ReferenceType> merge(final ReferenceContainer<ReferenceType> c) throws RowSpaceExceededException {
        return new ReferenceContainer<ReferenceType>(this.factory, this.termHash, super.merge(c));
    }
    
    public Reference replace(final Reference entry) throws RowSpaceExceededException {
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize;
        final Row.Entry r = super.replace(entry.toKelondroEntry());
        if (r == null) return null;
        return factory.produceSlow(r);
    }
    
    public void put(final Reference entry) throws RowSpaceExceededException {
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize;
        super.put(entry.toKelondroEntry());
    }
    
    public boolean putRecent(final Reference entry) throws RowSpaceExceededException {
        assert entry.toKelondroEntry().objectsize() == super.rowdef.objectsize;
        // returns true if the new entry was added, false if it already existed
        final Row.Entry oldEntryRow = this.replace(entry.toKelondroEntry());
        if (oldEntryRow == null) {
            return true;
        }
        final Reference oldEntry = factory.produceSlow(oldEntryRow);
        if (entry.isOlder(oldEntry)) { // A more recent Entry is already in this container
            this.replace(oldEntry.toKelondroEntry()); // put it back
            return false;
        }
        return true;
    }

    public int putAllRecent(final ReferenceContainer<ReferenceType> c) throws RowSpaceExceededException {
        // adds all entries in c and checks every entry for double-occurrence
        // returns the number of new elements
        if (c == null) return 0;
        int x = 0;
        synchronized (c) {
            final Iterator<ReferenceType> i = c.entries();
            while (i.hasNext()) {
                try {
                    if (putRecent(i.next())) x++;
                } catch (final ConcurrentModificationException e) {
                    Log.logException(e);
                }
            }
        }
        this.lastTimeWrote = java.lang.Math.max(this.lastTimeWrote, c.updated());
        return x;
    }
    
    public ReferenceType getReference(final byte[] urlHash) {
        final Row.Entry entry = super.get(urlHash);
        if (entry == null) return null;
        return this.factory.produceSlow(entry);
    }

    /**
     * remove a url reference from the container.
     * if the url hash was found, return the entry, but delete the entry from the container
     * if the entry was not found, return null.
     */
    public ReferenceType removeReference(final byte[] urlHash) {
        final Row.Entry entry = super.remove(urlHash);
        if (entry == null) return null;
        return this.factory.produceSlow(entry);
    }

    public int removeEntries(final HandleSet urlHashes) {
        int count = 0;
        final Iterator<byte[]> i = urlHashes.iterator();
        while (i.hasNext()) count += (delete(i.next())) ? 1 : 0;
        return count;
    }

    public Iterator<ReferenceType> entries() {
        // returns an iterator of indexRWIEntry objects
        return new entryIterator();
    }

    public class entryIterator implements Iterator<ReferenceType> {

        Iterator<Row.Entry> rowEntryIterator;
        
        public entryIterator() {
            rowEntryIterator = iterator();
        }
        
        public boolean hasNext() {
            return rowEntryIterator.hasNext();
        }

        public ReferenceType next() {
            final Row.Entry rentry = rowEntryIterator.next();
            if (rentry == null) return null;
            return factory.produceSlow(rentry);
        }

        public void remove() {
            rowEntryIterator.remove();
        }
        
    }
    
    public static Object mergeUnique(final Object a, final Object b) throws RowSpaceExceededException {
        final ReferenceContainer<?> c = (ReferenceContainer<?>) a;
        c.addAllUnique((ReferenceContainer<?>) b);
        return c;
    }
    
    public static final Method containerMergeMethod;
    static {
        Method meth = null;
        try {
            final Class<?> c = net.yacy.kelondro.rwi.ReferenceContainer.class;
            meth = c.getMethod("mergeUnique", new Class[]{Object.class, Object.class});
        } catch (final SecurityException e) {
            System.out.println("Error while initializing containerMerge.SecurityException: " + e.getMessage());
            meth = null;
        } catch (final NoSuchMethodException e) {
            System.out.println("Error while initializing containerMerge.NoSuchMethodException: " + e.getMessage());
            meth = null;
        }
        assert meth != null;
        containerMergeMethod = meth;
    }

    public static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> joinExcludeContainers(
            final ReferenceFactory<ReferenceType> factory,
            final Collection<ReferenceContainer<ReferenceType>> includeContainers,
            final Collection<ReferenceContainer<ReferenceType>> excludeContainers,
            final int maxDistance) throws RowSpaceExceededException {
        // join a search result and return the joincount (number of pages after join)

        // since this is a conjunction we return an empty entity if any word is not known
        if (includeContainers == null) return ReferenceContainer.emptyContainer(factory, null, 0);

        // join the result
        final ReferenceContainer<ReferenceType> rcLocal = ReferenceContainer.joinContainers(factory, includeContainers, maxDistance);
        if (rcLocal == null) return ReferenceContainer.emptyContainer(factory, null, 0);
        excludeContainers(factory, rcLocal, excludeContainers);
        
        return rcLocal;
    }
    
    public static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> joinContainers(
            final ReferenceFactory<ReferenceType> factory,
            final Collection<ReferenceContainer<ReferenceType>> containers,
            final int maxDistance) throws RowSpaceExceededException {
        
        // order entities by their size
        final TreeMap<Long, ReferenceContainer<ReferenceType>> map = new TreeMap<Long, ReferenceContainer<ReferenceType>>();
        ReferenceContainer<ReferenceType> singleContainer;
        final Iterator<ReferenceContainer<ReferenceType>> i = containers.iterator();
        int count = 0;
        while (i.hasNext()) {
            // get next entity:
            singleContainer = i.next();
            
            // check result
            if (singleContainer == null || singleContainer.isEmpty()) return null; // as this is a cunjunction of searches, we have no result if any word is not known
            
            // store result in order of result size
            map.put(Long.valueOf(singleContainer.size() * 1000 + count), singleContainer);
            count++;
        }
        
        // check if there is any result
        if (map.isEmpty()) return null; // no result, nothing found
        
        // the map now holds the search results in order of number of hits per word
        // we now must pairwise build up a conjunction of these sets
        Long k = map.firstKey(); // the smallest, which means, the one with the least entries
        ReferenceContainer<ReferenceType> searchA, searchB, searchResult = map.remove(k);
        while (!map.isEmpty() && !searchResult.isEmpty()) {
            // take the first element of map which is a result and combine it with result
            k = map.firstKey(); // the next smallest...
            searchA = searchResult;
            searchB = map.remove(k);
            searchResult = ReferenceContainer.joinConstructive(factory, searchA, searchB, maxDistance);
            // free resources
            searchA = null;
            searchB = null;
        }

        // in 'searchResult' is now the combined search result
        if (searchResult.isEmpty()) return null;
        return searchResult;
    }
    
    public static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> excludeContainers(
                            final ReferenceFactory<ReferenceType> factory,
                            ReferenceContainer<ReferenceType> pivot,
                            final Collection<ReferenceContainer<ReferenceType>> containers) {
        
        // check if there is any result
        if (containers == null || containers.isEmpty()) return pivot; // no result, nothing found
        
        final Iterator<ReferenceContainer<ReferenceType>> i = containers.iterator();
        while (i.hasNext()) {
        	pivot = excludeDestructive(factory, pivot, i.next());
        	if (pivot == null || pivot.isEmpty()) return null;
        }
        
        return pivot;
    }
    
    // join methods
    private static int log2(int x) {
        int l = 0;
        while (x > 0) {x = x >> 1; l++;}
        return l;
    }
    
    public static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> joinConstructive(
            final ReferenceFactory<ReferenceType> factory,
            final ReferenceContainer<ReferenceType> i1,
            final ReferenceContainer<ReferenceType> i2,
            final int maxDistance) throws RowSpaceExceededException {
        if ((i1 == null) || (i2 == null)) return null;
        if (i1.isEmpty() || i2.isEmpty()) return null;
        
        // decide which method to use
        final int high = ((i1.size() > i2.size()) ? i1.size() : i2.size());
        final int low  = ((i1.size() > i2.size()) ? i2.size() : i1.size());
        final int stepsEnum = 10 * (high + low - 1);
        final int stepsTest = 12 * log2(high) * low;
        
        // start most efficient method
        if (stepsEnum > stepsTest) {
            if (i1.size() < i2.size())
                return joinConstructiveByTest(factory, i1, i2, maxDistance);
            else
                return joinConstructiveByTest(factory, i2, i1, maxDistance);
        }
        return joinConstructiveByEnumeration(factory, i1, i2, maxDistance);
    }
    
    private static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> joinConstructiveByTest(
            final ReferenceFactory<ReferenceType> factory,
            final ReferenceContainer<ReferenceType> small,
            final ReferenceContainer<ReferenceType> large,
            final int maxDistance) throws RowSpaceExceededException {
        //System.out.println("DEBUG: JOIN METHOD BY TEST, maxdistance = " + maxDistance);
        assert small.rowdef.equals(large.rowdef) : "small = " + small.rowdef.toString() + "; large = " + large.rowdef.toString();
        final int keylength = small.rowdef.width(0);
        assert (keylength == large.rowdef.width(0));
        final ReferenceContainer<ReferenceType> conj = new ReferenceContainer<ReferenceType>(factory, null, 0); // start with empty search result
        final Iterator<ReferenceType> se = small.entries();
        ReferenceType ie1;
        ReferenceType ie2;
        while (se.hasNext()) {
            ie1 = se.next();
            ie2 = large.getReference(ie1.metadataHash());
            if ((ie1 != null) && (ie2 != null)) {
                assert (ie1.metadataHash().length == keylength) : "ie0.urlHash() = " + UTF8.String(ie1.metadataHash());
                assert (ie2.metadataHash().length == keylength) : "ie1.urlHash() = " + UTF8.String(ie2.metadataHash());
                // this is a hit. Calculate word distance:
                
                ie1 = factory.produceFast(ie2);
                ie1.join(ie2);
                if (ie1.distance() <= maxDistance) conj.add(ie1);
            }
        }
        return conj;
    }
    
    private static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> joinConstructiveByEnumeration(
            final ReferenceFactory<ReferenceType> factory,
            final ReferenceContainer<ReferenceType> i1,
            final ReferenceContainer<ReferenceType> i2,
            final int maxDistance) throws RowSpaceExceededException {
        //System.out.println("DEBUG: JOIN METHOD BY ENUMERATION, maxdistance = " + maxDistance);
        assert i1.rowdef.equals(i2.rowdef) : "i1 = " + i1.rowdef.toString() + "; i2 = " + i2.rowdef.toString();
        final int keylength = i1.rowdef.width(0);
        assert (keylength == i2.rowdef.width(0));
        final ReferenceContainer<ReferenceType> conj = new ReferenceContainer<ReferenceType>(factory, null, 0); // start with empty search result
        if (!((i1.rowdef.getOrdering().signature().equals(i2.rowdef.getOrdering().signature())))) return conj; // ordering must be equal
        ByteOrder ordering = i1.rowdef.getOrdering();
        final Iterator<ReferenceType> e1 = i1.entries();
        final Iterator<ReferenceType> e2 = i2.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            ReferenceType ie1;
            ReferenceType ie2;
            ie1 = e1.next();
            ie2 = e2.next();

            while (true) {
                assert (ie1.metadataHash().length == keylength) : "ie1.urlHash() = " + UTF8.String(ie1.metadataHash());
                assert (ie2.metadataHash().length == keylength) : "ie2.urlHash() = " + UTF8.String(ie2.metadataHash());
                c = ordering.compare(ie1.metadataHash(), ie2.metadataHash());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = e1.next(); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1 = factory.produceFast(ie1);
                    ie1.join(ie2);
                    if (ie1.distance() <= maxDistance) conj.add(ie1);
                    if (e1.hasNext()) ie1 = e1.next(); else break;
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                }
            }
        }
        return conj;
    }
    
    public static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> excludeDestructive(
            final ReferenceFactory<ReferenceType> factory,
            final ReferenceContainer<ReferenceType> pivot,
            final ReferenceContainer<ReferenceType> excl) {
        if (pivot == null) return null;
        if (excl == null) return pivot;
        if (pivot.isEmpty()) return null;
        if (excl.isEmpty()) return pivot;
        
        // decide which method to use
        final int high = ((pivot.size() > excl.size()) ? pivot.size() : excl.size());
        final int low  = ((pivot.size() > excl.size()) ? excl.size() : pivot.size());
        final int stepsEnum = 10 * (high + low - 1);
        final int stepsTest = 12 * log2(high) * low;
        
        // start most efficient method
        if (stepsEnum > stepsTest) {
            return excludeDestructiveByTest(pivot, excl);
        }
        return excludeDestructiveByEnumeration(factory, pivot, excl);
    }
    
    private static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> excludeDestructiveByTest(
            final ReferenceContainer<ReferenceType> pivot,
            final ReferenceContainer<ReferenceType> excl) {
        assert pivot.rowdef.equals(excl.rowdef) : "small = " + pivot.rowdef.toString() + "; large = " + excl.rowdef.toString();
        final int keylength = pivot.rowdef.width(0);
        assert (keylength == excl.rowdef.width(0));
        final boolean iterate_pivot = pivot.size() < excl.size();
        final Iterator<ReferenceType> se = (iterate_pivot) ? pivot.entries() : excl.entries();
        Reference ie0, ie1;
            while (se.hasNext()) {
                ie0 = se.next();
                ie1 = excl.getReference(ie0.metadataHash());
                if ((ie0 != null) && (ie1 != null)) {
                    assert (ie0.metadataHash().length == keylength) : "ie0.urlHash() = " + UTF8.String(ie0.metadataHash());
                    assert (ie1.metadataHash().length == keylength) : "ie1.urlHash() = " + UTF8.String(ie1.metadataHash());
                    if (iterate_pivot) se.remove(); pivot.delete(ie0.metadataHash());
                }
            }
        return pivot;
    }
    
    private static <ReferenceType extends Reference> ReferenceContainer<ReferenceType> excludeDestructiveByEnumeration(
                            final ReferenceFactory<ReferenceType> factory,
                            final ReferenceContainer<ReferenceType> pivot,
                            final ReferenceContainer<ReferenceType> excl) {
        assert pivot.rowdef.equals(excl.rowdef) : "i1 = " + pivot.rowdef.toString() + "; i2 = " + excl.rowdef.toString();
        final int keylength = pivot.rowdef.width(0);
        assert (keylength == excl.rowdef.width(0));
        if (!((pivot.rowdef.getOrdering().signature().equals(excl.rowdef.getOrdering().signature())))) return pivot; // ordering must be equal
        final Iterator<ReferenceType> e1 = pivot.entries();
        final Iterator<ReferenceType> e2 = excl.entries();
        int c;
        if ((e1.hasNext()) && (e2.hasNext())) {
            ReferenceType ie1;
            ReferenceType ie2;
            ie1 = e1.next();
            ie2 = e2.next();

            while (true) {
                assert (ie1.metadataHash().length == keylength) : "ie1.urlHash() = " + UTF8.String(ie1.metadataHash());
                assert (ie2.metadataHash().length == keylength) : "ie2.urlHash() = " + UTF8.String(ie2.metadataHash());
                c = pivot.rowdef.getOrdering().compare(ie1.metadataHash(), ie2.metadataHash());
                //System.out.println("** '" + ie1.getUrlHash() + "'.compareTo('" + ie2.getUrlHash() + "')="+c);
                if (c < 0) {
                    if (e1.hasNext()) ie1 = e1.next(); else break;
                } else if (c > 0) {
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                } else {
                    // we have found the same urls in different searches!
                    ie1 = factory.produceFast(ie1);
                    ie1.join(ie2);
                    e1.remove();
                    if (e1.hasNext()) ie1 = e1.next(); else break;
                    if (e2.hasNext()) ie2 = e2.next(); else break;
                }
            }
        }
        return pivot;
    }

    public synchronized String toString() {
        return "C[" + UTF8.String(termHash) + "] has " + this.size() + " entries";
    }
    
    public int hashCode() {
        return (int) Base64Order.enhancedCoder.decodeLong(this.termHash, 0, 4);
    }
    
}
