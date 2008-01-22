//rssParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 16.05.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma.parser.rss;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterAbstractScraper;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverCharBuffer;
import de.anomic.server.serverFileUtils;
import de.anomic.xml.rssReader;
import de.anomic.xml.rssReader.Item;
import de.anomic.yacy.yacyURL;

public class rssParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */  
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>(); 
    static {
        SUPPORTED_MIME_TYPES.put("text/rss","xml,rss,rdf"); 
        SUPPORTED_MIME_TYPES.put("application/rdf+xml","xml,rss,rdf");
        SUPPORTED_MIME_TYPES.put("application/rss+xml","xml,rss,rdf");
        SUPPORTED_MIME_TYPES.put("application/atom+xml","xml,atom");
    }     
  
    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {};       
    
	public rssParser() {
		super(LIBX_DEPENDENCIES);
        this.parserName = "Rich Site Summary/Atom Feed Parser"; 
	}

	public plasmaParserDocument parse(yacyURL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {

        try {
            LinkedList<String> feedSections = new LinkedList<String>();
            HashMap<yacyURL, String> anchors = new HashMap<yacyURL, String>();
            TreeSet<htmlFilterImageEntry> images  = new TreeSet<htmlFilterImageEntry>();
            serverByteBuffer text = new serverByteBuffer();
            serverCharBuffer authors = new serverCharBuffer();
            
            rssReader reader = new rssReader(source);
            
            // getting the rss feed title and description
            String feedTitle = reader.getChannel().getTitle();

            // getting feed creator
			String feedCreator = reader.getChannel().getAuthor();
			if (feedCreator != null && feedCreator.length() > 0) authors.append(",").append(feedCreator);            
            
            // getting the feed description
            String feedDescription = reader.getChannel().getDescription();
            
            if (reader.getImage() != null) {
                images.add(new htmlFilterImageEntry(new yacyURL(reader.getImage(), null), feedTitle, -1, -1));
            }            
            
            // loop through the feed items
            for (int i = 0; i < reader.items(); i++) {
                    // check for interruption
                    checkInterruption();
                    
                    // getting the next item
					Item item = reader.getItem(i);	
                    
        			String itemTitle = item.getTitle();
                    yacyURL    itemURL   = new yacyURL(item.getLink(), null);
        			String itemDescr = item.getDescription();
        			String itemCreator = item.getCreator();
        			if (itemCreator != null && itemCreator.length() > 0) authors.append(",").append(itemCreator);
                    
                    feedSections.add(itemTitle);
                    anchors.put(itemURL, itemTitle);
                    
                	if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
                	text.append(new serverCharBuffer(htmlFilterAbstractScraper.stripAll(new serverCharBuffer(itemDescr.toCharArray()))).trim().toString()).append(' ');
                    
                    String itemContent = item.getDescription();
                    if ((itemContent != null) && (itemContent.length() > 0)) {
                        
                        htmlFilterContentScraper scraper = new htmlFilterContentScraper(itemURL);
                        Writer writer = new htmlFilterWriter(null, null, scraper, null, false);
                        serverFileUtils.copy(new ByteArrayInputStream(itemContent.getBytes("UTF-8")), writer, "UTF-8");
                        
                        String itemHeadline = scraper.getTitle();     
                        if ((itemHeadline != null) && (itemHeadline.length() > 0)) {
                            feedSections.add(itemHeadline);
                        }
                        
                        Map<yacyURL, String> itemLinks = scraper.getAnchors();
                        if ((itemLinks != null) && (itemLinks.size() > 0)) {
                            anchors.putAll(itemLinks);
                        }
                        
                        TreeSet<htmlFilterImageEntry> itemImages = scraper.getImages();
                        if ((itemImages != null) && (itemImages.size() > 0)) {
                            images.addAll(itemImages);
                        }
                        
                        byte[] extractedText = scraper.getText();
                        if ((extractedText != null) && (extractedText.length > 0)) {
							if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
							text.append(scraper.getText());
                        }
                        
                    }
            }
            
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
                    feedTitle,
                    (authors.length() > 0)?authors.toString(1,authors.length()):"",
                    (String[]) feedSections.toArray(new String[feedSections.size()]),
                    feedDescription,
                    text.getBytes(),
                    anchors,
                    images);            
            
            return theDoc;
            
        } catch (Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing rss file." + e.getMessage(),location); 
        }
	}

	public Hashtable<String, String> getSupportedMimeTypes() {
		return SUPPORTED_MIME_TYPES;
	}

	public void reset() {
        // Nothing todo here at the moment
        super.reset();
	}

}
