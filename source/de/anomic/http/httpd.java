// httpd.java
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

package de.anomic.http;

import java.io.ByteArrayInputStream;
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
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.httpclient.ChunkedInputStream;

import de.anomic.data.htmlTools;
import de.anomic.data.userDB;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverHandler;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;


/**
 * Instances of this class can be passed as argument to the serverCore.
 * The generic server dispatches HTTP commands and calls the
 * method GET, HEAD or POST in this class
 * these methods parse the command line and decide wether to call
 * a proxy servlet or a file server servlet 
 */
public final class httpd implements serverHandler, Cloneable {
    
    /**
     * <p><code>public static final String <strong>ADMIN_ACCOUNT_B64MD5</strong> = "adminAccountBase64MD5"</code></p>
     * <p>Name of the setting holding the authentification hash for the static <code>admin</code>-account. It is calculated
     * by first encoding <code>username:password</code> as Base64 and hashing it using {@link serverCodings#encodeMD5Hex(String)}.</p>
     */
    public static final String ADMIN_ACCOUNT_B64MD5 = "adminAccountBase64MD5";
    
    public static final int ERRORCASE_MESSAGE = 4;
    public static final int ERRORCASE_FILE = 5;
    public static httpdAlternativeDomainNames alternativeResolver = null;
    
    /**
     * A hashset containing extensions that indicate content that should not be transported
     * using zipped content encoding
     * @see #shallTransportZipped(String)
     */
     
     //TODO: Load this from a file
    private static final HashSet<String> disallowZippedContentEncoding = new HashSet<String>(Arrays.asList(new String[]{
            ".gz", ".tgz", ".jpg", ".jpeg", ".gif", ".zip", ".rar", ".bz2", ".lha", ".jar", ".rpm", ".arc", ".arj", ".wmv", ".png", ".ico", ".bmp"
    }));    
    
    // static objects
    public static final String vDATE = "<<REPL>>";
    public static final String copyright = "[ HTTP SERVER: AnomicHTTPD v" + vDATE + " by Michael Christen / www.anomic.de ]";
    public static final String hline = "-------------------------------------------------------------------------------";
    
    public static final HashMap<String, String> reverseMappingCache = new HashMap<String, String>();
    private static volatile plasmaSwitchboard switchboard = null;
    private static String virtualHost = null;
    
    public static boolean keepAliveSupport = false;
    private static HashMap<String, Long> YaCyHopAccessRequester = new HashMap<String, Long>();
    private static HashMap<String, Long> YaCyHopAccessTargets = new HashMap<String, Long>();
    
    // class objects
    private serverCore.Session session;  // holds the session object of the calling class
    private InetAddress userAddress;     // the address of the client
    
    // for authentication
    private boolean use_proxyAccounts = false;
	private boolean proxyAccounts_init = false; // is use_proxyAccounts set?
    private String serverAccountBase64MD5;
    private String clientIP;
    private boolean allowProxy;
    private boolean allowServer;
    private boolean allowYaCyHop;
    
    // the connection properties
    private final Properties prop = new Properties();
    
    private int emptyRequestCount = 0;
    private int keepAliveRequestCount = 0;
    
    // needed for logging
    private final static serverLog log = new serverLog("HTTPD");

    // class methods
    public httpd(final serverSwitch<?> s) {
        // handler info
        httpd.switchboard = (plasmaSwitchboard)s;
        httpd.virtualHost = switchboard.getConfig("fileHost","localhost");
        
        // authentication: by default none
        this.proxyAccounts_init = false;
        this.serverAccountBase64MD5 = null;
        this.clientIP = null;
        
        // configuring keep alive support
        keepAliveSupport = Boolean.valueOf(switchboard.getConfig("connectionKeepAliveSupport","false")).booleanValue();
    }
    
    public Properties getConProp() {
        return this.prop;
    }
    
    /**
     * Can be used to reset this {@link serverHandler} oject so that
     * it can be reused for further connections
     * @see de.anomic.server.serverHandler#reset()
     */
    public void reset()  {
        this.session = null;
        this.userAddress = null;
        this.allowProxy = false;
        this.allowServer = false;
        this.allowYaCyHop = false;
        this.proxyAccounts_init = false;
        this.serverAccountBase64MD5 = null;
        this.clientIP = null;
        this.prop.clear();
        
        this.emptyRequestCount = 0;
        this.keepAliveRequestCount = 0;
    }    


    /**
     * Must be called at least once, but can be called again to re-use the object.
     * @see de.anomic.server.serverHandler#initSession(de.anomic.server.serverCore.Session)
     */
    public void initSession(final serverCore.Session newsession) throws IOException {
        this.session = newsession;
        this.userAddress = session.userAddress; // client InetAddress
        this.clientIP = this.userAddress.getHostAddress();
        if (this.userAddress.isAnyLocalAddress()) this.clientIP = "localhost";
        if (this.clientIP.startsWith("0:0:0:0:0:0:0:1")) this.clientIP = "localhost";
        if (this.clientIP.startsWith("127.")) this.clientIP = "localhost";
        final String proxyClient = switchboard.getConfig("proxyClient", "*");
        final String serverClient = switchboard.getConfig("serverClient", "*");

        this.allowProxy = (proxyClient.equals("*")) ? true : match(this.clientIP, proxyClient);
        this.allowServer = (serverClient.equals("*")) ? true : match(this.clientIP, serverClient);
        this.allowYaCyHop = switchboard.getConfigBool("YaCyHop", false);

        // check if we want to allow this socket to connect us
        if (!(this.allowProxy || this.allowServer || this.allowYaCyHop)) {
            final String errorMsg = "CONNECTION FROM " + this.userAddress.getHostName() + " [" + this.clientIP + "] FORBIDDEN";
            this.log.logWarning(errorMsg);
            throw new IOException(errorMsg);
        }

        this.proxyAccounts_init = false;
        this.serverAccountBase64MD5 = null;
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
    
    public String greeting() { // OBLIGATORIC FUNCTION
        // a response line upon connection is send to client
        // if no response line is wanted, return "" or null
        return null;
    }
    
    public String error(final Throwable e) { // OBLIGATORIC FUNCTION
        // return string in case of any error that occurs during communication
        // is always (but not only) called if an IO-dependent exception occurrs.
        this.log.logSevere("Unexpected Error. " + e.getClass().getName(),e);
        final String message = e.getMessage();
        if (message.indexOf("heap space") > 0) e.printStackTrace();
        return "501 Exception occurred: " + message;
    }
    
    /**
     * This function is used to determine if a persistent connection was requested by the client.
     * @param header the received http-headers
     * @return <code>true</code> if a persistent connection was requested or <code>false</code> otherwise
     */
    private boolean handlePersistentConnection(final httpHeader header) {
        
        if (!keepAliveSupport) {
            this.prop.put(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
            return false;
        }
        
        // getting the http version that is used by the client
        final String httpVersion = this.prop.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
        
        // managing keep-alive: in HTTP/0.9 and HTTP/1.0 every connection is closed
        // afterwards. In HTTP/1.1 (and above, in the future?) connections are
        // persistent by default, but closed with the "Connection: close"
        // property.
        boolean persistent = !(httpVersion.equals(httpHeader.HTTP_VERSION_0_9) || httpVersion.equals(httpHeader.HTTP_VERSION_1_0));
        if (((String)header.get(httpHeader.CONNECTION, "keep-alive")).toLowerCase().indexOf("close") != -1 || 
            ((String)header.get(httpHeader.PROXY_CONNECTION, "keep-alive")).toLowerCase().indexOf("close") != -1) {
            persistent = false;
        }        
        
        final String transferEncoding = (String) header.get(httpHeader.TRANSFER_ENCODING, "identity");
        final boolean isPostRequest = this.prop.getProperty(httpHeader.CONNECTION_PROP_METHOD).equals(httpHeader.METHOD_POST);
        final boolean hasContentLength = header.containsKey(httpHeader.CONTENT_LENGTH);
        final boolean hasTransferEncoding = header.containsKey(httpHeader.TRANSFER_ENCODING) && !transferEncoding.equalsIgnoreCase("identity");
        
        // if the request does not contain a content-length we have to close the connection
        // independently of the value of the connection header
        if (persistent && isPostRequest && !(hasContentLength || hasTransferEncoding)) 
        	  this.prop.put(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
        else  this.prop.put(httpHeader.CONNECTION_PROP_PERSISTENT,persistent?"keep-alive":"close");
        
        return persistent;
    }
    
    public static int staticAdminAuthenticated(final String authorization, final serverSwitch<?> sw) {
        // the authorization string must be given with the truncated 6 bytes at the beginning
        if (authorization == null) return 1;
        //if (authorization.length() < 6) return 1; // no authentication information given
        final String adminAccountBase64MD5 = sw.getConfig(ADMIN_ACCOUNT_B64MD5, "");
        if (adminAccountBase64MD5.length() == 0) return 2; // no password stored
        if (adminAccountBase64MD5.equals(serverCodings.encodeMD5Hex(authorization))) return 4; // hard-authenticated, all ok
        return 1;
    }
    
    private boolean handleServerAuthentication(final httpHeader header) throws IOException {
        // getting the http version that is used by the client
        final String httpVersion = this.prop.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER, "HTTP/0.9");        
        
        // reading the authentication settings from switchboard
        if (this.serverAccountBase64MD5 == null) 
            this.serverAccountBase64MD5 = switchboard.getConfig("serverAccountBase64MD5", "");
        
        if (this.serverAccountBase64MD5.length() > 0) {
            final String auth = header.get(httpHeader.AUTHORIZATION);
            if (auth == null) {
                // authorization requested, but no authorizeation given in header. Ask for authenticate:
                this.session.out.write((httpVersion + " 401 log-in required" + serverCore.CRLF_STRING +
                        httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.CRLF_STRING +
                        serverCore.CRLF_STRING).getBytes());
                this.session.out.write((httpHeader.CONTENT_LENGTH + ": 0\r\n").getBytes());
                this.session.out.write("\r\n".getBytes());
                return false;
            } else if (!this.serverAccountBase64MD5.equals(serverCodings.encodeMD5Hex(auth.trim().substring(6)))) {
                // wrong password given: ask for authenticate again
                log.logInfo("Wrong log-in for account 'server' in HTTPD.GET " + this.prop.getProperty("PATH") + " from IP " + this.clientIP);
                this.session.out.write((httpVersion + " 401 log-in required" + serverCore.CRLF_STRING +
                        httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + 
                        serverCore.CRLF_STRING).getBytes());
                this.session.out.write((httpHeader.CONTENT_LENGTH + ": 0\r\n").getBytes());
                this.session.out.write("\r\n".getBytes());                
                this.session.out.flush();
                return false;
            }
        }
        return true;
    }
    
    private boolean handleYaCyHopAuthentication(final httpHeader header) {
        // check if the user has allowed that his/her peer is used for hops
        if (!this.allowYaCyHop) return false;
        
        // proxy hops must identify with 4 criteria:
        
        // the accessed port must not be port 80
        final String host = this.prop.getProperty(httpHeader.CONNECTION_PROP_HOST);
        if (host == null) return false;
        int pos;
        if ((pos = host.indexOf(":")) < 0) {
            // default port 80
            return false; // not allowed
        }
        if (Integer.parseInt(host.substring(pos + 1)) == 80) return false;
        
        // the access path must be into the yacy protocol path; it must start with 'yacy'
        if (!(this.prop.getProperty(httpHeader.CONNECTION_PROP_PATH, "").startsWith("/yacy/"))) return false;

        // the accessing client must identify with user:password, where
        // user = addressed peer name
        // pw = addressed peer hash (b64-hash)
        final String auth = (String) header.get(httpHeader.PROXY_AUTHORIZATION,"xxxxxx");
        if (alternativeResolver != null) {
            final String test = kelondroBase64Order.standardCoder.encodeString(alternativeResolver.myName() + ":" + alternativeResolver.myID());
            if (!test.equals(auth.trim().substring(6))) return false;
        }
        
        // the accessing client must use a yacy user-agent
        if (!(((String) header.get(httpHeader.USER_AGENT,"")).startsWith("yacy"))) return false;
        
        // furthermore, YaCy hops must not exceed a specific access frequency
        
        // check access requester frequency: protection against DoS against this peer
        final String requester = this.prop.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP);
        if (requester == null) return false;
        if (lastAccessDelta(YaCyHopAccessRequester, requester) < 10000) return false;
        YaCyHopAccessRequester.put(requester, Long.valueOf(System.currentTimeMillis()));
        
        // check access target frequecy: protection against DoS from a single peer by several different requesters
        if (lastAccessDelta(YaCyHopAccessTargets, host) < 3000) return false;
        YaCyHopAccessTargets.put(host, Long.valueOf(System.currentTimeMillis()));
        
        // passed all tests
        return true;
    }

    private static long lastAccessDelta(final HashMap<String, Long> accessTable, final String domain) {
        final Long lastAccess = accessTable.get(domain);
        if (lastAccess == null) return Long.MAX_VALUE; // never accessed
        return System.currentTimeMillis() - lastAccess.longValue();
    }
    
    private boolean handleProxyAuthentication(final httpHeader header) throws IOException {
        // getting the http version that is used by the client
        final String httpVersion = this.prop.getProperty("HTTP", "HTTP/0.9");            
        
        // reading the authentication settings from switchboard
        if (!this.proxyAccounts_init) {
            this.use_proxyAccounts = switchboard.getConfigBool("use_proxyAccounts", false);
			this.proxyAccounts_init = true; // is initialised
		}
        
        if (this.use_proxyAccounts) {
            final String auth = (String) header.get(httpHeader.PROXY_AUTHORIZATION,"xxxxxx");    
            userDB.Entry entry=switchboard.userDB.ipAuth(this.clientIP);
			if(entry == null){
				entry=switchboard.userDB.proxyAuth(auth, this.clientIP);
			}
            if(entry != null){
                final int returncode=entry.surfRight();
			    if(returncode==userDB.Entry.PROXY_ALLOK){
				    return true;
				}
                final serverObjects tp=new serverObjects();
                if(returncode==userDB.Entry.PROXY_TIMELIMIT_REACHED){
                    tp.put("limit", "1");//time per day
                    tp.put("limit_timelimit", entry.getTimeLimit());
                    sendRespondError(this.prop, this.session.out, 403, "Internet-Timelimit reached", new File("proxymsg/proxylimits.inc"), tp, null);
                }else if(returncode==userDB.Entry.PROXY_NORIGHT){
                    tp.put("limit", "0");
                    sendRespondError(this.prop, this.session.out, 403, "Proxy use forbidden", new File("proxymsg/proxylimits.inc"), tp, null);
                }
                return false;
			}
            // ask for authenticate
            this.session.out.write((httpVersion + " 407 Proxy Authentication Required" + serverCore.CRLF_STRING +
				httpHeader.PROXY_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.CRLF_STRING).getBytes());
            this.session.out.write((httpHeader.CONTENT_LENGTH + ": 0\r\n").getBytes());
            this.session.out.write("\r\n".getBytes());                   
            this.session.out.flush();
            return false;
        }
        
        return true;
    }
    
    public Boolean UNKNOWN(final String requestLine) throws IOException {
        
        int pos;
        String unknownCommand = null, args = null;
        if ((pos = requestLine.indexOf(" ")) > 0) {
            unknownCommand = requestLine.substring(0,pos);
            args = requestLine.substring(pos+1);
        } else {
            unknownCommand = requestLine;
            args = "";
        }
        
        parseRequestLine(unknownCommand, args);
        //String httpVersion = this.prop.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER,"HTTP/0.9");
        
        sendRespondError(this.prop,this.session.out,0,501,null,unknownCommand + " method not implemented",null);
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean EMPTY(final String arg) throws IOException {
        if (++this.emptyRequestCount > 10) return serverCore.TERMINATE_CONNECTION;
        return serverCore.RESUME_CONNECTION;
    }
    
    public Boolean TRACE() throws IOException {
        sendRespondError(this.prop,this.session.out,0,501,null,"TRACE method not implemented",null);
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean OPTIONS() throws IOException {
        sendRespondError(this.prop,this.session.out,0,501,null,"OPTIONS method not implemented",null);
        return serverCore.TERMINATE_CONNECTION;
    }    
    
    
    public Boolean GET(final String arg) {
        try {
            // parsing the http request line
            parseRequestLine(httpHeader.METHOD_GET,arg);
            
            // we now know the HTTP version. depending on that, we read the header            
            final String httpVersion = this.prop.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER, httpHeader.HTTP_VERSION_0_9);
            final httpHeader header = (httpVersion.equals(httpHeader.HTTP_VERSION_0_9)) 
            			      ? new httpHeader(reverseMappingCache) 
                              : httpHeader.readHeader(this.prop,this.session);                  
            
            // handling transparent proxy support
            httpHeader.handleTransparentProxySupport(header, this.prop, virtualHost, httpdProxyHandler.isTransparentProxy); 
            
            // determines if the connection should be kept alive
            handlePersistentConnection(header);
            
            if (this.prop.getProperty(httpHeader.CONNECTION_PROP_HOST).equals(virtualHost)) {
                // pass to server
                if (this.allowServer) {
                    if (this.handleServerAuthentication(header)) {
                        httpdFileHandler.doGet(this.prop, header, this.session.out);
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    this.session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this server, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("serverClient", "*") + serverCore.CRLF_STRING).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (((this.allowYaCyHop) && (handleYaCyHopAuthentication(header))) ||
                    ((this.allowProxy) && (handleProxyAuthentication(header)))) {
                    httpdProxyHandler.doGet(this.prop, header, this.session.out);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    this.session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            
            return this.prop.getProperty(httpHeader.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (final Exception e) {
            logUnexpectedError(e);
            return serverCore.TERMINATE_CONNECTION;
        } finally {
            this.doUserAccounting(this.prop);
        }
    }
    
    private void logUnexpectedError(final Exception e) {
        if (e instanceof InterruptedException) {
            this.log.logInfo("Interruption detected");
        } else {
            final String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.startsWith("Socket closed")) {
                    this.log.logInfo("httpd shutdown detected ...");
                } else if ((errorMsg.startsWith("Broken pipe") || errorMsg.startsWith("Connection reset"))) {
                    // client closed the connection, so we just end silently
                    this.log.logInfo("Client unexpectedly closed connection");
                } else if (errorMsg.equals("400 Bad request")) {
                	this.log.logInfo("Bad client request.");
                } else {
                    this.log.logSevere("Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage(),e);
                }
            } else {
                this.log.logSevere("Unexpected Error. " + e.getClass().getName(),e);
            }
        }        
    }

    public Boolean HEAD(final String arg) {
        try {
            parseRequestLine(httpHeader.METHOD_HEAD,arg);
            
            // we now know the HTTP version. depending on that, we read the header
            httpHeader header;
            final String httpVersion = this.prop.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER, httpHeader.HTTP_VERSION_0_9);
            if (httpVersion.equals(httpHeader.HTTP_VERSION_0_9)) header = new httpHeader(reverseMappingCache);
            else  header = httpHeader.readHeader(this.prop,this.session);
            
            // handle transparent proxy support
            httpHeader.handleTransparentProxySupport(header, this.prop, virtualHost, httpdProxyHandler.isTransparentProxy);
            
            // determines if the connection should be kept alive
            handlePersistentConnection(header);
            
            // return multi-line message
            if (this.prop.getProperty(httpHeader.CONNECTION_PROP_HOST).equals(virtualHost)) {
                // pass to server
                if (allowServer) {
                    if (handleServerAuthentication(header)) {
                        httpdFileHandler.doHead(prop, header, this.session.out);
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" +
                            serverCore.CRLF_STRING).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (((this.allowYaCyHop) && (handleYaCyHopAuthentication(header))) ||
                    ((this.allowProxy) && (handleProxyAuthentication(header)))) {
                    httpdProxyHandler.doHead(prop, header, this.session.out);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" +
                            serverCore.CRLF_STRING).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            return this.prop.getProperty(httpHeader.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (final Exception e) {
            logUnexpectedError(e);
            return serverCore.TERMINATE_CONNECTION;
        } finally {
            this.doUserAccounting(this.prop);
        }
    }
    
    public Boolean POST(final String arg) {
        try {
            parseRequestLine(httpHeader.METHOD_POST,arg);
            
            // we now know the HTTP version. depending on that, we read the header
            httpHeader header;
            final String httpVersion = this.prop.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER, httpHeader.HTTP_VERSION_0_9);
            if (httpVersion.equals(httpHeader.HTTP_VERSION_0_9))  header = new httpHeader(reverseMappingCache);
            else header = httpHeader.readHeader(this.prop,this.session);
            
            // handle transfer-coding
            final InputStream sessionIn;
            final String transferEncoding = header.get(httpHeader.TRANSFER_ENCODING);
            if (transferEncoding != null) {
                if (!httpHeader.HTTP_VERSION_1_1.equals(httpVersion)) {
                    this.log.logWarning("client "+ session.getName() +" uses transfer-coding with HTTP version "+ httpVersion +"!");
                }
                if("chunked".equalsIgnoreCase(header.get(httpHeader.TRANSFER_ENCODING))) {
                    sessionIn = new ChunkedInputStream(this.session.in);
                } else {
                    // "A server which receives an entity-body with a transfer-coding it does
                    // not understand SHOULD return 501 (Unimplemented), and close the
                    // connection." [RFC 2616, section 3.6]
                    session.out.write((httpVersion + " 501 transfer-encoding not implemented" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you send a transfer-encoding to this server, which is not supported: " + transferEncoding + serverCore.CRLF_STRING).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                sessionIn = this.session.in;
            }
            
            // handle transparent proxy support
            httpHeader.handleTransparentProxySupport(header, this.prop, virtualHost, httpdProxyHandler.isTransparentProxy);
            
            // determines if the connection should be kept alive
            handlePersistentConnection(header);
            
            // return multi-line message
            if (prop.getProperty(httpHeader.CONNECTION_PROP_HOST).equals(virtualHost)) {
                // pass to server
                if (allowServer) {
                    if (handleServerAuthentication(header)) {
                        httpdFileHandler.doPost(prop, header, this.session.out, sessionIn);
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this server, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("serverClient", "*") + serverCore.CRLF_STRING).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (((this.allowYaCyHop) && (handleYaCyHopAuthentication(header))) ||
                    ((this.allowProxy) && (handleProxyAuthentication(header)))) {
                    httpdProxyHandler.doPost(prop, header, this.session.out, sessionIn);
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            //return serverCore.RESUME_CONNECTION;
            return this.prop.getProperty(httpHeader.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (final Exception e) {
            logUnexpectedError(e);
            return serverCore.TERMINATE_CONNECTION;
        } finally {
            this.doUserAccounting(this.prop);
        }
    }
    
    
    public Boolean CONNECT(String arg) throws IOException {
        // establish a ssh-tunneled http connection
        // this is to support https   
        
        // parse HTTP version
        int pos = arg.indexOf(" ");
        String httpVersion = "HTTP/1.0";
        if (pos >= 0) {
            httpVersion = arg.substring(pos + 1);
            arg = arg.substring(0, pos);
        }
        prop.setProperty(httpHeader.CONNECTION_PROP_HTTP_VER, httpVersion);
        
        // parse hostname and port
        prop.setProperty(httpHeader.CONNECTION_PROP_HOST, arg);
        pos = arg.indexOf(":");
        int port = 443;
        if (pos >= 0) {
            port = Integer.parseInt(arg.substring(pos + 1));
            //the offcut: arg = arg.substring(0, pos);
        }       
        
        // setting other connection properties
        prop.setProperty(httpHeader.CONNECTION_PROP_CLIENTIP, this.clientIP);
        prop.setProperty(httpHeader.CONNECTION_PROP_METHOD, httpHeader.METHOD_CONNECT);
        prop.setProperty(httpHeader.CONNECTION_PROP_PATH, "/");
        prop.setProperty(httpHeader.CONNECTION_PROP_EXT, "");
        prop.setProperty(httpHeader.CONNECTION_PROP_URL, "");        
        
        // parse remaining lines
        final httpHeader header = httpHeader.readHeader(this.prop,this.session);               
        
        if (!(allowProxy)) {
            // not authorized through firewall blocking (ip does not match filter)          
            session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING).getBytes());
            return serverCore.TERMINATE_CONNECTION;
        }        
        
        if (port != 443 && switchboard.getConfig("secureHttps", "true").equals("true")) {
            // security: connection only to ssl port
            // we send a 403 (forbidden) error back
            session.out.write((httpVersion + " 403 Connection to non-443 forbidden" +
                    serverCore.CRLF_STRING + serverCore.CRLF_STRING).getBytes());
            return serverCore.TERMINATE_CONNECTION;
        }
        
        // pass to proxy
        if (((this.allowYaCyHop) && (handleYaCyHopAuthentication(header))) ||
            ((this.allowProxy) && (this.handleProxyAuthentication(header)))) {
            httpdProxyHandler.doConnect(prop, header, this.session.in, this.session.out);
        } else {
            // not authorized through firewall blocking (ip does not match filter)
            session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.CRLF_STRING + serverCore.CRLF_STRING + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.CRLF_STRING).getBytes());
        }
        
        return serverCore.TERMINATE_CONNECTION;
    }
    
    private final void parseRequestLine(final String cmd, final String s) {
        
        // parsing the header
        httpHeader.parseRequestLine(cmd,s,this.prop,virtualHost);
        
        // track the request
        final String path = this.prop.getProperty(httpHeader.CONNECTION_PROP_URL);
        final String args = this.prop.getProperty(httpHeader.CONNECTION_PROP_ARGS, "");
        switchboard.track(this.userAddress.getHostName(), (args.length() > 0) ? path + "?" + args : path);
        
        // reseting the empty request counter
        this.emptyRequestCount = 0;
        
        // counting the amount of received requests within this permanent connection
        this.prop.setProperty(httpHeader.CONNECTION_PROP_KEEP_ALIVE_COUNT, Integer.toString(++this.keepAliveRequestCount));
        
        // setting the client-IP
        this.prop.setProperty(httpHeader.CONNECTION_PROP_CLIENTIP, this.clientIP);
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
            int bytesRead = in.read(buffer);
            assert bytesRead == buffer.length;
        // parsing post request bodies which are gzip content-encoded
        } else {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            serverFileUtils.copy(in,bout);
            buffer = bout.toByteArray();
            bout.close(); bout = null;
        }
        
        final int argc = parseArgs(args, new String(buffer, "UTF-8"));
        buffer = null;
        return argc;
    }
    
    public static int parseArgs(final serverObjects args, String argsString) {
        // this parses a arg string that can either be attached to a URL query
        // or can be given as result of a post method
        // the String argsString is supposed to be constructed as
        // <key1>=<value1>'&'<key2>=<value2>'&'<key3>=<value3>
        // the calling function must strip off a possible leading '?' char
        if (argsString.length() == 0) return 0;
        argsString = argsString + "&"; // for technical reasons
        int sep;
        int eqp;
        int argc = 0;
        // Textfield1=default+value+Textfield+1&Textfield2=default+value+Textfield+2&selection1=sel1&selection2=othervalue1&selection2=sel2&selection3=sel3&Menu1=SubEnry11&radio1=button1&check1=button2&check1=button3&hidden1=&sButton1=enter+%281%29
        while (argsString.length() > 0) {
            eqp = argsString.indexOf("=");
            sep = argsString.indexOf("&");
            if ((eqp <= 0) || (sep <= 0)) break;
            // resulting equations are inserted into the property args with leading '&'
            args.put(parseArg(argsString.substring(0, eqp)), parseArg(argsString.substring(eqp + 1, sep)));
            argsString = argsString.substring(sep + 1);
            argc++;
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
                        final int bh = Integer.parseInt(s.substring(pos + 2, pos + 4), 16);
                        final int bl = Integer.parseInt(s.substring(pos + 4, pos + 6), 16);
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
        
        try {
            return new String(baos.toByteArray(), "UTF-8");
        } catch (final UnsupportedEncodingException e) { return null; }
    }
    
    // 06.01.2007: decode HTML entities by [FB]
    public static String decodeHtmlEntities(String s) {
        // replace all entities defined in wikiCode.characters and htmlentities
        s = htmlTools.decodeHtml2Unicode(s);
        
        // replace all other 
        final CharArrayWriter b = new CharArrayWriter(s.length());
        int end;
        for (int i=0; i<s.length(); i++) {
            if (s.charAt(i) == '&' && (end = s.indexOf(';', i + 1)) > i) {
                if (s.charAt(i + 1) == '#') {                           // &#1234; symbols
                    b.write(Integer.parseInt(s.substring(i + 2, end)));
                    i += end - i;
                } else {                                                // 'named' smybols
                    log.logFine("discovered yet unimplemented HTML entity '" + s.substring(i, end + 1) + "'");
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
     * @param in
     * @param length
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public static HashMap<String, byte[]> parseMultipart(final httpHeader header, final serverObjects args, final InputStream in, final int length)
            throws IOException {
        // read all data from network in memory
        byte[] buffer = serverFileUtils.read(in);
        // parse data in memory
        RequestContext request = new yacyContextRequest(header, new ByteArrayInputStream(buffer));

        // check information
        if (!FileUploadBase.isMultipartContent(request)) {
            throw new IOException("the request is not a multipart-message!");
        }

        // format information for further usage
        FileItemFactory factory = new DiskFileItemFactory();
        FileUpload upload = new FileUpload(factory);
        List<FileItem> items;
        try {
            items = upload.parseRequest(request);
        } catch (FileUploadException e) {
            throw new IOException("FileUploadException " + e.getMessage());
        }

        final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
        int formFieldCount = 0;
        for (FileItem item : items) {
            if (item.isFormField()) {
                // simple text
                if (item.getContentType() == null || !item.getContentType().contains("charset")) {
                    // old yacy clients use their local default charset, on most systems UTF-8 (I hope ;)
                    args.put(item.getFieldName(), item.getString("UTF-8"));
                } else {
                    // use default encoding (given as header or ISO-8859-1)
                    args.put(item.getFieldName(), item.getString());
                }
                formFieldCount++;
            } else {
                // file
                args.put(item.getFieldName(), item.getName());
                final byte[] fileContent = serverFileUtils.read(item.getInputStream());
                item.getInputStream().close();
                files.put(item.getFieldName(), fileContent);
            }
        }
        header.put("ARGC", String.valueOf(items.size())); // store argument count

        return files;
    }
//        // FIXME this is a quick hack using a previously coded parseMultipart based on a buffer
//        // should be replaced sometime by a 'right' implementation
//
//        byte[] buffer = null;
//        
//        // parsing post request bodies with a given length
//        if (length != -1) { 
//            buffer = new byte[length];
//            int c, a = 0;
//            while (a < length) {
//                c = in.read(buffer, a, length - a);
//                if (c <= 0) break;
//                a += c;
//            }
//        // parsing post request bodies which are gzip content-encoded
//        } else {
//            serverByteBuffer bout = new serverByteBuffer();
//            serverFileUtils.copy(in,bout);
//            buffer = bout.getBytes();
//            bout.close(); bout = null;
//        }
//        
//        //System.out.println("MULTIPART-BUFFER=" + new String(buffer));
//        final HashMap<String, byte[]> files = parseMultipart(header, args, buffer);
//        buffer = null;
//        return files;
//    }
//    
//    public static HashMap<String, byte[]> parseMultipart(final httpHeader header, final serverObjects args, final byte[] buffer) throws IOException {
//        // we parse a multipart message and put results into the properties
//        // find/identify boundary marker
//        //System.out.println("DEBUG parseMultipart = <<" + new String(buffer) + ">>");
//        final String s = header.get(httpHeader.CONTENT_TYPE);
//        if (s == null) return null;
//        int q;
//        int p = s.toLowerCase().indexOf("boundary=");
//        if (p < 0) throw new IOException("boundary marker in multipart not found");
//        // boundaries start with additional leading "--", see RFC1867
//        final byte[] boundary = ("--" + s.substring(p + 9)).getBytes();
//        
//        // eat up first boundary
//        // the buffer must start with a boundary
//        byte[] line = readLine(0, buffer);
//        int pos = nextPos;
//        if ((line == null) || (!(equals(line, 0, boundary, 0, boundary.length))))
//            throw new IOException("boundary not recognized: " + ((line == null) ? "NULL" : new String(line, "UTF-8")) + ", boundary = " + new String(boundary));
//        
//        // we need some constants
//        final byte[] namec = "name=".getBytes();
//        final byte[] filenamec = "filename=".getBytes();
//        //byte[] semicolonc = (new String(";")).getBytes();
//        final byte[] quotec = new byte[] {(byte) '"'};
//        
//        // now loop over boundaries
//        byte [] name;
//        byte [] filename;
//        final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
//        int argc = 0;
//        //System.out.println("DEBUG: parsing multipart body:" + new String(buffer));
//        while (pos < buffer.length) { // boundary enumerator
//            // here the 'pos' marker points to the first line in a section after a boundary line
//            line = readLine(pos, buffer); pos = nextPos;
//            // termination if line is empty
//            if (line.length == 0) break;
//            // find name tag in line
//            p = indexOf(0, line, namec);
//            if (p < 0) throw new IOException("tag name in marker section not found: '" + new String(line, "UTF-8") + "'"); // a name tag must always occur
//            p += namec.length + 1; // first position of name value
//            q = indexOf(p, line, quotec);
//            if (q < 0) throw new IOException("missing quote in name tag: '" + new String(line, "UTF-8") + "'");
//            name = new byte[q - p];
//            java.lang.System.arraycopy(line, p, name, 0, q - p);
//            // if this line has also a filename attribute, read it
//            p = indexOf(q, line, filenamec);
//            if (p > 0) {
//                p += filenamec.length + 1; // first position of name value
//                q = indexOf(p, line, quotec);
//                if (q < 0) {
//                	log.logWarning("quote of filename tag not found, searching in next line");
//                	// append next line to this
//                	final byte[] nextline = readLine(pos, buffer); pos = nextPos;
//                	final byte[] holeLine = new byte[line.length + nextline.length];
//                	System.arraycopy(line, 0, holeLine, 0, line.length);
//                	System.arraycopy(nextline, 0, holeLine, line.length, nextline.length);
//                	p = indexOf(q, holeLine, quotec);
//                	if(p > 0)
//                		throw new IOException("missing quote in filename tag: '" + new String(line) + "'");
//                }
//                filename = new byte[q - p];
//                java.lang.System.arraycopy(line, p, filename, 0, q - p);
//            } else filename = null;
//            // we have what we need. more information lines may follow, but we omit parsing them
//            // we just skip until an empty line is reached
//            while (pos < buffer.length) { // line skiping
//                line = readLine(pos, buffer); pos = nextPos;
//                if ((line == null) || (line.length == 0)) break;
//            }
//            // depending on the filename tag exsistence, read now either a value for the name
//            // or a complete uploaded file
//            // to know the exact length of the value, we must identify the next boundary
//            p = indexOf(pos, buffer, boundary);
//            
//            // if we can't find another boundary, then this is an error in the input
//            if (p < 0) {
//                log.logSevere("ERROR in PUT body: no ending boundary. probably missing values");
//                break;
//            }
//            
//            // we don't know if the value is terminated by LF, CR or CRLF
//            // (it's suppose to be CRLF, but we want to be lazy about wrong terminations)
//            if (buffer[p - 2] == serverCore.CR) // ERROR: IndexOutOfBounds: -2
//                /* CRLF */ q = p - 2;
//            else
//                /* CR or LF only */ q = p - 1;
//            // the above line is wrong if we uploaded a file that has a CR as it's last byte
//            // and the client's line termination symbol is only a CR or LF (which would be incorrect)
//            // the value is between 'pos' and 'q', while the next marker is 'p'
//            line = new byte[q - pos];
//            java.lang.System.arraycopy(buffer, pos, line, 0, q - pos);
//            // in the 'line' variable we have now either a normal value or an uploadef file
//            if (filename == null) {
//                args.put(new String(name, "UTF-8"), new String(line, "UTF-8"));
//            } else {
//                // we store the file in a hashtable.
//                // we use the same key to address the file in the hashtable as we
//                // use to address the filename in the properties, but without leading '&'
//                args.put(new String(name, "UTF-8"), new String(filename, "UTF-8"));
//                files.put(new String(name, "UTF-8"), line);
//            }
//            argc++;
//            // finally, read the next boundary line
//            line = readLine(p, buffer);
//            pos = nextPos;
//        }
//        header.put("ARGC", Integer.toString(argc)); // store argument count
//        return files;
//    }
    
    /*
     ------------1090358578442
     Content-Disposition: form-data; name="youare"
     
     Ty2F86ekSWM5
     ------------1090358578442
     Content-Disposition: form-data; name="key"
     
     6EkPPOl7
     ------------1090358578442
     Content-Disposition: form-data; name="iam"
     
     HnTvzwV7SCJR
     ------------1090358578442
     Content-Disposition: form-data; name="process"
     
     permission
     ------------1090358578442
     
     */
    
    /**
     * wraps the request into a org.apache.commons.fileupload.RequestContext
     * 
	 * @author danielr
	 * @since 07.08.2008
	 */
	private static class yacyContextRequest implements RequestContext {
		private final httpHeader header;
		private final InputStream inStream;
	
		/**
		 * creates a new yacyContextRequest
		 * 
		 * @param header
		 * @param in
		 */
		public yacyContextRequest(httpHeader header, InputStream in) {
			this.header = header;
			this.inStream = in;
		}
	
		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.apache.commons.fileupload.RequestContext#getCharacterEncoding()
		 */
		@Override
		public String getCharacterEncoding() {
			return header.getCharacterEncoding();
		}
	
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.apache.commons.fileupload.RequestContext#getContentLength()
		 */
		@Override
		public int getContentLength() {
			return (int) header.contentLength();
		}
	
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.apache.commons.fileupload.RequestContext#getContentType()
		 */
		@Override
		public String getContentType() {
			return header.get(httpHeader.CONTENT_TYPE);
		}
	
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.apache.commons.fileupload.RequestContext#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return inStream;
		}
	
	}

	static int nextPos = -1;        
    private static byte[] readLine(final int start, final byte[] array) {
        // read a string from an array; line ending is always CRLF
        // but we are also fuzzy with that: may also be only CR or LF
        // if no remaining CR, CRLF or LF can be found, return null
        if (start > array.length) return null;
        int pos = indexOf(start, array, serverCore.CRLF); nextPos = pos + 2;
        if (pos < 0) {pos = indexOf(start, array, new byte[] {serverCore.CR}); nextPos = pos + 1;}
        if (pos < 0) {pos = indexOf(start, array, new byte[] {serverCore.LF}); nextPos = pos + 1;}
        if (pos < 0) {nextPos = start; return null;}
        final byte[] result = new byte[pos - start];
        java.lang.System.arraycopy(array, start, result, 0, pos - start);
        return result;
    }
    
    public static int indexOf(final int start, final byte[] array, final byte[] pattern) {
        // return a position of a pattern in an array
        if (start > array.length - pattern.length) return -1;
        if (pattern.length == 0) return start;
        for (int pos = start; pos <= array.length - pattern.length; pos++)
            if ((array[pos] == pattern[0]) && (equals(array, pos, pattern, 0, pattern.length)))
                return pos;
        return -1;
    }
    
    public static boolean equals(final byte[] a, final int aoff, final byte[] b, final int boff, final int len) {
        //System.out.println("equals: a = " + new String(a) + ", aoff = " + aoff + ", b = " + new String(b) + ", boff = " + boff + ", length = " + len);
        if ((aoff + len > a.length) || (boff + len > b.length)) return false;
        for (int i = 0; i < len; i++) if (a[aoff + i] != b[boff + i]) return false;
        //System.out.println("TRUE!");
        return true;
    }
    
    public httpd clone() {
        return new httpd(switchboard);        
    }
    
    public static final void sendRespondBody(
            final OutputStream respond,
            final byte[] body
    ) throws IOException {
        respond.write(body);
        respond.flush();        
    }
    
    
    public static final void sendRespondError(
            final Properties conProp,
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
            final Properties conProp,
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
            final Properties conProp,
            final OutputStream respond,
            final int errorcase,
            final int httpStatusCode,            
            String httpStatusText,
            final String detailedErrorMsgText,
            final Object detailedErrorMsgFile,
            final serverObjects detailedErrorMsgValues,
            final Throwable stackTrace,
            httpHeader header
    ) throws IOException {
        
        FileInputStream fis = null;
        ByteArrayOutputStream o = null;
        try {
            // setting the proper http status message
            final String httpVersion = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER,"HTTP/1.1");
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals("HTTP/1.0") && httpHeader.http1_0.containsKey(Integer.toString(httpStatusCode))) 
                    httpStatusText = httpHeader.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals("HTTP/1.1") && httpHeader.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = httpHeader.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }
            
            // generating the desired request url
            String host = conProp.getProperty(httpHeader.CONNECTION_PROP_HOST);
            final String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH,"/");
            final String args = conProp.getProperty(httpHeader.CONNECTION_PROP_ARGS);
            final String method = conProp.getProperty(httpHeader.CONNECTION_PROP_METHOD);
            
            int port = 80;
            final int pos = host.indexOf(":");        
            if (pos != -1) {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
            
            String urlString;
            try {
                urlString = (new yacyURL((method.equals(httpHeader.METHOD_CONNECT)?"https":"http"), host, port, (args == null) ? path : path + "?" + args)).toString();
            } catch (final MalformedURLException e) {
                urlString = "invalid URL";
            }

            // set rewrite values
            final serverObjects tp = new serverObjects();

//            tp.put("host", serverCore.publicIP().getHostAddress());
//            tp.put("port", switchboard.getConfig("port", "8080"));

            final String clientIP = conProp.getProperty(httpHeader.CONNECTION_PROP_CLIENTIP, "127.0.0.1");

            // check if ip is local ip address
            final InetAddress hostAddress = serverDomains.dnsResolve(clientIP);
            if (hostAddress == null) {
                tp.put("host", serverDomains.myPublicLocalIP().getHostAddress());
                tp.put("port", serverCore.getPortNr(switchboard.getConfig("port", "8080")));
            } else if (hostAddress.isSiteLocalAddress() || hostAddress.isLoopbackAddress()) {
                tp.put("host", serverDomains.myPublicLocalIP().getHostAddress());
                tp.put("port", serverCore.getPortNr(switchboard.getConfig("port", "8080")));
            } else {
                tp.put("host", serverDomains.myPublicIP());
                tp.put("port", Integer.toString(serverCore.getPortNr(switchboard.getConfig("port", "8080"))));
            }

            // if peer has public address it will be used
            if (alternativeResolver != null) {
                tp.put("extAddress", alternativeResolver.myIP() + ":" + alternativeResolver.myPort());
            }
            // otherwise the local ip address will be used
            else {
                tp.put("extAddress", tp.get("host", "127.0.0.1") + ":" + tp.get("port", "8080"));
            }

            tp.put("peerName", (alternativeResolver == null) ? "" : alternativeResolver.myName());
            tp.put("errorMessageType", errorcase);
            tp.put("httpStatus",       Integer.toString(httpStatusCode) + " " + httpStatusText);
            tp.put("requestMethod",    conProp.getProperty(httpHeader.CONNECTION_PROP_METHOD));
            tp.put("requestURL",       urlString);

            switch (errorcase) {
                case ERRORCASE_FILE:
                    tp.put("errorMessageType_file", (detailedErrorMsgFile == null) ? "" : detailedErrorMsgFile.toString());
                    if ((detailedErrorMsgValues != null) && (detailedErrorMsgValues.size() > 0)) {
                        // rewriting the value-names and add the proper name prefix:
                        final Iterator<String> nameIter = detailedErrorMsgValues.keySet().iterator();
                        while (nameIter.hasNext()) {
                            final String name = nameIter.next();
                            tp.put("errorMessageType_" + name, detailedErrorMsgValues.get(name));
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
                tp.put("printStackTrace",1);
                final serverByteBuffer errorMsg = new serverByteBuffer(100);
                stackTrace.printStackTrace(new PrintStream(errorMsg));
                tp.put("printStackTrace_exception", stackTrace.toString());
                tp.put("printStackTrace_stacktrace", new String(errorMsg.getBytes(),"UTF-8"));
            } else {
                tp.put("printStackTrace", 0);
            }
            
            // Generated Tue, 23 Aug 2005 11:19:14 GMT by brain.wg (squid/2.5.STABLE3)
            // adding some system information
            final String systemDate = HttpClient.dateString(new Date());
            tp.put("date", systemDate);
            
            // rewrite the file
            final File htRootPath = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath","htroot"));
            
            httpTemplate.writeTemplate(
                    fis = new FileInputStream(new File(htRootPath, "/proxymsg/error.html")), 
                    o = new ByteArrayOutputStream(), 
                    tp, 
                    "-UNRESOLVED_PATTERN-".getBytes()
            );
            final byte[] result = o.toByteArray();
            o.close(); o = null;

            if(header == null)
                header = new httpHeader();
            header.put(httpHeader.CONNECTION_PROP_PROXY_RESPOND_STATUS, Integer.toString(httpStatusCode));
            header.put(httpHeader.DATE, systemDate);
            header.put(httpHeader.CONTENT_TYPE, "text/html");
            header.put(httpHeader.CONTENT_LENGTH, Integer.toString(result.length));
            header.put(httpHeader.PRAGMA, "no-cache");
            sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,httpStatusText,header);

            if (! method.equals(httpHeader.METHOD_HEAD)) {
                // write the array to the client
                serverFileUtils.copy(result, respond);
            }
            respond.flush();
        } catch (final Exception e) { 
            throw new IOException(e.getMessage());
        } finally {
            if (fis != null) try { fis.close(); } catch (final Exception e) {}
            if (o != null)   try { o.close();   } catch (final Exception e) {}
        }     
    }
    
    public static final void sendRespondHeader(
            final Properties conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode, 
            final String httpStatusText, 
            final long contentLength
    ) throws IOException { 
        sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,httpStatusText,null,contentLength,null,null,null,null,null);
    }
    
    public static final void sendRespondHeader(
            final Properties conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode, 
            final String httpStatusText, 
            final String contentType,
            final long contentLength,
            final Date moddate, 
            final Date expires,
            final httpHeader headers,
            final String contentEnc,
            final String transferEnc
    ) throws IOException {    
        sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,httpStatusText,contentType,contentLength,moddate,expires,headers,contentEnc,transferEnc,true);
    }

    public static final void sendRespondHeader(
            final Properties conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode,
            final String httpStatusText,
            String contentType,
            final long contentLength,
            Date moddate,
            final Date expires,
            httpHeader headers,
            final String contentEnc,
            final String transferEnc,
            final boolean nocache
    ) throws IOException {
        
        final String reqMethod = conProp.getProperty(httpHeader.CONNECTION_PROP_METHOD);
        
        if ((transferEnc != null) && !httpVersion.equals(httpHeader.HTTP_VERSION_1_1)) { 
            throw new IllegalArgumentException("Transfer encoding is only supported for http/1.1 connections. The current connection version is " + httpVersion);
        }

        if (!reqMethod.equals(httpHeader.METHOD_HEAD)){
            if (!conProp.getProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close").equals("close")) {
                if (transferEnc == null && contentLength < 0) {
                    throw new IllegalArgumentException("Message MUST contain a Content-Length or a non-identity transfer-coding header field.");
                }
            }
            if (transferEnc != null && contentLength >= 0) {
                throw new IllegalArgumentException("Messages MUST NOT include both a Content-Length header field and a non-identity transfer-coding.");
            }            
        }
        
        if(headers==null) headers = new httpHeader();
        final Date now = new Date(System.currentTimeMillis());
        
        headers.put(httpHeader.SERVER, "AnomicHTTPD (www.anomic.de)");
        headers.put(httpHeader.DATE, HttpClient.dateString(now));
        if (moddate.after(now)) moddate = now;
        headers.put(httpHeader.LAST_MODIFIED, HttpClient.dateString(moddate));
        
        if (nocache) {
            if (httpVersion.toUpperCase().equals(httpHeader.HTTP_VERSION_1_1)) headers.put(httpHeader.CACHE_CONTROL, "no-cache");
            else headers.put(httpHeader.PRAGMA, "no-cache");
        }
        
        if (contentType == null) 
            contentType = "text/html; charset=UTF-8";
        else if (contentType.startsWith("text/") && contentType.toLowerCase().indexOf("charset=")==-1)
            contentType +="; charset=UTF-8";
        headers.put(httpHeader.CONTENT_TYPE, contentType);  
        if (contentLength > 0)   headers.put(httpHeader.CONTENT_LENGTH, Long.toString(contentLength));
        //if (cookie != null)      headers.put(httpHeader.SET_COOKIE, cookie);
        if (expires != null)     headers.put(httpHeader.EXPIRES, HttpClient.dateString(expires));
        if (contentEnc != null)  headers.put(httpHeader.CONTENT_ENCODING, contentEnc);
        if (transferEnc != null) headers.put(httpHeader.TRANSFER_ENCODING, transferEnc);
        
        sendRespondHeader(conProp, respond, httpVersion, httpStatusCode, httpStatusText, headers);
    }
    
    public static final void sendRespondHeader(
            final Properties conProp,
            final OutputStream respond,
            final String httpVersion,
            final int httpStatusCode,  
            final httpHeader header
    ) throws IOException {
        sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,null,header);
    }

    public static final void sendRespondHeader(
            final Properties conProp,
            final OutputStream respond,
            String httpVersion,
            final int httpStatusCode, 
            String httpStatusText, 
            httpHeader header
    ) throws IOException {
        
        if (respond == null) throw new NullPointerException("The outputstream must not be null.");
        if (conProp == null) throw new NullPointerException("The connection property structure must not be null.");
        if (httpVersion == null) httpVersion = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER,httpHeader.HTTP_VERSION_1_1);
        if (header == null) header = new httpHeader();
        
        try {                        
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals(httpHeader.HTTP_VERSION_1_0) && httpHeader.http1_0.containsKey(Integer.toString(httpStatusCode))) 
                    httpStatusText = httpHeader.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals(httpHeader.HTTP_VERSION_1_1) && httpHeader.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = httpHeader.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }
            
            final StringBuffer headerStringBuffer = new StringBuffer(560);
            
            // "HTTP/0.9" does not have a status line or header in the response
            if (! httpVersion.toUpperCase().equals(httpHeader.HTTP_VERSION_0_9)) {                
                // write status line
                headerStringBuffer.append(httpVersion).append(" ")
                                  .append(Integer.toString(httpStatusCode)).append(" ")
                                  .append(httpStatusText).append("\r\n");

                // prepare header
                if (!header.containsKey(httpHeader.DATE)) 
                    header.put(httpHeader.DATE, HttpClient.dateString(new Date()));
                if (!header.containsKey(httpHeader.CONTENT_TYPE)) 
                    header.put(httpHeader.CONTENT_TYPE, "text/html; charset=UTF-8"); // fix this
                if (!header.containsKey(httpHeader.CONNECTION) && conProp.containsKey(httpHeader.CONNECTION_PROP_PERSISTENT))
                    header.put(httpHeader.CONNECTION, conProp.getProperty(httpHeader.CONNECTION_PROP_PERSISTENT));
                if (!header.containsKey(httpHeader.PROXY_CONNECTION) && conProp.containsKey(httpHeader.CONNECTION_PROP_PERSISTENT))
                    header.put(httpHeader.PROXY_CONNECTION, conProp.getProperty(httpHeader.CONNECTION_PROP_PERSISTENT));                        
                
                if (conProp.containsKey(httpHeader.CONNECTION_PROP_PERSISTENT) && 
                    conProp.getProperty(httpHeader.CONNECTION_PROP_PERSISTENT).equals("keep-alive") && 
                    !header.containsKey(httpHeader.TRANSFER_ENCODING) && 
                    !header.containsKey(httpHeader.CONTENT_LENGTH))
                    header.put(httpHeader.CONTENT_LENGTH, "0");
                
                // adding some yacy specific headers
                header.put(httpHeader.X_YACY_KEEP_ALIVE_REQUEST_COUNT,conProp.getProperty(httpHeader.CONNECTION_PROP_KEEP_ALIVE_COUNT));
                header.put(httpHeader.X_YACY_ORIGINAL_REQUEST_LINE,conProp.getProperty(httpHeader.CONNECTION_PROP_REQUESTLINE));
                header.put(httpHeader.X_YACY_PREVIOUS_REQUEST_LINE,conProp.getProperty(httpHeader.CONNECTION_PROP_PREV_REQUESTLINE));
                  
                //read custom headers
                /*
                if (requestProperties != null)     
                {
                	httpHeader outgoingHeader=requestProperties.getOutgoingHeader();
                	if (outgoingHeader!=null)
                	{*/
                	final Iterator<httpHeader.Entry> it = header.getCookies();
                	while(it.hasNext()) {
                		//Append user properties to the main String
                		//TODO: Should we check for user properites. What if they intersect properties that are already in header?
                	    final httpHeader.Entry e = it.next();
                        headerStringBuffer.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");   
                	}
                	
                	/*
                	}
                }*/
                
                // write header
                final Iterator<String> i = header.keySet().iterator();
                String key;
                char tag;
                int count;
                //System.out.println("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
                while (i.hasNext()) {
                    key = i.next();
                    tag = key.charAt(0);
                    if ((tag != '*') && (tag != '#')) { // '#' in key is reserved for proxy attributes as artificial header values
                        count = header.keyCount(key);
                        for (int j = 0; j < count; j++) {
                            headerStringBuffer.append(key).append(": ").append((String) header.getSingle(key, j)).append("\r\n");  
                        }
                        //System.out.println("#" + key + ": " + value);
                    }            
                }
                
                // end header
                headerStringBuffer.append("\r\n");
                
                // sending headers to the client
                respond.write(headerStringBuffer.toString().getBytes());            
                
                // flush stream
                respond.flush();
            }
            
            conProp.put(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER,header);
            conProp.put(httpHeader.CONNECTION_PROP_PROXY_RESPOND_STATUS,Integer.toString(httpStatusCode));
        } catch (final Exception e) {
            // any interruption may be caused be network error or because the user has closed
            // the windows during transmission. We simply pass it as IOException
            throw new IOException(e.getMessage());
        }            
    }    
    
    public static boolean shallTransportZipped(final String path) {
        if ((path == null) || (path.length() == 0)) return true;
        
        int pos;
        if ((pos = path.lastIndexOf(".")) != -1) {
            return !disallowZippedContentEncoding.contains(path.substring(pos).toLowerCase());
        }
        return true;
    }    
    
    public void doUserAccounting(final Properties conProps) {
        // TODO: validation of conprop fields
        // httpHeader.CONNECTION_PROP_USER
        // httpHeader.CONNECTION_PROP_CLIENTIP
        // httpHeader.CONNECTION_PROP_PROXY_RESPOND_SIZE
        // httpHeader.CONNECTION_PROP_PROXY_RESPOND_STATUS
    }
    
    public static boolean isThisSeedIP(final String hostName) {
        if ((hostName == null) || (hostName.length() == 0)) return false;
        
        // getting ip address and port of this seed
        if (alternativeResolver == null) return false;
        
        // resolve ip addresses
        final InetAddress seedInetAddress = serverDomains.dnsResolve(alternativeResolver.myIP());
        final InetAddress hostInetAddress = serverDomains.dnsResolve(hostName);
        if (seedInetAddress == null || hostInetAddress == null) return false;
        
        // if it's equal, the hostname points to this seed
        return (seedInetAddress.equals(hostInetAddress));        
    }
    
    public static boolean isThisHostIP(final String hostName) {
        if ((hostName == null) || (hostName.length() == 0)) return false;
        
        boolean isThisHostIP = false;
        try {
//             final InetAddress clientAddress = InetAddress.getByName(hostName);
            final InetAddress clientAddress = serverDomains.dnsResolve(hostName);
            if (clientAddress == null) return false;
            
            if (clientAddress.isAnyLocalAddress() || clientAddress.isLoopbackAddress()) return true;
            
            final InetAddress[] localAddress = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
            for (int i=0; i<localAddress.length; i++) {
                if (localAddress[i].equals(clientAddress)) {
                    isThisHostIP = true;
                    break;
                }
            }  
        } catch (final Exception e) {}   
        return isThisHostIP;
    }    
    
    public static boolean isThisHostIP(final InetAddress clientAddress) {
        if (clientAddress == null) return false;
        
        boolean isThisHostIP = false;
        try {
            if (clientAddress.isAnyLocalAddress() || clientAddress.isLoopbackAddress()) return true;
            
            final InetAddress[] localAddress = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
            for (int i=0; i<localAddress.length; i++) {
                if (localAddress[i].equals(clientAddress)) {
                    isThisHostIP = true;
                    break;
                }
            }  
        } catch (final Exception e) {}   
        return isThisHostIP;
    }  
    
    public static boolean isThisHostName(final String hostName) {
        if ((hostName == null) || (hostName.length() == 0)) return false;
        
        try {                            
            final int idx = hostName.indexOf(":");
            final String dstHost = (idx != -1) ? hostName.substring(0,idx).trim() : hostName.trim();     
            final Integer dstPort = (idx != -1) ? Integer.valueOf(hostName.substring(idx+1).trim()) : Integer.valueOf(80);
            
            // if the hostname endswith thisPeerName.yacy ...
            final String alternativeAddress = (alternativeResolver == null) ? null : alternativeResolver.myAlternativeAddress();
            if ((alternativeAddress != null) && (dstHost.endsWith(alternativeAddress))) {
                return true;
            /* 
             * If the port number is equal to the yacy port and the IP address is an address of this host ...
             * Please note that yacy is listening to all interfaces of this host
             */
            } else if (
                    // check if the destination port is equal to the port yacy is listening to
                    dstPort.equals(Integer.valueOf(serverCore.getPortNr(switchboard.getConfig("port", "8080")))) &&
                    (
                            // check if the destination host is our local IP address
                            isThisHostIP(dstHost) ||
                            // check if the destination host is our seed ip address
                            isThisSeedIP(dstHost)
                    )
            ) {
                 return true;                
            }
        } catch (final Exception e) {}    
        return false;
    }       
}
