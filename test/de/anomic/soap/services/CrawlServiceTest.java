package de.anomic.soap.services;

import java.rmi.RemoteException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.rpc.ServiceException;

import org.apache.axis.AxisFault;
import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.crawl.CrawlService;
import yacy.soap.crawl.CrawlServiceServiceLocator;

public class CrawlServiceTest extends AbstractServiceTest {

	protected void createServiceClass() throws ServiceException {
		// construct Soap object
		CrawlServiceServiceLocator locator = new CrawlServiceServiceLocator();
		locator.setcrawlEndpointAddress(getBaseServiceURL() + "crawl");
		
		service = locator.getcrawl();	
	}

	public void testGetCrawlPauseResumeState() throws RemoteException  {
		Document xml = ((CrawlService)service).getCrawlPauseResumeState();
		System.out.println(XMLUtils.DocumentToString(xml));
	}
}
