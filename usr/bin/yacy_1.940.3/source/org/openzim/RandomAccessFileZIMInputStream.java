/*
 * Copyright (C) 2011 Arunesh Mathur
 *
 * This file is a part of zimreader-java.
 *
 * zimreader-java is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3.0 as
 * published by the Free Software Foundation.
 *
 * zimreader-java is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with zimreader-java.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openzim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * This is an implementation of RandomAccessFile to ensure that it is an
 * InputStream as well, specifically designed for reading a ZIM file. Ad-Hoc
 * implementation, can be improved.
 *
 * @author Arunesh Mathur <aruneshmathur1990 at gmail.com>
 * @author Michael Christen
 *         bugfix to long parsing (return value was int),
 *         moved conditions for exceptions to asserts,
 *         refactoring and merge with Utilities
 */
public class RandomAccessFileZIMInputStream extends InputStream {

    private final RandomAccessFile mRAFReader;
    private long mMarked = -1;
    private final byte[] buffer2 = new byte[2];
    private final byte[] buffer4 = new byte[4];
    private final byte[] buffer8 = new byte[8];

    public RandomAccessFileZIMInputStream(final RandomAccessFile reader) {
        this.mRAFReader = reader;
    }

    public int readTwoLittleEndianBytesInt() throws IOException {
        this.mRAFReader.read(buffer2, 0, 2);
        return toTwoLittleEndianInteger(buffer2);
    }

    public int readFourLittleEndianBytesInt() throws IOException {
        this.mRAFReader.read(buffer4, 0, 4);
        return toFourLittleEndianInteger(buffer4);
    }

    public long readEightLittleEndianBytesLong() throws IOException {
        this.mRAFReader.read(buffer8, 0, 8);
        return toEightLittleEndianLong(buffer8);
    }

    private static int toTwoLittleEndianInteger(final byte[] buffer) {
        return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8));
    }

    public static int toFourLittleEndianInteger(final byte[] buffer) {
        return
              ((buffer[0] & 0xFF)        | ((buffer[1] & 0xFF) << 8)
            | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
    }

    public static int toFourLittleEndianInteger(final byte[] buffer, int pos) {
        return
              ((buffer[pos    ] & 0xFF)        | ((buffer[pos + 1] & 0xFF) << 8)
            | ((buffer[pos + 2] & 0xFF) << 16) | ((buffer[pos + 3] & 0xFF) << 24));
    }

    public static long toEightLittleEndianLong(final byte[] buffer) {
        return // cast to long required otherwise this is again an integer
              ((long)(buffer[0] & 0xFF)        | ((long)(buffer[1] & 0xFF) << 8)
            | ((long)(buffer[2] & 0xFF) << 16) | ((long)(buffer[3] & 0xFF) << 24)
            | ((long)(buffer[4] & 0xFF) << 32) | ((long)(buffer[5] & 0xFF) << 40)
            | ((long)(buffer[6] & 0xFF) << 48) | ((long)(buffer[7] & 0xFF) << 56));
    }

    public static long toEightLittleEndianLong(final byte[] buffer, int pos) {
        return // cast to long required otherwise this is again an integer
              ((long)(buffer[pos    ] & 0xFF)        | ((long)(buffer[pos + 1] & 0xFF) << 8)
            | ((long)(buffer[pos + 2] & 0xFF) << 16) | ((long)(buffer[pos + 3] & 0xFF) << 24)
            | ((long)(buffer[pos + 4] & 0xFF) << 32) | ((long)(buffer[pos + 5] & 0xFF) << 40)
            | ((long)(buffer[pos + 6] & 0xFF) << 48) | ((long)(buffer[pos + 7] & 0xFF) << 56));
    }

    public static void skipFully(final InputStream stream, final long bytes) throws IOException {
        for (long i = stream.skip(bytes); i < bytes; i += stream.skip(bytes - i));
    }

    // Reads characters from the current position into a String and stops when a
    // '\0' is encountered
    public String readZeroTerminatedString() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = this.mRAFReader.read()) != '\0' && b != -1) {
            buffer.write(b);
        }
        return buffer.toString("UTF-8");
    }

    @Override
    public int read() throws IOException {
        return this.mRAFReader.read();
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return this.mRAFReader.read(b, off, len);
    }

    public static byte[] readFully(final InputStream is, final int len) throws IOException {
        byte[] b = new byte[len];
        int c = 0;
        while (c < len) {
            c = c + is.read(b, c, len - c);
        }
        return b;
    }

    public static void readFully(final InputStream is, final byte[] b) throws IOException {
        int c = 0;
        while (c < b.length) {
            c = c + is.read(b, c, b.length - c);
        }
    }

    public RandomAccessFile getRandomAccessFile() {
        return this.mRAFReader;
    }

    public void seek(final long pos) throws IOException {
        this.mRAFReader.seek(pos);
    }

    public long getFilePointer() throws IOException {
        return this.mRAFReader.getFilePointer();
    }

    public void mark() throws IOException {
        this.mMarked = this.mRAFReader.getFilePointer();
    }

    @Override
    public void reset() throws IOException {
        if (this.mMarked == -1) {
            return;
        } else {
            this.mRAFReader.seek(this.mMarked);
            this.mMarked = -1;
        }
    }
}
