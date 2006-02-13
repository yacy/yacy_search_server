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
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMap;
import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaWordIndexEntry;
import de.anomic.server.logging.serverLog;

public class bookmarksDB {
    kelondroMap tagsTable;
    kelondroMap bookmarksTable;
    kelondroMap datesTable;
    
    public static String dateToiso8601(Date date){
    	return new SimpleDateFormat("yyyy-MM-dd").format(date)+"T"+(new SimpleDateFormat("HH:mm:ss")).format(date)+"Z";
    }
    public static String tagHash(String tagName){
    	return plasmaWordIndexEntry.word2hash(tagName.toLowerCase());
    }
    public static Date iso8601ToDate(String iso8601){
    	String[] tmp=iso8601.split("T");
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
    
    public bookmarksDB(File bookmarksFile, File tagsFile, File datesFile, int bufferkb){
        //bookmarks
        //check if database exists
        if(bookmarksFile.exists()){
            try {
                //open it
                this.bookmarksTable=new kelondroMap(new kelondroDyn(bookmarksFile, 1024*bufferkb, '_'));
            } catch (IOException e) {
                //database reset :-((
                bookmarksFile.delete();
                bookmarksFile.getParentFile().mkdirs();
                //urlHash is 12 bytes long
                this.bookmarksTable = new kelondroMap(new kelondroDyn(bookmarksFile, bufferkb * 1024, 12, 256, '_', true));
            }
        }else{
            //new database
            bookmarksFile.getParentFile().mkdirs();
            this.bookmarksTable = new kelondroMap(new kelondroDyn(bookmarksFile, bufferkb * 1024, 12, 256, '_', true));
        }
        //tags
        //check if database exists
        if(tagsFile.exists()){
            try {
                //open it
                this.tagsTable=new kelondroMap(new kelondroDyn(tagsFile, 1024*bufferkb, '_'));
            } catch (IOException e) {
                //reset database
                tagsFile.delete();
                tagsFile.getParentFile().mkdirs();
                // max. 128 byte long tags
                this.tagsTable = new kelondroMap(new kelondroDyn(tagsFile, bufferkb * 1024, 128, 256, '_', true));
                rebuildTags();
            }
        }else{
            //new database
            tagsFile.getParentFile().mkdirs();
            this.tagsTable = new kelondroMap(new kelondroDyn(tagsFile, bufferkb * 1024, 128, 256, '_', true));
            rebuildTags();
        }
        // dates
        //check if database exists
        if(datesFile.exists()){
            try {
                //open it
                this.datesTable=new kelondroMap(new kelondroDyn(datesFile, 1024*bufferkb, '_'));
            } catch (IOException e) {
                //reset database
                datesFile.delete();
                datesFile.getParentFile().mkdirs();
                //YYYY-MM-DDTHH:mm:ssZ = 20 byte. currently used: YYYY-MM-DD = 10 bytes
                this.datesTable = new kelondroMap(new kelondroDyn(datesFile, bufferkb * 1024, 20, 256, '_', true));
                rebuildDates();
            }
        }else{
            //new database
            datesFile.getParentFile().mkdirs();
            this.datesTable = new kelondroMap(new kelondroDyn(datesFile, bufferkb * 1024, 20, 256, '_', true));
            rebuildDates();
        }
    }
    public void close(){
        try {
            bookmarksTable.close();
        } catch (IOException e) {}
        try {
            tagsTable.close();
        } catch (IOException e) {}
        try {
            datesTable.close();
        } catch (IOException e) {}
    }
    public int bookmarksSize(){
        return bookmarksTable.size();
    }
    public int tagsSize(){
        return tagsTable.size();
    }
    public String addTag(Tag tag){
        try {
            tagsTable.set(tag.getTagName(), tag.mem);
            return tag.getTagName();
        } catch (IOException e) {
            return null;
        }
    }
    public void rebuildTags(){
        serverLog.logInfo("BOOKMARKS", "rebuilding tags.db from bookmarks.db...");
        Iterator it=bookmarkIterator(true);
        Bookmark bookmark;
        Tag tag;
        String[] tags;
        while(it.hasNext()){
            bookmark=(Bookmark) it.next();
            tags = bookmark.getTags().split(",");
            tag=null;
            for(int i=0;i<tags.length;i++){
                tag=getTag(tagHash(tags[i]));
                if(tag==null){
                    tag=new Tag(tags[i]);
                }
                tag.add(bookmark.getUrlHash());
                tag.setTagsTable();
            }
        }
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
        Map map;
        try {
            map = tagsTable.get(hash);
            if(map==null) return null;
            return new Tag(hash, map);
        } catch (IOException e) {
            return null;
        }
    }
    public bookmarksDate getDate(String date){
        Map map;
        try {
            map=datesTable.get(date);
            if(map==null) return new bookmarksDate(date);
            return new bookmarksDate(date, map);
        } catch (IOException e) {
            return null;
        }
        
    }
    public boolean renameTag(String oldName, String newName){
    	String tagHash=tagHash(oldName);
        Tag tag=getTag(tagHash);
            if (tag != null) {
            Vector urlHashes = tag.getUrlHashes();
            try {
                tagsTable.remove(tagHash(oldName));
            } catch (IOException e) {
            }
            tag=new Tag(tagHash(newName), tag.mem);
            tag.tagHash = tagHash(newName);
            tag.setTagsTable();
            Iterator it = urlHashes.iterator();
            Bookmark bookmark;
            Vector tags;
            while (it.hasNext()) {
                bookmark = getBookmark((String) it.next());
                tags = listManager.string2vector(bookmark.getTags());
                tags.remove(oldName);
                tags.add(newName);
                bookmark.setTags(tags);
                bookmark.setBookmarksTable();
            }
            return true;
        }
        return false;
    }
    public void removeTag(String tagName){
        try {
            tagsTable.remove(tagName);
        } catch (IOException e) {}
    }
    public String addBookmark(Bookmark bookmark){
        try {
            bookmarksTable.set(bookmark.getUrlHash(), bookmark.mem);
            return bookmark.getUrlHash();
        } catch (IOException e) {
            return null;
        }    
    }
    public Bookmark getBookmark(String urlHash){
        Map map;
        try {
            map = bookmarksTable.get(urlHash);
            if(map==null) return null;
            return new Bookmark(urlHash, map);
        } catch (IOException e) {
            return null;
        }
    }
    public Iterator getBookmarksIterator(boolean priv){
        TreeSet set=new TreeSet(new bookmarkComparator());
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
        TreeSet set=new TreeSet(new bookmarkComparator());
        String tagHash=tagHash(tagName);
        Tag tag=getTag(tagHash);
        Vector hashes=new Vector();
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
    	if(priv){
    		return tagIterator(true);
    	}else{
    		Vector publicTags=new Vector();
    		Iterator it=tagIterator(true);
    		Tag tag;
    		while(it.hasNext()){
    			tag=(Tag)it.next();
    			//this may be slow...
    			if(tag.hasPublicItems()){
    				publicTags.add(tag);
    			}
    		}
    		return publicTags.iterator();
    	}
    }
    public boolean removeBookmark(String urlHash){
        Bookmark bookmark = getBookmark(urlHash);
        if(bookmark == null) return false; //does not exist
        String[] tags = bookmark.getTags().split(",");
        bookmarksDB.Tag tag=null;
        for(int i=0;i<tags.length;i++){
            tag=getTag(tagHash(tags[i]));
            if(tag !=null){
                tag.delete(urlHash);
                tag.setTagsTable();
            }
        }
        try {
            bookmarksTable.remove(urlHash);
            return true;
        } catch (IOException e) {
        	return false;
        }
    }
    public Bookmark createBookmark(String url){
        return new Bookmark(url);
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
    public void addBookmark(String url, String title, Vector tags){
        
    }

    public void importFromXML(String input, boolean isPublic){

		SAXParser parser;
		try {
			ByteArrayInputStream is=new ByteArrayInputStream(input.getBytes());
			parser = SAXParserFactory.newInstance().newSAXParser();
			xmlImportHandler handler=new xmlImportHandler(isPublic);
			parser.parse(is, handler);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FactoryConfigurationError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    public class xmlImportHandler extends DefaultHandler{
    	boolean importPublic;
    	public xmlImportHandler(boolean isPublic){
    		importPublic=isPublic;
    	}
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			if (qName.equals("post")) {
				String url=attributes.getValue("href");
				if(url.equals("")){
					return;
				}
				Bookmark bm = new Bookmark(url);
				String tagsString=attributes.getValue("tag").replace(' ', ',');
				String title=attributes.getValue("description");
				String description=attributes.getValue("extended");
				String time=attributes.getValue("time");
				Vector tags=new Vector();
				
				if(title != null){
					bm.setProperty(Bookmark.BOOKMARK_TITLE, title);
				}
				if(tagsString!=null){
					tags = listManager.string2vector(tagsString);
				}
				bm.setTags(tags);
				if(time != null){
					bm.setTimeStamp(iso8601ToDate(time).getTime());
				}
				if(description!=null){
					bm.setProperty(Bookmark.BOOKMARK_DESCRIPTION, description);
				}
				bm.setPublic(importPublic);
				bm.setBookmarksTable();
			}
		}
    }
    
    /**
     * Subclass, which stores an Tag
     *
     */
    public class Tag{
        public static final String URL_HASHES="urlHashes";
        public static final String TAG_FRIENDLY_NAME="friendlyName";
        public static final String TAG_NAME="tagName";
        private String tagHash;
        private Map mem;

        public Tag(String hash, Map map){
        	tagHash=hash;
            mem=map;
        }
        public Tag(String name, Vector entries){
            String tagName=name.toLowerCase();
            tagHash=plasmaWordIndexEntry.word2hash(tagName);
            mem=new HashMap();
            mem.put(URL_HASHES, listManager.vector2string(entries));
            mem.put(TAG_NAME, tagName);
            if(!name.equals(tagName)){
                mem.put(TAG_FRIENDLY_NAME, name);
            }
        }
        public Tag(String name){
            String tagName=name.toLowerCase();
            tagHash=plasmaWordIndexEntry.word2hash(tagName);
            mem=new HashMap();
            mem.put(URL_HASHES, "");
            mem.put(TAG_NAME, tagName);
            if(!name.equals(tagName)){
                mem.put(TAG_FRIENDLY_NAME, name);
            }
        }
        public String getTagName(){
        	if(this.mem.containsKey(TAG_NAME)){
                return (String) this.mem.get(TAG_NAME);
            }
            return "";
        }
        public String getTagHash(){
            return tagHash;
        }
        public String getFriendlyName(){
            if(this.mem.containsKey(TAG_FRIENDLY_NAME)){
                return (String) this.mem.get(TAG_FRIENDLY_NAME);
            }
            return getTagName();
        }
        public Vector getUrlHashes(){
            return listManager.string2vector((String)this.mem.get(URL_HASHES));
        }
        public boolean hasPublicItems(){
        	Iterator it=getBookmarksIterator(this.getTagName(), false);
        	if(it.hasNext()){
        		return true;
        	}
        	return false;
        }
        public void add(String urlHash){
            String urlHashes = (String)mem.get(URL_HASHES);
            Vector list;
            if(urlHashes != null && !urlHashes.equals("")){
                list=listManager.string2vector(urlHashes);
            }else{
                list=new Vector();
            }
            if(!list.contains(urlHash) && !urlHash.equals("")){
                list.add(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.vector2string(list));
            /*if(urlHashes!=null && !urlHashes.equals("") ){
                if(urlHashes.indexOf(urlHash) <0){
                    this.mem.put(URL_HASHES, urlHashes+","+urlHash);
                }
            }else{
                this.mem.put(URL_HASHES, urlHash);
            }*/
        }
        public void delete(String urlHash){
            Vector list=listManager.string2vector((String) this.mem.get(URL_HASHES));
            if(list.contains(urlHash)){
                list.remove(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.vector2string(list));
        }
        public void setTagsTable(){
            try {
                if(this.size() >0){
                    bookmarksDB.this.tagsTable.set(getTagHash(), mem);
                }else{
                    bookmarksDB.this.tagsTable.remove(getTagHash());
                }
            } catch (IOException e) {}
        }
        public int size(){
            return listManager.string2vector(((String)this.mem.get(URL_HASHES))).size();
        }
    }
    public class bookmarksDate{
        public static final String URL_HASHES="urlHashes";
        private Map mem;
        String date;
        public bookmarksDate(String mydate){
            date=mydate;
            mem=new HashMap();
            mem.put(URL_HASHES, "");
        }
        public bookmarksDate(String mydate, Map map){
            date=mydate;
            mem=map;
        }
        public bookmarksDate(String mydate, Vector entries){
            date=mydate;
            mem=new HashMap();
            mem.put(URL_HASHES, listManager.vector2string(entries));
        }
        public void add(String urlHash){
            String urlHashes = (String)mem.get(URL_HASHES);
            Vector list;
            if(urlHashes != null && !urlHashes.equals("")){
                list=listManager.string2vector(urlHashes);
            }else{
                list=new Vector();
            }
            if(!list.contains(urlHash) && !urlHash.equals("")){
                list.add(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.vector2string(list));
            /*if(urlHashes!=null && !urlHashes.equals("") ){
                if(urlHashes.indexOf(urlHash) <0){
                    this.mem.put(URL_HASHES, urlHashes+","+urlHash);
                }
            }else{
                this.mem.put(URL_HASHES, urlHash);
            }*/
        }
        public void delete(String urlHash){
            Vector list=listManager.string2vector((String) this.mem.get(URL_HASHES));
            if(list.contains(urlHash)){
                list.remove(urlHash);
            }
            this.mem.put(URL_HASHES, listManager.vector2string(list));
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
        public int size(){
            return listManager.string2vector(((String)this.mem.get(URL_HASHES))).size();
        }
    }
    /**
     * Subclass, which stores the bookmark
     *
     */
    public class Bookmark{
        public static final String BOOKMARK_URL="bookmarkUrl";
        public static final String BOOKMARK_TITLE="bookmarkTitle";
        public static final String BOOKMARK_DESCRIPTION="bookmarkDesc";
        public static final String BOOKMARK_TAGS="bookmarkTags";
        public static final String BOOKMARK_PUBLIC="bookmarkPublic";
        public static final String BOOKMARK_TIMESTAMP="bookmarkTimestamp";
        private String urlHash;
        private Map mem;
        public Bookmark(String urlHash, Map map){
            this.urlHash=urlHash;
            this.mem=map;
        }
        public Bookmark(String url){
            if(!url.toLowerCase().startsWith("http://")){
                url="http://"+url;
            }
            this.urlHash=plasmaURL.urlHash(url);
            mem=new HashMap();
            mem.put(BOOKMARK_URL, url);
            try {
                Map oldmap= bookmarksTable.get(this.urlHash);
                if(oldmap != null && oldmap.containsKey(BOOKMARK_TIMESTAMP)){
                    mem.put(BOOKMARK_TIMESTAMP, oldmap.get(BOOKMARK_TIMESTAMP)); //preserve timestamp on edit
                }else{
                    mem.put(BOOKMARK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                }
                bookmarksDate bmDate=getDate((String) mem.get(BOOKMARK_TIMESTAMP));
                bmDate.add(this.urlHash);
                bmDate.setDatesTable();
                
                removeBookmark(this.urlHash); //prevent empty tags
            } catch (IOException e) {
                //entry not yet present (normal case)
                mem.put(BOOKMARK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            }
        }
        public Bookmark(String urlHash, URL url){
            this.urlHash=urlHash;
            mem=new HashMap();
            mem.put(BOOKMARK_URL, url.toString());
        }
        public Bookmark(String urlHash, String url){
            this.urlHash=urlHash;
            mem=new HashMap();
            mem.put(BOOKMARK_URL, url);
        }
        public String getUrlHash(){
            return urlHash;
        }
        public String getUrl(){
            return (String) this.mem.get(BOOKMARK_URL);
        }
        public String getTags(){
            if(this.mem.containsKey(BOOKMARK_TAGS)){
                return (String)this.mem.get(BOOKMARK_TAGS);
            }
            return "";
        }
        public Vector getTagsVector(){
        	return listManager.string2vector(this.getTags());
        }
        public String getDescription(){
            if(this.mem.containsKey(BOOKMARK_DESCRIPTION)){
                return (String) this.mem.get(BOOKMARK_DESCRIPTION);
            }
            return "";
        }
        public String getTitle(){
            if(this.mem.containsKey(BOOKMARK_TITLE)){
                return (String) this.mem.get(BOOKMARK_TITLE);
            }
            return (String) this.mem.get(BOOKMARK_URL);
        }
        public boolean getPublic(){
            if(this.mem.containsKey(BOOKMARK_PUBLIC)){
                return ((String) this.mem.get(BOOKMARK_PUBLIC)).equals("public");
            }else{
                return false;
            }
        }
        public void setPublic(boolean isPublic){
        	if(isPublic){
        		this.mem.put(BOOKMARK_PUBLIC, "public");
        	}else{
        		this.mem.put(BOOKMARK_PUBLIC, "private");
        	}
        }
        public void setProperty(String name, String value){
            mem.put(name, value);
            //setBookmarksTable();
        }
        public void addTag(String tag){
            Vector tags;
            if(!mem.containsKey(BOOKMARK_TAGS)){
                tags=new Vector();
            }else{
                tags=(Vector)mem.get(BOOKMARK_TAGS);
            }
            tags.add(tag);
            this.setTags(tags);
        }
        public void setTags(Vector tags){
            mem.put(BOOKMARK_TAGS, listManager.vector2string(tags));
            Iterator it=tags.iterator();
            while(it.hasNext()){
                String tagName=(String) it.next();
                Tag tag=getTag(tagName);
                if(tag == null){
                    tag=new Tag(tagName);
                }
                tag.add(getUrlHash());
                tag.setTagsTable();
            }
        }
        public void setBookmarksTable(){
            try {
            	bookmarksDB.this.bookmarksTable.set(urlHash, mem);
            } catch (IOException e) {}
        }
        public long getTimeStamp(){
            if(mem.containsKey(BOOKMARK_TIMESTAMP)){
                return Long.parseLong((String)mem.get(BOOKMARK_TIMESTAMP));
            }else{
                return 0;
            }
        }
        public void setTimeStamp(long timestamp){
        	this.mem.put(BOOKMARK_TIMESTAMP, String.valueOf(timestamp));
        }
    }
    public class tagIterator implements Iterator{
        kelondroDyn.dynKeyIterator tagIter;
        bookmarksDB.Tag nextEntry;
        public tagIterator(boolean up) throws IOException {
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
                    Object tagName = this.nextEntry.getTagName();
                    if (tagName != null) removeTag((String) tagName);
                } catch (kelondroException e) {
                    //resetDatabase();
                }
            }
        }
    }
    public class bookmarkIterator implements Iterator{
        kelondroDyn.dynKeyIterator bookmarkIter;
        bookmarksDB.Bookmark nextEntry;
        public bookmarkIterator(boolean up) throws IOException {
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
        public int compare(Object obj1, Object obj2){
            Bookmark bm1=getBookmark((String)obj1);
            Bookmark bm2=getBookmark((String)obj2);
            //TODO: what happens, if there is a big difference? (to much for int)
            return (new Long(bm1.getTimeStamp() - bm2.getTimeStamp())).intValue();
        }
    }
}
