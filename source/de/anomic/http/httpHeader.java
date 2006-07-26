// httpHeader.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// last major change: $LastChangedDate$ by $LastChangedBy$
// Revision: $LastChangedRevision$
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
import java.net.MalformedURLException;
import de.anomic.net.URL;
import java.text.Collator;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Vector;

import de.anomic.server.serverCore;
import de.anomic.server.logging.serverLog;


public final class httpHeader extends TreeMap implements Map {

    
	private static final long serialVersionUID = 17L;
	
	/* =============================================================
     * Constants defining http versions
     * ============================================================= */
    public static final String HTTP_VERSION_0_9 = "HTTP/0.9";
    public static final String HTTP_VERSION_1_0 = "HTTP/1.0";
    public static final String HTTP_VERSION_1_1 = "HTTP/1.1";
    
    /* =============================================================
     * Constants defining http header names
     * ============================================================= */    
    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_CHARSET = "Accept-Charset";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    

    public static final String HOST = "Host";
    
    public static final String CONNECTION = "Connection";
    public static final String PROXY_CONNECTION = "Proxy-Connection";
    public static final String KEEP_ALIVE = "Keep-Alive";
    
    public static final String REFERER = "Referer";
    public static final String USER_AGENT = "User-Agent";

    public static final String AUTHORIZATION = "Authorization";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
    public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";

    public static final String DATE = "Date";
    public static final String SERVER = "Server";

    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_MD5 = "Content-MD5";
    
    public static final String SET_COOKIE = "Set-Cookie";
    public static final String SET_COOKIE2 = "Set-Cookie2";
    public static final String COOKIE = "Cookie";
    public static final String EXPIRES = "Expires";

    
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";
    
    public static final String ACCEPT_RANGES = "Accept-Ranges";
    public static final String CONTENT_RANGE = "Content-Range";
    public static final String RANGE = "Range";
    public static final String IF_RANGE = "If-Range";
    
    public static final String PRAGMA = "Pragma";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    public static final String LAST_MODIFIED = "Last-modified";

    public static final String LOCATION = "Location";
    public static final String ETAG = "ETag";
    public static final String VIA = "Via";
    
    public static final String X_CACHE = "X-Cache";
    public static final String X_CACHE_LOOKUP = "X-Cache-Lookup";
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    
    public static final String X_YACY_KEEP_ALIVE_REQUEST_COUNT = "X-Keep-Alive-Request-Count";
    public static final String X_YACY_ORIGINAL_REQUEST_LINE = "X-Original-Request-Line";
    public static final String X_YACY_PREVIOUS_REQUEST_LINE = "X-Previous-Request-Line";
    
    public static final String X_YACY_INDEX_CONTROL = "X-YACY-Index-Control";
    
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
    public static final String CONNECTION_PROP_KEEP_ALIVE_COUNT = "KEEP-ALIVE_COUNT";
    public static final String CONNECTION_PROP_REQUESTLINE = "REQUESTLINE";
    public static final String CONNECTION_PROP_PREV_REQUESTLINE = "PREVREQUESTLINE";
    public static final String CONNECTION_PROP_REQUEST_START = "REQUEST_START";
    public static final String CONNECTION_PROP_REQUEST_END = "REQUEST_END";
    
    /* PROPERTIES: Client -> Proxy */
    public static final String CONNECTION_PROP_CLIENT_REQUEST_HEADER = "CLIENT_REQUEST_HEADER";
    
    /* PROPERTIES: Proxy -> Client */
    public static final String CONNECTION_PROP_PROXY_RESPOND_CODE = "PROXY_RESPOND_CODE";
    public static final String CONNECTION_PROP_PROXY_RESPOND_STATUS = "PROXY_RESPOND_STATUS";
    public static final String CONNECTION_PROP_PROXY_RESPOND_HEADER = "PROXY_RESPOND_HEADER";
    public static final String CONNECTION_PROP_PROXY_RESPOND_SIZE = "PROXY_REQUEST_SIZE";    

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
	super((Collator) insensitiveCollator.clone());
	this.reverseMappingCache = reverseMappingCache;
    }

    public httpHeader(HashMap reverseMappingCache, File f) throws IOException {
	// creates also a case insensitive map and loads it initially
	// with some values
	super((Collator) insensitiveCollator.clone());
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
	super((Collator) insensitiveCollator.clone());
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
        }
        
        if (reverseMappingCache.containsKey(upperK)) {
            // we put in the value using the reverse mapping
            return super.put(reverseMappingCache.get(upperK), value);
        }
        
        // we put in without a cached key and store the key afterwards
        Object r = super.put(k, value);
        reverseMappingCache.put(upperK, k);
        return r;
    }

    // to make the occurrence of multiple keys possible, we add them using a counter
    public Object add(Object key, Object value) {
        int c = keyCount((String) key);
        if (c == 0) return put(key, value);
        return put("*" + key + "-" + c, value);
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
        if (result == null) return dflt;
        return result;
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
    
    //private static SimpleDateFormat HTTPGMTFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
    private static SimpleDateFormat EMLFormatter     = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US);
    
    public static Date parseHTTPDate(String s) {
        try {
            return parseHTTPDate(s,true);
        } catch (ParseException e) {
            //System.out.println("ERROR long version parse: " + e.getMessage() +  " at position " +  e.getErrorOffset());
            serverLog.logSevere("HTTPC-header", "DATE ERROR (Parse): " + s);
            return null;
        } catch (java.lang.NumberFormatException e) {
            //System.out.println("ERROR long version parse: " + e.getMessage() +  " at position " +  e.getErrorOffset());
            serverLog.logSevere("HTTPC-header", "DATE ERROR (NumberFormat): " + s);
            return null;
        }
    }
    
    public static Date parseHTTPDate(String s,boolean ignoreTimezone) throws ParseException, NumberFormatException {
        
        SimpleDateFormat formatter = EMLFormatter;
        if ((s == null) || (s.length() < 9)) return null;
        s = s.trim();
        if (s.charAt(3) == ',') s = s.substring(5).trim(); // we skip the name of the day
        if (s.charAt(9) == ' ') s = s.substring(0, 7) + "20" + s.substring(7); // short year version
        if (s.charAt(2) == ',') s = s.substring(0, 2) + s.substring(3); // ommit comma after day of week
        if ((s.charAt(0) > '9') && (s.length() > 20) && (s.charAt(2) == ' ')) s = s.substring(3);
        if (s.length() > 20) {
            if (!ignoreTimezone) {
                formatter = (SimpleDateFormat) formatter.clone();
                formatter.setTimeZone(TimeZone.getTimeZone(s.substring(20)));
            }
            s = s.substring(0, 20).trim(); // truncate remaining, since that must be wrong
        }
        if (s.indexOf("Mrz") > 0) s = s.replaceAll("Mrz", "March");
        
        // parsing the date string
        return formatter.parse(s);
    }

    private Date headerDate(String kind) {
        if (containsKey(kind)) {
            Date parsedDate = parseHTTPDate((String) get(kind));
            if (parsedDate == null) parsedDate = new Date();
            return new Date(parsedDate.getTime());
        }
        return null;
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
    
    public Object ifRange() {
        if (containsKey(httpHeader.IF_RANGE)) {
            try {
                Date rangeDate = parseHTTPDate((String) get(httpHeader.IF_RANGE),false);
                if (rangeDate != null) return new Date(rangeDate.getTime());
            } catch (Exception e) {}
            return get(httpHeader.IF_RANGE);
        } 
        return null;
    }
    
    public long age() {
        Date lm = lastModified();
        Date sd = date();
        if (lm == null) return Long.MAX_VALUE;
        return ((sd == null) ? new Date() : sd).getTime() - lm.getTime();
    }
    
    public long contentLength() {
        if (containsKey(httpHeader.CONTENT_LENGTH)) {
            try {
                return Long.parseLong((String) get(httpHeader.CONTENT_LENGTH));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    public boolean acceptGzip() {
        return ((containsKey(httpHeader.ACCEPT_ENCODING)) &&
                (((String) get(httpHeader.ACCEPT_ENCODING)).toUpperCase().indexOf("GZIP")) != -1);        
    }
    
    public boolean gzip() {
        return ((containsKey(httpHeader.CONTENT_ENCODING)) &&
		(((String) get(httpHeader.CONTENT_ENCODING)).toUpperCase().startsWith("GZIP")));
    }
    
    public static Object[] parseResponseLine(String respLine) {
        
        if ((respLine == null) || (respLine.length() == 0)) {
            return new Object[]{"HTTP/1.0",new Integer(500),"status line parse error"};
        }
        
        int p = respLine.indexOf(" ");
        if (p < 0) {
            return new Object[]{"HTTP/1.0",new Integer(500),"status line parse error"};
        }
        
        String httpVer, status, statusText;
        Integer statusCode;
        
        // the http version reported by the server
        httpVer = respLine.substring(0,p);
        
        // Status of the request, e.g. "200 OK"
        status = respLine.substring(p + 1).trim(); // the status code plus reason-phrase
        
        // splitting the status into statuscode and statustext
        p = status.indexOf(" ");
        try {
            statusCode = Integer.valueOf((p < 0) ? status.trim() : status.substring(0,p).trim());
            statusText = (p < 0) ? "" : status.substring(p+1).trim();
        } catch (Exception e) {
            statusCode = new Integer(500);
            statusText = status;
        }
        
        return new Object[]{httpVer,statusCode,statusText};
    }
    
    public static Properties parseRequestLine(String s, Properties prop, String virtualHost) {
        int p = s.indexOf(" ");
        if (p >= 0) {
            String cmd = s.substring(0,p);
            String args = s.substring(p+1);
            return parseRequestLine(cmd,args, prop,virtualHost);
        }
        return prop;
    }
    
    public static Properties parseRequestLine(String cmd, String args, Properties prop, String virtualHost) {
        
        // getting the last request line for debugging purposes
        String prevRequestLine = prop.containsKey(httpHeader.CONNECTION_PROP_REQUESTLINE)?
                prop.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE) : "";
        
        // reset property from previous run   
        prop.clear();

        // storing informations about the request
        prop.setProperty(httpHeader.CONNECTION_PROP_METHOD, cmd);
        prop.setProperty(httpHeader.CONNECTION_PROP_REQUESTLINE,cmd + " " + args);
        prop.setProperty(httpHeader.CONNECTION_PROP_PREV_REQUESTLINE,prevRequestLine);
        
        // this parses a whole URL
        if (args.length() == 0) {
            prop.setProperty(httpHeader.CONNECTION_PROP_HOST, virtualHost);
            prop.setProperty(httpHeader.CONNECTION_PROP_PATH, "/");
            prop.setProperty(httpHeader.CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
            prop.setProperty(httpHeader.CONNECTION_PROP_EXT, "");
            return prop;
        }
        
        // store the version propery "HTTP" and cut the query at both ends
        int sep = args.lastIndexOf(" ");
        if ((sep >= 0)&&(args.substring(sep + 1).toLowerCase().startsWith("http/"))) {
            // HTTP version is given
            prop.setProperty(httpHeader.CONNECTION_PROP_HTTP_VER, args.substring(sep + 1).trim());
            args = args.substring(0, sep).trim(); // cut off HTTP version mark
        } else {
            // HTTP version is not given, it will be treated as ver 0.9
            prop.setProperty(httpHeader.CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
        }
        
        // replacing spaces in the url string correctly
        args = args.replaceAll(" ","%20");
        
        
        // properties of the query are stored with the prefix "&"
        // additionally, the values URL and ARGC are computed
        
        String argsString = "";
        sep = args.indexOf("?");
        if (sep >= 0) {
            // there are values attached to the query string
            argsString = args.substring(sep + 1); // cut haed from tail of query
            args = args.substring(0, sep);
        }
        prop.setProperty(httpHeader.CONNECTION_PROP_URL, args); // store URL
        //System.out.println("HTTPD: ARGS=" + argsString);
        if (argsString.length() != 0) prop.setProperty(httpHeader.CONNECTION_PROP_ARGS, argsString); // store arguments in original form
        
        // find out file extension
        sep = args.lastIndexOf(".");
        if (sep >= 0) {
            if (args.indexOf("?", sep + 1) >= sep)
                prop.setProperty(httpHeader.CONNECTION_PROP_EXT, args.substring(sep + 1, args.indexOf("?", sep + 1)).toLowerCase());
            else if (args.indexOf("#", sep + 1) >= sep)
                prop.setProperty(httpHeader.CONNECTION_PROP_EXT, args.substring(sep + 1, args.indexOf("#", sep + 1)).toLowerCase());
            else
                prop.setProperty(httpHeader.CONNECTION_PROP_EXT, args.substring(sep + 1).toLowerCase());
        } else {
            prop.setProperty(httpHeader.CONNECTION_PROP_EXT, "");
        }
        
        // finally find host string
        if (args.toUpperCase().startsWith("HTTP://")) {
            // a host was given. extract it and set path
            args = args.substring(7);
            sep = args.indexOf("/");
            if (sep < 0) {
                // this is a malformed url, something like
                // http://index.html
                // we are lazy and guess that it means
                // /index.html
                // which is a localhost access to the file servlet
                prop.setProperty(httpHeader.CONNECTION_PROP_HOST, virtualHost);
                prop.setProperty(httpHeader.CONNECTION_PROP_PATH, "/" + args);
            } else {
                // THIS IS THE "GOOD" CASE
                // a perfect formulated url
                String dstHostSocket = args.substring(0, sep);
                prop.setProperty(httpHeader.CONNECTION_PROP_HOST, (httpd.isThisHostName(dstHostSocket)?virtualHost:dstHostSocket));
                prop.setProperty(httpHeader.CONNECTION_PROP_PATH, args.substring(sep)); // yes, including beginning "/"
            }
        } else {
            // no host in url. set path
            if (args.startsWith("/")) {
                // thats also fine, its a perfect localhost access
                // in this case, we simulate a
                // http://localhost/s
                // access by setting a virtual host
                prop.setProperty(httpHeader.CONNECTION_PROP_HOST, virtualHost);
                prop.setProperty(httpHeader.CONNECTION_PROP_PATH, args);
            } else {
                // the client 'forgot' to set a leading '/'
                // this is the same case as above, with some lazyness
                prop.setProperty(httpHeader.CONNECTION_PROP_HOST, virtualHost);
                prop.setProperty(httpHeader.CONNECTION_PROP_PATH, "/" + args);
            }
        }
        return prop;
    }    
    
    /**
     * Reading http headers from a reader class and building up a httpHeader object
     * @param reader the {@link BufferedReader} that is used to read the http header lines
     * @return a {@link httpHeader}-Object containing all parsed headers
     * @throws IOException
     */
    public static httpHeader readHttpHeader(BufferedReader reader) throws IOException {
        // reading all request headers
        httpHeader httpHeader = new httpHeader(httpd.reverseMappingCache);
        int p;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() == 0) break; 
            if ((p = line.indexOf(":")) >= 0) {
                // store a property
                httpHeader.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
            }
        }   
        return httpHeader;
    }        
    
    public static httpHeader readHeader(Properties prop, serverCore.Session theSession) throws IOException {
        
        // reading all headers
        httpHeader header = new httpHeader(httpd.reverseMappingCache);
        int p;
        String line;
        while ((line = theSession.readLineAsString()) != null) {
            if (line.length() == 0) break; // this seperates the header of the HTTP request from the body
            // parse the header line: a property seperated with the ':' sign
            if ((p = line.indexOf(":")) >= 0) {
                // store a property
                header.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
            }
        }
        
        /* 
         * doing some header validation here ...
         */
        String httpVersion = prop.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
        if (httpVersion.equals("HTTP/1.1") && !header.containsKey(httpHeader.HOST)) {
            // the HTTP/1.1 specification requires that an HTTP/1.1 server must reject any  
            // HTTP/1.1 message that does not contain a Host header.            
            httpd.sendRespondError(prop,theSession.out,0,400,null,null,null);
            throw new IOException("400 Bad request");
        }     
        
        return header;
    }
 
    
    public StringBuffer toHeaderString(
            String httpVersion, 
            int httpStatusCode, 
            String httpStatusText) {
        // creating a new buffer to store the header as string
        StringBuffer theHeader = new StringBuffer();
        
        // generating the header string
        this.toHeaderString(httpVersion,httpStatusCode,httpStatusText,theHeader);
        
        // returning the result
        return theHeader;
    }
    
    
    public void toHeaderString(
            String httpVersion, 
            int httpStatusCode, 
            String httpStatusText, 
            StringBuffer theHeader) {        
        
        if (theHeader == null) throw new IllegalArgumentException();
        
        // setting the http version if it was not already set
        if (httpVersion == null) httpVersion = "HTTP/1.0";
        
        // setting the status text if it was not already set
        if ((httpStatusText == null)||(httpStatusText.length()==0)) {
            if (httpVersion.equals("HTTP/1.0") && httpHeader.http1_0.containsKey(Integer.toString(httpStatusCode))) 
                httpStatusText = (String) httpHeader.http1_0.get(Integer.toString(httpStatusCode));
            else if (httpVersion.equals("HTTP/1.1") && httpHeader.http1_1.containsKey(Integer.toString(httpStatusCode)))
                httpStatusText = (String) httpHeader.http1_1.get(Integer.toString(httpStatusCode));
            else httpStatusText = "Unknown";
        }
        
        
        // write status line
        theHeader.append(httpVersion).append(" ")
                 .append(Integer.toString(httpStatusCode)).append(" ")
                 .append(httpStatusText).append("\r\n");
        
        // write header
        Iterator i = keySet().iterator();
        String key;
        char tag;
        int count;
        while (i.hasNext()) {
            key = (String) i.next();
            tag = key.charAt(0);
            if ((tag != '*') && (tag != '#')) { // '#' in key is reserved for proxy attributes as artificial header values
                count = keyCount(key);
                for (int j = 0; j < count; j++) {
                    theHeader.append(key).append(": ").append((String) getSingle(key, j)).append("\r\n");  
                }
            }            
        }
        // end header
        theHeader.append("\r\n");                
    }    
    
    public static URL getRequestURL(Properties conProp) throws MalformedURLException {
        String host =    conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
        String path =    conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);     // always starts with leading '/'
        String args =    conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);     // may be null if no args were given
        //String ip =      conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer
        
        int port, pos;        
        if ((pos = host.indexOf(":")) < 0) {
            port = 80;
        } else {
            port = Integer.parseInt(host.substring(pos + 1));
            host = host.substring(0, pos);
        }
        
        URL url = new URL("http", host, port, (args == null) ? path : path + "?" + args);
        return url;
    }

    public static void handleTransparentProxySupport(httpHeader header, Properties prop, String virtualHost, boolean isTransparentProxy) {   
        // transparent proxy support is only available for http 1.0 and above connections
        if (prop.getProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9").equals("HTTP/0.9")) return;
        
        // if the transparent proxy support was disabled, we have nothing todo here ...
        if (!(isTransparentProxy && header.containsKey(HOST))) return;
        
        // we only need to do the transparent proxy support if the request URL didn't contain the hostname
        // and therefor was set to virtualHost by function parseQuery()
        if (!prop.getProperty(CONNECTION_PROP_HOST).equals(virtualHost)) return;
        
        // TODO: we could have problems with connections from extern here ...
        String dstHostSocket = (String) header.get(httpHeader.HOST);
        prop.setProperty(CONNECTION_PROP_HOST,(httpd.isThisHostName(dstHostSocket)?virtualHost:dstHostSocket));
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
    private Vector cookies=new Vector();
    /**
     *
     * Implementation of Map.Entry. Structure that hold two values - exactly what we need!
     */
    class Entry implements Map.Entry
    {
        private Object Key;
        private Object Value;
        Entry(Object Key,String Value){this.Key=Key;this.Value=Value;}
        public Object getKey() {return Key;}
        public Object getValue() {return Value;}
        public Object setValue(Object Value) {return(this.Value=Value);}
    }

    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Cookie name
     * @param value: Cookie value
     * @param expires: when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     * @param path: Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain: Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     * @param secure: If true cookie will be send only over safe connection such as https
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(String name, String value, String expires, String path, String domain, boolean secure)
    {
         /*
         * TODO:Here every value can be validated for correctness if needed
         * For example semicolon should be not in any of the values
         * However an exception in this case would be an overhead IMHO.
         */
        String cookieString=name+"="+value+";";
        if(expires!=null)
            cookieString+=" expires="+expires+";";
        if(path!=null)
            cookieString+=" path="+path+";";
        if(domain!=null)
            cookieString+=" domain="+domain+";";
        if(secure)
            cookieString+=" secure;";
        cookies.add(new Entry("Set-Cookie",cookieString));
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Cookie name
     * @param value: Cookie value
     * @param expires: when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     * @param path: Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     * @param domain: Domain this cookie belongs to. Default - domain name. Can be <b>null</b>.
     *
     * Note: this cookie will be sent over each connection independend if it is safe connection or not.
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(String name, String value, String expires, String path, String domain)
    {
        setCookie( name,  value,  expires,  path,  domain, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Cookie name
     * @param value: Cookie value
     * @param expires: when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     * @param path: Path the cookie belongs to. Default - "/". Can be <b>null</b>.
     *
     * Note: this cookie will be sent over each connection independend if it is safe connection or not.
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(String name, String value, String expires, String path)
    {
        setCookie( name,  value,  expires,  path,  null, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Cookie name
     * @param value: Cookie value
     * @param expires: when should this cookie be autmatically deleted. If <b>null</b> - cookie will stay forever
     *
     * Note: this cookie will be sent over each connection independend if it is safe connection or not.
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(String name, String value, String expires)
    {
        setCookie( name,  value,  expires,  null,  null, false);
    }
    /**
     * Sets Cookie on the client machine.
     *
     * @param name: Cookie name
     * @param value: Cookie value
     *
     * Note: this cookie will be sent over each connection independend if it is safe connection or not. This cookie never expires
     * Further documentation at <a href="http://docs.sun.com/source/816-6408-10/cookies.htm">docs.sun.com</a>
     */
    public void setCookie(String name, String value )
    {
        setCookie( name,  value,  null,  null,  null, false);
    }
    public String getHeaderCookies(){
        Iterator it = this.entrySet().iterator();
        while(it.hasNext())
        {
            java.util.Map.Entry e = (java.util.Map.Entry) it.next();
            //System.out.println(""+e.getKey()+" : "+e.getValue());
            if(e.getKey().equals("Cookie"))
            {
                return e.getValue().toString();
            }
        }
        return "";
    }
    public Vector getCookieVector(){
        return cookies;
    }
    public void setCookieVector(Vector mycookies){
        cookies=mycookies;
    }
    /**
     * Returns an iterator within all properties can be reached.
     * Is used mainly by httpd.
     * @return iterator to read all request properties.
     *
     * Example:
     *
     * Iterator it=serverObjects.getRequestProperties();
     * while(it.hasNext())
     * {
     *  java.util.Map.Entry e=(java.util.Map.Entry)it.next();
     *  String propertyName=e.getKey();
     *  String propertyValue=e.getValue();
     * }
     */
    public Iterator getCookies()
    {
        return cookies.iterator();
    }
    /*
     * Patch END:
     * Name: Header Property Patch
     */ 
}
