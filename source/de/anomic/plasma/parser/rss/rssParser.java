package de.anomic.plasma.parser.rss;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
     * a list of file extensions that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final HashSet SUPPORTED_FILE_EXT = new HashSet(Arrays.asList(new String[] {
        new String("xml"),
        new String("rss"),
        new String("rdf"),
        new String("atom")
    }));     
    
	public rssParser() {
		super();
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

	public HashSet getSupportedFileExtensions() {
		// TODO Auto-generated method stub
		return SUPPORTED_FILE_EXT;
	}

}
