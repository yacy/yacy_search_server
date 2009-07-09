// Parser.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.07.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-03-20 16:44:59 +0100 (Fr, 20 Mrz 2009) $
// $LastChangedRevision: 5736 $
// $LastChangedBy: borg-0300 $
//
// LICENSE
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

package de.anomic.document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import de.anomic.document.parser.bzipParser;
import de.anomic.document.parser.docParser;
import de.anomic.document.parser.gzipParser;
import de.anomic.document.parser.htmlParser;
import de.anomic.document.parser.mimeTypeParser;
import de.anomic.document.parser.odtParser;
import de.anomic.document.parser.pdfParser;
import de.anomic.document.parser.pptParser;
import de.anomic.document.parser.psParser;
import de.anomic.document.parser.rpmParser;
import de.anomic.document.parser.rssParser;
import de.anomic.document.parser.rtfParser;
import de.anomic.document.parser.sevenzipParser;
import de.anomic.document.parser.swfParser;
import de.anomic.document.parser.tarParser;
import de.anomic.document.parser.vcfParser;
import de.anomic.document.parser.vsdParser;
import de.anomic.document.parser.xlsParser;
import de.anomic.document.parser.zipParser;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public final class Parser {

    private static final Log theLogger = new Log("PARSER");
    public static final HashMap<String, Idiom> availableParserList = new HashMap<String, Idiom>();
    
    static {
        initParser(new bzipParser());
        initParser(new docParser());
        initParser(new gzipParser());
        initParser(new mimeTypeParser());
        initParser(new odtParser());
        initParser(new pdfParser());
        initParser(new pptParser());
        initParser(new psParser());
        initParser(new rpmParser());
        initParser(new rssParser());
        initParser(new rtfParser());
        initParser(new sevenzipParser());
        initParser(new swfParser());
        initParser(new tarParser());
        initParser(new vcfParser());
        initParser(new vsdParser());
        initParser(new xlsParser());
        initParser(new zipParser());
    }

    private static void initParser(Idiom theParser) {
        final Hashtable<String, String> supportedMimeTypes = theParser.getSupportedMimeTypes();
        final Iterator<String> mimeTypeIterator = supportedMimeTypes.keySet().iterator();
        while (mimeTypeIterator.hasNext()) {
            final String mimeType = mimeTypeIterator.next();
            availableParserList.put(mimeType, theParser);
            Log.logInfo("PARSER", "Found parser for mimeType '" + mimeType + "': " + theParser.getName());
        }
    }

    public static Document parseSource(final yacyURL location,
            final String mimeType, final String charset,
            final byte[] sourceArray) throws InterruptedException,
            ParserException {
        ByteArrayInputStream byteIn = null;
        try {
            if (theLogger.isFine()) theLogger.logFine("Parsing '" + location + "' from byte-array");
            if (sourceArray == null || sourceArray.length == 0) {
                final String errorMsg = "No resource content available (1) " + (((sourceArray == null) ? "source == null" : "source.length() == 0") + ", url = " + location.toNormalform(true, false));
                theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location, errorMsg);
            }
            byteIn = new ByteArrayInputStream(sourceArray);
            return parseSource(location, mimeType, charset, sourceArray.length, byteIn);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            theLogger.logSevere("Unexpected exception in parseSource from byte-array: " + e.getMessage(), e);
            throw new ParserException("Unexpected exception while parsing " + location, location, e);
        } finally {
            if (byteIn != null) try {
                byteIn.close();
            } catch (final Exception ex) { }
        }
    }

    public static Document parseSource(final yacyURL location,
            final String mimeType, final String charset,
            final File sourceFile) throws InterruptedException, ParserException {

        BufferedInputStream sourceStream = null;
        try {
            if (theLogger.isFine()) theLogger.logFine("Parsing '" + location + "' from file");
            if (!(sourceFile.exists() && sourceFile.canRead() && sourceFile.length() > 0)) {
                final String errorMsg = sourceFile.exists() ? "Empty resource file." : "No resource content available (2).";
                theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location, "document has no content");
            }
            sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
            return parseSource(location, mimeType, charset, sourceFile.length(), sourceStream);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            theLogger.logSevere("Unexpected exception in parseSource from File: " + e.getMessage(), e);
            throw new ParserException("Unexpected exception while parsing " + location, location, e);
        } finally {
            if (sourceStream != null)try {
                sourceStream.close();
            } catch (final Exception ex) {}
        }
    }

    public static Document parseSource(final yacyURL location,
            String mimeType, final String charset,
            final long contentLength, final InputStream sourceStream)
            throws InterruptedException, ParserException {
        try {
            if (theLogger.isFine()) theLogger.logFine("Parsing '" + location + "' from stream");
            mimeType = Classification.normalizeMimeType(mimeType);
            final String fileExt = Classification.getFileExt(location);
            final String documentCharset = htmlParser.patchCharsetEncoding(charset);
            if (!Classification.supportedContent(location, mimeType)) {
                final String errorMsg = "No parser available to parse mimetype '" + mimeType + "' (1)";
                theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location, "wrong mime type or wrong extension");
            }
            if (theLogger.isFine()) theLogger.logInfo("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "'.");
            Idiom parser = availableParserList.get(Classification.normalizeMimeType(mimeType));
            Document doc = null;
            if (parser != null) {
                parser.setContentLength(contentLength);
                doc = parser.parse(location, mimeType, documentCharset, sourceStream);
            } else if (Classification.HTMLParsableMimeTypesContains(mimeType)) {
                doc = new htmlParser().parse(location, mimeType, documentCharset, sourceStream);
            } else {
                final String errorMsg = "No parser available to parse mimetype '" + mimeType + "' (2)";
                theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location, "wrong mime type or wrong extension");
            }
            if (doc == null) {
                final String errorMsg = "Unexpected error. Parser returned null.";
                theLogger.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            return doc;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            final String errorMsg = "Unexpected exception. " + e.getMessage();
            theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg, e);
            throw new ParserException(errorMsg, location, e);
        }
    }

}
