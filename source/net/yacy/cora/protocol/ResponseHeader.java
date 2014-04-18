/**
 *  ResponseHeader
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;

public class ResponseHeader extends HeaderFramework {

    // response header properties

    private static final long serialVersionUID = 0L;

    private Date date_cache_Date = null;
    private Date date_cache_Expires = null;
    private Date date_cache_LastModified = null;
    
    public ResponseHeader(final int statusCode) {
        super();
        this.put(HeaderFramework.STATUS_CODE, Integer.toString(statusCode));
    }

    public ResponseHeader(final int statusCode, final Header[] headers) {
        super();
        this.put(HeaderFramework.STATUS_CODE, Integer.toString(statusCode));
        for (final Header h : headers) {
        	add(h.getName(), h.getValue());
        }
    }

    public ResponseHeader(final int statusCode, final HashMap<String, String> reverseMappingCache) {
        super(reverseMappingCache);
        this.put(HeaderFramework.STATUS_CODE, Integer.toString(statusCode));
    }

    public ResponseHeader(final HashMap<String, String> reverseMappingCache, final Map<String, String> othermap)  {
        super(reverseMappingCache, othermap);
    }

    public int getStatusCode() {
        String statuscode = this.get(HeaderFramework.STATUS_CODE);
        if (statuscode == null) return 200;
        try {
            return Integer.parseInt(statuscode);
        } catch (final NumberFormatException e) {
            return 200;
        }
    }
    
    public Date date() {
        if (this.date_cache_Date != null) return this.date_cache_Date;
        final Date d = headerDate(HeaderFramework.DATE);
        final Date now = new Date();
        this.date_cache_Date = (d == null) ? now : d.after(now) ? now : d;
        return this.date_cache_Date;
    }

    public Date expires() {
        if (this.date_cache_Expires != null) return this.date_cache_Expires;
        this.date_cache_Expires = headerDate(HeaderFramework.EXPIRES);
        return this.date_cache_Expires;
    }

    public Date lastModified() {
        if (this.date_cache_LastModified != null) return this.date_cache_LastModified;
        final Date d = headerDate(HeaderFramework.LAST_MODIFIED);
        final Date now = new Date();
        this.date_cache_LastModified = (d == null) ? date() : d.after(now) ? now : d;
        return this.date_cache_LastModified;
    }

    public long age() {
        final Date lm = lastModified();
        final Date sd = date();
        if (lm == null) return Long.MAX_VALUE;
        return ((sd == null) ? new Date() : sd).getTime() - lm.getTime();
    }

    public boolean gzip() {
        return ((containsKey(HeaderFramework.CONTENT_ENCODING)) &&
        ((get(HeaderFramework.CONTENT_ENCODING)).toUpperCase().startsWith("GZIP")));
    }

    public String getXRobotsTag() {
        String x_robots_tag = this.get(HeaderFramework.X_ROBOTS_TAG, "");
        if (x_robots_tag.isEmpty()) {
            x_robots_tag = this.get(HeaderFramework.X_ROBOTS, "");
        }
        return x_robots_tag;
    }
}
