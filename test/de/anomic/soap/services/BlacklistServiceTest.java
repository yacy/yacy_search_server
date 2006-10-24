package de.anomic.soap.services;

import java.io.IOException;
import java.rmi.RemoteException;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.rpc.ServiceException;

import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.attachments.Attachments;
import org.apache.axis.attachments.PlainTextDataSource;
import org.apache.axis.client.Call;
import org.apache.axis.client.Stub;
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
	
	public void testBacklistImport() throws IOException {
		BlacklistService bl = ((BlacklistService)service);
		
		// create datasource to hold the attachment content
        DataSource data =  new PlainTextDataSource("import.txt","www.yacy.net/.*\r\n" +
        														"www.yacy-websuche.de/.*");
        DataHandler attachmentFile = new DataHandler(data);   
        
        // creating attachment part
        AttachmentPart part = new AttachmentPart();
        part.setDataHandler(attachmentFile);
        part.setContentType("text/plain");

        // setting the attachment format that should be used
        ((Stub)service)._setProperty(org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT,org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT_MIME);
        ((Stub)service).addAttachment(part);               
        
        // import it
        String blacklistName = "junit_test_" + System.currentTimeMillis();
        bl.importBlacklist(blacklistName);
        
        // clear attachment
        ((Stub)service).clearAttachments();         
        
        // delete blacklist
        bl.deleteBlacklist(blacklistName);
	}

}
