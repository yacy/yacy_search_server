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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.HandleSet;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.SetTools;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

public class QueryGoal {

    private static char space = ' ';
    private static char sq = '\'';
    private static char dq = '"';
    private static String seps = ".:;#'*`,!$%()=?^<>/&_";
    
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
     * Creates a QueryGoal from a serach query string
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
 * phrase     = ['-'], string
 * string     = {any character without sq, dq and whitespace} | sq, {any character without sq}, sq | dq, {any character without dq}, dq
 * sq         = '\''
 * dq         = '"'
 */
    private static void parseQuery(String s, Collection<String> include_string, Collection<String> exclude_string) {
        while (s.length() > 0) {
            // parse query
            int p = 0;
            while (p < s.length() && s.charAt(p) == space) p++;
            s = s.substring(p);
            if (s.length() == 0) return;

            // parse phrase
            boolean inc = true;
            if (s.charAt(0) == '-') {inc = false; s = s.substring(1);}
            if (s.charAt(0) == '+') {inc = true; s = s.substring(1);}
            if (s.length() == 0) return;
            
            // parse string
            char stop = space;
            if (s.charAt(0) == dq) {stop = s.charAt(0); s = s.substring(1);}
            if (s.charAt(0) == sq) {stop = s.charAt(0); s = s.substring(1);}
            p = 0;
            while (p < s.length() && s.charAt(p) != stop) p++;
            String string = s.substring(0, p);
            p++; // go behind the stop character (eats up space, sq and dq)
            s = p < s.length() ? s.substring(p) : "";
            if (string.length() > 0) {
                if (inc) {
                    if (!include_string.contains(string)) include_string.add(string);
                } else {
                    if (!exclude_string.contains(string)) exclude_string.add(string);
                }
            }
        }
    }

    /**
     * Search query string (without YaCy specific modifier like site:xxx or /smb)
     * the modifier are held separately in a search paramter modifier
     *
     * @param encodeHTML
     * @return
     */
    public String getQueryString(final boolean encodeHTML) {
        if (this.query_original == null) return null;
        String ret;
        if (encodeHTML){
            try {
                ret = URLEncoder.encode(this.query_original, "UTF-8");
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
     * @return a set of words to be included in the search result
     */
    public Iterator<String> getIncludeWords() {
        return this.include_words.iterator();
    }

    /**
     * @return a set of words to be excluded in the search result
     */
    public Iterator<String> getExcludeWords() {
        return this.exclude_words.iterator();
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
        if (include_strings.size() != 1 || exclude_strings.size() != 0) return false;
        String w = include_strings.get(0);
        return (Segment.catchallString.equals(w));
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

    public StringBuilder collectionTextQueryString(CollectionConfiguration configuration, int rankingProfile, boolean noimages) {
        final StringBuilder q = new StringBuilder(80);

        // add filter to prevent that results come from failed urls
        q.append(CollectionSchema.httpstatus_i.getSolrFieldName()).append(":200");
        if (noimages) q.append(" AND -").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":(jpg OR png OR gif)");
        
        // parse special requests
        if (isCatchall()) return q;
        
        q.append(" AND (");
        
        // add goal query
        int wc = 0;
        StringBuilder w = getGoalQuery();
        
        // combine these queries for all relevant fields
        wc = 0;
        Float boost;
        Ranking r = configuration.getRanking(rankingProfile);
        for (Map.Entry<SchemaDeclaration,Float> entry: r.getBoostMap()) {
            SchemaDeclaration field = entry.getKey();
            boost = entry.getValue();
            if (boost == null || boost.floatValue() <= 0.0f) continue;
            if (configuration != null && !configuration.contains(field.getSolrFieldName())) continue;
            if (field.getType() == SolrType.num_integer) continue;
            if (wc > 0) q.append(" OR ");
            q.append('(');
            q.append(field.getSolrFieldName()).append(':').append(w);
            if (boost != null) q.append('^').append(boost.toString());
            q.append(')');
            wc++;
        }
        q.append(')');

        return q;
    }
    
    public StringBuilder collectionImageQueryString() {
        final StringBuilder q = new StringBuilder(80);

        // add filter to prevent that results come from failed urls
        q.append(CollectionSchema.httpstatus_i.getSolrFieldName()).append(":200").append(" AND (");
        q.append(CollectionSchema.images_urlstub_sxt.getSolrFieldName()).append(AbstractSolrConnector.CATCHALL_DTERM + " OR ");
        q.append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":(jpg OR png OR gif) OR ");
        q.append(CollectionSchema.content_type.getSolrFieldName()).append(":(image/*))");
        
        // parse special requests
        if (isCatchall()) return q;

        // add goal query
        StringBuilder w = getGoalQuery();
        
        // combine these queries for all relevant fields
        q.append(" AND (");
        q.append('(').append(CollectionSchema.images_text_t.getSolrFieldName()).append(':').append(w).append("^10.0) OR ");
        q.append('(').append(CollectionSchema.text_t.getSolrFieldName()).append(':').append(w).append(')');
        q.append(')');

        return q;
    }
    
    private StringBuilder getGoalQuery() {
        int wc = 0;
        StringBuilder w = new StringBuilder(80);
        for (String s: include_strings) {
            if (wc > 0) w.append(" AND ");
            w.append(dq).append(s).append(dq);
            wc++;
        }
        for (String s: exclude_strings){
            if (wc > 0) w.append(" AND -");
            w.append(dq).append(s).append(dq);
            wc++;
        }
        if (wc > 1) {w.insert(0, '('); w.append(')');}
        return w;
    }

}
