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
import java.text.Collator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.anomic.document.parser.bzipParser;
import de.anomic.document.parser.docParser;
import de.anomic.document.parser.gzipParser;
import de.anomic.document.parser.htmlParser;
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

    private static final Log log = new Log("PARSER");
    
    // use a collator to relax when distinguishing between lowercase und uppercase letters
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }
    
    private static final Map<String, Idiom> mime2parser = new TreeMap<String, Idiom>(insensitiveCollator);
    private static final Map<String, Set<String>> ext2mime = new TreeMap<String, Set<String>>(insensitiveCollator);
    private static final Set<String> denyMime = new TreeSet<String>(insensitiveCollator);
    
    static {
        initParser(new bzipParser());
        initParser(new docParser());
        initParser(new gzipParser());
        initParser(new htmlParser());
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
    
    public static Set<Idiom> idioms() {
        Set<Idiom> c = new HashSet<Idiom>();
        c.addAll(mime2parser.values());
        return c;
    }

    private static void initParser(Idiom parser) {
        for (Map.Entry<String, String> e: parser.getSupportedMimeTypes().entrySet()) {
            // process the mime types
            final String mimeType = normalizeMimeType(e.getKey());
            Idiom p0 = mime2parser.get(mimeType);
            if (p0 != null) log.logSevere("parser for mime '" + mimeType + "' was set to '" + p0.getName() + "', overwriting with new parser '" + parser.getName() + "'.");
            mime2parser.put(mimeType, parser);
            Log.logInfo("PARSER", "Parser for mime type '" + mimeType + "': " + parser.getName());

            // process the extensions
            String[] exts = e.getValue().split(",");
            for (String ext: exts) {
                Set<String> s = ext2mime.get(ext);
                if (s == null) s = new HashSet<String>();
                s.add(mimeType);
                ext2mime.put(ext, s);
            }
        }
    }

    public static Document parseSource(final yacyURL location,
            final String mimeType, final String charset,
            final byte[] sourceArray) throws InterruptedException,
            ParserException {
        ByteArrayInputStream byteIn = null;
        try {
            if (log.isFine()) log.logFine("Parsing '" + location + "' from byte-array");
            if (sourceArray == null || sourceArray.length == 0) {
                final String errorMsg = "No resource content available (1) " + (((sourceArray == null) ? "source == null" : "source.length() == 0") + ", url = " + location.toNormalform(true, false));
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            byteIn = new ByteArrayInputStream(sourceArray);
            return parseSource(location, mimeType, charset, sourceArray.length, byteIn);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            log.logSevere("Unexpected exception in parseSource from byte-array: " + e.getMessage(), e);
            throw new ParserException("Unexpected exception: " + e.getMessage(), location);
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
            if (log.isFine()) log.logFine("Parsing '" + location + "' from file");
            if (!(sourceFile.exists() && sourceFile.canRead() && sourceFile.length() > 0)) {
                final String errorMsg = sourceFile.exists() ? "Empty resource file." : "No resource content available (2).";
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
            return parseSource(location, mimeType, charset, sourceFile.length(), sourceStream);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            log.logSevere("Unexpected exception in parseSource from File: " + e.getMessage(), e);
            throw new ParserException("Unexpected exception: " + e.getMessage(), location);
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
            if (log.isFine()) log.logFine("Parsing '" + location + "' from stream");
            mimeType = normalizeMimeType(mimeType);
            final String fileExt = location.getFileExtension();
            final String documentCharset = htmlParser.patchCharsetEncoding(charset);
            if (!supportsMime(mimeType)) {
                final String errorMsg = "No parser available to parse mimetype '" + mimeType + "'";
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            if (!supportsExtension(location)) {
                final String errorMsg = "No parser available to parse extension of url path";
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            if (log.isFine()) log.logInfo("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "'.");
            Idiom parser = mime2parser.get(normalizeMimeType(mimeType));
            Document doc = null;
            if (parser != null) {
                parser.setContentLength(contentLength);
                doc = parser.parse(location, mimeType, documentCharset, sourceStream);
            } else {
                final String errorMsg = "No parser available to parse mimetype '" + mimeType + "' (2)";
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            if (doc == null) {
                final String errorMsg = "Unexpected error. Parser returned null.";
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            return doc;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            final String errorMsg = "Unexpected exception. " + e.getMessage();
            log.logSevere("Unable to parse '" + location + "'. " + errorMsg, e);
            throw new ParserException(errorMsg, location);
        }
    }

    public static boolean supportsMime(String mimeType) {
        mimeType = normalizeMimeType(mimeType);
        return !denyMime.contains(mimeType) && mime2parser.containsKey(normalizeMimeType(mimeType));
    }
    
    public static boolean supportsExtension(final yacyURL url) {
        String ext = url.getFileExtension();
        if (ext.length() == 0) return true; // may be anything; thats ok if the mime type is ok
        return ext2mime.containsKey(ext);
    }
    
    public static String mimeOf(yacyURL url) {
        return mimeOf(url.getFileExtension());
    }
    
    public static String mimeOf(String ext) {
        Set<String> mimes = ext2mime.get(ext);
        if (mimes == null) return null;
        return mimes.iterator().next();
    }
    
    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null) return "application/octet-stream";
        final int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType.trim() : mimeType.substring(0, pos).trim());
    }
    
    public static void setDenyMime(String denyList) {
        denyMime.clear();
        for (String s: denyList.split(",")) denyMime.add(normalizeMimeType(s));
    }
    
    public static String getDenyMime() {
        String s = "";
        for (String d: denyMime) s += d + ",";
        s = s.substring(0, s.length() - 1);
        return s;
    }
    
    public static void grantMime(String mime, boolean grant) {
        if (grant) denyMime.remove(normalizeMimeType(mime)); else denyMime.add(normalizeMimeType(mime));
    }
}
