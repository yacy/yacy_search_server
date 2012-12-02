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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;

import net.yacy.cora.federate.solr.Boost;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.index.Segment;
import net.yacy.search.index.SolrConfiguration;

public class QueryGoal {


    private static char space = ' ';
    private static char sq = '\'';
    private static char dq = '"';
    private static String seps = ".,/&_";
    
    private String querystring;
    private HandleSet include_hashes, exclude_hashes, all_hashes;
    private final ArrayList<String> include_words, exclude_words, all_words;
    private final ArrayList<String> include_strings, exclude_strings, all_strings;


    public QueryGoal(HandleSet include_hashes, HandleSet exclude_hashes, HandleSet all_hashes) {
        this.querystring = null;
        this.include_words = null;
        this.exclude_words = null;
        this.all_words = null;
        this.include_strings = null;
        this.exclude_strings = null;
        this.all_strings = null;
        this.include_hashes = include_hashes;
        this.exclude_hashes = exclude_hashes;
        this.all_hashes = all_hashes;
    }

    public QueryGoal(byte[] queryHash) {
        assert querystring != null;
        assert queryHash.length == 12;
        assert Base64Order.enhancedCoder.wellformed(queryHash);
        this.querystring = null;
        this.include_words = new ArrayList<String>();
        this.exclude_words = new ArrayList<String>();
        this.all_words = new ArrayList<String>();
        this.include_strings = new ArrayList<String>();
        this.exclude_strings = new ArrayList<String>();
        this.all_strings = new ArrayList<String>();
        this.include_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        this.exclude_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        this.all_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
        try {
            this.include_hashes.put(queryHash);
            this.all_hashes.put(queryHash);
        } catch (final SpaceExceededException e) {
            Log.logException(e);
        }
        this.include_hashes = null;
        this.exclude_hashes = null;
        this.all_hashes = null;
    }
    
    public QueryGoal(String querystring) {
        assert querystring != null;
        this.querystring = querystring;
        this.include_words = new ArrayList<String>();
        this.exclude_words = new ArrayList<String>();
        this.all_words = new ArrayList<String>();
        this.include_strings = new ArrayList<String>();
        this.exclude_strings = new ArrayList<String>();
        this.all_strings = new ArrayList<String>();

        // remove funny symbols
        querystring = CharacterCoding.html2unicode(AbstractScraper.stripAllTags(querystring.toCharArray())).toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            while ((c = querystring.indexOf(seps.charAt(i))) >= 0) {
                querystring = querystring.substring(0, c) + (((c + 1) < querystring.length()) ? (' ' + querystring.substring(c + 1)) : "");
            }
        }

        // parse first quoted strings
        parseQuery(querystring, this.include_strings, this.exclude_strings, this.all_strings);

        // .. end then take these strings apart to generate word lists
        for (String s: this.include_strings) parseQuery(s, this.include_words, this.include_words, this.all_words);
        for (String s: this.exclude_strings) parseQuery(s, this.exclude_words, this.exclude_words, this.all_words);

        this.include_hashes = null;
        this.exclude_hashes = null;
        this.all_hashes = null;
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
    private static void parseQuery(String s, ArrayList<String> include_string, ArrayList<String> exclude_string, ArrayList<String> all_string) {
        while (s.length() > 0) {
            // parse query
            int p = 0;
            while (p < s.length() && s.charAt(p) == space) p++;
            s = s.substring(p);
            if (s.length() == 0) return;

            // parse phrase
            boolean inc = true;
            if (s.charAt(0) == '-') {inc = false; s = s.substring(1);}
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
                if (!all_string.contains(string)) all_string.add(string);
                if (inc) {
                    if (!include_string.contains(string)) include_string.add(string);
                } else {
                    if (!exclude_string.contains(string)) exclude_string.add(string);
                }
            }
        }
    }

    public String getQueryString() {
        return this.querystring;
    }

    public String queryStringForUrl() {
        try {
            return URLEncoder.encode(this.querystring, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            Log.logException(e);
            return this.querystring;
        }
    }
    
    public HandleSet getIncludeHashes() {
        if (include_hashes == null) include_hashes = Word.words2hashesHandles(include_words);
        return include_hashes;
    }

    public HandleSet getExcludeHashes() {
        if (exclude_hashes == null) exclude_hashes = Word.words2hashesHandles(exclude_words);
        return exclude_hashes;
    }

    public HandleSet getAllHashes() {
        if (all_hashes == null) all_hashes = Word.words2hashesHandles(all_words);
        return all_hashes;
    }
    
    public ArrayList<String> getIncludeStrings() {
        return include_strings;
    }
    
    public ArrayList<String> getExcludeStrings() {
        return exclude_strings;
    }
    
    public ArrayList<String> getAllStrings() {
        return all_strings;
    }
    
    public void filterOut(final SortedSet<String> blueList) {
        // filter out words that appear in this set
        // this is applied to the queryHashes
        final HandleSet blues = Word.words2hashesHandles(blueList);
        for (final byte[] b: blues) this.include_hashes.remove(b);
    }

    private final static YaCySchema[] fields = new YaCySchema[]{
        YaCySchema.sku,YaCySchema.title,YaCySchema.h1_txt,YaCySchema.h2_txt,
        YaCySchema.author,YaCySchema.description,YaCySchema.keywords,YaCySchema.text_t,YaCySchema.synonyms_sxt
    };
    
    public StringBuilder solrQueryString(SolrConfiguration configuration) {
        final StringBuilder q = new StringBuilder(80);

        // parse special requests
        if (include_strings.size() == 1 && exclude_strings.size() == 0) {
            String w = include_strings.get(0);
            if (Segment.catchallString.equals(w)) return new StringBuilder("*:*");
        }
        
        // add text query
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
        
        // combine these queries for all relevant fields
        wc = 0;
        Float boost;
        for (YaCySchema field: fields) {
            if (configuration != null && !configuration.contains(field.getSolrFieldName())) continue;
            if (wc > 0) q.append(" OR ");
            q.append('(');
            q.append(field.getSolrFieldName()).append(':').append(w);
            boost = Boost.RANKING.get(field);
            if (boost != null) q.append('^').append(boost.toString());
            q.append(')');
            wc++;
        }
        q.insert(0, '(');
        q.append(')');

        // add filter to prevent that results come from failed urls
        q.append(" AND -").append(YaCySchema.failreason_t.getSolrFieldName()).append(":[* TO *]");

        return q;
    }

}
