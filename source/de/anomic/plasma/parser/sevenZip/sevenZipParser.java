//gzipParser.java 
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

package de.anomic.plasma.parser.sevenZip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Hashtable;

import LZMA.LzmaInputStream;

import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;

public class sevenZipParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { 
        SUPPORTED_MIME_TYPES.put("application/x-7z-compressed","7z,t7z");
    }     

    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {"JLzma.jar"};    
    
    public sevenZipParser() {        
        super(LIBX_DEPENDENCIES);
        parserName = "7-Zip Compressed Archive Parser";
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, InputStream source) throws ParserException {
        
        File tempFile = null;
        try {           
            int read = 0;
            byte[] data = new byte[1024];
                       
            
            LzmaInputStream zippedContent = new LzmaInputStream(source);
            
            tempFile = File.createTempFile("sevenzip","tmp");
            tempFile.deleteOnExit();
            
            // creating a temp file to store the uncompressed data
            FileOutputStream out = new FileOutputStream(tempFile);
            
            // reading gzip file and store it uncompressed
            while((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
            zippedContent.close();
            out.close();
             
            // creating a new parser class to parse the unzipped content
            plasmaParser theParser = new plasmaParser();
            return theParser.parseSource(location,null,tempFile);
        } catch (Exception e) {            
            throw new ParserException("Unable to parse the 7Zip content. " + e.getMessage());
        } catch (Error e) {
            e.printStackTrace();
            throw new ParserException("Unable to parse the 7Zip content. " + e.getMessage());
        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }
    
    public void reset() {
		// Nothing todo here at the moment
    	
    }
}
