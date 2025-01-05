/**
 *  HeaderFramework
 *  Copyright 2004 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 2004 at https://yacy.net
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;


/**
 * this class implements a key-value mapping, as a hashtable
 * The difference to ordinary hashtable implementations is that the
 * keys are not compared by the equal() method, but are always
 * treated as string and compared as
 * key.uppercase().equal(.uppercase(comparator))
 */
public class HeaderFramework extends TreeMap<String, String> implements Map<String, String> {


    private static final long serialVersionUID = 18L;

    /* =============================================================
     * Constants defining http versions
     * ============================================================= */
    public static final String HTTP_VERSION_0_9 = "HTTP/0.9";
    public static final String HTTP_VERSION_1_0 = "HTTP/1.0";
    public static final String HTTP_VERSION_1_1 = "HTTP/1.1";

    /* =============================================================
     * Constants defining http header names
     * ============================================================= */


    public static final String HOST = "Host";
    public static final String USER_AGENT = "User-Agent";

    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String ACCEPT_CHARSET = "Accept-Charset";

    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_MD5 = "Content-MD5";
    public static final String CONTENT_LOCATION = "Content-Location";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String PRAGMA = "Pragma";
    public static final String CACHE_CONTROL = "Cache-Control";

    public static final String DATE = "Date"; // time message/response was created, https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.18
    public static final String LAST_MODIFIED = "Last-Modified";
    public static final String SERVER = "Server";

    public static final String ACCEPT_RANGES = "Accept-Ranges";
    public static final String CONTENT_RANGE = "Content-Range";
    public static final String RANGE = "Range";

    public static final String LOCATION = "Location";
    public static final String ETAG = "ETag";
    public static final String VIA = "Via";

    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_ROBOTS_TAG = "X-Robots-Tag"; // see http://googleblog.blogspot.com/2007/07/robots-exclusion-protocol-now-with-even.html
    public static final String X_ROBOTS = "X-Robots";

    public static final String X_YACY_INDEX_CONTROL = "X-YaCy-Index-Control";
    /** Added when generating legacy request header to allow template servlets to know the original request scheme : "http" or "https" */
    @Deprecated /** use getScheme() (header not used in any request, 2017-02-22) */
    public static final String X_YACY_REQUEST_SCHEME = "X-YaCy-Request-Scheme";
    
    /** Added to responses embedding a hidden HTML field containing a transaction token, 
     * to allow easier retrieval (without HTML parsing) of the token value by external tools such as bash scripts */
    public static final String X_YACY_TRANSACTION_TOKEN = "X-YaCy-Transaction-Token";

    public static final String SET_COOKIE = "Set-Cookie";
    public static final String SET_COOKIE2 = "Set-Cookie2";
    public static final String EXPIRES = "Expires";

    public static final String CORS_ALLOW_ORIGIN = "Access-Control-Allow-Origin"; // Cross-Origin Resource Sharing properties (http://www.w3.org/TR/cors/)

    public static final String RESPONSE_TIME_MILLIS = "ResponseTimeMillis";


    /* =============================================================
     * Constants for content-encodings
     * ============================================================= */
    public static final String CONTENT_ENCODING_GZIP = "gzip";

    /* =============================================================
     * Constants defining http methods
     * ============================================================= */
    public static final String METHOD_GET = "GET";
    public static final String METHOD_HEAD = "HEAD";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_CONNECT = "CONNECT";

    /*
     * constanst for metadata which is stored in the ResponseHeader
     */
    public static final String STATUS_CODE = "STATUS_CODE";

    /* =============================================================
     * defining default http status messages
     * ============================================================= */
    public static final Map<String, String> http1_0 = new ConcurrentHashMap<String, String>();
    static {
        http1_0.put("200","OK");
        http1_0.put("201","Created");
        http1_0.put("202","Accepted");
        http1_0.put("204","No Content");
        http1_0.put("300","Multiple Choices");
        http1_0.put("301","Moved Permanently");
        http1_0.put("302","Moved Temporarily");
        http1_0.put("304","Not Modified");
        http1_0.put("400","Bad Request");
        http1_0.put("401","Unauthorized");
        http1_0.put("403","Forbidden");
        http1_0.put("404","Not Found");
        http1_0.put("500","Internal Server Error");
        http1_0.put("501","Not Implemented");
        http1_0.put("502","Bad Gateway");
        http1_0.put("503","Service Unavailable");
    }
    public static final Map<String, String> http1_1 = new ConcurrentHashMap<String, String>();
    static {
        http1_1.putAll(http1_0);
        http1_1.put("100","Continue");
        http1_1.put("101","Switching Protocols");
        http1_1.put("203","Non-Authoritative Information");
        http1_1.put("205","Reset Content");
        http1_1.put("206","Partial Content");
        http1_1.put("300","Multiple Choices");
        http1_1.put("303","See Other");
        http1_1.put("305","Use Proxy");
        http1_1.put("307","Temporary Redirect");
        http1_1.put("402","Payment Required");
        http1_1.put("405","Method Not Allowed");
        http1_1.put("406","Not Acceptable");
        http1_1.put("407","Proxy Authentication Required");
        http1_1.put("408","Request Time-out");
        http1_1.put("409","Conflict");
        http1_1.put("410","Gone");
        http1_1.put("411","Length Required");
        http1_1.put("412","Precondition Failed");
        http1_1.put("413","Request Entity Too Large");
        http1_1.put("414","Request-URI Too Large");
        http1_1.put("415","Unsupported Media Type");
        http1_1.put("416","Requested range not satisfiable");
        http1_1.put("417","Expectation Failed");
        http1_1.put("504","Gateway Time-out");
        http1_1.put("505","HTTP Version not supported");
    }

    /* PROPERTIES: General properties */
    // Properties are used to internally store or remember header values and additional connection information
    // One of the usages is in proxy operation to prepare header values to be set as header values upon connection
    //  * use of properties as header values is discouraged (e.g. as proxy transmits it as arbitrary headers) [2016-8-21]
    public static final String CONNECTION_PROP_HTTP_VER = "HTTP";
    @Deprecated // use CONNECTION_PROP_DIGESTURL // misleading custom header (compared to servletrequest) get(CONNECTION_PROP_PROTOCOL) = servletrequest.getScheme()
    public static final String CONNECTION_PROP_PROTOCOL = "PROTOCOL";
    public static final String CONNECTION_PROP_HOST = "HOST";
    public static final String CONNECTION_PROP_USER = "USER";
    public static final String CONNECTION_PROP_METHOD = "METHOD";
    public static final String CONNECTION_PROP_PATH = "PATH";
    public static final String CONNECTION_PROP_EXT = "EXT";
    // public static final String CONNECTION_PROP_ARGS = "ARGS"; // use getQueryString() or getParameter()
    public static final String CONNECTION_PROP_CLIENTIP = "CLIENTIP";
    public static final String CONNECTION_PROP_PERSISTENT = "PERSISTENT";
    public static final String CONNECTION_PROP_REQUEST_START = "REQUEST_START";
    public static final String CONNECTION_PROP_REQUEST_END = "REQUEST_END";

    /* PROPERTIES: Client -> Proxy */
    public static final String CONNECTION_PROP_DIGESTURL = "URL"; // value DigestURL object
    public static final String CONNECTION_PROP_CLIENT_HTTPSERVLETREQUEST = "CLIENT_HTTPSERVLETREQUEST";

    /* PROPERTIES: Proxy -> Client */
    public static final String CONNECTION_PROP_PROXY_RESPOND_CODE = "PROXY_RESPOND_CODE";
    public static final String CONNECTION_PROP_PROXY_RESPOND_STATUS = "PROXY_RESPOND_STATUS";
    public static final String CONNECTION_PROP_PROXY_RESPOND_HEADER = "PROXY_RESPOND_HEADER";
    public static final String CONNECTION_PROP_PROXY_RESPOND_SIZE = "PROXY_REQUEST_SIZE";

    public HeaderFramework() {
        super(ASCII.insensitiveASCIIComparator);
    }

    public HeaderFramework(final Map<String, String> othermap)  {
        // creates a case insensitive map from another map
        super(ASCII.insensitiveASCIIComparator);

        // load with data
        if (othermap != null) putAll(othermap);
    }

    /** Date formatter/parser for standard compliant HTTP header dates (RFC 1123) */
    private static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss Z"; // with numeric time zone indicator as defined in RFC5322
    private static final String PATTERN_RFC1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";
    private static final SimpleDateFormat FORMAT_RFC1123 = new SimpleDateFormat(PATTERN_RFC1123, Locale.US);
    private static final TimeZone TZ_GMT = TimeZone.getTimeZone("GMT");
    private static final Calendar CAL_GMT = Calendar.getInstance(TZ_GMT, Locale.US);
    
	/**
	 * A thread-safe date formatter using the
	 * {@link HeaderFramework#PATTERN_RFC1123} pattern with the US locale on the UTC
	 * time zone.
	 */
	public static final DateTimeFormatter RFC1123_FORMATTER = DateTimeFormatter
			.ofPattern(PATTERN_RFC1123.replace("yyyy", "uuuu")).withLocale(Locale.US).withZone(ZoneOffset.UTC);
    
	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link HeaderFramework#PATTERN_RFC1123} pattern with the US locale.
	 */
	public static SimpleDateFormat newRfc1123Format() {
		return new SimpleDateFormat(HeaderFramework.PATTERN_RFC1123, Locale.US);
	}

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link HeaderFramework#PATTERN_RFC1036} pattern with the US locale.
	 */
	public static SimpleDateFormat newRfc1036Format() {
		return new SimpleDateFormat(HeaderFramework.PATTERN_RFC1036, Locale.US);
	}

    /**
     * RFC 2616 requires that HTTP clients are able to parse all 3 different
     * formats. All times MUST be in GMT/UTC, but ...
     */
    private static final SimpleDateFormat[] FORMATS_HTTP = new SimpleDateFormat[] {
            // RFC 1123/822 (Standard) "Mon, 12 Nov 2007 10:11:12 GMT"
    		newRfc1123Format(),
            // RFC 1036/850 (old)      "Monday, 12-Nov-07 10:11:12 GMT"
    		newRfc1036Format(),
            // ANSI C asctime()        "Mon Nov 12 10:11:12 2007"
            GenericFormatter.newAnsicFormat(),
    };


    private static long lastRFC1123long = 0;
    private static String lastRFC1123string = "";

    public static final String formatRFC1123(final Date date) {
        if (date == null) return "";
        if (Math.abs(date.getTime() - lastRFC1123long) < 1000) {
            //System.out.println("date cache hit - " + lastRFC1123string);
            return lastRFC1123string;
        }
        synchronized (FORMAT_RFC1123) {
            final String s = FORMAT_RFC1123.format(date);
            lastRFC1123long = date.getTime();
            lastRFC1123string = s;
            return s;
        }
    }
    
	/**
	 * @param epochMilli
	 *            a time value as the number of milliseconds from Epoch
	 *            (1970-01-01T00:00:00Z)
	 * @return the time formatted using the {@link HeaderFramework#PATTERN_RFC1123}
	 *         pattern.
	 */
	public static final String formatRFC1123(final long epochMilli) {
		try {
			/* Prefer first using the thread-safe DateTimeFormatter shared instance */
			return RFC1123_FORMATTER.format(Instant.ofEpochMilli(epochMilli));
		} catch (final DateTimeException e) {
			/*
			 * This should not happen, but rather than failing we prefer here to use
			 * formatting function using the synchronized SimpleDateFormat
			 */
			return formatRFC1123(new Date(epochMilli));
		}
	}
	
	/**
	 * @return the current time formatted using the
	 *         {@link HeaderFramework#PATTERN_RFC1123} pattern.
	 */
	public static final String formatNowRFC1123() {
		return formatRFC1123(System.currentTimeMillis());
	}

    /** Initialization of static formats */
    static {
        // 2-digit dates are automatically parsed by SimpleDateFormat,
        // we need to detect the real year by adding 1900 or 2000 to
        // the year value starting with 1970
        CAL_GMT.setTimeInMillis(0);

        for (final SimpleDateFormat format: FORMATS_HTTP) {
            format.setTimeZone(TZ_GMT);
            format.set2DigitYearStart(CAL_GMT.getTime());
        }
        
        FORMAT_RFC1123.setTimeZone(TZ_GMT);
        FORMAT_RFC1123.set2DigitYearStart(CAL_GMT.getTime());
    }

    /**
     * Parse a HTTP string representation of a date into a Date instance.
     * @param s The date String to parse.
     * @return The Date instance if successful, <code>null</code> otherwise.
     */
    public static Date parseHTTPDate(String s) {
        s = s.trim();
        if (s == null || s.length() < 9) return null;
        for (final SimpleDateFormat format: FORMATS_HTTP) synchronized (format) {
            try { return format.parse(s); } catch (final ParseException e) {}
        }
        return null;
    }

    // to make the occurrence of multiple keys possible, we add them using a counter
    public String add(final String key, final String value) {
        final int c = keyCount(key);
        if (c == 0) return put(key, value);
        return put("*" + key + "-" + Integer.toString(c), value);
    }

    /**
     * Count occurence of header keys, look for original header name and a
     * numbered version of the header *headername-NUMBER , with NUMBER starting at 1
     * @param key the raw header name
     * @return number of headers with same name
     */
    public int keyCount(final String key) {
        if (!(containsKey(key))) return 0;
        int c = 1;
        final String h = "*" + key + "-";
        while (containsKey(h + Integer.toString(c))) c++;
        return c;
    }

    // a convenience method to access the map with fail-over defaults
    public String get(final String key, final String dflt) {
        final String result = get(key);
        if (result == null) return dflt;
        return result;
    }

    /**
     * Get one Header of headers with same name.
     * The headers are internally numbered
     * @param key the raw header name
     * @param count the number of the numbered header name (0 = same as get(key))
     * @return value of header with number=count
     */
    public String getSingle(final String key, final int count) {
        if (count == 0) return get(key); // first look for just the key
        return get("*" + key + "-" + count); // now for the numbered header names
    }

    /**
     * Get multiple header values with same header name.
     * The header names are internally numbered (format *key-1)
     * @param key the raw header name
     * @return header values
     */
    public String[] getMultiple(final String key) {
        final int count = keyCount(key);
        final String[] result = new String[count];
        for (int i = 0; i < count; i++) result[i] = getSingle(key, i);
        return result;
    }

    // convenience methods for storing and loading to a file system
    public void store(final File f) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            for (final java.util.Map.Entry<String, String> entry: entrySet()) {
                fos.write(UTF8.getBytes(entry.getKey() + "=" + entry.getValue() + "\r\n"));
            }
            fos.flush();
        } finally {
            if (fos != null) try{fos.close();}catch(final Exception e){}
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }


    /*
     * example header
      Connection=close
      Content-Encoding=gzip
      Content-Length=7281
      Content-Type=text/html; charset=UTF-8
      Date=Mon, 05 Jan 2004 11:55:10 GMT
      Server=Apache/1.3.26
    */

    /**
     * Get mime type from header field Content-Type.
     * Strips any parameter denoted by ';'.
     * References : RFC 7231 on HTTP/1.1 and RFC 2045 on Multipurpose Internet Mail Extensions (MIME)
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-3.1.1.1">RFC 7231 (HTTP/1.1) - "Media Type" section</a>
     * @see <a href="https://tools.ietf.org/html/rfc2045#section-5">RFC 2045 (MIME) - "Content-Type Header Field" section</a>
     * @return mime or on missing header field "application/octet-stream"
     */
    public String mime() {
        final String tmpstr = this.get(CONTENT_TYPE, "application/octet-stream");
        final int pos = tmpstr.indexOf(';');
        if (pos > 0) {
            return tmpstr.substring(0, pos).trim();
        }
        return tmpstr;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.commons.fileupload.RequestContext#getCharacterEncoding()
     */
    public String getCharacterEncoding() {
        return getCharacterEncoding(getContentType());
    }
    
    /**
     * References : RFC 7231 on HTTP/1.1 and RFC 2045 on Multipurpose Internet Mail Extensions (MIME)
     * @param contentType a Content-Type header value
     * @return the characters set name extracted from the header, or null when not in the header
     * @see <a href="https://tools.ietf.org/html/rfc7231#section-3.1.1.1">RFC 7231 (HTTP/1.1) - "Media Type" section</a>
     * @see <a href="https://tools.ietf.org/html/rfc2045#section-5">RFC 2045 (MIME) - "Content-Type Header Field" section</a>
     */
    public static final String getCharacterEncoding(final String contentType) {
        if (contentType == null) return null;

        final String[] parts = CommonPattern.SEMICOLON.split(contentType);
        if (parts == null || parts.length <= 1) return null;

        for (int i=1; i < parts.length; i++) {
            final String param = parts[i].trim();
            if (param.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                String charset = param.substring("charset=".length()).trim();
                if (charset.length() > 0 && (charset.charAt(0) == '\"' || charset.charAt(0) == '\'')) charset = charset.substring(1);
                if (charset.endsWith("\"") || charset.endsWith("'")) charset = charset.substring(0,charset.length()-1);
                return charset.trim();
            }
        }

        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.commons.fileupload.RequestContext#getContentLength()
     */
    public int getContentLength() {
        if (containsKey(CONTENT_LENGTH)) {
            try {
                return (int) Long.parseLong(get(CONTENT_LENGTH));
            } catch (final NumberFormatException e) {
                ConcurrentLog.warn("HeaderFramework", "content-length cannot be parsed: " + get(CONTENT_LENGTH));
                return -1;
            }
        }
        return -1;
    }

    /*
     * provide method, which can handle big filelengths (for example from ftp)
     * because we can't change the interface in apache httpclient
     *
     * @see org.apache.commons.fileupload.RequestContext#getContentLength()
     */
    public long getContentLengthLong() {
        if (containsKey(CONTENT_LENGTH)) {
            try {
                return Long.parseLong(get(CONTENT_LENGTH));
            } catch (final NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Get header field content-type (unmodified)
     * which may include additional parameter (RFC 2616, obsoleted by RFC 7231)
     * see also mime()
     * @see org.apache.commons.fileupload.RequestContext#getContentType()
     */
    public String getContentType() {
        return this.get(CONTENT_TYPE);
    }

    protected Date headerDate(final String kind) {
        if (containsKey(kind)) {
            Date parsedDate = parseHTTPDate(get(kind));
            if (parsedDate == null) return null;
            return parsedDate;
        }
        return null;
    }

    public StringBuilder toHeaderString(
            final String httpVersion,
            final int httpStatusCode,
            final String httpStatusText) {
        // creating a new buffer to store the header as string
        final StringBuilder theHeader = new StringBuilder(180);

        // generating the header string
        this.toHeaderString(httpVersion,httpStatusCode,httpStatusText,theHeader);

        // returning the result
        return theHeader;
    }


    public void toHeaderString(
            String httpVersion,
            final int httpStatusCode,
            String httpStatusText,
            final StringBuilder theHeader) {

        if (theHeader == null) throw new IllegalArgumentException();

        // setting the http version if it was not already set
        if (httpVersion == null) httpVersion = HTTP_VERSION_1_0;

        // setting the status text if it was not already set
        if ((httpStatusText == null)||(httpStatusText.length()==0)) {
            // http1_1 contains all status code text
            if (HeaderFramework.http1_1.containsKey(Integer.toString(httpStatusCode)))
                httpStatusText = HeaderFramework.http1_1.get(Integer.toString(httpStatusCode));
            else httpStatusText = "Unknown";
        }


        // write status line
        theHeader.append(httpVersion).append(" ")
                 .append(Integer.toString(httpStatusCode)).append(" ")
                 .append(httpStatusText).append("\r\n");

        // write header
        final Iterator<String> i = keySet().iterator();
        String key;
        char tag;
        int count;
        while (i.hasNext()) {
            key = i.next();
            tag = key.charAt(0);
            if ((tag != '*') && (tag != '#')) { // '#' in key is reserved for proxy attributes as artificial header values
                count = keyCount(key);
                for (int j = 0; j < count; j++) {
                    theHeader.append(key).append(": ").append(getSingle(key, j)).append("\r\n");
                }
            }
        }
        // end header
        theHeader.append("\r\n");
    }

    /**
     * Reading http headers from a reader class and building up a httpHeader object
     * @param reader the {@link BufferedReader} that is used to read the http header lines
     * @return a {@link HeaderFramework}-Object containing all parsed headers
     * @throws IOException
     */
    public void readHttpHeader(final BufferedReader reader) throws IOException {
        // reading all request headers
        int p;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) break;
            if ((p = line.indexOf(':')) >= 0) {
                // store a property
                add(line.substring(0, p).trim(), line.substring(p + 1).trim());
            }
        }
    }
}
