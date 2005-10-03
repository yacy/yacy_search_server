// httpdProxyHandler.java 
// -----------------------
// part of YACY
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

// Contributions:
// [AS] Alexander Schier: Blacklist (404 response for AGIS hosts)
// [TL] Timo Leise: url-wildcards for blacklists

/*
   Class documentation:
   This class is a servlet to the httpd daemon. It is accessed each time
   an URL in a GET, HEAD or POST command contains the whole host information
   or a host is given in the header host field of an HTTP/1.0 / HTTP/1.1
   command.
   Transparency is maintained, whenever appropriate. We change header
   atributes if necessary for the indexing mechanism; i.e. we do not
   support gzip-ed encoding. We also do not support unrealistic
   'expires' values that would force a cache to be flushed immediately
   pragma non-cache attributes are supported
*/


package de.anomic.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import de.anomic.htmlFilter.htmlFilterContentTransformer;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.htmlFilter.htmlFilterTransformer;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaURL;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.server.logging.serverMiniLogFormatter;
import de.anomic.yacy.yacyCore;

public final class httpdProxyHandler extends httpdAbstractHandler implements httpdHandler {
    
    // static variables
    // can only be instantiated upon first instantiation of this class object
    private static plasmaSwitchboard switchboard = null;
    private static plasmaHTCache  cacheManager = null;
    public  static HashSet yellowList = null;
    private static int timeout = 30000;
    private static boolean yacyTrigger = true;
    public static boolean isTransparentProxy = false;
    public static boolean remoteProxyUse = false;
    public static String remoteProxyHost = "";
    public static int remoteProxyPort = -1;
    public static String remoteProxyNoProxy = "";
    public static String[] remoteProxyNoProxyPatterns = null;

    private static final HashSet remoteProxyAllowProxySet = new HashSet();
    private static final HashSet remoteProxyDisallowProxySet = new HashSet();    

    private static htmlFilterTransformer transformer = null;
    public static final String userAgent = "yacy (" + httpc.systemOST +") yacy.net";
    private File   htRootPath = null;

    private static boolean doAccessLogging = false; 
	private static HashSet users = null;
    
    /**
     * Do logging configuration for special proxy access log file
     */
    static {
        // Doing logger initialisation
        try {
            serverLog.logInfo("PROXY","Configuring proxy access logging ...");            
            
            // getting the logging manager
            LogManager manager = LogManager.getLogManager();
            String className = httpdProxyHandler.class.getName();
            
            // determining if proxy access logging is enabled
            String enabled = manager.getProperty("de.anomic.http.httpdProxyHandler.logging.enabled");
            if ("true".equalsIgnoreCase(enabled)) {
                
                // reading out some needed configuration properties
                int limit = 1024*1024, count = 20;
                String pattern = manager.getProperty(className + ".logging.FileHandler.pattern");
                if (pattern == null) pattern = "DATA/LOG/proxyAccess%u%g.log";
                
                String limitStr = manager.getProperty(className + ".logging.FileHandler.limit");
                if (limitStr != null) try { limit = Integer.valueOf(limitStr).intValue(); } catch (NumberFormatException e) {}
                
                String countStr = manager.getProperty(className + ".logging.FileHandler.count");
                if (countStr != null) try { count = Integer.valueOf(countStr).intValue(); } catch (NumberFormatException e) {}
                
                // creating the proxy access logger
                Logger proxyLogger = Logger.getLogger("PROXY.access");
                proxyLogger.setUseParentHandlers(false);
                proxyLogger.setLevel(Level.FINEST);
                
                FileHandler txtLog = new FileHandler(pattern,limit,count,true);
                txtLog.setFormatter(new serverMiniLogFormatter());
                txtLog.setLevel(Level.FINEST);
                proxyLogger.addHandler(txtLog);     
                
                doAccessLogging = true; 
                serverLog.logInfo("PROXY","Proxy access logging configuration done." + 
                                  "\n\tFilename: " + pattern + 
                                  "\n\tLimit: " + limitStr + 
                                  "\n\tCount: " + countStr);
            } else {
                serverLog.logInfo("PROXY","Proxy access logging is deactivated.");
            }
        } catch (Exception e) { 
            serverLog.logSevere("PROXY","Unable to configure proxy access logging.",e);        
        }
		users = new HashSet();
    }
    
    /**
     * Special logger instance for proxy access logging much similar
     * to the squid access.log file 
     */
    private final serverLog proxyLog = new serverLog("PROXY.access");
    
    /**
     * Reusable {@link StringBuffer} for logging
     */
    private final StringBuffer logMessage = new StringBuffer();
    
    /**
     * Reusable {@link StringBuffer} to generate the useragent string
     */
    private final StringBuffer userAgentStr = new StringBuffer();
    
    // class methods
    public httpdProxyHandler(serverSwitch sb) {
        
        // creating a logger
        this.theLogger = new serverLog("PROXY");
        
        if (switchboard == null) {
            switchboard = (plasmaSwitchboard) sb;
            cacheManager = switchboard.getCacheManager();
            
            isTransparentProxy = Boolean.valueOf(switchboard.getConfig("isTransparentProxy","false")).booleanValue();
            
            // doing httpc init
            httpc.useYacyReferer = sb.getConfig("useYacyReferer", "true").equals("true");
            
            // load remote proxy data
            remoteProxyHost    = switchboard.getConfig("remoteProxyHost","");
            try {
                remoteProxyPort    = Integer.parseInt(switchboard.getConfig("remoteProxyPort","3128"));
            } catch (NumberFormatException e) {
                remoteProxyPort = 3128;
            }
            remoteProxyUse     = switchboard.getConfig("remoteProxyUse","false").equals("true");
            remoteProxyNoProxy = switchboard.getConfig("remoteProxyNoProxy","");
            remoteProxyNoProxyPatterns = remoteProxyNoProxy.split(",");
            
            // set timeout
            timeout = Integer.parseInt(switchboard.getConfig("clientTimeout", "10000"));
            
            // create a htRootPath: system pages
            if (htRootPath == null) {
                htRootPath = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath","htroot"));
                if (!(htRootPath.exists())) htRootPath.mkdir();
            }
            
            // load a transformer
            transformer = new htmlFilterContentTransformer();
            transformer.init(new File(switchboard.getRootPath(), switchboard.getConfig("plasmaBlueList", "")).toString());
            
            String f;
            // load the yellow-list
            f = switchboard.getConfig("proxyYellowList", null);
            if (f != null) {
                yellowList = serverFileUtils.loadSet(f); 
                this.theLogger.logConfig("loaded yellow-list from file " + f + ", " + yellowList.size() + " entries");
            } else {
                yellowList = new HashSet();
            }
        }
    }
    
    private static String domain(String host) {
        String domain = host;
        int pos = domain.lastIndexOf(".");
        if (pos >= 0) {
            // truncate from last part
            domain = domain.substring(0, pos);
            pos = domain.lastIndexOf(".");
            if (pos >= 0) {
                // truncate from first part
                domain = domain.substring(pos + 1);
            }
        }
        return domain;
    }
    
    public void handleOutgoingCookies(httpHeader requestHeader, String targethost, String clienthost) {
        /*
         The syntax for the header is:
         
         cookie          =       "Cookie:" cookie-version
         1*((";" | ",") cookie-value)
         cookie-value    =       NAME "=" VALUE [";" path] [";" domain]
         cookie-version  =       "$Version" "=" value
         NAME            =       attr
         VALUE           =       value
         path            =       "$Path" "=" value
         domain          =       "$Domain" "=" value
         */
        if (requestHeader.containsKey(httpHeader.COOKIE)) {
            Object[] entry = new Object[]{new Date(), clienthost, requestHeader.getMultiple(httpHeader.COOKIE)};
            switchboard.outgoingCookies.put(targethost, entry);
        }
    }
    
    public void handleIncomingCookies(httpHeader respondHeader, String serverhost, String targetclient) {
        /*
         The syntax for the Set-Cookie response header is 
         
         set-cookie      =       "Set-Cookie:" cookies
         cookies         =       1#cookie
         cookie          =       NAME "=" VALUE *(";" cookie-av)
         NAME            =       attr
         VALUE           =       value
         cookie-av       =       "Comment" "=" value
         |       "Domain" "=" value
         |       "Max-Age" "=" value
         |       "Path" "=" value
         |       "Secure"
         |       "Version" "=" 1*DIGIT
         */
        if (respondHeader.containsKey(httpHeader.SET_COOKIE)) {
            Object[] entry = new Object[]{new Date(), targetclient, respondHeader.getMultiple(httpHeader.SET_COOKIE)};
            switchboard.incomingCookies.put(serverhost, entry);
        }
    }
    
    /**
     * @param conProp a collection of properties about the connection, like URL
     * @param requestHeader The header lines of the connection from the request
     * @param respond the OutputStream to the client
     * @see de.anomic.http.httpdHandler#doGet(java.util.Properties, de.anomic.http.httpHeader, java.io.OutputStream)
     */
    public void doGet(Properties conProp, httpHeader requestHeader, OutputStream respond) throws IOException {
        
        this.connectionProperties = conProp;
        
        try {
            // remembering the starting time of the request
            Date requestDate = new Date(); // remember the time...
            this.connectionProperties.put(httpHeader.CONNECTION_PROP_REQUEST_START,new Long(requestDate.getTime()));
            if (yacyTrigger) de.anomic.yacy.yacyCore.triggerOnlineAction();
            switchboard.proxyLastAccess = System.currentTimeMillis();
            
            // using an ByteCount OutputStream to count the send bytes (needed for the logfile)
            respond = new httpdByteCountOutputStream(respond,conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE).length() + 2);
            
            String host =    conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            String path =    conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);     // always starts with leading '/'
            String args =    conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);     // may be null if no args were given
            String ip =      conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer
            
            int port, pos;        
            if ((pos = host.indexOf(":")) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
            
            String ext;
            if ((pos = path.lastIndexOf('.')) < 0) {
                ext = "";
            } else {
                ext = path.substring(pos + 1).toLowerCase();
            }
            
            URL url = null;
            try {
                url = httpHeader.getRequestURL(conProp);
            } catch (MalformedURLException e) {
                String errorMsg = "ERROR: internal error with url generation: host=" +
                                  host + ", port=" + port + ", path=" + path + ", args=" + args;
                serverLog.logSevere("PROXY", errorMsg);
                httpd.sendRespondError(conProp,respond,4,501,null,errorMsg,e);
                return;
            }
            
            // check the blacklist
            // blacklist idea inspired by [AS]:
            // respond a 404 for all AGIS ("all you get is shit") servers
            String hostlow = host.toLowerCase();
            if (plasmaSwitchboard.urlBlacklist.isListed(hostlow, path)) {
                httpd.sendRespondError(conProp,respond,4,403,null,
                        "URL '" + hostlow + "' blocked by yacy proxy (blacklisted)",null);
                this.theLogger.logInfo("AGIS blocking of host '" + hostlow + "'");
                return;
            }
            
            // handle outgoing cookies
            handleOutgoingCookies(requestHeader, host, ip);
            
            // set another userAgent, if not yellowlisted
            if ((yellowList != null) && (!(yellowList.contains(domain(hostlow))))) {
                // change the User-Agent
                requestHeader.put(httpHeader.USER_AGENT, generateUserAgent(requestHeader));
            }
            
            // setting the X-Forwarded-For Header
            requestHeader.put(httpHeader.X_FORWARDED_FOR,conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP));
            
            // decide wether to use a cache entry or connect to the network
            File cacheFile = cacheManager.getCachePath(url);
            String urlHash = plasmaURL.urlHash(url);
            httpHeader cachedResponseHeader = cacheManager.getCachedResponse(urlHash);
            boolean cacheExists = ((cacheFile.isFile()) && (cachedResponseHeader != null));
            
            // why are files unzipped upon arrival? why not zip all files in cache?
            // This follows from the following premises
            // (a) no file shall be unzip-ed more than once to prevent unnessesary computing time
            // (b) old cache entries shall be comparable with refill-entries to detect/distiguish case 3+4
            // (c) the indexing mechanism needs files unzip-ed, a schedule could do that later
            // case b and c contradicts, if we use a scheduler, because files in a stale cache would be unzipped
            // and the newly arrival would be zipped and would have to be unzipped upon load. But then the
            // scheduler is superfluous. Therefore the only reminding case is
            // (d) cached files shall be either all zipped or unzipped
            // case d contradicts with a, because files need to be unzipped for indexing. Therefore
            // the only remaining case is to unzip files right upon load. Thats what we do here.
            
            // finally use existing cache if appropriate
            // here we must decide weather or not to save the data
            // to a cache
            // we distinguish four CACHE STATE cases:
            // 1. cache fill
            // 2. cache fresh - no refill
            // 3. cache stale - refill - necessary
            // 4. cache stale - refill - superfluous
            // in two of these cases we trigger a scheduler to handle newly arrived files:
            // case 1 and case 3
            plasmaHTCache.Entry cacheEntry = (cachedResponseHeader == null) ? null :
                cacheManager.newEntry(
                    requestDate,                     // init date 
                    0,                               // crawling depth
                    url,                             // url
                    "",                              // name of the url is unknown
                    requestHeader,                   // request headers
                    "200 OK",                        // request status
                    cachedResponseHeader,            // response headers
                    null,                            // initiator
                    switchboard.defaultProxyProfile  // profile
            );
            
            if (cacheExists && cacheEntry.shallUseCacheForProxy()) {
                fulfillRequestFromCache(conProp,url,ext,requestHeader,cachedResponseHeader,cacheFile,respond);
            } else {            
                fulfillRequestFromWeb(conProp,url,ext,requestHeader,cachedResponseHeader,cacheFile,respond);
            }
           
        } catch (Exception e) {
            try {
                String exTxt = e.getMessage();
                if ((exTxt!=null)&&(exTxt.startsWith("Socket closed"))) {
                    this.forceConnectionClose();
                } else if (!conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                    String errorMsg = "Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage(); 
                    httpd.sendRespondError(conProp,respond,4,501,null,errorMsg,e);
                    this.theLogger.logSevere(errorMsg);
                } else {
                    this.forceConnectionClose();                    
                }
            } catch (Exception ee) {
                this.forceConnectionClose();
            }            
        } finally {
            try { respond.flush(); } catch (Exception e) {}
            if (respond instanceof httpdByteCountOutputStream) ((httpdByteCountOutputStream)respond).finish();
            
            this.connectionProperties.put(httpHeader.CONNECTION_PROP_REQUEST_END,new Long(System.currentTimeMillis()));
            this.connectionProperties.put(httpHeader.CONNECTION_PROP_PROXY_RESPOND_SIZE,new Long(((httpdByteCountOutputStream)respond).getCount()));
            this.logProxyAccess();
        }
    }
    
    private void fulfillRequestFromWeb(Properties conProp, URL url,String ext, httpHeader requestHeader, httpHeader cachedResponseHeader, File cacheFile, OutputStream respond) {
        
        GZIPOutputStream gzippedOut = null; 
        httpChunkedOutputStream chunkedOut = null;
        OutputStream hfos = null;
        
        httpc remote = null;
        httpc.response res = null;                
        try {

            String host =    conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            String path =    conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);     // always starts with leading '/'
            String args =    conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);     // may be null if no args were given
            String ip =      conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer
            String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER); // the ip from the connecting peer            
            
            int port, pos;        
            if ((pos = host.indexOf(":")) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }            
            
            // resolve yacy and yacyh domains
            String yAddress = yacyCore.seedDB.resolveYacyAddress(host);
            
            // re-calc the url path
            String remotePath = (args == null) ? path : (path + "?" + args); // with leading '/'
            
            // attach possible yacy-sublevel-domain
            if ((yAddress != null) &&
                    ((pos = yAddress.indexOf("/")) >= 0) &&
                    (!(remotePath.startsWith("/env"))) // this is the special path, staying always at root-level
            ) remotePath = yAddress.substring(pos) + remotePath;            
            
            // open the connection
            remote = (yAddress == null) ? newhttpc(host, port, timeout) : newhttpc(yAddress, timeout);
            
            // removing hop by hop headers
            this.removeHopByHopHeaders(requestHeader);
            
            // send request
            res = remote.GET(remotePath, requestHeader);
            conProp.put(httpHeader.CONNECTION_PROP_CLIENT_REQUEST_HEADER,requestHeader);
            
            // determine if it's an internal error of the httpc
            if (res.responseHeader.size() == 0) {
                throw new Exception(res.statusText);
            }
            
            // if the content length is not set we have to use chunked transfer encoding
            long contentLength = res.responseHeader.contentLength();
            if (contentLength < 0) {
                // according to http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
                // a 204,304 message must not contain a message body.
                // Therefore we need to set the content-length to 0.
                if (res.status.startsWith("204") || 
                    res.status.startsWith("304")) {
                    res.responseHeader.put(httpHeader.CONTENT_LENGTH,"0");
                } else {
                    if (httpVer.equals("HTTP/0.9") || httpVer.equals("HTTP/1.0")) {
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
                    } else {
                        chunkedOut = new httpChunkedOutputStream(respond);
                    }
                    res.responseHeader.remove(httpHeader.CONTENT_LENGTH);
                }
            }            
            
//          if (((String)requestHeader.get(httpHeader.ACCEPT_ENCODING,"")).indexOf("gzip") != -1) {
//          zipped = new GZIPOutputStream((chunked != null) ? chunked : respond);
//          res.responseHeader.put(httpHeader.CONTENT_ENCODING, "gzip");
//          res.responseHeader.remove(httpHeader.CONTENT_LENGTH);
//          }

            // the cache does either not exist or is (supposed to be) stale
            long sizeBeforeDelete = -1;
            if ((cacheFile.isFile()) && (cachedResponseHeader != null)) {
                // delete the cache
                sizeBeforeDelete = cacheFile.length();
                cacheManager.deleteFile(url);
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_MISS");
            }            

            // reserver cache entry
            Date requestDate = new Date(((Long)conProp.get(httpHeader.CONNECTION_PROP_REQUEST_START)).longValue()); 
            plasmaHTCache.Entry cacheEntry = cacheManager.newEntry(
                    requestDate, 
                    0, 
                    url,
                    "",
                    requestHeader, 
                    res.status, 
                    res.responseHeader, 
                    null, 
                    switchboard.defaultProxyProfile
            );

            // handle file types and make (possibly transforming) output stream
            if (
                    (!transformer.isIdentityTransformer()) &&
                    (plasmaParser.supportedFileExt(url)) &&
                    (plasmaParser.realtimeParsableMimeTypesContains(res.responseHeader.mime()))
                ) {
                // make a transformer
                this.theLogger.logFine("create transformer for URL " + url);
                hfos = new htmlFilterOutputStream((gzippedOut != null) ? gzippedOut : ((chunkedOut != null)? chunkedOut : respond), null, transformer, (ext.length() == 0));
            } else {
                // simply pass through without parsing
                this.theLogger.logFine("create passthrough for URL " + url + ", extension '" + ext + "', mime-type '" + res.responseHeader.mime() + "'");
                hfos = (gzippedOut != null) ? gzippedOut : ((chunkedOut != null)? chunkedOut : respond);
            }
            
            // handle incoming cookies
            handleIncomingCookies(res.responseHeader, host, ip);
            
            // remove hop by hop headers
            this.removeHopByHopHeaders(res.responseHeader);
            
            // sending the respond header back to the client
            if (chunkedOut != null) {
                res.responseHeader.put(httpHeader.TRANSFER_ENCODING, "chunked");
            }
            
            httpd.sendRespondHeader(
                    conProp,
                    respond,
                    httpVer,
                    res.statusCode,
                    res.statusText, 
                    res.responseHeader);
            
            String storeError;
            if ((storeError = cacheEntry.shallStoreCacheForProxy()) == null) {
                // we write a new cache entry
                if ((contentLength > 0) && (contentLength < 1048576)) // if the length is known and < 1 MB
                {
                    // ok, we don't write actually into a file, only to RAM, and schedule writing the file.
                    byte[] cacheArray = res.writeContent(hfos);
                    this.theLogger.logFine("writeContent of " + url + " produced cacheArray = " + ((cacheArray == null) ? "null" : ("size=" + cacheArray.length)));
                    
                    if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
                    
                    if (sizeBeforeDelete == -1) {
                        // totally fresh file
                        //cacheEntry.status = plasmaHTCache.CACHE_FILL; // it's an insert
                        cacheEntry.cacheArray = cacheArray;
                        cacheManager.push(cacheEntry);
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_MISS");
                    } else if (sizeBeforeDelete == cacheArray.length) {
                        // before we came here we deleted a cache entry
                        cacheArray = null;
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
                        //cacheManager.push(cacheEntry); // unnecessary update
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REF_FAIL_HIT");                                
                    } else {
                        // before we came here we deleted a cache entry
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
                        cacheEntry.cacheArray = cacheArray;
                        cacheManager.push(cacheEntry); // necessary update, write response header to cache
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_MISS");
                    }          
                } else {
                    // the file is too big to cache it in the ram, or the size is unknown
                    // write to file right here.
                    cacheFile.getParentFile().mkdirs();
                    res.writeContent(hfos, cacheFile);
                    if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
                    this.theLogger.logFine("for write-file of " + url + ": contentLength = " + contentLength + ", sizeBeforeDelete = " + sizeBeforeDelete);
                    cacheManager.writeFileAnnouncement(cacheFile);
                    if (sizeBeforeDelete == -1) {
                        // totally fresh file
                        //cacheEntry.status = plasmaHTCache.CACHE_FILL; // it's an insert
                        cacheManager.push(cacheEntry);
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_MISS");
                    } else if (sizeBeforeDelete == cacheFile.length()) {
                        // before we came here we deleted a cache entry
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
                        //cacheManager.push(cacheEntry); // unnecessary update
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REF_FAIL_HIT");
                    } else {
                        // before we came here we deleted a cache entry
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
                        cacheManager.push(cacheEntry); // necessary update, write response header to cache
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_MISS");
                    }
                    // beware! all these writings will not fill the cacheEntry.cacheArray
                    // that means they are not available for the indexer (except they are scraped before)
                }
            } else {
                // no caching
                this.theLogger.logFine(cacheFile.toString() + " not cached: " + storeError);
                res.writeContent(hfos, null);
                if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
                if (sizeBeforeDelete == -1) {
                    // no old file and no load. just data passing
                    //cacheEntry.status = plasmaHTCache.CACHE_PASSING;
                    //cacheManager.push(cacheEntry);
                } else {
                    // before we came here we deleted a cache entry
                    //cacheEntry.status = plasmaHTCache.CACHE_STALE_NO_RELOAD;
                    //cacheManager.push(cacheEntry);
                }
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_MISS");
            }
            
            if (gzippedOut != null) {
                gzippedOut.finish();
            }
            if (chunkedOut != null) {
                chunkedOut.finish();
                chunkedOut.flush();
            }
        } catch (Exception e) {
            // deleting cached content
            if (cacheFile.exists()) cacheFile.delete();                            
            handleProxyException(e,remote,conProp,respond,url);
        } finally {
            if (remote != null) httpc.returnInstance(remote);
        } 
    }


    private void fulfillRequestFromCache(
            Properties conProp, 
            URL url,
            String ext,
            httpHeader requestHeader, 
            httpHeader cachedResponseHeader,
            File cacheFile,
            OutputStream respond
    ) throws IOException {
        
        String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
        
        httpChunkedOutputStream chunkedOut = null;
        GZIPOutputStream gzippedOut = null;
        OutputStream hfos = null;
        
        // we respond on the request by using the cache, the cache is fresh        
        try {
            // remove hop by hop headers
            this.removeHopByHopHeaders(cachedResponseHeader);
            
            // replace date field in old header by actual date, this is according to RFC
            cachedResponseHeader.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));
            
//          if (((String)requestHeader.get(httpHeader.ACCEPT_ENCODING,"")).indexOf("gzip") != -1) {
//          chunked = new httpChunkedOutputStream(respond);
//          zipped = new GZIPOutputStream(chunked);
//          cachedResponseHeader.put(httpHeader.TRANSFER_ENCODING, "chunked");
//          cachedResponseHeader.put(httpHeader.CONTENT_ENCODING, "gzip");                    
//          } else {                
            // maybe the content length is missing
//            if (!(cachedResponseHeader.containsKey(httpHeader.CONTENT_LENGTH)))
//                cachedResponseHeader.put(httpHeader.CONTENT_LENGTH, Long.toString(cacheFile.length()));
//          }
            
            // check if we can send a 304 instead the complete content
            if (requestHeader.containsKey(httpHeader.IF_MODIFIED_SINCE)) {
                // conditional request: freshness of cache for that condition was already
                // checked within shallUseCache(). Now send only a 304 response
                this.theLogger.logInfo("CACHE HIT/304 " + cacheFile.toString());
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_HIT");
                
                // setting the content length header to 0
                cachedResponseHeader.put(httpHeader.CONTENT_LENGTH, Integer.toString(0));
                
                // send cached header with replaced date and added length
                httpd.sendRespondHeader(conProp,respond,httpVer,304,cachedResponseHeader);
                //respondHeader(respond, "304 OK", cachedResponseHeader); // respond with 'not modified'
            } else {
                // unconditional request: send content of cache
                this.theLogger.logInfo("CACHE HIT/203 " + cacheFile.toString());
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_HIT");
                
                // setting the content header to the proper length
                cachedResponseHeader.put(httpHeader.CONTENT_LENGTH, Long.toString(cacheFile.length()));
                
                // send cached header with replaced date and added length 
                httpd.sendRespondHeader(conProp,respond,httpVer,203,cachedResponseHeader);
                //respondHeader(respond, "203 OK", cachedResponseHeader); // respond with 'non-authoritative'
                
                // make a transformer
                if ((!(transformer.isIdentityTransformer())) &&
                        ((ext == null) || (!(plasmaParser.mediaExtContains(ext)))) &&
                        ((cachedResponseHeader == null) || (plasmaParser.realtimeParsableMimeTypesContains(cachedResponseHeader.mime())))) {
                    hfos = new htmlFilterOutputStream((chunkedOut != null) ? chunkedOut : respond, null, transformer, (ext.length() == 0));
                } else {
                    hfos = (gzippedOut != null) ? gzippedOut : ((chunkedOut != null)? chunkedOut : respond);
                }
                
                // send also the complete body now from the cache
                // simply read the file and transfer to out socket
                serverFileUtils.copy(cacheFile,hfos);
                
                if (hfos instanceof htmlFilterOutputStream) ((htmlFilterOutputStream) hfos).finalize();
                if (gzippedOut != null) gzippedOut.finish();
                if (chunkedOut != null) chunkedOut.finish();
            }
            // that's it!
        } catch (Exception e) {
            // this happens if the client stops loading the file
            // we do nothing here                            
            if (conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                this.theLogger.logWarning("Error while trying to send cached message body.");
                conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
            } else {
                httpd.sendRespondError(conProp,respond,4,503,"socket error: " + e.getMessage(),"socket error: " + e.getMessage(), e);
            }
        } finally {
            try { respond.flush(); } catch (Exception e) {}
        }
        return;
    }


    private void removeHopByHopHeaders(httpHeader headers) {
        /*
         *       - Connection
         - Keep-Alive
         - Proxy-Authenticate
         - Proxy-Authorization
         - TE
         - Trailers
         - Transfer-Encoding
         - Upgrade 
         */
        
        headers.remove(httpHeader.CONNECTION);
        headers.remove(httpHeader.PROXY_CONNECTION);
        headers.remove(httpHeader.PROXY_AUTHENTICATE);
        headers.remove(httpHeader.PROXY_AUTHORIZATION);
        
        // special headers inserted by squid
        headers.remove(httpHeader.X_CACHE);
        headers.remove(httpHeader.X_CACHE_LOOKUP);     
        
        // remove transfer encoding header
        headers.remove(httpHeader.TRANSFER_ENCODING);
        
        //removing yacy status headers
        headers.remove(httpHeader.X_YACY_KEEP_ALIVE_REQUEST_COUNT);
        headers.remove(httpHeader.X_YACY_ORIGINAL_REQUEST_LINE);
    }
        
    private void forceConnectionClose() {
        if (this.connectionProperties != null) {
            this.connectionProperties.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");            
        }
    }
    
    public void doHead(Properties conProp, httpHeader requestHeader, OutputStream respond) throws IOException {
        this.connectionProperties = conProp;        
        
        httpc remote = null;
        httpc.response res = null;
        URL url = null;
        try {
            // remembering the starting time of the request
            Date requestDate = new Date(); // remember the time...
            this.connectionProperties.put(httpHeader.CONNECTION_PROP_REQUEST_START,new Long(requestDate.getTime()));
            if (yacyTrigger) de.anomic.yacy.yacyCore.triggerOnlineAction();
            switchboard.proxyLastAccess = System.currentTimeMillis();            
            
            // using an ByteCount OutputStream to count the send bytes
            respond = new httpdByteCountOutputStream(respond,conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE).length() + 2);                                   
            
            String host = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
            String args = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS); 
            String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
            
            switchboard.proxyLastAccess = System.currentTimeMillis();
            
            int port, pos;
            if ((pos = host.indexOf(":")) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
            
            try {
                url = new URL("http", host, port, (args == null) ? path : path + "?" + args);
            } catch (MalformedURLException e) {
                String errorMsg = "ERROR: internal error with url generation: host=" +
                                  host + ", port=" + port + ", path=" + path + ", args=" + args;
                serverLog.logSevere("PROXY", errorMsg);
                httpd.sendRespondError(conProp,respond,4,501,null,errorMsg,e);
                return;
            } 
            
            // check the blacklist, inspired by [AS]: respond a 404 for all AGIS (all you get is shit) servers
            String hostlow = host.toLowerCase();
            if (plasmaSwitchboard.urlBlacklist.isListed(hostlow, path)) {
                httpd.sendRespondError(conProp,respond,4,403,null,
                        "URL '" + hostlow + "' blocked by yacy proxy (blacklisted)",null);
                this.theLogger.logInfo("AGIS blocking of host '" + hostlow + "'");
                return;
            }                   
            
            // set another userAgent, if not yellowlisted
            if (!(yellowList.contains(domain(hostlow)))) {
                // change the User-Agent
                requestHeader.put(httpHeader.USER_AGENT, generateUserAgent(requestHeader));
            }
            
            // setting the X-Forwarded-For Header
            requestHeader.put(httpHeader.X_FORWARDED_FOR,conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP));        
            
            // resolve yacy and yacyh domains
            String yAddress = yacyCore.seedDB.resolveYacyAddress(host);
            
            // re-calc the url path
            String remotePath = (args == null) ? path : (path + "?" + args);
            
            // attach possible yacy-sublevel-domain
            if ((yAddress != null) && ((pos = yAddress.indexOf("/")) >= 0)) remotePath = yAddress.substring(pos) + remotePath;
            
            // removing hop by hop headers
            this.removeHopByHopHeaders(requestHeader);            
            
            // open the connection: second is needed for [AS] patch
            remote = (yAddress == null) ? newhttpc(host, port, timeout): newhttpc(yAddress, timeout);
            
            // sending the http-HEAD request to the server
            res = remote.HEAD(remotePath, requestHeader);
            
            // determine if it's an internal error of the httpc
            if (res.responseHeader.size() == 0) {
                throw new Exception(res.statusText);
            }            
            
            // removing hop by hop headers
            this.removeHopByHopHeaders(res.responseHeader);
            
            // sending the server respond back to the client
            httpd.sendRespondHeader(conProp,respond,httpVer,res.statusCode,res.statusText,res.responseHeader);
        } catch (Exception e) {
            handleProxyException(e,remote,conProp,respond,url); 
        } finally {
            if (remote != null) httpc.returnInstance(remote);
        }
        
        respond.flush();
    }
    
    public void doPost(Properties conProp, httpHeader requestHeader, OutputStream respond, PushbackInputStream body) throws IOException {
        
        this.connectionProperties = conProp;
        
        httpc remote = null;
        URL url = null;
        try {
            // remembering the starting time of the request
            Date requestDate = new Date(); // remember the time...
            this.connectionProperties.put(httpHeader.CONNECTION_PROP_REQUEST_START,new Long(requestDate.getTime()));
            if (yacyTrigger) de.anomic.yacy.yacyCore.triggerOnlineAction();
            switchboard.proxyLastAccess = System.currentTimeMillis();
            
            // using an ByteCount OutputStream to count the send bytes
            respond = new httpdByteCountOutputStream(respond,conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE).length() + 2);
                        
            String host    = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            String path    = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
            String args    = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS); // may be null if no args were given
            String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
            
            int port, pos;
            if ((pos = host.indexOf(":")) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
            
            try {
                url = new URL("http", host, port, (args == null) ? path : path + "?" + args);
            } catch (MalformedURLException e) {
                String errorMsg = "ERROR: internal error with url generation: host=" +
                                  host + ", port=" + port + ", path=" + path + ", args=" + args;
                serverLog.logSevere("PROXY", errorMsg);
                httpd.sendRespondError(conProp,respond,4,501,null,errorMsg,e);
                return;
            }                             
            
            // set another userAgent, if not yellowlisted
            if (!(yellowList.contains(domain(host).toLowerCase()))) {
                // change the User-Agent
                requestHeader.put(httpHeader.USER_AGENT, generateUserAgent(requestHeader));
            }
            
            // setting the X-Forwarded-For Header
            requestHeader.put(httpHeader.X_FORWARDED_FOR,conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP));            
            
            // resolve yacy and yacyh domains
            String yAddress = yacyCore.seedDB.resolveYacyAddress(host);
            
            // re-calc the url path
            String remotePath = (args == null) ? path : (path + "?" + args);
            
            // attach possible yacy-sublevel-domain
            if ((yAddress != null) && ((pos = yAddress.indexOf("/")) >= 0)) remotePath = yAddress.substring(pos) + remotePath;
                  
            // removing hop by hop headers
            this.removeHopByHopHeaders(requestHeader);
            
            // sending the request
            remote = (yAddress == null) ? newhttpc(host, port, timeout) : newhttpc(yAddress, timeout);                
            httpc.response res = remote.POST(remotePath, requestHeader, body);
            
            // determine if it's an internal error of the httpc
            if (res.responseHeader.size() == 0) {
                throw new Exception(res.statusText);
            }                                  
            
            // if the content length is not set we need to use chunked content encoding
            long contentLength = res.responseHeader.contentLength();
            httpChunkedOutputStream chunked = null;
            if (contentLength <= 0) {
                // according to http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
                // a 204,304 message must not contain a message body.
                // Therefore we need to set the content-length to 0.
                if (res.status.startsWith("204") || 
                    res.status.startsWith("304")) {
                    res.responseHeader.put(httpHeader.CONTENT_LENGTH,"0");
                } else {                
                    if (httpVer.equals("HTTP/0.9") || httpVer.equals("HTTP/1.0")) {
                        forceConnectionClose();
                    } else {
                        chunked = new httpChunkedOutputStream(respond);
                    }
                    res.responseHeader.remove(httpHeader.CONTENT_LENGTH);                
                }
            }
            
            // remove hop by hop headers
            this.removeHopByHopHeaders(res.responseHeader);
            
            // sending the respond header back to the client
            if (chunked != null) {
                res.responseHeader.put(httpHeader.TRANSFER_ENCODING, "chunked");
            }            
            
            // sending response headers
            httpd.sendRespondHeader(conProp,
                                    respond,
                                    httpVer,
                                    res.statusCode,
                                    res.statusText,
                                    res.responseHeader);
            
            // respondHeader(respond, res.status, res.responseHeader);
            res.writeContent((chunked != null) ? chunked : respond, null);
            if (chunked != null)  chunked.finish();
            
            remote.close();
            respond.flush();
        } catch (Exception e) {
            handleProxyException(e,remote,conProp,respond,url);                 
        } finally {
            if (remote != null) httpc.returnInstance(remote);
            
            respond.flush();
            if (respond instanceof httpdByteCountOutputStream) ((httpdByteCountOutputStream)respond).finish();           
            
            this.connectionProperties.put(httpHeader.CONNECTION_PROP_REQUEST_END,new Long(System.currentTimeMillis()));
            this.connectionProperties.put(httpHeader.CONNECTION_PROP_PROXY_RESPOND_SIZE,new Long(((httpdByteCountOutputStream)respond).getCount()));
            this.logProxyAccess();
        }
    }
    
    public void doConnect(Properties conProp, de.anomic.http.httpHeader requestHeader, InputStream clientIn, OutputStream clientOut) throws IOException {
        this.connectionProperties = conProp;
        switchboard.proxyLastAccess = System.currentTimeMillis();
        
        String host = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
        String httpVersion = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
        int timeout = Integer.parseInt(switchboard.getConfig("clientTimeout", "10000"));
        
        int port, pos;
        if ((pos = host.indexOf(":")) < 0) {
            port = 80;
        } else {
            port = Integer.parseInt(host.substring(pos + 1));
            host = host.substring(0, pos);
        }                
        
        // check the blacklist
        // blacklist idea inspired by [AS]:
        // respond a 404 for all AGIS ("all you get is shit") servers
        String hostlow = host.toLowerCase();
        if (plasmaSwitchboard.urlBlacklist.isListed(hostlow, "/")) {
            httpd.sendRespondError(conProp,clientOut,4,403,null,
                    "URL '" + hostlow + "' blocked by yacy proxy (blacklisted)",null);
            this.theLogger.logInfo("AGIS blocking of host '" + hostlow + "'");
            forceConnectionClose();
            return;
        }
        
        // possibly branch into PROXY-PROXY connection
        if (remoteProxyUse) {
            httpc remoteProxy = null;
            try {
                remoteProxy = httpc.getInstance(host, port, timeout, false, remoteProxyHost, remoteProxyPort);
                httpc.response response = remoteProxy.CONNECT(host, port, requestHeader);
                response.print();
                if (response.success()) {
                    // replace connection details
                    host = remoteProxyHost;
                    port = remoteProxyPort;
                    // go on (see below)
                } else {
                    // pass error response back to client
                    httpd.sendRespondHeader(conProp,clientOut,httpVersion,response.statusCode,response.statusText,response.responseHeader);
                    //respondHeader(clientOut, response.status, response.responseHeader);
                    forceConnectionClose();
                    return;
                }
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            } finally {
                if (remoteProxy != null) httpc.returnInstance(remoteProxy);
            }
        } 
        
        // try to establish connection to remote host
        Socket sslSocket = new Socket(host, port);
        sslSocket.setSoTimeout(timeout); // waiting time for write
        sslSocket.setSoLinger(true, timeout); // waiting time for read
        InputStream promiscuousIn  = sslSocket.getInputStream();
        OutputStream promiscuousOut = sslSocket.getOutputStream();
        
        // now then we can return a success message
        clientOut.write((httpVersion + " 200 Connection established" + serverCore.crlfString +
                "Proxy-agent: YACY" + serverCore.crlfString +
                serverCore.crlfString).getBytes());
        
        this.theLogger.logInfo("SSL CONNECTION TO " + host + ":" + port + " ESTABLISHED");
        
        // start stream passing with mediate processes
        try {
            Mediate cs = new Mediate(sslSocket, clientIn, promiscuousOut);
            Mediate sc = new Mediate(sslSocket, promiscuousIn, clientOut);
            cs.start();
            sc.start();
            while ((sslSocket != null) &&
                    (sslSocket.isBound()) &&
                    (!(sslSocket.isClosed())) &&
                    (sslSocket.isConnected()) &&
                    ((cs.isAlive()) || (sc.isAlive()))) {
                // idle
                try {Thread.currentThread().sleep(1000);} catch (InterruptedException e) {} // wait a while
            }
            // set stop mode
            cs.pleaseTerminate();
            sc.pleaseTerminate();
            // wake up thread
            cs.interrupt();
            sc.interrupt();
            // ...hope they have terminated...
        } catch (IOException e) {
            //System.out.println("promiscuous termination: " + e.getMessage());
        }
        
    }
    
    public class Mediate extends Thread {
        
        boolean terminate;
        Socket socket;
        InputStream in;
        OutputStream out;
        
        public Mediate(Socket socket, InputStream in, OutputStream out) throws IOException {
            this.terminate = false;
            this.in = in;
            this.out = out;
            this.socket = socket;
        }
        
        public void run() {
            byte[] buffer = new byte[512];
            int len;
            try {
                while ((socket != null) &&
                        (socket.isBound()) &&
                        (!(socket.isClosed())) &&
                        (socket.isConnected()) &&
                        (!(terminate)) &&
                        (in != null) &&
                        (out != null) &&
                        ((len = in.read(buffer)) >= 0)
                ) {
                    out.write(buffer, 0, len); 
                }
            } catch (IOException e) {}
        }
        
        public void pleaseTerminate() {
            terminate = true;
        }
    }
    
    private httpc newhttpc(String server, int port, int timeout) throws IOException {
        // a new httpc connection, combined with possible remote proxy
        boolean useProxy = remoteProxyUse;
        // check no-proxy rule
        if ((useProxy) && (!(remoteProxyAllowProxySet.contains(server)))) {
            if (remoteProxyDisallowProxySet.contains(server)) {
                useProxy = false;
            } else {
                // analyse remoteProxyNoProxy;
                // set either remoteProxyAllowProxySet or remoteProxyDisallowProxySet accordingly
                int i = 0;
                while (i < remoteProxyNoProxyPatterns.length) {
                    if (server.matches(remoteProxyNoProxyPatterns[i])) {
                        // disallow proxy for this server
                        remoteProxyDisallowProxySet.add(server);
                        useProxy = false;
                        break;
                    }
                    i++;
                }
                if (i == remoteProxyNoProxyPatterns.length) {
                    // no pattern matches: allow server
                    remoteProxyAllowProxySet.add(server);
                }
            }
        }
        // branch to server/proxy
        if (useProxy) {
            return httpc.getInstance(server, port, timeout, false, remoteProxyHost, remoteProxyPort);
        } else {
            return httpc.getInstance(server, port, timeout, false);
        }
    }
    
    private httpc newhttpc(String address, int timeout) throws IOException {
        // a new httpc connection for <host>:<port>/<path> syntax
        // this is called when a '.yacy'-domain is used
        int p = address.indexOf(":");
        if (p < 0) return null;
        String server = address.substring(0, p);
        address = address.substring(p + 1);
        // remove possible path elements (may occur for 'virtual' subdomains
        p = address.indexOf("/");
        if (p >= 0) address = address.substring(0, p); // cut it off
        int port = Integer.parseInt(address);
        // normal creation of httpc object
        return newhttpc(server, port, timeout);
    }
    
    private void textMessage(OutputStream out, String body) throws IOException {
        out.write(("HTTP/1.1 200 OK\r\n").getBytes());
        out.write((httpHeader.SERVER + ": AnomicHTTPD (www.anomic.de)\r\n").getBytes());
        out.write((httpHeader.DATE + ": " + httpc.dateString(httpc.nowDate()) + "\r\n").getBytes());
        out.write((httpHeader.CONTENT_TYPE + ": text/plain\r\n").getBytes());
        out.write((httpHeader.CONTENT_LENGTH + ": " + body.length() +"\r\n").getBytes());
        out.write(("\r\n").getBytes());
        out.flush();
        out.write(body.getBytes());
        out.flush();
    }
    
    private void handleProxyException(Exception e, httpc remote, Properties conProp, OutputStream respond, URL url) {
        // this may happen if 
        // - the targeted host does not exist 
        // - anything with the remote server was wrong.
        // - the client unexpectedly closed the connection ...
        try {
            

            // doing some errorhandling ...
            int httpStatusCode = 404; 
            String httpStatusText = null; 
            String errorMessage = null; 
            Exception errorExc = e;
            boolean unknownError = false;
            
            if (e instanceof ConnectException) {
                httpStatusCode = 403; httpStatusText = "Connection refused"; 
                errorMessage = "Connection refused by destination host";
            } else if (e instanceof BindException) {
                errorMessage = "Unable to establish a connection to the destination host";               
            } else if (e instanceof NoRouteToHostException) {
                errorMessage = "No route to destination host";                    
            } else if (e instanceof UnknownHostException) {
                errorMessage = "IP address of the destination host could not be determined";                    
            } else {
                String exceptionMsg = e.getMessage();
                if ((exceptionMsg != null) && (exceptionMsg.indexOf("Corrupt GZIP trailer") >= 0)) {
                    // just do nothing, we leave it this way
                    this.theLogger.logFine("ignoring bad gzip trail for URL " + url + " (" + e.getMessage() + ")");
                    this.forceConnectionClose();
                } else if ((exceptionMsg != null) && (exceptionMsg.indexOf("Connection reset")>= 0)) {
                    errorMessage = "Connection reset";
                } else if ((exceptionMsg != null) && 
                  (
                     (exceptionMsg.indexOf("unknown host")>=0) ||
                     (exceptionMsg.indexOf("socket write error")>=0) ||
                     (exceptionMsg.indexOf("Read timed out") >= 0)
                  )) {
                    errorMessage = exceptionMsg;
                } else if ((remote != null)&&(remote.isClosed())) { 
                    // TODO: query for broken pipe
                    errorMessage = "Destination host unexpectedly closed connection";                 
                } else {
                    errorMessage = "Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage();
                    unknownError = true;
                }
            }
            
            // sending back an error message to the client
            if (!conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                httpd.sendRespondError(conProp,respond,4,httpStatusCode,httpStatusText,errorMessage,errorExc);
            } else {
                if (unknownError) {
                    this.theLogger.logFine("Error while processing request '" + 
                            conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE,"unknown") + "':" +
                            "\n" + Thread.currentThread().getName() + 
                            "\n" + errorMessage,e);
                } else {
                    this.theLogger.logFine("Error while processing request '" + 
                            conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE,"unknown") + "':" +
                            "\n" + Thread.currentThread().getName() + 
                            "\n" + errorMessage);                        
                }
                this.forceConnectionClose();
            }                
        } catch (Exception ee) {
            this.forceConnectionClose();
        }
        
    }
    
    private String generateUserAgent(httpHeader requestHeaders) {
        this.userAgentStr.setLength(0);
        
        String browserUserAgent = (String) requestHeaders.get(httpHeader.USER_AGENT, userAgent);
        int pos = browserUserAgent.lastIndexOf(')');
        if (pos >= 0) {
            this.userAgentStr
            .append(browserUserAgent.substring(0,pos))
            .append("; YaCy ")
            .append(switchboard.getConfig("vString","0.1"))
            .append("; yacy.net")
            .append(browserUserAgent.substring(pos));
        } else {
            this.userAgentStr.append(browserUserAgent);
        }
        
        return this.userAgentStr.toString();
    }
    
    /**
     * This function is used to generate a logging message according to the 
     * <a href="http://www.squid-cache.org/Doc/FAQ/FAQ-6.html">squid logging format</a>.<p>
     * e.g.<br>
     * <code>1117528623.857    178 192.168.1.201 TCP_MISS/200 1069 GET http://www.yacy.de/ - DIRECT/81.169.145.74 text/html</code>
     */
    private final void logProxyAccess() {
        
        if (!doAccessLogging) return;
        
        this.logMessage.setLength(0);
        
        // Timestamp
        String currentTimestamp = Long.toString(System.currentTimeMillis());
        int offset = currentTimestamp.length()-3;
        
        this.logMessage.append(currentTimestamp.substring(0,offset));
        this.logMessage.append('.');
        this.logMessage.append(currentTimestamp.substring(offset));          
        this.logMessage.append(' ');        
        
        // Elapsed time
        Long requestStart = (Long) this.connectionProperties.get(httpHeader.CONNECTION_PROP_REQUEST_START);
        Long requestEnd =   (Long) this.connectionProperties.get(httpHeader.CONNECTION_PROP_REQUEST_END);
        String elapsed = Long.toString(requestEnd.longValue()-requestStart.longValue());
        
        for (int i=0; i<6-elapsed.length(); i++) this.logMessage.append(' ');
        this.logMessage.append(elapsed);
        this.logMessage.append(' ');
        
        // Remote Host
        String clientIP = this.connectionProperties.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP);
        this.logMessage.append(clientIP);
        this.logMessage.append(' ');
        
        // Code/Status
        String respondStatus = this.connectionProperties.getProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_STATUS);
        String respondCode = this.connectionProperties.getProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"UNKNOWN");        
        this.logMessage.append(respondCode);
        this.logMessage.append("/");
        this.logMessage.append(respondStatus);
        this.logMessage.append(' ');
        
        // Bytes
        Long bytes = (Long) this.connectionProperties.get(httpHeader.CONNECTION_PROP_PROXY_RESPOND_SIZE);
        this.logMessage.append(bytes.toString());
        this.logMessage.append(' ');        
        
        // Method
        String requestMethod = this.connectionProperties.getProperty(httpHeader.CONNECTION_PROP_METHOD); 
        this.logMessage.append(requestMethod);
        this.logMessage.append(' ');  
        
        // URL
        String requestURL = this.connectionProperties.getProperty(httpHeader.CONNECTION_PROP_URL);
        String requestArgs = this.connectionProperties.getProperty(httpHeader.CONNECTION_PROP_ARGS);
        this.logMessage.append(requestURL);
        if (requestArgs != null) {
            this.logMessage.append("?")
                           .append(requestArgs);
        }
        this.logMessage.append(' ');          
        
        // Rfc931
        this.logMessage.append("-");
        this.logMessage.append(' ');
        
        //  Peerstatus/Peerhost
        String host = this.connectionProperties.getProperty(httpHeader.CONNECTION_PROP_HOST);
        this.logMessage.append("DIRECT/");
        this.logMessage.append(host);    
        this.logMessage.append(' ');
        
        // Type
        String mime = "-";
        if (this.connectionProperties.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
            httpHeader proxyRespondHeader = (httpHeader) this.connectionProperties.get(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER);
            mime = proxyRespondHeader.mime();
            if (mime.indexOf(";") != -1) {
                mime = mime.substring(0,mime.indexOf(";"));
            }
        }
        this.logMessage.append(mime);        
        
        // sending the logging message to the logger
        this.proxyLog.logFine(this.logMessage.toString());
    }
    
}

/*
 proxy test:
 
 http://www.chipchapin.com/WebTools/cookietest.php?
 http://xlists.aza.org/moderator/cookietest/cookietest1.php
 http://vancouver-webpages.com/proxy/cache-test.html
 
 */
