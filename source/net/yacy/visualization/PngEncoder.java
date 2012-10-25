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
 * - new order of data computation: first compute the size of compressed deflater output,
 *   then assign an exact-sized byte[] which makes resizing afterwards superfluous
 * - after all enhancements all class objects were removed; result is just one short static method
 * - made objects final where possible
 * - prepared process for concurrency: PixelGrabber.grabPixels is the main time-consuming process. This shall be done in concurrency.
 * - added concurrent processes to call the PixelGrabber and framework to do that (queues)
 */

package net.yacy.visualization;

import java.awt.Image;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class PngEncoder extends Object {
    
    private static final int[] POISON_IN = new int[0];
    private static final byte IHDR[] = {73, 72, 68, 82};
    private static final byte IDAT[] = {73, 68, 65, 84};
    private static final byte IEND[] = {73, 69, 78, 68};

    public final static byte[] pngEncode(final Image image, final int compressionLevel) throws IOException {
        if (image == null) throw new IOException("image == null");
        final int width = image.getWidth(null);
        final int height = image.getHeight(null);
        
        final TreeMap<Integer, byte[]> scan = new TreeMap<Integer, byte[]>();
        if (height > 80) {
            // prepare an input list for concurrent PixelGrabber computation
            final BlockingQueue<int[]> grabberInput = new LinkedBlockingQueue<int[]>();
            int startRow = 0;       // starting row to process this time through
            int rowsLeft = height;  // number of rows remaining to write
            while (rowsLeft > 0) {
                int nRows = Math.max(Math.min(32767 / (width * 4), rowsLeft), 1); // how many rows to grab at a time
                grabberInput.add(new int[]{startRow, nRows});
                startRow += nRows;
                rowsLeft -= nRows;
            }
            // do the PixelGrabber computation and allocate the result in the right order
            ArrayList<Thread> ts = new ArrayList<Thread>();
            int tc = Math.max(2, Math.min(1 + grabberInput.size() / 40, Runtime.getRuntime().availableProcessors()));
            for (int i = 0; i < tc; i++) {
                grabberInput.add(POISON_IN);
                Thread t = new Thread() {
                    public void run() {
                        int[] gi;
                        try {
                            while ((gi = grabberInput.take()) != POISON_IN) pixelGrabber(image, width, gi[0], gi[1], scan);
                        } catch (InterruptedException e) {} catch (IOException e) {}
                    }
                };
                t.start();
                ts.add(t);
                if (grabberInput.size() == 0) break;
            }
            for (Thread t: ts) try {t.join();} catch (InterruptedException e) {}
        } else {
            int startRow = 0;       // starting row to process this time through
            int rowsLeft = height;  // number of rows remaining to write
            while (rowsLeft > 0) {
                int nRows = Math.max(Math.min(32767 / (width * 4), rowsLeft), 1); // how many rows to grab at a time
                pixelGrabber(image, width, startRow, nRows, scan);
                startRow += nRows;
                rowsLeft -= nRows;
            }
        }
        
        // finally write the result of the concurrent calculation into an DeflaterOutputStream to compress the png
        final Deflater scrunch = new Deflater(compressionLevel);
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream(1024);
        final DeflaterOutputStream compBytes = new DeflaterOutputStream(outBytes, scrunch);
        for (Map.Entry<Integer, byte[]> entry: scan.entrySet()) compBytes.write(entry.getValue());
        compBytes.close();
        final byte[] compressedLines = outBytes.toByteArray();
        outBytes.close();
        outBytes = null;
        final int nCompressed = compressedLines.length;
        final byte[] pngBytes = new byte[nCompressed + 57]; // yes thats the exact size, not too less, not too much. No resizing needed.
        int bytePos = writeBytes(pngBytes, new byte[]{-119, 80, 78, 71, 13, 10, 26, 10}, 0);
        final int startPos = bytePos = writeInt4(pngBytes, 13, bytePos);
        bytePos = writeBytes(pngBytes, IHDR, bytePos);
        bytePos = writeInt4(pngBytes, width, bytePos);
        bytePos = writeInt4(pngBytes, height, bytePos);
        bytePos = writeBytes(pngBytes, new byte[]{8, 2, 0, 0, 0}, bytePos);
        final CRC32 crc = new CRC32();
        crc.reset();
        crc.update(pngBytes, startPos, bytePos - startPos);
        bytePos = writeInt4(pngBytes, (int) crc.getValue(), bytePos);
        crc.reset();
        bytePos = writeInt4(pngBytes, nCompressed, bytePos);
        bytePos = writeBytes(pngBytes, IDAT, bytePos);
        crc.update(IDAT);
        System.arraycopy(compressedLines, 0, pngBytes, bytePos, nCompressed);
        bytePos += nCompressed;
        crc.update(compressedLines, 0, nCompressed);
        bytePos = writeInt4(pngBytes, (int) crc.getValue(), bytePos);
        scrunch.finish();
        bytePos = writeInt4(pngBytes, 0, bytePos);
        bytePos = writeBytes(pngBytes, IEND, bytePos);
        crc.reset();
        crc.update(IEND);
        bytePos = writeInt4(pngBytes, (int) crc.getValue(), bytePos);
        return pngBytes;
    }
    
    private final static int writeInt4(final byte[] target, final int n, final int offset) {
        return writeBytes(target, new byte[]{(byte) ((n >> 24) & 0xff), (byte) ((n >> 16) & 0xff), (byte) ((n >> 8) & 0xff), (byte) (n & 0xff)}, offset);
    }

    private final static int writeBytes(final byte[] target, final byte[] data, final int offset) {
        System.arraycopy(data, 0, target, offset, data.length);
        return offset + data.length;
    }
    
    private final static void pixelGrabber(final Image image, final int width, int y, int nRows, TreeMap<Integer, byte[]> scan) throws IOException {
        int[] pixels = new int[width * nRows];
        PixelGrabber pg = new PixelGrabber(image, 0, y, width, nRows, pixels, 0, width);
        try {pg.grabPixels();} catch (InterruptedException e) {throw new IOException("interrupted waiting for pixels!");}
        if ((pg.getStatus() & ImageObserver.ABORT) != 0) throw new IOException("image fetch aborted or errored");
        try {
            ByteArrayOutputStream scanLines = new ByteArrayOutputStream(width * nRows * 3 + width);
            for (int i = 0; i < width * nRows; i++) {
                if (i % width == 0) scanLines.write(0);
                scanLines.write((pixels[i] >> 16) & 0xff);
                scanLines.write((pixels[i] >>  8) & 0xff);
                scanLines.write((pixels[i]) & 0xff);
            }
            synchronized (scan) {scan.put(y, scanLines.toByteArray());}
            scanLines.close();
        } catch (OutOfMemoryError e) {
            throw new IOException("out of memory, needed bytes: " + width * nRows * 4);
        }
    }
    
}
