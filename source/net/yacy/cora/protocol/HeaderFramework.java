/**
 *  HeaderFramework
 *  Copyright 2004 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 2004 at http://yacy.net
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
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;


/**
 * this class implements a key-value mapping, as a hashtable
 * The difference to ordinary hashtable implementations is that the
 * keys are not compared by the equal() method, but are always
 * treated as string and compared as
 * key.uppercase().equal(.uppercase(comparator))
 * You use this class by first creation of a static HashMap
 * that then is used a the reverse mapping cache for every new
 * instance of this class.
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

    public static final String DATE = "Date";
    public static final String LAST_MODIFIED = "Last-modified";
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

    public static final String X_YACY_INDEX_CONTROL = "X-YACY-Index-Control";
    //public static final String X_YACY_PREVIOUS_REQUEST_LINE = "X-Previous-Request-Line";
    public static final String X_YACY_KEEP_ALIVE_REQUEST_COUNT = "X-Keep-Alive-Request-Count";
    public static final String X_YACY_ORIGINAL_REQUEST_LINE = "X-Original-Request-Line";

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
    public static final String CONNECTION_PROP_HTTP_VER = "HTTP";
    public static final String CONNECTION_PROP_HOST = "HOST";
    public static final String CONNECTION_PROP_USER = "USER";
    public static final String CONNECTION_PROP_METHOD = "METHOD";
    public static final String CONNECTION_PROP_PATH = "PATH";
    public static final String CONNECTION_PROP_EXT = "EXT";
    public static final String CONNECTION_PROP_URL = "URL";
    public static final String CONNECTION_PROP_ARGS = "ARGS";
    public static final String CONNECTION_PROP_CLIENTIP = "CLIENTIP";
    public static final String CONNECTION_PROP_PERSISTENT = "PERSISTENT";
    //public static final String CONNECTION_PROP_KEEP_ALIVE_COUNT = "KEEP-ALIVE_COUNT";
    //public static final String CONNECTION_PROP_REQUESTLINE = "REQUESTLINE";
    //public static final String CONNECTION_PROP_PREV_REQUESTLINE = "PREVREQUESTLINE";
    public static final String CONNECTION_PROP_REQUEST_START = "REQUEST_START";
    public static final String CONNECTION_PROP_REQUEST_END = "REQUEST_END";
    //public static final String CONNECTION_PROP_INPUTSTREAM = "INPUTSTREAM";
    //public static final String CONNECTION_PROP_OUTPUTSTREAM = "OUTPUTSTREAM";

    /* PROPERTIES: Client -> Proxy */
    public static final String CONNECTION_PROP_CLIENT_REQUEST_HEADER = "CLIENT_REQUEST_HEADER";

    /* PROPERTIES: Proxy -> Client */
    public static final String CONNECTION_PROP_PROXY_RESPOND_CODE = "PROXY_RESPOND_CODE";
    public static final String CONNECTION_PROP_PROXY_RESPOND_STATUS = "PROXY_RESPOND_STATUS";
    public static final String CONNECTION_PROP_PROXY_RESPOND_HEADER = "PROXY_RESPOND_HEADER";
    public static final String CONNECTION_PROP_PROXY_RESPOND_SIZE = "PROXY_REQUEST_SIZE";

    private final Map<String, String> reverseMappingCache;

    public HeaderFramework() {
        this(null);
    }

    public HeaderFramework(final Map<String, String> reverseMappingCache) {
        // this creates a new TreeMap with a case insensitive mapping
        // to provide a put-method that translates given keys into their
        // 'proper' appearance, a translation cache is needed.
        // upon instantiation, such a mapping cache can be handed over
        // If the reverseMappingCache is null, none is used
        super(ASCII.insensitiveASCIIComparator);
        this.reverseMappingCache = reverseMappingCache;
    }

    public HeaderFramework(final Map<String, String> reverseMappingCache, final Map<String, String> othermap)  {
        // creates a case insensitive map from another map
        super(ASCII.insensitiveASCIIComparator);
        this.reverseMappingCache = reverseMappingCache;

        // load with data
        if (othermap != null) putAll(othermap);
    }

    /** Date formatter/parser for standard compliant HTTP header dates (RFC 1123) */
    private static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss Z"; // with numeric time zone indicator as defined in RFC5322
    private static final String PATTERN_RFC1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";
    private static final String PATTERN_ANSIC   = "EEE MMM d HH:mm:ss yyyy";
    private static final String PATTERN_GSAFS = "yyyy-MM-dd";
    private static final SimpleDateFormat FORMAT_RFC1123      = new SimpleDateFormat(PATTERN_RFC1123, Locale.US);
    private static final SimpleDateFormat FORMAT_RFC1036      = new SimpleDateFormat(PATTERN_RFC1036, Locale.US);
    private static final SimpleDateFormat FORMAT_ANSIC        = new SimpleDateFormat(PATTERN_ANSIC, Locale.US);
    private static final SimpleDateFormat FORMAT_GSAFS        = new SimpleDateFormat(PATTERN_GSAFS, Locale.US);
    private static final TimeZone TZ_GMT = TimeZone.getTimeZone("GMT");
    private static final Calendar CAL_GMT = Calendar.getInstance(TZ_GMT, Locale.US);

    /**
     * RFC 2616 requires that HTTP clients are able to parse all 3 different
     * formats. All times MUST be in GMT/UTC, but ...
     */
    private static final SimpleDateFormat[] FORMATS_HTTP = new SimpleDateFormat[] {
            // RFC 1123/822 (Standard) "Mon, 12 Nov 2007 10:11:12 GMT"
            FORMAT_RFC1123,
            // RFC 1036/850 (old)      "Monday, 12-Nov-07 10:11:12 GMT"
            FORMAT_RFC1036,
            // ANSI C asctime()        "Mon Nov 12 10:11:12 2007"
            FORMAT_ANSIC,
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

    public static final String formatGSAFS(final Date date) {
        if (date == null) return "";
        synchronized (FORMAT_GSAFS) {
            final String s = FORMAT_GSAFS.format(date);
            return s;
        }
    }
    
    public static final Date parseGSAFS(final String datestring) {
        try {
            return FORMAT_GSAFS.parse(datestring);
        } catch (final ParseException e) {
            return null;
        }
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

    // we override the put method to make use of the reverseMappingCache
    @Override
    public String put(final String key, final String value) {
        final String upperK = key.toUpperCase();

        if (this.reverseMappingCache == null) {
            return super.put(key, value);
        }

        if (this.reverseMappingCache.containsKey(upperK)) {
            // we put in the value using the reverse mapping
            return super.put(this.reverseMappingCache.get(upperK), value);
        }

        // we put in without a cached key and store the key afterwards
        final String r = super.put(key, value);
        this.reverseMappingCache.put(upperK, key);
        return r;
    }

    // to make the occurrence of multiple keys possible, we add them using a counter
    public String add(final String key, final String value) {
        final int c = keyCount(key);
        if (c == 0) return put(key, value);
        return put("*" + key + "-" + Integer.toString(c), value);
    }

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

    // return multiple results
    public String getSingle(final String key, final int count) {
        if (count == 0) return get(key, null);
        return get("*" + key + "-" + count, null);
    }

    public Object[] getMultiple(final String key) {
        final int count = keyCount(key);
        final Object[] result = new Object[count];
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
      Content-Type=text/html
      Date=Mon, 05 Jan 2004 11:55:10 GMT
      Server=Apache/1.3.26
    */

    public String mime() {
        return get(CONTENT_TYPE, "application/octet-stream");
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.commons.fileupload.RequestContext#getCharacterEncoding()
     */
    public String getCharacterEncoding() {
        final String mimeType = mime();
        if (mimeType == null) return null;

        final String[] parts = CommonPattern.SEMICOLON.split(mimeType);
        if (parts == null || parts.length <= 1) return null;

        for (int i=1; i < parts.length; i++) {
            final String param = parts[i].trim();
            if (param.startsWith("charset=")) {
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

    /*
     * (non-Javadoc)
     *
     * @see org.apache.commons.fileupload.RequestContext#getContentType()
     */
    //@Override
    public String getContentType() {
        return get(CONTENT_TYPE);
    }

    protected Date headerDate(final String kind) {
        if (containsKey(kind)) {
            Date parsedDate = parseHTTPDate(get(kind));
            if (parsedDate == null) parsedDate = new Date();
            return parsedDate;
        }
        return null;
    }

    public static boolean supportChunkedEncoding(final Properties conProp) {
    	// getting the http version of the client
    	final String httpVer = conProp.getProperty(CONNECTION_PROP_HTTP_VER);

    	// only clients with http version 1.1 supports chunk
        return !(httpVer.equals(HTTP_VERSION_0_9) || httpVer.equals(HTTP_VERSION_1_0));
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
        if (httpVersion == null) httpVersion = "HTTP/1.0";

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

    public static DigestURL getRequestURL(final HashMap<String, Object> conProp) throws MalformedURLException {
        String host =    (String) conProp.get(HeaderFramework.CONNECTION_PROP_HOST);
        final String path =    (String) conProp.get(HeaderFramework.CONNECTION_PROP_PATH);     // always starts with leading '/'
        final String args =    (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS);     // may be null if no args were given
        //String ip =      conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer

        int port, pos;
        if ((pos = host.indexOf(':')) < 0) {
            port = 80;
        } else {
            port = NumberTools.parseIntDecSubstring(host, pos + 1);
            host = host.substring(0, pos);
        }

        final DigestURL url = new DigestURL("http", host, port, (args == null) ? path : path + "?" + args);
        return url;
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


    /*
     * Patch BEGIN:
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
     * @param expires when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     * @param path Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     *
     * Note: this cookie will be sent over each connection independend if it is safe connection or not.
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
     * @param expires when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     * @param path Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     *
     * Note: this cookie will be sent over each connection independend if it is safe connection or not.
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
     * @param expires when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     *
     * Note: this cookie will be sent over each connection independend if it is safe connection or not.
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
     * Note: this cookie will be sent over each connection independend if it is safe connection or not. This cookie never expires
     * @see further documentation: <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(final String name, final String value )
    {
        setCookie( name,  value,  null,  null,  null, false);
    }
    public String getHeaderCookies(){
        final Iterator<Map.Entry<String, String>> it = entrySet().iterator();
        while(it.hasNext())
        {
            final Map.Entry<String, String> e = it.next();
            //System.out.println(""+e.getKey()+" : "+e.getValue());
            if(e.getKey().equals("Cookie"))
            {
                return e.getValue();
            }
        }
        return "";
    }

    public void addHeader(final String key, final String value) {
        this.headerProps.add(new Entry(key, value));
    }

    public Vector<Entry> getAdditionalHeaderProperties() {
        return this.headerProps;
    }

    public void setAdditionalHeaderProperties(final Vector<Entry> mycookies){
        this.headerProps=mycookies;
    }

    /*
     * Patch END:
     * Name: Header Property Patch
     */
}
