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

import net.yacy.cora.document.MultiProtocolURI;

public class RequestHeader extends HeaderFramework {

    // request header properties    
    
    public static final String CONNECTION = "Connection";
    public static final String PROXY_CONNECTION = "Proxy-Connection";
    public static final String KEEP_ALIVE = "Keep-Alive";
    public static final String USER_AGENT = "User-Agent";
    
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
    public static final String REFERER = "Referer";
    
    private static final long serialVersionUID = 0L;

    public RequestHeader() {
        super();
    }
    
    public RequestHeader(final Map<String, String> reverseMappingCache) {
        super(reverseMappingCache);
    }
    
    public RequestHeader(final Map<String, String> reverseMappingCache, final Map<String, String> othermap)  {
        super(reverseMappingCache, othermap);
    }
    
    public MultiProtocolURI referer() {
        String referer = get(REFERER, null);
        if (referer == null) return null;
        try {
            return new MultiProtocolURI(referer);
        } catch (MalformedURLException e) {
            return null;
        }
    }
    
    public String refererHost() {
        final MultiProtocolURI url = referer();
        if (url == null) return null;
        return url.getHost();
    }
    
    public Date ifModifiedSince() {
        return headerDate(IF_MODIFIED_SINCE);
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
                ((get(ACCEPT_ENCODING)).toUpperCase().indexOf("GZIP")) != -1);        
    }
}
