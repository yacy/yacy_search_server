// httpc.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 26.02.2004
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.nxTools;
import de.anomic.yacy.yacyURL;

/**
* This class implements an http client. While http access is built-in in java
* libraries, it is still necessary to implement the network interface since
* otherwise there is no access to the HTTP/1.0 / HTTP/1.1 header information
* that comes along each connection.
*/

public final class httpc {

    // some constants
    /** 
     * Specifies that the httpc is allowed to use gzip content encoding for
     * http post requests 
     * @see #POST(String, httpHeader, serverObjects, HashMap)
     */
    public static final String GZIP_POST_BODY = "GZIP_POST_BODY";
    
    // final statics
    private static final String vDATE = "20040602";
    private static final int terminalMaxLength = 30000;
    private static final TimeZone GMTTimeZone = TimeZone.getTimeZone("GMT");
    private static final SimpleDateFormat HTTPGMTFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
    private static final HashMap reverseMappingCache = new HashMap();
    private static final HashSet activeConnections = new HashSet(); // all connections are stored here and deleted when they are finished
    private static final long minimumTime_before_activeConnections_cleanup = 600000;
    private static final int activeConnections_maximum = 64;
    public  static final connectionTimeComparator connectionTimeComparatorInstance = new connectionTimeComparator();
    
    private static int objCounter = 0; // will be increased with each object and is use to return a hash code
    
    // defined during set-up of switchboard
    public static boolean yacyDebugMode = false;
    
    // statics to be defined in static section below
    private static SSLSocketFactory theSSLSockFactory = null;
    public static String systemOST;
    public static String userAgent;
    
    static {
        // set the time zone
        HTTPGMTFormatter.setTimeZone(GMTTimeZone); // The GMT standard date format used in the HTTP protocol
        
        // set time-out of InetAddress.getByName cache ttl
        java.security.Security.setProperty("networkaddress.cache.ttl" , "60");
        java.security.Security.setProperty("networkaddress.cache.negative.ttl" , "0");

        // initializing a dummy trustManager to enable https connections
        
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
 
            public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
            }
 
            public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
            }
        } };
 
        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            // Create empty HostnameVerifier
            HostnameVerifier hv = new HostnameVerifier() {
                public boolean verify(String urlHostName, javax.net.ssl.SSLSession session) {
                    // logger.info("Warning: URL Host: "+urlHostName+"
                    // vs."+session.getPeerHost());
                    return true;
                }
            };
 
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(theSSLSockFactory = sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hv);
        } catch (Exception e) {
        }        

        // provide system information for client identification
        String loc = System.getProperty("user.timezone", "nowhere");
        int p = loc.indexOf("/");
        if (p > 0) loc = loc.substring(0,p);
        loc = loc + "/" + System.getProperty("user.language", "dumb");
        systemOST =
                System.getProperty("os.arch", "no-os-arch") + " " +
                System.getProperty("os.name", "no-os-name") + " " +
                System.getProperty("os.version", "no-os-version") + "; " +
                "java " + System.getProperty("java.version", "no-java-version") + "; " + loc;
        userAgent = "yacy (www.yacy.net; v" + vDATE + "; " + systemOST + ")";
    }
    
    
    // class variables
    private Socket socket = null; // client socket for commands
    public  String adressed_host = null;
    public  int    adressed_port = 80;
    private String target_virtual_host = null;
    
    // output and input streams for client control connection
    private PushbackInputStream clientInput = null;
    private OutputStream clientOutput = null;
    
    private httpdByteCountInputStream clientInputByteCount = null;
    private httpdByteCountOutputStream clientOutputByteCount = null;

    private boolean remoteProxyUse = false;
    private httpRemoteProxyConfig remoteProxyConfig = null;
    
    private String  requestPath = null;
    private boolean allowContentEncoding = true;
	
    public boolean ssl;
    public long initTime;
    public String command;
    public int timeout;
    
    private int hashIndex;


    /**
    * Initialize the httpc-instance with the given data.
    *
    * @param remoteProxyHost
    * @param remoteProxyPort
    * @throws IOException
    */
    public httpc(
            String server, 
            String vhost,
            int port, 
            int timeout, 
            boolean ssl,
            httpRemoteProxyConfig theRemoteProxyConfig,
            String incomingByteCountAccounting,
            String outgoingByteCountAccounting            
    ) throws IOException {
        
        // TODO method can be removed completely when no more CLOSE_WAIT connections appear.
    	// remove old connections
    	//checkIdleConnections();
    	
    	// register new connection
    	this.hashIndex = objCounter;
    	objCounter++;
    	synchronized (activeConnections) {activeConnections.add(this);}
    	//System.out.println("*** DEBUG init httpc: " + activeConnections.size() + " connections online");
    	
    	this.ssl = ssl;
    	this.initTime = Long.MAX_VALUE;
    	this.command = null;
    	this.timeout = timeout;
    	
        if ((theRemoteProxyConfig == null) ||
            (!theRemoteProxyConfig.useProxy())) {
            initN(
                    server,
                    vhost,
                    port,
                    timeout,
                    ssl,
                    incomingByteCountAccounting,
                    outgoingByteCountAccounting
            );
            return;
        }
        
        if (port == -1) {
            port = (ssl)? 443 : 80;
        }
        
        String remoteProxyHost = theRemoteProxyConfig.getProxyHost();
        int    remoteProxyPort = theRemoteProxyConfig.getProxyPort();
        
        this.initN(
                remoteProxyHost,
                vhost,
                remoteProxyPort,
                timeout,
                ssl,
                incomingByteCountAccounting,
                outgoingByteCountAccounting);
        
        this.remoteProxyUse = true;
        this.adressed_host = server;
        this.adressed_port = port;
        this.target_virtual_host = vhost;
        this.remoteProxyConfig = theRemoteProxyConfig;
    }

    /**
    * Convert the status of this class into an String object to output it.
    */
    public String toString() {
        return (this.adressed_host == null) ? "Disconnected" : "Connected to " + this.adressed_host +
                ((this.remoteProxyUse) ? " via " + adressed_host : "");
    }

    /**
    * Sets wether the content is allowed to be unzipped while getting?
    * FIXME: The name of this method seems misleading, if I read the usage of
    * this method correctly?
    *
    * @param status true, if the content is allowed to be decoded on the fly?
    */
    public void setAllowContentEncoding(boolean status) {
        this.allowContentEncoding = status;
    }

    /**
    * Check wether the connection of this instance is closed.
    *
    * @return true if the connection is no longer open.
    */
    public boolean isClosed() {
        if (this.socket == null) return true;
        return (!this.socket.isConnected()) || (this.socket.isClosed());
    }

    /**
    * Returns the given date in an HTTP-usable format.
    * (according to RFC822)
    *
    * @param date The Date-Object to be converted.
    * @return String with the date.
    */
    public static String dateString(Date date) {
        if (date == null) return "";
        
        /*
         * This synchronized is needed because SimpleDateFormat
         * is not thread-safe.
         * See: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6231579
         */
        synchronized(HTTPGMTFormatter) {
        	return HTTPGMTFormatter.format(date);
        }
    }

    /**
    * Returns the current date as Date-Object.
    *
    * @return Date-object with the current time.
    */
    public static Date nowDate() {
        return new GregorianCalendar(GMTTimeZone).getTime();
    }
    
    public int hashCode() {
    	// return a hash code so it is possible to store objects of httpc objects in a HashSet
    	return this.hashIndex;
    }
    
    /**
    * Initialize the https-instance with the given data. Opens the sockets to
    * the remote server and creats input and output streams.
    *
    * @param server Hostname of the server to connect to.
    * @param port On which port should we connect.
    * @param timeout How long do we wait for answers?
    * @param ssl Wether we should use SSL.
    * @throws IOException
    */
    private void initN(
            String server,
            String vhost,
            int port, 
            int timeout, 
            boolean ssl,
            String incomingByteCountAccounting,
            String outgoingByteCountAccounting
    ) throws IOException {
        //serverLog.logDebug("HTTPC", handle + " initialized");
        this.remoteProxyUse = false;

        try {
            if (port == -1) {
                port = (ssl)? 443 : 80;
            }
            
            this.adressed_host = server;
            this.adressed_port = port;
            this.target_virtual_host = vhost;
            
            // creating a socket
            this.socket = (ssl) 
                        ? theSSLSockFactory.createSocket()
                        : new Socket();
            
            // creating a socket address
            InetSocketAddress address = null;
            if (!this.remoteProxyUse) {
                // only try to resolve the address if we are not using a proxy
                InetAddress hostip = serverDomains.dnsResolve(server);
                if (hostip == null) throw new UnknownHostException(server);
                address = new InetSocketAddress(hostip, port);
            } else {
                address = new InetSocketAddress(server,port);
            }            

            // trying to establish a connection to the address
            this.initTime = System.currentTimeMillis();
            this.socket.setKeepAlive(false); //
            this.socket.connect(address, timeout);

            if (incomingByteCountAccounting != null) {
                this.clientInputByteCount = new httpdByteCountInputStream(this.socket.getInputStream(),incomingByteCountAccounting);
            }
            if (outgoingByteCountAccounting != null) {
                this.clientOutputByteCount = new httpdByteCountOutputStream(this.socket.getOutputStream(),outgoingByteCountAccounting);
            }
            
            // getting input and output streams
            this.clientInput  = new PushbackInputStream((this.clientInputByteCount!=null)?
                                this.clientInputByteCount:
                                this.socket.getInputStream()); 
            this.clientOutput = this.socket.getOutputStream();
            
            // if we reached this point, we should have a connection
        } catch (UnknownHostException e) {
            if (this.socket != null) {
                // no need to track this, the socket cannot be established
                synchronized (activeConnections) {activeConnections.remove(this);}
            }
            this.socket = null;
            throw new IOException("unknown host: " + server);
        } catch (IOException e) {
            // There was an error while connecting the socket, probably a SocketTimeoutException
            // we have to close the httpc, otherwise it would stay in activeConnections forever
            serverLog.logFine("HTTPC", "Couldn't open socket to: " + this.adressed_host + ":" + this.adressed_port);
            close();
            
            // TODO do we need to hand it over to the caller?
            throw e;
        }
    }    
    
    public long getInputStreamByteCount() {
        return (this.clientInputByteCount == null)?0:this.clientInputByteCount.getCount();
    }
    
    public long getOutputStreamByteCount() {
        return (this.clientOutputByteCount == null)?0:this.clientOutputByteCount.getCount();
    }

    // TODO remove when it's sure that the CLOSE_WAIT problem was solved.
    public static int checkIdleConnections() {
        // try to find and close all connections that did not find a target server and are idle waiting for a server socket
        
        httpc[] a = allConnections(); // put set into array to avoid ConcurrentModificationExceptions
        int tbd = 0;
        int c = 0;
        if (a.length > activeConnections_maximum) {
            // delete some connections; choose the oldest
            Arrays.sort(a, httpc.connectionTimeComparatorInstance);
            tbd = a.length - activeConnections_maximum;
            for (int i = 0; i < tbd; i++) {
                if (a[i] != null) {
                    a[i].close();
                    c++;
                }
            }
        }
        for (int i = tbd; i < a.length; i++) {
            httpc clientConnection = a[i];
            if ((clientConnection != null) &&
                (clientConnection.initTime != Long.MAX_VALUE) &&
                (clientConnection.initTime + Math.max(minimumTime_before_activeConnections_cleanup, clientConnection.timeout) < System.currentTimeMillis())) {
                // the time-out limit is reached. close the connection
                clientConnection.close();
                c++;
            }
        }
        return c;
    }
    
    public static int closeAllConnections() {
        httpc[] a = allConnections(); // put set into array to avoid ConcurrentModificationExceptions
        int c = 0;
        for (int i = 0; i < a.length; i++) {
        	httpc clientConnection = a[i];
            if (clientConnection != null) {
                clientConnection.close();
                c++;
            }
    	}
        return c;
    }
    
    public static httpc[] allConnections() {
        httpc[] a = null;
        synchronized (activeConnections) {
            a = new httpc[activeConnections.size()];
            Iterator i = httpc.activeConnections.iterator();
            int c = 0;
            while (i.hasNext()) {
                a[c++] = (httpc) i.next();
            }
        }
        return a;
    }
    
    public static class connectionTimeComparator implements Comparator {

        public connectionTimeComparator() {
            super();
        }
        
        public int compare(Object o1, Object o2) {
            httpc c1 = (httpc) o1;
            httpc c2 = (httpc) o2;
            long l1 = System.currentTimeMillis() - c1.initTime;
            long l2 = System.currentTimeMillis() - c2.initTime;
            if (l1 < l2) return 1;
            if (l1 > l2) return -1;
            return 0;
        }    
    }
    
    public void finalize() {
        this.close();
    }
    
    public void close() {
    	synchronized (activeConnections) {activeConnections.remove(this);}
    	System.out.println("*** DEBUG close httpc: " + activeConnections.size() + " connections online");
    	
        if (this.clientInput != null) {
            try {this.clientInput.close();} catch (Exception e) {}
            this.clientInput = null;
        }
        
        if (this.clientOutput != null) {
            try {this.clientOutput.close();} catch (Exception e) {}
            this.clientOutput = null;
        }
        
        if (this.socket != null) {
            try {this.socket.close();} catch (Exception e) {}
            this.socket = null;
        }
        
        if (this.clientInputByteCount != null) {
            this.clientInputByteCount.finish();
            this.clientInputByteCount = null;
        }
        
        if (this.clientOutputByteCount != null) {
            this.clientOutputByteCount.finish();
            this.clientOutputByteCount = null;
        }

        this.adressed_host = null;
        this.target_virtual_host = null;

        this.remoteProxyConfig = null;
        this.requestPath = null;
    }

    /**
     * This method invokes a call to the given server.
     * 
     * @param method
     *            Which method should be called? GET, POST, HEAD or CONNECT
     * @param path
     *            String with the path on the server to be get.
     * @param header
     *            The prefilled header (if available) from the calling browser.
     * @param zipped
     *            Is encoded content (gzip) allowed or not?
     * @throws IOException
     */
    private void send(String method, String path, httpHeader header, boolean zipped) throws IOException {
        // scheduled request through request-response objects/threads

        // check and correct path
        if ((path == null) || (path.length() == 0)) path = "/";

        // for debuggug:
        this.requestPath = path;

        // prepare header
        if (header == null) header = new httpHeader();

        // set some standard values
        if (!(header.containsKey(httpHeader.ACCEPT)))
            header.put(httpHeader.ACCEPT, "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        if (!(header.containsKey(httpHeader.ACCEPT_CHARSET)))
            header.put(httpHeader.ACCEPT_CHARSET, "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        if (!(header.containsKey(httpHeader.ACCEPT_LANGUAGE)))
            header.put(httpHeader.ACCEPT_LANGUAGE, "en-us,en;q=0.5");
        if (!(header.containsKey(httpHeader.KEEP_ALIVE)))
            header.put(httpHeader.KEEP_ALIVE, "300");

        // set user agent. The user agent is only set if the value does not yet exists.
        // this gives callers the opportunity, to change the user agent themselves, and
        // it will not be changed.
        if (!(header.containsKey(httpHeader.USER_AGENT))) header.put(httpHeader.USER_AGENT, userAgent);

        // set the host attribute. This is in particular necessary, if we contact another proxy
        // the host is mandatory, if we use HTTP/1.1
        if (!(header.containsKey(httpHeader.HOST))) {
            if (this.remoteProxyUse) {
                header.put(httpHeader.HOST, this.adressed_host);
            } else {
                header.put(httpHeader.HOST, this.target_virtual_host);
            }
        }
        
        if (this.remoteProxyUse) {
            String remoteProxyUser = this.remoteProxyConfig.getProxyUser();
            String remoteProxyPwd  = this.remoteProxyConfig.getProxyPwd();
            if ((remoteProxyUser!=null)&&(remoteProxyUser.length()>0)) {
                header.put(httpHeader.PROXY_AUTHORIZATION,"Basic " + kelondroBase64Order.standardCoder.encodeString(remoteProxyUser + ":" + remoteProxyPwd));
            }
        }

        if (!(header.containsKey(httpHeader.CONNECTION))) {
            header.put(httpHeader.CONNECTION, "close");
        }
        
        // stimulate zipping or not
        // we can unzip, and we will return it always as unzipped, unless not wanted
        if (header.containsKey(httpHeader.ACCEPT_ENCODING)) {
            String encoding = (String) header.get(httpHeader.ACCEPT_ENCODING);
            if (zipped) {
                if (encoding.indexOf("gzip") < 0) {
                    // add the gzip encoding
                    //System.out.println("!!! adding gzip encoding");
                    header.put(httpHeader.ACCEPT_ENCODING, "gzip,deflate" + ((encoding.length() == 0) ? "" : (";" + encoding)));
                }
            } else {
                int pos  = encoding.indexOf("gzip");
                if (pos >= 0) {
                    // remove the gzip encoding
                    //System.out.println("!!! removing gzip encoding");
                	// ex: "gzip,deflate" => pos == 0, but we need to remove the "," as well => substring(pos+5),
                	// ex: "gzip" => pos == 0, but substring(pos+5) would exceed boundaries
                	String enc = encoding.substring(0, pos) + (encoding.length() > (pos+5) ? encoding.substring(pos + 5) : "");
                	header.put(httpHeader.ACCEPT_ENCODING, enc);
                }
            }
        } else {
            if (zipped) header.put(httpHeader.ACCEPT_ENCODING, "gzip,deflate");
        }

        //header = new httpHeader(); header.put("Host", this.host); // debug

        StringBuffer sb = new StringBuffer();
        // send request
        if ((this.remoteProxyUse) && (!(method.equals(httpHeader.METHOD_CONNECT))))
            path = ((this.adressed_port == 443) ? "https://" : "http://") + this.adressed_host + ":" + this.adressed_port + path;
        sb.append(method + " " + path + " HTTP/1.0" + serverCore.crlfString); // TODO if set to HTTP/1.1, servers give time-outs?

        // send header
        //System.out.println("***HEADER for path " + path + ": PROXY TO SERVER = " + header.toString()); // DEBUG
        Iterator i = header.keySet().iterator();
        String key;
        int count;
        char tag;
        while (i.hasNext()) {
            key = (String) i.next();
            tag = key.charAt(0);
            if ((tag != '*') && (tag != '#')) {
                count = header.keyCount(key);
                for (int j = 0; j < count; j++) {
                    sb.append(key + ": " + ((String) header.getSingle(key, j)).trim() + serverCore.crlfString);
                }
                //System.out.println("#" + key + ": " + value);
            }
        }

        // add terminating line
        sb.append(serverCore.crlfString);
        serverCore.send(this.clientOutput, sb.toString());
        this.clientOutput.flush();

        // this is the place where www.stern.de refuses to answer ..???
    }

    /**
    * This method GETs a page from the server.
    *
    * @param path The path to the page which should be GET.
    * @param requestHeader Prefilled httpHeader.
    * @return Instance of response with the content.
    * @throws IOException
    */
    public response GET(String path, httpHeader requestHeader) throws IOException {
        //serverLog.logDebug("HTTPC", handle + " requested GET '" + path + "', time = " + (System.currentTimeMillis() - handle));
    	this.command = "GET " + path;
    	try {
            boolean zipped = (!this.allowContentEncoding) ? false : httpd.shallTransportZipped(path);
            send(httpHeader.METHOD_GET, path, requestHeader, zipped);
            response r = new response(zipped);
            //serverLog.logDebug("HTTPC", handle + " returned GET '" + path + "', time = " + (System.currentTimeMillis() - handle));
            return r;
        } catch (Exception e) {
            if (e.getMessage().indexOf("heap space") > 0) {
                e.printStackTrace();
            }
            throw new IOException(e.getMessage());
        }
    }

    /**
    * This method gets only the header of a page.
    *
    * @param path The path to the page whose header should be get.
    * @param requestHeader Prefilled httpHeader.
    * @return Instance of response with the content.
    * @throws IOException
    */
    public response HEAD(String path, httpHeader requestHeader) throws IOException {
    	this.command = "HEAD " + path;
        try {
            send(httpHeader.METHOD_HEAD, path, requestHeader, false);
            return new response(false);
            // in this case the caller should not read the response body,
            // since there is none...
        } catch (SocketException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
    * This method POSTs some data to a page.
    *
    * @param path The path to the page which the post is sent to.
    * @param requestHeader Prefilled httpHeader.
    * @param ins InputStream with the data to be posted to the server.
    * @return Instance of response with the content.
    * @throws IOException
    */
    public response POST(String path, httpHeader requestHeader, InputStream ins) throws IOException {
    	this.command = "POST " + path;
        try {
            send(httpHeader.METHOD_POST, path, requestHeader, false);
            // if there is a body to the call, we would have a CONTENT-LENGTH tag in the requestHeader
            String cl = (String) requestHeader.get(httpHeader.CONTENT_LENGTH);
            int len, c;
            byte[] buffer = new byte[512];
            if (cl != null) {
                len = Integer.parseInt(cl);
                // transfer len bytes from ins to the server
                while ((len > 0) && ((c = ins.read(buffer)) >= 0)) {
                    this.clientOutput.write(buffer, 0, c);
                    len -= c;
                }
            } else {
                len = 0;
                while ((c = ins.read(buffer)) >= 0) {
                    this.clientOutput.write(buffer, 0, c);
                    len += c;
                }
                
                // TODO: we can not set the header here. This ist too late
                requestHeader.put(httpHeader.CONTENT_LENGTH, Integer.toString(len));
            }
            this.clientOutput.flush();
            return new response(false);
        } catch (SocketException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
    * Call the server with the CONNECT-method.
    * This is used to establish https-connections through a https-proxy
    *
    * @param host To which host should a connection be made?
    * @param port Which port should be connected?
    * @param requestHeader prefilled httpHeader.
    * @return Instance of response with the content.
    */
    
    public response CONNECT(String remotehost, int remoteport, httpHeader requestHeader) throws IOException {
    	this.command = "CONNECT " + remotehost + ":" + remoteport;
        try {
            send(httpHeader.METHOD_CONNECT, remotehost + ":" + remoteport, requestHeader, false);
            return new response(false);
        } catch (SocketException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
    * This method sends several files at once via a POST request. Only those
    * files in the Hashtable files are written whose names are contained in
    * args.
    *
    * @param path The path to the page which the post is sent to.
    * @param requestHeader Prefilled httpHeader.
    * @param args serverObjects with the names of the files to send.
    * @param files HashMap with the names of the files as key and the content
    * of the files as value.
    * @return Instance of response with the content.
    * @throws IOException
    */
    public response POST(String path, httpHeader requestHeader, serverObjects args, HashMap files) throws IOException {
        // make shure, the header has a boundary information like
        // CONTENT-TYPE=multipart/form-data; boundary=----------0xKhTmLbOuNdArY
        if (requestHeader == null) requestHeader = new httpHeader();
        String boundary = (String) requestHeader.get(httpHeader.CONTENT_TYPE);
        if (boundary == null) {
            // create a boundary
            boundary = "multipart/form-data; boundary=----------" + java.lang.System.currentTimeMillis();
            requestHeader.put(httpHeader.CONTENT_TYPE, boundary);
        }
        // extract the boundary string
        int pos = boundary.toUpperCase().indexOf("BOUNDARY=");
        if (pos < 0) {
            // again, create a boundary
            boundary = "multipart/form-data; boundary=----------" + java.lang.System.currentTimeMillis();
            requestHeader.put(httpHeader.CONTENT_TYPE, boundary);
            pos = boundary.indexOf("boundary=");
        }
        boundary = "--" + boundary.substring(pos + "boundary=".length());

        boolean zipContent = args.containsKey(GZIP_POST_BODY);
        args.remove(GZIP_POST_BODY);
        
        OutputStream out;
        GZIPOutputStream zippedOut;
        serverByteBuffer buf = new serverByteBuffer();
        if (zipContent) {
            zippedOut = new GZIPOutputStream(buf);
            out = zippedOut;
        } else {
            out = buf;
        }
        
        // in contrast to GET and HEAD, this method also transports a message body
        // the body consists of repeated boundaries and values in between
        if (args.size() != 0) {
            // we have values for the POST, start with one boundary
            String key, value;
            Enumeration e = args.keys();
            while (e.hasMoreElements()) {
                // start with a boundary
                out.write(boundary.getBytes("UTF-8"));
                out.write(serverCore.crlf);
                // write value
                key = (String) e.nextElement();
                value = args.get(key, "");
                if ((files != null) && (files.containsKey(key))) {
                    // we are about to write a file
                    out.write(("Content-Disposition: form-data; name=" + '"' + key + '"' + "; filename=" + '"' + value + '"').getBytes("UTF-8"));
                    out.write(serverCore.crlf);
                    out.write(serverCore.crlf);
                    out.write((byte[]) files.get(key));
                    out.write(serverCore.crlf);
                } else {
                    // write a single value
                    out.write(("Content-Disposition: form-data; name=" + '"' + key + '"').getBytes("UTF-8"));
                    out.write(serverCore.crlf);
                    out.write(serverCore.crlf);
                    out.write(value.getBytes("UTF-8"));
                    out.write(serverCore.crlf);
                }
            }
            // finish with a boundary
            out.write(boundary.getBytes("UTF-8"));
            out.write(serverCore.crlf);
        }
        // create body array
        out.close();
        byte[] body = buf.toByteArray();
        buf = null; out = null;
        
        //System.out.println("DEBUG: PUT BODY=" + new String(body));
        if (zipContent) {
            requestHeader.put(httpHeader.CONTENT_ENCODING, "gzip");
            
            //TODO: should we also set the content length here?
        } else {
            // size of that body            
            requestHeader.put(httpHeader.CONTENT_LENGTH, Integer.toString(body.length));
        }
        
        // send the header
        send(httpHeader.METHOD_POST, path, requestHeader, false);
        
        // send the body
        serverCore.send(this.clientOutput, body);

        return new response(false);
    }

    public static byte[] singleGET(
            String realhost,
            String virtualhost,
            int port, 
            String path, 
            int timeout,
            String user, 
            String password, 
            boolean ssl,
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader,
            File download
    ) throws IOException {
    	// if download == null, the get result is stored to a byte[] and returned,
    	// otherwise the get is streamed to the file and null is returned
        if (requestHeader == null) requestHeader = new httpHeader();
        
        // setting host authorization header
        if ((user != null) && (password != null) && (user.length() != 0)) {
            requestHeader.put(httpHeader.AUTHORIZATION, kelondroBase64Order.standardCoder.encodeString(user + ":" + password));
        }

        httpc con = new httpc(realhost, virtualhost, port, timeout, ssl, theRemoteProxyConfig, null, null);

        httpc.response res = con.GET(path, requestHeader);
        if (res.status.startsWith("2")) {
            if (download == null) {
                // stream to byte[]
                serverByteBuffer sbb = new serverByteBuffer();
                res.writeContent(sbb, null);
                con.close();
                return sbb.getBytes();
            } else {
                // stream to file and return null
                res.writeContent(null, download);
                con.close();
                return null;
            }
        }
        return res.status.getBytes();
    }

    public static byte[] singleGET(
            yacyURL u,
            String vhost,
            int timeout,
            String user, 
            String password,
            httpRemoteProxyConfig theRemoteProxyConfig,
            File download
    ) throws IOException {
        int port = u.getPort();
        boolean ssl = u.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443: 80;
        String path = u.getPath();
        String query = u.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        return singleGET(u.getHost(), vhost, port, path, timeout, user, password, ssl, theRemoteProxyConfig, null, download);
    }

    public static byte[] singlePOST(
            String realhost, 
            String virtualhost, 
            int port, 
            String path, 
            int timeout,
            String user, 
            String password, 
            boolean ssl,
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader, 
            serverObjects props,
            HashMap files
    ) throws IOException {

        if (requestHeader == null) requestHeader = new httpHeader();
        if ((user != null) && (password != null) && (user.length() != 0)) {
            requestHeader.put(httpHeader.AUTHORIZATION, kelondroBase64Order.standardCoder.encodeString(user + ":" + password));
        }

        httpc con = new httpc(realhost, virtualhost, port, timeout, ssl, theRemoteProxyConfig, null, null);
        httpc.response res = con.POST(path, requestHeader, props, files);

        //System.out.println("response=" + res.toString());
        if (!(res.status.startsWith("2"))) {
        	byte[] status = res.status.getBytes();
            con.close();
            return status;
        }
            
        // read connection body and return body
        serverByteBuffer sbb = new serverByteBuffer();
        res.writeContent(sbb, null);
        con.close();
        return sbb.getBytes();
    }

    public static byte[] singlePOST(
            yacyURL u, 
            String vhost,
            int timeout,
            String user, 
            String password,
            httpRemoteProxyConfig theRemoteProxyConfig,
            serverObjects props,
            HashMap files
    ) throws IOException {
        int port = u.getPort();
        boolean ssl = u.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        String path = u.getPath();
        String query = u.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        return singlePOST(
                u.getHost(),
                vhost,
                port, 
                path, 
                timeout, 
                user, 
                password, 
                ssl, 
                theRemoteProxyConfig, 
                null, 
                props,
                files
        );
    }
    
    public static byte[] wget(
            yacyURL url,
            String vhost,
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader,
            File download
    ) throws IOException {
        
        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443: 80;
        String path = url.getPath();
        String query = url.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        
        // splitting of the byte array into lines
        byte[] a = singleGET(
                url.getHost(),
                vhost,
                port, 
                path, 
                timeout, 
                user, 
                password, 
                ssl, 
                theRemoteProxyConfig, 
                requestHeader,
                download
        );
        
        if (a == null) return null;
        
        // support of gzipped data (requested by roland)      
        a = serverFileUtils.uncompressGZipArray(a);

        // return result
        return a;
    }
    
    public static Map loadHashMap(yacyURL url, httpRemoteProxyConfig proxy) {
        try {
            // should we use the proxy?
            boolean useProxy = (proxy != null) &&  
                               (proxy.useProxy()) && 
                               (proxy.useProxy4Yacy());
            
            // sending request
            final HashMap result = nxTools.table(
                    httpc.wget(
                            url,
                            url.getHost(),
                            8000, 
                            null, 
                            null, 
                            (useProxy) ? proxy : null,
                            null,
                            null
                    )
                    , "UTF-8");
            
            if (result == null) return new HashMap();
            return result;
        } catch (Exception e) {
            return new HashMap();
        }
    }

    public static httpHeader whead(
            yacyURL url,
            String vhost,
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig
    ) throws IOException {
        return whead(url,vhost,timeout,user,password,theRemoteProxyConfig,null);
    }
    
    public static httpHeader whead(
            yacyURL url,
            String vhost,
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig,
            httpHeader requestHeader
    ) throws IOException {
        // generate request header
        if (requestHeader == null) requestHeader = new httpHeader();
        if ((user != null) && (password != null) && (user.length() != 0)) {
            requestHeader.put(httpHeader.AUTHORIZATION, kelondroBase64Order.standardCoder.encodeString(user + ":" + password));
        }
        // parse query

        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        String path = url.getPath();
        String query = url.getQuery();
        if ((query != null) && (query.length() > 0)) path = path + "?" + query;
        String realhost = url.getHost();

        // start connection
        httpc con = new httpc(realhost, vhost, port, timeout, ssl, theRemoteProxyConfig, null, null);
        httpc.response res = con.HEAD(path, requestHeader);
        httpHeader h = res.responseHeader;
        con.close();
        return h;
    }

    public static byte[] wput(
            yacyURL url,
            String vhost,
            int timeout, 
            String user, 
            String password, 
            httpRemoteProxyConfig theRemoteProxyConfig, 
            serverObjects props,
            HashMap files
    ) throws IOException {
        // splitting of the byte array into lines
        byte[] a = singlePOST(
                url,
                vhost,
                timeout, 
                user, 
                password, 
                theRemoteProxyConfig, 
                props,
                files
        );
        
        if (a == null) return null;
        
        // support of gzipped data  
        a = serverFileUtils.uncompressGZipArray(a);

        // return result
        return a;
        
        //System.out.println("wput-out=" + new String(a));
        //return nxTools.strings(a);
    }

    public static void main(String[] args) {
        System.out.println("ANOMIC.DE HTTP CLIENT v" + vDATE);
        String url = args[0];
        if (!(url.toUpperCase().startsWith("HTTP://"))) url = "http://" + url;
        ArrayList text = new ArrayList();
        if (args.length == 4) {
            int timeout = Integer.parseInt(args[1]);
            String proxyHost = args[2];
            int proxyPort = Integer.parseInt(args[3]);
            
            httpRemoteProxyConfig theRemoteProxyConfig = httpRemoteProxyConfig.init(proxyHost,proxyPort);
            try {
                yacyURL u = new yacyURL(url, null);
                text = nxTools.strings(wget(u, u.getHost(), timeout, null, null, theRemoteProxyConfig, null, null));
            } catch (MalformedURLException e) {
                System.out.println("The url '" + url + "' is wrong.");
            } catch (IOException e) {
                System.out.println("Error loading url '" + url + "': " + e.getMessage());
            }
        } /*else {
            serverObjects post = new serverObjects();
            int p;
            for (int i = 1; i < args.length; i++) {
                p = args[i].indexOf("=");
                if (p > 0) post.put(args[i].substring(0, p), args[i].substring(p + 1));
            }
            text = wput(url, post);
        }*/
        Iterator i = text.listIterator();
        while (i.hasNext()) System.out.println((String) i.next());
    }

    /**
    * Inner Class to get the response of an http-request and parse it.
    */
    public final class response {
        // Response-Header  = Date | Pragma | Allow | Content-Encoding | Content-Length | Content-Type |
        //                    Expires | Last-Modified | HTTP-header
        /*
          Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
          1xx: Informational - Not used, but reserved for future use
          2xx: Success - The action was successfully received, understood, and accepted.
          3xx: Redirection - Further action must be taken in order to complete the request
          4xx: Client Error - The request contains bad syntax or cannot be fulfilled
          5xx: Server Error - The server failed to fulfill an apparently valid request
         */

        // header information
        public httpHeader responseHeader = null;
        public String httpVer = "HTTP/0.9";
        public String status; // the success/failure response string starting with status-code
        public int statusCode = 503;
        public String statusText = "internal error";
        private boolean gzip; // for gunzipping on-the-fly
        private long gzippedLength = -1; // reported content length if content-encoding is set
        
        /**
        * Constructor for this class. Reads in the content for the given outer
        * instance and parses it.
        *
        * @param zipped true, if the content of this response is gzipped.
        * @throws IOException
        */
        public response(boolean zipped) throws IOException {

            // lets start with worst-case attributes as set-up
            this.responseHeader = new httpHeader(reverseMappingCache);
            this.statusCode = 503;
            this.statusText = "internal httpc error";
            this.status = Integer.toString(this.statusCode) + " " + this.statusText;
            this.gzip   = false;

            // check connection status
            if (httpc.this.clientInput == null) {
                // the server has meanwhile disconnected
                this.statusCode = 503;
                this.statusText = "lost connection to server";
                this.status = Integer.toString(this.statusCode) + " " + this.statusText;                
                return; // in bad mood
            }

            // reads in the http header, right now, right here
            byte[] b = serverCore.receive(httpc.this.clientInput, terminalMaxLength, false);
            if (b == null) {
                // the server has meanwhile disconnected
                this.statusCode = 503;
                this.statusText = "server has closed connection";
                this.status = Integer.toString(this.statusCode) + " " + this.statusText;
                return; // in bad mood
            }
            
            // parsing the response status line
            String buffer = new String(b);        
            Object[] responseInfo = httpHeader.parseResponseLine(buffer);
            this.httpVer =    (String) responseInfo[0];
            this.statusCode = ((Integer)responseInfo[1]).intValue();
            this.statusText = (String) responseInfo[2];
            this.status = this.statusCode + " " + this.statusText;
            
            if ((this.statusCode==500)&&(this.statusText.equals("status line parse error"))) {
                // flush in anything that comes without parsing
                while ((b != null) && (b.length != 0)) b = serverCore.receive(httpc.this.clientInput, terminalMaxLength, false);
                return; // in bad mood                
            }
                        
            // check validity
            if (this.statusCode == 400) {
                // bad request
                // flush in anything that comes without parsing
                while ((b = serverCore.receive(httpc.this.clientInput, terminalMaxLength, false)).length != 0) {}
                return; // in bad mood
            }

            // at this point we should have a valid response. read in the header properties
            String key = "";
            while ((b = serverCore.receive(httpc.this.clientInput, terminalMaxLength, false)) != null) {
                if (b.length == 0) break;
                buffer = new String(b);
                buffer=buffer.trim();
                //System.out.println("#H#" + buffer); // debug
                if (buffer.charAt(0) <= 32) {
                    // use old entry
                    if (key.length() == 0) throw new IOException("header corrupted - input error");
                    // attach new line
                    if (!(this.responseHeader.containsKey(key))) throw new IOException("header corrupted - internal error");
                    this.responseHeader.put(key, (String) this.responseHeader.get(key) + " " + buffer.trim());
                } else {
                    // create new entry
                    int p = buffer.indexOf(":");
                    if (p > 0) {
                        this.responseHeader.add(buffer.substring(0, p).trim(), buffer.substring(p + 1).trim());
                    } else {
                        serverLog.logSevere("HTTPC", "RESPONSE PARSE ERROR: HOST='" + httpc.this.adressed_host + "', PATH='" + httpc.this.requestPath + "', STATUS='" + this.status + "'");
                        serverLog.logSevere("HTTPC", "..............BUFFER: " + buffer);
                        throw new IOException(this.status);
                    }
                }
            }
            // finished with reading header

            // we will now manipulate the header if the content is gzip encoded, because
            // reading the content with "writeContent" will gunzip on-the-fly
            this.gzip = ((zipped) && (this.responseHeader.gzip()));

            if (this.gzip) {
                if (this.responseHeader.containsKey(httpHeader.CONTENT_LENGTH)) {
                    this.gzippedLength = this.responseHeader.contentLength(); 
                }
                this.responseHeader.remove(httpHeader.CONTENT_ENCODING); // we fake that we don't have encoding, since what comes out does not have gzip and we also don't know what was encoded
                this.responseHeader.remove(httpHeader.CONTENT_LENGTH); // we cannot use the length during gunzippig yet; still we can hope that it works
            }
        }
        
        public long getGzippedLength() {
            return this.gzippedLength;
        }
        
        public boolean isGzipped() {
            return this.gzip;
        }

        /**
        * Converts an instance of this class into a readable string.
        *
        * @return String with some information about this instance.
        */
        public String toString() {
            StringBuffer toStringBuffer = new StringBuffer();
            toStringBuffer.append((this.status == null) ? "Status: Unknown" : "Status: " + this.status)
            .append(" | Headers: ")
            .append((this.responseHeader == null) ? "none" : this.responseHeader.toString());
            return new String(toStringBuffer);
        }

        /**
        * Returns wether this request was successful or not. Stati beginning
        * with 2 or 3 are considered successful.
        *
        * @return True, if the request was successful.
        */
        public boolean success() {
            return ((this.status.charAt(0) == '2') || (this.status.charAt(0) == '3'));
        }

        /**
         * If the response was encoded using <code>Content-Encoding: gzip</code>
         * a {@link GZIPInputStream} is returned. If the <code>Content-Length</code> header was set,
         * a {@link httpContentLengthInputStream} is returned which returns <code>-1</code> if the end of the
         * response body was reached.
         * 
         * @return a {@link InputStream} to read the response body
         * @throws IOException
         */
        public InputStream getContentInputStream() throws IOException {
            if (this.gzip) {
                // use a gzip input stream for Content-Encoding: gzip
                return new GZIPInputStream(httpc.this.clientInput);
            } else if (this.responseHeader.contentLength() != -1) {
                // use a httpContentLengthInputStream to read until the end of the response body is reached
                return new httpContentLengthInputStream(httpc.this.clientInput,this.responseHeader.contentLength());
            } 
            // no Content-Lenght was set. In this case we can read until EOF
            return httpc.this.clientInput;
        }
        
        /**
        * This method outputs the found content into an byte-array and
        * additionally outputs it to procOS.
        *
        * @param procOS
        * @return the found content
        * @throws IOException
        */
        public byte[] writeContent(Object procOS, boolean returnByteArray) throws IOException {
            serverByteBuffer sbb = null;
            
            if (returnByteArray) {
                int contentLength = (int) this.responseHeader.contentLength();
                sbb = new serverByteBuffer((contentLength==-1)?8192:contentLength);
            }
            
            if (procOS instanceof OutputStream) {
                serverFileUtils.writeX(this.getContentInputStream(), (OutputStream) procOS, sbb);
            } else if (procOS instanceof Writer) {
                String charSet = this.responseHeader.getCharacterEncoding();
                if (charSet == null) charSet = httpHeader.DEFAULT_CHARSET;
                serverFileUtils.writeX(this.getContentInputStream(), charSet, (Writer) procOS, sbb, charSet);                
            } else {
                throw new IllegalArgumentException("Invalid procOS object type '" + procOS.getClass().getName() + "'");
            }
            
            return (sbb==null)?null:sbb.getBytes();
        }        

        /**
        * This method writes the input stream to either another output stream
        * or a file or both.
        * In case that an exception occurrs, the stream reading is just teminated
        * and content received so far is returned
        *
        * @param procOS
        * @param file
        */
        public void writeContent(Object procOS, File file) {
            // this writes the input stream to either another output stream or
            // a file or both.
            FileOutputStream bufferOS = null;
            if (file != null) try {
                bufferOS = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                file = null;
            }
            try {
                InputStream is = this.getContentInputStream();
                if (procOS == null) {
                    serverFileUtils.writeX(is, null, bufferOS);
                } else if (procOS instanceof OutputStream) {
                    serverFileUtils.writeX(is, (OutputStream) procOS, bufferOS);
                    //writeContentX(httpc.this.clientInput, this.gzip, this.responseHeader.contentLength(), procOS, bufferOS);
                } else if (procOS instanceof Writer) {
                    String charSet = this.responseHeader.getCharacterEncoding();
                    if (charSet == null) charSet = httpHeader.DEFAULT_CHARSET;
                    serverFileUtils.writeX(is, charSet, (Writer) procOS, bufferOS, charSet);                
                } else {
                    throw new IllegalArgumentException("Invalid procOS object type '" + procOS.getClass().getName() + "'");
                }
            } catch (IOException e) {}
            
            if (bufferOS != null) {
                try {
                    bufferOS.flush();
                    bufferOS.close();
                } catch (IOException e) {}
                if (file.length() == 0) file.delete();
            }
        }
        
        /**
        * This method outputs a logline to the serverlog with the current
        * status of this instance.
        */
        public void print() {
            serverLog.logInfo("HTTPC", "RESPONSE: status=" + this.status + ", header=" + this.responseHeader.toString());
        }

    }

}

/*
import java.net.*;
import java.io.*;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;
import java.security.KeyStore;


 //The application can be modified to connect to a server outside
 //the firewall by following SSLSocketClientWithTunneling.java.

public class SSLSocketClientWithClientAuth {

    public static void main(String[] args) throws Exception {
        String host = null;
        int port = -1;
        String path = null;
        for (int i = 0; i < args.length; i++)
            System.out.println(args[i]);

        if (args.length < 3) {
            System.out.println(
                "USAGE: java SSLSocketClientWithClientAuth " +
                "host port requestedfilepath");
            System.exit(-1);
        }

        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
            path = args[2];
        } catch (IllegalArgumentException e) {
             System.out.println("USAGE: java SSLSocketClientWithClientAuth " +
                 "host port requestedfilepath");
             System.exit(-1);
        }

        try {

            SSLSocketFactory factory = null;
            try {
                SSLContext ctx;
                KeyManagerFactory kmf;
                KeyStore ks;
                char[] passphrase = "passphrase".toCharArray();

                ctx = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");

                ks.load(new FileInputStream("testkeys"), passphrase);

                kmf.init(ks, passphrase);
                ctx.init(kmf.getKeyManagers(), null, null);

                factory = ctx.getSocketFactory();
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }

            SSLSocket socket = (SSLSocket)factory.createSocket(host, port);

            socket.startHandshake();

            PrintWriter out = new PrintWriter(
                                  new BufferedWriter(
                                  new OutputStreamWriter(
                                  socket.getOutputStream())));
            out.println("GET " + path + " HTTP/1.1");
            out.println();
            out.flush();

            if (out.checkError())
                System.out.println(
                    "SSLSocketClient: java.io.PrintWriter error");

            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                    socket.getInputStream()));

            String inputLine;

            while ((inputLine = in.readLine()) != null)
                System.out.println(inputLine);

            in.close();
            out.close();
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
 */
