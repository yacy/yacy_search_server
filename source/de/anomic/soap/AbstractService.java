//AbstractService.java 
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.SOAPHeaderElement;
import org.w3c.dom.Document;

import de.anomic.http.httpHeader;
import de.anomic.http.httpTemplate;
import de.anomic.server.serverClassLoader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public abstract class AbstractService {
    protected String rootPath;
    protected serverClassLoader provider;
    protected HashMap templates;
    protected serverSwitch switchboard;
    protected httpHeader requestHeader;
    protected MessageContext messageContext;
    
    /**
     * This function is called by the available service functions to
     * extract all needed informations from the SOAP message context.
     * @throws AxisFault 
     */
    protected void extractMessageContext(boolean authenticate) throws AxisFault {        
        this.messageContext = MessageContext.getCurrentContext();
        
        this.rootPath      = (String) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_HTTP_ROOT_PATH);       
        this.provider      = (serverClassLoader) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_SERVER_CLASSLOADER);
        this.templates     = (HashMap) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_TEMPLATES);
        this.switchboard   = (serverSwitch) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_SERVER_SWITCH);
        this.requestHeader = (httpHeader) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_HTTP_HEADER);
        
        if (authenticate) {
            this.doAuthentication();
        }                        
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
    protected byte[] writeTemplate(String templateName, serverObjects args) 
        throws AxisFault {
        try {
            // determining the proper class that should be invoked
            File file = new File(this.rootPath, templateName);    
            File rc = rewriteClassFile(file);
            
            // invoke the desired method
            serverObjects tp = (serverObjects) rewriteMethod(rc).invoke(null, new Object[] {this.requestHeader, args, this.switchboard});
            
            // testing if a authentication was needed by the invoked method
            validateAuthentication(tp);
            
            // adding all available templates
            tp.putAll(this.templates);
            
            // generating the output document
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(file);
            httpTemplate.writeTemplate(fis, o, tp, "-UNRESOLVED_PATTERN-".getBytes("UTF-8"));
            o.close();
            fis.close();
            
            // convert it into a byte array and send it back as result
            byte[] result = o.toByteArray();            
            return result; 
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
    protected void validateAuthentication(serverObjects tp) throws AxisFault {
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
    protected void doAuthentication() throws AxisFault {
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
            if (authString.length() == 0) {
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
    protected File rewriteClassFile(File template) {
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
    protected Method rewriteMethod(File classFile) {
        Method m = null;
        // now make a class out of the stream
        try {
            //System.out.println("**DEBUG** loading class file " + classFile);
            Class c = this.provider.loadClass(classFile);
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
    
    protected Document convertContentToXML(String contentString) throws Exception {
        return convertContentToXML(contentString.getBytes("UTF-8"));
    }    
    
    protected Document convertContentToXML(byte[] content) throws Exception {
        Document doc = null;
        try {
            DocumentBuilderFactory newDocBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder newDocBuilder = newDocBuilderFactory.newDocumentBuilder();          
            
            ByteArrayInputStream byteIn = new ByteArrayInputStream(content);
            doc = newDocBuilder.parse(byteIn);                      
        }  catch (Exception e) {
            String errorMessage = "Unable to parse the search result XML data. " + e.getClass().getName() + ". " + e.getMessage();
            throw new Exception(errorMessage);
        }       
        
        return doc;
    }        
        
}
