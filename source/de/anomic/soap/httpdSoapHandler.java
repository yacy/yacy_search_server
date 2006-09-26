package de.anomic.soap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;

import org.apache.axis.AxisFault;
import org.apache.axis.Constants;
import org.apache.axis.EngineConfiguration;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.WSDDEngineConfiguration;
import org.apache.axis.deployment.wsdd.WSDDDeployment;
import org.apache.axis.deployment.wsdd.WSDDDocument;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.SOAPFault;
import org.apache.axis.server.AxisServer;
import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.anomic.http.httpChunkedInputStream;
import de.anomic.http.httpContentLengthInputStream;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.http.httpdAbstractHandler;
import de.anomic.http.httpdHandler;
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
      +     "<service name=\"index\" provider=\"java:RPC\" >"
      +         "<parameter name=\"typeMappingVersion\" value=\"1.1\"/>"
      +         "<parameter name=\"scope\" value=\"Request\"/>"
      +         "<parameter name=\"className\" value=\"de.anomic.soap.httpdSoapService\" />"
      +         "<parameter name=\"allowedMethods\" value=\"*\" />"
      +     "</service>"
      + "</deployment>";       
    
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
    private serverClassLoader provider = null;
    private HashMap templates;
    private serverSwitch switchboard;
    
    private static AxisServer engine = null;
    private File htRootPath;
    private File htTemplatePath;
    
    /* 
     * Creating and configuring an apache Axis server.
     * This is only needed once.
     */
    static  {
        // create an Axis server
        engine = new AxisServer();
        
        // setting some options ...
        engine.setShouldSaveConfig(false);
        
        // deploy the service 
        deployService(serviceDeploymentString,engine);
    }
    
    /**
     * Constructor of this class
     * @param switchboard
     */
    public httpdSoapHandler(serverSwitch switchboard) {
        super();
        
        this.switchboard = switchboard;
        this.theLogger = new serverLog("SOAP");

        // create a htRootPath: system pages
        if (this.htRootPath == null) {
            this.htRootPath = new File(this.switchboard.getRootPath(), this.switchboard.getConfig("htRootPath","htroot"));
            // if (!(htRootPath.exists())) htRootPath.mkdir();
        }        
        
        if (this.htTemplatePath == null) {
            this.htTemplatePath = new File(switchboard.getRootPath(), switchboard.getConfig("htTemplatePath","htroot/env/templates"));
            // if (!(this.htTemplatePath.exists())) this.htTemplatePath.mkdir();
        }        
        
        if (this.provider == null) {
            this.provider = new serverClassLoader(/*this.getClass().getClassLoader()*/);
        }
        
        if (this.templates == null) this.templates = loadTemplates(this.htTemplatePath);        
    }
    
    private byte[] readRequestBody(httpHeader requestHeader, PushbackInputStream body) throws SoapException {
        try {
            // getting an input stream to handle transfer encoding and content encoding properly        
            InputStream soapInput = getBodyInputStream(requestHeader, body);

            // read the content
            return serverFileUtils.read(soapInput);
        } catch (IOException e) {
            throw new SoapException(500,"Read error",e.getMessage());
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
            if (transferEncoding.equalsIgnoreCase("chunked")) {
                input = new httpChunkedInputStream(body);
            } else {         
                String errorMsg = "Unsupported transfer-encoding: "+ transferEncoding;
                this.theLogger.logSevere(errorMsg);
                throw new SoapException(501,"Not Implemented",errorMsg);
            }
        } else if (contentLength > 0) {
            input = new httpContentLengthInputStream(body,contentLength);
        } else {
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
            throw new SoapException(400,"Bad Request",e.getMessage());
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
    public void doHead(Properties conProp, httpHeader header, OutputStream clientOut) throws IOException {
        sendMessage(clientOut, 501, "Not Implemented", "Connection method is not supported by this handler",null); 
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
        sendMessage(clientOut, 501, "Not Implemented", "Connection method is not supported by this handler",null);
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
        String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
        try {
            MessageContext msgContext = this.generateMessageContext(path, requestHeader, conProp);
            
            Document doc = null;
            try {
                engine.generateWSDL(msgContext);
                doc = (Document) msgContext.getProperty("WSDL");
            } catch (AxisFault ex) {
                Message errorMsg = faultToMessage(null, msgContext, ex);
                throw new SoapException(500,"Unable to generate WSDL",errorMsg);
            }
            
            if (doc != null) {
                // Converting the the wsdl document into a byte-array
                String responseDoc = XMLUtils.DocumentToString(doc);
                byte[] result = responseDoc.getBytes("UTF-8");
                
                // send back the result
                sendMessage(response, 200, "OK", "text/xml; charset=utf-8", result);
                
                if (!(requestHeader.get("Connection", "close").equals("keep-alive"))) {
                    // wait a little time until everything closes so that clients can read from the streams/sockets
                    try {Thread.currentThread().join(200);} catch (InterruptedException e) {/* ignore this */}
                  }
            } else {
                // if we where unable to generate the wsdl file ....
                String errorMsg = "Internal Server Error: Unable to generate the WSDL file.";
                sendMessage(response, 500, "Internal Error", "text/plain",errorMsg.getBytes("UTF-8"));            
            }
            
            return;
        } catch (SoapException e) {
            try {
                sendSoapException(response, e);
            } catch (Exception ex) {                
                this.theLogger.logSevere("Unexpected Exception while sending error message",e);
            } finally {
                conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");                
            }
        } catch (Exception e) {
            conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
            this.theLogger.logSevere("Unexpected Exception with query: " + path,e);            
        }
        
    }

    /**
     * HTTP Post method. Needed to call a soap service on this server from a soap client
     * @param conProp the connection properties
     * @param requestHeader the received http headers
     * @param response {@link OutputStream} to the client
     * @param body the request body containing the SOAP message
     * 
     * @throws IOException 
     * 
     * @see de.anomic.http.httpdHandler#doPost(java.util.Properties, de.anomic.http.httpHeader, java.io.OutputStream, java.io.PushbackInputStream)
     */
    public void doPost(Properties conProp, httpHeader requestHeader, OutputStream response, PushbackInputStream body) throws IOException {
        String path = conProp.getProperty(httpHeader.CONNECTION_PROP_PATH);
        try {            
            /* ========================================================================
             * GENERATE REQUEST MESSAGE
             * ======================================================================== */
            // read the request message
            byte[] buffer = readRequestBody(requestHeader, body);
            
            // generating the SOAP message context that will be passed over to the invoked service
            MessageContext msgContext = this.generateMessageContext(path, requestHeader, conProp);
            
            // Generating a SOAP Request Message Object
            Message requestMsg = new Message(
                    buffer, 
                    false, 
                    requestHeader.mime(), 
                    (String)requestHeader.get(httpHeader.CONTENT_LOCATION)
            );
            msgContext.setRequestMessage(requestMsg);
            
            
            /* ========================================================================
             * SERVICE INVOCATION
             * ======================================================================== */
            Message responseMsg = this.invokeService(msgContext);
            
            if (responseMsg != null) {
                sendMessage(response, 200, "OK", responseMsg);
            } else {
                sendMessage(response, 202, "Accepted", "text/plain", null);          
            }
            
            return;
        } catch (SoapException e) {
            try {
                sendSoapException(response, e);
            } catch (Exception ex) {                
                this.theLogger.logSevere("Unexpected Exception while sending error message",e);
            } finally {
                conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");                
            }
        } catch (Exception e) {
            conProp.setProperty(httpHeader.CONNECTION_PROP_PERSISTENT,"close");
            this.theLogger.logSevere("Unexpected Exception",e);
        }
    }
    
    protected Message invokeService(MessageContext msgContext) throws SoapException {
        
        int    invocationStatusCode = 200;
        String invocationStatusText = "OK";
        try {
            // invoke the service
            engine.invoke(msgContext);
    
            // Retrieve the response from Axis
            return msgContext.getResponseMessage();                  
        } catch (Exception ex) {
            Message errorMsg;
            AxisFault soapFault;
            if (ex instanceof AxisFault) {
                soapFault = (AxisFault) ex;
                
                QName faultCode = soapFault.getFaultCode();
                if (Constants.FAULT_SOAP12_SENDER.equals(faultCode))  {
                    invocationStatusCode = 400;
                    invocationStatusText = "Bad request";
                } else if ("Server.Unauthorized".equals(faultCode.getLocalPart()))  {
                    invocationStatusCode = 401; 
                    invocationStatusText = "Unauthorized";
                } else {
                    invocationStatusCode = 500; 
                    invocationStatusText = "Internal server error";
                }                    
            } else {                 
                invocationStatusCode = 500; 
                invocationStatusText = "Internal server error";
                soapFault = AxisFault.makeFault(ex);
            }
            
            // There may be headers we want to preserve in the
            // response message - so if it's there, just add the
            // FaultElement to it. Otherwise, make a new one.
            errorMsg = msgContext.getResponseMessage();
            errorMsg = faultToMessage(errorMsg, msgContext, soapFault);
            throw new SoapException(invocationStatusCode,invocationStatusText,errorMsg);
        }
    }
    
    protected Message faultToMessage(Message errorMsg, MessageContext msgContext, AxisFault soapFault) {
        Message theErrorMsg = errorMsg;
        if (theErrorMsg == null) {
            theErrorMsg = new Message(soapFault);
            theErrorMsg.setMessageContext(msgContext);
        }  else  {
            try {
                SOAPEnvelope env = theErrorMsg.getSOAPEnvelope();
                env.clearBody();
                env.addBodyElement(new SOAPFault(soapFault));
            } catch (AxisFault fault)  {
                // Should never reach here!
            }
        }     
        return theErrorMsg;
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
        } catch (AxisFault e) {
            throw new SoapException(500,"Unable to set the target service",e.getMessage());
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
    
    protected void sendSoapException(OutputStream out, SoapException e) throws UnsupportedEncodingException, IOException, SOAPException {
        Object errorMsg = e.getErrorMsg();
        String contentType = null;

        // getting the error message body and content length
        ByteArrayOutputStream bout = new ByteArrayOutputStream(); 
        if (errorMsg instanceof String) {
            bout.write(((String)errorMsg).getBytes("UTF-8"));  
            contentType = "text/plain; charset=UTF-8";
        } else {
            Message soapErrorMsg = (Message)errorMsg;
            soapErrorMsg.writeTo(bout);
            contentType = soapErrorMsg.getContentType(soapErrorMsg.getMessageContext().getSOAPConstants());
        }

        // send out the message
        sendMessage(out, e.getStatusCode(), e.getStatusText(), contentType, bout.toByteArray());
    }
    
    protected void sendMessage(OutputStream out, int statusCode, String statusText, String contentType, byte[] MessageBody) throws IOException {
        // write out the response header
        respondHeader(out, statusCode, statusText, (MessageBody==null)?null:contentType, (MessageBody==null)?-1:MessageBody.length);
        
        // write the message body
        if (MessageBody != null) out.write(MessageBody);
        out.flush();
    }
    
    protected void sendMessage(OutputStream out, int statusCode, String statusText, Message soapMessage) throws IOException, SoapException {
        // getting the content body
        ByteArrayOutputStream bout = new ByteArrayOutputStream(); 
        
        try {
            soapMessage.writeTo(bout);
        } catch (SOAPException e) {
            throw new SoapException(500,"Unable to externalize SOAP message",e.getMessage());
        }
        
        // getting the content type
        String contentType = null;
        try {
        contentType = soapMessage.getContentType(soapMessage.getMessageContext().getSOAPConstants());
        } catch (AxisFault e) {
            throw new SoapException(500,"Unable to get content-type for SOAP message",e.getMessage());
        }
        
        // sending the message
        sendMessage(out, statusCode, statusText, contentType, bout.toByteArray());
    }
    
    /**
     * This method was copied from {@link httpdFileHandler}. Maybe it would be a good idea
     * to move this function up into {@link httpdAbstractHandler} 
     * 
     * @param out the {@link OutputStream} to the client
     * @param retcode the http code 
     * @param conttype the content type and encoding
     * @param contlength the content length
     * @throws IOException
     */
    protected void respondHeader(OutputStream out, int retcode, String returnStatus, String conttype, long contlength) throws IOException {
        try {
            out.write(("HTTP/1.1 " + retcode + " " + returnStatus + "\r\n").getBytes());
            out.write(("Server: AnomicHTTPD (www.anomic.de)\r\n").getBytes());
            out.write(("Date: " + httpc.dateString(httpc.nowDate()) + "\r\n").getBytes());
            if (conttype != null) out.write((httpHeader.CONTENT_TYPE +  ": " + conttype + "\r\n").getBytes());
            if (contlength != -1) out.write((httpHeader.CONTENT_LENGTH + ": " + contlength +"\r\n").getBytes());
            out.write(("\r\n").getBytes());
            out.flush();
        } catch (Exception e) {
            // any interruption may be caused be network error or because the user has closed
            // the windows during transmission. We simply pass it as IOException
            throw new IOException(e.getMessage());
        }
    }     
}
