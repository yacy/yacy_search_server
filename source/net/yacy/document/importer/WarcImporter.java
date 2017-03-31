/**
 * WarcImporter.java
 * (C) 2017 by reger24; https://github.com/reger24
 *
 * This is a part of YaCy, a peer-to-peer based web search engine
 *
 * LICENSE
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package net.yacy.document.importer;

import java.io.IOException;
import java.io.InputStream;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.TextParser;
import net.yacy.search.Switchboard;
import net.yacy.server.http.ChunkedInputStream;
import org.jwat.common.HeaderLine;
import org.jwat.common.HttpHeader;
import org.jwat.warc.WarcConstants;
import org.jwat.warc.WarcReader;
import org.jwat.warc.WarcReaderFactory;
import org.jwat.warc.WarcRecord;

/**
 * Web Archive file format reader to process the warc archive content (responses)
 *
 * Warc format specification ISO 28500
 * https://archive.org/details/WARCISO28500Version1Latestdraft
 * http://bibnum.bnf.fr/WARC/WARC_ISO_28500_version1_latestdraft.pdf
 *
 * http://archive-access.sourceforge.net/warc/warc_file_format-0.9.html
 * http://archive-access.sourceforge.net/warc/
 */
public class WarcImporter {

    /**
     * Reads a Warc file and adds all contained responses to the index.
     * The reader automatically handles plain or gzip'd warc files
     *
     * @param f inputstream for the warc file
     * @throws IOException
     */
    public void indexWarcRecords(InputStream f) throws IOException {

        byte[] content;
        int cnt = 0;

        WarcReader localwarcReader = WarcReaderFactory.getReader(f);
        WarcRecord wrec = localwarcReader.getNextRecord();
        while (wrec != null) {

            HeaderLine hl = wrec.getHeader(WarcConstants.FN_WARC_TYPE);
            if (hl != null && hl.value.equals(WarcConstants.RT_RESPONSE)) { // filter responses

                hl = wrec.getHeader(WarcConstants.FN_WARC_TARGET_URI);
                DigestURL location = new DigestURL(hl.value);

                HttpHeader http = wrec.getHttpHeader();

                if (http != null && http.statusCode == 200) { // process http response header OK (status 200)

                    if (TextParser.supportsMime(http.contentType) == null) { // check availability of parser

                        InputStream istream = wrec.getPayloadContent();
                        hl = http.getHeader(HeaderFramework.TRANSFER_ENCODING);
                        if (hl != null && hl.value.contains("chunked")) {
                            // because chunked stream.read doesn't read source fully, make sure all chunks are read
                            istream = new ChunkedInputStream(istream);
                            final ByteBuffer bbuffer = new ByteBuffer();
                            int c;
                            while ((c = istream.read()) >= 0) {
                                bbuffer.append(c);
                            }
                            content = bbuffer.getBytes();
                        } else {
                            content = new byte[(int) http.getPayloadLength()];
                            istream.read(content, 0, content.length);
                        }
                        istream.close();

                        RequestHeader requestHeader = new RequestHeader();

                        ResponseHeader responseHeader = new ResponseHeader(http.statusCode);
                        for (HeaderLine hx : http.getHeaderList()) { // include all original response headers for parser
                            responseHeader.put(hx.name, hx.value);
                        }

                        final Request request = new Request(
                                null,
                                location,
                                requestHeader.referer() == null ? null : requestHeader.referer().hash(),
                                "warc",
                                responseHeader.lastModified(),
                                Switchboard.getSwitchboard().crawler.defaultRemoteProfile.handle(), // use remote profile (to index text & media, without writing to cache
                                0,
                                Switchboard.getSwitchboard().crawler.defaultRemoteProfile.timezoneOffset());

                        final Response response = new Response(
                                request,
                                requestHeader,
                                responseHeader,
                                Switchboard.getSwitchboard().crawler.defaultRemoteProfile,
                                false,
                                content
                        );

                        Switchboard.getSwitchboard().toIndexer(response);
                        cnt++;
                    }
                }
            }
            wrec = localwarcReader.getNextRecord();
        }
        localwarcReader.close();
        ConcurrentLog.info("WarcImporter", "Indexed " + cnt + " documents");
    }
}
