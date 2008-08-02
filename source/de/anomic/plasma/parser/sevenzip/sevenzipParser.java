// sevenzipParser.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
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
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset,
            final IInStream source, final long maxRamSize) throws ParserException, InterruptedException {
        final plasmaParserDocument doc = new plasmaParserDocument(location, mimeType, charset);
        Handler archive;
        super.theLogger.logFine("opening 7zip archive...");
        try {
            archive = new Handler(source);
        } catch (final IOException e) {
            throw new ParserException("error opening 7zip archive", location, e);
        }
        checkInterruption();
        final SZParserExtractCallback aec = new SZParserExtractCallback(super.theLogger, archive,
                maxRamSize, doc, location.getFile());
        super.theLogger.logFine("processing archive contents...");
        try {
            archive.Extract(null, -1, 0, aec);
            return doc;   
        } catch (final IOException e) {
            if (e.getCause() instanceof InterruptedException)
                throw (InterruptedException)e.getCause();
            if (e.getCause() instanceof ParserException)
                throw (ParserException)e.getCause();
            throw new ParserException(
                    "error processing 7zip archive at internal file: " + aec.getCurrentFilePath(),
                    location, e);
        } finally {
            try { archive.close(); } catch (final IOException e) {  }
        }
    }
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset,
            final byte[] source) throws ParserException, InterruptedException {
        return parse(location, mimeType, charset, new ByteArrayIInStream(source), Parser.MAX_KEEP_IN_MEMORY_SIZE - source.length);
    }
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset,
            final File sourceFile) throws ParserException, InterruptedException {
        try {
            return parse(location, mimeType, charset, new MyRandomAccessFile(sourceFile, "r"), Parser.MAX_KEEP_IN_MEMORY_SIZE);
        } catch (final IOException e) {
            throw new ParserException("error processing 7zip archive", location, e);
        }
    }
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType, final String charset,
            final InputStream source) throws ParserException, InterruptedException {
        try {
            final serverCachedFileOutputStream cfos = new serverCachedFileOutputStream(Parser.MAX_KEEP_IN_MEMORY_SIZE);
            serverFileUtils.copy(source, cfos);
            if (cfos.isFallback()) {
                return parse(location, mimeType, charset, cfos.getContentFile());
            } else {
                return parse(location, mimeType, charset, cfos.getContentBAOS());
            }
        } catch (final IOException e) {
            throw new ParserException("error processing 7zip archive", location, e);
        }
    }
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
}
