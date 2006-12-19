// plasmaSnippetCache.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 09.10.2006
//
// contributions by Marc Nause [MN]
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.net.URL;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.crawler.plasmaCrawlerException;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacyCore;

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
    
    public class TextSnippet {
        private String line;
        private String error;
        private int source;
        public TextSnippet(String line, int source, String errortext) {
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
            String prefix = "";
            String postfix = "";
            int len = 0;
            while (i.hasNext()) {
                h = (String) i.next();
                for (int j = 0; j < w.length; j++) {
                    //ignore punctuation marks (contrib [MN])
                    //note to myself:
                    //For details on regex see "Mastering regular expressions" by J.E.F. Friedl
                    //especially p. 123 and p. 390/391 (in the German version of the 2nd edition)

                    prefix = "";
                    postfix = "";

                    // cut off prefix if it contains of non-characters or non-numbers
                    while(w[j].matches("\\A[^\\p{L}\\p{N}].+")) {
                        prefix = w[j].substring(0,1) + prefix;
                        w[j] = w[j].substring(1);
                    }

                    // cut off postfix if it contains of non-characters or non-numbers
                    while(w[j].matches(".+[^\\p{L}\\p{N}]\\Z")) {
                        len = w[j].length();
                        postfix = w[j].substring(len-1,len) + postfix;
                        w[j] = w[j].substring(0,len-1);
                    }

                    //special treatment if there is a special character in the word
                    if(w[j].matches("\\A[\\p{L}\\p{N}]+[^\\p{L}\\p{N}].+\\Z")) {
                        String out = "";
                        String temp = "";
                        for(int k=0; k < w[j].length(); k++) {
                            //is character a special character?
                            if(w[j].substring(k,k+1).matches("[^\\p{L}\\p{N}]")) {
                                if (plasmaCondenser.word2hash(temp).equals(h)) temp = "<b>" + temp + "</b>";
                                out = out + temp + w[j].substring(k,k+1);
                                temp = "";
                            }
                            //last character
                            else if(k == (w[j].length()-1)) {
                                temp = temp + w[j].substring(k,k+1);
                                if (plasmaCondenser.word2hash(temp).equals(h)) temp = "<b>" + temp + "</b>";
                                out = out + temp;
                                temp = "";
                            }
                            else temp = temp + w[j].substring(k,k+1);
                        }
                        w[j] = out;
                    }

                    //end contrib [MN]
                    else if (plasmaCondenser.word2hash(w[j]).equals(h)) w[j] = "<b>" + w[j] + "</b>";

                    w[j] = prefix + w[j] + postfix;
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
    
    public class MediaSnippet {
        public String type, href, name, attr;
        public MediaSnippet(String type, String href, String name, String attr) {
            this.type = type;
            this.href = href;
            this.name = name;
            this.attr = attr;
            if ((this.name == null) || (this.name.length() == 0)) this.name = "_";
            if ((this.attr == null) || (this.attr.length() == 0)) this.attr = "_";
        }
    }
    
    public boolean existsInCache(URL url, Set queryhashes) {
        String hashes = yacySearch.set2string(queryhashes);
        return retrieveFromCache(hashes, plasmaURL.urlHash(url)) != null;
    }
    
    public TextSnippet retrieveTextSnippet(URL url, Set queryhashes, boolean fetchOnline, boolean pre, int snippetMaxLength, int timeout) {
        // heise = "0OQUNU3JSs05"
        if (queryhashes.size() == 0) {
            //System.out.println("found no queryhashes for URL retrieve " + url);
            return new TextSnippet(null, ERROR_NO_HASH_GIVEN, "no query hashes given");
        }
        String urlhash = plasmaURL.urlHash(url);
        
        // try to get snippet from snippetCache
        int source = SOURCE_CACHE;
        String wordhashes = yacySearch.set2string(queryhashes);
        String line = retrieveFromCache(wordhashes, urlhash);
        if (line != null) {
            //System.out.println("found snippet for URL " + url + " in cache: " + line);
            return new TextSnippet(line, source, null);
        }
        
        /* ===========================================================================
         * LOADING RESOURCE DATA
         * =========================================================================== */
        // if the snippet is not in the cache, we can try to get it from the htcache
        long resContentLength = 0;
        InputStream resContent = null;
        IResourceInfo resInfo = null;
        try {
            // trying to load the resource from the cache
            resContent = this.cacheManager.getResourceContentStream(url);
            if (resContent != null) {
                // if the content was found
                resContentLength = this.cacheManager.getResourceContentLength(url);
            } else if (fetchOnline) {
                // if not found try to download it
                
                // download resource using the crawler and keep resource in memory if possible
                plasmaHTCache.Entry entry = loadResourceFromWeb(url, timeout, true, true);
                
                // getting resource metadata (e.g. the http headers for http resources)
                if (entry != null) {
                    resInfo = entry.getDocumentInfo();

                    // read resource body (if it is there)
                    byte []resourceArray = entry.cacheArray();
                    if (resourceArray != null) {
                        resContent = new ByteArrayInputStream(resourceArray);
                        resContentLength = resourceArray.length;
                    } else {
                        resContent = this.cacheManager.getResourceContentStream(url); 
                        resContentLength = this.cacheManager.getResourceContentLength(url);
                    }
                }
                
                // if it is still not available, report an error
                if (resContent == null) return new TextSnippet(null, ERROR_RESOURCE_LOADING, "error loading resource, plasmaHTCache.Entry cache is NULL");                
                
                source = SOURCE_WEB;
            } else {
                return new TextSnippet(null, ERROR_SOURCE_LOADING, "no resource available");
            }
        } catch (Exception e) {
            if (!(e instanceof plasmaCrawlerException)) e.printStackTrace();
            return new TextSnippet(null, ERROR_SOURCE_LOADING, "error loading resource: " + e.getMessage());
        } 

        /* ===========================================================================
         * PARSING RESOURCE
         * =========================================================================== */
        plasmaParserDocument document = null;
        try {
             document = parseDocument(url, resContentLength, resContent, resInfo);            
        } catch (ParserException e) {
            return new TextSnippet(null, ERROR_PARSER_FAILED, e.getMessage()); // cannot be parsed
        } finally {
            try { resContent.close(); } catch (Exception e) {/* ignore this */}
        }
        if (document == null) return new TextSnippet(null, ERROR_PARSER_FAILED, "parser error/failed"); // cannot be parsed
        
        
        /* ===========================================================================
         * COMPUTE SNIPPET
         * =========================================================================== */        
        // we have found a parseable non-empty file: use the lines

        // compute snippet from text
        final Iterator sentences = document.getSentences(pre);
        if (sentences == null) return new TextSnippet(null, ERROR_PARSER_NO_LINES, "parser returned no sentences");
        String textline = computeTextSnippet(sentences, queryhashes, 3 * queryhashes.size(), snippetMaxLength);
        
        // compute snippet from media
        String audioline = computeMediaSnippet(document.getAudiolinks(), queryhashes);
        String videoline = computeMediaSnippet(document.getVideolinks(), queryhashes);
        String appline = computeMediaSnippet(document.getApplinks(), queryhashes);
        //String hrefline = computeMediaSnippet(document.getAnchors(), queryhashes);
        //String imageline = computeMediaSnippet(document.getAudiolinks(), queryhashes);
        
        line = "";
        if (audioline != null) line += (line.length() == 0) ? audioline : "<br />" + audioline;
        if (videoline != null) line += (line.length() == 0) ? videoline : "<br />" + videoline;
        if (appline   != null) line += (line.length() == 0) ? appline   : "<br />" + appline;
        //if (hrefline  != null) line += (line.length() == 0) ? hrefline  : "<br />" + hrefline;
        if (textline  != null) line += (line.length() == 0) ? textline  : "<br />" + textline;
        
        if ((line == null) || (line.length() < 3 /*snippetMinLength*/)) return new TextSnippet(null, ERROR_NO_MATCH, "no matching snippet found");
        if (line.length() > snippetMaxLength) line = line.substring(0, snippetMaxLength);

        // finally store this snippet in our own cache
        storeToCache(wordhashes, urlhash, line);
        document.close();
        return new TextSnippet(line, source, null);
    }

    /**
     * Tries to load and parse a resource specified by it's URL.
     * If the resource is not stored in cache and if fetchOnline is set the
     * this function tries to download the resource from web.
     * 
     * @param url the URL of the resource
     * @param fetchOnline specifies if the resource should be loaded from web if it'as not available in the cache
     * @return the parsed document as {@link plasmaParserDocument}
     */
    public plasmaParserDocument retrieveDocument(URL url, boolean fetchOnline, int timeout, boolean forText) {

        // load resource
        long resContentLength = 0;
        InputStream resContent = null;
        IResourceInfo resInfo = null;
        try {
            // trying to load the resource from the cache
            resContent = this.cacheManager.getResourceContentStream(url);
            if (resContent != null) {
                // if the content was found
                resContentLength = this.cacheManager.getResourceContentLength(url);
            } else if (fetchOnline) {
                // if not found try to download it
                
                // download resource using the crawler and keep resource in memory if possible
                plasmaHTCache.Entry entry = loadResourceFromWeb(url, timeout, true, forText);
                
                // getting resource metadata (e.g. the http headers for http resources)
                if (entry != null) {
                    resInfo = entry.getDocumentInfo();

                    // read resource body (if it is there)
                    byte []resourceArray = entry.cacheArray();
                    if (resourceArray != null) {
                        resContent = new ByteArrayInputStream(resourceArray);
                        resContentLength = resourceArray.length;
                    } else {
                        resContent = this.cacheManager.getResourceContentStream(url); 
                        resContentLength = this.cacheManager.getResourceContentLength(url);
                    }
                }
                
                // if it is still not available, report an error
                if (resContent == null) {
                    serverLog.logFine("snippet fetch", "plasmaHTCache.Entry cache is NULL for url " + url);
                    return null;
                }
            } else {
                serverLog.logFine("snippet fetch", "no resource available for url " + url);
                return null;
            }
        } catch (Exception e) {
            serverLog.logFine("snippet fetch", "error loading resource: " + e.getMessage() + " for url " + url);
            return null;
        } 

        // parse resource
        plasmaParserDocument document = null;
        try {
            document = parseDocument(url, resContentLength, resContent, resInfo);            
        } catch (ParserException e) {
            serverLog.logFine("snippet fetch", "parser error " + e.getMessage() + " for url " + url);
            return null;
        } finally {
            try { resContent.close(); } catch (Exception e) {}
        }
        return document;
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
    
    private String computeMediaSnippet(Map media, Set queryhashes) {
        Iterator i = media.entrySet().iterator();
        Map.Entry entry;
        String url, desc;
        Set s;
        String result = "";
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            url = (String) entry.getKey();
            desc = (String) entry.getValue();
            s = removeAppearanceHashes(url, queryhashes);
            if (s.size() == 0) {
                result += "<br /><a href=\"" + url + "\">" + ((desc.length() == 0) ? url : desc) + "</a>";
                continue;
            }
            s = removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                result += "<br /><a href=\"" + url + "\">" + ((desc.length() == 0) ? url : desc) + "</a>";
                continue;
            }
        }
        if (result.length() == 0) return null;
        return result.substring(6);
    }
    
    private String computeTextSnippet(Iterator sentences, Set queryhashes, int minLength, int maxLength) {
        try {
            if (sentences == null) return null;
            if ((queryhashes == null) || (queryhashes.size() == 0)) return null;
            Iterator j;
            HashMap hs;
            String hash;
            String sentence;
            TreeMap os = new TreeMap();
            int uniqCounter = 9999;
            int score;
            while (sentences.hasNext()) {
                sentence = (String) sentences.next();
                //System.out.println("Snippet-Sentence :" + sentence); // DEBUG
                if (sentence.length() > minLength) {
                    hs = hashSentence(sentence);
                    j = queryhashes.iterator();
                    score = 0;
                    while (j.hasNext()) {
                        hash = (String) j.next();
                        if (hs.containsKey(hash)) {
                            //System.out.println("hash " + hash + " appears in line " + i);
                            score++;
                        }
                    }
                    if (score > 0) {
                        os.put(new Integer(1000000 * score - sentence.length() * 10000 + uniqCounter--), sentence);
                    }
                }
            }
            
            String result;
            Set remaininghashes;
            while (os.size() > 0) {
                sentence = (String) os.remove((Integer) os.lastKey()); // sentence with the biggest score
                result = computeTextSnippet(sentence, queryhashes, minLength, maxLength);
                if ((result != null) && (result.length() > 0)) {
                    remaininghashes = removeAppearanceHashes(result, queryhashes);
                    if (remaininghashes.size() == 0) {
                        // we have found the snippet
                        return result;
                    } else if (remaininghashes.size() < queryhashes.size()) {
                        // the result has not all words in it.
                        // find another sentence that represents the missing other words
                        // and find recursively more sentences
                        maxLength = maxLength - result.length();
                        if (maxLength < 20) maxLength = 20;
                        String nextSnippet = computeTextSnippet(os.values().iterator(), remaininghashes, minLength / 2, maxLength);
                        if ((nextSnippet == null) || (nextSnippet.length() < (minLength / 2))) return null; // no success
                        return result + (" / " + nextSnippet);
                    } else {
                        // error
                        //assert remaininghashes.size() < queryhashes.size() : "remaininghashes.size() = " + remaininghashes.size() + ", queryhashes.size() = " + queryhashes.size() + ", sentence = '" + sentence + "', result = '" + result + "'";
                        continue;
                    }
                }
            }
            return null;
        } catch (IndexOutOfBoundsException e) {
            log.logSevere("computeSnippet: error with string generation", e);
            return "";
        }
    }
    
    private String computeTextSnippet(String sentence, Set queryhashes, int minLength, int maxLength) {
        try {
            if (sentence == null) return null;
            if ((queryhashes == null) || (queryhashes.size() == 0)) return null;
            Iterator j;
            HashMap hs;
            String hash;
            
            // find all hashes that appear in the sentence
            hs = hashSentence(sentence);
            j = queryhashes.iterator();
            Integer pos;
            int p, minpos = sentence.length(), maxpos = -1;
            while (j.hasNext()) {
                hash = (String) j.next();
                pos = (Integer) hs.get(hash);
                if (pos != null) {
                    p = pos.intValue();
                    if (p > maxpos) maxpos = p;
                    if (p < minpos) minpos = p;
                }
            }
            // check result size
            maxpos = maxpos + 10;
            if (maxpos > sentence.length()) maxpos = sentence.length();
            if (minpos < 0) minpos = 0;
            // we have a result, but is it short enough?
            if (maxpos - minpos + 10 > maxLength) {
                // the string is too long, even if we cut at both ends
                // so cut here in the middle of the string
                int lenb = sentence.length();
                sentence = sentence.substring(0, (minpos + 20 > sentence.length()) ? sentence.length() : minpos + 20).trim() +
                " [..] " +
                sentence.substring((maxpos + 26 > sentence.length()) ? sentence.length() : maxpos + 26).trim();
                maxpos = maxpos + lenb - sentence.length() + 6;
            }
            if (maxpos > maxLength) {
                // the string is too long, even if we cut it at the end
                // so cut it here at both ends at once
                int newlen = maxpos - minpos + 10;
                int around = (maxLength - newlen) / 2;
                sentence = "[..] " + sentence.substring(minpos - around, ((maxpos + around) > sentence.length()) ? sentence.length() : (maxpos + around)).trim() + " [..]";
                minpos = around;
                maxpos = sentence.length() - around - 5;
            }
            if (sentence.length() > maxLength) {
                // trim sentence, 1st step (cut at right side)
                sentence = sentence.substring(0, maxpos).trim() + " [..]";
            }
            if (sentence.length() > maxLength) {
                // trim sentence, 2nd step (cut at left side)
                sentence = "[..] " + sentence.substring(minpos).trim();
            }
            if (sentence.length() > maxLength) {
                // trim sentence, 3rd step (cut in the middle)
                sentence = sentence.substring(6, 20).trim() + " [..] " + sentence.substring(sentence.length() - 26, sentence.length() - 6).trim();
            }
            return sentence;
        } catch (IndexOutOfBoundsException e) {
            log.logSevere("computeSnippet: error with string generation", e);
            return null;
        }
    }
    
    public ArrayList retrieveMediaSnippets(URL url, Set queryhashes, boolean fetchOnline, int timeout) {
        if (queryhashes.size() == 0) {
            serverLog.logFine("snippet fetch", "no query hashes given for url " + url);
            return new ArrayList();
        }

        plasmaParserDocument document = retrieveDocument(url, fetchOnline, timeout, false);
        ArrayList a = new ArrayList();
        if (document != null) {
            a.addAll(computeMediaSnippets(document, queryhashes, "audio"));
            a.addAll(computeMediaSnippets(document, queryhashes, "video"));
            a.addAll(computeMediaSnippets(document, queryhashes, "app"));
            a.addAll(computeImageSnippets(document, queryhashes));
        }
        return a;
    }
    
    public ArrayList computeMediaSnippets(plasmaParserDocument document, Set queryhashes, String mediatype) {
        
        if (document == null) return new ArrayList();
        Map media = null;
        if (mediatype.equals("audio")) media = document.getAudiolinks();
        else if (mediatype.equals("video")) media = document.getVideolinks();
        else if (mediatype.equals("app")) media = document.getApplinks();
        if (media == null) return null;
        
        Iterator i = media.entrySet().iterator();
        Map.Entry entry;
        String url, desc;
        Set s;
        ArrayList result = new ArrayList();
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            url = (String) entry.getKey();
            desc = (String) entry.getValue();
            //result.add(new MediaSnippet(mediatype, url, (desc.length() == 0) ? url : desc, null));
            s = removeAppearanceHashes(url, queryhashes);
            if (s.size() == 0) {
                result.add(new MediaSnippet(mediatype, url, desc, null));
                continue;
            }
            s = removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                result.add(new MediaSnippet(mediatype, url, desc, null));
                continue;
            }
        }
        return result;
    }
    
    public ArrayList computeImageSnippets(plasmaParserDocument document, Set queryhashes) {
        
        TreeSet images = document.getImages();
        
        Iterator i = images.iterator();
        htmlFilterImageEntry ientry;
        String url, desc;
        Set s;
        ArrayList result = new ArrayList();
        while (i.hasNext()) {
            ientry = (htmlFilterImageEntry) i.next();
            url = (String) ientry.url().toNormalform();
            desc = (String) ientry.alt();
            //result.add(new MediaSnippet("image", url, (desc.length() == 0) ? url : desc, ientry.width() + " x " + ientry.height()));
            s = removeAppearanceHashes(url, queryhashes);
            if (s.size() == 0) {
                result.add(new MediaSnippet("image", url, desc, ientry.width() + " x " + ientry.height()));
                continue;
            }
            s = removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                result.add(new MediaSnippet("image", url, desc, ientry.width() + " x " + ientry.height()));
                continue;
            }
        }
        return result;
    }
    
    private Set removeAppearanceHashes(String sentence, Set queryhashes) {
        // remove all hashes that appear in the sentence
        if (sentence == null) return queryhashes;
        HashMap hs = hashSentence(sentence);
        Iterator j = queryhashes.iterator();
        String hash;
        Integer pos;
        Set remaininghashes = new HashSet();
        while (j.hasNext()) {
            hash = (String) j.next();
            pos = (Integer) hs.get(hash);
            if (pos == null) {
                remaininghashes.add(new String(hash));
            }
        }
        return remaininghashes;
    }
    
    private HashMap hashSentence(String sentence) {
        // generates a word-wordPos mapping
        HashMap map = new HashMap();
        Enumeration words = plasmaCondenser.wordTokenizer(sentence, "UTF-8", 0);
        int pos = 0;
        String word;
        while (words.hasMoreElements()) {
            word = (String) words.nextElement();
            map.put(plasmaCondenser.word2hash(word), new Integer(pos));
            pos += word.length() + 1;
        }
        return map;
    }
     
    public plasmaParserDocument parseDocument(URL url, long contentLength, InputStream resourceStream) throws ParserException {
        return parseDocument(url, contentLength, resourceStream, null);
    }
    
    /**
     * Parse the resource
     * @param url the URL of the resource
     * @param contentLength the contentLength of the resource
     * @param resourceStream the resource body as stream
     * @param docInfo metadata about the resource
     * @return the extracted data
     * @throws ParserException
     */
    public plasmaParserDocument parseDocument(URL url, long contentLength, InputStream resourceStream, IResourceInfo docInfo) throws ParserException {
        try {
            if (resourceStream == null) return null;

            // STEP 1: if no resource metadata is available, try to load it from cache 
            if (docInfo == null) {
                // try to get the header from the htcache directory
                try {                    
                    docInfo = this.cacheManager.loadResourceInfo(url);
                } catch (Exception e) {
                    // ignore this. resource info loading failed
                }   
            }
            
            // STEP 2: if the metadata is still null try to download it from web
            if ((docInfo == null) && (url.getProtocol().startsWith("http"))) {
                // TODO: we need a better solution here
                // e.g. encapsulate this in the crawlLoader class
                
                // getting URL mimeType
                try {
                    httpHeader header = httpc.whead(url, url.getHost(), 10000, null, null, this.sb.remoteProxyConfig);
                    docInfo = this.cacheManager.getResourceInfoFactory().buildResourceInfoObj(url, header);
                } catch (Exception e) {
                    // ingore this. http header download failed
                } 
            }

            // STEP 3: if the metadata is still null try to guess the mimeType of the resource
            if (docInfo == null) {
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

                    return this.parser.parseSource(url, supposedMime, null, contentLength, resourceStream);
                }
                return null;
            }            
            if (plasmaParser.supportedMimeTypesContains(docInfo.getMimeType())) {
                return this.parser.parseSource(url, docInfo.getMimeType(), docInfo.getCharacterEncoding(), contentLength, resourceStream);
            }
            return null;
        } catch (InterruptedException e) {
            // interruption of thread detected
            return null;
        }
    }
    
    /**
     * 
     * @param url
     * @param fetchOnline
     * @param socketTimeout
     * @return an Object array containing
     * <table>
     * <tr><td>[0]</td><td>the content as {@link InputStream}</td></tr>
     * <tr><td>[1]</td><td>the content-length as {@link Integer}</td></tr>
     * </table>
     */
    public Object[] getResource(URL url, boolean fetchOnline, int socketTimeout, boolean forText) {
        // load the url as resource from the web
        try {
            long contentLength = -1;
            
            // trying to load the resource body from cache
            InputStream resource = this.cacheManager.getResourceContentStream(url);
            if (resource != null) {
                contentLength = this.cacheManager.getResourceContentLength(url);
            } else if (fetchOnline) {
                // if the content is not available in cache try to download it from web
                
                // try to download the resource using a crawler
                plasmaHTCache.Entry entry = loadResourceFromWeb(url, (socketTimeout < 0) ? -1 : socketTimeout, true, forText);
                
                // read resource body (if it is there)
                byte[] resourceArray = entry.cacheArray();
            
                // in case that the reosurce was not in ram, read it from disk
                if (resourceArray == null) {
                    resource = this.cacheManager.getResourceContentStream(url);   
                    contentLength = this.cacheManager.getResourceContentLength(url); 
                } else {
                    resource = new ByteArrayInputStream(resourceArray);
                    contentLength = resourceArray.length;
                }
            } else {
                return null;
            }
            return new Object[]{resource,new Long(contentLength)};
        } catch (IOException e) {
            return null;
        }
    }
    
    public plasmaHTCache.Entry loadResourceFromWeb(
            URL url, 
            int socketTimeout,
            boolean keepInMemory,
            boolean forText
    ) throws plasmaCrawlerException {
        
        plasmaHTCache.Entry result = this.sb.cacheLoader.loadSync(
                url,                         // the url
                "",                          // name of the url, from anchor tag <a>name</a>
                null,                        // referer
                yacyCore.seedDB.mySeed.hash, // initiator
                0,                           // depth
                (forText) ? sb.defaultTextSnippetProfile : sb.defaultMediaSnippetProfile, // crawl profile
                socketTimeout,
                keepInMemory
        );
        
        return result;
    }
    
    public void fetch(plasmaSearchResult acc, Set queryhashes, String urlmask, int fetchcount, long maxTime) {
        // fetch snippets
        int i = 0;
        indexURLEntry urlentry;
        String urlstring;
        long limitTime = (maxTime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxTime;
        while ((acc.hasMoreElements()) && (i < fetchcount) && (System.currentTimeMillis() < limitTime)) {
            urlentry = acc.nextElement();
            indexURLEntry.Components comp = urlentry.comp();
            if (comp.url().getHost().endsWith(".yacyh")) continue;
            urlstring = comp.url().toNormalform();
            if ((urlstring.matches(urlmask)) &&
                (!(existsInCache(comp.url(), queryhashes)))) {
                new Fetcher(comp.url(), queryhashes, urlentry.flags().get(plasmaCondenser.flag_cat_indexof), (int) maxTime).start();
                i++;
            }
        }
    }
        
    public class Fetcher extends Thread {
        URL url;
        Set queryhashes;
        int timeout;
        boolean pre;
        public Fetcher(URL url, Set queryhashes, boolean pre, int timeout) {
            if (url.getHost().endsWith(".yacyh")) return;
            this.url = url;
            this.queryhashes = queryhashes;
            this.timeout = timeout;
            this.pre = pre;
        }
        public void run() {
            log.logFine("snippetFetcher: try to get URL " + url);
            plasmaSnippetCache.TextSnippet snippet = retrieveTextSnippet(url, queryhashes, true, pre, 260, timeout);
            if (snippet.line == null)
                log.logFine("snippetFetcher: cannot get URL " + url + ". error(" + snippet.source + "): " + snippet.error);
            else
                log.logFine("snippetFetcher: got URL " + url + ", the snippet is '" + snippet.line + "', source=" + snippet.source);
        }
    }
    
}