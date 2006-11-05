package de.anomic.soap.services;

import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Date;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.rpc.ServiceException;

import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.attachments.PlainTextDataSource;
import org.apache.axis.client.Stub;
import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.bookmarks.BookmarkService;
import yacy.soap.bookmarks.BookmarkServiceServiceLocator;
import de.anomic.data.bookmarksDB;
import de.anomic.index.indexURL;
import de.anomic.net.URL;

public class BookmarkServiceTest extends AbstractServiceTest {

	protected void createServiceClass() throws ServiceException {
		// construct Soap object
		BookmarkServiceServiceLocator locator = new BookmarkServiceServiceLocator();
		locator.setbookmarksEndpointAddress(getBaseServiceURL() + "bookmarks");
		
		service = locator.getbookmarks();	
	}
	
	public void testBookmarks() throws Exception {
		BookmarkService bm = ((BookmarkService)service);
		
		String testURL1 = "http://www.yacy.de/testurl1";
		String testURL2 = "http://www.yacy.de/testurl2";
		
		// create new bookmark
		String urlHash = bm.addBookmark(testURL1,"YaCy Bookmarks Test","YaCy Bookmarks junit test",new String[]{"yacy","bookmarks","testing"},false);
		
		// change bookmark
		urlHash = bm.editBookmark(urlHash,testURL2,null,null,null,false);

		// get bookmark listing
		Document xml = bm.getBookmarkList("testing",bookmarksDB.dateToiso8601(new Date(System.currentTimeMillis())));
		System.out.println(XMLUtils.DocumentToString(xml));		
		
		// get tag list
		xml = bm.getBookmarkTagList();
		System.out.println(XMLUtils.DocumentToString(xml));
		
		// rename tag
		bm.renameTag("testing","tested");
		
		// delete tag
		bm.deleteBookmarkByHash(urlHash);
	}
	
	public void testImportHtmlBookmarklist() throws RemoteException {
		BookmarkService bm = ((BookmarkService)service);		
		String[] hashs = new String[5];		
		
		// generate the html file
		StringBuffer xmlStr = new StringBuffer();
		xmlStr.append("<html><body>");		
		for (int i=0; i < hashs.length; i++) {
			String url = "/testxmlimport" + i;
			String title = "YaCy Bookmark XML Import " + i;
			String hash = indexURL.urlHash("http://www.yacy.de"+ url);
			
			xmlStr.append("\t<a href=\"").append(url).append("\">").append(title).append("</a>\r\n");
			
			hashs[i] = hash;			
		}		
		xmlStr.append("</body></html>");
				
		// create datasource to hold the attachment content
        DataSource data =  new PlainTextDataSource("bookmarks.html",xmlStr.toString());
        DataHandler attachmentFile = new DataHandler(data);   
        
        // creating attachment part
        AttachmentPart part = new AttachmentPart();
        part.setDataHandler(attachmentFile);

        // setting the attachment format that should be used
        ((Stub)service)._setProperty(org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT,org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT_MIME);
        ((Stub)service).addAttachment(part);      			
		
		// import xml
		int importCount = bm.importHtmlBookmarkFile("http://www.yacy.de/",new String[]{"yacy","bookmarks","htmlimport"},false);
		assertEquals(hashs.length,importCount);

		// query imported documents
		Document xml = bm.getBookmarkList("htmlimport",null);
		System.out.println(XMLUtils.DocumentToString(xml));	
		
		// delete imported URLS
		bm.deleteBookmarksByHash(hashs);		
	}
	
	public void testImportXML() throws MalformedURLException, RemoteException {		
		BookmarkService bm = ((BookmarkService)service);
		
		String dateString = bookmarksDB.dateToiso8601(new Date(System.currentTimeMillis()));
		String[] hashs = new String[5];
		
		// generate xml document to import
		StringBuffer xmlStr = new StringBuffer();
		xmlStr.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n")
		   .append("<posts>\r\n");
		
		for (int i=0; i < hashs.length; i++) {
			URL url = new URL("http://www.yacy.de/testxmlimport" + i);
			String title = "YaCy Bookmark XML Import " + i;
			String description = "YaCy Bookmarkx XML Import junit test with url " + i;
			String hash = indexURL.urlHash(url);
			String tags = "yacy bookmarks xmlimport";
			
			xmlStr.append("\t<post description=\"").append(title).append("\"  extended=\"")
			   .append(description).append("\" hash=\"").append(hash).append("\" href=\"") 
			   .append(url).append("\" tag=\"").append(tags).append("\" time=\"").append(dateString).append("\"/>\r\n");
			
			hashs[i] = hash;
		}
		
		xmlStr.append("</posts>");
		
		// create datasource to hold the attachment content
        DataSource data =  new PlainTextDataSource("bookmarks.xml",xmlStr.toString());
        DataHandler attachmentFile = new DataHandler(data);   
        
        // creating attachment part
        AttachmentPart part = new AttachmentPart();
        part.setDataHandler(attachmentFile);

        // setting the attachment format that should be used
        ((Stub)service)._setProperty(org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT,org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT_MIME);
        ((Stub)service).addAttachment(part);      			
		
		// import xml
		int importCount = bm.importBookmarkXML(false);
		assertEquals(hashs.length,importCount);

		// query imported documents
		Document xml = bm.getBookmarkList("xmlimport",dateString);
		System.out.println(XMLUtils.DocumentToString(xml));	
		
		// delete imported URLS
		bm.deleteBookmarksByHash(hashs);
	}

}
