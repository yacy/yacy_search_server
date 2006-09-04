// plasmaSnippetCache.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 07.06.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


package de.anomic.plasma;

import java.io.IOException;
import de.anomic.net.URL;
import de.anomic.plasma.crawler.http.CrawlWorker;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySearch;
import de.anomic.index.indexEntryAttribute;
import de.anomic.index.indexURL;

public class plasmaSnippetCache {

    private static final int maxCache = 500;
    
    public static final int SOURCE_CACHE = 0;
    public static final int SOURCE_FILE = 1;
    public static final int SOURCE_WEB = 2;
    
    public static final int ERROR_NO_HASH_GIVEN = 11;
    public static final int ERROR_SOURCE_LOADING = 12;
    public static final int ERROR_RESOURCE_LOADING = 13;
    public static final int ERROR_PARSER_FAILED = 14;
    public static final int ERROR_PARSER_NO_LINES = 15;
    public static final int ERROR_NO_MATCH = 16;
    
    private int                   snippetsScoreCounter;
    private kelondroMScoreCluster snippetsScore;
    private HashMap               snippetsCache;
    private plasmaHTCache         cacheManager;
    private plasmaParser          parser;
    private serverLog             log;
    private plasmaSwitchboard     sb;
    
    public plasmaSnippetCache(
            plasmaSwitchboard theSb,
            plasmaHTCache cacheManager, 
            plasmaParser parser,
            serverLog log
    ) {
        this.cacheManager = cacheManager;
        this.parser = parser;
        this.log = log;
        this.sb = theSb;
        this.snippetsScoreCounter = 0;
        this.snippetsScore = new kelondroMScoreCluster();
        this.snippetsCache = new HashMap();        
    }
    
    public class result {
        private String line;
        private String error;
        private int source;
        public result(String line, int source, String errortext) {
            this.line = line;
            this.source = source;
            this.error = errortext;
        }
        public boolean exists() {
            return line != null;
        }
        public String toString() {
            return (line == null) ? "" : line;
        }
        public String getLineRaw() {
            return (line == null) ? "" : line;
        }
        public String getError() {
            return (error == null) ? "" : error.trim();
        }
        public String getLineMarked(Set queryHashes) {
            if (line == null) return "";
            if ((queryHashes == null) || (queryHashes.size() == 0)) return line.trim();
            if (line.endsWith(".")) line = line.substring(0, line.length() - 1);
            Iterator i = queryHashes.iterator();
            String h;
            String[] w = line.split(" ");
            while (i.hasNext()) {
                h = (String) i.next();
                for (int j = 0; j < w.length; j++) {
                    if (indexEntryAttribute.word2hash(w[j]).equals(h)) w[j] = "<b>" + w[j] + "</b>";
                }
            }
            StringBuffer l = new StringBuffer(line.length() + queryHashes.size() * 8);
            for (int j = 0; j < w.length; j++) {
                l.append(w[j]);
                l.append(' ');
            }
            return l.toString().trim();
        }
        public int getSource() {
            return source;
        }
    }
    
    public boolean existsInCache(URL url, Set queryhashes) {
        String hashes = yacySearch.set2string(queryhashes);
        return retrieveFromCache(hashes, indexURL.urlHash(url)) != null;
    }
    
    public result retrieve(URL url, Set queryhashes, boolean fetchOnline, int snippetMaxLength) {
        // heise = "0OQUNU3JSs05"
        if (queryhashes.size() == 0) {
            //System.out.println("found no queryhashes for URL retrieve " + url);
            return new result(null, ERROR_NO_HASH_GIVEN, "no query hashes given");
        }
        String urlhash = indexURL.urlHash(url);
        
        // try to get snippet from snippetCache
        int source = SOURCE_CACHE;
        String wordhashes = yacySearch.set2string(queryhashes);
        String line = retrieveFromCache(wordhashes, urlhash);
        if (line != null) {
            //System.out.println("found snippet for URL " + url + " in cache: " + line);
            return new result(line, source, null);
        }
        
        // if the snippet is not in the cache, we can try to get it from the htcache
        byte[] resource = null;
        httpHeader header = null;
        try {
            resource = cacheManager.loadResource(url);
            if ((fetchOnline) && (resource == null)) {
                plasmaHTCache.Entry entry = loadResourceFromWeb(url, 5000);
                if (entry != null) {
                    header = entry.responseHeader;
                }
                resource = cacheManager.loadResource(url);
                source = SOURCE_WEB;
            }
        } catch (IOException e) {
            return new result(null, ERROR_SOURCE_LOADING, "error loading resource from web: " + e.getMessage());
        }
        if (resource == null) {
            //System.out.println("cannot load document for URL " + url);
            return new result(null, ERROR_RESOURCE_LOADING, "error loading resource from web, cacheManager returned NULL");
        }
        plasmaParserDocument document = parseDocument(url, resource, header);
        
        if (document == null) return new result(null, ERROR_PARSER_FAILED, "parser error/failed"); // cannot be parsed
        //System.out.println("loaded document for URL " + url);
        String[] sentences = document.getSentences();
        //System.out.println("----" + url.toString()); for (int l = 0; l < sentences.length; l++) System.out.println(sentences[l]);
        if ((sentences == null) || (sentences.length == 0)) {
            //System.out.println("found no sentences in url " + url);
            return new result(null, ERROR_PARSER_NO_LINES, "parser returned no sentences");
        }

        // we have found a parseable non-empty file: use the lines
        line = computeSnippet(sentences, queryhashes, 8 + 6 * queryhashes.size(), snippetMaxLength);
        //System.out.println("loaded snippet for URL " + url + ": " + line);
        if (line == null) return new result(null, ERROR_NO_MATCH, "no matching snippet found");
        if (line.length() > snippetMaxLength) line = line.substring(0, snippetMaxLength);

        // finally store this snippet in our own cache
        storeToCache(wordhashes, urlhash, line);
        return new result(line, source, null);
    }
    
    public void storeToCache(String wordhashes, String urlhash, String snippet) {
        // generate key
        String key = urlhash + wordhashes;

        // do nothing if snippet is known
        if (snippetsCache.containsKey(key)) return;

        // learn new snippet
        snippetsScore.addScore(key, snippetsScoreCounter++);
        snippetsCache.put(key, snippet);

        // care for counter
        if (snippetsScoreCounter == java.lang.Integer.MAX_VALUE) {
            snippetsScoreCounter = 0;
            snippetsScore = new kelondroMScoreCluster();
            snippetsCache = new HashMap();
        }
        
        // flush cache if cache is full
        while (snippetsCache.size() > maxCache) {
            key = (String) snippetsScore.getMinObject();
            snippetsScore.deleteScore(key);
            snippetsCache.remove(key);
        }
    }
    
    private String retrieveFromCache(String wordhashes, String urlhash) {
        // generate key
        String key = urlhash + wordhashes;
        return (String) snippetsCache.get(key);
    }
    
    private String computeSnippet(String[] sentences, Set queryhashes, int minLength, int maxLength) {
        try {
            if ((sentences == null) || (sentences.length == 0)) return null;
            if ((queryhashes == null) || (queryhashes.size() == 0)) return null;
            kelondroMScoreCluster hitTable = new kelondroMScoreCluster();
            Iterator j;
            HashMap hs;
            String hash;
            for (int i = 0; i < sentences.length; i++) {
                //System.out.println("Sentence " + i + ": " + sentences[i]);
                if (sentences[i].length() > minLength) {
                    hs = hashSentence(sentences[i]);
                    j = queryhashes.iterator();
                    while (j.hasNext()) {
                        hash = (String) j.next();
                        if (hs.containsKey(hash)) {
                            //System.out.println("hash " + hash + " appears in line " + i);
                            hitTable.incScore(new Integer(i));
                        }
                    }
                }
            }
            int score = hitTable.getMaxScore(); // best number of hits
            if (score <= 0) return null;
            // we found (a) line(s) that have <score> hits.
            // now find the shortest line of these hits
            int shortLineIndex = -1;
            int shortLineLength = Integer.MAX_VALUE;
            for (int i = 0; i < sentences.length; i++) {
                if ((hitTable.getScore(new Integer(i)) == score) &&
                (sentences[i].length() < shortLineLength)) {
                    shortLineIndex = i;
                    shortLineLength = sentences[i].length();
                }
            }
            // find a first result
            String result = sentences[shortLineIndex];
            // remove all hashes that appear in the result
            hs = hashSentence(result);
            j = queryhashes.iterator();
            Integer pos;
            Set remaininghashes = new HashSet();
            int p, minpos = result.length(), maxpos = -1;
            while (j.hasNext()) {
                hash = (String) j.next();
                pos = (Integer) hs.get(hash);
                if (pos == null) {
                    remaininghashes.add(new String(hash));
                } else {
                    p = pos.intValue();
                    if (p > maxpos) maxpos = p;
                    if (p < minpos) minpos = p;
                }
            }
            // check result size
            maxpos = maxpos + 10;
            if (maxpos > result.length()) maxpos = result.length();
            if (minpos < 0) minpos = 0;
            // we have a result, but is it short enough?
            if (maxpos - minpos + 10 > maxLength) {
                // the string is too long, even if we cut at both ends
                // so cut here in the middle of the string
                int lenb = result.length();
                result = result.substring(0, (minpos + 20 > result.length()) ? result.length() : minpos + 20).trim() +
                " [..] " +
                result.substring((maxpos + 26 > result.length()) ? result.length() : maxpos + 26).trim();
                maxpos = maxpos + lenb - result.length() + 6;
            }
            if (maxpos > maxLength) {
                // the string is too long, even if we cut it at the end
                // so cut it here at both ends at once
                int newlen = maxpos - minpos + 10;
                int around = (maxLength - newlen) / 2;
                result = "[..] " + result.substring(minpos - around, ((maxpos + around) > result.length()) ? result.length() : (maxpos + around)).trim() + " [..]";
                minpos = around;
                maxpos = result.length() - around - 5;
            }
            if (result.length() > maxLength) {
                // trim result, 1st step (cut at right side)
                result = result.substring(0, maxpos).trim() + " [..]";
            }
            if (result.length() > maxLength) {
                // trim result, 2nd step (cut at left side)
                result = "[..] " + result.substring(minpos).trim();
            }
            if (result.length() > maxLength) {
                // trim result, 3rd step (cut in the middle)
                result = result.substring(6, 20).trim() + " [..] " + result.substring(result.length() - 26, result.length() - 6).trim();
            }
            if (queryhashes.size() == 0) return result;
            // the result has not all words in it.
            // find another sentence that represents the missing other words
            // and find recursively more sentences
            maxLength = maxLength - result.length();
            if (maxLength < 20) maxLength = 20;
            String nextSnippet = computeSnippet(sentences, remaininghashes, minLength, maxLength);
            return result + ((nextSnippet == null) ? "" : (" / " + nextSnippet));
        } catch (IndexOutOfBoundsException e) {
            log.logSevere("computeSnippet: error with string generation", e);
            return "";
        }
    }
    
    private HashMap hashSentence(String sentence) {
        // generates a word-wordPos mapping
        HashMap map = new HashMap();
        Enumeration words = plasmaCondenser.wordTokenizer(sentence, 0);
        int pos = 0;
        String word;
        while (words.hasMoreElements()) {
            word = (String) words.nextElement();
            map.put(indexEntryAttribute.word2hash(word), new Integer(pos));
            pos += word.length() + 1;
        }
        return map;
    }
     
    public plasmaParserDocument parseDocument(URL url, byte[] resource) {
        return parseDocument(url, resource, null);
    }
    
    public plasmaParserDocument parseDocument(URL url, byte[] resource, httpHeader header) {
        try {
            if (resource == null) return null;

            // try to get the header from the htcache directory
            if (header == null) {
                try {
                    header = this.cacheManager.getCachedResponse(indexURL.urlHash(url));
                } catch (IOException e) {}
            }

            if (header == null) {
                String filename = this.cacheManager.getCachePath(url).getName();
                int p = filename.lastIndexOf('.');
                if (    // if no extension is available
                        (p < 0) ||
                        // or the extension is supported by one of the parsers
                        ((p >= 0) && (plasmaParser.supportedFileExtContains(filename.substring(p + 1))))
                ) {
                    String supposedMime = "text/html";

                    // if the mimeType Parser is installed we can set the mimeType to null to force
                    // a mimetype detection
                    if (plasmaParser.supportedMimeTypesContains("application/octet-stream")) {
                        supposedMime = null;
                    } else if (p != -1){
                        // otherwise we try to determine the mimeType per file Extension
                        supposedMime = plasmaParser.getMimeTypeByFileExt(filename.substring(p + 1));
                    }

                    return this.parser.parseSource(url, supposedMime, resource);
                }
                return null;
            }
            if (plasmaParser.supportedMimeTypesContains(header.mime())) {
                return this.parser.parseSource(url, header.mime(), resource);
            }
            return null;
        } catch (InterruptedException e) {
            // interruption of thread detected
            return null;
        }
    }
    
    public byte[] getResource(URL url, boolean fetchOnline, int socketTimeout) {
        // load the url as resource from the web
        try {
            byte[] resource = cacheManager.loadResource(url);
            if ((fetchOnline) && (resource == null)) {
                loadResourceFromWeb(url, (socketTimeout < 0) ? -1 : socketTimeout);
                resource = cacheManager.loadResource(url);
            }
            return resource;
        } catch (IOException e) {
            return null;
        }
    }
    
    public plasmaHTCache.Entry loadResourceFromWeb(URL url, int socketTimeout) throws IOException {
        
        plasmaHTCache.Entry result = this.sb.cacheLoader.loadSync(
                url, 
                "",
                null, 
                null, 
                0, 
                null,
                socketTimeout
        );
        
        return result;
    }
    
    public void fetch(plasmaSearchResult acc, Set queryhashes, String urlmask, int fetchcount, long maxTime) {
        // fetch snippets
        int i = 0;
        plasmaCrawlLURL.Entry urlentry;
        String urlstring;
        long limitTime = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        while ((acc.hasMoreElements()) && (i < fetchcount) && (System.currentTimeMillis() < limitTime)) {
            urlentry = acc.nextElement();
            if (urlentry.url().getHost().endsWith(".yacyh")) continue;
            urlstring = urlentry.url().toNormalform();
            if ((urlstring.matches(urlmask)) &&
                (!(existsInCache(urlentry.url(), queryhashes)))) {
                new Fetcher(urlentry.url(), queryhashes).start();
                i++;
            }
        }
    }
        
    public class Fetcher extends Thread {
        URL url;
        Set queryhashes;
        public Fetcher(URL url, Set queryhashes) {
            if (url.getHost().endsWith(".yacyh")) return;
            this.url = url;
            this.queryhashes = queryhashes;
        }
        public void run() {
            log.logFine("snippetFetcher: try to get URL " + url);
            plasmaSnippetCache.result snippet = retrieve(url, queryhashes, true, 260);
            if (snippet.line == null)
                log.logFine("snippetFetcher: cannot get URL " + url + ". error(" + snippet.source + "): " + snippet.error);
            else
                log.logFine("snippetFetcher: got URL " + url + ", the snippet is '" + snippet.line + "', source=" + snippet.source);
        }
    }
    
}
