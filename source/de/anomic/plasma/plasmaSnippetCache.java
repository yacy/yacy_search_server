// plasmaSnippetCache.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma;

import java.io.ByteArrayInputStream;
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
import de.anomic.http.HttpClient;
import de.anomic.http.httpResponseHeader;
import de.anomic.index.indexDocumentMetadata;
import de.anomic.index.indexURLReference;
import de.anomic.index.indexWord;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMSetTools;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacyURL;

public class plasmaSnippetCache {

    private static final int maxCache = 500;
    
    public static final int SOURCE_CACHE = 0;
    public static final int SOURCE_FILE = 1;
    public static final int SOURCE_WEB = 2;
    public static final int SOURCE_METADATA = 3;
    
    public static final int ERROR_NO_HASH_GIVEN = 11;
    public static final int ERROR_SOURCE_LOADING = 12;
    public static final int ERROR_RESOURCE_LOADING = 13;
    public static final int ERROR_PARSER_FAILED = 14;
    public static final int ERROR_PARSER_NO_LINES = 15;
    public static final int ERROR_NO_MATCH = 16;
    
    private static int                   snippetsScoreCounter = 0;
    private static kelondroMScoreCluster<String> snippetsScore = null;
    private static final HashMap<String, String>       snippetsCache = new HashMap<String, String>();
    
    /**
     * a cache holding URLs to favicons specified by the page content, e.g. by using the html link-tag. e.g.
     * <pre>
     * 	 &lt;link rel="shortcut icon" type="image/x-icon" href="../src/favicon.ico"&gt;
     * </pre>
     */
    private static final HashMap<String, yacyURL> faviconCache = new HashMap<String, yacyURL>();
    private static plasmaParser          parser = null;
    private static serverLog             log = null;
    
    public static void init(
            final plasmaParser parserx,
            final serverLog logx
    ) {
        parser = parserx;
        log = logx;
        snippetsScoreCounter = 0;
        snippetsScore = new kelondroMScoreCluster<String>();
        snippetsCache.clear(); 
        faviconCache.clear();
    }
    
    public static class TextSnippet {
        private final yacyURL url;
        private String line;
        private final String error;
        private final int errorCode;
        Set<String> remaingHashes;
        private final yacyURL favicon;
        
        public TextSnippet(final yacyURL url, final String line, final int errorCode, final Set<String> remaingHashes, final String errortext) {
        	this(url,line,errorCode,remaingHashes,errortext,null);
        }
        
        public TextSnippet(final yacyURL url, final String line, final int errorCode, final Set<String> remaingHashes, final String errortext, final yacyURL favicon) {
            this.url = url;
            this.line = line;
            this.errorCode = errorCode;
            this.error = errortext;
            this.remaingHashes = remaingHashes;
            this.favicon = favicon;
        }
        public yacyURL getUrl() {
            return this.url;
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
        public int getErrorCode() {
            return errorCode;
        }
        public Set<String> getRemainingHashes() {
            return this.remaingHashes;
        }
        public String getLineMarked(final Set<String> queryHashes) {
            if (line == null) return "";
            if ((queryHashes == null) || (queryHashes.size() == 0)) return line.trim();
            if (line.endsWith(".")) line = line.substring(0, line.length() - 1);
            final Iterator<String> i = queryHashes.iterator();
            String h;
            final String[] w = line.split(" ");
            String prefix = "";
            String postfix = "";
            int len = 0;
            while (i.hasNext()) {
                h = i.next();
                for (int j = 0; j < w.length; j++) {
                    //ignore punctuation marks (contrib [MN])
                    //note to myself:
                    //For details on regex see "Mastering regular expressions" by J.E.F. Friedl
                    //especially p. 123 and p. 390/391 (in the German version of the 2nd edition)

                    prefix = "";
                    postfix = "";

                    // cut off prefix if it contains of non-characters or non-numbers
                    while(w[j].matches("\\A[^\\p{L}\\p{N}].+")) {
                        prefix = prefix + w[j].substring(0,1);
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
                                if (indexWord.word2hash(temp).equals(h)) temp = "<b>" + temp + "</b>";
                                out = out + temp + w[j].substring(k,k+1);
                                temp = "";
                            }
                            //last character
                            else if(k == (w[j].length()-1)) {
                                temp = temp + w[j].substring(k,k+1);
                                if (indexWord.word2hash(temp).equals(h)) temp = "<b>" + temp + "</b>";
                                out = out + temp;
                                temp = "";
                            }
                            else temp = temp + w[j].substring(k,k+1);
                        }
                        w[j] = out;
                    }

                    //end contrib [MN]
                    else if (indexWord.word2hash(w[j]).equals(h)) w[j] = "<b>" + w[j] + "</b>";

                    w[j] = prefix + w[j] + postfix;
                }
            }
            final StringBuffer l = new StringBuffer(line.length() + queryHashes.size() * 8);
            for (int j = 0; j < w.length; j++) {
                l.append(w[j]);
                l.append(' ');
            }
            return l.toString().trim();
        }
        
        public yacyURL getFavicon() {
        	return this.favicon;
        }
    }
    
    public static class MediaSnippet {
        public int type;
        public yacyURL href, source;
        public String name, attr;
        public int ranking;
        public MediaSnippet(final int type, final yacyURL href, final String name, final String attr, final int ranking, final yacyURL source) {
            this.type = type;
            this.href = href;
            this.source = source; // the web page where the media resource appeared
            this.name = name;
            this.attr = attr;
            this.ranking = ranking; // the smaller the better! small values should be shown first
            if ((this.name == null) || (this.name.length() == 0)) this.name = "_";
            if ((this.attr == null) || (this.attr.length() == 0)) this.attr = "_";
        }
        public int hashCode() {
            return href.hashCode();
        }
    }
    
    public static boolean existsInCache(final yacyURL url, final Set<String> queryhashes) {
        final String hashes = yacySearch.set2string(queryhashes);
        return retrieveFromCache(hashes, url.hash()) != null;
    }
    
    @SuppressWarnings("unchecked")
    public static TextSnippet retrieveTextSnippet(final indexURLReference.Components comp, final Set<String> queryhashes, final boolean fetchOnline, final boolean pre, final int snippetMaxLength, final int timeout, final int maxDocLen, final boolean reindexing) {
        // heise = "0OQUNU3JSs05"
        final yacyURL url = comp.url();
        if (queryhashes.size() == 0) {
            //System.out.println("found no queryhashes for URL retrieve " + url);
            return new TextSnippet(url, null, ERROR_NO_HASH_GIVEN, queryhashes, "no query hashes given");
        }
        
        // try to get snippet from snippetCache
        int source = SOURCE_CACHE;
        final String wordhashes = yacySearch.set2string(queryhashes);
        String line = retrieveFromCache(wordhashes, url.hash());
        if (line != null) {
            // found the snippet
            return new TextSnippet(url, line, source, null, null, faviconCache.get(url.hash()));
        }
        
        /* ===========================================================================
         * LOADING RESOURCE DATA
         * =========================================================================== */
        // if the snippet is not in the cache, we can try to get it from the htcache
        long resContentLength = 0;
        InputStream resContent = null;
        httpResponseHeader responseHeader = null;
        try {
            // trying to load the resource from the cache
            resContent = plasmaHTCache.getResourceContentStream(url);
            responseHeader = plasmaHTCache.loadResponseHeader(url);
            if (resContent != null) {
                // if the content was found
                resContentLength = plasmaHTCache.getResourceContentLength(url);
                if ((resContentLength > maxDocLen) && (!fetchOnline)) {
                    // content may be too large to be parsed here. To be fast, we omit calculation of snippet here
                    return new TextSnippet(url, null, ERROR_SOURCE_LOADING, queryhashes, "resource available, but too large: " + resContentLength + " bytes");
                }
            } else if (containsAllHashes(comp.dc_title(), queryhashes)) {
                // try to create the snippet from information given in the url itself
                return new TextSnippet(url, (comp.dc_subject().length() > 0) ? comp.dc_creator() : comp.dc_subject(), SOURCE_METADATA, null, null, faviconCache.get(url.hash()));
            } else if (containsAllHashes(comp.dc_creator(), queryhashes)) {
                // try to create the snippet from information given in the creator metadata
                return new TextSnippet(url, comp.dc_creator(), SOURCE_METADATA, null, null, faviconCache.get(url.hash()));
            } else if (containsAllHashes(comp.dc_subject(), queryhashes)) {
                // try to create the snippet from information given in the subject metadata
                return new TextSnippet(url, (comp.dc_creator().length() > 0) ? comp.dc_creator() : comp.dc_subject(), SOURCE_METADATA, null, null, faviconCache.get(url.hash()));
            } else if (containsAllHashes(comp.url().toNormalform(true, true), queryhashes)) {
                // try to create the snippet from information given in the subject metadata
                return new TextSnippet(url, (comp.dc_creator().length() > 0) ? comp.dc_creator() : comp.dc_subject(), SOURCE_METADATA, null, null, faviconCache.get(url.hash()));
            } else if (fetchOnline) {
                // if not found try to download it
                
                // download resource using the crawler and keep resource in memory if possible
                final indexDocumentMetadata entry = plasmaSwitchboard.getSwitchboard().crawlQueues.loadResourceFromWeb(url, timeout, true, true, reindexing);
                
                // getting resource metadata (e.g. the http headers for http resources)
                if (entry != null) {
                    // place entry on crawl queue
                    plasmaHTCache.push(entry);
                    
                    // read resource body (if it is there)
                    final byte []resourceArray = entry.cacheArray();
                    if (resourceArray != null) {
                        resContent = new ByteArrayInputStream(resourceArray);
                        resContentLength = resourceArray.length;
                    } else {
                        resContent = plasmaHTCache.getResourceContentStream(url); 
                        resContentLength = plasmaHTCache.getResourceContentLength(url);
                    }
                }
                
                // if it is still not available, report an error
                if (resContent == null) return new TextSnippet(url, null, ERROR_RESOURCE_LOADING, queryhashes, "error loading resource, plasmaHTCache.Entry cache is NULL");                
                
                source = SOURCE_WEB;
            } else {
                return new TextSnippet(url, null, ERROR_SOURCE_LOADING, queryhashes, "no resource available");
            }
        } catch (final Exception e) {
            e.printStackTrace();
            return new TextSnippet(url, null, ERROR_SOURCE_LOADING, queryhashes, "error loading resource: " + e.getMessage());
        } 
        
        /* ===========================================================================
         * PARSING RESOURCE
         * =========================================================================== */
        plasmaParserDocument document = null;
        try {
             document = parseDocument(url, resContentLength, resContent, responseHeader);
        } catch (final ParserException e) {
            return new TextSnippet(url, null, ERROR_PARSER_FAILED, queryhashes, e.getMessage()); // cannot be parsed
        } finally {
            try { resContent.close(); } catch (final Exception e) {/* ignore this */}
        }
        if (document == null) return new TextSnippet(url, null, ERROR_PARSER_FAILED, queryhashes, "parser error/failed"); // cannot be parsed
        
        
        /* ===========================================================================
         * COMPUTE SNIPPET
         * =========================================================================== */    
        final yacyURL resFavicon = document.getFavicon();
        if (resFavicon != null) faviconCache.put(url.hash(), resFavicon);
        // we have found a parseable non-empty file: use the lines

        // compute snippet from text
        final Iterator<StringBuffer> sentences = document.getSentences(pre);
        if (sentences == null) return new TextSnippet(url, null, ERROR_PARSER_NO_LINES, queryhashes, "parser returned no sentences",resFavicon);
        final Object[] tsr = computeTextSnippet(sentences, queryhashes, snippetMaxLength);
        final String textline = (tsr == null) ? null : (String) tsr[0];
        final Set<String> remainingHashes = (tsr == null) ? queryhashes : (Set<String>) tsr[1];
        
        // compute snippet from media
        //String audioline = computeMediaSnippet(document.getAudiolinks(), queryhashes);
        //String videoline = computeMediaSnippet(document.getVideolinks(), queryhashes);
        //String appline = computeMediaSnippet(document.getApplinks(), queryhashes);
        //String hrefline = computeMediaSnippet(document.getAnchors(), queryhashes);
        //String imageline = computeMediaSnippet(document.getAudiolinks(), queryhashes);
        
        line = "";
        //if (audioline != null) line += (line.length() == 0) ? audioline : "<br />" + audioline;
        //if (videoline != null) line += (line.length() == 0) ? videoline : "<br />" + videoline;
        //if (appline   != null) line += (line.length() == 0) ? appline   : "<br />" + appline;
        //if (hrefline  != null) line += (line.length() == 0) ? hrefline  : "<br />" + hrefline;
        if (textline  != null) line += (line.length() == 0) ? textline  : "<br />" + textline;
        
        if ((line == null) || (remainingHashes.size() > 0)) return new TextSnippet(url, null, ERROR_NO_MATCH, remainingHashes, "no matching snippet found",resFavicon);
        if (line.length() > snippetMaxLength) line = line.substring(0, snippetMaxLength);

        // finally store this snippet in our own cache
        storeToCache(wordhashes, url.hash(), line);
        
        document.close();
        return new TextSnippet(url, line, source, null, null, resFavicon);
    }

    /**
     * Tries to load and parse a resource specified by it's URL.
     * If the resource is not stored in cache and if fetchOnline is set the
     * this function tries to download the resource from web.
     * 
     * @param url the URL of the resource
     * @param fetchOnline specifies if the resource should be loaded from web if it'as not available in the cache
     * @param timeout 
     * @param forText 
     * @param global the domain of the search. If global == true then the content is re-indexed
     * @return the parsed document as {@link plasmaParserDocument}
     */
    public static plasmaParserDocument retrieveDocument(final yacyURL url, final boolean fetchOnline, final int timeout, final boolean forText, final boolean global) {

        // load resource
        long resContentLength = 0;
        InputStream resContent = null;
        httpResponseHeader responseHeader = null;
        try {
            // trying to load the resource from the cache
            resContent = plasmaHTCache.getResourceContentStream(url);
            responseHeader = plasmaHTCache.loadResponseHeader(url);
            if (resContent != null) {
                // if the content was found
                resContentLength = plasmaHTCache.getResourceContentLength(url);
            } else if (fetchOnline) {
                // if not found try to download it
                
                // download resource using the crawler and keep resource in memory if possible
                final indexDocumentMetadata entry = plasmaSwitchboard.getSwitchboard().crawlQueues.loadResourceFromWeb(url, timeout, true, forText, global);
                
                // getting resource metadata (e.g. the http headers for http resources)
                if (entry != null) {

                    // read resource body (if it is there)
                    final byte[] resourceArray = entry.cacheArray();
                    if (resourceArray != null) {
                        resContent = new ByteArrayInputStream(resourceArray);
                        resContentLength = resourceArray.length;
                    } else {
                        resContent = plasmaHTCache.getResourceContentStream(url); 
                        resContentLength = plasmaHTCache.getResourceContentLength(url);
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
        } catch (final Exception e) {
            serverLog.logFine("snippet fetch", "error loading resource: " + e.getMessage() + " for url " + url);
            return null;
        } 

        // parse resource
        plasmaParserDocument document = null;
        try {
            document = parseDocument(url, resContentLength, resContent, responseHeader);            
        } catch (final ParserException e) {
            serverLog.logFine("snippet fetch", "parser error " + e.getMessage() + " for url " + url);
            return null;
        } finally {
            try { resContent.close(); } catch (final Exception e) {}
        }
        return document;
    }

    
    public static void storeToCache(final String wordhashes, final String urlhash, final String snippet) {
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
            snippetsScore = new kelondroMScoreCluster<String>();
            snippetsCache.clear();
        }
        
        // flush cache if cache is full
        while (snippetsCache.size() > maxCache) {
            key = snippetsScore.getMinObject();
            snippetsScore.deleteScore(key);
            snippetsCache.remove(key);
        }
    }
    
    private static String retrieveFromCache(final String wordhashes, final String urlhash) {
        // generate key
        final String key = urlhash + wordhashes;
        return snippetsCache.get(key);
    }
    
    /*
    private static String computeMediaSnippet(Map<yacyURL, String> media, Set<String> queryhashes) {
        Iterator<Map.Entry<yacyURL, String>> i = media.entrySet().iterator();
        Map.Entry<yacyURL, String> entry;
        yacyURL url;
        String desc;
        Set<String> s;
        String result = "";
        while (i.hasNext()) {
            entry = i.next();
            url = entry.getKey();
            desc = entry.getValue();
            s = removeAppearanceHashes(url.toNormalform(false, false), queryhashes);
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
    */
    
    @SuppressWarnings("unchecked")
    private static Object[] /*{String - the snippet, Set - remaining hashes}*/
            computeTextSnippet(final Iterator<StringBuffer> sentences, final Set<String> queryhashes, int maxLength) {
        try {
            if (sentences == null) return null;
            if ((queryhashes == null) || (queryhashes.size() == 0)) return null;
            Iterator<String> j;
            HashMap<String, Integer> hs;
            StringBuffer sentence;
            final TreeMap<Integer, StringBuffer> os = new TreeMap<Integer, StringBuffer>();
            int uniqCounter = 9999;
            int score;
            while (sentences.hasNext()) {
                sentence = sentences.next();
                hs = hashSentence(sentence.toString());
                j = queryhashes.iterator();
                score = 0;
                while (j.hasNext()) {if (hs.containsKey(j.next())) score++;}
                if (score > 0) {
                    os.put(Integer.valueOf(1000000 * score - sentence.length() * 10000 + uniqCounter--), sentence);
                }
            }
            
            String result;
            Set<String> remaininghashes;
            while (os.size() > 0) {
                sentence = os.remove(os.lastKey()); // sentence with the biggest score
                Object[] tsr = computeTextSnippet(sentence.toString(), queryhashes, maxLength);
                if (tsr == null) continue;
                result = (String) tsr[0];
                if ((result != null) && (result.length() > 0)) {
                    remaininghashes = (Set<String>) tsr[1];
                    if (remaininghashes.size() == 0) {
                        // we have found the snippet
                        return new Object[]{result, remaininghashes};
                    } else if (remaininghashes.size() < queryhashes.size()) {
                        // the result has not all words in it.
                        // find another sentence that represents the missing other words
                        // and find recursively more sentences
                        maxLength = maxLength - result.length();
                        if (maxLength < 20) maxLength = 20;
                        tsr = computeTextSnippet(os.values().iterator(), remaininghashes, maxLength);
                        if (tsr == null) return null;
                        final String nextSnippet = (String) tsr[0];
                        if (nextSnippet == null) return tsr;
                        return new Object[]{result + (" / " + nextSnippet), tsr[1]};
                    } else {
                        // error
                        //assert remaininghashes.size() < queryhashes.size() : "remaininghashes.size() = " + remaininghashes.size() + ", queryhashes.size() = " + queryhashes.size() + ", sentence = '" + sentence + "', result = '" + result + "'";
                        continue;
                    }
                }
            }
            return null;
        } catch (final IndexOutOfBoundsException e) {
            log.logSevere("computeSnippet: error with string generation", e);
            return new Object[]{null, queryhashes};
        }
    }
    
    private static Object[] /*{String - the snippet, Set - remaining hashes}*/
            computeTextSnippet(String sentence, final Set<String> queryhashes, final int maxLength) {
        try {
            if (sentence == null) return null;
            if ((queryhashes == null) || (queryhashes.size() == 0)) return null;
            String hash;
            
            // find all hashes that appear in the sentence
            final HashMap<String, Integer> hs = hashSentence(sentence);
            final Iterator<String> j = queryhashes.iterator();
            Integer pos;
            int p, minpos = sentence.length(), maxpos = -1;
            final HashSet<String> remainingHashes = new HashSet<String>();
            while (j.hasNext()) {
                hash = j.next();
                pos = hs.get(hash);
                if (pos == null) {
                    remainingHashes.add(hash);
                } else {
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
                final int lenb = sentence.length();
                sentence = sentence.substring(0, (minpos + 20 > sentence.length()) ? sentence.length() : minpos + 20).trim() +
                " [..] " +
                sentence.substring((maxpos + 26 > sentence.length()) ? sentence.length() : maxpos + 26).trim();
                maxpos = maxpos + lenb - sentence.length() + 6;
            }
            if (maxpos > maxLength) {
                // the string is too long, even if we cut it at the end
                // so cut it here at both ends at once
                assert maxpos >= minpos;
                final int newlen = Math.max(10, maxpos - minpos + 10);
                final int around = (maxLength - newlen) / 2;
                assert minpos - around < sentence.length() : "maxpos = " + maxpos + ", minpos = " + minpos + ", around = " + around + ", sentence.length() = " + sentence.length();
                assert ((maxpos + around) <= sentence.length()) && ((maxpos + around) <= sentence.length()) : "maxpos = " + maxpos + ", minpos = " + minpos + ", around = " + around + ", sentence.length() = " + sentence.length();
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
            return new Object[] {sentence, remainingHashes};
        } catch (final IndexOutOfBoundsException e) {
            log.logSevere("computeSnippet: error with string generation", e);
            return null;
        }
    }
    
    public static ArrayList<MediaSnippet> retrieveMediaSnippets(final yacyURL url, final Set<String> queryhashes, final int mediatype, final boolean fetchOnline, final int timeout, final boolean reindexing) {
        if (queryhashes.size() == 0) {
            serverLog.logFine("snippet fetch", "no query hashes given for url " + url);
            return new ArrayList<MediaSnippet>();
        }
        
        final plasmaParserDocument document = retrieveDocument(url, fetchOnline, timeout, false, reindexing);
        final ArrayList<MediaSnippet> a = new ArrayList<MediaSnippet>();
        if (document != null) {
            if ((mediatype == plasmaSearchQuery.CONTENTDOM_ALL) || (mediatype == plasmaSearchQuery.CONTENTDOM_AUDIO)) a.addAll(computeMediaSnippets(document, queryhashes, plasmaSearchQuery.CONTENTDOM_AUDIO));
            if ((mediatype == plasmaSearchQuery.CONTENTDOM_ALL) || (mediatype == plasmaSearchQuery.CONTENTDOM_VIDEO)) a.addAll(computeMediaSnippets(document, queryhashes, plasmaSearchQuery.CONTENTDOM_VIDEO));
            if ((mediatype == plasmaSearchQuery.CONTENTDOM_ALL) || (mediatype == plasmaSearchQuery.CONTENTDOM_APP)) a.addAll(computeMediaSnippets(document, queryhashes, plasmaSearchQuery.CONTENTDOM_APP));
            if ((mediatype == plasmaSearchQuery.CONTENTDOM_ALL) || (mediatype == plasmaSearchQuery.CONTENTDOM_IMAGE)) a.addAll(computeImageSnippets(document, queryhashes));
        }
        return a;
    }
    
    public static ArrayList<MediaSnippet> computeMediaSnippets(final plasmaParserDocument document, final Set<String> queryhashes, final int mediatype) {
        
        if (document == null) return new ArrayList<MediaSnippet>();
        Map<yacyURL, String> media = null;
        if (mediatype == plasmaSearchQuery.CONTENTDOM_AUDIO) media = document.getAudiolinks();
        else if (mediatype == plasmaSearchQuery.CONTENTDOM_VIDEO) media = document.getVideolinks();
        else if (mediatype == plasmaSearchQuery.CONTENTDOM_APP) media = document.getApplinks();
        if (media == null) return null;
        
        final Iterator<Map.Entry<yacyURL, String>> i = media.entrySet().iterator();
        Map.Entry<yacyURL, String> entry;
        yacyURL url;
        String desc;
        Set<String> s;
        final ArrayList<MediaSnippet> result = new ArrayList<MediaSnippet>();
        while (i.hasNext()) {
            entry = i.next();
            url = entry.getKey();
            desc = entry.getValue();
            s = removeAppearanceHashes(url.toNormalform(false, false), queryhashes);
            if (s.size() == 0) {
                result.add(new MediaSnippet(mediatype, url, desc, null, 0, document.dc_source()));
                continue;
            }
            s = removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                result.add(new MediaSnippet(mediatype, url, desc, null, 0, document.dc_source()));
                continue;
            }
        }
        return result;
    }
    
    public static ArrayList<MediaSnippet> computeImageSnippets(final plasmaParserDocument document, final Set<String> queryhashes) {
        
        final TreeSet<htmlFilterImageEntry> images = new TreeSet<htmlFilterImageEntry>();
        images.addAll(document.getImages().values()); // iterates images in descending size order!
        // a measurement for the size of the images can be retrieved using the htmlFilterImageEntry.hashCode()
        
        final Iterator<htmlFilterImageEntry> i = images.iterator();
        htmlFilterImageEntry ientry;
        yacyURL url;
        String desc;
        Set<String> s;
        final ArrayList<MediaSnippet> result = new ArrayList<MediaSnippet>();
        while (i.hasNext()) {
            ientry = i.next();
            url = ientry.url();
            desc = ientry.alt();
            s = removeAppearanceHashes(url.toNormalform(false, false), queryhashes);
            if (s.size() == 0) {
                final int ranking = ientry.hashCode();
                result.add(new MediaSnippet(plasmaSearchQuery.CONTENTDOM_IMAGE, url, desc, ientry.width() + " x " + ientry.height(), ranking, document.dc_source()));
                continue;
            }
            s = removeAppearanceHashes(desc, s);
            if (s.size() == 0) {
                final int ranking = ientry.hashCode();
                result.add(new MediaSnippet(plasmaSearchQuery.CONTENTDOM_IMAGE, url, desc, ientry.width() + " x " + ientry.height(), ranking, document.dc_source()));
                continue;
            }
        }
        return result;
    }
    
    private static Set<String> removeAppearanceHashes(final String sentence, final Set<String> queryhashes) {
        // remove all hashes that appear in the sentence
        if (sentence == null) return queryhashes;
        final HashMap<String, Integer> hs = hashSentence(sentence);
        final Iterator<String> j = queryhashes.iterator();
        String hash;
        Integer pos;
        final Set<String> remaininghashes = new HashSet<String>();
        while (j.hasNext()) {
            hash = j.next();
            pos = hs.get(hash);
            if (pos == null) {
                remaininghashes.add(hash);
            }
        }
        return remaininghashes;
    }
    
    private static HashMap<String, Integer> hashSentence(final String sentence) {
        // generates a word-wordPos mapping
        final HashMap<String, Integer> map = new HashMap<String, Integer>();
        final Enumeration<StringBuffer> words = plasmaCondenser.wordTokenizer(sentence, "UTF-8");
        int pos = 0;
        StringBuffer word;
        String hash;
        while (words.hasMoreElements()) {
            word = words.nextElement();
            hash = indexWord.word2hash(new String(word));
            if (!map.containsKey(hash)) map.put(hash, Integer.valueOf(pos)); // dont overwrite old values, that leads to too far word distances
            pos += word.length() + 1;
        }
        return map;
    }
    
    private static boolean containsAllHashes(final String sentence, final Set<String> queryhashes) {
        final HashMap<String, Integer> m = hashSentence(sentence);
        final Iterator<String> i = queryhashes.iterator();
        while (i.hasNext()) {
            if (!(m.containsKey(i.next()))) return false;
        }
        return true;
    }
    
    public static plasmaParserDocument parseDocument(final yacyURL url, final long contentLength, final InputStream resourceStream) throws ParserException {
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
    public static plasmaParserDocument parseDocument(final yacyURL url, final long contentLength, final InputStream resourceStream, httpResponseHeader responseHeader) throws ParserException {
        try {
            if (resourceStream == null) return null;

            // STEP 1: if no resource metadata is available, try to load it from cache 
            if (responseHeader == null) {
                // try to get the header from the htcache directory
                try {                    
                    responseHeader = plasmaHTCache.loadResponseHeader(url);
                } catch (final Exception e) {
                    // ignore this. resource info loading failed
                }   
            }
            
            // STEP 2: if the metadata is still null try to download it from web
            if ((responseHeader == null) && (url.getProtocol().startsWith("http"))) {
                // TODO: we need a better solution here
                // e.g. encapsulate this in the crawlLoader class
                
                // getting URL mimeType
                try {
                    responseHeader = HttpClient.whead(url.toString());
                } catch (final Exception e) {
                    // ingore this. http header download failed
                } 
            }

            // STEP 3: if the metadata is still null try to guess the mimeType of the resource
            if (responseHeader == null) {
                final String filename = plasmaHTCache.getCachePath(url).getName();
                final int p = filename.lastIndexOf('.');
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

                    return parser.parseSource(url, supposedMime, null, contentLength, resourceStream);
                }
                return null;
            }            
            if (plasmaParser.supportedMimeTypesContains(responseHeader.mime())) {
                return parser.parseSource(url, responseHeader.mime(), responseHeader.getCharacterEncoding(), contentLength, resourceStream);
            }
            return null;
        } catch (final InterruptedException e) {
            // interruption of thread detected
            return null;
        }
    }
    
    /**
     * 
     * @param url
     * @param fetchOnline
     * @param socketTimeout
     * @param forText 
     * @return an Object array containing
     * <table>
     * <tr><td>[0]</td><td>the content as {@link InputStream}</td></tr>
     * <tr><td>[1]</td><td>the content-length as {@link Integer}</td></tr>
     * </table>
     */
    public static Object[] getResource(final yacyURL url, final boolean fetchOnline, final int socketTimeout, final boolean forText, final boolean reindexing) {
        // load the url as resource from the web
            long contentLength = -1;
            
            // trying to load the resource body from cache
            InputStream resource = plasmaHTCache.getResourceContentStream(url);
            if (resource != null) {
                contentLength = plasmaHTCache.getResourceContentLength(url);
            } else if (fetchOnline) {
                // if the content is not available in cache try to download it from web
                
                // try to download the resource using a crawler
                final indexDocumentMetadata entry = plasmaSwitchboard.getSwitchboard().crawlQueues.loadResourceFromWeb(url, (socketTimeout < 0) ? -1 : socketTimeout, true, forText, reindexing);
                if (entry == null) return null; // not found in web
                
                // read resource body (if it is there)
                final byte[] resourceArray = entry.cacheArray();
            
                // in case that the resource was not in ram, read it from disk
                if (resourceArray == null) {
                    resource = plasmaHTCache.getResourceContentStream(url);   
                    contentLength = plasmaHTCache.getResourceContentLength(url); 
                } else {
                    resource = new ByteArrayInputStream(resourceArray);
                    contentLength = resourceArray.length;
                }
            } else {
                return null;
            }
            return new Object[]{resource, Long.valueOf(contentLength)};
    }
    
    public static String failConsequences(final TextSnippet snippet, final String eventID) {
        // problems with snippet fetch
        final String urlHash = snippet.getUrl().hash();
        final String querystring = kelondroMSetTools.setToString(snippet.getRemainingHashes(), ' ');
        if ((snippet.getErrorCode() == ERROR_SOURCE_LOADING) ||
            (snippet.getErrorCode() == ERROR_RESOURCE_LOADING) ||
            (snippet.getErrorCode() == ERROR_PARSER_FAILED) ||
            (snippet.getErrorCode() == ERROR_PARSER_NO_LINES)) {
            log.logInfo("error: '" + snippet.getError() + "', remove url = " + snippet.getUrl().toNormalform(false, true) + ", cause: " + snippet.getError());
            plasmaSwitchboard.getSwitchboard().webIndex.removeURL(urlHash);
            final plasmaSearchEvent event = plasmaSearchEvent.getEvent(eventID);
            assert plasmaSwitchboard.getSwitchboard() != null;
            assert plasmaSwitchboard.getSwitchboard().webIndex != null;
            assert event != null : "eventID = " + eventID;
            assert event.getQuery() != null;
            plasmaSwitchboard.getSwitchboard().webIndex.removeEntryMultiple(event.getQuery().queryHashes, urlHash);
            event.remove(urlHash);
        }
        if (snippet.getErrorCode() == ERROR_NO_MATCH) {
            log.logInfo("error: '" + snippet.getError() + "', remove words '" + querystring + "' for url = " + snippet.getUrl().toNormalform(false, true) + ", cause: " + snippet.getError());
            plasmaSwitchboard.getSwitchboard().webIndex.removeEntryMultiple(snippet.remaingHashes, urlHash);
            plasmaSearchEvent.getEvent(eventID).remove(urlHash);
        }
        return snippet.getError();
    }
    
}