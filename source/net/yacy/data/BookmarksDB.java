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

package net.yacy.data;

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

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;

public class BookmarksDB {

	// ------------------------------------
	// Declaration of Class-Attributes
	// ------------------------------------

	//final static int SORT_ALPHA = 1;
	private final static int SORT_SIZE = 2;
	private final static int SHOW_ALL = -1;

    // bookmarks
    private final MapHeap bookmarks;

    // tags
    private final ConcurrentHashMap<String, Tag> tags;

    private final BookmarkDate dates;

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
        this.tags = new ConcurrentHashMap<String, Tag>();
        ConcurrentLog.info("BOOKMARKS", "started init of tags from bookmarks.db...");
        final Iterator<Bookmark> it = new bookmarkIterator(true);
        Bookmark bookmark;
        Tag tag;
        String[] tagArray;
        while (it.hasNext()) {
            try {
                bookmark = it.next();
            } catch (final Throwable e) {
                //Log.logException(e);
                continue;
            }
            if (bookmark == null) continue;
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
        ConcurrentLog.info("BOOKMARKS", "finished init " + this.tags.size() + " tags using your "+this.bookmarks.size()+" bookmarks.");

        // dates
        final boolean datesExisted = datesFile.exists();
        //this.datesTable = new MapView(BLOBTree.toHeap(datesFile, true, true, 20, 256, '_', NaturalOrder.naturalOrder, datesFileNew), 500, '_');
        this.dates = new BookmarkDate(datesFile);
        if (!datesExisted) this.dates.init(new bookmarkIterator(true));
    }

    // -----------------------------------------------------
    // bookmarksDB's functions for 'destructing' the class
    // -----------------------------------------------------

    public synchronized void close(){
        this.bookmarks.close();
        this.tags.clear();
        this.dates.close();
    }

    // -----------------------------------------------------------
    // bookmarksDB's functions for bookmarksTable / bookmarkCache
    // -----------------------------------------------------------

    public Bookmark createBookmark(final String url, final String user){
        if (url == null || url.isEmpty()) return null;
        Bookmark bk;
        try {
            bk = new Bookmark(url);
            bk.setOwner(user);
            return (bk.getUrlHash() == null || bk.toMap() == null) ? null : bk;
        } catch (final MalformedURLException e) {
            return null;
        }
    }

    // returning the number of bookmarks
    public int bookmarksSize(){
        return this.bookmarks.size();
    }

    // adding a bookmark to the bookmarksDB
    public void saveBookmark(final Bookmark bookmark){
    	try {
            this.bookmarks.insert(ASCII.getBytes(bookmark.getUrlHash()), bookmark.entry);
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

    public Bookmark getBookmark(final String urlHash) throws IOException {
        try {
            final Map<String, String> map = this.bookmarks.get(ASCII.getBytes(urlHash));
            if (map == null) throw new IOException("cannot get bookmark for url hash " + urlHash);
            return new Bookmark(map);
        } catch (final Throwable e) {
            throw new IOException(e.getMessage());
        }
    }

    public boolean removeBookmark(final String urlHash){
        Bookmark bookmark;
        try {
            bookmark = getBookmark(urlHash);
        } catch (final IOException e1) {
            return false;
        }
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
            this.bookmarks.delete(ASCII.getBytes(urlHash));
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
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
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
        RowHandleSet hashes = tag == null ? new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 10) : tag.getUrlHashes();
        if (priv) {
            for (byte[] hash: hashes) set.add(ASCII.String(hash));
        } else {
            for (byte[] hash: hashes) {
                try {
                    Bookmark bm = getBookmark(ASCII.String(hash));
                    if (bm != null && bm.getPublic()) {
                        set.add(bm.getUrlHash());
                    }
                } catch (final IOException e) {
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
    private Tag getTag(final String hash){
        return this.tags.get(hash); //null if it does not exists
    }

    /**
     * store a Tag in tagsTable or remove an empty tag
     * @param tag an object of type Tag to be stored/removed
     */
    private void putTag(final Tag tag){
    	if (tag == null) return;
        if (tag.isEmpty()) {
            this.tags.remove(tag.getTagHash());
        } else {
            this.tags.put(tag.getTagHash(), tag);
        }
    }

    private void removeTag(final String hash) {
        this.tags.remove(hash);
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

    private Iterator<Tag> getTagIterator(final String tagName, final boolean priv, final int comp) {
    	final TreeSet<Tag> set=new TreeSet<Tag>((comp == SORT_SIZE) ? tagSizeComparator : tagComparator);
    	Iterator<String> it=null;
    	final Iterator<String> bit=getBookmarksIterator(tagName, priv);
    	Bookmark bm;
    	Tag tag;
    	Set<String> tagSet;
    	while (bit.hasNext()) {
            try {
                bm = getBookmark(bit.next());
                if (bm == null) continue;
                tagSet = bm.getTags();
                it = tagSet.iterator();
                while (it.hasNext()) {
                    tag=getTag(BookmarkHelper.tagHash(it.next()) );
                    if((priv ||tag.hasPublicItems()) && tag != null){
                            set.add(tag);
                    }
                }
            } catch (final IOException e) {
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
            final RowHandleSet urlHashes = oldTag.getUrlHashes();	// preserve urlHashes of oldTag
            removeTag(BookmarkHelper.tagHash(oldName));							// remove oldHash from TagsDB

            Bookmark bookmark;
            Set<String> tagSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            for (final byte[] urlHash : urlHashes) {									// looping through all bookmarks which were tagged with oldName
                try {
                    bookmark = getBookmark(ASCII.String(urlHash));
                    tagSet = bookmark.getTags();
                    tagSet.remove(oldName);
                    bookmark.setTags(tagSet, true);                     // might not be needed, but doesn't hurt
                    if(!"".equals(newName)) bookmark.addTag(newName);
                    saveBookmark(bookmark);
                } catch (final IOException e) {
                }
            }
            return true;
    	}
    	return false;
    }

    public void addTag(final String selectTag, final String newTag) {

    	Bookmark bookmark;
    	for (final byte[] urlHash : getTag(BookmarkHelper.tagHash(selectTag)).getUrlHashes()) {	// looping through all bookmarks which were tagged with selectTag
            try {
                bookmark = getBookmark(ASCII.String(urlHash));
                bookmark.addTag(newTag);
                saveBookmark(bookmark);
            } catch (final IOException e) {
            }
        }
    }

    // --------------------------------------
	// bookmarksDB's Subclasses
	// --------------------------------------

    /**
     * Subclass of bookmarksDB, which provides the Tag object-type
     */
    public class Tag {
        private final String tagHash;
        private final String tagName;
        private RowHandleSet urlHashes;

        private Tag(final String name) {
            this.tagHash = BookmarkHelper.tagHash(name);
            this.tagName = name;
            this.urlHashes = new RowHandleSet(Word.commonHashLength, Word.commonHashOrder, 10);
        }

        /**
         * get the lowercase Tagname
         */
        public String getTagName(){
            return getFriendlyName().toLowerCase();
        }

        private String getTagHash(){
            return this.tagHash;
        }

        /**
         * @return the tag name, with all uppercase chars
         */
        public String getFriendlyName(){
            return this.tagName;
        }

        private RowHandleSet getUrlHashes(){
            return this.urlHashes;
        }

        private boolean hasPublicItems(){
            return getBookmarksIterator(getTagName(), false).hasNext();
        }

        private void addUrl(final String urlHash){
            try {
                this.urlHashes.put(ASCII.getBytes(urlHash));
            } catch (SpaceExceededException e) {
            }
        }

        private void delete(final String urlHash){
            this.urlHashes.remove(ASCII.getBytes(urlHash));
        }

        public int size(){
            return this.urlHashes.size();
        }

        private boolean isEmpty() {
            return this.urlHashes.isEmpty();
        }
    }

    /**
     * Subclass of bookmarksDB, which provides the Bookmark object-type
     */
    public class Bookmark {

        private static final String BOOKMARK_URL = "bookmarkUrl";
        public static final String BOOKMARK_TITLE = "bookmarkTitle";
        public static final String BOOKMARK_DESCRIPTION = "bookmarkDesc";
        private static final String BOOKMARK_TAGS = "bookmarkTags";
        private static final String BOOKMARK_PUBLIC = "bookmarkPublic";
        private static final String BOOKMARK_TIMESTAMP = "bookmarkTimestamp";
        private static final String BOOKMARK_OWNER = "bookmarkOwner";
        private static final String BOOKMARK_IS_FEED = "bookmarkIsFeed";
        private final String urlHash;
        private Set<String> tagNames;
        private long timestamp;
        private final Map<String, String> entry;

        public Bookmark(final DigestURL url) {
            this.entry = new HashMap<String, String>();
            this.urlHash = ASCII.String(url.hash());
            this.entry.put(BOOKMARK_URL, url.toNormalform(false));
            this.tagNames = new HashSet<String>();
            this.timestamp = System.currentTimeMillis();
            Bookmark oldBm;
            try {
                oldBm = getBookmark(this.urlHash);
                if(oldBm!=null && oldBm.entry.containsKey(BOOKMARK_TIMESTAMP)){
                    this.entry.put(BOOKMARK_TIMESTAMP, oldBm.entry.get(BOOKMARK_TIMESTAMP)); //preserve timestamp on edit
                }else{
                    this.entry.put(BOOKMARK_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
                }
            } catch (final IOException e) {
            }
            final BookmarkDate.Entry bmDate=BookmarksDB.this.dates.getDate(this.entry.get(BOOKMARK_TIMESTAMP));
            bmDate.add(this.urlHash);
            bmDate.setDatesTable();

            removeBookmark(this.urlHash); //prevent empty tags
        }

        public Bookmark(final String url) throws MalformedURLException {
            this(new DigestURL((url.indexOf("://") < 0) ? "http://" + url : url));
        }

        private Bookmark(final Map<String, String> map) throws MalformedURLException {
            this.entry = map;
            this.urlHash = ASCII.String((new DigestURL(map.get(BOOKMARK_URL))).hash());
            this.tagNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
            if (map.containsKey(BOOKMARK_TAGS)) this.tagNames.addAll(ListManager.string2set(map.get(BOOKMARK_TAGS)));
            loadTimestamp();
        }

        private Map<String, String> toMap() {
            this.entry.put(BOOKMARK_TAGS, ListManager.collection2string(this.tagNames));
            this.entry.put(BOOKMARK_TIMESTAMP, String.valueOf(this.timestamp));
            return this.entry;
        }

        private void loadTimestamp() {
            if(this.entry.containsKey(BOOKMARK_TIMESTAMP))
                this.timestamp=Long.parseLong(this.entry.get(BOOKMARK_TIMESTAMP));
        }

        public String getUrlHash() {
            return this.urlHash;
        }

        public String getUrl() {
            return this.entry.get(BOOKMARK_URL);
        }

        public Set<String> getTags() {
            return this.tagNames;
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
            if(this.entry.containsKey(BOOKMARK_DESCRIPTION)){
                return this.entry.get(BOOKMARK_DESCRIPTION);
            }
            return "";
        }

        public String getTitle(){
            if(this.entry.containsKey(BOOKMARK_TITLE)){
                return this.entry.get(BOOKMARK_TITLE);
            }
            return this.entry.get(BOOKMARK_URL);
        }

        public String getOwner(){
            if(this.entry.containsKey(BOOKMARK_OWNER)){
                return this.entry.get(BOOKMARK_OWNER);
            }
            return null; //null means admin
        }

        public void setOwner(final String owner){
            this.entry.put(BOOKMARK_OWNER, owner);
        }

        public boolean getPublic(){
            if(this.entry.containsKey(BOOKMARK_PUBLIC)){
                return "public".equals(this.entry.get(BOOKMARK_PUBLIC));
            }
            return false;
        }

        public boolean getFeed(){
            if(this.entry.containsKey(BOOKMARK_IS_FEED)){
                return "true".equals(this.entry.get(BOOKMARK_IS_FEED));
            }
            return false;
        }

        public void setPublic(final boolean isPublic){
            if(isPublic){
                this.entry.put(BOOKMARK_PUBLIC, "public");
            } else {
                this.entry.put(BOOKMARK_PUBLIC, "private");
            }
        }

        public void setFeed(final boolean isFeed){
            if(isFeed){
                this.entry.put(BOOKMARK_IS_FEED, "true");
            }else{
                this.entry.put(BOOKMARK_IS_FEED, "false");
            }
        }

        public void setProperty(final String name, final String value){
            this.entry.put(name, value);
            //setBookmarksTable();
        }

        public void addTag(final String tagName){
            this.tagNames.add(tagName);
            setTags(this.tagNames);
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
            this.tagNames = tags2;			// TODO: check if this is safe
        	// tags.addAll(tags2);	// in order for renameTag() to work I had to change this form 'add' to 'set'
            for (final String tagName : this.tagNames) {
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
            return this.timestamp;
        }

        public void setTimeStamp(final long ts){
        	this.timestamp = ts;
        }
    }


    /**
     * Subclass of bookmarksDB, which provides the bookmarkIterator object-type
     */
    private class bookmarkIterator implements Iterator<Bookmark> {

        Iterator<byte[]> bookmarkIter;

        private bookmarkIterator(final boolean up) throws IOException {
            //flushBookmarkCache(); //XXX: this will cost performance
            this.bookmarkIter = BookmarksDB.this.bookmarks.keys(up, false);
            //this.nextEntry = null;
        }

        @Override
        public boolean hasNext() {
            return this.bookmarkIter.hasNext();
        }

        @Override
        public Bookmark next() {
            try {
                return getBookmark(UTF8.String(this.bookmarkIter.next()));
            } catch (final IOException e) {
                this.bookmarkIter.remove();
                return null;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Comparator to sort objects of type Bookmark according to their timestamps
     */
    private class bookmarkComparator implements Comparator<String> {

        private final boolean newestFirst;

        /**
         * @param newestFirst newest first, or oldest first?
         */
        private bookmarkComparator(final boolean newestFirst){
            this.newestFirst = newestFirst;
        }

        @Override
        public int compare(final String obj1, final String obj2) {
            try {
                Bookmark bm1 = getBookmark(obj1);
                Bookmark bm2 = getBookmark(obj2);
                if (bm1 == null || bm2 == null) {
                    return 0; //XXX: i think this should not happen? maybe this needs further tracing of the bug
                }
                if (this.newestFirst){
                    if (bm2.getTimeStamp() - bm1.getTimeStamp() >0) return 1;
                    return -1;
                }
                if  (bm1.getTimeStamp() - bm2.getTimeStamp() > 0) return 1;
            } catch (final IOException e) {
            }
            return -1;
        }
    }

    private static final TagComparator tagComparator = new TagComparator();
    private static final TagSizeComparator tagSizeComparator = new TagSizeComparator();

    /**
     * Comparator to sort objects of type Tag according to their names
     */
    private static class TagComparator implements Comparator<Tag>, Serializable {

    	/**
         * generated serial
         */
        private static final long serialVersionUID = 3105057490088903930L;

        @Override
        public int compare(final Tag obj1, final Tag obj2){
            return obj1.getTagName().compareTo(obj2.getTagName());
    	}

    }

    private static class TagSizeComparator implements Comparator<Tag>, Serializable {

    	/**
         * generated serial
         */
        private static final long serialVersionUID = 4149185397646373251L;

        @Override
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
        return this.dates.getDate(date);
    }
}
