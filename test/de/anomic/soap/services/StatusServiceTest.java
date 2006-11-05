package de.anomic.soap.services;

import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.status.StatusService;
import yacy.soap.status.StatusServiceServiceLocator;

public class StatusServiceTest extends AbstractServiceTest {

	protected void createServiceClass() throws ServiceException {
		// construct Soap object
		StatusServiceServiceLocator locator = new StatusServiceServiceLocator();
		locator.setstatusEndpointAddress(getBaseServiceURL() + "status");
		
		service = locator.getstatus();		
	}	
	
	public void testNetwork() throws RemoteException {
		Document xml = ((StatusService)service).network();
		System.out.println(XMLUtils.DocumentToString(xml));	
	}
	
	public void testGetQueueStatus() throws RemoteException {
		Document xml = ((StatusService)service).getQueueStatus(10,10,10,10);
		System.out.println(XMLUtils.DocumentToString(xml));
	}
	
	public void testStatus() throws RemoteException {
		Document xml = ((StatusService)service).getStatus();
		System.out.println(XMLUtils.DocumentToString(xml));
	}
}
