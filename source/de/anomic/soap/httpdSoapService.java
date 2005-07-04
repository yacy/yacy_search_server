package de.anomic.soap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.SOAPHeaderElement;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import de.anomic.http.httpHeader;
import de.anomic.http.httpTemplate;
import de.anomic.server.serverClassLoader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

/**
 * SOAP Service Class that will be invoked by the {@link de.anomic.http.httpdSoapHandler}
 * 
 * @author Martin Thelian
 */
public class httpdSoapService
{
    /* ================================================================
     * Constants needed to set the template that should be used to 
     * fullfil the request
     * ================================================================ */
    /**
     * Constant: template for searching
     */
    private static final String TEMPLATE_SEARCH = "index.soap";
    /**
     * Constant: template for the network status page
     */    
    private static final String TEMPLATE_NETWORK_XML = "Network.xml";
    /**
     * Constant: template for crawling
     */    
    private static final String TEMPLATE_CRAWLING = "IndexCreate_p.html";
    
    /* ================================================================
     * Other Object fields
     * ================================================================ */
    /**
     * A hashset containing all templates that requires user authentication
     */
    private static final HashSet SERVICE_NEEDS_AUTHENTICATION = new HashSet(Arrays.asList(new String[] 
                                                                {TEMPLATE_CRAWLING}));
        
    private String rootPath;
    private serverClassLoader provider;
    private HashMap templates;
    private serverSwitch switchboard;
    private httpHeader requestHeader;
    private MessageContext messageContext;
    

    /**
     * Constructor of this class
     */
    public httpdSoapService() {
        super();
        
        // nothing special todo here at the moment
    }

    /**
     * Service for doing a simple search with the standard settings
     * 
     * @param searchString the search string that should be used
     * @return an rss document containing the search results.
     * 
     * @throws AxisFault if the service could not be executed propery. 
     */
    public Document search(
                String searchString,
                String searchMode,
                String searchOrder,
                int maxSearchCount,
                int maxSearchTime,
                String urlMaskFilter            
            ) 
        throws AxisFault {        
        try {
            // extracting the message context
            extractMessageContext();
            
            if ((searchMode == null) || !(searchMode.equalsIgnoreCase("global") || searchMode.equalsIgnoreCase("locale"))) {
                searchMode = "global";
            }
            
            if (urlMaskFilter == null) urlMaskFilter = ".*";
            
            // setting the searching properties
            serverObjects args = new serverObjects();
            args.put("order","Quality-Date");
            args.put("Enter","Search");
            args.put("count",Integer.toString(maxSearchCount));
            args.put("resource","global");
            args.put("time",Integer.toString(maxSearchTime));
            args.put("urlmaskfilter",urlMaskFilter);
            
            args.put("search",searchString);
            
            // generating the template containing the search result
            String result = writeTemplate(TEMPLATE_SEARCH, args);
            
            // sending back the result to the client
            return this.convertContentToXML(result);
        } catch (Exception e)  {
            throw new AxisFault(e.getMessage());
        }
    }
    
    /**
     * Service used to query the network properties
     * @throws AxisFault if the service could not be executed propery. 
     */
    public String network() throws AxisFault {
        try {
            // extracting the message context
            extractMessageContext();  
            
            // generating the template containing the network status information
            String result = writeTemplate(TEMPLATE_NETWORK_XML, new serverObjects());
            
            // sending back the result to the client
            return result;
        } catch (Exception e) {
            throw new AxisFault(e.getMessage());
        }
    }
    
    /**
     * Service used start a new crawling job using the default settings for crawling
     * 
     * @return returns the http status page containing the crawling properties to the user
     * TODO: creating an extra xml template that can be send back to the client. 
     * 
     * @throws AxisFault if the service could not be executed propery. 
     */    
    public String crawling(String crawlingURL) throws AxisFault {
        try {
            // extracting the message context
            extractMessageContext();  
            
            // setting the crawling properties
            serverObjects args = new serverObjects();
            args.put("crawlingQ","on");
            args.put("xsstopw","on");
            args.put("crawlOrder","on");
            args.put("crawlingstart","Start New Crawl");
            args.put("crawlingDepth","2");
            args.put("crawlingFilter",".*");
            args.put("storeHTCache","on");
            args.put("localIndexing","on");            
            args.put("crawlingURL",crawlingURL);            
            
            // triggering the crawling
            String result = writeTemplate(TEMPLATE_CRAWLING, args);
            
            // sending back the crawling status page to the user
            return result;
        } catch (Exception e) {
            throw new AxisFault(e.getMessage());
        }        
    }
    
    /**
     * This function is called by the available service functions to
     * extract all needed informations from the SOAP message context.
     */
    private void extractMessageContext() {
        this.messageContext = MessageContext.getCurrentContext();
        
        this.rootPath      = (String) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_HTTP_ROOT_PATH);       
        this.provider      = (serverClassLoader) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_SERVER_CLASSLOADER);
        this.templates     = (HashMap) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_TEMPLATES);
        this.switchboard   = (serverSwitch) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_SERVER_SWITCH);
        this.requestHeader = (httpHeader) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_HTTP_HEADER);          
    }
    
    /**
     * This function is called by the service functions to
     * invoke the desired server-internal method and to generate
     * a output document using one of the available templates.
     * 
     * @param templateName
     * @param args
     * @return
     * @throws AxisFault
     */
    private String writeTemplate(String templateName, serverObjects args) 
        throws AxisFault {
        try {
            // determining the proper class that should be invoked
            File file = new File(this.rootPath, templateName);    
            File rc = rewriteClassFile(file);
            
            if (SERVICE_NEEDS_AUTHENTICATION.contains(templateName)) {
                this.doAuthentication();
            }
            
            // invoke the desired method
            serverObjects tp = (serverObjects) rewriteMethod(rc).invoke(null, new Object[] {this.requestHeader, args, this.switchboard});
            
            // testing if a authentication was needed by the invoked method
            validateAuthentication(tp);
            
            // adding all available templates
            tp.putAll(this.templates);
            
            // generating the output document
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(file);
            httpTemplate.writeTemplate(fis, o, tp, "-UNRESOLVED_PATTERN-".getBytes());
            o.close();
            fis.close();
            
            // convert it into a byte array and send it back as result
            byte[] result = o.toByteArray();            
            return new String(result); 
        } catch (Exception e) {
            throw new AxisFault(e.getMessage());
        }
    }    
    
    /**
     * This function is used to test if an invoked method requires authentication
     * 
     * @param tp the properties returned by a previous method invocation
     * 
     * @throws AxisFault if an authentication was required.
     */
    private void validateAuthentication(serverObjects tp)
        throws AxisFault
    {
        // check if the servlets requests authentification
        if (tp.containsKey("AUTHENTICATE")) {
            throw new AxisFault("log-in required");
        }             
    }    
    
    /**
     * Doing the user authentication. To improve security, this client
     * accepts the base64 encoded and md5 hashed password directly. 
     * 
     * @throws AxisFault if the authentication could not be done successfully
     */
    private void doAuthentication() 
        throws AxisFault {
        // accessing the SOAP request message
        Message message = this.messageContext.getRequestMessage();
        
        // getting the contained soap envelope
        SOAPEnvelope envelope = message.getSOAPEnvelope();
        
        // getting the proper soap header containing the authorization field
        SOAPHeaderElement authElement = envelope.getHeaderByName(httpdSoapHandler.serviceHeaderNamespace, "Authorization");
        if (authElement != null) {        
            // the base64 encoded and md5 hashed authentication string 
            String authString = authElement.getValue();
            
            String adminAccountBase64MD5 = this.switchboard.getConfig("adminAccountBase64MD5","");
            if (adminAccountBase64MD5.length() == 0) {
                throw new AxisFault("log-in required");
            } else if (!(adminAccountBase64MD5.equals(authString))) {
                throw new AxisFault("log-in required");
            }
        }
        else throw new AxisFault("log-in required");
    }
    
    /**
     * This method was copied from the {@link httpdFileHandler httpdFileHandler-class}
     * @param template
     * @return
     */
    private File rewriteClassFile(File template) {
        try {
            String f = template.getCanonicalPath();
            int p = f.lastIndexOf(".");
            if (p < 0) return null;
            f = f.substring(0, p) + ".class";
            //System.out.println("constructed class path " + f);
            File cf = new File(f);
            if (cf.exists()) return cf;
            return null;
        } catch (IOException e) {
            return null;
        }
    }    
    
    /**
     * This method was copied from the {@link httpdFileHandler httpdFileHandler-class}
     * @param classFile
     * @return
     */    
    private Method rewriteMethod(File classFile) {
        Method m = null;
        // now make a class out of the stream
        try {
            //System.out.println("**DEBUG** loading class file " + classFile);
            Class c = provider.loadClass(classFile);
            Class[] params = new Class[] {
            Class.forName("de.anomic.http.httpHeader"),
            Class.forName("de.anomic.server.serverObjects"),
            Class.forName("de.anomic.server.serverSwitch")};
            m = c.getMethod("respond", params);
        } catch (ClassNotFoundException e) {
            System.out.println("INTERNAL ERROR: class " + classFile + " is missing:" + e.getMessage()); 
        } catch (NoSuchMethodException e) {
            System.out.println("INTERNAL ERROR: method respond not found in class " + classFile + ": " + e.getMessage());
        }
        //System.out.println("found method: " + m.toString());
        return m;
        }        
    
    private Document convertContentToXML(String contentString) 
    throws Exception
    {
        Document doc = null;
        try {
            DocumentBuilderFactory newDocBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder newDocBuilder = newDocBuilderFactory.newDocumentBuilder();          
            
            InputSource is = new InputSource(new StringReader(contentString));
            doc = newDocBuilder.parse(is);                      
        }  catch (Exception e) {
            String errorMessage = "Unable to parse the search result XML data. " + e.getClass().getName() + ". " + e.getMessage();
            throw new Exception(errorMessage);
        }       
        
        return doc;
    }    
    
}
