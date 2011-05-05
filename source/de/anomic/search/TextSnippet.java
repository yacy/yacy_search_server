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

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.SnippetExtractor;
import net.yacy.document.WordTokenizer;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.util.ByteArray;
import net.yacy.repository.LoaderDispatcher;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.retrieval.Response;
import de.anomic.yacy.yacySearch;

public class TextSnippet implements Comparable<TextSnippet>, Comparator<TextSnippet> {

    private static final int maxCache = 1000;


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

    public static class Cache {
        private final ARC<String, String> cache;
        public Cache() {
            cache = new ConcurrentARC<String, String>(maxCache, Math.max(10, Runtime.getRuntime().availableProcessors()));
        }
        public void put(final String wordhashes, final String urlhash, final String snippet) {
            // generate key
            final String key = urlhash + wordhashes;

            // do nothing if snippet is known
            if (cache.containsKey(key)) return;

            // learn new snippet
            cache.put(key, snippet);
        }
        
        public String get(final String wordhashes, final String urlhash) {
            // generate key
            final String key = urlhash + wordhashes;
            return cache.get(key);
        }
        
        public boolean contains(final String wordhashes, final String urlhash) {
            return cache.containsKey(urlhash + wordhashes);
        }
    }
    
    public static final Cache snippetsCache = new Cache();
    
    public static enum ResultClass {
        SOURCE_CACHE(false),
        SOURCE_FILE(false),
        SOURCE_WEB(false),
        SOURCE_METADATA(false),
        ERROR_NO_HASH_GIVEN(true),
        ERROR_SOURCE_LOADING(true),
        ERROR_RESOURCE_LOADING(true),
        ERROR_PARSER_FAILED(true),
        ERROR_PARSER_NO_LINES(true),
        ERROR_NO_MATCH(true);
        private final boolean fail;
        private ResultClass(final boolean fail) {
            this.fail = fail;
        }
        public boolean fail() {
            return this.fail;
        }
    }
    
    private byte[] urlhash;
    private String line;
    private String error;
    private ResultClass resultStatus;

    public TextSnippet(
            final byte[] urlhash,
            final String line,
            final ResultClass errorCode,
            final String errortext) {
        init(urlhash, line, errorCode, errortext);
    }

    public TextSnippet(
            final LoaderDispatcher loader,
            final URIMetadataRow.Components comp,
            final HandleSet queryhashes,
            final CrawlProfile.CacheStrategy cacheStrategy,
            final boolean pre,
            final int snippetMaxLength,
            final int maxDocLen,
            final boolean reindexing) {
        // heise = "0OQUNU3JSs05"
        final DigestURI url = comp.url();
        if (queryhashes.isEmpty()) {
            //System.out.println("found no queryhashes for URL retrieve " + url);
            init(url.hash(), null, ResultClass.ERROR_NO_HASH_GIVEN, "no query hashes given");
            return;
        }
        
        // try to get snippet from snippetCache
        ResultClass source = ResultClass.SOURCE_CACHE;
        final String wordhashes = yacySearch.set2string(queryhashes);
        final String urls = UTF8.String(url.hash());
        String snippetLine = snippetsCache.get(wordhashes, urls);
        if (snippetLine != null) {
            // found the snippet
            init(url.hash(), snippetLine, source, null);
            return;
        }
        
        
        /* ===========================================================================
         * LOAD RESOURCE DATA
         * =========================================================================== */
        // if the snippet is not in the cache, we can try to get it from the htcache
        final Response response;
        try {
            // first try to get the snippet from metadata
            String loc;
            boolean noCacheUsage = url.isFile() || url.isSMB();
            boolean objectWasInCache = (noCacheUsage) ? false : de.anomic.http.client.Cache.has(url);
            boolean useMetadata = !objectWasInCache && (cacheStrategy == null || !cacheStrategy.mustBeOffline());
            if (useMetadata && containsAllHashes(loc = comp.dc_title(), queryhashes)) {
                // try to create the snippet from information given in the url itself
                init(url.hash(), loc, ResultClass.SOURCE_METADATA, null);
                return;
            } else if (useMetadata && containsAllHashes(loc = comp.dc_creator(), queryhashes)) {
                // try to create the snippet from information given in the creator metadata
                init(url.hash(), loc, ResultClass.SOURCE_METADATA, null);
                return;
            } else if (useMetadata && containsAllHashes(loc = comp.dc_subject(), queryhashes)) {
                // try to create the snippet from information given in the subject metadata
                init(url.hash(), loc, ResultClass.SOURCE_METADATA, null);
                return;
            } else if (useMetadata && containsAllHashes(loc = comp.url().toNormalform(true, true).replace('-', ' '), queryhashes)) {
                // try to create the snippet from information given in the url
                init(url.hash(), loc, ResultClass.SOURCE_METADATA, null);
                return;
            } else {
                // try to load the resource from the cache
                response = loader == null ? null : loader.load(loader.request(url, true, reindexing), noCacheUsage ? CrawlProfile.CacheStrategy.NOCACHE : cacheStrategy, Long.MAX_VALUE, true);
                if (response == null) {
                    // in case that we did not get any result we can still return a success when we are not allowed to go online
                    if (cacheStrategy == null || cacheStrategy.mustBeOffline()) {
                        init(url.hash(), null, ResultClass.ERROR_SOURCE_LOADING, "omitted network load (not allowed), no cache entry");
                        return;
                    }
                    
                    // if it is still not available, report an error
                    init(url.hash(), null, ResultClass.ERROR_RESOURCE_LOADING, "error loading resource from net, no cache entry");
                    return;
                }
                if (!objectWasInCache) {
                    // place entry on indexing queue
                    Switchboard.getSwitchboard().toIndexer(response);
                    source = ResultClass.SOURCE_WEB;
                }
            }
        } catch (final Exception e) {
            //Log.logException(e);
            init(url.hash(), null, ResultClass.ERROR_SOURCE_LOADING, "error loading resource: " + e.getMessage());
            return;
        } 
        
        /* ===========================================================================
         * PARSE RESOURCE
         * =========================================================================== */
        Document document = null;
        try {
            document = Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
        } catch (final Parser.Failure e) {
            init(url.hash(), null, ResultClass.ERROR_PARSER_FAILED, e.getMessage()); // cannot be parsed
            return;
        }
        if (document == null) {
            init(url.hash(), null, ResultClass.ERROR_PARSER_FAILED, "parser error/failed"); // cannot be parsed
            return;
        }
        
        /* ===========================================================================
         * COMPUTE SNIPPET
         * =========================================================================== */    
        // we have found a parseable non-empty file: use the lines

        // compute snippet from text
        final Collection<StringBuilder> sentences = document.getSentences(pre);
        if (sentences == null) {
            init(url.hash(), null, ResultClass.ERROR_PARSER_NO_LINES, "parser returned no sentences");
            return;
        }
        final SnippetExtractor tsr;
        String textline = null;
        HandleSet remainingHashes = queryhashes;
        try {
            tsr = new SnippetExtractor(sentences, queryhashes, snippetMaxLength);
            textline = tsr.getSnippet();
            remainingHashes =  tsr.getRemainingWords();
        } catch (UnsupportedOperationException e) {
            init(url.hash(), null, ResultClass.ERROR_NO_MATCH, "no matching snippet found");
            return;
        }
        
        // compute snippet from media
        //String audioline = computeMediaSnippet(document.getAudiolinks(), queryhashes);
        //String videoline = computeMediaSnippet(document.getVideolinks(), queryhashes);
        //String appline = computeMediaSnippet(document.getApplinks(), queryhashes);
        //String hrefline = computeMediaSnippet(document.getAnchors(), queryhashes);
        //String imageline = computeMediaSnippet(document.getAudiolinks(), queryhashes);
        
        snippetLine = "";
        //if (audioline != null) line += (line.length() == 0) ? audioline : "<br />" + audioline;
        //if (videoline != null) line += (line.length() == 0) ? videoline : "<br />" + videoline;
        //if (appline   != null) line += (line.length() == 0) ? appline   : "<br />" + appline;
        //if (hrefline  != null) line += (line.length() == 0) ? hrefline  : "<br />" + hrefline;
        if (textline  != null) snippetLine += (snippetLine.length() == 0) ? textline  : "<br />" + textline;
        
        if (snippetLine == null || !remainingHashes.isEmpty()) {
            init(url.hash(), null, ResultClass.ERROR_NO_MATCH, "no matching snippet found");
            return;
        }
        if (snippetLine.length() > snippetMaxLength) snippetLine = snippetLine.substring(0, snippetMaxLength);

        // finally store this snippet in our own cache
        snippetsCache.put(wordhashes, urls, snippetLine);
        
        document.close();
        init(url.hash(), snippetLine, source, null);
    }
    
    private void init(final byte[] urlhash, final String line, final ResultClass errorCode, final String errortext) {
        this.urlhash = urlhash;
        this.line = line;
        this.resultStatus = errorCode;
        this.error = errortext;
    }
    
    public boolean exists() {
        return line != null;
    }
    
    public String getLineRaw() {
        return (line == null) ? "" : line;
    }
    
    public String getError() {
        return (error == null) ? "" : error.trim();
    }
    
    public ResultClass getErrorCode() {
        return resultStatus;
    }
    
    private final static Pattern splitPattern = Pattern.compile(" |-");
    public String getLineMarked(final HandleSet queryHashes) {
        if (line == null) return "";
        if (queryHashes == null || queryHashes.isEmpty()) return line.trim();
        if (line.endsWith(".")) line = line.substring(0, line.length() - 1);
        final Iterator<byte[]> i = queryHashes.iterator();
        byte[] h;
        final String[] words = splitPattern.split(line);
        while (i.hasNext()) {
            h = i.next();
            for (int j = 0; j < words.length; j++) {
                final List<String> al = markedWordArrayList(words[j]); // mark special character separated words correctly if more than 1 word has to be marked
                words[j] = "";
                for (int k = 0; k < al.size(); k++) {
                    if(k % 2 == 0){ // word has not been marked
                        words[j] += getWordMarked(al.get(k), h);
                    } else { // word has been marked, do not encode again
                        words[j] += al.get(k);
                    }
                }
            }
        }
        final StringBuilder l = new StringBuilder(line.length() + queryHashes.size() * 8);
        for (int j = 0; j < words.length; j++) {
            l.append(words[j]);
            l.append(' ');
        }
        return l.toString().trim();
    }

    public int compareTo(TextSnippet o) {
        return Base64Order.enhancedCoder.compare(this.urlhash, o.urlhash);
    }
    
    public int compare(TextSnippet o1, TextSnippet o2) {
        return o1.compareTo(o2);
    }
    
    @Override
    public int hashCode() {
        return ByteArray.hashCode(this.urlhash);
    }
    
    @Override
    public String toString() {
        return (line == null) ? "" : line;
    }
    
    /**
     * mark words with &lt;b&gt;-tags
     * @param word the word to mark
     * @param h the hash of the word to mark
     * @return the marked word if hash matches, else the unmarked word
     * @see #getLineMarked(Set)
     */
    private static String getWordMarked(final String word, final byte[] h){
        //ignore punctuation marks (contrib [MN])
        //note to myself:
        //For details on regex see "Mastering regular expressions" by J.E.F. Friedl
        //especially p. 123 and p. 390/391 (in the German version of the 2nd edition)

        StringBuilder theWord = new StringBuilder(word);
        StringBuilder prefix = new StringBuilder(40);
        StringBuilder postfix = new StringBuilder(40);
        int len = 0;

        // cut off prefix if it contains of non-characters or non-numbers
        while(p1.matcher(theWord).find()) {
            prefix.append(theWord.substring(0,1));
            theWord = theWord.delete(0, 1);
        }

        // cut off postfix if it contains of non-characters or non-numbers
        while(p2.matcher(theWord).find()) {
            len = theWord.length();
            postfix.insert(0, theWord.substring(len-1,len));
            theWord = theWord.delete(len - 1, len);
        }

        //special treatment if there is a special character in the word
        if(p3.matcher(theWord).find()) {
            StringBuilder out = null;
            String temp = "";
            for(int k=0; k < theWord.length(); k++) {
                out = new StringBuilder(80);
                //is character a special character?
                if(p4.matcher(theWord.substring(k,k+1)).find()) {
                    if (UTF8.String(Word.word2hash(temp)).equals(UTF8.String(h))) temp = "<b>" + CharacterCoding.unicode2html(temp, false) + "</b>";
                    out.append(temp);
                    out.append(CharacterCoding.unicode2html(theWord.substring(k,k+1), false));
                    temp = "";
                }
                //last character
                else if(k == (theWord.length()-1)) {
                    temp = temp + theWord.substring(k,k+1);
                    if (UTF8.String(Word.word2hash(temp)).equals(UTF8.String(h))) temp = "<b>" + CharacterCoding.unicode2html(temp, false) + "</b>";
                    out.append(temp);
                    temp = "";
                }
                else {
                    temp = temp + theWord.substring(k,k+1);
                }
            }
            theWord = out;
        }

        //end contrib [MN]
        else if (UTF8.String(Word.word2hash(theWord)).equals(UTF8.String(h))) {
            theWord.replace(0, theWord.length(), CharacterCoding.unicode2html(theWord.toString(), false));
            theWord.insert(0, "<b>");
            theWord.append("</b>");
        }

        theWord.insert(0, CharacterCoding.unicode2html(prefix.toString(), false));
        theWord.append(CharacterCoding.unicode2html(postfix.toString(), false));
        return theWord.toString();
    }
    
    /**
     * words that already has been marked has index <code>(i % 2 == 1)</code>
     * words that has not yet been marked has index <code>(i % 2 == 0)</code>
     * @param string the String to be processed
     * @return words that already has and has not yet been marked
     * @author [DW], 08.11.2008
     */
    private static List<String> markedWordArrayList(String string){
        List<String> al = new java.util.ArrayList<String>(1);
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
    
    private static boolean containsAllHashes(final String sentence, final HandleSet queryhashes) {
        final SortedMap<byte[], Integer> m = WordTokenizer.hashSentence(sentence, null);
        for (final byte[] b: queryhashes) {
            if (!(m.containsKey(b))) return false;
        }
        return true;
    }

}
