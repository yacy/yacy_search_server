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


package de.anomic.search;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


import net.yacy.cora.document.UTF8;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.LibraryProvider;
import net.yacy.document.TextParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.meta.URIMetadataRow.Components;
import net.yacy.kelondro.logging.Log;


/**
 * convenience class to access the yacycore library from outside of yacy to put files into the index
 * @author Michael Christen
 *
 */
public class DocumentIndex extends Segment {
	
    private static final RankingProfile textRankingDefault = new RankingProfile(ContentDomain.TEXT);
    //private Bitfield zeroConstraint = new Bitfield(4);
    
    private static DigestURI poison;
    static {
        try {
            poison = new DigestURI("file://.");
        } catch (MalformedURLException e) {}
    }
    BlockingQueue<DigestURI> queue; // a queue of document ID's
    private Worker[] worker;
    CallbackListener callback;

    static final ThreadGroup workerThreadGroup = new ThreadGroup("workerThreadGroup");
    
    
    public DocumentIndex(final File segmentPath, CallbackListener callback, int cachesize) throws IOException {
        super(new Log("DocumentIndex"), segmentPath, cachesize, targetFileSize * 4 - 1, false, false);
        int cores = Runtime.getRuntime().availableProcessors() + 1;
        this.callback = callback;
        this.queue = new LinkedBlockingQueue<DigestURI>(cores * 300);
        this.worker = new Worker[cores];
        for (int i = 0; i < cores; i++) {
            this.worker[i] = new Worker(i);
            this.worker[i].start();
        }
    }
	
    class Worker extends Thread {
        public Worker(int count) {
            super(workerThreadGroup, "query-" + count);
        }
        
        @Override
        public void run() {
            DigestURI f;
            URIMetadataRow resultRow;
            try {
                while ((f = queue.take()) != poison) try {
                    resultRow = add(f);
                    if (callback != null) {
                        if (resultRow == null) {
                            callback.fail(f, "result is null");
                        } else {
                            callback.commit(f, resultRow);
                        }
                    }
                } catch (IOException e) {
                    if (e.getMessage().indexOf("cannot parse") < 0) Log.logException(e);
                    callback.fail(f, e.getMessage());
                }
            } catch (InterruptedException e) {}
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
    
    private URIMetadataRow add(DigestURI url) throws IOException {
        if (url == null) throw new IOException("file = null");
        if (url.isDirectory()) throw new IOException("file should be a document, not a path");
        if (!url.canRead()) throw new IOException("cannot read file");
        Document[] documents;
        long length;
        try {
            length = url.length();
        } catch (Exception e) {
            length = -1;
        }
        try {
            documents = TextParser.parseSource(url, null, null, length, url.getInputStream(null, -1));
        } catch (Exception e) {
            throw new IOException("cannot parse " + url.toString() + ": " + e.getMessage());
        }
        Document document = Document.mergeDocuments(url, null, documents);
        final Condenser condenser = new Condenser(document, true, true, LibraryProvider.dymLib);
        return super.storeDocument(
                url,
                null,
                new Date(url.lastModified()),
                new Date(),
                url.length(),
                document,
                condenser,
                null,
                DocumentIndex.class.getName() + ".add"
                );
    }
    
    /**
     * add a file or a directory of files to the index
     * If the given file is a path to a directory, the complete sub-tree is indexed
     * @param start
     */
    public void addConcurrent(DigestURI start) throws IOException {
        assert (start != null);
        assert (start.canRead()) : start.toString();
        if (!start.isDirectory()) {
            try {
                this.queue.put(start);
            } catch (InterruptedException e) {}
            return;
        }
        String[] s = start.list();
        DigestURI w;
        for (String t: s) {
            try {
                w = new DigestURI(start, t);
                if (w.canRead() && !w.isHidden()) {
                    if (w.isDirectory()) {
                        addConcurrent(w);
                    } else {
                        try {
                            this.queue.put(w);
                        } catch (InterruptedException e) {}
                    }
                }
            } catch (MalformedURLException e1) {
                Log.logException(e1);
            }
        }
    }
    
    /**
     * do a full-text search of a given string and return a specific number of results
     * @param querystring
     * @param count
     * @return a list of files that contain the given string
     */    
    public ArrayList<DigestURI> find(String querystring, int count) {
        // make a query and start a search
        QueryParams query = new QueryParams(querystring, count, null, this, textRankingDefault, "DocumentIndex");
        ReferenceOrder order = new ReferenceOrder(query.ranking, UTF8.getBytes(query.targetlang));
        RankingProcess rankedCache = new RankingProcess(query, order, SearchEvent.max_results_preparation);
        rankedCache.start();
        
        // search is running; retrieve results
        URIMetadataRow row;
        ArrayList<DigestURI> files = new ArrayList<DigestURI>();
        Components metadata;
        while ((row = rankedCache.takeURL(false, 1000)) != null) {
            metadata = row.metadata();
            if (metadata == null) continue;
            files.add(metadata.url());
            count--;
            if (count == 0) break;
        }
        return files;
    }
    
    /**
     * close the index.
     * This terminates all worker threads and then closes the segment.
     */
    @Override
    public void close() {
        // send termination signal to worker threads
        for (int i = 0; i < this.worker.length; i++) {
            try {
                this.queue.put(poison);
            } catch (InterruptedException e) {}
        }
        // wait for termination
        for (int i = 0; i < this.worker.length; i++) {
            try {
                this.worker[i].join();
            } catch (InterruptedException e) {}
        }
        // close the segment
        super.close();
    }
    
    public interface CallbackListener {
        public void commit(DigestURI f, URIMetadataRow resultRow);
        public void fail(DigestURI f, String failReason);
    }
    
    public static void main(String[] args) {
        // first argument: path to segment
        // second argument: either 'add' or 'search'
        // third and more arguments exists only in case that second argument is 'search': these are then the search words
        //
        // example:
        // DocumentIndex yacyindex add test/parsertest
        // DocumentIndex yacyindex search steht
        System.setProperty("java.awt.headless", "true");
        if (args.length < 3) return;
        File segmentPath = new File(args[0]);
        System.out.println("using index files at " + segmentPath.getAbsolutePath());
        CallbackListener callback = new CallbackListener() {
            public void commit(DigestURI f, URIMetadataRow resultRow) {
                System.out.println("indexed: " + f.toString());
            }
            public void fail(DigestURI f, String failReason) {
                System.out.println("not indexed " + f.toString() + ": " + failReason);
            }
        };
        try {
            if (args[1].equals("add")) {
                DigestURI f = new DigestURI(args[2]);
                DocumentIndex di = new DocumentIndex(segmentPath, callback, 100000);
                di.addConcurrent(f);
                di.close();
            } else {
                String query = "";
                for (int i = 2; i < args.length; i++) query += args[i];
                query.trim();
                DocumentIndex di = new DocumentIndex(segmentPath, callback, 100000);
                ArrayList<DigestURI> results = di.find(query, 100);
                for (DigestURI f: results) {
                    if (f != null) System.out.println(f.toString());
                }
                di.close();
            }
        } catch (IOException e) {
            Log.logException(e);
        }
        //System.exit(0);
    }
    
}
