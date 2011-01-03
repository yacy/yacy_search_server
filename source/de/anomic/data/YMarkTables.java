package de.anomic.data;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.document.Condenser;
import net.yacy.document.Document;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import de.anomic.search.Segment;

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
		public String tablename(String bmk_user) {
			return bmk_user+this.basename;
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
    	//				key					dflt			html_attrb				xbel_attrb		type
    	URL 			("url",				"",				"href",					"href",			"link"),
    	TITLE 			("title",			"",				"",						"",				"meta"),
    	DESC 			("desc",			"",				"",						"",				"comment"),
    	DATE_ADDED 		("date_added",		"",				"add_date",				"added",		"date"),
    	DATE_MODIFIED 	("date_modified",	"",				"last_modified",		"modified",		"date"),
    	DATE_VISITED 	("date_visited",	"",				"last_visited",			"visited",		"date"),
    	PUBLIC 			("public",			"flase",		"",						"yacy:public",	"lock"),
    	TAGS 			("tags",			"unsorted",		"shortcuturl",			"yacy:tags",	"tag"),
    	VISITS 			("visits",			"0",			"",						"yacy:visits",	"stat"),
    	FOLDERS 		("folders",			"/unsorted",	"",						"",				"folder");
    	    	
    	private String key;
    	private String dflt;
    	private String html_attrb;
    	private String xbel_attrb;
    	private String type;

        private static final Map<String,BOOKMARK> lookup = new HashMap<String,BOOKMARK>();
        static {
        	for(BOOKMARK b : EnumSet.allOf(BOOKMARK.class))
        		lookup.put(b.key(), b);
        }
    	
        private static StringBuilder buffer = new StringBuilder(25);;
        
    	private BOOKMARK(String k, String s, String a, String x, String t) {
    		this.key = k;
    		this.dflt = s;
    		this.html_attrb = a;
    		this.xbel_attrb = x;
    		this.type = t;
    	}
    	public static BOOKMARK get(String key) { 
            return lookup.get(key); 
    	}
    	public static boolean contains(String key) {
    		return lookup.containsKey(key);
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
    	public String xbel() {
    		buffer.setLength(0);
    		buffer.append('"');
    		buffer.append('\n');
    		buffer.append(' ');
    		buffer.append(this.xbel_attrb);
    		buffer.append('=');
    		buffer.append('"');
    		return buffer.toString();
    	}
    	public String type() {
    		return this.type;
    	}
    }
    
	public enum METADATA {
		TITLE,
		DESCRIPTION,
		FAVICON,
		KEYWORDS,
		LANGUAGE,
		CREATOR,
		PUBLISHER,
		CHARSET,
		MIMETYPE,
		SIZE,
		WORDCOUNT,
		IN_URLDB,
		FRESHDATE,
		LOADDATE,
		MODDATE,
		SNIPPET
	}
    
    public final static HashMap<String,String> POISON = new HashMap<String,String>();
    
    public final static String TAGS_SEPARATOR = ",";
    
    public final static String FOLDERS_SEPARATOR = "/";
    public final static String FOLDERS_ROOT = "/"; 
    public final static String FOLDERS_UNSORTED = "/unsorted";
    public final static String FOLDERS_IMPORTED = "/imported";
	public static final int FOLDER_BUFFER_SIZE = 100;    
    
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
    
    public static Date parseISO8601(final String s) throws ParseException {
    	if(s == null || s.length() < 1) {
    		throw new ParseException("parseISO8601 - empty string, nothing to parse", 0);
    	}
    	SimpleDateFormat dateformat;
    	StringBuilder date = new StringBuilder(s);
    	if(s.length()==10)
    		dateformat = new SimpleDateFormat("yyyy-MM-dd");
    	else {
    		dateformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz"); 
	        if(date.charAt(date.length()-1) == 'Z') {
	        	date.deleteCharAt(date.length()-1);
	        	date.append("GMT-00:00");
	        } else {
	            date.insert(date.length()-6, "GMT");
	        }
    	}
        return dateformat.parse(date.toString());
    }
    
    public static String getISO8601(final byte[] date) {
    	if(date != null) {
        	final String s = new String(date);
        	if(s != null && s.length() > 0)
        		return ISO8601Formatter.FORMATTER.format(new Date(Long.parseLong(s)));	
    	}
    	return "";
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
    	if(ts.length() == 0)
    		return YMarkTables.BOOKMARK.TAGS.deflt();
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
    	if(fs.length() == 0)
    		return YMarkTables.BOOKMARK.FOLDERS.deflt();
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
	    					if(bmk.containsKey(b.key()) && bmk.get(b.key()) != null) {
	    						data.put(b.key(), bmk.get(b.key()));
	    					} else {
	    						data.put(b.key(), String.valueOf(System.currentTimeMillis()).getBytes());
	    					}
	    					break;
	    				case TAGS:
	    					if(bmk.containsKey(b.key()) && bmk.get(b.key()) != null) {
	    						this.tags.insertIndexEntry(bmk_user, bmk.get(b.key()), urlHash);
	    						data.put(b.key(), bmk.get(b.key()));
	    					} else {
	    						this.tags.insertIndexEntry(bmk_user, b.deflt(), urlHash);
	    						data.put(b.key(), b.deflt());	
	    					}
	    					break;
	    				case FOLDERS:
	    					if(bmk.containsKey(b.key()) && bmk.get(b.key()) != null) {
	    						this.folders.insertIndexEntry(bmk_user, bmk.get(b.key()), urlHash);
	    						data.put(b.key(), bmk.get(b.key()));
	    					} else {
	    						this.folders.insertIndexEntry(bmk_user, b.deflt(), urlHash);
	    						data.put(b.key(), b.deflt());	
	    					}
	    					break;	
	    				default:
	    					if(bmk.containsKey(b.key()) && bmk.get(b.key()) != null) {
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
	
	public static EnumMap<METADATA, String> getMetadata(final byte[] urlHash, final Segment indexSegment) {
        final EnumMap<METADATA, String> metadata = new EnumMap<METADATA, String>(METADATA.class);
        final URIMetadataRow urlEntry = indexSegment.urlMetadata().load(urlHash, null, 0);
        if (urlEntry != null) {
        	metadata.put(METADATA.IN_URLDB, "true");
        	metadata.put(METADATA.SIZE, String.valueOf(urlEntry.size()));
        	metadata.put(METADATA.FRESHDATE, ISO8601Formatter.FORMATTER.format(urlEntry.freshdate()));
        	metadata.put(METADATA.LOADDATE, ISO8601Formatter.FORMATTER.format(urlEntry.loaddate()));
        	metadata.put(METADATA.MODDATE, ISO8601Formatter.FORMATTER.format(urlEntry.moddate()));
        	metadata.put(METADATA.SNIPPET, String.valueOf(urlEntry.snippet()));
        	metadata.put(METADATA.WORDCOUNT, String.valueOf(urlEntry.wordCount()));
        	metadata.put(METADATA.MIMETYPE, String.valueOf(urlEntry.doctype()));
        	metadata.put(METADATA.LANGUAGE, urlEntry.language());
        	
        	final URIMetadataRow.Components meta = urlEntry.metadata();
        	if (meta != null) {	        	
	        	metadata.put(METADATA.TITLE, meta.dc_title()); 
	        	metadata.put(METADATA.CREATOR, meta.dc_creator());
	        	metadata.put(METADATA.KEYWORDS, meta.dc_subject());
	        	metadata.put(METADATA.PUBLISHER, meta.dc_publisher());
        	}
        } 
        return metadata;
	}
	
	public static EnumMap<METADATA, String> getMetadata(final Document document) {
		final EnumMap<METADATA, String> metadata = new EnumMap<METADATA, String>(METADATA.class);
		metadata.put(METADATA.IN_URLDB, "false");
        if(document != null) {
			metadata.put(METADATA.TITLE, document.dc_title()); 
			metadata.put(METADATA.CREATOR, document.dc_creator());
			metadata.put(METADATA.KEYWORDS, document.dc_subject(' '));
			metadata.put(METADATA.PUBLISHER, document.dc_publisher());
			metadata.put(METADATA.DESCRIPTION, document.dc_description());
			metadata.put(METADATA.MIMETYPE, document.dc_format());
			metadata.put(METADATA.LANGUAGE, document.dc_language());
			metadata.put(METADATA.CHARSET, document.getCharset());
			// metadata.put(METADATA.SIZE, String.valueOf(document.getTextLength()));
		}
		return metadata;
	}
	
	public String autoTag(final Document document, final String bmk_user, final int count) {
        final StringBuilder buffer = new StringBuilder();
		final Map<String, Word> words;
        if(document != null) {
		    try {
				words = new Condenser(document, true, true, LibraryProvider.dymLib).words();
		        buffer.append(document.dc_title());
		        buffer.append(document.dc_description());
		        buffer.append(document.dc_subject(' '));
		        final Enumeration<String> tokens = Condenser.wordTokenizer(buffer.toString(), "UTF-8", LibraryProvider.dymLib);
		        while(tokens.hasMoreElements()) {
		        	int max = 1;
		        	String token = tokens.nextElement();
		        	Word word = words.get(token);
		        	if (words.containsKey(token)) {
		        		if (this.worktables.has(TABLES.TAGS.tablename(bmk_user), getKeyId(token))) {
		        			max = word.occurrences() * 1000;
		        		} else if (token.length()>3) {
		        			max = word.occurrences() * 100;
		        		}
		        		for(int i=0; i<max; i++) {
		        			word.inc();
		        		}
		        	}
		        }
		        buffer.setLength(0);
				final ArrayList<String> topwords = new ArrayList<String>(sortWordCounts(words).descendingKeySet());
				for(int i=0; i<count && i<topwords.size() ; i++) {
					if(words.get(topwords.get(i)).occurrences() > 100) {
						buffer.append(topwords.get(i));
						buffer.append(YMarkTables.TAGS_SEPARATOR);	
					}
				}
			} catch (UnsupportedEncodingException e) {
				Log.logException(e);
			} catch (IOException e) {
				Log.logException(e);
			} 
		}
		return YMarkTables.cleanTagsString(buffer.toString());
	}
	
	public static TreeMap<String,Word> getWordCounts(final Document document) {
        try {
			if(document != null) {
                return sortWordCounts(new Condenser(document, true, true, LibraryProvider.dymLib).words());
			}
		} catch (IOException e) {			
			Log.logException(e);
		}
		return new TreeMap<String, Word>();
	}
	
	public static TreeMap<String,Word> sortWordCounts(final Map<String, Word> unsorted_words) {		
        final TreeMap<String, Word> sorted_words = new TreeMap<String, Word>(new YMarkWordCountComparator(unsorted_words));
        sorted_words.putAll(unsorted_words);
        return sorted_words;	
    }

}
