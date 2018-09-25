// StrictSizeLimitEntityWrapper.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
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

package net.yacy.cora.protocol.http;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import net.yacy.cora.util.StrictLimitInputStream;

/**
 * HTTP entity wrapper used to strictly limit the size of the response content
 * fetched from an http connection.
 *
 */
public class StrictSizeLimitEntityWrapper extends HttpEntityWrapper {

	/** Reusable wrapped content stream */
	private InputStream content;

	/** Maximum amount of bytes to fetch from the http response body */
	private final long maxBytes;

	/**
	 * @param wrappedEntity
	 *            the http entity to wrap. Must not be null.
	 * @param maxBytes
	 *            the maximum amount of bytes to fetch from the http response body
	 * @throws IllegalArgumentException
	 *             when wrappedEntity parameter is null or when maxBytes value is
	 *             lower than zero.
	 */
	public StrictSizeLimitEntityWrapper(final HttpEntity wrappedEntity, final long maxBytes) {
		super(wrappedEntity);
		if (wrappedEntity == null) {
			throw new IllegalArgumentException("The wrappedEntity parameter must not be null.");
		}
		if (maxBytes < 0) {
			throw new IllegalArgumentException("The maxBytes parameter must be greater or equal than zero.");
		}
		this.maxBytes = maxBytes;
	}

	/**
	 * @return a wrapper on the wrapped entity content stream
	 * @throws IOException
	 *             when an error occurred while accessing the wrapped stream
	 */
	private InputStream getWrappedStream() throws IOException {
		final InputStream in = this.wrappedEntity.getContent();
		if (in == null) {
			return in;
		}

		return new StrictLimitInputStream(in, this.maxBytes);
	}

	@Override
	public InputStream getContent() throws IOException {
		final InputStream result;
		if (this.content == null) {
			this.content = this.getWrappedStream();
			result = this.content;
		} else {
			result = this.content;
		}
		return result;
	}

}