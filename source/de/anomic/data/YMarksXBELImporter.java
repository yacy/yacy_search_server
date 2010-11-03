package de.anomic.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.kelondro.logging.Log;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class YMarksXBELImporter extends DefaultHandler implements Runnable {

	public static enum XBEL {
		XBEL,
		TITLE,
		DESC,
		BOOKMARK,
		FOLDER,
		SEPARATOR,
		ALIAS,
		INFO,
		METADATA;
		
		public String tag() {
			return this.toString().toLowerCase();
		}
	}
	
    public static enum STATE {
    	NOTHING,
    	BOOKMARK,
    	INFO,
    	FOLDER,
    	FOLDER_DESC
    }
	
	private HashMap<String,String> bmk;
    private boolean parsingValue;
    private STATE state;
	private String keyname;
	private String folder;
	private final InputStream input;
    private final StringBuilder buffer;
	private final ArrayBlockingQueue<HashMap<String,String>> bookmarks;
	private final SAXParser saxParser;

    
    public YMarksXBELImporter (final InputStream input, int queueSize) throws IOException {
        this.buffer = new StringBuilder();
        this.bmk = null;
		this.folder = YMarkTables.FOLDERS_IMPORTED;
        this.bookmarks = new ArrayBlockingQueue<HashMap<String,String>>(queueSize);
        this.input = input;
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            this.saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException e) {
            Log.logException(e);
            throw new IOException(e.getMessage());
        } catch (SAXException e) {
            Log.logException(e);
            throw new IOException(e.getMessage());
        }
    }
    
    public void run() {
        try {
            this.saxParser.parse(this.input, this);
        } catch (SAXParseException e) {
            Log.logException(e);
        } catch (SAXException e) {
            Log.logException(e);
        } catch (IOException e) {
            Log.logException(e);
        } finally {
        	try {
				this.bookmarks.put(YMarkTables.POISON);
			} catch (InterruptedException e1) {
			    Log.logException(e1);
			}
			try {
        		this.input.close();
			} catch (IOException e) {
			    Log.logException(e);
			}
        }
    }
    
    public void startElement(final String uri, final String name, String tag, final Attributes atts) throws SAXException {
        if (tag == null) return;
        tag = tag.toLowerCase();              
        if (XBEL.BOOKMARK.tag().equals(tag)) {
            this.bmk = new HashMap<String,String>();
            this.bmk.put(YMarkTables.BOOKMARK.URL.key(), atts.getValue(uri, YMarkTables.BOOKMARK.URL.xbel_attrb()));
            this.bmk.put(YMarkTables.BOOKMARK.DATE_ADDED.key(), atts.getValue(uri, YMarkTables.BOOKMARK.DATE_ADDED.xbel_attrb()));
            this.bmk.put(YMarkTables.BOOKMARK.DATE_VISITED.key(), atts.getValue(uri, YMarkTables.BOOKMARK.DATE_VISITED.xbel_attrb()));
            this.bmk.put(YMarkTables.BOOKMARK.DATE_MODIFIED.key(), atts.getValue(uri, YMarkTables.BOOKMARK.DATE_MODIFIED.xbel_attrb()));
            state = STATE.BOOKMARK;
            this.parsingValue = false;            
        } else if(XBEL.FOLDER.tag().equals(tag)) {
        	this.state = STATE.FOLDER;
        } else if (XBEL.DESC.tag().equals(tag)) {
            if(this.state == STATE.FOLDER) {
            	this.keyname = null;
            	this.state = STATE.FOLDER_DESC;            	
            } else if (this.state == STATE.BOOKMARK) {
            	this.keyname = YMarkTables.BOOKMARK.DESC.key();
            } else {
            	Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksXBELImporter - state: "+this.state+" tag: "+tag);
            	this.parsingValue = false;
            	return;
            }
        	this.parsingValue = true;
        } else if (XBEL.TITLE.tag().equals(tag)) {
            if(this.state == STATE.FOLDER) {
            	this.keyname = null;
            } else if (this.state == STATE.BOOKMARK) {
            	this.keyname = YMarkTables.BOOKMARK.TITLE.key();
            } else {
            	Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksXBELImporter - state: "+this.state+" tag: "+tag);
            	this.parsingValue = false;
            	return;
            }
        	this.parsingValue = true;
        } else if (XBEL.INFO.tag().equals(tag)) {
        	this.parsingValue = false;
        	this.state = STATE.INFO;
        } else {
        	this.parsingValue = false;
        	this.state = STATE.NOTHING;
        }
        
    }

    public void endElement(final String uri, final String name, String tag) {
        if (tag == null) return;
        tag = tag.toLowerCase();
        if(XBEL.BOOKMARK.tag().equals(tag)) {
			// write bookmark
        	if (!this.bmk.isEmpty()) {
				this.bmk.put(YMarkTables.BOOKMARK.FOLDERS.key(), this.folder);
        		try {
					this.bookmarks.put(this.bmk);
					bmk = new HashMap<String,String>();
				} catch (InterruptedException e) {
					Log.logException(e);
				}
			}
        	this.state = STATE.FOLDER;
        } else if (XBEL.FOLDER.tag().equals(tag)) {
        	this.state = STATE.NOTHING;
        	// go up one folder
        	if(!folder.equals(YMarkTables.FOLDERS_IMPORTED)) {
	    		folder = folder.replaceAll(YMarkIndex.PATTERN_REPLACE, "");
	    		this.state = STATE.FOLDER;
        	}        	
        } else if (XBEL.INFO.tag().equals(tag)) {
        	this.state = STATE.BOOKMARK;
        }
    }

    public void characters(final char ch[], final int start, final int length) {
        if (parsingValue) {
            buffer.append(ch, start, length);
            if (this.state == STATE.BOOKMARK) {
            	this.bmk.put(this.keyname, this.buffer.toString());
            } else if (this.state == STATE.FOLDER) {
            	this.folder = this.folder + YMarkTables.FOLDERS_SEPARATOR + this.buffer.toString();
            } else if (this.state == STATE.FOLDER_DESC) {
            	Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksXBELImporter - folder: "+this.folder+" desc: "+this.buffer.toString());
            	this.state = STATE.FOLDER;
            }
        this.buffer.setLength(0);
        this.parsingValue = false;
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
