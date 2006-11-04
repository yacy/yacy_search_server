//BookmarkService.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file was contributed by Martin Thelian
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

import java.util.HashMap;
import java.util.HashSet;

import org.apache.axis.AxisFault;
import org.w3c.dom.Document;

import de.anomic.data.bookmarksDB;
import de.anomic.index.indexURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsRecord;

public class BookmarkService extends AbstractService {
	/* =====================================================================
	 * Used XML Templates
	 * ===================================================================== */
    private static final String TEMPLATE_BOOKMARK_LIST_XML = "xml/bookmarks/posts/get.xml";
    private static final String TEMPLATE_BOOKMARK_TAGS_XML = "xml/bookmarks/tags/get.xml"; 
	
	/**
	 * Converts an array of tags into a HashSet
	 * @param tagArray the array of tags
	 * @return the HashSet
	 */
	private HashSet stringArrayToHashSet(String[] tagArray) {
		HashSet tagSet = new HashSet();
		if (tagArray == null) return tagSet;
		
		for (int i=0; i < tagArray.length; i++) {
			String nextTag = tagArray[i].trim();
			if (nextTag.length() > 0) tagSet.add(nextTag);
		}
		
		return tagSet;
	}
	
	/**
	 * To publish a YaCy news message that a new bookmark was added.
	 * This is only done for public bookmarks
	 * @param url the url of the bookmark
	 * @param title bookmark title
	 * @param description bookmark description
	 * @param tags array of tags
	 */
	private void publisNewBookmarkNews(String url, String title, String description, String[] tags) {
        if (title == null) title = "";
        if (description == null) description = "";
        if (tags == null || tags.length == 0) tags = new String[]{"unsorted"};				
		
        // convert tag array into hashset
        HashSet tagSet = stringArrayToHashSet(tags);	        
        
        // create a news message
        HashMap map = new HashMap();
        map.put("url", url.replace(',', '|'));
        map.put("title", title.replace(',', ' '));
        map.put("description", description.replace(',', ' '));
        map.put("tags", tagSet.toString().replace(',', ' '));
        yacyCore.newsPool.publishMyNews(new yacyNewsRecord("bkmrkadd", map));		
	}
	
	/**
	 * Sets the properties of a {@link bookmarksDB.Bookmark} object
	 * @param isEdit specifies if we are in edit mode or would like to create a new bookmark
	 * @param bookmark the {@link bookmarksDB.Bookmark} object
	 * 
	 * @param isPublic specifies if the bookmark is public
	 * @param url the url of the bookmark
	 * @param title bookmark title
	 * @param description bookmark description
	 * @param tags array of tags
	 */
	private void setBookmarkProperties(boolean isEdit, bookmarksDB.Bookmark bookmark, String url, String title, String description, Boolean isPublic, String[] tags) {
		
		if (!isEdit) {
			if (url == null || url.length()==0) throw new IllegalArgumentException("The url must not be null or empty");
			if (title == null) title = "";
			if (description == null) description = "";
			if (tags == null || tags.length == 0) tags = new String[]{"unsorted"};
			if (isPublic == null) isPublic = Boolean.FALSE;
		}
		
        // convert tag array into hashset
		HashSet tagSet = null;
        if (tags != null) tagSet = stringArrayToHashSet(tags);		
		
        // set properties
        if (url != null) bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_URL, url);
        if (title != null) bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_TITLE, title);
        if (description != null)bookmark.setProperty(bookmarksDB.Bookmark.BOOKMARK_DESCRIPTION, description);
        if (isPublic != null) bookmark.setPublic(isPublic.booleanValue());        
        if (tags != null) bookmark.setTags(tagSet, true);        	
	}
	
	/**
	 * Function to add a new bookmark to the yacy bookmark DB.
	 * 
	 * @param isPublic specifies if the bookmark is public
	 * @param url the url of the bookmark
	 * @param title bookmark title
	 * @param description bookmark description
	 * @param tags array of tags
	 * 
	 * @return the url hash of the newly created bookmark
	 * 
	 * @throws AxisFault if authentication failed
	 */
	public String addBookmark(
			String url,
			String title,
			String description,
			String[] tags,
			Boolean isPublic
	) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);        
        if (url == null || url.length()==0) throw new IllegalArgumentException("The url must not be null or empty");
        
        // create new bookmark object
        bookmarksDB.Bookmark bookmark = ((plasmaSwitchboard)this.switchboard).bookmarksDB.createBookmark(url);
        
        // set bookmark properties
        if(bookmark != null){
        	this.setBookmarkProperties(false,bookmark,url,title,description,isPublic,tags);	
            if (isPublic != null && isPublic.booleanValue()) {
                // create a news message
                publisNewBookmarkNews(url,title,description,tags);
            } 
            ((plasmaSwitchboard)this.switchboard).bookmarksDB.saveBookmark(bookmark);
        } else {
        	throw new AxisFault("Unable to create bookmark. Unknown reason.");
        }        
        
        return bookmark.getUrlHash();
	}
	
	/**
	 * Function to delete a bookmark from the yacy bookmark db
	 * 
	 * @param urlHash the url hash to identify the bookmark
	 * 
	 * @throws AxisFault if authentication failed
	 */
	public void deleteBookmarkByHash(String urlHash) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);        
        if (urlHash == null || urlHash.length()==0) throw new IllegalArgumentException("The url hash must not be null or empty");
        
        // delete bookmark
        ((plasmaSwitchboard)this.switchboard).bookmarksDB.removeBookmark(urlHash);
	}
	
	public void deleteBookmark(String url) throws AxisFault {
		if (url == null || url.length()==0) throw new IllegalArgumentException("The url must not be null or empty");
		
		// generating the url hash
		String hash = indexURL.urlHash(url);
		
		// delete url
		this.deleteBookmarkByHash(hash);
	}
	
	/**
	 * Function to change the properties of a bookmark stored in the YaCy Bookmark DB
	 * 
	 * @param urlHash the url hash to identify the bookmark
	 * @param isPublic specifies if the bookmark is public
	 * @param url the changed url of the bookmark
	 * @param title the changed bookmark title
	 * @param description the changed bookmark description
	 * @param tags the changed array of tags
	 * 
	 * @return the url hash of the changed bookmark (this will be different to the urlHash input parameter if the bookmark url was changed
	 * 
	 * @throws AxisFault if authentication failed
	 */
	public String editBookmark(
			String urlHash,
			String url,
			String title,
			String description,
			String[] tags,
			Boolean isPublic
	) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);		
        
        if (urlHash == null || urlHash.length()==0) throw new IllegalArgumentException("The url hash must not be null or empty");
        
        // getting the bookmark
        bookmarksDB.Bookmark bookmark = ((plasmaSwitchboard)this.switchboard).bookmarksDB.getBookmark(urlHash);
        if (bookmark == null) throw new AxisFault("Bookmark with hash " + urlHash + " could not be found");
        
        // edit values
        setBookmarkProperties(true,bookmark,url,title,description,isPublic,tags); 
        
        // return the url has (may have been changed)
        return bookmark.getUrlHash();
	}
	
	/**
	 * To rename a bookmark tag
	 * @param oldTagName the old tag name
	 * @param newTagName the new name
	 * @throws AxisFault if authentication failed
	 */
	public void renameTag(String oldTagName, String newTagName) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);
        
        if (oldTagName == null || oldTagName.length()==0) throw new IllegalArgumentException("The old tag name not be null or empty");
        if (newTagName == null || newTagName.length()==0) throw new IllegalArgumentException("The nwe tag name not be null or empty");
        
        boolean success = ((plasmaSwitchboard)this.switchboard).bookmarksDB.renameTag(oldTagName,newTagName);
        if (!success) throw new AxisFault("Unable to rename tag. Unknown reason.");
	}
	
	/**
	 * Returns the list of bookmarks stored in the bookmark db
	 * @param tag the tag name for which the corresponding bookmarks should be fetched
	 * @param date the bookmark date
	 * 
	 * @return a XML document of the following format:
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
	 * &lt;posts&gt;
	 *   &lt;post description="YaCy Bookmarks Test" extended="YaCy Bookmarks junit test" hash="c294613d42343009949c0369bc56f722" href="http://www.yacy.de/testurl2" tag="bookmarks testing yacy" time="2006-11-04T14:33:01Z"/&gt;
	 * &lt;/posts&gt;
	 * <pre>
	 * 
	 * @throws AxisFault if authentication failed
	 * @throws Exception if xml generation failed
	 */
	public Document getBookmarkList(String tag, String date) throws Exception {
		
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);          	
        
        // generating the template containing the network status information
        serverObjects args = new serverObjects();
        if (tag != null) args.put("tag",tag);
        if (date != null) args.put("date",date);
        
        byte[] result = writeTemplate(TEMPLATE_BOOKMARK_LIST_XML, args);
        
        // sending back the result to the client
        return this.convertContentToXML(result);    		
	}
	
	/**
	 * Returns the list of bookmark tags for which bookmarks exists in the YaCy bookmark db
	 * 
	 * @return a XML document of the following format:
	 * <pre>
	 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
	 * &lt;tags&gt;
	 *   &lt;tag count="1" tag="bookmarks"/&gt;
	 *   &lt;tag count="1" tag="testing"/&gt;
	 *   &lt;tag count="1" tag="yacy"/&gt;
	 * &lt;/tags&gt;
	 * <pre>
	 * 
	 * @throws AxisFault if authentication failed
	 * @throws Exception if xml generation failed
	 */	
	public Document getBookmarkTagList() throws Exception {
		
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);          	
        
        // generate the xml document
        byte[] result = writeTemplate(TEMPLATE_BOOKMARK_TAGS_XML, new serverObjects());
        
        // sending back the result to the client
        return this.convertContentToXML(result);    
	}
}
