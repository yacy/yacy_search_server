package de.anomic.data;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;

public class YMarkTables {
	
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
    public final static String TABLE_BOOKMARKS_COL_VISITS = "visits";
    public final static String TABLE_BOOKMARKS_COL_FOLDERS = "folders";    
    public final static String TABLE_BOOKMARKS_COL_DEFAULT = "";
    public final static String TABLE_BOOKMARKS_COL_PUBLIC_TRUE = "true";
    public final static String TABLE_BOOKMARKS_COL_PUBLIC_FALSE = "false";  
    public final static String TABLE_BOOKMARKS_COL_VISITS_ZERO = "0";
    
	public final static String TABLE_TAGS_BASENAME = "_tags";	
	public final static String TABLE_TAGS_SEPARATOR = ",";

	public final static String TABLE_INDEX_COL_ID = "id";
    public final static String TABLE_INDEX_COL_NAME = "name";
    public final static String TABLE_INDEX_DESC = "desc";
    public final static String TABLE_INDEX_COL_URLS = "urls";    
    public final static short TABLE_INDEX_ACTION_ADD = 1;
    public final static short TABLE_INDEX_ACTION_REMOVE = 2;
    
    public final static String TABLE_FOLDERS_BASENAME = "_folders";
    public final static String TABLE_FOLDERS_SEPARATOR = "/"; 
    public final static String TABLE_FOLDERS_ROOT = "/"; 
    public final static String TABLE_FOLDERS_UNSORTED = "/unsorted";
    public final static String TABLE_FOLDERS_IMPORTED = "/imported"; 
    
    private WorkTables worktables;
    public ConcurrentARC<String, byte[]> cache;
    
    public YMarkTables(final Tables wt) {
    	this.worktables = (WorkTables)wt;
    	this.cache = new ConcurrentARC<String, byte[]>(50,1);
    }
    
    public final static byte[] getBookmarkId(String url) throws MalformedURLException {
		return (new DigestURI(url, null)).hash();
    }
    
    public final static byte[] getKeyId(final String tag) {
        return Word.word2hash(tag.toLowerCase());
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
    
    public final static HashSet<String> keysStringToSet(final String keysString) {
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
        while (foldersString.endsWith(TABLE_FOLDERS_SEPARATOR)){
        	foldersString = foldersString.substring(0, foldersString.length() -1);
        }
        while (foldersString.contains("/,")){
        	foldersString = foldersString.replaceAll("/,", TABLE_TAGS_SEPARATOR);
        }
        while (foldersString.contains("//")){
        	foldersString = foldersString.replaceAll("//", TABLE_FOLDERS_SEPARATOR);
        }        
        return foldersString;
    }

    public void cleanCache(final String tablename) {
    	final Iterator<String> iter = this.cache.keySet().iterator();
    	while(iter.hasNext()) {
    		final String key = iter.next();
    		if (key.startsWith(tablename)) {
    			this.cache.remove(key);
    		}    			
    	}
    }
    
    public void createIndexEntry(final String index_table, final  String keyname, final HashSet<String> urlSet) throws IOException {
        final byte[] key = YMarkTables.getKeyId(keyname);
        final String cacheKey = index_table+":"+keyname;
    	final byte[] BurlSet = keySetToBytes(urlSet);
        Data tagEntry = new Data();
        
		this.cache.insert(cacheKey, BurlSet);	
    	
    	tagEntry.put(TABLE_INDEX_COL_NAME, keyname);
        tagEntry.put(TABLE_INDEX_COL_URLS, BurlSet);
        this.worktables.insert(index_table, key, tagEntry);
    }
    
	public HashSet<String> getBookmarks(final String index_table, final String keyname) throws IOException, RowSpaceExceededException {
		final String cacheKey = index_table+":"+keyname;
		if (this.cache.containsKey(cacheKey)) {
			return keysStringToSet(new String(this.cache.get(cacheKey)));
		} else {
			final Tables.Row idx_row = this.worktables.select(index_table, YMarkTables.getKeyId(keyname));
			if (idx_row != null) {						
				final byte[] keys = idx_row.get(YMarkTables.TABLE_INDEX_COL_URLS);
				this.cache.put(cacheKey, keys);
				return keysStringToSet(new String(keys));
			}
		}
		return new HashSet<String>();
	}
	
	public HashSet<String> getBookmarks(final String index_table, final String[] keyArray) throws IOException, RowSpaceExceededException {
    	final HashSet<String> urlSet = new HashSet<String>();
		urlSet.addAll(getBookmarks(index_table, keyArray[0]));
		if (urlSet.isEmpty())
			return urlSet;
		if (keyArray.length > 1) {
			for (final String keyname : keyArray) {
				urlSet.retainAll(getBookmarks(index_table, keyname));
				if (urlSet.isEmpty())
					return urlSet;
			}
		}
		return urlSet;		
	}
    
	/**
	 * YMark function that updates the tag/folder index
	 * @param index_table is the user specific index
	 * @param keyname
	 * @param url is the url has as returned by DigestURI.hash()
	 * @param action is either add (1) or remove (2)
	 */
    public void updateIndexTable(final String index_table, final String keyname, final byte[] url, final int action) {
        final byte[] key = YMarkTables.getKeyId(keyname);
        final String urlHash = new String(url);        		        
        Tables.Row row = null;
        
        // try to load urlSet from cache
        final String cacheKey = index_table+":"+keyname;
        HashSet<String>urlSet = this.cache.containsKey(cacheKey) ? keysStringToSet(new String(this.cache.get(cacheKey))) : new HashSet<String>();
        
    	try {
    		row = this.worktables.select(index_table, key);    		
    		
    		// key has no index_table entry
    		if(row == null) {
    			switch (action) {
					case TABLE_INDEX_ACTION_ADD:		        		
						urlSet.add(urlHash);        			
	        			createIndexEntry(index_table, keyname, urlSet);
			            break;
					case TABLE_INDEX_ACTION_REMOVE:
						// key has no index_table entry but a cache entry
						// TODO: this shouldn't happen
						if(!urlSet.isEmpty()) {
							urlSet.remove(urlHash);	        			
		        			createIndexEntry(index_table, keyname, urlSet);
						}
						break;
					default:
						break;       					
				}    			
    		} 
    		// key has an existing index_table entry
    		else {
    	        byte[] BurlSet = null;
    			// key has no cache entry
    			if (urlSet.isEmpty()) {
	    			// load urlSet from index_table
    				urlSet = keysStringToSet(new String(row.get(TABLE_INDEX_COL_URLS)));	   
    			}    			
    			switch (action) {
					case TABLE_INDEX_ACTION_ADD:
		        		urlSet.add(urlHash);
	        			break;
					case TABLE_INDEX_ACTION_REMOVE:
						urlSet.remove(urlHash);
						break;					
					default:
						break;
    			}
    			if (urlSet.isEmpty()) {
    				this.cache.remove(cacheKey);
    				this.worktables.delete(index_table, key);
    			} else {
    	        	BurlSet = keySetToBytes(urlSet);
        			this.cache.insert(cacheKey, BurlSet);
        			row.put(TABLE_INDEX_COL_URLS, BurlSet);
        			this.worktables.update(index_table, row);
    			}
    		}
    	}  catch (IOException e) {
            Log.logException(e);
		} catch (RowSpaceExceededException e) {
            Log.logException(e);
		}
	}
}
