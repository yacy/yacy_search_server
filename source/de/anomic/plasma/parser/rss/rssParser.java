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
import java.io.OutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import de.anomic.htmlFilter.htmlFilterAbstractScraper;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterOutputStream;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverFileUtils;
import de.nava.informa.core.ChannelIF;
import de.nava.informa.core.ImageIF;
import de.nava.informa.impl.basic.ChannelBuilder;
import de.nava.informa.impl.basic.Item;
import de.nava.informa.parsers.FeedParser;

public class rssParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */  
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
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
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "informa-0.6.0.jar",
        "commons-logging.jar",
        "jdom.jar"
    };    
    
	public rssParser() {
		super(LIBX_DEPENDENCIES);
	}

	public plasmaParserDocument parse(URL location, String mimeType,
			InputStream source) throws ParserException {

        try {
            LinkedList feedSections = new LinkedList();
            HashMap anchors = new HashMap();
            HashMap images = new HashMap();
            serverByteBuffer text = new serverByteBuffer();
            
            
	        // creating a channel-builder
	        ChannelBuilder builder = new ChannelBuilder();   
            
            // parsing the rss/atom feed
	        ChannelIF channel = FeedParser.parse(builder, source);
            
            // getting the rss feed title and description
            String feedTitle = channel.getTitle();

            // getting the feed description
            String feedDescription = channel.getDescription();
            
            // getting the channel site url
            URL	channelSiteURL = channel.getSite();
            
            ImageIF channelImage = channel.getImage();
            if (channelImage != null) {
                images.put(channelImage.getLocation().toString(),channelImage.getTitle());
            }            
            
            // loop through the feed items
            Collection feedItemCollection = channel.getItems();
            if (!feedItemCollection.isEmpty()) {
				Iterator feedItemIterator = feedItemCollection.iterator();
                while (feedItemIterator.hasNext()) {
					Item item = (Item)feedItemIterator.next();	
                    
        			String itemTitle = item.getTitle();
        			URL    itemURL   = item.getLink();
        			String itemDescr = item.getDescription();
                    
                    feedSections.add(itemTitle);
                    anchors.put(itemURL.toString(),itemTitle);
                    
                	if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
                	text.append(new serverByteBuffer(htmlFilterAbstractScraper.stripAll(new serverByteBuffer(itemDescr.getBytes()))).trim()).append((byte) ' ');
                    
                    String itemContent = item.getElementValue("content");
                    if ((itemContent != null) && (itemContent.length() > 0)) {
                        
                        htmlFilterContentScraper scraper = new htmlFilterContentScraper(itemURL);
                        OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                        serverFileUtils.copy(new ByteArrayInputStream(itemContent.getBytes()), os);
                        
                        String itemHeadline = scraper.getHeadline();     
                        if ((itemHeadline != null) && (itemHeadline.length() > 0)) {
                            feedSections.add(itemHeadline);
                        }
                        
                        Map itemLinks = scraper.getAnchors();
                        if ((itemLinks != null) && (itemLinks.size() > 0)) {
                            anchors.putAll(itemLinks);
                        }
                        
                        Map itemImages = scraper.getImages();
                        if ((itemImages != null) && (itemImages.size() > 0)) {
                            images.putAll(itemImages);
                        }
                        
                        byte[] extractedText = scraper.getText();
                        if ((extractedText != null) && (extractedText.length > 0)) {
							if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
							text.append(scraper.getText());
                        }
                        
                    }
                }
            }
            
	        /* (URL location, String mimeType,
                    String keywords, String shortTitle, String longTitle,
                    String[] sections, String abstrct,
                    byte[] text, Map anchors, Map images)
            */
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    null,
                    null,
                    feedTitle,
                    (String[]) feedSections.toArray(new String[feedSections.size()]),
                    feedDescription,
                    text.getBytes(),
                    anchors,
                    images);            
            
            return theDoc;
            
        } catch (Exception e) {
            
        }
        
		return null;
	}

	public Hashtable getSupportedMimeTypes() {
		return SUPPORTED_MIME_TYPES;
	}

	public void reset() {
		// TODO Auto-generated method stub

	}

}
