//swfParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Marc Nause
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ContentScraper;
import pt.tumba.parser.swf.SWF2HTML;

public class swfParser extends AbstractParser implements Parser {

    public swfParser() {
        super("Adobe Flash Parser");
        this.SUPPORTED_EXTENSIONS.add("swf");
        this.SUPPORTED_MIME_TYPES.add("application/x-shockwave-flash");
        this.SUPPORTED_MIME_TYPES.add("application/x-shockwave-flash2-preview");
        this.SUPPORTED_MIME_TYPES.add("application/futuresplash");
        this.SUPPORTED_MIME_TYPES.add("image/vnd.rn-realflash");
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Parser.Failure, InterruptedException
    {

        try {
            final SWF2HTML swf2html = new SWF2HTML();
            String contents = "";
            try {
                contents = swf2html.convertSWFToHTML(source);
                scraperObject = htmlParser.parseToScraper(location, charset, scraper, timezoneOffset, contents, 100);
            } catch (final NegativeArraySizeException e) {
                throw new Parser.Failure(e.getMessage(), location);
            } catch (final IOException e) {
                throw new Parser.Failure(e.getMessage(), location);
            } catch (final Exception e) {
                throw new Parser.Failure(e.getMessage(), location);
            }

            // As the result of parsing this function must return a plasmaParserDocument object
            ContentScraper htmlscraper = (ContentScraper) this.scraperObject; // shortcut to access ContentScraper methodes
            return new Document[]{new Document(
                location, // url of the source document
                mimeType, // the documents mime type
                StandardCharsets.UTF_8.name(), // charset of the document text
                this,
                htmlscraper.getContentLanguages(),
                htmlscraper.getKeywords(),
                htmlscraper.getTitles(),
                htmlscraper.getAuthor(),
                htmlscraper.getPublisher(),
                null, // sections
                htmlscraper.getDescriptions(),
                htmlscraper.getLon(), htmlscraper.getLat(),
                htmlscraper.getText(),
                htmlscraper.getAnchors(),
                htmlscraper.getRSS(),
                null, // images
                false,
                htmlscraper.getDate())};
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new Parser.Failure
            final String errorMsg = "Unable to parse the swf document '" + location + "':" + e.getMessage();
            //AbstractParser.log.logSevere(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
    }

}