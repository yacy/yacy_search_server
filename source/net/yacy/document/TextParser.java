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

package net.yacy.document;

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

import net.yacy.document.parser.bzipParser;
import net.yacy.document.parser.docParser;
import net.yacy.document.parser.gzipParser;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.odtParser;
import net.yacy.document.parser.ooxmlParser;
import net.yacy.document.parser.pdfParser;
import net.yacy.document.parser.pptParser;
import net.yacy.document.parser.psParser;
import net.yacy.document.parser.rssParser;
import net.yacy.document.parser.rtfParser;
import net.yacy.document.parser.sevenzipParser;
import net.yacy.document.parser.swfParser;
import net.yacy.document.parser.tarParser;
import net.yacy.document.parser.vcfParser;
import net.yacy.document.parser.vsdParser;
import net.yacy.document.parser.xlsParser;
import net.yacy.document.parser.zipParser;
import net.yacy.document.parser.images.genericImageParser;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;


public final class TextParser {

    private static final Log log = new Log("PARSER");
    
    // use a collator to relax when distinguishing between lowercase und uppercase letters
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }
    
    private static final Map<String, Idiom> mime2parser = new TreeMap<String, Idiom>(insensitiveCollator);
    private static final Map<String, String> ext2mime = new TreeMap<String, String>(insensitiveCollator);
    private static final Set<String> denyMime = new TreeSet<String>(insensitiveCollator);
    private static final Set<String> denyExtension = new TreeSet<String>(insensitiveCollator);
    
    static {
        initParser(new bzipParser());
        initParser(new docParser());
        initParser(new gzipParser());
        initParser(new htmlParser());
        initParser(new genericImageParser());
        initParser(new odtParser());
        initParser(new ooxmlParser());
        initParser(new pdfParser());
        initParser(new pptParser());
        initParser(new psParser());
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
        String prototypeMime = null;
        for (String mime: parser.supportedMimeTypes()) {
            // process the mime types
            final String mimeType = normalizeMimeType(mime);
            if (prototypeMime == null) prototypeMime = mimeType;
            Idiom p0 = mime2parser.get(mimeType);
            if (p0 != null) log.logSevere("parser for mime '" + mimeType + "' was set to '" + p0.getName() + "', overwriting with new parser '" + parser.getName() + "'.");
            mime2parser.put(mimeType, parser);
            Log.logInfo("PARSER", "Parser for mime type '" + mimeType + "': " + parser.getName());
        }
        
        if (prototypeMime != null) for (String ext: parser.supportedExtensions()) {
            String s = ext2mime.get(ext);
            if (s != null) log.logSevere("parser for extension '" + ext + "' was set to mime '" + s + "', overwriting with new mime '" + prototypeMime + "'.");
            ext2mime.put(ext, prototypeMime);
        }
    }

    public static Document parseSource(
            final DigestURI location,
            final String mimeType,
            final String charset,
            final byte[] sourceArray
        ) throws InterruptedException, ParserException {
        
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

    public static Document parseSource(
            final DigestURI location,
            final String mimeType,
            final String charset,
            final File sourceFile
        ) throws InterruptedException, ParserException {

        BufferedInputStream sourceStream = null;
        try {
            if (log.isFine()) log.logFine("Parsing '" + location + "' from file");
            if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() == 0) {
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

    public static Document parseSource(
            final DigestURI location,
            String mimeType,
            final String charset,
            final long contentLength,
            final InputStream sourceStream
        ) throws InterruptedException, ParserException {
        try {
            if (log.isFine()) log.logFine("Parsing '" + location + "' from stream");
            mimeType = normalizeMimeType(mimeType);
            final String fileExt = location.getFileExtension();
            final String documentCharset = htmlParser.patchCharsetEncoding(charset);
            Idiom parser = idiomParser(location, mimeType);
            
            if (parser == null) {
                final String errorMsg = "No parser available to parse extension '" + location.getFileExtension() + "' or mimetype '" + mimeType + "'";
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new ParserException(errorMsg, location);
            }
            
            if (log.isFine()) log.logInfo("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "'.");
            parser.setContentLength(contentLength);
            Document doc = parser.parse(location, mimeType, documentCharset, sourceStream);

            if (doc == null) {
                final String errorMsg = "Parsing content with file extension '" + location.getFileExtension() + "' and mimetype '" + mimeType + "' failed: document == null";
                log.logWarning("Unable to parse '" + location + "'. " + errorMsg);
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
    
    /**
     * check if the parser supports the given content.
     * @param url
     * @param mimeType
     * @return returns null if the content is supportet. If the content is not supported, return a error string.
     */
    public static String supports(final DigestURI url, String mimeType) {
        try {
            // try to get a parser. If this works, we don't need the parser itself, we just return null to show that everything is ok.
            idiomParser(url, mimeType);
            return null;
        } catch (ParserException e) {
            // in case that a parser is not available, return a error string describing the problem.
            return e.getMessage();
        }
    }
    
    private static Idiom idiomParser(final DigestURI url, String mimeType) throws ParserException {
        // check mime type
        if (mimeType != null) {
            mimeType = normalizeMimeType(mimeType);
            if (denyMime.contains(mimeType)) throw new ParserException("mime type '" + mimeType + "' is denied", url);
        } else {
            mimeType = normalizeMimeType(mimeType);
        }
        
        Idiom idiom = mime2parser.get(mimeType);
        if (idiom != null) return idiom;
        
        // check extension
        String ext = url.getFileExtension();
        if (ext == null || ext.length() == 0) throw new ParserException("no file extension", url);
        if (denyExtension.contains(ext)) throw new ParserException("file extension '" + ext + "' is denied", url);
        mimeType = ext2mime.get(ext);
        if (mimeType == null) throw new ParserException("no parser available", url);
        idiom = mime2parser.get(mimeType);
        assert idiom != null;
        if (idiom == null) throw new ParserException("no parser available (internal error!)", url);
        return idiom;
    }
    
    public static String supportsMime(String mimeType) {
        if (mimeType == null) return null;
        mimeType = normalizeMimeType(mimeType);
        if (denyMime.contains(mimeType)) return "mime type '" + mimeType + "' is denied";
        if (mime2parser.get(mimeType) == null) return "no parser for mime '" + mimeType + "' available";
        return null;
    }
    
    public static String supportsExtension(final DigestURI url) {
        String ext = url.getFileExtension();
        if (ext == null || ext.length() == 0) return null;
        if (denyExtension.contains(ext)) return "file extension '" + ext + "' is denied";
        String mimeType = ext2mime.get(ext);
        if (mimeType == null) return "no parser available";
        Idiom idiom = mime2parser.get(mimeType);
        assert idiom != null;
        if (idiom == null) return "no parser available (internal error!)";
        return null;
    }
    
    public static String mimeOf(DigestURI url) {
        return mimeOf(url.getFileExtension());
    }
    
    public static String mimeOf(String ext) {
        return ext2mime.get(ext);
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
    
    public static void setDenyExtension(String denyList) {
        denyExtension.clear();
        for (String s: denyList.split(",")) denyExtension.add(s);
    }
    
    public static String getDenyExtension() {
        String s = "";
        for (String d: denyExtension) s += d + ",";
        s = s.substring(0, s.length() - 1);
        return s;
    }
    
    public static void grantExtension(String ext, boolean grant) {
        if (grant) denyExtension.remove(ext); else denyExtension.add(ext);
    }
}
