//bookmarksDB.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file has been originally contributed by Alexander Schier
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
package de.anomic.data;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroObjects;
import de.anomic.kelondro.kelondroObjectsMapEntry;
import de.anomic.plasma.plasmaCondenser;
import de.anomic.server.serverDate;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class bookmarksDB {
	// ------------------------------------
	// Declaration of Class-Attributes
	// ------------------------------------

	final static int SORT_ALPHA = 1;
	final static int SORT_SIZE = 2;
	final static int SHOW_ALL = -1;
	
	// bookmarks
    kelondroObjects bookmarksTable;		// kelondroMap bookmarksTable;
    
    // tags
    kelondroMapObjects tagsTable;
    HashMap<String, Tag> tagCache;					
    
    // dates
    kelondroMapObjects datesTable;

    
	// ------------------------------------
	// bookmarksDB's class constructor
	// ------------------------------------

    public bookmarksDB(File bookmarksFile, File tagsFile, File datesFile, long preloadTime) {
        // bookmarks
        tagCache=new HashMap<String, Tag>();
        bookmarksFile.getParentFile().mkdirs();
        //this.bookmarksTable = new kelondroMap(kelondroDyn.open(bookmarksFile, bufferkb * 1024, preloadTime, 12, 256, '_', true, false));
        this.bookmarksTable = new kelondroObjects(new kelondroDyn(bookmarksFile, true, true, preloadTime, 12, 256, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 1000);

        // tags
        tagsFile.getParentFile().mkdirs();
        boolean tagsFileExisted = tagsFile.exists();
        this.tagsTable = new kelondroMapObjects(new kelondroDyn(tagsFile, true, true, preloadTime, 12, 256, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 500);
        if (!tagsFileExisted) rebuildTags();

        // dates
        boolean datesExisted = datesFile.exists();
        this.datesTable = new kelondroMapObjects(new kelondroDyn(datesFile, true, true, preloadTime, 20, 256, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 500);
        if (!datesExisted) rebuildDates();

    }

    // -----------------------------------------------------
	// bookmarksDB's functions for 'destructing' the class
	// ----------------------------------------------------- 
        
    public void close(){
        bookmarksTable.close();
        flushTagCache();
        tagsTable.close();
        datesTable.close();
    }
    
    // -------------------------------------
	// bookmarksDB's public helper functions
	// -------------------------------------    
 
    /**
     * returns an object of type String that contains a tagHash
     * @param tagName an object of type String with the name of the tag. 
     *        tagName is converted to lower case before hash is generated!
     */
    public static String tagHash(String tagName){
        return plasmaCondenser.word2hash(tagName.toLowerCase());
    }    
    public static String tagHash(String tagName, String user){
        return plasmaCondenser.word2hash(user+":"+tagName.toLowerCase());
    }
    
    public Iterator<String> getFolderList(boolean priv){
    
    	Set<String> folders = new TreeSet<String>();
    	String path = "";   	
    	Iterator<Tag> it = this.getTagIterator(priv);
    	Tag tag;
    	
    	while(it.hasNext()){
    		tag=it.next();
    		if (tag.getFriendlyName().startsWith("/")) {
    			path = tag.getFriendlyName();
    			path = cleanTagsString(path);                  
    			while(path.length() > 0){
    				folders.add(path);
    				path = path.replaceAll("(/.[^/]*$)", "");	// create missing folders in path
    			}       			
    		}
    	}
    	folders.add("\uffff");
    	return (Iterator<String>) folders.iterator();    	
    }
    
    public static String cleanTagsString(String tagsString){
    	
    	// get rid of heading, trailing and double commas since they are useless
        while (tagsString.startsWith(",")) {
            tagsString = tagsString.substring(1);
        }
        while (tagsString.endsWith(",")) {
            tagsString = tagsString.substring(0,tagsString.length() -1);
        }
        while(tagsString.contains(",,")){
            tagsString = tagsString.replaceAll(",,", ",");
        }
        // get rid of double and trailing slashes
        while(tagsString.endsWith("/")){
            tagsString = tagsString.substring(0, tagsString.length() -1);
        }
        while(tagsString.contains("/,")){
            tagsString = tagsString.replaceAll("/,", ",");
        }
        while(tagsString.contains("//")){
            tagsString = tagsString.replaceAll("//", "/");
        }
        // space characters following a comma are removed
        tagsString = tagsString.replaceAll(",\\s+", ","); 
    	
        return tagsString;
    }
 
    // -----------------------------------------------------------
	// bookmarksDB's functions for bookmarksTable / bookmarkCache
	// ----------------------------------------------------------- 
    
    public Bookmark createBookmark(String url, String user){
        if (url == null || url.length() == 0) return null;
        Bookmark bk = new Bookmark(url);
        bk.setOwner(user);
        return (bk.getUrlHash() == null || bk.toMap() == null) ? null : bk;
    }
    
    // returning the number of bookmarks
    public int bookmarksSize(){
        return bookmarksTable.size();
    }
    
    // adding a bookmark to the bookmarksDB
    public void saveBookmark(Bookmark bookmark){
    	try {
    		bookmarksTable.set(bookmark.getUrlHash(), bookmark);
        } catch (IOException e) {
        	// TODO Auto-generated catch block
        	e.printStackTrace();
        }
    }
    public String addBookmark(Bookmark bookmark){
        saveBookmark(bookmark);
        return bookmark.getUrlHash();
 
    }
    
    public Bookmark getBookmark(String urlHash){
        try {
            kelondroObjectsMapEntry map = (kelondroObjectsMapEntry)bookmarksTable.get(urlHash);
            if (map == null) return null;
            if (map instanceof Bookmark) return (Bookmark)map;
            return new Bookmark(map);
        } catch (IOException e) {
            return null;
        }
    }
    
    public boolean removeBookmark(String urlHash){
        Bookmark bookmark = getBookmark(urlHash);
        if(bookmark == null) return false; //does not exist
        Set<String> tags = bookmark.getTags();
        bookmarksDB.Tag tag=null;
        Iterator<String> it=tags.iterator();
        while(it.hasNext()){
            tag=getTag(tagHash(it.next()));
            if(tag!=null){
                tag.delete(urlHash);
                saveTag(tag);
            }
        }
        Bookmark b;
        try {
            b = getBookmark(urlHash);
            bookmarksTable.remove(urlHash);
        } catch (IOException e) {
            b = null;
        }
        return b != null;
    }
    
    public Iterator<Bookmark> bookmarkIterator(boolean up){
    	try {
            return new bookmarkIterator(up);
        } catch (IOException e) {
            return new HashSet<Bookmark>().iterator();
        }
    }
    
    public Iterator<String> getBookmarksIterator(boolean priv){
        TreeSet<String> set=new TreeSet<String>(new bookmarkComparator(true));
        Iterator<Bookmark> it=bookmarkIterator(true);
        Bookmark bm;
        while(it.hasNext()){
            bm=(Bookmark)it.next();
            if(priv || bm.getPublic()){
            	set.add(bm.getUrlHash());
            }
        }
        return set.iterator();
    }
    
    public Iterator<String> getBookmarksIterator(String tagName, boolean priv){
        TreeSet<String> set=new TreeSet<String>(new bookmarkComparator(true));
        String tagHash=tagHash(tagName);
        Tag tag=getTag(tagHash);
        Set<String> hashes=new HashSet<String>();
        if(tag != null){
            hashes=getTag(tagHash).getUrlHashes();
        }
        if(priv){
        	set.addAll(hashes);
        }else{
        	Iterator<String> it=hashes.iterator();
        	Bookmark bm;
        	while(it.hasNext()){
        		bm=getBookmark(it.next());
        		if(bm.getPublic()){
        			set.add(bm.getUrlHash());
        		}
        	}
        }
    	return set.iterator();
    }
    
    // -------------------------------------------------
	// bookmarksDB's functions for tagsTable / tagCache
	// -------------------------------------------------
    
    // returning the number of tags
    public int tagsSize(){					// TODO: shouldn't this be public int tagSize()
        return tagSize(false);
    }
    
    public int tagSize(boolean flushed){
    	if(flushed)
    		flushTagCache();
        return tagsTable.size();
    }
    
    /**
     * load/retrieve an object of type Tag from the tagsTable (also save it in tagCache)
     * @param hash an object of type String, containing a tagHash
     */
    public Tag loadTag(String hash){	// TODO: check if loadTag() could be private
        HashMap<String, String> map;
        Tag ret=null;
        map = tagsTable.getMap(hash);
        if(map!=null){
            ret=new Tag(hash, map);
            tagCache.put(hash, ret);
        }
        return ret;
    }
    
    /**
     * retrieve an object of type Tag from the the tagCache, if object is not cached return loadTag(hash)
     * @param hash an object of type String, containing a tagHash
     */
    public Tag getTag(String hash){
        if(tagCache.containsKey(hash)){
            return tagCache.get(hash);
        }
        return loadTag(hash); //null if it does not exists
    }    
    /**
     * store a Tag in tagsTable or remove an empty tag
     * @param tag an object of type Tag to be stored/removed
     */
    public void storeTag(Tag tag){
        try {
            if(tag.size() >0){
                bookmarksDB.this.tagsTable.set(tag.getTagHash(), tag.getMap());
            }else{
                bookmarksDB.this.tagsTable.remove(tag.getTagHash());
            }
        } catch (IOException e) {}
    } 
    /**
     * save a Tag in tagCache; see also flushTagCache(), addTag(), loadTag() 
     * @param tag an object of type Tag to be saved in tagCache
     */
    public void saveTag(Tag tag) {
        if(tag!=null){
            tagCache.put(tag.getTagHash(), tag);
        }
    }
    
    public void flushTagCache() {
        Iterator<String> it=tagCache.keySet().iterator();
        while(it.hasNext()){
            storeTag(tagCache.get(it.next()));
        }
        tagCache=new HashMap<String, Tag>();
    }
    
    public String addTag(Tag tag) {		// TODO: is addTag() really needed - check storeTag() and saveTag()
    	//tagsTable.set(tag.getTagName(), tag.getMap());
        //tagCache.put(tag.getTagHash(), tag);
    	saveTag(tag);
        return tag.getTagName();
    }
    
    public void removeTag(String hash) {
        try {
            if(tagCache.containsKey(hash)){
                tagCache.remove(hash);
            }
            tagsTable.remove(hash);
        } catch (IOException e) {}
    }
    
    public Iterator<Tag> tagIterator(boolean up) {
        try {
            return new tagIterator(up);
        } catch (IOException e) {
            return new HashSet<Tag>().iterator();
        }
    }
    
    public Iterator<Tag> getTagIterator(boolean priv) {
    	return getTagIterator(priv,1);
    }
    
    public Iterator<Tag> getTagIterator(boolean priv, int c) {
    	Comparator<Tag> comp;    	
    	if (c == SORT_SIZE) comp = new tagSizeComparator(); 
    	else comp = new tagComparator();
    	TreeSet<Tag> set=new TreeSet<Tag>(comp);
    	Iterator<Tag> it = tagIterator(true);
    	Tag tag;
    	while(it.hasNext()){
    		tag=(Tag) it.next();
    		if(priv ||tag.hasPublicItems()){
    			set.add(tag);
    		}
    	}    	  		
    	return set.iterator();
    }
    
    public Iterator<Tag> getTagIterator(boolean priv, int comp, int max){
    	if (max==SHOW_ALL) 
    		return getTagIterator(priv, comp);
    	Iterator<Tag> it = getTagIterator(priv, comp);
    	TreeSet<Tag> set=new TreeSet<Tag>(new tagComparator());
    	int count = 0;
    	while (it.hasNext() && count<=max) {
    		set.add(it.next());
    		count++;    		
    	}    	
    	return set.iterator();
    }
    
    public Iterator<Tag> getTagIterator(String tagName, boolean priv){
     	return getTagIterator(tagName, priv, 1);
    }
    
    public Iterator<Tag> getTagIterator(String tagName, boolean priv, int comp){
    	Comparator<Tag> c;    	
    	if (comp == SORT_SIZE) c = new tagSizeComparator();
    	else c = new tagComparator();
    	TreeSet<Tag> set=new TreeSet<Tag>(c);
    	Iterator<String> it=null;
    	Iterator<String> bit=getBookmarksIterator(tagName, priv);
    	Bookmark bm;
    	Tag tag;
    	Set<String> tags;
    	while(bit.hasNext()){
    		bm=getBookmark(bit.next());
    		tags = bm.getTags();
    		it = tags.iterator();
    		while (it.hasNext()) {
    			tag=getTag( tagHash(it.next()) );
        		if(priv ||tag.hasPublicItems()){
        			set.add(tag);
        		}
    		}
    	}
    	return set.iterator();
    }
    
    public Iterator<Tag> getTagIterator(String tagName, boolean priv, int comp, int max){
    	if (max==SHOW_ALL) 
    		return getTagIterator(priv, comp);
   		Iterator<Tag> it = getTagIterator(tagName, priv, comp);
   		TreeSet<Tag> set=new TreeSet<Tag>(new tagComparator());
   		int count = 0;
   		while (it.hasNext() && count<=max) {
   			set.add(it.next());
   			count++;    		
   		}
    	return set.iterator();
    }    
    
    // rebuilds the tagsDB from the bookmarksDB
    public void rebuildTags(){
        serverLog.logInfo("BOOKMARKS", "rebuilding tags.db from bookmarks.db...");
        Iterator<Bookmark> it = bookmarkIterator(true);
        Bookmark bookmark;
        Tag tag;
        String[] tags;
        while(it.hasNext()){
            bookmark=(Bookmark) it.next();
            tags = bookmark.getTagsString().split(",");
            tag=null;
            for(int i=0;i<tags.length;i++){
                tag=getTag(tagHash(tags[i]));
                if(tag==null){
                    tag=new Tag(tags[i]);
                }
                tag.addUrl(bookmark.getUrlHash());
                saveTag(tag);
            }
        }
        flushTagCache();
        serverLog.logInfo("BOOKMARKS", "Rebuilt "+tagsTable.size()+" tags using your "+bookmarksTable.size()+" bookmarks.");
    }
 
    // ---------------------------------------
	// bookmarksDB's functions for datesTable
	// ---------------------------------------
    
    public bookmarksDate getDate(String date){
    	HashMap<String, String> map;
        map=datesTable.getMap(date);
        if(map==null) return new bookmarksDate(date);
        return new bookmarksDate(date, map);
    }
    // rebuilds the datesDB from the bookmarksDB
    public void rebuildDates(){
        serverLog.logInfo("BOOKMARKS", "rebuilding dates.db from bookmarks.db...");
        Iterator<Bookmark> it=bookmarkIterator(true);
        Bookmark bookmark;
        String date;
        bookmarksDate bmDate;
        while(it.hasNext()){
            bookmark=(Bookmark) it.next();
            date = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date(bookmark.getTimeStamp()));
            bmDate=getDate(date);
            if(bmDate==null){
                bmDate=new bookmarksDate(date);
            }
            bmDate.add(bookmark.getUrlHash());
            bmDate.setDatesTable();
        }
        serverLog.logInfo("BOOKMARKS", "Rebuilt "+datesTable.size()+" dates using your "+bookmarksTable.size()+" bookmarks.");
    }
  
    // -------------------------------------
	// bookmarksDB's experimental functions
	// -------------------------------------
    
    public boolean renameTag(String oldName, String newName){
    	
    	String oldHash=tagHash(oldName);
    	//String newHash=tagHash(newName);
    	Tag tag=getTag(oldHash);							// set tag to oldHash
    	if (tag != null) {
    		Set<String> urlHashes = tag.getUrlHashes();				// preserve urlHashes of tag
    		removeTag(oldHash);
    		Iterator<String> it = urlHashes.iterator();
            Bookmark bookmark;                
            Set<String> tags = new HashSet<String>(); 
            String tagsString;            
            while (it.hasNext()) {							// looping through all bookmarks which were tagged with oldName
                bookmark = getBookmark((String) it.next());
                tagsString = bookmark.getTagsString();				
                // Set<String> tags is difficult with case sensitivity, so I tried 
                // Set<String> tags = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER), but it didn't do the trick :-(
                // so I chose the tagsString and replaceAll() as workaround 
                // unfortunately doing the replaceAll with Patterns (regexp) really costs performance
                tags=listManager.string2set(Pattern.compile(oldName,66).matcher(tagsString).replaceAll(newName)); // TODO: need better solution for renaming tags
                bookmark.setTags(tags, true);	// I had to adjust setTags() for this to work
                saveBookmark(bookmark);
            }
            return true;
    	}
    	return false;
    }

    // --------------------------------------
	// bookmarksDB's Import/Export functions
	// --------------------------------------
    
    public int importFromBookmarks(yacyURL baseURL, String input, String tag, boolean importPublic){
		try {
			// convert string to input stream
			ByteArrayInputStream byteIn = new ByteArrayInputStream(input.getBytes("UTF-8"));
			InputStreamReader reader = new InputStreamReader(byteIn,"UTF-8");
			
			// import stream
			return this.importFromBookmarks(baseURL,reader,tag,importPublic);
		} catch (UnsupportedEncodingException e) { 
			return 0;
		}        	
    }
    
    public int importFromBookmarks(yacyURL baseURL, InputStreamReader input, String tag, boolean importPublic){
    	  	
    	int importCount = 0;
    	
    	Map<yacyURL, String> links = new HashMap<yacyURL, String>();
    	String title;
    	yacyURL url;
    	Bookmark bm;
    	Set<String> tags=listManager.string2set(tag); //this allow multiple default tags
    	try {
    		//load the links
    		htmlFilterContentScraper scraper = new htmlFilterContentScraper(baseURL);    		
    		//OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
    		Writer writer= new htmlFilterWriter(null,null,scraper, null, false);
    		serverFileUtils.copy(input,writer);
    		writer.close();
    		links = scraper.getAnchors();    		
    	} catch (IOException e) {}
    	Iterator<yacyURL> it = links.keySet().iterator();
    	while (it.hasNext()) {
    		url= it.next();
    		title=(String) links.get(url);
    		serverLog.logInfo("BOOKMARKS", "links.get(url)");
    		if(title.equals("")){//cannot be displayed
    			title=url.toString();
    		}
    		bm=new Bookmark(url.toString());
    		bm.setProperty(Bookmark.BOOKMARK_TITLE, title);
    		bm.setTags(tags);
    		bm.setPublic(importPublic);
    		saveBookmark(bm);
    		
    		importCount++;
    	}

    	flushTagCache();
    	
    	return importCount;
    }
    
    public int importFromXML(String input, boolean importPublic){    	
		try {
			// convert string to input stream
			ByteArrayInputStream byteIn = new ByteArrayInputStream(input.getBytes("UTF-8"));
			
			// import stream
			return this.importFromXML(byteIn,importPublic);
		} catch (UnsupportedEncodingException e) { 
			return 0;
		}    	
    }
    
    public int importFromXML(InputStream input, boolean importPublic){
    	DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
    	factory.setValidating(false);
    	factory.setNamespaceAware(false);
    	DocumentBuilder builder;
    	try {
    		builder = factory.newDocumentBuilder();
    		Document doc=builder.parse(input);
    		return parseXMLimport(doc, importPublic);
    	} catch (ParserConfigurationException e) {  
    	} catch (SAXException e) {
    	} catch (IOException e) {
    	}
    	return 0;
    	
    }
    
    public int parseXMLimport(Node doc, boolean importPublic){
    	int importCount = 0;
        if(doc.getNodeName()=="post"){
            NamedNodeMap attributes = doc.getAttributes();
            String url=attributes.getNamedItem("href").getNodeValue();
            if(url.equals("")){
                return 0;
            }
            Bookmark bm=new Bookmark(url);
            String tagsString="";
            String title="";
            String description="";
            String time="";
            if(attributes.getNamedItem("tag")!=null){
                tagsString=attributes.getNamedItem("tag").getNodeValue();
            }
            if(attributes.getNamedItem("description")!=null){
                title=attributes.getNamedItem("description").getNodeValue();
            }
            if(attributes.getNamedItem("extended")!=null){
                description=attributes.getNamedItem("extended").getNodeValue();
            }
            if(attributes.getNamedItem("time")!=null){
                time=attributes.getNamedItem("time").getNodeValue();
            }
            Set<String> tags=new HashSet<String>();
            
            if(title != null){
                bm.setProperty(Bookmark.BOOKMARK_TITLE, title);
            }
            if(tagsString!=null){
                tags = listManager.string2set(tagsString.replace(' ', ','));
            }
            bm.setTags(tags, true);
            if(time != null){
            	
            	Date parsedDate = null;
            	try {
					parsedDate = serverDate.parseISO8601(time);
				} catch (ParseException e) {
					parsedDate = new Date();
				}            	
                bm.setTimeStamp(parsedDate.getTime());
            }
            if(description!=null){
                bm.setProperty(Bookmark.BOOKMARK_DESCRIPTION, description);
            }
            bm.setPublic(importPublic);
            saveBookmark(bm);
            
            importCount++;
        }
        NodeList children=doc.getChildNodes();
        if(children != null){
            for (int i=0; i<children.getLength(); i++) {
            	importCount += parseXMLimport(children.item(i), importPublic);
            }
        }
        flushTagCache();
        
        return importCount;
    }
    
    // --------------------------------------
	// bookmarksDB's Subclasses
	// --------------------------------------
        
    /**
     * Subclass of bookmarksDB, which provides the Tag object-type
     */
    public class Tag{
        public static final String URL_HASHES="urlHashes";
        public static final String TAG_NAME="tagName";
        private String tagHash;
        private HashMap<String, String> mem;
        private Set<String> urlHashes;

        public Tag(String hash, HashMap<String, String> map){
        	tagHash=hash;
            mem=map;
            if(mem.containsKey(URL_HASHES))
                urlHashes = listManager.string2set(mem.get(URL_HASHES));
            else
                urlHashes = new HashSet<String>();
        }
        public Tag(String name, HashSet<String> entries){
            tagHash=tagHash(name);
            mem=new HashMap<String, String>();
            //mem.put(URL_HASHES, listManager.arraylist2string(entries));
            urlHashes=entries;
            mem.put(TAG_NAME, name);
        }
        public Tag(String name){
            tagHash=tagHash(name);
            mem=new HashMap<String, String>();
            //mem.put(URL_HASHES, "");
            urlHashes=new HashSet<String>();
            mem.put(TAG_NAME, name);
        }
        public HashMap<String, String> getMap(){
            mem.put(URL_HASHES, listManager.collection2string(this.urlHashes));
            return mem;
        }
        /**
         * get the lowercase Tagname
         */
        public String getTagName(){
            /*if(this.mem.containsKey(TAG_NAME)){
                return (String) this.mem.get(TAG_NAME);
            }
            return "";*/
            return getFriendlyName().toLowerCase();
        }
        public String getTagHash(){
            return tagHash;
        }
        /**
         * @return the tag name, with all uppercase chars
         */
        public String getFriendlyName(){
            /*if(this.mem.containsKey(TAG_FRIENDLY_NAME)){
                return (String) this.mem.get(TAG_FRIENDLY_NAME);
            }
            return getTagName();*/
            if(this.mem.containsKey(TAG_NAME)){
                return this.mem.get(TAG_NAME);
            }
            return "notagname";
        }
        public Set<String> getUrlHashes(){
            return urlHashes;
        }
        public boolean hasPublicItems(){
        	Iterator<String> it=getBookmarksIterator(this.getTagHash(), false);
        	if(it.hasNext()){
        		return true;
        	}
        	return false;
        }
        public void addUrl(String urlHash){
            urlHashes.add(urlHash);
        }
        public void delete(String urlHash){
            urlHashes.remove(urlHash);
        }
        public int size(){
            return urlHashes.size();
        }
    }
    /**
     * Subclass of bookmarksDB, which provide the bookmarksDate object-type
     */    
    public class bookmarksDate{
        public static final String URL_HASHES="urlHashes";
        private HashMap<String, String> mem;
        String date;

        public bookmarksDate(String mydate){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=new HashMap<String, String>();
            mem.put(URL_HASHES, "");
        }

        public bookmarksDate(String mydate, HashMap<String, String> map){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=map;
        }
        public bookmarksDate(String mydate, ArrayList<String> entries){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=new HashMap<String, String>();
            mem.put(URL_HASHES, listManager.collection2string(entries));
        }
        public void add(String urlHash){
            String urlHashes = mem.get(URL_HASHES);
            ArrayList<String> list;
            if(urlHashes != null && !urlHashes.equals("")){
                list=listManager.string2arraylist(urlHashes);
            }else{
                list=new ArrayList<String>();
            }
            if(!list.contains(urlHash) && urlHash != null && !urlHash.equals("")){
                list.add(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.collection2string(list));
            /*if(urlHashes!=null && !urlHashes.equals("") ){
                if(urlHashes.indexOf(urlHash) <0){
                    this.mem.put(URL_HASHES, urlHashes+","+urlHash);
                }
            }else{
                this.mem.put(URL_HASHES, urlHash);
            }*/
        }
        public void delete(String urlHash){
            ArrayList<String> list=listManager.string2arraylist(this.mem.get(URL_HASHES));
            if(list.contains(urlHash)){
                list.remove(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.collection2string(list));
        }
        public void setDatesTable(){
            try {
                if(this.size() >0){
                    bookmarksDB.this.datesTable.set(getDateString(), mem);
                }else{
                    bookmarksDB.this.datesTable.remove(getDateString());
                }
            } catch (IOException e) {}
        }
        public String getDateString(){
            return date;
        }
        public ArrayList<String> getBookmarkList(){
            return listManager.string2arraylist(this.mem.get(URL_HASHES));
        }
        public int size(){
            return listManager.string2arraylist(this.mem.get(URL_HASHES)).size();
        }
    }
    /**
     * Subclass of bookmarksDB, which provides the Bookmark object-type
     */
    public class Bookmark extends kelondroObjectsMapEntry {
        public static final String BOOKMARK_URL="bookmarkUrl";
        public static final String BOOKMARK_TITLE="bookmarkTitle";
        public static final String BOOKMARK_DESCRIPTION="bookmarkDesc";
        public static final String BOOKMARK_TAGS="bookmarkTags";
        public static final String BOOKMARK_PUBLIC="bookmarkPublic";
        public static final String BOOKMARK_TIMESTAMP="bookmarkTimestamp";
        public static final String BOOKMARK_OWNER="bookmarkOwner";
        public static final String BOOKMARK_IS_FEED="bookmarkIsFeed";
        private String urlHash;
        private Set<String> tags;
        private long timestamp;
        
        public Bookmark(String urlHash, HashMap<String, String> map) {
            super(map);
            this.urlHash=urlHash;
            if(map.containsKey(BOOKMARK_TAGS))
                tags=listManager.string2set((String) map.get(BOOKMARK_TAGS));
            else
                tags=new HashSet<String>();
            loadTimestamp();
        }
        
        public Bookmark(String url){
            super();
            if(!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")){
                url="http://"+url;
            }
            try {
                this.urlHash=(new yacyURL(url, null)).hash();
            } catch (MalformedURLException e) {
                this.urlHash = null;
            }
            entry.put(BOOKMARK_URL, url);
            this.timestamp=System.currentTimeMillis();
            tags=new HashSet<String>();
            Bookmark oldBm=getBookmark(this.urlHash);
            if(oldBm!=null && oldBm.entry.containsKey(BOOKMARK_TIMESTAMP)){
                entry.put(BOOKMARK_TIMESTAMP, oldBm.entry.get(BOOKMARK_TIMESTAMP)); //preserve timestamp on edit
            }else{
                entry.put(BOOKMARK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            }  
            bookmarksDate bmDate=getDate(entry.get(BOOKMARK_TIMESTAMP));
            bmDate.add(this.urlHash);
            bmDate.setDatesTable();
            
            removeBookmark(this.urlHash); //prevent empty tags
        }
        
        public Bookmark(String urlHash, yacyURL url) {
            super();
            this.urlHash=urlHash;
            entry.put(BOOKMARK_URL, url.toNormalform(false, true));
            tags=new HashSet<String>();
            timestamp=System.currentTimeMillis();
        }
        
        public Bookmark(String urlHash, String url) {
            super();
            this.urlHash=urlHash;
            entry.put(BOOKMARK_URL, url);
            tags=new HashSet<String>();
            timestamp=System.currentTimeMillis();
        }

        public Bookmark(kelondroObjectsMapEntry map) throws MalformedURLException {
            this((new yacyURL((String)map.map().get(BOOKMARK_URL), null)).hash(), map.map());
        }
        
        private Map<String, String> toMap() {
            entry.put(BOOKMARK_TAGS, listManager.collection2string(tags));
            entry.put(BOOKMARK_TIMESTAMP, String.valueOf(this.timestamp));
            return entry;
        }
        
        private void loadTimestamp() {
            if(entry.containsKey(BOOKMARK_TIMESTAMP))
                this.timestamp=Long.parseLong(entry.get(BOOKMARK_TIMESTAMP));
        }
        
        public String getUrlHash() {
            return urlHash;
        }
        
        public String getUrl() {
            return entry.get(BOOKMARK_URL);
        }
        
        public Set<String> getTags() {
            return tags;
        }
        
        public String getTagsString() {        	
        	String s[] = listManager.collection2string(getTags()).split(",");
        	String tagsString="";        	
        	for (int i=0; i<s.length; i++){
        		if(!s[i].startsWith("/")){
        			tagsString += s[i]+",";
        		}
        	}
        	return tagsString;
        }
        
        public String getFoldersString(){
        	String s[] = listManager.collection2string(getTags()).split(",");
        	String foldersString="";        	
        	for (int i=0; i<s.length; i++){
        		if(s[i].startsWith("/")){
        			foldersString += s[i]+",";
        		}
        	}
        	return foldersString;
        }
        
        public String getDescription(){
            if(entry.containsKey(BOOKMARK_DESCRIPTION)){
                return entry.get(BOOKMARK_DESCRIPTION);
            }
            return "";
        }
        
        public String getTitle(){
            if(entry.containsKey(BOOKMARK_TITLE)){
                return entry.get(BOOKMARK_TITLE);
            }
            return entry.get(BOOKMARK_URL);
        }
        
        public String getOwner(){
            if(entry.containsKey(BOOKMARK_OWNER)){
                return entry.get(BOOKMARK_OWNER);
            }
            return null; //null means admin
        }
        
        public void setOwner(String owner){
            entry.put(BOOKMARK_OWNER, owner);
        }
        
        public boolean getPublic(){
            if(entry.containsKey(BOOKMARK_PUBLIC)){
                return entry.get(BOOKMARK_PUBLIC).equals("public");
            }
            return false;
        }
        
        public boolean getFeed(){
            if(entry.containsKey(BOOKMARK_IS_FEED)){
                return entry.get(BOOKMARK_IS_FEED).equals("true");
            }
            return false;
        }
        
        public void setPublic(boolean isPublic){
        	if(isPublic){
                entry.put(BOOKMARK_PUBLIC, "public");
        	}else{
                entry.put(BOOKMARK_PUBLIC, "private");
        	}
        }
        
        public void setFeed(boolean isFeed){
        	if(isFeed){
                entry.put(BOOKMARK_IS_FEED, "true");
        	}else{
                entry.put(BOOKMARK_IS_FEED, "false");
        	}
        }
        
        public void setProperty(String name, String value){
            entry.put(name, value);
            //setBookmarksTable();
        }
        
        public void addTag(String tag){
            tags.add(tag);
        }
        
        /**
         * set the Tags of the bookmark, and write them into the tags table.
         * @param tags2 a ArrayList with the tags
         */
        public void setTags(Set<String> tags2){
            setTags(tags2, true);
        }
        
        /**
         * set the Tags of the bookmark
         * @param tags ArrayList with the tagnames
         * @param local sets, whether the updated tags should be stored to tagsDB
         */
        public void setTags(Set<String> tags2, boolean local){
            tags = tags2;			// TODO: check if this is safe
        	// tags.addAll(tags2);	// in order for renameTag() to work I had to change this form 'add' to 'set'
            Iterator<String> it=tags.iterator();
            while(it.hasNext()){
                String tagName=it.next();
                Tag tag=getTag(tagHash(tagName));
                if(tag == null){
                    tag=new Tag(tagName);
                }
                tag.addUrl(getUrlHash());
                if(local){
                    saveTag(tag);
                }
            }
            toMap();
        }

        public long getTimeStamp(){
            return timestamp;
        }
        
        public void setTimeStamp(long ts){
        	this.timestamp=ts;
        }
    }
    /**
     * Subclass of bookmarksDB, which provides the tagIterator object-type
     */
    public class tagIterator implements Iterator<Tag> {
        kelondroCloneableIterator<String> tagIter;
        bookmarksDB.Tag nextEntry;
        
        public tagIterator(boolean up) throws IOException {
            flushTagCache(); //XXX: This costs performace :-((
            this.tagIter = bookmarksDB.this.tagsTable.keys(up, false);
            this.nextEntry = null;
        }
        
        public boolean hasNext() {
            try {
                return this.tagIter.hasNext();
            } catch (kelondroException e) {
                //resetDatabase();
                return false;
            }
        }
        
        public Tag next() {
            try {
                return getTag(this.tagIter.next());
            } catch (kelondroException e) {
                //resetDatabase();
                return null;
            }
        }
        
        public void remove() {
            if (this.nextEntry != null) {
                try {
                    String tagHash = this.nextEntry.getTagHash();
                    if (tagHash != null) removeTag(tagHash);
                } catch (kelondroException e) {
                    //resetDatabase();
                }
            }
        }
    }
    
    /**
     * Subclass of bookmarksDB, which provides the bookmarkIterator object-type
     */
    public class bookmarkIterator implements Iterator<Bookmark> {
        Iterator<String> bookmarkIter;
        bookmarksDB.Bookmark nextEntry;
        public bookmarkIterator(boolean up) throws IOException {
            //flushBookmarkCache(); //XXX: this will cost performance
            this.bookmarkIter = bookmarksDB.this.bookmarksTable.keys(up, false);
            this.nextEntry = null;
        }
        
        public boolean hasNext() {
            try {
                return this.bookmarkIter.hasNext();
            } catch (kelondroException e) {
                //resetDatabase();
                return false;
            }
        }
        
        public Bookmark next() {
            try {
                return getBookmark((String) this.bookmarkIter.next());
            } catch (kelondroException e) {
                //resetDatabase();
                return null;
            }
        }
        
        public void remove() {
            if (this.nextEntry != null) {
                try {
                    Object bookmarkName = this.nextEntry.getUrlHash();
                    if (bookmarkName != null) removeBookmark((String) bookmarkName);
                } catch (kelondroException e) {
                    //resetDatabase();
                }
            }
        }
    }
    
    /**
     * Comparator to sort objects of type Bookmark according to their timestamps
     */
    public class bookmarkComparator implements Comparator<String> {
        
        private boolean newestFirst;
        
        /**
         * @param newestFirst newest first, or oldest first?
         */
        public bookmarkComparator(boolean newestFirst){
            this.newestFirst=newestFirst;
        }
        
        public int compare(String obj1, String obj2) {
            Bookmark bm1=getBookmark(obj1);
            Bookmark bm2=getBookmark(obj2);
			if(bm1==null || bm2==null)
				return 0; //XXX: i think this should not happen? maybe this needs further tracing of the bug
            if(this.newestFirst){
                if(bm2.getTimeStamp() - bm1.getTimeStamp() >0) return 1;
                return -1;
            }
            if(bm1.getTimeStamp() - bm2.getTimeStamp() >0) return 1;
            return -1;
        }
    }
    
    /**
     * Comparator to sort objects of type Tag according to their names
     */
    public class tagComparator implements Comparator<Tag> {
        
    	public int compare(Tag obj1, Tag obj2){
    		return obj1.getTagName().compareTo(obj2.getTagName());
    	}
    	
    }
    
    public class tagSizeComparator implements Comparator<Tag> {
        
    	public int compare(Tag obj1, Tag obj2) {
    		if (obj1.size() < obj2.size()) return 1;
    		else if (obj1.getTagName().equals(obj2.getTagName())) return 0;
    		else return -1;
    	}
    	
    }
}