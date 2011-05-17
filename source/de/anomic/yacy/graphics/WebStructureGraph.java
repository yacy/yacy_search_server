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

package de.anomic.yacy.graphics;

import java.io.File;
import java.io.IOException;
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
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.MicroDate;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerCache;
import net.yacy.kelondro.rwi.ReferenceFactory;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.LookAheadIterator;


public class WebStructureGraph {

    public static int maxref = 300; // maximum number of references, to avoid overflow when a large link farm occurs (i.e. wikipedia)
    public static int maxhosts = 20000; // maximum number of hosts in web structure map
    
    private final static Log log = new Log("WebStructureGraph");

    private final File   structureFile;
    private final TreeMap<String, String> structure_old; // <b64hash(6)>','<host> to <date-yyyymmdd(8)>{<target-b64hash(6)><target-count-hex(4)>}*
    private final TreeMap<String, String> structure_new;
    private final BlockingQueue<leanrefObject> publicRefDNSResolvingQueue;
    private final PublicRefDNSResolvingProcess publicRefDNSResolvingWorker;
    
    private final static leanrefObject leanrefObjectPOISON = new leanrefObject(null, null);
    
    private static class leanrefObject {
        private final DigestURI url;
        private final Set<MultiProtocolURI> globalRefURLs;
        private leanrefObject(final DigestURI url, final Set<MultiProtocolURI> globalRefURLs) {
            this.url = url;
            this.globalRefURLs = globalRefURLs;
        }
    }
    
    public WebStructureGraph(final File structureFile) {
        this.structure_old = new TreeMap<String, String>();
        this.structure_new = new TreeMap<String, String>();
        this.structureFile = structureFile;
        this.publicRefDNSResolvingQueue = new LinkedBlockingQueue<leanrefObject>();
        
        // load web structure
        Map<String, String> loadedStructure;
        try {
            loadedStructure = (this.structureFile.exists()) ? FileUtils.loadMap(this.structureFile) : new TreeMap<String, String>();
        } catch (OutOfMemoryError e) {
            loadedStructure = new TreeMap<String, String>();
        }
        if (loadedStructure != null) this.structure_old.putAll(loadedStructure);
        
        // delete out-dated entries in case the structure is too big
        if (this.structure_old.size() > maxhosts) {
        	// fill a set with last-modified - dates of the structure
        	final TreeSet<String> delset = new TreeSet<String>();
        	String key, value;
        	for (final Map.Entry<String, String> entry : this.structure_old.entrySet()) {
        		key = entry.getKey();
        		value = entry.getValue();
        		if (value.length() >= 8) delset.add(value.substring(0, 8) + key); 
        	}
        	int delcount = this.structure_old.size() - (maxhosts * 9 / 10);
        	final Iterator<String> j = delset.iterator();
        	while ((delcount > 0) && (j.hasNext())) {
        		this.structure_old.remove(j.next().substring(8));
        		delcount--;
        	}
        }
        this.publicRefDNSResolvingWorker = new PublicRefDNSResolvingProcess();
        this.publicRefDNSResolvingWorker.start();
    }
    
    private class PublicRefDNSResolvingProcess extends Thread {
        private PublicRefDNSResolvingProcess() {
        }
        public void run() {
            leanrefObject lro;
            try {
                while ((lro = publicRefDNSResolvingQueue.take()) != leanrefObjectPOISON) {
                    learnrefs(lro);
                }
            } catch (InterruptedException e) {
            }
        }
    }
    
    public void generateCitationReference(final DigestURI url, final Document document, final Condenser condenser) {
        // generate citation reference
        final Map<MultiProtocolURI, String> hl = document.getHyperlinks();
        final Iterator<MultiProtocolURI> it = hl.keySet().iterator();
        final HashSet<MultiProtocolURI> globalRefURLs = new HashSet<MultiProtocolURI>();
        final String refhost = url.getHost();
        MultiProtocolURI u;
        while (it.hasNext()) {
            u = it.next();
            if (u == null) continue;
            if (refhost != null && u.getHost() != null && !u.getHost().equals(refhost)) {
                // this is a global link
                globalRefURLs.add(u);
            }
        }
        leanrefObject lro = new leanrefObject(url, globalRefURLs);
        if (globalRefURLs.size() > 0) try {
            if (this.publicRefDNSResolvingWorker.isAlive()) {
                this.publicRefDNSResolvingQueue.put(lro);
            } else {
                this.learnrefs(lro);
            }
        } catch (InterruptedException e) {
            this.learnrefs(lro);
        }
    }
    
    private void learnrefs(final leanrefObject lro) {
        final StringBuilder cpg = new StringBuilder(240);
        assert cpg.length() % 12 == 0 : "cpg.length() = " + cpg.length() + ", cpg = " + cpg.toString();
        final String refhashp = UTF8.String(lro.url.hash(), 6, 6); // ref hash part
        String nexturlhash;
        for (MultiProtocolURI u: lro.globalRefURLs) {
            byte[] nexturlhashb = new DigestURI(u).hash();
            assert nexturlhashb != null;
            if (nexturlhashb != null) {
                nexturlhash = UTF8.String(nexturlhashb);
                assert nexturlhash.length() == 12 : "nexturlhash.length() = " + nexturlhash.length() + ", nexturlhash = " + nexturlhash;
                assert !nexturlhash.substring(6).equals(refhashp);
                // this is a global link
                cpg.append(nexturlhash); // store complete hash
                assert cpg.length() % 12 == 0 : "cpg.length() = " + cpg.length() + ", cpg = " + cpg.toString();
            }
        }
        assert cpg.length() % 12 == 0 : "cpg.length() = " + cpg.length() + ", cpg = " + cpg.toString();
        learn(lro.url, cpg);
    }
    
    private static int refstr2count(final String refs) {
        if ((refs == null) || (refs.length() <= 8)) return 0;
        assert (refs.length() - 8) % 10 == 0 : "refs = " + refs + ", length = " + refs.length();
        return (refs.length() - 8) / 10;
    }
    
    static Map<String, Integer> refstr2map(final String refs) {
        if ((refs == null) || (refs.length() <= 8)) return new HashMap<String, Integer>();
        final Map<String, Integer> map = new HashMap<String, Integer>();
        String c;
        final int refsc = refstr2count(refs);
        int d;
        for (int i = 0; i < refsc; i++) {
            c = refs.substring(8 + i * 10, 8 + (i + 1) * 10);
            try {
                d = Integer.valueOf(c.substring(6), 16);
            } catch (NumberFormatException e) {
                d = 1;
            }
            map.put(c.substring(0, 6), d);
        }
        return map;
    }
    
    private static String map2refstr(final Map<String, Integer> map) {
        final StringBuilder s = new StringBuilder(map.size() * 10);
        s.append(GenericFormatter.SHORT_DAY_FORMATTER.format());
        String h;
        for (final Map.Entry<String, Integer> entry : map.entrySet()) {
            s.append(entry.getKey());
            h = Integer.toHexString(entry.getValue().intValue());
            if (h.length() == 0) {
                s.append("0000");
            } else if (h.length() == 1) {
                s.append("000").append(h);
            } else if (h.length() == 2) {
                s.append("00").append(h);
            } else if (h.length() == 3) {
                s.append('0').append(h);
            } else if (h.length() == 4) {
                s.append(h);
            } else {
                s.append("FFFF");
            }
        }
        return s.toString();
    }
    
    public StructureEntry outgoingReferences(final String hosthash) {
        // returns a map with a hosthash(String):refcount(Integer) relation
        assert hosthash.length() == 6;
        SortedMap<String, String> tailMap;
        Map<String, Integer> h = new HashMap<String, Integer>();
        String hostname = "";
        String date = "";
        String ref;
        synchronized (structure_old) {
            tailMap = structure_old.tailMap(hosthash);
            if (!tailMap.isEmpty()) {
                final String key = tailMap.firstKey();
                if (key.startsWith(hosthash)) {
                    hostname = key.substring(7);
                    ref = tailMap.get(key);
                    date = ref.substring(0, 8);
                    h = refstr2map(ref);
                }
            }
        }
        synchronized (structure_new) {
            tailMap = structure_new.tailMap(hosthash);
            if (!tailMap.isEmpty()) {
                final String key = tailMap.firstKey();
                if (key.startsWith(hosthash)) {
                    ref = tailMap.get(key);
                    if (hostname.length() == 0) hostname = key.substring(7);
                    if (date.length() == 0) date = ref.substring(0, 8);
                    h.putAll(refstr2map(ref));
                }
            }
        }
        if (h.isEmpty()) return null;
        return new StructureEntry(hosthash, hostname, date, h);
    }
    
    public StructureEntry incomingReferences(final String hosthash) {
        String hostname = hostHash2hostName(hosthash);
        if (hostname == null) return null;
        // collect the references
        WebStructureGraph.StructureEntry sentry;
        HashMap<String, Integer> hosthashes = new HashMap<String, Integer>();
        Iterator<WebStructureGraph.StructureEntry> i = new StructureIterator(false);
        while (i.hasNext()) {
            sentry = i.next();
            if (sentry.references.containsKey(hosthash)) hosthashes.put(sentry.hosthash, sentry.references.get(hosthash));
        }
        i = new StructureIterator(true);
        while (i.hasNext()) {
            sentry = i.next();
            if (sentry.references.containsKey(hosthash)) hosthashes.put(sentry.hosthash, sentry.references.get(hosthash));
        }
        // construct a new structureEntry Object
        return new StructureEntry(
                hosthash,
                hostname,
                GenericFormatter.SHORT_DAY_FORMATTER.format(),
                hosthashes);
    }
    
    public static class HostReferenceFactory implements ReferenceFactory<HostReference> {

        private static final Row hostReferenceRow = new Row("String h-6, Cardinal m-4 {b256}, Cardinal c-4 {b256}", Base64Order.enhancedCoder);
        
        public HostReferenceFactory() {
        }
        
        public Row getRow() {
            return hostReferenceRow;
        }

        public HostReference produceSlow(Entry e) {
            return new HostReference(e);
        }

        public HostReference produceFast(HostReference e) {
            return e;
        }
        
    }
    
    public static class HostReference extends AbstractReference implements Reference {

        private final Row.Entry entry;
        
        public HostReference(final byte[] hostHash, final long modified, final int count) {
            assert (hostHash.length == 6) : "hostHash = " + UTF8.String(hostHash);
            this.entry = hostReferenceFacory.getRow().newEntry();
            this.entry.setCol(0, hostHash);
            this.entry.setCol(1, MicroDate.microDateDays(modified));
            this.entry.setCol(2, count);
        }
        
        public HostReference(Row.Entry entry) {
            this.entry = entry;
        }
        
        public String toPropertyForm() {
            return this.entry.toPropertyForm(':', true, true, false, true);
        }

        public Entry toKelondroEntry() {
            return this.entry;
        }

        public byte[] metadataHash() {
            return this.entry.getPrimaryKeyBytes();
        }

        public int count() {
            return (int) this.entry.getColLong(2);
        }
        
        public long lastModified() {
            return MicroDate.reverseMicroDateDays((int) this.entry.getColLong(1));
        }

        public void join(final Reference r) {
            // joins two entries into one entry
            HostReference oe = (HostReference) r; 
            
            // combine date
            long o = oe.lastModified();
            if (this.lastModified() < o) this.entry.setCol(1, MicroDate.microDateDays(o));
            
            // combine count
            int c = oe.count();
            if (this.count() < c) this.entry.setCol(2, c);
        }

        public Collection<Integer> positions() {
            return new ArrayList<Integer>(0);
        }
    }
    
    public static final HostReferenceFactory hostReferenceFacory = new HostReferenceFactory();
    public static       ReferenceContainerCache<HostReference> hostReferenceIndexCache = null;
    public static       long hostReferenceIndexCacheTime = 0;
    public static final long hostReferenceIndexCacheTTL = 1000 * 60 * 60 * 12; // 12 hours time to live for cache
    
    public synchronized ReferenceContainerCache<HostReference> incomingReferences() {
        // we return a cache if the cache is filled and not stale
        if (hostReferenceIndexCache != null &&
            hostReferenceIndexCacheTime + hostReferenceIndexCacheTTL > System.currentTimeMillis()) return hostReferenceIndexCache; 
        
        // collect the references
        HostReferenceFactory hostReferenceFactory = new HostReferenceFactory();
        ReferenceContainerCache<HostReference> idx = new ReferenceContainerCache<HostReference>(hostReferenceFactory, hostReferenceFactory.getRow(), Base64Order.enhancedCoder);
        
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
            ReferenceContainerCache<HostReference> idx,
            Iterator<WebStructureGraph.StructureEntry> structureIterator,
            long time) {
        // we iterate over all structure entries.
        // one structure entry has information that a specific host links to a list of other hosts
        long timeout = System.currentTimeMillis() + time;
        byte[] term;
        HostReference hr;
        WebStructureGraph.StructureEntry sentry;
        structureLoop: while (structureIterator.hasNext()) {
            sentry = structureIterator.next();
            // then we loop over all the hosts that are linked from sentry.hosthash
            refloop: for (Map.Entry<String, Integer> refhosthashandcounter: sentry.references.entrySet()) {
                term = UTF8.getBytes(refhosthashandcounter.getKey());
                try {
                    hr = new HostReference(UTF8.getBytes(sentry.hosthash), GenericFormatter.SHORT_DAY_FORMATTER.parse(sentry.date).getTime(), refhosthashandcounter.getValue().intValue());
                } catch (ParseException e) {
                    continue refloop;
                }
                // each term refers to an index entry. look if we already have such an entry
                ReferenceContainer<HostReference> r = idx.get(term, null);
                try {
                    if (r == null) {
                        r = new ReferenceContainer<HostReference>(hostReferenceFacory, term);
                        r.add(hr);
                        idx.add(r);
                    } else {
                        r.put(hr);
                    }
                } catch (RowSpaceExceededException e) {
                    continue refloop;
                }
            }
            if (System.currentTimeMillis() > timeout) break structureLoop;
        }
    }
    
    /*
    private void incomingReferencesTest(ReferenceContainerCache<HostReference> idx) {
        for (ReferenceContainer<HostReference> references: idx) {
            log.logInfo("Term-Host: " + hostHash2hostName(UTF8.String(references.getTermHash())));
            Iterator<HostReference> referenceIterator = references.entries();
            StringBuilder s = new StringBuilder();
            HostReference reference;
            while (referenceIterator.hasNext()) {
                reference = referenceIterator.next();
                s.append(reference.toPropertyForm());
                log.logInfo("   ... referenced by " + hostHash2hostName(UTF8.String(reference.metadataHash())) + ", " + reference.count() + " references");
            }
        }
    }
    */
    
    public int referencesCount(final String hosthash) {
        // returns the number of hosts that are referenced by this hosthash
        assert hosthash.length() == 6 : "hosthash = " + hosthash;
        if (hosthash == null || hosthash.length() != 6) return 0;
        SortedMap<String, String> tailMap;
        int c = 0;
        synchronized (structure_old) {
            tailMap = structure_old.tailMap(hosthash);
            if (!tailMap.isEmpty()) {
                final String key = tailMap.firstKey();
                if (key.startsWith(hosthash)) {
                    c = refstr2count(tailMap.get(key));
                }
            }
        }
        synchronized (structure_new) {
            tailMap = structure_new.tailMap(hosthash);
            if (!tailMap.isEmpty()) {
                final String key = tailMap.firstKey();
                if (key.startsWith(hosthash)) {
                    c += refstr2count(tailMap.get(key));
                }
            }
        }
        return c;
    }
    
    public String hostHash2hostName(final String hosthash) {
        // returns the host as string, null if unknown
        assert hosthash.length() == 6;
        SortedMap<String, String> tailMap;
        synchronized(structure_old) {
            tailMap = structure_old.tailMap(hosthash);
            if (!tailMap.isEmpty()) {
                final String key = tailMap.firstKey();
                if (key.startsWith(hosthash)) {
                    return key.substring(7);
                }
            }
        }
        synchronized(structure_new) {
            tailMap = structure_new.tailMap(hosthash);
            if (!tailMap.isEmpty()) {
                final String key = tailMap.firstKey();
                if (key.startsWith(hosthash)) {
                    return key.substring(7);
                }
            }
        }
        return null;
    }
    
    private void learn(final DigestURI url, final StringBuilder reference /*string of b64(12digits)-hashes*/) {
        final String hosthash = UTF8.String(url.hash(), 6, 6);

        // parse the new reference string and join it with the stored references
        StructureEntry structure = outgoingReferences(hosthash);
        final Map<String, Integer> refs = (structure == null) ? new HashMap<String, Integer>() : structure.references;
        assert reference.length() % 12 == 0 : "reference.length() = " + reference.length() + ", reference = " + reference.toString();
        String dom;
        int c;
        for (int i = 0; i < reference.length() / 12; i++) {
            dom = reference.substring(i * 12 + 6, (i + 1) * 12);
            c = 0;
            if (refs.containsKey(dom)) {
                c = (refs.get(dom)).intValue();
            }
            refs.put(dom, Integer.valueOf(++c));
        }

        // check if the maxref is exceeded
        if (refs.size() > maxref) {
            int shrink = refs.size() - (maxref * 9 / 10);
            delloop: while (shrink > 0) {
                // shrink the references: the entry with the smallest number of references is removed
                int minrefcount = Integer.MAX_VALUE;
                String minrefkey = null;
                findloop: for (final Map.Entry<String, Integer> entry : refs.entrySet()) {
                    if (entry.getValue().intValue() < minrefcount) {
                        minrefcount = entry.getValue().intValue();
                        minrefkey = entry.getKey();
                    }
                    if (minrefcount == 1) break findloop;
                }
                // remove the smallest
                if (minrefkey == null) break delloop;
                refs.remove(minrefkey);
                shrink--;
            }
        }

        // store the map back to the structure
        synchronized(structure_new) {
            structure_new.put(hosthash + "," + url.getHost(), map2refstr(refs));
        }
    }
    
    private static void joinStructure(final TreeMap<String, String> into, final TreeMap<String, String> from) {
        for (final Map.Entry<String, String> e: from.entrySet()) {
            if (into.containsKey(e.getKey())) {
                final Map<String, Integer> s0 = refstr2map(into.get(e.getKey()));
                final Map<String, Integer> s1 = refstr2map(e.getValue());
                for (final Map.Entry<String, Integer> r: s1.entrySet()) {
                    if (s0.containsKey(r.getKey())) {
                        s0.put(r.getKey(), s0.get(r.getKey()).intValue() + r.getValue().intValue());
                    } else {
                        s0.put(r.getKey(), r.getValue().intValue());
                    }
                }
                into.put(e.getKey(), map2refstr(s0));
            } else {
                into.put(e.getKey(), e.getValue());
            }
        }
    }
    
    public void joinOldNew() {
        synchronized(structure_new) {
            joinStructure(this.structure_old, this.structure_new);
            this.structure_new.clear();
        }
    }
    
    private void saveWebStructure() {
        joinOldNew();
        try {
            synchronized(structure_old) {
                FileUtils.saveMap(this.structureFile, this.structure_old, "Web Structure Syntax: <b64hash(6)>','<host> to <date-yyyymmdd(8)>{<target-b64hash(6)><target-count-hex(4)>}*");
            }
        } catch (final IOException e) {
            Log.logException(e);
        }
    }
    
    public String hostWithMaxReferences() {
        // find host with most references
        String maxhost = null;
        int refsize, maxref = 0;
        joinOldNew();
        synchronized(structure_new) {
            for (final Map.Entry<String, String> entry : structure_old.entrySet()) {
                refsize = entry.getValue().length();
                if (refsize > maxref) {
                    maxref = refsize;
                    maxhost = entry.getKey().substring(7);
                }
            }
        }
        return maxhost;
    }
    
    public Iterator<StructureEntry> structureEntryIterator(final boolean latest) {
        return new StructureIterator(latest);
    }
    
    private class StructureIterator extends LookAheadIterator<StructureEntry> implements Iterator<StructureEntry> {

        private final Iterator<Map.Entry<String, String>> i;
        
        private StructureIterator(final boolean latest) {
            i = ((latest) ? structure_new : structure_old).entrySet().iterator();
        }
        
        public StructureEntry next0() {
            Map.Entry<String, String> entry = null;
            String dom = null, ref = "";
            while (i.hasNext()) {
                entry = i.next();
                ref = entry.getValue();
                if ((ref.length() - 8) % 10 != 0) continue;
                dom = entry.getKey();
                if (dom.length() >= 8) break;
                dom = null;
            }
            if (entry == null || dom == null) return null;
            assert (ref.length() - 8) % 10 == 0 : "refs = " + ref + ", length = " + ref.length();
            return new StructureEntry(dom.substring(0, 6), dom.substring(7), ref.substring(0, 8), refstr2map(ref));
        }
    }
    
    public static class StructureEntry {
        public String hosthash; // the tail of the host hash
        public String hostname; // the host name
        public String date;     // date of latest change
        public Map<String, Integer> references; // a map from the referenced host hash to the number of referenced to that host
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
    }
    
    public void close() {
        if (this.publicRefDNSResolvingWorker.isAlive()) {
            log.logInfo("Waiting for the DNS Resolving Queue to terminate");
            try {
                this.publicRefDNSResolvingQueue.put(leanrefObjectPOISON);
                this.publicRefDNSResolvingWorker.join(5000);
            } catch (InterruptedException e) {
            }
        }
        log.logInfo("Saving Web Structure File");
        saveWebStructure();
    }
}
