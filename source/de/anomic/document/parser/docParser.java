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

package de.anomic.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import org.textmining.extraction.TextExtractor;
import org.textmining.extraction.word.WordTextExtractorFactory;

import de.anomic.document.AbstractParser;
import de.anomic.document.Idiom;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.yacy.yacyURL;

public class docParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final HashMap<String, String> SUPPORTED_MIME_TYPES = new HashMap<String, String>();
    static {
        String ext = "doc,docx";
        SUPPORTED_MIME_TYPES.put("application/msword",ext);
        SUPPORTED_MIME_TYPES.put("application/doc",ext);
        SUPPORTED_MIME_TYPES.put("appl/text",ext);
        SUPPORTED_MIME_TYPES.put("application/vnd.msword",ext);
        SUPPORTED_MIME_TYPES.put("application/vnd.ms-word",ext);
        SUPPORTED_MIME_TYPES.put("application/winword",ext);
        SUPPORTED_MIME_TYPES.put("application/word",ext);
        SUPPORTED_MIME_TYPES.put("application/x-msw6",ext);
        SUPPORTED_MIME_TYPES.put("application/x-msword",ext);
    }
    
	public docParser() {
		super("Word Document Parser");
	}

	public Document parse(final yacyURL location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
       final WordTextExtractorFactory extractorFactory = new WordTextExtractorFactory();
       TextExtractor extractor = null;
        try {
            extractor = extractorFactory.textExtractor(source);
        } catch (Exception e) {
            throw new ParserException("error in docParser, WordTextExtractorFactory: " + e.getMessage(), location);
        }
		String contents = null;
        try {
            contents = extractor.getText().trim();
        } catch (IOException e) {
            throw new ParserException("error in docParser, getText: " + e.getMessage(), location);
        }
	    String title = contents.replaceAll("\r"," ").replaceAll("\n"," ").replaceAll("\t"," ").trim();
	    if (title.length() > 80) title = title.substring(0, 80);
	    int l = title.length();
	    while (true) {
	        title = title.replaceAll("  ", " ");
	        if (title.length() == l) break;
	        l = title.length();
	    }
        Document theDoc;
        try {
            theDoc = new Document(
                      location,
                      mimeType,
                      "UTF-8",
                      null,
                      null,
                      title,
                      "", // TODO: AUTHOR
                      null,
                      null,
                      contents.getBytes("UTF-8"),
                      null,
                      null);
        } catch (UnsupportedEncodingException e) {
            throw new ParserException("error in docParser, getBytes: " + e.getMessage(), location);
        }
          
        return theDoc;
	}

	public HashMap<String, String> getSupportedMimeTypes() {
		return docParser.SUPPORTED_MIME_TYPES;
	}

    @Override
	public void reset() {
        // Nothing todo here at the moment
        super.reset();
	}

}
