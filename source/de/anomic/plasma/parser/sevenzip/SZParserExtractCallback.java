// SZParserExtractCallback.java 
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import SevenZip.ArchiveExtractCallback;
import SevenZip.Archive.IInArchive;
import SevenZip.Archive.SevenZipEntry;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverCachedFileOutputStream;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

// wrapper class to redirect output of standard ArchiveExtractCallback to serverLog
// and parse the extracted content
public class SZParserExtractCallback extends ArchiveExtractCallback {
    
    private final serverLog log;
    private final long maxRamSize;
    private serverCachedFileOutputStream cfos = null;
    private final plasmaParser parser;
    private final plasmaParserDocument doc;
    private final String prefix;
    
    public SZParserExtractCallback(final serverLog logger, final IInArchive handler,
            final long maxRamSize, final plasmaParserDocument doc, final String prefix) {
        super.Init(handler);
        this.log = logger;
        this.maxRamSize = maxRamSize;
        this.parser = new plasmaParser();
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
                plasmaParserDocument theDoc;
                // workaround for relative links in file, normally '#' shall be used behind the location, see
                // below for reversion of the effects
                final yacyURL url = yacyURL.newURL(doc.dc_source(), this.prefix + "/" + super.filePath);
                final String mime = plasmaParser.getMimeTypeByFileExt(super.filePath.substring(super.filePath.lastIndexOf('.') + 1));
                if (this.cfos.isFallback()) {
                    theDoc = this.parser.parseSource(url, mime, null, this.cfos.getContentFile());
                } else {
                    theDoc = this.parser.parseSource(url, mime, null, this.cfos.getContentBAOS());
                }
                
                // revert the above workaround
                final Map<yacyURL, String> nanchors = new HashMap<yacyURL, String>(theDoc.getAnchors().size(), 1f);
                final Iterator<Map.Entry<yacyURL, String>> it = theDoc.getAnchors().entrySet().iterator();
                Map.Entry<yacyURL, String> entry;
                final String base = doc.dc_source().toNormalform(false, true);
                String u;
                while (it.hasNext()) {
                    entry = it.next();
                    u = entry.getKey().toNormalform(true, true);
                    if (u.startsWith(base + "/")) {
                        final String ref = "#" + u.substring(base.length() + 1);
                        if (this.log.isFinest()) this.log.logFinest("changing " + entry.getKey() + " to use reference " + ref);
                        nanchors.put(new yacyURL(base + ref, null), entry.getValue());
                    } else {
                        nanchors.put(entry.getKey(), entry.getValue());
                    }
                }
                theDoc.getAnchors().clear();
                theDoc.getAnchors().putAll(nanchors);
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
