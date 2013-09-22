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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import net.yacy.cora.document.feed.Hit;
import net.yacy.cora.document.feed.RSSFeed;
import net.yacy.cora.document.feed.RSSReader;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.parser.html.ImageEntry;

public class rssParser extends AbstractParser implements Parser {

    public rssParser() {
        super("RSS Parser");
        this.SUPPORTED_EXTENSIONS.add("rss");
        this.SUPPORTED_EXTENSIONS.add("xml");
        this.SUPPORTED_MIME_TYPES.add("XML");
        this.SUPPORTED_MIME_TYPES.add("text/rss");
        this.SUPPORTED_MIME_TYPES.add("application/rss+xml");
        this.SUPPORTED_MIME_TYPES.add("application/atom+xml");
    }

    @Override
    public Document[] parse(final AnchorURL url, final String mimeType,
            final String charset, final InputStream source)
            throws Failure, InterruptedException {
        RSSReader rssReader;
        try {
            rssReader = new RSSReader(RSSFeed.DEFAULT_MAXSIZE, source, RSSReader.Type.none);
        } catch (final IOException e) {
            throw new Parser.Failure("Load error:" + e.getMessage(), url, e);
        }

        final RSSFeed feed = rssReader.getFeed();
        //RSSMessage channel = feed.getChannel();
        final List<Document> docs = new ArrayList<Document>();
        AnchorURL uri;
        Set<String> languages;
        List<AnchorURL> anchors;
        Document doc;
        for (final Hit item: feed) try {
            uri = new AnchorURL(item.getLink());
            languages = new HashSet<String>();
            languages.add(item.getLanguage());
            anchors = new ArrayList<AnchorURL>();
            uri.setNameProperty(item.getTitle());
            anchors.add(uri);
            doc = new Document(
                    uri,
                    TextParser.mimeOf(url),
                    charset,
                    this,
                    languages,
                    item.getSubject(),
                    singleList(item.getTitle()),
                    item.getAuthor(),
                    item.getCopyright(),
                    new String[0],
                    item.getDescriptions(),
                    item.getLon(),
                    item.getLat(),
                    null,
                    anchors,
                    null,
                    new LinkedHashMap<AnchorURL, ImageEntry>(),
                    false,
                    item.getPubDate());
            docs.add(doc);
        } catch (final MalformedURLException e) {
            continue;
        }

        final Document[] da = new Document[docs.size()];
        docs.toArray(da);
        return da;
    }

}
