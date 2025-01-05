//rtfParser.java
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
import java.nio.charset.StandardCharsets;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;


public class rtfParser extends AbstractParser implements Parser {

    public rtfParser() {
        super("Rich Text Format Parser");
        this.SUPPORTED_EXTENSIONS.add("rtf");
        this.SUPPORTED_MIME_TYPES.add("text/rtf");
        this.SUPPORTED_MIME_TYPES.add("text/richtext");
        this.SUPPORTED_MIME_TYPES.add("application/rtf");
        this.SUPPORTED_MIME_TYPES.add("application/x-rtf");
        this.SUPPORTED_MIME_TYPES.add("application/x-soffice");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Parser.Failure, InterruptedException {

        try {
            final DefaultStyledDocument doc = new DefaultStyledDocument();

            final RTFEditorKit theRtfEditorKit = new RTFEditorKit();
            theRtfEditorKit.read(source, doc, 0);

            final String bodyText = doc.getText(0, doc.getLength());

            return new Document[]{new Document(
                    location,
                    mimeType,
                    StandardCharsets.UTF_8.name(),
                    this,
                    null,
                    null,
                    singleList(((bodyText.length() > 80)? bodyText.substring(0, 80):bodyText.trim()).
                        replaceAll("\r\n"," ").
                        replaceAll("\n"," ").
                        replaceAll("\r"," ").
                        replaceAll("\t"," ")),
                    null, // TODO: AUTHOR
                    "", // TODO: publisher
                    null,
                    null,
                    0.0d, 0.0d,
                    bodyText,
                    null,
                    null,
                    null,
                    false,
                    null)};
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unexpected error while parsing rtf resource." + e.getMessage(),location);
        }
    }

}
