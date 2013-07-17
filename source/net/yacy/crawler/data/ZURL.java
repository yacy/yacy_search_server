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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.solr.common.SolrInputDocument;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.FailType;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.retrieval.Request;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.table.SplitTable;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.index.Fulltext;

public class ZURL implements Iterable<ZURL.Entry> {

    private static ConcurrentLog log = new ConcurrentLog("REJECTED");

    private static final int EcoFSBufferSize = 2000;
    private static final int maxStackSize    = 1000;

    public enum FailCategory {
        // TEMPORARY categories are such failure cases that should be tried again
        // FINAL categories are such failure cases that are final and should not be tried again
        TEMPORARY_NETWORK_FAILURE(true, FailType.fail), // an entity could not been loaded
        FINAL_PROCESS_CONTEXT(false, FailType.excl),    // because of a processing context we do not want that url again (i.e. remote crawling)
        FINAL_LOAD_CONTEXT(false, FailType.excl),       // the crawler configuration does not want to load the entity
        FINAL_ROBOTS_RULE(true, FailType.excl),         // a remote server denies indexing or loading
        FINAL_REDIRECT_RULE(true, FailType.excl);       // the remote server redirects this page, thus disallowing reading of content

        public final boolean store;
        public final FailType failType;

        private FailCategory(boolean store, FailType failType) {
            this.store = store;
            this.failType = failType;
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
    private final Fulltext fulltext;

    protected ZURL(
            final Fulltext fulltext,
    		final File cachePath,
    		final String tablename,
    		final boolean startWithEmptyFile,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.fulltext = fulltext;
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
                ConcurrentLog.logException(e1);
            }
        }
        //urlIndex = new kelondroFlexTable(cachePath, tablename, -1, rowdef, 0, true);
        this.stack = new LinkedBlockingQueue<byte[]>();
    }

    protected void clear() throws IOException {
        if (this.urlIndex != null) this.urlIndex.clear();
        if (this.stack != null) this.stack.clear();
    }

    protected void close() {
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
    
    public void removeHost(final Iterable<byte[]> hosthashes, final boolean concurrent) {
        if (hosthashes == null) return;
        Thread t = new Thread() {
            public void run() {
                try {
                    Iterator<byte[]> i = ZURL.this.urlIndex.keys(true, null);
                    List<byte[]> r = new ArrayList<byte[]>();
                    while (i.hasNext()) {
                        byte[] b = i.next();
                        for (byte[] hosthash: hosthashes) {
                            if (NaturalOrder.naturalOrder.equal(hosthash, 0, b, 6, 6)) r.add(b);
                        }
                    }
                    for (byte[] b: r) ZURL.this.urlIndex.remove(b);
                    i = ZURL.this.stack.iterator();
                    while (i.hasNext()) {
                        byte[] b = i.next();
                        for (byte[] hosthash: hosthashes) {
                            if (NaturalOrder.naturalOrder.equal(hosthash, 0, b, 6, 6)) i.remove();
                        }
                    }
                } catch (final IOException e) {}
            }
        };
        if (concurrent) t.start(); else t.run();
    }

    public void push(
            final Request bentry,
            final CrawlProfile profile,
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
        if (!reason.startsWith("double")) log.info(bentry.url().toNormalform(true) + " - " + reason);
        if (this.fulltext.getDefaultConnector() != null && failCategory.store) {
            // send the error to solr
            try {
                SolrInputDocument errorDoc = this.fulltext.getDefaultConfiguration().err(bentry.url(), profile == null ? null : profile.collections(), failCategory.name() + " " + reason, failCategory.failType, httpcode);
                this.fulltext.getDefaultConnector().add(errorDoc);
            } catch (final IOException e) {
                ConcurrentLog.warn("SOLR", "failed to send error " + bentry.url().toNormalform(true) + " to solr: " + e.getMessage());
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
            ConcurrentLog.logException(e);
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
            ConcurrentLog.logException(e);
        }
    }

    boolean exists(final byte[] urlHash) {
        return this.urlIndex.has(urlHash);
    }

    public void clearStack() {
        this.stack.clear();
    }

    public int stackSize() {
        return this.stack.size();
    }

    public class Entry {

        private Request bentry;    // the balancer entry
        private final byte[]   executor;  // the crawling executor
        private final Date     workdate;  // the time when the url was last time tried to load
        private final int      workcount; // number of tryings
        private final String   anycause;  // string describing reason for load fail
        private boolean  stored;

        private Entry(
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

        private Entry(final Row.Entry entry) throws IOException {
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

        private byte[] hash() {
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

}

