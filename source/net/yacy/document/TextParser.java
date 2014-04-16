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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.parser.audioTagParser;
import net.yacy.document.parser.bzipParser;
import net.yacy.document.parser.csvParser;
import net.yacy.document.parser.docParser;
import net.yacy.document.parser.genericParser;
import net.yacy.document.parser.gzipParser;
import net.yacy.document.parser.htmlParser;
import net.yacy.document.parser.mmParser;
import net.yacy.document.parser.odtParser;
import net.yacy.document.parser.ooxmlParser;
import net.yacy.document.parser.pdfParser;
import net.yacy.document.parser.pptParser;
import net.yacy.document.parser.psParser;
import net.yacy.document.parser.rdfParser;
import net.yacy.document.parser.rssParser;
import net.yacy.document.parser.rtfParser;
import net.yacy.document.parser.sevenzipParser;
import net.yacy.document.parser.sidAudioParser;
import net.yacy.document.parser.swfParser;
import net.yacy.document.parser.tarParser;
import net.yacy.document.parser.torrentParser;
import net.yacy.document.parser.vcfParser;
import net.yacy.document.parser.vsdParser;
import net.yacy.document.parser.xlsParser;
import net.yacy.document.parser.zipParser;
import net.yacy.document.parser.augment.AugmentParser;
import net.yacy.document.parser.images.genericImageParser;
import net.yacy.document.parser.rdfa.impl.RDFaParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;

public final class TextParser {

    private static final Object v = new Object();

    private static final Parser genericIdiom = new genericParser();
    //use LinkedHashSet for parser collection to use (init) order to prefered parser for same ext or mime
    private static final Map<String, LinkedHashSet<Parser>> mime2parser = new ConcurrentHashMap<String, LinkedHashSet<Parser>>();
    private static final ConcurrentHashMap<String, LinkedHashSet<Parser>> ext2parser = new ConcurrentHashMap<String, LinkedHashSet<Parser>>();
    private static final Map<String, String> ext2mime = new ConcurrentHashMap<String, String>();
    private static final Map<String, Object> denyMime = new ConcurrentHashMap<String, Object>();
    private static final Map<String, Object> denyExtensionx = new ConcurrentHashMap<String, Object>();

    static {
        initParser(new bzipParser());
        initParser(new csvParser());
        initParser(new docParser());
        initParser(new gzipParser());
        // AugmentParser calls internally RDFaParser (therefore add before RDFa)
        if (Switchboard.getSwitchboard().getConfigBool("parserAugmentation", true)) initParser(new AugmentParser()); 
        // RDFaParser calls internally htmlParser (therefore add before html)
        if (Switchboard.getSwitchboard().getConfigBool("parserAugmentation.RDFa", true)) initParser(new RDFaParser());          
        initParser(new htmlParser()); // called within rdfa parser
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
        initParser(new rdfParser());
        initParser(new audioTagParser());
        
    }

    public static Set<Parser> parsers() {
        final Set<Parser> c = new HashSet<Parser>();
        for (Set<Parser> pl: ext2parser.values()) c.addAll(pl);
        for (Set<Parser> pl: mime2parser.values()) c.addAll(pl);
        return c;
    }

    private static void initParser(final Parser parser) {
        String prototypeMime = null;
        for (final String mime: parser.supportedMimeTypes()) {
            // process the mime types
            final String mimeType = normalizeMimeType(mime);
            if (prototypeMime == null) prototypeMime = mimeType;
            LinkedHashSet<Parser> p0 = mime2parser.get(mimeType);
            if (p0 == null) {
                p0 = new LinkedHashSet<Parser>();
                mime2parser.put(mimeType, p0);
            }
            p0.add(parser);
            AbstractParser.log.info("Parser for mime type '" + mimeType + "': " + parser.getName());
        }

        if (prototypeMime != null) for (String ext: parser.supportedExtensions()) {
            ext = ext.toLowerCase();
            final String s = ext2mime.get(ext);
            if (s != null && !s.equals(prototypeMime)) AbstractParser.log.info("Parser for extension '" + ext + "' was set to mime '" + s + "', overwriting with new mime '" + prototypeMime + "'.");
            ext2mime.put(ext, prototypeMime);
        }

        for (String ext: parser.supportedExtensions()) {
            // process the extensions
            ext = ext.toLowerCase();
            LinkedHashSet<Parser> p0 = ext2parser.get(ext);
            if (p0 == null) {
                p0 = new LinkedHashSet<Parser>();
                ext2parser.put(ext, p0);
            }
            p0.add(parser);
            AbstractParser.log.info("Parser for extension '" + ext + "': " + parser.getName());
        }
    }

    public static Document[] parseSource(
            final AnchorURL location,
            final String mimeType,
            final String charset,
            final int depth,
            final File sourceFile
        ) throws InterruptedException, Parser.Failure {

        BufferedInputStream sourceStream = null;
        Document[] docs = null;
        try {
            if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from file");
            if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() == 0) {
                final String errorMsg = sourceFile.exists() ? "Empty resource file." : "No resource content available (2).";
                AbstractParser.log.info("Unable to parse '" + location + "'. " + errorMsg);
                throw new Parser.Failure(errorMsg, location);
            }
            sourceStream = new BufferedInputStream(new FileInputStream(sourceFile));
            docs = parseSource(location, mimeType, charset, depth, sourceFile.length(), sourceStream);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;
            AbstractParser.log.severe("Unexpected exception in parseSource from File: " + e.getMessage(), e);
            throw new Parser.Failure("Unexpected exception: " + e.getMessage(), location);
        } finally {
            if (sourceStream != null) try { sourceStream.close(); } catch (final Exception ex) {}
        }

        return docs;
    }

    public static Document[] parseSource(
            final AnchorURL location,
            String mimeType,
            final String charset,
            final int depth,
            final byte[] content
        ) throws Parser.Failure {
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from byte-array");
        mimeType = normalizeMimeType(mimeType);
        Set<Parser> idioms = null;
        try {
            idioms = parsers(location, mimeType);
        } catch (final Parser.Failure e) {
            final String errorMsg = "Parser Failure for extension '" + MultiProtocolURL.getFileExtension(location.getFileName()) + "' or mimetype '" + mimeType + "': " + e.getMessage();
            AbstractParser.log.warn(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
        assert !idioms.isEmpty() : "no parsers applied for url " + location.toNormalform(true);

        Document[] docs = parseSource(location, mimeType, idioms, charset, depth, content);

        return docs;
    }

    public static Document[] parseSource(
            final AnchorURL location,
            String mimeType,
            final String charset,
            final int depth,
            final long contentLength,
            final InputStream sourceStream
        ) throws Parser.Failure {
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from stream");
        mimeType = normalizeMimeType(mimeType);
        Set<Parser> idioms = null;
        try {
            idioms = parsers(location, mimeType);
        } catch (final Parser.Failure e) {
            final String errorMsg = "Parser Failure for extension '" + MultiProtocolURL.getFileExtension(location.getFileName()) + "' or mimetype '" + mimeType + "': " + e.getMessage();
            AbstractParser.log.warn(errorMsg);
            throw new Parser.Failure(errorMsg, location);
        }
        assert !idioms.isEmpty() : "no parsers applied for url " + location.toNormalform(true);

        // if we do not have more than one parser or the content size is over MaxInt
        // then we use only one stream-oriented parser.
        if (idioms.size() == 1 || contentLength > Integer.MAX_VALUE) {
            // use a specific stream-oriented parser
            return parseSource(location, mimeType, idioms.iterator().next(), charset, sourceStream);
        }

        // in case that we know more parsers we first transform the content into a byte[] and use that as base
        // for a number of different parse attempts.
        byte[] b = null;
        try {
            b = FileUtils.read(sourceStream, (int) contentLength);
        } catch (final IOException e) {
            throw new Parser.Failure(e.getMessage(), location);
        }
        Document[] docs = parseSource(location, mimeType, idioms, charset, depth, b);

        return docs;
    }

    private static Document[] parseSource(
            final AnchorURL location,
            final String mimeType,
            final Parser parser,
            final String charset,
            final InputStream sourceStream
        ) throws Parser.Failure {
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing '" + location + "' from stream");
        final String fileExt = MultiProtocolURL.getFileExtension(location.getFileName());
        final String documentCharset = htmlParser.patchCharsetEncoding(charset);
        assert parser != null;

        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "'.");
        try {
            final Document[] docs = parser.parse(location, mimeType, documentCharset, sourceStream);
            return docs;
        } catch (final Exception e) {
            throw new Parser.Failure("parser failed: " + parser.getName(), location);
        }
    }

    private static Document[] parseSource(
            final AnchorURL location,
            final String mimeType,
            final Set<Parser> parsers,
            final String charset,
            final int depth,
            final byte[] sourceArray
        ) throws Parser.Failure {
        final String fileExt = MultiProtocolURL.getFileExtension(location.getFileName());
        if (AbstractParser.log.isFine()) AbstractParser.log.fine("Parsing " + location + " with mimeType '" + mimeType + "' and file extension '" + fileExt + "' from byte[]");
        final String documentCharset = htmlParser.patchCharsetEncoding(charset);
        assert !parsers.isEmpty();

        Document[] docs = null;
        final Map<Parser, Parser.Failure> failedParser = new HashMap<Parser, Parser.Failure>();
        String origName = Thread.currentThread().getName();
        Thread.currentThread().setName("parsing + " + location.toString()); // set a name to get the address in Thread Dump
        for (final Parser parser: parsers) {
            if (MemoryControl.request(sourceArray.length * 6, false)) {
            	ByteArrayInputStream bis;
            	if (mimeType.equals("text/plain") && parser.getName().equals("HTML Parser")) {
            	    // a hack to simulate html files .. is needed for NOLOAD queues. This throws their data into virtual text/plain messages.
            	    bis = new ByteArrayInputStream(UTF8.getBytes("<html><head></head><body><h1>" + UTF8.String(sourceArray) + "</h1></body><html>"));
            	} else {
            	    bis = new ByteArrayInputStream(sourceArray);
            	}
                try {
                    docs = parser.parse(location, mimeType, documentCharset, bis);
                } catch (final Parser.Failure e) {
                    failedParser.put(parser, e);
                    //log.logWarning("tried parser '" + parser.getName() + "' to parse " + location.toNormalform(true, false) + " but failed: " + e.getMessage(), e);
                } catch (final Exception e) {
                    failedParser.put(parser, new Parser.Failure(e.getMessage(), location));
                    //log.logWarning("tried parser '" + parser.getName() + "' to parse " + location.toNormalform(true, false) + " but failed: " + e.getMessage(), e);
                } finally {
                	try {
                		bis.close();
                	} catch(IOException ioe) {
                		// Ignore.
                	}
                }
                if (docs != null) break;
            }
        }
        Thread.currentThread().setName(origName);

        if (docs == null) {
            if (failedParser.isEmpty()) {
                final String errorMsg = "Parsing content with file extension '" + fileExt + "' and mimetype '" + mimeType + "' failed.";
                //log.logWarning("Unable to parse '" + location + "'. " + errorMsg);
                throw new Parser.Failure(errorMsg, location);
            }
            String failedParsers = "";
            for (final Map.Entry<Parser, Parser.Failure> error: failedParser.entrySet()) {
            	AbstractParser.log.warn("tried parser '" + error.getKey().getName() + "' to parse " + location.toNormalform(true) + " but failed: " + error.getValue().getMessage(), error.getValue());
                failedParsers += error.getKey().getName() + " ";
            }
            throw new Parser.Failure("All parser failed: " + failedParsers, location);
        }
        for (final Document d: docs) {
            assert d.getTextStream() != null : "mimeType = " + mimeType;
            d.setDepth(depth);
        } // verify docs

        return docs;
    }

    /**
     * check if the parser supports the given content.
     * @param url
     * @param mimeType
     * @return returns null if the content is supported. If the content is not supported, return a error string.
     */
    public static String supports(final MultiProtocolURL url, final String mimeType) {
        try {
            // try to get a parser. If this works, we don't need the parser itself, we just return null to show that everything is ok.
            final Set<Parser> idioms = parsers(url, mimeType);
            return (idioms == null || idioms.isEmpty() || (idioms.size() == 1 && idioms.iterator().next().getName().equals(genericIdiom.getName()))) ? "no parser found" : null;
        } catch (final Parser.Failure e) {
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
    private static Set<Parser> parsers(final MultiProtocolURL url, String mimeType1) throws Parser.Failure {
        final Set<Parser> idioms = new LinkedHashSet<Parser>(2); // LinkedSet to maintain order (genericParser should be last)

        // check extension
        String ext = MultiProtocolURL.getFileExtension(url.getFileName());
        Set<Parser> idiom;
        if (ext != null && ext.length() > 0) {
            if (denyExtensionx.containsKey(ext)) throw new Parser.Failure("file extension '" + ext + "' is denied (1)", url);
            idiom = ext2parser.get(ext);
            if (idiom != null) idioms.addAll(idiom);
        }

        // check given mime type
        if (mimeType1 != null) {
            mimeType1 = normalizeMimeType(mimeType1);
            if (denyMime.containsKey(mimeType1)) throw new Parser.Failure("mime type '" + mimeType1 + "' is denied (1)", url);
            idiom = mime2parser.get(mimeType1);
            if (idiom != null && !idioms.contains(idiom)) idioms.addAll(idiom);
        }

        // check mime type computed from extension
        final String mimeType2 = ext2mime.get(ext);
        if (mimeType2 != null && (idiom = mime2parser.get(mimeType2)) != null && !idioms.contains(idiom)) idioms.addAll(idiom);

        // always add the generic parser (make sure it is the last in access order)
        idioms.add(genericIdiom);
        //if (idioms.isEmpty()) throw new Parser.Failure("no parser found for extension '" + ext + "' and mime type '" + mimeType1 + "'", url);

        return idioms;
    }

    /**
     * checks if the parser supports the given mime type. It is not only checked if the parser can parse such types,
     * it is also checked if the mime type is not included in the mimetype-deny list.
     * @param mimeType
     * @return an error if the mime type is not supported, null otherwise
     */
    public static String supportsMime(String mimeType) {
        if (mimeType == null) return null;
        mimeType = normalizeMimeType(mimeType);
        if (denyMime.containsKey(mimeType)) return "mime type '" + mimeType + "' is denied (2)";
        if (mime2parser.get(mimeType) == null) return "no parser for mime '" + mimeType + "' available";
        return null;
    }

    /**
     * checks if the parser supports the given extension. It is not only checked if the parser can parse such files,
     * it is also checked if the extension is not included in the extension-deny list.
     * @param extention
     * @return an error if the extension is not supported, null otherwise
     */
    public static String supportsExtension(final String ext) {
        if (ext == null || ext.isEmpty()) return null;
        if (denyExtensionx.containsKey(ext)) return "file extension '" + ext + "' is denied (2)";
        final String mimeType = ext2mime.get(ext);
        if (mimeType == null) return "no parser available";
        final Set<Parser> idiom = mime2parser.get(mimeType);
        assert idiom != null;
        if (idiom == null || idiom.isEmpty()) return "no parser available (internal error!)";
        return null;
    }

    /**
     * checks if the parser supports the given extension. It is not only checked if the parser can parse such files,
     * it is also checked if the extension is not included in the extension-deny list.
     * @param extention
     * @return an error if the extension is not supported, null otherwise
     */
    public static String supportsExtension(final MultiProtocolURL url) {
        return supportsExtension(MultiProtocolURL.getFileExtension(url.getFileName()));
    }

    public static String mimeOf(final MultiProtocolURL url) {
        return mimeOf(MultiProtocolURL.getFileExtension(url.getFileName()));
    }

    public static String mimeOf(final String ext) {
        return ext2mime.get(ext.toLowerCase());
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null) return "application/octet-stream";
        mimeType = mimeType.toLowerCase();
        final int pos = mimeType.indexOf(';');
        return ((pos < 0) ? mimeType.trim() : mimeType.substring(0, pos).trim());
    }

    public static void setDenyMime(final String denyList) {
        denyMime.clear();
        String n;
        for (final String s: denyList.split(",")) {
            n = normalizeMimeType(s);
            if (n != null && n.length() > 0) denyMime.put(n, v);
        }
    }

    public static String getDenyMime() {
        String s = "";
        for (final String d: denyMime.keySet()) s += d + ",";
        if (!s.isEmpty()) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static void grantMime(final String mime, final boolean grant) {
        final String n = normalizeMimeType(mime);
        if (n == null || n.isEmpty()) return;
        if (grant) denyMime.remove(n); else denyMime.put(n, v);
    }

    public static void setDenyExtension(final String denyList) {
        denyExtensionx.clear();
        for (final String s: denyList.split(",")) denyExtensionx.put(s, v);
    }

    public static String getDenyExtension() {
        String s = "";
        for (final String d: denyExtensionx.keySet()) s += d + ",";
        if (!s.isEmpty()) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static void grantExtension(final String ext, final boolean grant) {
        if (ext == null || ext.isEmpty()) return;
        if (grant) denyExtensionx.remove(ext); else denyExtensionx.put(ext, v);
    }

}
