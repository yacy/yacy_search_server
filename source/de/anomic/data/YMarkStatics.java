package de.anomic.data;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;

public class YMarkStatics {
	
	public final static String TABLE_BOOKMARKS_BASENAME = "_bookmarks";
	public final static String TABLE_BOOKMARKS_LOG = "BOOKMARKS";
	
	public final static String TABLE_BOOKMARKS_USER_ADMIN = "admin";
	public final static String TABLE_BOOKMARKS_USER_AUTHENTICATE = "AUTHENTICATE";
	public final static String TABLE_BOOKMARKS_USER_AUTHENTICATE_MSG = "Authentication required!";
	
    public final static String TABLE_BOOKMARKS_URL_PROTOCOL_HTTP = "http://";
    public final static String TABLE_BOOKMARKS_URL_PROTOCOL_HTTPS = "https://";
	
	public final static String TABLE_BOOKMARKS_COL_ID = "id";
    public final static String TABLE_BOOKMARKS_COL_URL = "url";
    public final static String TABLE_BOOKMARKS_COL_TITLE = "title";
    public final static String TABLE_BOOKMARKS_COL_DESC = "desc";
    public final static String TABLE_BOOKMARKS_COL_DATE_ADDED = "added";
    public final static String TABLE_BOOKMARKS_COL_DATE_MODIFIED = "modified";
    public final static String TABLE_BOOKMARKS_COL_DATE_VISITED = "visited";
    public final static String TABLE_BOOKMARKS_COL_PUBLIC = "public";
    public final static String TABLE_BOOKMARKS_COL_TAGS = "tags";
    
    public final static String TABLE_BOOKMARKS_COL_DEFAULT = "";
    public final static String TABLE_BOOKMARKS_COL_PUBLIC_TRUE = "true";
    public final static String TABLE_BOOKMARKS_COL_PUBLIC_FALSE = "false";
    
	public final static String TABLE_TAGS_BASENAME = "_tags";
	public final static String TABLE_TAGS_SEPARATOR = ",";

	public final static String TABLE_TAGS_COL_ID = "id";
    public final static String TABLE_TAGS_COL_TAG = "tag";
    public final static String TABLE_TAGS_COL_URLS = "urls";
    
    public final static int TABLE_TAGS_ACTION_ADD = 1;
    public final static int TABLE_TAGS_ACTION_REMOVE = 2;
        
    public final static byte[] getBookmarkId(String url) throws MalformedURLException {
    	if (!url.toLowerCase().startsWith(TABLE_BOOKMARKS_URL_PROTOCOL_HTTP) && !url.toLowerCase().startsWith(TABLE_BOOKMARKS_URL_PROTOCOL_HTTPS)) {
            url=TABLE_BOOKMARKS_URL_PROTOCOL_HTTP+url;
        }
		return (new DigestURI(url, null)).hash();
    }
    
    public final static byte[] getTagHash(final String tag) {
        return Word.word2hash(tag.toLowerCase());
    }
    
    public final static HashSet<String> getTagSet(final String tagsString, boolean clean) {
        HashSet<String>tagSet = new HashSet<String>();
        final String[] tagArray = clean ? cleanTagsString(tagsString).split(TABLE_TAGS_SEPARATOR) : tagsString.split(TABLE_TAGS_SEPARATOR);
        for (final String tag : tagArray) {
        	tagSet.add(tag);
        } 
        return tagSet;
    }
    
    public final static HashSet<String> getTagSet(final String tagsString) {
    	return getTagSet(tagsString, true);
    }
    
    public final static HashSet<byte[]> getTagIdSet(final String tagsString, boolean clean) {
    	HashSet<byte[]>tagSet = new HashSet<byte[]>();
    	final String[] tagArray = clean ? cleanTagsString(tagsString).split(TABLE_TAGS_SEPARATOR) : tagsString.split(TABLE_TAGS_SEPARATOR);
        for (final String tag : tagArray) {
        	tagSet.add(getTagHash(tag));
        }        
    	return tagSet;
    }
    
    public final static Set<byte[]> getTagIdSet(final String tagsString) {
    	return getTagIdSet(tagsString, true);
    }
    
    public final static byte[] keySetToBytes(final HashSet<String> urlSet) {
    	final Iterator<String> urlIter = urlSet.iterator();
    	final 
    	StringBuilder urls = new StringBuilder();
    	while(urlIter.hasNext()) {
    		urls.append(TABLE_TAGS_SEPARATOR);
    		urls.append(urlIter.next());
    	}
    	urls.deleteCharAt(0);
    	return urls.toString().getBytes();
    }
    
    public final static HashSet<String> keysStringToKeySet(final String keysString) {
    	HashSet<String> keySet = new HashSet<String>();
        final String[] keyArray = keysString.split(TABLE_TAGS_SEPARATOR);                    
        for (final String key : keyArray) {
        	keySet.add(key);
        } 
        return keySet;
    }
    
    public final static String cleanTagsString(String tagsString) {        
        // get rid of heading, trailing and double commas since they are useless
        while (tagsString.length() > 0 && tagsString.charAt(0) == TABLE_TAGS_SEPARATOR.charAt(0)) {
            tagsString = tagsString.substring(1);
        }
        while (tagsString.endsWith(TABLE_TAGS_SEPARATOR)) {
            tagsString = tagsString.substring(0,tagsString.length() -1);
        }
        while (tagsString.contains(",,")){
            tagsString = tagsString.replaceAll(",,", TABLE_TAGS_SEPARATOR);
        }
        // space characters following a comma are removed
        tagsString = tagsString.replaceAll(",\\s+", TABLE_TAGS_SEPARATOR);         
        return tagsString;
    }
    
    public final static String cleanFoldersString(String foldersString) {        
    	foldersString = cleanTagsString(foldersString);    	
        // get rid of double and trailing slashes
        while (foldersString.endsWith("/")){
        	foldersString = foldersString.substring(0, foldersString.length() -1);
        }
        while (foldersString.contains("/,")){
        	foldersString = foldersString.replaceAll("/,", TABLE_TAGS_SEPARATOR);
        }
        while (foldersString.contains("//")){
        	foldersString = foldersString.replaceAll("//", "/");
        }        
        return foldersString;
    }
    
}
