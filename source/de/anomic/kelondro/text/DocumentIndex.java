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
import java.util.Date;

import de.anomic.document.Condenser;
import de.anomic.document.Document;
import de.anomic.document.Parser;
import de.anomic.document.ParserException;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.search.QueryParams;
import de.anomic.search.RankingProfile;
import de.anomic.search.ResultEntry;
import de.anomic.search.SearchEvent;
import de.anomic.search.SearchEventCache;
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
    
    public DocumentIndex(Log log, final File segmentPath) throws IOException {
        super(log, segmentPath, 100000, targetFileSize * 4 - 1, false, false);
    }
    
    public DocumentIndex(final File segmentPath) throws IOException {
        this(new Log("DocumentIndex"), segmentPath);
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
                add(start);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
                        add(w);
                    } catch (IOException e) {
                        if (e.getMessage().indexOf("cannot parse") < 0) e.printStackTrace();
                    }
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
    public File[] find(String querystring, int pos, int count) {
        QueryParams query = new QueryParams(querystring, 100, textRankingDefault, null);
        SearchEvent se = SearchEventCache.getEvent(query, this, null, null, null, false);
        File[] result = new File[count];
        ResultEntry re;
        for (int i = 0; i < count; i++) {
            re = se.oneResult(pos + i);
            result[i] = (re == null) ? null : re.url().getLocalFile();
        }
        return result;
    }
    
    /**
     * find the given string and return 20 hits
     * @param querystring
     * @return a list of files that contain the word
     */
    public File[] find(String querystring) {
        return find(querystring, 0, 20);
    }
    
    public void close() {
        super.close();
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
                File[] results = di.find(query);
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
