/**
 *  TextParser.java
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.07.2009 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.parser.bzipParser;
import net.yacy.document.parser.csvParser;
import net.yacy.document.parser.docParser;
import net.yacy.document.parser.genericParser;
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
import net.yacy.document.parser.torrentParser;
import net.yacy.document.parser.vcfParser;
import net.yacy.document.parser.vsdParser;
import net.yacy.document.parser.xlsParser;
import net.yacy.document.parser.zipParser;
import net.yacy.document.parser.images.genericImageParser;
import net.yacy.document.parser.mmParser;
import net.yacy.document.parser.sidAudioParser;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.FileUtils;

public final class TextParser {

    private static final Log log = new Log("PARSER");
    private static final Object v = new Object();

    private static final Parser genericIdiom = new genericParser();
    private static final Map<String, Parser> mime2parser = new ConcurrentHashMap<String, Parser>();
    private static final Map<String, Parser> ext2parser = new ConcurrentHashMap<String, Parser>();
    private static final Map<String, String> ext2mime = new ConcurrentHashMap<String, String>();
    private static final Map<String, Object> denyMime = new ConcurrentHashMap<String, Object>();
    private static final Map<String, Object> denyExtensionx = new ConcurrentHashMap<String, Object>();
    
    static {
        initParser(new bzipParser());
        initParser(new csvParser());
        initParser(new docParser());
        initParser(new gzipParser());
        initParser(new htmlParser());
        initParser(new genericImageParser());
        initParser(new mmParser());
        initParser(new odtParser());
        initParser(new ooxmlParser());
        initParser(new pdfParser());
        initParser(new pptParser());
        initParser(new psParser());
        initParser(new rssParser());
        initParser(new rtfParser());
        initParser(new sevenzipParser());
        initParser(new sidAudioParser());
        initParser(new swfParser());
        initParser(new tarParser());
        initParser(new torrentParser());
        initParser(new vcfParser());
        initParser(new vsdParser());
        initParser(new xlsParser());
        initParser(new zipParser());
    }
    
    public static Set<Parser> parsers() {
        Set<Parser> c = new HashSet<Parser>();
        c.addAll(ext2parser.values());
        c.addAll(mime2parser.values());
        return c;
    }

    private static void initParser(Parser parser) {
        String prototypeMime = null;
        for (String mime: parser.supportedMimeTypes()) {
            // process the mime types
            final String mimeType = normalizeMimeType(mime);
            if (prototypeMime == null) prototypeMime = mimeType;
            Parser p0 = mime2parser.get(mimeType);
            if (p0 != null) log.logSevere("parser for mime '" + mimeType + "' was set to '" + p0.getName() + "', overwriting with new parser '" + parser.getName() + "'.");
            mime2parser.put(mimeType, parser);
            Log.logInfo("PARSER", "Parser for mime type '" + mimeType + "': " + parser.getName());
        }
        
        if (prototypeMime != null) for (String ext: parser.supportedExtensions()) {
            ext = ext.toLowerCase();
            String s = ext2mime.get(ext);
            if (s != null) log.logSevere("parser for extension '" + ext + "' was set to mime '" + s + "', overwriting with new mime '" + prototypeMime + "'.");
            ext2mime.put(ext, prototypeMime);
        }
        
        for (String ext: parser.supportedExtensions()) {
            // process the extensions
            ext = ext.toLowerCase();
            Parser p0 = ext2parser.get(ext);
            if (p0 != null) log.logSevere("parser for extension '" + ext + "' was set to '" + p0.getName() + "', overwriting with new parser '" + parser.getName() + "'.");
            ext2parser.put(ext, parser);
            Log.logInfo("PARSER", "Parser for extension '" + ext + "': " + parser.getName());
        }
    }
    
    public static Document[] parseSource(
            final MultiProtocolURI location,
            final String mimeType,
            final String charset,
            final File sourceFile
        ) throws InterruptedException, Parser.Failure {

        BufferedInputStream sourceStream = null;
        Document[] docs = null;
        try {
            if (log.isFine()) log.logFine("Parsing '" + location + "' from file");
            if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() == 0) {
                final String errorMsg = sourceFile.exists() ? "Empty resource file." : "No resource content available (2).";
                log.logInfo("Unable to parse '" + location + "'. " + errorMsg);
                throw new Parser.Failure(errorMsg, location);
            }
            sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
            docs = parseSource(location, mimeType, charset, sourceFile.length(), sourceStream);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;
            log.logSevere("Unexpected exception in parseSource from File: " + e.getMessage(), e);
            throw new Parser.Failure("Unexpected exception: " + e.getMessage(), location);
        } finally {
            if (sourceStream != null) try { sourceStream.close(); } catch (final Exception ex) {}
        }
        for (Document d: docs) { assert d.getText() != null; } // verify docs
        return docs;
    }
    
    public static Document[] parseSource(
            final MultiProtocolURI location,
            String mimeType,
            final String charset,
            final byte[] content
        ) throws Parser.Failure {
        return parseSource(location, mimeType, charset, content.length, new ByteArrayInputStream(content));
    }
    
    public static Document[] parseSource(
            final MultiProtocolURI location,
            String mimeType,
            final String charset,
            final long contentLength,
            final InputStream sourceStream
        ) throws Parser.Failure {
        if (log.isFine()) log.logFine("Parsing '" + location + "' from stream");
        mimeType = normalizeMimeType(mimeType);
        List<Parser> idioms = null;
        try {
            idioms = parsers(location, mimeType);
        } catch (Parser.Failure e) {
            final String errorMsg = "Parser Failure for extension '" + location.getFileExtension() + "' or mimetype '" + mimeType + "': " + e.getMessage();
            log.logWarning(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
        assert !idioms.isEmpty() : "no parsers applied for url " + location.toNormalform(true, false);
        
        // if we do not have more than one parser or the content size is over MaxInt
        // then we use only one stream-oriented parser.
        if (idioms.size() == 1 || contentLength > Integer.MAX_VALUE) {
            // use a specific stream-oriented parser
            Document[] docs = parseSource(location, mimeType, idioms.get(0), charset, contentLength, sourceStream);
            for (Document d: docs) { assert d.getText() != null; } // verify docs
            return docs;
        }
        
        // in case that we know more parsers we first transform the content into a byte[] and use that as base
        // for a number of different parse attempts.
        byte[] b = null;
        try {
            b = FileUtils.read(sourceStream, (int) contentLength);
        } catch (IOException e) {
            throw new Parser.Failure(e.getMessage(), location);
        }
        Document[] docs = parseSource(location, mimeType, idioms, charset, b);
        for (Document d: docs) { assert d.getText() != null; } // verify docs
        return docs;
    }

    private static Document[] parseSource(
            final MultiProtocolURI location,
            String mimeType,
            Parser parser,
            final String charset,
            final long contentLength,
            final InputStream sourceStream
        ) throws Parser.Failure {
        if (log.isFine()) log.logFine("Parsing '" + location + "' from stream");
        final String fileExt = location.getFileExtension();
        final String documentCharset = htmlParser.patchCharsetEncoding(charset);
        assert parser != null;

        if (log.isFine()) log.logInfo("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "'.");
        try {
            Document[] docs = parser.parse(location, mimeType, documentCharset, sourceStream);
            for (Document d: docs) { assert d.getText() != null; } // verify docs
            return docs;
        } catch (Exception e) {
            throw new Parser.Failure("parser failed: " + parser.getName(), location);
        }
    }

    private static Document[] parseSource(
            final MultiProtocolURI location,
            final String mimeType,
            final List<Parser> parsers,
            final String charset,
            final byte[] sourceArray
        ) throws Parser.Failure {
        final String fileExt = location.getFileExtension();
        if (log.isFine()) log.logInfo("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "' from byte[]");
        final String documentCharset = htmlParser.patchCharsetEncoding(charset);
        assert !parsers.isEmpty();

        Document[] docs = null;
        HashMap<Parser, Parser.Failure> failedParser = new HashMap<Parser, Parser.Failure>();
        for (Parser parser: parsers) {
            try {
                docs = parser.parse(location, mimeType, documentCharset, new ByteArrayInputStream(sourceArray));
            } catch (Parser.Failure e) {
                failedParser.put(parser, e);
                //log.logWarning("tried parser '" + parser.getName() + "' to parse " + location.toNormalform(true, false) + " but failed: " + e.getMessage(), e);
            } catch (Exception e) {
                failedParser.put(parser, new Parser.Failure(e.getMessage(), location));
                //log.logWarning("tried parser '" + parser.getName() + "' to parse " + location.toNormalform(true, false) + " but failed: " + e.getMessage(), e);
            }
            if (docs != null) break;
        }
        
        if (docs == null) {
            if (failedParser.isEmpty()) {
                final String errorMsg = "Parsing content with file extension '" + location.getFileExtension() + "' and mimetype '" + mimeType + "' failed.";
                //log.logWarning("Unable to parse '" + location + "'. " + errorMsg);
                throw new Parser.Failure(errorMsg, location);
            } else {
                String failedParsers = "";
                for (Map.Entry<Parser, Parser.Failure> error: failedParser.entrySet()) {
                    log.logWarning("tried parser '" + error.getKey().getName() + "' to parse " + location.toNormalform(true, false) + " but failed: " + error.getValue().getMessage(), error.getValue());
                    failedParsers += error.getKey().getName() + " ";
                }
                throw new Parser.Failure("All parser failed: " + failedParsers, location);
            }
        }
        for (Document d: docs) { assert d.getText() != null : "mimeType = " + mimeType; } // verify docs
        return docs;
    }
    
    /**
     * check if the parser supports the given content.
     * @param url
     * @param mimeType
     * @return returns null if the content is supported. If the content is not supported, return a error string.
     */
    public static String supports(final MultiProtocolURI url, String mimeType) {
        try {
            // try to get a parser. If this works, we don't need the parser itself, we just return null to show that everything is ok.
            List<Parser> idioms = parsers(url, mimeType);
            return (idioms == null || idioms.isEmpty() || (idioms.size() == 1 && idioms.get(0).getName().equals(genericIdiom.getName()))) ? "no parser found" : null;
        } catch (Parser.Failure e) {
            // in case that a parser is not available, return a error string describing the problem.
            return e.getMessage();
        }
    }
    
    /**
     * find a parser for a given url and mime type
     * because mime types returned by web severs are sometimes wrong, we also compute the mime type again
     * from the extension that can be extracted from the url path. That means that there are 3 criteria
     * that can be used to select a parser:
     * - the given extension
     * - the given mime type
     * - the mime type computed from the extension
     * @param url the given url
     * @param mimeType the given mime type
     * @return a list of Idiom parsers that may be appropriate for the given criteria
     * @throws Parser.Failure
     */
    private static List<Parser> parsers(final MultiProtocolURI url, String mimeType1) throws Parser.Failure {
        List<Parser> idioms = new ArrayList<Parser>(2);
        
        // check extension
        String ext = url.getFileExtension();
        Parser idiom;
        if (ext != null && ext.length() > 0) {
            ext = ext.toLowerCase();
            if (denyExtensionx.containsKey(ext)) throw new Parser.Failure("file extension '" + ext + "' is denied (1)", url);
            idiom = ext2parser.get(ext);
            if (idiom != null) idioms.add(idiom);
        }
        
        // check given mime type
        if (mimeType1 != null) {
            mimeType1 = normalizeMimeType(mimeType1);
            if (denyMime.containsKey(mimeType1)) throw new Parser.Failure("mime type '" + mimeType1 + "' is denied (1)", url);
            idiom = mime2parser.get(mimeType1);
            if (idiom != null && !idioms.contains(idiom)) idioms.add(idiom);
        }
        
        // check mime type computed from extension
        String mimeType2 = ext2mime.get(ext);
        if (mimeType2 != null && (idiom = mime2parser.get(mimeType2)) != null && !idioms.contains(idiom)) idioms.add(idiom);
        
        // always add the generic parser
        idioms.add(genericIdiom);
        //if (idioms.isEmpty()) throw new Parser.Failure("no parser found for extension '" + ext + "' and mime type '" + mimeType1 + "'", url);
        
        return idioms;
    }
    public static String supportsMime(String mimeType) {
        if (mimeType == null) return null;
        mimeType = normalizeMimeType(mimeType);
        if (denyMime.containsKey(mimeType)) return "mime type '" + mimeType + "' is denied (2)";
        if (mime2parser.get(mimeType) == null) return "no parser for mime '" + mimeType + "' available";
        return null;
    }

    public static String supportsExtension(final MultiProtocolURI url) {
        String ext = url.getFileExtension().toLowerCase();
        if (ext == null || ext.length() == 0) return null;
        if (denyExtensionx.containsKey(ext)) return "file extension '" + ext + "' is denied (2)";
        String mimeType = ext2mime.get(ext);
        if (mimeType == null) return "no parser available";
        Parser idiom = mime2parser.get(mimeType);
        assert idiom != null;
        if (idiom == null) return "no parser available (internal error!)";
        return null;
    }
    
    public static String mimeOf(MultiProtocolURI url) {
        return mimeOf(url.getFileExtension());
    }
    
    public static String mimeOf(String ext) {
        return ext2mime.get(ext.toLowerCase());
    }
    
    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null) return "application/octet-stream";
        mimeType = mimeType.toLowerCase();
        final int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType.trim() : mimeType.substring(0, pos).trim());
    }
    
    public static void setDenyMime(String denyList) {
        denyMime.clear();
        String n;
        for (String s: denyList.split(",")) {
            n = normalizeMimeType(s);
            if (n != null && n.length() > 0) denyMime.put(n, v);
        }
    }
    
    public static String getDenyMime() {
        String s = "";
        for (String d: denyMime.keySet()) s += d + ",";
        if (s.length() > 0) s = s.substring(0, s.length() - 1);
        return s;
    }
    
    public static void grantMime(String mime, boolean grant) {
        String n = normalizeMimeType(mime);
        if (n == null || n.length() == 0) return;
        if (grant) denyMime.remove(n); else denyMime.put(n, v);
    }
    
    public static void setDenyExtension(String denyList) {
        denyExtensionx.clear();
        for (String s: denyList.split(",")) denyExtensionx.put(s, v);
    }
    
    public static String getDenyExtension() {
        String s = "";
        for (String d: denyExtensionx.keySet()) s += d + ",";
        s = s.substring(0, s.length() - 1);
        return s;
    }
    
    public static void grantExtension(String ext, boolean grant) {
        if (grant) denyExtensionx.remove(ext); else denyExtensionx.put(ext, v);
    }

}
