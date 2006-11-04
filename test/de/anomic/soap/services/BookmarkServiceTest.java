package de.anomic.soap.services;

import java.util.Date;

import javax.xml.rpc.ServiceException;

import org.apache.axis.utils.XMLUtils;
import org.w3c.dom.Document;

import yacy.soap.bookmarks.BookmarkService;
import yacy.soap.bookmarks.BookmarkServiceServiceLocator;
import de.anomic.data.bookmarksDB;

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

}
