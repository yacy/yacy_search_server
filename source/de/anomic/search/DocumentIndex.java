// DocumentIndex.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 14.09.2009 on http://yacy.net;
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-05-28 01:51:34 +0200 (Do, 28 Mai 2009) $
// $LastChangedRevision: 5988 $
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


package de.anomic.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.logging.Log;

import de.anomic.document.Condenser;
import de.anomic.document.Document;
import de.anomic.document.Parser;
import de.anomic.document.ParserException;

/**
 * convenience class to access the yacycore library from outside of yacy to put files into the index
 * @author Michael Christen
 *
 */
public class DocumentIndex extends Segment {
	
    private static final RankingProfile textRankingDefault = new RankingProfile(QueryParams.CONTENTDOM_TEXT);
    //private Bitfield zeroConstraint = new Bitfield(4);
    
    private final static File poison = new File(".");
    private BlockingQueue<File> queue;
    private Worker[] worker;
    private CallbackListener callback;
    
    public DocumentIndex(Log log, final File segmentPath, CallbackListener callback, int cachesize) throws IOException {
        super(log, segmentPath, cachesize, targetFileSize * 4 - 1, false, false);
        int cores = Runtime.getRuntime().availableProcessors() + 1;
        this.callback = callback;
        this.queue = new LinkedBlockingQueue<File>(cores * 300);
        this.worker = new Worker[cores];
        for (int i = 0; i < cores; i++) {
            this.worker[i] = new Worker();
            this.worker[i].start();
        }
    }
    
    public DocumentIndex(final File segmentPath, CallbackListener callback, int cachesize) throws IOException {
        this(new Log("DocumentIndex"), segmentPath, callback, cachesize);
    }
	
    class Worker extends Thread {
        public void run() {
            File f;
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
                    if (e.getMessage().indexOf("cannot parse") < 0) e.printStackTrace();
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
    
    /**
     * put a single file into the index
     * @param file
     * @return a metadata object that has been generated to identify the file
     * @throws IOException in case that the file does not exist or cannot be parsed
     */
    public URIMetadataRow add(File file) throws IOException {
        if (file == null) throw new IOException("file = null");
        if (file.isDirectory()) throw new IOException("file should be a document, not a path");
        if (!file.canRead()) throw new IOException("cannot read file");
    	DigestURI url = new DigestURI("file:" + file.getAbsolutePath());
    	Document document;
        try {
            document = Parser.parseSource(url, null, null, file);
        } catch (InterruptedException e) {
            throw new IOException("cannot parse " + file.toString() + ": " + e.getMessage());
        } catch (ParserException e) {
            throw new IOException("cannot parse " + file.toString() + ": " + e.getMessage());
        }
        final Condenser condenser = new Condenser(document, true, true);
        return super.storeDocument(
                url,
                null,
                new Date(file.lastModified()),
                file.length(),
                document,
                condenser
                );
    }
    
    /**
     * add a file or a directory of files to the index
     * If the given file is a path to a directory, the complete sub-tree is indexed
     * @param start
     */
    public void addConcurrent(File start) {
        assert (start != null);
        assert (start.canRead()) : start.toString();
        if (!start.isDirectory()) {
            try {
                this.queue.put(start);
            } catch (InterruptedException e) {}
            return;
        }
        String[] s = start.list();
        File w;
        for (String t: s) {
            w = new File(start, t);
            if (w.canRead() && !w.isHidden()) {
                if (w.isDirectory()) {
                    addConcurrent(w);
                } else {
                    try {
                        this.queue.put(w);
                    } catch (InterruptedException e) {}
                }
            }
        }
    }
    
    /**
     * do a full-text search of a given string and return a specific number of results
     * @param querystring
     * @param pos
     * @param count
     * @return a list of files that contain the given string
     */    
    public ArrayList<File> find(String querystring, int pos, int count) {
        ArrayList<URIMetadataRow> result = findMetadata(querystring, this);
        ArrayList<File> files = new ArrayList<File>();
        for (URIMetadataRow row : result) {
            files.add(row.metadata().url().getLocalFile());
            count--;
            if (count == 0) break;
        }
        return files;
    }

    public static final ArrayList<URIMetadataRow> findMetadata(
            final String querystring,
            final Segment indexSegment) {
        QueryParams query = new QueryParams(querystring, 100, textRankingDefault, null);
        return findMetadata(query, indexSegment);
    }
    
    public static final ArrayList<URIMetadataRow> findMetadata(
            final QueryParams query,
            final Segment indexSegment) {
        
        RankingProcess rankedCache = new RankingProcess(indexSegment, query, 1000, 2);
        rankedCache.run();
        
        ArrayList<URIMetadataRow> result = new ArrayList<URIMetadataRow>();
        URIMetadataRow r;
        while ((r = rankedCache.takeURL(false, 1)) != null) result.add(r);
        
        return result;
    }
    
    /**
     * find the given string and return 20 hits
     * @param querystring
     * @return a list of files that contain the word
     */
    public ArrayList<File> find(String querystring) {
        return find(querystring, 0, 100);
    }
    
    /**
     * close the index.
     * This terminates all worker threads and then closes the segment.
     */
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
        public void commit(File f, URIMetadataRow resultRow);
        public void fail(File f, String failReason);
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
            public void commit(File f, URIMetadataRow resultRow) {
                System.out.println("indexed: " + f.toString());
            }
            public void fail(File f, String failReason) {
                System.out.println("not indexed " + f.toString() + ": " + failReason);
            }
        };
        try {
            if (args[1].equals("add")) {
                File f = new File(args[2]);
                DocumentIndex di = new DocumentIndex(segmentPath, callback, 100000);
                di.addConcurrent(f);
                di.close();
            } else {
                String query = "";
                for (int i = 2; i < args.length; i++) query += args[i];
                query.trim();
                DocumentIndex di = new DocumentIndex(segmentPath, callback, 100000);
                ArrayList<File> results = di.find(query);
                for (File f: results) {
                    if (f != null) System.out.println(f.toString());
                }
                di.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.exit(0);
    }
    
}
