/**
 *  ResponseHeader
 *  Copyright 2008 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 22.08.2008 at https://yacy.net
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.servlet.http.Cookie;

import org.apache.http.Header;

public class ResponseHeader extends HeaderFramework {

    // response header properties

    private static final long serialVersionUID = 0L;

    // cached values for quicker repeated access
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

    public ResponseHeader(final Map<String, String> othermap)  {
        super(othermap);
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

    /**
     * Get the http field Date or now (if header date missing)
     * @return date message was created or now
     */
    public Date date() {
        if (this.date_cache_Date != null) return this.date_cache_Date;
        final Date d = headerDate(HeaderFramework.DATE);
        final Date now = new Date();
        this.date_cache_Date = (d == null) ? now : d.after(now) ? now : d;
        return this.date_cache_Date;
    }

    /**
     * get http field Expires if available
     * @return date or null
     */
    public Date expires() {
        if (this.date_cache_Expires != null) return this.date_cache_Expires;
        this.date_cache_Expires = headerDate(HeaderFramework.EXPIRES);
        return this.date_cache_Expires;
    }

    /**
     * get http field Last-Modified or now (if header field is missing)
     * @return valid date (always != null)
     */
    public Date lastModified() {
        if (this.date_cache_LastModified != null) return this.date_cache_LastModified;
        Date d = headerDate(HeaderFramework.LAST_MODIFIED);
        final Date now = new Date();
        this.date_cache_LastModified = (d == null) ? date() : d.after(now) ? now : d;
        return this.date_cache_LastModified;
    }

    /**
     * age in milliseconds (difference between now and last_modified)
     * @return age in milliseconds
     */
    public long age() {
        final Date lm = lastModified();
        final Date now = new Date();
        return now.getTime() - lm.getTime();
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
        return x_robots_tag.toLowerCase();
    }

    /*
     * Patch BEGIN: (moved from HeaderFramework here (2016-11-20)
     * Name: Header Property Patch
     * Date: Fri. 13.01.2006
     * Description: Makes possible to send header properties such as cookies back to the client.
     * Part 1 of 5
     * Questions: sergej.z@list.ru
     */
    /**
     * Holds header properties
     */
    //Since properties such as cookies can be multiple, we cannot use HashMap here. We have to use Vector.
    private List<Cookie> cookieStore; // init on first access

    /**
     * Sets Cookie on the client machine.
     *
     * @param name Cookie name
     * @param value Cookie value
     * @param maxage time to live in seconds, none negative number, according to https://tools.ietf.org/html/rfc2109, 0=discard in https://tools.ietf.org/html/rfc2965
     * @param path Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     * @param secure If true cookie will be send only over safe connection such as https
     * @see <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">further documentation at docs.sun.com</a>
     */
    public void setCookie(final String name, final String value, final Integer maxage, final String path, final String domain, final boolean secure)
    {
         /*
         * TODO:Here every value can be validated for correctness if needed
         * For example semicolon should be not in any of the values
         * However an exception in this case would be an overhead IMHO.
         */
        if (!name.isEmpty()) {
            if (this.cookieStore == null) this.cookieStore = new ArrayList<Cookie>();
            Cookie c = new Cookie (name, value);
            if (maxage != null && maxage >= 0) c.setMaxAge(maxage);
            if (path != null) c.setPath(path);
            if (domain != null) c.setDomain(domain);
            if (secure) c.setSecure(secure);
            this.cookieStore.add(c);
        }
    }
    
    /**
     * Sets Cookie on the client machine.
     *
     * @param name Cookie name
     * @param value Cookie value
     *
     * Note: this cookie will be sent over each connection independent if it is safe connection or not. This cookie never expires
     * @see further documentation: <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(final String name, final String value )
    {
        setCookie( name,  value,  null,  null,  null, false);
    }

    public List<Cookie> getCookiesEntries() {
        return this.cookieStore;
    }

    /*
     * Patch END:
     * Name: Header Property Patch
     */
}
