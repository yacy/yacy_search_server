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

public class Utilities {

    // TODO: Write a binary search algorithm
    public static int binarySearch() {
        return -1;
    }

    public static int toTwoLittleEndianInteger(final byte[] buffer) throws IOException {
        if (buffer.length < 2) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            final int result = ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8));
            return result;
        }
    }

    public static int toFourLittleEndianInteger(final byte[] buffer) throws IOException {
        if (buffer.length < 4) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            final int result = ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
                    | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
            return result;
        }
    }

    public static int toEightLittleEndianInteger(final byte[] buffer) throws IOException {
        if (buffer.length < 8) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            final int result = ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
                    | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24)
                    | ((buffer[4] & 0xFF) << 32) | ((buffer[5] & 0xFF) << 40)
                    | ((buffer[6] & 0xFF) << 48) | ((buffer[7] & 0xFF) << 56));
            return result;
        }
    }

    public static int toSixteenLittleEndianInteger(final byte[] buffer) throws IOException {
        if (buffer.length < 16) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            final int result = ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
                    | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24)
                    | ((buffer[4] & 0xFF) << 32) | ((buffer[5] & 0xFF) << 40)
                    | ((buffer[6] & 0xFF) << 48) | ((buffer[7] & 0xFF) << 56)
                    | ((buffer[8] & 0xFF) << 64) | ((buffer[9] & 0xFF) << 72)
                    | ((buffer[10] & 0xFF) << 80) | ((buffer[11] & 0xFF) << 88)
                    | ((buffer[12] & 0xFF) << 96)
                    | ((buffer[13] & 0xFF) << 104)
                    | ((buffer[14] & 0xFF) << 112) | ((buffer[15] & 0xFF) << 120));
            return result;
        }
    }

    public static void skipFully(final InputStream stream, final long bytes) throws IOException {
            for (long i = stream.skip(bytes); i < bytes; i += stream.skip(bytes - i));
         }

}
