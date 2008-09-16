// httpdProxyHandler.java
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2004 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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
   attributes if necessary for the indexing mechanism; i.e. we do not
   support gzip-ed encoding. We also do not support unrealistic
   'expires' values that would force a cache to be flushed immediately
   pragma non-cache attributes are supported
*/


package de.anomic.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import de.anomic.crawler.HTTPLoader;
import de.anomic.htmlFilter.htmlFilterContentTransformer;
import de.anomic.htmlFilter.htmlFilterTransformer;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.index.indexDocumentMetadata;
import de.anomic.index.indexReferenceBlacklist;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.logging.serverLog;
import de.anomic.server.logging.serverMiniLogFormatter;
import de.anomic.yacy.yacyURL;

public final class httpdProxyHandler {
    
    // static variables
    // can only be instantiated upon first instantiation of this class object
    private static plasmaSwitchboard switchboard = null;
    private static final HashSet<String> yellowList;
    private static int timeout = 30000;
    private static boolean yacyTrigger = true;
    public static boolean isTransparentProxy = false;
    private static Process redirectorProcess = null;
    private static boolean redirectorEnabled = false;
    private static PrintWriter redirectorWriter = null;
    private static BufferedReader redirectorReader = null;

    private static htmlFilterTransformer transformer = null;
    private static File htRootPath = null;

    //private Properties connectionProperties = null;
    // creating a logger
    private static final serverLog theLogger = new serverLog("PROXY");
    
    private static boolean doAccessLogging = false; 
	/**
     * Do logging configuration for special proxy access log file
     */
    static {
        // Doing logger initialization
        try {
            theLogger.logInfo("Configuring proxy access logging ...");            
            
            // getting the logging manager
            final LogManager manager = LogManager.getLogManager();
            final String className = httpdProxyHandler.class.getName();
            
            // determining if proxy access logging is enabled
            final String enabled = manager.getProperty("de.anomic.http.httpdProxyHandler.logging.enabled");
            if ("true".equalsIgnoreCase(enabled)) {
                
                // reading out some needed configuration properties
                int limit = 1024*1024, count = 20;
                String pattern = manager.getProperty(className + ".logging.FileHandler.pattern");
                if (pattern == null) pattern = "DATA/LOG/proxyAccess%u%g.log";
                
                final String limitStr = manager.getProperty(className + ".logging.FileHandler.limit");
                if (limitStr != null) try { limit = Integer.valueOf(limitStr).intValue(); } catch (final NumberFormatException e) {}
                
                final String countStr = manager.getProperty(className + ".logging.FileHandler.count");
                if (countStr != null) try { count = Integer.valueOf(countStr).intValue(); } catch (final NumberFormatException e) {}
                
                // creating the proxy access logger
                final Logger proxyLogger = Logger.getLogger("PROXY.access");
                proxyLogger.setUseParentHandlers(false);
                proxyLogger.setLevel(Level.FINEST);
                
                final FileHandler txtLog = new FileHandler(pattern,limit,count,true);
                txtLog.setFormatter(new serverMiniLogFormatter());
                txtLog.setLevel(Level.FINEST);
                proxyLogger.addHandler(txtLog);     
                
                doAccessLogging = true; 
                theLogger.logInfo("Proxy access logging configuration done." + 
                                  "\n\tFilename: " + pattern + 
                                  "\n\tLimit: " + limitStr + 
                                  "\n\tCount: " + countStr);
            } else {
                theLogger.logInfo("Proxy access logging is deactivated.");
            }
        } catch (final Exception e) { 
            theLogger.logSevere("Unable to configure proxy access logging.",e);        
        }
        
        switchboard = plasmaSwitchboard.getSwitchboard();
        if (switchboard != null) {
            
        isTransparentProxy = Boolean.valueOf(switchboard.getConfig("isTransparentProxy","false")).booleanValue();
            
        // set timeout
        timeout = Integer.parseInt(switchboard.getConfig("proxy.clientTimeout", "10000"));
            
        // create a htRootPath: system pages
        htRootPath = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath","htroot"));
        if (!(htRootPath.exists())) {
            if(!htRootPath.mkdir())
                serverLog.logSevere("PROXY", "could not create htRoot "+ htRootPath);
        }
            
        // load a transformer
        transformer = new htmlFilterContentTransformer();
        transformer.init(new File(switchboard.getRootPath(), switchboard.getConfig(plasmaSwitchboardConstants.LIST_BLUE, "")).toString());
            
        // load the yellow-list
        final String f = switchboard.getConfig("proxyYellowList", null);
        if (f != null) {
            yellowList = serverFileUtils.loadList(new File(f)); 
            theLogger.logConfig("loaded yellow-list from file " + f + ", " + yellowList.size() + " entries");
        } else {
            yellowList = new HashSet<String>();
        }
        
        final String redirectorPath = switchboard.getConfig("externalRedirector", "");
        if (redirectorPath.length() > 0 && redirectorEnabled == false){    
            try {
                redirectorProcess=Runtime.getRuntime().exec(redirectorPath);
                redirectorWriter = new PrintWriter(redirectorProcess.getOutputStream());
                redirectorReader = new BufferedReader(new InputStreamReader(redirectorProcess.getInputStream()));
                redirectorEnabled=true;
            } catch (final IOException e) {
                System.out.println("redirector not Found");
            }
        }
        } else {
        	yellowList = null;
        }
    }
    
    /**
     * Special logger instance for proxy access logging much similar
     * to the squid access.log file 
     */
    private static final serverLog proxyLog = new serverLog("PROXY.access");
    
    /**
     * Reusable {@link StringBuilder} for logging
     */
    private static final StringBuilder logMessage = new StringBuilder();
    
    /**
     * Reusable {@link StringBuffer} to generate the useragent string
     */
    private static final StringBuffer userAgentStr = new StringBuffer();
    
    /**
     * A Set of media types which are known to only contain binary data (no readable text)
     * Each is only the first part of the content-type field (no subtypes)
     */
    private static final Set<String> binaryTypes = new HashSet<String>();
    
    /**
     * A Set of content-types which are known to only contain binary data (no readable text)
     * Each is a complete content-type header field (without parameters)
     */
    private static final Set<String> binaryContent = new HashSet<String>();
    static {
        // all Strings must be lower case!!
        // RFC 2045: "Matching of media type and subtype is ALWAYS case-insensitive."
        // discrete types
        binaryTypes.add("image");
        binaryTypes.add("audio");
        binaryTypes.add("video");
        
        binaryContent.add("application/octet-stream");
    }
    
    public static void handleOutgoingCookies(final httpRequestHeader requestHeader, final String targethost, final String clienthost) {
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
        if (switchboard.getConfigBool("proxy.monitorCookies", false)) {
            if (requestHeader.containsKey(httpRequestHeader.COOKIE)) {
                final Object[] entry = new Object[]{new Date(), clienthost, requestHeader.getMultiple(httpRequestHeader.COOKIE)};
                synchronized(switchboard.outgoingCookies) {
                    switchboard.outgoingCookies.put(targethost, entry);
                }
            }
        }
    }
    
    public static void handleIncomingCookies(final httpResponseHeader respondHeader, final String serverhost, final String targetclient) {
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
        if (switchboard.getConfigBool("proxy.monitorCookies", false)) {
            if (respondHeader.containsKey(httpResponseHeader.SET_COOKIE)) {
                final Object[] entry = new Object[]{new Date(), targetclient, respondHeader.getMultiple(httpResponseHeader.SET_COOKIE)};
                synchronized(switchboard.incomingCookies) {
                    switchboard.incomingCookies.put(serverhost, entry);
                }
            }
        }
    }
    
    /**
     * @param conProp a collection of properties about the connection, like URL
     * @param requestHeader The header lines of the connection from the request
     * @param respond the OutputStream to the client
     * @see de.anomic.http.httpdHandler#doGet(java.util.Properties, de.anomic.http.httpHeader, java.io.OutputStream)
     */
    public static void doGet(final Properties conProp, final httpRequestHeader requestHeader, final OutputStream respond) {
        httpdByteCountOutputStream countedRespond = null;
        try {
            final int reqID = requestHeader.hashCode();
            // remembering the starting time of the request
            final Date requestDate = new Date(); // remember the time...
            conProp.put(httpHeader.CONNECTION_PROP_REQUEST_START, Long.valueOf(requestDate.getTime()));
            if (yacyTrigger) de.anomic.yacy.yacyCore.triggerOnlineAction();
            switchboard.proxyLastAccess = System.currentTimeMillis();

            // using an ByteCount OutputStream to count the send bytes (needed for the logfile)
            countedRespond = new httpdByteCountOutputStream(respond,conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE).length() + 2,"PROXY");

            String host = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);           // always starts with leading '/'
            final String args = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);     // may be null if no args were given
            final String ip   = conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer
            int pos=0;
            int port=0;

            yacyURL url = null;
            try {
                url = httpHeader.getRequestURL(conProp);
                if (theLogger.isFine()) theLogger.logFine(reqID +" GET "+ url);
                if (theLogger.isFinest()) theLogger.logFinest(reqID +"    header: "+ requestHeader);

                //redirector
                if (redirectorEnabled){
                    synchronized(redirectorProcess){
                        redirectorWriter.println(url.toNormalform(false, true));
                        redirectorWriter.flush();
                    }
                    final String newUrl = redirectorReader.readLine();
                    if (!newUrl.equals("")) {
                        try {
                            url = new yacyURL(newUrl, null);
                        } catch(final MalformedURLException e){}//just keep the old one
                    }
                    if (theLogger.isFinest()) theLogger.logFinest(reqID +"    using redirector to "+ url);
                    conProp.setProperty(httpHeader.CONNECTION_PROP_HOST, url.getHost()+":"+url.getPort());
                    conProp.setProperty(httpHeader.CONNECTION_PROP_PATH, url.getPath());
                    requestHeader.put(httpHeader.HOST, url.getHost()+":"+url.getPort());
                    requestHeader.put(httpHeader.CONNECTION_PROP_PATH, url.getPath());
                }
            } catch (final MalformedURLException e) {
                final String errorMsg = "ERROR: internal error with url generation: host=" +
                                  host + ", port=" + port + ", path=" + path + ", args=" + args;
                theLogger.logSevere(errorMsg);
                httpd.sendRespondError(conProp,countedRespond,4,501,null,errorMsg,e);
                return;
            }

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

            // check the blacklist
            // blacklist idea inspired by [AS]:
            // respond a 404 for all AGIS ("all you get is shit") servers
            final String hostlow = host.toLowerCase();
            if (args != null) { path = path + "?" + args; }
            if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_PROXY, hostlow, path)) {
                theLogger.logInfo("AGIS blocking of host '" + hostlow + "'");
                httpd.sendRespondError(conProp,countedRespond,4,403,null,
                        "URL '" + hostlow + "' blocked by yacy proxy (blacklisted)",null);
                return;
            }

            // handle outgoing cookies
            handleOutgoingCookies(requestHeader, host, ip);

            prepareRequestHeader(conProp, requestHeader, hostlow);
            
            // decide whether to use a cache entry or connect to the network
            final File cacheFile = plasmaHTCache.getCachePath(url);
            
            httpResponseHeader cachedResponseHeader = plasmaHTCache.loadResponseHeader(url);
            final boolean cacheExists = cacheFile.isFile() && (cachedResponseHeader != null);
            
            // why are files unzipped upon arrival? why not zip all files in cache?
            // This follows from the following premises
            // (a) no file shall be unzip-ed more than once to prevent unnecessary computing time
            // (b) old cache entries shall be comparable with refill-entries to detect/distinguish case 3+4
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
            if (cachedResponseHeader == null) {
                if (theLogger.isFinest()) theLogger.logFinest(reqID + " page not in cache: fulfill request from web");
                    fulfillRequestFromWeb(conProp,url,ext,requestHeader,cachedResponseHeader,cacheFile,countedRespond);
            } else {
                final indexDocumentMetadata cacheEntry = new httpdProxyCacheEntry(
                        0,                               // crawling depth
                        url,                             // url
                        "",                              // name of the url is unknown
                        //requestHeader,                 // request headers
                        "200 OK",                        // request status
                        requestHeader,
                        cachedResponseHeader,
                        null,                            // initiator
                        switchboard.webIndex.defaultProxyProfile  // profile
                );
                plasmaHTCache.storeMetadata(cachedResponseHeader, cacheEntry); // TODO: check if this storeMetadata is necessary
                
                if (cacheExists && cacheEntry.shallUseCacheForProxy()) {
                    if (theLogger.isFinest()) theLogger.logFinest(reqID + " fulfill request from cache");
                    fulfillRequestFromCache(conProp,url,ext,requestHeader,cachedResponseHeader,cacheFile,countedRespond);
                } else {            
                    if (theLogger.isFinest()) theLogger.logFinest(reqID + " fulfill request from web");
                    fulfillRequestFromWeb(conProp,url,ext,requestHeader,cachedResponseHeader,cacheFile,countedRespond);
                }
            }
            
           
        } catch (final Exception e) {
            try {
                final String exTxt = e.getMessage();
                if ((exTxt!=null)&&(exTxt.startsWith("Socket closed"))) {
                    forceConnectionClose(conProp);
                } else if (!conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                    final String errorMsg = "Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage(); 
                    httpd.sendRespondError(conProp,countedRespond,4,501,null,errorMsg,e);
                    theLogger.logSevere(errorMsg);
                } else {
                    forceConnectionClose(conProp);                    
                }
            } catch (final Exception ee) {
                forceConnectionClose(conProp);
            }            
        } finally {
            try { if(countedRespond != null) countedRespond.flush(); else if(respond != null) respond.flush(); } catch (final Exception e) {}
            if (countedRespond != null) countedRespond.finish();
            
            conProp.put(httpHeader.CONNECTION_PROP_REQUEST_END, Long.valueOf(System.currentTimeMillis()));
            conProp.put(httpHeader.CONNECTION_PROP_PROXY_RESPOND_SIZE,(countedRespond != null) ? Long.valueOf(countedRespond.getCount()) : -1L);
            logProxyAccess(conProp);
        }
    }
    
    private static void fulfillRequestFromWeb(final Properties conProp, final yacyURL url,final String ext, final httpRequestHeader requestHeader, final httpResponseHeader cachedResponseHeader, final File cacheFile, final OutputStream respond) {
        
        final GZIPOutputStream gzippedOut = null; 
        Writer textOutput = null;
        
        JakartaCommonsHttpResponse res = null;                
        try {
            final int reqID = requestHeader.hashCode();

            String host =    conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            final String path =    conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);     // always starts with leading '/'
            final String args =    conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);     // may be null if no args were given
            final String ip =      conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer
            final String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER); // the ip from the connecting peer            
            
            int port, pos;        
            if ((pos = host.indexOf(":")) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }            
            
            // resolve yacy and yacyh domains
            final String yAddress = resolveYacyDomains(host);
            
            // re-calc the url path
            String remotePath = (args == null) ? path : (path + "?" + args); // with leading '/'
            
            // attach possible yacy-sublevel-domain
            if ((yAddress != null) &&
                    ((pos = yAddress.indexOf("/")) >= 0) &&
                    (!(remotePath.startsWith("/env"))) // this is the special path, staying always at root-level
            ) remotePath = yAddress.substring(pos) + remotePath;            
            
            modifyProxyHeaders(requestHeader, httpVer);
            
            final String connectHost = hostPart(host, port, yAddress);
            final String getUrl = "http://"+ connectHost + remotePath;
            
            final JakartaCommonsHttpClient client = setupHttpClient(requestHeader, connectHost);
            
            // send request
            try {
            res = client.GET(getUrl);
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    response status: "+ res.getStatusLine());
            conProp.put(httpHeader.CONNECTION_PROP_CLIENT_REQUEST_HEADER, requestHeader);
            
            final httpResponseHeader responseHeader = res.getResponseHeader();
            // determine if it's an internal error of the httpc
            if (responseHeader.size() == 0) {
                throw new Exception(res.getStatusLine());
            }
            
            final httpChunkedOutputStream chunkedOut = setTransferEncoding(conProp, responseHeader, res.getStatusCode(), respond);        
            
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
                plasmaHTCache.deleteURLfromCache(url, false);
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_MISS");
            }            

            // reserver cache entry
            final indexDocumentMetadata cacheEntry = new httpdProxyCacheEntry(
                    0, 
                    url,
                    "",
                    res.getStatusLine(),
                    requestHeader,
                    responseHeader,
                    null, 
                    switchboard.webIndex.defaultProxyProfile
            );
            plasmaHTCache.storeMetadata(responseHeader, cacheEntry);

            // handle file types and make (possibly transforming) output stream
            final OutputStream outStream = (gzippedOut != null) ? gzippedOut : ((chunkedOut != null)? chunkedOut : respond);
            final boolean isBinary = isBinary(responseHeader);
            if (isBinary) {
                if (theLogger.isFine()) theLogger.logFine(reqID +" create direct passthrough for URL " + url + ", extension '" + ext + "', mime-type '" + responseHeader.mime() + "'");
            } else {
                // handle text stuff (encoding and so on)
                final Charset charSet = responseHeader.getCharSet();
                if (
                        (!transformer.isIdentityTransformer()) &&
                        (plasmaParser.supportedHTMLContent(url,responseHeader.mime()))
                    ) {
                    // make a transformer
                    if (theLogger.isFine()) theLogger.logFine(reqID +" create transformer for URL " + url);
                    //hfos = new htmlFilterOutputStream((gzippedOut != null) ? gzippedOut : ((chunkedOut != null)? chunkedOut : respond), null, transformer, (ext.length() == 0));
                    textOutput = new htmlFilterWriter(outStream,charSet, null, transformer, (ext.length() == 0));
                } else {
                    // simply pass through without parsing
                    if (theLogger.isFine()) theLogger.logFine(reqID +" create text passthrough for URL " + url + ", extension '" + ext + "', mime-type '" + responseHeader.mime() + "'");
                    textOutput = new OutputStreamWriter(outStream, charSet);
                }
            }
            
            // handle incoming cookies
            handleIncomingCookies(responseHeader, host, ip);
            
            prepareResponseHeader(responseHeader, res.getHttpVer());               
            
            // sending the respond header back to the client
            if (chunkedOut != null) {
                responseHeader.put(httpResponseHeader.TRANSFER_ENCODING, "chunked");
            }
            
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    sending response header: "+ responseHeader);
            httpd.sendRespondHeader(
                    conProp,
                    respond,
                    httpVer,
                    res.getStatusCode(),
                    res.getStatusLine().substring(4), // status text 
                    responseHeader);
            
            if(hasBody(res.getStatusCode())) {
            
            final String storeError = cacheEntry.shallStoreCacheForProxy();
            final boolean storeHTCache = cacheEntry.profile().storeHTCache();
            final boolean isSupportedContent = plasmaParser.supportedContent(plasmaParser.PARSER_MODE_PROXY,cacheEntry.url(),cacheEntry.getMimeType());
            if (
                    /*
                     * Now we store the response into the htcache directory if 
                     * a) the response is cacheable AND 
                     */
                    (storeError == null) &&
                    /*  
                     * b) the user has configured to use the htcache OR
                     * c) the content should be indexed
                     */
                    ((storeHTCache) || (isSupportedContent))
            ) {
                final long contentLength = responseHeader.getContentLength();
                // we write a new cache entry
                if ((contentLength > 0) && (contentLength < 1048576)) // if the length is known and < 1 MB
                {
                    // ok, we don't write actually into a file, only to RAM, and schedule writing the file.
                    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    if(isBinary) {
                        final OutputStream toClientAndMemory = new MultiOutputStream(new OutputStream[] {outStream, byteStream});
                        serverFileUtils.copy(res.getDataAsStream(), toClientAndMemory);
                    } else {
                        writeTextContent(res, new BufferedWriter(textOutput), byteStream);
                    }
                    // cached bytes
                    byte[] cacheArray;
                    if(byteStream.size() > 0) {
                        cacheArray = byteStream.toByteArray();
                    } else {
                        cacheArray = null;
                    }
                    if (theLogger.isFine()) theLogger.logFine(reqID +" writeContent of " + url + " produced cacheArray = " + ((cacheArray == null) ? "null" : ("size=" + cacheArray.length)));

                    if (textOutput instanceof htmlFilterWriter) ((htmlFilterWriter) textOutput).close();

                    if (sizeBeforeDelete == -1) {
                        // totally fresh file
                        //cacheEntry.status = plasmaHTCache.CACHE_FILL; // it's an insert
                        cacheEntry.setCacheArray(cacheArray);
                        plasmaHTCache.push(cacheEntry);
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_MISS");
                    } else if (cacheArray != null && sizeBeforeDelete == cacheArray.length) {
                        // before we came here we deleted a cache entry
                        cacheArray = null;
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
                        //cacheManager.push(cacheEntry); // unnecessary update
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REF_FAIL_HIT");                                
                    } else {
                        // before we came here we deleted a cache entry
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
                        cacheEntry.setCacheArray(cacheArray);
                        plasmaHTCache.push(cacheEntry); // necessary update, write response header to cache
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_MISS");
                    }
                } else {
                    // the file is too big to cache it in the ram, or the size is unknown
                    // write to file right here.
                    cacheFile.getParentFile().mkdirs();
                    final OutputStream fileStream = new FileOutputStream(cacheFile);
                    if(isBinary) {
                        OutputStream toClientAndFile = new MultiOutputStream(new OutputStream[] {outStream, fileStream});
                        serverFileUtils.copy(res.getDataAsStream(), toClientAndFile);
                    } else {
                        writeTextContent(res, new BufferedWriter(textOutput), fileStream);
                    }
                    if (textOutput instanceof htmlFilterWriter) ((htmlFilterWriter) textOutput).close();
                    if (theLogger.isFine()) theLogger.logFine(reqID +" for write-file of " + url + ": contentLength = " + contentLength + ", sizeBeforeDelete = " + sizeBeforeDelete);
                    plasmaHTCache.writeFileAnnouncement(cacheFile);
                    if (sizeBeforeDelete == -1) {
                        // totally fresh file
                        //cacheEntry.status = plasmaHTCache.CACHE_FILL; // it's an insert
                        plasmaHTCache.push(cacheEntry);
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_MISS");
                    } else if (sizeBeforeDelete == cacheFile.length()) {
                        // before we came here we deleted a cache entry
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_BAD;
                        //cacheManager.push(cacheEntry); // unnecessary update
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REF_FAIL_HIT");
                    } else {
                        // before we came here we deleted a cache entry
                        //cacheEntry.status = plasmaHTCache.CACHE_STALE_RELOAD_GOOD;
                        plasmaHTCache.push(cacheEntry); // necessary update, write response header to cache
                        conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_MISS");
                    }
                    // beware! all these writings will not fill the cacheEntry.cacheArray
                    // that means they are not available for the indexer (except they are scraped before)
                }
            } else {
                // no caching
                if (theLogger.isFine()) theLogger.logFine(reqID +" "+ cacheFile.toString() + " not cached." +
                        " StoreError=" + ((storeError==null)?"None":storeError) + 
                        " StoreHTCache=" + storeHTCache + 
                        " SupportetContent=" + isSupportedContent);

                if(isBinary) {
                    // directly pass bytes to client
                    serverFileUtils.copy(res.getDataAsStream(), outStream);
                } else {
                    // read data with specified encoding and send it as character stream
                    writeTextContent(res, new BufferedWriter(textOutput));
                }
                if (textOutput instanceof htmlFilterWriter) ((htmlFilterWriter) textOutput).close();
                /*if (sizeBeforeDelete == -1) {
                    // no old file and no load. just data passing
                    //cacheEntry.status = plasmaHTCache.CACHE_PASSING;
                    //cacheManager.push(cacheEntry);
                } else {
                    // before we came here we deleted a cache entry
                    //cacheEntry.status = plasmaHTCache.CACHE_STALE_NO_RELOAD;
                    //cacheManager.push(cacheEntry);
                }*/
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_MISS");
            }
            
            if (gzippedOut != null) {
                gzippedOut.finish();
            }
            if (chunkedOut != null) {
                chunkedOut.finish();
                chunkedOut.flush();
            }
            } // end hasBody
            } finally {
                // if opened ...
                if(res != null) {
                    // ... close connection
                    res.closeStream();
                }
            }
        } catch (final Exception e) {
            // deleting cached content
            if (cacheFile.exists()) cacheFile.delete();
            handleProxyException(e,conProp,respond,url);
        }
    }

    /**
     * determines if the response should have a body
     * 
     * @param statusCode
     * @param responseHeader
     * @return
     */
    private static boolean hasBody(final int statusCode) {
        // "All 1xx (informational), 204 (no content), and 304 (not modified) responses MUST NOT
        //  include a message-body."
        // [RFC 2616 HTTP/1.1, Sect. 4.3] and like [RFC 1945 HTTP/1.0, Sect. 7.2]
        if((statusCode >= 100 && statusCode < 200) || statusCode == 204 || statusCode == 304) {
            return false; 
        }
        return true;
    }

    private static void fulfillRequestFromCache(
            final Properties conProp, 
            final yacyURL url,
            final String ext,
            final httpRequestHeader requestHeader, 
            final httpResponseHeader cachedResponseHeader,
            final File cacheFile,
            final OutputStream respond
    ) throws IOException {
        
        final String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
        
        final httpChunkedOutputStream chunkedOut = null;
        final GZIPOutputStream gzippedOut = null;
        Writer textOutput = null;
        
        // we respond on the request by using the cache, the cache is fresh        
        try {
            modifyProxyHeaders(cachedResponseHeader, httpVer);
            
            // replace date field in old header by actual date, this is according to RFC
            cachedResponseHeader.put(httpHeader.DATE, HttpClient.dateString(new Date()));
            
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
            if (requestHeader.containsKey(httpRequestHeader.IF_MODIFIED_SINCE)) {
                // conditional request: freshness of cache for that condition was already
                // checked within shallUseCache(). Now send only a 304 response
                theLogger.logInfo("CACHE HIT/304 " + cacheFile.toString());
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_HIT");
                
                // setting the content length header to 0
                cachedResponseHeader.put(httpResponseHeader.CONTENT_LENGTH, Integer.toString(0));
                
                // send cached header with replaced date and added length
                httpd.sendRespondHeader(conProp,respond,httpVer,304,cachedResponseHeader);
                //respondHeader(respond, "304 OK", cachedResponseHeader); // respond with 'not modified'
            } else {
                // unconditional request: send content of cache
                theLogger.logInfo("CACHE HIT/203 " + cacheFile.toString());
                conProp.setProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_HIT");
                
                // setting the content header to the proper length
                cachedResponseHeader.put(httpResponseHeader.CONTENT_LENGTH, Long.toString(cacheFile.length()));
                
                // send cached header with replaced date and added length 
                httpd.sendRespondHeader(conProp,respond,httpVer,203,cachedResponseHeader);
                //respondHeader(respond, "203 OK", cachedResponseHeader); // respond with 'non-authoritative'
                
                // determine the content charset
                final Charset charSet = cachedResponseHeader.getCharSet();
                
                // make a transformer
                final OutputStream outStream = (gzippedOut != null) ? gzippedOut : ((chunkedOut != null)? chunkedOut : respond);
                if (( !transformer.isIdentityTransformer()) &&
                        (ext == null || !plasmaParser.supportedHTMLFileExtContains(url)) &&
                        (plasmaParser.HTMLParsableMimeTypesContains(cachedResponseHeader.mime()))) {
                    textOutput = new htmlFilterWriter(outStream, charSet, null, transformer, (ext == null || ext.length() == 0));
                }
                
                // send also the complete body now from the cache
                // simply read the file and transfer to out socket
                if(textOutput != null && !isBinary(cachedResponseHeader)) {
                    // send as encoded text
                    serverFileUtils.copy(cacheFile, charSet, textOutput);
                } else {
                    serverFileUtils.copy(cacheFile, outStream);
                }
                
                if (textOutput != null) textOutput.close();
                if (gzippedOut != null) gzippedOut.finish();
                if (chunkedOut != null) chunkedOut.finish();
            }
            // that's it!
        } catch (final Exception e) {
            // this happens if the client stops loading the file
            // we do nothing here                            
            if (conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                theLogger.logWarning("Error while trying to send cached message body.");
                conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
            } else {
                httpd.sendRespondError(conProp,respond,4,503,"socket error: " + e.getMessage(),"socket error: " + e.getMessage(), e);
            }
        } finally {
            try { respond.flush(); } catch (final Exception e) {}
        }
        return;
    }

    /**
     * determines if the body is text or not
     * 
     * @param responseHeader
     * @return
     */
    private static boolean isBinary(httpResponseHeader responseHeader) {
        String mime = responseHeader.mime().toLowerCase();
        if(mime.contains(";")) {
            // cut of parameters
            mime = mime.substring(0, mime.indexOf(';'));
        }
        // mime and the contents of the Set must be lower case!
        if(binaryContent.contains(mime)) {
            return true;
        }
        final int endType = mime.contains("/") ? mime.indexOf('/') : mime.length();  
        final String type = mime.substring(0, endType);
        if(binaryTypes.contains(type)) {
            return true;
        }
        return false;
    }

    /**
     * ready the body of res with charSet and write it to output
     * 
     * @param res
     * @param output
     * @throws IOException
     */
    public static void writeTextContent(final JakartaCommonsHttpResponse res, final BufferedWriter output) throws IOException {
        try {
            final InputStream data = res.getDataAsStream();
            if (data == null) return;
            final Charset charSet = res.getResponseHeader().getCharSet();
            serverFileUtils.copyToWriter(new BufferedInputStream(data), output, charSet);
        } finally {
            res.closeStream();
        }
    }
    
    /**
     * ready the body of res with charSet and write it to output and parallel encoded with charSet to byteStream
     * 
     * @param res
     * @param output
     * @param byteStream
     * @throws IOException
     */
    public static void writeTextContent(final JakartaCommonsHttpResponse res, final BufferedWriter output, final OutputStream byteStream) throws IOException {
        assert byteStream != null;
        try {
            final InputStream data = res.getDataAsStream();
            if (data == null) return;
            final Charset charSet = res.getResponseHeader().getCharSet();
            serverFileUtils.copyToWriters(new BufferedInputStream(data), output, new BufferedWriter(new OutputStreamWriter(byteStream, charSet)) , charSet);
        } finally {
            res.closeStream();
        }
    }

    public static void doHead(final Properties conProp, final httpRequestHeader requestHeader, OutputStream respond) {
        
        JakartaCommonsHttpResponse res = null;
        yacyURL url = null;
        try {
            final int reqID = requestHeader.hashCode();
            // remembering the starting time of the request
            final Date requestDate = new Date(); // remember the time...
            conProp.put(httpHeader.CONNECTION_PROP_REQUEST_START, Long.valueOf(requestDate.getTime()));
            if (yacyTrigger) de.anomic.yacy.yacyCore.triggerOnlineAction();
            switchboard.proxyLastAccess = System.currentTimeMillis();
            
            // using an ByteCount OutputStream to count the send bytes
            respond = new httpdByteCountOutputStream(respond,conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE).length() + 2,"PROXY");                                   
            
            String host = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            final String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
            final String args = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS); 
            final String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
            
            int port, pos;
            if ((pos = host.indexOf(":")) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
            
            try {
                url = new yacyURL("http", host, port, (args == null) ? path : path + "?" + args);
            } catch (final MalformedURLException e) {
                final String errorMsg = "ERROR: internal error with url generation: host=" +
                                  host + ", port=" + port + ", path=" + path + ", args=" + args;
                theLogger.logSevere(errorMsg);
                httpd.sendRespondError(conProp,respond,4,501,null,errorMsg,e);
                return;
            } 
            if (theLogger.isFine()) theLogger.logFine(reqID +" HEAD "+ url);
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    header: "+ requestHeader);
            
            // check the blacklist, inspired by [AS]: respond a 404 for all AGIS (all you get is shit) servers
            final String hostlow = host.toLowerCase();

            // re-calc the url path
            String remotePath = (args == null) ? path : (path + "?" + args);

            if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_PROXY, hostlow, remotePath)) {
                httpd.sendRespondError(conProp,respond,4,403,null,
                        "URL '" + hostlow + "' blocked by yacy proxy (blacklisted)",null);
                theLogger.logInfo("AGIS blocking of host '" + hostlow + "'");
                return;
            }                   
            
            prepareRequestHeader(conProp, requestHeader, hostlow);
            
            // resolve yacy and yacyh domains
            final String yAddress = resolveYacyDomains(host);
            
            // attach possible yacy-sublevel-domain
            if ((yAddress != null) && ((pos = yAddress.indexOf("/")) >= 0)) remotePath = yAddress.substring(pos) + remotePath;
            
            modifyProxyHeaders(requestHeader, httpVer);            
            
            // generate request-url
            final String connectHost = hostPart(host, port, yAddress);
            final String getUrl = "http://"+ connectHost + remotePath;
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    using url: "+ getUrl);
            
            final JakartaCommonsHttpClient client = setupHttpClient(requestHeader, connectHost);
            
            // send request
            try {
            res = client.HEAD(getUrl);
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    response status: "+ res.getStatusLine());
            
            // determine if it's an internal error of the httpc
            final httpResponseHeader responseHeader = res.getResponseHeader();
            if (responseHeader.size() == 0) {
                throw new Exception(res.getStatusLine());
            }            
            
            prepareResponseHeader(responseHeader, res.getHttpVer());

            // sending the server respond back to the client
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    sending response header: "+ responseHeader);
            httpd.sendRespondHeader(conProp,respond,httpVer,res.getStatusCode(),res.getStatusLine().substring(4),responseHeader);
            respond.flush();
            } finally {
                if(res != null) {
                    // ... close connection
                    res.closeStream();
                }
            }
        } catch (final Exception e) {
            handleProxyException(e,conProp,respond,url); 
        }
    }

    public static void doPost(final Properties conProp, final httpRequestHeader requestHeader, final OutputStream respond, InputStream body) throws IOException {
        assert conProp != null : "precondition violated: conProp != null";
        assert requestHeader != null : "precondition violated: requestHeader != null";
        assert body != null : "precondition violated: body != null";
        yacyURL url = null;
        httpdByteCountOutputStream countedRespond = null;
        try {
            final int reqID = requestHeader.hashCode();
            // remembering the starting time of the request
            final Date requestDate = new Date(); // remember the time...
            conProp.put(httpHeader.CONNECTION_PROP_REQUEST_START, Long.valueOf(requestDate.getTime()));
            if (yacyTrigger) de.anomic.yacy.yacyCore.triggerOnlineAction();
            switchboard.proxyLastAccess = System.currentTimeMillis();
            
            // using an ByteCount OutputStream to count the send bytes
            countedRespond  = new httpdByteCountOutputStream(respond,conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE).length() + 2,"PROXY");
                        
            String host    = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            final String path    = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
            final String args    = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS); // may be null if no args were given
            final String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);

            int port, pos;
            if ((pos = host.indexOf(":")) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
            
            try {
                url = new yacyURL("http", host, port, (args == null) ? path : path + "?" + args);
            } catch (final MalformedURLException e) {
                final String errorMsg = "ERROR: internal error with url generation: host=" +
                                  host + ", port=" + port + ", path=" + path + ", args=" + args;
                theLogger.logSevere(errorMsg);
                httpd.sendRespondError(conProp,countedRespond,4,501,null,errorMsg,e);
                return;
            }                             
            if (theLogger.isFine()) theLogger.logFine(reqID +" POST "+ url);
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    header: "+ requestHeader);
            
            prepareRequestHeader(conProp, requestHeader, host.toLowerCase());
            
            final String yAddress = resolveYacyDomains(host);
            
            // re-calc the url path
            String remotePath = (args == null) ? path : (path + "?" + args);
            
            // attach possible yacy-sublevel-domain
            if ((yAddress != null) && ((pos = yAddress.indexOf("/")) >= 0)) remotePath = yAddress.substring(pos) + remotePath;
                  
            modifyProxyHeaders(requestHeader, httpVer); 
            
            final String connectHost = hostPart(host, port, yAddress);
            final String getUrl = "http://"+ connectHost + remotePath;
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    using url: "+ getUrl);
            
            final JakartaCommonsHttpClient client = setupHttpClient(requestHeader, connectHost);
            
            // check input
            if(body == null) {
                theLogger.logSevere("no body to POST!");
            }
            // from old httpc:
            // "if there is a body to the call, we would have a CONTENT-LENGTH tag in the requestHeader"
            // it seems that it is a HTTP/1.1 connection which stays open (the inputStream) and endlessly waits for
            // input so we have to end it to do the request
            final long requestLength = requestHeader.getContentLength();
            if(requestLength > -1) {
                final byte[] bodyData;
                if(requestLength == 0) {
                    // no body
                    bodyData = new byte[0];
                } else {
                    // read content-length bytes into memory
                    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    serverFileUtils.copy(body, buffer, requestLength);
                    bodyData = buffer.toByteArray();
                }
                body = new ByteArrayInputStream(bodyData);
            }
            JakartaCommonsHttpResponse res = null;
            try {
            // sending the request
            res = client.POST(getUrl, body);
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    response status: "+ res.getStatusLine());
            
            final httpResponseHeader responseHeader = res.getResponseHeader();
            // determine if it's an internal error of the httpc
            if (responseHeader.size() == 0) {
                throw new Exception(res.getStatusLine());
            }                                  
            
            final httpChunkedOutputStream chunked = setTransferEncoding(conProp, responseHeader, res.getStatusCode(), countedRespond);
            
            prepareResponseHeader(responseHeader, res.getHttpVer()); 
            
            // sending the respond header back to the client
            if (chunked != null) {
                responseHeader.put(httpResponseHeader.TRANSFER_ENCODING, "chunked");
            }
            
            // sending response headers
            if (theLogger.isFinest()) theLogger.logFinest(reqID +"    sending response header: "+ responseHeader);
            httpd.sendRespondHeader(conProp,
                                    countedRespond,
                                    httpVer,
                                    res.getStatusCode(),
                                    res.getStatusLine().substring(4), // status text
                                    responseHeader);
            
            // respondHeader(respond, res.status, res.responseHeader);
            // Saver.writeContent(res, (chunked != null) ? new BufferedOutputStream(chunked) : new BufferedOutputStream(respond));
            /*
            // *** (Uebernommen aus Saver-Klasse: warum ist dies hier die einzige Methode, die einen OutputStream statt einen Writer benutzt?)
            try {
                serverFileUtils.copyToStream(new BufferedInputStream(res.getDataAsStream()), (chunked != null) ? new BufferedOutputStream(chunked) : new BufferedOutputStream(respond));
            } finally {
                res.closeStream();
            }
            if (chunked != null)  chunked.finish();
            */
            final OutputStream outStream = (chunked != null) ? chunked : countedRespond;
            if(isBinary(responseHeader)) {
                serverFileUtils.copy(res.getDataAsStream(), outStream);
            } else {
                writeTextContent(res, new BufferedWriter(new OutputStreamWriter(outStream, responseHeader.getCharSet())));
            }
            
            if (chunked != null) {
                chunked.finish();
            }
            outStream.flush();
            } finally {
                // if opened ...
                if(res != null) {
                    // ... close connection
                    res.closeStream();
                }
            }
        } catch (final Exception e) {
            handleProxyException(e,conProp,countedRespond,url);                 
        } finally {
            if(countedRespond != null) {
                countedRespond.flush();
                countedRespond.finish();
            }
            if(respond != null) {
                respond.flush();
            }
            
            conProp.put(httpHeader.CONNECTION_PROP_REQUEST_END, Long.valueOf(System.currentTimeMillis()));
            conProp.put(httpHeader.CONNECTION_PROP_PROXY_RESPOND_SIZE,(countedRespond != null) ? Long.valueOf(countedRespond.getCount()) : -1L);
            logProxyAccess(conProp);
        }
    }

    /**
     * resolve yacy and yacyh domains
     * 
     * @param host
     * @return
     */
    private static String resolveYacyDomains(final String host) {
        return (httpd.getAlternativeResolver() == null) ? null : httpd.getAlternativeResolver().resolve(host);
    }

    /**
     * @param host
     * @param port
     * @param yAddress
     * @return
     */
    private static String hostPart(final String host, final int port, final String yAddress) {
        final String connectHost = (yAddress == null) ? host +":"+ port : yAddress;
        return connectHost;
    }

    /**
     * @param conProp
     * @param requestHeader
     * @param hostlow
     */
    private static void prepareRequestHeader(final Properties conProp, final httpRequestHeader requestHeader, final String hostlow) {
        // set another userAgent, if not yellow-listed
        if ((yellowList != null) && (!(yellowList.contains(domain(hostlow))))) {
            // change the User-Agent
            requestHeader.put(httpHeader.USER_AGENT, generateUserAgent(requestHeader));
        }

	// only gzip-encoding is supported, remove other encodings (e. g. deflate)
        if (((String)requestHeader.get(httpRequestHeader.ACCEPT_ENCODING,"")).indexOf("gzip") != -1) {
            requestHeader.put(httpRequestHeader.ACCEPT_ENCODING, "gzip");
	} else {
            requestHeader.put(httpRequestHeader.ACCEPT_ENCODING, "");
	}
        
        addXForwardedForHeader(conProp, requestHeader);
    }

    private static String domain(final String host) {
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

    /**
     * creates a new HttpClient and sets parameters according to proxy needs
     * 
     * @param requestHeader
     * @param connectHost may be 'host:port' or 'host:port/path'
     * @return
     */
    private static JakartaCommonsHttpClient setupHttpClient(final httpRequestHeader requestHeader, final String connectHost) {
        // setup HTTP-client
        final JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(timeout, requestHeader);
        client.setFollowRedirects(false);
        // cookies are handled by the user's browser
        client.setIgnoreCookies(true);
        client.setProxy(httpRemoteProxyConfig.getProxyConfigForURI(connectHost));
        return client;
    }

    /**
     * determines in which form the response should be send and sets header accordingly
     * if the content length is not set we need to use chunked content encoding 
     * Implemented:
     * if !content-length
     *  switch httpVer
     *    case 0.9:
     *    case 1.0:
     *      close connection after transfer
     *      break;
     *    default:
     *      new ChunkedStream around respond
     * end if
     * 
     * @param conProp
     * @param responseHeader
     * @param statusCode
     * @param respond
     * @return
     */
    private static httpChunkedOutputStream setTransferEncoding(
            final Properties conProp, final httpResponseHeader responseHeader,
            final int statusCode, final OutputStream respond) {
        final String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
        httpChunkedOutputStream chunkedOut = null;
        // gzipped response is ungzipped an therefor the length is unknown
        if (responseHeader.gzip() || responseHeader.getContentLength() < 0) {
            // according to http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
            // a 204,304 message must not contain a message body.
            // Therefore we need to set the content-length to 0.
            if (statusCode == 204 || statusCode == 304) {
                responseHeader.put(httpHeader.CONTENT_LENGTH, "0");
            } else {
                if (httpVer.equals(httpHeader.HTTP_VERSION_0_9) || httpVer.equals(httpHeader.HTTP_VERSION_1_0)) {
                    forceConnectionClose(conProp);
                } else {
                    chunkedOut = new httpChunkedOutputStream(respond);
                }
                responseHeader.remove(httpHeader.CONTENT_LENGTH);
            }
        }
        return chunkedOut;
    }

    /**
     * @param res
     * @param responseHeader
     */
    private static void prepareResponseHeader(final httpResponseHeader responseHeader, final String httpVer) {
        modifyProxyHeaders(responseHeader, httpVer);
        
        correctContentEncoding(responseHeader);
    }

    /**
     * @param responseHeader
     */
    private static void correctContentEncoding(final httpResponseHeader responseHeader) {
        // TODO gzip again? set "correct" encoding?
        if(responseHeader.gzip()) {
            responseHeader.remove(httpResponseHeader.CONTENT_ENCODING);
            responseHeader.remove(httpHeader.CONTENT_LENGTH); // remove gziped length
        }
    }

    /**
     * adds the client-IP of conProp to the requestHeader
     * 
     * @param conProp
     * @param requestHeader
     */
    private static void addXForwardedForHeader(final Properties conProp, final httpRequestHeader requestHeader) {
        // setting the X-Forwarded-For Header
        if (switchboard.getConfigBool("proxy.sendXForwardedForHeader", true)) {
            requestHeader.put(httpHeader.X_FORWARDED_FOR, conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP));
        }
    }

    /**
     * removing hop by hop headers and adding additional headers
     * 
     * @param requestHeader
     * @param httpVer
     */
    private static void modifyProxyHeaders(final httpHeader requestHeader, final String httpVer) {
        removeHopByHopHeaders(requestHeader);
        setViaHeader(requestHeader, httpVer);
    }

    private static void removeHopByHopHeaders(final httpHeader headers) {
        /*
         - Trailers
         */
        
        headers.remove(httpRequestHeader.CONNECTION);
        headers.remove(httpRequestHeader.KEEP_ALIVE);
        headers.remove(httpRequestHeader.UPGRADE);
        headers.remove(httpRequestHeader.TE);
        headers.remove(httpRequestHeader.PROXY_CONNECTION);
        headers.remove(httpRequestHeader.PROXY_AUTHENTICATE);
        headers.remove(httpRequestHeader.PROXY_AUTHORIZATION);
        
        // special headers inserted by squid
        headers.remove(httpRequestHeader.X_CACHE);
        headers.remove(httpRequestHeader.X_CACHE_LOOKUP);     
        
        // remove transfer encoding header
        headers.remove(httpResponseHeader.TRANSFER_ENCODING);
        
        //removing yacy status headers
        headers.remove(httpResponseHeader.X_YACY_KEEP_ALIVE_REQUEST_COUNT);
        headers.remove(httpResponseHeader.X_YACY_ORIGINAL_REQUEST_LINE);
    }

    private static void setViaHeader(final httpHeader header, final String httpVer) {
        if (!switchboard.getConfigBool("proxy.sendViaHeader", true)) return;
        final String myAddress = (httpd.getAlternativeResolver() == null) ? null : httpd.getAlternativeResolver().myAlternativeAddress();
        if (myAddress != null) {
    
            // getting header set by other proxies in the chain
            final StringBuffer viaValue = new StringBuffer();
            if (header.containsKey(httpHeader.VIA)) viaValue.append(header.get(httpHeader.VIA));
            if (viaValue.length() > 0) viaValue.append(", ");
              
            // appending info about this peer
            viaValue
            .append(httpVer).append(" ")
            .append(myAddress).append(" ")
            .append("(YaCy ").append(switchboard.getConfig("vString", "0.0")).append(")");
            
            // storing header back
            header.put(httpHeader.VIA, new String(viaValue));
        }
    }

    public static void doConnect(final Properties conProp, final httpRequestHeader requestHeader, final InputStream clientIn, final OutputStream clientOut) throws IOException {
        
        switchboard.proxyLastAccess = System.currentTimeMillis();
    
        String host = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
        final String httpVersion = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
        String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
        final String args = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);
        if (args != null) { path = path + "?" + args; }
    
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
        final String hostlow = host.toLowerCase();
        if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_PROXY, hostlow, path)) {
            httpd.sendRespondError(conProp,clientOut,4,403,null,
                    "URL '" + hostlow + "' blocked by yacy proxy (blacklisted)",null);
            theLogger.logInfo("AGIS blocking of host '" + hostlow + "'");
            forceConnectionClose(conProp);
            return;
        }
    
        // possibly branch into PROXY-PROXY connection
        final httpRemoteProxyConfig proxyConfig = httpRemoteProxyConfig.getRemoteProxyConfig();
        if (
                (proxyConfig != null) &&
                (proxyConfig.useProxy()) &&
                (proxyConfig.useProxy4SSL())
        ) {
            final JakartaCommonsHttpClient remoteProxy = new JakartaCommonsHttpClient(timeout, requestHeader, proxyConfig);
            remoteProxy.setFollowRedirects(false); // should not be needed, but safe is safe 
    
            JakartaCommonsHttpResponse response = null;
            try {
                response = remoteProxy.CONNECT(host, port);
                // outputs a logline to the serverlog with the current status
                theLogger.logInfo("CONNECT-RESPONSE: status=" + response.getStatusLine() + ", header=" + response.getResponseHeader().toString());
                // (response.getStatusLine().charAt(0) == '2') || (response.getStatusLine().charAt(0) == '3')
                final boolean success = response.getStatusCode() >= 200 && response.getStatusCode() <= 399;
                if (success) {
                    // replace connection details
                    host = proxyConfig.getProxyHost();
                    port = proxyConfig.getProxyPort();
                    // go on (see below)
                } else {
                    // pass error response back to client
                    httpd.sendRespondHeader(conProp,clientOut,httpVersion,response.getStatusCode(),response.getStatusLine().substring(4),response.getResponseHeader());
                    //respondHeader(clientOut, response.status, response.responseHeader);
                    forceConnectionClose(conProp);
                    return;
                }
            } catch (final Exception e) {
                throw new IOException(e.getMessage());
            } finally {
                if(response != null) {
                    // release connection
                    response.closeStream();
                }
            }
        }
    
        // try to establish connection to remote host
        final Socket sslSocket = new Socket(host, port);
        sslSocket.setSoTimeout(timeout); // waiting time for write
        sslSocket.setSoLinger(true, timeout); // waiting time for read
        final InputStream promiscuousIn  = sslSocket.getInputStream();
        final OutputStream promiscuousOut = sslSocket.getOutputStream();
        
        // now then we can return a success message
        clientOut.write((httpVersion + " 200 Connection established" + serverCore.CRLF_STRING +
                "Proxy-agent: YACY" + serverCore.CRLF_STRING +
                serverCore.CRLF_STRING).getBytes());
        
        theLogger.logInfo("SSL connection to " + host + ":" + port + " established.");
        
        // start stream passing with mediate processes
        final Mediate cs = new Mediate(sslSocket, clientIn, promiscuousOut);
        final Mediate sc = new Mediate(sslSocket, promiscuousIn, clientOut);
        cs.start();
        sc.start();
        while ((sslSocket != null) &&
               (sslSocket.isBound()) &&
               (!(sslSocket.isClosed())) &&
               (sslSocket.isConnected()) &&
               ((cs.isAlive()) || (sc.isAlive()))) {
            // idle
            try {Thread.sleep(1000);} catch (final InterruptedException e) {} // wait a while
        }
        // set stop mode
        cs.pleaseTerminate();
        sc.pleaseTerminate();
        // wake up thread
        cs.interrupt();
        sc.interrupt();
        // ...hope they have terminated...
    }

    public static class Mediate extends Thread {
        
        boolean terminate;
        Socket socket;
        InputStream in;
        OutputStream out;
        
        public Mediate(final Socket socket, final InputStream in, final OutputStream out) {
            this.terminate = false;
            this.in = in;
            this.out = out;
            this.socket = socket;
        }
        
        public void run() {
            final byte[] buffer = new byte[512];
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
            } catch (final IOException e) {}
        }
        
        public void pleaseTerminate() {
            terminate = true;
        }
    }
    
    private static void handleProxyException(final Exception e, final Properties conProp, final OutputStream respond, final yacyURL url) {
        // this may happen if 
        // - the targeted host does not exist 
        // - anything with the remote server was wrong.
        // - the client unexpectedly closed the connection ...
        try {
            

            // doing some errorhandling ...
            int httpStatusCode = 404; 
            String httpStatusText = null; 
            String errorMessage = null; 
            Exception errorExc = null;
            boolean unknownError = false;
            
            // for customized error messages
            boolean detailedErrorMsg = false;
            String  detailedErrorMsgFile = null;
            serverObjects detailedErrorMsgMap = null;
            
            if (e instanceof ConnectException) {
                httpStatusCode = 403; httpStatusText = "Connection refused"; 
                errorMessage = "Connection refused by destination host";
            } else if (e instanceof BindException) {
                errorMessage = "Unable to establish a connection to the destination host";               
            } else if (e instanceof NoRouteToHostException) {
                errorMessage = "No route to destination host";                    
            } else if (e instanceof UnknownHostException) {
                //errorMessage = "IP address of the destination host could not be determined";
                try {
                    detailedErrorMsgMap = unknownHostHandling(conProp);
                    httpStatusText = "Unknown Host";
                    detailedErrorMsg = true;
                    detailedErrorMsgFile = "proxymsg/unknownHost.inc";                    
                } catch (final Exception e1) {
                    errorMessage = "IP address of the destination host could not be determined";
                }
            } else if (e instanceof SocketTimeoutException) {
                errorMessage = "Unable to establish a connection to the destination host. Connect timed out.";
            } else {
                final String exceptionMsg = e.getMessage();
                if ((exceptionMsg != null) && (exceptionMsg.indexOf("Corrupt GZIP trailer") >= 0)) {
                    // just do nothing, we leave it this way
                    if (theLogger.isFine()) theLogger.logFine("ignoring bad gzip trail for URL " + url + " (" + e.getMessage() + ")");
                    forceConnectionClose(conProp);
                } else if ((exceptionMsg != null) && (exceptionMsg.indexOf("Connection reset")>= 0)) {
                    errorMessage = "Connection reset";
                } else if ((exceptionMsg != null) && (exceptionMsg.indexOf("unknown host")>=0)) {
                    try {
                        detailedErrorMsgMap = unknownHostHandling(conProp);
                        httpStatusText = "Unknown Host";
                        detailedErrorMsg = true;
                        detailedErrorMsgFile = "proxymsg/unknownHost.inc";
                    } catch (final Exception e1) {
                        errorMessage = "IP address of the destination host could not be determined";
                    }
                } else if ((exceptionMsg != null) && 
                  (
                     (exceptionMsg.indexOf("socket write error")>=0) ||
                     (exceptionMsg.indexOf("Read timed out") >= 0) || 
                     (exceptionMsg.indexOf("Broken pipe") >= 0) ||
                     (exceptionMsg.indexOf("server has closed connection") >= 0)
                  )) { 
                    errorMessage = exceptionMsg;
                    e.printStackTrace();
                } else {
                    errorMessage = "Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage();
                    unknownError = true;
                    errorExc = e;
                }
            }
            
            // sending back an error message to the client
            if (!conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                if (detailedErrorMsg) {
                    httpd.sendRespondError(conProp,respond, httpStatusCode, httpStatusText, new File(detailedErrorMsgFile), detailedErrorMsgMap, errorExc);
                } else {
                    httpd.sendRespondError(conProp,respond,4,httpStatusCode,httpStatusText,errorMessage,errorExc);
                }
            } else {
                if (unknownError) {
                    theLogger.logSevere("Unknown Error while processing request '" + 
                            conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE,"unknown") + "':" +
                            "\n" + Thread.currentThread().getName() + 
                            "\n" + errorMessage,e);
                } else {
                    theLogger.logWarning("Error while processing request '" + 
                            conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE,"unknown") + "':" +
                            "\n" + Thread.currentThread().getName() + 
                            "\n" + errorMessage);                        
                }
                forceConnectionClose(conProp);
            }                
        } catch (final Exception ee) {
            forceConnectionClose(conProp);
        }
        
    }
    
    private static void forceConnectionClose(final Properties conProp) {
        if (conProp != null) {
            conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");            
        }
    }

    private static serverObjects unknownHostHandling(final Properties conProp) throws Exception {
        final serverObjects detailedErrorMsgMap = new serverObjects();
        
        // generic toplevel domains        
        final HashSet<String> topLevelDomains = new HashSet<String>(Arrays.asList(new String[]{
                "aero", // Fluggesellschaften/Luftfahrt
                "arpa", // Einrichtung des ARPANet
                "biz",  // Business
                "com",  // Commercial
                "coop", // genossenschaftliche Unternehmen
                "edu",  // Education
                "gov",  // Government
                "info", // Informationsangebote
                "int",  // International
                "jobs", // Jobangebote von Unternemen
                "mil",  // Military (US-Militaer)
                // "museum", // Museen
                "name",   // Privatpersonen
                "nato",   // NATO (veraltet)
                "net",    // Net (Netzwerkbetreiber)
                "org",    // Organization (Nichtkommerzielle Organisation)
                "pro",    // Professionals
                "travel",  // Touristikindustrie
                
                // some country tlds
                "de",
                "at",
                "ch",
                "it",
                "uk"
        }));
        
        // getting some connection properties
        String orgHostPort = "80";
        String orgHostName = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST,"unknown").toLowerCase();
        int pos = orgHostName.indexOf(":");
        if (pos != -1) {
            orgHostPort = orgHostName.substring(pos+1);
            orgHostName = orgHostName.substring(0,pos);                        
        }                  
        final String orgHostPath = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH,"");
        String orgHostArgs = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS,"");
        if (orgHostArgs.length() > 0) orgHostArgs = "?" + orgHostArgs;
        detailedErrorMsgMap.put("hostName", orgHostName);
        
        // guessing hostnames
        final HashSet<String> testHostNames = new HashSet<String>();
        String testHostName = null;
        if (!orgHostName.startsWith("www.")) {
            testHostName = "www." + orgHostName;
            final InetAddress addr = serverDomains.dnsResolve(testHostName);
            if (addr != null) testHostNames.add(testHostName);
        } else if (orgHostName.startsWith("www.")) {
            testHostName = orgHostName.substring(4);
            final InetAddress addr = serverDomains.dnsResolve(testHostName);
            if (addr != null) if (addr != null) testHostNames.add(testHostName);                      
        } 
        if (orgHostName.length()>4 && orgHostName.startsWith("www") && (orgHostName.charAt(3) != '.')) {
            testHostName = orgHostName.substring(0,3) + "." + orgHostName.substring(3);
            final InetAddress addr = serverDomains.dnsResolve(testHostName);
            if (addr != null) if (addr != null) testHostNames.add(testHostName);                             
        }
        
        pos = orgHostName.lastIndexOf(".");
        if (pos != -1) {
            final Iterator<String> iter = topLevelDomains.iterator();
            while (iter.hasNext()) {
                final String topLevelDomain = iter.next();
                testHostName = orgHostName.substring(0,pos) + "." + topLevelDomain;
                final InetAddress addr = serverDomains.dnsResolve(testHostName);
                if (addr != null) if (addr != null) testHostNames.add(testHostName);                        
            }
        }
        
        int hostNameCount = 0;
        final Iterator<String> iter = testHostNames.iterator();
        while (iter.hasNext()) {
            testHostName = iter.next();
            detailedErrorMsgMap.put("list_" + hostNameCount + "_hostName",testHostName);
            detailedErrorMsgMap.put("list_" + hostNameCount + "_hostPort",orgHostPort);
            detailedErrorMsgMap.put("list_" + hostNameCount + "_hostPath",orgHostPath);
            detailedErrorMsgMap.put("list_" + hostNameCount + "_hostArgs",orgHostArgs);
            hostNameCount++;     
        }
        
        detailedErrorMsgMap.put("list", hostNameCount);
        
        if (hostNameCount != 0) {
            detailedErrorMsgMap.put("showList", 1);
        } else {
            detailedErrorMsgMap.put("showList", 0);
        }        
        
        return detailedErrorMsgMap;
    }
    
    private static synchronized String generateUserAgent(final httpHeader requestHeaders) {
        userAgentStr.setLength(0);
        
        final String browserUserAgent = (String) requestHeaders.get(httpHeader.USER_AGENT, HTTPLoader.yacyUserAgent);
        final int pos = browserUserAgent.lastIndexOf(')');
        if (pos >= 0) {
            userAgentStr
            .append(browserUserAgent.substring(0,pos))
            .append("; YaCy ")
            .append(switchboard.getConfig("vString","0.1"))
            .append("; yacy.net")
            .append(browserUserAgent.substring(pos));
        } else {
            userAgentStr.append(browserUserAgent);
        }
        
        return new String(userAgentStr);
    }
    
    /**
     * This function is used to generate a logging message according to the 
     * <a href="http://www.squid-cache.org/Doc/FAQ/FAQ-6.html">squid logging format</a>.<p>
     * e.g.<br>
     * <code>1117528623.857    178 192.168.1.201 TCP_MISS/200 1069 GET http://www.yacy.de/ - DIRECT/81.169.145.74 text/html</code>
     */
    private final static synchronized void logProxyAccess(final Properties conProp) {
        
        if (!doAccessLogging) return;
        
        logMessage.setLength(0);
        
        // Timestamp
        final String currentTimestamp = Long.toString(System.currentTimeMillis());
        final int offset = currentTimestamp.length()-3;
        
        logMessage.append(currentTimestamp.substring(0,offset));
        logMessage.append('.');
        logMessage.append(currentTimestamp.substring(offset));          
        logMessage.append(' ');        
        
        // Elapsed time
        final Long requestStart = (Long) conProp.get(httpHeader.CONNECTION_PROP_REQUEST_START);
        final Long requestEnd =   (Long) conProp.get(httpHeader.CONNECTION_PROP_REQUEST_END);
        final String elapsed = Long.toString(requestEnd.longValue()-requestStart.longValue());
        
        for (int i=0; i<6-elapsed.length(); i++) logMessage.append(' ');
        logMessage.append(elapsed);
        logMessage.append(' ');
        
        // Remote Host
        final String clientIP = conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP);
        logMessage.append(clientIP);
        logMessage.append(' ');
        
        // Code/Status
        final String respondStatus = conProp.getProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_STATUS);
        final String respondCode = conProp.getProperty(httpHeader.CONNECTION_PROP_PROXY_RESPOND_CODE,"UNKNOWN");        
        logMessage.append(respondCode);
        logMessage.append("/");
        logMessage.append(respondStatus);
        logMessage.append(' ');
        
        // Bytes
        final Long bytes = (Long) conProp.get(httpHeader.CONNECTION_PROP_PROXY_RESPOND_SIZE);
        logMessage.append(bytes.toString());
        logMessage.append(' ');        
        
        // Method
        final String requestMethod = conProp.getProperty(httpHeader.CONNECTION_PROP_METHOD); 
        logMessage.append(requestMethod);
        logMessage.append(' ');  
        
        // URL
        final String requestURL = conProp.getProperty(httpHeader.CONNECTION_PROP_URL);
        final String requestArgs = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);
        logMessage.append(requestURL);
        if (requestArgs != null) {
            logMessage.append("?")
                           .append(requestArgs);
        }
        logMessage.append(' ');          
        
        // Rfc931
        logMessage.append("-");
        logMessage.append(' ');
        
        //  Peerstatus/Peerhost
        final String host = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
        logMessage.append("DIRECT/");
        logMessage.append(host);    
        logMessage.append(' ');
        
        // Type
        String mime = "-";
        if (conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
            final httpHeader proxyRespondHeader = (httpHeader) conProp.get(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER);
            mime = proxyRespondHeader.mime();
            if (mime.indexOf(";") != -1) {
                mime = mime.substring(0,mime.indexOf(";"));
            }
        }
        logMessage.append(mime);        
        
        // sending the logging message to the logger
        if (proxyLog.isFine()) proxyLog.logFine(logMessage.toString());
    }
    
}

/*
 proxy test:
 
 http://www.chipchapin.com/WebTools/cookietest.php?
 http://xlists.aza.org/moderator/cookietest/cookietest1.php
 http://vancouver-webpages.com/proxy/cache-test.html
 
 */
