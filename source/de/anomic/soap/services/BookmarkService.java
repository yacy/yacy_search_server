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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;

import javax.activation.DataHandler;
import javax.xml.soap.SOAPException;

import org.apache.axis.AxisFault;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.attachments.Attachments;
import org.w3c.dom.Document;

import de.anomic.data.bookmarksDB;
import de.anomic.plasma.plasmaURL;
import de.anomic.net.URL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.soap.AbstractService;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacyNewsPool;
import de.anomic.yacy.yacyNewsRecord;

public class BookmarkService extends AbstractService {
	/* =====================================================================
	 * Used XML Templates
	 * ===================================================================== */
    private static final String TEMPLATE_BOOKMARK_LIST_GET_XML = "xml/bookmarks/posts/get.xml";
    private static final String TEMPLATE_BOOKMARK_LIST_ALL_XML = "xml/bookmarks/posts/all.xml";
    private static final String TEMPLATE_BOOKMARK_TAGS_XML = "xml/bookmarks/tags/get.xml";
	
    /**
     * @return a handler to the YaCy Bookmark DB
     */
    private bookmarksDB getBookmarkDB() {
    	assert (this.switchboard != null) : "Switchboard object is null";
    	assert (this.switchboard instanceof plasmaSwitchboard) : "Incorrect switchboard object";
    	assert (((plasmaSwitchboard)this.switchboard).bookmarksDB != null) : "Bookmark DB is null";
    	
    	return ((plasmaSwitchboard)this.switchboard).bookmarksDB;
    }
    
    /**
     * @return returns the input stream of a soap attachment
     * @throws AxisFault if no attachment was found or attachments are not supported
     * @throws SOAPException if attachment decoding didn't work
     * @throws IOException on attachment read errors
     */
    private InputStream getAttachmentInputstream() throws AxisFault, SOAPException, IOException {
		// get the current message context
        MessageContext msgContext = MessageContext.getCurrentContext();

        // getting the request message
        Message reqMsg = msgContext.getRequestMessage();		
		
        // getting the attachment implementation
        Attachments messageAttachments = reqMsg.getAttachmentsImpl();
        if (messageAttachments == null) {
            throw new AxisFault("Attachments not supported");
        }		
        
        int attachmentCount= messageAttachments.getAttachmentCount();
        if (attachmentCount == 0) 
            throw new AxisFault("No attachment found");
        else if (attachmentCount != 1)
            throw new AxisFault("Too many attachments as expected.");     
        
        // getting the attachments
        AttachmentPart[] attachments = (AttachmentPart[])messageAttachments.getAttachments().toArray(new AttachmentPart[attachmentCount]);
    	
        // getting the content of the attachment        
        DataHandler dh = attachments[0].getDataHandler();	
        
        // return the input stream
        return dh.getInputStream();
    }
    
	/**
	 * Converts an array of tags into a HashSet
	 * @param tagArray the array of tags
	 * @return the HashSet
	 */
	private HashSet tagArrayToHashSet(String[] tagArray) {
		HashSet tagSet = new HashSet();
		if (tagArray == null) return tagSet;
		
		for (int i=0; i < tagArray.length; i++) {
			String nextTag = tagArray[i].trim();
			if (nextTag.length() > 0) tagSet.add(nextTag);
		}
		
		return tagSet;
	}
	
	/**
	 * Converts the tag array into a space separated list
	 * @param tagArray the tag array
	 * @return space separated list of tags
	 */
	private String tagArrayToSepString(String[] tagArray, String sep) {
		StringBuffer buffer = new StringBuffer();
		
		for (int i=0; i < tagArray.length; i++) {
			String nextTag = tagArray[i].trim();
			if (nextTag.length() > 0) {
				if (i > 0) buffer.append(sep);
				buffer.append(nextTag);
			}
		}
		
		return buffer.toString();		
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
        String tagString = tagArrayToSepString(tags," ");	        
        
        // create a news message
        HashMap map = new HashMap();
        map.put("url", url.replace(',', '|'));
        map.put("title", title.replace(',', ' '));
        map.put("description", description.replace(',', ' '));
        map.put("tags", tagString);
        yacyCore.newsPool.publishMyNews(yacyNewsRecord.newRecord(yacyNewsPool.CATEGORY_BOOKMARK_ADD, map));		
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
        if (tags != null) tagSet = tagArrayToHashSet(tags);		
		
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
        bookmarksDB.Bookmark bookmark = getBookmarkDB().createBookmark(url, "admin"); //FIXME: "admin" can be user.getUserName() for users with bookmarkrights 
        
        // set bookmark properties
        if(bookmark != null){
        	this.setBookmarkProperties(false,bookmark,url,title,description,isPublic,tags);	
            if (isPublic != null && isPublic.booleanValue()) {
                // create a news message
                publisNewBookmarkNews(url,title,description,tags);
            } 
            getBookmarkDB().saveBookmark(bookmark);
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
        getBookmarkDB().removeBookmark(urlHash);
	}
	
	public void deleteBookmarksByHash(String[] urlHashs) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);        
        if (urlHashs == null || urlHashs.length==0) throw new IllegalArgumentException("The url hash array must not be null or empty");
        
        for (int i=0; i < urlHashs.length; i++) {
        	String urlHash = urlHashs[i];
        	 if (urlHash == null || urlHash.length()==0) throw new IllegalArgumentException("The url hash at position " + i + " is null or empty.");
        	
            // delete bookmark
        	 getBookmarkDB().removeBookmark(urlHash);        	
        }
	}
	
	public void deleteBookmark(String url) throws AxisFault {
		if (url == null || url.length()==0) throw new IllegalArgumentException("The url must not be null or empty");
		
		// generating the url hash
		String hash = plasmaURL.urlHash(url);
		
		// delete url
		this.deleteBookmarkByHash(hash);
	}
	
	public void deleteBookmarks(String[] urls) throws AxisFault {
		if (urls == null || urls.length==0) throw new IllegalArgumentException("The url array must not be null or empty");
		
		String[] hashs = new String[urls.length];
		for (int i=0; i < urls.length; i++) {
			String url = urls[i];
			if (url == null || url.length()==0) throw new IllegalArgumentException("The url at position " + i + " is null or empty");
			
			// generating the url hash
			hashs[i] = plasmaURL.urlHash(url);
		}
		
		// delete url
		this.deleteBookmarksByHash(hashs);	
	}
	
	
	public String bookmarkIsKnown(String url) throws AxisFault {
		String urlHash = plasmaURL.urlHash(url);
		return this.bookmarkIsKnownByHash(urlHash);
	}
	
	public String bookmarkIsKnownByHash(String urlHash) throws AxisFault {
        // extracting the message context		
        extractMessageContext(AUTHENTICATION_NEEDED);        
        if (urlHash == null || urlHash.length()==0) throw new IllegalArgumentException("The url-hash must not be null or empty");
        
        // get the bookmark object
        bookmarksDB.Bookmark bookmark = getBookmarkDB().getBookmark(urlHash);  
        
        // set bookmark properties
        if(bookmark == null) return null;        
        return bookmark.getTagsString();
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
        bookmarksDB.Bookmark bookmark = getBookmarkDB().getBookmark(urlHash);
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
        
        boolean success = getBookmarkDB().renameTag(oldTagName,newTagName);
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
        args.put("extendedXML", "");
        if (tag != null) args.put("tag",tag);
        if (date != null) args.put("date",date);
        
        byte[] result = this.serverContext.writeTemplate((date != null)?TEMPLATE_BOOKMARK_LIST_GET_XML:TEMPLATE_BOOKMARK_LIST_ALL_XML, args, this.requestHeader);
        
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
        byte[] result = this.serverContext.writeTemplate(TEMPLATE_BOOKMARK_TAGS_XML, new serverObjects(), this.requestHeader);
        
        // sending back the result to the client
        return this.convertContentToXML(result);    
	}
	
	/**
	 * Function to import YaCy from XML (transfered via SOAP Attachment).<br>
	 * This function expects a xml document in the same format as returned by 
	 * function {@link #getBookmarkList(String, String)}.
	 * @param isPublic specifies if the imported bookmarks are public or local
	 * @return the amount of imported bookmarks
	 * 
	 * @throws SOAPException if there is no data in the attachment
	 * @throws IOException if authentication failed or the attachment could not be read 
	 */
	public int importBookmarkXML(boolean isPublic) throws SOAPException, IOException {
		
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);    
        
        // getting the attachment input stream
        InputStream xmlIn = getAttachmentInputstream();
        
        // import bookmarks
        int importCount = getBookmarkDB().importFromXML(xmlIn, isPublic);
        
        // return amount of imported bookmarks
        return importCount;
	}
	
	/**
	 * Function to import YaCy from a html document (transfered via SOAP Attachment).<br>
	 * This function expects a well formed html document.
	 * 
	 * @param baseURL the base url. This is needed to generate absolut URLs from relative URLs
	 * @param tags a list of bookmarks tags that should be assigned to the new bookmarks
	 * @param isPublic specifies if the imported bookmarks are public or local
	 * @return the amount of imported bookmarks
	 * 
	 * @throws SOAPException if there is no data in the attachment
	 * @throws IOException if authentication failed or the attachment could not be read 
	 */
	public int importHtmlBookmarkFile(String baseURL, String[] tags, boolean isPublic) throws SOAPException, IOException {
		if (tags == null || tags.length == 0) tags = new String[]{"unsorted"};
				
        // extracting the message context
        extractMessageContext(AUTHENTICATION_NEEDED);    		
		
        // getting the attachment input stream
        InputStream htmlIn = getAttachmentInputstream();
        InputStreamReader htmlReader = new InputStreamReader(htmlIn,"UTF-8");
        
        // import bookmarks
        URL theBaseURL = new URL(baseURL);
        String tagList = tagArrayToSepString(tags,",");
        int importCount = getBookmarkDB().importFromBookmarks(theBaseURL,htmlReader, tagList,isPublic);
        
        // return amount of imported bookmarks
		return importCount;
	}
}
