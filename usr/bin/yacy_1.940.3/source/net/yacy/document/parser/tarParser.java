/**
 *  tarParser
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 29.6.2010 at https://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.TagValency;
import net.yacy.kelondro.util.FileUtils;

// this is a new implementation of this parser idiom using multiple documents as result set
/**
 * Parses the tar file and each contained file,
 * returns one document with combined content.
 */
public class tarParser extends AbstractParser implements Parser {

    private final static String MAGIC = "ustar"; // A magic for a tar archive, may appear at #101h-#105

    public tarParser() {
        super("Tape Archive File Parser");
        this.SUPPORTED_EXTENSIONS.add("tar");
        this.SUPPORTED_MIME_TYPES.add("application/x-tar");
        this.SUPPORTED_MIME_TYPES.add("application/tar");
        this.SUPPORTED_MIME_TYPES.add("applicaton/x-gtar");
        this.SUPPORTED_MIME_TYPES.add("multipart/x-tar");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final TagValency defaultValency, 
            final Set<String> valencySwitchTagNames,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            InputStream source) throws Parser.Failure, InterruptedException {

        final String filename = location.getFileName();
        final String ext = MultiProtocolURL.getFileExtension(filename);
        final DigestURL parentTarURL = createParentTarURL(location);
        // TODO is this hack really useful ? These extensions are already handled by the gzipParser
        if (ext.equals("gz") || ext.equals("tgz")) {
            try {
                source = new GZIPInputStream(source);
            } catch (final IOException e) {
                throw new Parser.Failure("tar parser: " + e.getMessage(), location);
            }
        }
        TarArchiveEntry entry;
        final TarArchiveInputStream tis = new TarArchiveInputStream(source);
        
        // create maindoc for this tar container
        final Document maindoc = createMainDocument(location, mimeType, charset, this);
        // loop through the elements in the tar file and parse every single file inside
        while (true) {
            try {
                File tmp = null;
                entry = tis.getNextEntry();
                if (entry == null) break;
                if (entry.isDirectory() || entry.getSize() <= 0) continue;
                final String name = entry.getName();
                final int idx = name.lastIndexOf('.');
                final String mime = TextParser.mimeOf((idx > -1) ? name.substring(idx+1) : "");
                try {
                    tmp = FileUtils.createTempFile(this.getClass(), name);
                    FileUtils.copy(tis, tmp, entry.getSize());
/*
 * Create an appropriate sub location to prevent unwanted fallback to the tarparser on resources included in the archive. 
 * We use the tar file name as the parent sub path. Example : http://host/archive.tar/name.
 * Indeed if we create a sub location with a '#' separator such as http://host/archive.tar#name, the
 * extension of the URL is still ".tar", thus incorrectly making the tar parser
 * as a possible parser for the sub resource.
 */
                    final DigestURL subLocation = new DigestURL(parentTarURL, name);
                    final Document[] subDocs = TextParser.parseSource(subLocation, mime, null, defaultValency, valencySwitchTagNames, scraper, timezoneOffset,999, tmp);
                    if (subDocs == null) {
                    continue;
                    }
                    maindoc.addSubDocuments(subDocs);
                } catch (final Parser.Failure e) {
                    AbstractParser.log.warn("tar parser entry " + name + ": " + e.getMessage());
                } finally {
                    if (tmp != null) FileUtils.deletedelete(tmp);
                }
            } catch (final IOException e) {
                AbstractParser.log.warn("tar parser:" + e.getMessage());
                break;
            }
        }
        return new Document[]{maindoc};
    }

@Override
public boolean isParseWithLimitsSupported() {
return true;
}

@Override
public Document[] parseWithLimits(final DigestURL location, final String mimeType, final String charset,
final VocabularyScraper scraper, final int timezoneOffset, final InputStream source, final int maxLinks,
final long maxBytes) throws Failure, InterruptedException, UnsupportedOperationException {

final DigestURL parentTarURL = createParentTarURL(location);

final TarArchiveInputStream tis = new TarArchiveInputStream(source);

// create maindoc for this tar container
final Document maindoc = createMainDocument(location, mimeType, charset, this);

// loop through the elements in the tar file and parse every single file inside
TarArchiveEntry entry;
int totalProcessedLinks = 0;
while (true) {
try {
entry = tis.getNextEntry();
if (entry == null) {
break;
}

/*
 * We are here sure at least one entry has still to be processed : let's check
 * now the bytes limit as sub parsers applied on eventual previous entries may
 * not support partial parsing and would have thrown a Parser.Failure instead of
 * marking the document as partially parsed.
 */
if (tis.getBytesRead() >= maxBytes) {
maindoc.setPartiallyParsed(true);
break;
}

if (entry.isDirectory() || entry.getSize() <= 0) {
continue;
}
final String name = entry.getName();
final int idx = name.lastIndexOf('.');
final String mime = TextParser.mimeOf((idx > -1) ? name.substring(idx + 1) : "");
try {
/*
 * Rely on the supporting parsers to respect the maxLinks and maxBytes limits on
 * compressed content
 */

/*
 * Create an appropriate sub location to prevent unwanted fallback to the
 * tarparser on resources included in the archive. We use the tar file name as
 * the parent sub path. Example : http://host/archive.tar/name. Indeed if we
 * create a sub location with a '#' separator such as
 * http://host/archive.tar#name, the extension of the URL is still ".tar", thus
 * incorrectly making the tar parser as a possible parser for the sub resource.
 */
final DigestURL subLocation = new DigestURL(parentTarURL, name);
final Document[] subDocs = TextParser.parseWithLimits(subLocation, mime, null, timezoneOffset, 999,
entry.getSize(), tis, maxLinks - totalProcessedLinks, maxBytes - tis.getBytesRead());

/*
 * If the parser(s) did not consume all bytes in the entry, these ones will be
 * skipped by the next call to getNextTarEntry()
 */
if (subDocs == null) {
continue;
}
maindoc.addSubDocuments(subDocs);
for (Document subDoc : subDocs) {
if (subDoc.getAnchors() != null) {
totalProcessedLinks += subDoc.getAnchors().size();
}
}
/*
 * Check if a limit has been exceeded (we are sure to pass here when maxLinks
 * has been exceeded as this limit require parser support for partial parsing to
 * be detected)
 */
if (subDocs[0].isPartiallyParsed()) {
maindoc.setPartiallyParsed(true);
break;
}
} catch (final Parser.Failure e) {
AbstractParser.log.warn("tar parser entry " + name + ": " + e.getMessage());
}
} catch (final IOException e) {
AbstractParser.log.warn("tar parser:" + e.getMessage());
break;
}
}
return new Document[] { maindoc };
}

/**
 * Generate a parent URL to use for generating sub URLs on tar archive entries.
 * 
 * @param tarURL
 *            the URL of the tar archive
 * @return an URL ending with a "/" suitable as a base URL for archive entries
 */
private DigestURL createParentTarURL(final DigestURL tarURL) {
String locationStr = tarURL.toNormalform(false);
if (!locationStr.endsWith("/")) {
locationStr += "/";
}
DigestURL parentTarURL;
try {
parentTarURL = new DigestURL(locationStr);
} catch (MalformedURLException e1) {
/* This should not happen */
parentTarURL = tarURL;
}
return parentTarURL;
}

/**
 * Create the main resulting parsed document for a tar container
 * 
 * @param location
 *            the parsed resource URL
 * @param mimeType
 *            the media type of the resource
 * @param charset
 *            the charset name if known
 * @param parser
 *            instance of tarParser that is registered as the parser origin of
 *            the document
 * @return a Document instance
 */
public static Document createMainDocument(final DigestURL location, final String mimeType, final String charset,
final tarParser parser) {
final String filename = location.getFileName();
final Document maindoc = new Document(location, mimeType, charset, parser, null, null,
AbstractParser
.singleList(filename.isEmpty() ? location.toTokens() : MultiProtocolURL.unescape(filename)), // title
null, null, null, null, 0.0d, 0.0d, (Object) null, null, null, null, false, new Date());
return maindoc;
}

    public final static boolean isTar(File f) {
        if (!f.exists() || f.length() < 0x105) return false;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            raf.seek(0x101);
            byte[] b = new byte[5];
            raf.read(b);
            return MAGIC.equals(UTF8.String(b));
        } catch (final FileNotFoundException e) {
            return false;
        } catch (final IOException e) {
            return false;
        } finally {
            if (raf != null) try {raf.close();} catch (final IOException e) {}
        }
    }
}
