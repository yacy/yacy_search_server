// StreamResponse.java
// ---------------------------
// SPDX-FileCopyrightText: 2017 luccioman; https://github.com/luccioman
// SPDX-License-Identifier: GPL-2.0-or-later
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.crawler.retrieval;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;

/**
 * A crawler load response, holding content as a stream.
 */
public class StreamResponse implements Closeable {

	/** Logger */
	private final static ConcurrentLog log = new ConcurrentLog(StreamResponse.class.getSimpleName());

	/**
	 * Content as a stream.
	 */
	private InputStream contentStream;

	/**
	 * The response details, including notably the request and response headers.
	 */
	private Response response;

	/**
	 * @param response
	 *            contains the complete crawler response details
	 * @param contentStream
	 *            an open input stream on the response content
	 * @throws IllegalArgumentException
	 *             when response is null
	 */
	public StreamResponse(final Response response, final InputStream contentStream) {
		if (response == null) {
			throw new IllegalArgumentException("response parameter must not be null");
		}
		this.response = response;
		this.contentStream = contentStream;
	}

	/**
	 * @return the content stream. Don't forget to close it when processing is
	 *         terminated.
	 */
	public InputStream getContentStream() {
		return this.contentStream;
	}

	/**
	 * @return the crawler response with complete details
	 */
	public Response getResponse() {
		return this.response;
	}
	
	@Override
	public void close() throws IOException {
		if(this.contentStream != null) {
			this.contentStream.close();
		}
	}

	/**
	 * Parse and close the content stream and return the parsed documents when
	 * possible
	 * 
	 * @return the parsed documents or null when an error occurred
	 * @throws Parser.Failure
	 *             when no parser support the content
	 */
	public Document[] parse() throws Parser.Failure {
		return parseWithLimits(Integer.MAX_VALUE, Long.MAX_VALUE);
	}
	
	/**
	 * Parse and close the content stream and return the parsed documents when
	 * possible.<br>
	 * Try to limit the parser processing with a maximum total number of links
	 * detection (anchors, images links, media links...) or a maximum amount of
	 * content bytes to parse.<br>
	 * Limits apply only when the available parsers for the resource media type
	 * support parsing within limits (see
	 * {@link Parser#isParseWithLimitsSupported()}. When available parsers do
	 * not support parsing within limits, an exception is thrown when
	 * content size is beyond maxBytes.
	 * 
	 * @param maxLinks
	 *            the maximum total number of links to parse and add to the
	 *            result documents
	 * @param maxBytes
	 *            the maximum number of content bytes to process
	 * @return the parsed documents or null when an error occurred
	 * @throws Parser.Failure
	 *             when no parser support the content, or an error occurred while parsing
	 */
	public Document[] parseWithLimits(final int maxLinks, final long maxBytes) throws Parser.Failure {
		final String supportError = TextParser.supports(this.response.url(),
				this.response.getResponseHeader() == null ? null : this.response.getResponseHeader().getContentType());
		if (supportError != null) {
			throw new Parser.Failure("no parser support:" + supportError, this.response.url());
		}
		try {
			final String mimeType = this.response.getResponseHeader() == null ? null
					: this.response.getResponseHeader().getContentType();
			final String charsetName = this.response.getResponseHeader() == null ? StandardCharsets.UTF_8.name()
					: this.response.getResponseHeader().getCharacterEncoding();
			
			return TextParser.parseWithLimits(this.response.url(), mimeType, charsetName,
						this.response.getRequest().timezoneOffset(), this.response.getRequest().depth(),
						this.response.size(), this.contentStream, maxLinks, maxBytes);
		} catch(Parser.Failure e) {
			throw e;
		}catch (final Exception e) {
			return null;
		} finally {
			if (this.contentStream != null) {
				try {
					this.contentStream.close();
				} catch (IOException ignored) {
					log.warn("Could not close content stream on url " + this.response.url());
				}
			}
		}

	}

}
