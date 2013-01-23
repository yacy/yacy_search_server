// DocumentIndex.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.09.2009 on http://yacy.net;
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

package net.yacy.search.index;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.solr.common.SolrInputDocument;

import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;

/**
 * convenience class to access the yacycore library from outside of yacy to put files into the index
 *
 * @author Michael Christen
 */
public class DocumentIndex extends Segment {

    private static DigestURI poison;
    static {
        try {
            poison = new DigestURI("file://.");
        } catch ( final MalformedURLException e ) {
        }
    }
    BlockingQueue<DigestURI> queue; // a queue of document ID's
    private final Worker[] worker;
    CallbackListener callback;

    static final ThreadGroup workerThreadGroup = new ThreadGroup("workerThreadGroup");

    public DocumentIndex(final File segmentPath, final File schemePath, final CallbackListener callback, final int cachesize)
        throws IOException {
        super(new Log("DocumentIndex"), segmentPath, schemePath == null ? null : new SolrConfiguration(schemePath, true));
        super.connectRWI(cachesize, targetFileSize * 4 - 1);
        super.connectCitation(cachesize, targetFileSize * 4 - 1);
        super.connectUrlDb(
                false, // useTailCache
                false  // exceed134217727
                );
        super.fulltext().connectLocalSolr(-1);
        final int cores = Runtime.getRuntime().availableProcessors() + 1;
        this.callback = callback;
        this.queue = new LinkedBlockingQueue<DigestURI>(cores * 300);
        this.worker = new Worker[cores];
        for ( int i = 0; i < cores; i++ ) {
            this.worker[i] = new Worker(i);
            this.worker[i].start();
        }
    }

    class Worker extends Thread
    {
        public Worker(final int count) {
            super(workerThreadGroup, "query-" + count);
        }

        @Override
        public void run() {
            DigestURI f;
            SolrInputDocument[] resultRows;
            try {
                while ( (f = DocumentIndex.this.queue.take()) != poison ) {
                    try {
                        resultRows = add(f);
                        for ( final SolrInputDocument resultRow : resultRows ) {
                            if ( DocumentIndex.this.callback != null ) {
                                if ( resultRow == null ) {
                                    DocumentIndex.this.callback.fail(f, "result is null");
                                } else {
                                    DocumentIndex.this.callback.commit(f, resultRow);
                                }
                            }
                        }
                    } catch ( final IOException e ) {
                        if ( e.getMessage().indexOf("cannot parse", 0) < 0 ) {
                            Log.logException(e);
                        }
                        DocumentIndex.this.callback.fail(f, e.getMessage());
                    }
                }
            } catch ( final InterruptedException e ) {
            }
        }
    }

    /**
     * get the number of pending documents in the indexing queue
     */
    public int pending() {
        return this.queue.size();
    }

    public void clearQueue() {
        this.queue.clear();
    }

    private SolrInputDocument[] add(final DigestURI url) throws IOException {
        if ( url == null ) {
            throw new IOException("file = null");
        }
        if ( url.isDirectory() ) {
            throw new IOException("file should be a document, not a path");
        }
        if ( !url.canRead() ) {
            throw new IOException("cannot read file");
        }
        Document[] documents;
        long length;
        try {
            length = url.length();
        } catch ( final Exception e ) {
            length = -1;
        }
        try {
            documents = TextParser.parseSource(url, null, null, length, url.getInputStream(null, -1));
        } catch ( final Exception e ) {
            throw new IOException("cannot parse " + url.toString() + ": " + e.getMessage());
        }
        //Document document = Document.mergeDocuments(url, null, documents);
        final SolrInputDocument[] rows = new SolrInputDocument[documents.length];
        int c = 0;
        for ( final Document document : documents ) {
        	if (document == null) continue;
            final Condenser condenser = new Condenser(document, true, true, LibraryProvider.dymLib, LibraryProvider.synonyms, true);
            rows[c++] =
                super.storeDocument(
                    url,
                    null,
                    null,
                    null,
                    document,
                    condenser,
                    null,
                    DocumentIndex.class.getName() + ".add",
                    false);
        }
        return rows;
    }

    /**
     * add a file or a directory of files to the index If the given file is a path to a directory, the
     * complete sub-tree is indexed
     *
     * @param start
     */
    public void addConcurrent(final DigestURI start) throws IOException {
        assert (start != null);
        assert (start.canRead()) : start.toString();
        if ( !start.isDirectory() ) {
            try {
                this.queue.put(start);
            } catch ( final InterruptedException e ) {
            }
            return;
        }
        final String[] s = start.list();
        DigestURI w;
        for ( final String t : s ) {
            try {
                w = new DigestURI(start, t);
                if ( w.canRead() && !w.isHidden() ) {
                    if ( w.isDirectory() ) {
                        addConcurrent(w);
                    } else {
                        try {
                            this.queue.put(w);
                        } catch ( final InterruptedException e ) {
                        }
                    }
                }
            } catch ( final MalformedURLException e1 ) {
                Log.logException(e1);
            }
        }
    }

    /**
     * close the index. This terminates all worker threads and then closes the segment.
     */
    @Override
    public synchronized void close() {
        // send termination signal to worker threads
        for ( @SuppressWarnings("unused")
        final Worker element : this.worker ) {
            try {
                this.queue.put(poison);
            } catch ( final InterruptedException e ) {
            }
        }
        // wait for termination
        for ( final Worker element : this.worker ) {
            try {
                element.join();
            } catch ( final InterruptedException e ) {
            }
        }
        // close the segment
        super.close();
    }

    public interface CallbackListener
    {
        public void commit(DigestURI f, SolrInputDocument resultRow);

        public void fail(DigestURI f, String failReason);
    }

}
