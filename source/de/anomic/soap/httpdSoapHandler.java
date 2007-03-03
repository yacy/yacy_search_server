//httpdSoapHandler.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file was contributed by Martin Thelian
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


package de.anomic.soap;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;

import org.apache.axis.AxisFault;
import org.apache.axis.EngineConfiguration;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.WSDDEngineConfiguration;
import org.apache.axis.deployment.wsdd.WSDDDeployment;
import org.apache.axis.deployment.wsdd.WSDDDocument;
import org.apache.axis.server.AxisServer;
import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.anomic.http.httpChunkedInputStream;
import de.anomic.http.httpChunkedOutputStream;
import de.anomic.http.httpContentLengthInputStream;
import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.http.httpdAbstractHandler;
import de.anomic.http.httpdHandler;
import de.anomic.plasma.plasmaParser;
import de.anomic.server.serverClassLoader;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;

/**
 * Class to accept SOAP Requests and invoke the desired soapService.
 * An example how to do a soap call from php:
 * <code>
 * <?php
 *   $client = new SoapClient("http://192.168.1.201:8080/soap/index?wsdl", array(
 *                   "trace"      => 1,
 *                   "exceptions" => 1));
 *                   
 *   try
 *   {
 *       $result = $client->__call("crawling", array("http://test.at"), NULL,
 *               new SoapHeader("http://http.anomic.de/header", "Authorization", md5(base64_encode("admin:xxxxxxx"))));
 *   }
 *   catch (SoapFault $fault)
 *   {
 *       $result = $fault->faultstring;
 *   }
 *   
 *   print($result);
 * ?>
 * </code>
 *  
 *  
 *  
 * @author Martin Thelian
 */
public final class httpdSoapHandler extends httpdAbstractHandler implements httpdHandler 
{
	public static final String SOAP_HANDLER_VERSION = "YaCySOAP V0.1";
	
 
    /* ===============================================================
     * Constants needed to set some SOAP properties
     * =============================================================== */    
    /**
     * SOAP Header Namespace needed to access the soap header field containing
     * the user authentication
     */
    public static final String serviceHeaderNamespace = "http://http.anomic.de/header";
    
    /**
     * define the needed deployment strings
     */ 
    public static final String serviceDeploymentString = 
        "<deployment "
      +     "xmlns=\"http://xml.apache.org/axis/wsdd/\" " 
      +     "xmlns:java=\"http://xml.apache.org/axis/wsdd/providers/java\" >"
      +     "<service name=\"@serviceName@\" provider=\"java:RPC\">"
      +         "<parameter name=\"typeMappingVersion\" value=\"1.1\"/>"
      +         "<parameter name=\"scope\" value=\"Request\"/>"
      +         "<parameter name=\"className\" value=\"@className@\" />"
      +         "<parameter name=\"allowedMethods\" value=\"*\" />"
      +     "</service>"
      + "</deployment>";       
    
    private static final String[] defaultServices = new String[] {
        "search=de.anomic.soap.services.SearchService",
        "crawl=de.anomic.soap.services.CrawlService",
        "status=de.anomic.soap.services.StatusService",
        "admin=de.anomic.soap.services.AdminService",
        "blacklist=de.anomic.soap.services.BlacklistService",
        "share=de.anomic.soap.services.ShareService",
        "bookmarks=de.anomic.soap.services.BookmarkService",
        "messages=de.anomic.soap.services.MessageService"
    };
    
    /* ===============================================================
     * Constants needed to set the SOAP message context
     * =============================================================== */
    /**
     * CONSTANT: the http root path 
     */
    public static final String MESSAGE_CONTEXT_HTTP_ROOT_PATH = "htRootPath";    
    /**
     * CONSTANT: tge server switchboard
     */
    public static final String MESSAGE_CONTEXT_SERVER_SWITCH = "serverSwitch";
    /**
     * CONSTANT: received http headers
     */
    public static final String MESSAGE_CONTEXT_HTTP_HEADER = "httpHeader";
    /**
     * CONSTANT: the server classloader
     */
    public static final String MESSAGE_CONTEXT_SERVER_CLASSLOADER = "serverClassLoader";
    /**
     * CONSTANT: available templates
     */
    public static final String MESSAGE_CONTEXT_TEMPLATES = "templates";
    
    /* ===============================================================
     * Other object fields
     * =============================================================== */
    private static final Object initSync = new Object();
    
    private serverClassLoader provider = null;
    private HashMap templates;
    private serverSwitch switchboard;
    
    private static AxisServer engine = null;
    private File htRootPath;
    private File htTemplatePath;
    
    private static Properties additionalServices = null;
        
    /**
     * Constructor of this class
     * @param theSwitchboard
     * @throws Exception 
     */
    public httpdSoapHandler(serverSwitch theSwitchboard) throws Exception {
        super();
        
        this.switchboard = theSwitchboard;
        this.theLogger = new serverLog("SOAP");

        // create a htRootPath: system pages
        if (this.htRootPath == null) {
            this.htRootPath = new File(this.switchboard.getRootPath(), this.switchboard.getConfig("htRootPath","htroot"));
            // if (!(htRootPath.exists())) htRootPath.mkdir();
        }        
        
        if (this.htTemplatePath == null) {
            this.htTemplatePath = new File(theSwitchboard.getRootPath(), theSwitchboard.getConfig("htTemplatePath","htroot/env/templates"));
            // if (!(this.htTemplatePath.exists())) this.htTemplatePath.mkdir();
        }        
        
        if (this.provider == null) {
            this.provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
        }
        
        if (this.templates == null) {
        	this.templates = loadTemplates(this.htTemplatePath); 
        }

        // deploy default soap services
        if (engine == null) synchronized (initSync) { deployDefaultServices(); }
        
        // init additional soap services
        if (additionalServices == null) synchronized (initSync) { deployAdditionalServices(); }
    }
    
    private void deployDefaultServices() throws Exception {
    	try {
    		// create an Axis server
    		this.theLogger.logInfo("Init soap engine ...");
    		engine = new AxisServer();
    		
    		// setting some options ...
    		engine.setShouldSaveConfig(false);
    		
    	} catch (Exception e) {
    		this.theLogger.logSevere("Unable to initialize soap engine",e);
    		throw e;
    	} catch (Error e) {
    		this.theLogger.logSevere("Unable to initialize soap engine",e);
    		throw e;    		
    	}
    	
    	try {
    		this.theLogger.logInfo("Deploying default services ...");
    		for (int i=0; i < defaultServices.length; i++) {
    			String[] nextService = defaultServices[i].split("=");
    			this.theLogger.logInfo("Deploying service " + nextService[0] + ": " + nextService[1]);
    			String deploymentStr = serviceDeploymentString
    			.replaceAll("@serviceName@", nextService[0])
    			.replaceAll("@className@", nextService[1]);
    			
    			// deploy the service 
    			deployService(deploymentStr,engine);
    		}    	
    	} catch (Exception e) {
    		this.theLogger.logSevere("Unable to deploy default soap services.",e);
    		throw e;
    	} catch (Error e) {
    		this.theLogger.logSevere("Unable to deploy default soap services.",e);
    		throw e;    		
    	}
    }
    
    private void deployAdditionalServices() {
    	additionalServices = new Properties();
    	
    	// getting the property filename containing the file list
    	String fileName = this.switchboard.getConfig("soap.serviceDeploymentList","");
    	if (fileName.length() > 0) {
    		BufferedInputStream fileInput = null;
    		try {
    			File deploymentFile = new File(this.switchboard.getRootPath(),fileName);        				
    			fileInput = new BufferedInputStream(new FileInputStream(deploymentFile));
    			
    			// load property list
    			additionalServices.load(fileInput);
    			fileInput.close();  
    			
    			// loop through and deploy services
    			if (additionalServices.size() > 0) {
    				Enumeration serviceNameEnum = additionalServices.keys();
    				while (serviceNameEnum.hasMoreElements()) {
    					String serviceName = (String) serviceNameEnum.nextElement();
    					String className = additionalServices.getProperty(serviceName);
    					
    					String deploymentStr = serviceDeploymentString
    					.replaceAll("@serviceName@", serviceName)
    					.replaceAll("@className@", className);
    					
    					// deploy the service 
    					deployService(deploymentStr,engine);        					
    				}
    			}
    		} catch (Exception e) {
    			this.theLogger.logSevere("Unable to deploy additional services: " + e.getMessage(), e);
    		} finally {
    			if (fileInput != null) try { fileInput.close(); } catch (Exception e){/* ignore this */}
    		}
    	}
    }          
    
    private InputStream getBodyInputStream(httpHeader requestHeader, PushbackInputStream body) throws SoapException{
        InputStream input;
        
        // getting the content length
        long contentLength = requestHeader.contentLength();
        String transferEncoding = (String) requestHeader.get(httpHeader.TRANSFER_ENCODING);
        String contentEncoding = (String) requestHeader.get(httpHeader.CONTENT_ENCODING);
        
        /* ===========================================================================
         * Handle TRANSFER ENCODING
         * =========================================================================== */
        if (transferEncoding != null && !transferEncoding.equalsIgnoreCase("identity")) {
        	// read using transfer encoding
            if (transferEncoding.equalsIgnoreCase("chunked")) {
                input = new httpChunkedInputStream(body);
            } else {         
                String errorMsg = "Unsupported transfer-encoding: "+ transferEncoding;
                this.theLogger.logSevere(errorMsg);
                throw new SoapException(501,"Not Implemented",errorMsg);
            }
        } else if (contentLength > 0) {
        	// read contentLength bytes
            input = new httpContentLengthInputStream(body,contentLength);
        } else {
        	// read until EOF
            input = body;
        }
        
        /* ===========================================================================
         * Handle CONTENT ENCODING
         * =========================================================================== */
        try {
            if (contentEncoding != null && !contentEncoding.equals("identity")) {
                if (contentEncoding.equalsIgnoreCase("gzip")) {
                    input = new GZIPInputStream(input);
                } else {
                    String errorMsg = "Unsupported content encoding: " + contentEncoding;
                    this.theLogger.logSevere(errorMsg);
                    throw new SoapException(415,"Unsupported Media Type",errorMsg);
                }
            }
        } catch (IOException e) {
            throw new SoapException(400,"Bad Request",e);
        }
        
        return input;
    }

    /**
     * HTTP HEAD method. Not needed for soap.
     * @param conProp
     * @param header
     * @param response
     * @throws IOException
     * 
     * @see de.anomic.http.httpdHandler#doHead(java.util.Properties, de.anomic.http.httpHeader, java.io.OutputStream)
     */
    public void doHead(Properties conProp, httpHeader requestHeader, OutputStream clientOut) throws IOException {
        sendMessage(conProp, requestHeader, clientOut, 501, "Not Implemented", "Connection method is not supported by this handler",null); 
        conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
    }
    

    /**
     * HTTP Connect Method. Not needed for SOAP
     * @param conProp
     * @param requestHeader
     * @param clientIn
     * @param clientOut
     * @throws IOException
     * 
     * @see de.anomic.http.httpdHandler#doConnect(java.util.Properties, de.anomic.http.httpHeader, java.io.InputStream, java.io.OutputStream)
     */
    public void doConnect(Properties conProp, httpHeader requestHeader, InputStream clientIn, OutputStream clientOut) throws IOException {
        sendMessage(conProp, requestHeader, clientOut, 501, "Not Implemented", "Connection method is not supported by this handler",null);
        conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
    }        
    
    /**
     * Handle http-GET requests. For soap this is usually a query for the wsdl-file.
     * Therefore we always return the wsdl file for a get request
     * 
     * @param conProp
     * @param requestHeader all received http headers
     * @param response {@link OutputStream} to the client
     * 
     * @throws IOException
     * 
     * @see de.anomic.http.httpdHandler#doGet(java.util.Properties, de.anomic.http.httpHeader, java.io.OutputStream)
     */
    public void doGet(Properties conProp, httpHeader requestHeader, OutputStream response) throws IOException {
    	MessageContext msgContext = null;
        String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
        try {
        	// generating message context
            msgContext = this.generateMessageContext(path, requestHeader, conProp);
            
            // generating wsdl file
            Document doc = generateWSDL(msgContext);
            
            if (doc != null) {
            	// TODO: what about doc.getInputEncoding()?
            	// TODO: what about getXmlEncoding?           
                // Converting the the wsdl document into a byte-array
                String responseDoc = XMLUtils.DocumentToString(doc);
                byte[] result = responseDoc.getBytes("UTF-8");
                
                // send back the result
                sendMessage(conProp, requestHeader, response, 200, "OK", "text/xml; charset=utf-8", result);
                
                if (!(requestHeader.get(httpHeader.CONNECTION, "close").equals("keep-alive"))) {
                    // wait a little time until everything closes so that clients can read from the streams/sockets
                    try {Thread.currentThread().join(200);} catch (InterruptedException e) {/* ignore this */}
                  }
            } else {
                // if we where unable to generate the wsdl file ....
                String errorMsg = "Internal Server Error: Unable to generate the WSDL file.";
                sendMessage(conProp, requestHeader, response, 500, "Internal Error", "text/plain",errorMsg.getBytes("UTF-8"));            
            }
            
            return;
        } catch (Exception e) {
        	// handle error
        	handleException(conProp,requestHeader,msgContext,response,e);        	
        }
        
    }

    /**
     * HTTP Post method. Needed to call a soap service on this server from a soap client
     * @param conProp the connection properties
     * @param requestHeader the received http headers
     * @param response {@link OutputStream} to the client
     * @param body the request body containing the SOAP message
     * 
     * @see de.anomic.http.httpdHandler#doPost(java.util.Properties, de.anomic.http.httpHeader, java.io.OutputStream, java.io.PushbackInputStream)
     */
    public void doPost(Properties conProp, httpHeader requestHeader, OutputStream response, PushbackInputStream body) {
    	
    	MessageContext msgContext = null;
        String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
        try {            
            /* ========================================================================
             * GENERATE REQUEST MESSAGE
             * ======================================================================== */
            // read the request message
            InputStream bodyStream = getBodyInputStream(requestHeader, body);
            
            // generating the SOAP message context that will be passed over to the invoked service
            msgContext = this.generateMessageContext(path, requestHeader, conProp);
            
            // Generating a SOAP Request Message Object
            String mime = plasmaParser.getRealMimeType(requestHeader.mime()); // this is important !!!!
            Message requestMsg = new Message(
            		bodyStream, 
                    false, 
                    mime, 
                    (String)requestHeader.get(httpHeader.CONTENT_LOCATION)
            );
            msgContext.setRequestMessage(requestMsg);
            
            
            /* ========================================================================
             * SERVICE INVOCATION
             * ======================================================================== */
            Message responseMsg = this.invokeService(msgContext);
            
            if (responseMsg != null) {
                sendMessage(conProp, requestHeader, response, 200, "OK", responseMsg);
            } else {
                sendMessage(conProp, requestHeader, response, 202, "Accepted", "text/plain", null);          
            }
            
            return;
        } catch (Exception e) {
        	// handle error
        	handleException(conProp, requestHeader, msgContext, response,e);
        }
    }
    
    private void handleException(Properties conProp, httpHeader requestHeader, MessageContext messageContext, OutputStream response, Exception e) {
    	try {
    		Message soapErrorMsg = null;
    		
    		if (!conProp.containsKey(httpHeader.CONNECTION_PROP_PROXY_RESPOND_HEADER)) {
    			// if no header was send until now we can send back an error message
    			
    			SoapException soapEx = null;
    			if (!(e instanceof SoapException)) {
    				soapEx = new SoapException(500,"internal server error",e);
    			} else {
    				soapEx = (SoapException) e;
    			}
    			// generating a soap error message
    			soapErrorMsg = soapEx.getFaultMessage(messageContext);
    			
    			// send error message back to the client
    			sendMessage(conProp,requestHeader,response,soapEx.getStatusCode(),soapEx.getStatusText(),soapErrorMsg);
    		} else {
    			this.theLogger.logSevere("Unexpected Exception while sending data to client",e);
    			
    		}
		} catch (Exception ex) {
			// the http response header was already send. Just log the error
			this.theLogger.logSevere("Unexpected Exception while sending error message",e);
		} finally {
			// force connection close
			conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");                
		}
    }
    
    private Document generateWSDL(MessageContext msgContext) throws SoapException {
        try {
            engine.generateWSDL(msgContext);
            Document doc = (Document) msgContext.getProperty("WSDL");
            return doc;
        } catch (Exception ex) {
        	if (ex instanceof AxisFault) throw new SoapException((AxisFault)ex);
            throw new SoapException(500,"Unable to generate WSDL",ex);
        }    	
    }
    
    protected Message invokeService(MessageContext msgContext) throws SoapException {
        try {
            // invoke the service
            engine.invoke(msgContext);
    
            // Retrieve the response from Axis
            return msgContext.getResponseMessage();                  
        } catch (Exception ex) {        	
            if (ex instanceof AxisFault) throw new SoapException((AxisFault)ex);
			throw new SoapException(500,"Unable to invoke service",ex);
        }
    }
    


    /**
     * This function deplays all java classes that should be available via SOAP call.
     * 
     *  @param deploymentString the deployment string containing detailed information about
     *  the java class that should be deployed
     *  @param theAxisServer the apache axis engine where the service should be deployed
     *  
     *  @return <code>true</code> if the deployment was done successfully or <code>false</code>
     *  otherwise
     */
    private static boolean deployService(String deploymentString, AxisServer theAxisServer)
    {
        // convert WSDD file string into bytestream for furhter processing
        InputStream deploymentStream = null;
        if (deploymentString != null) {
            deploymentStream = new ByteArrayInputStream(deploymentString.getBytes());        
            Document root = null;
    
            try {
                // build XML document from stream
                root = XMLUtils.newDocument(deploymentStream);
                
                // parse WSDD file
                WSDDDocument wsddDoc = new WSDDDocument(root);
                
                // get the configuration of this axis engine
                EngineConfiguration config = theAxisServer.getConfig();
    
                if (config instanceof WSDDEngineConfiguration)  {
                    // get the current configuration of the Axis engine 
                    WSDDDeployment deploymentWSDD =
                        ((WSDDEngineConfiguration) config).getDeployment();
    
                    // undeply unneeded standard services
                    deploymentWSDD.undeployService(new QName("Version"));
                    deploymentWSDD.undeployService(new QName("AdminService"));
                    
                    // deploy the new service       
                    // an existing service with the same name gets deleted
                    wsddDoc.deploy(deploymentWSDD);
                }
            } catch (ParserConfigurationException e) {                
                System.err.println("Could not deploy service.");
                return false;
            } catch (SAXException e) {
                System.err.println("Could not deploy service.");
                return false;
            } catch (IOException e) {
                System.err.println("Could not deploy service.");
                return false;
            }
        } else {
            System.err.println("Service deployment string is NULL! SOAP Service not deployed.");
            return false;
        }
        return true;
    }    
    
    /**
     * This function is used to generate the SOAP Message Context that is handed over to
     * the called service.
     * This message context contains some fields needed by the service to fullfil the request.
     * 
     * @param path the path of the request
     * @param requestHeader the http headers of the request
     * @param conProps TODO
     * @return the generated {@link MessageContext}
     * @throws SoapException 
     *  
     * @throws Exception if the {@link MessageContext} could not be generated successfully.
     */
    private MessageContext generateMessageContext(String path, httpHeader requestHeader, Properties conProps) throws SoapException  {
        try {
            // getting the requestes service name
            String serviceName = path.substring("/soap/".length());
            
            // create and initialize a message context
            MessageContext msgContext = new MessageContext(httpdSoapHandler.engine);
            msgContext.setTransportName("YaCy-SOAP");
            msgContext.setProperty(MessageContext.TRANS_URL, "http://" + requestHeader.get(httpHeader.HOST) + ((((String)requestHeader.get(httpHeader.HOST)).indexOf(":") > -1)?"":Integer.toString(serverCore.getPortNr(this.switchboard.getConfig("port","8080")))) + 
                                   "/soap/" + serviceName);
            
            // the used http verson
            String version = conProps.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);
            msgContext.setProperty(MessageContext.HTTP_TRANSPORT_VERSION,version);
                        
            // YaCy specific objects
            msgContext.setProperty(MESSAGE_CONTEXT_HTTP_ROOT_PATH  ,this.htRootPath.toString());
            msgContext.setProperty(MESSAGE_CONTEXT_SERVER_SWITCH,this.switchboard);
            msgContext.setProperty(MESSAGE_CONTEXT_HTTP_HEADER ,requestHeader);        
            msgContext.setProperty(MESSAGE_CONTEXT_SERVER_CLASSLOADER ,this.provider);
            msgContext.setProperty(MESSAGE_CONTEXT_TEMPLATES ,this.templates);    
            
            msgContext.setTargetService(serviceName);
            
            return msgContext;
        } catch (Exception e) {
        	if (e instanceof AxisFault) throw new SoapException((AxisFault)e);
			throw new SoapException(500,"Unable to generate message context",e);
        }
    }
    
    /**
     * This method was copied from {@link httpdFileHandler}. Maybe it would be a good idea
     * to move this function up into {@link httpdAbstractHandler} 
     *  
     * @param path the path to the template dir
     * @return a hasmap containing all templates 
     */
    private static HashMap loadTemplates(File path) {
    	// reads all templates from a path
    	// we use only the folder from the given file path
    	HashMap result = new HashMap();
    	if (path == null) return result;
    	if (!(path.isDirectory())) path = path.getParentFile();
    	if ((path == null) || (!(path.isDirectory()))) return result;
    	String[] templates = path.list();
    	for (int i = 0; i < templates.length; i++) {
    		if (templates[i].endsWith(".template")) try {
    			//System.out.println("TEMPLATE " + templates[i].substring(0, templates[i].length() - 9) + ": " + new String(buf, 0, c));
    			result.put(templates[i].substring(0, templates[i].length() - 9),
    					new String(serverFileUtils.read(new File(path, templates[i])), "UTF-8"));
    		} catch (Exception e) {}
    	}
    	return result;
    }    
    
    /**
     * TODO: handle accept-charset http header
     * TODO: what about content-encoding, transfer-encoding here?
     */
    protected void sendMessage(Properties conProp, httpHeader requestHeader, OutputStream out, int statusCode, String statusText, String contentType, byte[] MessageBody) throws IOException {
        // write out the response header
        respondHeader(conProp, out, statusCode, statusText, (MessageBody==null)?null:contentType, (MessageBody==null)?-1:MessageBody.length, null, null);
        
        // write the message body
        if (MessageBody != null) out.write(MessageBody);
        out.flush();
    }
        
    /**
     * TODO: handle accept-charset http header
     */    
    protected void sendMessage(Properties conProp, httpHeader requestHeader, OutputStream out, int statusCode, String statusText, Message soapMessage) throws IOException, SOAPException {
    	httpChunkedOutputStream chunkedOut = null;
    	GZIPOutputStream gzipOut = null;
    	OutputStream bodyOut = out;
    	
        // getting the content type of the response
        String contentType = soapMessage.getContentType(soapMessage.getMessageContext().getSOAPConstants());

        // getting the content length
        String transferEncoding = null;
        String contentEncoding = null;
        long contentLength = -1;
        
        if (httpHeader.supportChunkedEncoding(conProp)) {
        	// we use chunked transfer encoding
        	transferEncoding = "chunked";        	
        } else {
        	contentLength = soapMessage.getContentLength();
        }
        if (requestHeader.acceptGzip()) {
        	// send the response gzip encoded
        	contentEncoding = "gzip";      
        	
        	// we don't know the content length of the compressed body
        	contentLength = -1;
        	
        	// if chunked transfer encoding is not used we need to close the connection
        	if (!transferEncoding.equals("chunked")) {
        		conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
        	}
        }
        	
        // sending the soap header
        respondHeader(conProp, out, statusCode, statusText, contentType, contentLength, contentEncoding, transferEncoding);

        if (transferEncoding != null) bodyOut = chunkedOut = new httpChunkedOutputStream(bodyOut);
        if (contentEncoding != null) bodyOut = gzipOut = new GZIPOutputStream(bodyOut);
        
        // sending the body     
        soapMessage.writeTo(System.out);
        soapMessage.writeTo(bodyOut);            
        bodyOut.flush();
        
        if (gzipOut != null) {
        	gzipOut.flush();
        	gzipOut.finish();
        }
        if (chunkedOut != null) {
            chunkedOut.finish();
        }
    }
    
    
    
    protected void respondHeader(
    		Properties conProp,
    		OutputStream respond, 
    		int httpStatusCode, 
    		String httpStatusText, 
    		String conttype, 
    		long contlength,
    		String contentEncoding,
    		String transferEncoding
    ) throws IOException {
    	httpHeader outgoingHeader = new httpHeader();
    	outgoingHeader.put(httpHeader.SERVER, SOAP_HANDLER_VERSION);
    	if (conttype != null) outgoingHeader.put(httpHeader.CONTENT_TYPE,conttype); 
    	if (contlength != -1) outgoingHeader.put(httpHeader.CONTENT_LENGTH, Long.toString(contlength)); 
        if (contentEncoding != null) outgoingHeader.put(httpHeader.CONTENT_ENCODING, contentEncoding);
        if (transferEncoding != null) outgoingHeader.put(httpHeader.TRANSFER_ENCODING, transferEncoding);    	
    	
    	// getting the http version of the soap client
    	String httpVer = conProp.getProperty(httpHeader.CONNECTION_PROP_HTTP_VER);     	
    	
    	// sending http headers
    	httpd.sendRespondHeader(conProp,respond,httpVer,httpStatusCode,httpStatusText,outgoingHeader);
    }     
}
