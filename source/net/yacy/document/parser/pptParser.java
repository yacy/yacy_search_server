//pptParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Tim Riemann
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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Date;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;

import org.apache.poi.hslf.extractor.PowerPointExtractor;

public class pptParser extends AbstractParser implements Parser {

    public pptParser(){
        super("Microsoft Powerpoint Parser");
        this.SUPPORTED_EXTENSIONS.add("ppt");
        this.SUPPORTED_EXTENSIONS.add("pps");
        this.SUPPORTED_MIME_TYPES.add("application/mspowerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.ms-powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/ms-powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/mspowerpnt");
        this.SUPPORTED_MIME_TYPES.add("application/vnd-mspowerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/x-powerpoint");
        this.SUPPORTED_MIME_TYPES.add("application/x-m");
    }

    /*
     * parses the source documents and returns a plasmaParserDocument containing
     * all extracted information about the parsed document
     */
    @Override
    public Document[] parse(final AnchorURL location, final String mimeType,
            final String charset, final InputStream source) throws Parser.Failure,
            InterruptedException {
        try {
            /*
             * create new PowerPointExtractor and extract text and notes
             * of the document
             */
            final PowerPointExtractor pptExtractor = new PowerPointExtractor(new BufferedInputStream(source));
            final String contents = pptExtractor.getText(true, true).trim();
            String title = contents.replaceAll("\r"," ").replaceAll("\n"," ").replaceAll("\t"," ").trim();
            if (title.length() > 80) title = title.substring(0, 80);
            int l = title.length();
            while (true) {
                title = title.replaceAll("  ", " ");
                if (title.length() == l) break;
                l = title.length();
            }

            /*
             * create the plasmaParserDocument for the database
             * and set shortText and bodyText properly
             */
            final Document[] docs = new Document[]{new Document(
                    location,
                    mimeType,
                    "UTF-8",
                    this,
                    null,
                    null,
                    singleList(title),
                    "", // TODO: AUTHOR
                    pptExtractor.getDocSummaryInformation().getCompany(),
                    null,
                    null,
                    0.0f, 0.0f,
                    contents,
                    null,
                    null,
                    null,
                    false,
                    new Date())};
            return docs;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;

            /*
             * an unexpected error occurred, log it and throw a Parser.Failure
             */
            ConcurrentLog.logException(e);
            final String errorMsg = "Unable to parse the ppt document '" + location + "':" + e.getMessage();
            AbstractParser.log.severe(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
    }

}
