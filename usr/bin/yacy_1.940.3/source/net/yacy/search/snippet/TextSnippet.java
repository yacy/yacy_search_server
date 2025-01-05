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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.storage.ARC;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.SentenceReader;
import net.yacy.document.SnippetExtractor;
import net.yacy.document.WordTokenizer;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.Word;
import net.yacy.peers.RemoteSearch;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.Switchboard;
import net.yacy.search.query.QueryGoal;

public class TextSnippet implements Comparable<TextSnippet>, Comparator<TextSnippet> {

	/** The maximum number of sinppet entries in the cache */
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
     * updated to <code>\\A([\\p{L}\\p{N}]+[^\\p{L}\\p{N}].+)([\\p{N}]+[.,][\\p{N}])+\\Z</code>
     * to detect words with none alphanumeric chars (1) allow comma/dot surrounded by number (2)
     */
    private static final Pattern p3 =
            Pattern.compile("\\A([\\p{L}\\p{N}]+[^\\p{L}\\p{N}].+)([\\p{N}]+[.,][\\p{N}])+\\Z");
    /**
     * <code>[^\\p{L}\\p{N}]</code>
     */
    private static final Pattern p4 =
            Pattern.compile("[^\\p{L}\\p{N}]");

    public static class Cache {
        private final ARC<String, String> cache;
        public Cache() {
            this.cache = new ConcurrentARC<String, String>(MAX_CACHE, Math.min(32, 2 * Runtime.getRuntime().availableProcessors()));
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
    
    /** Handle statistics on TextSnippet processing */
    public static final TextSnippetStatistics statistics = new TextSnippetStatistics();

    public static enum ResultClass {
    	/** Snippet provided by Solr */
        SOURCE_SOLR(false),
    	/** Snippet retrieved from snippets cache or computed from cached document */
        SOURCE_CACHE(false),
        SOURCE_FILE(false),
        /** Snippet computed from the original document fetched and parsed */
        SOURCE_WEB(false),
        /** Snippet computed by YaCy from document metadata */
        SOURCE_METADATA(false),
        /** Could not extract a snippet because no search term was provided */
        ERROR_NO_TERM_GIVEN(true),
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

    /** URL hash of this snippet */
    private byte[] urlhash;
    
    /** The raw (unmodified) line from source ( use getDescriptionLine() to get the html encoded version for display) */
    private String line;
    
    /** Set to true when query words are already marked in the input text */
    private boolean isMarked;
    
    private String error;
    private ResultClass resultStatus;

    public TextSnippet(
            final DigestURL url,
            final String line,
            final boolean isMarked,
            final ResultClass errorCode,
            final String errortext) {
    	long beginTime = System.currentTimeMillis();
        init(url, line, isMarked, errorCode, errortext, beginTime);
    }

    public TextSnippet(
            final LoaderDispatcher loader,
            final URIMetadataNode row,
            final Set<String> queryTerms,
            final HandleSet queryhashes,
            final CacheStrategy cacheStrategy,
            final boolean pre,
            final int snippetMaxLength,
            final boolean reindexing) {
    	long beginTime = System.currentTimeMillis();
        // heise = "0OQUNU3JSs05"
        
        final DigestURL url = row.url();
        if (queryTerms.isEmpty()) {
            init(url, null, false, ResultClass.ERROR_NO_TERM_GIVEN, "no query terms given", beginTime);
            return;
        }

        // try to get snippet from snippetCache
        ResultClass source = ResultClass.SOURCE_CACHE;
    	final String urlHash = ASCII.String(url.hash());
        final String wordhashes;
        if(queryhashes != null) {
        	wordhashes = RemoteSearch.set2string(queryhashes);
        	final String snippetLine = snippetsCache.get(wordhashes, urlHash);
        	if (snippetLine != null) {
        		// found the snippet
        		init(url, snippetLine, false, source, null, beginTime);
        		return;
        	}
        } else {
        	wordhashes = null;
        }

        // try to get the snippet from a document at the cache (or in the web)
        // this requires that the document is parsed after loading
        String textline = null;
        Set<String> remainingTerms = new HashSet<>(queryTerms);
        SentenceReader sentences = null;
        List<StringBuilder> firstSentencesList = null;
        
        // try to get the snippet from metadata
        removeMatchingTerms(row.url().toTokens(), remainingTerms);
        removeMatchingTerms(row.dc_title(), remainingTerms);
        removeMatchingTerms(row.dc_creator(), remainingTerms);
        removeMatchingTerms(row.dc_subject(), remainingTerms);
        
        if (!remainingTerms.isEmpty()) {
            // we did not find everything in the metadata, look further into the document itself.

            // first acquire the sentences (from description/abstract or text):
            final ArrayList<String> solrdesc = row.getDescription();
            if (!solrdesc.isEmpty()) { // include description_txt (similar to solr highlighting config)
            	firstSentencesList = new ArrayList<>();
                for (final String s : solrdesc) {
                	firstSentencesList.add(new StringBuilder(s));
                }
            }
            final String solrText = row.getText();
            if (solrText != null && solrText.length() > 0) { // TODO: instead of join with desc, we could check if snippet already complete and skip further computation
                // compute sentences from solr query
               	sentences = new SentenceReader(firstSentencesList, solrText, pre);
            } else if (net.yacy.crawler.data.Cache.has(url.hash())) {
                // get the sentences from the cache
                final Request request = loader == null ? null : loader.request(url, true, reindexing);
                Response response;
                try {
                    response = loader == null || request == null ? null : loader.load(request, CacheStrategy.CACHEONLY, BlacklistType.SEARCH, ClientIdentification.yacyIntranetCrawlerAgent);
                } catch (final IOException e1) {
                    response = null;
                }
                Document document = null;
                if (response != null) {
                    try {
                        document = Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
                        sentences = new SentenceReader(firstSentencesList, document.getTextString(), pre);
                        response = null;
                        document = null;
                    } catch (final Parser.Failure e) {
                    }
                }
            }
            if (sentences == null) {
                // not found the snippet
                init(url, null, false, ResultClass.SOURCE_METADATA, null, beginTime);
                return;
            }

            if (sentences.iterator().hasNext()) {
                try {
                    final SnippetExtractor tsr = new SnippetExtractor(sentences, remainingTerms, snippetMaxLength);
                    textline = tsr.getSnippet();
                    remainingTerms = tsr.getRemainingTerms();
                } catch (final UnsupportedOperationException e) {
                    init(url, null, false, ResultClass.ERROR_NO_MATCH, "snippet extractor failed:" + e.getMessage(), beginTime);
                    return;
                }
            }
       }

       if (remainingTerms.isEmpty()) {
            // we found the snippet or the query is fully included in the headline or url
            if (textline == null || textline.length() == 0) {
                // this is the case where we don't have a snippet because all search words are included in the headline or the url
            	if(sentences == null) {
            		String solrText = row.getText();
            		if (solrText != null && solrText.length() > 0) {
            			// compute sentences from solr query
            			sentences = new SentenceReader(firstSentencesList, solrText, pre);
            		}
            	} else {
                	sentences.reset();
            	}
                if (sentences == null || (!sentences.iterator().hasNext())) {
                    textline = row.dc_subject();
                } else {
                    // use the first lines from the text after the h1 tag as snippet
                    // get first the h1 tag
                    List<String> h1 = row.h1();
                    if (h1 != null && h1.size() > 0) {
                        // find first appearance of first h1 in sentences and then take the next sentence
                        String h1s = h1.get(0);
                        if (h1s.length() > 0) {
                        	String prevSentence = null, currentSentence;
                            solrsearch: for (final StringBuilder sentence: sentences) {
                            	currentSentence = sentence.toString();
                                if (prevSentence != null && prevSentence.startsWith(h1s)) {
                                    textline = currentSentence;
                                    break solrsearch;
                                }
                                prevSentence = currentSentence;
                            }
                        }
                    }
                    if (textline == null) {
                    	sentences.reset();
                        final StringBuilder s = new StringBuilder(snippetMaxLength);
                        for (final StringBuilder t: sentences) {
                        	s.append(t).append(' ');
                        	if (s.length() >= snippetMaxLength / 4 * 3) break;
                        }
                        if (s.length() > snippetMaxLength) { s.setLength(snippetMaxLength); s.trimToSize(); }
                        textline = s.toString();
                    }
                }
            }
            init(url, textline.length() > 0 ? textline : this.line, false, ResultClass.SOURCE_METADATA, null, beginTime);
            return;
        }
        sentences = null; // we don't need this here any more

        // try to load the resource from the cache
        Response response = null;
        try {
            response = loader == null ? null : loader.load(loader.request(url, true, reindexing), (url.isFile() || url.isSMB()) ? CacheStrategy.NOCACHE : (cacheStrategy == null ? CacheStrategy.CACHEONLY : cacheStrategy), BlacklistType.SEARCH, ClientIdentification.yacyIntranetCrawlerAgent);
        } catch (final IOException e) {
            response = null;
        }

        if (response == null) {
            // in case that we did not get any result we can still return a success when we are not allowed to go online
            if (cacheStrategy == null || cacheStrategy.mustBeOffline()) {
                init(url, null, false, ResultClass.ERROR_SOURCE_LOADING, "omitted network load (not allowed), no cache entry", beginTime);
                return;
            }

            // if it is still not available, report an error
            init(url, null, false, ResultClass.ERROR_RESOURCE_LOADING, "error loading resource from net, no cache entry", beginTime);
            return;
        }

        if (!response.fromCache()) {
            // place entry on indexing queue
            Switchboard.getSwitchboard().toIndexer(response);
            source = ResultClass.SOURCE_WEB;
        }

        // parse the document to get all sentenced; available for snippet computation
        Document document = null;
        try {
            document = Document.mergeDocuments(response.url(), response.getMimeType(), response.parse());
        } catch (final Parser.Failure e) {
            init(url, null, false, ResultClass.ERROR_PARSER_FAILED, e.getMessage(), beginTime); // cannot be parsed
            return;
        }
        if (document == null) {
            init(url, null, false, ResultClass.ERROR_PARSER_FAILED, "parser error/failed", beginTime); // cannot be parsed
            return;
        }

        // compute sentences from parsed document
        sentences = new SentenceReader(document.getTextString(), pre);
        document.close();

        if (!sentences.hasNext()) {
            init(url, null, false, ResultClass.ERROR_PARSER_NO_LINES, "parser returned no sentences", beginTime);
            return;
        }

        try {
            final SnippetExtractor tsr = new SnippetExtractor(sentences, remainingTerms, snippetMaxLength);
            textline = tsr.getSnippet();
            remainingTerms =  tsr.getRemainingTerms();
        } catch (final UnsupportedOperationException e) {
            init(url, null, false, ResultClass.ERROR_NO_MATCH, "snippet extractor failed:" + e.getMessage(), beginTime);
            return;
        }
        sentences = null;

        if (textline == null || !remainingTerms.isEmpty()) {
            init(url, null, false, ResultClass.ERROR_NO_MATCH, "no matching snippet found", beginTime);
            return;
        }
        if (textline.length() > snippetMaxLength) textline = textline.substring(0, snippetMaxLength);

        // finally store this snippet in our own cache
        if(wordhashes != null) {
        	snippetsCache.put(wordhashes, urlHash, textline);
        }
        init(url, textline, false, source, null, beginTime);
    }

    /**
     * Init a snippet line for urlhash
     *
     * @param url hash of the url for this snippet
     * @param line text to use as snippet
     * @param isMarked true if query words already marked in input text
     * @param errorCode
     * @param errortext
     * @param beginTime the time in milliseconds when TextSnippet creation started
     */
    private void init(
            final DigestURL url,
            final String line,
            final boolean isMarked,
            final ResultClass errorCode,
            final String errortext,
            final long beginTime) {
        this.urlhash = url.hash();
        this.line = line;
        this.isMarked = isMarked;
        this.resultStatus = errorCode;
        this.error = errortext;
		TextSnippet.statistics.addTextSnippetStatistics(url, System.currentTimeMillis() - beginTime, this.resultStatus);
    }

    /**
     * @return true when a snippet text is available for the document corresponding to this.urlhash
     */
    public boolean exists() {
        return this.line != null;
    }

    /**
     * @return true when query words are already marked in the raw snippet text
     */
    public boolean isMarked() {
        return this.isMarked;
    }
    
    /**
     * @return the raw snippet text line, with query words eventually already marked
     */
    public String getLineRaw() {
        return (this.line == null) ? "" : this.line;
    }

    public String getError() {
        return (this.error == null) ? "" : this.error.trim();
    }

    public ResultClass getErrorCode() {
        return this.resultStatus;
    }

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[ |-]+");

    /**
     * Marks all words in current line which have the same
     * hash values as the ones contained in argument.
     * @param queryGoal the query goal
     * @return line with marked words
     */
    public String getLineMarked(final QueryGoal queryGoal) {
        final HandleSet queryHashes = queryGoal.getIncludeHashes();
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

    private String descriptionline = null;
    /**
     * Snippet line formatted/encoded for display in browser
     * possible html code in raw line is html encoded
     * query words marked by <b>..</b>
     *
     * @param queryGoal
     * @return html encoded snippet line
     */
    public String descriptionline(QueryGoal queryGoal) {
        if (descriptionline != null) return descriptionline;
        if (this.isMarked) {
            // html encode source, keep <b>..</b>
            descriptionline = CharacterCoding.unicode2html(this.getLineRaw(), false).replaceAll("&lt;b&gt;(.+?)&lt;/b&gt;", "<b>$1</b>");
        } else {
            descriptionline = this.getLineMarked(queryGoal);
        }
        return descriptionline;
    }
    
    @Override
    public int compareTo(final TextSnippet o) {
        return Base64Order.enhancedCoder.compare(this.urlhash, o.urlhash);
    }

    @Override
    public int compare(final TextSnippet o1, final TextSnippet o2) {
        return o1.compareTo(o2);
    }

    private int hashCache = Integer.MIN_VALUE; // if this is used in a compare method many times, a cache is useful

    @Override
    public int hashCode() {
        if (this.hashCache == Integer.MIN_VALUE) {
            this.hashCache = ByteArray.hashCode(this.urlhash);
        }
        return this.hashCache;
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
     * @see #getLineMarked(QueryGoal)
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
    
    /**
     * Modify the queryTerms set : remove terms present in the given sentence.
     * @param sentence a sentence potentially matching some terms of queryTerms
     * @param queryTerms a set of normalized terms
     */
    private static void removeMatchingTerms(final String sentence, final Set<String> queryTerms) {
        if (queryTerms.size() == 0) {
        	return;
        }
        final Set<String> sentenceWords = WordTokenizer.tokenizeSentence(sentence, 100).keySet();
        queryTerms.removeAll(sentenceWords);
    }
}
