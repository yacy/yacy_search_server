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

package net.yacy.crawler.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.connector.ShardSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.table.SplitTable;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.index.SolrConfiguration;

public class ZURL implements Iterable<ZURL.Entry> {

    public static Log log = new Log("REJECTED");

    private static final int EcoFSBufferSize = 2000;
    private static final int maxStackSize    = 1000;

    public enum FailCategory {
        // TEMPORARY categories are such failure cases that should be tried again
        // FINAL categories are such failure cases that are final and should not be tried again
        TEMPORARY_NETWORK_FAILURE(true), // an entity could not been loaded
        FINAL_PROCESS_CONTEXT(false),    // because of a processing context we do not want that url again (i.e. remote crawling)
        FINAL_LOAD_CONTEXT(false),       // the crawler configuration does not want to load the entity
        FINAL_ROBOTS_RULE(true),         // a remote server denies indexing or loading
        FINAL_REDIRECT_RULE(true);       // the remote server redirects this page, thus disallowing reading of content

        public final boolean store;

        private FailCategory(boolean store) {
            this.store = store;
        }
    }

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
    private final Queue<byte[]> stack;
    private final SolrConnector solrConnector;
    private final SolrConfiguration solrConfiguration;

    public ZURL(
            final SolrConnector solrConnector,
            final SolrConfiguration solrConfiguration,
    		final File cachePath,
    		final String tablename,
    		final boolean startWithEmptyFile,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.solrConnector = solrConnector;
        this.solrConfiguration = solrConfiguration;
        // creates a new ZURL in a file
        cachePath.mkdirs();
        final File f = new File(cachePath, tablename);
        if (startWithEmptyFile) {
            if (f.exists()) {
                if (f.isDirectory()) SplitTable.delete(cachePath, tablename); else FileUtils.deletedelete(f);
            }
        }
        try {
            this.urlIndex = new Table(f, rowdef, EcoFSBufferSize, 0, useTailCache, exceed134217727, true);
        } catch (final SpaceExceededException e) {
            try {
                this.urlIndex = new Table(f, rowdef, 0, 0, false, exceed134217727, true);
            } catch (final SpaceExceededException e1) {
                Log.logException(e1);
            }
        }
        //urlIndex = new kelondroFlexTable(cachePath, tablename, -1, rowdef, 0, true);
        this.stack = new LinkedBlockingQueue<byte[]>();
    }

    public ZURL(final ShardSolrConnector solrConnector,
                    final SolrConfiguration solrConfiguration) {
        this.solrConnector = solrConnector;
        this.solrConfiguration = solrConfiguration;
        // creates a new ZUR in RAM
        this.urlIndex = new RowSet(rowdef);
        this.stack = new LinkedBlockingQueue<byte[]>();
    }

    public void clear() throws IOException {
        if (this.urlIndex != null) this.urlIndex.clear();
        if (this.stack != null) this.stack.clear();
    }

    public void close() {
        try {clear();} catch (final IOException e) {}
        if (this.urlIndex != null) this.urlIndex.close();
    }

    public boolean remove(final byte[] hash) {
        if (hash == null) return false;
        //System.out.println("*** DEBUG ZURL " + this.urlIndex.filename() + " remove " + hash);
        try {
            this.urlIndex.delete(hash);
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
            final FailCategory failCategory,
            String anycause,
            final int httpcode) {
        // assert executor != null; // null == proxy !
        assert failCategory.store || httpcode == -1 : "failCategory=" + failCategory.name();
        if (exists(bentry.url().hash())) return; // don't insert double causes
        if (anycause == null) anycause = "unknown";
        final String reason = anycause + ((httpcode >= 0) ? " (http return code = " + httpcode + ")" : "");
        final Entry entry = new Entry(bentry, executor, workdate, workcount, reason);
        put(entry);
        this.stack.add(entry.hash());
        if (!reason.startsWith("double")) log.logInfo(bentry.url().toNormalform(false, false) + " - " + reason);
        if (this.solrConnector != null && failCategory.store) {
            // send the error to solr
            try {
                SolrInputDocument errorDoc = this.solrConfiguration.err(bentry.url(), failCategory.name() + " " + reason, httpcode);
                this.solrConnector.add(errorDoc);
            } catch (final IOException e) {
                Log.logWarning("SOLR", "failed to send error " + bentry.url().toNormalform(true, false) + " to solr: " + e.getMessage());
            }
        }
        while (this.stack.size() > maxStackSize) this.stack.poll();
    }

    @Override
    public Iterator<ZURL.Entry> iterator() {
        return new EntryIterator();
    }

    public ArrayList<ZURL.Entry> list(int max) {
        final ArrayList<ZURL.Entry> l = new ArrayList<ZURL.Entry>();
        DigestURI url;
        for (final ZURL.Entry entry: this) {
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
            this.hi = ZURL.this.stack.iterator();
        }
        @Override
        public boolean hasNext() {
            return this.hi.hasNext();
        }

        @Override
        public ZURL.Entry next() {
            return get(this.hi.next());
        }

        @Override
        public void remove() {
            this.hi.remove();
        }

    }

    public ZURL.Entry get(final byte[] urlhash) {
        try {
            if (this.urlIndex == null) return null;
            // System.out.println("*** DEBUG ZURL " + this.urlIndex.filename() + " get " + urlhash);
            final Row.Entry entry = this.urlIndex.get(urlhash, false);
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
    private void put(final Entry entry) {
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
            if (this.urlIndex != null) this.urlIndex.put(newrow);
            entry.stored = true;
        } catch (final Exception e) {
            Log.logException(e);
        }
    }

    public boolean exists(final byte[] urlHash) {
        return this.urlIndex.has(urlHash);
    }

    public void clearStack() {
        this.stack.clear();
    }

    public int stackSize() {
        return this.stack.size();
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
            this.stored = false;
        }

        protected Entry(final Row.Entry entry) throws IOException {
            assert (entry != null);
            this.executor = entry.getColBytes(1, true);
            this.workdate = new Date(entry.getColLong(2));
            this.workcount = (int) entry.getColLong(3);
            this.anycause = entry.getColUTF8(4);
            this.bentry = new Request(Request.rowdef.newEntry(entry.getColBytes(5, false)));
            assert (Base64Order.enhancedCoder.equal(entry.getPrimaryKeyBytes(), this.bentry.url().hash()));
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
            return this.workdate;
        }

        public byte[] executor() {
            // return the creator's hash
            return this.executor;
        }

        public String anycause() {
            return this.anycause;
        }

    }

    private class kiter implements Iterator<Entry> {
        // enumerates entry elements
        private final Iterator<Row.Entry> i;
        private boolean error = false;

        private kiter(final boolean up, final String firstHash) throws IOException {
            this.i = ZURL.this.urlIndex.rows(up, (firstHash == null) ? null : ASCII.getBytes(firstHash));
            this.error = false;
        }

        @Override
        public boolean hasNext() {
            if (this.error) return false;
            return this.i.hasNext();
        }

        @Override
        public Entry next() throws RuntimeException {
            final Row.Entry e = this.i.next();
            if (e == null) return null;
            try {
                return new Entry(e);
            } catch (final IOException ex) {
                throw new RuntimeException("error '" + ex.getMessage() + "' for hash " + e.getPrimaryKeyASCII());
            }
        }

        @Override
        public void remove() {
            this.i.remove();
        }

    }

    public Iterator<Entry> entries(final boolean up, final String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }

}

