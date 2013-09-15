/**
 *  sidAudioParser
 *  Copyright 2010 by Marc Nause, marc.nause@gmx.de, Braunschweig, Germany
 *  First released 28.12.2010 at http://yacy.net
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;

// this is a new implementation of this parser idiom using multiple documents as result set

/**
 * Parser for Commodore 64 SID audio files.
 * @see <a href="http://cpansearch.perl.org/src/LALA/Audio-SID-3.11/SID_file_format.txt">
 * SID file format description</a>
 * @author low012
 */
public class sidAudioParser extends AbstractParser implements Parser {

    public sidAudioParser() {
        super("Commodore 64 SID Audio File Parser");
        this.SUPPORTED_EXTENSIONS.add("sid");
        this.SUPPORTED_MIME_TYPES.add("audio/prs.sid");
        this.SUPPORTED_MIME_TYPES.add("audio/psid");
        this.SUPPORTED_MIME_TYPES.add("audio/x-psid");
        this.SUPPORTED_MIME_TYPES.add("audio/sidtune");
        this.SUPPORTED_MIME_TYPES.add("audio/x-sidtune");
    }

    @Override
    public Document[] parse(final AnchorURL location, final String mimeType,
            final String charset, final InputStream source)
            throws Parser.Failure, InterruptedException {
        try {
            final int available = source.available();
            final byte[] b = new byte[available];

            if (available >= 128 && source.read(b) >= 128) {

                final int version = (b[4] << 2) + b[5];
                Map<String, String> header = new HashMap<String, String>();
                switch (version) {
                    case 1:
                        header = parseHeader(b);
                        break;
                    case 2:
                        header = parseHeader(b);
                        break;
                    default:
                        throw new Parser.Failure("Unable to parse SID file, unexpected version: " + version, location);
                }

                return new Document[]{new Document(
                        location,
                        mimeType,
                        "UTF-8",
                        this,
                        null,
                        null,
                        singleList(header.get("name")),
                        header.get("author"),
                        header.get("publisher"),
                        null,
                        null,
                        0.0f, 0.0f,
                        null,
                        null,
                        null,
                        null,
                        false,
                        new Date())};
            }
            throw new Parser.Failure("Unable to parse SID file, file does seems to be incomplete (len = " + available + ").", location);
        } catch (final IOException ex) {
            throw new Parser.Failure("Unable to read SID file header.", location, ex);
        }
    }

    /**
     *
     * @param header must contain at least the header of the SID file.
     * @return values parsed from the input data
     */
    private Map<String, String> parseHeader(final byte[] header) {
        final byte[] name = new byte[32];
        for (int i = 0; i < 32; i++) {
            name[i] = header[i + 16];
        }

        final byte[] author = new byte[32];
        for (int i = 0; i < 32; i++) {
            author[i] = header[i + 48];
        }

        final byte[] copyright = new byte[32];
        for (int i = 0; i < 32; i++) {
            copyright[i] = header[i + 80];
        }

        Map<String, String> ret = new HashMap<String, String>();

        ret.put("name", new String(name, Charset.forName("ISO-8859-1")).trim());
        ret.put("author", new String(author, Charset.forName("ISO-8859-1")).trim());
        ret.put("publisher", new String(copyright, Charset.forName("ISO-8859-1")).trim());

        return ret;
    }
}
