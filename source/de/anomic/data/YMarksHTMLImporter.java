package de.anomic.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import net.yacy.kelondro.logging.Log;

public class YMarksHTMLImporter extends HTMLEditorKit.ParserCallback implements Runnable {
    
    public static enum STATE {
    	NOTHING,
    	BOOKMARK,
    	FOLDER,
    	BMK_DESC,
    	FOLDER_DESC
    }
    
    private static final String MILLIS = "000";
		
	private STATE state;
	private HTML.Tag prevTag;
	private HashMap<String,String> bmk;
	private String folder;
	
    private final InputStream input;
	private final BlockingQueue<HashMap<String,String>> bookmarks;
	private final ParserDelegator htmlParser;
	
	public YMarksHTMLImporter(final InputStream input, int queueSize) {		
		this.state = STATE.NOTHING;
		this.prevTag = null;
		this.bmk = new HashMap<String,String>();
		this.folder = YMarkTables.FOLDERS_IMPORTED;
		this.bookmarks = new ArrayBlockingQueue<HashMap<String,String>>(queueSize);
		this.input = input;
		this.htmlParser = new ParserDelegator();
	}

	public void run() {
		try {
			this.htmlParser.parse(new InputStreamReader(this.input,"UTF-8"), this, true);
		} catch (IOException e) {
			Log.logException(e);
		} finally {
			try {
				this.bookmarks.put(YMarkTables.POISON);
			} catch (InterruptedException e) {
				Log.logException(e);
			}
			try {
        		this.input.close();
			} catch (IOException e) {
			    Log.logException(e);
			}
		}
	}
	
	public void handleText(char[] data, int pos) {
    	switch (state) {
    		case NOTHING:
    			break;
    		case BOOKMARK:
				this.bmk.put(YMarkTables.BOOKMARK.TITLE.key(), new String(data));
				this.bmk.put(YMarkTables.BOOKMARK.FOLDERS.key(), this.folder);
				break;
    		case FOLDER:
    			this.folder = this.folder + YMarkTables.FOLDERS_SEPARATOR + new String(data);
    			break;
    		case FOLDER_DESC:
    			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - folder: "+this.folder+" desc: "+new String(data));
    			break;
    		case BMK_DESC:
    			this.bmk.put(YMarkTables.BOOKMARK.DESC.key(), new String(data));
    			break;    			
    		default:
    			break;
    	}
	}

	public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
		if (t == HTML.Tag.A) {
			if (!this.bmk.isEmpty()) {
				try {
					this.bookmarks.put(this.bmk);
					bmk = new HashMap<String,String>();
				} catch (InterruptedException e) {
					Log.logException(e);
				}
			}
	    	final String url = (String)a.getAttribute(HTML.Attribute.HREF);
			this.bmk.put(YMarkTables.BOOKMARK.URL.key(), url);
	    	
			for (YMarkTables.BOOKMARK bmk : YMarkTables.BOOKMARK.values()) {    			
	    		final String s = (String)a.getAttribute(bmk.html_attrb());    			
    			if(s != null) {
	    			switch(bmk) {	    					
	    				case TAGS:
	    					this.bmk.put(bmk.key(), YMarkTables.cleanTagsString(s));
	    	    			break;
	    				case DATE_ADDED:
	    				case DATE_MODIFIED:
	    				case DATE_VISITED:
	    					this.bmk.put(bmk.key(), s+MILLIS);
	    					break;
	    				default:
	    					break;		    					
	    			}
	    		} 		
	    	}
	    	state = STATE.BOOKMARK;
	    } else if (t == HTML.Tag.H3) {
	    	state = STATE.FOLDER;
	    } else if (t == HTML.Tag.DD && this.prevTag == HTML.Tag.A) {
	    	state = STATE.BMK_DESC;	    	
	    } else {
	    	state = STATE.NOTHING;
	    }
	    this.prevTag = t;
	}

	public void handleEndTag(HTML.Tag t, int pos) {
		if (t == HTML.Tag.H3) {
			state = STATE.FOLDER_DESC;
	    } else if (t == HTML.Tag.DL) {
	    	if(!folder.equals(YMarkTables.FOLDERS_IMPORTED)) {
	    		folder = folder.replaceAll("(/.[^/]*$)", "");
	    	}
	    } else {
	    	state = STATE.NOTHING;
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
