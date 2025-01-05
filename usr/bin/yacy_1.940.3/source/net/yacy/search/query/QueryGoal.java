/**
 *  QueryGoal
 *  Copyright 2012 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 16.11.2005 on http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package net.yacy.search.query;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.http.HttpStatus;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.storage.HandleSet;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.SetTools;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionSchema;

public class QueryGoal {

    private static char space = ' ';
    private static char sq = '\'';
    private static char dq = '"';
    private static String seps = ":;#*`!$%()=?^<>/&_";

    public String query_original;
    private HandleSet include_hashes, exclude_hashes;
    private final NormalizedWords include_words, exclude_words;
    private final ArrayList<String> include_strings, exclude_strings;

    public static class NormalizedWords extends TreeSet<String> {

        private static final long serialVersionUID = -3050851079671868007L;

        public NormalizedWords() {
            super(NaturalOrder.naturalComparator);
        }

        public NormalizedWords(String[] rawWords) {
            super(NaturalOrder.naturalComparator);
            for (String word: rawWords) super.add(word.toLowerCase(Locale.ENGLISH));
        }

        public NormalizedWords(Collection<String> rawWords) {
            super(NaturalOrder.naturalComparator);
            for (String word: rawWords) super.add(word.toLowerCase(Locale.ENGLISH));
        }

        @Override
        public boolean add(String word) {
            return super.add(word.toLowerCase(Locale.ENGLISH));
        }

        @Override
        public boolean contains(Object word) {
            if (!(word instanceof String)) return false;
            return super.contains(((String) word).toLowerCase(Locale.ENGLISH));
        }
    }

    public QueryGoal(HandleSet include_hashes, HandleSet exclude_hashes) {
        this.query_original = null;
        this.include_words = new NormalizedWords();
        this.exclude_words = new NormalizedWords();
        this.include_strings = new ArrayList<String>();
        this.exclude_strings = new ArrayList<String>();
        this.include_hashes = include_hashes;
        this.exclude_hashes = exclude_hashes;
    }

    /**
     * Creates a QueryGoal from a search query string
     * @param query_words search string (the actual search terms, excluding application specific modifier)
     */
    public QueryGoal(String query_words) {
        assert query_words != null;
        this.query_original = query_words;
        this.include_words = new NormalizedWords();
        this.exclude_words = new NormalizedWords();
        this.include_strings = new ArrayList<String>();
        this.exclude_strings = new ArrayList<String>();

        // remove funny symbols
        query_words = CharacterCoding.html2unicode(AbstractScraper.stripAllTags(query_words.toCharArray())).toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            while ((c = query_words.indexOf(seps.charAt(i))) >= 0) {
                query_words = query_words.substring(0, c) + (((c + 1) < query_words.length()) ? (' ' + query_words.substring(c + 1)) : "");
            }
        }

        // parse first quoted strings
        parseQuery(query_words, this.include_strings, this.exclude_strings);

        // .. end then take these strings apart to generate word lists
        for (String s: this.include_strings) parseQuery(s, this.include_words, this.include_words);
        for (String s: this.exclude_strings) parseQuery(s, this.exclude_words, this.exclude_words);

        WordCache.learn(this.include_words);
        WordCache.learn(this.exclude_words);

        this.include_hashes = null;
        this.exclude_hashes = null;
    }

/*
 * EBNF of a query
 * 
 * query      = {whitespace, phrase}, [whitespace]
 * whitespace = space, {space}
 * space      = ' '
 * phrase     = ['-']|['+'], string
 * string     = {any character without sq, dq and whitespace} | sq, {any character without sq}, sq | dq, {any character without dq}, dq
 * sq         = '\''
 * dq         = '"'
 */
    private static void parseQuery(String s, Collection<String> include_string, Collection<String> exclude_string) {

        while (s.length() > 0) {
            // parse whitespace
            int p = 0;
            while (p < s.length() && s.charAt(p) == space) p++;
            s = s.substring(p);
            if (s.length() == 0) return;

            // parse phrase
            boolean inc = true;
            if (s.charAt(0) == '-') {
                inc = false;
                s = s.substring(1);
            } else if (s.charAt(0) == '+') {
                inc = true;
                s = s.substring(1);
            }
            if (s.length() == 0) return;

            // parse string
            char stop = space;
            if (s.charAt(0) == dq) {
                stop = s.charAt(0);
                s = s.substring(1);
            } else if (s.charAt(0) == sq) {
                stop = s.charAt(0);
                s = s.substring(1);
            }

            if (stop == space) {
                // For non-quoted strings, just skip to the next token
                while (p < s.length() && s.charAt(p) != stop) p++;
            } else {
                // For quoted strings, find the closing quote
                while (p < s.length() && s.charAt(p) != stop) p++;

                // Consume the closing quote
                if (p < s.length() && s.charAt(p) == stop) p++;
            }

            String string;
            if (stop == space) {
                string = s.substring(0, p);
            } else {
                string = s.substring(0, p - 1);  // Exclude the closing quote
            }
            s = p < s.length() ? s.substring(p) : "";
            p++; // go behind the stop character (eats up space, sq and dq)
            if (string.length() > 0) {
                if (inc) {
                    if (!include_string.contains(string)) include_string.add(string);
                } else {
                    if (!exclude_string.contains(string)) exclude_string.add(string);
                }
            }
        }

        // in case that the include_string contains several entries including 1-char tokens and also more-than-1-char tokens,
        // then remove the 1-char tokens to prevent that we are to strict. This will make it possible to be a bit more fuzzy
        // in the search where it is appropriate
        boolean contains_single = false, contains_multiple = false;
        for (String token: include_string) {
            if (token.length() == 1) contains_single = true; else contains_multiple = true;
        }
        if (contains_single && contains_multiple) {
            Iterator<String> i = include_string.iterator();
            while (i.hasNext()) if (i.next().length() == 1) i.remove();
        }
    }

    /**
     * Search query string (without YaCy specific modifier like site:xxx or /smb)
     * the modifier are held separately in a search paramter modifier
     *
     * @param encodeHTML
     * @return the search query string
     */
    public String getQueryString(final boolean encodeHTML) {
        if (this.query_original == null) return null;
        String ret;
        if (encodeHTML){
            try {
                ret = URLEncoder.encode(this.query_original, StandardCharsets.UTF_8.name());
            } catch (final UnsupportedEncodingException e) {
                ret = this.query_original;
            }
        } else {
            ret = this.query_original;
        }
        return ret;
    }

    /**
     * @return a set of hashes of words to be included in the search result.
     * if possible, use getIncludeWords instead
     */
    public HandleSet getIncludeHashes() {
        if (this.include_hashes == null) this.include_hashes = Word.words2hashesHandles(include_words);
        return this.include_hashes;
    }

    /**
     * @return a set of hashes of words to be excluded in the search result
     * if possible, use getExcludeWords instead
     */
    public HandleSet getExcludeHashes() {
        if (this.exclude_hashes == null) this.exclude_hashes = Word.words2hashesHandles(exclude_words);
        return this.exclude_hashes;
    }

    public int getIncludeSize() {
        assert this.include_hashes == null || this.include_words.size() == 0 || this.include_hashes.size() == this.include_words.size();
        return this.include_hashes == null ? this.include_words.size() : this.include_hashes.size();
    }

    public int getExcludeSize() {
        assert this.exclude_hashes == null || this.exclude_words.size() == 0 || this.exclude_hashes.size() == this.exclude_words.size();
        return this.exclude_hashes == null ? this.exclude_words.size() : this.exclude_hashes.size();
    }

    /**
     * @return an iterator on the set of words to be included in the search result
     */
    public Iterator<String> getIncludeWords() {
        return this.include_words.iterator();
    }

    /**
     * @return a copy of the set of words to be included in the search result
     */
    public Set<String> getIncludeWordsSet() {
        return new NormalizedWords(this.include_words);
    }

    /**
     * @return an iterator on the set of words to be excluded from the search result
     */
    public Iterator<String> getExcludeWords() {
        return this.exclude_words.iterator();
    }

    /**
     * @return a copy of the set of words to be excluded from the search result
     */
    public Set<String> getExcludeWordsSet() {
        return new NormalizedWords(this.exclude_words);
    }

    /**
     * @return a list of include strings which reproduces the original order of the search words and quotation
     */
    public Iterator<String> getIncludeStrings() {
        return this.include_strings.iterator();
    }

    /**
     * @return a list of exclude strings which reproduces the original order of the search words and quotation
     */
    public Iterator<String> getExcludeStrings() {
        return this.exclude_strings.iterator();
    }

    public void removeIncludeWords(Set<String> words) {
        if (!words.isEmpty()) {
            SetTools.excludeDestructiveByTestSmallInLarge(this.exclude_words, words); //remove stopwords
            SetTools.excludeDestructiveByTestSmallInLarge(this.exclude_strings, words); //remove stopwords
            if (include_hashes != null) for (String word: words) this.include_hashes.remove(Word.word2hash(word));
        }
    }

    /**
     * the include string may be useful (and better) for highlight/snippet computation 
     * @return the query string containing only the positive literals (includes) and without whitespace characters
     */
    public String getIncludeString() {
        if (this.include_strings.size() == 0) return "";
        StringBuilder sb = new StringBuilder(10 * include_strings.size());
        for (String s: this.include_strings) sb.append(s).append(' ');
        return sb.toString().substring(0, sb.length() - 1);
    }

    public boolean isCatchall() {
        if (this.include_hashes != null && this.include_hashes.has(Segment.catchallHash)) return true;
        if (this.include_strings == null || this.include_strings.size() != 1) return false;
        return (this.include_strings.contains(Segment.catchallString));
    }

    public boolean containsInclude(String word) {
        if (word == null || word.length() == 0) return false;

        String t = word.toLowerCase(Locale.ENGLISH);
        return this.include_strings.contains(t) || this.include_words.contains(t);
    }

    public boolean matches(String text) {
        if (text == null || text.length() == 0) return false;

        // parse special requests
        if (isCatchall()) return true;

        String t = text.toLowerCase(Locale.ENGLISH);
        for (String i: this.include_strings) if (t.indexOf(i.toLowerCase()) < 0) return false;
        for (String e: this.exclude_strings) if (t.indexOf(e.toLowerCase()) >= 0) return false;
        return true;
    }

    public void filterOut(final SortedSet<String> blueList) {
        // filter out words that appear in this set
        // this is applied to the queryHashes
        for (String word: blueList) {
            this.include_words.remove(word);
            this.include_strings.remove(word);
        }
        final HandleSet blues = Word.words2hashesHandles(blueList);
        for (final byte[] b: blues) this.include_hashes.remove(b);
    }

    /**
     * Generate a Solr filter query to receive valid urls
     *
     * This filters out error-urls.
     * On noimages=true a filter is added to exclude links to images
     * using the content_type (as well as urls with common image file extension)
     *
     * @param noimages  true if filter for images should be included
     * @return Solr filter query
     */
    public List<String> collectionTextFilterQuery(boolean noimages) {
        final ArrayList<String> fqs = new ArrayList<>();

        // add filter to prevent that results come from failed urls
        fqs.add(CollectionSchema.httpstatus_i.getSolrFieldName() + ":" + HttpStatus.SC_OK);
        if (noimages) {
            fqs.add("-" + CollectionSchema.content_type.getSolrFieldName() + ":(image/*)");
            fqs.add("-" + CollectionSchema.url_file_ext_s.getSolrFieldName() + ":(jpg OR png OR gif)");
        }

        return fqs;
    }

    public StringBuilder collectionTextQuery() {

        // parse special requests
        if (isCatchall()) return new StringBuilder(AbstractSolrConnector.CATCHALL_QUERY);

        // add goal query
        return getGoalQuery();
    }

    /**
     * Generate a Solr filter query to receive valid image results.
     *
     * This filters error-urls out and includes urls with mime image/*, as well
     * as urls with links to images when strict is false.
     * We use the mime (image/*) only to find images as the parser assigned the
     * best mime to index documents. This applies also to parsed file systems.
     * This ensures that no text urls with image-fileextension is returned
     * (as some large internet sites like to use such urls)
     *
     * @param strict when true, do not include non-image urls with links to images
     * @return Solr filter query for image urls
     */
    public List<String> collectionImageFilterQuery(final boolean strict) {
        final ArrayList<String> fqs = new ArrayList<>();

        // add filter to prevent that results come from failed urls
        fqs.add(CollectionSchema.httpstatus_i.getSolrFieldName() + ":" + HttpStatus.SC_OK);
        StringBuilder filter = new StringBuilder(CollectionSchema.content_type.getSolrFieldName()).append(":(image/*)");
        if (!strict) {
            filter.append(" OR ").append(CollectionSchema.images_urlstub_sxt.getSolrFieldName())
                    .append(AbstractSolrConnector.CATCHALL_DTERM);
        }
        fqs.add(filter.toString());
        return fqs;
    }

    /**
     * Generate Solr filter queries to receive valid audio content results.
     *
     * This filters out documents with bad HTTP status and includes documents with MIME type matching the prefix audio/* as well
     * documents with links to audio content when strict is false.
     *
     * @param strict when true, do not include non-audio urls with links to audio
     * @return Solr filter queries for audio content URLs
     */
    public List<String> collectionAudioFilterQuery(final boolean strict) {
        final ArrayList<String> fqs = new ArrayList<>();

        // add filter to prevent that results come from failed urls
        fqs.add(CollectionSchema.httpstatus_i.getSolrFieldName() + ":" + HttpStatus.SC_OK);
        StringBuilder filter = new StringBuilder(CollectionSchema.content_type.getSolrFieldName()).append(":(audio/*)");
        if (!strict) {
            filter.append(" OR ").append(CollectionSchema.audiolinkscount_i.getSolrFieldName()).append(":[1 TO *]");
        }
        fqs.add(filter.toString());
        return fqs;
    }

    /**
     * Generate Solr filter queries to receive valid video content results.
     *
     * This filters out documents with bad HTTP status and includes documents with MIME type matching the prefix video/* as well
     * documents with links to video content when strict is false.
     *
     * @param strict when true, do not include non-video urls with links to video
     * @return Solr filter queries for video content URLs
     */
    public List<String> collectionVideoFilterQuery(final boolean strict) {
        final ArrayList<String> fqs = new ArrayList<>();

        // add filter to prevent that results come from failed urls
        fqs.add(CollectionSchema.httpstatus_i.getSolrFieldName() + ":" + HttpStatus.SC_OK);
        StringBuilder filter = new StringBuilder(CollectionSchema.content_type.getSolrFieldName()).append(":(video/*)");
        if (!strict) {
            filter.append(" OR ").append(CollectionSchema.videolinkscount_i.getSolrFieldName()).append(":[1 TO *]");
        }
        fqs.add(filter.toString());
        return fqs;
    }

    /**
     * Generate Solr filter queries to receive valid application specific content results.
     *
     * This filters out documents with bad HTTP status and includes documents with MIME type matching the prefix application/* as well
     * docuemnts with links to application specific content when strict is false.
     *
     * @param strict when true, do not include non-video urls with links to video
     * @return Solr filter queries for application specific content URLs
     */
    public List<String> collectionApplicationFilterQuery(final boolean strict) {
        final ArrayList<String> fqs = new ArrayList<>();

        // add filter to prevent that results come from failed urls
        fqs.add(CollectionSchema.httpstatus_i.getSolrFieldName() + ":" + HttpStatus.SC_OK);
        StringBuilder filter = new StringBuilder(CollectionSchema.content_type.getSolrFieldName())
                .append(":(application/*)");
        if (!strict) {
            filter.append(" OR ").append(CollectionSchema.applinkscount_i.getSolrFieldName()).append(":[1 TO *]");
        }
        fqs.add(filter.toString());
        return fqs;
    }

    public StringBuilder collectionImageQuery(final QueryModifier modifier) {
        final StringBuilder q = new StringBuilder(80);

        // parse special requests
        if (isCatchall()) return new StringBuilder(AbstractSolrConnector.CATCHALL_QUERY);

        // add goal query
        StringBuilder w = getGoalQuery();
        q.append(w);

        // combine these queries for all relevant fields
        if (w.length() > 0) {
            String hostname = modifier == null || modifier.sitehost == null || modifier.sitehost.length() == 0 ? null : Domains.getSmartSLD(modifier.sitehost);
            q.append(" AND (");
            q.append('(').append(CollectionSchema.images_text_t.getSolrFieldName()).append(':').append(hostname == null ? w : "(" + w + " " /*NOT an OR!, the hostname shall only boost*/ + hostname + ")").append("^100.0) OR ");
            q.append('(').append(CollectionSchema.title.getSolrFieldName()).append(':').append(w).append("^50.0) OR ");
            q.append('(').append(CollectionSchema.keywords.getSolrFieldName()).append(':').append(w).append("^10.0) OR ");
            q.append('(').append(CollectionSchema.text_t.getSolrFieldName()).append(':').append(w).append(')');
            q.append(')');
        }

        return q;
    }

    private StringBuilder getGoalQuery() {
        int wc = 0;
        StringBuilder w = new StringBuilder(80);
        for (String s: include_strings) {
            if (Segment.catchallString.equals(s)) continue;
            if (wc > 0) w.append(" AND ");
            if (s.indexOf('~') >= 0 || s.indexOf('*') >= 0 || s.indexOf('?') >= 0) w.append(s); else w.append(dq).append(s).append(dq);
            wc++;
        }
        for (String s: exclude_strings){
            if (wc > 0) w.append(" AND -");
            if (s.indexOf('~') >= 0 || s.indexOf('*') >= 0 || s.indexOf('?') >= 0) w.append(s); else w.append(dq).append(s).append(dq);
            wc++;
        }
        if (wc > 1) {w.insert(0, '('); w.append(')');}
        return w;
    }

}
