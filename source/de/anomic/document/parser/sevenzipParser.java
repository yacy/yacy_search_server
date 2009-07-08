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

package de.anomic.document.parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;

import SevenZip.ArchiveExtractCallback;
import SevenZip.IInStream;
import SevenZip.MyRandomAccessFile;
import SevenZip.Archive.IInArchive;
import SevenZip.Archive.SevenZipEntry;
import SevenZip.Archive.SevenZip.Handler;
import de.anomic.document.AbstractParser;
import de.anomic.document.Parser;
import de.anomic.document.ParserDispatcher;
import de.anomic.document.ParserException;
import de.anomic.document.Document;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.server.serverCachedFileOutputStream;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

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
    
    public Document parse(final yacyURL location, final String mimeType, final String charset,
            final IInStream source, final long maxRamSize) throws ParserException, InterruptedException {
        final Document doc = new Document(location, mimeType, charset, null);
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
    
    public Document parse(final yacyURL location, final String mimeType, final String charset,
            final byte[] source) throws ParserException, InterruptedException {
        return parse(location, mimeType, charset, new ByteArrayIInStream(source), Parser.MAX_KEEP_IN_MEMORY_SIZE - source.length);
    }
    
    public Document parse(final yacyURL location, final String mimeType, final String charset,
            final File sourceFile) throws ParserException, InterruptedException {
        try {
            return parse(location, mimeType, charset, new MyRandomAccessFile(sourceFile, "r"), Parser.MAX_KEEP_IN_MEMORY_SIZE);
        } catch (final IOException e) {
            throw new ParserException("error processing 7zip archive", location, e);
        }
    }
    
    public Document parse(final yacyURL location, final String mimeType, final String charset,
            final InputStream source) throws ParserException, InterruptedException {
        try {
            final serverCachedFileOutputStream cfos = new serverCachedFileOutputStream(Parser.MAX_KEEP_IN_MEMORY_SIZE);
            FileUtils.copy(source, cfos);
            if (cfos.isFallback()) {
                return parse(location, mimeType, charset, cfos.getContentFile());
            }
            return parse(location, mimeType, charset, cfos.getContentBAOS());
        } catch (final IOException e) {
            throw new ParserException("error processing 7zip archive", location, e);
        }
    }
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    

     // wrapper class to redirect output of standard ArchiveExtractCallback to serverLog
     // and parse the extracted content
     public static class SZParserExtractCallback extends ArchiveExtractCallback {
         
         private final Log log;
         private final long maxRamSize;
         private serverCachedFileOutputStream cfos = null;
         private final Document doc;
         private final String prefix;
         
         public SZParserExtractCallback(final Log logger, final IInArchive handler,
                 final long maxRamSize, final Document doc, final String prefix) {
             super.Init(handler);
             this.log = logger;
             this.maxRamSize = maxRamSize;
             this.doc = doc;
             this.prefix = prefix;
         }
         
         public void PrepareOperation(final int arg0) {
             this.extractMode = (arg0 == IInArchive.NExtract_NAskMode_kExtract);
             switch (arg0) {
                 case IInArchive.NExtract_NAskMode_kExtract:
                     this.log.logFine("Extracting " + this.filePath);
                     break;
                 case IInArchive.NExtract_NAskMode_kTest:
                     this.log.logFine("Testing " + this.filePath);
                     break;
                 case IInArchive.NExtract_NAskMode_kSkip:
                     this.log.logFine("Skipping " + this.filePath);
                     break;
             }
         }
    
         public void SetOperationResult(final int arg0) throws IOException {
             if (arg0 != IInArchive.NExtract_NOperationResult_kOK) {
                 this.NumErrors++;
                 switch(arg0) {
                     case IInArchive.NExtract_NOperationResult_kUnSupportedMethod:
                         throw new IOException("Unsupported Method");
                     case IInArchive.NExtract_NOperationResult_kCRCError:
                         throw new IOException("CRC Failed");
                     case IInArchive.NExtract_NOperationResult_kDataError:
                         throw new IOException("Data Error");
                     default:
                         // throw new IOException("Unknown Error");
                 }
             } else try {
                 AbstractParser.checkInterruption();
                 
                 if (this.cfos != null) {
                     // parse the file
                     Document theDoc;
                     // workaround for relative links in file, normally '#' shall be used behind the location, see
                     // below for reversion of the effects
                     final yacyURL url = yacyURL.newURL(doc.dc_source(), this.prefix + "/" + super.filePath);
                     final String mime = ParserDispatcher.getMimeTypeByFileExt(super.filePath.substring(super.filePath.lastIndexOf('.') + 1));
                     if (this.cfos.isFallback()) {
                         theDoc = ParserDispatcher.parseSource(url, mime, null, this.cfos.getContentFile());
                     } else {
                         theDoc = ParserDispatcher.parseSource(url, mime, null, this.cfos.getContentBAOS());
                     }
                     
                     this.doc.addSubDocument(theDoc);
                 }
             } catch (final ParserException e) {
                 final IOException ex = new IOException("error parsing extracted content of " + super.filePath + ": " + e.getMessage());
                 ex.initCause(e);
                 throw ex;
             } catch (final InterruptedException e) {
                 final IOException ex = new IOException("interrupted");
                 ex.initCause(e);
                 throw ex;
             }
         }
         
         public OutputStream GetStream(final int index, final int askExtractMode) throws IOException {
             final SevenZipEntry item = super.archiveHandler.getEntry(index);
             super.filePath = item.getName();
             try {
                 AbstractParser.checkInterruption();
             } catch (final InterruptedException e) {
                 final IOException ex = new IOException("interrupted");
                 ex.initCause(e);
                 throw ex;
             }
             this.cfos = (item.isDirectory()) ? null
                     : new serverCachedFileOutputStream(this.maxRamSize, null, true, item.getSize());
             return this.cfos;
         }
         
         public String getCurrentFilePath() {
             return super.filePath;
         }
     }
     
     private static class SeekableByteArrayInputStream extends ByteArrayInputStream {
         public SeekableByteArrayInputStream(final byte[] buf) { super(buf); }
         public SeekableByteArrayInputStream(final byte[] buf, final int off, final int len) { super(buf, off, len); }
         
         public int getPosition() { return super.pos; }
         public void seekRelative(final int offset) { seekAbsolute(super.pos + offset); }
         public void seekAbsolute(final int offset) {
             if (offset > super.count)
                 throw new IndexOutOfBoundsException(Integer.toString(offset));
             super.pos = offset;
         }
     }
     
     private static class ByteArrayIInStream extends IInStream {
         
         private final SeekableByteArrayInputStream sbais;
         
         public ByteArrayIInStream(final byte[] buffer) {
             this.sbais = new SeekableByteArrayInputStream(buffer);
         }
         
         public long Seek(final long offset, final int origin) {
             switch (origin) {
                 case STREAM_SEEK_SET: this.sbais.seekAbsolute((int)offset); break;
                 case STREAM_SEEK_CUR: this.sbais.seekRelative((int)offset); break;
             }
             return this.sbais.getPosition();
         }
         
         public int read() throws IOException {
             return this.sbais.read();
         }
         
         public int read(final byte[] b, final int off, final int len) throws IOException {
             return this.sbais.read(b, off, len);
         }
     }
     
}
