package de.anomic.data;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.logging.Log;

public class YMarksHTMLImporter extends HTMLEditorKit.ParserCallback {
    
    public static enum STATE {
    	NOTHING,
    	BOOKMARK,
    	FOLDER,
    	BMK_DESC,
    	FOLDER_DESC
    }
    
    private static final String MILLIS = "000";
    
    private final WorkTables worktables;
    private final String bmk_table;
    private final String tag_table;
    private final String folder_table;
		
	private STATE state;
	private HTML.Tag prevTag;
	private Data bookmark;
	private String folder;
	private String[] tagArray;
	private byte[] urlHash;
	
	public YMarksHTMLImporter(final WorkTables worktables, final String user) {
		this(worktables, user, YMarkTables.FOLDERS_IMPORTED);
	}
	
	public YMarksHTMLImporter(final WorkTables worktables, final String user, final String folder) {
		this.bmk_table = YMarkTables.TABLES.BOOKMARKS.tablename(user);
		this.tag_table = YMarkTables.TABLES.TAGS.tablename(user);
		this.folder_table = YMarkTables.TABLES.FOLDERS.tablename(user);		
		this.worktables = worktables;
		
		this.state = STATE.NOTHING;
		this.bookmark = new Data();
		
		if(folder.contains(YMarkTables.TAGS_SEPARATOR))
			this.folder = folder.substring(0, folder.indexOf(','));
		else if(!folder.startsWith(YMarkTables.FOLDERS_ROOT))
			this.folder = YMarkTables.FOLDERS_ROOT + folder;
		else
			this.folder = folder;
	}

	public void handleText(char[] data, int pos) {
    	switch (state) {
    		case NOTHING:
    			break;
    		case BOOKMARK:
				try {
					if(this.urlHash != null) {
						// only import new bookmarks					
						if(!worktables.has(this.bmk_table, this.urlHash)) {
							bookmark.put(YMarkTables.BOOKMARK.FOLDERS.key(), this.folder.getBytes());
		                    this.worktables.bookmarks.updateIndexTable(this.folder_table, this.folder, this.urlHash, YMarkTables.INDEX_ACTION.ADD);	                    
	                        if (this.tagArray != null) {
			                    for (final String tag : this.tagArray) {
		                        	this.worktables.bookmarks.updateIndexTable(this.tag_table, tag, this.urlHash, YMarkTables.INDEX_ACTION.ADD);
		                        }
	                        }
		                    this.worktables.insert(bmk_table, urlHash, bookmark);
						}
					}
                    break;
				} catch (IOException e) {
	                Log.logException(e);
				}
				break;
    		case FOLDER:
    			this.folder = this.folder + YMarkTables.FOLDERS_SEPARATOR + new String(data);
    			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - folder: "+this.folder);
			    break;
    		case FOLDER_DESC:
    			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - folder_desc: "+new String(data));
    			break;
    		case BMK_DESC:
    			Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - bmk_desc: "+new String(data));
    			break;    			
    		default:
    			break;
    	}
	}

	public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
	    if (t == HTML.Tag.A) {	    	
	    	this.urlHash = null;
	    	this.tagArray = null;
	    	this.bookmark.clear();
	    	final String url = (String)a.getAttribute(HTML.Attribute.HREF);
	    	try {
				this.urlHash = YMarkTables.getBookmarkId(url);
				this.bookmark.put(YMarkTables.BOOKMARK.URL.key(), url);
				Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - url: "+url);
			} catch (MalformedURLException e) {
				Log.logInfo(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - bmk_url malformed: "+url);
			}
	    	for (YMarkTables.BOOKMARK bmk : YMarkTables.BOOKMARK.values()) {    			
	    		final String s = (String)a.getAttribute(bmk.html_attrb());    			
    			if(s != null) {
	    			switch(bmk) {	    					
	    				case TAGS:
	    	    			this.tagArray = s.split(YMarkTables.TAGS_SEPARATOR);
	    					this.bookmark.put(bmk.key(), YMarkTables.cleanTagsString(s));
	    	    			break;
	    				case DATE_ADDED:
	    				case DATE_MODIFIED:
	    				case DATE_VISITED:
	    					this.bookmark.put(bmk.key(), s+MILLIS);
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
	    	// for some reason the <DD> is not recognized as StartTag
			state = STATE.FOLDER_DESC;
	    } else if (t == HTML.Tag.DL) {
	    	if(!folder.equals(YMarkTables.FOLDERS_IMPORTED)) {
	    		folder = folder.replaceAll("(/.[^/]*$)", "");
	    	}
	    } else {
	    	state = STATE.NOTHING;
	    }
	}
}
