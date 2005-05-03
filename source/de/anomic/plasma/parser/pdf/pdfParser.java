//pdfParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
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
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma.parser.pdf;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;


import org.pdfbox.pdfparser.PDFParser;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.pdmodel.PDDocumentInformation;
import org.pdfbox.util.PDFTextStripper;

import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;

public class pdfParser extends AbstractParser implements Parser
{

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { SUPPORTED_MIME_TYPES.put("application/pdf","pdf"); }     
    
    /**
     * a list of file extensions that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final HashSet SUPPORTED_FILE_EXT = new HashSet(Arrays.asList(new String[] {
        new String("pdf")
    }));       
    
    public pdfParser() {
        super();
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, InputStream source) throws ParserException {
        
        try {       
            String docTitle = null, docSubject = null, docAuthor = null, docKeyWords = null;
            
            PDFParser parser = new PDFParser(source);
            parser.parse();
            
            PDFTextStripper stripper = new PDFTextStripper();
            PDDocument theDocument = parser.getPDDocument();
                              
            PDDocumentInformation theDocInfo = theDocument.getDocumentInformation();
            
            if (theDocInfo != null)
            {
                docTitle = theDocInfo.getTitle();
                docSubject = theDocInfo.getSubject();
                docAuthor = theDocInfo.getAuthor();
                docKeyWords = theDocInfo.getKeywords();
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter( out );            
            stripper.writeText(theDocument, writer );
            
            writer.close();
            theDocument.close();
            
            byte[] contents = out.toByteArray();
			
            /*
             *         public document(URL location, String mimeType,
                            String keywords, String shortTitle, String longTitle,
                            String[] sections, String abstrct,
                            byte[] text, Map anchors, Map images) {
             * 
             */            
            plasmaParserDocument theDoc = new plasmaParserDocument(
                    location,
                    mimeType,
                    docKeyWords,
                    docSubject,
                    docTitle,
                    null,
                    null,
                    contents,
                    null,
                    null);
            
            return theDoc;
        }
        catch (Exception e) {            
            throw new ParserException("Unable to parse the pdf content. " + e.getMessage());
        }        
    }
    
    public void reset() {
		// Nothing todo here at the moment
    	
    }

	public HashSet getSupportedFileExtensions() {
		return SUPPORTED_FILE_EXT;
	}

}
