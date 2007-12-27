// sevenzipParser.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// 
// This file ist contributed by Franz Brausze
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
// 
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
// 
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma.parser.sevenzip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;

import SevenZip.IInStream;
import SevenZip.MyRandomAccessFile;
import SevenZip.Archive.SevenZip.Handler;

import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverCachedFileOutputStream;
import de.anomic.server.serverFileUtils;
import de.anomic.yacy.yacyURL;

public class sevenzipParser extends AbstractParser implements Parser {
    
    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>(); 
    static { 
        SUPPORTED_MIME_TYPES.put("application/x-7z-compressed", "7z"); 
    } 
    
    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] { "J7Zip-modified.jar" };
    
    public sevenzipParser() {
        super(LIBX_DEPENDENCIES);
        super.parserName = "7zip Archive Parser";
    }
    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset,
            IInStream source, long maxRamSize) throws ParserException, InterruptedException {
        plasmaParserDocument doc = new plasmaParserDocument(location, mimeType, charset);
        Handler archive;
        super.theLogger.logFine("opening 7zip archive...");
        try {
            archive = new Handler(source);
        } catch (IOException e) {
            throw new ParserException("error opening 7zip archive", location, e);
        }
        checkInterruption();
        SZParserExtractCallback aec = new SZParserExtractCallback(super.theLogger, archive,
                maxRamSize, doc, location.getFile());
        super.theLogger.logFine("processing archive contents...");
        try {
            archive.Extract(null, -1, 0, aec);
            return doc;   
        } catch (IOException e) {
            if (e.getCause() instanceof InterruptedException)
                throw (InterruptedException)e.getCause();
            if (e.getCause() instanceof ParserException)
                throw (ParserException)e.getCause();
            throw new ParserException(
                    "error processing 7zip archive at internal file: " + aec.getCurrentFilePath(),
                    location, e);
        } finally {
            try { archive.close(); } catch (IOException e) {  }
        }
    }
    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset,
            byte[] source) throws ParserException, InterruptedException {
        return parse(location, mimeType, charset, new ByteArrayIInStream(source), Parser.MAX_KEEP_IN_MEMORY_SIZE - source.length);
    }
    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset,
            File sourceFile) throws ParserException, InterruptedException {
        try {
            return parse(location, mimeType, charset, new MyRandomAccessFile(sourceFile, "r"), Parser.MAX_KEEP_IN_MEMORY_SIZE);
        } catch (IOException e) {
            throw new ParserException("error processing 7zip archive", location, e);
        }
    }
    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset,
            InputStream source) throws ParserException, InterruptedException {
        try {
            serverCachedFileOutputStream cfos = new serverCachedFileOutputStream(Parser.MAX_KEEP_IN_MEMORY_SIZE);
            serverFileUtils.copy(source, cfos);
            if (cfos.isFallback()) {
                return parse(location, mimeType, charset, cfos.getContentFile());
            } else {
                return parse(location, mimeType, charset, cfos.getContentBAOS());
            }
        } catch (IOException e) {
            throw new ParserException("error processing 7zip archive", location, e);
        }
    }
    
    public Hashtable getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
}
