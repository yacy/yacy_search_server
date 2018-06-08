// StrictSizeLimitResponseInterceptor.java
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

/**
 * An HTTP response interceptor stricly limiting the amount of bytes fetched
 * from an HTTP response.
 */
public class StrictSizeLimitResponseInterceptor implements HttpResponseInterceptor {

	/** Maximum amount of bytes to fetch from the HTTP response body */
	private final long maxBytes;

	/**
	 * @param maxBytes
	 *            the maximum amount of bytes to fetch from the HTTP response body
	 * @throws IllegalArgumentException
	 *             when the maxBytes value is lower than zero
	 */
	public StrictSizeLimitResponseInterceptor(final long maxBytes) {
		if (maxBytes < 0) {
			throw new IllegalArgumentException("The maxBytes parameter must be greater or equals than zero");
		}
		this.maxBytes = maxBytes;
	}

	@Override
	public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
		final HttpEntity entity = response.getEntity();
		if (entity != null) {
			response.setEntity(new StrictSizeLimitEntityWrapper(entity, this.maxBytes));
		}

	}

}
