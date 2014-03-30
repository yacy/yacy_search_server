// HTTPDProxyHandler.java
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


package net.yacy.server.http;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.TextParser;
import net.yacy.document.parser.html.ContentTransformer;
import net.yacy.document.parser.html.Transformer;
import net.yacy.kelondro.io.ByteCountOutputStream;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.peers.operation.yacyBuildProperties;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;

public final class HTTPDProxyHandler {


    private static final String yacyProxyUserAgent = "yacyproxy (" + ClientIdentification.yacySystem +") http://yacy.net/bot.html";

    // static variables
    // can only be instantiated upon first instantiation of this class object
    private static Switchboard sb = null;
    private static final HashSet<String> yellowList;
    private static int timeout = 60000;
    private static Process redirectorProcess = null;
    private static boolean redirectorEnabled = false;
    private static PrintWriter redirectorWriter = null;
    private static BufferedReader redirectorReader = null;

    private static Transformer transformer = null;

    //private Properties connectionProperties = null;
    // creating a logger
    private static final ConcurrentLog log = new ConcurrentLog("PROXY");

	/**
     * Do logging configuration for special proxy access log file
     */
    static {

        // get a switchboard
        sb = Switchboard.getSwitchboard();
        if (sb != null) {

        // set timeout
        timeout = sb.getConfigInt("proxy.clientTimeout", 60000);

        // do logger initialization
        try {
            log.info("Configuring proxy access logging ...");

            // getting the logging manager
            final LogManager manager = LogManager.getLogManager();
            final String className = HTTPDProxyHandler.class.getName();

            // determining if proxy access logging is enabled
            final String enabled = manager.getProperty(className + ".logging.enabled");
            if ("true".equalsIgnoreCase(enabled)) {

                // reading out some needed configuration properties
                int limit = 1024*1024, count = 20;
                String pattern = manager.getProperty(className + ".logging.FileHandler.pattern");
                if (pattern == null) pattern = "DATA/LOG/proxyAccess%u%g.log";
                // make pattern absolute
                if (!new File(pattern).isAbsolute()) pattern = new File(sb.getDataPath(), pattern).getAbsolutePath();

                final String limitStr = manager.getProperty(className + ".logging.FileHandler.limit");
                if (limitStr != null) try { limit = Integer.parseInt(limitStr); } catch (final NumberFormatException e) {}

                final String countStr = manager.getProperty(className + ".logging.FileHandler.count");
                if (countStr != null) try { count = Integer.parseInt(countStr); } catch (final NumberFormatException e) {}

                // creating the proxy access logger
                final Logger proxyLogger = Logger.getLogger("PROXY.access");
                proxyLogger.setUseParentHandlers(false);
                proxyLogger.setLevel(Level.FINEST);

                final FileHandler txtLog = new FileHandler(pattern, limit, count, true);
                txtLog.setFormatter(new ProxyLogFormatter());
                txtLog.setLevel(Level.FINEST);
                proxyLogger.addHandler(txtLog);

                log.info("Proxy access logging configuration done." +
                                  "\n\tFilename: " + pattern +
                                  "\n\tLimit: " + limitStr +
                                  "\n\tCount: " + countStr);
            } else {
                log.info("Proxy access logging is deactivated.");
            }
        } catch (final Exception e) {
            log.severe("Unable to configure proxy access logging.",e);
        }

        // load a transformer
        transformer = new ContentTransformer();
        transformer.init(new File(sb.getAppPath(), sb.getConfig(SwitchboardConstants.LIST_BLUE, "")).toString());

        // load the yellow-list
        final String f = sb.getConfig("proxyYellowList", null);
        if (f != null) {
            yellowList = FileUtils.loadList(new File(f));
            log.config("loaded yellow-list from file " + f + ", " + yellowList.size() + " entries");
        } else {
            yellowList = new HashSet<String>();
        }

        final String redirectorPath = sb.getConfig("externalRedirector", "");
        if (redirectorPath.length() > 0 && !redirectorEnabled) {
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
    public static final ConcurrentLog proxyLog = new ConcurrentLog("PROXY.access");

    /**
     * Reusable {@link StringBuilder} for logging
     */
    private static final StringBuilder logMessage = new StringBuilder();

    /**
     * Reusable {@link StringBuilder} to generate the useragent string
     */
    private static final StringBuilder userAgentStr = new StringBuilder();

    private static void handleOutgoingCookies(final RequestHeader requestHeader, final String targethost, final String clienthost) {
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
        if (sb.getConfigBool("proxy.monitorCookies", false)) {
            if (requestHeader.containsKey(RequestHeader.COOKIE)) {
                final Object[] entry = new Object[]{new Date(), clienthost, requestHeader.getMultiple(RequestHeader.COOKIE)};
                synchronized(sb.outgoingCookies) {
                    sb.outgoingCookies.put(targethost, entry);
                }
            }
        }
    }

    private static void handleIncomingCookies(final ResponseHeader respondHeader, final String serverhost, final String targetclient) {
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
        if (sb.getConfigBool("proxy.monitorCookies", false)) {
            if (respondHeader.containsKey(HeaderFramework.SET_COOKIE)) {
                final Object[] entry = new Object[]{new Date(), targetclient, respondHeader.getMultiple(HeaderFramework.SET_COOKIE)};
                synchronized(sb.incomingCookies) {
                    sb.incomingCookies.put(serverhost, entry);
                }
            }
        }
    }

    /**
     * @param conProp a collection of properties about the connection, like URL
     * @param requestHeader The header lines of the connection from the request
     * @param respond the OutputStream to the client
     * @see de.anomic.http.httpdHandler#doGet(java.util.Properties, net.yacy.cora.protocol.HeaderFramework, java.io.OutputStream)
     */
    public static void doGet(final HashMap<String, Object> conProp, final RequestHeader requestHeader, final OutputStream respond, final ClientIdentification.Agent agent) {
        ByteCountOutputStream countedRespond = null;
        try {
            final int reqID = requestHeader.hashCode();
            // remembering the starting time of the request
            final Date requestDate = new Date(); // remember the time...
            conProp.put(HeaderFramework.CONNECTION_PROP_REQUEST_START, Long.valueOf(requestDate.getTime()));
            sb.proxyLastAccess = System.currentTimeMillis();

            // using an ByteCount OutputStream to count the send bytes (needed for the logfile)
            countedRespond = new ByteCountOutputStream(respond, "PROXY");

            final String ip   = (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer

            DigestURL url = null;
            try {
                url = HeaderFramework.getRequestURL(conProp);
                if (log.isFine()) log.fine(reqID +" GET "+ url);
                if (log.isFinest()) log.finest(reqID +"    header: "+ requestHeader);

                //redirector
                if (redirectorEnabled){
                    synchronized(redirectorProcess){
                        redirectorWriter.println(url.toNormalform(true));
                        redirectorWriter.flush();
                    }
                    final String newUrl = redirectorReader.readLine();
                    if (!newUrl.equals("")) {
                        try {
                            url = new DigestURL(newUrl);
                        } catch(final MalformedURLException e){}//just keep the old one
                    }
                    if (log.isFinest()) log.finest(reqID +"    using redirector to "+ url);
                    conProp.put(HeaderFramework.CONNECTION_PROP_HOST, url.getHost()+":"+url.getPort());
                    conProp.put(HeaderFramework.CONNECTION_PROP_PATH, url.getPath());
                    requestHeader.put(HeaderFramework.HOST, url.getHost()+":"+url.getPort());
                    requestHeader.put(HeaderFramework.CONNECTION_PROP_PATH, url.getPath());
                }
            } catch (final MalformedURLException e) {
                // get header info for error logging
                final String host = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HOST);
                final String path = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PATH); // always starts with leading '/'
                final String args = (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS); // may be null if no args were given
                final String errorMsg = "ERROR: internal error with url generation: host=" +
                                  host + ", path=" + path + ", args=" + args;
                log.severe(errorMsg);
                HTTPDemon.sendRespondError(conProp,countedRespond,4,501,null,errorMsg,e);
                return;
            }

            // check the blacklist
            // blacklist idea inspired by [AS]:
            // respond a 404 for all AGIS ("all you get is shit") servers
            if (Switchboard.urlBlacklist.isListed(BlacklistType.PROXY, url)) {
                log.info("AGIS blocking of host '" + url.getHost() + "'");
                HTTPDemon.sendRespondError(conProp,countedRespond,4,403,null,
                        "URL '" + url.getHost() + "' blocked by yacy proxy (blacklisted)",null);
                return;
            }

            // handle outgoing cookies
            handleOutgoingCookies(requestHeader, url.getHost(), ip);
            prepareRequestHeader(conProp, requestHeader, url.getHost().toLowerCase());
            final ResponseHeader cachedResponseHeader = Cache.getResponseHeader(url.hash());

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
                if (log.isFinest()) log.finest(reqID + " page not in cache: fulfill request from web");
                    fulfillRequestFromWeb(conProp, url, requestHeader, cachedResponseHeader, countedRespond, agent);
            } else {
            	final Request request = new Request(
            			null,
                        url,
                        requestHeader.referer() == null ? null : requestHeader.referer().hash(),
                        "",
                        cachedResponseHeader.lastModified(),
                        sb.crawler.defaultProxyProfile.handle(),
                        0,
                        0,
                        0);
                final Response response = new Response(
                		request,
                        requestHeader,
                        cachedResponseHeader,
                        sb.crawler.defaultProxyProfile,
                        true
                );
                final byte[] cacheContent = Cache.getContent(url.hash());
                if (cacheContent != null && response.isFreshForProxy()) {
                    if (log.isFinest()) log.finest(reqID + " fulfill request from cache");
                    fulfillRequestFromCache(conProp, url, requestHeader, cachedResponseHeader, cacheContent, countedRespond);
                } else {
                    if (log.isFinest()) log.finest(reqID + " fulfill request from web");
                    fulfillRequestFromWeb(conProp, url, requestHeader, cachedResponseHeader, countedRespond, agent);
                }
            }


        } catch (final Exception e) {
            try {
                final String exTxt = e.getMessage();
                if ((exTxt!=null)&&(exTxt.startsWith("Socket closed"))) {
                    forceConnectionClose(conProp);
                } else if (!conProp.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                    final String errorMsg = "Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage();
                    HTTPDemon.sendRespondError(conProp,countedRespond,4,501,null,errorMsg,e);
                    log.severe(errorMsg);
                } else {
                    forceConnectionClose(conProp);
                }
            } catch (final Exception ee) {
                forceConnectionClose(conProp);
            }
        } finally {
            try { if(countedRespond != null) countedRespond.flush(); else if(respond != null) respond.flush(); } catch (final Exception e) {}
            if (countedRespond != null) countedRespond.finish();

            conProp.put(HeaderFramework.CONNECTION_PROP_REQUEST_END, Long.valueOf(System.currentTimeMillis()));
            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE,(countedRespond != null) ? Long.toString(countedRespond.getCount()) : -1L);
            logProxyAccess(conProp);
        }
    }

    private static void fulfillRequestFromWeb(final HashMap<String, Object> conProp, final DigestURL url, final RequestHeader requestHeader, final ResponseHeader cachedResponseHeader, final OutputStream respond, final ClientIdentification.Agent agent) {
        try {
            final int reqID = requestHeader.hashCode();

            String host =    (String) conProp.get(HeaderFramework.CONNECTION_PROP_HOST);
            String path =    (String) conProp.get(HeaderFramework.CONNECTION_PROP_PATH);     // always starts with leading '/'
            final String args =    (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS);     // may be null if no args were given
            final String ip =      (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP); // the ip from the connecting peer
            final String httpVer = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); // the ip from the connecting peer

            int port, pos;
            if ((pos = host.indexOf(':')) < 0) {
                port = 80;
            } else {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }

            // resolve yacy and yacyh domains
            String yAddress = resolveYacyDomains(host);

            // re-calc the url path
            final String remotePath = (args == null) ? path : (path + "?" + args); // with leading '/'

            // remove yacy-subdomain-path, when accessing /env
            if ( (yAddress != null)
            		&& (remotePath.startsWith("/env"))
            		&& ((pos = yAddress.indexOf('/')) != -1)
            ) yAddress = yAddress.substring(0, yAddress.indexOf('/'));

            modifyProxyHeaders(requestHeader, httpVer);

            final String connectHost = hostPart(host, port, yAddress);
            final String getUrl = "http://"+ connectHost + remotePath;

            requestHeader.remove(HeaderFramework.HOST);

            final HTTPClient client = setupHttpClient(requestHeader, agent, connectHost);

            // send request
            try {
            	client.GET(getUrl, false);
                if (log.isFinest()) log.finest(reqID +"    response status: "+ client.getHttpResponse().getStatusLine());
                conProp.put(HeaderFramework.CONNECTION_PROP_CLIENT_REQUEST_HEADER, requestHeader);

                int statusCode = client.getHttpResponse().getStatusLine().getStatusCode();
                final ResponseHeader responseHeader = new ResponseHeader(statusCode, client.getHttpResponse().getAllHeaders());
                // determine if it's an internal error of the httpc
                if (responseHeader.isEmpty()) {
                	throw new Exception(client.getHttpResponse().getStatusLine().toString());
                }

                ChunkedOutputStream chunkedOut = setTransferEncoding(conProp, responseHeader, statusCode, respond);

                // the cache does either not exist or is (supposed to be) stale
                long sizeBeforeDelete = -1;
                if (cachedResponseHeader != null) {
                    // delete the cache
                    final ResponseHeader rh = Cache.getResponseHeader(url.hash());
                    if (rh != null && (sizeBeforeDelete = rh.getContentLength()) == 0) {
                        final byte[] b = Cache.getContent(url.hash());
                        if (b != null) sizeBeforeDelete = b.length;
                    }
                    Cache.delete(url.hash());
                    conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE, "TCP_REFRESH_MISS");
                }

                // reserver cache entry
                final Request request = new Request(
            			null,
                        url,
                        requestHeader.referer() == null ? null : requestHeader.referer().hash(),
                        "",
                        responseHeader.lastModified(),
                        sb.crawler.defaultProxyProfile.handle(),
                        0,
                        0,
                        0);


                // handle incoming cookies
                handleIncomingCookies(responseHeader, host, ip);

//                prepareResponseHeader(responseHeader, res.getHttpVer());
                prepareResponseHeader(responseHeader, client.getHttpResponse().getProtocolVersion().toString());

                // sending the respond header back to the client
                if (chunkedOut != null) {
                    responseHeader.put(HeaderFramework.TRANSFER_ENCODING, "chunked");
                }

                if (log.isFinest()) log.finest(reqID +"    sending response header: "+ responseHeader);
                HTTPDemon.sendRespondHeader(
                        conProp,
                        respond,
                        httpVer,
                        statusCode,
                        client.getHttpResponse().getStatusLine().toString(), // status text
                        responseHeader);

                if (hasBody(client.getHttpResponse().getStatusLine().getStatusCode())) {

                    OutputStream outStream = chunkedOut != null ? chunkedOut : respond;
                    final Response response = new Response(
                            request,
                            requestHeader,
                            responseHeader,
                            sb.crawler.defaultProxyProfile,
                            true
                    );
                    final String storeError = response.shallStoreCacheForProxy();
                    final boolean storeHTCache = response.profile().storeHTCache();
                    final String supportError = TextParser.supports(response.url(), response.getMimeType());

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
                            ((storeHTCache) || (supportError != null))
                    ) {
                        // we don't write actually into a file, only to RAM, and schedule writing the file.
//                        int l = res.getResponseHeader().size();
                    	final int l = responseHeader.size();
                        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream((l < 32) ? 32 : l);

                        final OutputStream toClientAndMemory = new MultiOutputStream(new OutputStream[] {outStream, byteStream});
//                        FileUtils.copy(res.getDataAsStream(), toClientAndMemory);
                        client.writeTo(toClientAndMemory);
                        // cached bytes
                        byte[] cacheArray;
                        if (byteStream.size() > 0) {
                            cacheArray = byteStream.toByteArray();
                        } else {
                            cacheArray = null;
                        }
                        if (log.isFine()) log.fine(reqID +" writeContent of " + url + " produced cacheArray = " + ((cacheArray == null) ? "null" : ("size=" + cacheArray.length)));

                        if (sizeBeforeDelete == -1) {
                            // totally fresh file
                            response.setContent(cacheArray);
                            try {
                                Cache.store(response.url(), response.getResponseHeader(), cacheArray);
                                sb.toIndexer(response);
                            } catch (final IOException e) {
                                log.warn("cannot write " + response.url() + " to Cache (1): " + e.getMessage(), e);
                            }
                            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE, "TCP_MISS");
                        } else if (cacheArray != null && sizeBeforeDelete == cacheArray.length) {
                            // before we came here we deleted a cache entry
                            cacheArray = null;
                            //cacheManager.push(cacheEntry); // unnecessary update
                            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE, "TCP_REF_FAIL_HIT");
                        } else {
                            // before we came here we deleted a cache entry
                            response.setContent(cacheArray);
                            try {
                                Cache.store(response.url(), response.getResponseHeader(), cacheArray);
                                sb.toIndexer(response);
                            } catch (final IOException e) {
                                log.warn("cannot write " + response.url() + " to Cache (2): " + e.getMessage(), e);
                            }
                            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE, "TCP_REFRESH_MISS");
                        }
                    } else {
                        // no caching
                        if (log.isFine()) log.fine(reqID +" "+ url.toString() + " not cached." +
                                " StoreError=" + ((storeError==null)?"None":storeError) +
                                " StoreHTCache=" + storeHTCache +
                                " SupportError=" + supportError);

//                        FileUtils.copy(res.getDataAsStream(), outStream);
                        client.writeTo(outStream);

                        conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_MISS");
                    }

                    outStream.close();

                    if (chunkedOut != null) {
                        chunkedOut.finish();
                        chunkedOut.flush();
                    }
                } // end hasBody
            } catch(final SocketException se) {
                // if opened ...
//                if(res != null) {
//                    // client cut proxy connection, abort download
//                    res.abort();
//                }
            	client.finish();
                handleProxyException(se,conProp,respond,url);
            } finally {
                // if opened ...
//                if(res != null) {
//                    // ... close connection
//                    res.closeStream();
//                }
            	client.finish();
            }
        } catch (final Exception e) {
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
            final HashMap<String, Object> conProp,
            final DigestURL url,
            final RequestHeader requestHeader,
            final ResponseHeader cachedResponseHeader,
            final byte[] cacheEntry,
            OutputStream respond
    ) throws IOException {

        final String httpVer = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER);

        // we respond on the request by using the cache, the cache is fresh
        try {
            prepareResponseHeader(cachedResponseHeader, httpVer);

            // replace date field in old header by actual date, this is according to RFC
            cachedResponseHeader.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(new Date()));

            // check if we can send a 304 instead the complete content
            if (requestHeader.containsKey(RequestHeader.IF_MODIFIED_SINCE)) {
                // conditional request: freshness of cache for that condition was already
                // checked within shallUseCache(). Now send only a 304 response
                log.info("CACHE HIT/304 " + url.toString());
                conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_REFRESH_HIT");

                // setting the content length header to 0
                cachedResponseHeader.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(0));

                // send cached header with replaced date and added length
                HTTPDemon.sendRespondHeader(conProp,respond,httpVer,304,cachedResponseHeader);
                //respondHeader(respond, "304 OK", cachedResponseHeader); // respond with 'not modified'
            } else {
                // unconditional request: send content of cache
                log.info("CACHE HIT/203 " + url.toString());
                conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE,"TCP_HIT");

                // setting the content header to the proper length
                cachedResponseHeader.put(HeaderFramework.CONTENT_LENGTH, Long.toString(cacheEntry.length));

                // send cached header with replaced date and added length
                HTTPDemon.sendRespondHeader(conProp,respond,httpVer,203,cachedResponseHeader);
                //respondHeader(respond, "203 OK", cachedResponseHeader); // respond with 'non-authoritative'

                // send also the complete body now from the cache
                // simply read the file and transfer to out socket
                FileUtils.copy(cacheEntry, respond);
            }
            // that's it!
        } catch (final Exception e) {
            // this happens if the client stops loading the file
            // we do nothing here
            if (conProp.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                log.warn("Error while trying to send cached message body.");
                conProp.put(HeaderFramework.CONNECTION_PROP_PERSISTENT,"close");
            } else {
                HTTPDemon.sendRespondError(conProp,respond,4,503,"socket error: " + e.getMessage(),"socket error: " + e.getMessage(), e);
            }
        } finally {
            try { respond.flush(); respond.close(); } catch (final Exception e) {}
        }
    }

    /**
     * resolve yacy and yacyh domains
     *
     * @param host
     * @return
     */
    private static String resolveYacyDomains(final String host) {
        return (sb.peers == null) ? null : sb.peers.resolve(host);
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
    private static void prepareRequestHeader(final HashMap<String, Object> conProp, final RequestHeader requestHeader, final String hostlow) {
        // set another userAgent, if not yellow-listed
        if ((yellowList != null) && (!(yellowList.contains(domain(hostlow))))) {
            // change the User-Agent
            requestHeader.put(HeaderFramework.USER_AGENT, generateUserAgent(requestHeader));
        }

	// only gzip-encoding is supported, remove other encodings (e. g. deflate)
        if ((requestHeader.get(HeaderFramework.ACCEPT_ENCODING,"")).indexOf("gzip",0) != -1) {
            requestHeader.put(HeaderFramework.ACCEPT_ENCODING, "gzip");
	} else {
            requestHeader.put(HeaderFramework.ACCEPT_ENCODING, "");
	}

        addXForwardedForHeader(conProp, requestHeader);
    }

    private static String domain(final String host) {
        String domain = host;
        int pos = domain.lastIndexOf('.');
        if (pos >= 0) {
            // truncate from last part
            domain = domain.substring(0, pos);
            pos = domain.lastIndexOf('.');
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
    private static HTTPClient setupHttpClient(final RequestHeader requestHeader, final ClientIdentification.Agent agent, final String connectHost) {
        // setup HTTP-client
    	final HTTPClient client = new HTTPClient(agent, timeout);
    	client.setHeader(requestHeader.entrySet());
    	client.setRedirecting(false);
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
    private static ChunkedOutputStream setTransferEncoding(
            final HashMap<String, Object> conProp, final ResponseHeader responseHeader,
            final int statusCode, final OutputStream respond) {
        final String httpVer = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER);
        ChunkedOutputStream chunkedOut = null;
        // gzipped response is ungzipped an therefor the length is unknown
        if (responseHeader.gzip() || responseHeader.getContentLength() < 0) {
            // according to http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
            // a 204,304 message must not contain a message body.
            // Therefore we need to set the content-length to 0.
            if (statusCode == 204 || statusCode == 304) {
                responseHeader.put(HeaderFramework.CONTENT_LENGTH, "0");
            } else {
                if (httpVer.equals(HeaderFramework.HTTP_VERSION_0_9) || httpVer.equals(HeaderFramework.HTTP_VERSION_1_0)) {
                    forceConnectionClose(conProp);
                } else {
                    chunkedOut = new ChunkedOutputStream(respond);
                }
                responseHeader.remove(HeaderFramework.CONTENT_LENGTH);
            }
        }
        return chunkedOut;
    }

    /**
     * @param res
     * @param responseHeader
     */
    private static void prepareResponseHeader(final ResponseHeader responseHeader, final String httpVer) {
        modifyProxyHeaders(responseHeader, httpVer);
        correctContentEncoding(responseHeader);
    }

    /**
     * @param responseHeader
     */
    private static void correctContentEncoding(final ResponseHeader responseHeader) {
        // TODO gzip again? set "correct" encoding?
        if(responseHeader.gzip()) {
            responseHeader.remove(HeaderFramework.CONTENT_ENCODING);
            responseHeader.remove(HeaderFramework.CONTENT_LENGTH); // remove gziped length
        }
    }

    /**
     * adds the client-IP of conProp to the requestHeader
     *
     * @param conProp
     * @param requestHeader
     */
    private static void addXForwardedForHeader(final HashMap<String, Object> conProp, final RequestHeader requestHeader) {
        // setting the X-Forwarded-For Header
        if (sb.getConfigBool("proxy.sendXForwardedForHeader", true)) {
            requestHeader.put(HeaderFramework.X_FORWARDED_FOR, (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP));
        }
    }

    /**
     * removing hop by hop headers and adding additional headers
     *
     * @param requestHeader
     * @param httpVer
     */
    public static void modifyProxyHeaders(final HeaderFramework requestHeader, final String httpVer) {
        removeHopByHopHeaders(requestHeader);
        setViaHeader(requestHeader, httpVer);
    }

    private static void removeHopByHopHeaders(final HeaderFramework headers) {
        /*
         - Trailers
         */

        headers.remove(RequestHeader.CONNECTION);
        headers.remove(RequestHeader.KEEP_ALIVE);
        headers.remove(RequestHeader.UPGRADE);
        headers.remove(RequestHeader.TE);
        headers.remove(RequestHeader.PROXY_CONNECTION);
        headers.remove(RequestHeader.PROXY_AUTHENTICATE);
        headers.remove(RequestHeader.PROXY_AUTHORIZATION);

        // special headers inserted by squid
        headers.remove(RequestHeader.X_CACHE);
        headers.remove(RequestHeader.X_CACHE_LOOKUP);

        // remove transfer encoding header
        headers.remove(HeaderFramework.TRANSFER_ENCODING);

        //removing yacy status headers
        headers.remove(HeaderFramework.X_YACY_KEEP_ALIVE_REQUEST_COUNT);
        headers.remove(HeaderFramework.X_YACY_ORIGINAL_REQUEST_LINE);
    }

    private static void setViaHeader(final HeaderFramework header, final String httpVer) {
        if (!sb.getConfigBool("proxy.sendViaHeader", true)) return;
        final String myAddress = (sb.peers == null) ? null : sb.peers.myAlternativeAddress();
        if (myAddress != null) {

            // getting header set by other proxies in the chain
            final StringBuilder viaValue = new StringBuilder(80);
            if (header.containsKey(HeaderFramework.VIA)) viaValue.append(header.get(HeaderFramework.VIA));
            if (viaValue.length() > 0) viaValue.append(", ");

            // appending info about this peer
            viaValue
            .append(httpVer).append(" ")
            .append(myAddress).append(" ")
            .append("(YaCy ").append(yacyBuildProperties.getVersion()).append(")");

            // storing header back
            header.put(HeaderFramework.VIA, viaValue.toString());
        }
    }

    private static void handleProxyException(final Exception e, final HashMap<String, Object> conProp, final OutputStream respond, final DigestURL url) {
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
                if ((exceptionMsg != null) && (exceptionMsg.indexOf("Corrupt GZIP trailer",0) >= 0)) {
                    // just do nothing, we leave it this way
                    if (log.isFine()) log.fine("ignoring bad gzip trail for URL " + url + " (" + e.getMessage() + ")");
                    forceConnectionClose(conProp);
                } else if ((exceptionMsg != null) && (exceptionMsg.indexOf("Connection reset",0)>= 0)) {
                    errorMessage = "Connection reset";
                } else if ((exceptionMsg != null) && (exceptionMsg.indexOf("unknown host",0)>=0)) {
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
                     (exceptionMsg.indexOf("socket write error",0)>=0) ||
                     (exceptionMsg.indexOf("Read timed out",0) >= 0) ||
                     (exceptionMsg.indexOf("Broken pipe",0) >= 0) ||
                     (exceptionMsg.indexOf("server has closed connection",0) >= 0)
                  )) {
                    errorMessage = exceptionMsg;
                    ConcurrentLog.logException(e);
                } else {
                    errorMessage = "Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage();
                    unknownError = true;
                    errorExc = e;
                }
            }

            // sending back an error message to the client
            if (!conProp.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
                if (detailedErrorMsg) {
                    HTTPDemon.sendRespondError(conProp,respond, httpStatusCode, httpStatusText, new File(detailedErrorMsgFile), detailedErrorMsgMap, errorExc);
                } else {
                    HTTPDemon.sendRespondError(conProp,respond,4,httpStatusCode,httpStatusText,errorMessage,errorExc);
                }
            } else {
                if (unknownError) {
                    log.severe("Unknown Error while processing request 'PROXY':" +
                            "\n" + Thread.currentThread().getName() +
                            "\n" + errorMessage,e);
                } else {
                    log.warn("Error while processing request 'PROXY':" +
                            "\n" + Thread.currentThread().getName() +
                            "\n" + errorMessage);
                }
                forceConnectionClose(conProp);
            }
        } catch (final Exception ee) {
            forceConnectionClose(conProp);
        }

    }

    private static void forceConnectionClose(final HashMap<String, Object> conProp) {
        if (conProp != null) {
            conProp.put(HeaderFramework.CONNECTION_PROP_PERSISTENT,"close");
        }
    }

    private static serverObjects unknownHostHandling(final HashMap<String, Object> conProp) throws Exception {
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
        String orgHostName = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HOST);
        if (orgHostName == null) orgHostName = "unknown";
        orgHostName = orgHostName.toLowerCase();
        int pos = orgHostName.indexOf(':');
        if (pos != -1) {
            orgHostPort = orgHostName.substring(pos+1);
            orgHostName = orgHostName.substring(0,pos);
        }
        String orgHostPath = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PATH); if (orgHostPath == null) orgHostPath = "";
        String orgHostArgs = (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS); if (orgHostArgs == null) orgHostArgs = "";
        if (orgHostArgs.length() > 0) orgHostArgs = "?" + orgHostArgs;
        detailedErrorMsgMap.put("hostName", orgHostName);

        // guessing hostnames
        final HashSet<String> testHostNames = new HashSet<String>();
        String testHostName = null;
        if (!orgHostName.startsWith("www.")) {
            testHostName = "www." + orgHostName;
            final InetAddress addr = Domains.dnsResolve(testHostName);
            if (addr != null) testHostNames.add(testHostName);
        } else if (orgHostName.startsWith("www.")) {
            testHostName = orgHostName.substring(4);
            final InetAddress addr = Domains.dnsResolve(testHostName);
            if (addr != null) if (addr != null) testHostNames.add(testHostName);
        }
        if (orgHostName.length()>4 && orgHostName.startsWith("www") && (orgHostName.charAt(3) != '.')) {
            testHostName = orgHostName.substring(0,3) + "." + orgHostName.substring(3);
            final InetAddress addr = Domains.dnsResolve(testHostName);
            if (addr != null) if (addr != null) testHostNames.add(testHostName);
        }

        pos = orgHostName.lastIndexOf('.');
        if (pos != -1) {
            final Iterator<String> iter = topLevelDomains.iterator();
            while (iter.hasNext()) {
                final String topLevelDomain = iter.next();
                testHostName = orgHostName.substring(0,pos) + "." + topLevelDomain;
                final InetAddress addr = Domains.dnsResolve(testHostName);
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

    private static synchronized String generateUserAgent(final HeaderFramework requestHeaders) {
        userAgentStr.setLength(0);

        final String browserUserAgent = requestHeaders.get(HeaderFramework.USER_AGENT, yacyProxyUserAgent);
        final int pos = browserUserAgent.lastIndexOf(')');
        if (pos >= 0) {
            userAgentStr
            .append(browserUserAgent.substring(0,pos))
            .append("; YaCy ")
            .append(yacyBuildProperties.getVersion())
            .append("; yacy.net")
            .append(browserUserAgent.substring(pos));
        } else {
            userAgentStr.append(browserUserAgent);
        }

        return userAgentStr.toString();
    }

    /**
     * This function is used to generate a logging message according to the
     * <a href="http://www.squid-cache.org/Doc/FAQ/FAQ-6.html">squid logging format</a>.<p>
     * e.g.<br>
     * <code>1117528623.857    178 192.168.1.201 TCP_MISS/200 1069 GET http://www.yacy.de/ - DIRECT/81.169.145.74 text/html</code>
     */
    private final static synchronized void logProxyAccess(final HashMap<String, Object> conProp) {

        logMessage.setLength(0);

        // Timestamp
        final String currentTimestamp = Long.toString(System.currentTimeMillis());
        final int offset = currentTimestamp.length()-3;

        logMessage.append(currentTimestamp.substring(0,offset));
        logMessage.append('.');
        logMessage.append(currentTimestamp.substring(offset));
        logMessage.append(' ');

        // Elapsed time
        final Long requestStart = (Long) conProp.get(HeaderFramework.CONNECTION_PROP_REQUEST_START);
        final Long requestEnd =   (Long) conProp.get(HeaderFramework.CONNECTION_PROP_REQUEST_END);
        final String elapsed = Long.toString(requestEnd.longValue()-requestStart.longValue());

        for (int i=0; i<6-elapsed.length(); i++) logMessage.append(' ');
        logMessage.append(elapsed);
        logMessage.append(' ');

        // Remote Host
        final String clientIP = (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP);
        logMessage.append(clientIP);
        logMessage.append(' ');

        // Code/Status
        final String respondStatus = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_STATUS);
        String respondCode = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_CODE);
        if (respondCode == null) respondCode = "UNKNOWN";
        logMessage.append(respondCode);
        logMessage.append("/");
        logMessage.append(respondStatus);
        logMessage.append(' ');

        // Bytes
        final String bytes = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_SIZE);
        logMessage.append(bytes.toString());
        logMessage.append(' ');

        // Method
        final String requestMethod = (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD);
        logMessage.append(requestMethod);
        logMessage.append(' ');

        // URL
        final String requestURL = (String) conProp.get(HeaderFramework.CONNECTION_PROP_URL);
        final String requestArgs = (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS);
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
        final String host = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HOST);
        logMessage.append("DIRECT/");
        logMessage.append(host);
        logMessage.append(' ');

        // Type
        String mime = "-";
        if (conProp.containsKey(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
            final HeaderFramework proxyRespondHeader = (HeaderFramework) conProp.get(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER);
            mime = proxyRespondHeader.mime();
            if (mime.indexOf(';') != -1) {
                mime = mime.substring(0,mime.indexOf(';'));
            }
        }
        logMessage.append(mime);

        // sending the logging message to the logger
        if (proxyLog.isFine()) proxyLog.fine(logMessage.toString());
    }

}

/*
 proxy test:

 http://www.chipchapin.com/WebTools/cookietest.php?
 http://xlists.aza.org/moderator/cookietest/cookietest1.php
 http://vancouver-webpages.com/proxy/cache-test.html

 */
