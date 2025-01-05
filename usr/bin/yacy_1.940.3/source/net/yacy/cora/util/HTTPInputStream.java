/**
 *  HTTPInputStream
 *  Copyright 2014 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 26.11.2014 on http://yacy.net
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

package net.yacy.cora.util;

import java.io.IOException;
import java.io.InputStream;

import net.yacy.cora.protocol.http.HTTPClient;

/**
 * A HTTP InputStream delegating to HTTPClient. Use it when streaming HTTP content to easily finish HTTP client when closing stream.
 * @author luc
 *
 */
public class HTTPInputStream extends InputStream {
	
	/** HTTP client */
	private HTTPClient httpClient;
	
	/** Encapsulated HTTP content stream */
	private InputStream contentStream;
	
	
	/**
	 * Constructs from a httpClient.
	 * @param httpClient a httpClient with accessible stream content.
	 * @throws IOException when content stream can not be open on httpClient
	 */
	public HTTPInputStream(HTTPClient httpClient) throws IOException {
		if(httpClient == null) {
			throw new IllegalArgumentException("httpClient is null");
		}
		this.httpClient = httpClient;
		this.contentStream = httpClient.getContentstream();
		if(this.contentStream == null) {
			throw new IOException("content stream is null");
		}
	}
	
	/**
	 * Close properly HTTP connection with httpClient
	 */
	@Override
	public void close() throws IOException {
		httpClient.close();
	}


	@Override
	public int read() throws IOException {
		return contentStream.read();
	}


	@Override
	public int hashCode() {
		return contentStream.hashCode();
	}

	@Override
	public int read(byte[] b) throws IOException {
		return contentStream.read(b);
	}

	@Override
	public boolean equals(Object obj) {
		return contentStream.equals(obj);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return contentStream.read(b, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		return contentStream.skip(n);
	}

	@Override
	public String toString() {
		return contentStream.toString();
	}

	@Override
	public int available() throws IOException {
		return contentStream.available();
	}

	@Override
	public synchronized void mark(int readlimit) {
		contentStream.mark(readlimit);
	}

	@Override
	public synchronized void reset() throws IOException {
		contentStream.reset();
	}

	@Override
	public boolean markSupported() {
		return contentStream.markSupported();
	}
	
	

}
