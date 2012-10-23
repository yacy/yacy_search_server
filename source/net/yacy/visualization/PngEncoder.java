/**
 * PngEncoder takes a Java Image object and creates a byte string which can be saved as a PNG file.
 * The Image is presumed to use the DirectColorModel.
 *
 * <p>Thanks to Jay Denny at KeyPoint Software
 *    http://www.keypoint.com/
 * who let me develop this code on company time.</p>
 *
 * <p>You may contact me with (probably very-much-needed) improvements,
 * comments, and bug fixes at:</p>
 *
 *   <p><code>david@catcode.com</code></p>
 *
 * <p>This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.</p>
 *
 * <p>This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.</p>
 *
 * <p>You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * A copy of the GNU LGPL may be found at
 * <code>http://www.gnu.org/copyleft/lesser.html</code></p>
 *
 * @author J. David Eisenberg
 * @version 1.5, 19 Oct 2003
 *
 * CHANGES:
 * --------
 * 19-Nov-2002 : CODING STYLE CHANGES ONLY (by David Gilbert for Object Refinery Limited);
 * 19-Sep-2003 : Fix for platforms using EBCDIC (contributed by Paulo Soares);
 * 19-Oct-2003 : Change private fields to private fields so that
 *               PngEncoderB can inherit them (JDE)
 *				 Fixed bug with calculation of nRows
 * 23.10.2012
 * For the integration into YaCy this class was adopted to YaCy graphics by Michael Christen:
 * - removed alpha encoding
 * - removed not used code
 * - inlined static values
 * - inlined all methods that had been called only once
 * - moved class objects which appear after all refactoring only within a single method into this method
 * - removed a giant number of useless (obvious things) comments and empty lines to increase readability (!)
 */

package net.yacy.visualization;

import java.awt.Image;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class PngEncoder extends Object {
    
    private static final byte IHDR[] = {73, 72, 68, 82};
    private static final byte IDAT[] = {73, 68, 65, 84};
    private static final byte IEND[] = {73, 69, 78, 68};
    private byte[] pngBytes;
    private Image image;
    private int maxPos;

    public PngEncoder(Image image) {
        this.image = image;
    }

    public byte[] pngEncode(int compressionLevel) {
        if (image == null) return null;
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        pngBytes = new byte[((width + 1) * height * 3) + 200];
        maxPos = 0;
        int bytePos = writeBytes(new byte[]{-119, 80, 78, 71, 13, 10, 26, 10}, 0);
        int startPos;
        startPos = bytePos = writeInt4(13, bytePos);
        bytePos = writeBytes(IHDR, bytePos);
        width = image.getWidth(null);
        height = image.getHeight(null);
        bytePos = writeInt4(width, bytePos);
        bytePos = writeInt4(height, bytePos);
        bytePos = writeBytes(new byte[]{8, 2, 0, 0, 0}, bytePos);
        CRC32 crc = new CRC32();
        crc.reset();
        crc.update(pngBytes, startPos, bytePos - startPos);
        bytePos = writeInt4((int) crc.getValue(), bytePos);
        try {
            int rowsLeft = height;  // number of rows remaining to write
            int startRow = 0;       // starting row to process this time through
            int nRows;              // how many rows to grab at a time
            byte[] scanLines;       // the scan lines to be compressed
            int scanPos;            // where we are in the scan lines
            byte[] compressedLines; // the resultant compressed lines
            int nCompressed;        // how big is the compressed area?
            PixelGrabber pg;
            Deflater scrunch = new Deflater(compressionLevel);
            ByteArrayOutputStream outBytes = new ByteArrayOutputStream(1024);
            DeflaterOutputStream compBytes = new DeflaterOutputStream(outBytes, scrunch);
            while (rowsLeft > 0) {
                nRows = Math.min(32767 / (width * 4), rowsLeft);
                nRows = Math.max(nRows, 1);
                int[] pixels = new int[width * nRows];
                pg = new PixelGrabber(image, 0, startRow, width, nRows, pixels, 0, width);
                try {pg.grabPixels();} catch (InterruptedException e) {throw new IOException("interrupted waiting for pixels!");}
                if ((pg.getStatus() & ImageObserver.ABORT) != 0) throw new IOException("image fetch aborted or errored");
                scanLines = new byte[width * nRows * 3 + nRows];            
                scanPos = 0;
                for (int i = 0; i < width * nRows; i++) {
                    if (i % width == 0) scanLines[scanPos++] = (byte) 0;
                    scanLines[scanPos++] = (byte) ((pixels[i] >> 16) & 0xff);
                    scanLines[scanPos++] = (byte) ((pixels[i] >>  8) & 0xff);
                    scanLines[scanPos++] = (byte) ((pixels[i]) & 0xff);
                }
                compBytes.write(scanLines, 0, scanPos);
                startRow += nRows;
                rowsLeft -= nRows;
            }
            compBytes.close();
            compressedLines = outBytes.toByteArray();
            nCompressed = compressedLines.length;
            crc.reset();
            bytePos = writeInt4(nCompressed, bytePos);
            bytePos = writeBytes(IDAT, bytePos);
            crc.update(IDAT);
            maxPos = Math.max(maxPos, bytePos + nCompressed);
            if (nCompressed + bytePos > pngBytes.length) {
                pngBytes = resizeByteArray(pngBytes, pngBytes.length + Math.max(1000, nCompressed));
            }
            System.arraycopy(compressedLines, 0, pngBytes, bytePos, nCompressed);
            bytePos += nCompressed;
            crc.update(compressedLines, 0, nCompressed);
            bytePos = writeInt4((int) crc.getValue(), bytePos);
            scrunch.finish();
            bytePos = writeInt4(0, bytePos);
            bytePos = writeBytes(IEND, bytePos);
            crc.reset();
            crc.update(IEND);
            bytePos = writeInt4((int) crc.getValue(), bytePos);
            pngBytes = resizeByteArray(pngBytes, maxPos);
            return pngBytes;
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] resizeByteArray(byte[] array, int newLength) {
        byte[] newArray = new byte[newLength];
        System.arraycopy(array, 0, newArray, 0, Math.min(array.length, newLength));
        return newArray;
    }

    private int writeBytes(byte[] data, int offset) {
        maxPos = Math.max(maxPos, offset + data.length);
        if (data.length + offset > pngBytes.length) pngBytes = resizeByteArray(pngBytes, pngBytes.length + Math.max(1000, data.length));
        System.arraycopy(data, 0, pngBytes, offset, data.length);
        return offset + data.length;
    }
    
    private int writeInt4(int n, int offset) {
        return writeBytes(new byte[]{(byte) ((n >> 24) & 0xff), (byte) ((n >> 16) & 0xff), (byte) ((n >> 8) & 0xff), (byte) (n & 0xff)}, offset);
    }

}
