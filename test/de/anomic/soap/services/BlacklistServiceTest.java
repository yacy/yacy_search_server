package de.anomic.soap.services;

import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.blacklist.BlacklistService;
import yacy.soap.blacklist.BlacklistServiceServiceLocator;

public class BlacklistServiceTest extends AbstractServiceTest {

	protected void createServiceClass() throws ServiceException {
		// construct Soap object
		BlacklistServiceServiceLocator locator = new BlacklistServiceServiceLocator();
		locator.setblacklistEndpointAddress(getBaseServiceURL() + "blacklist");
		
		service = locator.getblacklist();	
	}
	
	public void testGetBlacklistList() throws RemoteException {
		Document xml = ((BlacklistService)service).getBlacklistList();
		System.out.println(XMLUtils.DocumentToString(xml));
	}
	
	public void testBlacklist() throws RemoteException {
		BlacklistService bl = ((BlacklistService)service);
		
		// create new blacklist
		String blacklistName = "junit_test_" + System.currentTimeMillis();
		bl.createBlacklist(blacklistName,false,null);
		
		// share blacklist
		bl.shareBlacklist(blacklistName);
		
		// getting supported blacklist Types
		String[] blTypes = bl.getBlacklistTypes();
		
		// activate blacklist
		bl.activateBlacklist(blacklistName,blTypes);
		
		// add blacklist item
		String item = "http://www.yacy.net";
		bl.addBlacklistItem(blacklistName,item);
		
		// getting the blacklist list
		Document xml = ((BlacklistService)service).getBlacklistList();
		System.out.println(XMLUtils.DocumentToString(xml));		
		
		// remove blacklist item
		bl.removeBlacklistItem(blacklistName,item);
		
		// unshare
		bl.unshareBlacklist(blacklistName);
		
		// deactivate for proxy and dht
		bl.deactivateBlacklist(blacklistName,new String[]{"proxy","dht"});
		
		// delete blacklist
		bl.deleteBlacklist(blacklistName);
	}

}
