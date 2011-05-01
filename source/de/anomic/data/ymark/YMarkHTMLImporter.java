// YMarkHTMLImporter.java
// (C) 2011 by Stefan Förster, sof@gmx.de, Norderstedt, Germany
// first published 2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.data.ymark;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.ArrayBlockingQueue;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import net.yacy.kelondro.logging.Log;

public class YMarkHTMLImporter extends HTMLEditorKit.ParserCallback implements Runnable {
        
    // Importer Variables
    private final ArrayBlockingQueue<YMarkEntry> bookmarks;
    private final Reader bmk_file;
    private final String RootFolder;
    private final StringBuilder folderstring;
    private YMarkEntry bmk;
    private final ParserDelegator htmlParser;
    
    // Statics
    public static enum STATE {
        NOTHING,
        BOOKMARK,
        FOLDER,
        BMK_DESC,
        FOLDER_DESC
    }
    public static final String MILLIS = "000";
    
    // Parser variables    
    private STATE state;
	private HTML.Tag prevTag;
	
	public YMarkHTMLImporter(final Reader bmk_file, final int queueSize, final String root) {		
        this.bookmarks = new ArrayBlockingQueue<YMarkEntry>(queueSize);
        this.bmk_file = bmk_file;
        this.RootFolder = root;
        this.folderstring = new StringBuilder(YMarkTables.BUFFER_LENGTH);
        this.folderstring.append(this.RootFolder);        
        this.bmk = new YMarkEntry();
        
        this.htmlParser = new ParserDelegator();
        
	    this.state = STATE.NOTHING;
		this.prevTag = null;
	}

	public void run() {
		try {
			this.htmlParser.parse(this.bmk_file, this, true);
		} catch (IOException e) {
			Log.logException(e);
		} finally {
			try {
				this.bookmarks.put(YMarkEntry.POISON);
			} catch (InterruptedException e) {
				Log.logException(e);
			}
			try {
        		this.bmk_file.close();
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
				this.bmk.put(YMarkEntry.BOOKMARK.TITLE.key(), new String(data));
				this.bmk.put(YMarkEntry.BOOKMARK.FOLDERS.key(), this.folderstring.toString());
				this.bmk.put(YMarkEntry.BOOKMARK.PUBLIC.key(), YMarkEntry.BOOKMARK.PUBLIC.deflt());
				this.bmk.put(YMarkEntry.BOOKMARK.VISITS.key(), YMarkEntry.BOOKMARK.VISITS.deflt());
				break;
    		case FOLDER:
    			this.folderstring.append(YMarkUtil.FOLDERS_SEPARATOR);
    			this.folderstring.append(data);
    			break;
    		case FOLDER_DESC:
    			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - folder: "+this.folderstring+" desc: " + new String(data));
    			break;
    		case BMK_DESC:
    			this.bmk.put(YMarkEntry.BOOKMARK.DESC.key(), new String(data));
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
					bmk = new YMarkEntry();
				} catch (InterruptedException e) {
					Log.logException(e);
				}
			}
	    	final String url = (String)a.getAttribute(HTML.Attribute.HREF);
			this.bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);
	    	
			for (YMarkEntry.BOOKMARK bmk : YMarkEntry.BOOKMARK.values()) {    
				final String s = (String)a.getAttribute(bmk.html_attrb());    			
    			if(s != null) {
	    			switch(bmk) {	    					
	    				case TAGS:
	    					// mozilla shortcuturl
	    					this.bmk.put(bmk.key(), YMarkUtil.cleanTagsString(s));
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
            //TODO: get rid of .toString.equals()
        	if(!this.folderstring.toString().equals(this.RootFolder)) {
	    		folderstring.setLength(folderstring.lastIndexOf(YMarkUtil.FOLDERS_SEPARATOR));
        	}
	    } else {
	    	state = STATE.NOTHING;
	    }
	}
	
    public YMarkEntry take() {
        try {
            return this.bookmarks.take();
        } catch (InterruptedException e) {
            Log.logException(e);
            return null;
        }
    }
}
