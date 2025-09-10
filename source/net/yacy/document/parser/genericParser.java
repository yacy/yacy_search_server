/**
 *  genericParser
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 30.11.2010 at https://yacy.net
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

import java.io.InputStream;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;

/**
 * this parser can parse just anything because it uses only the uri/file/path information
 */
public class genericParser extends AbstractParser implements Parser {

    public genericParser() {
        super("Generic Parser");
        // no SUPPORTED_EXTENSIONS and no SUPPORTED_MIME_TYPES
        // this parser is used if no other fits. This parser fits all
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Parser.Failure, InterruptedException {
    	/* Exactly the same implementation when applying limits as only tokens in the location URL are parsed */
        return parseWithLimits(location, mimeType, charset, scraper, timezoneOffset, source, Integer.MAX_VALUE, Long.MAX_VALUE);
    }
    
    @Override
    public boolean isParseWithLimitsSupported() {
    	return true;
    }
    
	@Override
	public Document[] parseWithLimits(final DigestURL location, final String mimeType, final String charset,
			final VocabularyScraper scraper, final int timezoneOffset, final InputStream source, final int maxLinks,
			final long maxBytes) throws Failure, InterruptedException, UnsupportedOperationException {
        String filename = location.getFileName();
        final Document[] docs = new Document[]{new Document(
                location,
                mimeType,
                charset,
                this,
                null,
                null,
                singleList(filename.isEmpty() ? location.toTokens() : MultiProtocolURL.unescape(filename)), // title
                null, // author
                location.getHost(),
                null,
                null,
                0.0d, 0.0d,
                location.toTokens(),
                null,
                null,
                null,
                false,
                null)};
        return docs;
    }
}
