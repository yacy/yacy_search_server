//pptParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005

//this file is contributed by Tim Riemann
//last major change: 10.09.2006

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.plasma.parser.ppt;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Hashtable;

import org.apache.poi.hslf.extractor.PowerPointExtractor;

import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.yacy.yacyURL;

public class pptParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();  
    static { 
        SUPPORTED_MIME_TYPES.put("application/mspowerpoint","ppt,pps");
        SUPPORTED_MIME_TYPES.put("application/powerpoint","ppt,pps");
        SUPPORTED_MIME_TYPES.put("application/vnd.ms-powerpoint","ppt,pps");
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "poi-3.0-alpha2-20060616.jar",
        "poi-scratchpad-3.0-alpha2-20060616.jar"
    }; 

    public pptParser(){
        super(LIBX_DEPENDENCIES);
        this.parserName = "Microsoft Powerpoint Parser";
        this.parserVersionNr = "0.1"; 
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */ 
    public plasmaParserDocument parse(yacyURL location, String mimeType,
            String charset, InputStream source) throws ParserException,
            InterruptedException {
        try {
            /*
             * create new PowerPointExtractor and extract text and notes
             * of the document
             */
            PowerPointExtractor pptExtractor = new PowerPointExtractor(new BufferedInputStream(source));
            String contents = pptExtractor.getText(true, true);
            
            /*
             * create the plasmaParserDocument for the database
             * and set shortText and bodyText properly
             */
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
                    ((contents.length() > 80) ? contents.substring(0, 80) : contents.trim()).
                    replaceAll("\r\n"," ").
                    replaceAll("\n"," ").
                    replaceAll("\r"," ").
                    replaceAll("\t"," "),
                    "", // TODO: AUTHOR
                    null,
                    null,
                    contents.getBytes("UTF-8"),
                    null,
                    null);
            return theDoc;
        } catch (Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            /*
             * an unexpected error occurred, log it and throw a ParserException
             */            
            String errorMsg = "Unable to parse the ppt document '" + location + "':" + e.getMessage();
            this.theLogger.logSevere(errorMsg);            
            throw new ParserException(errorMsg, location);
        }
    }

    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    public void reset(){
        //nothing to do
        super.reset();
    }
}
