//docParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Marc Nause
//last major change: 01.11.2006
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma.parser.swf;

import java.io.InputStream;
import de.anomic.net.URL;
import java.util.Hashtable;
import java.util.HashMap;

import pt.tumba.parser.swf.*;

import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;

public class swfParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();
    static {
        SUPPORTED_MIME_TYPES.put("application/x-shockwave-flash","swf");
        SUPPORTED_MIME_TYPES.put("application/x-shockwave-flash2-preview","swf");
    }

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {"webcat-0.1-swf.jar"};

    public swfParser() {
        super(LIBX_DEPENDENCIES);
        this.parserName = "Adobe Flash Parser";
        this.parserVersionNr = "0.1";
    }

    /**
     * returns a hashtable containing the mimetypes that are supported by this class
     */
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    public plasmaParserDocument parse(URL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {

        try {
            SWF2HTML swf2html = new SWF2HTML();
            String contents = swf2html.convertSWFToHTML(source);
            String url = null;
            String urlnr = null;
            String linebreak = System.getProperty("line.separator");
            String[] sections =  null;
            String abstrct = null;
            //TreeSet images = null;
            HashMap anchors = new HashMap();
            int urls = 0;
            int urlStart = -1;
            int urlEnd = 0;
            int p0 = 0;

            //getting rid of HTML-Tags
            p0 = contents.indexOf("<html><body>");
            contents = contents.substring(p0+12);
            p0 = contents.indexOf("</body></html>");
            contents = contents.substring(0,p0);

            //extracting urls
            while ((urlStart = contents.indexOf("http://",urlEnd)) >= 0){
                urlEnd = contents.indexOf(linebreak,urlStart);
                url = contents.substring(urlStart,urlEnd);
                urlnr = (new Integer(++urls)).toString();
                anchors.put(url,urlnr);
                contents = contents.substring(0,urlStart)+contents.substring(urlEnd);
            }

           // As the result of parsing this function must return a plasmaParserDocument object
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,     // url of the source document
                    mimeType,     // the documents mime type
                    "UTF-8",      // charset of the document text
                    null,          //keywords
                      ((contents.length() > 80)? contents.substring(0, 80):contents.trim()).
                          replaceAll("\r\n"," ").
                          replaceAll("\n"," ").
                          replaceAll("\r"," ").
                          replaceAll("\t"," "), // title
                    "", // TODO: AUTHOR
                    sections,     // an array of section headlines
                    abstrct,     // an abstract
                    contents.getBytes("UTF-8"),     // the parsed document text
                    anchors,      // a map of extracted anchors
                    null);      // a treeset of image URLs
            return theDoc;
        } catch (Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new ParserException
            String errorMsg = "Unable to parse the swf document '" + location + "':" + e.getMessage();
            this.theLogger.logSevere(errorMsg);
            throw new ParserException(errorMsg, location);
        }
    }

    public void reset() {
    // this code is executed if the parser class is returned into the parser pool
        super.reset();

    }
}