package net.yacy.data.ymark;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.util.ConcurrentLog;

import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class YMarkJSONImporter implements Runnable, ContentHandler{
		
	// Importer Variables
	private final ArrayBlockingQueue<YMarkEntry> bookmarks;	
    private final Reader bmk_file;
    private final String RootFolder;
    private final StringBuilder folderstring;
    private YMarkEntry bmk;
    private final JSONParser parser;

    // Statics
    public final static String FOLDER = "text/x-moz-place-container";
    public final static String BOOKMARK = "text/x-moz-place";
    public final static String ANNOS = "annos";
    public final static String TYPE = "type";
    public final static String CHILDREN = "children";
    
    // Parser Variables
	private final StringBuilder value;
	private final StringBuilder key;
	private final StringBuilder date;
	private final HashMap<String,String> obj;
	private int depth;
	
	private Boolean isFolder;
	private Boolean isBookmark;
	private Boolean isAnnos;	
	
	public YMarkJSONImporter(final Reader bmk_file, final int queueSize, final String root) {
        this.bookmarks = new ArrayBlockingQueue<YMarkEntry>(queueSize);		
        this.bmk_file = bmk_file;
        this.RootFolder = root;
        this.folderstring = new StringBuilder(YMarkTables.BUFFER_LENGTH);
        this.folderstring.append(this.RootFolder);
        this.bmk = new YMarkEntry();
	    
        this.parser = new JSONParser();
		
	    this.value = new StringBuilder(128);
	    this.key = new StringBuilder(16);
		this.date = new StringBuilder(32);
	    this.obj = new HashMap<String,String>();
		this.depth = 0;
		
		this.isAnnos = false;
		this.isBookmark = false;
		this.isFolder = true;
	}
	
	@Override
    public void startJSON() throws ParseException, IOException {
	}

	@Override
    public void endJSON() throws ParseException, IOException {
	}

	@Override
    public boolean startArray() throws ParseException, IOException {
		final String key = this.key.toString();
		if(key.equals(CHILDREN) && this.isFolder) {
			if(this.depth > 0) {
				this.folderstring.append(YMarkUtil.FOLDERS_SEPARATOR);
				this.folderstring.append(this.obj.get(YMarkEntry.BOOKMARK.TITLE.json_attrb()));
			}
			this.depth++;
		} else if(key.equals(ANNOS)) {
			this.isAnnos = true;
		}
		return true;
	}
	
	@Override
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

	@Override
    public boolean startObject() throws ParseException, IOException {
		if(!this.isAnnos) {
			this.obj.clear();
		}
		return true;
	}
	
	@Override
    public boolean endObject() throws ParseException, IOException {
		if(this.isBookmark) {
			this.bmk.put(YMarkEntry.BOOKMARK.TITLE.key(),obj.get(YMarkEntry.BOOKMARK.TITLE.json_attrb()));
			this.bmk.put(YMarkEntry.BOOKMARK.URL.key(),obj.get(YMarkEntry.BOOKMARK.URL.json_attrb()));
			date.setLength(0);
			date.append(obj.get(YMarkEntry.BOOKMARK.DATE_ADDED.json_attrb()));
			date.setLength(date.length()-3);
			this.bmk.put(YMarkEntry.BOOKMARK.DATE_ADDED.key(), date.toString());
			date.setLength(0);
			date.append(obj.get(YMarkEntry.BOOKMARK.DATE_MODIFIED.json_attrb()));
			date.setLength(date.length()-3);
			this.bmk.put(YMarkEntry.BOOKMARK.DATE_MODIFIED.key(), date.toString());
			this.bmk.put(YMarkEntry.BOOKMARK.FOLDERS.key(),this.folderstring.toString());
			if(this.obj.containsKey(YMarkEntry.BOOKMARK.TAGS.json_attrb())) {
				this.bmk.put(YMarkEntry.BOOKMARK.TAGS.key(),obj.get(YMarkEntry.BOOKMARK.TAGS.json_attrb()));
			}
			try {
				this.bookmarks.put(this.bmk);
			} catch (final InterruptedException e) {
				ConcurrentLog.logException(e);
			}
			this.bmk = new YMarkEntry();	
		}
		this.isBookmark = false;
		return true;
	}

	@Override
    public boolean startObjectEntry(String key) throws ParseException, IOException {
		if(!this.isAnnos) {
			this.key.setLength(0);
			this.key.append(key);	
		}
		return true;
	}
	
	@Override
    public boolean primitive(Object value) throws ParseException, IOException {
		if(!this.isAnnos) {
			this.value.setLength(0);
			if(value instanceof java.lang.String) {
				this.value.append((String)value);
			} else if(value instanceof java.lang.Boolean) {
				this.value.append(value);
			} else if(value instanceof java.lang.Number) {
				this.value.append(value);
			}
		}
		return true;
	}

	@Override
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

	@Override
    public void run() {
		try {
			ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, "JSON Importer run()");
			this.parser.parse(this.bmk_file, this, true);
		} catch (final IOException e) {
			ConcurrentLog.logException(e);
		} catch (final ParseException e) {
			ConcurrentLog.logException(e);
		} finally {			
			try {
				ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, "JSON Importer inserted poison pill in queue");
				this.bookmarks.put(YMarkEntry.POISON);
			} catch (final InterruptedException e) {
				ConcurrentLog.logException(e);
			}
		}
	}
	
    public YMarkEntry take() {
        try {
            return this.bookmarks.take();
        } catch (final InterruptedException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }
}
