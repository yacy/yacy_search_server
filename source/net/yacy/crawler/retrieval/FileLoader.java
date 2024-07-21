/**
 *  FileLoader
 *  SPDX-FileCopyrightText: 2010 Michael Peter Christen <mc@yacy.net)>
 *  SPDX-License-Identifier: GPL-2.0-or-later
 *  First released 25.5.2010 at https://yacy.net
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

package net.yacy.crawler.retrieval;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.StrictLimitInputStream;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;

public class FileLoader {

	/** Default maximum file size allowed for the crawler */
    public static final int DEFAULT_MAXFILESIZE = 100000000;

    private final Switchboard sb;
    private final ConcurrentLog log;
    private final int maxFileSize;

    public FileLoader(final Switchboard sb, final ConcurrentLog log) {
        this.sb = sb;
        this.log = log;
        this.maxFileSize = sb.getConfigInt("crawler.file.maxFileSize", DEFAULT_MAXFILESIZE);
    }

    /**
     * Load fully the requested file in a byte buffer
     *
     * @param request the request to process
     * @param acceptOnlyParseable when true and no parser can be found to handle the detected MIME type, the response content buffer contains only URL tokens
     * @return a response with full meta data and embedding the content as a byte buffer
     */
    public Response load(final Request request, boolean acceptOnlyParseable) throws IOException {
        StreamResponse streamResponse = openInputStream(request, acceptOnlyParseable);

        /* Read fully the stream and update the response */
        byte[] content = FileUtils.read(streamResponse.getContentStream());
        Response response = streamResponse.getResponse();
        response.setContent(content);
        return response;
    }

    /**
     * Open a stream on the requested file. When actual file size is over maxBytes, return a stream on metadata only (URL tokens).
     *
     * @param request the request to process
     * @param acceptOnlyParseable when true and no parser can be found to handle the detected MIME type, open a stream on the URL tokens
     * @param maxBytes max file size to load. -1 means no limit.
     * @return a response with full meta data and embedding on open input stream on content. Don't forget to close the stream.
     */
    public StreamResponse openInputStream(final Request request, final boolean acceptOnlyParseable, final int maxBytes) throws IOException {
        DigestURL url = request.url();
        if (!url.getProtocol().equals("file")) throw new IOException("wrong protocol for FileLoader: " + url.getProtocol());

        RequestHeader requestHeader = null;
        if (request.referrerhash() != null) {
            String ur = this.sb.getURL(request.referrerhash());
            if (ur != null) {
                requestHeader = new RequestHeader();
                requestHeader.put(RequestHeader.REFERER, ur);
            }
        }

        // process directories: transform them to html with meta robots=noindex (using the ftpc lib)
        String[] l = null;
        try {l = url.list();} catch (final IOException e) {}
        if (l != null) {
            String u = url.toNormalform(true);
            List<String> list = new ArrayList<String>();
            for (String s: l) {
                list.add(u + ((u.endsWith("/") || u.endsWith("\\")) ? "" : "/") + s);
            }

            StringBuilder content = FTPClient.dirhtml(u, null, null, null, list, true);

            ResponseHeader responseHeader = new ResponseHeader(200);
            responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(new Date(url.lastModified())));
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/html");
            final CrawlProfile profile = this.sb.crawler.get(ASCII.getBytes(request.profileHandle()));
            Response response = new Response(
                    request,
                    requestHeader,
                    responseHeader,
                    profile,
                    false,
                    null);

            return new StreamResponse(response, new ByteArrayInputStream(UTF8.getBytes(content.toString())));
        }

        // create response header
        String mime = Classification.ext2mime(MultiProtocolURL.getFileExtension(url.getFileName()));
        ResponseHeader responseHeader = new ResponseHeader(200);
        responseHeader.put(HeaderFramework.LAST_MODIFIED, HeaderFramework.formatRFC1123(new Date(url.lastModified())));
        responseHeader.put(HeaderFramework.CONTENT_TYPE, mime);

        // check mime type and availability of parsers
        // and also check resource size and limitation of the size
        long size;
        try {
            size = url.length();
            responseHeader.put(HeaderFramework.CONTENT_LENGTH, Long.toString(size));
        } catch (final Exception e) {
            size = -1;
        }
        String parserError = null;
        if ((acceptOnlyParseable && (parserError = TextParser.supports(url, mime)) != null) ||
            (size > maxBytes && maxBytes >= 0)) {
            // we know that we cannot process that file before loading
            // only the metadata is returned

            if (parserError != null) {
                this.log.info("No parser available in File crawler: '" + parserError + "' for URL " + request.url().toNormalform(false) + ": parsing only metadata");
            } else {
                this.log.info("Too big file in File crawler with size = " + size + " Bytes for URL " + request.url().toNormalform(false) + ": parsing only metadata");
            }

            // create response with metadata only
            responseHeader.put(HeaderFramework.CONTENT_TYPE, "text/plain");
            final CrawlProfile profile = this.sb.crawler.get(ASCII.getBytes(request.profileHandle()));
            Response response = new Response(
                    request,
                    requestHeader,
                    responseHeader,
                    profile,
                    false,
                    null);
            return new StreamResponse(response, new ByteArrayInputStream(UTF8.getBytes(url.toTokens())));
        }

        // load the resource
        InputStream is = url.getInputStream(ClientIdentification.yacyInternetCrawlerAgent);

        if(size < 0 && maxBytes >= 0) {
			/* If content length is unknown for some reason, let's apply now the eventual size restriction */
			is = new StrictLimitInputStream(is, maxBytes,
					"Too big file in File crawler for URL " + request.url().toString());
        }

        // create response with stream open on content
        final CrawlProfile profile = this.sb.crawler.get(ASCII.getBytes(request.profileHandle()));
        Response response = new Response(
                request,
                requestHeader,
                responseHeader,
                profile,
                false,
                null);
        return new StreamResponse(response, is);
    }

    /**
     * Open a stream on the requested file
     *
     * @param request the request to process
     * @param acceptOnlyParseable when true and no parser can be found to handle the detected MIME type, open a stream on the URL tokens
     * @return a response with full meta data and embedding on open input stream on content. Don't forget to close the stream.
     */
    public StreamResponse openInputStream(final Request request, final boolean acceptOnlyParseable) throws IOException {
    	return openInputStream(request, acceptOnlyParseable,  this.maxFileSize);
    }
}
