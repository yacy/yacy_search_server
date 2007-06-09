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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.message.SOAPEnvelope;
import org.apache.axis.message.SOAPHeaderElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.anomic.data.userDB;
import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverSwitch;

public abstract class AbstractService {
    protected serverSwitch switchboard;
    protected httpHeader requestHeader;
    protected MessageContext messageContext;
    protected ServerContext serverContext;
    
    protected static final boolean NO_AUTHENTICATION = false;
    protected static final boolean AUTHENTICATION_NEEDED = true;
    
    
    /**
     * This function is called by the available service functions to
     * extract all needed informations from the SOAP message context.
     * @throws AxisFault 
     */
    protected void extractMessageContext(boolean authenticate) throws AxisFault {        
        this.messageContext = MessageContext.getCurrentContext();
        
        this.switchboard   = (serverSwitch) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_SERVER_SWITCH);
        this.requestHeader = (httpHeader) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_HTTP_HEADER);
        this.serverContext = (ServerContext) this.messageContext.getProperty(httpdSoapHandler.MESSAGE_CONTEXT_SERVER_CONTEXT);
        
        if (authenticate) {
            String authInfo = this.doAuthentication();
            
            // modify headers
            // This is needed for plasmaSwitchboard.adminAuthenticated to work
            this.requestHeader.put(httpHeader.AUTHORIZATION,"Basic " + authInfo);
            this.requestHeader.put("CLIENTIP","localhost");
            
        }                        
    }  
    
    /**
     * Doing the user authentication. To improve security, this client
     * accepts the base64 encoded and md5 hashed password directly. 
     * 
     * @throws AxisFault if the authentication could not be done successfully
     */
    protected String doAuthentication() throws AxisFault {
        // accessing the SOAP request message
        Message message = this.messageContext.getRequestMessage();
        
        // getting the contained soap envelope
        SOAPEnvelope envelope = message.getSOAPEnvelope();
        
        // getting the proper soap header containing the authorization field
        SOAPHeaderElement authElement = envelope.getHeaderByName(httpdSoapHandler.serviceHeaderNamespace, "Authorization");
        if (authElement != null) {     
            String adminAccountBase64MD5 = this.switchboard.getConfig(httpd.ADMIN_ACCOUNT_B64MD5,"");        	
        	
            // the base64 encoded and md5 hashed authentication string 
            String authString = authElement.getValue();
            if (authString.length() == 0) throw new AxisFault("log-in required");

            // validate MD5 hash against the user-DB
            SOAPHeaderElement userElement = envelope.getHeaderByName(httpdSoapHandler.serviceHeaderNamespace, "Username");
            if (userElement != null) {
            	String userName = userElement.getValue();
            	userDB.Entry userEntry = ((plasmaSwitchboard)this.switchboard).userDB.md5Auth(userName,authString);
            	if (userEntry.hasRight(userDB.Entry.SOAP_RIGHT))
            		// we need to return the ADMIN_ACCOUNT_B64MD5 here because some servlets also do 
            		// user/admin authentication
            		return adminAccountBase64MD5;
            }
            
            // validate MD5 hash against the static-admin account
            if (!(adminAccountBase64MD5.equals(authString))) {
            	throw new AxisFault("log-in required");
            }
            return adminAccountBase64MD5;
        }
		throw new AxisFault("log-in required");
    }
        
    protected Document convertContentToXML(String contentString) throws Exception {
        return convertContentToXML(contentString.getBytes("UTF-8"));
    }    
    
    protected Document convertContentToXML(byte[] content) throws Exception {
        Document doc = null;
        try {
            DocumentBuilderFactory newDocBuilderFactory = DocumentBuilderFactory.newInstance();
            
//            // disable dtd validation
//            newDocBuilderFactory.setValidating(false);
//            newDocBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
//            newDocBuilderFactory.setFeature("http://xml.org/sax/features/validation", false);
//            
            DocumentBuilder newDocBuilder = newDocBuilderFactory.newDocumentBuilder();
            
            ByteArrayInputStream byteIn = new ByteArrayInputStream(content);
            doc = newDocBuilder.parse(byteIn);                      
        }  catch (Exception e) {
            String errorMessage = "Unable to parse the search result XML data. " + e.getClass().getName() + ". " + e.getMessage();
            throw new Exception(errorMessage);
        }       
        
        return doc;
    }        
    
    public Document createNewXMLDocument(String rootElementName) throws ParserConfigurationException {
    	// creating a new document builder factory
    	DocumentBuilderFactory newDocBuilderFactory = DocumentBuilderFactory.newInstance();

    	// creating a new document builder
    	DocumentBuilder newDocBuilder = newDocBuilderFactory.newDocumentBuilder();

    	// creating a new xml document
    	Document newXMLDocument = newDocBuilder.newDocument();

    	if (rootElementName != null) {
    		// creating the xml root document
    		Element rootElement = newXMLDocument.createElement(rootElementName);    
    		newXMLDocument.appendChild(rootElement);
    	}

    	return newXMLDocument;
    }

        
}
