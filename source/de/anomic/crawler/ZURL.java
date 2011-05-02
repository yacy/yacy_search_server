// plasmaCrawlZURL.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.03.2007 on http://www.anomic.de
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

package de.anomic.crawler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.services.federated.solr.SolrSingleConnector;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.table.SplitTable;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.crawler.retrieval.Request;

public class ZURL implements Iterable<ZURL.Entry> {
    
    private static final int EcoFSBufferSize = 2000;
    private static final int maxStackSize    = 1000;
    
    private final static Row rowdef = new Row(
            "String urlhash-"   + Word.commonHashLength + ", " + // the url's hash
            "String executor-"  + Word.commonHashLength + ", " + // the crawling executor
            "Cardinal workdate-8 {b256}, " +                           // the time when the url was last time tried to load
            "Cardinal workcount-4 {b256}, " +                          // number of load retries
            "String anycause-132, " +                                   // string describing load failure
            "byte[] entry-" + Request.rowdef.objectsize,                                          // extra space
            Base64Order.enhancedCoder
    );

    // the class object
    private Index urlIndex;
    private final ConcurrentLinkedQueue<byte[]> stack;
    private final SolrSingleConnector solrConnector;
    
    public ZURL(
            final SolrSingleConnector solrConnector,
    		final File cachePath,
    		final String tablename,
    		final boolean startWithEmptyFile,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.solrConnector = solrConnector;
        // creates a new ZURL in a file
        cachePath.mkdirs();
        final File f = new File(cachePath, tablename);
        if (startWithEmptyFile) {
            if (f.exists()) {
                if (f.isDirectory()) SplitTable.delete(cachePath, tablename); else FileUtils.deletedelete(f);
            }
        }
        try {
            this.urlIndex = new Table(f, rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727);
        } catch (RowSpaceExceededException e) {
            try {
                this.urlIndex = new Table(f, rowdef, 0, 0, false, exceed134217727);
            } catch (RowSpaceExceededException e1) {
                Log.logException(e1);
            }
        }
        //urlIndex = new kelondroFlexTable(cachePath, tablename, -1, rowdef, 0, true);
        this.stack = new ConcurrentLinkedQueue<byte[]>();
    }
    
    public ZURL(final SolrSingleConnector solrConnector) {
        this.solrConnector = solrConnector;
        // creates a new ZUR in RAM
        this.urlIndex = new RowSet(rowdef);
        this.stack = new ConcurrentLinkedQueue<byte[]>();
    }
    
    public void clear() throws IOException {
        if (urlIndex != null) urlIndex.clear();
        if (stack != null) stack.clear();
    }

    public void close() {
        try {this.clear();} catch (IOException e) {}
        if (urlIndex != null) urlIndex.close();
    }

    public boolean remove(final byte[] hash) {
        if (hash == null) return false;
        //System.out.println("*** DEBUG ZURL " + this.urlIndex.filename() + " remove " + hash);
        try {
            urlIndex.delete(hash);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }
    
    public void push(
            final Request bentry,
            final byte[] executor,
            final Date workdate,
            final int workcount,
            String anycause,
            int httpcode) {
        // assert executor != null; // null == proxy !
        if (exists(bentry.url().hash())) return; // don't insert double causes
        if (anycause == null) anycause = "unknown";
        String reason = anycause + ((httpcode >= 0) ? " (http return code = " + httpcode + ")" : "");
        Entry entry = new Entry(bentry, executor, workdate, workcount, reason);
        put(entry);
        stack.add(entry.hash());
        Log.logInfo("Rejected URL", bentry.url().toNormalform(false, false) + " - " + reason);
        if (this.solrConnector != null) {
            // send the error to solr
            try {
                this.solrConnector.err(bentry.url(), reason, httpcode);
            } catch (IOException e) {
                Log.logWarning("SOLR", "failed to send error " + bentry.url().toNormalform(true, false) + " to solr: " + e.getMessage());
            }
        }
        while (stack.size() > maxStackSize) stack.poll();
    }
    
    public Iterator<ZURL.Entry> iterator() {
        return new EntryIterator();
    }
    
    public ArrayList<ZURL.Entry> list(int max) {
        ArrayList<ZURL.Entry> l = new ArrayList<ZURL.Entry>();
        DigestURI url;
        for (ZURL.Entry entry: this) {
            if (entry == null) continue;
            url = entry.url();
            if (url == null) continue;
            l.add(entry);
            if (max-- <= 0) l.remove(0);
        }
        return l;
    }
    
    private class EntryIterator implements Iterator<ZURL.Entry> {
        private final Iterator<byte[]> hi;
        public EntryIterator() {
            this.hi = stack.iterator();
        }
        public boolean hasNext() {
            return hi.hasNext();
        }

        public ZURL.Entry next() {
            return get(hi.next());
        }

        public void remove() {
            hi.remove();
        }
        
    }
   
    public ZURL.Entry get(final byte[] urlhash) {
        try {
            if (urlIndex == null) return null;
            // System.out.println("*** DEBUG ZURL " + this.urlIndex.filename() + " get " + urlhash);
            final Row.Entry entry = urlIndex.get(urlhash);
            if (entry == null) return null;
            return new Entry(entry);
        } catch (final IOException e) {
            Log.logException(e);
            return null;
        }
    }

    /**
     * private put (use push instead)
     * @param entry
     */
    private void put(Entry entry) {
        // stores the values from the object variables into the database
        if (entry.stored) return;
        if (entry.bentry == null) return;
        final Row.Entry newrow = rowdef.newEntry();
        newrow.setCol(0, entry.bentry.url().hash());
        newrow.setCol(1, entry.executor);
        newrow.setCol(2, entry.workdate.getTime());
        newrow.setCol(3, entry.workcount);
        newrow.setCol(4, UTF8.getBytes(entry.anycause));
        newrow.setCol(5, entry.bentry.toRow().bytes());
        try {
            if (urlIndex != null) urlIndex.put(newrow);
            entry.stored = true;
        } catch (final Exception e) {
            Log.logException(e);
        }
    }
    
    public boolean exists(final byte[] urlHash) {
        return urlIndex.has(urlHash);
    }
    
    public void clearStack() {
        stack.clear();
    }
    
    public int stackSize() {
        return stack.size();
    }
    
    public class Entry {

        Request bentry;    // the balancer entry
        private final byte[]   executor;  // the crawling executor
        private final Date     workdate;  // the time when the url was last time tried to load
        private final int      workcount; // number of tryings
        private final String   anycause;  // string describing reason for load fail
        private boolean  stored;

        protected Entry(
                final Request bentry,
                final byte[] executor,
                final Date workdate,
                final int workcount,
                final String anycause) {
            // create new entry
            assert bentry != null;
            // assert executor != null; // null == proxy !
            this.bentry = bentry;
            this.executor = executor;
            this.workdate = (workdate == null) ? new Date() : workdate;
            this.workcount = workcount;
            this.anycause = (anycause == null) ? "" : anycause;
            stored = false;
        }

        protected Entry(final Row.Entry entry) throws IOException {
            assert (entry != null);
            this.executor = entry.getColBytes(1, true);
            this.workdate = new Date(entry.getColLong(2));
            this.workcount = (int) entry.getColLong(3);
            this.anycause = entry.getColString(4);
            this.bentry = new Request(Request.rowdef.newEntry(entry.getColBytes(5, false)));
            assert (Base64Order.enhancedCoder.equal(entry.getPrimaryKeyBytes(), bentry.url().hash()));
            this.stored = true;
            return;
        }

        public DigestURI url() {
            return this.bentry.url();
        }
        
        public byte[] initiator() {
            return this.bentry.initiator();
        }
        
        public byte[] hash() {
            // return a url-hash, based on the md5 algorithm
            // the result is a String of 12 bytes within a 72-bit space
            // (each byte has an 6-bit range)
            // that should be enough for all web pages on the world
            return this.bentry.url().hash();
        }

        public Date workdate() {
            return workdate;
        }
        
        public byte[] executor() {
            // return the creator's hash
            return executor;
        }
        
        public String anycause() {
            return anycause;
        }

    }

    private class kiter implements Iterator<Entry> {
        // enumerates entry elements
        private Iterator<Row.Entry> i;
        private boolean error = false;
        
        private kiter(final boolean up, final String firstHash) throws IOException {
            i = urlIndex.rows(up, (firstHash == null) ? null : UTF8.getBytes(firstHash));
            error = false;
        }

        public boolean hasNext() {
            if (error) return false;
            return i.hasNext();
        }

        public Entry next() throws RuntimeException {
            final Row.Entry e = i.next();
            if (e == null) return null;
            try {
                return new Entry(e);
            } catch (final IOException ex) {
                throw new RuntimeException("error '" + ex.getMessage() + "' for hash " + e.getColString(0));
            }
        }
        
        public void remove() {
            i.remove();
        }
        
    }

    public Iterator<Entry> entries(final boolean up, final String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }

}

