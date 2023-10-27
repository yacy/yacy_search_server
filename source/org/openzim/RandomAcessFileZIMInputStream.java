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

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * This is an implementation of RandomAccessFile to ensure that it is an
 * InputStream as well, specifically designed for reading a ZIM file. Ad-Hoc
 * implementation, can be improved.
 *
 * @author Arunesh Mathur <aruneshmathur1990 at gmail.com>
 */

public class RandomAcessFileZIMInputStream extends InputStream {

    private final RandomAccessFile mRAFReader;

    private long mMarked = -1;

    public RandomAcessFileZIMInputStream(final RandomAccessFile reader) {
        this.mRAFReader = reader;
    }

    // TODO: Remove the parameter buffer
    public int readTwoLittleEndianBytesValue(final byte[] buffer) throws IOException {
        if (buffer.length < 2) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            this.mRAFReader.read(buffer, 0, 2);
            return Utilities.toTwoLittleEndianInteger(buffer);
        }
    }

    // TODO: Remove the parameter buffer
    public int readFourLittleEndianBytesValue(final byte[] buffer) throws IOException {
        if (buffer.length < 4) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            this.mRAFReader.read(buffer, 0, 4);
            return Utilities.toFourLittleEndianInteger(buffer);
        }
    }

    // TODO: Remove the parameter buffer
    public int readEightLittleEndianBytesValue(final byte[] buffer)
            throws IOException {
        if (buffer.length < 8) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            this.mRAFReader.read(buffer, 0, 8);
            return Utilities.toEightLittleEndianInteger(buffer);
        }
    }

    // TODO: Remove the parameter buffer
    public int readSixteenLittleEndianBytesValue(final byte[] buffer)
            throws IOException {
        if (buffer.length < 16) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            this.mRAFReader.read(buffer, 0, 16);
            return Utilities.toSixteenLittleEndianInteger(buffer);
        }
    }

    // Reads characters from the current position into a String and stops when a
    // '\0' is encountered
    public String readString() throws IOException {
        final StringBuffer sb = new StringBuffer();
        /*
         * int i; byte[] buffer = new byte[100]; while (true) {
         * mRAFReader.read(buffer); for (i = 0; i < buffer.length; i++) { if
         * (buffer[i] == '\0') { break; } sb.append((char) buffer[i]); } if (i
         * != buffer.length) break; } return sb.toString();
         */
        int b;
        b = this.mRAFReader.read();
        while (b != '\0') {
            sb.append((char) b);
            b = this.mRAFReader.read();
        }
        return sb.toString();

    }

    @Override
    public int read() throws IOException {
        return this.mRAFReader.read();
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
