//icapd.java
//-----------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.icap;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import de.anomic.net.URL;
import java.util.Date;
import java.util.Properties;

import de.anomic.http.httpChunkedInputStream;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.http.ResourceInfo;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverHandler;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverCore.Session;

/**
 * @author theli
 */
public class icapd implements serverHandler {
    
    
    private serverCore.Session session;  // holds the session object of the calling class
    
    // the connection properties
    private final Properties prop = new Properties();
    
    // the address of the client
    private InetAddress userAddress;
    private String clientIP;
    private int keepAliveRequestCount = 0;
    
    // needed for logging
    private final serverLog log = new serverLog("ICAPD");
    
    private static plasmaSwitchboard switchboard = null;
    private static plasmaHTCache cacheManager = null;
    private static String virtualHost = null;
    private static boolean keepAliveSupport = true;
    
    
    
    public icapd() {
        if (switchboard == null) {
            switchboard = plasmaSwitchboard.getSwitchboard();
            cacheManager = switchboard.cacheManager;
            virtualHost = switchboard.getConfig("fileHost","localhost");
        }
        
    }
    
    public Object clone(){
        return new icapd();
    }
    
    public void initSession(Session session) throws IOException {
        this.session = session;
        this.userAddress = session.userAddress; // client InetAddress
        this.clientIP = this.userAddress.getHostAddress();
        if (this.userAddress.isAnyLocalAddress()) this.clientIP = "localhost";
        if (this.clientIP.equals("0:0:0:0:0:0:0:1")) this.clientIP = "localhost";
        if (this.clientIP.equals("127.0.0.1")) this.clientIP = "localhost";
    }
    
    public String greeting() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public String error(Throwable e) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public void reset() {
    }
    
    public Boolean EMPTY(String arg) throws IOException {
        // TODO Auto-generated method stub
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean UNKNOWN(String requestLine) throws IOException {
        // TODO Auto-generated method stub
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public icapHeader getDefaultHeaders() {
        icapHeader newHeaders = new icapHeader();
        
        newHeaders.put(icapHeader.SERVER,"YaCy/" + switchboard.getConfig("vString",""));
        newHeaders.put(icapHeader.DATE, httpc.dateString(httpc.nowDate()));
        newHeaders.put(icapHeader.ISTAG, "\"" + switchboard.getConfig("vString","") + "\"");
        
        return newHeaders;
    }
    
    public Boolean OPTIONS(String arg) throws IOException {
        
        BufferedOutputStream out = new BufferedOutputStream(this.session.out);   
        
        // parsing the http request line
        parseRequestLine(icapHeader.METHOD_OPTIONS,arg);        
        
        // reading the headers
        icapHeader icapReqHeader = icapHeader.readHeader(this.prop,this.session);
        
        // determines if the connection should be kept alive
        boolean persistent = handlePersistentConnection(icapReqHeader);              
        
        // setting the icap response headers
        icapHeader resHeader = getDefaultHeaders();        
        resHeader.put(icapHeader.ALLOW,"204");
        resHeader.put(icapHeader.ENCAPSULATED,"null-body=0");
        resHeader.put(icapHeader.MAX_CONNECTIONS,"1000");
        resHeader.put(icapHeader.OPTIONS_TTL,"300");
        resHeader.put(icapHeader.SERVICE_ID, "???");
        resHeader.put(icapHeader.PREVIEW, "30");
        resHeader.put(icapHeader.TRANSFER_COMPLETE, "*");
        //resHeader.put(icapHeader.TRANSFER_PREVIEW, "*");
        if (!persistent) resHeader.put(icapHeader.CONNECTION, "close");

        
        // determining the requested service and call it or send back an error message
        String reqService = this.prop.getProperty(icapHeader.CONNECTION_PROP_PATH,"");
        if (reqService.equalsIgnoreCase("/resIndexing")) {
            resHeader.put(icapHeader.SERVICE, "YaCy ICAP Indexing Service 1.0");
            resHeader.put(icapHeader.METHODS,icapHeader.METHOD_RESPMOD);
            
            String transferIgnoreList = plasmaParser.getMediaExtList();  
            transferIgnoreList = transferIgnoreList.substring(1,transferIgnoreList.length()-1);
            resHeader.put(icapHeader.TRANSFER_IGNORE, transferIgnoreList);
        } else {
            resHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
        }
        
        
        StringBuffer headerStringBuffer = resHeader.toHeaderString("ICAP/1.0",200,null);        
        out.write(headerStringBuffer.toString().getBytes());
        out.flush();        
        
        return this.prop.getProperty(icapHeader.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean REQMOD(String arg) {
        return serverCore.TERMINATE_CONNECTION;
    }    
    
    public Boolean RESPMOD(String arg) {
        try {
            InputStream in = this.session.in;
            OutputStream out = this.session.out;      
            
            // parsing the icap request line
            parseRequestLine(icapHeader.METHOD_RESPMOD,arg);        
            
            // reading the icap request header
            icapHeader icapReqHeader = icapHeader.readHeader(this.prop,this.session);
            
            // determines if the connection should be kept alive
            handlePersistentConnection(icapReqHeader);            
            
            // determining the requested service and call it or send back an error message
            String reqService = this.prop.getProperty(icapHeader.CONNECTION_PROP_PATH,"");
            if (reqService.equalsIgnoreCase("/resIndexing")) {
                indexingService(icapReqHeader,in,out);
            } else {
                icapHeader icapResHeader = getDefaultHeaders();
                icapResHeader.put(icapHeader.ENCAPSULATED,icapReqHeader.get(icapHeader.ENCAPSULATED));
                icapResHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
                // icapResHeader.put(icapHeader.CONNECTION, "close");    
                
                StringBuffer headerStringBuffer = icapResHeader.toHeaderString("ICAP/1.0",404,null);            
                out.write(headerStringBuffer.toString().getBytes());
                out.flush();    
            }
            
            
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            
        }
        return this.prop.getProperty(icapHeader.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
    }
    
    /*
    private void blacklistService(icapHeader reqHeader, InputStream in, OutputStream out) {
        try {
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */
    
    private void indexingService(icapHeader reqHeader, InputStream in, OutputStream out) {
        try {
            
            /* =========================================================================
             * Reading the various message parts into buffers
             * ========================================================================= */
            ByteArrayInputStream reqHdrStream = null, resHdrStream = null, resBodyStream = null;
            String[] encapsulated = ((String) reqHeader.get(icapHeader.ENCAPSULATED)).split(",");
            int prevLength = 0, currLength=0;
            for (int i=0; i < encapsulated.length; i++) {  
                // reading the request header
                if (encapsulated[i].indexOf("req-hdr")>=0) {
                    prevLength = currLength;
                    currLength = Integer.parseInt(encapsulated[i+1].split("=")[1]);
                    
                    byte[] buffer = new byte[currLength-prevLength];
                    in.read(buffer, 0, buffer.length);
                    
                    reqHdrStream = new ByteArrayInputStream(buffer);
                    
                    // reading the response header
                } else if (encapsulated[i].indexOf("res-hdr")>=0) {
                    prevLength = currLength;
                    currLength = Integer.parseInt(encapsulated[i+1].split("=")[1]);
                    
                    byte[] buffer = new byte[currLength-prevLength];
                    in.read(buffer, 0, buffer.length);
                    
                    resHdrStream = new ByteArrayInputStream(buffer);
                    
                    // reading the response body
                } else if (encapsulated[i].indexOf("res-body")>=0) {
                    httpChunkedInputStream chunkedIn = new httpChunkedInputStream(in);
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    int l = 0,len = 0;
                    byte[] buffer = new byte[2048];
                    while ((l = chunkedIn.read(buffer)) >= 0) {
                        len += l;
                        bout.write(buffer,0,l);
                    }
                    resBodyStream = new ByteArrayInputStream(bout.toByteArray());                    
                }
            }
            
            /* =========================================================================
             * sending back the icap status
             * ========================================================================= */
            icapHeader icapResHeader = getDefaultHeaders();            
            if (reqHeader.allow(204)) {
                icapResHeader.put(icapHeader.ENCAPSULATED,reqHeader.get(icapHeader.ENCAPSULATED));
                icapResHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
                // resHeader.put(icapHeader.CONNECTION, "close");    
                
                StringBuffer headerStringBuffer = icapResHeader.toHeaderString("ICAP/1.0",204,null);            
                out.write(headerStringBuffer.toString().getBytes());
                out.flush();
            } else {
                icapResHeader.put(icapHeader.ENCAPSULATED,reqHeader.get(icapHeader.ENCAPSULATED));
                icapResHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
                // icapResHeader.put(icapHeader.CONNECTION, "close");    
                
                StringBuffer headerStringBuffer = icapResHeader.toHeaderString("ICAP/1.0",503,null);            
                out.write(headerStringBuffer.toString().getBytes());
                out.flush();                
            }
            
            /* =========================================================================
             * Parsing request data
             * ========================================================================= */
            // reading the requestline
            BufferedReader reader = new BufferedReader(new InputStreamReader(reqHdrStream));
            String httpRequestLine = reader.readLine();
            
            // parsing the requestline
            Properties httpReqProps = new Properties();
            httpHeader.parseRequestLine(httpRequestLine,httpReqProps,virtualHost);
            
            if (!httpReqProps.getProperty(httpHeader.CONNECTION_PROP_METHOD).equals(httpHeader.METHOD_GET)) {
                this.log.logInfo("Wrong http request method for indexing:" +
                        "\nRequest Method: " + httpReqProps.getProperty(httpHeader.CONNECTION_PROP_METHOD) + 
                        "\nRequest Line:   " + httpRequestLine);
                reader.close();
                reqHdrStream.close();
                return;
            }
            
            // reading all request headers
            httpHeader httpReqHeader = httpHeader.readHttpHeader(reader); 
            reader.close();
            reqHdrStream.close();
            
            // handle transparent proxy support: this function call is needed to set the host property properly
            httpHeader.handleTransparentProxySupport(httpReqHeader,httpReqProps,virtualHost,true);
            
            // getting the request URL
            URL httpRequestURL = httpHeader.getRequestURL(httpReqProps);            
            
            /* =========================================================================
             * Parsing response data
             * ========================================================================= */
            // getting the response status
            reader = new BufferedReader(new InputStreamReader(resHdrStream));
            String httpRespStatusLine = reader.readLine();
            
            Object[] httpRespStatus = httpHeader.parseResponseLine(httpRespStatusLine);
            
            if (!(httpRespStatus[1].equals(new Integer(200)) || httpRespStatus[1].equals(new Integer(203)))) {
                this.log.logInfo("Wrong status code for indexing:" +
                                 "\nStatus Code:   " + httpRespStatus[1] +
                                 "\nRequest Line:  " + httpRequestLine + 
                                 "\nResponse Line: " + httpRespStatusLine);
                reader.close();
                resHdrStream.close();
                return;
            }
            
            // reading all response headers
            httpHeader httpResHeader = httpHeader.readHttpHeader(reader);
            reader.close();
            resHdrStream.close();
            
            if (!plasmaParser.supportedContent(plasmaParser.PARSER_MODE_ICAP, httpRequestURL, httpResHeader.mime())) {
                this.log.logInfo("Wrong mimeType or fileExtension for indexing:" +
                                 "\nMimeType:    " + httpResHeader.mime() +
                                 "\nRequest Line:" + httpRequestLine);
                return ;
            }
            
            
            /* =========================================================================
             * Prepare data for indexing
             * ========================================================================= */
            
            // generating a htcache entry object
            IResourceInfo resInfo = new ResourceInfo(httpRequestURL,httpReqHeader,httpResHeader);
            plasmaHTCache.Entry cacheEntry = cacheManager.newEntry(
                    new Date(),  
                    0, 
                    httpRequestURL,
                    "",
                    httpRespStatusLine,
                    resInfo,
                    null, 
                    switchboard.defaultProxyProfile
            );
            
            // getting the filename/path to store the response body
            File cacheFile = cacheManager.getCachePath(httpRequestURL);
            
            // if the file already exits we delete it
            if (cacheFile.isFile()) {
                cacheManager.deleteFile(httpRequestURL);
            }                        
            // we write the new cache entry to file system directly
            cacheFile.getParentFile().mkdirs();            
            
            // copy the response body into the file
            serverFileUtils.copy(resBodyStream,cacheFile);
            resBodyStream.close(); resBodyStream = null;
            
            // indexing the response
            cacheManager.push(cacheEntry);    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private final void parseRequestLine(String cmd, String s) {
        // parsing the requestlin
        icapHeader.parseRequestLine(cmd,s, this.prop,virtualHost);
        
        // adding the client ip prop
        this.prop.setProperty(icapHeader.CONNECTION_PROP_CLIENTIP, this.clientIP); 
        
        // counting the amount of received requests within this permanent conneciton
        this.prop.setProperty(icapHeader.CONNECTION_PROP_KEEP_ALIVE_COUNT, Integer.toString(++this.keepAliveRequestCount));        
    }
    
    private boolean handlePersistentConnection(icapHeader header) {
        
        if (!keepAliveSupport) {
            this.prop.put(icapHeader.CONNECTION_PROP_PERSISTENT,"close");
            return false;
        }
        
        boolean persistent = true;
        if (((String)header.get(icapHeader.CONNECTION, "keep-alive")).toLowerCase().equals("close")) {
            persistent = false;
        }        
        
        this.prop.put(icapHeader.CONNECTION_PROP_PERSISTENT,persistent?"keep-alive":"close");
        return persistent;
    }    
    
}
