//gzipParser.java 
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;


public class gzipParser extends AbstractParser implements Parser {

    public gzipParser() {        
        super("GNU Zip Compressed Archive Parser");
        SUPPORTED_EXTENSIONS.add("gz");
        SUPPORTED_EXTENSIONS.add("tgz");
        SUPPORTED_MIME_TYPES.add("application/x-gzip");
        SUPPORTED_MIME_TYPES.add("application/gzip");
        SUPPORTED_MIME_TYPES.add("application/x-gunzip");
        SUPPORTED_MIME_TYPES.add("application/gzipped");
        SUPPORTED_MIME_TYPES.add("application/gzip-compressed");
        SUPPORTED_MIME_TYPES.add("gzip/document");
    }
    
    public Document[] parse(final MultiProtocolURI location, final String mimeType, final String charset, final InputStream source) throws Parser.Failure, InterruptedException {
        
        File tempFile = null;
        Document[] docs = null;
        try {           
            int read = 0;
            final byte[] data = new byte[1024];
            
            final GZIPInputStream zippedContent = new GZIPInputStream(source);
            
            tempFile = File.createTempFile("gunzip","tmp");
            tempFile.deleteOnExit();
            
            // creating a temp file to store the uncompressed data
            final FileOutputStream out = new FileOutputStream(tempFile);
            
            // reading gzip file and store it uncompressed
            while ((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
            zippedContent.close();
            out.close();
            
            // creating a new parser class to parse the unzipped content
            docs = TextParser.parseSource(location,null,null,tempFile);
        } catch (final Exception e) {    
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;
            
            throw new Parser.Failure("Unexpected error while parsing gzip file. " + e.getMessage(),location); 
        } finally {
            if (tempFile != null) FileUtils.deletedelete(tempFile);
        }
        return docs;
    }
 
}
