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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMap;
import de.anomic.plasma.plasmaURL;

public class bookmarksDB {
    kelondroMap tagsTable;
    kelondroMap bookmarksTable;
    
    public bookmarksDB(File bookmarksFile, File tagsFile, int bufferkb){
        if(bookmarksFile.exists() && tagsFile.exists()){
            try {
                this.bookmarksTable=new kelondroMap(new kelondroDyn(bookmarksFile, 1024*bufferkb));
                this.tagsTable=new kelondroMap(new kelondroDyn(tagsFile, 1024*bufferkb));
            } catch (IOException e) {
                //TODO: check if both are corrupted
                bookmarksFile.delete();
                bookmarksFile.getParentFile().mkdirs();
                this.bookmarksTable = new kelondroMap(new kelondroDyn(bookmarksFile, bufferkb * 1024, 128, 256, true));
                tagsFile.delete();
                tagsFile.getParentFile().mkdirs();
                this.tagsTable = new kelondroMap(new kelondroDyn(tagsFile, bufferkb * 1024, 128, 256, true));
            }

        }else{
            bookmarksFile.getParentFile().mkdirs();
            this.bookmarksTable = new kelondroMap(new kelondroDyn(bookmarksFile, bufferkb * 1024, 128, 256, true));
            tagsFile.getParentFile().mkdirs();
            this.tagsTable = new kelondroMap(new kelondroDyn(tagsFile, bufferkb * 1024, 128, 256, true));
        }
    }
    public void close(){
        try {
            bookmarksTable.close();
        } catch (IOException e) {}
        try {
            tagsTable.close();
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
    //TODO: Move it in listmanager?
    private String vector2string(Vector vector){
        Iterator it=vector.iterator();
        String ret="";
        if(it.hasNext()){
            ret=(String) it.next();
            while(it.hasNext()){
                ret+=","+(String)it.next();
            }
        }
        return ret;
    }
    private Vector string2vector(String string){
        Vector ret=new Vector();
        String[] hashes=string.split(",");
        if(string.indexOf(",") > -1){
            for(int i=0;i<hashes.length;i++){
                ret.add(hashes[i]);
            }
        }else{
            ret = new Vector();
            ret.add(string);
        }
        return ret;
    }
    public Tag getTag(String tagName){
        Map map;
        try {
            map = tagsTable.get(tagName);
            if(map==null) return null;
            return new Tag(tagName, map);
        } catch (IOException e) {
            return null;
        }
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
    public Iterator getBookmarksIterator(){
        TreeSet set=new TreeSet(new bookmarkComparator());
        Iterator it=bookmarkIterator(true);
        Bookmark bm;
        while(it.hasNext()){
            bm=(Bookmark)it.next();
            set.add(bm.getUrlHash());
        }
        return set.iterator();
    }
    public Iterator getBookmarksIterator(String tag){
        TreeSet set=new TreeSet(new bookmarkComparator());
        Vector hashes=getTag(tag).getUrlHashes();
        set.addAll(hashes);
        return set.iterator();
    }
    public void removeBookmark(String urlHash){
        Bookmark bookmark = getBookmark(urlHash);
        if(bookmark == null) return; //does not exist
        String[] tags = bookmark.getTags().split(",");
        bookmarksDB.Tag tag;
        for(int i=0;i<tags.length;i++){
            tag=getTag(tags[i]);
            tag.delete(urlHash);
            tag.setTagsTable();
        }
        try {
            bookmarksTable.remove(urlHash);
        } catch (IOException e) {}
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

    /**
     * Subclass, which stores an Tag
     *
     */
    public class Tag{
        public static final String URL_HASHES="urlHashes";
        private String tagName;
        private Map mem;
        public Tag(String name, Map map){
            tagName=name;
            mem=map;
        }
        public Tag(String name, Vector entries){
            tagName=name;
            mem=new HashMap();
            mem.put(URL_HASHES, vector2string(entries));
        }
        public Tag(String name){
            tagName=name;
            mem=new HashMap();
            mem.put(URL_HASHES, "");
        }
        public String getTagName(){
            return tagName;
        }
        public Vector getUrlHashes(){
            System.out.println(this.mem.get(URL_HASHES));
            return string2vector((String)this.mem.get(URL_HASHES));
        }
        public void add(String urlHash){
            String urlHashes = (String)mem.get(URL_HASHES);
            /*Vector list;
            if(urlHashes != null){
                list=string2vector(urlHashes);
            }else{
                list=new Vector();
            }
            list.add(urlHash);
            this.mem.put(URL_HASHES, vector2string(list));*/
            if(urlHashes!=null && !urlHashes.equals("")){
                this.mem.put(URL_HASHES, urlHashes+","+urlHash);
            }else{
                this.mem.put(URL_HASHES, urlHash);
            }
        }
        public void delete(String urlHash){
            Vector list=string2vector((String) this.mem.get(URL_HASHES));
            if(list.contains(urlHash)){
                list.remove(urlHash);
            }
            this.mem.put(URL_HASHES, vector2string(list));
        }
        public void setTagsTable(){
            try {
                if(this.size() >0){
                    bookmarksDB.this.tagsTable.set(getTagName(), mem);
                }else{
                    bookmarksDB.this.tagsTable.remove(getTagName());
                }
            } catch (IOException e) {}
        }
        public int size(){
            return ((String)this.mem.get(URL_HASHES)).length();
        }
    }
    /**
     * Subclass, which stores the bookmark
     *
     */
    public class Bookmark{
        public static final String BOOKMARK_URL="bookmarkUrl";
        public static final String BOOKMARK_TITLE="bookmarkTitle";
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
        public String getTitle(){
            if(this.mem.containsKey(BOOKMARK_TITLE)){
                return (String) this.mem.get(BOOKMARK_TITLE);
            }
            return (String) this.mem.get(BOOKMARK_URL);
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
            mem.put(BOOKMARK_TAGS, vector2string(tags));
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
                bookmarksDB.this.bookmarksTable.set(getUrlHash(), mem);
            } catch (IOException e) {}
        }
        public long getTimeStamp(){
            if(mem.containsKey(BOOKMARK_TIMESTAMP)){
                return Long.parseLong((String)mem.get(BOOKMARK_TIMESTAMP));
            }else{
                return 0;
            }
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
