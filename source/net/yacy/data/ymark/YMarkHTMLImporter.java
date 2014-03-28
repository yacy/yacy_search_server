// YMarkHTMLImporter.java
// (C) 2011 by Stefan F��rster, sof@gmx.de, Norderstedt, Germany
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

package net.yacy.data.ymark;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import net.yacy.cora.util.ConcurrentLog;

public class YMarkHTMLImporter extends YMarkImporter {
	
    // Importer Variables
    private final ParserDelegator htmlParser;
    
    // Statics
	public static String IMPORTER = "HTML";
    public static enum STATE {
        NOTHING,
        BOOKMARK,
        FOLDER,
        BMK_DESC,
        FOLDER_DESC
    }
    public static final String MILLIS = "000";

	public YMarkHTMLImporter(final MonitoredReader bmk_file, final int queueSize, final String targetFolder, final String sourceFolder) {		
		super(bmk_file, queueSize, targetFolder, sourceFolder);
		setImporter(IMPORTER);
		this.htmlParser = new ParserDelegator();
	}
	
	public YMarkHTMLImporter (final MonitoredReader bmk_file, final int queueSize, final String targetFolder) {
		this(bmk_file, queueSize, targetFolder, "");
	}	
	
	@Override
    public void parse() throws Exception {
		htmlParser.parse(bmk_file, new HTMLParser(), true);
	}
	
	public class HTMLParser extends HTMLEditorKit.ParserCallback {
	    
	    private YMarkEntry bmk;
	    private final StringBuilder folderstring;
	    private STATE state;
		private HTML.Tag prevTag;
		
	    public HTMLParser() {
	        this.folderstring = new StringBuilder(YMarkTables.BUFFER_LENGTH);
	        this.folderstring.append(targetFolder);        
	        this.bmk = new YMarkEntry();
		    this.state = STATE.NOTHING;
			this.prevTag = null;
	    }
		
		@Override
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
	    			ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, "YMarksHTMLImporter - folder: "+this.folderstring+" desc: " + new String(data));
	    			break;
	    		case BMK_DESC:
	    			this.bmk.put(YMarkEntry.BOOKMARK.DESC.key(), new String(data));
	    			break;    			
	    		default:
	    			break;
	    	}
		}

		@Override
        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
			if (t == HTML.Tag.A) {
				if (!this.bmk.isEmpty()) {
					try {
						bookmarks.put(this.bmk);
						bmk = new YMarkEntry();
					} catch (final InterruptedException e) {
						ConcurrentLog.logException(e);
					}
				}
		    	final String url = (String)a.getAttribute(HTML.Attribute.HREF);
				this.bmk.put(YMarkEntry.BOOKMARK.URL.key(), url);	    	
				final StringBuilder sb = new StringBuilder(255);
				for (YMarkEntry.BOOKMARK bmk : YMarkEntry.BOOKMARK.values()) {    
					sb.setLength(0);   			
					if (a.isDefined(bmk.html_attrb())) {
						sb.append((String)a.getAttribute(bmk.html_attrb()));
						ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, bmk.key()+" : "+sb.toString());
					}
	    			switch(bmk) {	    					
	    				case TAGS:	    					
	    					// sb already contains the mozilla shortcuturl
	    					// add delicious.com tags that are stored in the tags attribute	    					
	    					if (a.isDefined(YMarkEntry.BOOKMARK.TAGS.key())) {		    					
	    						sb.append(YMarkUtil.TAGS_SEPARATOR);
	    						sb.append((String)a.getAttribute(YMarkEntry.BOOKMARK.TAGS.key()));		    					
	    					}
	    					this.bmk.put(bmk.key(), YMarkUtil.cleanTagsString(sb.toString()));
	    	    			break;
	    				case PUBLIC:
	    					// look for delicious.com private attribute
	    					if(sb.toString().equals("0"))
	    						this.bmk.put(bmk.key(), "true");
	    					break;
	    				case DATE_ADDED:
	    				case DATE_MODIFIED:
	    				case DATE_VISITED:
	   						sb.append(MILLIS);    					
	   						this.bmk.put(bmk.key(), sb.toString());
	    					break;
	    				default:
	    					break;		    					
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

		@Override
        public void handleEndTag(HTML.Tag t, int pos) {
			// write the last bookmark, as no more <a> tags are following
			if (t == HTML.Tag.HTML) {
				if (!this.bmk.isEmpty()) {
					try {
						bookmarks.put(this.bmk);
					} catch (final InterruptedException e) {
						ConcurrentLog.logException(e);
					}
				}
			}
			if (t == HTML.Tag.H3) {
				state = STATE.FOLDER_DESC;
		    } else if (t == HTML.Tag.DL) {
	            //TODO: get rid of .toString.equals()
	        	if(!this.folderstring.toString().equals(targetFolder)) {
		    		folderstring.setLength(folderstring.lastIndexOf(YMarkUtil.FOLDERS_SEPARATOR));
	        	}
		    } else {
		    	state = STATE.NOTHING;
		    }
		}
	}
}
