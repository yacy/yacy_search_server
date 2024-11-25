/**
 *  rssParser.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 20.08.2010 at https://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package net.yacy.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import net.yacy.cora.document.feed.Hit;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ImageEntry;

public class rssParser extends AbstractParser implements Parser {
	
	private final static ConcurrentLog LOG = new ConcurrentLog(rssParser.class.getSimpleName());

    public rssParser() {
        super("RSS Parser");
        this.SUPPORTED_EXTENSIONS.add("rss");
        this.SUPPORTED_EXTENSIONS.add("xml");
        this.SUPPORTED_MIME_TYPES.add("xml");
        this.SUPPORTED_MIME_TYPES.add("text/rss");
        this.SUPPORTED_MIME_TYPES.add("text/xml");
        this.SUPPORTED_MIME_TYPES.add("application/rss+xml");
        this.SUPPORTED_MIME_TYPES.add("application/atom+xml");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Failure, InterruptedException {
        final RSSReader rssReader;
        try {
            rssReader = new RSSReader(RSSFeed.DEFAULT_MAXSIZE, source);
        } catch (final IOException e) {
            throw new Parser.Failure("Load error:" + e.getMessage(), location, e);
        }

        return rssFeedToDocuments(charset, rssReader.getFeed());
    }

    /**
     * Create parsed documents from the given feed.
     * @param charset the charset name of the feed, if known
     * @param feed the feed instance 
     * @return an array of documents : a document per feed item
     */
	private Document[] rssFeedToDocuments(final String charset, final RSSFeed feed) {
        //RSSMessage channel = feed.getChannel();
        final List<Document> docs = new ArrayList<Document>();
        DigestURL itemuri;
        Set<String> languages;
        Document doc;
        for (final Hit item : feed) {
        	final String linkUrlString = item.getLink();
        	itemuri = null;
        	if(StringUtils.isNotBlank(linkUrlString)) {
            	/* Link element is optional in RSS 2.0 and Atom */
        		try {
        			itemuri = new DigestURL(item.getLink());
        		} catch(final MalformedURLException e) {
        			LOG.warn("Malformed feed item link url : " + linkUrlString);
        		}
        	}
        	languages = new HashSet<String>();
        	languages.add(item.getLanguage());
        		
        	Set<AnchorURL> anchors = null;
        	final String enclosureUrlString = item.getEnclosure();
        	if(StringUtils.isNotBlank(enclosureUrlString)) {
        		try {
        			final AnchorURL enclosureUrl = new AnchorURL(enclosureUrlString);
        			if(itemuri == null) {
        				/* No <link> element in this item : the enclosure URL is used as the sub document main location URL */
        				itemuri = enclosureUrl;
        			} else {
        				anchors = new HashSet<>();
        				anchors.add(enclosureUrl);
        			}
        		} catch(final MalformedURLException e) {
        			LOG.warn("Malformed feed item enclosure url : " + enclosureUrlString);
        		}
        	}
        		
        	if(itemuri != null) {
        		doc = new Document(
        				itemuri,
        				TextParser.mimeOf(itemuri),
        				charset,
        				this,
        				languages,
        				item.getSubject(),
        				singleList(item.getTitle()),
        				item.getAuthor(),
        				item.getCopyright(),
        				null,
        				item.getDescriptions(),
        				item.getLon(),
        				item.getLat(),
        				null,
        				anchors,
        				null,
        				new LinkedHashMap<DigestURL, ImageEntry>(),
        				false,
        				item.getPubDate());
        		docs.add(doc);
        	}
        }

        final Document[] da = new Document[docs.size()];
        docs.toArray(da);
        return da;
	}
    
    @Override
    public boolean isParseWithLimitsSupported() {
    	return true;
    }
    
    @Override
    public Document[] parseWithLimits(final DigestURL url, final String mimeType, final String charset, final VocabularyScraper scraper,
    		final int timezoneOffset, final InputStream source, final int maxLinks, final long maxBytes)
    		throws Failure, InterruptedException, UnsupportedOperationException {
        RSSReader rssReader;
        try {
            rssReader = new RSSReader(maxLinks, maxBytes, source);
        } catch (final IOException e) {
            throw new Parser.Failure("Load error:" + e.getMessage(), url, e);
        }

        Document[] documents =  rssFeedToDocuments(charset, rssReader.getFeed());
		if (documents != null && documents.length > 0
				&& (rssReader.isMaxBytesExceeded() || rssReader.getFeed().isMaxSizeExceeded())) {
			/* A limit has been exceeded : mark the last document as partially parsed for information of the caller */
			documents[documents.length - 1].setPartiallyParsed(true);
		}
        return documents;
    }

}
