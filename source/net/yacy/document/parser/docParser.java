//docParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
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
import org.apache.poi.hwpf.extractor.WordExtractor;

public class docParser extends AbstractParser implements Parser {

    public docParser() {
        super("Word Document Parser");
        SUPPORTED_EXTENSIONS.add("doc");
        SUPPORTED_MIME_TYPES.add("application/msword");
        SUPPORTED_MIME_TYPES.add("application/doc");
        SUPPORTED_MIME_TYPES.add("appl/text");
        SUPPORTED_MIME_TYPES.add("application/vnd.msword");
        SUPPORTED_MIME_TYPES.add("application/vnd.ms-word");
        SUPPORTED_MIME_TYPES.add("application/winword");
        SUPPORTED_MIME_TYPES.add("application/word");
        SUPPORTED_MIME_TYPES.add("application/x-msw6");
        SUPPORTED_MIME_TYPES.add("application/x-msword");
    }

    public Document[] parse(final MultiProtocolURI location, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {

        final WordExtractor extractor;

        try {
            extractor = new WordExtractor(source);
        } catch (Exception e) {
            throw new Parser.Failure("error in docParser, WordTextExtractorFactory: " + e.getMessage(), location);
        }

        final StringBuilder contents = new StringBuilder(80);
        try {
            contents.append(extractor.getText().trim());
            contents.append(' ');
            contents.append(extractor.getHeaderText());
            contents.append(' ');
            contents.append(extractor.getFooterText());
        } catch (Exception e) {
            throw new Parser.Failure("error in docParser, getText: " + e.getMessage(), location);
        }
        String title = (contents.length() > 240) ? contents.substring(0,240) : contents.toString().trim();
        title.replaceAll("\r"," ").replaceAll("\n"," ").replaceAll("\t"," ").trim();
        if (title.length() > 80) title = title.substring(0, 80);
        int l = title.length();
        while (true) {
            title = title.replaceAll("  ", " ");
            if (title.length() == l) break;
            l = title.length();
        }

        Document[] docs;
        docs = new Document[]{new Document(
                  location,
                  mimeType,
                  "UTF-8",
                  this,
                  null,
                  null,
                  title,
                  "", // TODO: AUTHOR
                  extractor.getDocSummaryInformation().getCompany(), // publisher
                  null,
                  null,
                  0.0f, 0.0f, 
                  UTF8.getBytes(contents.toString()),
                  null,
                  null,
                  null,
                  false)};
          
        return docs;
    }

}
