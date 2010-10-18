package de.anomic.data;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Data;
import net.yacy.kelondro.logging.Log;

public class YMarksHTMLImporter extends HTMLEditorKit.ParserCallback {

    private static final int NOTHING = 0;
    private static final int BOOKMARK = 2;
    private static final int FOLDER   = 3;
    
    private Tables worktables;
    private String bmk_table;

	private int state;
	private String folder = YMarkTables.TABLE_FOLDERS_IMPORTED;
	private String href;
	private Date date_added;
	private Date date_visited;
	private Date date_modified;
	
	public YMarksHTMLImporter(final Tables worktables, final String bmk_table) {
		this.bmk_table = bmk_table;
		this.worktables = worktables;
	}
	
    public void handleText(char[] data, int pos) {
    	switch (state) {
    		case NOTHING:
    			break;
    		case BOOKMARK:
    			Data bmk = new Data();
				byte[] urlHash;
				try {
					if(href.toLowerCase().startsWith("http://") || href.toLowerCase().startsWith("http://")) {
						urlHash = YMarkTables.getBookmarkId(this.href);
	        			// create and insert new entry                
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_URL, this.href.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_TITLE, (new String(data)).getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_DESC, YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC, YMarkTables.TABLE_BOOKMARKS_COL_PUBLIC_FALSE.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_TAGS, YMarkTables.TABLE_BOOKMARKS_COL_DEFAULT.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_VISITS, YMarkTables.TABLE_BOOKMARKS_COL_VISITS_ZERO.getBytes());
	                    bmk.put(YMarkTables.TABLE_BOOKMARKS_COL_FOLDER, this.folder.getBytes());
	                    // bmk.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_ADDED, DateFormatter.formatShortMilliSecond(this.date_added).getBytes());
	                    // bmk.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_MODIFIED, DateFormatter.formatShortMilliSecond(this.date_modified).getBytes());
	                    // bmk.put(YMarkStatics.TABLE_BOOKMARKS_COL_DATE_VISITED, DateFormatter.formatShortMilliSecond(this.date_visited).getBytes());
	                    worktables.insert(bmk_table, urlHash, bmk);
					}
                    break;
				} catch (MalformedURLException e) {
	                Log.logException(e);
				} catch (IOException e) {
	                Log.logException(e);
				}
				break;
    		case FOLDER:
    			this.folder = this.folder + YMarkTables.TABLE_FOLDERS_SEPARATOR + new String(data);
    			Log.logInfo("IMPORT folder:", folder);
			    break;
    		default:
    			break;
    	}
        state = NOTHING;
	}

	public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
	    if (t == HTML.Tag.A) {
	    	this.href = (String)a.getAttribute(HTML.Attribute.HREF);
	    	// this.date_added = new Date(Long.parseLong((String)a.getAttribute("add_date"))*1000l);
	    	// this.date_visited = new Date(Long.parseLong((String)a.getAttribute("last_visit"))*1000l);
	    	// this.date_modified = new Date(Long.parseLong((String)a.getAttribute("last_modified"))*1000l);	    	
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
