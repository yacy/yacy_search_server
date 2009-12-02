//rssParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.document.content.RSSMessage;
import net.yacy.document.parser.html.AbstractScraper;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.document.parser.xml.RSSFeed;
import net.yacy.document.parser.xml.RSSReader;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;


public class rssParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */  
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("rss");
        SUPPORTED_EXTENSIONS.add("xml");
        SUPPORTED_MIME_TYPES.add("XML");
        SUPPORTED_MIME_TYPES.add("text/rss");
        SUPPORTED_MIME_TYPES.add("application/rss+xml");
        SUPPORTED_MIME_TYPES.add("application/atom+xml");
    }
    
	public rssParser() {
		super("Rich Site Summary/Atom Feed Parser"); 
	}

	public Document parse(final DigestURI location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {

        try {
            final LinkedList<String> feedSections = new LinkedList<String>();
            final HashMap<DigestURI, String> anchors = new HashMap<DigestURI, String>();
            final HashMap<String, ImageEntry> images  = new HashMap<String, ImageEntry>();
            final ByteBuffer text = new ByteBuffer();
            final CharBuffer authors = new CharBuffer();
            
            final RSSFeed feed = new RSSReader(source).getFeed();
            if (feed == null) throw new ParserException("no feed in document",location);
            
            String feedTitle = "";
            String feedDescription = "";
            if (feed.getChannel() != null) {//throw new ParserException("no channel in document",location);
                
                // get the rss feed title and description
                feedTitle = feed.getChannel().getTitle();
    
                // get feed creator
    			final String feedCreator = feed.getChannel().getAuthor();
    			if (feedCreator != null && feedCreator.length() > 0) authors.append(",").append(feedCreator);            
                
                // get the feed description
                feedDescription = feed.getChannel().getDescription();
            }
            
            if (feed.getImage() != null) {
                final DigestURI imgURL = new DigestURI(feed.getImage(), null);
                images.put(imgURL.hash(), new ImageEntry(imgURL, feedTitle, -1, -1, -1));
            }            
            
            // loop through the feed items
            for (final RSSMessage item: feed) {
                    // check for interruption
                    checkInterruption();
                    
        			final String itemTitle = item.getTitle();
                    final DigestURI    itemURL   = new DigestURI(item.getLink(), null);
        			final String itemDescr = item.getDescription();
        			final String itemCreator = item.getCreator();
        			if (itemCreator != null && itemCreator.length() > 0) authors.append(",").append(itemCreator);
                    
                    feedSections.add(itemTitle);
                    anchors.put(itemURL, itemTitle);
                    
                	if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
                	text.append(AbstractScraper.stripAll(itemDescr).trim()).append(' ');
                    
                    final String itemContent = item.getDescription();
                    if ((itemContent != null) && (itemContent.length() > 0)) {
                        
                        final ContentScraper scraper = new ContentScraper(itemURL);
                        final Writer writer = new TransformerWriter(null, null, scraper, null, false);
                        FileUtils.copy(new ByteArrayInputStream(itemContent.getBytes("UTF-8")), writer, Charset.forName("UTF-8"));
                        
                        final String itemHeadline = scraper.getTitle();     
                        if (itemHeadline != null && !itemHeadline.isEmpty()) {
                            feedSections.add(itemHeadline);
                        }
                        
                        final Map<DigestURI, String> itemLinks = scraper.getAnchors();
                        if (itemLinks != null && !itemLinks.isEmpty()) {
                            anchors.putAll(itemLinks);
                        }
                        
                        final HashMap<String, ImageEntry> itemImages = scraper.getImages();
                        if (itemImages != null && !itemImages.isEmpty()) {
                            ContentScraper.addAllImages(images, itemImages);
                        }
                        
                        final byte[] extractedText = scraper.getText();
                        if ((extractedText != null) && (extractedText.length > 0)) {
							if ((text.length() != 0) && (text.byteAt(text.length() - 1) != 32)) text.append((byte) 32);
							text.append(scraper.getText());
                        }
                        
                    }
            }
            
            final Document theDoc = new Document(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
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

	public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    @Override
	public void reset() {
        // Nothing todo here at the moment
        super.reset();
	}

}
