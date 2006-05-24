package de.anomic.soap;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.axis.Constants;
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

import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.http.httpdAbstractHandler;
import de.anomic.http.httpdHandler;
import de.anomic.server.serverClassLoader;
import de.anomic.server.serverCore;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverSwitch;

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
    static 
    {
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
    public httpdSoapHandler(serverSwitch switchboard)
    {
        super();
        
        this.switchboard = switchboard;

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
    public void doGet(Properties conProp, httpHeader requestHeader, OutputStream response) throws IOException
    {
        String path = conProp.getProperty("PATH");
        try
        {
            MessageContext msgContext = this.generateMessageContext(path, requestHeader);
            
            engine.generateWSDL(msgContext);
            Document doc = (Document) msgContext.getProperty("WSDL");
            
            if (doc != null) 
            {
                // Converting the the wsdl document into a byte-array
                String responseDoc = XMLUtils.DocumentToString(doc);
                byte[] result = responseDoc.getBytes();
                
                /*
                 * Setting the response header
                 * - Status: 200 OK
                 * - Content Type: text/xml
                 * - Encoding: UTF8
                 * - Date: current Date
                 */
                respondHeader(response, 200, "text/xml; charset=utf-8", result.length, new Date(), null, null);
                
                // writing out the data
                Thread.currentThread().join(200);
                serverFileUtils.write(result, response); 
                response.flush();
                
                if (!(requestHeader.get("Connection", "close").equals("keep-alive"))) {
                    // wait a little time until everything closes so that clients can read from the streams/sockets
                    try {Thread.currentThread().join(1000);} catch (InterruptedException e) {}
                  }
            }
            
            // if we where unable to generate the wsdl file ....
            else
            {
                String errorMsg = "Internal Server Error: Unable to generate the WSDL file.";
                respondHeader(response, 500, "text/plain", errorMsg.length(), httpc.nowDate(), null, null);
                response.write(errorMsg.getBytes());
                response.flush();                
            }
            
            return;
        }
        catch (Exception e)
        {
            System.out.println("ERROR: Exception with query: " + path + "; '" + e.toString() + ":" + e.getMessage() + "'\r\n");
        }
        
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
    public void doHead(Properties conProp, httpHeader header, OutputStream response) throws IOException
    {
        // the http HEAD method is not allowed for this SOAP API
        String errorMsg = "Method Not Allowed";
        respondHeader(response, 405, "text/plain", errorMsg.length(), httpc.nowDate(), null, null);
        response.write(errorMsg.getBytes());
        response.flush();                
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
    public void doPost(Properties conProp, httpHeader requestHeader, OutputStream response, PushbackInputStream body) throws IOException
    {
        String path = conProp.getProperty("PATH");
        try
        {            
            /*
             * Converting the request body into a bytehstream needed by the apache
             * axis engine
             */
            int contentLength = requestHeader.containsKey("CONTENT-LENGTH")?
                                Integer.parseInt((String) requestHeader.get("CONTENT-LENGTH")):
                                0;
                                
            byte[] buffer = new byte[contentLength];
            body.read(buffer);
            InputStream soapInputStream = new ByteArrayInputStream(buffer);      
            
            /*
             * generating the SOAP message context that will be passed over to the invoked
             * service
             */
            MessageContext msgContext = this.generateMessageContext(path, requestHeader);
            
            /*
             * Generating a SOAP Request Message Object from the XML document
             * and store it into the message context
             */
            Message requestMsg = new Message(soapInputStream, false, "text/xml;charset=\"utf-8\"", "");
            msgContext.setRequestMessage(requestMsg);
            
            // invoke the service
            engine.invoke(msgContext);
    
            // Retrieve the response from Axis
            Message responseMsg = msgContext.getResponseMessage();                 
            
            if (responseMsg != null)
            {
                respondHeader(response, 200, "text/xml; charset=utf-8", responseMsg.getContentLength(), new Date(), null, null);
                Thread.currentThread().join(200);
                responseMsg.writeTo(response);
                response.flush();
            }       
            else
            {
                String errorMsg = "Internal Server Error: Unable to invoke the requested service.";
                respondHeader(response, 500, "text/plain", errorMsg.length(), httpc.nowDate(), null, null);
                response.write(errorMsg.getBytes());
                response.flush();                 
            }
            
            return;
        }
        catch (Exception e)
        {
            System.out.println("ERROR: Exception with query: " + path + "; '" + e.toString() + ":" + e.getMessage() + "'\r\n");
        }
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
    public void doConnect(Properties conProp, httpHeader requestHeader, InputStream clientIn, OutputStream clientOut) throws IOException
    {
        // the CONNECT method is not allowed for this SOAP API
        String errorMsg = "Method Not Allowed";
        respondHeader(clientOut, 405, "text/plain", errorMsg.length(), httpc.nowDate(), null, null);
        clientOut.write(errorMsg.getBytes());
        clientOut.flush();            
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
        if (deploymentString != null)
        {
            deploymentStream = new ByteArrayInputStream(deploymentString.getBytes());
        
            Document root = null;
    
            try
            {
                // build XML document from stream
                root = XMLUtils.newDocument(deploymentStream);
                
                // parse WSDD file
                WSDDDocument wsddDoc = new WSDDDocument(root);
                
                // get the configuration of this axis engine
                EngineConfiguration config = theAxisServer.getConfig();
    
                if (config instanceof WSDDEngineConfiguration)
                {
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
            }
            catch (ParserConfigurationException e)
            {
                System.err.println("Could not deploy service.");
                return false;
            }
            catch (SAXException e)
            {
                System.err.println("Could not deploy service.");
                return false;
            }
            catch (IOException e)
            {
                System.err.println("Could not deploy service.");
                return false;
            }
        }
        else
        {
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
     * 
     * @return the generated {@link MessageContext}
     *  
     * @throws Exception if the {@link MessageContext} could not be generated successfully.
     */
    private MessageContext generateMessageContext(String path, httpHeader requestHeader) 
        throws Exception
    {
        try
        {
            // create and initialize a message context
            MessageContext msgContext = new MessageContext(httpdSoapHandler.engine);
            msgContext.setTransportName("SimpleHTTP");
            msgContext.setProperty(org.apache.axis.Constants.MC_REALPATH,      path.toString());
            msgContext.setProperty(Constants.MC_RELATIVE_PATH, path.toString());
            msgContext.setProperty(Constants.MC_JWS_CLASSDIR,  "jwsClasses");
            msgContext.setProperty(Constants.MC_HOME_DIR,      "."); 
            msgContext.setProperty(MessageContext.TRANS_URL, "http://" + requestHeader.get("Host") + ((((String)requestHeader.get("Host")).indexOf(":") > -1)?"":Integer.toString(serverCore.getPortNr(this.switchboard.getConfig("port","8080")))) + "/soap/index");
            
            msgContext.setProperty(MESSAGE_CONTEXT_HTTP_ROOT_PATH  ,this.htRootPath.toString());
            msgContext.setProperty(MESSAGE_CONTEXT_SERVER_SWITCH,this.switchboard);
            msgContext.setProperty(MESSAGE_CONTEXT_HTTP_HEADER ,requestHeader);        
            msgContext.setProperty(MESSAGE_CONTEXT_SERVER_CLASSLOADER ,this.provider);
            msgContext.setProperty(MESSAGE_CONTEXT_TEMPLATES ,this.templates);    
            
            msgContext.setTargetService(path.substring(6));
            
            return msgContext;
        }
        catch (Exception e)
        {
            throw new Exception ("Unable to generate the message context. ",e);
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
     * This method was copied from {@link httpdFileHandler}. Maybe it would be a good idea
     * to move this function up into {@link httpdAbstractHandler} 
     * 
     * @param out the {@link OutputStream} to the client
     * @param retcode the http code 
     * @param conttype the content type and encoding
     * @param contlength the content length
     * @param moddate the modification date of the content
     * @param expires the date of expiry of the content
     * @param cookie cookies to be send
     * 
     * @throws IOException
     */
    protected void respondHeader(OutputStream out, int retcode, String conttype, long contlength, Date moddate, Date expires, String cookie) throws IOException
    {
        try {
            out.write(("HTTP/1.1 " + retcode + " OK\r\n").getBytes());
            out.write(("Server: AnomicHTTPD (www.anomic.de)\r\n").getBytes());
            out.write(("Date: " + httpc.dateString(httpc.nowDate()) + "\r\n").getBytes());
            if (expires != null) out.write(("Expires: " + httpc.dateString(expires) + "\r\n").getBytes()); 
            out.write(("Content-type: " + conttype /* "image/gif", "text/html" */ + "\r\n").getBytes());
            out.write(("Last-modified: " + httpc.dateString(moddate) + "\r\n").getBytes()); 
            out.write(("Content-length: " + contlength +"\r\n").getBytes());
            out.write(("Pragma: no-cache\r\n").getBytes());
            //    out.write(("Accept-ranges: bytes\r\n").getBytes());
            if (cookie != null) out.write(("Set-Cookie: " + cookie + "\r\n").getBytes());
            out.write(("\r\n").getBytes());
            out.flush();
        } catch (Exception e) {
            // any interruption may be caused be network error or because the user has closed
            // the windows during transmission. We simply pass it as IOException
            throw new IOException(e.getMessage());
        }
    }     
}
