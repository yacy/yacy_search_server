// TextSnippet.java
// -----------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.document.ParserException;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.ARC;
import net.yacy.kelondro.index.ConcurrentARC;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.util.ByteArray;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.crawler.retrieval.Response;
import de.anomic.http.client.Cache;
import de.anomic.http.server.ResponseHeader;
import de.anomic.yacy.yacySearch;

public class TextSnippet implements Comparable<TextSnippet>, Comparator<TextSnippet> {

    private static final int maxCache = 1000;

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
    
    private static final ARC<String, String> snippetsCache = new ConcurrentARC<String, String>(maxCache, Math.max(10, Runtime.getRuntime().availableProcessors()));
    private static final ARC<String, DigestURI> faviconCache = new ConcurrentARC<String, DigestURI>(maxCache, Math.max(10, Runtime.getRuntime().availableProcessors()));
    
    private final DigestURI url;
    private String line;
    private final String error;
    private final int errorCode;
    private HandleSet remaingHashes;
    private final DigestURI favicon;
    
    public static boolean existsInCache(final DigestURI url, final HandleSet queryhashes) {
        final String hashes = yacySearch.set2string(queryhashes);
        return retrieveFromCache(hashes, new String(url.hash())) != null;
    }

    public static void storeToCache(final String wordhashes, final String urlhash, final String snippet) {
        // generate key
        String key = urlhash + wordhashes;

        // do nothing if snippet is known
        if (snippetsCache.containsKey(key)) return;

        // learn new snippet
        snippetsCache.put(key, snippet);
    }
    
    public static String retrieveFromCache(final String wordhashes, final String urlhash) {
        // generate key
        final String key = urlhash + wordhashes;
        return snippetsCache.get(key);
    }
    
    /**
     * removed all word hashes that can be computed as tokens from a given sentence from a given hash set
     * @param sentence
     * @param queryhashes
     * @return the given hash set minus the hashes from the tokenization of the given sentence
     */
    public static HandleSet removeAppearanceHashes(final String sentence, final HandleSet queryhashes) {
        // remove all hashes that appear in the sentence
        if (sentence == null) return queryhashes;
        final TreeMap<byte[], Integer> hs = Condenser.hashSentence(sentence);
        final Iterator<byte[]> j = queryhashes.iterator();
        byte[] hash;
        Integer pos;
        final HandleSet remaininghashes = new HandleSet(queryhashes.row().primaryKeyLength, queryhashes.comparator(), queryhashes.size());
        while (j.hasNext()) {
            hash = j.next();
            pos = hs.get(hash);
            if (pos == null) {
                try {
                    remaininghashes.put(hash);
                } catch (RowSpaceExceededException e) {
                    Log.logException(e);
                }
            }
        }
        return remaininghashes;
    }

    /**
     * <code>\\A[^\\p{L}\\p{N}].+</code>
     */
    private final static Pattern p1 = Pattern.compile("\\A[^\\p{L}\\p{N}].+");
    /**
     * <code>.+[^\\p{L}\\p{N}]\\Z</code>
     */
    private final static Pattern p2 = Pattern.compile(".+[^\\p{L}\\p{N}]\\Z");
    /**
     * <code>\\A[\\p{L}\\p{N}]+[^\\p{L}\\p{N}].+\\Z</code>
     */
    private final static Pattern p3 = Pattern.compile("\\A[\\p{L}\\p{N}]+[^\\p{L}\\p{N}].+\\Z");
    /**
     * <code>[^\\p{L}\\p{N}]</code>
     */
    private final static Pattern p4 = Pattern.compile("[^\\p{L}\\p{N}]");
    /**
     * <code>(.*?)(\\&lt;b\\&gt;.+?\\&lt;/b\\&gt;)(.*)</code>
     */
    private final static Pattern p01 = Pattern.compile("(.*?)(\\<b\\>.+?\\</b\\>)(.*)"); // marked words are in <b>-tags
    
    public TextSnippet(final DigestURI url, final String line, final int errorCode, final HandleSet remaingHashes, final String errortext) {
        this(url, line, errorCode, remaingHashes, errortext, null);
    }
    
    public TextSnippet(final DigestURI url, final String line, final int errorCode, final HandleSet remaingHashes, final String errortext, final DigestURI favicon) {
        this.url = url;
        this.line = line;
        this.errorCode = errorCode;
        this.error = errortext;
        this.remaingHashes = remaingHashes;
        this.favicon = favicon;
    }
    public DigestURI getUrl() {
        return this.url;
    }
    public DigestURI getFavicon() {
        return this.favicon;
    }
    public boolean exists() {
        return line != null;
    }
    public int compareTo(TextSnippet o) {
        return Base64Order.enhancedCoder.compare(this.url.hash(), o.url.hash());
    }
    public int compare(TextSnippet o1, TextSnippet o2) {
        return o1.compareTo(o2);
    }
    public int hashCode() {
        return ByteArray.hashCode(this.url.hash());
    }
    
    @Override
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
    public HandleSet getRemainingHashes() {
        return this.remaingHashes;
    }
    public String getLineMarked(final HandleSet queryHashes) {
        if (line == null) return "";
        if (queryHashes == null || queryHashes.isEmpty()) return line.trim();
        if (line.endsWith(".")) line = line.substring(0, line.length() - 1);
        final Iterator<byte[]> i = queryHashes.iterator();
        byte[] h;
        final String[] w = line.split(" ");
        while (i.hasNext()) {
            h = i.next();
            for (int j = 0; j < w.length; j++) {
                final ArrayList<String> al = markedWordArrayList(w[j]); // mark special character separated words correctly if more than 1 word has to be marked
                w[j] = "";
                for (int k = 0; k < al.size(); k++) {
                    if(k % 2 == 0){ // word has not been marked
                        w[j] += getWordMarked(al.get(k), h);
                    } else { // word has been marked, do not encode again
                        w[j] += al.get(k);
                    }
                }
            }
        }
        final StringBuilder l = new StringBuilder(line.length() + queryHashes.size() * 8);
        for (int j = 0; j < w.length; j++) {
            l.append(w[j]);
            l.append(' ');
        }
        return l.toString().trim();
    }

    /**
     * mark words with &lt;b&gt;-tags
     * @param word the word to mark
     * @param h the hash of the word to mark
     * @return the marked word if hash matches, else the unmarked word
     * @see #getLineMarked(Set)
     */
    private static String getWordMarked(String word, byte[] h){
        //ignore punctuation marks (contrib [MN])
        //note to myself:
        //For details on regex see "Mastering regular expressions" by J.E.F. Friedl
        //especially p. 123 and p. 390/391 (in the German version of the 2nd edition)

        String prefix = "";
        String postfix = "";
        int len = 0;

        // cut off prefix if it contains of non-characters or non-numbers
        while(p1.matcher(word).find()) {
            prefix = prefix + word.substring(0,1);
            word = word.substring(1);
        }

        // cut off postfix if it contains of non-characters or non-numbers
        while(p2.matcher(word).find()) {
            len = word.length();
            postfix = word.substring(len-1,len) + postfix;
            word = word.substring(0,len-1);
        }

        //special treatment if there is a special character in the word
        if(p3.matcher(word).find()) {
            String out = "";
            String temp = "";
            for(int k=0; k < word.length(); k++) {
                //is character a special character?
                if(p4.matcher(word.substring(k,k+1)).find()) {
                    if (new String(Word.word2hash(temp)).equals(new String(h))) temp = "<b>" + CharacterCoding.unicode2html(temp, false) + "</b>";
                    out = out + temp + CharacterCoding.unicode2html(word.substring(k,k+1), false);
                    temp = "";
                }
                //last character
                else if(k == (word.length()-1)) {
                    temp = temp + word.substring(k,k+1);
                    if (new String(Word.word2hash(temp)).equals(new String(h))) temp = "<b>" + CharacterCoding.unicode2html(temp, false) + "</b>";
                    out = out + temp;
                    temp = "";
                }
                else temp = temp + word.substring(k,k+1);
            }
            word = out;
        }

        //end contrib [MN]
        else if (new String(Word.word2hash(word)).equals(new String(h))) word = "<b>" + CharacterCoding.unicode2html(word, false) + "</b>";

        word = CharacterCoding.unicode2html(prefix, false)
            + word
            + CharacterCoding.unicode2html(postfix, false);
        return word;
    }
    
    /**
     * words that already has been marked has index <code>(i % 2 == 1)</code>
     * words that has not yet been marked has index <code>(i % 2 == 0)</code>
     * @param string the String to be processed
     * @return words that already has and has not yet been marked
     * @author [DW], 08.11.2008
     */
    private static ArrayList<String> markedWordArrayList(String string){
        ArrayList<String> al = new java.util.ArrayList<String>(1);
        Matcher m = p01.matcher(string);
        while (m.find()) {
            al.add(m.group(1));
            al.add(m.group(2));
            string = m.group(3); // the postfix
            m = p01.matcher(string);
            }
        al.add(string);
        return al;
    }
    
    public static TextSnippet retrieveTextSnippet(final LoaderDispatcher loader, final URIMetadataRow.Components comp, final HandleSet queryhashes, final boolean fetchOnline, final boolean pre, final int snippetMaxLength, final int maxDocLen, final boolean reindexing) {
        // heise = "0OQUNU3JSs05"
        final DigestURI url = comp.url();
        if (queryhashes.isEmpty()) {
            //System.out.println("found no queryhashes for URL retrieve " + url);
            return new TextSnippet(url, null, ERROR_NO_HASH_GIVEN, queryhashes, "no query hashes given");
        }
        
        // try to get snippet from snippetCache
        int source = SOURCE_CACHE;
        final String wordhashes = yacySearch.set2string(queryhashes);
        String line = retrieveFromCache(wordhashes, new String(url.hash()));
        if (line != null) {
            // found the snippet
            return new TextSnippet(url, line, source, null, null, faviconCache.get(new String(url.hash())));
        }
        
        
        /* ===========================================================================
         * LOADING RESOURCE DATA
         * =========================================================================== */
        // if the snippet is not in the cache, we can try to get it from the htcache
        byte[] resContent = null;
        ResponseHeader responseHeader = null;
        try {
            // first try to get the snippet from metadata
            String loc;
            if (containsAllHashes(loc = comp.dc_title(), queryhashes)) {
                // try to create the snippet from information given in the url itself
                return new TextSnippet(url, loc, SOURCE_METADATA, null, null, faviconCache.get(new String(url.hash())));
            } else if (containsAllHashes(loc = comp.dc_creator(), queryhashes)) {
                // try to create the snippet from information given in the creator metadata
                return new TextSnippet(url, loc, SOURCE_METADATA, null, null, faviconCache.get(new String(url.hash())));
            } else if (containsAllHashes(loc = comp.dc_subject(), queryhashes)) {
                // try to create the snippet from information given in the subject metadata
                return new TextSnippet(url, loc, SOURCE_METADATA, null, null, faviconCache.get(new String(url.hash())));
            } else if (containsAllHashes(loc = comp.url().toNormalform(true, true).replace('-', ' '), queryhashes)) {
                // try to create the snippet from information given in the subject metadata
                return new TextSnippet(url, loc, SOURCE_METADATA, null, null, faviconCache.get(new String(url.hash())));
            } else {
                        
                // trying to load the resource from the cache
                resContent = Cache.getContent(url);
                responseHeader = Cache.getResponseHeader(url);
                if (resContent != null && !fetchOnline && resContent.length > maxDocLen) {
                    // content may be too large to be parsed here. To be fast, we omit calculation of snippet here
                    return new TextSnippet(url, null, ERROR_SOURCE_LOADING, queryhashes, "resource available, but too large: " + resContent.length + " bytes");
                } else if (fetchOnline) {
                    // if not found try to download it
                    
                    // download resource using the crawler and keep resource in memory if possible
                    final Response entry = loader.load(url, true, reindexing, Long.MAX_VALUE);
                    
                    // get resource metadata (e.g. the http headers for http resources)
                    if (entry != null) {
                        // place entry on indexing queue
                        Switchboard.getSwitchboard().toIndexer(entry);
                        
                        // read resource body (if it is there)
                        final byte[] resourceArray = entry.getContent();
                        if (resourceArray != null) {
                            resContent = resourceArray;
                        } else {
                            resContent = Cache.getContent(url); 
                        }
                    }
                    
                    // if it is still not available, report an error
                    if (resContent == null) return new TextSnippet(url, null, ERROR_RESOURCE_LOADING, queryhashes, "error loading resource, plasmaHTCache.Entry cache is NULL");                
                    
                    source = SOURCE_WEB;
                } else {
                    return new TextSnippet(url, null, ERROR_SOURCE_LOADING, queryhashes, "no resource available");
                }
            }
        } catch (final Exception e) {
            //Log.logException(e);
            return new TextSnippet(url, null, ERROR_SOURCE_LOADING, queryhashes, "error loading resource: " + e.getMessage());
        } 
        
        /* ===========================================================================
         * PARSING RESOURCE
         * =========================================================================== */
        Document document = null;
        try {
             document = LoaderDispatcher.parseDocument(url, resContent.length, new ByteArrayInputStream(resContent), responseHeader);
        } catch (final ParserException e) {
            return new TextSnippet(url, null, ERROR_PARSER_FAILED, queryhashes, e.getMessage()); // cannot be parsed
        } finally {
            resContent = null;
        }
        if (document == null) return new TextSnippet(url, null, ERROR_PARSER_FAILED, queryhashes, "parser error/failed"); // cannot be parsed
        
        
        /* ===========================================================================
         * COMPUTE SNIPPET
         * =========================================================================== */    
        final DigestURI resFavicon = document.getFavicon();
        if (resFavicon != null) faviconCache.put(new String(url.hash()), resFavicon);
        // we have found a parseable non-empty file: use the lines

        // compute snippet from text
        final Iterator<StringBuilder> sentences = document.getSentences(pre);
        if (sentences == null) return new TextSnippet(url, null, ERROR_PARSER_NO_LINES, queryhashes, "parser returned no sentences",resFavicon);
        final Object[] tsr = computeTextSnippet(sentences, queryhashes, snippetMaxLength);
        final String textline = (tsr == null) ? null : (String) tsr[0];
        final HandleSet remainingHashes = (tsr == null) ? queryhashes : (HandleSet) tsr[1];
        
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
        
        if (line == null || !remainingHashes.isEmpty()) return new TextSnippet(url, null, ERROR_NO_MATCH, remainingHashes, "no matching snippet found",resFavicon);
        if (line.length() > snippetMaxLength) line = line.substring(0, snippetMaxLength);

        // finally store this snippet in our own cache
        storeToCache(wordhashes, new String(url.hash()), line);
        
        document.close();
        return new TextSnippet(url, line, source, null, null, resFavicon);
    }
    
    private static boolean containsAllHashes(final String sentence, final HandleSet queryhashes) {
        final TreeMap<byte[], Integer> m = Condenser.hashSentence(sentence);
        for (byte[] b: queryhashes) {
            if (!(m.containsKey(b))) return false;
        }
        return true;
    }
    
    private static Object[] /*{String - the snippet, HandleSet - remaining hashes}*/
            computeTextSnippet(final Iterator<StringBuilder> sentences, final HandleSet queryhashes, int maxLength) {
        try {
            if (sentences == null) return null;
            if ((queryhashes == null) || (queryhashes.isEmpty())) return null;
            Iterator<byte[]> j;
            TreeMap<byte[], Integer> hs;
            StringBuilder sentence;
            final TreeMap<Integer, StringBuilder> os = new TreeMap<Integer, StringBuilder>();
            int uniqCounter = 9999;
            int score;
            while (sentences.hasNext()) {
                sentence = sentences.next();
                hs = Condenser.hashSentence(sentence.toString());
                j = queryhashes.iterator();
                score = 0;
                while (j.hasNext()) {if (hs.containsKey(j.next())) score++;}
                if (score > 0) {
                    os.put(Integer.valueOf(1000000 * score - sentence.length() * 10000 + uniqCounter--), sentence);
                }
            }
            
            String result;
            HandleSet remaininghashes;
            while (!os.isEmpty()) {
                sentence = os.remove(os.lastKey()); // sentence with the biggest score
                Object[] tsr = computeTextSnippet(sentence.toString(), queryhashes, maxLength);
                if (tsr == null) continue;
                result = (String) tsr[0];
                if ((result != null) && (result.length() > 0)) {
                    remaininghashes = (HandleSet) tsr[1];
                    if (remaininghashes.isEmpty()) {
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
            Log.logSevere("computeSnippet", "error with string generation", e);
            return new Object[]{null, queryhashes};
        }
    }
    
    private static Object[] /*{String - the snippet, HandleSet - remaining hashes}*/
            computeTextSnippet(String sentence, final HandleSet queryhashes, final int maxLength) {
        try {
            if (sentence == null) return null;
            if ((queryhashes == null) || (queryhashes.isEmpty())) return null;
            byte[] hash;
            
            // find all hashes that appear in the sentence
            final TreeMap<byte[], Integer> hs = Condenser.hashSentence(sentence);
            final Iterator<byte[]> j = queryhashes.iterator();
            Integer pos;
            int p, minpos = sentence.length(), maxpos = -1;
            final HandleSet remainingHashes = new HandleSet(queryhashes.row().primaryKeyLength, queryhashes.comparator(), 0);
            while (j.hasNext()) {
                hash = j.next();
                pos = hs.get(hash);
                if (pos == null) {
                    try {
                        remainingHashes.put(hash);
                    } catch (RowSpaceExceededException e) {
                        Log.logException(e);
                    }
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
                //assert ((maxpos + around) <= sentence.length()) && ((maxpos + around) <= sentence.length()) : "maxpos = " + maxpos + ", minpos = " + minpos + ", around = " + around + ", sentence.length() = " + sentence.length();
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
            Log.logSevere("computeSnippet", "error with string generation", e);
            return null;
        }
    }
    
}
