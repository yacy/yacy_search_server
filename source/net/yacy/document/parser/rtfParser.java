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

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;


public class rtfParser extends AbstractParser implements Parser {

    public rtfParser() {
        super("Rich Text Format Parser");
        SUPPORTED_EXTENSIONS.add("rtf");
        SUPPORTED_MIME_TYPES.add("text/rtf");
        SUPPORTED_MIME_TYPES.add("text/richtext");
        SUPPORTED_MIME_TYPES.add("application/rtf");
        SUPPORTED_MIME_TYPES.add("application/x-rtf");
        SUPPORTED_MIME_TYPES.add("application/x-soffice");
    }

    public Document[] parse(final MultiProtocolURI location, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {

        try {	
            final DefaultStyledDocument doc = new DefaultStyledDocument();
            
            final RTFEditorKit theRtfEditorKit = new RTFEditorKit();               
            theRtfEditorKit.read(source, doc, 0);            
            
            final String bodyText = doc.getText(0, doc.getLength());
            
            return new Document[]{new Document(
                    location,
                    mimeType,
                    "UTF-8",
                    this,
                    null,
                    null,
                    ((bodyText.length() > 80)? bodyText.substring(0, 80):bodyText.trim()).
                        replaceAll("\r\n"," ").
                        replaceAll("\n"," ").
                        replaceAll("\r"," ").
                        replaceAll("\t"," "),
                    "", // TODO: AUTHOR
                    "", // TODO: publisher
                    null,
                    null,
                    0.0f, 0.0f, 
                    UTF8.getBytes(bodyText),
                    null,
                    null,
                    null,
                    false)};        
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;
            
            throw new Parser.Failure("Unexpected error while parsing rtf resource." + e.getMessage(),location); 
        }
    }

}
