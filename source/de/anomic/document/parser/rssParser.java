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

package de.anomic.document.parser;

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

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.kelondro.util.FileUtils;

import de.anomic.content.RSSMessage;
import de.anomic.document.AbstractParser;
import de.anomic.document.Idiom;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.document.parser.html.AbstractScraper;
import de.anomic.document.parser.html.ContentScraper;
import de.anomic.document.parser.html.ImageEntry;
import de.anomic.document.parser.html.TransformerWriter;
import de.anomic.document.parser.xml.RSSFeed;
import de.anomic.document.parser.xml.RSSReader;
import de.anomic.server.serverCharBuffer;

public class rssParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */  
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("xml");
        SUPPORTED_EXTENSIONS.add("rss");
        SUPPORTED_EXTENSIONS.add("rdf");
        SUPPORTED_MIME_TYPES.add("text/rss");
        SUPPORTED_MIME_TYPES.add("application/rdf+xml");
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
            final serverCharBuffer authors = new serverCharBuffer();
            
            final RSSFeed feed = new RSSReader(source).getFeed();
            if (feed == null) throw new ParserException("no feed in document",location);
            if (feed.getChannel() == null) throw new ParserException("no channel in document",location);
            
            // getting the rss feed title and description
            final String feedTitle = feed.getChannel().getTitle();

            // getting feed creator
			final String feedCreator = feed.getChannel().getAuthor();
			if (feedCreator != null && feedCreator.length() > 0) authors.append(",").append(feedCreator);            
            
            // getting the feed description
            final String feedDescription = feed.getChannel().getDescription();
            
            if (feed.getImage() != null) {
                final DigestURI imgURL = new DigestURI(feed.getImage(), null);
                images.put(imgURL.hash(), new ImageEntry(imgURL, feedTitle, -1, -1));
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
                        if ((itemHeadline != null) && (itemHeadline.length() > 0)) {
                            feedSections.add(itemHeadline);
                        }
                        
                        final Map<DigestURI, String> itemLinks = scraper.getAnchors();
                        if ((itemLinks != null) && (itemLinks.size() > 0)) {
                            anchors.putAll(itemLinks);
                        }
                        
                        final HashMap<String, ImageEntry> itemImages = scraper.getImages();
                        if ((itemImages != null) && (itemImages.size() > 0)) {
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
