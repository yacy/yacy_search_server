//MessageService.java 
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


package de.anomic.soap.services;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.axis.AxisFault;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.anomic.data.messageBoard;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;
import de.anomic.yacy.yacyClient;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public class MessageService extends AbstractService {
	
	/* =====================================================================
	 * Used XML Templates
	 * ===================================================================== */
    private static final String TEMPLATE_MESSAGE_HEADER_LIST_XML = "Messages_p.xml";
	
	/* =====================================================================
	 * Other used constants
	 * ===================================================================== */	
	private static final String MESSAGES_CATEGORY_REMOTE = "remote";
	
    /**
     * @return a handler to the YaCy Messages DB
     */
	private messageBoard getMessageDB() {
    	assert (this.switchboard != null) : "Switchboard object is null";
    	assert (this.switchboard instanceof plasmaSwitchboard) : "Incorrect switchboard object";
    	assert (((plasmaSwitchboard)this.switchboard).messageDB != null) : "Messsage DB is null";
    	
    	return ((plasmaSwitchboard)this.switchboard).messageDB;		
	}
	
	/**
	 * Function to read the identifiers of all messages stored in the message db
	 * @return an array of message identifiers currently stored in the message DB
	 * @throws IOException if authentication failed or a DB read error occured
	 */
	public String[] getMessageIDs() throws IOException {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);     
        
        // getting the messageDB
        messageBoard db = getMessageDB();
        
        // loop through the messages and receive the message ids
        ArrayList idList = new ArrayList(db.size());		
		Iterator i = getMessageDB().keys(MESSAGES_CATEGORY_REMOTE, true);
		while (i.hasNext()) {
			String messageKey = (String) i.next();
			if (messageKey != null) idList.add(messageKey);
		}
		
		//return array
		return (String[]) idList.toArray(new String[idList.size()]);
	}
	
	/**
	 * Returns a list with the sender, subject and date of all messages stored in the message db
	 * 
	 * @return a xml document of the following format
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
	 * &lt;messages&gt;
	 * 	&lt;message id="remote______2005060901120600"&gt;
	 * 		&lt;date&gt;2005/06/09 01:12:06&lt;/date&gt;
	 * 		&lt;from hash="peerhash"&gt;SourcePeerName&lt;/from&gt;
	 * 		&lt;to&gt;DestPeerName&lt;/to&gt;
	 * 		&lt;subject&gt;&lt;![CDATA[Message subject]]&gt;&lt;/subject&gt;
	 * 	&lt;/message&gt;
	 * &lt;/messages&gt;
	 * </pre>
	 * 
	 * @throws Exception if authentication failed
	 */
	public Document getMessageHeaderList() throws Exception {
		
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);          	
        
        // generate the xml document
        serverObjects args = new serverObjects();
        args.put("action","list");
        
        byte[] result = this.serverContext.writeTemplate(TEMPLATE_MESSAGE_HEADER_LIST_XML, args, this.requestHeader);
        
        // sending back the result to the client
        return this.convertContentToXML(result);    		
	}
	
	/**
	 * Function to geht detailes about a message stored in the message db
	 * @param messageID the identifier of the message to query
	 * @return a xml document of the following format
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
	 * &lt;message id="remote______2005060901120600"&gt;
	 * 	&lt;date&gt;2005/06/09 01:12:06&lt;/date&gt;
	 * 	&lt;from hash="peerhash"&gt;sourcePeerName&lt;/from&gt;
	 * 	&lt;to&gt;destPeerName&lt;/to&gt;
	 * 	&lt;subject&gt;&lt;![CDATA[Test-Subject]]&gt;&lt;/subject&gt;
	 * 	&lt;message&gt;&lt;![CDATA[Message-Body]]&gt;
	 * &lt;/message&gt;
	 * </pre>
	 * 
	 * @throws Exception if authentication failed
	 */
	public Document getMessage(String messageID) throws Exception {
		
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);
        if (messageID == null || messageID.length() == 0) throw new IllegalArgumentException("The message id must not be null or empty.");
        
        // generate the xml document
        serverObjects args = new serverObjects();
        args.put("action","view");
        args.put("object",messageID);
        
        byte[] result = this.serverContext.writeTemplate(TEMPLATE_MESSAGE_HEADER_LIST_XML, args, this.requestHeader);
        
        // sending back the result to the client
        return this.convertContentToXML(result);    		
	}	
	
	/**
	 * Function to delete a message 
	 * @param messageID the message identifier of the message that should be deleted 
	 * @throws AxisFault if authentication failed or the message ID is unknown
	 */
	public void deleteMessage(String messageID) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);     
        if (messageID == null || messageID.length() == 0) throw new IllegalArgumentException("The message id must not be null or empty.");        
        
        // getting the messageDB
        messageBoard db = getMessageDB();       
        
        // check if the message exists
        if (db.read(messageID) == null) throw new AxisFault("Message with ID " + messageID + " does not exist.");
        
        // delete the message
        db.remove(messageID);
	}
	
	/**
	 * Function to delete multiple messages
	 * @param messageIDs an array of message ids
	 * @throws AxisFault if authentication failed or one of the message IDs is unknown
	 */
	public void deleteMessages(String[] messageIDs) throws AxisFault {
		if (messageIDs == null || messageIDs.length == 0) throw new IllegalArgumentException("The message id array must not be null or empty.");
		
		// loop through the ids
		for (int i=0; i < messageIDs.length; i++) {
			String nextID = messageIDs[i];
			if (nextID == null || nextID.length() == 0) throw new IllegalArgumentException("The message id at position " + i + " is null or empty.");
			
			this.deleteMessage(nextID);
		}
	}
	
	/**
	 * A function to check if the destination peer will accept a message of this peer.
	 * @param destinationPeerHash the peer hash of the destination peer
	 * @return a XML document of the following format
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
	 * &lt;messageSendPermission&gt;
	 * 	&lt;permission&gt;true&lt;/permission&gt;
	 * 	&lt;response&gt;Welcome to my peer!&lt;/response&gt;
	 * 	&lt;messageSize&gt;10240&lt;/messageSize&gt;
	 * 	&lt;attachmentsize&gt;0&lt;/attachmentsize&gt;
	 * &lt;/messageSendPermission&gt;
	 * </pre>
	 * The tag <i>permission</i> specifies if we are allowed to send a messag to this peer. <i>Response</i> is a textual
	 * description why we are allowed or not allowed to send a message. <i>messageSize</i> specifies the maximum
	 * allowed message size. <i>attachmentsize</i> specifies the maximum attachment size accepted.
	 * 
	 * @throws AxisFault if authentication failed or the destination peer is not reachable
	 * @throws ParserConfigurationException if xml generation failed
	 */
	public Document getMessageSendPermission(String destinationPeerHash) throws AxisFault, ParserConfigurationException {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);  
        if (destinationPeerHash == null || destinationPeerHash.length() == 0) throw new IllegalArgumentException("The destination peer hash must not be null or empty.");
        
        // get the peer from the db
        yacySeed targetPeer = yacyCore.seedDB.getConnected(destinationPeerHash);
        if (targetPeer == null) throw new AxisFault("The destination peer is not connected");
        
        // check for permission to send message
        HashMap result = yacyClient.permissionMessage(destinationPeerHash);
        if (result == null) throw new AxisFault("No response received from peer");
        
        boolean accepted = false;
        String reason = "Unknown reason";
        if (result.containsKey("response")) {
        	String response = (String) result.get("response");
        	if (response.equals("-1")) {
        		accepted = false;
        		reason = "request rejected";
        	} else {
        		accepted = true;
        		reason = response;
        	}
        }
        
        // return XML Document
        Element xmlElement = null, xmlRoot;
    	Document xmlDoc = createNewXMLDocument("messageSendPermission");
    	xmlRoot = xmlDoc.getDocumentElement();     
    	
    	xmlElement = xmlDoc.createElement("permission");
    	xmlElement.appendChild(xmlDoc.createTextNode(Boolean.toString(accepted)));
    	xmlRoot.appendChild(xmlElement);    	
    	
    	xmlElement = xmlDoc.createElement("response");
    	xmlElement.appendChild(xmlDoc.createTextNode(reason));
    	xmlRoot.appendChild(xmlElement);    
    	
    	xmlElement = xmlDoc.createElement("messageSize");
    	xmlElement.appendChild(xmlDoc.createTextNode((String)result.get("messagesize")));
    	xmlRoot.appendChild(xmlElement);  
    	
    	xmlElement = xmlDoc.createElement("attachmentsize");
    	xmlElement.appendChild(xmlDoc.createTextNode((String)result.get("attachmentsize")));
    	xmlRoot.appendChild(xmlElement);        	
    	
    	return xmlDoc;
	}
	
	/**
	 * Function to send a message to a remote peer
	 * @param destinationPeerHash the peer hash of the remot peer
	 * @param subject the message subject
	 * @param message the message body
	 * 
	 * @return the a response status message of the remote peer.
	 * 
	 * @throws AxisFault if authentication failed
	 */
	public String sendMessage(String destinationPeerHash, String subject, String message) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);  
        if (destinationPeerHash == null || destinationPeerHash.length() == 0) throw new IllegalArgumentException("The destination peer hash must not be null or empty.");
        if (subject == null || subject.length() == 0) throw new IllegalArgumentException("The subject must not be null or empty.");
        if (message == null || message.length() == 0) throw new IllegalArgumentException("The message body must not be null or empty.");
        
        // convert the string into a byte array
        byte[] mb;
        try {
            mb = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            mb = message.getBytes();
        }
        
        // send the message to the remote peer
        HashMap result = yacyClient.postMessage(destinationPeerHash, subject, mb);
        
        // get the peer resonse
        if (result == null) throw new AxisFault("No response received from peer");        
        return (String) (result.containsKey("response") ? result.get("response") : "Unknown response");
	}
}
