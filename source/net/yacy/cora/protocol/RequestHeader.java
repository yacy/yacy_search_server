/**
 *  RequestHeader
 *  Copyright 2008 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 22.08.2008 at http://yacy.net
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

package net.yacy.cora.protocol;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;

public class RequestHeader extends HeaderFramework {

    // request header properties

    public static final String CONNECTION = "Connection";
    public static final String PROXY_CONNECTION = "Proxy-Connection";
    public static final String KEEP_ALIVE = "Keep-Alive";

    public static final String AUTHORIZATION = "Authorization";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";

    public static final String UPGRADE = "Upgrade";
    public static final String TE = "TE";

    public static final String X_CACHE = "X-Cache";
    public static final String X_CACHE_LOOKUP = "X-Cache-Lookup";

    public static final String COOKIE = "Cookie";

    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String IF_RANGE = "If-Range";
    public static final String REFERER = "Referer"; // a misspelling of referrer that occurs as an HTTP header field. Its defined so in the http protocol, so please don't 'fix' it!

    private static final long serialVersionUID = 0L;

    public enum FileType {
        HTML, JSON, XML
    }

    private Date date_cache_IfModifiedSince = null;
    
    public RequestHeader() {
        super();
    }

    public RequestHeader(final Map<String, String> reverseMappingCache) {
        super(reverseMappingCache);
    }

    public RequestHeader(final Map<String, String> reverseMappingCache, final Map<String, String> othermap)  {
        super(reverseMappingCache, othermap);
    }

    public DigestURL referer() {
        final String referer = get(REFERER, null);
        if (referer == null) return null;
        try {
            return new DigestURL(referer);
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    public String refererHost() {
        final MultiProtocolURL url = referer();
        if (url == null) return null;
        return url.getHost();
    }

    
    public Date ifModifiedSince() {
        if (this.date_cache_IfModifiedSince != null) return date_cache_IfModifiedSince;
        this.date_cache_IfModifiedSince = headerDate(RequestHeader.IF_MODIFIED_SINCE);
        return this.date_cache_IfModifiedSince;
    }

    public Object ifRange() {
        if (containsKey(IF_RANGE)) {
            final Date rangeDate = parseHTTPDate(get(IF_RANGE));
            if (rangeDate != null)
                return rangeDate;

            return get(IF_RANGE);
        }
        return null;
    }

    public String userAgent() {
        return get(USER_AGENT, "");
    }

    public boolean acceptGzip() {
        return ((containsKey(ACCEPT_ENCODING)) &&
                ((get(ACCEPT_ENCODING)).toUpperCase().indexOf("GZIP",0)) != -1);
    }

    public FileType fileType() {
        String path = get(HeaderFramework.CONNECTION_PROP_PATH);
        if (path == null) return FileType.HTML;
        path = path.toLowerCase();
        if (path.endsWith(".json")) return FileType.JSON;
        if (path.endsWith(".xml")) return FileType.XML;
        if (path.endsWith(".rdf")) return FileType.XML;
        if (path.endsWith(".rss")) return FileType.XML;
        return FileType.HTML;
    }


    public boolean accessFromLocalhost() {
        // authorization for localhost, only if flag is set to grant localhost access as admin
        final String clientIP = this.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "");
        if ( !Domains.isLocalhost(clientIP) ) {
            return false;
        }
        final String refererHost = this.refererHost();
        return refererHost == null || refererHost.isEmpty() || Domains.isLocalhost(refererHost);
    }
}
