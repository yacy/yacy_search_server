//bookmarksDB.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.NaturalOrder;

public class BookmarksDB {
    
	// ------------------------------------
	// Declaration of Class-Attributes
	// ------------------------------------

	//final static int SORT_ALPHA = 1;
	private final static int SORT_SIZE = 2;
	private final static int SHOW_ALL = -1;

    // bookmarks
    private MapHeap bookmarks;
    
    // tags
    private ConcurrentHashMap<String, Tag> tags;
    
    private BookmarkDate dates;
    
	// ------------------------------------
	// bookmarksDB's class constructor
	// ------------------------------------

    public BookmarksDB(final File bookmarksFile, final File datesFile) throws IOException {
        
        // bookmarks
        bookmarksFile.getParentFile().mkdirs();
        //this.bookmarksTable = new kelondroMap(kelondroDyn.open(bookmarksFile, bufferkb * 1024, preloadTime, 12, 256, '_', true, false));
        //this.bookmarksTable = new MapView(BLOBTree.toHeap(bookmarksFile, true, true, 12, 256, '_', NaturalOrder.naturalOrder, bookmarksFileNew), 1000, '_');
        this.bookmarks = new MapHeap(bookmarksFile, 12, NaturalOrder.naturalOrder, 1024 * 64, 1000, ' ');
        
        // tags
        tags = new ConcurrentHashMap<String, Tag>();
        Log.logInfo("BOOKMARKS", "started init of tags from bookmarks.db...");
        final Iterator<Bookmark> it = new bookmarkIterator(true);
        Bookmark bookmark;
        Tag tag;
        String[] tagArray;
        while(it.hasNext()){
            bookmark = it.next();
//            if (bookmark == null) continue;
            tagArray = BookmarkHelper.cleanTagsString(bookmark.getTagsString() + bookmark.getFoldersString()).split(",");
            tag = null;
            for (final String element : tagArray) {
                tag = getTag(BookmarkHelper.tagHash(element));
                if (tag == null) {
                    tag = new Tag(element);
                }
                tag.addUrl(bookmark.getUrlHash());
                putTag(tag);
            }
        }
        Log.logInfo("BOOKMARKS", "finished init " + this.tags.size() + " tags using your "+bookmarks.size()+" bookmarks.");        

        // dates
        final boolean datesExisted = datesFile.exists();
        //this.datesTable = new MapView(BLOBTree.toHeap(datesFile, true, true, 20, 256, '_', NaturalOrder.naturalOrder, datesFileNew), 500, '_');
        this.dates = new BookmarkDate(datesFile);
        if (!datesExisted) this.dates.init(new bookmarkIterator(true));
    }

    // -----------------------------------------------------
    // bookmarksDB's functions for 'destructing' the class
    // -----------------------------------------------------
        
    public void close(){
        bookmarks.close();
        tags.clear();
        dates.close();
    }
    
    // -----------------------------------------------------------
    // bookmarksDB's functions for bookmarksTable / bookmarkCache
    // -----------------------------------------------------------
    
    public Bookmark createBookmark(final String url, final String user){
        if (url == null || url.length() == 0) return null;
        final Bookmark bk = new Bookmark(url);
        bk.setOwner(user);
        return (bk.getUrlHash() == null || bk.toMap() == null) ? null : bk;
    }
    
    // returning the number of bookmarks
    public int bookmarksSize(){
        return bookmarks.size();
    }
    
    // adding a bookmark to the bookmarksDB
    public void saveBookmark(final Bookmark bookmark){
    	try {
            bookmarks.insert(UTF8.getBytes(bookmark.getUrlHash()), bookmark.entry);
        } catch (final Exception e) {
            Log.logException(e);
        }
    }
    public String addBookmark(final Bookmark bookmark){
        this.saveBookmark(bookmark);
        return bookmark.getUrlHash();
 
    }
    
    public Bookmark getBookmark(final String urlHash){
        try {
            final Map<String, String> map = bookmarks.get(UTF8.getBytes(urlHash));
            return (map == null) ? null : new Bookmark(map);
        } catch (final IOException e) {
            Log.logException(e);
            return null;
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            return null;
        }
    }
    
    public boolean removeBookmark(final String urlHash){
        final Bookmark bookmark = getBookmark(urlHash);
        if (bookmark == null) return false; //does not exist
        final Set<String> tagSet = bookmark.getTags();
        BookmarksDB.Tag tag=null;
        final Iterator<String> it=tagSet.iterator();
        while(it.hasNext()){
            tag=getTag(BookmarkHelper.tagHash(it.next()));
            if(tag!=null){
                tag.delete(urlHash);
                putTag(tag);
            }
        }
        Bookmark b;
        try {
            b = getBookmark(urlHash);
            bookmarks.delete(UTF8.getBytes(urlHash));
        } catch (final IOException e) {
            b = null;
        }
        return b != null;
    }
    
    public Iterator<String> getBookmarksIterator(final boolean priv) {
        final TreeSet<String> set=new TreeSet<String>(new bookmarkComparator(true));
        Iterator<Bookmark> it;
        try {
            it = new bookmarkIterator(true);
        } catch (IOException e) {
            Log.logException(e);
            return set.iterator();
        }
        Bookmark bm;
        while(it.hasNext()){
            bm=it.next();
//            if (bm == null) continue;
            if(priv || bm.getPublic()){
            	set.add(bm.getUrlHash());
            }
        }
        return set.iterator();
    }
    
    public Iterator<String> getBookmarksIterator(final String tagName, final boolean priv){
        final TreeSet<String> set=new TreeSet<String>(new bookmarkComparator(true));
        final String tagHash=BookmarkHelper.tagHash(tagName);
        final Tag tag=getTag(tagHash);
        Set<String> hashes=new HashSet<String>();
        if(tag != null){
            hashes=getTag(tagHash).getUrlHashes();
        }
        if(priv){
            set.addAll(hashes);
        }else{
        	final Iterator<String> it=hashes.iterator();
            Bookmark bm;
            while(it.hasNext()){
                bm = getBookmark(it.next());
                if (bm != null && bm.getPublic()) {
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
    public int tagsSize(){
        return this.tags.size();
    }
    
    /**
     * retrieve an object of type Tag from the the tagCache, if object is not cached return loadTag(hash)
     * @param hash an object of type String, containing a tagHash
     */
    public Tag getTag(final String hash){
        return this.tags.get(hash); //null if it does not exists
    }
    
    /**
     * store a Tag in tagsTable or remove an empty tag
     * @param tag an object of type Tag to be stored/removed
     */
    public void putTag(final Tag tag){
    	if (tag == null) return;
        if (tag.size() > 0) {
            this.tags.put(tag.getTagHash(), tag);
        } else {
            this.tags.remove(tag.getTagHash());
        }
    }
    
    public void removeTag(final String hash) {
        tags.remove(hash);
    }
    
    public Iterator<Tag> getTagIterator(final boolean priv) {
    	return getTagIterator(priv, 1);
    }
    
    private Iterator<Tag> getTagIterator(final boolean priv, final int c) {
    	final Set<Tag> set = new TreeSet<Tag>((c == SORT_SIZE) ? tagSizeComparator : tagComparator);
    	final Iterator<Tag> it = this.tags.values().iterator();
    	Tag tag;
    	while (it.hasNext()) {
            tag=it.next();
            if (tag == null) continue;
    		if (priv ||tag.hasPublicItems()) {
                set.add(tag);
            }
    	}    	  		
    	return set.iterator();
    }
    
    public Iterator<Tag> getTagIterator(final boolean priv, final int comp, final int max){
    	if (max==SHOW_ALL) 
    		return getTagIterator(priv, comp);    	
    	final Iterator<Tag> it = getTagIterator(priv, SORT_SIZE);
    	final TreeSet<Tag> set=new TreeSet<Tag>((comp == SORT_SIZE) ? tagSizeComparator : tagComparator);
    	int count = 0;
    	while (it.hasNext() && count<=max) {
            set.add(it.next());
            count++;
    	}    	
    	return set.iterator();
    }
    
    public Iterator<Tag> getTagIterator(final String tagName, final boolean priv, final int comp) {
    	final TreeSet<Tag> set=new TreeSet<Tag>((comp == SORT_SIZE) ? tagSizeComparator : tagComparator);
    	Iterator<String> it=null;
    	final Iterator<String> bit=getBookmarksIterator(tagName, priv);
    	Bookmark bm;
    	Tag tag;
    	Set<String> tagSet;
    	while(bit.hasNext()){
            bm=getBookmark(bit.next());
            tagSet = bm.getTags();
            it = tagSet.iterator();
            while (it.hasNext()) {
                tag=getTag(BookmarkHelper.tagHash(it.next()) );
                if((priv ||tag.hasPublicItems()) && tag != null){
                        set.add(tag);
                }
            }
    	}
    	return set.iterator();
    }
    
    public Iterator<Tag> getTagIterator(final String tagName, final boolean priv, final int comp, final int max) {
    	if (max==SHOW_ALL) return getTagIterator(priv, comp);
            final Iterator<Tag> it = getTagIterator(tagName, priv, SORT_SIZE);
            final TreeSet<Tag> set=new TreeSet<Tag>((comp == SORT_SIZE) ? tagSizeComparator : tagComparator);
            int count = 0;
            while (it.hasNext() && count<=max) {
                set.add(it.next());
                count++;
            }
    	return set.iterator();
    }    

  
    // -------------------------------------
	// bookmarksDB's experimental functions
	// -------------------------------------
    
    public boolean renameTag(final String oldName, final String newName){
 	
    	final Tag oldTag=getTag(BookmarkHelper.tagHash(oldName));
    	if (oldTag != null) {
            final Set<String> urlHashes = oldTag.getUrlHashes();	// preserve urlHashes of oldTag
            removeTag(BookmarkHelper.tagHash(oldName));							// remove oldHash from TagsDB
	
            Bookmark bookmark;
            Set<String> tagSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            for (String urlHash : urlHashes) {									// looping through all bookmarks which were tagged with oldName
                bookmark = getBookmark(urlHash);
                tagSet = bookmark.getTags();
                tagSet.remove(oldName);
                bookmark.setTags(tagSet, true);						// might not be needed, but doesn't hurt
                if(!"".equals(newName)) bookmark.addTag(newName);
                saveBookmark(bookmark);
            }
            return true;
    	}
    	return false;
    }
    
    public void addTag(final String selectTag, final String newTag) {    		
    
    	Bookmark bookmark;
    	for (final String urlHash : getTag(BookmarkHelper.tagHash(selectTag)).getUrlHashes()) {	// looping through all bookmarks which were tagged with selectTag
            bookmark = getBookmark(urlHash);
            bookmark.addTag(newTag);
            saveBookmark(bookmark);
        }
    }
    
    // --------------------------------------
	// bookmarksDB's Subclasses
	// --------------------------------------
        
    /**
     * Subclass of bookmarksDB, which provides the Tag object-type
     */
    public class Tag {
        public static final String URL_HASHES = "urlHashes";
        public static final String TAG_NAME =  "tagName";
        private final String tagHash;
        private final Map<String, String> mem;
        private Set<String> urlHashes;

        public Tag(final String hash, final Map<String, String> map){
            tagHash = hash;
            mem = map;
            if (mem.containsKey(URL_HASHES)) {
                urlHashes = ListManager.string2set(mem.get(URL_HASHES));
            } else {
                urlHashes = new HashSet<String>();
            }
        }
        
        public Tag(final String name, final HashSet<String> entries){
            tagHash = BookmarkHelper.tagHash(name);
            mem = new HashMap<String, String>();
            //mem.put(URL_HASHES, listManager.arraylist2string(entries));
            urlHashes = entries;
            mem.put(TAG_NAME, name);
        }
        
        public Tag(final String name){
            this(name, new HashSet<String>());
        }
        
        public Map<String, String> getMap(){
            mem.put(URL_HASHES, ListManager.collection2string(this.urlHashes));
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
            return getBookmarksIterator(this.getTagName(), false).hasNext();
        }
        
        public void addUrl(final String urlHash){
            urlHashes.add(urlHash);
        }
        
        public void delete(final String urlHash){
            urlHashes.remove(urlHash);
        }
        
        public int size(){
            return urlHashes.size();
        }
    }
    
    /**
     * Subclass of bookmarksDB, which provides the Bookmark object-type
     */
    public class Bookmark {
        
        public static final String BOOKMARK_URL = "bookmarkUrl";
        public static final String BOOKMARK_TITLE = "bookmarkTitle";
        public static final String BOOKMARK_DESCRIPTION = "bookmarkDesc";
        public static final String BOOKMARK_TAGS = "bookmarkTags";
        public static final String BOOKMARK_PUBLIC = "bookmarkPublic";
        public static final String BOOKMARK_TIMESTAMP = "bookmarkTimestamp";
        public static final String BOOKMARK_OWNER = "bookmarkOwner";
        public static final String BOOKMARK_IS_FEED = "bookmarkIsFeed";
        private String urlHash;
        private Set<String> tagNames;
        private long timestamp;
        private Map<String, String> entry;
        
        public Bookmark(final String urlHash, final Map<String, String> map) {
            entry = map;
            this.urlHash = urlHash;
            tagNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            if (map.containsKey(BOOKMARK_TAGS)) tagNames.addAll(ListManager.string2set(map.get(BOOKMARK_TAGS)));
            loadTimestamp();
        }

        public Bookmark(final String urlHash, final String url) {
            entry = new HashMap<String, String>();
            this.urlHash = urlHash;
            entry.put(BOOKMARK_URL, url);
            tagNames = new HashSet<String>();
            timestamp = System.currentTimeMillis();
        }

        public Bookmark(final String urlHash, final DigestURI url) {
            this(urlHash, url.toNormalform(false, true));
        }

        public Bookmark(String url){
            entry = new HashMap<String, String>();
            if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
                url="http://"+url;
            }
            try {
                this.urlHash = UTF8.String((new DigestURI(url)).hash());
            } catch (final MalformedURLException e) {
                this.urlHash = null;
            }
            entry.put(BOOKMARK_URL, url);
            this.timestamp=System.currentTimeMillis();
            tagNames=new HashSet<String>();
            final Bookmark oldBm=getBookmark(this.urlHash);
            if(oldBm!=null && oldBm.entry.containsKey(BOOKMARK_TIMESTAMP)){
                entry.put(BOOKMARK_TIMESTAMP, oldBm.entry.get(BOOKMARK_TIMESTAMP)); //preserve timestamp on edit
            }else{
                entry.put(BOOKMARK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
            }  
            final BookmarkDate.Entry bmDate=dates.getDate(entry.get(BOOKMARK_TIMESTAMP));
            bmDate.add(this.urlHash);
            bmDate.setDatesTable();
            
            removeBookmark(this.urlHash); //prevent empty tags
        }
        
        public Bookmark(final Map<String, String> map) throws MalformedURLException {
            this(UTF8.String((new DigestURI(map.get(BOOKMARK_URL))).hash()), map);
        }
        
        Map<String, String> toMap() {
            entry.put(BOOKMARK_TAGS, ListManager.collection2string(tagNames));
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
            return tagNames;
        }
        
        public String getTagsString() {        	
            final String s[] = ListManager.collection2string(getTags()).split(",");
            final StringBuilder stringBuilder = new StringBuilder();
            for (final String element : s){
                if(!element.startsWith("/")){
                    stringBuilder.append(element);
                    stringBuilder.append(",");
                }
            }
            return stringBuilder.toString();
        }
        
        public String getFoldersString(){
            final String s[] = ListManager.collection2string(getTags()).split(",");
            final StringBuilder stringBuilder = new StringBuilder();
            for (final String element : s){
                if(element.startsWith("/")){
                    stringBuilder.append(element);
                    stringBuilder.append(",");
                }
            }
            return stringBuilder.toString();
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
        
        public void setOwner(final String owner){
            entry.put(BOOKMARK_OWNER, owner);
        }
        
        public boolean getPublic(){
            if(entry.containsKey(BOOKMARK_PUBLIC)){
                return "public".equals(entry.get(BOOKMARK_PUBLIC));
            }
            return false;
        }
        
        public boolean getFeed(){
            if(entry.containsKey(BOOKMARK_IS_FEED)){
                return "true".equals(entry.get(BOOKMARK_IS_FEED));
            }
            return false;
        }
        
        public void setPublic(final boolean isPublic){
            if(isPublic){
                entry.put(BOOKMARK_PUBLIC, "public");
            } else {
                entry.put(BOOKMARK_PUBLIC, "private");
            }
        }
        
        public void setFeed(final boolean isFeed){
            if(isFeed){
                entry.put(BOOKMARK_IS_FEED, "true");
            }else{
                entry.put(BOOKMARK_IS_FEED, "false");
            }
        }
        
        public void setProperty(final String name, final String value){
            entry.put(name, value);
            //setBookmarksTable();
        }
        
        public void addTag(final String tagName){
            tagNames.add(tagName);
            setTags(tagNames);
            saveBookmark(this);
        }
        
        /**
         * set the Tags of the bookmark, and write them into the tags table.
         * @param tags2 a ArrayList with the tags
         */
        public void setTags(final Set<String> tags2){
            setTags(tags2, true);
        }
        
        /**
         * set the Tags of the bookmark
         * @param tagNames ArrayList with the tagnames
         * @param local sets, whether the updated tags should be stored to tagsDB
         */
        public void setTags(final Set<String> tags2, final boolean local){
            tagNames = tags2;			// TODO: check if this is safe
        	// tags.addAll(tags2);	// in order for renameTag() to work I had to change this form 'add' to 'set'
            for (final String tagName : tagNames) {
                Tag tag = getTag(BookmarkHelper.tagHash(tagName));
                if (tag == null) {
                    tag = new Tag(tagName);
                }
                tag.addUrl(getUrlHash());
                if (local) {
                    putTag(tag);
                }
            }
            toMap();
        }

        public long getTimeStamp(){
            return timestamp;
        }
        
        public void setTimeStamp(final long ts){
        	this.timestamp = ts;
        }
    }
    
    
    /**
     * Subclass of bookmarksDB, which provides the bookmarkIterator object-type
     */
    public class bookmarkIterator implements Iterator<Bookmark> {

        Iterator<byte[]> bookmarkIter;
        
        public bookmarkIterator(final boolean up) throws IOException {
            //flushBookmarkCache(); //XXX: this will cost performance
            this.bookmarkIter = BookmarksDB.this.bookmarks.keys(up, false);
            //this.nextEntry = null;
        }
        
        public boolean hasNext() {
            return this.bookmarkIter.hasNext();
        }
        
        public Bookmark next() {
            return getBookmark(UTF8.String(this.bookmarkIter.next()));
        }
        
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    /**
     * Comparator to sort objects of type Bookmark according to their timestamps
     */
    public class bookmarkComparator implements Comparator<String> {
        
        private final boolean newestFirst;
        
        /**
         * @param newestFirst newest first, or oldest first?
         */
        public bookmarkComparator(final boolean newestFirst){
            this.newestFirst = newestFirst;
        }
        
        public int compare(final String obj1, final String obj2) {
            final Bookmark bm1 = getBookmark(obj1);
            final Bookmark bm2 = getBookmark(obj2);
            if (bm1 == null || bm2 == null) {
                return 0; //XXX: i think this should not happen? maybe this needs further tracing of the bug
            }
            if (this.newestFirst){
                if (bm2.getTimeStamp() - bm1.getTimeStamp() >0) return 1;
                return -1;
            }
            if  (bm1.getTimeStamp() - bm2.getTimeStamp() > 0) return 1;
            return -1;
        }
    }
    
    public static final TagComparator tagComparator = new TagComparator();
    public static final TagSizeComparator tagSizeComparator = new TagSizeComparator();
    
    /**
     * Comparator to sort objects of type Tag according to their names
     */
    public static class TagComparator implements Comparator<Tag>, Serializable {
        
    	/**
         * generated serial
         */
        private static final long serialVersionUID = 3105057490088903930L;

        public int compare(final Tag obj1, final Tag obj2){
            return obj1.getTagName().compareTo(obj2.getTagName());
    	}
    	
    }
    
    public static class TagSizeComparator implements Comparator<Tag>, Serializable {
        
    	/**
         * generated serial
         */
        private static final long serialVersionUID = 4149185397646373251L;

        public int compare(final Tag obj1, final Tag obj2) {
            if (obj1.size() < obj2.size()) {
                return 1;
            } else if (obj1.getTagName().equals(obj2.getTagName())) {
                return 0;
            } else {
                return -1;
            }
    	}
    	
    }
    
    public BookmarkDate.Entry getDate(final String date) {
        return dates.getDate(date);
    }
}