//vsdParser.java
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;

import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpsf.SummaryInformation;


public class vsdParser extends AbstractParser implements Parser {

    public vsdParser() {
        super("Microsoft Visio Parser");
        this.SUPPORTED_EXTENSIONS.add("vsd");
        this.SUPPORTED_EXTENSIONS.add("vss");
        this.SUPPORTED_EXTENSIONS.add("vst");
        this.SUPPORTED_EXTENSIONS.add("vdx");
        this.SUPPORTED_EXTENSIONS.add("vtx");
        this.SUPPORTED_MIME_TYPES.add("application/visio");
        this.SUPPORTED_MIME_TYPES.add("application/x-visio");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.visio");
        this.SUPPORTED_MIME_TYPES.add("application/visio.drawing");
        this.SUPPORTED_MIME_TYPES.add("application/vsd");
        this.SUPPORTED_MIME_TYPES.add("application/x-vsd");
        this.SUPPORTED_MIME_TYPES.add("image/x-vsd");
        this.SUPPORTED_MIME_TYPES.add("zz-application/zz-winassoc-vsd");
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
            throws Parser.Failure, InterruptedException {

        try {
            String contents = "";
            SummaryInformation summary = null;
            try {
            	// TODO : the VisioTextExtractor doesn't support Visio XML based formats such as .vdx or .vtx : handle this with a custom parser or with the relevant apache.poi component
                final VisioTextExtractor extractor = new VisioTextExtractor(source);
            	contents = extractor.getText();
                summary = extractor.getSummaryInformation();
                extractor.close();
            } catch (final Exception e) {
            	ConcurrentLog.warn("vsdParser", e.getMessage());
            }

            String author = null;
            String[] keywords = null;
            String title = null;
            List<String> descriptions = null;
            Date lastModified = null;
            if (summary != null) {
                author = summary.getAuthor();
                if (summary.getKeywords() != null) {
                    keywords = summary.getKeywords().split("[ ,;]");
                }
                title = summary.getTitle();
                final String subject = summary.getSubject();
                if (subject != null && !subject.isEmpty()) {
                    descriptions = new ArrayList<String>();
                    descriptions.add(subject);
                }
                lastModified = summary.getLastSaveDateTime();
            }

            if (title == null) title = location.toNormalform(true);

           // As the result of parsing this function must return a plasmaParserDocument object
            return new Document[]{new Document(
                    location,     // url of the source document
                    mimeType,     // the documents mime type
                    StandardCharsets.UTF_8.name(),      // charset of the document text
                    this,
                    null,         // language
                    keywords,
                    singleList(title),
                    author,
                    null,
                    null,         // an array of section headlines
                    descriptions, // an abstract
                    0.0d, 0.0d,
                    contents,     // the parsed document text
                    null,         // a map of extracted anchors
                    null,
                    null,         // a treeset of image URLs
                    false,
                    lastModified)};
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new ParserException
            final String errorMsg = "Unable to parse the vsd document '" + location + "':" + e.getMessage();
            AbstractParser.log.severe(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
    }
}