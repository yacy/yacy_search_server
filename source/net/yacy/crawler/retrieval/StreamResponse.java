// StreamResponse.java
// ---------------------------
// Copyright 2017 by luccioman; https://github.com/luccioman
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.document.VocabularyScraper;

/**
 * A crawler load response, holding content as a stream.
 */
public class StreamResponse {

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

	/**
	 * Parse and close the content stream and return the parsed documents when
	 * possible
	 * 
	 * @return the parsed documents or null when an error occurred
	 * @throws Parser.Failure
	 *             when no parser support the content
	 */
	public Document[] parse() throws Parser.Failure {
		final String supportError = TextParser.supports(this.response.url(),
				this.response.getResponseHeader() == null ? null : this.response.getResponseHeader().getContentType());
		if (supportError != null) {
			throw new Parser.Failure("no parser support:" + supportError, this.response.url());
		}
		try {
			return TextParser.parseSource(this.response.url(),
					this.response.getResponseHeader() == null ? null
							: this.response.getResponseHeader().getContentType(),
					this.response.getResponseHeader() == null ? StandardCharsets.UTF_8.name()
							: this.response.getResponseHeader().getCharacterEncoding(),
					new VocabularyScraper(), this.response.getRequest().timezoneOffset(),
					this.response.getRequest().depth(), this.response.size(), this.contentStream);
		} catch (final Exception e) {
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
