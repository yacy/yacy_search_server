// HTTPDemon.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.server.http;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.data.UserDB;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.server.serverCore;
import net.yacy.server.serverHandler;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.server.serverCore.Session;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;



/**
 * Instances of this class can be passed as argument to the serverCore.
 * The generic server dispatches HTTP commands and calls the
 * method GET, HEAD or POST in this class
 * these methods parse the command line and decide wether to call
 * a proxy servlet or a file server servlet
 */
public final class HTTPDemon implements serverHandler, Cloneable {


    public static final int ERRORCASE_MESSAGE = 4;
    public static final int ERRORCASE_FILE = 5;
    private static final File TMPDIR = new File(System.getProperty("java.io.tmpdir"));
    private static final int SIZE_FILE_THRESHOLD = 20 * 1024 * 1024;
    private static final FileItemFactory DISK_FILE_ITEM_FACTORY = new DiskFileItemFactory(SIZE_FILE_THRESHOLD, TMPDIR);

    private static AlternativeDomainNames alternativeResolver = null;

    /**
     * A Set containing extensions that indicate content that should not be transported
     * using zipped content encoding
     * @see #shallTransportZipped(String)
     */

     //TODO: Load this from a file
    private static final Set<String> disallowZippedContentEncoding = new HashSet<String>(Arrays.asList(new String[]{
            ".gz", ".tgz", ".jpg", ".jpeg", ".png", ".mp3", ".mov", ".avi", ".gif", ".zip", ".rar", ".bz2", ".lha", ".jar", ".rpm", ".arc", ".arj", ".wmv"
    }));

    // static objects
    public static final String vDATE = "<<REPL>>";
    public static final String copyright = "[ HTTP SERVER: AnomicHTTPD v" + vDATE + " by Michael Christen / www.anomic.de ]";
    public static final String hline = "-------------------------------------------------------------------------------";

    public static final ConcurrentMap<String, String> reverseMappingCache = new ConcurrentHashMap<String, String>();
    private static volatile Switchboard switchboard;
    private static String virtualHost;

    public static boolean keepAliveSupport = false;
    private static ConcurrentMap<String, Long> YaCyHopAccessRequester = new ConcurrentHashMap<String, Long>();
    private static ConcurrentMap<String, Long> YaCyHopAccessTargets = new ConcurrentHashMap<String, Long>();

    // for authentication
    private boolean use_proxyAccounts = false;
    private boolean proxyAccounts_init = false; // is use_proxyAccounts set?

    private int emptyRequestCount = 0;
    private int keepAliveRequestCount = 0;

    // needed for logging
    private final static ConcurrentLog log = new ConcurrentLog("HTTPD");

    // class methods
    public HTTPDemon(final serverSwitch s) {
        // handler info
        HTTPDemon.switchboard = (Switchboard)s;
        HTTPDemon.virtualHost = switchboard.getConfig("fileHost","localhost");

        // authentication: by default none
        this.proxyAccounts_init = false;

        // configuring keep alive support
        keepAliveSupport = Boolean.parseBoolean(switchboard.getConfig("connectionKeepAliveSupport","false"));
    }

    /**
     * Can be used to reset this {@link serverHandler} oject so that
     * it can be reused for further connections
     * @see net.yacy.server.serverHandler#reset()
     */
    @Override
    public void reset()  {
        this.proxyAccounts_init = false;

        this.emptyRequestCount = 0;
        this.keepAliveRequestCount = 0;
    }

    private static boolean allowProxy(final Session session) {
        final String proxyClient = switchboard.getConfig("proxyClient", "*");
        return (proxyClient.equals("*")) ? true : match(session.userAddress.getHostAddress(), proxyClient);
    }

    private static boolean allowServer(final Session session) {
        final String serverClient = switchboard.getConfig("serverClient", "*");
        return (serverClient.equals("*")) ? true : match(session.userAddress.getHostAddress(), serverClient);
    }

    private static boolean allowYaCyHop() {
        return switchboard.getConfigBool("YaCyHop", false);
    }

    private static boolean match(final String key, final String latch) {
        // the latch is a comma-separated list of patterns
        // each pattern may contain one wildcard-character '*' which matches anything
        final StringTokenizer st = new StringTokenizer(latch,",");
        String pattern;
        while (st.hasMoreTokens()) {
            pattern = st.nextToken();
            if (key.matches(pattern)) return true;
            /*
            pos = pattern.indexOf("*");
            if (pos < 0) {
                // no wild card: exact match
                if (key.equals(pattern)) return true;
            } else {
                // wild card: match left and right side of pattern
                if ((key.startsWith(pattern.substring(0, pos))) &&
                        (key.endsWith(pattern.substring(pos + 1)))) return true;
            }
             */
        }
        return false;
    }

    @Override
    public String greeting() { // OBLIGATORIC FUNCTION
        // a response line upon connection is send to client
        // if no response line is wanted, return "" or null
        return null;
    }

    @Override
    public String error(final Throwable e) { // OBLIGATORIC FUNCTION
        // return string in case of any error that occurs during communication
        // is always (but not only) called if an IO-dependent exception occurrs.
        log.severe("Unexpected Error. " + e.getClass().getName(),e);
        final String message = e.getMessage();
        if (message != null && message.indexOf("heap space",0) > 0) ConcurrentLog.logException(e);
        return "501 Exception occurred: " + message;
    }

    /**
     * This function is used to determine if a persistent connection was requested by the client.
     * @param header the received http-headers
     * @param prop
     * @return <code>true</code> if a persistent connection was requested or <code>false</code> otherwise
     */
    private static boolean handlePersistentConnection(final RequestHeader header, final HashMap<String, Object> prop) {

        if (!keepAliveSupport) {
            prop.put(HeaderFramework.CONNECTION_PROP_PERSISTENT,"close");
            return false;
        }

        // getting the http version that is used by the client
        String httpVersion = (String) prop.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = "HTTP/0.9";

        // managing keep-alive: in HTTP/0.9 and HTTP/1.0 every connection is closed
        // afterwards. In HTTP/1.1 (and above, in the future?) connections are
        // persistent by default, but closed with the "Connection: close"
        // property.
        boolean persistent = !(httpVersion.equals(HeaderFramework.HTTP_VERSION_0_9) || httpVersion.equals(HeaderFramework.HTTP_VERSION_1_0));
        if ((header.get(RequestHeader.CONNECTION, "keep-alive")).toLowerCase().indexOf("close",0) != -1 ||
            (header.get(RequestHeader.PROXY_CONNECTION, "keep-alive")).toLowerCase().indexOf("close",0) != -1) {
            persistent = false;
        }

        final String transferEncoding = header.get(HeaderFramework.TRANSFER_ENCODING, "identity");
        final boolean isPostRequest = prop.get(HeaderFramework.CONNECTION_PROP_METHOD).equals(HeaderFramework.METHOD_POST);
        final boolean hasContentLength = header.containsKey(HeaderFramework.CONTENT_LENGTH);
        final boolean hasTransferEncoding = header.containsKey(HeaderFramework.TRANSFER_ENCODING) && !transferEncoding.equalsIgnoreCase("identity");

        // if the request does not contain a content-length we have to close the connection
        // independently of the value of the connection header
        if (persistent && isPostRequest && !(hasContentLength || hasTransferEncoding))
        	  prop.put(HeaderFramework.CONNECTION_PROP_PERSISTENT,"close");
        else  prop.put(HeaderFramework.CONNECTION_PROP_PERSISTENT,persistent?"keep-alive":"close");

        return persistent;
    }

    private static boolean handleYaCyHopAuthentication(final RequestHeader header, final HashMap<String, Object> prop) {
        // check if the user has allowed that his/her peer is used for hops
        if (!allowYaCyHop()) return false;

        // proxy hops must identify with 4 criteria:

        // the accessed port must not be port 80
        final String host = (String) prop.get(HeaderFramework.CONNECTION_PROP_HOST);
        if (host == null) return false;
        int pos;
        if ((pos = host.indexOf(':')) < 0) {
            // default port 80
            return false; // not allowed
        }
        if (NumberTools.parseIntDecSubstring(host, pos + 1) == 80) return false;

        // the access path must be into the yacy protocol path; it must start with 'yacy'
        if (!(prop.containsKey(HeaderFramework.CONNECTION_PROP_PATH) && ((String) prop.get(HeaderFramework.CONNECTION_PROP_PATH)).startsWith("/yacy/"))) return false;

        // the accessing client must identify with user:password, where
        // user = addressed peer name
        // pw = addressed peer hash (b64-hash)
        final String auth = header.get(RequestHeader.PROXY_AUTHORIZATION,"xxxxxx");
        if (getAlternativeResolver() != null) {
            final String test = Base64Order.standardCoder.encodeString(getAlternativeResolver().myName() + ":" + getAlternativeResolver().myID());
            if (!test.equals(auth.trim().substring(6))) return false;
        }

        // the accessing client must use a yacy user-agent
        if (!((header.get(HeaderFramework.USER_AGENT,"")).startsWith("yacy"))) return false;

        // furthermore, YaCy hops must not exceed a specific access frequency

        // check access requester frequency: protection against DoS against this peer
        final String requester = (String) prop.get(HeaderFramework.CONNECTION_PROP_CLIENTIP);
        if (requester == null) return false;
        if (lastAccessDelta(YaCyHopAccessRequester, requester) < 10000) return false;
        YaCyHopAccessRequester.put(requester, Long.valueOf(System.currentTimeMillis()));

        // check access target frequecy: protection against DoS from a single peer by several different requesters
        if (lastAccessDelta(YaCyHopAccessTargets, host) < 3000) return false;
        YaCyHopAccessTargets.put(host, Long.valueOf(System.currentTimeMillis()));

        // passed all tests
        return true;
    }

    private static long lastAccessDelta(final Map<String, Long> accessTable, final String domain) {
        final Long lastAccess = accessTable.get(domain);
        return (lastAccess == null) ? Long.MAX_VALUE : System.currentTimeMillis() - lastAccess.longValue();
    }

    private boolean handleProxyAuthentication(final RequestHeader header, final HashMap<String, Object> prop, final Session session) throws IOException {
        // getting the http version that is used by the client
        String httpVersion = (String) prop.get("HTTP"); if (httpVersion == null) httpVersion = "HTTP/0.9";

        // reading the authentication settings from switchboard
        if (!this.proxyAccounts_init) {
            this.use_proxyAccounts = switchboard.getConfigBool("use_proxyAccounts", false);
            this.proxyAccounts_init = true; // is initialised
        }

        if (this.use_proxyAccounts) {
            final String auth = header.get(RequestHeader.PROXY_AUTHORIZATION,"xxxxxx");
            UserDB.Entry entry = switchboard.userDB.ipAuth(session.userAddress.getHostAddress());
            if (entry == null) {
                entry = switchboard.userDB.proxyAuth(auth, session.userAddress.getHostAddress());
            }
            if (entry != null) {
                final int returncode=entry.surfRight();
                if (returncode == UserDB.Entry.PROXY_ALLOK) {
                    return true;
                }
                final serverObjects tp = new serverObjects();
                if (returncode == UserDB.Entry.PROXY_TIMELIMIT_REACHED) {
                    tp.put("limit", "1");//time per day
                    tp.put("limit_timelimit", Long.toString(entry.getTimeLimit()));
                    sendRespondError(prop, session.out, 403, "Internet-Timelimit reached", new File("proxymsg/proxylimits.inc"), tp, null);
                } else if (returncode == UserDB.Entry.PROXY_NORIGHT){
                    tp.put("limit", "0");
                    sendRespondError(prop, session.out, 403, "Proxy use forbidden", new File("proxymsg/proxylimits.inc"), tp, null);
                }
                return false;
            }
            // ask for authenticate
            session.out.write(UTF8.getBytes(httpVersion + " 407 Proxy Authentication Required" + serverCore.CRLF_STRING +
            RequestHeader.PROXY_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.CRLF_STRING));
            session.out.write(UTF8.getBytes(HeaderFramework.CONTENT_LENGTH + ": 0\r\n"));
            session.out.write(UTF8.getBytes("\r\n"));
            session.out.flush();
            return false;
        }

        return true;
    }

    @Override
    public Boolean EMPTY(final String arg, final Session session) throws IOException {
        //System.out.println("EMPTY " + arg);
        return (++this.emptyRequestCount > 10) ? serverCore.TERMINATE_CONNECTION : serverCore.RESUME_CONNECTION;
    }

    @Override
    public Boolean UNKNOWN(final String arg, final Session session) throws IOException {
        //System.out.println("UNKNOWN " + arg);

        final HashMap<String, Object> prop = parseRequestLine(HeaderFramework.METHOD_GET, arg, session);
        int pos;
        String unknownCommand = null, args = null;
        if ((pos = arg.indexOf(' ')) > 0) {
            unknownCommand = arg.substring(0,pos);
            args = arg.substring(pos+1);
        } else {
            unknownCommand = arg;
            args = "";
        }

        parseRequestLine(unknownCommand, args, session);

        sendRespondError(prop, session.out, 4, 501, null, unknownCommand + " method not implemented", null);
        return serverCore.TERMINATE_CONNECTION;
    }

    public Boolean GET(final String arg, final Session session) {
        //System.out.println("GET " + arg);
        try {
            // parsing the http request line
            final HashMap<String, Object> prop = parseRequestLine(HeaderFramework.METHOD_GET, arg, session);

            // we now know the HTTP version. depending on that, we read the header
            String httpVersion = (String) prop.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = HeaderFramework.HTTP_VERSION_0_9;
            final RequestHeader header = (httpVersion.equals(HeaderFramework.HTTP_VERSION_0_9))
                    ? new RequestHeader(reverseMappingCache)
                    : readHeader(prop, session);

            // handling transparent proxy support
            handleTransparentProxySupport(header, prop, virtualHost, HTTPDProxyHandler.isTransparentProxy);

            // determines if the connection should be kept alive
            handlePersistentConnection(header, prop);

            if (prop.get(HeaderFramework.CONNECTION_PROP_HOST).equals(virtualHost)) {
                // pass to server
                if (allowServer(session)) {
                    HTTPDFileHandler.doGet(prop, header, session.out);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted, 1)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this server, because you are using a non-granted IP (" + session.userAddress.getHostAddress() + "). allowed are only connections that match with the following filter (1): " + switchboard.getConfig("serverClient", "*") + serverCore.CRLF_STRING));
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (((allowYaCyHop()) && (handleYaCyHopAuthentication(header, prop))) ||
                    ((allowProxy(session)) && (handleProxyAuthentication(header, prop, session)))) {
                    HTTPDProxyHandler.doGet(prop, header, session.out, ClientIdentification.yacyProxyAgent);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted, 2)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using a non-granted IP (" + session.userAddress.getHostAddress() + "). allowed are only connections that match with the following filter (2): " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING));
                    return serverCore.TERMINATE_CONNECTION;
                }
            }

            return prop.get(HeaderFramework.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (final Exception e) {
            logUnexpectedError(e, session.userAddress.getHostAddress());
            return serverCore.TERMINATE_CONNECTION;
        }
    }

    private static void logUnexpectedError(final Exception e, final String address) {
        if (e instanceof InterruptedException) {
            log.info("Interruption detected");
        } else {
            final String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.startsWith("Socket closed")) {
                    log.info("httpd shutdown detected ... (" + e.getMessage() + "), client = " +address);
                } else if ((errorMsg.startsWith("Broken pipe") || errorMsg.startsWith("Connection reset"))) {
                    // client closed the connection, so we just end silently
                    log.info("Client unexpectedly closed connection... (" + e.getMessage() + "), client = " + address);
                } else if (errorMsg.equals("400 Bad request")) {
                	log.info("Bad client request ... (" + e.getMessage() + "), client = " + address);
                } else {
                    log.severe("Unexpected Error ... (" + e.getMessage() + "), client = " + address,e);
                }
            } else {
                log.severe("Unexpected Error ... (" + e.getMessage() + "), client = " + address,e);
            }
        }
    }

    public Boolean HEAD(final String arg, final Session session) {
        //System.out.println("HEAD " + arg);
        try {
            final HashMap<String, Object> prop = parseRequestLine(HeaderFramework.METHOD_HEAD, arg, session);

            // we now know the HTTP version. depending on that, we read the header
            String httpVersion = (String) prop.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = HeaderFramework.HTTP_VERSION_0_9;
            final RequestHeader header = (httpVersion.equals(HeaderFramework.HTTP_VERSION_0_9))
                    ? new RequestHeader(reverseMappingCache)
                    : readHeader(prop,session);

            // handle transparent proxy support
            handleTransparentProxySupport(header, prop, virtualHost, HTTPDProxyHandler.isTransparentProxy);

            // determines if the connection should be kept alive
            handlePersistentConnection(header, prop);

            // return multi-line message
            if (prop.get(HeaderFramework.CONNECTION_PROP_HOST).equals(virtualHost)) {
                // pass to server
                if (allowServer(session)) {
                    HTTPDFileHandler.doHead(prop, header, session.out);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING));
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (((allowYaCyHop()) && (handleYaCyHopAuthentication(header, prop))) ||
                    ((allowProxy(session)) && (handleProxyAuthentication(header, prop, session)))) {
                    HTTPDProxyHandler.doHead(prop, header, session.out, ClientIdentification.yacyProxyAgent);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING));
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            return prop.get(HeaderFramework.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (final Exception e) {
            logUnexpectedError(e, session.userAddress.getHostAddress());
            return serverCore.TERMINATE_CONNECTION;
        }
    }

    public Boolean POST(final String arg, final Session session) {
        //System.out.println("POST " + arg);
        InputStream sessionIn = null;
        boolean retv;
        try {
            final HashMap<String, Object> prop = parseRequestLine(HeaderFramework.METHOD_POST, arg, session);

            // we now know the HTTP version. depending on that, we read the header
            String httpVersion = (String) prop.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = HeaderFramework.HTTP_VERSION_0_9;
            final RequestHeader header = (httpVersion.equals(HeaderFramework.HTTP_VERSION_0_9))
                    ? new RequestHeader(reverseMappingCache)
                    : readHeader(prop, session);

            // handle transfer-coding
            final String transferEncoding = header.get(HeaderFramework.TRANSFER_ENCODING);
            if (transferEncoding != null) {
                if (!HeaderFramework.HTTP_VERSION_1_1.equals(httpVersion)) {
                    log.warn("client "+ session.getName() +" uses transfer-coding with HTTP version "+ httpVersion +"!");
                }
                if("chunked".equalsIgnoreCase(header.get(HeaderFramework.TRANSFER_ENCODING))) {
                    sessionIn = new ChunkedInputStream(session.in);
                } else {
                    // "A server which receives an entity-body with a transfer-coding it does
                    // not understand SHOULD return 501 (Unimplemented), and close the
                    // connection." [RFC 2616, section 3.6]
                    session.out.write(UTF8.getBytes(httpVersion + " 501 transfer-encoding not implemented" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you send a transfer-encoding to this server, which is not supported: " + transferEncoding + serverCore.CRLF_STRING));
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                sessionIn = session.in;
            }

            // handle transparent proxy support
            handleTransparentProxySupport(header, prop, virtualHost, HTTPDProxyHandler.isTransparentProxy);

            // determines if the connection should be kept alive
            handlePersistentConnection(header, prop);

            // return multi-line message
            if (prop.get(HeaderFramework.CONNECTION_PROP_HOST).equals(virtualHost)) {
                // pass to server
                if (allowServer(session)) {
                    HTTPDFileHandler.doPost(prop, header, session.out, sessionIn);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this server, because you are using the non-granted IP " + session.userAddress.getHostAddress() + ". allowed are only connections that match with the following filter (3): " + switchboard.getConfig("serverClient", "*") + serverCore.CRLF_STRING));
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (((allowYaCyHop()) && (handleYaCyHopAuthentication(header, prop))) ||
                    ((allowProxy(session)) && (handleProxyAuthentication(header, prop, session)))) {
                    HTTPDProxyHandler.doPost(prop, header, session.out, sessionIn, ClientIdentification.yacyProxyAgent);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + session.userAddress.getHostAddress() + ". allowed are only connections that match with the following filter (4): " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING));
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            retv = prop.get(HeaderFramework.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (final Exception e) {
            logUnexpectedError(e, session.userAddress.getHostAddress());
            retv = serverCore.TERMINATE_CONNECTION;
        } finally {
            if (sessionIn != null && (sessionIn instanceof ChunkedInputStream)) {
                // read to end, but do not close the stream (maybe HTTP/1.1 persistent)
                try {sessionIn.close();} catch (final IOException e) {}
            }
        }
        return retv;
    }

    public static void handleTransparentProxySupport(final RequestHeader header, final HashMap<String, Object> prop, final String virtualHost, final boolean isTransparentProxy) {
        // transparent proxy support is only available for http 1.0 and above connections
        if (prop.containsKey(HeaderFramework.CONNECTION_PROP_HTTP_VER) && prop.get(HeaderFramework.CONNECTION_PROP_HTTP_VER).equals("HTTP/0.9")) return;

        // if the transparent proxy support was disabled, we have nothing todo here ...
        if (!(isTransparentProxy && header.containsKey(HeaderFramework.HOST))) return;

        // we only need to do the transparent proxy support if the request URL didn't contain the hostname
        // and therefor was set to virtualHost by function parseQuery()
        if (!prop.get(HeaderFramework.CONNECTION_PROP_HOST).equals(virtualHost)) return;

        // TODO: we could have problems with connections from extern here ...
        final String dstHostSocket = header.get(HeaderFramework.HOST);
        prop.put(HeaderFramework.CONNECTION_PROP_HOST,(HTTPDemon.isThisHostName(dstHostSocket)?virtualHost:dstHostSocket));
    }

    public Boolean CONNECT(String arg, final Session session) throws IOException {
        //System.out.println("CONNECT " + arg);
        // establish a ssh-tunneled http connection
        // this is to support https

        // parse HTTP version
        int pos = arg.indexOf(' ');
        final String httpVersion;
        if (pos >= 0) {
            httpVersion = arg.substring(pos + 1);
            arg = arg.substring(0, pos);
        } else {
            httpVersion = "HTTP/1.0";
        }
        final HashMap<String, Object> prop = new HashMap<String, Object>();
        prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, httpVersion);

        // parse hostname and port
        prop.put(HeaderFramework.CONNECTION_PROP_HOST, arg);
        pos = arg.indexOf(':');
        int port = 443;
        if (pos >= 0) {
            port = NumberTools.parseIntDecSubstring(arg, pos + 1);
            //the offcut: arg = arg.substring(0, pos);
        }

        // setting other connection properties
        prop.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, session.userAddress.isAnyLocalAddress() || session.userAddress.isLinkLocalAddress() || session.userAddress.isLoopbackAddress() ? "localhost" : session.userAddress.getHostAddress());
        prop.put(HeaderFramework.CONNECTION_PROP_METHOD, HeaderFramework.METHOD_CONNECT);
        prop.put(HeaderFramework.CONNECTION_PROP_PATH, "/");
        prop.put(HeaderFramework.CONNECTION_PROP_EXT, "");
        prop.put(HeaderFramework.CONNECTION_PROP_URL, "");

        // parse remaining lines
        final RequestHeader header = readHeader(prop,session);

        if (!(allowProxy(session))) {
            // not authorized through firewall blocking (ip does not match filter)
            session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + session.userAddress.getHostAddress() + ". allowed are only connections that match with the following filter (5): " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING));
            return serverCore.TERMINATE_CONNECTION;
        }

        if (port != 443 && switchboard.getConfig("secureHttps", "true").equals("true")) {
            // security: connection only to ssl port
            // we send a 403 (forbidden) error back
            session.out.write(UTF8.getBytes(httpVersion + " 403 Connection to non-443 forbidden" +
                    serverCore.CRLF_STRING + serverCore.CRLF_STRING));
            return serverCore.TERMINATE_CONNECTION;
        }

        // pass to proxy
        if (((allowYaCyHop()) && (handleYaCyHopAuthentication(header, prop))) ||
            ((allowProxy(session)) && (handleProxyAuthentication(header, prop, session)))) {
            HTTPDProxyHandler.doConnect(prop, header, session.in, session.out, ClientIdentification.yacyProxyAgent);
        } else {
            // not authorized through firewall blocking (ip does not match filter)
            session.out.write(UTF8.getBytes(httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + session.userAddress.getHostAddress() + ". allowed are only connections that match with the following filter (6): " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING));
        }

        return serverCore.TERMINATE_CONNECTION;
    }

    private final HashMap<String, Object> parseRequestLine(final String cmd, final String s, final Session session) {

        // parsing the header
        final HashMap<String, Object> p = parseRequestLine(cmd, s, virtualHost);

        // track the request
        final String path = (String) p.get(HeaderFramework.CONNECTION_PROP_URL);
        String args = (String) p.get(HeaderFramework.CONNECTION_PROP_ARGS); if (args == null) args = "";
        switchboard.track(session.userAddress.getHostAddress(), (args.length() > 0) ? path + "?" + args : path);

        // reseting the empty request counter
        this.emptyRequestCount = 0;

        // counting the amount of received requests within this permanent connection
        p.put(HeaderFramework.CONNECTION_PROP_KEEP_ALIVE_COUNT, Integer.toString(++this.keepAliveRequestCount));

        // setting the client-IP
        p.put(HeaderFramework.CONNECTION_PROP_CLIENTIP, session.userAddress.getHostAddress());

        return p;
    }

    // some static methods that needs to be used from any CGI
    // and also by the httpdFileHandler
    // but this belongs to the protocol handler, this class.

    public static int parseArgs(final serverObjects args, final InputStream in, final int length) throws IOException {
        // this is a quick hack using a previously coded parseMultipart based on a buffer
        // should be replaced sometime by a 'right' implementation
        byte[] buffer = null;

        // parsing post request bodies with a given length
        if (length != -1) {
            buffer = new byte[length];
            final int bytesRead = in.read(buffer);
            assert bytesRead == buffer.length;
        // parsing post request bodies which are gzip content-encoded
        } else {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(512);
            FileUtils.copy(in,bout);
            buffer = bout.toByteArray();
            bout.close(); bout = null;
        }

        final int argc = parseArgs(args, UTF8.String(buffer));
        buffer = null;
        return argc;
    }

    public static int parseArgs(final serverObjects args, String argsString) {
        // this parses a arg string that can either be attached to a URL query
        // or can be given as result of a post method
        // the String argsString is supposed to be constructed as
        // <key1>=<value1>'&'<key2>=<value2>'&'<key3>=<value3>
        // the calling function must strip off a possible leading '?' char
        if (argsString.isEmpty()) return 0;
        argsString = argsString.replaceAll("&quot;", "%22");
        argsString = argsString + "&"; // for technical reasons
        int sep;
        int eqp;
        int argc = 0;
        // Textfield1=default+value+Textfield+1&Textfield2=default+value+Textfield+2&selection1=sel1&selection2=othervalue1&selection2=sel2&selection3=sel3&Menu1=SubEnry11&radio1=button1&check1=button2&check1=button3&hidden1=&sButton1=enter+%281%29
        while (argsString.length() > 0) {
            eqp = argsString.indexOf('=');
            if (eqp <= 0) break;
            sep = argsString.indexOf("&amp;", eqp + 1);
            if (sep > 0) {
                // resulting equations are inserted into the property args with leading '&amp;'
                args.add(parseArg(argsString.substring(0, eqp)), parseArg(argsString.substring(eqp + 1, sep)));
                argsString = argsString.substring(sep + 5);
                argc++;
                continue;
            }
            sep = argsString.indexOf('&', eqp + 1);
            if (sep > 0) {
                // resulting equations are inserted into the property args with leading '&'
                args.add(parseArg(argsString.substring(0, eqp)), parseArg(argsString.substring(eqp + 1, sep)));
                argsString = argsString.substring(sep + 1);
                argc++;
                continue;
            }
            break;
        }
        // we return the number of parsed arguments
        return argc;
    }

    /**
     * <p>This method basically does the same as {@link URLDecoder#decode(String, String) URLDecoder.decode(s, "UTF-8")}
     * would do with the exception of more lazyness in regard to current browser implementations as they do not
     * always comply with the standards.</p>
     * <p>The following replacements are performed on the input-<code>String</code>:</p>
     * <ul>
     * <li>'<code>+</code>'-characters are replaced by space
     * <li>(supbsequent (in the case of encoded unicode-chars)) '<code>%HH</code>'-entities are replaced by their
     * respective <code>char</code>-representation</li>
     * <li>'<code>%uHHHH</code>'-entities (sent by IE although rejected by the W3C) are replaced by their respective
     * <code>char</code>-representation</li>
     * <li><strong>TODO</strong>: <code>chars</code> already encoded in UTF-8 are url-encoded and re-decoded due to internal restrictions,
     * which slows down this method unnecessarily</li>
     * </ul>
     *
     * @param s the URL-encoded <code>String</code> to decode, note that the encoding used to URL-encode the original
     * <code>String</code> has to be UTF-8 (i.e. the "<code>accept-charset</code>"-property of HTML
     * <code>&lt;form&gt;</code>-elements)
     * @return the "normal" Java-<code>String</code> (UTF-8) represented by the input or <code>null</code>
     * if the passed argument <code>encoding</code> is not supported
     */
    private static String parseArg(String s) {
        int pos = 0;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(s.length());

        while (pos < s.length()) {
            if (s.charAt(pos) == '+') {
                baos.write(' ');
                pos++;
            } else if (s.charAt(pos) == '%') {
                try {
                    if (s.length() >= pos + 6 && (s.charAt(pos + 1) == 'u' || s.charAt(pos + 1) == 'U')) {
                        // non-standard encoding of IE for unicode-chars
                        final int bh = NumberTools.parseIntDecSubstring(s.substring(pos + 2, pos + 4), 16);
                        final int bl = NumberTools.parseIntDecSubstring(s.substring(pos + 4, pos + 6), 16);
                        // TODO: needs conversion from UTF-16 to UTF-8
                        baos.write(bh);
                        baos.write(bl);
                        pos += 6;
                    } else if (s.length() >= pos + 3) {
                        baos.write(Integer.parseInt(s.substring(pos + 1, pos + 3), 16));
                        pos += 3;
                    } else {
                        baos.write(s.charAt(pos++));
                    }
                } catch (final NumberFormatException e) {
                    baos.write(s.charAt(pos++));
                }
            } else if (s.charAt(pos) > 127) {
                // Unicode chars sent by client, see http://www.w3.org/International/O-URL-code.html
                try {
                    // don't write anything but url-encode the unicode char
                    s = s.substring(0, pos) + URLEncoder.encode(s.substring(pos, pos + 1), "UTF-8") + s.substring(pos + 1);
                } catch (final UnsupportedEncodingException e) { return null; }
            } else {
                baos.write(s.charAt(pos++));
            }
        }

        return UTF8.String(baos.toByteArray());
    }

    // 06.01.2007: decode HTML entities by [FB]
    public static String decodeHtmlEntities(String s) {
        // replace all entities defined in wikiCode.characters and htmlentities
        s = CharacterCoding.html2unicode(s);

        // replace all other
        final CharArrayWriter b = new CharArrayWriter(s.length());
        int end;
        for (int i = 0, len = s.length(); i < len; i++) {
            if (s.charAt(i) == '&' && (end = s.indexOf(';', i + 1)) > i) {
                if (s.charAt(i + 1) == '#') {                           // &#1234; symbols
                    b.write(NumberTools.parseIntDecSubstring(s, i + 2, end));
                    i += end - i;
                } else {                                                // 'named' smybols
                    if (log.isFine()) log.fine("discovered yet unimplemented HTML entity '" + s.substring(i, end + 1) + "'");
                    b.write(s.charAt(i));
                }
            } else {
                b.write(s.charAt(i));
            }
        }
        return b.toString();
    }

    /**
     * parses the message accordingly to RFC 1867 using "Commons FileUpload" (http://commons.apache.org/fileupload/)
     *
     * @author danielr
     * @since 07.08.2008
     * @param header
     *            hier muss ARGC gesetzt werden!
     * @param args
     * @param in the raw body
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public static Map<String, byte[]> parseMultipart(final RequestHeader header, final serverObjects args, final InputStream in) throws IOException {

        final InputStream body = prepareBody(header, in);

        final RequestContext request = new yacyContextRequest(header, body);

        // check information
        if (!FileUploadBase.isMultipartContent(request)) {
            throw new IOException("the request is not a multipart-message!");
        }

        // reject too large uploads
        if (request.getContentLength() > SIZE_FILE_THRESHOLD) throw new IOException("FileUploadException: uploaded file too large = " + request.getContentLength());

        // check if we have enough memory
        if (!MemoryControl.request(request.getContentLength() * 3, false)) {
        	throw new IOException("not enough memory available for request. request.getContentLength() = " + request.getContentLength() + ", MemoryControl.available() = " + MemoryControl.available());
        }

        // parse data in memory
        final List<FileItem> items;

        try {
            final FileUpload upload = new FileUpload(DISK_FILE_ITEM_FACTORY);
            items = upload.parseRequest(request);
        } catch (final FileUploadException e) {
            throw new IOException("FileUploadException " + e.getMessage());
        }

        // format information for further usage
        final Map<String, byte[]> files = new HashMap<String, byte[]>();
        byte[] fileContent;
        for (final FileItem item : items) {
            if (item.isFormField()) {
                // simple text
                if (item.getContentType() == null || !item.getContentType().contains("charset")) {
                    // old yacy clients use their local default charset, on most systems UTF-8 (I hope ;)
                    args.add(item.getFieldName(), item.getString("UTF-8"));
                } else {
                    // use default encoding (given as header or ISO-8859-1)
                    args.add(item.getFieldName(), item.getString());
                }
            } else {
                // file
                args.add(item.getFieldName(), item.getName());
                fileContent = FileUtils.read(item.getInputStream(), (int) item.getSize());
                item.getInputStream().close();
                files.put(item.getFieldName(), fileContent);
            }
        }
        header.put("ARGC", String.valueOf(items.size())); // store argument count

        return files;
    }

    /**
     * prepares the body so that it can be read as whole plain text
     * (uncompress if necessary and ensure correct ending)
     *
     * @param header
     * @param in
     * @return
     * @throws IOException
     */
    private static InputStream prepareBody(final RequestHeader header, final InputStream in) throws IOException {
        InputStream body = in;
        // data may be compressed
        final String bodyEncoding = header.get(HeaderFramework.CONTENT_ENCODING);
        if(HeaderFramework.CONTENT_ENCODING_GZIP.equalsIgnoreCase(bodyEncoding) && !(body instanceof GZIPInputStream)) {
            body = new GZIPInputStream(body);
            // length of uncompressed data is unknown
            header.remove(HeaderFramework.CONTENT_LENGTH);
        } else {
            // ensure the end of data (if client keeps alive the connection)
            final long clength = header.getContentLength();
            if (clength > 0) {
                body = new ContentLengthInputStream(body, clength);
            }
        }
        return body;
    }

    /**
     * wraps the request into a org.apache.commons.fileupload.RequestContext
     *
     * @author danielr
     * @since 07.08.2008
     */
    private static class yacyContextRequest extends RequestHeader implements RequestContext {

        private static final long serialVersionUID = -8936741958551376593L;

        private final InputStream inStream;

        /**
         * creates a new yacyContextRequest
         *
         * @param header
         * @param in
         */
        public yacyContextRequest(final Map<String, String> requestHeader, final InputStream in) {
            super(null, requestHeader);
            this.inStream = in;
        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.commons.fileupload.RequestContext#getInputStream()
         */
        // @Override
        @Override
        public InputStream getInputStream() throws IOException {
            return this.inStream;
        }

    }

    public static int indexOf(final int start, final byte[] array, final byte[] pattern) {
        // return a position of a pattern in an array
        if (start > array.length - pattern.length) return -1;
        if (pattern.length == 0) return start;
        for (int pos = start, lens = array.length - pattern.length; pos <= lens; pos++) {
            if ((array[pos] == pattern[0]) && (equals(array, pos, pattern, 0, pattern.length))) {
                return pos;
            }
        }
        return -1;
    }

    public static boolean equals(final byte[] a, final int aoff, final byte[] b, final int boff, final int len) {
        if ((aoff + len > a.length) || (boff + len > b.length)) return false;
        for (int i = 0; i < len; i++) {
            if (a[aoff + i] != b[boff + i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public HTTPDemon clone() {
        return new HTTPDemon(switchboard);
    }

    public static final void sendRespondBody(
            final OutputStream respond,
            final byte[] body
    ) throws IOException {
        respond.write(body);
        respond.flush();
    }

    public static final void sendRespondError(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final int errorcase,
            final int httpStatusCode,
            final String httpStatusText,
            final String detailedErrorMsg,
            final Throwable stackTrace
    ) throws IOException {
        sendRespondError(
                conProp,
                respond,
                errorcase,
                httpStatusCode,
                httpStatusText,
                detailedErrorMsg,
                null,
                null,
                stackTrace,
                null
        );
    }

    public static final void sendRespondError(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final int httpStatusCode,
            final String httpStatusText,
            final File detailedErrorMsgFile,
            final serverObjects detailedErrorMsgValues,
            final Throwable stackTrace
    ) throws IOException {
        sendRespondError(
                conProp,
                respond,
                5,
                httpStatusCode,
                httpStatusText,
                null,
                detailedErrorMsgFile,
                detailedErrorMsgValues,
                stackTrace,
                null
        );
    }

    public static final void sendRespondError(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final int errorcase,
            final int httpStatusCode,
            String httpStatusText,
            final String detailedErrorMsgText,
            final Object detailedErrorMsgFile,
            final serverObjects detailedErrorMsgValues,
            final Throwable stackTrace,
            ResponseHeader header
    ) throws IOException {

        FileInputStream fis = null;
        ByteArrayOutputStream o = null;
        try {
            // setting the proper http status message
            String httpVersion = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = "HTTP/1.1";
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals("HTTP/1.0") && HeaderFramework.http1_0.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals("HTTP/1.1") && HeaderFramework.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }

            // generating the desired request url
            String host = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HOST);
            String path = (String) conProp.get(HeaderFramework.CONNECTION_PROP_PATH); if (path == null) path = "/";
            final String args = (String) conProp.get(HeaderFramework.CONNECTION_PROP_ARGS);
            final String method = (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD);

            final int port;
            final int pos = host.indexOf(':');
            if (pos != -1) {
                port = NumberTools.parseIntDecSubstring(host, pos + 1);
                host = host.substring(0, pos);
            } else {
                port = 80;
            }

            String urlString;
            try {
                urlString = (new DigestURL((method.equals(HeaderFramework.METHOD_CONNECT)?"https":"http"), host, port, (args == null) ? path : path + "?" + args)).toString();
            } catch (final MalformedURLException e) {
                urlString = "invalid URL";
            }

            // set rewrite values
            final serverObjects tp = new serverObjects();

            String clientIP = (String) conProp.get(HeaderFramework.CONNECTION_PROP_CLIENTIP); if (clientIP == null) clientIP = Domains.LOCALHOST;

            // check if ip is local ip address
            final InetAddress hostAddress = Domains.dnsResolve(clientIP);
            if (hostAddress == null) {
                tp.put("host", Domains.myPublicLocalIP().getHostAddress());
                tp.put("port", Integer.toString(serverCore.getPortNr(switchboard.getConfig("port", "8090"))));
            } else if (hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress()) {
                tp.put("host", Domains.myPublicLocalIP().getHostAddress());
                tp.put("port", Integer.toString(serverCore.getPortNr(switchboard.getConfig("port", "8090"))));
            } else {
                tp.put("host", switchboard.myPublicIP());
                tp.put("port", Integer.toString(serverCore.getPortNr(switchboard.getConfig("port", "8090"))));
            }

            tp.put("peerName", (getAlternativeResolver() == null) ? "" : getAlternativeResolver().myName());
            tp.put("errorMessageType", Integer.toString(errorcase));
            tp.put("httpStatus",       Integer.toString(httpStatusCode) + " " + httpStatusText);
            tp.put("requestMethod",    (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD));
            tp.put("requestURL",       urlString);

            switch (errorcase) {
                case ERRORCASE_FILE:
                    tp.put("errorMessageType_file", (detailedErrorMsgFile == null) ? "" : detailedErrorMsgFile.toString());
                    if ((detailedErrorMsgValues != null) && !detailedErrorMsgValues.isEmpty()) {
                        // rewriting the value-names and add the proper name prefix:
                        for (final Entry<String, String> entry: detailedErrorMsgValues.entrySet()) {
                            tp.put("errorMessageType_" + entry.getKey(), entry.getValue());
                        }
                    }
                    break;
                case ERRORCASE_MESSAGE:
                default:
                    tp.put("errorMessageType_detailedErrorMsg", (detailedErrorMsgText == null) ? "" : detailedErrorMsgText.replaceAll("\n", "<br />"));
                    break;
            }

            // building the stacktrace
            if (stackTrace != null) {
                tp.put("printStackTrace", "1");
                final ByteBuffer errorMsg = new ByteBuffer(100);
                stackTrace.printStackTrace(new PrintStream(errorMsg));
                tp.put("printStackTrace_exception", stackTrace.toString());
                tp.put("printStackTrace_stacktrace", UTF8.String(errorMsg.getBytes()));
            } else {
                tp.put("printStackTrace", "0");
            }

            // Generated Tue, 23 Aug 2005 11:19:14 GMT by brain.wg (squid/2.5.STABLE3)
            // adding some system information
            final String systemDate = HeaderFramework.formatRFC1123(new Date());
            tp.put("date", systemDate);

            // rewrite the file
            final File htRootPath = new File(switchboard.getAppPath(), switchboard.getConfig("htRootPath","htroot"));

            TemplateEngine.writeTemplate(
                    fis = new FileInputStream(new File(htRootPath, "/proxymsg/error.html")),
                    o = new ByteArrayOutputStream(512),
                    tp,
                    ASCII.getBytes("-UNRESOLVED_PATTERN-")
            );
            final byte[] result = o.toByteArray();
            o.close(); o = null;

            if (header == null) header = new ResponseHeader(httpStatusCode);
            header.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_STATUS, Integer.toString(httpStatusCode));
            header.put(HeaderFramework.DATE, systemDate);
            header.put(HeaderFramework.CONTENT_TYPE, "text/html");
            header.put(HeaderFramework.CONTENT_LENGTH, Integer.toString(result.length));
            header.put(HeaderFramework.PRAGMA, "no-cache");
            sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,httpStatusText,header);

            if (! method.equals(HeaderFramework.METHOD_HEAD)) {
                // write the array to the client
                FileUtils.copy(result, respond);
            }
            respond.flush();
        } finally {
            if (fis != null) try { fis.close(); } catch (final Exception e) { ConcurrentLog.logException(e); }
            if (o != null)   try { o.close();   } catch (final Exception e) { ConcurrentLog.logException(e); }
        }
    }

    public static final void sendRespondHeader(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode,
            final String httpStatusText,
            String contentType,
            final long contentLength,
            Date moddate,
            final Date expires,
            ResponseHeader headers,
            final String contentEnc,
            final String transferEnc,
            final boolean nocache
    ) throws IOException {

        final String reqMethod = (String) conProp.get(HeaderFramework.CONNECTION_PROP_METHOD);

        if ((transferEnc != null) && !httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1)) {
            throw new IllegalArgumentException("Transfer encoding is only supported for http/1.1 connections. The current connection version is " + httpVersion);
        }

        if (!reqMethod.equals(HeaderFramework.METHOD_HEAD)){
            if (!"close".equals(conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT)) &&
                    transferEnc == null && contentLength < 0) {
                throw new IllegalArgumentException("Message MUST contain a Content-Length or a non-identity transfer-coding header field.");
            }
            if (transferEnc != null && contentLength >= 0) {
                throw new IllegalArgumentException("Messages MUST NOT include both a Content-Length header field and a non-identity transfer-coding.");
            }
        }

        if (headers == null) headers = new ResponseHeader(httpStatusCode);
        final Date now = new Date(System.currentTimeMillis());

        headers.put(HeaderFramework.SERVER, "AnomicHTTPD (www.anomic.de)");
        headers.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(now));
        if (moddate.after(now)) {
            moddate = now;
        }
        headers.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(moddate));

        if (nocache) {
            headers.put(HeaderFramework.CACHE_CONTROL, "no-cache");
            headers.put(HeaderFramework.CACHE_CONTROL, "no-store");
            headers.put(HeaderFramework.PRAGMA, "no-cache");
        }

        if (contentType == null) contentType = "text/html; charset=UTF-8";
        if (headers.get(HeaderFramework.CONTENT_TYPE) == null) headers.put(HeaderFramework.CONTENT_TYPE, contentType);
        if (contentLength > 0)   headers.put(HeaderFramework.CONTENT_LENGTH, Long.toString(contentLength));
        //if (cookie != null)      headers.put(httpHeader.SET_COOKIE, cookie);
        if (expires != null)     headers.put(HeaderFramework.EXPIRES, HeaderFramework.formatRFC1123(expires));
        if (contentEnc != null)  headers.put(HeaderFramework.CONTENT_ENCODING, contentEnc);
        if (transferEnc != null) headers.put(HeaderFramework.TRANSFER_ENCODING, transferEnc);

        sendRespondHeader(conProp, respond, httpVersion, httpStatusCode, httpStatusText, headers);
    }

    public static final void sendRespondHeader(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode,
            final ResponseHeader header
    ) throws IOException {
        sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,null,header);
    }

    public static final void sendRespondHeader(
            final HashMap<String, Object> conProp,
            final OutputStream respond,
            String httpVersion,
            final int httpStatusCode,
            String httpStatusText,
            ResponseHeader responseHeader
    ) throws IOException {

        if (respond == null) throw new NullPointerException("The outputstream must not be null.");
        if (conProp == null) throw new NullPointerException("The connection property structure must not be null.");
        if (httpVersion == null) httpVersion = (String) conProp.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = HeaderFramework.HTTP_VERSION_1_1;
        if (responseHeader == null) responseHeader = new ResponseHeader(httpStatusCode);

        try {
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals(HeaderFramework.HTTP_VERSION_1_0) && HeaderFramework.http1_0.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals(HeaderFramework.HTTP_VERSION_1_1) && HeaderFramework.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = HeaderFramework.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }

            final StringBuilder header = new StringBuilder(560);

            // "HTTP/0.9" does not have a status line or header in the response
            if (! httpVersion.toUpperCase().equals(HeaderFramework.HTTP_VERSION_0_9)) {
                // write status line
                header.append(httpVersion).append(" ")
                                  .append(Integer.toString(httpStatusCode)).append(" ")
                                  .append(httpStatusText).append("\r\n");

                // prepare header
                if (!responseHeader.containsKey(HeaderFramework.DATE))
                    responseHeader.put(HeaderFramework.DATE, HeaderFramework.formatRFC1123(new Date()));
                if (!responseHeader.containsKey(HeaderFramework.CONTENT_TYPE))
                    responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html; charset=UTF-8"); // fix this
                if (!responseHeader.containsKey(RequestHeader.CONNECTION) && conProp.containsKey(HeaderFramework.CONNECTION_PROP_PERSISTENT))
                    responseHeader.put(RequestHeader.CONNECTION, (String) conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT));
                if (!responseHeader.containsKey(RequestHeader.PROXY_CONNECTION) && conProp.containsKey(HeaderFramework.CONNECTION_PROP_PERSISTENT))
                    responseHeader.put(RequestHeader.PROXY_CONNECTION, (String) conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT));

                if (conProp.containsKey(HeaderFramework.CONNECTION_PROP_PERSISTENT) &&
                    conProp.get(HeaderFramework.CONNECTION_PROP_PERSISTENT).equals("keep-alive") &&
                    !responseHeader.containsKey(HeaderFramework.TRANSFER_ENCODING) &&
                    !responseHeader.containsKey(HeaderFramework.CONTENT_LENGTH))
                    responseHeader.put(HeaderFramework.CONTENT_LENGTH, "0");

                // adding some yacy specific headers
                responseHeader.put(HeaderFramework.X_YACY_KEEP_ALIVE_REQUEST_COUNT,(String) conProp.get(HeaderFramework.CONNECTION_PROP_KEEP_ALIVE_COUNT));
                responseHeader.put(HeaderFramework.X_YACY_ORIGINAL_REQUEST_LINE,(String) conProp.get(HeaderFramework.CONNECTION_PROP_REQUESTLINE));
                //responseHeader.put(HeaderFramework.X_YACY_PREVIOUS_REQUEST_LINE,conProp.getProperty(HeaderFramework.CONNECTION_PROP_PREV_REQUESTLINE));

                //read custom headers
                final Iterator<ResponseHeader.Entry> it = responseHeader.getAdditionalHeaderProperties().iterator();
                ResponseHeader.Entry e;
                while(it.hasNext()) {
                        //Append user properties to the main String
                        //TODO: Should we check for user properites. What if they intersect properties that are already in header?
                    e = it.next();
                    header.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
                }

                // write header
                final Iterator<String> i = responseHeader.keySet().iterator();
                String key;
                char tag;
                int count;
                while (i.hasNext()) {
                    key = i.next();
                    tag = key.charAt(0);
                    if ((tag != '*') && (tag != '#')) { // '#' in key is reserved for proxy attributes as artificial header values
                        count = responseHeader.keyCount(key);
                        for (int j = 0; j < count; j++) {
                            header.append(key).append(": ").append(responseHeader.getSingle(key, j)).append("\r\n");
                        }
                    }
                }

                // end header
                header.append("\r\n");

                // sending headers to the client
                respond.write(UTF8.getBytes(header.toString()));

                // flush stream
                respond.flush();
            }

            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_HEADER,responseHeader);
            conProp.put(HeaderFramework.CONNECTION_PROP_PROXY_RESPOND_STATUS,Integer.toString(httpStatusCode));
        } catch (final Exception e) {
            // any interruption may be caused be network error or because the user has closed
            // the windows during transmission. We simply pass it as IOException
            throw new IOException(e.getMessage());
        }
    }

    public static boolean shallTransportZipped(final String path) {
        if ((path == null) || (path.isEmpty())) return true;

        int pos;
        if ((pos = path.lastIndexOf('.')) != -1) {
            return !disallowZippedContentEncoding.contains(path.substring(pos).toLowerCase());
        }
        return true;
    }

    public static boolean isThisSeedIP(final String hostName) {
        if ((hostName == null) || (hostName.isEmpty())) return false;

        // getting ip address and port of this seed
        if (getAlternativeResolver() == null) return false;

        // resolve ip addresses
        final InetAddress seedInetAddress = Domains.dnsResolve(getAlternativeResolver().myIP());
        final InetAddress hostInetAddress = Domains.dnsResolve(hostName);
        if (seedInetAddress == null || hostInetAddress == null) return false;

        // if it's equal, the hostname points to this seed
        return (seedInetAddress.equals(hostInetAddress));
    }

    public static boolean isThisHostName(final String hostName) {
        if ((hostName == null) || (hostName.isEmpty())) return false;

        try {
            final int idx = hostName.indexOf(':');
            final String dstHost = (idx != -1) ? hostName.substring(0,idx).trim() : hostName.trim();
            final Integer dstPort = (idx != -1) ? Integer.valueOf(hostName.substring(idx+1).trim()) : Integer.valueOf(80);

            // if the hostname endswith thisPeerName.yacy ...
            final String alternativeAddress = (getAlternativeResolver() == null) ? null : getAlternativeResolver().myAlternativeAddress();
            if ((alternativeAddress != null) && (dstHost.endsWith(alternativeAddress))) {
                return true;
            /*
             * If the port number is equal to the yacy port and the IP address is an address of this host ...
             * Please note that yacy is listening to all interfaces of this host
             */
            } else if (
                    // check if the destination port is equal to the port yacy is listening to
                    dstPort.equals(Integer.valueOf(serverCore.getPortNr(switchboard.getConfig("port", "8090")))) &&
                    (
                        // check if the destination host is our local IP address
                        Domains.isThisHostIP(dstHost) ||
                        // check if the destination host is our seed ip address
                        isThisSeedIP(dstHost)
                    )
            ) {
                 return true;
            }
        } catch (final Exception e) {}
        return false;
    }

    /**
     * @param alternativeResolver the alternativeResolver to set
     */
    public static void setAlternativeResolver(final AlternativeDomainNames alternativeResolver) {
        HTTPDemon.alternativeResolver = alternativeResolver;
    }

    /**
     * @return the alternativeResolver
     */
    public static AlternativeDomainNames getAlternativeResolver() {
        return alternativeResolver;
    }


    public static RequestHeader readHeader(final HashMap<String, Object> prop, final serverCore.Session theSession) throws IOException {

        // reading all headers
        final RequestHeader header = new RequestHeader(HTTPDemon.reverseMappingCache);
        int p;
        String line;
        while ((line = theSession.readLineAsString()) != null) {
            if (line.isEmpty()) break; // this separates the header of the HTTP request from the body
            // parse the header line: a property separated with the ':' sign
            if ((p = line.indexOf(':')) >= 0) {
                // store a property
                header.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
            }
        }

        /*
         * doing some header validation here ...
         */
        String httpVersion = (String) prop.get(HeaderFramework.CONNECTION_PROP_HTTP_VER); if (httpVersion == null) httpVersion = "HTTP/0.9";
        if (httpVersion.equals("HTTP/1.1") && !header.containsKey(HeaderFramework.HOST)) {
            // the HTTP/1.1 specification requires that an HTTP/1.1 server must reject any
            // HTTP/1.1 message that does not contain a Host header.
            HTTPDemon.sendRespondError(prop,theSession.out,0,400,null,null,null);
            throw new IOException("400 Bad request");
        }

        return header;
    }

    private static final Pattern P_20 = Pattern.compile(" ", Pattern.LITERAL);
    private static final Pattern P_7B = Pattern.compile("{", Pattern.LITERAL);
    private static final Pattern P_7D = Pattern.compile("}", Pattern.LITERAL);
    private static final Pattern P_7C = Pattern.compile("|", Pattern.LITERAL);
    private static final Pattern P_5C = Pattern.compile("\\", Pattern.LITERAL);
    private static final Pattern P_5E = Pattern.compile("^", Pattern.LITERAL);
    private static final Pattern P_5B = Pattern.compile("[", Pattern.LITERAL);
    private static final Pattern P_5D = Pattern.compile("]", Pattern.LITERAL);
    private static final Pattern P_60 = Pattern.compile("`", Pattern.LITERAL);


    public static HashMap<String, Object> parseRequestLine(final String cmd, String args, final String virtualHost) {

        final HashMap<String, Object> prop = new HashMap<String, Object>(); // we can use a non-synchronized data structure here

        // storing informations about the request
        prop.put(HeaderFramework.CONNECTION_PROP_METHOD, cmd);
        prop.put(HeaderFramework.CONNECTION_PROP_REQUESTLINE, cmd + " " + args);

        // this parses a whole URL
        if (args.isEmpty()) {
            prop.put(HeaderFramework.CONNECTION_PROP_HOST, virtualHost);
            prop.put(HeaderFramework.CONNECTION_PROP_PATH, "/");
            prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_0_9);
            prop.put(HeaderFramework.CONNECTION_PROP_EXT, "");
            return prop;
        }

        // store the version propery "HTTP" and cut the query at both ends
        int sep = args.lastIndexOf(' ');
        if ((sep >= 0) && (args.substring(sep + 1).toLowerCase().startsWith("http/"))) {
            // HTTP version is given
            prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, args.substring(sep + 1).trim());
            args = args.substring(0, sep).trim(); // cut off HTTP version mark
        } else {
            // HTTP version is not given, it will be treated as ver 0.9
            prop.put(HeaderFramework.CONNECTION_PROP_HTTP_VER, HeaderFramework.HTTP_VERSION_0_9);
        }

        // replacing spaces in the url string correctly
        args = P_20.matcher(args).replaceAll("%20");
        // replace unwise characters (see RFC 2396, 2.4.3), which may not be escaped
        args = P_7B.matcher(args).replaceAll("%7B");
        args = P_7D.matcher(args).replaceAll("%7D");
        args = P_7C.matcher(args).replaceAll("%7C");
        args = P_5C.matcher(args).replaceAll("%5C");
        args = P_5E.matcher(args).replaceAll("%5E");
        args = P_5B.matcher(args).replaceAll("%5B");
        args = P_5D.matcher(args).replaceAll("%5D");
        args = P_60.matcher(args).replaceAll("%60");

        // properties of the query are stored with the prefix "&"
        // additionally, the values URL and ARGC are computed

        final String argsString;
        sep = args.indexOf('?');
        if (sep >= 0) {
            // there are values attached to the query string
            argsString = args.substring(sep + 1); // cut head from tail of query
            args = args.substring(0, sep);
        } else {
            argsString = "";
        }
        prop.put(HeaderFramework.CONNECTION_PROP_URL, args); // store URL
        if (!argsString.isEmpty()) {
            prop.put(HeaderFramework.CONNECTION_PROP_ARGS, argsString);
        } // store arguments in original form

        // finally find host string
        final String path;
        if (args.toUpperCase().startsWith("HTTP://")) {
            // a host was given. extract it and set path
            args = args.substring(7);
            sep = args.indexOf('/');
            if (sep < 0) {
                // this is a malformed url, something like
                // http://index.html
                // we are lazy and guess that it means
                // /index.html
                // which is a localhost access to the file servlet
                prop.put(HeaderFramework.CONNECTION_PROP_HOST, args);
                path = "/";
            } else {
                // THIS IS THE "GOOD" CASE
                // a perfect formulated url
                final String dstHostSocket = args.substring(0, sep);
                prop.put(HeaderFramework.CONNECTION_PROP_HOST, (HTTPDemon.isThisHostName(dstHostSocket) ? virtualHost : dstHostSocket));
                path = args.substring(sep); // yes, including beginning "/"
            }
        } else {
            // no host in url. set path
            if (args.length() > 0 && args.charAt(0) == '/') {
                // thats also fine, its a perfect localhost access
                // in this case, we simulate a
                // http://localhost/s
                // access by setting a virtual host
                prop.put(HeaderFramework.CONNECTION_PROP_HOST, virtualHost);
                path = args;
            } else {
                // the client 'forgot' to set a leading '/'
                // this is the same case as above, with some lazyness
                prop.put(HeaderFramework.CONNECTION_PROP_HOST, virtualHost);
                path = "/" + args;
            }
        }
        prop.put(HeaderFramework.CONNECTION_PROP_PATH, path);

        // find out file extension (we already stripped ?-parameters from args)
        final String ext;
        sep = path.lastIndexOf('.');
        if (sep >= 0) {
            final int ancpos = path.indexOf("#", sep + 1);
            if (ancpos  >= sep) {
                // ex: /foo/bar.html#xy => html
                ext = path.substring(sep + 1, ancpos).toLowerCase();
            } else {
                // ex: /foo/bar.php => php
                ext = path.substring(sep + 1).toLowerCase();
            }
        } else {
            ext = ""; // default when no file extension
        }
        prop.put(HeaderFramework.CONNECTION_PROP_EXT, ext);

        return prop;
    }
}
