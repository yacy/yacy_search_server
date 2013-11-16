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

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.util.ConcurrentLog;

import org.apache.http.Header;

public class ResponseHeader extends HeaderFramework {

    // response header properties

    private static final long serialVersionUID = 0L;
    private static final ConcurrentLog log = new ConcurrentLog(ResponseHeader.class.getName());

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
        final Date d = headerDate(HeaderFramework.DATE);
        final Date now = new Date();
        return (d == null) ? now : d.after(now) ? now : d;
    }

    public Date expires() {
        return headerDate(EXPIRES);
    }

    public Date lastModified() {
        final Date d = headerDate(LAST_MODIFIED);
        final Date now = new Date();
        return (d == null) ? date() : d.after(now) ? now : d;
    }

    public long age() {
        final Date lm = lastModified();
        final Date sd = date();
        if (lm == null) return Long.MAX_VALUE;
        return ((sd == null) ? new Date() : sd).getTime() - lm.getTime();
    }

    public boolean gzip() {
        return ((containsKey(CONTENT_ENCODING)) &&
        ((get(CONTENT_ENCODING)).toUpperCase().startsWith("GZIP")));
    }

    public static Object[] parseResponseLine(final String respLine) {

        if ((respLine == null) || (respLine.isEmpty())) {
            return new Object[]{"HTTP/1.0",Integer.valueOf(500),"status line parse error"};
        }

        int p = respLine.indexOf(' ',0);
        if (p < 0) {
            return new Object[]{"HTTP/1.0",Integer.valueOf(500),"status line parse error"};
        }

        String httpVer, status, statusText;
        Integer statusCode;

        // the http version reported by the server
        httpVer = respLine.substring(0,p);

        // Status of the request, e.g. "200 OK"
        status = respLine.substring(p + 1).trim(); // the status code plus reason-phrase

        // splitting the status into statuscode and statustext
        p = status.indexOf(' ',0);
        try {
            statusCode = Integer.valueOf((p < 0) ? status.trim() : status.substring(0,p).trim());
            statusText = (p < 0) ? "" : status.substring(p+1).trim();
        } catch (final Exception e) {
            statusCode = Integer.valueOf(500);
            statusText = status;
        }

        return new Object[]{httpVer,statusCode,statusText};
    }


    /**
     * @param header
     * @return a supported Charset, so data can be encoded (may not be correct)
     */
    public Charset getCharSet() {
        String charSetName = getCharacterEncoding();
        if (charSetName == null) {
            // no character encoding is sent by the server
            charSetName = DEFAULT_CHARSET;
        }
        // maybe the charset is valid but not installed on this computer
        try {
            if(!Charset.isSupported(charSetName)) {
                log.warn("charset '"+ charSetName +"' is not supported on this machine, using default ("+ Charset.defaultCharset().name() +")");
                // use system default
                return Charset.defaultCharset();
            }
        } catch(final IllegalCharsetNameException e) {
            log.warn("Charset in header is illegal: '"+ charSetName +"'\n    "+ toString() + "\n" + e.getMessage());
            // use system default
            return Charset.defaultCharset();
        } catch (final UnsupportedCharsetException e) {
            log.warn("Charset in header is unsupported: '"+ charSetName +"'\n    "+ toString() + "\n" + e.getMessage());
            // use system default
            return Charset.defaultCharset();
        }
        return Charset.forName(charSetName);
    }

    public String getXRobotsTag() {
        String x_robots_tag = this.get(HeaderFramework.X_ROBOTS_TAG, "");
        if (x_robots_tag.isEmpty()) {
            x_robots_tag = this.get(HeaderFramework.X_ROBOTS, "");
        }
        return x_robots_tag;
    }
}
