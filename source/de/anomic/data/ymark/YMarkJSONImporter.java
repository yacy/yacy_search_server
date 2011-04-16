package de.anomic.data.ymark;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.kelondro.logging.Log;

import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class YMarkJSONImporter implements Runnable, ContentHandler{

	public final static String FOLDER = "text/x-moz-place-container";
	public final static String BOOKMARK = "text/x-moz-place";
	public final static String ANNOS = "annos";
	public final static String TYPE = "type";
	public final static String CHILDREN = "children";
	private final static String MILLIS = "000";
		
	private final JSONParser parser;

	private final Reader json;
	private final StringBuilder folderstring;
	private final StringBuilder value;
	private final StringBuilder key;
	private final HashMap<String,String> obj;
	private final ArrayBlockingQueue<HashMap<String,String>> bookmarks;
	private HashMap<String,String> bmk;
	private int depth;
	private Boolean isFolder;
	private Boolean isBookmark;
	private Boolean isAnnos;	
	
	public YMarkJSONImporter(final InputStream input, int queueSize) throws UnsupportedEncodingException {
		this.parser = new JSONParser();
		this.bookmarks = new ArrayBlockingQueue<HashMap<String,String>>(queueSize);
		this.json = new InputStreamReader(input, "UTF-8");
		this.folderstring = new StringBuilder(256);
		this.key = new StringBuilder(16);
		this.value = new StringBuilder(128);
		this.obj = new HashMap<String,String>();
		this.bmk = new HashMap<String,String>();
		this.depth = 0;
		this.isAnnos = false;
		this.isBookmark = false;
		this.isFolder = true;
	}
	
	public void startJSON() throws ParseException, IOException {
	}

	public void endJSON() throws ParseException, IOException {
	}

	public boolean startArray() throws ParseException, IOException {
		final String key = this.key.toString();
		if(key.equals(CHILDREN) && this.isFolder) {
			if(this.depth > 0) {
				this.folderstring.append(YMarkUtil.FOLDERS_SEPARATOR);
				this.folderstring.append(this.obj.get(YMarkTables.BOOKMARK.TITLE.json_attrb()));
			}
			this.depth++;
		} else if(key.equals(ANNOS)) {
			this.isAnnos = true;
		}
		return true;
	}
	
	public boolean endArray() throws ParseException, IOException {
		if(this.isAnnos) {
			this.isAnnos = false;
		} else if(this.depth > 0) {
			if(this.depth == 1)
			    folderstring.setLength(0);
			else
			    folderstring.setLength(folderstring.lastIndexOf(YMarkUtil.FOLDERS_SEPARATOR));
			this.depth--;
		}
		return true;
	}

	public boolean startObject() throws ParseException, IOException {
		if(!this.isAnnos) {
			this.obj.clear();
		}
		return true;
	}
	
	public boolean endObject() throws ParseException, IOException {
		if(this.isBookmark) {
			this.bmk.put(YMarkTables.BOOKMARK.TITLE.key(),obj.get(YMarkTables.BOOKMARK.TITLE.json_attrb()));
			this.bmk.put(YMarkTables.BOOKMARK.URL.key(),obj.get(YMarkTables.BOOKMARK.URL.json_attrb()));
			this.bmk.put(YMarkTables.BOOKMARK.DATE_ADDED.key(),obj.get(YMarkTables.BOOKMARK.DATE_ADDED.json_attrb())+MILLIS);
			this.bmk.put(YMarkTables.BOOKMARK.DATE_MODIFIED.key(),obj.get(YMarkTables.BOOKMARK.DATE_MODIFIED.json_attrb())+MILLIS);
			this.bmk.put(YMarkTables.BOOKMARK.FOLDERS.key(),this.folderstring.toString());
			if(this.obj.containsKey(YMarkTables.BOOKMARK.TAGS.json_attrb())) {
				this.bmk.put(YMarkTables.BOOKMARK.TAGS.key(),obj.get(YMarkTables.BOOKMARK.TAGS.json_attrb()));
			}
			try {
				this.bookmarks.put(this.bmk);
			} catch (InterruptedException e) {
				Log.logException(e);
			}
			this.bmk = new HashMap<String,String>();	
		}
		this.isBookmark = false;
		return true;
	}

	public boolean startObjectEntry(String key) throws ParseException, IOException {
		if(!this.isAnnos) {
			this.key.setLength(0);
			this.key.append(key);	
		}
		return true;
	}
	
	public boolean primitive(Object value) throws ParseException, IOException {
		if(!this.isAnnos) {
			this.value.setLength(0);
			if(value instanceof java.lang.String) {
				this.value.append((String)value);
			} else if(value instanceof java.lang.Boolean) {
				this.value.append((Boolean)value);
			} else if(value instanceof java.lang.Number) {
				this.value.append((Number)value);
			}
		}
		return true;
	}

	public boolean endObjectEntry() throws ParseException, IOException {
		if(!this.isAnnos) {
			final String key = this.key.toString();
			final String value = this.value.toString();
			if(key.equals(TYPE)) {
				if(value.equals(FOLDER)) {
					this.isFolder = true;
				} else if(value.equals(BOOKMARK)) {
					this.isBookmark = true;
				}
			}
			this.obj.put(key, value);
		}
		return true;
	}

	public void run() {
		try {
			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "JSON Importer run()");
			this.parser.parse(json, this, true);
		} catch (IOException e) {
			Log.logException(e);
		} catch (ParseException e) {
			Log.logException(e);
		} finally {			
			try {
				Log.logInfo(YMarkTables.BOOKMARKS_LOG, "JSON Importer inserted poison pill in queue");
				this.bookmarks.put(YMarkTables.POISON);
			} catch (InterruptedException e) {
				Log.logException(e);
			}
		}
	}
	
    public HashMap<String,String> take() {
        try {
            return this.bookmarks.take();
        } catch (InterruptedException e) {
            Log.logException(e);
            return null;
        }
    }
}
