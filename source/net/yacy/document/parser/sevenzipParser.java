// sevenzipParser.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// 
// This file ist contributed by Franz Brausze
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;

import SevenZip.ArchiveExtractCallback;
import SevenZip.IInStream;
import SevenZip.Archive.IInArchive;
import SevenZip.Archive.SevenZipEntry;
import SevenZip.Archive.SevenZip.Handler;

public class sevenzipParser extends AbstractParser implements Parser {
    
    public sevenzipParser() {
        super("7zip Archive Parser");
        SUPPORTED_EXTENSIONS.add("7z");
        SUPPORTED_MIME_TYPES.add("application/x-7z-compressed"); 
    }
    
    public Document parse(final MultiProtocolURI location, final String mimeType, final String charset, final IInStream source) throws Parser.Failure, InterruptedException {
        final Document doc = new Document(
                location,
                mimeType,
                charset,
                this,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0.0f, 0.0f, 
                (Object)null,
                null,
                null,
                null,
                false);
        Handler archive;
        super.log.logFine("opening 7zip archive...");
        try {
            archive = new Handler(source);
        } catch (final IOException e) {
            throw new Parser.Failure("error opening 7zip archive: " + e.getMessage(), location);
        }
        final SZParserExtractCallback aec = new SZParserExtractCallback(super.log, archive,
                doc, location.getFile());
        super.log.logFine("processing archive contents...");
        try {
            archive.Extract(null, -1, 0, aec);
            return doc;   
        } catch (final IOException e) {
            if (e.getCause() instanceof InterruptedException)
                throw (InterruptedException)e.getCause();
            if (e.getCause() instanceof Parser.Failure)
                throw (Parser.Failure)e.getCause();
            throw new Parser.Failure(
                    "error processing 7zip archive at internal file " + aec.getCurrentFilePath() + ": " + e.getMessage(),
                    location);
        } finally {
            try { archive.close(); } catch (final IOException e) {  }
        }
    }
    
    public Document[] parse(final MultiProtocolURI location, final String mimeType, final String charset,
            final InputStream source) throws Parser.Failure, InterruptedException {
        try {
            final ByteArrayOutputStream cfos = new ByteArrayOutputStream();
            FileUtils.copy(source, cfos);
            return parse(location, mimeType, charset, new ByteArrayInputStream(cfos.toByteArray()));
        } catch (final IOException e) {
            throw new Parser.Failure("error processing 7zip archive: " + e.getMessage(), location);
        }
    }

     // wrapper class to redirect output of standard ArchiveExtractCallback to serverLog
     // and parse the extracted content
     private static class SZParserExtractCallback extends ArchiveExtractCallback {
         
         private final Log log;
         private ByteArrayOutputStream cfos = null;
         private final Document doc;
         private final String prefix;
         
         public SZParserExtractCallback(final Log logger, final IInArchive handler,
                 final Document doc, final String prefix) {
             super.Init(handler);
             this.log = logger;
             this.doc = doc;
             this.prefix = prefix;
         }
         
        @Override
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
    
        @Override
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
                 
                 if (this.cfos != null) {
                     // parse the file
                     Document[] theDocs;
                     // workaround for relative links in file, normally '#' shall be used behind the location, see
                     // below for reversion of the effects
                     final MultiProtocolURI url = MultiProtocolURI.newURL(doc.dc_source(), this.prefix + "/" + super.filePath);
                     final String mime = TextParser.mimeOf(super.filePath.substring(super.filePath.lastIndexOf('.') + 1));
                     theDocs = TextParser.parseSource(url, mime, null, this.cfos.toByteArray());
                     
                     this.doc.addSubDocuments(theDocs);
                 }
             } catch (final Exception e) {
                 final IOException ex = new IOException("error parsing extracted content of " + super.filePath + ": " + e.getMessage());
                 ex.initCause(e);
                 throw ex;
             }
         }
         
        @Override
         public OutputStream GetStream(final int index, final int askExtractMode) throws IOException {
             final SevenZipEntry item = super.archiveHandler.getEntry(index);
             super.filePath = item.getName();
             this.cfos = (item.isDirectory()) ? null : new ByteArrayOutputStream();
             return this.cfos;
         }
         
         public String getCurrentFilePath() {
             return super.filePath;
         }
     }
     
}
