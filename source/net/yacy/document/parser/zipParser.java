/**
 *  zipParser
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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;

// this is a new implementation of this parser idiom using multiple documents as result set

public class zipParser extends AbstractParser implements Parser {

    public zipParser() {        
        super("ZIP File Parser");
        SUPPORTED_EXTENSIONS.add("zip");
        SUPPORTED_EXTENSIONS.add("jar");
        SUPPORTED_EXTENSIONS.add("apk");    // Android package
        SUPPORTED_MIME_TYPES.add("application/zip");
        SUPPORTED_MIME_TYPES.add("application/x-zip");
        SUPPORTED_MIME_TYPES.add("application/x-zip-compressed");
        SUPPORTED_MIME_TYPES.add("application/x-compress");
        SUPPORTED_MIME_TYPES.add("application/x-compressed");
        SUPPORTED_MIME_TYPES.add("multipart/x-zip");
        SUPPORTED_MIME_TYPES.add("application/java-archive");
        SUPPORTED_MIME_TYPES.add("application/vnd.android.package-archive");
    }
    
    public Document[] parse(final MultiProtocolURI url, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {
        Document[] docs = null;
        final List<Document> docacc = new ArrayList<Document>();
        ZipEntry entry;
        final ZipInputStream zis = new ZipInputStream(source);                      
        File tmp = null;
        
        // loop through the elements in the zip file and parse every single file inside
        while (true) {
            try {
                if (zis.available() <= 0) break;
                entry = zis.getNextEntry();
                if (entry == null) break;
                if (entry.isDirectory() || entry.getSize() <= 0) continue;
                final String name = entry.getName();                
                final int idx = name.lastIndexOf('.');
                final String mime = TextParser.mimeOf((idx >= 0) ? name.substring(idx + 1) : "");
                try {
                    tmp = FileUtils.createTempFile(this.getClass(), name);
                    FileUtils.copy(zis, tmp, entry.getSize());  
                    docs = TextParser.parseSource(MultiProtocolURI.newURL(url, "#" + name), mime, null, tmp);
                    if (docs == null) continue;
                    for (final Document d: docs) docacc.add(d);
                } catch (final Parser.Failure e) {
                    log.logWarning("ZIP parser entry " + name + ": " + e.getMessage());
                } finally {
                    if (tmp != null) FileUtils.deletedelete(tmp);
                }
            } catch (IOException e) {
                log.logWarning("ZIP parser:" + e.getMessage());
                break;
            }
        }
        if (docacc.isEmpty()) return null;
        return docacc.toArray(new Document[docacc.size()]);
    }
}
