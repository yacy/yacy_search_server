package de.anomic.data;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.logging.Log;

public class YMarksHTMLImporter extends HTMLEditorKit.ParserCallback {

    private static final short NOTHING = 0;
    private static final short BOOKMARK = 2;
    private static final short FOLDER   = 3;
	private static final String MILLIS = "000";
    
    private final WorkTables worktables;
    private final String bmk_table;
    private final String tag_table;
    private final String folder_table;
	private final String[] tagArray;
	private final String tagsString;
		
	private short state;
	private String folder;
	private String href;
	private String date_added;
	private String date_visited;
	private String date_modified;
	

	public YMarksHTMLImporter(final WorkTables worktables, final String user) {
		this(worktables, user, YMarkTables.TABLE_FOLDERS_IMPORTED, null);
	}
	
	public YMarksHTMLImporter(final WorkTables worktables, final String user, final String folder) {
		this(worktables, user, folder, null);
	}
	
	public YMarksHTMLImporter(final WorkTables worktables, final String user, final String folder, final String tagsString) {
		this.bmk_table = user + YMarkTables.TABLE_BOOKMARKS_BASENAME;
		this.tag_table = user + YMarkTables.TABLE_TAGS_BASENAME;
		this.folder_table = user + YMarkTables.TABLE_FOLDERS_BASENAME;		
		this.worktables = worktables;
		
		if(folder.contains(YMarkTables.TABLE_TAGS_SEPARATOR))
			this.folder = folder.substring(0, folder.indexOf(','));
		else if(!folder.startsWith(YMarkTables.TABLE_FOLDERS_ROOT))
			this.folder = YMarkTables.TABLE_FOLDERS_ROOT + folder;
		else
			this.folder = folder;
		
		this.tagsString = tagsString;
		if(tagsString != null)
			this.tagArray = tagsString.split(YMarkTables.TABLE_TAGS_SEPARATOR);
		else 
			this.tagArray = null;
	}

	public void handleText(char[] data, int pos) {
    	switch (state) {
    		case NOTHING:
    			break;
    		case BOOKMARK:
				try {
					final byte[] urlHash = YMarkTables.getBookmarkId(this.href);
					// only import new bookmarks					
					if(!worktables.has(this.bmk_table, urlHash)) {
	        			// create and insert new entry                
	                   final Data bmk = new Data();
						bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_URL, this.href.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_TITLE, (new String(data)).getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_DESC, YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC, YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC_FALSE.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_VISITS, YMarkTables.TABLE_BOOKMARKS_COL_VISITS_ZERO.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_DATE_ADDED, this.date_added.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_DATE_MODIFIED, this.date_modified.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_DATE_VISITED, this.date_visited.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_FOLDERS, this.folder.getBytes());
	                    this.worktables.bookmarks.updateIndexTable(this.folder_table, this.folder, urlHash, YMarkTables.TABLE_INDEX_ACTION_ADD);
	                    Log.logInfo(YMarkTables.TABLE_BOOKMARKS_LOG, "YMarksHTMLImporter - folder: "+this.folder);
	                    if (this.tagsString != null) {
	                    	bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_TAGS, this.tagsString.getBytes());                   
	                        for (final String tag : tagArray) {
	                        	this.worktables.bookmarks.updateIndexTable(this.tag_table, tag, urlHash, YMarkTables.TABLE_INDEX_ACTION_ADD);
	                        } 
	                    }
	                    this.worktables.insert(bmk_table, urlHash, bmk);
	                    Log.logInfo(YMarkTables.TABLE_BOOKMARKS_LOG, "YMarksHTMLImporter - url successfully imported: "+this.href);
					} else {
						Log.logInfo(YMarkTables.TABLE_BOOKMARKS_LOG, "YMarksHTMLImporter - url already exists: "+this.href);
					}
                    break;
				} catch (MalformedURLException e) {
					Log.logInfo(YMarkTables.TABLE_BOOKMARKS_LOG, "YMarksHTMLImporter - malformed url: "+this.href);
				} catch (IOException e) {
	                Log.logException(e);
				}
				break;
    		case FOLDER:
    			this.folder = this.folder + YMarkTables.TABLE_FOLDERS_SEPARATOR + new String(data);
			    break;
    		default:
    			break;
    	}
        state = NOTHING;
	}

	public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
	    if (t == HTML.Tag.A) {
	    	this.href = (String)a.getAttribute(HTML.Attribute.HREF);
	    	this.date_added = (String)a.getAttribute("add_date")+MILLIS;
	    	this.date_visited = (String)a.getAttribute("last_visit")+MILLIS;
	    	this.date_modified = (String)a.getAttribute("last_modified")+MILLIS;	    	
	    	state = BOOKMARK;
	    } else if (t == HTML.Tag.H3) {
	    	state = FOLDER;
	    }
	}

	public void handleEndTag(HTML.Tag t, int pos) {
	    if (t == HTML.Tag.DL) {
	    	if(!folder.equals(YMarkTables.TABLE_FOLDERS_IMPORTED)) {
	    		folder = folder.replaceAll("(/.[^/]*$)", "");
	    	}
	    }
	}
}
