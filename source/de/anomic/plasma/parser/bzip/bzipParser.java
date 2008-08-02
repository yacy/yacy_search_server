//bzipParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
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
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        
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
            final byte[] data = new byte[1024];
            final CBZip2InputStream zippedContent = new CBZip2InputStream(source);        
            
            tempFile = File.createTempFile("bunzip","tmp");
            tempFile.deleteOnExit();
            
            // creating a temp file to store the uncompressed data
            final FileOutputStream out = new FileOutputStream(tempFile);
            
            // reading gzip file and store it uncompressed
            while((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
            zippedContent.close();
            out.close();
             
            // check for interruption
            checkInterruption();
            
            // creating a new parser class to parse the unzipped content
            final plasmaParser theParser = new plasmaParser();
            return theParser.parseSource(location,null,null,tempFile);
        } catch (final Exception e) {  
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
