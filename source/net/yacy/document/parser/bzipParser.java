//bzipParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;


public class bzipParser extends AbstractParser implements Parser {

    public bzipParser() {
        super("Bzip 2 UNIX Compressed File Parser");
        this.SUPPORTED_EXTENSIONS.add("bz2");
        this.SUPPORTED_EXTENSIONS.add("tbz");
        this.SUPPORTED_EXTENSIONS.add("tbz2");
        this.SUPPORTED_MIME_TYPES.add("application/x-bzip2");
        this.SUPPORTED_MIME_TYPES.add("application/bzip2");
        this.SUPPORTED_MIME_TYPES.add("application/x-bz2");
        this.SUPPORTED_MIME_TYPES.add("application/x-bzip");
        this.SUPPORTED_MIME_TYPES.add("application/x-stuffit");
    }

    @Override
    public Document[] parse(final AnchorURL location, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {

        File tempFile = null;
        Document[] docs;
        try {
            /*
             * First we have to consume the first two char from the stream. Otherwise
             * the bzip decompression will fail with a nullpointerException!
             */
            int b = source.read();
            if (b != 'B') {
                throw new Exception("Invalid bz2 content.");
            }
            b = source.read();
            if (b != 'Z') {
                throw new Exception("Invalid bz2 content.");
            }

            int read = 0;
            final byte[] data = new byte[1024];
            final BZip2CompressorInputStream zippedContent = new BZip2CompressorInputStream(source);

            tempFile = File.createTempFile("bunzip","tmp");
            tempFile.deleteOnExit();

            // creating a temp file to store the uncompressed data
            final FileOutputStream out = new FileOutputStream(tempFile);

            // reading gzip file and store it uncompressed
            while((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
            zippedContent.close();
            out.close();

            // creating a new parser class to parse the unzipped content
            docs = TextParser.parseSource(location, null, null, 999, tempFile);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unexpected error while parsing bzip file. " + e.getMessage(),location);
        } finally {
            if (tempFile != null) FileUtils.deletedelete(tempFile);
        }
        return docs;
    }
}
