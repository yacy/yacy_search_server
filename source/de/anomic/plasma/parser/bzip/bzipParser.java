//bzipParser.java 
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

package de.anomic.plasma.parser.bzip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Hashtable;

import org.apache.tools.bzip2.CBZip2InputStream;

import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.yacy.yacyURL;

public class bzipParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();    
    static String fileExtensions = "bz2,tbz,tbz2";
    static { 
        SUPPORTED_MIME_TYPES.put("application/x-bzip2",fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/bzip2", fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/x-bz2", fileExtensions);
    }     
    
    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "bzip2.jar"
    };
    
    public bzipParser() {        
        super(LIBX_DEPENDENCIES);
        this.parserName = "Bzip 2 UNIX Compressed File Parser";
    }
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {
        
        File tempFile = null;
        try {           
            /*
             * First we have to consume the first two char from the stream. Otherwise
             * the bzip decompression will fail with a nullpointerException!
             */
            int b = source.read();
            if (b != 'B') {
                throw new Exception("Invalid bz2 content.");
            }
            b = source.read();
            if (b != 'Z') {
                throw new Exception("Invalid bz2 content.");
            }           
            
            int read = 0;
            byte[] data = new byte[1024];
            CBZip2InputStream zippedContent = new CBZip2InputStream(source);        
            
            tempFile = File.createTempFile("bunzip","tmp");
            tempFile.deleteOnExit();
            
            // creating a temp file to store the uncompressed data
            FileOutputStream out = new FileOutputStream(tempFile);
            
            // reading gzip file and store it uncompressed
            while((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
            zippedContent.close();
            out.close();
             
            // check for interruption
            checkInterruption();
            
            // creating a new parser class to parse the unzipped content
            plasmaParser theParser = new plasmaParser();
            return theParser.parseSource(location,null,null,tempFile);
        } catch (Exception e) {  
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing bzip file. " + e.getMessage(),location);
        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }
    
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
}
