package de.anomic.data;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;

public class YMarkStatics {
	
	public final static String TABLE_BOOKMARKS_BASENAME = "bookmarks";

	public final static String TABLE_BOOKMARKS_LOG = "BOOKMARKS";
	
	public final static String TABLE_BOOKMARKS_COL_ID = "id";
    public final static String TABLE_BOOKMARKS_COL_URL = "url";
    public final static String TABLE_BOOKMARKS_COL_TITLE = "title";
    public final static String TABLE_BOOKMARKS_COL_DESC = "desc";

    public final static String TABLE_BOOKMARKS_COL_DATE_ADDED = "added";
    public final static String TABLE_BOOKMARKS_COL_DATE_MODIFIED = "modified";
    public final static String TABLE_BOOKMARKS_COL_DATE_VISITED = "visited";

    public final static String TABLE_BOOKMARKS_COL_PUBLIC = "public";
    public final static String TABLE_BOOKMARKS_COL_TAGS = "tags";
    
    public final static byte[] getBookmarkID(String url) throws MalformedURLException {
    	if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
            url="http://"+url;
        }
		return (new DigestURI(url, null)).hash();
    }
    
    public final static byte[] getTagHash(final String tag) {
        return Word.word2hash(tag.toLowerCase());
    }
    
    public final static Set<String> getTagSet(final String tagsString) {
        Set<String>tagSet = new HashSet<String>();
        String[] tagArray = cleanTagsString(tagsString).split(",");
        for (final String tag : tagArray) {
        	tagSet.add(tag);
        } 
        return tagSet;
    }
    
    public final static Set<byte[]> getTagHashSet(final String tagsString) {
    	Set<byte[]>tagSet = new HashSet<byte[]>();
        String[] tagArray = cleanTagsString(tagsString).split(",");
        for (final String tag : tagArray) {
        	tagSet.add(getTagHash(tag));
        }        
    	return tagSet;
    }
    
    public final static String cleanTagsString(String tagsString) {        
        // get rid of heading, trailing and double commas since they are useless
        while (tagsString.length() > 0 && tagsString.charAt(0) == ',') {
            tagsString = tagsString.substring(1);
        }
        while (tagsString.endsWith(",")) {
            tagsString = tagsString.substring(0,tagsString.length() -1);
        }
        while (tagsString.contains(",,")){
            tagsString = tagsString.replaceAll(",,", ",");
        }
        // space characters following a comma are removed
        tagsString = tagsString.replaceAll(",\\s+", ",");         
        return tagsString;
    }
    
    public final static String cleanFoldersString(String foldersString) {        
    	foldersString = cleanTagsString(foldersString);    	
        // get rid of double and trailing slashes
        while (foldersString.endsWith("/")){
        	foldersString = foldersString.substring(0, foldersString.length() -1);
        }
        while (foldersString.contains("/,")){
        	foldersString = foldersString.replaceAll("/,", ",");
        }
        while (foldersString.contains("//")){
        	foldersString = foldersString.replaceAll("//", "/");
        }        
        return foldersString;
    }
    
}
