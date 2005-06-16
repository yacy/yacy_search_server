// httpHeader.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 29.04.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
   Documentation:
   this class implements a key-value mapping, as a hashtable
   The difference to ordinary hashtable implementations is that the
   keys are not compared by the equal() method, but are always
   treated as string and compared as
   key.uppercase().equal(.uppercase(comparator))
   You use this class by first creation of a static HashMap
   that then is used a the reverse mapping cache for every new
   instance of this class.
*/

package de.anomic.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import de.anomic.server.logging.serverLog;

public final class httpHeader extends TreeMap implements Map {

    
    /* =============================================================
     * Constants defining http header names
     * ============================================================= */    
    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_CHARSET = "Accept-Charset";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    public static final String KEEP_ALIVE = "Keep-Alive";
    public static final String USER_AGENT = "User-Agent";
    public static final String HOST = "Host";
    public static final String CONNECTION = "Connection";
    public static final String REFERER = "Referer";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String AUTHORIZATION = "Authorization";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
    public static final String PROXY_CONNECTION = "Proxy-Connection";
    public static final String DATE = "Date";
    public static final String SERVER = "Server";
    public static final String LAST_MODIFIED = "Last-modified";
    public static final String PRAGMA = "Pragma";
    public static final String SET_COOKIE = "Set-Cookie";
    public static final String SET_COOKIE2 = "Set-Cookie2";
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String COOKIE = "Cookie";
    public static final String EXPIRES = "Expires";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String CONTENT_RANGE = "Content-Range";
    public static final String RANGE = "Range";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";
    
    public static final String X_CACHE = "X-Cache";
    public static final String X_CACHE_LOOKUP = "X-Cache-Lookup";
    
    public static final String X_YACY_KEEP_ALIVE_REQUEST_COUNT = "X-Keep-Alive-Request-Count";
    public static final String X_YACY_ORIGINAL_REQUEST_LINE = "X-Original-Request-Line";
    
    /* =============================================================
     * Constants defining http methods
     * ============================================================= */
    public static final String METHOD_GET = "GET";
    public static final String METHOD_HEAD = "HEAD";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_CONNECT = "CONNECT";    
    
    /* =============================================================
     * defining default http status messages
     * ============================================================= */
    public static final HashMap http0_9 = new HashMap();
    public static final HashMap http1_0 = new HashMap();
    static {
        http1_0.putAll(http0_9);
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
    public static final HashMap http1_1 = new HashMap(); 
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
    
    private final HashMap reverseMappingCache;

    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
	insensitiveCollator.setStrength(Collator.SECONDARY);
	insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }

    public httpHeader() {
	this(null);
    }

    public httpHeader(HashMap reverseMappingCache) {
	// this creates a new TreeMap with a case insesitive mapping
	// to provide a put-method that translates given keys into their
	// 'proper' appearance, a translation cache is needed.
	// upon instantiation, such a mapping cache can be handed over
	// If the reverseMappingCache is null, none is used
	super(insensitiveCollator);
	this.reverseMappingCache = reverseMappingCache;
    }

    public httpHeader(HashMap reverseMappingCache, File f) throws IOException {
	// creates also a case insensitive map and loads it initially
	// with some values
	super(insensitiveCollator);
	this.reverseMappingCache = reverseMappingCache;

	// load with data
	BufferedReader br = new BufferedReader(new FileReader(f));
	String line;
	int pos;
	while ((line = br.readLine()) != null) {
	    pos = line.indexOf("=");
	    if (pos >= 0) put(line.substring(0, pos), line.substring(pos + 1));
	}
	br.close();
    }

    public httpHeader(HashMap reverseMappingCache, Map othermap)  {
	// creates a case insensitive map from another map
	super(insensitiveCollator);
	this.reverseMappingCache = reverseMappingCache;

	// load with data
	if (othermap != null) this.putAll(othermap);
    }


    // we override the put method to make use of the reverseMappingCache
    public Object put(Object key, Object value) {
	String k = (String) key;
        String upperK = k.toUpperCase();
	if (reverseMappingCache == null) {
	    return super.put(k, value);
	} else {
	    if (reverseMappingCache.containsKey(upperK)) {
		// we put in the value using the reverse mapping
		return super.put(reverseMappingCache.get(upperK), value);
	    } else {
		// we put in without a cached key and store the key afterwards
		Object r = super.put(k, value);
		reverseMappingCache.put(upperK, k);
		return r;
	    }
	}
    }

    // to make the occurrence of multiple keys possible, we add them using a counter
    public Object add(Object key, Object value) {
        int c = keyCount((String) key);
        if (c == 0) return put(key, value); else return put("*" + key + "-" + c, value);
    }
    
    public int keyCount(String key) {
        if (!(containsKey(key))) return 0;
        int c = 1;
        while (containsKey("*" + key + "-" + c)) c++;
        return c;
    }
    
    // a convenience method to access the map with fail-over defaults
    public Object get(Object key, Object dflt) {
	Object result = get(key);
	if (result == null) return dflt; else return result;
    }

    // return multiple results
    public Object getSingle(Object key, int count) {
        if (count == 0) return get(key, null);
        return get("*" + key + "-" + count, null);
    }
    
    public Object[] getMultiple(String key) {
        int count = keyCount(key);
        Object[] result = new Object[count];
        for (int i = 0; i < count; i++) result[i] = getSingle(key, i);
        return result;
    }
    
    // convenience methods for storing and loading to a file system
    public void store(File f) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            Iterator i = keySet().iterator();
            String key, value;
            while (i.hasNext()) {
                key = (String) i.next();
                value = (String) get(key);
                fos.write((key + "=" + value + "\r\n").getBytes());
            }
            fos.flush();
        } finally {
            if (fos != null) try{fos.close();}catch(Exception e){}
        }
    }

    public String toString() {
        return super.toString();
    }
    	/*
	  Connection=close
	  Content-Encoding=gzip
	  Content-Length=7281
	  Content-Type=text/html
	  Date=Mon, 05 Jan 2004 11:55:10 GMT
	  Server=Apache/1.3.26
	*/
    
    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    private static SimpleDateFormat HTTPGMTFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
    private static SimpleDateFormat EMLFormatter     = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US);

    
    public static Date parseHTTPDate(String s) {
	if ((s == null) || (s.length() < 9)) return new Date();
	s = s.trim();
	if (s.charAt(3) == ',') s = s.substring(5).trim(); // we skip the name of the day
	if (s.charAt(9) == ' ') s = s.substring(0, 7) + "20" + s.substring(7); // short year version
	if (s.charAt(2) == ',') s = s.substring(0, 2) + s.substring(3); // ommit comma after day of week
	if ((s.charAt(0) > '9') && (s.length() > 20) && (s.charAt(2) == ' ')) s = s.substring(3);
	if (s.length() > 20) s = s.substring(0, 20).trim(); // truncate remaining, since that must be wrong
        if (s.indexOf("Mrz") > 0) s = s.replaceAll("Mrz", "March");
	try {
	    return EMLFormatter.parse(s);
	} catch (java.text.ParseException e) {
	    //System.out.println("ERROR long version parse: " + e.getMessage() +  " at position " +  e.getErrorOffset());
	    serverLog.logError("HTTPC-header", "DATE ERROR (Parse): " + s);
	    return new Date();
	} catch (java.lang.NumberFormatException e) {
	    //System.out.println("ERROR long version parse: " + e.getMessage() +  " at position " +  e.getErrorOffset());
	    serverLog.logError("HTTPC-header", "DATE ERROR (NumberFormat): " + s);
	    return new Date();
	}
    }

    private Date headerDate(String kind) {
        if (containsKey(kind)) return parseHTTPDate((String) get(kind));
        else return null;
    }
    
    public String mime() {
        return (String) get(httpHeader.CONTENT_TYPE, "application/octet-stream");
    }
    
    public Date date() {
        return headerDate(httpHeader.DATE);
    }
    
    public Date expires() {
        return headerDate(httpHeader.EXPIRES);
    }
    
    public Date lastModified() {
        return headerDate(httpHeader.LAST_MODIFIED);
    }
    
    public Date ifModifiedSince() {
        return headerDate(httpHeader.IF_MODIFIED_SINCE);
    }
    
    public long age() {
        Date lm = lastModified();
        if (lm == null) return Long.MAX_VALUE; else return (new Date()).getTime() - lm.getTime();
    }
    
    public long contentLength() {
        if (containsKey(httpHeader.CONTENT_LENGTH)) {
            try {
                return Long.parseLong((String) get(httpHeader.CONTENT_LENGTH));
            } catch (NumberFormatException e) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public boolean acceptGzip() {
        return ((containsKey(httpHeader.ACCEPT_ENCODING)) &&
                (((String) get(httpHeader.ACCEPT_ENCODING)).toUpperCase().indexOf("GZIP")) != -1);        
    }
    
    public boolean gzip() {
        return ((containsKey(httpHeader.CONTENT_ENCODING)) &&
		(((String) get(httpHeader.CONTENT_ENCODING)).toUpperCase().startsWith("GZIP")));
    }
    /*
    public static void main(String[] args) {
	Collator c;
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.PRIMARY);
	System.out.println("PRIMARY:   compare(abc, ABC) = " + c.compare("abc", "ABC"));
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.SECONDARY);
	System.out.println("SECONDARY: compare(abc, ABC) = " + c.compare("abc", "ABC"));
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.TERTIARY);
	System.out.println("TERTIARY:  compare(abc, ABC) = " + c.compare("abc", "ABC"));
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.IDENTICAL);
	System.out.println("IDENTICAL: compare(abc, ABC) = " + c.compare("abc", "ABC"));
    }
    */
}