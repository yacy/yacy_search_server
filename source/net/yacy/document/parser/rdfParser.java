/**
 *  rssParser.java
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 20.08.2010 at https://yacy.net
 *
 * $LastChangedDate: 2011-04-21 15:58:49 +0200 (Do, 21 Apr 2011) $
 * $LastChangedRevision: 7672 $
 * $LastChangedBy: orbiter $
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;

public class rdfParser extends AbstractParser implements Parser {

    public rdfParser() {
        super("RDF Parser");

        this.SUPPORTED_EXTENSIONS.add("rdf");
        this.SUPPORTED_MIME_TYPES.add("application/rdf+xml");
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


    	// this function currently only registers detected rdf files.

    	// next step: load rdf content into triplestore.

        final List<Document> docs = new ArrayList<Document>();

        Document doc;

        String all = "rdfdatasource";
		doc = new Document(location, mimeType, charset, null, null, null, singleList(""), null,
					"", null, null, 0, 0, all, null, null, null, false, new Date());

        docs.add(doc);

        final Document[] da = new Document[docs.size()];
        docs.toArray(da);
        return da;
    }

}
