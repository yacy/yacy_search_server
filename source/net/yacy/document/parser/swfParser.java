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
import java.util.Map;
import java.util.Properties;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.kelondro.logging.Log;

import pt.tumba.parser.swf.SWF2HTML;

public class swfParser extends AbstractParser implements Parser {

    public swfParser() {
        super("Adobe Flash Parser");
        SUPPORTED_EXTENSIONS.add("swf");
        SUPPORTED_MIME_TYPES.add("application/x-shockwave-flash");
        SUPPORTED_MIME_TYPES.add("application/x-shockwave-flash2-preview");
        SUPPORTED_MIME_TYPES.add("application/futuresplash");
        SUPPORTED_MIME_TYPES.add("image/vnd.rn-realflash");
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    public Document[] parse(final MultiProtocolURI location, final String mimeType, 
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException
    {

        try {
            final SWF2HTML swf2html = new SWF2HTML();
            String contents = "";
            try {
            	contents = swf2html.convertSWFToHTML(source);
            } catch (NegativeArraySizeException e) {
                throw new Parser.Failure(e.getMessage(), location);
            } catch (IOException e) {
                throw new Parser.Failure(e.getMessage(), location);
            } catch (Exception e) {
                Log.logException(e);
                throw new Parser.Failure(e.getMessage(), location);
            }
            String url = null;
            String urlnr = null;
            final String linebreak = System.getProperty("line.separator");
            final String[] sections =  null;
            final String abstrct = null;
            //TreeSet images = null;
            final Map<MultiProtocolURI, Properties> anchors = new HashMap<MultiProtocolURI, Properties>();
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
                urlnr = Integer.toString(++urls).toString();
                Properties p = new Properties();
                p.put("name", urlnr);
                anchors.put(new MultiProtocolURI(url), p);
                contents = contents.substring(0,urlStart)+contents.substring(urlEnd);
            }

           // As the result of parsing this function must return a plasmaParserDocument object
            return new Document[]{new Document(
                    location,     // url of the source document
                    mimeType,     // the documents mime type
                    "UTF-8",      // charset of the document text
                    this,
                    null,
                    null,          //keywords
                      ((contents.length() > 80)? contents.substring(0, 80):contents.trim()).
                          replaceAll("\r\n"," ").
                          replaceAll("\n"," ").
                          replaceAll("\r"," ").
                          replaceAll("\t"," "), // title
                    "", // TODO: AUTHOR
                    "",
                    sections,     // an array of section headlines
                    abstrct,     // an abstract
                    0.0f, 0.0f, 
                    UTF8.getBytes(contents),     // the parsed document text
                    anchors,      // a map of extracted anchors
                    null,
                    null,
                    false)};      // a treeset of image URLs
        } catch (final Exception e) { 
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new Parser.Failure
            final String errorMsg = "Unable to parse the swf document '" + location + "':" + e.getMessage();
            this.log.logSevere(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
    }

}