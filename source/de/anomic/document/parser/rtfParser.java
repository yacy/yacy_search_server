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

package de.anomic.document.parser;

import java.io.InputStream;
import java.util.HashMap;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import de.anomic.document.AbstractParser;
import de.anomic.document.Idiom;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.yacy.yacyURL;

public class rtfParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final HashMap<String, String> SUPPORTED_MIME_TYPES = new HashMap<String, String>();
    static { 
        SUPPORTED_MIME_TYPES.put("application/rtf","rtf"); 
        SUPPORTED_MIME_TYPES.put("text/rtf","rtf");
        SUPPORTED_MIME_TYPES.put("application/x-rtf","rtf");
        SUPPORTED_MIME_TYPES.put("text/richtext","rtf");
        SUPPORTED_MIME_TYPES.put("application/x-soffice","rtf");
    } 

	public rtfParser() {
		super("Rich Text Format Parser");  
	}

	public Document parse(final yacyURL location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {

        
		try {	
            final DefaultStyledDocument doc = new DefaultStyledDocument();
            
            final RTFEditorKit theRtfEditorKit = new RTFEditorKit();               
            theRtfEditorKit.read(source, doc, 0);            
            
            final String bodyText = doc.getText(0, doc.getLength());
            
            final Document theDoc = new Document(
                    location,
                    mimeType,
                    "UTF-8",
                    null,
                    null,
                    ((bodyText.length() > 80)? bodyText.substring(0, 80):bodyText.trim()).
                        replaceAll("\r\n"," ").
                        replaceAll("\n"," ").
                        replaceAll("\r"," ").
                        replaceAll("\t"," "),
                    "", // TODO: AUTHOR
                    null,
                    null,
                    bodyText.getBytes("UTF-8"),
                    null,
                    null);
            
            return theDoc;             
		}
		catch (final Exception e) {			
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing rtf resource." + e.getMessage(),location); 
		}        
	}

	public HashMap<String, String> getSupportedMimeTypes() {
		return rtfParser.SUPPORTED_MIME_TYPES;
	}

	public void reset() {
        // Nothing todo here at the moment
        super.reset();
	}

}
