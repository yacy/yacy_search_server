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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

// this is a new implementation of this parser idiom using multiple documents as result set

public class tarParser extends AbstractParser implements Parser {

    public tarParser() {        
        super("Tape Archive File Parser"); 
        SUPPORTED_EXTENSIONS.add("tar");
        SUPPORTED_MIME_TYPES.add("application/x-tar");
        SUPPORTED_MIME_TYPES.add("application/tar");
        SUPPORTED_MIME_TYPES.add("applicaton/x-gtar");
        SUPPORTED_MIME_TYPES.add("multipart/x-tar");
    }
    
    public Document[] parse(final MultiProtocolURI url, final String mimeType, final String charset, InputStream source) throws Parser.Failure, InterruptedException {
        
        final List<Document> docacc = new ArrayList<Document>();
        Document[] subDocs = null;
        final String ext = url.getFileExtension().toLowerCase();
        if (ext.equals("gz") || ext.equals("tgz")) {
            try {
                source = new GZIPInputStream(source);
            } catch (IOException e) {
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
                    subDocs = TextParser.parseSource(MultiProtocolURI.newURL(url,"#" + name), mime, null, tmp);
                    if (subDocs == null) continue;
                    for (final Document d: subDocs) docacc.add(d);
                } catch (final Parser.Failure e) {
                    log.logWarning("tar parser entry " + name + ": " + e.getMessage());
                } finally {
                    if (tmp != null) FileUtils.deletedelete(tmp);
                }
            } catch (IOException e) {
                log.logWarning("tar parser:" + e.getMessage());
                break;
            }
        }
        return docacc.toArray(new Document[docacc.size()]);
    }
}
