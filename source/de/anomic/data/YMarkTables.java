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
    
	public static enum TABLES {
		BOOKMARKS ("_bookmarks"),
		TAGS ("_tags"),
		FOLDERS ("_folders");
		
		private String basename;
		
		private TABLES(String b) {
			this.basename = b;
		}
		public String basename() {
			return this.basename;
		}
		public String tablename(String user) {
			return user+this.basename;
		}
	}
	
	public static enum PROTOCOLS {
    	HTTP ("http://"),
    	HTTPS ("https://");
    	
    	private String protocol;
    	
    	private PROTOCOLS(String s) {
    		this.protocol = s;
    	}
    	public String protocol() {
    		return this.protocol;
    	}
    	public String protocol(String s) {
    		return this.protocol+s;
    	}
    }

    public static enum BOOKMARK {
    	URL 			("url",				"",				"HREF"),
    	TITLE 			("title",			"",				""),
    	DESC 			("desc",			"",				""),
    	DATE_ADDED 		("date_added",		"",				"ADD_DATE"),
    	DATE_MODIFIED 	("date_modified",	"",				"LAST_MODIFIED"),
    	DATE_VISITED 	("date_visited",	"",				"LAST_VISITED"),
    	PUBLIC 			("public",			"flase",		""),
    	TAGS 			("tags",			"unsorted",		"SHORTCUTURL"),
    	VISITS 			("visits",			"0",			""),
    	FOLDERS 		("folders",			"/unsorted",	"");
    	    	
    	private String key;
    	private String dflt;
    	private String html_attrb;
    	
    	private BOOKMARK(String k, String s, String a) {
    		this.key = k;
    		this.dflt = s;
    		this.html_attrb = a;
    	}    	
    	public String key() {
    		return this.key;
    	}    	
    	public String deflt() {
    		return  this.dflt;
    	}
    	public String html_attrb() {
    		return this.html_attrb.toLowerCase();
    	}
    }
    
    public static enum INDEX {
    	ID 		("id", ""),
    	NAME 	("name", ""),
    	DESC 	("desc", ""),
    	URLS 	("urls", "");
    	
    	private String key;
    	private String dflt;
    	
    	private INDEX(String k, String s) {
    		this.key = k;
    		this.dflt = s;
    	}
    	public String key() {
    		return this.key;
    	}
    	public String deflt() {
    		return  this.dflt;
    	}
    	public byte[] b_deflt() {
    		return  dflt.getBytes();
    	}
    }
    
    public static enum INDEX_ACTION {
    	ADD,
    	REMOVE
    }
    
    public final static String TAGS_SEPARATOR = ",";
    public final static String FOLDERS_SEPARATOR = "/";
    public final static String FOLDERS_ROOT = "/"; 
    public final static String FOLDERS_UNSORTED = "/unsorted";
    public final static String FOLDERS_IMPORTED = "/imported"; 
    public final static String BOOKMARKS_LOG = "BOOKMARKS";
    public final static String BOOKMARKS_ID = "id";
	public final static String USER_ADMIN = "admin";
	public final static String USER_AUTHENTICATE = "AUTHENTICATE";
	public final static String USER_AUTHENTICATE_MSG = "Authentication required!";
    
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
    	StringBuilder urls = new StringBuilder(urlSet.size()*20);
    	while(urlIter.hasNext()) {
    		urls.append(TAGS_SEPARATOR);
    		urls.append(urlIter.next());
    	}
    	urls.deleteCharAt(0);
    	return urls.toString().getBytes();
    }
    
    public final static HashSet<String> keysStringToSet(final String keysString) {
    	HashSet<String> keySet = new HashSet<String>();
        final String[] keyArray = keysString.split(TAGS_SEPARATOR);                    
        for (final String key : keyArray) {
        	keySet.add(key);
        }
        return keySet;
    }
    
    public final static String cleanTagsString(final String tagsString) {        
    	StringBuilder ts = new StringBuilder(tagsString);    	
    	// get rid of double commas and space characters following a comma
    	for (int i = 0; i < ts.length()-1; i++) {
    		if (ts.charAt(i) == TAGS_SEPARATOR.charAt(0)) {
    			if (ts.charAt(i+1) == TAGS_SEPARATOR.charAt(0) || ts.charAt(i+1) == ' ') {
    				ts.deleteCharAt(i+1);
    				i--;
    			}
    		}
    	}
		// get rid of heading and trailing comma
		if (ts.charAt(0) == TAGS_SEPARATOR.charAt(0))
			ts.deleteCharAt(0);
		if (ts.charAt(ts.length()-1) == TAGS_SEPARATOR.charAt(0))
			ts.deleteCharAt(ts.length()-1);
    	return ts.toString();
    }
    
    public final static String cleanFoldersString(final String foldersString) {        
    	StringBuilder fs = new StringBuilder(cleanTagsString(foldersString));    	
    	for (int i = 0; i < fs.length()-1; i++) {
    		if (fs.charAt(i) == FOLDERS_SEPARATOR.charAt(0)) {
    			if (fs.charAt(i+1) == TAGS_SEPARATOR.charAt(0) || fs.charAt(i+1) == FOLDERS_SEPARATOR.charAt(0)) {
    				fs.deleteCharAt(i);
    				i--;
    			} else if (fs.charAt(i+1) == ' ') {
    				fs.deleteCharAt(i+1);
    				i--;
    			}
    		}
    	}
		if (fs.charAt(fs.length()-1) == FOLDERS_SEPARATOR.charAt(0)) {
			fs.deleteCharAt(fs.length()-1);
		}
    	return fs.toString();
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
    	
    	tagEntry.put(INDEX.NAME.key, keyname);
        tagEntry.put(INDEX.URLS.key, BurlSet);
        this.worktables.insert(index_table, key, tagEntry);
    }
    
	public HashSet<String> getBookmarks(final String index_table, final String keyname) throws IOException, RowSpaceExceededException {
		final String cacheKey = index_table+":"+keyname;
		if (this.cache.containsKey(cacheKey)) {
			return keysStringToSet(new String(this.cache.get(cacheKey)));
		} else {
			final Tables.Row idx_row = this.worktables.select(index_table, getKeyId(keyname));
			if (idx_row != null) {						
				final byte[] keys = idx_row.get(INDEX.URLS.key);
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
    public void updateIndexTable(final String index_table, final String keyname, final byte[] url, final INDEX_ACTION action) {
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
					case ADD:		        		
						urlSet.add(urlHash);        			
	        			createIndexEntry(index_table, keyname, urlSet);
			            break;
					case REMOVE:
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
    				urlSet = keysStringToSet(new String(row.get(INDEX.URLS.key)));	   
    			}    			
    			switch (action) {
					case ADD:
		        		urlSet.add(urlHash);
	        			break;
					case REMOVE:
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
        			row.put(INDEX.URLS.key, BurlSet);
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
