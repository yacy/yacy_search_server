//docParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 24.04.2005
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

import java.io.InputStream;
import java.util.Hashtable;

import org.textmining.extraction.TextExtractor;
import org.textmining.extraction.word.WordTextExtractorFactory;

import de.anomic.document.AbstractParser;
import de.anomic.document.Parser;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.yacy.yacyURL;

public class docParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();
    static { SUPPORTED_MIME_TYPES.put("application/msword","doc"); } 
    
    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "tm-extractors-1.0.jar"
    };    
    
	public docParser() {
		super(LIBX_DEPENDENCIES);
        this.parserName = "Word Document Parser";
	}

	public Document parse(final yacyURL location, final String mimeType, final String charset,
			final InputStream source) throws ParserException, InterruptedException {

        
		try {	
			  final WordTextExtractorFactory extractorFactory = new WordTextExtractorFactory();
			  final TextExtractor extractor = extractorFactory.textExtractor(source);
			  final String contents = extractor.getText().trim();
			  String title = contents.replaceAll("\r"," ").replaceAll("\n"," ").replaceAll("\t"," ").trim();
			  if (title.length() > 80) title = title.substring(0, 80);
			  int l = title.length();
			  while (true) {
			      title = title.replaceAll("  ", " ");
			      if (title.length() == l) break;
			      l = title.length();
			  }
              final Document theDoc = new Document(
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
              
              return theDoc;             
		} catch (final Exception e) {
		    e.printStackTrace();
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing doc file. " + e.getMessage(),location);            
		}        
	}

	public java.util.Hashtable<String, String> getSupportedMimeTypes() {
		return docParser.SUPPORTED_MIME_TYPES;
	}

	public void reset() {
        // Nothing todo here at the moment
        super.reset();
	}

}
