/* ====================================================================
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
 * to work for the YaCy http server when http client library use was migrated
 * to apache http components 4.0
 * by Michael Christen, 20.09.2010
 */


package net.yacy.server.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;


/**
 * <p>Transparently coalesces chunks of a HTTP stream that uses
 * Transfer-Encoding chunked.</p>
 *
 * <p>Note that this class NEVER closes the underlying stream, even when close
 * gets called.  Instead, it will read until the "end" of its chunking on close,
 * which allows for the seamless invocation of subsequent HTTP 1.1 calls, while
 * not requiring the client to remember to read the entire contents of the
 * response.</p>
 *
 * @author Ortwin Glueck
 * @author Sean C. Sullivan
 * @author Martin Elwin
 * @author Eric Johnson
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author Michael Becke
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 *
 * @since 2.0
 *
 */
public class ChunkedInputStream extends InputStream {
    /** The inputstream that we're wrapping */
    private final InputStream in;

    /** The chunk size */
    private int chunkSize;

    /** The current position within the current chunk */
    private int pos;

    /** True if we'are at the beginning of stream */
    private boolean bof = true;

    /** True if we've reached the end of stream */
    private boolean eof = false;

    /** True if this stream is closed */
    private boolean closed = false;

    /**
     * ChunkedInputStream constructor
     *
     * @param in the raw input stream
     *
     * @throws IOException If an IO error occurs
     */
    public ChunkedInputStream(final InputStream in) throws IOException {

        if (in == null) {
            throw new IllegalArgumentException("InputStream parameter may not be null");
        }
        this.in = in;
        this.pos = 0;
    }


    /**
     * <p> Returns all the data in a chunked stream in coalesced form. A chunk
     * is followed by a CRLF. The method returns -1 as soon as a chunksize of 0
     * is detected.</p>
     *
     * <p> Trailer headers are read automcatically at the end of the stream and
     * can be obtained with the getResponseFooters() method.</p>
     *
     * @return -1 of the end of the stream has been reached or the next data
     * byte
     * @throws IOException If an IO problem occurs
     *
     * @see HttpMethod#getResponseFooters()
     */
    @Override
    public int read() throws IOException {

        if (this.closed) {
            throw new IOException("Attempted read from closed stream.");
        }
        if (this.eof) {
            return -1;
        }
        if (this.pos >= this.chunkSize) {
            nextChunk();
            if (this.eof) {
                return -1;
            }
        }
        this.pos++;
        return this.in.read();
    }

    /**
     * Read some bytes from the stream.
     * @param b The byte array that will hold the contents from the stream.
     * @param off The offset into the byte array at which bytes will start to be
     * placed.
     * @param len the maximum number of bytes that can be returned.
     * @return The number of bytes returned or -1 if the end of stream has been
     * reached.
     * @see java.io.InputStream#read(byte[], int, int)
     * @throws IOException if an IO problem occurs.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {

        if (this.closed) throw new IOException("Attempted read from closed stream.");
        if (this.eof) return -1;

        if (this.pos >= this.chunkSize) {
            nextChunk();
            if (this.eof) {
                return -1;
            }
        }
        len = Math.min(len, this.chunkSize - this.pos);
        int count = this.in.read(b, off, len);
        this.pos += count;
        return count;
    }

    /**
     * Read some bytes from the stream.
     * @param b The byte array that will hold the contents from the stream.
     * @return The number of bytes returned or -1 if the end of stream has been
     * reached.
     * @see java.io.InputStream#read(byte[])
     * @throws IOException if an IO problem occurs.
     */
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Read the CRLF terminator.
     * @throws IOException If an IO error occurs.
     */
    private void readCRLF() throws IOException {
        int cr = this.in.read();
        if (cr != '\r') throw new IOException("CRLF expected at end of chunk: cr != " + cr);
        int lf = this.in.read();
        if (lf != '\n') throw new IOException("CRLF expected at end of chunk: lf != " + lf);
    }


    /**
     * Read the next chunk.
     * @throws IOException If an IO error occurs.
     */
    private void nextChunk() throws IOException {
        if (!this.bof) readCRLF();
        this.chunkSize = getChunkSizeFromInputStream(this.in);
        this.bof = false;
        this.pos = 0;
        if (this.chunkSize == 0) {
            this.eof = true;
            skipTrailerHeaders();
        }
    }

    /**
     * Expects the stream to start with a chunksize in hex with optional
     * comments after a semicolon. The line must end with a CRLF: "a3; some
     * comment\r\n" Positions the stream at the start of the next line.
     *
     * @param in The new input stream.
     * @param required <tt>true<tt/> if a valid chunk must be present,
     *                 <tt>false<tt/> otherwise.
     *
     * @return the chunk size as integer
     *
     * @throws IOException when the chunk size could not be parsed
     */
    private static int getChunkSizeFromInputStream(final InputStream in)
      throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // States: 0=normal, 1=\r was scanned, 2=inside quoted string, -1=end
        int state = 0;
        while (state != -1) {
        int b = in.read();
            if (b == -1) {
                throw new IOException("chunked stream ended unexpectedly");
            }
            switch (state) {
                case 0:
                    switch (b) {
                        case '\r':
                            state = 1;
                            break;
                        case '\"':
                            state = 2;
                            baos.write(b);
                            break;
                        default:
                            baos.write(b);
                    }
                    break;

                case 1:
                    if (b == '\n') {
                        state = -1;
                    } else {
                        // this was not CRLF
                        throw new IOException("Protocol violation: Unexpected"
                            + " single newline character in chunk size");
                    }
                    break;

                case 2:
                    switch (b) {
                        case '\\':
                            b = in.read();
                            baos.write(b);
                            break;
                        case '\"':
                            state = 0;
                            baos.write(b);
                            break;
                        default:
                            baos.write(b);
                    }
                    break;
                default: throw new RuntimeException("assertion failed");
            }
        }

        //parse data
        String dataString = getAsciiString(baos.toByteArray());
        int separator = dataString.indexOf(';');
        dataString = (separator > 0)
            ? dataString.substring(0, separator).trim()
            : dataString.trim();

        int result;
        try {
            result = Integer.parseInt(dataString.trim(), 16);
        } catch (final NumberFormatException e) {
            throw new IOException ("Bad chunk size: " + dataString);
        }
        return result;
    }


    /**
     * Converts the byte array of ASCII characters to a string. This method is
     * to be used when decoding content of HTTP elements (such as response
     * headers)
     *
     * @param data the byte array to be encoded
     * @return The string representation of the byte array
     *
     * @since 3.0
     */
    private static String getAsciiString(final byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }

        try {
            return new String(data, 0, data.length, "US-ASCII");
        } catch (final UnsupportedEncodingException e) {
            throw new IOException("HttpClient requires ASCII support");
        }
    }

    /**
     * Reads and stores the Trailer headers.
     * @throws IOException If an IO problem occurs
     */
    private void skipTrailerHeaders() throws IOException {
        for (; ;) {
            String line = readLine(this.in, "US-ASCII");
            if ((line == null) || (line.trim().length() < 1)) break;
        }
    }


    /**
     * Read up to <tt>"\n"</tt> from an (unchunked) input stream.
     * If the stream ends before the line terminator is found,
     * the last part of the string will still be returned.
     * If no input data available, <code>null</code> is returned.
     *
     * @param inputStream the stream to read from
     * @param charset charset of HTTP protocol elements
     *
     * @throws IOException if an I/O problem occurs
     * @return a line from the stream
     *
     * @since 3.0
     */
    private static String readLine(InputStream inputStream, String charset) throws IOException {
        byte[] rawdata = readRawLine(inputStream);
        if (rawdata == null) {
            return null;
        }
        // strip CR and LF from the end
        int len = rawdata.length;
        int offset = 0;
        if (len > 0) {
            if (rawdata[len - 1] == '\n') {
                offset++;
                if (len > 1) {
                    if (rawdata[len - 2] == '\r') {
                        offset++;
                    }
                }
            }
        }
        final String result = getString(rawdata, 0, len - offset, charset);
        return result;
    }


    /**
     * Converts the byte array of HTTP content characters to a string. If
     * the specified charset is not supported, default system encoding
     * is used.
     *
     * @param data the byte array to be encoded
     * @param offset the index of the first byte to encode
     * @param length the number of bytes to encode
     * @param charset the desired character encoding
     * @return The result of the conversion.
     *
     * @since 3.0
     */
    private static String getString(
        final byte[] data,
        int offset,
        int length,
        String charset
    ) {

        if (data == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }

        if (charset == null || charset.isEmpty()) {
            throw new IllegalArgumentException("charset may not be null or empty");
        }

        try {
            return new String(data, offset, length, charset);
        } catch (final UnsupportedEncodingException e) {
            return new String(data, offset, length);
        }
    }

    /**
     * Return byte array from an (unchunked) input stream.
     * Stop reading when <tt>"\n"</tt> terminator encountered
     * If the stream ends before the line terminator is found,
     * the last part of the string will still be returned.
     * If no input data available, <code>null</code> is returned.
     *
     * @param inputStream the stream to read from
     *
     * @throws IOException if an I/O problem occurs
     * @return a byte array from the stream
     */
    private static byte[] readRawLine(InputStream inputStream) throws IOException {

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ch;
        while ((ch = inputStream.read()) >= 0) {
            buf.write(ch);
            if (ch == '\n') { // be tolerant (RFC-2616 Section 19.3)
                break;
            }
        }
        if (buf.size() == 0) {
            return null;
        }
        return buf.toByteArray();
    }

    /**
     * Upon close, this reads the remainder of the chunked message,
     * leaving the underlying socket at a position to start reading the
     * next response without scanning.
     * @throws IOException If an IO problem occurs.
     */
    @Override
    public synchronized void close() throws IOException {
        if (!this.closed) {
            try {
                if (!this.eof) {
                    exhaustInputStream(this);
                }
            } finally {
                this.eof = true;
                this.closed = true;
            }
        }
    }

    /**
     * Exhaust an input stream, reading until EOF has been encountered.
     *
     * <p>Note that this function is intended as a non-public utility.
     * This is a little weird, but it seemed silly to make a utility
     * class for this one function, so instead it is just static and
     * shared that way.</p>
     *
     * @param inStream The {@link InputStream} to exhaust.
     * @throws IOException If an IO problem occurs
     */
    static void exhaustInputStream(InputStream inStream) throws IOException {
        // read and discard the remainder of the message
        byte buffer[] = new byte[1024];
        while (inStream.read(buffer) >= 0) {
        }
    }
}
