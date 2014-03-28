// YMarkDMOZImporter.java
// (C) 2012 by Stefan Foerster (apfelmaennchen), sof@gmx.de, Norderstedt, Germany
// first published 2012 on http://yacy.net
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

import net.yacy.cora.lod.vocabulary.DMOZ;
import net.yacy.cora.lod.vocabulary.DublinCore;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class YMarkDMOZImporter extends YMarkImporter {
    // Statics
	public static String IMPORTER = "DMOZ";
	
    // Importer Variables
	private final XMLReader xmlReader;
	private int depth;
	
	public YMarkDMOZImporter(final MonitoredReader dmoz_file, final int queueSize, final String targetFolder, final String sourceFolder) throws SAXException {
		super(dmoz_file, queueSize, sourceFolder, targetFolder);
		setImporter(IMPORTER);
		this.xmlReader = XMLReaderFactory.createXMLReader();
        this.xmlReader.setFeature(XML_NAMESPACE_PREFIXES, false);
        this.xmlReader.setFeature(XML_NAMESPACES, false);
        this.xmlReader.setFeature(XML_VALIDATION, false);
		this.xmlReader.setContentHandler(new DMOZParser());
		this.depth = Integer.MAX_VALUE;
	}
	
	@Override
    public void parse() throws Exception {
		xmlReader.parse(new InputSource(bmk_file));
	}
	
	public void setDepth(int d) {
		this.depth = d + YMarkUtil.FOLDERS_SEPARATOR_PATTERN.split(this.targetFolder).length-1;
	}
	
	public class DMOZParser extends DefaultHandler {
		
		private YMarkEntry bmk;
		private boolean	isNewEntry;
		private boolean isSubtopic;
		private String tag;
		private final StringBuilder buffer;
		
		public DMOZParser() {
			this.bmk = new YMarkEntry();
			this.isNewEntry = false;
			this.isSubtopic = false;
			this.buffer = new StringBuilder(512);
		}
		
		@Override
        public void startElement(final String uri, String localName, final String qName, final Attributes attributes) throws SAXException {
			// get rid of namespace prefixes
			if (localName.isEmpty()) {
				localName = qName.substring(qName.indexOf(':')+1);
			}
			this.tag = null;    	
	    	if (localName.equals(DMOZ.ExternalPage.name())) {
    			this.bmk = new YMarkEntry();
    			this.bmk.put(YMarkEntry.BOOKMARK.URL.key(), attributes.getValue(0));
            	this.isNewEntry = true;
    		}
    		if(isNewEntry && localName.equals(DublinCore.Title.name())) {
    			this.tag = YMarkEntry.BOOKMARK.TITLE.key();
    		}
    		if(isNewEntry && localName.equals(DublinCore.Description.name())) {
    			this.tag = YMarkEntry.BOOKMARK.DESC.key();
    		}
    		if(isNewEntry && localName.equals(DMOZ.topic.name())) {
    			this.tag = YMarkEntry.BOOKMARK.FOLDERS.key();
				buffer.append(targetFolder);
				buffer.append(YMarkUtil.FOLDERS_SEPARATOR);
    		}		    
		}

		@Override
        public void endElement(final String uri, String localName, final String qName) throws SAXException {
			// get rid of namespace prefixes
			if (localName.isEmpty()) {
				localName = qName.substring(qName.indexOf(':')+1);
			}
			if (this.isNewEntry && this.isSubtopic && localName.equals(DMOZ.ExternalPage.name())) {
                try {
                	bookmarks.put(this.bmk);
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                } finally {
        			this.isSubtopic = false;
        			this.isNewEntry = false;
                }
    		} else if(localName.equals(DMOZ.topic.name())) {
    			int d = 0;
    			for(int i=0; i<this.buffer.length(); i++) {
    				if (this.buffer.charAt(i) == '/') {
    					d++;
    					if (d > depth) {
    						this.buffer.setLength(i);
    						break;
    					}
    				}
    			}
    			if (this.buffer.substring(targetFolder.length()+1).startsWith(sourceFolder)) {
	    			this.isSubtopic = true;
	    			this.bmk.put(this.tag, YMarkUtil.cleanFoldersString(buffer));
				} else {
					this.isSubtopic = false;
					this.isNewEntry = false;
				}
    		} else if (this.tag != null) {
    			this.bmk.put(this.tag, buffer.toString());
    		}
			this.tag = null;
			this.buffer.setLength(0);
		}

		@Override
        public void characters(final char ch[], final int start, final int length) throws SAXException {
			// no processing here, as the SAX Parser characters method could be called more than once per tag!
			if(this.tag != null) {				
				buffer.append(ch, start, length); 
			}
		}
	}
}

