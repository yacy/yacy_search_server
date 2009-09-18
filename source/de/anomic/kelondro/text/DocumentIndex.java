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


package de.anomic.kelondro.text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import de.anomic.document.Condenser;
import de.anomic.document.Document;
import de.anomic.document.Parser;
import de.anomic.document.ParserException;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.search.QueryParams;
import de.anomic.search.RankingProcess;
import de.anomic.search.RankingProfile;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

/**
 * convenience class to access the yacycore library from outside of yacy to put files into the index
 * @author Michael Christen
 *
 */
public class DocumentIndex extends Segment {
	
    private RankingProfile textRankingDefault = new RankingProfile(QueryParams.CONTENTDOM_TEXT);
    //private Bitfield zeroConstraint = new Bitfield(4);
    
    File poison = new File(".");
    BlockingQueue<File> queue;
    Worker[] worker;
    
    public DocumentIndex(Log log, final File segmentPath) throws IOException {
        super(log, segmentPath, 100000, targetFileSize * 4 - 1, false, false);
        int cores = Runtime.getRuntime().availableProcessors() + 1;
        this.queue = new ArrayBlockingQueue<File>(cores * 2);
        this.worker = new Worker[cores];
        for (int i = 0; i < cores; i++) {
            this.worker[i] = new Worker();
            this.worker[i].start();
        }
    }
    
    public DocumentIndex(final File segmentPath) throws IOException {
        this(new Log("DocumentIndex"), segmentPath);
    }
	
    class Worker extends Thread {
        public void run() {
            File f;
            try {
                while ((f = queue.take()) != poison) try {
                    add(f);
                } catch (IOException e) {
                    if (e.getMessage().indexOf("cannot parse") < 0) e.printStackTrace();
                }
            } catch (InterruptedException e) {}
        }
    }
    
    /**
     * put a single file into the index
     * @param file
     * @return a metadata object that has been generated to identify the file
     * @throws IOException in case that the file does not exist or cannot be parsed
     */
    public URLMetadataRow add(File file) throws IOException {
        if (file == null) throw new IOException("file = null");
        if (file.isDirectory()) throw new IOException("file should be a document, not a path");
        if (!file.canRead()) throw new IOException("cannot read file");
    	yacyURL url = new yacyURL("file:" + file.getAbsolutePath());
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
    public void addAll(File start) {
        assert (start != null);
        assert (start.canRead());
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
            if (w.canRead() && ! w.isHidden()) {
                if (w.isDirectory()) {
                    addAll(w);
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
        QueryParams query = new QueryParams(querystring, 100, textRankingDefault, null);
        ArrayList<URLMetadataRow> result = findMetadata(query, this);
        ArrayList<File> files = new ArrayList<File>();
        for (URLMetadataRow row : result) {
            files.add(row.metadata().url().getLocalFile());
            count--;
            if (count == 0) break;
        }
        return files;
    }
    
    public static final ArrayList<URLMetadataRow> findMetadata(
            final QueryParams query,
            final Segment indexSegment) {
        
        RankingProcess rankedCache = new RankingProcess(indexSegment, query, 1000, 2);
        rankedCache.run();
        
        ArrayList<URLMetadataRow> result = new ArrayList<URLMetadataRow>();
        URLMetadataRow r;
        while ((r = rankedCache.takeURL(false, 1)) != null) result.add(r);
        
        return result;
    }
    
    /**
     * find the given string and return 20 hits
     * @param querystring
     * @return a list of files that contain the word
     */
    public ArrayList<File> find(String querystring) {
        return find(querystring, 0, 20);
    }
    
    public void close() {
        super.close();
        for (int i = 0; i < this.worker.length; i++) {
            try {
                this.queue.put(poison);
            } catch (InterruptedException e) {}
        }
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
        try {
            if (args[1].equals("add")) {
                File f = new File(args[2]);
                DocumentIndex di = new DocumentIndex(segmentPath);
                di.addAll(f);
                di.close();
            } else {
                String query = "";
                for (int i = 2; i < args.length; i++) query += args[i];
                query.trim();
                DocumentIndex di = new DocumentIndex(segmentPath);
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
