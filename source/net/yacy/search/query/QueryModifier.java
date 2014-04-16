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

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.ISO639;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;


public class QueryModifier {

    private final StringBuilder modifier;
    public String sitehost, sitehash, filetype, protocol, language, author, collection;
    
    public QueryModifier() {
        this.sitehash = null;
        this.sitehost = null;
        this.filetype = null;
        this.protocol = null;
        this.language = null;
        this.author = null;
        this.collection = null;
        this.modifier = new StringBuilder(20);
    }
    
    public String parse(String querystring) {

        // parse protocol
        if ( querystring.indexOf("/https", 0) >= 0 ) {
            querystring = querystring.replace("/https", "");
            protocol = "https";
            add("/https");
        } else if ( querystring.indexOf("/http", 0) >= 0 ) {
            querystring = querystring.replace("/http", "");
            protocol = "http";
            add("/http");
        }
        if ( querystring.indexOf("/ftp", 0) >= 0 ) {
            querystring = querystring.replace("/ftp", "");
            protocol = "ftp";
            add("/ftp");
        }
        if ( querystring.indexOf("/smb", 0) >= 0 ) {
            querystring = querystring.replace("/smb", "");
            protocol = "smb";
            add("/smb");
        }
        if ( querystring.indexOf("/file", 0) >= 0 ) {
            querystring = querystring.replace("/file", "");
            protocol = "file";
            add("/file");
        }
        
        // parse 'common search mistakes' like guessed regular expressions
        querystring = filetypeParser(querystring, "*");
        
        // parse filetype
        querystring = filetypeParser(querystring, "filetype:");
        
        // parse site
        final int sp = querystring.indexOf("site:", 0);
        if ( sp >= 0 ) {
            int ftb = querystring.indexOf(' ', sp);
            if ( ftb == -1 ) {
                ftb = querystring.length();
            }
            sitehost = querystring.substring(sp + 5, ftb);
            querystring = querystring.replace("site:" + sitehost, "");
            while ( sitehost.length() > 0 && sitehost.charAt(0) == '.' ) {
                sitehost = sitehost.substring(1);
            }
            while ( sitehost.endsWith(".") ) {
                sitehost = sitehost.substring(0, sitehost.length() - 1);
            }
            try {
                sitehash = DigestURL.hosthash(sitehost, sitehost.startsWith("ftp.") ? 21 : 80);
            } catch (MalformedURLException e) {
                sitehash = "";
                ConcurrentLog.logException(e);
            }
            add("site:" + sitehost);
        }
        
        // parse author
        final int authori = querystring.indexOf("author:", 0);
        if ( authori >= 0 ) {
            // check if the author was given with single quotes or without
            final boolean quotes = (querystring.charAt(authori + 7) == '(');
            if ( quotes ) {
                int ftb = querystring.indexOf(')', authori + 8);
                if (ftb == -1) ftb = querystring.length() + 1;
                author = querystring.substring(authori + 8, ftb);
                querystring = querystring.replace("author:(" + author + ")", "");
                add("author:(" + author + ")");
            } else {
                int ftb = querystring.indexOf(' ', authori);
                if ( ftb == -1 ) {
                    ftb = querystring.length();
                }
                author = querystring.substring(authori + 7, ftb);
                querystring = querystring.replace("author:" + author, "");
                add("author:" + author);
            }
        }

        // parse language
        final int langi = querystring.indexOf("/language/");
        if (langi >= 0) {
            if (querystring.length() >= (langi + 12)) {
                language = querystring.substring(langi + 10, langi + 12);
                querystring = querystring.replace("/language/" + language, "");
                if (language.length() == 2 && ISO639.exists(language)) { // only 2-digit codes valid
                    language = language.toLowerCase();
                    add("/language/" + language);
                } else {
                    language = null;
                }
            }
        }
        
        // check the number of quotes in the string; if there is only one double-quote, add another one. this will prevent error messages in 
        int p = querystring.indexOf('"');
        if (p >= 0) {
            int q = querystring.indexOf('"', p + 1);
            if (q < 0) querystring += '"';
        }
        
        return querystring.trim();
    }
    
    private String filetypeParser(String querystring, final String filetypePrefix) {
        final int ftp = querystring.indexOf(filetypePrefix, 0);
        if ( ftp >= 0 ) {
            int ftb = querystring.indexOf(' ', ftp);
            if ( ftb < 0 ) ftb = querystring.length();
            filetype = querystring.substring(ftp + filetypePrefix.length(), ftb);
            querystring = querystring.replace(filetypePrefix + filetype, "");
            while ( !filetype.isEmpty() && filetype.charAt(0) == '.' ) {
                filetype = filetype.substring(1);
            }
            add("filetype:" + filetype);
            if (filetype.isEmpty()) filetype = null;
            if (querystring.length() == 0) querystring = "*";
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
        ArrayList<String> sites = new ArrayList<String>(2);
        for (String s: s0) {
            s = s.trim();
            if (s.length() > 0) sites.add(s);
        }
        StringBuilder filterQuery = new StringBuilder(20);
        if (sites.size() > 1) {
            filterQuery.append('(').append(CollectionSchema.collection_sxt.getSolrFieldName()).append(":\"").append(sites.get(0)).append('\"');
            for (int i = 1; i < sites.size(); i++) {
                filterQuery.append(" OR ").append(CollectionSchema.collection_sxt.getSolrFieldName()).append(":\"").append(sites.get(i)).append('\"');
            }
            filterQuery.append(')');
        } else if (sites.size() == 1) {
            filterQuery.append(CollectionSchema.collection_sxt.getSolrFieldName()).append(":\"").append(sites.get(0)).append('\"');
        }
        return filterQuery.toString();

    }
    
}
