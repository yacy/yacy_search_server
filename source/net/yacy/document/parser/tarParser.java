/**
 *  tarParser
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 29.6.2010 at http://yacy.net
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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

// this is a new implementation of this parser idiom using multiple documents as result set

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
    public Document[] parse(final AnchorURL url, final String mimeType, final String charset, InputStream source) throws Parser.Failure, InterruptedException {

        final List<Document> docacc = new ArrayList<Document>();
        Document[] subDocs = null;
        final String ext = MultiProtocolURL.getFileExtension(url.getFileName());
        if (ext.equals("gz") || ext.equals("tgz")) {
            try {
                source = new GZIPInputStream(source);
            } catch (final IOException e) {
                throw new Parser.Failure("tar parser: " + e.getMessage(), url);
            }
        }
        TarEntry entry;
        final TarInputStream tis = new TarInputStream(source);
        File tmp = null;

        // loop through the elements in the tar file and parse every single file inside
        while (true) {
            try {
                if (tis.available() <= 0) break;
                entry = tis.getNextEntry();
                if (entry == null) break;
                if (entry.isDirectory() || entry.getSize() <= 0) continue;
                final String name = entry.getName();
                final int idx = name.lastIndexOf('.');
                final String mime = TextParser.mimeOf((idx > -1) ? name.substring(idx+1) : "");
                try {
                    tmp = FileUtils.createTempFile(this.getClass(), name);
                    FileUtils.copy(tis, tmp, entry.getSize());
                    subDocs = TextParser.parseSource(AnchorURL.newAnchor(url, "#" + name), mime, null, 999, tmp);
                    if (subDocs == null) continue;
                    for (final Document d: subDocs) docacc.add(d);
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
        return docacc.toArray(new Document[docacc.size()]);
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
