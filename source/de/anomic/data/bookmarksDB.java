//bookmarksDB.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Alexander Schier
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

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
import de.anomic.plasma.plasmaCondenser;
import de.anomic.plasma.plasmaURL;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.kelondro.kelondroObjects;
import de.anomic.kelondro.kelondroObjectsMapEntry;
import de.anomic.net.URL;
import de.anomic.server.serverFileUtils;
import de.anomic.server.logging.serverLog;

public class bookmarksDB {
    kelondroMapObjects tagsTable;
    //kelondroMap bookmarksTable;
    kelondroObjects bookmarksTable;
    kelondroMapObjects datesTable;
    HashMap tagCache;
    HashMap bookmarkCache;
    
    public static String tagHash(String tagName){
        return plasmaCondenser.word2hash(tagName.toLowerCase());
    }
    public static String tagHash(String tagName, String user){
        return plasmaCondenser.word2hash(user+":"+tagName.toLowerCase());
    }
    public static String dateToiso8601(Date date){
    	return new SimpleDateFormat("yyyy-MM-dd").format(date)+"T"+(new SimpleDateFormat("HH:mm:ss")).format(date)+"Z";
    }
    public static Date iso8601ToDate(String iso8601){
    	String[] tmp=iso8601.split("T");
        if(tmp.length!=2){
            //Error parsing Date
            return new Date();
        }
    	String day=tmp[0];
    	String time=tmp[1];
    	if(time.length()>8){
    		time=time.substring(0,8);
    	}
    	try {
			Calendar date=Calendar.getInstance();
			Calendar date2=Calendar.getInstance();
			date.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(day));
			date2.setTime(new SimpleDateFormat("HH:mm:ss").parse(time));
			
			date.set(Calendar.HOUR_OF_DAY, date2.get(Calendar.HOUR_OF_DAY));
			date.set(Calendar.MINUTE, date2.get(Calendar.MINUTE));
			date.set(Calendar.SECOND, date2.get(Calendar.SECOND));
			
			return date.getTime();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return new Date();
    }
    
    public bookmarksDB(File bookmarksFile, File tagsFile, File datesFile, int bufferkb, long preloadTime) {
        // bookmarks
        tagCache=new HashMap();
        bookmarkCache=new HashMap();
        bookmarksFile.getParentFile().mkdirs();
        //this.bookmarksTable = new kelondroMap(kelondroDyn.open(bookmarksFile, bufferkb * 1024, preloadTime, 12, 256, '_', true, false));
        this.bookmarksTable = new kelondroObjects(kelondroDyn.open(bookmarksFile, bufferkb * 1024, preloadTime, 12, 256, '_', true, false), 1000);

        // tags
        tagsFile.getParentFile().mkdirs();
        boolean tagsFileExisted = tagsFile.exists();
        this.tagsTable = new kelondroMapObjects(kelondroDyn.open(tagsFile, bufferkb * 1024, preloadTime, 12, 256, '_', true, false), 500);
        if (!tagsFileExisted) rebuildTags();

        // dates
        boolean datesExisted = datesFile.exists();
        this.datesTable = new kelondroMapObjects(kelondroDyn.open(datesFile, bufferkb * 1024, preloadTime, 20, 256, '_', true, false), 500);
        if (!datesExisted) rebuildDates();
        
    }
    
    public void close(){
        try {
            //flushBookmarkCache();
            bookmarksTable.close();
        } catch (IOException e) {}
        try {
            flushTagCache();
            tagsTable.close();
        } catch (IOException e) {}
        try {
            datesTable.close();
        } catch (IOException e) {}
    }
    public int bookmarksSize(){
        return bookmarksTable.size();
    }
    public int tagSize(boolean flushed){
        if(flushed)
            flushTagCache();
        return tagsTable.size();
    }
    public int tagsSize(){
        return tagSize(false);
    }
    public void saveBookmark(Bookmark bookmark){
        try {
            bookmarksTable.set(bookmark.getUrlHash(), bookmark);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public Tag loadTag(String hash){
        Map map;
        Tag ret=null;
        map = tagsTable.getMap(hash);
        if(map!=null){
            ret=new Tag(hash, map);
            tagCache.put(hash, ret);
        }
        
        return ret;
    }
    public void saveTag(Tag tag){
        if(tag!=null){
            tagCache.put(tag.getTagHash(), tag);
        }
    }
    /**
     * store a Tag in the tagsDB or remove an empty tag
     * @param tag the tagobject to be stored/removed
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
    public void flushTagCache(){
        Iterator it=tagCache.keySet().iterator();
        while(it.hasNext()){
            storeTag((Tag) tagCache.get(it.next()));
        }
        tagCache=new HashMap();
    }
    
    public String addTag(Tag tag){
        //tagsTable.set(tag.getTagName(), tag.getMap());
        tagCache.put(tag.getTagHash(), tag);
        return tag.getTagName();
    }
    public void rebuildTags(){
        serverLog.logInfo("BOOKMARKS", "rebuilding tags.db from bookmarks.db...");
        Iterator it=bookmarkIterator(true);
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
    public void rebuildDates(){
        serverLog.logInfo("BOOKMARKS", "rebuilding dates.db from bookmarks.db...");
        Iterator it=bookmarkIterator(true);
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
    public Tag getTag(String hash){
        if(tagCache.containsKey(hash)){
            return (Tag) tagCache.get(hash);
        }
        return loadTag(hash); //null if it does not exists
    }
    public bookmarksDate getDate(String date){
        Map map;
        map=datesTable.getMap(date);
        if(map==null) return new bookmarksDate(date);
        return new bookmarksDate(date, map);
    }
    public boolean renameTag(String oldName, String newName){
    	String tagHash=tagHash(oldName);
        Tag tag=getTag(tagHash);
            if (tag != null) {
            HashSet urlHashes = tag.getUrlHashes();
            try {
                if(tagCache.containsKey(tagHash(oldName))){
                    tagCache.remove(tagHash(oldName));
                }
                tagsTable.remove(tagHash(oldName));
            } catch (IOException e) {
            }
            tag=new Tag(tagHash(newName), tag.getMap());
            saveTag(tag);
            Iterator it = urlHashes.iterator();
            Bookmark bookmark;
            HashSet tags;
            while (it.hasNext()) {
                bookmark = getBookmark((String) it.next());
                tags = bookmark.getTags();
                tags.remove(oldName); //this will fail, if upper/lowercase is not matching
                tags.add(newName);
                bookmark.setTags(tags, true);
                saveBookmark(bookmark);
            }
            return true;
        }
        return false;
    }
    public void removeTag(String hash){
        try {
            if(tagCache.containsKey(hash)){
                tagCache.remove(hash);
            }
            tagsTable.remove(hash);
        } catch (IOException e) {}
    }
    public String addBookmark(Bookmark bookmark){
        saveBookmark(bookmark);
        return bookmark.getUrlHash();
 
    }
    public Bookmark getBookmark(String urlHash){
        try {
            return (Bookmark) bookmarksTable.get(urlHash);
        } catch (IOException e) {
            return null;
        }
        
    }
    public Iterator getBookmarksIterator(boolean priv){
        TreeSet set=new TreeSet(new bookmarkComparator(true));
        Iterator it=bookmarkIterator(true);
        Bookmark bm;
        while(it.hasNext()){
            bm=(Bookmark)it.next();
            if(priv || bm.getPublic()){
            	set.add(bm.getUrlHash());
            }
        }
        return set.iterator();
    }
    public Iterator getBookmarksIterator(String tagName, boolean priv){
        TreeSet set=new TreeSet(new bookmarkComparator(true));
        String tagHash=tagHash(tagName);
        Tag tag=getTag(tagHash);
        HashSet hashes=new HashSet();
        if(tag != null){
            hashes=getTag(tagHash).getUrlHashes();
        }
        if(priv){
        	set.addAll(hashes);
        }else{
        	Iterator it=hashes.iterator();
        	Bookmark bm;
        	while(it.hasNext()){
        		bm=getBookmark((String) it.next());
        		if(bm.getPublic()){
        			set.add(bm.getUrlHash());
        		}
        	}
        }
    	return set.iterator();
    }
    public Iterator getTagIterator(boolean priv){
    	TreeSet set=new TreeSet(new tagComparator());
    	Iterator it=tagIterator(true);
    	Tag tag;
    	while(it.hasNext()){
    		tag=(Tag) it.next();
    		if(priv ||tag.hasPublicItems()){
    			set.add(tag);
    		}
    	}
    	return set.iterator();
    }
    public boolean removeBookmark(String urlHash){
        Bookmark bookmark = getBookmark(urlHash);
        if(bookmark == null) return false; //does not exist
        HashSet tags = bookmark.getTags();
        bookmarksDB.Tag tag=null;
        Iterator it=tags.iterator();
        while(it.hasNext()){
            tag=getTag( tagHash((String) it.next()) );
            if(tag!=null){
                tag.delete(urlHash);
                saveTag(tag);
            }
        }
        Bookmark b;
        try {
            b = (Bookmark) bookmarksTable.get(urlHash);
            bookmarksTable.remove(urlHash);
        } catch (IOException e) {
            b = null;
        }
        return b != null;
    }

    public Bookmark createBookmark(String url, String user){
        if (url == null || url.length() == 0) return null;
        Bookmark bk = new Bookmark(url);
        bk.setOwner(user);
        return (bk.getUrlHash() == null || bk.toMap() == null) ? null : bk;
    }
    public Iterator tagIterator(boolean up){
        try {
            return new tagIterator(up);
        } catch (IOException e) {
            return new HashSet().iterator();
        }
    }
    public Iterator bookmarkIterator(boolean up){
        try {
            return new bookmarkIterator(up);
        } catch (IOException e) {
            return new HashSet().iterator();
        }
    }
    public void addBookmark(String url, String title, ArrayList tags){
        
    }
    
    public int importFromBookmarks(URL baseURL, String input, String tag, boolean importPublic){
		try {
			// convert string to inputstream
			ByteArrayInputStream byteIn = new ByteArrayInputStream(input.getBytes("UTF-8"));
			InputStreamReader reader = new InputStreamReader(byteIn,"UTF-8");
			
			// import stream
			return this.importFromBookmarks(baseURL,reader,tag,importPublic);
		} catch (UnsupportedEncodingException e) { 
			return 0;
		}        	
    }
    public int importFromBookmarks(URL baseURL, InputStreamReader input, String tag, boolean importPublic){
    	int importCount = 0;
    	
    	HashMap links=new HashMap();
    	Iterator it;
    	String url,title;
    	Bookmark bm;
    	HashSet tags=listManager.string2hashset(tag); //this allow multiple default tags
    	try {
    		//load the links
    		htmlFilterContentScraper scraper = new htmlFilterContentScraper(baseURL);
    		//OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
    		Writer writer= new htmlFilterWriter(null,null,scraper, null, false);
    		serverFileUtils.copy(input,writer);
    		writer.close();
    		links = (HashMap) scraper.getAnchors();
    	} catch (IOException e) {}
    	it=links.keySet().iterator();
    	while(it.hasNext()){
    		url=(String) it.next();
    		title=(String) links.get(url);
    		if(title.equals("")){//cannot be displayed
    			title=url;
    		}
    		bm=new Bookmark(url);
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
			// convert string to inputstream
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
            HashSet tags=new HashSet();
            
            if(title != null){
                bm.setProperty(Bookmark.BOOKMARK_TITLE, title);
            }
            if(tagsString!=null){
                tags = listManager.string2hashset(tagsString.replace(' ', ','));
            }
            bm.setTags(tags, true);
            if(time != null){
                bm.setTimeStamp(iso8601ToDate(time).getTime());
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
    /**
     * Subclass, which stores an Tag
     *
     */
    public class Tag{
        public static final String URL_HASHES="urlHashes";
        public static final String TAG_NAME="tagName";
        private String tagHash;
        private Map mem;
        private HashSet urlHashes;

        public Tag(String hash, Map map){
        	tagHash=hash;
            mem=map;
            if(mem.containsKey(URL_HASHES))
                urlHashes=listManager.string2hashset((String) mem.get(URL_HASHES));
            else
                urlHashes=new HashSet();
        }
        public Tag(String name, HashSet entries){
            tagHash=tagHash(name);
            mem=new HashMap();
            //mem.put(URL_HASHES, listManager.arraylist2string(entries));
            urlHashes=entries;
            mem.put(TAG_NAME, name);
        }
        public Tag(String name){
            tagHash=tagHash(name);
            mem=new HashMap();
            //mem.put(URL_HASHES, "");
            urlHashes=new HashSet();
            mem.put(TAG_NAME, name);
        }
        public Map getMap(){
            mem.put(URL_HASHES, listManager.hashset2string(this.urlHashes));
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
         * get tag name, with all uppercase chars.
         * @return
         */
        public String getFriendlyName(){
            /*if(this.mem.containsKey(TAG_FRIENDLY_NAME)){
                return (String) this.mem.get(TAG_FRIENDLY_NAME);
            }
            return getTagName();*/
            if(this.mem.containsKey(TAG_NAME)){
                return (String) this.mem.get(TAG_NAME);
            }
            return "notagname";
        }
        public HashSet getUrlHashes(){
            return urlHashes;
        }
        public boolean hasPublicItems(){
        	Iterator it=getBookmarksIterator(this.getTagHash(), false);
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
    public class bookmarksDate{
        public static final String URL_HASHES="urlHashes";
        private Map mem;
        String date;

        public bookmarksDate(String mydate){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=new HashMap();
            mem.put(URL_HASHES, "");
        }

        public bookmarksDate(String mydate, Map map){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=map;
        }
        public bookmarksDate(String mydate, ArrayList entries){
            //round to seconds, but store as milliseconds (java timestamp)
            date=String.valueOf((Long.parseLong(mydate)/1000)*1000);
            mem=new HashMap();
            mem.put(URL_HASHES, listManager.arraylist2string(entries));
        }
        public void add(String urlHash){
            String urlHashes = (String)mem.get(URL_HASHES);
            ArrayList list;
            if(urlHashes != null && !urlHashes.equals("")){
                list=listManager.string2arraylist(urlHashes);
            }else{
                list=new ArrayList();
            }
            if(!list.contains(urlHash) && urlHash != null && !urlHash.equals("")){
                list.add(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.arraylist2string(list));
            /*if(urlHashes!=null && !urlHashes.equals("") ){
                if(urlHashes.indexOf(urlHash) <0){
                    this.mem.put(URL_HASHES, urlHashes+","+urlHash);
                }
            }else{
                this.mem.put(URL_HASHES, urlHash);
            }*/
        }
        public void delete(String urlHash){
            ArrayList list=listManager.string2arraylist((String) this.mem.get(URL_HASHES));
            if(list.contains(urlHash)){
                list.remove(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.arraylist2string(list));
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
        public ArrayList getBookmarkList(){
            return listManager.string2arraylist((String)this.mem.get(URL_HASHES));
        }
        public int size(){
            return listManager.string2arraylist(((String)this.mem.get(URL_HASHES))).size();
        }
    }
    /**
     * Subclass, which stores the bookmark
     *
     */
    public class Bookmark extends kelondroObjectsMapEntry{
        public static final String BOOKMARK_URL="bookmarkUrl";
        public static final String BOOKMARK_TITLE="bookmarkTitle";
        public static final String BOOKMARK_DESCRIPTION="bookmarkDesc";
        public static final String BOOKMARK_TAGS="bookmarkTags";
        public static final String BOOKMARK_PUBLIC="bookmarkPublic";
        public static final String BOOKMARK_TIMESTAMP="bookmarkTimestamp";
        public static final String BOOKMARK_OWNER="bookmarkOwner";
        private String urlHash;
        private HashSet tags;
        private long timestamp;
        public Bookmark(String urlHash, Map map){
            super(map);
            this.urlHash=urlHash;
            if(map.containsKey(BOOKMARK_TAGS))
                tags=listManager.string2hashset((String) map.get(BOOKMARK_TAGS));
            else
                tags=new HashSet();
            loadTimestamp();
        }
        public Bookmark(String url){
            super();
            if(!url.toLowerCase().startsWith("http://")){
                url="http://"+url;
            }
            this.urlHash=plasmaURL.urlHash(url);
            entry.put(BOOKMARK_URL, url);
            this.timestamp=System.currentTimeMillis();
            tags=new HashSet();
            Bookmark oldBm=getBookmark(this.urlHash);
            if(oldBm!=null && oldBm.entry.containsKey(BOOKMARK_TIMESTAMP)){
                entry.put(BOOKMARK_TIMESTAMP, oldBm.entry.get(BOOKMARK_TIMESTAMP)); //preserve timestamp on edit
            }else{
                entry.put(BOOKMARK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            }  
            bookmarksDate bmDate=getDate((String) entry.get(BOOKMARK_TIMESTAMP));
            bmDate.add(this.urlHash);
            bmDate.setDatesTable();
            
            removeBookmark(this.urlHash); //prevent empty tags
        }
        public Bookmark(String urlHash, URL url){
            super();
            this.urlHash=urlHash;
            entry.put(BOOKMARK_URL, url.toString());
            tags=new HashSet();
            timestamp=System.currentTimeMillis();
        }
        public Bookmark(String urlHash, String url){
            super();
            this.urlHash=urlHash;
            entry.put(BOOKMARK_URL, url);
            tags=new HashSet();
            timestamp=System.currentTimeMillis();
        }
       
        private Map toMap(){
            entry.put(BOOKMARK_TAGS, listManager.hashset2string(tags));
            entry.put(BOOKMARK_TIMESTAMP, String.valueOf(this.timestamp));
            return entry;
        }
        private void loadTimestamp(){
            if(entry.containsKey(BOOKMARK_TIMESTAMP))
                this.timestamp=Long.parseLong((String)entry.get(BOOKMARK_TIMESTAMP));
        }
        public String getUrlHash(){
            return urlHash;
        }
        public String getUrl(){
            return (String) entry.get(BOOKMARK_URL);
        }
        public HashSet getTags(){
            return tags;
        }
        public String getTagsString(){
            return listManager.hashset2string(getTags());
        }
        public String getDescription(){
            if(entry.containsKey(BOOKMARK_DESCRIPTION)){
                return (String) entry.get(BOOKMARK_DESCRIPTION);
            }
            return "";
        }
        public String getTitle(){
            if(entry.containsKey(BOOKMARK_TITLE)){
                return (String) entry.get(BOOKMARK_TITLE);
            }
            return (String) entry.get(BOOKMARK_URL);
        }
        public String getOwner(){
            if(entry.containsKey(BOOKMARK_OWNER)){
                return (String) entry.get(BOOKMARK_OWNER);
            }else{
                return null; //null means admin
            }
        }
        public void setOwner(String owner){
            entry.put(BOOKMARK_OWNER, owner);
        }
        public boolean getPublic(){
            if(entry.containsKey(BOOKMARK_PUBLIC)){
                return ((String) entry.get(BOOKMARK_PUBLIC)).equals("public");
            }else{
                return false;
            }
        }
        public void setPublic(boolean isPublic){
        	if(isPublic){
                entry.put(BOOKMARK_PUBLIC, "public");
        	}else{
                entry.put(BOOKMARK_PUBLIC, "private");
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
         * @param tags a ArrayList with the tags
         */
        public void setTags(HashSet tags){
            setTags(tags, true);
        }
        /**
         * set the Tags of the bookmark
         * @param tags ArrayList with the tagnames
         * @param local sets, whether the updated tags should be stored to tagsDB
         */
        public void setTags(HashSet mytags, boolean local){
            tags.addAll(mytags);
            Iterator it=tags.iterator();
            while(it.hasNext()){
                String tagName=(String) it.next();
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
    public class tagIterator implements Iterator{
        kelondroDyn.dynKeyIterator tagIter;
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
        public Object next() {
            try {
                return getTag((String) this.tagIter.next());
            } catch (kelondroException e) {
                //resetDatabase();
                return null;
            }
        }
        public void remove() {
            if (this.nextEntry != null) {
                try {
                    String tagHash = (String)this.nextEntry.getTagHash();
                    if (tagHash != null) removeTag((String) tagHash);
                } catch (kelondroException e) {
                    //resetDatabase();
                }
            }
        }
    }
    public class bookmarkIterator implements Iterator{
        Iterator bookmarkIter;
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
        public Object next() {
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
     * Comparator to sort the Bookmarks with Timestamps
     */
    public class bookmarkComparator implements Comparator{
        
        private boolean newestFirst;
        /**
         * @param newestFirst newest first, or oldest first?
         */
        public bookmarkComparator(boolean newestFirst){
            this.newestFirst=newestFirst;
        }
        public int compare(Object obj1, Object obj2){
            Bookmark bm1=getBookmark((String)obj1);
            Bookmark bm2=getBookmark((String)obj2);
            //XXX: what happens, if there is a big difference? (to much for int)
            /*if(this.newestFirst){
                return (new Long(bm2.getTimeStamp() - bm1.getTimeStamp())).intValue();
            }else{
                return (new Long(bm1.getTimeStamp() - bm2.getTimeStamp())).intValue();
            }*/
            if(this.newestFirst){
                if(bm2.getTimeStamp() - bm1.getTimeStamp() >0)
                    return 1;
                else
                    return -1;
            }else{
                if(bm1.getTimeStamp() - bm2.getTimeStamp() >0)
                    return 1;
                else
                    return -1;
            }
        }
    }
    /**
     * sorts the tag for name
     */
    public class tagComparator implements Comparator{
    	public int compare(Object obj1, Object obj2){
    		return ((Tag)obj1).getTagName().compareTo(((Tag)obj2).getTagName());
    	}
    }
}
