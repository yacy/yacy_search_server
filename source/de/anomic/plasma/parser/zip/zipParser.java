//zipParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 16.05.2005
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

package de.anomic.plasma.parser.zip;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverByteBuffer;

public class zipParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { 
        SUPPORTED_MIME_TYPES.put("application/zip","zip");
        SUPPORTED_MIME_TYPES.put("application/x-zip","zip");
        SUPPORTED_MIME_TYPES.put("application/x-zip-compressed","zip");
        SUPPORTED_MIME_TYPES.put("application/java-archive","jar");
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {};    
    
    public zipParser() {        
        super(LIBX_DEPENDENCIES);
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, InputStream source) throws ParserException {
        
        try {           
            StringBuffer docKeywords = new StringBuffer();
            StringBuffer docShortTitle = new StringBuffer();  
            StringBuffer docLongTitle = new StringBuffer();   
            LinkedList docSections = new LinkedList();
            StringBuffer docAbstrct = new StringBuffer();
            serverByteBuffer docText = new serverByteBuffer();
            Map docAnchors = new HashMap();
            Map docImages = new HashMap(); 
            
            
            // creating a new parser class to parse the unzipped content
            plasmaParser theParser = new plasmaParser();            
            
            // looping through the contained files
            ZipEntry entry;
            ZipInputStream zippedContent = new ZipInputStream(source);                      
            while ((entry = zippedContent.getNextEntry()) !=null) {
                
                if (entry.isDirectory()) continue;
                
                // Get the entry name
                String entryName = entry.getName();                
                int idx = entryName.lastIndexOf(".");
                String entryExt = (idx > -1) ? entryName.substring(idx+1) : null;
                
                // trying to determine the mimeType per file extension   
                String entryMime = plasmaParser.getMimeTypeByFileExt(entryExt);
                
                // getting the entry content
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[(int) entry.getSize()];
                int bytesRead = zippedContent.read(buf);
                bos.write(buf);
                byte[] ut = bos.toByteArray();           
                
                // parsing the content
                plasmaParserDocument theDoc = theParser.parseSource(location,entryMime,ut);
                if (theDoc == null) continue;
                
                // merging all documents together
                if (docKeywords.length() > 0) docKeywords.append("\n");
                docKeywords.append(theDoc.getKeywords());
                
                if (docLongTitle.length() > 0) docLongTitle.append("\n");
                docLongTitle.append(theDoc.getMainLongTitle());
                
                if (docShortTitle.length() > 0) docShortTitle.append("\n");
                docShortTitle.append(theDoc.getMainShortTitle());                
                
                docSections.addAll(Arrays.asList(theDoc.getSectionTitles()));
                
                if (docAbstrct.length() > 0) docAbstrct.append("\n");
                docAbstrct.append(theDoc.getAbstract());                   

                if (docText.length() > 0) docText.append("\n");
                docText.append(theDoc.getText());                 
                
                docAnchors.putAll(theDoc.getAnchors());
                docImages.putAll(theDoc.getImages());
            }
            
            /* (URL location, String mimeType,
             String keywords, String shortTitle, String longTitle,
             String[] sections, String abstrct,
             byte[] text, Map anchors, Map images)
             */            
            return new plasmaParserDocument(
                    location,
                    mimeType,
                    docKeywords.toString(),
                    docShortTitle.toString(), 
                    docLongTitle.toString(),
                    (String[])docSections.toArray(new String[docSections.size()]),
                    docAbstrct.toString(),
                    docText.toByteArray(),
                    docAnchors,
                    docImages);
        } catch (Exception e) {            
            throw new ParserException("Unable to parse the zip content. " + e.getMessage());
        } catch (Error e) {
            throw new ParserException("Unable to parse the zip content. " + e.getMessage());
        }
    }
    
    public void reset() {
		// Nothing todo here at the moment
    	
    }
}
