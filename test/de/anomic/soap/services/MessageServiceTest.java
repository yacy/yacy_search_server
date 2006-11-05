package de.anomic.soap.services;

import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.messages.MessageService;
import yacy.soap.messages.MessageServiceServiceLocator;



public class MessageServiceTest extends AbstractServiceTest {

	protected void createServiceClass() throws ServiceException {
		// construct Soap object
		MessageServiceServiceLocator locator = new MessageServiceServiceLocator();
		locator.setmessagesEndpointAddress(getBaseServiceURL() + "messages");
		
		service = locator.getmessages(); 	
	}

	public void testGetMessageIDs() throws RemoteException {
		MessageService ms = ((MessageService)service);
		String[] IDs = ms.getMessageIDs();
		
		StringBuffer idList = new StringBuffer();
		for (int i=0; i < IDs.length; i++) {
			if (i > 0) idList.append(", ");
			idList.append(IDs[i]);
		}
		
		System.out.println(idList);				
	}
	
	public void testGetMessageHeaderList() throws RemoteException {
		MessageService ms = ((MessageService)service);
		Document xml = ms.getMessageHeaderList();
		System.out.println(XMLUtils.DocumentToString(xml));				
	}	
	
	public void testMessage() throws RemoteException {
		MessageService ms = ((MessageService)service);
		
		// get message IDs
		String[] IDs = ms.getMessageIDs();		
		
		if (IDs != null && IDs.length > 0) {
			Document xml = ms.getMessage(IDs[0]);
			System.out.println(XMLUtils.DocumentToString(xml));			
		}
	}		
	
	public void testGetMessageSendPermission() throws RemoteException {
		MessageService ms = ((MessageService)service);
		
		Document xml = ms.getMessageSendPermission("mseSVGrNKKnw");
		System.out.println(XMLUtils.DocumentToString(xml));	
	}
}
