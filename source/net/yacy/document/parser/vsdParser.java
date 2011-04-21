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

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.kelondro.logging.Log;

import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpsf.SummaryInformation;


public class vsdParser extends AbstractParser implements Parser {

    public vsdParser() {
        super("Microsoft Visio Parser");
        SUPPORTED_EXTENSIONS.add("vsd");
        SUPPORTED_EXTENSIONS.add("vss");
        SUPPORTED_EXTENSIONS.add("vst");
        SUPPORTED_EXTENSIONS.add("vdx");
        SUPPORTED_EXTENSIONS.add("vtx");
        SUPPORTED_MIME_TYPES.add("application/visio");
        SUPPORTED_MIME_TYPES.add("application/x-visio");
        SUPPORTED_MIME_TYPES.add("application/vnd.visio");
        SUPPORTED_MIME_TYPES.add("application/visio.drawing");
        SUPPORTED_MIME_TYPES.add("application/vsd");
        SUPPORTED_MIME_TYPES.add("application/x-vsd");
        SUPPORTED_MIME_TYPES.add("image/x-vsd");
        SUPPORTED_MIME_TYPES.add("zz-application/zz-winassoc-vsd");
    }
    
    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    public Document[] parse(final MultiProtocolURI location, final String mimeType, final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {

    	Document theDoc = null;
    	
        try {
            String contents = "";
            SummaryInformation summary = null;
            try {
                final VisioTextExtractor extractor = new VisioTextExtractor(source);
            	contents = extractor.getText();
                summary = extractor.getSummaryInformation();
            } catch (Exception e) {
            	Log.logWarning("vsdParser", e.getMessage());
            }

            String author = null;
            String[] keywords = null;
            String title = null;
            if (summary != null) {
                author = summary.getAuthor();
                if (summary.getKeywords() != null) {
                    keywords = summary.getKeywords().split("[ ,;]");
                }
                title = summary.getTitle();
            }

            String abstrct = null;
            abstrct = ((contents.length() > 80)? contents.substring(0, 80) : contents.trim()).
                          replaceAll("\r\n"," ").
                          replaceAll("\n"," ").
                          replaceAll("\r"," ").
                          replaceAll("\t"," ");
            
            if (title == null) {
                title = abstrct;
            }

           // As the result of parsing this function must return a plasmaParserDocument object
            return new Document[]{new Document(
                    location,     // url of the source document
                    mimeType,     // the documents mime type
                    "UTF-8",      // charset of the document text
                    this,
                    null,         // language
                    keywords,
                    title,
                    author,
                    "",
                    null,         // an array of section headlines
                    abstrct,      // an abstract
                    0.0f, 0.0f, 
                    UTF8.getBytes(contents),     // the parsed document text
                    null,         // a map of extracted anchors
                    null,
                    null,         // a treeset of image URLs
                    false)};
        } catch (final Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new ParserException
            final String errorMsg = "Unable to parse the vsd document '" + location + "':" + e.getMessage();
            this.log.logSevere(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        } finally {
            if (theDoc == null) {
                // if an unexpected error occures just log the error and raise a new Parser.Failure
                final String errorMsg = "Unable to parse the vsd document '" + location + "': possibly out of memory";
                this.log.logSevere(errorMsg);
                throw new Parser.Failure(errorMsg, location);
            }
        }
    }

}