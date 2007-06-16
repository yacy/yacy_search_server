//BlacklistService.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file was contributed by Martin Thelian
//
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import javax.activation.DataHandler;
import javax.xml.soap.SOAPException;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.attachments.Attachments;
import org.w3c.dom.Document;

import de.anomic.data.listManager;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.urlPattern.abstractURLPattern;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;

public class BlacklistService extends AbstractService {


	private static final String LIST_MANAGER_LISTS_PATH = "listManager.listsPath";
	private static final String BLACKLISTS = ".BlackLists";
	//private static final String BLACKLISTS_TYPES = "BlackLists.types";
    private final static String BLACKLIST_SHARED = "BlackLists.Shared";	

	/* =====================================================================
	 * Used XML Templates
	 * ===================================================================== */	
    private static final String TEMPLATE_BLACKLIST_XML = "xml/blacklists_p.xml";
    
    
    public boolean urlIsBlacklisted(String blacklistType, String urlString) throws AxisFault, MalformedURLException {
    	if (blacklistType == null || blacklistType.length() == 0) throw new IllegalArgumentException("The blacklist type must not be null or empty.");
    	if (urlString == null || urlString.length() == 0) throw new IllegalArgumentException("The url must not be null or empty.");
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
    	// check if we know all type passed to this function
    	checkForKnownBlacklistTypes(new String[]{blacklistType});	
    	
    	// check for url validity
    	URL url = new URL(urlString);    	
    	String hostlow = url.getHost().toLowerCase();
    	String file = url.getFile();
    	
    	// check if the specified url is listed
        return (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_PROXY, hostlow, file));
    }    
    
    public Document getBlacklistList() throws Exception {
    	try {
    		// extracting the message context
    		extractMessageContext(AUTHENTICATION_NEEDED);      
    		
    		// generating the template containing the network status information
    		byte[] result = this.serverContext.writeTemplate(TEMPLATE_BLACKLIST_XML,  new serverObjects(), this.requestHeader);
    		
    		// sending back the result to the client
    		return this.convertContentToXML(result);
    	} catch (Exception e) {
    		e.printStackTrace();
    		throw e;
    	}
    }
    
    public void createBlacklist(String blacklistName, boolean shareBlacklist, String[] activateForBlacklistTypes) throws IOException {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");
    	
    	if (blacklistName.indexOf("/") != -1)
    		throw new IllegalArgumentException("Blacklist name must not contain '/'.");    	
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    	
    	
    	// check if we know all types passed to this function
    	checkForKnownBlacklistTypes(activateForBlacklistTypes);
    	
		// initialize the list manager
		initBlacklistManager();
		
		// check if the blacklist already exists
		if (blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' already exist.");
		
        // creating the new file
		createBlacklistFile(blacklistName);
        
        // share the newly created blacklist
        if (shareBlacklist) doShareBlacklist(blacklistName);
        
        // activate blacklist
        this.activateBlacklistForTypes(blacklistName,activateForBlacklistTypes);
    }
    
    public void deleteBlacklist(String blacklistName) throws AxisFault {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);
		
        // initialize the list manager
		initBlacklistManager();		
		
		// check if the blacklist exists
		if (!blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' does not exist.");				
		
		// deactivate list
		deativateBlacklistForAllTypes(blacklistName);
		
		// unshare list
		doUnshareBlacklist(blacklistName);
		
		// delete the file
		deleteBlacklistFile(blacklistName);
    }
    
    public void shareBlacklist(String blacklistName) throws AxisFault {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
        // initialize the list manager
		initBlacklistManager();				
		
		// check if the blacklist file exists
		if (!blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' does not exist.");
		
		// share blacklist
		this.doShareBlacklist(blacklistName);
    }
    
    public void unshareBlacklist(String blacklistName) throws AxisFault {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
        // initialize the list manager
		initBlacklistManager();				
		
		// check if the blacklist file exists
		if (!blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' does not exist.");
		
		// share blacklist
		this.doUnshareBlacklist(blacklistName);		
    }
    
    public void activateBlacklist(String blacklistName, String[] activateForBlacklistTypes) throws AxisFault {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");  
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
        // initialize the list manager
		initBlacklistManager();				
		
		// check if the blacklist file exists
		if (!blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' does not exist.");		
		
    	// check if we know all types passed to this function
    	checkForKnownBlacklistTypes(activateForBlacklistTypes);		
    	
    	// activate blacklist
    	activateBlacklistForTypes(blacklistName, activateForBlacklistTypes);
    }
    
    public void deactivateBlacklist(String blacklistName, String[] deactivateForBlacklistTypes) throws AxisFault {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");  
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
        // initialize the list manager
		initBlacklistManager();		
		
		// check if the blacklist file exists
		if (!blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' does not exist.");
		
		
    	// check if we know all types passed to this function
    	checkForKnownBlacklistTypes(deactivateForBlacklistTypes);		
    	
    	// activate blacklist
    	deactivateBlacklistForTypes(blacklistName, deactivateForBlacklistTypes);		
    }
    
    public void addBlacklistItem(String blacklistName, String blacklistItem) throws AxisFault {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");  
    	if ((blacklistItem == null)||(blacklistItem.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist item must not be null or empty.");      	
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
        // initialize the list manager
		initBlacklistManager();		    	
		
		// check if the blacklist file exists
		if (!blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' does not exist.");		
		
		// prepare item
		blacklistItem = prepareBlacklistItem(blacklistItem);
        
        // TODO: check if the entry is already in there
        
        // append the line to the file
		addBlacklistItemToFile(blacklistItem, blacklistName);
        
        // pass the entry to the blacklist engine
        addBlacklistItemToBlacklist(blacklistItem, blacklistName);
    }
    
    public void removeBlacklistItem(String blacklistName, String blacklistItem) throws AxisFault {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");  
    	if ((blacklistItem == null)||(blacklistItem.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist item must not be null or empty.");      	
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
        // initialize the list manager
		initBlacklistManager();		    	
		
		// check if the blacklist file exists
		if (!blacklistExists(blacklistName))
			throw new AxisFault("Blacklist with name '" + blacklistName + "' does not exist.");		
		
		// prepare item
		blacklistItem = prepareBlacklistItem(blacklistItem);
		
		// remove blacklist from file
		removeBlacklistItemFromBlacklistFile(blacklistItem,blacklistName);
		
		// remove it from the blacklist engine
		removeBlacklistItemFromBlacklist(blacklistItem,blacklistName);
    }
    
    public void importBlacklist(String blacklistName) throws IOException, SOAPException {
    	// Check for errors
    	if ((blacklistName == null)||(blacklistName.length() == 0)) 
    		throw new IllegalArgumentException("Blacklist name must not be null or empty.");   
    	
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);    
		
        // initialize the list manager
		initBlacklistManager();		    	
		
		// check if the blacklist file exists
		if (!blacklistExists(blacklistName)) {
			// create blacklist
			createBlacklistFile(blacklistName);
		}
		
		// get attachment
        MessageContext msgContext = MessageContext.getCurrentContext();

        // getting the request message
        Message reqMsg = msgContext.getRequestMessage();
        
        // getting the attachment implementation
        Attachments messageAttachments = reqMsg.getAttachmentsImpl();
        if (messageAttachments == null) {
            throw new AxisFault("Attachments not supported");
        }		
        
        int attachmentCount= messageAttachments.getAttachmentCount();
        if (attachmentCount == 0) 
            throw new AxisFault("No attachment found");
        else if (attachmentCount != 1)
            throw new AxisFault("Too many attachments as expected.");     
        
        // getting the attachments
        AttachmentPart[] attachments = (AttachmentPart[])messageAttachments.getAttachments().toArray(new AttachmentPart[attachmentCount]);
        
        // getting the content of the attachment
        DataHandler dh = attachments[0].getDataHandler();
        
        PrintWriter writer = null;
        BufferedReader reader = null;
        try {
        	// getting a reader
        	reader = new BufferedReader(new InputStreamReader(dh.getInputStream(),"UTF-8"));
        	
        	// getting blacklist file writer
        	writer = getBlacklistFileWriter(blacklistName);
        	
        	// read new item
        	String blacklistItem = null;
        	while ((blacklistItem = reader.readLine()) != null) {
        		// convert it into a proper format
        		blacklistItem = prepareBlacklistItem(blacklistItem);
        		
        		// TODO: check if the item already exits
        		
        		// write item to blacklist file
        		writer.println(blacklistItem);
        		writer.flush();
        		
        		// inform blacklist engine about new item
        		addBlacklistItemToBlacklist(blacklistItem, blacklistName);
        	}
        } finally {
        	if (reader != null) try { reader.close(); } catch (Exception e) {/* */}
        	if (writer != null) try { writer.close(); } catch (Exception e) {/* */}
        }
    }
    
    public String[] getBlacklistTypes() throws AxisFault {
		// extracting the message context
		extractMessageContext(AUTHENTICATION_NEEDED);
		
		// initialize the list manager
		initBlacklistManager();		
		
		// return supported types
		return getSupportedBlacklistTypeArray();
    }
    
    private void addBlacklistItemToBlacklist(String blacklistItem, String blacklistName) {
    	// split the item into host part and path
    	String[] itemParts = getBlacklistItemParts(blacklistItem);
    	
    	// getting the supported blacklist types
        String[] supportedBlacklistTypes = getSupportedBlacklistTypeArray();
        
        // loop through the various types
        for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
        	
        	// if the current blacklist is activated for the type, add the item to the list
            if (listManager.ListInListslist(supportedBlacklistTypes[blTypes] + BLACKLISTS,blacklistName)) {
                plasmaSwitchboard.urlBlacklist.add(supportedBlacklistTypes[blTypes],itemParts[0], itemParts[1]);
            }                
        }       	
    }
    
    private void addBlacklistItemToFile(String blacklistItem, String blacklistName) throws AxisFault {
        PrintWriter pw = null;
        try {
            pw = getBlacklistFileWriter(blacklistName);
            pw.println(blacklistItem);
            pw.flush();
            pw.close();
        } catch (IOException e) {
            throw new AxisFault("Unable to append blacklist entry.",e);
        } finally {
            if (pw != null) try { pw.close(); } catch (Exception e){ /* */}
        }            	
    }
    
    private PrintWriter getBlacklistFileWriter(String blacklistName) throws AxisFault {
    	try {
    	return new PrintWriter(new FileWriter(getBlacklistFile(blacklistName), true));
    	} catch (IOException e) {
    		throw new AxisFault("Unable to open blacklist file.",e);
    	}
    }
    
    private void removeBlacklistItemFromBlacklistFile(String blacklistItem, String blacklistName) {
        // load blacklist data from file
        ArrayList list = listManager.getListArray(getBlacklistFile(blacklistName));
        
        // delete the old entry from file
        if (list != null) {
            for (int i=0; i < list.size(); i++) {
                if (((String)list.get(i)).equals(blacklistItem)) {
                    list.remove(i);
                    break;
                }
            }
            listManager.writeList(getBlacklistFile(blacklistName), (String[])list.toArray(new String[list.size()]));
        }
    }
    
    private void removeBlacklistItemFromBlacklist(String blacklistItem, String blacklistName) {
    	String[] itemParts = getBlacklistItemParts(blacklistItem);
    	
    	// getting the supported blacklist types
        String[] supportedBlacklistTypes = getSupportedBlacklistTypeArray();
        
        // loop through the various types
        for (int blTypes=0; blTypes < supportedBlacklistTypes.length; blTypes++) {
        	
        	// if the current blacklist is activated for the type, remove the item from the list
            if (listManager.ListInListslist(supportedBlacklistTypes[blTypes] + BLACKLISTS,blacklistName)) {
                plasmaSwitchboard.urlBlacklist.remove(supportedBlacklistTypes[blTypes],itemParts[0], itemParts[1]);
            }                
        }       	
    }
    
    private String prepareBlacklistItem(String blacklistItem) {
    	if (blacklistItem == null) throw new NullPointerException("Item is null");
    	
		// cut of heading http://
        if (blacklistItem.startsWith("http://") ){
        	blacklistItem = blacklistItem.substring("http://".length());
        }		
        
        // adding missing parts
        int pos = blacklistItem.indexOf("/");
        if (pos < 0) {
            // add default empty path pattern
            blacklistItem = blacklistItem + "/.*";
        }    	
        return blacklistItem;
    }
    
    private String[] getBlacklistItemParts(String blacklistItem) {
    	if (blacklistItem == null) throw new NullPointerException("Item is null");
    	
    	int pos = blacklistItem.indexOf("/");
    	if (pos == -1) throw new IllegalArgumentException("Item format is not correct.");
    	
    	return new String[] {
    			blacklistItem.substring(0, pos), 
    			blacklistItem.substring(pos + 1)
    	};
    }
    
    /* not used
    private String[] getSharedBlacklistArray() {
        String sharedBlacklists = this.switchboard.getConfig(BLACKLIST_SHARED, "");
        String[] supportedBlacklistTypeArray = sharedBlacklists.split(",");
        return supportedBlacklistTypeArray;    	
    }
    */
    
    private File getBlacklistFile(String blacklistName) {
    	File blacklistFile = new File(listManager.listsPath, blacklistName);
    	return blacklistFile;
    }
    
    private boolean blacklistExists(String blacklistName) {
    	File blacklistFile = getBlacklistFile(blacklistName);
    	return blacklistFile.exists();
    }
    
    /* not used
    private HashSet getSharedBlacklistSet() {
        HashSet supportedTypesSet = new HashSet(Arrays.asList(getSharedBlacklistArray()));
        return supportedTypesSet;    	
    }
    */
    
    private String[] getSupportedBlacklistTypeArray() {
        String supportedBlacklistTypesStr = abstractURLPattern.BLACKLIST_TYPES_STRING;
        String[] supportedBlacklistTypeArray = supportedBlacklistTypesStr.split(",");
        return supportedBlacklistTypeArray;
    }
    
    private void createBlacklistFile(String blacklistName) throws IOException {
        File newFile = getBlacklistFile(blacklistName);
        newFile.createNewFile();    	
    }
    
    private void deleteBlacklistFile(String blacklistName) {
        File BlackListFile = new File(listManager.listsPath, blacklistName);
        BlackListFile.delete();    	
    }
    
    private void doShareBlacklist(String blacklistName) {
    	listManager.addListToListslist(BLACKLIST_SHARED, blacklistName);
    }
    
    private void doUnshareBlacklist(String blacklistName) {
    	listManager.removeListFromListslist(BLACKLIST_SHARED, blacklistName);
    }
    
    private void initBlacklistManager() {
    	// init Manager properties
    	if (listManager.switchboard == null)
    		listManager.switchboard = (plasmaSwitchboard) this.switchboard;
    	
    	if (listManager.listsPath == null)
    		listManager.listsPath = new File(listManager.switchboard.getRootPath(),listManager.switchboard.getConfig(LIST_MANAGER_LISTS_PATH, "DATA/LISTS"));
    }
    
    /* not used
    private void ativateBlacklistForAllTypes(String blacklistName) {
    	String[] supportedBlacklistTypes = getSupportedBlacklistTypeArray();
    	this.activateBlacklistForTypes(blacklistName,supportedBlacklistTypes);
    }    
    */
    
    private void activateBlacklistForTypes(String blacklistName, String[] activateForBlacklistTypes) {
    	if (activateForBlacklistTypes == null) return;
    	
        for (int blTypes=0; blTypes < activateForBlacklistTypes.length; blTypes++) {
            listManager.addListToListslist(activateForBlacklistTypes[blTypes] + BLACKLISTS, blacklistName);
        } 
    }
    
    private void deativateBlacklistForAllTypes(String blacklistName) {
    	String[] supportedBlacklistTypes = getSupportedBlacklistTypeArray();
    	this.deactivateBlacklistForTypes(blacklistName,supportedBlacklistTypes);
    }
    
    private void deactivateBlacklistForTypes(String blacklistName, String[] deactivateForBlacklistTypes) {
    	if (deactivateForBlacklistTypes == null) return;
    	
        for (int blTypes=0; blTypes < deactivateForBlacklistTypes.length; blTypes++) {
            listManager.removeListFromListslist(deactivateForBlacklistTypes[blTypes] + BLACKLISTS, blacklistName);
        } 
    }        
    
    private HashSet getSupportedBlacklistTypeSet() {
        HashSet supportedTypesSet = new HashSet(Arrays.asList(getSupportedBlacklistTypeArray()));
        return supportedTypesSet;
    }
    
    private void checkForKnownBlacklistTypes(String[] types) throws AxisFault {
    	if (types == null) return;
    	
    	// get kown blacklist types
    	HashSet supportedTypesSet = getSupportedBlacklistTypeSet();
    	
    	// check if we know all types stored in the array
        for (int i=0; i < types.length; i++) {
            if (!supportedTypesSet.contains(types[i]))
            	throw new AxisFault("Unknown blaclist type '" + types[i] + "' at position " + i);
        }
    }
    
}
