/**
 *  rssParser.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 20.08.2010 at http://yacy.net
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.RSSFeed;
import net.yacy.cora.document.RSSReader;
import net.yacy.cora.document.Hit;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.parser.html.ImageEntry;

public class rssParser extends AbstractParser implements Parser {

    public rssParser() {
        super("RSS Parser");
        SUPPORTED_EXTENSIONS.add("rss");
        SUPPORTED_EXTENSIONS.add("xml");
        SUPPORTED_MIME_TYPES.add("XML");
        SUPPORTED_MIME_TYPES.add("text/rss");
        SUPPORTED_MIME_TYPES.add("application/rss+xml");
        SUPPORTED_MIME_TYPES.add("application/atom+xml");
    }
    
    public Document[] parse(final MultiProtocolURI url, final String mimeType,
            final String charset, final InputStream source)
            throws Failure, InterruptedException {
        RSSReader rssReader;
        try {
            rssReader = new RSSReader(RSSFeed.DEFAULT_MAXSIZE, source, RSSReader.Type.none);
        } catch (IOException e) {
            throw new Parser.Failure("Load error:" + e.getMessage(), url, e);
        }
        
        final RSSFeed feed = rssReader.getFeed();
        //RSSMessage channel = feed.getChannel();
        final List<Document> docs = new ArrayList<Document>();
        MultiProtocolURI uri;
        Set<String> languages;
        Map<MultiProtocolURI, Properties> anchors;
        Document doc;
        for (final Hit item: feed) try {
            uri = new MultiProtocolURI(item.getLink());
            languages = new HashSet<String>();
            languages.add(item.getLanguage());
            anchors = new HashMap<MultiProtocolURI, Properties>();
            Properties p = new Properties();
            p.put("name", item.getTitle());
            anchors.put(uri, p);
            doc = new Document(
                    uri,
                    TextParser.mimeOf(url),
                    charset,
                    this,
                    languages,
                    item.getSubject(),
                    item.getTitle(),
                    item.getAuthor(),
                    item.getCopyright(),
                    new String[0],
                    item.getDescription(),
                    item.getLon(),
                    item.getLat(),
                    null,
                    anchors,
                    null,
                    new HashMap<MultiProtocolURI, ImageEntry>(),
                    false);
            docs.add(doc);
        } catch (MalformedURLException e) {
            continue;
        }
        
        final Document[] da = new Document[docs.size()];
        docs.toArray(da);
        return da;
    }

}
