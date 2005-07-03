// httpd.java
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

package de.anomic.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverHandler;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;

/**
 * Instances of this class can be passed as argument to the serverCore.
 * The generic server dispatches HTTP commands and calls the
 * method GET, HEAD or POST in this class
 * these methods parse the command line and decide wether to call
 * a proxy servlet or a file server servlet 
 */
public final class httpd implements serverHandler {
    
    /* PROPERTIES: General properties */    
    public static final String CONNECTION_PROP_HTTP_VER = "HTTP";
    public static final String CONNECTION_PROP_HOST = "HOST";
    public static final String CONNECTION_PROP_METHOD = "METHOD";    
    public static final String CONNECTION_PROP_PATH = "PATH";
    public static final String CONNECTION_PROP_EXT = "EXT";
    public static final String CONNECTION_PROP_URL = "URL";
    public static final String CONNECTION_PROP_ARGS = "ARGS";
    public static final String CONNECTION_PROP_CLIENTIP = "CLIENTIP";
    public static final String CONNECTION_PROP_PERSISTENT = "PERSISTENT";
    public static final String CONNECTION_PROP_KEEP_ALIVE_COUNT = "KEEP-ALIVE_COUNT";
    public static final String CONNECTION_PROP_REQUESTLINE = "REQUESTLINE";
    public static final String CONNECTION_PROP_REQUEST_START = "REQUEST_START";
    public static final String CONNECTION_PROP_REQUEST_END = "REQUEST_END";
    
    /* PROPERTIES: Client -> Proxy */
    public static final String CONNECTION_PROP_CLIENT_REQUEST_HEADER = "CLIENT_REQUEST_HEADER";
    
    /* PROPERTIES: Proxy -> Server */
    
    /* PROPERTIES: Server -> Proxy */
    
    /* PROPERTIES: Proxy -> Client */
    public static final String CONNECTION_PROP_PROXY_RESPOND_CODE = "PROXY_RESPOND_CODE";
    public static final String CONNECTION_PROP_PROXY_RESPOND_STATUS = "PROXY_RESPOND_STATUS";
    public static final String CONNECTION_PROP_PROXY_RESPOND_HEADER = "PROXY_RESPOND_HEADER";
    public static final String CONNECTION_PROP_PROXY_RESPOND_SIZE = "PROXY_REQUEST_SIZE";
    
    /**
     * A hashset containing extensions that indicate content that should not be transported
     * using zipped content encoding
     * @see #shallTransportZipped(String)
     */
    private static final HashSet disallowZippedContentEncoding = new HashSet(Arrays.asList(new String[]{
            ".gz", ".tgz", ".jpg", ".jpeg", ".gif", ".zip", ".rar", ".bz2", ".lha", ".jar", ".rpm", ".arc", ".arj"
    }));    
    
    // static objects
    public static final String vDATE = "<<REPL>>";
    public static final String copyright = "[ HTTP SERVER: AnomicHTTPD v" + vDATE + " by Michael Christen / www.anomic.de ]";
    public static final String hline = "-------------------------------------------------------------------------------";
    
    private static HashMap reverseMappingCache = new HashMap();
    private static httpdHandler proxyHandler = null;   // a servlet that holds the proxy functions
    private static httpdHandler fileHandler = null;    // a servlet that holds the file serving functions
    private static httpdHandler soapHandler = null;
    private static serverSwitch switchboard = null;
    private static String virtualHost = null;
    
    public static boolean keepAliveSupport = false;
    
    // class objects
    private serverCore.Session session;  // holds the session object of the calling class
    private InetAddress userAddress;     // the address of the client
    private boolean allowProxy;
    private boolean allowServer;
    
    // for authentication
    private String proxyAccountBase64MD5;
    private String serverAccountBase64MD5;
    private String clientIP;
    
    // the connection properties
    private final Properties prop = new Properties();
    
    private int emptyRequestCount = 0;
    private int keepAliveRequestCount = 0;
    
    // needed for logging
    private final serverLog log = new serverLog("HTTPD");
    
    // class methods
    public httpd(serverSwitch s, httpdHandler fileHandler, httpdHandler proxyHandler) {
        // handler info
        httpd.switchboard = s;
        httpd.fileHandler = fileHandler;
        httpd.proxyHandler = proxyHandler;
        httpd.virtualHost = switchboard.getConfig("fileHost","localhost");
        
        // authentication: by default none
        this.proxyAccountBase64MD5 = null;
        this.serverAccountBase64MD5 = null;
        this.clientIP = null;
        
        // configuring keep alive support
        keepAliveSupport = Boolean.valueOf(switchboard.getConfig("connectionKeepAliveSupport","false")).booleanValue();
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
        this.proxyAccountBase64MD5 = null;
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
    public void initSession(serverCore.Session session) throws IOException {
        this.session = session;
        this.userAddress = session.userAddress; // client InetAddress
        this.clientIP = this.userAddress.getHostAddress();
        if (this.userAddress.isAnyLocalAddress()) this.clientIP = "localhost";
        if (this.clientIP.equals("0:0:0:0:0:0:0:1")) this.clientIP = "localhost";
        if (this.clientIP.equals("127.0.0.1")) this.clientIP = "localhost";
        String proxyClient = switchboard.getConfig("proxyClient", "*");
        String serverClient = switchboard.getConfig("serverClient", "*");
        
        this.allowProxy = (proxyClient.equals("*")) ? true : match(this.clientIP, proxyClient);
        this.allowServer = (serverClient.equals("*")) ? true : match(this.clientIP, serverClient);
        
        // check if we want to allow this socket to connect us
        if (!(this.allowProxy || this.allowServer)) {
            String errorMsg = "CONNECTION FROM " + this.clientIP + " FORBIDDEN";
            this.log.logWarning(errorMsg);
            throw new IOException(errorMsg);
        }
        
        this.proxyAccountBase64MD5 = null;
        this.serverAccountBase64MD5 = null;
    }
    
    private static boolean match(String key, String latch) {
        // the latch is a comma-separated list of patterns
        // each pattern may contain one wildcard-character '*' which matches anything
        StringTokenizer st = new StringTokenizer(latch,",");
        String pattern;
        int pos;
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
    
    public String error(Throwable e) { // OBLIGATORIC FUNCTION
        // return string in case of any error that occurs during communication
        // is always (but not only) called if an IO-dependent exception occurrs.
        this.log.logError("Unexpected Error. " + e.getClass().getName(),e);
        return "501 Exception occurred: " + e.getMessage();
    }
    
    /**
     * reads a line from the input socket
     * this function is provided by the server through a passed method on initialization
     * @return the next requestline as string
     */
    private String readLine() {
        byte[] l = this.session.readLine();
        return (l == null) ? null: new String(l);
    }
    
    private httpHeader readHeader() throws IOException {
        
        // reading all headers
        httpHeader header = new httpHeader(reverseMappingCache);
        int p;
        String line;
        while ((line = readLine()) != null) {
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
        String httpVersion = this.prop.getProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
        if (httpVersion.equals("HTTP/1.1") && !header.containsKey(httpHeader.HOST)) {
            // the HTTP/1.1 specification requires that an HTTP/1.1 server must reject any  
            // HTTP/1.1 message that does not contain a Host header.            
            httpd.sendRespondError(this.prop,this.session.out,0,400,null,null,null);
            throw new IOException("400 Bad request");
        }     
        
        return header;
    }
    
    private void handleTransparentProxySupport(httpHeader header) {   
        // transparent proxy support is only available for http 1.0 and above connections
        if (this.prop.getProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9").equals("HTTP/0.9")) return;
        
        // if the transparent proxy support was disabled, we have nothing todo here ...
        if (!(httpdProxyHandler.isTransparentProxy && header.containsKey(httpHeader.HOST))) return;
        
        try {                
            String dstHost, dstHostSocket = (String) header.get(httpHeader.HOST);
            
            int idx = dstHostSocket.indexOf(":");
            dstHost = (idx != -1) ? dstHostSocket.substring(0,idx).trim() : dstHostSocket.trim();     
            Integer dstPort = (idx != -1) ? Integer.valueOf(dstHostSocket.substring(idx+1)) : new Integer(80);
            
            if (dstPort.intValue() == 80) {
                if (dstHost.endsWith(".yacy")) {
                    this.prop.setProperty(httpd.CONNECTION_PROP_HOST,dstHostSocket);
                } else {
                    InetAddress dstHostAddress = InetAddress.getByName(dstHost);
                    if (!(dstHostAddress.isAnyLocalAddress() || dstHostAddress.isLoopbackAddress())) {
                        this.prop.setProperty(httpd.CONNECTION_PROP_HOST,dstHostSocket);
                    }
                }
            }
        } catch (Exception e) {}
    }
    
    /**
     * This funciton is used to determine if a persistent connection was requested by the
     * client.
     * @param header the received http-headers
     * @return <code>true</code> if a persistent connection was requested or <code>false</code> otherwise
     */
    private boolean handlePersistentConnection(httpHeader header) {
        
        if (!keepAliveSupport) {
            this.prop.put(CONNECTION_PROP_PERSISTENT,"close");
            return false;
        }
        
        // getting the http version that is used by the client
        String httpVersion = this.prop.getProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
        
        // managing keep-alive: in HTTP/0.9 and HTTP/1.0 every connection is closed
        // afterwards. In HTTP/1.1 (and above, in the future?) connections are
        // persistent by default, but closed with the "Connection: close"
        // property.
        boolean persistent = !(httpVersion.equals("HTTP/0.9") || httpVersion.equals("HTTP/1.0"));
        if (((String)header.get(httpHeader.CONNECTION, "keep-alive")).toLowerCase().equals("close") || 
                ((String)header.get(httpHeader.PROXY_CONNECTION, "keep-alive")).toLowerCase().equals("close")) {
            persistent = false;
        }        
        
        // if the request does not contain a content-length we have to close the connection
        // independently of the value of the connection header
        if (persistent && 
            this.prop.getProperty(httpd.CONNECTION_PROP_METHOD).equals(httpHeader.METHOD_POST) && 
            !header.containsKey(httpHeader.CONTENT_LENGTH)) 
              this.prop.put(CONNECTION_PROP_PERSISTENT,"close");
        else  this.prop.put(CONNECTION_PROP_PERSISTENT,persistent?"keep-alive":"close");
        
        return persistent;
    }
    
    private boolean handleServerAuthentication(httpHeader header) throws IOException {
        // getting the http version that is used by the client
        String httpVersion = this.prop.getProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9");        
        
        // reading the authentication settings from switchboard
        if (this.serverAccountBase64MD5 == null) 
            this.serverAccountBase64MD5 = switchboard.getConfig("serverAccountBase64MD5", "");
        
        if (this.serverAccountBase64MD5.length() > 0) {
            String auth = (String) header.get(httpHeader.AUTHORIZATION);
            if (auth == null) {
                // authorization requested, but no authorizeation given in header. Ask for authenticate:
                this.session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                        httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                        serverCore.crlfString).getBytes());
                this.session.out.write((httpHeader.CONTENT_LENGTH + ": 0\r\n").getBytes());
                this.session.out.write("\r\n".getBytes());
                return false;
            } else if (!this.serverAccountBase64MD5.equals(serverCodings.encodeMD5Hex(auth.trim().substring(6)))) {
                // wrong password given: ask for authenticate again
                serverLog.logInfo("HTTPD", "Wrong log-in for account 'server' in HTTPD.GET " + this.prop.getProperty("PATH") + " from IP " + this.clientIP);
                this.session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                        httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + 
                        serverCore.crlfString).getBytes());
                this.session.out.write((httpHeader.CONTENT_LENGTH + ": 0\r\n").getBytes());
                this.session.out.write("\r\n".getBytes());                
                return false;
            }
        }
        return true;
    }
    
    private boolean handleProxyAuthentication(httpHeader header) throws IOException {
        // getting the http version that is used by the client
        String httpVersion = this.prop.getProperty("HTTP", "HTTP/0.9");            
        
        // reading the authentication settings from switchboard
        if (this.proxyAccountBase64MD5 == null) 
            this.proxyAccountBase64MD5 = switchboard.getConfig("proxyAccountBase64MD5", "");
        
        if (this.proxyAccountBase64MD5.length() > 0) {
            String auth = (String) header.get(httpHeader.PROXY_AUTHORIZATION,"xxxxxx");    
            if (!this.proxyAccountBase64MD5.equals(serverCodings.encodeMD5Hex(auth.trim().substring(6)))) {
                // ask for authenticate
                this.session.out.write((httpVersion + " 407 Proxy Authentication Required" + serverCore.crlfString +
                        httpHeader.PROXY_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString).getBytes());
                this.session.out.write((httpHeader.CONTENT_LENGTH + ": 0\r\n").getBytes());
                this.session.out.write("\r\n".getBytes());                   
                return false;
            }
        }
        return true;
    }
    
    public Boolean UNKNOWN(String requestLine) throws IOException {
        
        int pos;
        String unknownCommand = null, args = null;
        if ((pos = requestLine.indexOf(" ")) > 0) {
            unknownCommand = requestLine.substring(0,pos);
            args = requestLine.substring(pos+1);
        } else {
            unknownCommand = requestLine;
            args = "";
        }
        
        parseQuery(unknownCommand, args);
        String httpVersion = this.prop.getProperty(httpd.CONNECTION_PROP_HTTP_VER,"HTTP/0.9");
        
        sendRespondError(this.prop,this.session.out,0,501,null,unknownCommand + " method not implemented",null);
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean EMPTY(String arg) throws IOException {
        if (++this.emptyRequestCount > 10) return serverCore.TERMINATE_CONNECTION;
        return serverCore.RESUME_CONNECTION;
    }
    
    public Boolean TRACE(String arg) throws IOException {
        sendRespondError(this.prop,this.session.out,0,501,null,"TRACE method not implemented",null);
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean OPTIONS(String arg) throws IOException {
        sendRespondError(this.prop,this.session.out,0,501,null,"OPTIONS method not implemented",null);
        return serverCore.TERMINATE_CONNECTION;
    }    
    
    
    public Boolean GET(String arg) throws IOException {
        try {
            // parsing the http request line
            parseQuery(httpHeader.METHOD_GET,arg);
            
            // we now know the HTTP version. depending on that, we read the header            
            String httpVersion = this.prop.getProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
            httpHeader header = (httpVersion.equals("HTTP/0.9")) ? new httpHeader(reverseMappingCache) 
                                                                 : readHeader();                  
            
            // handling transparent proxy support
            this.handleTransparentProxySupport(header); 
            
            // determines if the connection should be kept alive
            handlePersistentConnection(header);
            
            if (this.prop.getProperty(CONNECTION_PROP_HOST).equals(virtualHost)) {
                // pass to server
                if (this.allowServer) {
                    
                    /*
                     * Handling SOAP Requests here ...
                     */
                    if (this.prop.containsKey(CONNECTION_PROP_PATH) && this.prop.getProperty(CONNECTION_PROP_PATH).startsWith("/soap")) {
                        if (soapHandler == null) {
                            try {
                                Class soapHandlerClass = Class.forName("de.anomic.soap.httpdSoapHandler");
                                Constructor classConstructor = soapHandlerClass.getConstructor( new Class[] { serverSwitch.class } );
                                soapHandler  = (httpdHandler) classConstructor.newInstance(new Object[] { this.switchboard });
                            } catch (Exception e) {
                                sendRespondHeader(this.prop,this.session.out,httpVersion,503,null);
                                return serverCore.TERMINATE_CONNECTION;
                            }
                        }
                        soapHandler.doGet(this.prop, header, this.session.out);
                        
                        /*
                         * Handling HTTP requests here ...
                         */
                    } else {              
                        if (this.handleServerAuthentication(header)) {
                            if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                            fileHandler.doGet(this.prop, header, this.session.out);
                        }
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    this.session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this server, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("serverClient", "*") + serverCore.crlfString).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (this.allowProxy) {
                    if (this.handleProxyAuthentication(header)) {
                        if (proxyHandler != null) proxyHandler = new httpdProxyHandler(this.switchboard); 
                        proxyHandler.doGet(this.prop, header, this.session.out);
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    this.session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.crlfString).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            
            return this.prop.getProperty(CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (Exception e) {
            logUnexpectedError(e);
            return serverCore.TERMINATE_CONNECTION;
        }
    }
    
    private void logUnexpectedError(Exception e) {
        if (e instanceof InterruptedException) {
            this.log.logInfo("Interruption detected");
        } else {
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.startsWith("Socket closed")) {
                    this.log.logInfo("httpd shutdown detected ...");
                } else if ((errorMsg.startsWith("Broken pipe") || errorMsg.startsWith("Connection reset"))) {
                    // client closed the connection, so we just end silently
                    this.log.logInfo("Client unexpectedly closed connection");
                } else {
                    this.log.logError("Unexpected Error. " + e.getClass().getName() + ": " + e.getMessage(),e);
                }
            } else {
                this.log.logError("Unexpected Error. " + e.getClass().getName(),e);
            }
        }        
    }

    public Boolean HEAD(String arg) throws IOException {
        try {
            parseQuery(httpHeader.METHOD_HEAD,arg);
            
            // we now know the HTTP version. depending on that, we read the header
            httpHeader header;
            String httpVersion = prop.getProperty("HTTP", "HTTP/0.9");
            if (httpVersion.equals("HTTP/0.9")) header = new httpHeader(reverseMappingCache);
            else  header = readHeader();
            
            // handle transparent proxy support
            this.handleTransparentProxySupport(header);
            
            // determines if the connection should be kept alive
            boolean persistent = handlePersistentConnection(header);
            
            // return multi-line message
            if (this.prop.getProperty("HOST").equals(virtualHost)) {
                // pass to server
                if (allowServer) {
                    if (handleServerAuthentication(header)) {
                        if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                        fileHandler.doHead(prop, header, this.session.out);
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" +
                            serverCore.crlfString).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (allowProxy) {
                    if (handleProxyAuthentication(header)) {
                        if (proxyHandler != null) proxyHandler = new httpdProxyHandler(this.switchboard); 
                        proxyHandler.doHead(prop, header, this.session.out);
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" +
                            serverCore.crlfString).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            return this.prop.getProperty(CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (Exception e) {
            logUnexpectedError(e);
            return serverCore.TERMINATE_CONNECTION;
        }        
    }
    
    public Boolean POST(String arg) throws IOException {
        try {
            parseQuery("POST",arg);
            
            // we now know the HTTP version. depending on that, we read the header
            httpHeader header;
            String httpVersion = prop.getProperty("HTTP", "HTTP/0.9");
            if (httpVersion.equals("HTTP/0.9"))  header = new httpHeader(reverseMappingCache);
            else header = readHeader();
            
            // handle transparent proxy support
            this.handleTransparentProxySupport(header);
            
            // determines if the connection should be kept alive
            boolean persistent = handlePersistentConnection(header);
            
            // return multi-line message
            if (prop.getProperty("HOST").equals(virtualHost)) {
                // pass to server
                if (allowServer) {
                    
                    /*
                     * Handling SOAP Requests here ...
                     */
                    if (this.prop.containsKey("PATH") && this.prop.getProperty("PATH").startsWith("/soap")) {
                        if (soapHandler == null) {
                            try {
                                soapHandler  = (httpdHandler) Class.forName("de.anomic.soap.httpdSoapHandler").newInstance();
                            } catch (Exception e) {
                                sendRespondHeader(this.prop,this.session.out,httpVersion,503,null);
                                return serverCore.TERMINATE_CONNECTION;
                            }
                        }
                        soapHandler.doPost(prop, header, this.session.out, this.session.in);                
                        /*
                         * Handling normal HTTP requests here ...
                         */
                    } else {       
                        if (handleServerAuthentication(header)) {
                            if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                            fileHandler.doPost(prop, header, this.session.out, this.session.in);
                        }
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this server, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("serverClient", "*") + serverCore.crlfString).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            } else {
                // pass to proxy
                if (allowProxy) {
                    if (handleProxyAuthentication(header)) {
                        if (proxyHandler != null) proxyHandler = new httpdProxyHandler(this.switchboard); 
                        proxyHandler.doPost(prop, header, this.session.out, this.session.in);
                    }
                } else {
                    // not authorized through firewall blocking (ip does not match filter)
                    session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.crlfString).getBytes());
                    return serverCore.TERMINATE_CONNECTION;
                }
            }
            //return serverCore.RESUME_CONNECTION;
            return this.prop.getProperty(CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
        } catch (Exception e) {
            logUnexpectedError(e);
            return serverCore.TERMINATE_CONNECTION;
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
        
        if (!(allowProxy)) {
            // not authorized through firewall blocking (ip does not match filter)
            session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.crlfString).getBytes());
            return serverCore.TERMINATE_CONNECTION;
        }
        
        // parse port
        pos = arg.indexOf(":");
        int port = 443;
        if (pos >= 0) {
            port = Integer.parseInt(arg.substring(pos + 1));
            arg = arg.substring(0, pos);
        }
        
        // arg is now the host string
        
        // parse remaining lines
        httpHeader header = readHeader();
        
        if (port != 443) {
            // security: connection only to ssl port
            // we send a 403 (forbidden) error back
            session.out.write((httpVersion + " 403 Connection to non-443 forbidden" +
                    serverCore.crlfString + serverCore.crlfString).getBytes());
            return serverCore.TERMINATE_CONNECTION;
        }
        
        // prepare to pass values
        Properties prop = new Properties();
        prop.setProperty("HOST", arg);
        prop.setProperty("PORT", "" + port);
        prop.setProperty("HTTP", httpVersion);
        
        // pass to proxy
        if (allowProxy) {
            if (handleProxyAuthentication(header)) {
                if (proxyHandler != null) proxyHandler = new httpdProxyHandler(this.switchboard); 
                proxyHandler.doConnect(prop, header, this.session.in, this.session.out);
            } 
        } else {
            // not authorized through firewall blocking (ip does not match filter)
            session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.crlfString).getBytes());
        }
        
        return serverCore.TERMINATE_CONNECTION;
    }
    
    
    private final Properties parseQuery(String cmd, String s) {
        
        // reset property from previous run   
        this.prop.clear();
        this.emptyRequestCount = 0;

        // storing informations about the request
        this.prop.setProperty(CONNECTION_PROP_METHOD, cmd);
        this.prop.setProperty(CONNECTION_PROP_REQUESTLINE,cmd + " " + s);
        this.prop.setProperty(CONNECTION_PROP_CLIENTIP, this.clientIP);
        
        // counting the amount of received requests within this permanent conneciton
        this.prop.setProperty(CONNECTION_PROP_KEEP_ALIVE_COUNT, Integer.toString(++this.keepAliveRequestCount));
        
        // this parses a whole URL
        if (s.length() == 0) {
            this.prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
            this.prop.setProperty(CONNECTION_PROP_PATH, "/");
            this.prop.setProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
            this.prop.setProperty(CONNECTION_PROP_EXT, "");
            return this.prop;
        }
        
        // store the version propery "HTTP" and cut the query at both ends
        int sep = s.indexOf(" ");
        if (sep >= 0) {
            // HTTP version is given
            this.prop.setProperty(CONNECTION_PROP_HTTP_VER, s.substring(sep + 1).trim());
            s = s.substring(0, sep).trim(); // cut off HTTP version mark
        } else {
            // HTTP version is not given, it will be treated as ver 0.9
            this.prop.setProperty(CONNECTION_PROP_HTTP_VER, "HTTP/0.9");
        }
        
        // properties of the query are stored with the prefix "&"
        // additionally, the values URL and ARGC are computed
        
        String argsString = "";
        sep = s.indexOf("?");
        if (sep >= 0) {
            // there are values attached to the query string
            argsString = s.substring(sep + 1); // cut haed from tail of query
            s = s.substring(0, sep);
        }
        this.prop.setProperty(CONNECTION_PROP_URL, s); // store URL
        //System.out.println("HTTPD: ARGS=" + argsString);
        if (argsString.length() != 0) this.prop.setProperty(CONNECTION_PROP_ARGS, argsString); // store arguments in original form
        
        // find out file extension
        sep = s.lastIndexOf(".");
        if (sep >= 0) {
            if (s.indexOf("?", sep + 1) >= sep)
                this.prop.setProperty(CONNECTION_PROP_EXT, s.substring(sep + 1, s.indexOf("?", sep + 1)).toLowerCase());
            else if (s.indexOf("#", sep + 1) >= sep)
                this.prop.setProperty(CONNECTION_PROP_EXT, s.substring(sep + 1, s.indexOf("#", sep + 1)).toLowerCase());
            else
                this.prop.setProperty(CONNECTION_PROP_EXT, s.substring(sep + 1).toLowerCase());
        } else {
            this.prop.setProperty(CONNECTION_PROP_EXT, "");
        }
        
        // finally find host string
        if (s.toUpperCase().startsWith("HTTP://")) {
            // a host was given. extract it and set path
            s = s.substring(7);
            sep = s.indexOf("/");
            if (sep < 0) {
                // this is a malformed url, something like
                // http://index.html
                // we are lazy and guess that it means
                // /index.html
                // which is a localhost access to the file servlet
                this.prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
                this.prop.setProperty(CONNECTION_PROP_PATH, "/" + s);
            } else {
                // THIS IS THE "GOOD" CASE
                // a perfect formulated url
                this.prop.setProperty(CONNECTION_PROP_HOST, s.substring(0, sep));
                this.prop.setProperty(CONNECTION_PROP_PATH, s.substring(sep)); // yes, including beginning "/"
            }
        } else {
            // no host in url. set path
            if (s.startsWith("/")) {
                // thats also fine, its a perfect localhost access
                // in this case, we simulate a
                // http://localhost/s
                // access by setting a virtual host
                this.prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
                this.prop.setProperty(CONNECTION_PROP_PATH, s);
            } else {
                // the client 'forgot' to set a leading '/'
                // this is the same case as above, with some lazyness
                this.prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
                this.prop.setProperty(CONNECTION_PROP_PATH, "/" + s);
            }
        }
        return this.prop;
    }
    
    
    // some static methods that needs to be used from any CGI
    // and also by the httpdFileHandler
    // but this belongs to the protocol handler, this class.
    
    
    public static int parseArgs(serverObjects args, PushbackInputStream in, int length) throws IOException {
        // this is a quick hack using a previously coded parseMultipart based on a buffer
        // should be replaced sometime by a 'right' implementation
        byte[] buffer = new byte[length];
        in.read(buffer);
        int argc = parseArgs(args, new String(buffer));
        buffer = null;
        return argc;
    }
    
    public static int parseArgs(serverObjects args, String argsString) {
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
    
    private static String parseArg(String s) {
        // this parses a given value-string from a http property
        // we replace all "+" by spaces
        // and resolve %-escapes with two-digit hex attributes
        int pos = 0;
        StringBuffer result = new StringBuffer(s.length());
        while (pos < s.length()) {
            if (s.charAt(pos) == '+') {
                result.append(" "); 
                pos++;
            } else if (s.charAt(pos) == '%') {
                result.append((char) Integer.parseInt(s.substring(pos + 1, pos + 3), 16));
                pos += 3;
            } else {
                result.append(s.charAt(pos++));
            }
        }
        return result.toString();
    }
    
    
    public static HashMap parseMultipart(httpHeader header, serverObjects args, PushbackInputStream in, int length) throws IOException {
        // this is a quick hack using a previously coded parseMultipart based on a buffer
        // should be replaced sometime by a 'right' implementation
        byte[] buffer = new byte[length];
        int c, a = 0;
        while (a < length) {
            c = in.read(buffer, a, length - a);
            if (c <= 0) break;
            a += c;
        }
        //System.out.println("MULTIPART-BUFFER=" + new String(buffer));
        HashMap files = parseMultipart(header, args, buffer);
        buffer = null;
        return files;
    }
    
    public static HashMap parseMultipart(httpHeader header, serverObjects args, byte[] buffer) throws IOException {
        // we parse a multipart message and put results into the properties
        // find/identify boundary marker
        //System.out.println("DEBUG parseMultipart = <<" + new String(buffer) + ">>");
        String s = (String) header.get(httpHeader.CONTENT_TYPE);
        if (s == null) return null;
        int q;
        int p = s.toLowerCase().indexOf("boundary=");
        if (p < 0) throw new IOException("boundary marker in multipart not found");
        // boundaries start with additional leading "--", see RFC1867
        byte[] boundary = ("--" + s.substring(p + 9)).getBytes();
        
        // eat up first boundary
        // the buffer must start with a boundary
        byte[] line = readLine(0, buffer);
        int pos = nextPos;
        if ((line == null) || (!(equals(line, 0, boundary, 0, boundary.length))))
            throw new IOException("boundary not recognized: " + ((line == null) ? "NULL" : new String(line)) + ", boundary = " + new String(boundary));
        
        // we need some constants
        byte[] namec = (new String("name=")).getBytes();
        byte[] filenamec = (new String("filename=")).getBytes();
        byte[] semicolonc = (new String(";")).getBytes();
        byte[] quotec = new byte[] {(byte) '"'};
        
        // now loop over boundaries
        byte [] name;
        byte [] filename;
        HashMap files = new HashMap();
        int argc = 0;
        //System.out.println("DEBUG: parsing multipart body:" + new String(buffer));
        while (pos < buffer.length) { // boundary enumerator
            // here the 'pos' marker points to the first line in a section after a boundary line
            line = readLine(pos, buffer); pos = nextPos;
            // termination if line is empty
            if (line.length == 0) break;
            // find name tag in line
            p = indexOf(0, line, namec);
            if (p < 0) throw new IOException("tag name in marker section not found: '" + new String(line) + "'"); // a name tag must always occur
            p += namec.length + 1; // first position of name value
            q = indexOf(p, line, quotec);
            if (q < 0) throw new IOException("missing quote in name tag: '" + new String(line) + "'");
            name = new byte[q - p];
            java.lang.System.arraycopy(line, p, name, 0, q - p);
            // if this line has also a filename attribute, read it
            p = indexOf(q, line, filenamec);
            if (p > 0) {
                p += filenamec.length + 1; // first position of name value
                q = indexOf(p, line, quotec);
                if (q < 0) throw new IOException("missing quote in filename tag: '" + new String(line) + "'");
                filename = new byte[q - p];
                java.lang.System.arraycopy(line, p, filename, 0, q - p);
            } else filename = null;
            // we have what we need. more information lines may follow, but we omit parsing them
            // we just skip until an empty line is reached
            while (pos < buffer.length) { // line skiping
                line = readLine(pos, buffer); pos = nextPos;
                if ((line == null) || (line.length == 0)) break;
            }
            // depending on the filename tag exsistence, read now either a value for the name
            // or a complete uploaded file
            // to know the exact length of the value, we must identify the next boundary
            p = indexOf(pos, buffer, boundary);
            
            // if we can't find another boundary, then this is an error in the input
            if (p < 0) {
                serverLog.logError("HTTPD", "ERROR in PUT body: no ending boundary. probably missing values");
                break;
            }
            
            // we don't know if the value is terminated by lf, cr or crlf
            // (it's suppose to be crlf, but we want to be lazy about wrong terminations)
            if (buffer[p - 2] == serverCore.cr) // ERROR: IndexOutOfBounds: -2
                /* crlf */ q = p - 2;
            else
                /* cr or lf only */ q = p - 1;
            // the above line is wrong if we uploaded a file that has a cr as it's last byte
            // and the client's line termination symbol is only a cr or lf (which would be incorrect)
            // the value is between 'pos' and 'q', while the next marker is 'p'
            line = new byte[q - pos];
            java.lang.System.arraycopy(buffer, pos, line, 0, q - pos);
            // in the 'line' variable we have now either a normal value or an uploadef file
            if (filename == null) {
                args.put(new String(name), new String(line, "ISO-8859-1"));
            } else {
                // we store the file in a hashtable.
                // we use the same key to address the file in the hashtable as we
                // use to address the filename in the properties, but without leading '&'
                args.put(new String(name), new String(filename));
                files.put(new String(name), line);
            }
            argc++;
            // finally, read the next boundary line
            line = readLine(p, buffer);
            pos = nextPos;
        }
        header.put("ARGC", ("" + argc)); // store argument count
        return files;
    }
    
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
    
    static int nextPos = -1;        
    private static byte[] readLine(int start, byte[] array) {
        // read a string from an array; line ending is always CRLF
        // but we are also fuzzy with that: may also be only CR or LF
        // if no remaining cr, crlf or lf can be found, return null
        if (start > array.length) return null;
        int pos = indexOf(start, array, serverCore.crlf); nextPos = pos + 2;
        if (pos < 0) {pos = indexOf(start, array, new byte[] {serverCore.cr}); nextPos = pos + 1;}
        if (pos < 0) {pos = indexOf(start, array, new byte[] {serverCore.lf}); nextPos = pos + 1;}
        if (pos < 0) {nextPos = start; return null;}
        byte[] result = new byte[pos - start];
        java.lang.System.arraycopy(array, start, result, 0, pos - start);
        return result;
    }
    
    public static int indexOf(int start, byte[] array, byte[] pattern) {
        // return a position of a pattern in an array
        if (start > array.length - pattern.length) return -1;
        if (pattern.length == 0) return start;
        int i;
        for (int pos = start; pos <= array.length - pattern.length; pos++)
            if ((array[pos] == pattern[0]) && (equals(array, pos, pattern, 0, pattern.length)))
                return pos;
        return -1;
    }
    
    public static boolean equals(byte[] a, int aoff, byte[] b, int boff, int len) {
        //System.out.println("equals: a = " + new String(a) + ", aoff = " + aoff + ", b = " + new String(b) + ", boff = " + boff + ", length = " + len);
        if ((aoff + len > a.length) || (boff + len > b.length)) return false;
        for (int i = 0; i < len; i++) if (a[aoff + i] != b[boff + i]) return false;
        //System.out.println("TRUE!");
        return true;
    }
    
    public Object clone() {
        return new httpd(this.switchboard, this.fileHandler, this.proxyHandler);        
    }
    
    public static final void sendRespondBody(
            Properties conProp,
            OutputStream respond,
            byte[] body
    ) throws IOException {
        respond.write(body);
        respond.flush();        
    }
    
    public static final void sendRespondError(
            Properties conProp,
            OutputStream respond,
            int errorcase,
            int httpStatusCode,            
            String httpStatusText,
            String detailedErrorMsg,
            Exception stackTrace
    ) throws IOException {
        
        FileInputStream fis = null;
        try {
            
            File htRootPath = new File(switchboard.getRootPath(), switchboard.getConfig("htRootPath","htroot"));
            
            // setting the proper http status message
            String httpVersion = conProp.getProperty(httpd.CONNECTION_PROP_HTTP_VER,"HTTP/1.1");
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals("HTTP/1.0") && httpHeader.http1_0.containsKey(Integer.toString(httpStatusCode))) 
                    httpStatusText = (String) httpHeader.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals("HTTP/1.1") && httpHeader.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = (String) httpHeader.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }
            
            // generating the desired request url
            String host = conProp.getProperty(httpd.CONNECTION_PROP_HOST);
            String path = conProp.getProperty(httpd.CONNECTION_PROP_PATH);
            String args = conProp.getProperty(httpd.CONNECTION_PROP_ARGS);
            
            int port = 80, pos = host.indexOf(":");        
            if (pos != -1) {
                port = Integer.parseInt(host.substring(pos + 1));
                host = host.substring(0, pos);
            }
            
            String urlString;
            try {
                urlString = (new URL("http", host, port, (args == null) ? path : path + "?" + args)).toString();
            } catch (MalformedURLException e) {
                urlString = "invalid URL"; 
            }            
            
            // set rewrite values
            serverObjects tp = new serverObjects();
            
            tp.put("host", serverCore.publicIP().getHostAddress());
            tp.put("port", switchboard.getConfig("port", "8080"));
            
            tp.put("errorMessageType", errorcase);            
            tp.put("httpStatus",       Integer.toString(httpStatusCode) + " " + httpStatusText);
            tp.put("requestMethod",    conProp.getProperty(httpd.CONNECTION_PROP_METHOD));
            tp.put("requestURL",       urlString);
            tp.put("errorMessageType_detailedErrorMsg",(detailedErrorMsg != null) ? detailedErrorMsg : "");
            
            // building the stacktrace            
            if (stackTrace != null) {    
                serverByteBuffer errorMsg = new serverByteBuffer(100);
                errorMsg.append("Exception occurred:\r\n\r\n")
                        .append(stackTrace.toString())
                        .append("\r\n")
                        .append("TRACE: ");
                stackTrace.printStackTrace(new PrintStream(errorMsg));
                errorMsg.write(("\r\n").getBytes());
                tp.put("printStackTrace",1);
                tp.put("printStackTrace_stacktrace",errorMsg.toString().replaceAll("\n","<br>"));
            } else {
                tp.put("printStackTrace",0);
            }
            
            // rewrite the file
            File file = new File(htRootPath, "/proxymsg/error.html");
            byte[] result;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            fis = new FileInputStream(file);
            httpTemplate.writeTemplate(fis, o, tp, "-UNRESOLVED_PATTERN-".getBytes());
            result = o.toByteArray();
            o.close(); o = null;

            httpHeader header = new httpHeader();            
            header.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));
            header.put(httpHeader.CONTENT_TYPE, "text/html");
            header.put(httpHeader.CONTENT_LENGTH, "" + result.length);
            header.put(httpHeader.PRAGMA, "no-cache");
            sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,httpStatusText,header);
            
            // write the array to the client
            serverFileUtils.write(result, respond);
            respond.flush();
        } catch (Exception e) { 
            throw new IOException(e.getMessage());
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception e) {}
        }     
    }
    
    public static final void sendRespondHeader(
            Properties conProp,
            OutputStream respond,
            String httpVersion,
            int httpStatusCode, 
            String httpStatusText, 
            long contentLength
    ) throws IOException { 
        sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,httpStatusText,null,contentLength,null,null,null,null,null);
    }
    
    public static final void sendRespondHeader(
            Properties conProp,
            OutputStream respond,
            String httpVersion,
            int httpStatusCode, 
            String httpStatusText, 
            String contentType,
            long contentLength,
            Date moddate, 
            Date expires,
            String cookie,
            String contentEnc,
            String transferEnc
    ) throws IOException {    
        
        httpHeader headers = new httpHeader();
        
        headers.put(httpHeader.SERVER, "AnomicHTTPD (www.anomic.de)");
        headers.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));              
        headers.put(httpHeader.LAST_MODIFIED, httpc.dateString(moddate)); 
        headers.put(httpHeader.PRAGMA, "no-cache");        
        
        if (contentLength > 0)   headers.put(httpHeader.CONTENT_TYPE,  (contentType == null)? "text/html" : contentType);  
        if (contentLength > 0)   headers.put(httpHeader.CONTENT_LENGTH, Long.toString(contentLength));
        if (cookie != null)      headers.put(httpHeader.SET_COOKIE, cookie);
        if (expires != null)     headers.put(httpHeader.EXPIRES, httpc.dateString(expires));
        if (contentEnc != null)  headers.put(httpHeader.CONTENT_ENCODING, contentEnc);
        if (transferEnc != null) headers.put(httpHeader.TRANSFER_ENCODING, transferEnc);
        
        sendRespondHeader(conProp, respond, httpVersion, httpStatusCode, httpStatusText, headers);
    }
    
    public static final void sendRespondHeader(
            Properties conProp,
            OutputStream respond,
            String httpVersion,
            int httpStatusCode,  
            httpHeader header
    ) throws IOException {
        sendRespondHeader(conProp,respond,httpVersion,httpStatusCode,null,header);
    }
    
    public static final void sendRespondHeader(
            Properties conProp,
            OutputStream respond,
            String httpVersion,
            int httpStatusCode, 
            String httpStatusText, 
            httpHeader header
    ) throws IOException {
        
        if (respond == null) throw new NullPointerException("The outputstream must not be null.");
        if (conProp == null) throw new NullPointerException("The connection property structure must not be null.");
        if (httpVersion == null) httpVersion = conProp.getProperty(httpd.CONNECTION_PROP_HTTP_VER,"HTTP/1.1");
        
        try {                        
            if ((httpStatusText == null)||(httpStatusText.length()==0)) {
                if (httpVersion.equals("HTTP/1.0") && httpHeader.http1_0.containsKey(Integer.toString(httpStatusCode))) 
                    httpStatusText = (String) httpHeader.http1_0.get(Integer.toString(httpStatusCode));
                else if (httpVersion.equals("HTTP/1.1") && httpHeader.http1_1.containsKey(Integer.toString(httpStatusCode)))
                    httpStatusText = (String) httpHeader.http1_1.get(Integer.toString(httpStatusCode));
                else httpStatusText = "Unknown";
            }
            
            // prepare header
            if (!header.containsKey(httpHeader.DATE)) 
                header.put(httpHeader.DATE, httpc.dateString(httpc.nowDate()));
            if (!header.containsKey(httpHeader.CONTENT_TYPE)) 
                header.put(httpHeader.CONTENT_TYPE, "text/html"); // fix this
            if (!header.containsKey(httpHeader.CONNECTION) && conProp.containsKey(CONNECTION_PROP_PERSISTENT))
                header.put(httpHeader.CONNECTION, conProp.getProperty(CONNECTION_PROP_PERSISTENT));
            if (!header.containsKey(httpHeader.PROXY_CONNECTION) && conProp.containsKey(CONNECTION_PROP_PERSISTENT))
                header.put(httpHeader.PROXY_CONNECTION, conProp.getProperty(CONNECTION_PROP_PERSISTENT));                        
            
            if (conProp.containsKey(CONNECTION_PROP_PERSISTENT) && 
                conProp.getProperty(CONNECTION_PROP_PERSISTENT).equals("keep-alive") && 
                !header.containsKey(httpHeader.TRANSFER_ENCODING) && 
                !header.containsKey(httpHeader.CONTENT_LENGTH))
                header.put(httpHeader.CONTENT_LENGTH, "0");
            
            // adding some yacy specific headers
            header.put(httpHeader.X_YACY_KEEP_ALIVE_REQUEST_COUNT,conProp.getProperty(CONNECTION_PROP_KEEP_ALIVE_COUNT));
            header.put(httpHeader.X_YACY_ORIGINAL_REQUEST_LINE,conProp.getProperty(CONNECTION_PROP_REQUESTLINE));
            
            StringBuffer headerStringBuffer = new StringBuffer(560);
            
            // write status line
            headerStringBuffer.append(httpVersion).append(" ")
            .append(Integer.toString(httpStatusCode)).append(" ")
            .append(httpStatusText).append("\r\n");
            
            // write header
            Iterator i = header.keySet().iterator();
            String key, value;
            char tag;
            int count;
            //System.out.println("vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
            while (i.hasNext()) {
                key = (String) i.next();
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
            respond.flush();
            
            conProp.put(httpd.CONNECTION_PROP_PROXY_RESPOND_HEADER,header);
            conProp.put(httpd.CONNECTION_PROP_PROXY_RESPOND_STATUS,Integer.toString(httpStatusCode));
        } catch (Exception e) {
            // any interruption may be caused be network error or because the user has closed
            // the windows during transmission. We simply pass it as IOException
            throw new IOException(e.getMessage());
        }            
    }    
    
    public static boolean shallTransportZipped(String path) {
        if ((path == null) || (path.length() == 0)) return true;
        
        int pos;
        if ((pos = path.lastIndexOf(".")) != -1) {
            return !disallowZippedContentEncoding.contains(path.substring(pos).toLowerCase());
        }
        return true;
    }    
    
    
    
//  public static boolean isTextMime(String mime, Set whitelist) {
//  if (whitelist.contains(mime)) return true;
//  // some mime-types are given as "text/html; charset=...", so look for ";"
//  if (mime.length() == 0) return false;
//  int pos = mime.indexOf(';');
//  if (pos < 0) return false;
//  return whitelist.contains(mime.substring(0, pos));
//  }
}

/*
 ###
 ### Messages of the Server
 ###
 
 # success Messages
 HTTPStatus200 = OK; The URL was found. It contents follows.
 HTTPStatus201 = Created; A URL was created in response to a POST.
 HTTPStatus202 = Accepted; The request was accepted for processing later.
 HTTPStatus203 = Non-Authoritative; The information here is unofficial.
 HTTPStatus204 = No Response; The request is successful, but there is no data to send.
 
 # redirection
 HTTPStatus300 = Moved; The URL has permanently moved to a new location.
 HTTPStatus301 = Found; The URL can be temporarily found at a new location.
 
 # client errors
 HTTPStatus400 = Bad Request; Syntax error in the request.
 HTTPStatus401 = Unauthorized; The client is not authorized to access this web page.
 HTTPStatus402 = Payment Required; A payment is required to access this web page.
 HTTPStatus403 = Forbidden; This URL is forbidden. No authorization is required, it won't help.
 HTTPStatus404 = Not Found; This page is not on the server.
 
 # server errors
 HTTPStatus500 = Internal Error; The server encountered an unexpected error.
 HTTPStatus501 = Not Implemented; The client requested an unimplemented feature.
 HTTPStatus502 = Service Overloaded; The server reached the maximum number of connections.
 HTTPStatus503 = Gateway timeout; Fetching data from remote service failed.
 */
