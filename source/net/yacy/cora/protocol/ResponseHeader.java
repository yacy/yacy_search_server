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
import java.util.Vector;

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
    private Vector<Entry> headerProps = new Vector<Entry>();

    /**
     * Implementation of Map.Entry. Structure that hold two values - exactly what we need!
     */
    public static class Entry implements Map.Entry<String, String> {
        private final String k;
        private String v;
        Entry(final String k, final String v) {
            this.k = k;
            this.v = v;
        }
        @Override
        public String getKey() {
            return this.k;
        }
        @Override
        public String getValue() {
            return this.v;
        }
        @Override
        public String setValue(final String v) {
            final String r = this.v;
            this.v = v;
            return r;
        }
    }

    /**
     * Sets Cookie on the client machine.
     *
     * @param name Cookie name
     * @param value Cookie value
     * @param expires when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     * @param path Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     * @param secure If true cookie will be send only over safe connection such as https
     * @see further documentation: <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(final String name, final String value, final String expires, final String path, final String domain, final boolean secure)
    {
         /*
         * TODO:Here every value can be validated for correctness if needed
         * For example semicolon should be not in any of the values
         * However an exception in this case would be an overhead IMHO.
         */
        String cookieString = name + "=" + value + ";";
        if (expires != null) cookieString += " expires=" + expires + ";";
        if (path != null) cookieString += " path=" + path + ";";
        if (domain != null) cookieString += " domain=" + domain + ";";
        if (secure) cookieString += " secure;";
        this.headerProps.add(new Entry("Set-Cookie", cookieString));
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name Cookie name
     * @param value Cookie value
     * @param expires when should this cookie be automatically deleted. If <b>null</b> - cookie will stay forever
     * @param path Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     *
     * Note: this cookie will be sent over each connection independent if it is safe connection or not.
     * @see further documentation: <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(final String name, final String value, final String expires, final String path, final String domain)
    {
        setCookie( name,  value,  expires,  path,  domain, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name Cookie name
     * @param value Cookie value
     * @param expires when should this cookie be automatically deleted. If <b>null</b> - cookie will stay forever
     * @param path Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     *
     * Note: this cookie will be sent over each connection independent if it is safe connection or not.
     * @see further documentation: <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(final String name, final String value, final String expires, final String path)
    {
        setCookie( name,  value,  expires,  path,  null, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name Cookie name
     * @param value Cookie value
     * @param expires when should this cookie be automatically deleted. If <b>null</b> - cookie will stay forever
     *
     * Note: this cookie will be sent over each connection independent if it is safe connection or not.
     * @see further documentation: <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(final String name, final String value, final String expires)
    {
        setCookie( name,  value,  expires,  null,  null, false);
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

    public Vector<Entry> getAdditionalHeaderProperties() {
        return this.headerProps;
    }

    /*
     * Patch END:
     * Name: Header Property Patch
     */
}
