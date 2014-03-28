// YMarkXBELImporter.java
// (C) 2011 by Stefan Foerster, sof@gmx.de, Norderstedt, Germany
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

import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;

import net.yacy.cora.util.ConcurrentLog;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class YMarkXBELImporter extends YMarkImporter {
   
	// Statics
	public static String IMPORTER = "XBEL";
	public static enum XBEL {
		NOTHING			(""),
		XBEL			("<xbel"),
		TITLE			("<title"),
		DESC			("<desc"),
		BOOKMARK		("<bookmark"),
		FOLDER			("<folder"),
		SEPARATOR		("<separator"),
		ALIAS			("<alias"),
		INFO			("<info"),
		METADATA		("<metadata");
		
	    private static StringBuilder buffer = new StringBuilder(25);
		private String tag;
		
		private XBEL(String t) {
			this.tag = t;
		}
		public String tag() {
			return this.toString().toLowerCase();
		}
		public String endTag(boolean empty) {
			buffer.setLength(0);
			buffer.append(tag);
			if(empty) {
				buffer.append('/');			
			} else {
				buffer.insert(1, '/');
			}
			buffer.append('>');
			return buffer.toString();
		}
		public String startTag(boolean att) {
			buffer.setLength(0);
			buffer.append(tag);
			if(!att)
				buffer.append('>');
			return buffer.toString();
		}
	}
	
    // Importer Variables
	private final XMLReader xmlReader;
	
	public YMarkXBELImporter (final MonitoredReader bmk_file, final int queueSize, final String targetFolder, final String sourceFolder) throws SAXException {
		super(bmk_file, queueSize, targetFolder, sourceFolder);
		setImporter(IMPORTER);
		this.xmlReader = XMLReaderFactory.createXMLReader();
        this.xmlReader.setFeature(XML_NAMESPACE_PREFIXES, false);
        this.xmlReader.setFeature(XML_NAMESPACES, false);
        this.xmlReader.setFeature(XML_VALIDATION, false);
		this.xmlReader.setContentHandler(new XBELParser());
	}
	
	public YMarkXBELImporter (final MonitoredReader bmk_file, final int queueSize, final String targetFolder) throws SAXException {
		this(bmk_file, queueSize, "", targetFolder);
	}	
	
	@Override
    public void parse() throws Exception {
		xmlReader.parse(new InputSource(bmk_file));
	}
	
	public class XBELParser extends DefaultHandler {
		
		// Parser Variables
	    private final StringBuilder folderstring;
		private final HashMap<String,YMarkEntry> bmkRef;
		private final HashSet<YMarkEntry> aliasRef;
	    private final StringBuilder buffer;
	    private final StringBuilder folder;
			
	    private YMarkEntry bmk;
	    private YMarkEntry ref;
	    private XBEL outer_state;                   // BOOKMARK, FOLDER, NOTHING
	    private XBEL inner_state;                   // DESC, TITLE, INFO, ALIAS, (METADATA), NOTHING
	    private boolean parse_value;
	    
	    
		public XBELParser() {
	        this.folderstring = new StringBuilder(YMarkTables.BUFFER_LENGTH);
	        this.folderstring.append(targetFolder);
	        this.bmk = new YMarkEntry();
			this.bmkRef = new HashMap<String,YMarkEntry>();
			this.aliasRef = new HashSet<YMarkEntry>();
			this.buffer = new StringBuilder();
			this.folder = new StringBuilder(YMarkTables.BUFFER_LENGTH);
			this.folder.append(targetFolder);
		}
	    
	    @Override
        public void endDocument() throws SAXException {
	    	// put alias references in the bookmark queue to ensure that folders get updated
	    	// we do that at endDocument to ensure all referenced bookmarks already exist
	    	bookmarks.addAll(this.aliasRef);
	    	this.aliasRef.clear();
	    	this.bmkRef.clear();
	    }
	    
	    @Override
        public void startElement(final String uri, final String name, String tag, final Attributes atts) throws SAXException {
	        YMarkDate date = new YMarkDate();
	        if (tag == null) return;
	        tag = tag.toLowerCase();              
	        if (XBEL.BOOKMARK.tag().equals(tag)) {
	            this.bmk = new YMarkEntry();            
	            this.bmk.put(YMarkEntry.BOOKMARK.URL.key(), atts.getValue(uri, YMarkEntry.BOOKMARK.URL.xbel_attrb()));
	            //TODO: include a dynamic loop over all annotation tags 
	            this.bmk.put(YMarkEntry.BOOKMARK.TAGS.key(), atts.getValue(uri, YMarkEntry.BOOKMARK.TAGS.xbel_attrb()));
	            this.bmk.put(YMarkEntry.BOOKMARK.PUBLIC.key(), atts.getValue(uri, YMarkEntry.BOOKMARK.PUBLIC.xbel_attrb()));
	            this.bmk.put(YMarkEntry.BOOKMARK.VISITS.key(), atts.getValue(uri, YMarkEntry.BOOKMARK.VISITS.xbel_attrb()));
	            try {
					date.parseISO8601(atts.getValue(uri, YMarkEntry.BOOKMARK.DATE_ADDED.xbel_attrb()));
				} catch (final ParseException e) {
					// TODO: exception handling
				}
	            this.bmk.put(YMarkEntry.BOOKMARK.DATE_ADDED.key(), date.toString());
	            try {
					date.parseISO8601(atts.getValue(uri, YMarkEntry.BOOKMARK.DATE_VISITED.xbel_attrb()));
	            } catch (final ParseException e) {
	            	// TODO: exception handling
	            }
	            this.bmk.put(YMarkEntry.BOOKMARK.DATE_VISITED.key(), date.toString());
	            try {
					date.parseISO8601(atts.getValue(uri, YMarkEntry.BOOKMARK.DATE_MODIFIED.xbel_attrb()));
				} catch (final ParseException e) {
					// TODO: exception handling
				}
	            this.bmk.put(YMarkEntry.BOOKMARK.DATE_MODIFIED.key(), date.toString());
	            UpdateBmkRef(atts.getValue(uri, YMarkEntry.BOOKMARKS_ID), true);
	            this.outer_state = XBEL.BOOKMARK;
	            this.inner_state = XBEL.NOTHING;
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
	        	// Support for old YaCy BookmarksDB XBEL Metadata (non valid XBEL)        	
	        	if(this.outer_state == XBEL.BOOKMARK) {
	        		final boolean isMozillaShortcutURL = atts.getValue(uri, "owner").equals("Mozilla") && !atts.getValue(uri, "ShortcutURL").isEmpty();
	        		final boolean isYacyPublic = atts.getValue(uri, "owner").equals("YaCy") && !atts.getValue(uri, "public").isEmpty();
	        		if(isMozillaShortcutURL)
	        			this.bmk.put(YMarkEntry.BOOKMARK.TAGS.key(), YMarkUtil.cleanTagsString(atts.getValue(uri, "ShortcutURL")));
	        		if(isYacyPublic)
	        			this.bmk.put(YMarkEntry.BOOKMARK.PUBLIC.key(), atts.getValue(uri, "public"));        			
	        	}
	        } else if (XBEL.ALIAS.tag().equals(tag)) {
	        	final String r = atts.getValue(uri, YMarkEntry.BOOKMARKS_REF);
	        	UpdateBmkRef(r, false);
	        	this.aliasRef.add(this.bmkRef.get(r));
	        }
	        else {
	        	this.outer_state = XBEL.NOTHING;
	        	this.inner_state = XBEL.NOTHING;
	        	this.parse_value = false;
	        }
	    }

	    @Override
        public void endElement(final String uri, final String name, String tag) {
	        if (tag == null) return;
	        tag = tag.toLowerCase();
	        if(XBEL.BOOKMARK.tag().equals(tag)) {
				// write bookmark
	        	if (!this.bmk.isEmpty()) {				
	        		this.bmk.put(YMarkEntry.BOOKMARK.FOLDERS.key(), this.folder.toString());
	        		try {
						bookmarks.put(this.bmk);
						bmk = new YMarkEntry();
					} catch (final InterruptedException e) {
						ConcurrentLog.logException(e);
					}
				}
	        	this.outer_state = XBEL.FOLDER;
	        } else if (XBEL.FOLDER.tag().equals(tag)) {
	        	// go up one folder
	            //TODO: get rid of .toString.equals()
	        	if(!this.folder.toString().equals(targetFolder)) {
	        		folder.setLength(folder.lastIndexOf(YMarkUtil.FOLDERS_SEPARATOR));
	        	}
	        	this.outer_state = XBEL.FOLDER;
	        } else if (XBEL.INFO.tag().equals(tag)) {
	        	this.inner_state = XBEL.NOTHING;
	        } else if (XBEL.METADATA.tag().equals(tag)) {
	        	this.inner_state = XBEL.INFO;
	        }
	    }

	    @Override
        public void characters(final char ch[], final int start, final int length) {
	        // TODO move string processing to endElement as characters() could be called more than once per tag
	    	if (parse_value) {
	        	buffer.append(ch, start, length);      	        	
	        	switch(outer_state) {
	            	case BOOKMARK:
	            		switch(inner_state) {
	            			case DESC:            				
	            				this.bmk.put(YMarkEntry.BOOKMARK.DESC.key(), buffer.toString().trim());
	            				break;
	            			case TITLE:
	            				this.bmk.put(YMarkEntry.BOOKMARK.TITLE.key(), buffer.toString().trim());
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
		        				this.folder.append(YMarkUtil.FOLDERS_SEPARATOR);
		        				this.folder.append(this.buffer);
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
	    
	    private void UpdateBmkRef(final String id, final boolean url) {
	    	this.folderstring.setLength(0);
	    	
	    	if(this.bmkRef.containsKey(id)) {
	        	this.folderstring.append(this.bmkRef.get(id).get(YMarkEntry.BOOKMARK.FOLDERS.key()));
	        	this.folderstring.append(',');
	        	this.ref = this.bmkRef.get(id);
	        } else {
	            this.ref = new YMarkEntry();
	        }
	    	this.folderstring.append(this.folder);
	        if(url)
	        	this.ref.put(YMarkEntry.BOOKMARK.URL.key(), this.bmk.get(YMarkEntry.BOOKMARK.URL.key()));
	        this.ref.put(YMarkEntry.BOOKMARK.FOLDERS.key(), this.folderstring.toString());
	        this.bmkRef.put(id, ref);
	    }		
	}
}
