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

import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.document.Condenser;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.index.Segment;
import net.yacy.search.index.SolrConfiguration;

public class QueryGoal {
    
    private static String seps = "'.,/&_"; static {seps += '"';}
    
    private String querystring;
    private HandleSet include_hashes, exclude_hashes, all_hashes;
    private final ArrayList<String> include_words, exclude_words, all_words;

    public QueryGoal(HandleSet include_hashes, HandleSet exclude_hashes, HandleSet all_hashes) {
        this.querystring = null;
        this.include_words = null;
        this.exclude_words = null;
        this.all_words = null;
        this.include_hashes = include_hashes;
        this.exclude_hashes = exclude_hashes;
        this.all_hashes = all_hashes;
    }
    public QueryGoal(String querystring) {
        this.querystring = querystring;
        this.include_words = new ArrayList<String>();
        this.exclude_words = new ArrayList<String>();
        this.all_words = new ArrayList<String>();
        byte[] queryHash;
        if ((querystring.length() == 12) && (Base64Order.enhancedCoder.wellformed(queryHash = UTF8.getBytes(querystring)))) {
            this.querystring = null;
            this.include_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            this.exclude_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            this.all_hashes = new RowHandleSet(WordReferenceRow.urlEntryRow.primaryKeyLength, WordReferenceRow.urlEntryRow.objectOrder, 0);
            try {
                this.include_hashes.put(queryHash);
                this.all_hashes.put(queryHash);
            } catch (final SpaceExceededException e) {
                Log.logException(e);
            }
        } else if ((querystring != null) && (!querystring.isEmpty())) {

            // remove funny symbols
            querystring = CharacterCoding.html2unicode(AbstractScraper.stripAllTags(querystring.toCharArray())).toLowerCase().trim();
            int c;
            for (int i = 0; i < seps.length(); i++) {
                while ((c = querystring.indexOf(seps.charAt(i))) >= 0) {
                    querystring = querystring.substring(0, c) + (((c + 1) < querystring.length()) ? (' ' + querystring.substring(c + 1)) : "");
                }
            }

            String s;
            int l;
            // the string is clean now, but we must generate a set out of it
            final String[] queries = querystring.split(" ");
            for (String quer : queries) {
                if (quer.startsWith("-")) {
                    String x = quer.substring(1);
                    if (!exclude_words.contains(x)) exclude_words.add(x);
                } else {
                    while ((c = quer.indexOf('-')) >= 0) {
                        s = quer.substring(0, c);
                        l = s.length();
                        if (l >= Condenser.wordminsize && !include_words.contains(s)) {include_words.add(s);}
                        if (l > 0 && !all_words.contains(s)) {all_words.add(s);}
                        quer = quer.substring(c + 1);
                    }
                    l = quer.length();
                    if (l >= Condenser.wordminsize && !include_words.contains(quer)) {include_words.add(quer);}
                    if (l > 0 && !all_words.contains(quer)) {all_words.add(quer);}
                }
            }
        }
        
        this.include_hashes = null;
        this.exclude_hashes = null;
        this.all_hashes = null;
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

    public ArrayList<String> getIncludeWords() {
        return include_words;
    }

    public ArrayList<String> getExcludeWords() {
        return exclude_words;
    }

    public ArrayList<String> getAllWords() {
        return all_words;
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
    
    private final static Map<YaCySchema,Float> boosts = new LinkedHashMap<YaCySchema,Float>();
    static {
        boosts.put(YaCySchema.sku, 20.0f);
        boosts.put(YaCySchema.url_paths_sxt, 20.0f);
        boosts.put(YaCySchema.title, 15.0f);
        boosts.put(YaCySchema.h1_txt, 11.0f);
        boosts.put(YaCySchema.h2_txt, 10.0f);
        boosts.put(YaCySchema.author, 8.0f);
        boosts.put(YaCySchema.description, 5.0f);
        boosts.put(YaCySchema.keywords, 2.0f);
        boosts.put(YaCySchema.text_t, 1.0f);
    }
    
    public StringBuilder solrQueryString(SolrConfiguration configuration) {
        final StringBuilder q = new StringBuilder(80);

        // parse special requests
        if (include_words.size() == 1 && exclude_words.size() == 0) {
            String w = include_words.get(0);
            if (Segment.catchallString.equals(w)) return new StringBuilder("*:*");
        }
        
        // add text query
        int wc = 0;
        StringBuilder w = new StringBuilder(80);
        for (String s: include_words) {
            if (wc > 0) w.append(" AND ");
            w.append(s);
            wc++;
        }
        for (String s: exclude_words){
            if (wc > 0) w.append(" AND -");
            w.append(s);
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
            boost = boosts.get(field);
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
