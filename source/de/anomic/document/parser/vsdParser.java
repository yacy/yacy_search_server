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

package de.anomic.document.parser;

import java.io.InputStream;
import java.util.Hashtable;

import de.anomic.document.AbstractParser;
import de.anomic.document.Idiom;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.yacy.yacyURL;
import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpsf.SummaryInformation;

public class vsdParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();
    static {
        SUPPORTED_MIME_TYPES.put("application/visio","vsd");
        SUPPORTED_MIME_TYPES.put("application/x-visio","vsd");
        SUPPORTED_MIME_TYPES.put("application/vnd.visio","vsd");
        SUPPORTED_MIME_TYPES.put("application/visio.drawing","vsd");
        SUPPORTED_MIME_TYPES.put("application/vsd","vsd");
        SUPPORTED_MIME_TYPES.put("application/x-vsd","vsd");
        SUPPORTED_MIME_TYPES.put("image/x-vsd","vsd");
        SUPPORTED_MIME_TYPES.put("zz-application/zz-winassoc-vsd","vsd");
    }

    public vsdParser() {
        super();
        this.parserName = "Microsoft Visio Parser";
    }

    /**
     * returns a hashtable containing the mimetypes that are supported by this class
     */
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    public Document parse(final yacyURL location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {

    	Document theDoc = null;
    	
        try {
            String contents = "";
            SummaryInformation summary = null;
            try {
                VisioTextExtractor extractor = new VisioTextExtractor(source);
            	contents = extractor.getText();
                summary = extractor.getSummaryInformation();
            } catch (Exception e) {
            	e.printStackTrace();
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
            abstrct = ((contents.length() > 80)? contents.substring(0, 80):contents.trim()).
                          replaceAll("\r\n"," ").
                          replaceAll("\n"," ").
                          replaceAll("\r"," ").
                          replaceAll("\t"," ");
            
            if (title == null) {
                title = abstrct;
            }

           // As the result of parsing this function must return a plasmaParserDocument object
            theDoc = new Document(
                    location,     // url of the source document
                    mimeType,     // the documents mime type
                    "UTF-8",      // charset of the document text
                    null,         // language
                    keywords,
                    title,
                    author,
                    null,         // an array of section headlines
                    abstrct,      // an abstract
                    contents.getBytes("UTF-8"),     // the parsed document text
                    null,         // a map of extracted anchors
                    null);        // a treeset of image URLs
            return theDoc;
        } catch (final Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new ParserException
            final String errorMsg = "Unable to parse the vsd document '" + location + "':" + e.getMessage();
            this.theLogger.logSevere(errorMsg);
            throw new ParserException(errorMsg, location);
        } finally {
        	if (theDoc == null) {
                // if an unexpected error occures just log the error and raise a new ParserException
                final String errorMsg = "Unable to parse the vsd document '" + location + "': possibly out of memory";
                this.theLogger.logSevere(errorMsg);
                throw new ParserException(errorMsg, location);
        	}
        }
    }

    @Override
    public void reset() {
    // this code is executed if the parser class is returned into the parser pool
        super.reset();

    }
}