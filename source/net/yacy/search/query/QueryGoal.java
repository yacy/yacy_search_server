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
import java.util.Map;
import java.util.SortedSet;

import net.yacy.cora.document.WordCache;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.storage.HandleSet;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.word.Word;
import net.yacy.search.index.Segment;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

public class QueryGoal {


    private static char space = ' ';
    private static char sq = '\'';
    private static char dq = '"';
    private static String seps = ".,/&_";
    
    private String query_original;
    private HandleSet include_hashes, exclude_hashes, all_hashes;
    private final ArrayList<String> include_words, exclude_words, all_words;
    private final ArrayList<String> include_strings, exclude_strings, all_strings;


    public QueryGoal(HandleSet include_hashes, HandleSet exclude_hashes, HandleSet all_hashes) {
        this.query_original = null;
        this.include_words = new ArrayList<String>();
        this.exclude_words = new ArrayList<String>();
        this.all_words = new ArrayList<String>();
        this.include_strings = new ArrayList<String>();
        this.exclude_strings = new ArrayList<String>();
        this.all_strings = new ArrayList<String>();
        this.include_hashes = include_hashes;
        this.exclude_hashes = exclude_hashes;
        this.all_hashes = all_hashes;
    }
    
    public QueryGoal(String query_original, String query_words) {
        assert query_original != null;
        assert query_words != null;
        this.query_original = query_original;
        this.include_words = new ArrayList<String>();
        this.exclude_words = new ArrayList<String>();
        this.all_words = new ArrayList<String>();
        this.include_strings = new ArrayList<String>();
        this.exclude_strings = new ArrayList<String>();
        this.all_strings = new ArrayList<String>();

        // remove funny symbols
        query_words = CharacterCoding.html2unicode(AbstractScraper.stripAllTags(query_words.toCharArray())).toLowerCase().trim();
        int c;
        for (int i = 0; i < seps.length(); i++) {
            while ((c = query_words.indexOf(seps.charAt(i))) >= 0) {
                query_words = query_words.substring(0, c) + (((c + 1) < query_words.length()) ? (' ' + query_words.substring(c + 1)) : "");
            }
        }

        // parse first quoted strings
        parseQuery(query_words, this.include_strings, this.exclude_strings, this.all_strings);

        // .. end then take these strings apart to generate word lists
        for (String s: this.include_strings) parseQuery(s, this.include_words, this.include_words, this.all_words);
        for (String s: this.exclude_strings) parseQuery(s, this.exclude_words, this.exclude_words, this.all_words);

        WordCache.learn(this.include_strings);
        WordCache.learn(this.exclude_strings);
        
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

    public String getOriginalQueryString(final boolean encodeHTML) {
        if (this.query_original == null) return null;
        String ret;
        if (encodeHTML){
            try {
                ret = URLEncoder.encode(this.query_original, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                ret = this.query_original;
            }
        } else {
            ret = this.query_original;
        }
        return ret;
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
    
    public StringBuilder collectionQueryString(CollectionConfiguration configuration, int rankingProfile) {
        final StringBuilder q = new StringBuilder(80);

        // parse special requests
        if (include_strings.size() == 1 && exclude_strings.size() == 0) {
            String w = include_strings.get(0);
            if (Segment.catchallString.equals(w)) return new StringBuilder(AbstractSolrConnector.CATCHALL_TERM);
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
        q.insert(0, '(');
        q.append(')');

        // add filter to prevent that results come from failed urls
        q.append(" AND ").append(CollectionSchema.httpstatus_i.getSolrFieldName()).append(":200");
        //q.append(" AND -").append(YaCySchema.failreason_t.getSolrFieldName()).append(":[* TO *]");

        return q;
    }

}
