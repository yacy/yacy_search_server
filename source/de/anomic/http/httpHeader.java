// httpHeader.java 
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
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
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.Collator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

import de.anomic.server.serverDate;
import de.anomic.yacy.yacyURL;


public class httpHeader extends TreeMap<String, String> implements Map<String, String> {

    
    private static final long serialVersionUID = 18L;
    
    static final String DEFAULT_CHARSET = "ISO-8859-1";
	
	/* =============================================================
     * Constants defining http versions
     * ============================================================= */
    public static final String HTTP_VERSION_0_9 = "HTTP/0.9";
    public static final String HTTP_VERSION_1_0 = "HTTP/1.0";
    public static final String HTTP_VERSION_1_1 = "HTTP/1.1";
    
    /* =============================================================
     * Constants defining http header names
     * ============================================================= */    
    

    // TODO: sort these header properties into request and response properties (some are both)
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
    public static final String SERVER = "Server";
    
    public static final String ACCEPT_RANGES = "Accept-Ranges";
    public static final String CONTENT_RANGE = "Content-Range";
    public static final String RANGE = "Range";
    
    public static final String LOCATION = "Location";
    public static final String ETAG = "ETag";
    public static final String VIA = "Via";
    
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_YACY_INDEX_CONTROL = "X-YACY-Index-Control";
    
    
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
    
    /* =============================================================
     * defining default http status messages
     * ============================================================= */
    public static final HashMap<String, String> http0_9 = new HashMap<String, String>();
    public static final HashMap<String, String> http1_0 = new HashMap<String, String>();
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
    public static final HashMap<String, String> http1_1 = new HashMap<String, String>(); 
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
    //public static final String CONNECTION_PROP_INPUTSTREAM = "INPUTSTREAM";
    //public static final String CONNECTION_PROP_OUTPUTSTREAM = "OUTPUTSTREAM";
    
    /* PROPERTIES: Client -> Proxy */
    public static final String CONNECTION_PROP_CLIENT_REQUEST_HEADER = "CLIENT_REQUEST_HEADER";
    
    /* PROPERTIES: Proxy -> Client */
    public static final String CONNECTION_PROP_PROXY_RESPOND_CODE = "PROXY_RESPOND_CODE";
    public static final String CONNECTION_PROP_PROXY_RESPOND_STATUS = "PROXY_RESPOND_STATUS";
    public static final String CONNECTION_PROP_PROXY_RESPOND_HEADER = "PROXY_RESPOND_HEADER";
    public static final String CONNECTION_PROP_PROXY_RESPOND_SIZE = "PROXY_REQUEST_SIZE";    

    private final HashMap<String, String> reverseMappingCache;

    
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }

    public httpHeader() {
        this(null);
    }

    public httpHeader(final HashMap<String, String> reverseMappingCache) {
        // this creates a new TreeMap with a case insensitive mapping
        // to provide a put-method that translates given keys into their
        // 'proper' appearance, a translation cache is needed.
        // upon instantiation, such a mapping cache can be handed over
        // If the reverseMappingCache is null, none is used
        super((Collator) insensitiveCollator.clone());
        this.reverseMappingCache = reverseMappingCache;
    }

    public httpHeader(final HashMap<String, String> reverseMappingCache, final Map<String, String> othermap)  {
        // creates a case insensitive map from another map
        super((Collator) insensitiveCollator.clone());
        this.reverseMappingCache = reverseMappingCache;

        // load with data
        if (othermap != null) this.putAll(othermap);
    }


    // we override the put method to make use of the reverseMappingCache
    public String put(final String key, final String value) {
        final String upperK = key.toUpperCase();
        
        if (reverseMappingCache == null) {
            return super.put(key, value);
        }
        
        if (reverseMappingCache.containsKey(upperK)) {
            // we put in the value using the reverse mapping
            return super.put(reverseMappingCache.get(upperK), value);
        }
        
        // we put in without a cached key and store the key afterwards
        final String r = super.put(key, value);
        reverseMappingCache.put(upperK, key);
        return r;
    }

    // to make the occurrence of multiple keys possible, we add them using a counter
    public String add(final String key, final String value) {
        final int c = keyCount(key);
        if (c == 0) return put(key, value);
        return put("*" + key + "-" + c, value);
    }
    
    public int keyCount(final String key) {
        if (!(containsKey(key))) return 0;
        int c = 1;
        while (containsKey("*" + key + "-" + c)) c++;
        return c;
    }
    
    // a convenience method to access the map with fail-over defaults
    public Object get(final Object key, final Object dflt) {
        final Object result = get(key);
        if (result == null) return dflt;
        return result;
    }

    // return multiple results
    public Object getSingle(final Object key, final int count) {
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
            for (java.util.Map.Entry<String, String> entry: entrySet()) {
                fos.write((entry.getKey() + "=" + entry.getValue() + "\r\n").getBytes());
            }
            fos.flush();
        } finally {
            if (fos != null) try{fos.close();}catch(final Exception e){}
        }
    }

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
        return (String) get(CONTENT_TYPE, "application/octet-stream");
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
        
        final String[] parts = mimeType.split(";");
        if (parts == null || parts.length <= 1) return null;
        
        for (int i=1; i < parts.length; i++) {    
            final String param = parts[i].trim();
            if (param.startsWith("charset=")) {
                String charset = param.substring("charset=".length()).trim();
                if (charset.startsWith("\"") || charset.startsWith("'")) charset = charset.substring(1);
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
            Date parsedDate = serverDate.parseHTTPDate(get(kind));
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
    
    public StringBuffer toHeaderString(
            final String httpVersion, 
            final int httpStatusCode, 
            final String httpStatusText) {
        // creating a new buffer to store the header as string
        final StringBuffer theHeader = new StringBuffer();
        
        // generating the header string
        this.toHeaderString(httpVersion,httpStatusCode,httpStatusText,theHeader);
        
        // returning the result
        return theHeader;
    }
    
    
    public void toHeaderString(
            String httpVersion, 
            final int httpStatusCode, 
            String httpStatusText, 
            final StringBuffer theHeader) {        
        
        if (theHeader == null) throw new IllegalArgumentException();
        
        // setting the http version if it was not already set
        if (httpVersion == null) httpVersion = "HTTP/1.0";
        
        // setting the status text if it was not already set
        if ((httpStatusText == null)||(httpStatusText.length()==0)) {
            if (httpVersion.equals("HTTP/1.0") && httpHeader.http1_0.containsKey(Integer.toString(httpStatusCode))) 
                httpStatusText = httpHeader.http1_0.get(Integer.toString(httpStatusCode));
            else if (httpVersion.equals("HTTP/1.1") && httpHeader.http1_1.containsKey(Integer.toString(httpStatusCode)))
                httpStatusText = httpHeader.http1_1.get(Integer.toString(httpStatusCode));
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
                    theHeader.append(key).append(": ").append((String) getSingle(key, j)).append("\r\n");  
                }
            }            
        }
        // end header
        theHeader.append("\r\n");                
    }    
    
    public static yacyURL getRequestURL(final Properties conProp) throws MalformedURLException {
        String host =    conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
        final String path =    conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);     // always starts with leading '/'
        final String args =    conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);     // may be null if no args were given
        //String ip =      conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer
        
        int port, pos;        
        if ((pos = host.indexOf(":")) < 0) {
            port = 80;
        } else {
            port = Integer.parseInt(host.substring(pos + 1));
            host = host.substring(0, pos);
        }
        
        final yacyURL url = new yacyURL("http", host, port, (args == null) ? path : path + "?" + args);
        return url;
    }

    public static void handleTransparentProxySupport(final httpRequestHeader header, final Properties prop, final String virtualHost, final boolean isTransparentProxy) {   
        // transparent proxy support is only available for http 1.0 and above connections
        if (prop.getProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9").equals("HTTP/0.9")) return;
        
        // if the transparent proxy support was disabled, we have nothing todo here ...
        if (!(isTransparentProxy && header.containsKey(HOST))) return;
        
        // we only need to do the transparent proxy support if the request URL didn't contain the hostname
        // and therefor was set to virtualHost by function parseQuery()
        if (!prop.getProperty(CONNECTION_PROP_HOST).equals(virtualHost)) return;
        
        // TODO: we could have problems with connections from extern here ...
        final String dstHostSocket = header.get(httpHeader.HOST);
        prop.setProperty(CONNECTION_PROP_HOST,(httpd.isThisHostName(dstHostSocket)?virtualHost:dstHostSocket));
    }
    

    /**
     * Reading http headers from a reader class and building up a httpHeader object
     * @param reader the {@link BufferedReader} that is used to read the http header lines
     * @return a {@link httpHeader}-Object containing all parsed headers
     * @throws IOException
     */
    public void readHttpHeader(final BufferedReader reader) throws IOException {
        // reading all request headers
        int p;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() == 0) break;
            if ((p = line.indexOf(":")) >= 0) {
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
    private Vector<Entry> cookies = new Vector<Entry>();
    
    /**
     * Implementation of Map.Entry. Structure that hold two values - exactly what we need!
     */
    static class Entry implements Map.Entry<String, String> {
        private final String k;
        private String v;
        Entry(final String k, final String v) {
            this.k = k;
            this.v = v;
        }
        public String getKey() {
            return k;
        }
        public String getValue() {
            return v;
        }
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
        cookies.add(new Entry("Set-Cookie", cookieString));
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
        final Iterator<Map.Entry<String, String>> it = this.entrySet().iterator();
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
    public Vector<Entry> getCookieVector(){
        return cookies;
    }
    public void setCookieVector(final Vector<Entry> mycookies){
        cookies=mycookies;
    }
    /**
     * Returns an iterator within all properties can be reached.
     * Is used mainly by httpd.
     *
     * <p>Example:</p>
     * <pre>
     * Iterator it=serverObjects.getRequestProperties();
     * while(it.hasNext())
     * {
     *  java.util.Map.Entry e=(java.util.Map.Entry)it.next();
     *  String propertyName=e.getKey();
     *  String propertyValue=e.getValue();
     * }</pre>
     * @return iterator to read all request properties.
     */
    public Iterator<Entry> getCookies()
    {
        return cookies.iterator();
    }
    /*
     * Patch END:
     * Name: Header Property Patch
     */
}
