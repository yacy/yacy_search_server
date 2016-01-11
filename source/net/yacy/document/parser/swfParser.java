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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
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
            final AnchorURL location,
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
                // read and check file signature (library expect stream positioned after signature)
                // magic bytes according to specification http://wwwimages.adobe.com/www.adobe.com/content/dam/Adobe/en/devnet/swf/pdf/swf-file-format-spec.pdf
                // 0x46, 0x57, 0x53 (“FWS”) signature indicates an uncompressed SWF file
                // 0x43, 0x57, 0x53 (“CWS”) indicates that the entire file after the first 8 bytes  was compressed by using the ZLIB
                // 0x5a, 0x57, 0x53 (“ZWS”) indicates that the entire file after the first 8 bytes was compressed by using the LZMA
                int magic = source.read();
                if (magic != 'F') // F=uncompressed, C= ZIP-compressed Z=LZMA-compressed
                    throw new Parser.Failure("compressed swf file not supported", location); // compressed not supported yet
                magic = source.read(); // always 'W'
                if (magic != 'W') throw new Parser.Failure("not a swf file (wrong file signature)", location);
                magic = source.read(); // always 'S'
                if (magic != 'S') throw new Parser.Failure("not a swf file (wrong file signature)", location);

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
                urlnr = Integer.toString(++urls);
                AnchorURL u = new AnchorURL(url);
                u.setNameProperty(urlnr);
                anchors.add(u);
                contents = contents.substring(0,urlStart)+contents.substring(urlEnd);
            }

           // As the result of parsing this function must return a plasmaParserDocument object
            return new Document[]{new Document(
                    location,     // url of the source document
                    mimeType,     // the documents mime type
                    StandardCharsets.UTF_8.name(),      // charset of the document text
                    this,
                    null,
                    null,          //keywords
                    singleList(((contents.length() > 80)? contents.substring(0, 80):contents.trim()).
                          replaceAll("\r\n"," ").
                          replaceAll("\n"," ").
                          replaceAll("\r"," ").
                          replaceAll("\t"," ")), // title
                    null, // TODO: AUTHOR
                    null,
                    null,        // an array of section headlines
                    null,        // an abstract
                    0.0d, 0.0d,
                    contents,     // the parsed document text
                    anchors,      // a map of extracted anchors
                    null,
                    null,
                    false,
                    new Date())};
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            // if an unexpected error occures just log the error and raise a new Parser.Failure
            final String errorMsg = "Unable to parse the swf document '" + location + "':" + e.getMessage();
            //AbstractParser.log.logSevere(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
    }

}