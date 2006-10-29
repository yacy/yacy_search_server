package de.anomic.soap.services;

import java.rmi.RemoteException;
import java.util.HashMap;

import javax.xml.rpc.ServiceException;
import javax.xml.transform.TransformerException;

import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;

import yacy.soap.admin.AdminService;
import yacy.soap.admin.AdminServiceServiceLocator;

import com.sun.org.apache.xpath.internal.XPathAPI;

public class AdminServiceTest extends AbstractServiceTest {

	protected void createServiceClass() throws ServiceException {
		// construct Soap object
		AdminServiceServiceLocator locator = new AdminServiceServiceLocator();
		locator.setadminEndpointAddress(getBaseServiceURL() + "admin");
		
		service = locator.getadmin();	
	}

	private HashMap getMessageForwardingProperties(Document xml) throws DOMException, TransformerException {
		HashMap result = new HashMap();
		
		result.put("msgForwardingEnabled",Boolean.valueOf(XPathAPI.selectSingleNode(xml,"/msgForwarding/msgForwardingEnabled").getTextContent()));
		result.put("msgForwardingCmd",XPathAPI.selectSingleNode(xml,"/msgForwarding/msgForwardingCmd").getTextContent());
		result.put("msgForwardingTo",XPathAPI.selectSingleNode(xml,"/msgForwarding/msgForwardingTo").getTextContent());			
		
		return result;
	}
	
	public void testMessageForwarding() throws RemoteException, TransformerException {
		// backup old values
		HashMap oldValues = getMessageForwardingProperties(((AdminService)service).getMessageForwarding());
		
		// set new values
		Boolean msgEnabled = Boolean.TRUE;
		String msgCmd = "/usr/sbin/sendmail";
		String msgTo = "yacy@localhost";	
		((AdminService)service).setMessageForwarding(msgEnabled.booleanValue(),msgCmd,msgTo);
		
		// query configured properties
		Document xml = ((AdminService)service).getMessageForwarding();
		
		// check if values are equal
		assertEquals(msgEnabled,Boolean.valueOf(XPathAPI.selectSingleNode(xml,"/msgForwarding/msgForwardingEnabled").getTextContent()));
		assertEquals(msgCmd,XPathAPI.selectSingleNode(xml,"/msgForwarding/msgForwardingCmd").getTextContent());
		assertEquals(msgTo,XPathAPI.selectSingleNode(xml,"/msgForwarding/msgForwardingTo").getTextContent());		

		// print it out
		System.out.println(XMLUtils.DocumentToString(xml));
		
		// set back to old values
		((AdminService)service).setMessageForwarding(
				((Boolean)oldValues.get("msgForwardingEnabled")).booleanValue(),
				(String)oldValues.get("msgForwardingCmd"),
				(String)oldValues.get("msgForwardingTo")
		);
	}
}
