// SZParserExtractCallback.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// 
// This file ist contributed by Franz Brausse
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.net.URL;
import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverCachedFileOutputStream;
import de.anomic.server.logging.serverLog;

import SevenZip.ArchiveExtractCallback;
import SevenZip.Archive.IInArchive;
import SevenZip.Archive.SevenZipEntry;

// wrapper class to redirect output of standard ArchiveExtractCallback to serverLog
// and parse the extracted content
public class SZParserExtractCallback extends ArchiveExtractCallback {
    
    private final serverLog log;
    private final long maxRamSize;
    private serverCachedFileOutputStream cfos = null;
    private final plasmaParser parser;
    private final plasmaParserDocument doc;
    private final String prefix;
    
    public SZParserExtractCallback(serverLog logger, IInArchive handler,
            long maxRamSize, plasmaParserDocument doc, String prefix) {
        super.Init(handler);
        this.log = logger;
        this.maxRamSize = maxRamSize;
        this.parser = new plasmaParser();
        this.doc = doc;
        this.prefix = prefix;
    }
    
    public void PrepareOperation(int arg0) {
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
        };
    }

    public void SetOperationResult(int arg0) throws IOException {
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
                URL url = URL.newURL(doc.getLocation(), this.prefix + "/" + super.filePath);
                String mime = plasmaParser.getMimeTypeByFileExt(super.filePath.substring(super.filePath.lastIndexOf('.') + 1));
                if (this.cfos.isFallback()) {
                    theDoc = this.parser.parseSource(url, mime, null, this.cfos.getContentFile());
                } else {
                    theDoc = this.parser.parseSource(url, mime, null, this.cfos.getContentBAOS());
                }
                
                // revert the above workaround
                Map nanchors = new HashMap(theDoc.getAnchors().size(), 1f);
                Iterator it = theDoc.getAnchors().entrySet().iterator();
                Map.Entry entry;
                String base = doc.getLocation().toNormalform(false, true);
                while (it.hasNext()) {
                    entry = (Map.Entry)it.next();
                    if (((String)entry.getKey()).startsWith(base + "/")) {
                        String ref = "#" + ((String)entry.getKey()).substring(base.length() + 1);
                        this.log.logFinest("changing " + entry.getKey() + " to use reference " + ref);
                        nanchors.put(base + ref, entry.getValue());
                    } else {
                        nanchors.put(entry.getKey(), entry.getValue());
                    }
                }
                theDoc.getAnchors().clear();
                theDoc.getAnchors().putAll(nanchors);
                this.doc.addSubDocument(theDoc);
            }
        } catch (ParserException e) {
            IOException ex = new IOException("error parsing extracted content of " + super.filePath + ": " + e.getMessage());
            ex.initCause(e);
            throw ex;
        } catch (InterruptedException e) {
            IOException ex = new IOException("interrupted");
            ex.initCause(e);
            throw ex;
        }
    }
    
    public OutputStream GetStream(int index, int askExtractMode) throws IOException {
        SevenZipEntry item = super.archiveHandler.getEntry(index);
        super.filePath = item.getName();
        try {
            AbstractParser.checkInterruption();
        } catch (InterruptedException e) {
            IOException ex = new IOException("interrupted");
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
