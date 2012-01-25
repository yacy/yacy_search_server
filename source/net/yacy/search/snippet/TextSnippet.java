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

package net.yacy.search.snippet;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.services.federated.yacy.CacheStrategy;
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
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.peers.RemoteSearch;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.Switchboard;
import de.anomic.crawler.retrieval.Request;
import de.anomic.crawler.retrieval.Response;

public class TextSnippet implements Comparable<TextSnippet>, Comparator<TextSnippet> {

    private static final int MAX_CACHE = 1000;


    /**
     * <code>\\A[^\\p{L}\\p{N}].+</code>
     */
    private static final Pattern p1 =
            Pattern.compile("\\A[^\\p{L}\\p{N}].+");
    /**
     * <code>.+[^\\p{L}\\p{N}]\\Z</code>
     */
    private static final Pattern p2 =
            Pattern.compile(".+[^\\p{L}\\p{N}]\\Z");
    /**
     * <code>\\A[\\p{L}\\p{N}]+[^\\p{L}\\p{N}].+\\Z</code>
     */
    private static final Pattern p3 =
            Pattern.compile("\\A[\\p{L}\\p{N}]+[^\\p{L}\\p{N}].+\\Z");
    /**
     * <code>[^\\p{L}\\p{N}]</code>
     */
    private static final Pattern p4 =
            Pattern.compile("[^\\p{L}\\p{N}]");

    public static class Cache {
        private final ARC<String, String> cache;
        public Cache() {
            this.cache = new ConcurrentARC<String, String>(MAX_CACHE, Math.max(32, 4 * Runtime.getRuntime().availableProcessors()));
        }
        public void put(final String wordhashes, final String urlhash, final String snippet) {
            // generate key
            final String key = urlhash + wordhashes;

            // do nothing if snippet is known or otherwise learn new snippet
            this.cache.insertIfAbsent(key, snippet);
        }

        public String get(final String wordhashes, final String urlhash) {
            // generate key
            final String key = urlhash + wordhashes;
            return this.cache.get(key);
        }

        public boolean contains(final String wordhashes, final String urlhash) {
            return this.cache.containsKey(urlhash + wordhashes);
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
            final String solrText,
            final URIMetadataRow row,
            final HandleSet queryhashes,
            final CacheStrategy cacheStrategy,
            final boolean pre,
            final int snippetMaxLength,
            final int maxDocLen,
            final boolean reindexing) {
        // heise = "0OQUNU3JSs05"
        final DigestURI url = row.url();
        if (queryhashes.isEmpty()) {
            //System.out.println("found no queryhashes for URL retrieve " + url);
            init(url.hash(), null, ResultClass.ERROR_NO_HASH_GIVEN, "no query hashes given");
            return;
        }

        // try to get snippet from snippetCache
        final ResultClass source = ResultClass.SOURCE_CACHE;
        final String wordhashes = RemoteSearch.set2string(queryhashes);
        final String urls = ASCII.String(url.hash());
        String snippetLine = snippetsCache.get(wordhashes, urls);
        if (snippetLine != null) {
            // found the snippet
            init(url.hash(), snippetLine, source, null);
            return;
        }

        // try to get the snippet from a document at the cache (or in the web)
        // this requires that the document is parsed after loading
        String textline = null;
        HandleSet remainingHashes = queryhashes;
        { //encapsulate potential expensive sentences
            Collection<StringBuilder> sentences = null;

            // try the solr text first
            if (solrText != null) {
                // compute sentences from solr query
                sentences = Document.getSentences(pre, new ByteArrayInputStream(UTF8.getBytes(solrText)));
            }

            // if then no sentences are found, we fail-over to get the content from the re-loaded document
            if (sentences == null) {
                final Document document = loadDocument(loader, row, queryhashes, cacheStrategy, url, reindexing, source);
                if (document == null) {
                    return;
                }

                // compute sentences from parsed document
                sentences = document.getSentences(pre);
                document.close();

                if (sentences == null) {
                    init(url.hash(), null, ResultClass.ERROR_PARSER_NO_LINES, "parser returned no sentences");
                    return;
                }
            }

            if (this.resultStatus == ResultClass.SOURCE_METADATA) {
                // if we already know that there is a match then use the first lines from the text as snippet
                final StringBuilder s = new StringBuilder(snippetMaxLength);
                for (final StringBuilder t: sentences) {
                    s.append(t).append(' ');
                    if (s.length() >= snippetMaxLength / 4 * 3) break;
                }
                if (s.length() > snippetMaxLength) { s.setLength(snippetMaxLength); s.trimToSize(); }
                init(url.hash(), s.length() > 0 ? s.toString() : this.line, ResultClass.SOURCE_METADATA, null);
                return;
            }

            try {
                final SnippetExtractor tsr = new SnippetExtractor(sentences, queryhashes, snippetMaxLength);
                textline = tsr.getSnippet();
                remainingHashes =  tsr.getRemainingWords();
            } catch (final UnsupportedOperationException e) {
                init(url.hash(), null, ResultClass.ERROR_NO_MATCH, "no matching snippet found");
                return;
            }
        } //encapsulate potential expensive sentences END

        // compute snippet from media - attention document closed above!
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

//        document.close();
        init(url.hash(), snippetLine, source, null);
    }

    private Document loadDocument(
            final LoaderDispatcher loader,
            final URIMetadataRow row,
            final HandleSet queryhashes,
            final CacheStrategy cacheStrategy,
            final DigestURI url,
            final boolean reindexing,
            ResultClass source) {
        /* ===========================================================================
         * LOAD RESOURCE DATA
         * =========================================================================== */
        // if the snippet is not in the cache, we can try to get it from the htcache
        Response response = null;
        try {
            // first try to get the snippet from metadata
            String loc;
            final Request request = loader.request(url, true, reindexing);
            final boolean inCache = de.anomic.http.client.Cache.has(row.url());
            final boolean noCacheUsage = url.isFile() || url.isSMB() || cacheStrategy == null;
            if (containsAllHashes(loc = row.dc_title(), queryhashes) ||
                containsAllHashes(loc = row.dc_creator(), queryhashes) ||
                containsAllHashes(loc = row.dc_subject(), queryhashes) ||
                containsAllHashes(loc = row.url().toNormalform(true, true).replace('-', ' '), queryhashes)) {
                // try to create the snippet from information given in the url
                if (inCache) response = loader == null ? null : loader.load(request, CacheStrategy.CACHEONLY, true);
                Document document = null;
                if (response != null) {
                    try {
                        document = Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
                    } catch (final Parser.Failure e) {
                    }
                }
                init(url.hash(), loc, ResultClass.SOURCE_METADATA, null);
                return document;
            } else {
                // try to load the resource from the cache
                response = loader == null ? null : loader.load(request, noCacheUsage ? CacheStrategy.NOCACHE : cacheStrategy, true);
                if (response == null) {
                    // in case that we did not get any result we can still return a success when we are not allowed to go online
                    if (cacheStrategy == null || cacheStrategy.mustBeOffline()) {
                        init(url.hash(), null, ResultClass.ERROR_SOURCE_LOADING, "omitted network load (not allowed), no cache entry");
                        return null;
                    }

                    // if it is still not available, report an error
                    init(url.hash(), null, ResultClass.ERROR_RESOURCE_LOADING, "error loading resource from net, no cache entry");
                    return null;
                } else {
                    // place entry on indexing queue
                    Switchboard.getSwitchboard().toIndexer(response);
                    source = ResultClass.SOURCE_WEB;
                }
            }
        } catch (final Exception e) {
            //Log.logException(e);
            init(url.hash(), null, ResultClass.ERROR_SOURCE_LOADING, "error loading resource: " + e.getMessage());
            return null;
        }

        /* ===========================================================================
         * PARSE RESOURCE
         * =========================================================================== */
        Document document = null;
        try {
            document = Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
        } catch (final Parser.Failure e) {
            init(url.hash(), null, ResultClass.ERROR_PARSER_FAILED, e.getMessage()); // cannot be parsed
            return null;
        }
        if (document == null) {
            init(url.hash(), null, ResultClass.ERROR_PARSER_FAILED, "parser error/failed"); // cannot be parsed
        }
        return document;
    }

    private void init(
            final byte[] urlhash,
            final String line,
            final ResultClass errorCode,
            final String errortext) {
        this.urlhash = urlhash;
        this.line = line;
        this.resultStatus = errorCode;
        this.error = errortext;
    }

    public boolean exists() {
        return this.line != null;
    }

    public String getLineRaw() {
        return (this.line == null) ? "" : this.line;
    }

    public String getError() {
        return (this.error == null) ? "" : this.error.trim();
    }

    public ResultClass getErrorCode() {
        return this.resultStatus;
    }

    private static final Pattern SPLIT_PATTERN = Pattern.compile(" |-");

    /**
     * Marks all words in current line which have the same
     * hash values as the ones contained in argument.
     * @param queryHashes hashes of search words
     * @return line with marked words
     */
    public String getLineMarked(final HandleSet queryHashes) {
        if (this.line == null) {
            return "";
        }
        if (queryHashes == null || queryHashes.isEmpty()) {
            return this.line.trim();
        }
        if (this.line.endsWith(".")) {
            this.line = this.line.substring(0, this.line.length() - 1);
        }

        final String[] words = SPLIT_PATTERN.split(this.line);

        final Iterator<byte[]> iterator = queryHashes.iterator();
        final Set<byte[]> queryHashesSet = new HashSet<byte[]>();
        while (iterator.hasNext()) {
            queryHashesSet.add(iterator.next());
        }

        for (int i = 0; i < words.length; i++) {
            words[i] = getWordMarked(words[i], queryHashesSet);
        }

        final StringBuilder l =
                new StringBuilder(this.line.length() + queryHashes.size() * 8);
        for (final String word : words) {
            l.append(word);
            l.append(' ');
        }
        return l.toString().trim();
    }

    @Override
    public int compareTo(final TextSnippet o) {
        return Base64Order.enhancedCoder.compare(this.urlhash, o.urlhash);
    }

    @Override
    public int compare(final TextSnippet o1, final TextSnippet o2) {
        return o1.compareTo(o2);
    }

    @Override
    public int hashCode() {
        return ByteArray.hashCode(this.urlhash);
    }

    @Override
    public String toString() {
        return (this.line == null) ? "" : this.line;
    }

    /**
     * Marks words with &lt;b&gt;-tags. <b>Beware</b>: Method
     * has side effects! Certain characters in words will be
     * escaped to HTML encoding. Using this method a second
     * time with already escaped characters might lead to
     * undesired results.
     * @param word the word to mark
     * @param queryHashes hashes of the words to mark
     * @return the marked word if one of the hashes matches,
     * else the unmarked word
     * @see #getLineMarked(Set)
     */
    private static String getWordMarked(
            final String word, final Set<byte[]> queryHashes) {
        //note to myself [MN]:
        //For details on regex see "Mastering regular expressions" by J.E.F. Friedl
        //especially p. 123 and p. 390/391 (in the German version of the 2nd edition)

        final StringBuilder theWord = new StringBuilder(word);
        final StringBuilder prefix = new StringBuilder(40);
        final StringBuilder postfix = new StringBuilder(40);
        int len = 0;

        // cut off prefix if it contains of non-characters or non-numbers
        while (p1.matcher(theWord).find()) {
            prefix.append(theWord.substring(0, 1));
            theWord.delete(0, 1);
        }

        // cut off postfix if it contains of non-characters or non-numbers
        while (p2.matcher(theWord).find()) {
            len = theWord.length();
            postfix.insert(0, theWord.substring(len - 1, len));
            theWord.delete(len - 1, len);
        }

        //special treatment if there is a special character in the word
        if (p3.matcher(theWord).find()) {

            StringBuilder out = null;
            String temp = "";
            for (int k = 0; k < theWord.length(); k++) {
                out = new StringBuilder(80);
                //is character a special character?
                if (p4.matcher(theWord.substring(k, k + 1)).find()) {
                    if (ByteBuffer.contains(queryHashes, Word.word2hash(temp))) {
                        temp = "<b>" + CharacterCoding.unicode2html(temp, false) + "</b>";
                    }
                    out.append(temp);
                    out.append(CharacterCoding.unicode2html(theWord.substring(k, k +1), false));
                    temp = "";
                }
                //last character
                else if (k == (theWord.length() - 1)) {
                    temp = temp + theWord.substring(k, k + 1);
                    if (ByteBuffer.contains(queryHashes, Word.word2hash(temp))) {
                        temp = "<b>" + CharacterCoding.unicode2html(temp, false) + "</b>";
                    }
                    out.append(temp);
                    temp = "";
                }
                else {
                    temp = temp + theWord.substring(k, k + 1);
                }
            }
            theWord.delete(0, theWord.length());
            theWord.append(out);

        } else if (ByteBuffer.contains(queryHashes, Word.word2hash(theWord))) {
            theWord.replace(
                    0,
                    theWord.length(),
                    CharacterCoding.unicode2html(theWord.toString(), false));
            theWord.insert(0, "<b>");
            theWord.append("</b>");
        }

        theWord.insert(
                0,
                CharacterCoding.unicode2html(prefix.toString(), false));
        theWord.append(CharacterCoding.unicode2html(postfix.toString(), false));
        return theWord.toString();
    }

    private static boolean containsAllHashes(
            final String sentence, final HandleSet queryhashes) {
        final SortedMap<byte[], Integer> m = WordTokenizer.hashSentence(sentence, null, 100);
        for (final byte[] b : queryhashes) {
            if (!(m.containsKey(b))) {
                return false;
            }
        }
        return true;
    }

}
