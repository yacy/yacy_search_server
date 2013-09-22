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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
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
    public Document[] parse(final AnchorURL location, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException
    {

        try {
            final SWF2HTML swf2html = new SWF2HTML();
            String contents = "";
            try {
            	contents = swf2html.convertSWFToHTML(source);
            } catch (final NegativeArraySizeException e) {
                throw new Parser.Failure(e.getMessage(), location);
            } catch (final IOException e) {
                throw new Parser.Failure(e.getMessage(), location);
            } catch (final Exception e) {
                throw new Parser.Failure(e.getMessage(), location);
            }
            String url = null;
            String urlnr = null;
            final String linebreak = System.getProperty("line.separator");
            final String[] sections =  null;
            final List<String> abstrct = new ArrayList<String>();
            //TreeSet images = null;
            final List<AnchorURL> anchors = new ArrayList<AnchorURL>();
            int urls = 0;
            int urlStart = -1;
            int urlEnd = 0;
            int p0 = 0;

            //getting rid of HTML-Tags
            p0 = contents.indexOf("<html><body>",0);
            contents = contents.substring(p0+12);
            p0 = contents.indexOf("</body></html>",0);
            contents = contents.substring(0,p0);

            //extracting urls
            while ((urlStart = contents.indexOf("http://",urlEnd)) >= 0){
                urlEnd = contents.indexOf(linebreak,urlStart);
                url = contents.substring(urlStart,urlEnd);
                urlnr = Integer.toString(++urls).toString();
                AnchorURL u = new AnchorURL(url);
                u.setNameProperty(urlnr);
                anchors.add(u);
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
                    singleList(((contents.length() > 80)? contents.substring(0, 80):contents.trim()).
                          replaceAll("\r\n"," ").
                          replaceAll("\n"," ").
                          replaceAll("\r"," ").
                          replaceAll("\t"," ")), // title
                    "", // TODO: AUTHOR
                    "",
                    sections,     // an array of section headlines
                    abstrct,     // an abstract
                    0.0f, 0.0f,
                    contents,     // the parsed document text
                    anchors,      // a map of extracted anchors
                    null,
                    null,
                    false,
                    new Date())};      // a treeset of image URLs
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new Parser.Failure
            final String errorMsg = "Unable to parse the swf document '" + location + "':" + e.getMessage();
            //AbstractParser.log.logSevere(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
    }

}