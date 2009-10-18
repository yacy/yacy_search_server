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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.kelondro.data.meta.DigestURI;

import pt.tumba.parser.swf.SWF2HTML;

public class swfParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("swf");
        SUPPORTED_MIME_TYPES.add("application/x-shockwave-flash");
        SUPPORTED_MIME_TYPES.add("application/x-shockwave-flash2-preview");
        SUPPORTED_MIME_TYPES.add("application/futuresplash");
        SUPPORTED_MIME_TYPES.add("image/vnd.rn-realflash");
    }

    public swfParser() {
        super("Adobe Flash Parser");
    }

    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    public Document parse(final DigestURI location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {

        try {
            final SWF2HTML swf2html = new SWF2HTML();
            String contents = "";
            try {
            	contents = swf2html.convertSWFToHTML(source);
            } catch (NegativeArraySizeException e) {
                // seen in log
                return null;
            } catch (IOException e) {
                // seems to happen quite often
                return null;
            } catch (Exception e) {
            	// we have seen a lot of OOM errors in the parser...
            	e.printStackTrace();
            	return null;
            }
            String url = null;
            String urlnr = null;
            final String linebreak = System.getProperty("line.separator");
            final String[] sections =  null;
            final String abstrct = null;
            //TreeSet images = null;
            final HashMap<DigestURI, String> anchors = new HashMap<DigestURI, String>();
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
                urlnr = (Integer.valueOf(++urls)).toString();
                anchors.put(new DigestURI(url, null), urlnr);
                contents = contents.substring(0,urlStart)+contents.substring(urlEnd);
            }

           // As the result of parsing this function must return a plasmaParserDocument object
            final Document theDoc = new Document(
                    location,     // url of the source document
                    mimeType,     // the documents mime type
                    "UTF-8",      // charset of the document text
                    null,
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
        } catch (final Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new ParserException
            final String errorMsg = "Unable to parse the swf document '" + location + "':" + e.getMessage();
            this.theLogger.logSevere(errorMsg);
            throw new ParserException(errorMsg, location);
        }
    }

    @Override
    public void reset() {
    // this code is executed if the parser class is returned into the parser pool
        super.reset();

    }
}