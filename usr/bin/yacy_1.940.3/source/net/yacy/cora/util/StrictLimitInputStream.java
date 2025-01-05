// StrictLimitInputStream.java
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

package net.yacy.cora.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.yacy.kelondro.util.Formatter;

/**
 * Strictly limit the number of bytes consumed on a wrapped input stream :
 * doesn't allow exceeding the limit and throw an exception when it is reached.
 * See also some alternatives to consider :
 * <ul>
 * <li>org.apache.commons.fileupload.util.LimitedInputStream : check the limit
 * only after reading, thus eventually allowing more bytes than the limit to be
 * read. Doesn't properly implement mark() and reset() (when resetting, total
 * count of consumed bytes is not reset)</li>
 * <li>com.google.common.io.ByteStreams.LimitedInputStream : doesn't throw an
 * exception on read() when the limit has been reached</li>
 * <li>org.apache.commons.io.input.BoundedInputStream : doesn't throw an
 * exception on read() when the limit has been reached</li>
 * </ul>
 * 
 * @author luccioman
 */
public class StrictLimitInputStream extends FilterInputStream {

	/**
	 * Strict maximum bytes amount to consume on the wrapped stream. An
	 * exception is raised once consumed bytes is exactly equals to this value.
	 */
	private final long maxBytes;

	/** The current position in the wrapped stream */
	private long position = 0;

	/** The marked position */
	private long mark = -1;

	/**
	 * The error message to use when a StreamLimitException is eventually raised
	 */
	private final String limitErrorMessage;

	/**
	 * Wrap the given input stream and limit read bytes to maxBytes.
	 *
	 * @param inStream
	 *            the input stream to wrap. Must not be null.
	 * @param maxBytes
	 *            the maximum number of bytes to consume on the inStream. Must
	 *            be greater or equals than zero.
	 * @throws IllegalArgumentException
	 *             when inStream is null, or maxBytes is lower than zero
	 */
	public StrictLimitInputStream(final InputStream inStream, final long maxBytes) {
		this(inStream, maxBytes, Formatter.bytesToString(maxBytes) + " limit has been reached");
	}

	/**
	 * Wrap the given input stream and limit read bytes to maxBytes.
	 *
	 * @param inStream
	 *            the input stream to wrap. Must not be null.
	 * @param maxBytes
	 *            the maximum number of bytes to consume on the inStream. Must
	 *            be greater or equals than zero.
	 * @param limitErrorMessage
	 *            the custom error message to use when a StreamLimitException is
	 *            eventually raised. May be null.
	 * @throws IllegalArgumentException
	 *             when inStream is null, or maxBytes is lower than zero
	 */
	public StrictLimitInputStream(final InputStream inStream, final long maxBytes, final String limitErrorMessage) {
		super(inStream);
		if (inStream == null) {
			throw new IllegalArgumentException("inStream parameter must not be null");
		}
		if (maxBytes < 0) {
			throw new IllegalArgumentException("maxBytes parameter must be greater or equals to zero");
		}
		this.maxBytes = maxBytes;
		this.limitErrorMessage = limitErrorMessage;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws StreamLimitException
	 *             when the maxBytes limit has been reached
	 * @throws IOException
	 *             when an I/O error occurs
	 */
	@Override
	public int read() throws IOException {
		if (this.position >= this.maxBytes) {
			throw new StreamLimitException(this.limitErrorMessage);
		}
		final int result = this.in.read();
		this.position++;
		return result;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws StreamLimitException
	 *             when the maxBytes limit has been reached
	 */
	@Override
	public int read(final byte[] b) throws IOException {
		return this.read(b, 0, b.length);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws StreamLimitException
	 *             when the maxBytes limit has been reached
	 */
	@Override
	public int read(final byte[] b, final int off, final int len) throws IOException, StreamLimitException {
		if (this.position >= this.maxBytes) {
			throw new StreamLimitException(this.limitErrorMessage);
		}
		final long maxToRead = Math.min(len, this.maxBytes - this.position);
		final int nbRead = this.in.read(b, off, (int) maxToRead);

		if (nbRead > 0) {
			this.position += nbRead;
		}
		return nbRead;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @throws StreamLimitException
	 *             when the maxBytes limit has been reached
	 */
	@Override
	public long skip(final long n) throws IOException {
		if (this.position >= this.maxBytes) {
			throw new StreamLimitException(this.limitErrorMessage);
		}
		final long toSkip = Math.min(n, this.maxBytes - this.position);
		final long nbSkipped = this.in.skip(toSkip);
		this.position += nbSkipped;
		return nbSkipped;
	}

	/* We do not override available() even when position has reached maxBytes : limit
	   reached must be signaled to the caller trough a StreamLimitException
	   when reading */

	@Override
	public synchronized void reset() throws IOException {
		this.in.reset();
		/*
		 * Rely on the wrapped input stream to check and throw an exception if
		 * the mark is invalid
		 */
		this.position = this.mark;
	}

	@Override
	public synchronized void mark(final int readlimit) {
		this.in.mark(readlimit);
		this.mark = this.position;
	}
}
