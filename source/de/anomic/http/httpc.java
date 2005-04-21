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

/*
   Class documentation:
   this class forms an http client
   while http access is built-in in java libraries, it is still
   necessary to implement the network interface since otherwise
   there is no access to the HTTP/1.0 / HTTP/1.1 header information
   that comes along each connection.
*/

package de.anomic.http;

import java.io.*;
import java.net.*;
import java.text.*;
import java.lang.*;
import java.util.*;
import java.util.zip.*;
import de.anomic.server.*;
import de.anomic.server.serverCore.Session;
import de.anomic.server.serverCore.SessionFactory;
import de.anomic.server.serverCore.SessionPool;

import javax.net.ssl.SSLSocketFactory; 

import org.apache.commons.pool.impl.GenericObjectPool;

public final class httpc {

    // statics
    private static final String vDATE = "20040602";
    private static String userAgent;
    public static String systemOST;
    private static final int terminalMaxLength = 30000;
    private static final TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    
    // --- The GMT standard date format used in the HTTP protocol
    private static final SimpleDateFormat HTTPGMTFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    private static final SimpleDateFormat EMLFormatter     = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US);
    private static final SimpleDateFormat ShortFormatter   = new SimpleDateFormat("yyyyMMddHHmmss");
    //Mo 06 Sep 2004 23:32 
    private static final HashMap reverseMappingCache = new HashMap();

    // class variables
    private Socket socket = null; // client socket for commands
    private String host = null;
    private long timeout;
    private long handle;
    
    // output and input streams for client control connection
    PushbackInputStream clientInput = null;
    OutputStream clientOutput = null;

    private boolean remoteProxyUse = false;
    private String  savedRemoteHost = null;
    private String  requestPath = null;

    // the dns cache
    private static final HashMap nameCacheHit = new HashMap();
    //private static HashSet nameCacheMiss = new HashSet();
    
    static {
    	// set time-out of InetAddress.getByName cache ttl
    	java.security.Security.setProperty("networkaddress.cache.ttl" , "60");
        java.security.Security.setProperty("networkaddress.cache.negative.ttl" , "0");
    }
    
    /**
     * A Object Pool containing all pooled httpc-objects.
     * @see httpcPool
     */
    private static final httpcPool theHttpcPool;
    
    /**
     * Indicates if the current object was removed from pool because the maximum limit
     * was exceeded.
     */
    boolean removedFromPool = false;
    
    // Configuring the httpc object pool
    static {
        // implementation of session thread pool
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        
        // The maximum number of active connections that can be allocated from pool at the same time,
        // 0 for no limit
        config.maxActive = 150;
        
        // The maximum number of idle connections connections in the pool
        // 0 = no limit.        
        config.maxIdle = 75;
        config.minIdle = 10;    
        
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK; 
        config.minEvictableIdleTimeMillis = 30000; 
        
        theHttpcPool = new httpcPool(new httpcFactory(),config);         
    }

    /**
     * A reusable readline buffer
     * @see serverByteBuffer
     */
    final serverByteBuffer readLineBuffer = new serverByteBuffer();
    
    public static httpc getInstance(
            String server, 
            int port, 
            int timeout, 
            boolean ssl,
            String remoteProxyHost,  
            int remoteProxyPort
        ) throws IOException {
        
        httpc newHttpc;
        try {
            // fetching a new httpc from the object pool
            newHttpc = (httpc) httpc.theHttpcPool.borrowObject();            
        } catch (Exception e) {
            throw new IOException("Unable to initialize a new httpc. " + e.getMessage());
        }            
            
        // initialize it
        newHttpc.init(server,port,timeout,ssl,remoteProxyHost, remoteProxyPort);        
        return newHttpc;
    }
    
    public static httpc getInstance(String server, int port, int timeout, boolean ssl) throws IOException {
        
        httpc newHttpc = null;
        try {
            // fetching a new httpc from the object pool
            newHttpc = (httpc) httpc.theHttpcPool.borrowObject();
            
        } catch (Exception e) {
            throw new IOException("Unable to initialize a new httpc. " + e.getMessage());
        }                 
        
        // initialize it
        newHttpc.init(server,port,timeout,ssl);        
        return newHttpc;
            
       
    }    
    
    public static void returnInstance(httpc theHttpc) {
        try {
            theHttpc.reset();
            httpc.theHttpcPool.returnObject(theHttpc);
        } catch (Exception e) {
            // we could ignore this error 
        }
    }

    protected void finalize() throws Throwable {
        if (!this.removedFromPool) System.err.println("Httpc object was not returned to object pool.");
        this.reset();
        httpc.theHttpcPool.invalidateObject(this);
    }
    
    public static String dnsResolve(String host) {
        // looks for the ip of host <host> and returns ip number as string
        String ip = (String) nameCacheHit.get(host);
        if (ip != null) return ip;
        // if (nameCacheMiss.contains(host)) return null;
        try {
            ip = InetAddress.getByName(host).getHostAddress();
            if ((ip != null) && (!(ip.equals("127.0.0.1"))) && (!(ip.equals("localhost")))) {
                nameCacheHit.put(host, ip);
                return ip;
            } else {
                return null;
            }
        } catch (UnknownHostException e) {
            //nameCacheMiss.add(host);
        }
        return null;
    }

    public static boolean dnsFetch(String host) {
        // looks for the ip of host <host> and returns false if the host was in the cache
        // if it is not in the cache the ip is fetched and this resturns true
        if ((nameCacheHit.get(host) != null) /*|| (nameCacheMiss.contains(host)) */) return false;
        try {
            String ip = InetAddress.getByName(host).getHostAddress();
            if ((ip != null) && (!(ip.equals("127.0.0.1"))) && (!(ip.equals("localhost")))) {
                nameCacheHit.put(host, ip);
                return true;
            } else {
                return false;
            }
        } catch (UnknownHostException e) {
            //nameCacheMiss.add(host);
            return false;
        }
    }
    
    void reset() {
        try {
            if (this.clientInput != null) {
                this.clientInput.close();
                this.clientInput = null;
            }
            if (this.clientOutput != null) {
                this.clientOutput.close();
                this.clientOutput = null;
            }
            if (this.socket != null) {
                this.socket.close();
                this.socket = null;
            }
            
            this.host = null;
            this.timeout = 0;
            this.handle = 0;
            
            this.remoteProxyUse = false;
            this.savedRemoteHost = null;
            this.requestPath = null;
        } catch (Exception e) {
            // we could ignore this ...
        }
    }
      
    
    // http client
    
    void init(String server, int port, int timeout, boolean ssl,
		String remoteProxyHost,  int remoteProxyPort) throws IOException {
    	this.init(remoteProxyHost, remoteProxyPort, timeout, ssl);
    	this.remoteProxyUse = true;
    	this.savedRemoteHost = server + ((port == 80) ? "" : (":" + port));
    }

    void init(String server, int port, int timeout, boolean ssl) throws IOException {
        handle = System.currentTimeMillis();
        //serverLog.logDebug("HTTPC", handle + " initialized");
    	this.remoteProxyUse = false;
    	this.timeout = timeout;
            this.savedRemoteHost = server;
    	try {
    	    this.host = server + ((port == 80) ? "" : (":" + port));
    	    String hostip;
    	    if ((server.equals("localhost")) || (server.equals("127.0.0.1")) || (server.startsWith("192.168.")) || (server.startsWith("10."))) {
                hostip = server;
    	    } else {
        		hostip = dnsResolve(server);
        		if (hostip == null) throw new UnknownHostException(server);
    	    }
                if (ssl)
                    socket = SSLSocketFactory.getDefault().createSocket(hostip, port);
                else
                    socket = new Socket(hostip, port);
    	    socket.setSoTimeout(timeout); // waiting time for write
    	    //socket.setSoLinger(true, timeout); // waiting time for read
    	    socket.setKeepAlive(true); //
    	    clientInput  = new PushbackInputStream(socket.getInputStream());
    	    clientOutput = socket.getOutputStream();
    	    // if we reached this point, we should have a connection
    	} catch (UnknownHostException e) {
    	    throw new IOException("unknown host: " + server);
    	}
    }

    // provide HTTP date handling static methods
    public static String dateString(Date date) {
	if (date == null) return ""; else return HTTPGMTFormatter.format(date);
    }

    public static Date nowDate() {
	return new GregorianCalendar(GMTTimeZone).getTime();
    }

    static {
	// provide system information for client identification
	String loc = System.getProperty("user.timezone", "nowhere");
	int p = loc.indexOf("/");
	if (p > 0) loc = loc.substring(0,p);
	loc = loc + "/" + System.getProperty("user.language", "dumb");
	systemOST =
	    System.getProperty("os.arch", "no-os-arch") + " " + System.getProperty("os.name", "no-os-arch") + " " +
	    System.getProperty("os.version", "no-os-version") + "; " + 
	    "java " + System.getProperty("java.version", "no-java-version") + "; " + loc;
	userAgent = "yacy (www.yacy.net; v" + vDATE + "; " + systemOST + ")";
    }
    
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
	public String status; // the success/failure response string starting with status-code
	private boolean gzip; // for gunzipping on-the-fly
        private long gzipLength; // zipped-length of the response

	public response(boolean zipped) throws IOException {

	    // lets start with worst-case attributes as set-up
	    responseHeader = new httpHeader(reverseMappingCache);
	    status = "503 internal error";
	    gzip   = false;

	    // check connection status
	    if (clientInput == null) {
		// the server has meanwhile disconnected
		status = "503 lost connection to server";
		return; // in bad mood
	    }

	    // reads in the http header, right now, right here
	    byte[] b = serverCore.receive(clientInput, readLineBuffer, timeout, terminalMaxLength, false);
	    if (b == null) {
		// the server has meanwhile disconnected
		status = "503 server has closed connection";
		return; // in bad mood
	    }
	    String buffer = new String(b); // this is the status response line
            //System.out.println("#S#" + buffer);
            int p = buffer.indexOf(" ");
	    if (p < 0) {
		status = "500 status line parse error";
		// flush in anything that comes without parsing
		while ((b = serverCore.receive(clientInput, readLineBuffer, timeout, terminalMaxLength, false)).length != 0) {}
		return; // in bad mood
	    }
	    // we have a status
	    status = buffer.substring(p + 1).trim(); // the status code plus reason-phrase

	    // check validity
	    if (status.startsWith("400")) {
		// bad request
		// flush in anything that comes without parsing
		while ((b = serverCore.receive(clientInput, readLineBuffer, timeout, terminalMaxLength, false)).length != 0) {}
		return; // in bad mood
	    }

	    // at this point we should have a valid response. read in the header properties
	    String key = "";
	    String value = "";
	    while ((b = serverCore.receive(clientInput, readLineBuffer, timeout, terminalMaxLength, false)) != null) {
		if (b.length == 0) break;
		buffer = new String(b);
		//System.out.println("#H#" + buffer); // debug
		if (buffer.charAt(0) <= 32) {
		    // use old entry
		    if (key.length() == 0) throw new IOException("header corrupted - input error");
		    // attach new line
		    if (!(responseHeader.containsKey(key))) throw new IOException("header corrupted - internal error");
		    responseHeader.put(key, (String) responseHeader.get(key) + " " + buffer.trim());
		} else {
		    // create new entry
		    p = buffer.indexOf(":");
		    if (p > 0) {
			key = buffer.substring(0, p).trim();
			value = (String) responseHeader.get(key);
			// check if the header occurred already
			if (value == null) {
			    // create new entry
			    responseHeader.put(key, buffer.substring(p + 1).trim());
			} else {
			    // attach to old entry
			    responseHeader.put(key, value + "#" + buffer.substring(p + 1).trim());
			}
		    } else {
			serverLog.logError("HTTPC", "RESPONSE PARSE ERROR: HOST='" + host + "', PATH='" + requestPath + "', STATUS='" + status + "'");
			serverLog.logError("HTTPC", "..............BUFFER: " + buffer);
		    }
		}
	    }
	    // finished with reading header

	    // we will now manipulate the header if the content is gzip encoded, because
	    // reading the content with "writeContent" will gunzip on-the-fly
	    gzip = ((zipped) && (responseHeader.gzip()));
                
	    if (gzip) {
		// change attributes in case of gzip decoding
                gzipLength = responseHeader.contentLength();
		responseHeader.remove("CONTENT-ENCODING"); // we fake that we don't have encoding, since what comes out does not have gzip and we also don't know what was encoded
                responseHeader.remove("CONTENT-LENGTH"); // we cannot use the length during gunzippig yet; still we can hope that it works
	    } else {
                gzipLength = -1;
            }

            //System.out.println("###incoming header: " + responseHeader.toString());
            
	    // the body must be read separately by the get/writeContent methods
	    //System.out.println("## connection is " + ((socket.isClosed()) ? "closed" : "open") + ".");
	}

	public boolean success() {
	    return ((status.charAt(0) == '2') || (status.charAt(0) == '3'));
	}

	public byte[] writeContent(OutputStream procOS) throws IOException {
        int contentLength = (int) this.responseHeader.contentLength();
	    serverByteBuffer sbb = new serverByteBuffer((contentLength==-1)?8192:contentLength);
	    writeContentX(procOS, sbb, httpc.this.clientInput);
	    return sbb.getBytes();
	}

	public void writeContent(OutputStream procOS, File file) throws IOException {
	    // this writes the input stream to either another output stream or
	    // a file or both.
	    FileOutputStream bufferOS = null;
	    if (file != null) bufferOS = new FileOutputStream(file);
	    writeContentX(procOS, bufferOS, httpc.this.clientInput);
	    if (bufferOS != null) {
		bufferOS.close();
		if (file.length() == 0) file.delete();
	    }
	}

	public void writeContentX(OutputStream procOS, OutputStream bufferOS, InputStream clientInput) throws IOException {
	    // we write length bytes, but if length == -1 (or < 0) then we
	    // write until the input stream closes
	    // procOS == null -> no write to procOS
	    // file == null -> no write to file
	    // If the Content-Encoding is gzip, we gunzip on-the-fly
	    // and change the Content-Encoding and Content-Length attributes in the header
	    byte[] buffer = new byte[2048];
	    int l;
            long len = 0;
                
	    // find out length
	    long length = responseHeader.contentLength();

	    // we have three methods of reading: length-based, length-based gzip and connection-close-based
	    if (length > 0) {
		// we read exactly 'length' bytes
		try {
                    while ((len < length) && ((l = clientInput.read(buffer)) >= 0)) {
                        if (procOS != null) procOS.write(buffer, 0, l);
                        if (bufferOS != null) bufferOS.write(buffer, 0, l);
                        len += l;
                    }
                } catch (java.net.SocketException e) {
                    // this is an error:
                    throw new IOException("Socket exception: " + e.getMessage());
                } catch (java.net.SocketTimeoutException e) {
                    // this is an error:
                    throw new IOException("Socket time-out: " + e.getMessage());
                }
            } else if ((gzip) && (gzipLength > 0) && (gzipLength < 100000)) {
                //System.out.println("PERFORMING NEW GZIP-LENGTH-BASED HTTPC: gzipLength=" + gzipLength); // DEBUG
                // we read exactly 'gzipLength' bytes; first copy into buffer:
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((len < gzipLength) && ((l = clientInput.read(buffer)) >= 0)) {
                    baos.write(buffer, 0, l);
                    len += l;
                }
                baos.flush();
                // now uncompress
                InputStream dis = new GZIPInputStream(new ByteArrayInputStream(baos.toByteArray()));
                try {
                    while ((l = dis.read(buffer)) > 0) {
                        if (procOS != null) procOS.write(buffer, 0, l);
                        if (bufferOS != null) bufferOS.write(buffer, 0, l);
                        len += l;
                    }
                } catch (java.net.SocketException e) {
                    // this is an error:
                    throw new IOException("Socket exception: " + e.getMessage());
                } catch (java.net.SocketTimeoutException e) {
                    // this is an error:
                    throw new IOException("Socket time-out: " + e.getMessage());
                }
                baos.close(); baos = null;
	    } else {
    		// no content-length was given, thus we read until the connection closes
    		InputStream dis = (gzip) ? (InputStream) new GZIPInputStream(clientInput) : (InputStream) clientInput;
    		try {
    		    while ((l = dis.read(buffer, 0, buffer.length)) >= 0) {
        			if (procOS != null) procOS.write(buffer, 0, l);
        			if (bufferOS != null) bufferOS.write(buffer, 0, l);
    		    }
    		} catch (java.net.SocketException e) {
    		    // this is not an error: it's ok, we waited for that
    		} catch (java.net.SocketTimeoutException e) {
                    // the same here; should be ok.
            }
	    }

	    // close the streams
	    if (procOS != null) procOS.flush();
	    if (bufferOS != null) bufferOS.flush();
	    buffer = null;
	}

	public void print() {
	    serverLog.logInfo("HTTPC", "RESPONSE: status=" + status + ", header=" + responseHeader.toString());
	}

    }

    public void close() {
	// closes the connection
	try {
	    clientInput.close();
	    clientOutput.close();
	    socket.close();
	} catch (IOException e) {}
    }

    // method is either GET, HEAD or POST
    private void send(String method, String path, httpHeader header, boolean zipped) throws IOException {
	// scheduled request through request-response objects/threads

	// check and correct path
	if ((path == null) || (path.length() == 0)) path = "/";

	// for debuggug:
	requestPath = path;

	// prepare header
	if (header == null) header = new httpHeader();

        // set some standard values
        if (!(header.containsKey("Accept")))
            header.put("Accept", "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        if (!(header.containsKey("Accept-Charset")))
            header.put("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        if (!(header.containsKey("Accept-Language")))
            header.put("Accept-Language", "en-us,en;q=0.5");
        if (!(header.containsKey("Keep-Alive")))
            header.put("Keep-Alive", "300");
        
	// set user agent. The user agent is only set if the value does not yet exists.
	// this gives callers the opportunity, to change the user agent themselves, and
	// it will not be changed.
	if (!(header.containsKey("User-Agent"))) header.put("User-Agent", userAgent);

	// set the host attribute. This is in particular necessary, if we contact another proxy
	// the host is mandatory, if we use HTTP/1.1
	if (!(header.containsKey("Host"))) {
	    if (this.remoteProxyUse)
		header.put("Host", savedRemoteHost);
	    else
		header.put("Host", this.host);
	}

	if (!(header.containsKey("Connection"))) {
	    header.put("Connection", "close");
	}

	// advertise a little bit...
	if ((!(header.containsKey("Referer"))) || (((String) header.get("Referer")).trim().length() == 0))  {
            header.put("Referer",
                       (((System.currentTimeMillis() >> 10) & 1) == 0) ?
                       "http://www.anomic.de" :
                       "http://www.yacy.net/yacy");
        }

	// stimulate zipping or not
	// we can unzip, and we will return it always as unzipped, unless not wanted
	if (header.containsKey("Accept-Encoding")) {
	    String encoding = (String) header.get("Accept-Encoding");
	    if (zipped) {
		if (encoding.indexOf("gzip") < 0) {
		    // add the gzip encoding
		    //System.out.println("!!! adding gzip encoding");
		    header.put("Accept-Encoding", "gzip,deflate" + ((encoding.length() == 0) ? "" : (";" + encoding)));
		}
	    } else {
		int pos  = encoding.indexOf("gzip");
		if (pos >= 0) {
		    // remove the gzip encoding
		    //System.out.println("!!! removing gzip encoding");
		    header.put("Accept-Encoding", encoding.substring(0, pos) + encoding.substring(pos + 4));
		}
	    }
	} else {
	    if (zipped) header.put("Accept-Encoding", "gzip,deflate");
	}

	//header = new httpHeader(); header.put("Host", this.host); // debug

	// send request
	if ((this.remoteProxyUse) && (!(method.equals("CONNECT"))))
	    path = "http://" + this.savedRemoteHost + path;
	serverCore.send(clientOutput, method + " " + path + " HTTP/1.0"); // if set to HTTP/1.1, servers give time-outs?

        // send header
	//System.out.println("***HEADER for path " + path + ": PROXY TO SERVER = " + header.toString()); // DEBUG
	Iterator i = header.keySet().iterator();
	String key;
	String value;
	int pos;
	while (i.hasNext()) {
	    key = (String) i.next();
	    value = (String) header.get(key);
	    while ((pos = value.lastIndexOf("#")) >= 0) {
		// special handling is needed if a key appeared several times, which is valid.
		// all lines with same key are combined in one value, separated by a "#"
		serverCore.send(clientOutput, key + ": " + value.substring(pos + 1).trim());
		//System.out.println("**+" + key + ": " + value.substring(pos + 1).trim()); // debug
		value = value.substring(0, pos).trim();
	    }
	    serverCore.send(clientOutput, key + ": " + value);
	    //System.out.println("***" + key + ": " + value); // debug
	}

	// send terminating line
	serverCore.send(clientOutput, "");
	clientOutput.flush();

	// this is the place where www.stern.de refuses to answer ..???
    }

	
    private boolean shallTransportZipped(String path) {
	return (!((path.endsWith(".gz")) || (path.endsWith(".tgz")) ||
		  (path.endsWith(".jpg")) || (path.endsWith(".jpeg")) ||
		  (path.endsWith(".gif")) | (path.endsWith(".zip"))));
    }
    
    public response GET(String path, httpHeader requestHeader) throws IOException {
        //serverLog.logDebug("HTTPC", handle + " requested GET '" + path + "', time = " + (System.currentTimeMillis() - handle));
	try {
	    boolean zipped = shallTransportZipped(path);
	    send("GET", path, requestHeader, zipped);
	    response r = new response(zipped);
            //serverLog.logDebug("HTTPC", handle + " returned GET '" + path + "', time = " + (System.currentTimeMillis() - handle));
            return r;
        } catch (SocketException e) {
	    throw new IOException(e.getMessage());
	}
    }

    public response HEAD(String path, httpHeader requestHeader) throws IOException {
	try {
	    send("HEAD", path, requestHeader, false);
	    return new response(false);
	    // in this case the caller should not read the response body,
	    // since there is none...
	} catch (SocketException e) {
	    throw new IOException(e.getMessage());
	}
    }

    public response POST(String path, httpHeader requestHeader, InputStream ins) throws IOException {
	try {
	    send("POST", path, requestHeader, false);
	    // if there is a body to the call, we would have a CONTENT-LENGTH tag in the requestHeader
	    String cl = (String) requestHeader.get("CONTENT-LENGTH");
            int len, c;
            byte[] buffer = new byte[512];
	    if (cl != null) {
		len = Integer.parseInt(cl);
		// transfer len bytes from ins to the server
		while ((len > 0) && ((c = ins.read(buffer)) >= 0)) {
                    clientOutput.write(buffer, 0, c);
                    len -= c;
                }
	    } else {
                len = 0;
		while ((c = ins.read(buffer)) >= 0) {
                    clientOutput.write(buffer, 0, c);
                    len += c;
                }
                requestHeader.put("CONTENT-LENGTH", "" + len);
            }
            clientOutput.flush();
	    return new response(false);
	} catch (SocketException e) {
	    throw new IOException(e.getMessage());
	}
    }

    public response CONNECT(String host, int port, httpHeader requestHeader) throws IOException {
	try {
	    send("CONNECT", host + ":" + port, requestHeader, false);
	    return new response(false);
	} catch (SocketException e) {
	    throw new IOException(e.getMessage());
	}
    }


    public response POST(String path, httpHeader requestHeader, serverObjects args, Hashtable files) throws IOException {
	// make shure, the header has a boundary information like
	// CONTENT-TYPE=multipart/form-data; boundary=----------0xKhTmLbOuNdArY
	if (requestHeader == null) requestHeader = new httpHeader();
	String boundary = (String) requestHeader.get("CONTENT-TYPE");
	if (boundary == null) {
	    // create a boundary
	    boundary = "multipart/form-data; boundary=----------" + java.lang.System.currentTimeMillis();
	    requestHeader.put("CONTENT-TYPE", boundary);
	}
	// extract the boundary string
	int pos = boundary.toUpperCase().indexOf("BOUNDARY=");
	if (pos < 0) {
	    // again, create a boundary
	    boundary = "multipart/form-data; boundary=----------" + java.lang.System.currentTimeMillis();
	    requestHeader.put("CONTENT-TYPE", boundary);
	    pos = boundary.indexOf("boundary=");
	}
	boundary = "--" + boundary.substring(pos + "boundary=".length());

	ByteArrayOutputStream buf = new ByteArrayOutputStream();
	// in contrast to GET and HEAD, this method also transports a message body
	// the body consists of repeated boundaries and values in between
	if (args.size() != 0) {
	    // we have values for the POST, start with one boundary
	    String key, value;
	    Enumeration e = args.keys();
	    while (e.hasMoreElements()) {
		// start with a boundary
		buf.write(boundary.getBytes());
		buf.write(serverCore.crlf);
                // write value
		key = (String) e.nextElement();
		value = (String) args.get(key, "");
		if ((files != null) && (files.containsKey(key))) {
		    // we are about to write a file
		    buf.write(("Content-Disposition: form-data; name=" + '"' + key + '"' + "; filename=" + '"' + value + '"').getBytes());
                    buf.write(serverCore.crlf);
		    buf.write(serverCore.crlf);
		    buf.write((byte[]) files.get(key));
		    buf.write(serverCore.crlf);
		} else {
		    // write a single value
		    buf.write(("Content-Disposition: form-data; name=" + '"' + key + '"').getBytes());
		    buf.write(serverCore.crlf);
		    buf.write(serverCore.crlf);
                    buf.write(value.getBytes());
		    buf.write(serverCore.crlf);
                }
	    }
	    // finish with a boundary
	    buf.write(boundary.getBytes());
            buf.write(serverCore.crlf);
	    //buf.write("" + serverCore.crlfString);
	}
        // create body array
        buf.close();
        byte[] body = buf.toByteArray();
        //System.out.println("DEBUG: PUT BODY=" + new String(body));
        // size of that body
	requestHeader.put("CONTENT-LENGTH", "" + body.length);
	// send the header
	//System.out.println("header=" + requestHeader);
	send("POST", path, requestHeader, false);
	// send the body
	//System.out.println("body=" + buf.toString());
	serverCore.send(clientOutput, body);

	return new response(false);
    }

    /*
DEBUG: PUT BODY=------------1090358578442
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
    
    /*
------------0xKhTmLbOuNdArY
Content-Disposition: form-data; name="file1"; filename="dir.gif"
Content-Type: image/gif

GIF89
------------0xKhTmLbOuNdArY
Content-Disposition: form-data; name="file2"; filename=""


------------0xKhTmLbOuNdArY
Content-Disposition: form-data; name="upload"

do upload
------------0xKhTmLbOuNdArY--

###### Listing Properties ######
# METHOD=POST
### Header Values:
# EXT=html
# HTTP=HTTP/1.1
# ACCEPT-ENCODING=gzip, deflate;q=1.0, identity;q=0.5, *;q=0
# HOST=localhost:8080
# PATH=/testcgi/doit.html
# CONTENT-LENGTH=474
# CONTENT-TYPE=multipart/form-data; boundary=----------0xKhTmLbOuNdArY
# ARGC=0
# CONNECTION=close
# USER-AGENT=Mozilla/5.0 (Macintosh; U; PPC Mac OS X; de-de) AppleWebKit/103u (KHTML, like Gecko) Safari/100.1
### Call Properties:
###### End OfList ######
    */

    public static byte[] singleGET(String host, int port, String path, int timeout,
                                   String user, String password, boolean ssl,
                                   String proxyHost,  int proxyPort,
                                   httpHeader requestHeader) throws IOException {
    	if (requestHeader == null) requestHeader = new httpHeader();
    	if ((user != null) && (password != null) && (user.length() != 0)) {
    	    requestHeader.put("Authorization", serverCodings.standardCoder.encodeBase64String(user + ":" + password));
    	}
        
        httpc con = null;
        try {        

            if ((proxyHost == null) || (proxyPort == 0)) {
                con = httpc.getInstance(host, port, timeout, ssl);
            } else { 
                con = httpc.getInstance(host, port, timeout, ssl, proxyHost, proxyPort);
            }
                
        	httpc.response res = con.GET(path, null);
        	if (res.status.startsWith("2")) {
        	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
        	    res.writeContent(bos, null);
        	    con.close();
        	    return bos.toByteArray();
        	} else {
        	    return res.status.getBytes();
        	}
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } finally {
            if (con != null) httpc.returnInstance(con);
        }
        
    }

    public static byte[] singleGET(URL u, int timeout,
                                   String user, String password,
                                   String proxyHost, int proxyPort) throws IOException {
	int port = u.getPort();
        boolean ssl = u.getProtocol().equals("https");
	if (port < 0) port = (ssl) ? 443: 80;
	String path = u.getPath();
	String query = u.getQuery();
	if ((query != null) && (query.length() > 0)) path = path + "?" + query;
	return singleGET(u.getHost(), port, path, timeout, user, password, ssl, proxyHost, proxyPort, null);
    }

    /*
    public static byte[] singleGET(String url, int timeout) throws IOException {
	try {
	    return singleGET(new URL(url), timeout, null, null, null, 0);
	} catch (MalformedURLException e) {
	    throw new IOException("Malformed URL: " + e.getMessage());
	}
    }
    */
    
    public static byte[] singlePOST(String host, int port, String path, int timeout,
                                    String user, String password, boolean ssl,
                                    String proxyHost, int proxyPort,
                                    httpHeader requestHeader, serverObjects props) throws IOException {
        
    	if (requestHeader == null) requestHeader = new httpHeader();
    	if ((user != null) && (password != null) && (user.length() != 0)) {
    	    requestHeader.put("Authorization", serverCodings.standardCoder.encodeBase64String(user + ":" + password));
    	}
        
    	httpc con = null;
        try {
                if ((proxyHost == null) || (proxyPort == 0))
                    con = httpc.getInstance(host, port, timeout, ssl);
                else 
                    con = httpc.getInstance(host, port, timeout, ssl, proxyHost, proxyPort);
        	httpc.response res = con.POST(path, null, props, null);
            
        	//System.out.println("response=" + res.toString());
        	if (res.status.startsWith("2")) {
        	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
        	    res.writeContent(bos, null);
        	    con.close();
        	    return bos.toByteArray();
        	} else {
        	    return res.status.getBytes();
        	}
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } finally {
            if (con != null) httpc.returnInstance(con);
        }
        
    }

    public static byte[] singlePOST(URL u, int timeout,
                                    String user, String password,
                                    String proxyHost, int proxyPort,
                                    serverObjects props) throws IOException {
	int port = u.getPort();
        boolean ssl = u.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
	String path = u.getPath();
	String query = u.getQuery();
	if ((query != null) && (query.length() > 0)) path = path + "?" + query;
	return singlePOST(u.getHost(), port, path, timeout, user, password, ssl, proxyHost, proxyPort, null, props);
    }

    public static byte[] singlePOST(String url, int timeout, serverObjects props) throws IOException {
	try {
	    return singlePOST(new URL(url), timeout, null, null, null, 0, props);
	} catch (MalformedURLException e) {
	    throw new IOException("Malformed URL: " + e.getMessage());
	}
    }

    public static Vector wget(URL url, int timeout, String user, String password, String proxyHost, int proxyPort) throws IOException {
	// splitting of the byte array into lines
	byte[] a = singleGET(url, timeout, user, password, proxyHost, proxyPort);
        if (a == null) return null;
	int s = 0;
	int e;
	Vector v = new Vector();
	while (s < a.length) {
	    e = s; while (e < a.length) if (a[e++] < 32) {e--; break;}
	    v.add(new String(a, s, e - s));
	    s = e; while (s < a.length) if (a[s++] >= 32) {s--; break;}
	}
	return v;
    }
    
    public static httpHeader whead(URL url, int timeout, String user, String password, String proxyHost, int proxyPort) throws IOException {
        // generate request header
        httpHeader requestHeader = new httpHeader();
    	if ((user != null) && (password != null) && (user.length() != 0)) {
    	    requestHeader.put("Authorization", serverCodings.standardCoder.encodeBase64String(user + ":" + password));
    	}
        // parse query
        
        int port = url.getPort();
        boolean ssl = url.getProtocol().equals("https");
    	if (port < 0) port = (ssl) ? 443 : 80;
    	String path = url.getPath();
    	String query = url.getQuery();
    	if ((query != null) && (query.length() > 0)) path = path + "?" + query;
    	String host = url.getHost();
        
        // start connection
        httpc con = null;
        try {        	
            if ((proxyHost == null) || (proxyPort == 0))
                 con = httpc.getInstance(host, port, timeout, ssl);
            else con = httpc.getInstance(host, port, timeout, ssl, proxyHost, proxyPort);
            
        	httpc.response res = con.HEAD(path, requestHeader);
        	if (res.status.startsWith("2")) {
                    // success
        	    return res.responseHeader;
        	} else {
                    // fail
        	    return res.responseHeader;
        	}
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } finally {
            if (con != null) httpc.returnInstance(con);
        }
    }

    /*
    public static Vector wget(String url) {
	try {
	    return wget(new URL(url), 5000, null, null, null, 0);
	} catch (IOException e) {
	    Vector ll = new Vector();
	    ll.add("503 " + e.getMessage());
	    return ll;
	}
    }
    */

    public static Vector wput(URL url, int timeout, String user, String password, String proxyHost, int proxyPort, serverObjects props) throws IOException {
	// splitting of the byte array into lines
	byte[] a = singlePOST(url, timeout, user, password, proxyHost, proxyPort, props);
	//System.out.println("wput-out=" + new String(a));
	int s = 0;
	int e;
	Vector v = new Vector();
	while (s < a.length) {
	    e = s; while (e < a.length) if (a[e++] < 32) {e--; break;}
	    v.add(new String(a, s, e - s));
	    s = e; while (s < a.length) if (a[s++] >= 32) {s--; break;}
	}
	return v;
    }

    /*
    public static Vector wput(String url, serverObjects props) {
	try {
	    return wput(url, 5000, null, null, null, 0, props);
	} catch (IOException e) {
	    serverLog.logError("HTTPC", "wput exception for url " + url + ": " + e.getMessage());
	    e.printStackTrace();
	    Vector ll = new Vector();
	    ll.add("503 " + e.getMessage());
	    return ll;
	}
    }
    */
    
    public static void main(String[] args) {
	System.out.println("ANOMIC.DE HTTP CLIENT v" + vDATE);
	String url = args[0];
	if (!(url.toUpperCase().startsWith("HTTP://"))) url = "http://" + url;
	Vector text = new Vector();
	if (args.length == 4) {
            int timeout = Integer.parseInt(args[1]);
            String proxyHost = args[2];
            int proxyPort = Integer.parseInt(args[3]);
            try {
                text = wget(new URL(url), timeout, null, null, proxyHost, proxyPort);
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
	Enumeration i = text.elements();
	while (i.hasMoreElements()) System.out.println((String) i.nextElement());
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

final class httpcFactory implements org.apache.commons.pool.PoolableObjectFactory {

    public httpcFactory() {
        super();  
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
     */
    public Object makeObject() throws Exception {
        return new httpc();
    }          
    
     /**
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object obj) {
        if (obj instanceof httpc) {
            httpc theHttpc = (httpc) obj;
            theHttpc.removedFromPool = true;
        }
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object obj) {
        if (obj instanceof httpc) 
        {
            httpc theHttpc = (httpc) obj;
            return true;
        }
        return true;
    }
    
    /**
     * @param obj 
     * 
     */
    public void activateObject(Object obj)  {
        //log.debug(" activateObject...");
    }

    /**
     * @param obj 
     * 
     */
    public void passivateObject(Object obj) { 
        //log.debug(" passivateObject..." + obj);
        if (obj instanceof Session)  {
            httpc theHttpc = (httpc) obj;              
        }
    }
}    

final class httpcPool extends GenericObjectPool {
    /**
     * First constructor.
     * @param objFactory
     */        
    public httpcPool(httpcFactory objFactory) {
        super(objFactory);
        this.setMaxIdle(75); // Maximum idle threads.
        this.setMaxActive(150); // Maximum active threads.
        this.setMinEvictableIdleTimeMillis(30000); //Evictor runs every 30 secs.
        //this.setMaxWait(1000); // Wait 1 second till a thread is available
    }
    
    public httpcPool(httpcFactory objFactory,
                     GenericObjectPool.Config config) {
        super(objFactory, config);
    }
    
    /**
     * @see org.apache.commons.pool.impl.GenericObjectPool#borrowObject()
     */
    public Object borrowObject() throws Exception  {
       return super.borrowObject();
    }

    /**
     * @see org.apache.commons.pool.impl.GenericObjectPool#returnObject(java.lang.Object)
     */
    public void returnObject(Object obj) throws Exception  {
        super.returnObject(obj);
    }        
}    
