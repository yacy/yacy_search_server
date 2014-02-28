// plasmaWebStructure.java
// -----------------------------
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.05.2007 on http://yacy.net
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

package net.yacy.peers.graphics;

import java.io.File;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.MicroDate;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LookAheadIterator;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Document;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.rwi.ReferenceFactory;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;

public class WebStructureGraph {

    public static int maxref = 200; // maximum number of references, to avoid overflow when a large link farm occurs (i.e. wikipedia)
    public static int maxhosts = 10000; // maximum number of hosts in web structure map

    private final static ConcurrentLog log = new ConcurrentLog("WebStructureGraph");

    private final File structureFile;
    private final TreeMap<String, byte[]> structure_old; // <b64hash(6)>','<host> to <date-yyyymmdd(8)>{<target-b64hash(6)><target-count-hex(4)>}*
    private final TreeMap<String, byte[]> structure_new;
    private final BlockingQueue<LearnObject> publicRefDNSResolvingQueue;
    private final PublicRefDNSResolvingProcess publicRefDNSResolvingWorker;

    private final static LearnObject leanrefObjectPOISON = new LearnObject(null, null);

    private static class LearnObject {
        private final DigestURL url;
        private final Set<DigestURL> globalRefURLs;

        private LearnObject(final DigestURL url, final Set<DigestURL> globalRefURLs) {
            this.url = url;
            this.globalRefURLs = globalRefURLs;
        }
    }

    public WebStructureGraph(final File structureFile) {
        this.structure_old = new TreeMap<String, byte[]>();
        this.structure_new = new TreeMap<String, byte[]>();
        this.structureFile = structureFile;
        this.publicRefDNSResolvingQueue = new LinkedBlockingQueue<LearnObject>();

        // load web structure
        Map<String, byte[]> loadedStructureB;
        try {
            loadedStructureB =
                (this.structureFile.exists())
                    ? FileUtils.loadMapB(this.structureFile)
                    : new TreeMap<String, byte[]>();
        } catch (final OutOfMemoryError e ) {
            loadedStructureB = new TreeMap<String, byte[]>();
        }
        if ( loadedStructureB != null ) {
            this.structure_old.putAll(loadedStructureB);
        }
        log.info("loaded dump of " + loadedStructureB.size() + " entries from " + this.structureFile.toString());
        
        // delete out-dated entries in case the structure is too big
        if ( this.structure_old.size() > maxhosts ) {
            // fill a set with last-modified - dates of the structure
            final TreeSet<String> delset = new TreeSet<String>();
            String key;
            byte[] value;
            for ( final Map.Entry<String, byte[]> entry : this.structure_old.entrySet() ) {
                key = entry.getKey();
                value = entry.getValue();
                if ( value != null && value.length >= 8 ) {
                    delset.add(UTF8.String(value).substring(0, 8) + key);
                }
            }
            int delcount = this.structure_old.size() - (maxhosts * 9 / 10);
            final Iterator<String> j = delset.iterator();
            while ( (delcount > 0) && (j.hasNext()) ) {
                this.structure_old.remove(j.next().substring(8));
                delcount--;
            }
        }

        this.publicRefDNSResolvingWorker = new PublicRefDNSResolvingProcess();
        this.publicRefDNSResolvingWorker.start();
    }

    private class PublicRefDNSResolvingProcess extends Thread {
        private PublicRefDNSResolvingProcess() {
            this.setName("WebStructureGraph.PublicRefDNSResolvingProcess");
        }

        @Override
        public void run() {
            LearnObject lro;
            try {
                while ( (lro = WebStructureGraph.this.publicRefDNSResolvingQueue.take()) != leanrefObjectPOISON ) {
                    learnrefs(lro);
                }
            } catch (final InterruptedException e ) {
            }
        }
    }

    public void clear() {
        this.structure_old.clear();
        this.structure_new.clear();
    }
    
    public void generateCitationReference(final DigestURL url, final Document document) {
        // generate citation reference
        final Map<AnchorURL, String> hl = document.getHyperlinks();
        final Iterator<AnchorURL> it = hl.keySet().iterator();
        final HashSet<DigestURL> globalRefURLs = new HashSet<DigestURL>();
        final String refhost = url.getHost();
        DigestURL u;
        int maxref = 1000;
        while ( it.hasNext() && maxref-- > 0 ) {
            u = it.next();
            if ( u == null ) {
                continue;
            }
            if ( refhost != null && u.getHost() != null && !u.getHost().equals(refhost) ) {
                // this is a global link
                globalRefURLs.add(u);
            }
        }
        final LearnObject lro = new LearnObject(url, globalRefURLs);
        if (!globalRefURLs.isEmpty()) {
            try {
                if (this.publicRefDNSResolvingWorker.isAlive()) {
                    this.publicRefDNSResolvingQueue.put(lro);
                } else {
                    learnrefs(lro);
                }
            } catch (final InterruptedException e ) {
                learnrefs(lro);
            }
        }
    }
    
    public void generateCitationReference(final DigestURL from, final DigestURL to) {
        final HashSet<DigestURL> globalRefURLs = new HashSet<DigestURL>();
        final String refhost = from.getHost();
        if (refhost != null && to.getHost() != null && !to.getHost().equals(refhost)) globalRefURLs.add(to);
        final LearnObject lro = new LearnObject(from, globalRefURLs);
        if ( !globalRefURLs.isEmpty() ) {
            try {
                if (this.publicRefDNSResolvingWorker.isAlive()) {
                    this.publicRefDNSResolvingQueue.put(lro);
                } else {
                    learnrefs(lro);
                }
            } catch (final InterruptedException e ) {
                learnrefs(lro);
            }
        }
    }

    private static int refstr2count(final String refs) {
        if (refs == null || refs.length() <= 8) return 0;
        assert (refs.length() - 8) % 10 == 0 : "refs = " + refs + ", length = " + refs.length();
        return (refs.length() - 8) / 10;
    }

    private static Map<String, Integer> refstr2map(final String refs) {
        if (refs == null || refs.length() <= 8) return new HashMap<String, Integer>();
        final Map<String, Integer> map = new HashMap<String, Integer>();
        String c;
        final int refsc = refstr2count(refs);
        int d;
        for (int i = 0; i < refsc; i++) {
            c = refs.substring(8 + i * 10, 8 + (i + 1) * 10);
            try {
                d = Integer.valueOf(c.substring(6), 16);
            } catch (final NumberFormatException e ) {
                d = 1;
            }
            map.put(c.substring(0, 6), d);
        }
        return map;
    }

    private static String none2refstr() {
        return GenericFormatter.SHORT_DAY_FORMATTER.format();
    }
    
    private static String map2refstr(final Map<String, Integer> map) {
        final StringBuilder s = new StringBuilder(GenericFormatter.PATTERN_SHORT_DAY.length() + map.size() * 10);
        s.append(GenericFormatter.SHORT_DAY_FORMATTER.format());
        String h;
        for ( final Map.Entry<String, Integer> entry : map.entrySet() ) {
            s.append(entry.getKey());
            h = Integer.toHexString(entry.getValue().intValue());
            final int hl = h.length();
            if ( hl == 0 ) {
                s.append("0000");
            } else if ( hl == 1 ) {
                s.append("000").append(h);
            } else if ( hl == 2 ) {
                s.append("00").append(h);
            } else if ( hl == 3 ) {
                s.append('0').append(h);
            } else if ( hl == 4 ) {
                s.append(h);
            } else {
                s.append("FFFF");
            }
        }
        return s.toString();
    }

    public boolean exists(final String hosthash) {
        // returns a map with a hosthash(String):refcount(Integer) relation
        assert hosthash.length() == 6;
        SortedMap<String, byte[]> tailMap;
        synchronized ( this.structure_old ) {
            tailMap = this.structure_old.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    return true;
                }
            }
        }
        synchronized ( this.structure_new ) {
            tailMap = this.structure_new.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public StructureEntry outgoingReferences(final String hosthash) {
        // returns a map with a hosthash(String):refcount(Integer) relation
        assert hosthash.length() == 6;
        SortedMap<String, byte[]> tailMap;
        Map<String, Integer> h = new HashMap<String, Integer>();
        String hostname = "";
        String date = "";
        String ref;
        synchronized ( this.structure_old ) {
            tailMap = this.structure_old.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    hostname = key.substring(7);
                    ref = ASCII.String(tailMap.get(key));
                    date = ref.substring(0, 8);
                    h = refstr2map(ref);
                }
            }
        }
        synchronized ( this.structure_new ) {
            tailMap = this.structure_new.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    ref = ASCII.String(tailMap.get(key));
                    if ( hostname.isEmpty() ) {
                        hostname = key.substring(7);
                    }
                    if ( date.isEmpty() ) {
                        date = ref.substring(0, 8);
                    }
                    h.putAll(refstr2map(ref));
                }
            }
        }
        if (h.isEmpty()) return null;
        return new StructureEntry(hosthash, hostname, date, h);
    }
    
    public StructureEntry incomingReferences(final String hosthash) {
        final String hostname = hostHash2hostName(hosthash);
        if ( hostname == null ) {
            return null;
        }
        // collect the references
        WebStructureGraph.StructureEntry sentry;
        final HashMap<String, Integer> hosthashes = new HashMap<String, Integer>();
        Iterator<WebStructureGraph.StructureEntry> i = new StructureIterator(false);
        while ( i.hasNext() ) {
            sentry = i.next();
            if ( sentry.references.containsKey(hosthash) ) {
                hosthashes.put(sentry.hosthash, sentry.references.get(hosthash));
            }
        }
        i = new StructureIterator(true);
        while ( i.hasNext() ) {
            sentry = i.next();
            if ( sentry.references.containsKey(hosthash) ) {
                hosthashes.put(sentry.hosthash, sentry.references.get(hosthash));
            }
        }
        // construct a new structureEntry Object
        return new StructureEntry(
            hosthash,
            hostname,
            GenericFormatter.SHORT_DAY_FORMATTER.format(),
            hosthashes);
    }

    public static class HostReferenceFactory implements ReferenceFactory<HostReference>, Serializable {

        private static final long serialVersionUID=7461135579006223155L;

        private static final Row hostReferenceRow = new Row(
            "String h-6, Cardinal m-4 {b256}, Cardinal c-4 {b256}",
            Base64Order.enhancedCoder);

        public HostReferenceFactory() {
        }

        @Override
        public Row getRow() {
            return hostReferenceRow;
        }

        @Override
        public HostReference produceSlow(final Entry e) {
            return new HostReference(e);
        }

        @Override
        public HostReference produceFast(final HostReference e, final boolean local) {
            return e;
        }

    }

    public static class HostReference extends AbstractReference implements Reference, Serializable {

        private static final long serialVersionUID=-9170091435821206765L;

        private final Row.Entry entry;

        private HostReference(final byte[] hostHash, final long modified, final int count) {
            assert (hostHash.length == 6) : "hostHash = " + ASCII.String(hostHash);
            this.entry = hostReferenceFactory.getRow().newEntry();
            this.entry.setCol(0, hostHash);
            this.entry.setCol(1, MicroDate.microDateDays(modified));
            this.entry.setCol(2, count);
        }

        public HostReference(final String json) {
            this.entry = hostReferenceFactory.getRow().newEntry(json, true);
        }

        private HostReference(final Row.Entry entry) {
            this.entry = entry;
        }

        @Override
        public String toPropertyForm() {
            return this.entry.toPropertyForm(':', true, true, false, true);
        }

        @Override
        public Entry toKelondroEntry() {
            return this.entry;
        }

        @Override
        public byte[] urlhash() {
            return this.entry.getPrimaryKeyBytes();
        }

        private int count() {
            return (int) this.entry.getColLong(2);
        }

        @Override
        public long lastModified() {
            return MicroDate.reverseMicroDateDays((int) this.entry.getColLong(1));
        }

        @Override
        public void join(final Reference r) {
            // joins two entries into one entry
            final HostReference oe = (HostReference) r;

            // combine date
            final long o = oe.lastModified();
            if ( lastModified() < o ) {
                this.entry.setCol(1, MicroDate.microDateDays(o));
            }

            // combine count
            final int c = oe.count();
            if ( count() < c ) {
                this.entry.setCol(2, c);
            }
        }

        @Override
        public Collection<Integer> positions() {
            return new ArrayList<Integer>(0);
        }
    }

    public static final HostReferenceFactory hostReferenceFactory = new HostReferenceFactory();
    private static ReferenceContainerCache<HostReference> hostReferenceIndexCache = null;
    private static long hostReferenceIndexCacheTime = 0;
    private static final long hostReferenceIndexCacheTTL = 1000 * 60 * 60 * 12; // 12 hours time to live for cache

    public synchronized ReferenceContainerCache<HostReference> incomingReferences() {
        // we return a cache if the cache is filled and not stale
        if ( hostReferenceIndexCache != null
            && hostReferenceIndexCacheTime + hostReferenceIndexCacheTTL > System.currentTimeMillis() ) {
            return hostReferenceIndexCache;
        }

        // collect the references
        final ReferenceContainerCache<HostReference> idx =
            new ReferenceContainerCache<HostReference>(hostReferenceFactory, Base64Order.enhancedCoder, 6);

        // we iterate over all structure entries.
        // one structure entry has information that a specific host links to a list of other hosts
        incomingReferencesEnrich(idx, new StructureIterator(false), 3000);
        incomingReferencesEnrich(idx, new StructureIterator(true), 3000);

        // fill the cache again and set fill time
        hostReferenceIndexCache = idx;
        hostReferenceIndexCacheTime = System.currentTimeMillis();
        //incomingReferencesTest(hostReferenceIndexCache);
        return hostReferenceIndexCache;
    }

    private void incomingReferencesEnrich(
        final ReferenceContainerCache<HostReference> idx,
        final Iterator<WebStructureGraph.StructureEntry> structureIterator,
        final long time) {
        // we iterate over all structure entries.
        // one structure entry has information that a specific host links to a list of other hosts
        final long timeout = time == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + time;
        byte[] term;
        HostReference hr;
        WebStructureGraph.StructureEntry sentry;
        structureLoop: while ( structureIterator.hasNext() ) {
            sentry = structureIterator.next();
            // then we loop over all the hosts that are linked from sentry.hosthash
            refloop: for ( final Map.Entry<String, Integer> refhosthashandcounter : sentry.references
                .entrySet() ) {
                term = UTF8.getBytes(refhosthashandcounter.getKey());
                try {
                    hr =
                        new HostReference(
                            ASCII.getBytes(sentry.hosthash),
                            GenericFormatter.SHORT_DAY_FORMATTER.parse(sentry.date).getTime(),
                            refhosthashandcounter.getValue().intValue());
                } catch (final ParseException e ) {
                    continue refloop;
                }
                // each term refers to an index entry. look if we already have such an entry
                ReferenceContainer<HostReference> r = idx.get(term, null);
                try {
                    if ( r == null ) {
                        r = new ReferenceContainer<HostReference>(hostReferenceFactory, term);
                        r.add(hr);
                        idx.add(r);
                    } else {
                        r.put(hr);
                    }
                } catch (final SpaceExceededException e ) {
                    continue refloop;
                }
            }
            if ( System.currentTimeMillis() > timeout ) {
                break structureLoop;
            }
        }
    }

    public int referencesCount(final String hosthash) {
        // returns the number of hosts that are referenced by this hosthash
        assert hosthash.length() == 6 : "hosthash = " + hosthash;
        if (hosthash == null || hosthash.length() != 6) return 0;
        SortedMap<String, byte[]> tailMap;
        int c = 0;
        try {
        synchronized ( this.structure_old ) {
            tailMap = this.structure_old.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    c = refstr2count(UTF8.String(tailMap.get(key)));
                }
            }
        }
        synchronized ( this.structure_new ) {
            tailMap = this.structure_new.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    c += refstr2count(UTF8.String(tailMap.get(key)));
                }
            }
        }
        } catch (final Throwable t) {
            this.clear();
        }
        return c;
    }

    public String hostHash2hostName(final String hosthash) {
        // returns the host as string, null if unknown
        assert hosthash.length() == 6;
        SortedMap<String, byte[]> tailMap;
        synchronized ( this.structure_old ) {
            tailMap = this.structure_old.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    return key.substring(7);
                }
            }
        }
        synchronized ( this.structure_new ) {
            tailMap = this.structure_new.tailMap(hosthash);
            if ( !tailMap.isEmpty() ) {
                final String key = tailMap.firstKey();
                if ( key.startsWith(hosthash) ) {
                    return key.substring(7);
                }
            }
        }
        return null;
    }


    private void learnrefs(final LearnObject lro) {
        final Set<String> refhosts = new HashSet<String>();
        String hosthash;
        for ( final DigestURL u : lro.globalRefURLs ) {
            if (Switchboard.getSwitchboard().shallTerminate()) break;
            hosthash = ASCII.String(u.hash(), 6, 6);
            if (!exists(hosthash)) {
                // this must be recorded as an host with no references
                synchronized ( this.structure_new ) {
                    this.structure_new.put(hosthash + "," + u.getHost(), UTF8.getBytes(none2refstr()));
                }
            }
            refhosts.add(hosthash);
        }
        final DigestURL url = lro.url;
        hosthash = ASCII.String(url.hash(), 6, 6);

        // parse the new reference string and join it with the stored references
        final StructureEntry structure = outgoingReferences(hosthash);
        final Map<String, Integer> refs = (structure == null) ? new HashMap<String, Integer>() : structure.references;
        int c;
        for (String dom: refhosts) {
            c = 0;
            if ( refs.containsKey(dom) ) {
                c = (refs.get(dom)).intValue();
            }
            refs.put(dom, Integer.valueOf(++c));
        }

        // check if the maxref is exceeded
        if ( refs.size() > maxref ) {
            int shrink = refs.size() - (maxref * 9 / 10);
            delloop: while ( shrink > 0 ) {
                // shrink the references: the entry with the smallest number of references is removed
                int minrefcount = Integer.MAX_VALUE;
                String minrefkey = null;
                findloop: for ( final Map.Entry<String, Integer> entry : refs.entrySet() ) {
                    if ( entry.getValue().intValue() < minrefcount ) {
                        minrefcount = entry.getValue().intValue();
                        minrefkey = entry.getKey();
                    }
                    if ( minrefcount == 1 ) {
                        break findloop;
                    }
                }
                // remove the smallest
                if ( minrefkey == null ) {
                    break delloop;
                }
                refs.remove(minrefkey);
                shrink--;
            }
        }

        // store the map back to the structure
        synchronized ( this.structure_new ) {
            this.structure_new.put(hosthash + "," + url.getHost(), UTF8.getBytes(map2refstr(refs)));
        }
    }

    private static void joinStructure(final TreeMap<String, byte[]> into, final TreeMap<String, byte[]> from) {
        for ( final Map.Entry<String, byte[]> e : from.entrySet() ) {
            if ( into.containsKey(e.getKey()) ) {
                final Map<String, Integer> s0 = refstr2map(UTF8.String(into.get(e.getKey())));
                final Map<String, Integer> s1 = refstr2map(UTF8.String(e.getValue()));
                for ( final Map.Entry<String, Integer> r : s1.entrySet() ) {
                    if ( s0.containsKey(r.getKey()) ) {
                        s0.put(r.getKey(), s0.get(r.getKey()).intValue() + r.getValue().intValue());
                    } else {
                        s0.put(r.getKey(), r.getValue().intValue());
                    }
                }
                into.put(e.getKey(), UTF8.getBytes(map2refstr(s0)));
            } else {
                into.put(e.getKey(), e.getValue());
            }
        }
    }

    public void joinOldNew() {
        synchronized ( this.structure_new ) {
            joinStructure(this.structure_old, this.structure_new);
            this.structure_new.clear();
        }
    }

    public String hostWithMaxReferences() {
        // find host with most references
        String maxhost = null;
        int refsize, maxref = 0;
        synchronized ( this.structure_old ) {
            for ( final Map.Entry<String, byte[]> entry : this.structure_old.entrySet() ) {
                refsize = entry.getValue().length;
                if ( refsize > maxref ) {
                    maxref = refsize;
                    maxhost = entry.getKey().substring(7);
                }
            }
        }
        synchronized ( this.structure_new ) {
            for ( final Map.Entry<String, byte[]> entry : this.structure_new.entrySet() ) {
                refsize = entry.getValue().length;
                if ( refsize > maxref ) {
                    maxref = refsize;
                    maxhost = entry.getKey().substring(7);
                }
            }
        }
        return maxhost;
    }
    
    public ReversibleScoreMap<String> hostReferenceScore() {
        ReversibleScoreMap<String> result = new ClusteredScoreMap<String>(ASCII.identityASCIIComparator);
        synchronized ( this.structure_old ) {
            for ( final Map.Entry<String, byte[]> entry : this.structure_old.entrySet() ) {
                result.set(entry.getKey().substring(7), (entry.getValue().length - 8) / 10);
            }
        }
        synchronized ( this.structure_new ) {
            for ( final Map.Entry<String, byte[]> entry : this.structure_new.entrySet() ) {
                result.set(entry.getKey().substring(7), (entry.getValue().length - 8) / 10);
            }
        }
        return result;
    }
    
    public Iterator<StructureEntry> structureEntryIterator(final boolean latest) {
        return new StructureIterator(latest);
    }

    private class StructureIterator extends LookAheadIterator<StructureEntry> implements Iterator<StructureEntry> {

        private final Iterator<Map.Entry<String, byte[]>> i;

        private StructureIterator(final boolean latest) {
            this.i = ((latest) ? WebStructureGraph.this.structure_new : WebStructureGraph.this.structure_old).entrySet().iterator();
        }

        @Override
        public StructureEntry next0() {
            Map.Entry<String, byte[]> entry = null;
            String dom = null;
            byte[] ref = null;
            String refs;
            while ( this.i.hasNext() ) {
                entry = this.i.next();
                ref = entry.getValue();
                if ( (ref.length - 8) % 10 != 0 ) {
                    continue;
                }
                dom = entry.getKey();
                if ( dom.length() >= 8 ) {
                    break;
                }
                dom = null;
            }
            if ( entry == null || dom == null ) {
                return null;
            }
            assert (ref.length - 8) % 10 == 0 : "refs = " + ref + ", length = " + ref.length;
            refs = UTF8.String(ref);
            return new StructureEntry(
                dom.substring(0, 6),
                dom.substring(7),
                refs.substring(0, 8),
                refstr2map(refs));
        }
    }

    public static class StructureEntry implements Comparable<StructureEntry> {
        
        public String hosthash; // the tail of the host hash
        public String hostname; // the host name
        public String date; // date of latest change
        public Map<String, Integer> references; // a map from the referenced host hash to the number of referenced to that host

        private StructureEntry(final String hosthash, final String hostname) {
            this(hosthash, hostname, GenericFormatter.SHORT_DAY_FORMATTER.format(), new HashMap<String, Integer>());
        }
        
        private StructureEntry(
                final String hosthash,
                final String hostname,
                final String date,
                final Map<String, Integer> references) {
            this.hosthash = hosthash;
            this.hostname = hostname;
            this.date = date;
            this.references = references;
        }
        
        @Override
        public int compareTo(StructureEntry arg0) {
            return hosthash.compareTo(arg0.hosthash);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StructureEntry)) return false;
            return hosthash.equals(((StructureEntry)o).hosthash); 
        }

        @Override
        public int hashCode() {
            return this.hosthash.hashCode();
        }
    }

    public synchronized void close() {
        // finish dns resolving queue
        if ( this.publicRefDNSResolvingWorker.isAlive() ) {
            log.info("Waiting for the DNS Resolving Queue to terminate");
            try {
                this.publicRefDNSResolvingQueue.put(leanrefObjectPOISON);
                this.publicRefDNSResolvingWorker.join(5000);
            } catch (final InterruptedException e ) {
            }
        }

        // save to web structure file
        log.info("Saving Web Structure File: new = "
            + this.structure_new.size()
            + " entries, old = "
            + this.structure_old.size()
            + " entries");
        final long time = System.currentTimeMillis();
        joinOldNew();
        log.info("dumping " + structure_old.size() + " entries to " + structureFile.toString());
        if ( !this.structure_old.isEmpty() ) {
            synchronized ( this.structure_old ) {
                if ( !this.structure_old.isEmpty() ) {
                    FileUtils
                        .saveMapB(
                            this.structureFile,
                            this.structure_old,
                            "Web Structure Syntax: <b64hash(6)>','<host> to <date-yyyymmdd(8)>{<target-b64hash(6)><target-count-hex(4)>}*");
                    final long t = Math.max(1, System.currentTimeMillis() - time);
                    log.info("Saved Web Structure File: "
                        + this.structure_old.size()
                        + " entries in "
                        + t
                        + " milliseconds, "
                        + (this.structure_old.size() * 1000 / t)
                        + " entries/second");
                }
                this.structure_old.clear();
            }
        }
    }
}
