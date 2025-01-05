/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

/*
 * This file was imported from apache http client 3.1 library and modified
 * to work for the YaCy http server when htto client library use was migrated
 * to apache http components 4.0
 * by Michael Christen, 20.09.2010
 */

package net.yacy.server.http;

import java.io.IOException;
import java.io.InputStream;

/**
 * Cuts the wrapped InputStream off after a specified number of bytes.
 *
 * <p>Implementation note: Choices abound. One approach would pass
 * through the {@link InputStream#mark} and {@link InputStream#reset} calls to
 * the underlying stream.  That's tricky, though, because you then have to
 * start duplicating the work of keeping track of how much a reset rewinds.
 * Further, you have to watch out for the "readLimit", and since the semantics
 * for the readLimit leave room for differing implementations, you might get
 * into a lot of trouble.</p>
 *
 * <p>Alternatively, you could make this class extend {@link java.io.BufferedInputStream}
 * and then use the protected members of that class to avoid duplicated effort.
 * That solution has the side effect of adding yet another possible layer of
 * buffering.</p>
 *
 * <p>Then, there is the simple choice, which this takes - simply don't
 * support {@link InputStream#mark} and {@link InputStream#reset}.  That choice
 * has the added benefit of keeping this class very simple.</p>
 *
 * @author Ortwin Glueck
 * @author Eric Johnson
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @since 2.0
 */
public class ContentLengthInputStream extends InputStream {

    /**
     * The maximum number of bytes that can be read from the stream. Subsequent
     * read operations will return -1.
     */
    private final long contentLength;

    /** The current position */
    private long pos = 0;

    /** True if the stream is closed. */
    private boolean closed = false;

    /**
     * Wrapped input stream that all calls are delegated to.
     */
    private InputStream wrappedStream = null;

    /**
     * Creates a new length limited stream
     *
     * @param in The stream to wrap
     * @param contentLength The maximum number of bytes that can be read from
     * the stream. Subsequent read operations will return -1.
     *
     * @since 3.0
     */
    public ContentLengthInputStream(InputStream in, long contentLength) {
        super();
        this.wrappedStream = in;
        this.contentLength = contentLength;
    }

    /**
     * <p>Reads until the end of the known length of content.</p>
     *
     * <p>Does not close the underlying socket input, but instead leaves it
     * primed to parse the next response.</p>
     * @throws IOException If an IO problem occurs.
     */
    @Override
    public synchronized void close() throws IOException {
        if (!this.closed) {
            try {
                ChunkedInputStream.exhaustInputStream(this);
            } finally {
                // close after above so that we don't throw an exception trying
                // to read after closed!
                this.closed = true;
            }
        }
    }


    /**
     * Read the next byte from the stream
     * @return The next byte or -1 if the end of stream has been reached.
     * @throws IOException If an IO problem occurs
     * @see java.io.InputStream#read()
     */
    @Override
    public int read() throws IOException {
        if (this.closed) {
            throw new IOException("Attempted read from closed stream.");
        }

        if (this.pos >= this.contentLength) {
            return -1;
        }
        this.pos++;
        return this.wrappedStream.read();
    }

    /**
     * Does standard {@link InputStream#read(byte[], int, int)} behavior, but
     * also notifies the watcher when the contents have been consumed.
     *
     * @param b     The byte array to fill.
     * @param off   Start filling at this position.
     * @param len   The number of bytes to attempt to read.
     * @return The number of bytes read, or -1 if the end of content has been
     *  reached.
     *
     * @throws java.io.IOException Should an error occur on the wrapped stream.
     */
    @Override
    public int read (byte[] b, int off, int len) throws java.io.IOException {
        if (this.closed) {
            throw new IOException("Attempted read from closed stream.");
        }

        if (this.pos >= this.contentLength) {
            return -1;
        }

        if (this.pos + len > this.contentLength) {
            len = (int) (this.contentLength - this.pos);
        }
        int count = this.wrappedStream.read(b, off, len);
        this.pos += count;
        return count;
    }


    /**
     * Read more bytes from the stream.
     * @param b The byte array to put the new data in.
     * @return The number of bytes read into the buffer.
     * @throws IOException If an IO problem occurs
     * @see java.io.InputStream#read(byte[])
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Skips and discards a number of bytes from the input stream.
     * @param n The number of bytes to skip.
     * @return The actual number of bytes skipped. <= 0 if no bytes
     * are skipped.
     * @throws IOException If an error occurs while skipping bytes.
     * @see InputStream#skip(long)
     */
    @Override
    public long skip(long n) throws IOException {
        // make sure we don't skip more bytes than are
        // still available
        long length = Math.min(n, this.contentLength - this.pos);
        // skip and keep track of the bytes actually skipped
        length = this.wrappedStream.skip(length);
        // only add the skipped bytes to the current position
        // if bytes were actually skipped
        if (length > 0) {
            this.pos += length;
        }
        return length;
    }

    @Override
    public int available() throws IOException {
        if (this.closed) {
            return 0;
        }
        int avail = this.wrappedStream.available();
        if (this.pos + avail > this.contentLength ) {
            avail = (int)(this.contentLength - this.pos);
        }
        return avail;
    }

}
