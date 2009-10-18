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
import java.util.HashSet;
import java.util.Set;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.kelondro.data.meta.DigestURI;


public class rtfParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("rtf");
        SUPPORTED_MIME_TYPES.add("text/rtf");
        SUPPORTED_MIME_TYPES.add("text/richtext");
        SUPPORTED_MIME_TYPES.add("application/rtf");
        SUPPORTED_MIME_TYPES.add("application/x-rtf");
        SUPPORTED_MIME_TYPES.add("application/x-soffice");
    } 

	public rtfParser() {
		super("Rich Text Format Parser");  
	}

	public Document parse(final DigestURI location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {

        
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

	public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

	public void reset() {
        // Nothing todo here at the moment
        super.reset();
	}

}
