//bzipParser.java 
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import org.apache.tools.bzip2.CBZip2InputStream;

import de.anomic.document.AbstractParser;
import de.anomic.document.Idiom;
import de.anomic.document.Parser;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.yacy.yacyURL;

public class bzipParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final HashMap<String, String> SUPPORTED_MIME_TYPES = new HashMap<String, String>();    
    static final String fileExtensions = "bz2,tbz,tbz2";
    static { 
        SUPPORTED_MIME_TYPES.put("application/x-bzip2",fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/bzip2", fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/x-bz2", fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/octet-stream",fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/x-bzip",fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/x-compressed",fileExtensions);
        SUPPORTED_MIME_TYPES.put("application/x-stuffit",fileExtensions);
    }
    
    public bzipParser() {        
        super("Bzip 2 UNIX Compressed File Parser");
    }
    
    public HashMap<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Document parse(final yacyURL location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        
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
            return Parser.parseSource(location,null,null,tempFile);
        } catch (final Exception e) {  
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing bzip file. " + e.getMessage(),location);
        } finally {
            if (tempFile != null) FileUtils.deletedelete(tempFile);
        }
    }
    
    @Override
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
}
