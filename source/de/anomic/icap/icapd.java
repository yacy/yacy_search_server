//icapd.java
//-----------------------
//(C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.icap;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Date;
import java.util.Properties;

import de.anomic.http.HttpClient;
import de.anomic.http.httpChunkedInputStream;
import de.anomic.http.httpRequestHeader;
import de.anomic.http.httpResponseHeader;
import de.anomic.http.httpdProxyCacheEntry;
import de.anomic.index.indexDocumentMetadata;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverHandler;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverCore.Session;
import de.anomic.yacy.yacyURL;

/**
 * @author theli
 */
public class icapd implements serverHandler, Cloneable {
    
    
    private serverCore.Session session;  // holds the session object of the calling class
    
    // the connection properties
    private final Properties prop = new Properties();
    
    // the address of the client
    private InetAddress userAddress;
    private String clientIP;
    private int keepAliveRequestCount = 0;
    
    // needed for logging
    private final serverLog log = new serverLog("ICAPD");
    
    private static plasmaSwitchboard sb = null;
    private static String virtualHost = null;
    private static boolean keepAliveSupport = true;
    
    
    
    public icapd() {
        if (sb == null) {
            sb = plasmaSwitchboard.getSwitchboard();
            virtualHost = sb.getConfig("fileHost","localhost");
        }
        
    }
    
    public icapd clone(){
        return new icapd();
    }
    
    public void initSession(final Session aSession) throws IOException {
        this.session = aSession;
        this.userAddress = aSession.userAddress; // client InetAddress
        this.clientIP = this.userAddress.getHostAddress();
        if (this.userAddress.isAnyLocalAddress()) this.clientIP = "localhost";
        if (this.clientIP.startsWith("0:0:0:0:0:0:0:1")) this.clientIP = "localhost";
        if (this.clientIP.startsWith("127.")) this.clientIP = "localhost";
    }
    
    public String greeting() {
        // TODO Auto-generated method stub
        return null;
    }
    
    public String error(final Throwable e) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public void reset() {
    }
    
    public Boolean EMPTY(final String arg) throws IOException {
        // TODO Auto-generated method stub
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean UNKNOWN(final String requestLine) throws IOException {
        // TODO Auto-generated method stub
        return serverCore.TERMINATE_CONNECTION;
    }
    
    public icapHeader getDefaultHeaders() {
        final icapHeader newHeaders = new icapHeader();
        
        newHeaders.put(icapHeader.SERVER,"YaCy/" + sb.getConfig("vString",""));
        newHeaders.put(icapHeader.DATE, HttpClient.dateString(new Date()));
        newHeaders.put(icapHeader.ISTAG, "\"" + sb.getConfig("vString","") + "\"");
        
        return newHeaders;
    }
    
    public Boolean OPTIONS(final String arg) throws IOException {
        
        final BufferedOutputStream out = new BufferedOutputStream(this.session.out);   
        
        // parsing the http request line
        parseRequestLine(icapHeader.METHOD_OPTIONS,arg);        
        
        // reading the headers
        final icapHeader icapReqHeader = icapHeader.readHeader(this.prop,this.session);
        
        // determines if the connection should be kept alive
        final boolean persistent = handlePersistentConnection(icapReqHeader);              
        
        // setting the icap response headers
        final icapHeader resHeader = getDefaultHeaders();        
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
        final String reqService = this.prop.getProperty(icapHeader.CONNECTION_PROP_PATH,"");
        if (reqService.equalsIgnoreCase("/resIndexing")) {
            resHeader.put(icapHeader.SERVICE, "YaCy ICAP Indexing Service 1.0");
            resHeader.put(icapHeader.METHODS,icapHeader.METHOD_RESPMOD);
            
            String transferIgnoreList = plasmaParser.getMediaExtList();  
            transferIgnoreList = transferIgnoreList.substring(1,transferIgnoreList.length()-1);
            resHeader.put(icapHeader.TRANSFER_IGNORE, transferIgnoreList);
        } else {
            resHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
        }
        
        
        final StringBuffer headerStringBuffer = resHeader.toHeaderString("ICAP/1.0",200,null);        
        out.write(headerStringBuffer.toString().getBytes());
        out.flush();        
        
        return this.prop.getProperty(icapHeader.CONNECTION_PROP_PERSISTENT).equals("keep-alive") ? serverCore.RESUME_CONNECTION : serverCore.TERMINATE_CONNECTION;
    }
    
    public Boolean REQMOD() {
        return serverCore.TERMINATE_CONNECTION;
    }    
    
    public Boolean RESPMOD(final String arg) {
        try {
            final InputStream in = this.session.in;
            final OutputStream out = this.session.out;      
            
            // parsing the icap request line
            parseRequestLine(icapHeader.METHOD_RESPMOD,arg);        
            
            // reading the icap request header
            final icapHeader icapReqHeader = icapHeader.readHeader(this.prop,this.session);
            
            // determines if the connection should be kept alive
            handlePersistentConnection(icapReqHeader);            
            
            // determining the requested service and call it or send back an error message
            final String reqService = this.prop.getProperty(icapHeader.CONNECTION_PROP_PATH,"");
            if (reqService.equalsIgnoreCase("/resIndexing")) {
                indexingService(icapReqHeader,in,out);
            } else {
                final icapHeader icapResHeader = getDefaultHeaders();
                icapResHeader.put(icapHeader.ENCAPSULATED,icapReqHeader.get(icapHeader.ENCAPSULATED));
                icapResHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
                // icapResHeader.put(icapHeader.CONNECTION, "close");    
                
                final StringBuffer headerStringBuffer = icapResHeader.toHeaderString("ICAP/1.0",404,null);            
                out.write((new String(headerStringBuffer)).getBytes());
                out.flush();    
            }
            
            
            
        } catch (final Exception e) {
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
    
    private void indexingService(final icapHeader reqHeader, final InputStream in, final OutputStream out) {
        try {
            
            /* =========================================================================
             * Reading the various message parts into buffers
             * ========================================================================= */
            ByteArrayInputStream reqHdrStream = null, resHdrStream = null, resBodyStream = null;
            final String[] encapsulated = (reqHeader.get(icapHeader.ENCAPSULATED)).split(",");
            int prevLength = 0, currLength=0;
            for (int i=0; i < encapsulated.length; i++) {  
                // reading the request header
                if (encapsulated[i].indexOf("req-hdr")>=0) {
                    prevLength = currLength;
                    currLength = Integer.parseInt(encapsulated[i+1].split("=")[1]);
                    
                    final byte[] buffer = new byte[currLength-prevLength];
                    final int bytesRead = in.read(buffer, 0, buffer.length);
                    assert bytesRead == buffer.length;
                    
                    reqHdrStream = new ByteArrayInputStream(buffer);
                    
                    // reading the response header
                } else if (encapsulated[i].indexOf("res-hdr")>=0) {
                    prevLength = currLength;
                    currLength = Integer.parseInt(encapsulated[i+1].split("=")[1]);
                    
                    final byte[] buffer = new byte[currLength-prevLength];
                    final int bytesRead = in.read(buffer, 0, buffer.length);
                    assert bytesRead == buffer.length;
                    
                    resHdrStream = new ByteArrayInputStream(buffer);
                    
                    // reading the response body
                } else if (encapsulated[i].indexOf("res-body")>=0) {
                    final httpChunkedInputStream chunkedIn = new httpChunkedInputStream(in);
                    final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    int l = 0,len = 0;
                    final byte[] buffer = new byte[2048];
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
            final icapHeader icapResHeader = getDefaultHeaders();            
            if (reqHeader.allow(204)) {
                icapResHeader.put(icapHeader.ENCAPSULATED,reqHeader.get(icapHeader.ENCAPSULATED));
                icapResHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
                // resHeader.put(icapHeader.CONNECTION, "close");    
                
                final StringBuffer headerStringBuffer = icapResHeader.toHeaderString("ICAP/1.0",204,null);            
                out.write((new String(headerStringBuffer)).getBytes());
                out.flush();
            } else {
                icapResHeader.put(icapHeader.ENCAPSULATED,reqHeader.get(icapHeader.ENCAPSULATED));
                icapResHeader.put(icapHeader.SERVICE, "YaCy ICAP Service 1.0");
                // icapResHeader.put(icapHeader.CONNECTION, "close");    
                
                final StringBuffer headerStringBuffer = icapResHeader.toHeaderString("ICAP/1.0",503,null);            
                out.write((new String(headerStringBuffer)).getBytes());
                out.flush();                
            }
            
            /* =========================================================================
             * Parsing request data
             * ========================================================================= */
            // reading the requestline
            BufferedReader reader = new BufferedReader(new InputStreamReader(reqHdrStream));
            final String httpRequestLine = reader.readLine();
            
            // parsing the requestline
            final Properties httpReqProps = new Properties();
            httpRequestHeader.parseRequestLine(httpRequestLine,httpReqProps,virtualHost);
            
            if (!httpReqProps.getProperty(httpRequestHeader.CONNECTION_PROP_METHOD).equals(httpRequestHeader.METHOD_GET)) {
                this.log.logInfo("Wrong http request method for indexing:" +
                        "\nRequest Method: " + httpReqProps.getProperty(httpRequestHeader.CONNECTION_PROP_METHOD) + 
                        "\nRequest Line:   " + httpRequestLine);
                reader.close();
                if(reqHdrStream != null) {
                    reqHdrStream.close();
                }
                return;
            }
            
            // reading all request headers
            final httpRequestHeader httpReqHeader = new httpRequestHeader();
            httpReqHeader.readHttpHeader(reader); 
            reader.close();
            if(reqHdrStream != null) {
                reqHdrStream.close();
            }
            
            // handle transparent proxy support: this function call is needed to set the host property properly
            httpRequestHeader.handleTransparentProxySupport(httpReqHeader,httpReqProps,virtualHost,true);
            
            // getting the request URL
            final yacyURL httpRequestURL = httpRequestHeader.getRequestURL(httpReqProps);            
            
            /* =========================================================================
             * Parsing response data
             * ========================================================================= */
            // getting the response status
            reader = new BufferedReader(new InputStreamReader(resHdrStream));
            final String httpRespStatusLine = reader.readLine();
            
            final Object[] httpRespStatus = httpResponseHeader.parseResponseLine(httpRespStatusLine);
            
            if (!(httpRespStatus[1].equals(Integer.valueOf(200)) || httpRespStatus[1].equals(Integer.valueOf(203)))) {
                this.log.logInfo("Wrong status code for indexing:" +
                                 "\nStatus Code:   " + httpRespStatus[1] +
                                 "\nRequest Line:  " + httpRequestLine + 
                                 "\nResponse Line: " + httpRespStatusLine);
                reader.close();
                if(resHdrStream != null) {
                    resHdrStream.close();
                }
                return;
            }
            
            // reading all response headers
            final httpResponseHeader httpResHeader = new httpResponseHeader();
            httpResHeader.readHttpHeader(reader);
            reader.close();
            if(resHdrStream != null) {
                resHdrStream.close();
            }
            
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
            final indexDocumentMetadata cacheEntry = new httpdProxyCacheEntry(
                    0, 
                    httpRequestURL,
                    "",
                    httpRespStatusLine,
                    httpReqHeader, httpResHeader,
                    null, 
                    sb.webIndex.defaultProxyProfile
            );
            
            // copy the response body into the file
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            serverFileUtils.copy(resBodyStream, baos);
            if(resBodyStream != null) {
                resBodyStream.close(); resBodyStream = null;
            }
            cacheEntry.setCacheArray(baos.toByteArray());
            plasmaHTCache.storeMetadata(httpResHeader, cacheEntry);
            
            // indexing the response
            sb.htEntryStoreProcess(cacheEntry);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    private final void parseRequestLine(final String cmd, final String s) {
        // parsing the requestlin
        icapHeader.parseRequestLine(cmd,s, this.prop,virtualHost);
        
        // adding the client ip prop
        this.prop.setProperty(icapHeader.CONNECTION_PROP_CLIENTIP, this.clientIP); 
        
        // counting the amount of received requests within this permanent conneciton
        this.prop.setProperty(icapHeader.CONNECTION_PROP_KEEP_ALIVE_COUNT, Integer.toString(++this.keepAliveRequestCount));        
    }
    
    private boolean handlePersistentConnection(final icapHeader header) {
        
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
