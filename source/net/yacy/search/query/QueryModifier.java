/**
 *  QueryModifier
 *  Copyright 2013 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 12.02.2013 on http://yacy.net
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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.document.id.Punycode.PunycodeException;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.DateDetection;
import net.yacy.kelondro.util.ISO639;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;

/**
 * Handle search query modifiers 
 */
public class QueryModifier {

    private final StringBuilder modifier;
    public String sitehost, sitehash, filetype, protocol, language, author, keyword, collection, on, from, to;
    public int timezoneOffset;
    
    public QueryModifier(final int timezoneOffset) {
        this.timezoneOffset = timezoneOffset;
        this.sitehash = null;
        this.sitehost = null;
        this.filetype = null;
        this.protocol = null;
        this.language = null;
        this.author = null;
        this.keyword = null;
        this.collection = null;
        this.on = null;
        this.from = null;
        this.to = null;
        this.modifier = new StringBuilder(20);
    }

    /**
     * Parse query string for query modifier parameters
     * @param querystring
     * @return query string with parameters removed
     */
    public String parse(String querystring) {

        // parse protocol
        if ( querystring.indexOf("/https", 0) >= 0 ) {
            querystring = querystring.replace("/https", "");
            this.protocol = "https";
            add("/https");
        } else if ( querystring.indexOf("/http", 0) >= 0 ) {
            querystring = querystring.replace("/http", "");
            this.protocol = "http";
            add("/http");
        }
        if ( querystring.indexOf("/ftp", 0) >= 0 ) {
            querystring = querystring.replace("/ftp", "");
            this.protocol = "ftp";
            add("/ftp");
        }
        if ( querystring.indexOf("/smb", 0) >= 0 ) {
            querystring = querystring.replace("/smb", "");
            this.protocol = "smb";
            add("/smb");
        }
        if ( querystring.indexOf("/file", 0) >= 0 ) {
            querystring = querystring.replace("/file", "");
            this.protocol = "file";
            add("/file");
        }
        
        // parse 'common search mistakes' like guessed regular expressions
        // (changes  "abc*" to "ab",   "abc *def" to "abcdef")  TODO: handle most common  "abc*"
        int p = querystring.indexOf('*');
        if ((p >= 0) && ((p > 0 && querystring.charAt(p - 1) != ' ') || (p > 1 && p < querystring.length() - 1 && querystring.charAt(p + 1) != ' '))) {
            querystring = querystring.substring(0, p - 1) + querystring.substring(p + 1);
        }

        // parse filetype
        querystring = filetypeParser(querystring, "filetype:");
        
        // parse site
        querystring = parseSiteModifier(querystring);
        
        // parse author
        final int authori = querystring.indexOf("author:", 0);
        if (authori >= 0) {
            // check if the author was given with single quotes or without
            final boolean quotes = (querystring.charAt(authori + 7) == '(');
            if ( quotes ) {
                int ftb = querystring.indexOf(')', authori + 8);
                this.author = querystring.substring(authori + 8, ftb == -1 ? querystring.length() : ftb);
                querystring = querystring.replace("author:(" + this.author + ")", "");
                add("author:(" + author + ")");
            } else {
                int ftb = querystring.indexOf(' ', authori);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                this.author = querystring.substring(authori + 7, ftb);
                querystring = querystring.replace("author:" + this.author, "").replace("  ", " ").trim();
                add("author:" + author);
            }
        }

        // parse keyword
        final int keywordi = querystring.indexOf("keyword:", 0);
        if (keywordi >= 0) {
            // TODO: should we handle quoted keywords (to allow space) and comma separated list ?
            int ftb = querystring.indexOf(' ', keywordi);
            this.keyword = querystring.substring(keywordi + 8, ftb == -1 ? querystring.length() : ftb);
            querystring = querystring.replace("keyword:" + this.keyword, "").replace("  ", " ").trim();
            add("keyword:" + this.keyword);
        }

        // parse collection
        int collectioni = querystring.indexOf("collection:", 0);
        while (collectioni >= 0) { // due to possible collision with "on:" modifier make sure no "collection:" remains
            int ftb = querystring.indexOf(' ', collectioni);
            this.collection = querystring.substring(collectioni + 11, ftb == -1 ? querystring.length() : ftb);
            querystring = querystring.replace("collection:" + this.collection, "").replace("  ", " ").trim();
            collectioni = querystring.indexOf("collection:", 0);
        }
        if (this.collection != null) add("collection:" + this.collection);
        
        // parse on-date, must be after "collection:" as "on:" contained in it
        final int oni = querystring.indexOf("on:", 0);
        if (oni >= 0) {
            int ftb = querystring.indexOf(' ', oni);
            this.on = querystring.substring(oni + 3, ftb == -1 ? querystring.length() : ftb);
            querystring = querystring.replace("on:" + this.on, "").replace("  ", " ").trim();
            add("on:" + this.on);
        }
        
        // parse from-date
        final int fromi = querystring.indexOf("from:", 0);
        if (fromi >= 0) {
            int ftb = querystring.indexOf(' ', fromi);
            this.from = querystring.substring(fromi + 5, ftb == -1 ? querystring.length() : ftb);
            querystring = querystring.replace("from:" + this.from, "").replace("  ", " ").trim();
            add("from:" + this.from);
        }
        
        // parse to-date
        final int toi = querystring.indexOf("to:", 0);
        if (toi >= 0) {
            int ftb = querystring.indexOf(' ', toi);
            this.to = querystring.substring(toi + 3, ftb == -1 ? querystring.length() : ftb);
            querystring = querystring.replace("to:" + this.to, "").replace("  ", " ").trim();
            add("to:" + this.to);
        }

        // parse language
        final int langi = querystring.indexOf("/language/");
        if (langi >= 0) {
            if (querystring.length() >= (langi + 12)) {
                this.language = querystring.substring(langi + 10, langi + 12);
                querystring = querystring.replace("/language/" + this.language, "");
                if (this.language.length() == 2 && ISO639.exists(this.language)) { // only 2-digit codes valid
                    this.language = this.language.toLowerCase(Locale.ROOT);
                    add("/language/" + this.language);
                } else {
                    this.language = null;
                }
            }
        }
        
        // check the number of quotes in the string; if there is only one double-quote, add another one. this will prevent error messages in 
        p = querystring.indexOf('"');
        if (p >= 0) {
            int q = querystring.indexOf('"', p + 1);
            if (q < 0) querystring += '"';
        }
        
        return querystring.trim();
    }

    /**
     * Parse query string for filetype (file extension) parameter
     * and adjust parameter to lowercase
     * @param querystring
     * @param filetypePrefix "filetype:"
     * @return querystring with filetype parameter removed
     */
    private String filetypeParser(String querystring, final String filetypePrefix) {
        final int ftp = querystring.indexOf(filetypePrefix, 0);
        if ( ftp >= 0 ) {
            int ftb = querystring.indexOf(' ', ftp);
            if ( ftb < 0 ) ftb = querystring.length();
            String tmpqueryparameter = querystring.substring(ftp + filetypePrefix.length(), ftb);
            querystring = querystring.replace(filetypePrefix + tmpqueryparameter, ""); // replace prefix:Text  as found
            filetype = tmpqueryparameter.toLowerCase(Locale.ROOT); // file extension are always compared lowercase, can be converted here for further processing
            while ( !filetype.isEmpty() && filetype.charAt(0) == '.' ) {
                filetype = filetype.substring(1);
            }
            add(filetypePrefix + filetype);
            if (filetype.isEmpty()) filetype = null;
            if (querystring.length() == 0) querystring = "*";
        }
        return querystring;
    }
    
	/**
	 * Parses the query string for any eventual site modifier (site:), adjust it to
	 * lower case, and fill the {@link #sitehost} and {@link #sitehash} attributes
	 * accordingly.
	 * 
	 * @param querystring
	 *            the query string. Must not be null.
	 * @return the query string with site operator removed
	 */
	protected String parseSiteModifier(String querystring) {
		final String modifierPrefix = "site:";
		final int sp = querystring.indexOf(modifierPrefix, 0);
        if (sp >= 0) {
            int ftb = querystring.indexOf(' ', sp);
            if ( ftb == -1 ) {
                ftb = querystring.length();
            }
            this.sitehost = querystring.substring(sp + modifierPrefix.length(), ftb);
            querystring = querystring.replace(modifierPrefix + this.sitehost, "");
            while ( this.sitehost.length() > 0 && this.sitehost.charAt(0) == '.' ) {
                this.sitehost = this.sitehost.substring(1);
            }
            while ( sitehost.endsWith(".") ) {
                this.sitehost = this.sitehost.substring(0, this.sitehost.length() - 1);
            }
            
            try {
            	/* Internationalized domain names support : convert to the same ASCII Compatible Encoding (ACE) representation that is used in normalized URLs */
				this.sitehost = MultiProtocolURL.toPunycode(this.sitehost);
			} catch (final PunycodeException e1) {
                ConcurrentLog.logException(e1);
			}
            
            /* Domain name in an URL is case insensitive : convert now modifier to lower case for further processing over normalized URLs */
            this.sitehost = this.sitehost.toLowerCase(Locale.ROOT);
            
            try {
                this.sitehash = DigestURL.hosthash(this.sitehost, this.sitehost.startsWith("ftp.") ? 21 : 80);
            } catch (MalformedURLException e) {
                this.sitehash = "";
                ConcurrentLog.logException(e);
            }
            add(modifierPrefix + this.sitehost);
        }
		return querystring;
	}
    
    public void add(String m) {
        if (modifier.length() > 0 && modifier.charAt(modifier.length() - 1) != ' ' && m != null && m.length() > 0) modifier.append(' ');
        if (m != null) modifier.append(m);
    }
    
    public void remove(String m) {
        int p = modifier.indexOf(" " + m);
        if (p >= 0) modifier.delete(p, p + m.length() + 1);
        p = modifier.indexOf(m);
        if (p == 0) modifier.delete(p, p + m.length());
        if (modifier.length() > 0 && modifier.charAt(0) == ' ') modifier.delete(0, 1);
    }
    
    @Override
    public String toString() {
        return this.modifier.toString();
    }

    /**
     * @return true if no modifier active
     */
    public boolean isEmpty() {
        return this.modifier.length() == 0;
    }

    private StringBuilder apply(String FQ) {
        
        final StringBuilder fq = new StringBuilder(FQ);
        
        if (this.sitehost != null && this.sitehost.length() > 0 && fq.indexOf(CollectionSchema.host_s.getSolrFieldName()) < 0) {
            // consider to search for hosts with 'www'-prefix, if not already part of the host name
            if (this.sitehost.startsWith("www.")) {
                fq.append(" AND (").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"").append(this.sitehost.substring(4)).append('\"');
                fq.append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"").append(this.sitehost).append("\")");
            } else {
                fq.append(" AND (").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"").append(this.sitehost).append('\"');
                fq.append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(":\"www.").append(this.sitehost).append("\")");
            }
        }
        if (this.sitehash != null && this.sitehash.length() > 0 && fq.indexOf(CollectionSchema.host_id_s.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(CollectionSchema.host_id_s.getSolrFieldName()).append(":\"").append(this.sitehash).append('\"');
        }

        if (this.filetype != null && this.filetype.length() > 0 && fq.indexOf(CollectionSchema.url_file_ext_s.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(CollectionSchema.url_file_ext_s.getSolrFieldName()).append(":\"").append(this.filetype).append('\"');
        }
        
        if (this.author != null && this.author.length() > 0 && fq.indexOf(CollectionSchema.author_sxt.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(CollectionSchema.author_sxt.getSolrFieldName()).append(":\"").append(this.author).append('\"');
        }

        if (this.keyword != null && this.keyword.length() > 0 && fq.indexOf(CollectionSchema.keywords.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(CollectionSchema.keywords.getSolrFieldName()).append(":\"").append(this.keyword).append('\"');
        }

        if (this.collection != null && this.collection.length() > 0 && fq.indexOf(CollectionSchema.collection_sxt.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(QueryModifier.parseCollectionExpression(this.collection));
        }
        
        if (fq.indexOf(CollectionSchema.dates_in_content_dts.getSolrFieldName()) < 0) {
            if (this.on != null && this.on.length() > 0) {
                fq.append(" AND ").append(QueryModifier.parseOnExpression(this.on, this.timezoneOffset));
            }
            
            if (this.from != null && this.from.length() > 0 && (this.to == null || this.to.equals("*"))) {
                fq.append(" AND ").append(QueryModifier.parseFromToExpression(this.from, null, this.timezoneOffset));
            }
            
            if ((this.from == null || this.from.equals("*")) && this.to != null && this.to.length() > 0) {
                fq.append(" AND ").append(QueryModifier.parseFromToExpression(null, this.to, this.timezoneOffset));
            }
            
            if (this.from != null && this.from.length() > 0 && this.to != null && this.to.length() > 0) {
                fq.append(" AND ").append(QueryModifier.parseFromToExpression(this.from, this.to, this.timezoneOffset));
            }
        }
        
        if (this.protocol != null && this.protocol.length() > 0 && fq.indexOf(CollectionSchema.url_protocol_s.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(CollectionSchema.url_protocol_s.getSolrFieldName()).append(":\"").append(this.protocol).append('\"');
        }

        return fq;
    }
    
    public void apply(serverObjects post) {
        
        final StringBuilder fq = apply(post.get(CommonParams.FQ,""));
        
        if (fq.length() > 0) {
            String fqs = fq.toString();
            if (fqs.startsWith(" AND ")) fqs = fqs.substring(5);
            post.remove(CommonParams.FQ);
            post.put(CommonParams.FQ, fqs);
        }
    }

    public void apply(MultiMapSolrParams mmsp) {

        final StringBuilder fq = apply(mmsp.get(CommonParams.FQ,""));

        if (fq.length() > 0) {
            String fqs = fq.toString();
            if (fqs.startsWith(" AND ")) fqs = fqs.substring(5);
            mmsp.getMap().remove(CommonParams.FQ);
            mmsp.getMap().put(CommonParams.FQ, new String[]{fqs});
        }
    }
    
    /**
     * parse a GSA site description string and create a filter query string
     * which is used to restrict the search result to collections as named with the site attributes
     * @param collectionDescription
     * @return a solr query string which shall be used for a filter query
     */
    public static String parseCollectionExpression(String collectionDescription) {
        String[] s0 = CommonPattern.VERTICALBAR.split(collectionDescription);
        ArrayList<String> collections = new ArrayList<String>(2);
        for (String s: s0) {
            s = s.trim();
            if (s.length() > 0) collections.add(s);
        }
        StringBuilder fq = new StringBuilder(20);
        if (collections.size() > 1) {
            fq.append('(').append(CollectionSchema.collection_sxt.getSolrFieldName()).append(":\"").append(collections.get(0)).append('\"');
            for (int i = 1; i < collections.size(); i++) {
                fq.append(" OR ").append(CollectionSchema.collection_sxt.getSolrFieldName()).append(":\"").append(collections.get(i)).append('\"');
            }
            fq.append(')');
        } else if (collections.size() == 1) {
            fq.append(CollectionSchema.collection_sxt.getSolrFieldName()).append(":\"").append(collections.get(0)).append('\"');
        }
        if (fq.length() > 0) fq.insert(0, "{!tag=" + CollectionSchema.collection_sxt.getSolrFieldName() + "}"); 
        return fq.toString();
    }
    
    public static String parseOnExpression(final String onDescription, final int timezoneOffset) {
        assert onDescription != null;
        Date onDate = DateDetection.parseLine(onDescription, timezoneOffset);
        StringBuilder filterQuery = new StringBuilder(20);
        if (onDate != null) {
            String dstr = onDate.toInstant().toString();
            filterQuery.append(CollectionSchema.dates_in_content_dts.getSolrFieldName()).append(":[").append(dstr).append(" TO ").append(dstr).append(']'); 
        }
        return filterQuery.toString();
    }
    
    public static String parseFromToExpression(final String from, final String to, final int timezoneOffset) {
        Date fromDate = from == null || from.equals("*") ? null : DateDetection.parseLine(from, timezoneOffset);
        Date toDate = to == null || to.equals("*") ? null : DateDetection.parseLine(to, timezoneOffset);
        StringBuilder filterQuery = new StringBuilder(20);
        if (fromDate != null && toDate != null) {
            String dstrFrom = fromDate.toInstant().toString();
            String dstrTo = toDate.toInstant().toString();
            filterQuery.append(CollectionSchema.dates_in_content_dts.getSolrFieldName()).append(":[").append(dstrFrom).append(" TO ").append(dstrTo).append(']'); 
        }
        return filterQuery.toString();
    }
    
}
