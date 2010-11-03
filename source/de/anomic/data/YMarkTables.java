package de.anomic.data;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowSpaceExceededException;

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
    	URL 			("url",				"",				"href",					"href"),
    	TITLE 			("title",			"",				"",						""),
    	DESC 			("desc",			"",				"",						""),
    	DATE_ADDED 		("date_added",		"",				"add_date",				"added"),
    	DATE_MODIFIED 	("date_modified",	"",				"last_modified",		"modified"),
    	DATE_VISITED 	("date_visited",	"",				"last_visited",			"visited"),
    	PUBLIC 			("public",			"flase",		"",						""),
    	TAGS 			("tags",			"unsorted",		"shortcuturl",			""),
    	VISITS 			("visits",			"0",			"",						""),
    	FOLDERS 		("folders",			"/unsorted",	"",						"");
    	    	
    	private String key;
    	private String dflt;
    	private String html_attrb;
    	private String xbel_attrb;
    	
    	private BOOKMARK(String k, String s, String a, String x) {
    		this.key = k;
    		this.dflt = s;
    		this.html_attrb = a;
    		this.xbel_attrb = x;
    	}    	
    	public String key() {
    		return this.key;
    	}    	
    	public String deflt() {
    		return  this.dflt;
    	}
    	public String html_attrb() {
    		return this.html_attrb;
    	}
    	public String xbel_attrb() {
    		return this.xbel_attrb;
    	}
    }
    
    public final static HashMap<String,String> POISON = new HashMap<String,String>();
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
    public YMarkIndex tags;
    public YMarkIndex folders;
    
    public YMarkTables(final Tables wt) {
    	this.worktables = (WorkTables)wt;
    	this.folders = new YMarkIndex(this.worktables, TABLES.FOLDERS.basename());
    	this.tags = new YMarkIndex(this.worktables, TABLES.TAGS.basename());
    }
    
    public final static byte[] getBookmarkId(String url) throws MalformedURLException {
		return (new DigestURI(url, null)).hash();
    }
    
    public final static byte[] getKeyId(final String tag) {
        return Word.word2hash(tag.toLowerCase());
    }
    
    public final static byte[] keySetToBytes(final HashSet<String> urlSet) {
    	return keySetToString(urlSet).getBytes();
    }
    
    public final static String keySetToString(final HashSet<String> urlSet) {
    	final Iterator<String> urlIter = urlSet.iterator();
    	final 
    	StringBuilder urls = new StringBuilder(urlSet.size()*20);
    	while(urlIter.hasNext()) {
    		urls.append(TAGS_SEPARATOR);
    		urls.append(urlIter.next());
    	}
    	urls.deleteCharAt(0);
    	return urls.toString();
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
    
    public void clearIndex(String tablename) {
    	if (tablename.endsWith(TABLES.TAGS.basename()))
    		this.tags.clearCache();
    	if (tablename.endsWith(TABLES.FOLDERS.basename()))
    		this.folders.clearCache();
    }
    
    public void deleteBookmark(final String bmk_user, final byte[] urlHash) throws IOException, RowSpaceExceededException {
        final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
    	Tables.Row bmk_row = null;
        bmk_row = this.worktables.select(bmk_table, urlHash);
        if(bmk_row != null) {
            final String tagsString = bmk_row.get(YMarkTables.BOOKMARK.TAGS.key(),YMarkTables.BOOKMARK.TAGS.deflt());
            tags.removeIndexEntry(bmk_user, tagsString, urlHash);
            final String foldersString = bmk_row.get(YMarkTables.BOOKMARK.FOLDERS.key(),YMarkTables.FOLDERS_ROOT);
            folders.removeIndexEntry(bmk_user, foldersString, urlHash);
    		this.worktables.delete(bmk_table,urlHash);
        }
    }
    
    public void deleteBookmark(final String bmk_user, final String url) throws IOException, RowSpaceExceededException {
    	this.deleteBookmark(bmk_user, getBookmarkId(url));
    }
    
	public void addBookmark(final String bmk_user, final HashMap<String,String> bmk, final boolean importer) throws IOException, RowSpaceExceededException {
		final String bmk_table = TABLES.BOOKMARKS.tablename(bmk_user);
        final String date = String.valueOf(System.currentTimeMillis());
		final byte[] urlHash = getBookmarkId(bmk.get(BOOKMARK.URL.key()));
		Tables.Row bmk_row = null;

		if (urlHash != null) {
			bmk_row = this.worktables.select(bmk_table, urlHash);
	        if (bmk_row == null) {
	        	// create and insert new entry
	        	final Data data = new Data();
	            for (BOOKMARK b : BOOKMARK.values()) {
	            	switch(b) {
	    				case DATE_ADDED:
	    				case DATE_MODIFIED:
	    					if(bmk.containsKey(b.key())) {
	    						data.put(b.key(), bmk.get(b.key()));
	    					} else {
	    						data.put(b.key(), String.valueOf(System.currentTimeMillis()).getBytes());
	    					}
	    					break;
	    				case TAGS:
	    					if(bmk.containsKey(b.key())) {
	    						this.tags.insertIndexEntry(bmk_user, bmk.get(b.key()), urlHash);
	    						data.put(b.key(), bmk.get(b.key()));
	    					} else {
	    						this.tags.insertIndexEntry(bmk_user, b.deflt(), urlHash);
	    						data.put(b.key(), b.deflt());	
	    					}
	    					break;
	    				case FOLDERS:
	    					if(bmk.containsKey(b.key())) {
	    						this.folders.insertIndexEntry(bmk_user, bmk.get(b.key()), urlHash);
	    						data.put(b.key(), bmk.get(b.key()));
	    					} else {
	    						this.folders.insertIndexEntry(bmk_user, b.deflt(), urlHash);
	    						data.put(b.key(), b.deflt());	
	    					}
	    					break;	
	    				default:
	    					if(bmk.containsKey(b.key())) {
	    						data.put(b.key(), bmk.get(b.key()));
	    					}
	            	 }
	             }
            	 this.worktables.insert(bmk_table, urlHash, data);
	        } else {	
            	// modify and update existing entry
                HashSet<String> oldSet;
                HashSet<String> newSet;
	        	for (BOOKMARK b : BOOKMARK.values()) {
	            	switch(b) {
	    				case DATE_ADDED:
	    					if(!bmk_row.containsKey(b.key))
	    						bmk_row.put(b.key(), date); 
	    					break;
	    				case DATE_MODIFIED:
	    					bmk_row.put(b.key(), date); 
	    					break;
	    				case TAGS:
	    	            	oldSet = keysStringToSet(bmk_row.get(b.key(),b.deflt()));
	    	            	if(bmk.containsKey(b.key())) {
	    	            		newSet = keysStringToSet(bmk.get(b.key()));
	    	            		if(importer) {
		    	            		newSet.addAll(oldSet);
		    	            		bmk_row.put(b.key(), keySetToString(newSet));
		    	            		oldSet.clear();
	    	            		} else {
	    	            			bmk_row.put(b.key, bmk.get(b.key()));
	    	            		}
	    	            	} else {
	    	            		newSet = new HashSet<String>();
	    	            		bmk_row.put(b.key, bmk_row.get(b.key(), b.deflt()));
	    	            	}
	    	            	this.tags.updateIndexEntry(bmk_user, urlHash, oldSet, newSet);	    					
	    	            	break;
	    				case FOLDERS:
	    					oldSet = keysStringToSet(bmk_row.get(b.key(),b.deflt()));
	    					if(bmk.containsKey(b.key())) {
	    	            		newSet = keysStringToSet(bmk.get(b.key()));
	    	            		if(importer) {
		    	            		newSet.addAll(oldSet);
		    	            		bmk_row.put(b.key(), keySetToString(newSet));
		    	            		oldSet.clear();
	    	            		} else {
	    	            			bmk_row.put(b.key, bmk.get(b.key()));
	    	            		}
	    	            	} else {
	    	            		newSet = new HashSet<String>();
	    	            		bmk_row.put(b.key, bmk_row.get(b.key(), b.deflt()));
	    	            	}
	    	            	this.folders.updateIndexEntry(bmk_user, urlHash, oldSet, newSet);
	    					break;	
	    				default:
	    					if(bmk.containsKey(b.key())) {
	    						bmk_row.put(b.key, bmk.get(b.key()));
	    					} else {
	    						bmk_row.put(b.key, bmk_row.get(b.key(), b.deflt()));
	    					}
	            	 }
	             }
                // update bmk_table
                this.worktables.update(bmk_table, bmk_row); 
            }
		}
	}
}
