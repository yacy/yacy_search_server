package de.anomic.soap.services;

import java.io.IOException;
import java.util.Date;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.rpc.ServiceException;
import javax.xml.soap.SOAPException;

import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.attachments.PlainTextDataSource;
import org.apache.axis.client.Stub;
import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.share.ShareService;
import yacy.soap.share.ShareServiceServiceLocator;
import de.anomic.server.serverFileUtils;

public class ShareServiceTest extends AbstractServiceTest {

	protected void createServiceClass() throws ServiceException {
		// construct Soap object
		ShareServiceServiceLocator locator = new ShareServiceServiceLocator();
		locator.setshareEndpointAddress(getBaseServiceURL() + "share");		
		service = locator.getshare();	
	}
	
	public void testCreateDeleteDir() throws SOAPException, IOException {
		String newDirName = "junit_test_" + System.currentTimeMillis();
		String newFileName = "import.txt";
		
		/* ===================================================================
		 * Create directory
		 * =================================================================== */
		System.out.println("Creating new directory ...");
		((ShareService)service).createDirectory("/",newDirName);
		
		/* ===================================================================
		 * Upload file
		 * =================================================================== */
		System.out.println("Uploading test file ...");
		
		// create datasource to hold the attachment content
		String testText = "Test text of the test file";
        DataSource data =  new PlainTextDataSource(newFileName,testText);
        DataHandler attachmentFile = new DataHandler(data);   
        
        // creating attachment part
        AttachmentPart part = new AttachmentPart();
        part.setDataHandler(attachmentFile);
        part.setContentType("text/plain");
        part.setContentId(newFileName);

        // setting the attachment format that should be used
        ((Stub)service)._setProperty(org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT,org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT_MIME);
        ((Stub)service).addAttachment(part);      		
        ((ShareService)service).uploadFile(newDirName,true,"jUnit Testupload at " + new Date());
		
        // clear attachment
        ((Stub)service).clearAttachments();        
		
		/* ===================================================================
		 * Download file
		 * =================================================================== */ 
        System.out.println("Downloading test file ...");
        
        // execute service call
        String md5 = ((ShareService)service).getFile(newDirName,newFileName);
        
        // get received attachments
        Object[] attachments = ((Stub)service).getAttachments();
        
        assertTrue(attachments.length == 1);
        assertTrue(attachments[0] instanceof AttachmentPart);
        
        // get datahandler
        DataHandler dh = ((AttachmentPart)attachments[0]).getDataHandler();
        
        // cread content
        byte[] content = serverFileUtils.read(dh.getInputStream());
        assertTrue(content.length > 0);
        
        // convert it to string
        String contentString = new String(content,"UTF-8");
        assertEquals(testText,contentString);        
        
		/* ===================================================================
		 * Change file comment
		 * =================================================================== */
        System.out.println("Changing file comment ...");
        ((ShareService)service).changeComment(newDirName,newFileName,"New comment on this file",true);
        
		/* ===================================================================
		 * Get dirlist
		 * =================================================================== */   
        System.out.println("Get dirlist ... ");
		Document xml =((ShareService)service).getDirList(newDirName);
		System.out.println(XMLUtils.DocumentToString(xml));
		
		/* ===================================================================
		 * Delete directory
		 * =================================================================== */
		System.out.println("Deleting directory and testfile ... ");
		((ShareService)service).delete("/",newDirName);
	}

}
