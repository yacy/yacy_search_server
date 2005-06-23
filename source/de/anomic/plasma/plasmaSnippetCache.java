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

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;
import de.anomic.http.httpHeader;
import de.anomic.yacy.yacySearch;

public class plasmaSnippetCache {

    private static final int maxCache = 500;
    
    private int                   snippetsScoreCounter;
    private kelondroMScoreCluster snippetsScore;
    private HashMap               snippetsCache;
    private plasmaHTCache         cacheManager;
    private plasmaParser          parser;
    private serverLog             log;
    private String                remoteProxyHost;
    private int                   remoteProxyPort;
    private boolean               remoteProxyUse;
    
    public plasmaSnippetCache(plasmaHTCache cacheManager, plasmaParser parser,
                              String remoteProxyHost, int remoteProxyPort, boolean remoteProxyUse,
                              serverLog log) {
        this.cacheManager = cacheManager;
        this.parser = parser;
        this.log = log;
        this.remoteProxyHost = remoteProxyHost;
        this.remoteProxyPort = remoteProxyPort;
        this.remoteProxyUse = remoteProxyUse;
        this.snippetsScoreCounter = 0;
        this.snippetsScore = new kelondroMScoreCluster();
        this.snippetsCache = new HashMap();        
    }
    
    
    public synchronized void store(String wordhashes, String urlhash, String snippet) {
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
    
    private String retrieve(String wordhashes, String urlhash) {
        // generate key
        String key = urlhash + wordhashes;
        return (String) snippetsCache.get(key);
    }
    
    public String retrieve(java.net.URL url, boolean fetchOnline, Set queryhashes) {
        if (queryhashes.size() == 0) {
            //System.out.println("found no queryhashes for url retrieve " + url);
            return null;
        }
        String urlhash = plasmaURL.urlHash(url);
        
        // try to get snippet from snippetCache
        String wordhashes = yacySearch.set2string(queryhashes);
        String snippet = retrieve(wordhashes, urlhash);
        if (snippet != null) {
            //System.out.println("found snippet for url " + url + " in cache: " + snippet);
            return snippet;
        }
        
        // if the snippet is not in the cache, we can try to get it from the htcache
        plasmaParserDocument document = getDocument(url, fetchOnline);
        if (document == null) {
            //System.out.println("cannot load document for url " + url);
            return null;
        }
        //System.out.println("loaded document for url " + url);
        String[] sentences = document.getSentences();
        //System.out.println("----" + url.toString()); for (int l = 0; l < sentences.length; l++) System.out.println(sentences[l]);
        if ((sentences == null) || (sentences.length == 0)) {
            //System.out.println("found no sentences in url " + url);
            return null;
        }

        // we have found a parseable non-empty file: use the lines
        TreeMap sentencematrix = hashMatrix(sentences);
        Iterator i = queryhashes.iterator();
        String hash;
        kelondroMScoreCluster hitTable = new kelondroMScoreCluster();
        Iterator j;
        Integer sentencenumber;
        Map.Entry entry;
        while (i.hasNext()) {
            hash = (String) i.next();
            j = sentencematrix.entrySet().iterator();
            while (j.hasNext()) {
                entry = (Map.Entry) j.next();
                sentencenumber = (Integer) entry.getKey();
                if (((HashSet) entry.getValue()).contains(hash)) hitTable.addScore(sentencenumber, sentences[sentencenumber.intValue()].length());
            }
        }
        Integer maxLine = (Integer) hitTable.getMaxObject();
        if (maxLine == null) return null;
        snippet = sentences[maxLine.intValue()];
        //System.out.println("loaded snippet for url " + url + ": " + snippet);
        if (snippet.length() > 120) snippet = snippet.substring(0, 120);

        // finally store this snippet in our own cache
        store(wordhashes, urlhash, snippet);
        return snippet;
    }
        
    private TreeMap hashMatrix(String[] sentences) {
        TreeMap map = new TreeMap();
        HashSet set;
        Enumeration words;
        for (int i = 0; i < sentences.length; i++) {
            set = new HashSet();
            words = plasmaCondenser.wordTokenizer(sentences[i]);
            while (words.hasMoreElements()) set.add(plasmaWordIndexEntry.word2hash((String) words.nextElement()));
            map.put(new Integer(i), set);
        }
        return map;
    }
    
    private byte[] getResource(URL url, boolean fetchOnline) {
        // load the url as resource from the web
        try {
            //return httpc.singleGET(url, 5000, null, null, remoteProxyHost, remoteProxyPort);
            byte[] resource = cacheManager.loadResource(url);
            if ((fetchOnline) && (resource == null)) {
                loadResourceFromWeb(url, 5000);
                resource = cacheManager.loadResource(url);
            }
            return resource;
        } catch (IOException e) {
            return null;
        }
    }
    
    private void loadResourceFromWeb(URL url, int socketTimeout) throws IOException {
        plasmaCrawlWorker.load(
            url, 
            null, 
            null, 
            0, 
            null,
            socketTimeout,
            remoteProxyHost,
            remoteProxyPort,
            remoteProxyUse,
            cacheManager,
            log);
    }
    
    public plasmaParserDocument getDocument(URL url, boolean fetchOnline) {
        byte[] resource = getResource(url, fetchOnline);
        if (resource == null) return null;
        httpHeader header = null;
        try {
            header = cacheManager.getCachedResponse(plasmaURL.urlHash(url));
        } catch (IOException e) {}
        
        if (header == null) {
            String filename = url.getFile();
            int p = filename.lastIndexOf('.');
            if ((p < 0) ||
                ((p >= 0) && (plasmaParser.supportedFileExtContains(filename.substring(p + 1))))) {
                return parser.parseSource(url, "text/html", resource);
            } else {
                return null;
            }
        } else {
            if (plasmaParser.supportedMimeTypesContains(header.mime())) {
                return parser.parseSource(url, header.mime(), resource);
            } else {
                return null;
            }
        }
    }
}
