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

/**
 * @author Arunesh Mathur
 *         A ZIM file implementation that stores the Header and the MIMETypeList
 *
 * @author Michael Christen
 *         int/long bugfix (did reading of long values with int variables, causing negative offsets)
 */
public class Utilities {

    public static int toTwoLittleEndianInteger(final byte[] buffer) throws IOException {
        if (buffer.length < 2) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            final int result =
                      ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8));
            return result;
        }
    }

    public static int toFourLittleEndianInteger(final byte[] buffer) throws IOException {
        if (buffer.length < 4) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            final int result =
                      ((buffer[0] & 0xFF)        | ((buffer[1] & 0xFF) << 8)
                    | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
            return result;
        }
    }

    public static long toEightLittleEndianLong(final byte[] buffer) throws IOException {
        if (buffer.length < 8) {
            throw new OutOfMemoryError("buffer too small");
        } else {
            final long result = // cast to long required otherwise this is again an integer
                      ((long)(buffer[0] & 0xFF)        | ((long)(buffer[1] & 0xFF) << 8)
                    | ((long)(buffer[2] & 0xFF) << 16) | ((long)(buffer[3] & 0xFF) << 24)
                    | ((long)(buffer[4] & 0xFF) << 32) | ((long)(buffer[5] & 0xFF) << 40)
                    | ((long)(buffer[6] & 0xFF) << 48) | ((long)(buffer[7] & 0xFF) << 56));
            return result;
        }
    }

    public static long toSixteenLittleEndianLong(final byte[] buffer) throws IOException {
        return toEightLittleEndianLong(buffer); // there are no sixten bytes long values
    }

    public static void skipFully(final InputStream stream, final long bytes) throws IOException {
            for (long i = stream.skip(bytes); i < bytes; i += stream.skip(bytes - i));
         }

}
