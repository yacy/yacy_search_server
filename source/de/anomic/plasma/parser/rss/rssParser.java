//rssParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma.parser.rss;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;

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
import de.anomic.xml.RSSFeed;
import de.anomic.xml.RSSMessage;
import de.anomic.xml.RSSReader;
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

	public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {

        try {
            final LinkedList<String> feedSections = new LinkedList<String>();
            final HashMap<yacyURL, String> anchors = new HashMap<yacyURL, String>();
            final HashMap<String, htmlFilterImageEntry> images  = new HashMap<String, htmlFilterImageEntry>();
            final serverByteBuffer text = new serverByteBuffer();
            final serverCharBuffer authors = new serverCharBuffer();
            
            final RSSFeed feed = new RSSReader(source).getFeed();
            
            // getting the rss feed title and description
            final String feedTitle = feed.getChannel().getTitle();

            // getting feed creator
			final String feedCreator = feed.getChannel().getAuthor();
			if (feedCreator != null && feedCreator.length() > 0) authors.append(",").append(feedCreator);            
            
            // getting the feed description
            final String feedDescription = feed.getChannel().getDescription();
            
            if (feed.getImage() != null) {
                final yacyURL imgURL = new yacyURL(feed.getImage(), null);
                images.put(imgURL.hash(), new htmlFilterImageEntry(imgURL, feedTitle, -1, -1));
            }            
            
            // loop through the feed items
            for (final RSSMessage item: feed) {
                    // check for interruption
                    checkInterruption();
                    
        			final String itemTitle = item.getTitle();
                    final yacyURL    itemURL   = new yacyURL(item.getLink(), null);
        			final String itemDescr = item.getDescription();
        			final String itemCreator = item.getCreator();
        			if (itemCreator != null && itemCreator.length() > 0) authors.append(",").append(itemCreator);
                    
                    feedSections.add(itemTitle);
                    anchors.put(itemURL, itemTitle);
                    
                	if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
                	serverCharBuffer scb = new serverCharBuffer(htmlFilterAbstractScraper.stripAll(new serverCharBuffer(itemDescr.toCharArray())));
                	text.append(scb.trim().toString()).append(' ');
                	scb.close();
                    
                    final String itemContent = item.getDescription();
                    if ((itemContent != null) && (itemContent.length() > 0)) {
                        
                        final htmlFilterContentScraper scraper = new htmlFilterContentScraper(itemURL);
                        final Writer writer = new htmlFilterWriter(null, null, scraper, null, false);
                        serverFileUtils.copy(new ByteArrayInputStream(itemContent.getBytes("UTF-8")), writer, "UTF-8");
                        
                        final String itemHeadline = scraper.getTitle();     
                        if ((itemHeadline != null) && (itemHeadline.length() > 0)) {
                            feedSections.add(itemHeadline);
                        }
                        
                        final Map<yacyURL, String> itemLinks = scraper.getAnchors();
                        if ((itemLinks != null) && (itemLinks.size() > 0)) {
                            anchors.putAll(itemLinks);
                        }
                        
                        final HashMap<String, htmlFilterImageEntry> itemImages = scraper.getImages();
                        if ((itemImages != null) && (itemImages.size() > 0)) {
                            htmlFilterContentScraper.addAllImages(images, itemImages);
                        }
                        
                        final byte[] extractedText = scraper.getText();
                        if ((extractedText != null) && (extractedText.length > 0)) {
							if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
							text.append(scraper.getText());
                        }
                        
                    }
            }
            
            final plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
                    feedTitle,
                    (authors.length() > 0)?authors.toString(1,authors.length()):"",
                    feedSections.toArray(new String[feedSections.size()]),
                    feedDescription,
                    text.getBytes(),
                    anchors,
                    images);            
            // close streams
            text.close();
            authors.close();
            
            
            return theDoc;
            
        } catch (final InterruptedException e) {
        	throw e;
        } catch (final IOException e) {
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
