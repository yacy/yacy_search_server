// httpd.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 03.01.2004
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
   Class documentation:
   Instances of this class can be passed as argument to the serverCore.
   The generic server dispatches HTTP commands and calls the
   method GET, HEAD or POST in this class
   these methods parse the command line and decide wether to call
   a proxy servlet or a file server servlet
*/

package de.anomic.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import de.anomic.server.serverCodings;
import de.anomic.server.serverCore;
import de.anomic.server.serverHandler;
import de.anomic.server.serverLog;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class httpd implements serverHandler {

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
    }
    
    public void reset()  {
        this.session = null;
        this.userAddress = null;
        this.allowProxy = false;
        this.allowServer = false;
        this.proxyAccountBase64MD5 = null;
        this.serverAccountBase64MD5 = null;
        this.clientIP = null;
        this.prop.clear();
    }    
    
     // must be called at least once, but can be called again to re-use the object.
     public void initSession(serverCore.Session session) throws IOException {
        this.session = session;
        this.userAddress = session.userAddress; // client InetAddress
        this.clientIP = userAddress.getHostAddress();
       	if (this.userAddress.isAnyLocalAddress()) this.clientIP = "localhost";
        if (this.clientIP.equals("0:0:0:0:0:0:0:1")) this.clientIP = "localhost";
        if (this.clientIP.equals("127.0.0.1")) this.clientIP = "localhost";
     	String proxyClient = switchboard.getConfig("proxyClient", "*");
    	String serverClient = switchboard.getConfig("serverClient", "*");
        this.allowProxy = (proxyClient.equals("*")) ? true : match(clientIP, proxyClient);
    	this.allowServer = (serverClient.equals("*")) ? true : match(clientIP, serverClient);
            	
    	// check if we want to allow this socket to connect us
    	if (!((allowProxy) || (allowServer))) {
    	    throw new IOException("CONNECTION FROM " + clientIP + " FORBIDDEN");
    	}

        proxyAccountBase64MD5 = null;
        serverAccountBase64MD5 = null;
    }

    private static boolean match(String key, String latch) {
    	// the latch is a comma-separated list of patterns
    	// each pattern may contain one wildcard-character '*' which matches anything
    	StringTokenizer st = new StringTokenizer(latch,",");
    	String pattern;
    	int pos;
    	while (st.hasMoreTokens()) {
    	    pattern = st.nextToken();
    	    pos = pattern.indexOf("*");
    	    if (pos < 0) {
    		// no wild card: exact match
    		if (key.equals(pattern)) return true;
    	    } else {
    		// wild card: match left and right side of pattern
    		if ((key.startsWith(pattern.substring(0, pos))) &&
    		    (key.endsWith(pattern.substring(pos + 1)))) return true;
    	    }
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
    	e.printStackTrace();
    	return "501 Exception occurred: " + e.getMessage();
    }

    private String readLine() {
    	// reads a line from the input socket
    	// this function is provided by the server through a passed method on initialization
    	byte[] l = this.session.readLine();
    	if (l == null) return null; else return new String(l);
    }

    private httpHeader readHeader() {
    	httpHeader header = new httpHeader(reverseMappingCache);
    	int p;
    	String line;
    	while ((line = readLine()) != null) {
    	    if (line.length() == 0) break; // this seperates the header of the HTTP request from the body
    	    //System.out.println("***" + line); // debug
    	    // parse the header line: a property seperated with the ':' sign
    	    p = line.indexOf(":");
    	    if (p >= 0) {
    		// store a property
    		header.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
    	    }
    	}
    	return header;
    }

    private void transparentProxyHandling(httpHeader header) {        
        if (!(httpdProxyHandler.isTransparentProxy && header.containsKey(httpHeader.HOST))) return;
        
        try {                
            String dstHost, dstHostSocket = (String) header.get(httpHeader.HOST);
            
            int idx = dstHostSocket.indexOf(":");
            dstHost = (idx != -1) ? dstHostSocket.substring(0,idx).trim() : dstHostSocket.trim();     
            Integer dstPort = (idx != -1) ? Integer.valueOf(dstHostSocket.substring(idx+1)) : new Integer(80);
            
            if (dstPort.intValue() == 80) {
                if (dstHost.endsWith(".yacy")) {
                    this.prop.setProperty("HOST",dstHostSocket);
                } else {
                    InetAddress dstHostAddress = InetAddress.getByName(dstHost);
                    if (!(dstHostAddress.isAnyLocalAddress() || dstHostAddress.isLoopbackAddress())) {
                        this.prop.setProperty("HOST",dstHostSocket);
                    }
                }
            }
        } catch (Exception e) {}
    }
    
    public Boolean GET(String arg) throws IOException {
	parseQuery(prop, arg);
	prop.setProperty("METHOD", httpHeader.METHOD_GET);
	prop.setProperty("CLIENTIP", clientIP);
	
        // we now know the HTTP version. depending on that, we read the header
	httpHeader header;
	String httpVersion = prop.getProperty("HTTP", "HTTP/0.9");
	if (httpVersion.equals("HTTP/0.9")) {
	    header = new httpHeader(reverseMappingCache);
	} else {
	    header = readHeader();
        this.transparentProxyHandling(header);           
	}

	// managing keep-alive: in HTTP/0.9 and HTTP/1.0 every connection is closed
	// afterwards. In HTTP/1.1 (and above, in the future?) connections are
	// persistent by default, but closed with the "Connection: close"
	// property.
	boolean persistent = (!((httpVersion.equals("HTTP/0.9")) || (httpVersion.equals("HTTP/1.0"))));
	String connection = prop.getProperty(httpHeader.CONNECTION, "close").toLowerCase();
	if (connection.equals("close")) persistent = false;
	if (connection.equals("keep-alive")) persistent = true;
	    
	//System.out.println("HEADER: " + header.toString());

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
                        throw new IOException("Unable to load the soap handler");
                    }
                }
                soapHandler.doGet(prop, header, this.session.out);
                
            /*
             * Handling HTTP requests here ...
             */
            } else {                               
                if (serverAccountBase64MD5 == null) serverAccountBase64MD5 = switchboard.getConfig("serverAccountBase64MD5", "");
                if (serverAccountBase64MD5.length() == 0) {
                    // no authenticate requested
                    if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                    fileHandler.doGet(prop, header, this.session.out);
                } else {
                    String auth = (String) header.get(httpHeader.AUTHORIZATION);
                    if (auth == null) {
                        // authorization requested, but no authorizeation given in header. Ask for authenticate:
                        session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                                httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                                serverCore.crlfString).getBytes());
                        return serverCore.TERMINATE_CONNECTION;
                    } else if (serverAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(auth.trim().substring(6)))) {
                        // we are authorized
                        if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                        fileHandler.doGet(prop, header, this.session.out);
                    } else {
                        // wrong password given: ask for authenticate again
                        serverLog.logInfo("HTTPD", "Wrong log-in for account 'server' in HTTPD.GET " + prop.getProperty("PATH") + " from IP " + clientIP);
                        session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                                httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                                serverCore.crlfString).getBytes());
                        return serverCore.TERMINATE_CONNECTION;
                    }
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
		if (proxyAccountBase64MD5 == null) proxyAccountBase64MD5 = switchboard.getConfig("proxyAccountBase64MD5", "");
		if ((proxyAccountBase64MD5.length() == 0) ||
		    (proxyAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(((String) header.get(httpHeader.PROXY_AUTHORIZATION, "xxxxxx")).trim().substring(6))))) {
		    // we are authorized or no authenticate requested
		    if (proxyHandler != null) proxyHandler.doGet(prop, header, this.session.out);
		} else {
		    // ask for authenticate
		    session.out.write((httpVersion + " 407 Proxy Authentication Required" + serverCore.crlfString +
				       httpHeader.PROXY_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
				       serverCore.crlfString).getBytes());
		    return serverCore.TERMINATE_CONNECTION;
		}
	    } else {
		// not authorized through firewall blocking (ip does not match filter)
		session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.crlfString).getBytes());
		return serverCore.TERMINATE_CONNECTION;
	    }
	}
	return (persistent) ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
    }

    public Boolean HEAD(String arg) throws IOException {
	parseQuery(prop,arg);
	prop.setProperty("METHOD", httpHeader.METHOD_HEAD);
	prop.setProperty("CLIENTIP", clientIP);
	
	// we now know the HTTP version. depending on that, we read the header
	httpHeader header;
	String httpVersion = prop.getProperty("HTTP", "HTTP/0.9");
	if (httpVersion.equals("HTTP/0.9")) {
	    header = new httpHeader(reverseMappingCache);
    } else {
	    header = readHeader();
        this.transparentProxyHandling(header);  
    }

	// return multi-line message
	if (prop.getProperty("HOST").equals(virtualHost)) {
	// pass to server
            if (allowServer) {
                if (serverAccountBase64MD5 == null) serverAccountBase64MD5 = switchboard.getConfig("serverAccountBase64MD5", "");
                if (serverAccountBase64MD5.length() == 0) {
                    // no authenticate requested
                    if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                    fileHandler.doHead(prop, header, this.session.out);
                } else {
                    String auth = (String) header.get(httpHeader.AUTHORIZATION);
                    if (auth == null) {
                        // authorization requested, but no authorizeation given in header. Ask for authenticate:
                        session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                        httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                        serverCore.crlfString).getBytes());
                        return serverCore.TERMINATE_CONNECTION;
                    } else if (serverAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(auth.trim().substring(6)))) {
                        // we are authorized
                        if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                        fileHandler.doHead(prop, header, this.session.out);
                    } else {
                        // wrong password given: ask for authenticate again
                        serverLog.logInfo("HTTPD", "Wrong log-in for account 'server' in HTTPD.HEAD " + prop.getProperty("PATH") + " from IP " + clientIP);
                        session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                        httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                        serverCore.crlfString).getBytes());
                        return serverCore.TERMINATE_CONNECTION;
                    }
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
		if (proxyAccountBase64MD5 == null) proxyAccountBase64MD5 = switchboard.getConfig("proxyAccountBase64MD5", "");
		if ((proxyAccountBase64MD5.length() == 0) ||
		    (proxyAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(((String) header.get(httpHeader.PROXY_AUTHORIZATION, "xxxxxx")).trim().substring(6))))) {
		    // we are authorized or no authenticate requested
		    if (proxyHandler != null) proxyHandler.doHead(prop, header, this.session.out);
		} else {
		    // ask for authenticate
		    session.out.write((httpVersion + " 407 Proxy Authentication Required" + serverCore.crlfString +
				       httpHeader.PROXY_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
				       serverCore.crlfString).getBytes());
		    return serverCore.TERMINATE_CONNECTION;
		}
	    } else {
		// not authorized through firewall blocking (ip does not match filter)
		session.out.write((httpVersion + " 403 refused (IP not granted)" +
				   serverCore.crlfString).getBytes());
		return serverCore.TERMINATE_CONNECTION;
	    }
	}
	return serverCore.TERMINATE_CONNECTION;
    }

    public Boolean POST(String arg) throws IOException {
	parseQuery(prop, arg);
	prop.setProperty("METHOD", httpHeader.METHOD_POST);
	prop.setProperty("CLIENTIP", clientIP);

	// we now know the HTTP version. depending on that, we read the header
	httpHeader header;
	String httpVersion = prop.getProperty("HTTP", "HTTP/0.9");
	if (httpVersion.equals("HTTP/0.9")) {
	    header = new httpHeader(reverseMappingCache);
    } else {
	    header = readHeader();
        this.transparentProxyHandling(header);         
    }

	boolean persistent = (!((httpVersion.equals("HTTP/0.9")) || (httpVersion.equals("HTTP/1.0"))));
	String connection = prop.getProperty("Connection", "close").toLowerCase();
	if (connection.equals("close")) persistent = false;
	if (connection.equals("keep-alive")) persistent = true;

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
                        throw new IOException("Unable to load the soap handler");
                    }
                }
                soapHandler.doPost(prop, header, this.session.out, this.session.in);                
            /*
             * Handling normal HTTP requests here ...
             */
            } else {                         
                if (serverAccountBase64MD5 == null) serverAccountBase64MD5 = switchboard.getConfig("serverAccountBase64MD5", "");
                if (serverAccountBase64MD5.length() == 0) {
                    // no authenticate requested
                    if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                    fileHandler.doPost(prop, header, this.session.out, this.session.in);
                } else {
                    String auth = (String) header.get(httpHeader.AUTHORIZATION);
                    if (auth == null) {
                        // ask for authenticate
                        session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                                httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                                serverCore.crlfString).getBytes());
                        return serverCore.TERMINATE_CONNECTION;
                    } else if (serverAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(auth.trim().substring(6)))) {
                        // we are authorized
                        if (fileHandler == null) fileHandler  = new httpdFileHandler(this.switchboard);
                        fileHandler.doPost(prop, header, this.session.out, this.session.in);
                    } else {
                        // wrong password given: ask for authenticate again
                        serverLog.logInfo("HTTPD", "Wrong log-in for account 'server' in HTTPD.POST " + prop.getProperty("PATH") + " from IP " + clientIP);
                        session.out.write((httpVersion + " 401 log-in required" + serverCore.crlfString +
                                httpHeader.WWW_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                                serverCore.crlfString).getBytes());
                        return serverCore.TERMINATE_CONNECTION;
                    }
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
            if (proxyAccountBase64MD5 == null) proxyAccountBase64MD5 = switchboard.getConfig("proxyAccountBase64MD5", "");
            if ((proxyAccountBase64MD5.length() == 0) ||
                    (proxyAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(((String) header.get(httpHeader.PROXY_AUTHORIZATION, "xxxxxx")).trim().substring(6))))) {
                // we are authorized or no authenticate requested
                if (proxyHandler != null) proxyHandler.doPost(prop, header, this.session.out, this.session.in);
            } else {
                // ask for authenticate
                session.out.write((httpVersion + " 407 Proxy Authentication Required" + serverCore.crlfString +
                        httpHeader.PROXY_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
                        serverCore.crlfString).getBytes());
                return serverCore.TERMINATE_CONNECTION;
            }
        } else {
            // not authorized through firewall blocking (ip does not match filter)
            session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.crlfString).getBytes());
            return serverCore.TERMINATE_CONNECTION;
        }
    }
    //return serverCore.RESUME_CONNECTION;
    return (persistent) ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
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
	    if (proxyAccountBase64MD5 == null) proxyAccountBase64MD5 = switchboard.getConfig("proxyAccountBase64MD5", "");
	    if ((proxyAccountBase64MD5.length() == 0) ||
		(proxyAccountBase64MD5.equals(serverCodings.standardCoder.encodeMD5Hex(((String) header.get(httpHeader.PROXY_AUTHORIZATION, "xxxxxx")).trim().substring(6))))) {
		// we are authorized or no authenticate requested
		if (proxyHandler != null) proxyHandler.doConnect(prop, header, (InputStream) this.session.in, this.session.out);
	    } else {
		// ask for authenticate
		session.out.write((httpVersion + " 407 Proxy Authentication Required" + serverCore.crlfString +
				   httpHeader.PROXY_AUTHENTICATE + ": Basic realm=\"log-in\"" + serverCore.crlfString +
				   serverCore.crlfString).getBytes());
	    }
	} else {
	    // not authorized through firewall blocking (ip does not match filter)
	    session.out.write((httpVersion + " 403 refused (IP not granted)" + serverCore.crlfString + serverCore.crlfString + "you are not allowed to connect to this proxy, because you are using the non-granted IP " + clientIP + ". allowed are only connections that match with the following filter: " + switchboard.getConfig("proxyClient", "*") + serverCore.crlfString).getBytes());
	}

	return serverCore.TERMINATE_CONNECTION;
    }

    
    private static final Properties parseQuery(Properties prop, String s) {
        
    if (prop == null) {
        prop = new Properties();
    } else {
        prop.clear();
    }

	// this parses a whole URL
	if (s.length() == 0) {
	    prop.setProperty("HOST", virtualHost);
	    prop.setProperty("PATH", "/");
	    prop.setProperty("HTTP", "HTTP/0.9");
	    prop.setProperty("EXT", "");
	    return prop;
	}

	// store the version propery "HTTP" and cut the query at both ends
	int sep = s.indexOf(" ");
	if (sep >= 0) {
	    // HTTP version is given
	    prop.setProperty("HTTP", s.substring(sep + 1).trim());
	    s = s.substring(0, sep).trim(); // cut off HTTP version mark
	} else {
	    // HTTP version is not given, it will be treated as ver 0.9
	    prop.setProperty("HTTP", "HTTP/0.9");
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
	prop.setProperty("URL", s); // store URL
	//System.out.println("HTTPD: ARGS=" + argsString);
	if (argsString.length() != 0) prop.setProperty("ARGS", argsString); // store arguments in original form

	// find out file extension
	sep = s.lastIndexOf(".");
	if (sep >= 0) {
            if (s.indexOf("?", sep + 1) >= sep)
                prop.setProperty("EXT", s.substring(sep + 1, s.indexOf("?", sep + 1)).toLowerCase());
            else if (s.indexOf("#", sep + 1) >= sep)
                prop.setProperty("EXT", s.substring(sep + 1, s.indexOf("#", sep + 1)).toLowerCase());
            else
                prop.setProperty("EXT", s.substring(sep + 1).toLowerCase());
	} else {
	    prop.setProperty("EXT", "");
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
		prop.setProperty("HOST", virtualHost);
		prop.setProperty("PATH", "/" + s);
	    } else {
		// THIS IS THE "GOOD" CASE
		// a perfect formulated url
		prop.setProperty("HOST", s.substring(0, sep));
		prop.setProperty("PATH", s.substring(sep)); // yes, including beginning "/"
	    }
	} else {
	    // no host in url. set path
	    if (s.startsWith("/")) {
		// thats also fine, its a perfect localhost access
		// in this case, we simulate a
		// http://localhost/s
		// access by setting a virtual host
		prop.setProperty("HOST", virtualHost);
		prop.setProperty("PATH", s);
	    } else {
		// the client 'forgot' to set a leading '/'
		// this is the same case as above, with some lazyness
		prop.setProperty("HOST", virtualHost);
		prop.setProperty("PATH", "/" + s);
	    }
	}
	return prop;
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

//    public static boolean isTextMime(String mime, Set whitelist) {
//        if (whitelist.contains(mime)) return true;
//        // some mime-types are given as "text/html; charset=...", so look for ";"
//        if (mime.length() == 0) return false;
//        int pos = mime.indexOf(';');
//        if (pos < 0) return false;
//        return whitelist.contains(mime.substring(0, pos));
//    }
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
