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

import org.apache.solr.common.params.CommonParams;

import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.server.serverObjects;


public class QueryModifier {

    private final StringBuilder modifier;
    public String sitehost, sitehash, filetype, protocol, author;
    
    public QueryModifier() {
        this.sitehash = null;
        this.sitehost = null;
        this.filetype = null;
        this.protocol = null;
        this.author = null;
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
        
        // parse filetype
        final int ftp = querystring.indexOf("filetype:", 0);
        if ( ftp >= 0 ) {
            int ftb = querystring.indexOf(' ', ftp);
            if ( ftb == -1 ) {
                ftb = querystring.length();
            }
            filetype = querystring.substring(ftp + 9, ftb);
            querystring = querystring.replace("filetype:" + filetype, "");
            while ( !filetype.isEmpty() && filetype.charAt(0) == '.' ) {
                filetype = filetype.substring(1);
            }
            add("filetype:" + filetype);
            if (filetype.isEmpty()) filetype = null;
        }
        
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
            sitehash = DigestURI.hosthash(sitehost);
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
        
        return querystring.trim();
    }
    
    public void add(String m) {
        if (modifier.length() > 0 && modifier.charAt(modifier.length() - 1) != ' ' && m != null && m.length() > 0) modifier.append(' ');
        if (m != null) modifier.append(m);
    }
    
    public String toString() {
        return this.modifier.toString();
    }
    
    public void apply(serverObjects post) {
        
        final StringBuilder fq = new StringBuilder(post.get(CommonParams.FQ,""));
        
        if (this.sitehost != null && this.sitehost.length() > 0 && fq.indexOf(YaCySchema.host_s.getSolrFieldName()) < 0) {
            // consider to search for hosts with 'www'-prefix, if not already part of the host name
            if (this.sitehost.startsWith("www.")) {
                fq.append(" AND (").append(YaCySchema.host_s.getSolrFieldName()).append(":\"").append(this.sitehost.substring(4)).append('\"');
                fq.append(" OR ").append(YaCySchema.host_s.getSolrFieldName()).append(":\"").append(this.sitehost).append("\")");
            } else {
                fq.append(" AND (").append(YaCySchema.host_s.getSolrFieldName()).append(":\"").append(this.sitehost).append('\"');
                fq.append(" OR ").append(YaCySchema.host_s.getSolrFieldName()).append(":\"www.").append(this.sitehost).append("\")");
            }
        }
        if (this.sitehash != null && this.sitehash.length() > 0 && fq.indexOf(YaCySchema.host_id_s.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(YaCySchema.host_id_s.getSolrFieldName()).append(":\"").append(this.sitehash).append('\"');
        }

        if (this.filetype != null && this.filetype.length() > 0 && fq.indexOf(YaCySchema.url_file_ext_s.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(YaCySchema.url_file_ext_s.getSolrFieldName()).append(":\"").append(this.filetype).append('\"');
        }
        
        if (this.author != null && this.author.length() > 0 && fq.indexOf(YaCySchema.author_sxt.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(YaCySchema.author_sxt.getSolrFieldName()).append(":\"").append(this.author).append('\"');
        }
        
        if (this.protocol != null && this.protocol.length() > 0 && fq.indexOf(YaCySchema.url_protocol_s.getSolrFieldName()) < 0) {
            fq.append(" AND ").append(YaCySchema.url_protocol_s.getSolrFieldName()).append(":\"").append(this.protocol).append('\"');
        }

        if (fq.length() > 0) {
            String fqs = fq.toString();
            if (fqs.startsWith(" AND ")) fqs = fqs.substring(5);
            post.remove(CommonParams.FQ);
            post.put(CommonParams.FQ, fqs);
        }
    }
    
}
