package de.anomic.data;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
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
		NOTHING,
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
	
	private HashMap<String,String> bmk;
	private XBEL outer_state;					// BOOKMARK, FOLDER, NOTHING
    private XBEL inner_state;					// DESC, TITLE, INFO, ALIAS, (METADATA), NOTHING
    private boolean parse_value;
    private final StringBuilder buffer;
	private final StringBuilder folder;
	
	private final InputStream input;
	private final ArrayBlockingQueue<HashMap<String,String>> bookmarks;
	private final SAXParser saxParser;

    
    public YMarksXBELImporter (final InputStream input, int queueSize) throws SAXException {
        this.bmk = null;
    	this.buffer = new StringBuilder();
        this.folder = new StringBuilder(YMarkTables.FOLDER_BUFFER_SIZE);
        this.folder.append(YMarkTables.FOLDERS_IMPORTED);
        this.bookmarks = new ArrayBlockingQueue<HashMap<String,String>>(queueSize);
        this.input = input;
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            this.saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException e) {
            Log.logException(e);
            throw new SAXException (e.getMessage());
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
        String date;
    	if (tag == null) return;
        tag = tag.toLowerCase();              
        if (XBEL.BOOKMARK.tag().equals(tag)) {
            this.bmk = new HashMap<String,String>();
            this.bmk.put(YMarkTables.BOOKMARK.URL.key(), atts.getValue(uri, YMarkTables.BOOKMARK.URL.xbel_attrb()));
            try {
				date = String.valueOf(YMarkTables.parseISO8601(atts.getValue(uri, YMarkTables.BOOKMARK.DATE_ADDED.xbel_attrb())));
			} catch (ParseException e) {
				date = String.valueOf(System.currentTimeMillis());
			}
            this.bmk.put(YMarkTables.BOOKMARK.DATE_ADDED.key(), date);
            try {
				date = String.valueOf(YMarkTables.parseISO8601(atts.getValue(uri, YMarkTables.BOOKMARK.DATE_VISITED.xbel_attrb())));
	            this.bmk.put(YMarkTables.BOOKMARK.DATE_VISITED.key(), date);
            } catch (ParseException e) {
			}
            try {
				date = String.valueOf(YMarkTables.parseISO8601(atts.getValue(uri, YMarkTables.BOOKMARK.DATE_MODIFIED.xbel_attrb())));
			} catch (ParseException e) {
				date = String.valueOf(System.currentTimeMillis());
			}
            this.bmk.put(YMarkTables.BOOKMARK.DATE_MODIFIED.key(), date);
            outer_state = XBEL.BOOKMARK;
            inner_state = XBEL.NOTHING;
            this.parse_value = false;            
        } else if(XBEL.FOLDER.tag().equals(tag)) {
        	this.outer_state = XBEL.FOLDER;
        	this.inner_state = XBEL.NOTHING;
        } else if (XBEL.DESC.tag().equals(tag)) {
            this.inner_state = XBEL.DESC;
        	this.parse_value = true;
        } else if (XBEL.TITLE.tag().equals(tag)) {
        	this.inner_state = XBEL.TITLE;
        	this.parse_value = true;
        } else if (XBEL.INFO.tag().equals(tag)) {
        	this.inner_state = XBEL.INFO;
        	this.parse_value = false;
        } else if (XBEL.METADATA.tag().equals(tag)) {
        	/*
        	this.meta_owner = atts.getValue(uri, "owner");
        	this.inner_state = XBEL.METADATA;
        	this.parse_value = true;
        	*/
        } else if (XBEL.ALIAS.tag().equals(tag)) {
        	// TODO: handle xbel aliases
        	/*
        	this.alias_ref = atts.getValue(uri, "ref");
        	this.inner_state = XBEL.ALIAS;
        	this.parse_value = false;
        	*/
        }
        else {
        	this.outer_state = XBEL.NOTHING;
        	this.inner_state = XBEL.NOTHING;
        	this.parse_value = false;
        }
        
    }

    public void endElement(final String uri, final String name, String tag) {
        if (tag == null) return;
        tag = tag.toLowerCase();
        if(XBEL.BOOKMARK.tag().equals(tag)) {
			// write bookmark
        	if (!this.bmk.isEmpty()) {
				this.bmk.put(YMarkTables.BOOKMARK.FOLDERS.key(), this.folder.toString());
        		try {
					this.bookmarks.put(this.bmk);
					bmk = new HashMap<String,String>();
				} catch (InterruptedException e) {
					Log.logException(e);
				}
			}
        	this.outer_state = XBEL.FOLDER;
        } else if (XBEL.FOLDER.tag().equals(tag)) {
        	// go up one folder
            //TODO: get rid of .toString.equals()
        	if(!this.folder.toString().equals(YMarkTables.FOLDERS_IMPORTED)) {
	    		folder.setLength(folder.lastIndexOf(YMarkTables.FOLDERS_SEPARATOR));
        	}
        	this.outer_state = XBEL.FOLDER;
        } else if (XBEL.INFO.tag().equals(tag)) {
        	this.inner_state = XBEL.NOTHING;
        } else if (XBEL.METADATA.tag().equals(tag)) {
        	this.inner_state = XBEL.INFO;
        }
    }

    public void characters(final char ch[], final int start, final int length) {
        if (parse_value) {
            buffer.append(ch, start, length);
            switch(outer_state) {
            	case BOOKMARK:
            		switch(inner_state) {
            			case DESC:
            				this.bmk.put(YMarkTables.BOOKMARK.DESC.key(), this.buffer.toString());
            				break;
            			case TITLE:
            				this.bmk.put(YMarkTables.BOOKMARK.TITLE.key(), this.buffer.toString());
            				break;
        				case METADATA:	
        					// TODO: handle xbel bookmark metadata
        					// this.meta_data = this.buffer.toString();
        					break;
            			default:
            				break;		
            		}
            		break;
            	case FOLDER:
            		switch(inner_state) {
	        			case DESC:
	        				break;
	        			case TITLE:
	        				this.folder.append(YMarkTables.FOLDERS_SEPARATOR);
	        				this.folder.append(this.buffer);
	        				break;
	        			case METADATA:
        					// TODO: handle xbel folder metadata
	        				// this.meta_data = this.buffer.toString();
	        				break;
	        			default:
	        				break;		
            		}
            		break;
            	default:
            		break;
             }
            this.buffer.setLength(0);
            this.parse_value = false;
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
