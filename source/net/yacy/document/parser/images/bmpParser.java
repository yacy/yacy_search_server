// bmpParser.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.07.2007 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser.images;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.yacy.cora.util.ConcurrentLog;

public class bmpParser {

    // this is a implementation of http://de.wikipedia.org/wiki/Windows_Bitmap

    // file offsets
    private static int FILEHEADER_offset = 0;
    private static int INFOHEADER_offset = 14;
    public  static int INFOHEADER_size   = 40;

    // compression tags
    static int BI_RGB = 0;
    //private static int BI_RLE8 = 1;
    //private static int BI_RLE4 = 2;
    //private static int BI_BITFIELDS = 3;


    public static final boolean isBMP(final byte[] source) {
        // check the file magic
        return (source != null) && (source.length >= 2) && (source[0] == 'B') && (source[1] == 'M');
    }

    public static IMAGEMAP parse(final byte[] source) {
        // read info-header
        final int bfOffBits  = DWORD(source, FILEHEADER_offset + 10);

        final INFOHEADER infoheader = new INFOHEADER(source, INFOHEADER_offset);
        final COLORTABLE colortable = new COLORTABLE(source, INFOHEADER_offset + INFOHEADER_size, infoheader);

        // check consistency with bfOffBits
        assert bfOffBits == INFOHEADER_offset + 40 + colortable.colorbytes : "bfOffBits = " + bfOffBits + ", colorbytes = " + colortable.colorbytes;
        assert infoheader.biSizeImage <= source.length - bfOffBits : "bfOffBits = " + bfOffBits + ", biSizeImage = " + infoheader.biSizeImage + ", source.length = " + source.length;

        return new IMAGEMAP(source, bfOffBits, infoheader.biWidth, infoheader.biHeight, infoheader.biCompression, infoheader.biBitCount, colortable);
    }

    public static final int DWORD(final byte[] b, final int offset) {
        if (offset + 3 >= b.length) return 0;
        int ret = (b[offset + 3] & 0xff);
        ret = (ret << 8) | (b[offset + 2] & 0xff);
        ret = (ret << 8) | (b[offset + 1] & 0xff);
        ret = (ret << 8) | (b[offset] & 0xff);
        return ret;
    }

    public static final int WORD(final byte[] b, final int offset) {
        final int ret = ((b[offset + 1] & 0xff) << 8) | (b[offset] & 0xff);
        return ret;
    }

    public static final int BYTE(final byte[] b, final int offset) {
        final int ret = (b[offset] & 0xff);
        return ret;
    }


    public static class INFOHEADER {

        public int biWidth, biHeight, biBitCount, biCompression, biSizeImage, biClrUsed;

        public INFOHEADER(final byte[] s, final int offset) {
            // read info-header
            this.biWidth       = DWORD(s, offset + 4);
            this.biHeight      = DWORD(s, offset + 8);
            this.biBitCount    =  WORD(s, offset + 14);
            this.biCompression =  WORD(s, offset + 16);
            this.biSizeImage   = DWORD(s, offset + 20);
            this.biClrUsed     = DWORD(s, offset + 32);
        }
    }

    public static class COLORTABLE {

        public int colorbytes;
        public int[] colorindex;

        public COLORTABLE(final byte[] s, final int offset, final INFOHEADER infoheader) {
            // read colortable
            this.colorbytes = 0; // for consistency check
            if (infoheader.biClrUsed == 0 || infoheader.biClrUsed > 1024) {
                if ((infoheader.biBitCount == 1) || (infoheader.biBitCount == 4) || (infoheader.biBitCount == 8)) {
                    this.colorindex = new int[1 << infoheader.biBitCount];
                    this.colorbytes = 4 * this.colorindex.length;
                    int color;
                    for (int i = 0; i < this.colorindex.length; i++) {
                        // translate BGR into RGB color Scheme
                        color = 0xffffff & DWORD(s, offset + 4 * i);
                        this.colorindex[i] = color;
                    }
                } else {
                    this.colorindex = null;
                }
            } else {
                this.colorindex = new int[infoheader.biClrUsed]; // OOM DANGER!
                this.colorbytes = 4 * this.colorindex.length;
                int color;
                for (int i = 0; i < this.colorindex.length; i++) {
                    // translate BGR into RGB color Scheme
                    color = 0xffffff & DWORD(s, offset + 4 * i);
                    this.colorindex[i] = color;
                    //if (debugmode) System.out.println("Color " + i + " = " + Integer.toHexString(colorindex[i]));
                }
            }
        }
    }

    public static class IMAGEMAP {

        private BufferedImage image;

        public IMAGEMAP(final byte[] s, final int offset, final int width, final int height, final int compression, final int bitcount, final COLORTABLE colortable) {
            // parse picture content
            if ((width != 0) && (height != 0)) {
                this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

                if (compression == BI_RGB) {
                    if (bitcount == 1) parseBMP1(s, offset, width, height, colortable);
                    else if (bitcount == 4) parseBMP4(s, offset, width, height, colortable);
                    else if (bitcount == 8) parseBMP8(s, offset, width, height, colortable);
                    else if (bitcount == 24) parseBMP24(s, offset, width, height);
                    else if (bitcount == 32) parseBMP32(s, offset, width, height);
                    else System.out.println("unsupported BMP format: biCompression = " + compression + ", biBitCount = " + bitcount);
                } else {
                    System.out.println("unsupported BMP format: biCompression = " + compression + ", biBitCount = " + bitcount);
                }
            }
        }

        private void parseBMP1(final byte[] s, final int offset, final int width, final int height, final COLORTABLE colortable) {
            int n = 0;
            int b;
            for (int rows = 0; rows < height; rows++) {
                for (int columns = 0; columns < width; columns = columns + 8) {
                    if (offset + n >= s.length) return; // emergency break
                    b = (s[offset + n] & 0xff);
                    n++;
                    this.image.setRGB(columns,     (height - rows - 1), colortable.colorindex[(b & 0x80) >> 7]);
                    this.image.setRGB(columns + 1, (height - rows - 1), colortable.colorindex[(b & 0x40) >> 6]);
                    this.image.setRGB(columns + 2, (height - rows - 1), colortable.colorindex[(b & 0x20) >> 5]);
                    this.image.setRGB(columns + 3, (height - rows - 1), colortable.colorindex[(b & 0x10) >> 4]);
                    this.image.setRGB(columns + 4, (height - rows - 1), colortable.colorindex[(b & 0x08) >> 3]);
                    this.image.setRGB(columns + 5, (height - rows - 1), colortable.colorindex[(b & 0x04) >> 2]);
                    this.image.setRGB(columns + 6, (height - rows - 1), colortable.colorindex[(b & 0x02) >> 1]);
                    this.image.setRGB(columns + 7, (height - rows - 1), colortable.colorindex[ b & 0x01]);
                }
                n += fill4(n);
            }
        }

        private void parseBMP4(final byte[] s, final int offset, final int width, final int height, final COLORTABLE colortable) {
            int n = 0;
            int b;
            for (int rows = 0; rows < height; rows++) {
                for (int columns = 0; columns < width - 1; columns = columns + 2) {
                    if (offset + n >= s.length) return; // emergency break
                    b = (s[offset + n] & 0xff);
                    n++;
                    this.image.setRGB(columns,     (height - rows - 1), colortable.colorindex[(b & 0xf0) >> 4]);
                    this.image.setRGB(columns + 1, (height - rows - 1), colortable.colorindex[b & 0xf]);
                }
                n += fill4(n);
            }
        }

        private void parseBMP8(final byte[] s, final int offset, final int width, final int height, final COLORTABLE colortable) {
            int n = 0;
            for (int rows = 0; rows < height; rows++) {
                for (int columns = 0; columns < width; columns++) {
                    if (offset + n >= s.length) return; // emergency break
                    this.image.setRGB(columns, (height - rows - 1), colortable.colorindex[(s[offset + n] & 0xff)]);
                    n++;
                }
                n += fill4(n);
            }
        }

        private void parseBMP24(final byte[] s, final int offset, final int width, final int height) {
            int n = 0;
            for (int rows = 0; rows < height; rows++) {
                for (int columns = 0; columns < width; columns++) {
                    if (offset + n + 3 >= s.length) return; // emergency break
                    this.image.setRGB(columns, (height - rows - 1), 0xffffff & DWORD(s, offset + n));
                    n += 3;
                }
                n += fill4(n);
            }
        }

        private void parseBMP32(final byte[] s, final int offset, final int width, final int height) {
            int n = 0;
            for (int rows = 0; rows < height; rows++) {
                for (int columns = 0; columns < width; columns++) {
                    if (offset + n + 3 >= s.length) return; // emergency break
                    this.image.setRGB(columns, (height - rows - 1), 0xffffff & DWORD(s, offset + n));
                    n += 4;
                }
            }
        }

        private final static int fill4(final int x) {
            final int r = x % 4;
            if (r == 0) return 0;
            return 4 - r;
        }

        public BufferedImage getImage() {
            return this.image;
        }

    }

    public static void main(final String[] args) {
        // read a bmp and write it as png
        System.setProperty("java.awt.headless", "true");
        final File in = new File(args[0]);
        final File out = new File(args[1]);

        final byte[] file = new byte[(int) in.length()];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(in);
            fis.read(file);
        } catch (final FileNotFoundException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        try {
            ImageIO.write(parse(file).getImage(), "PNG", out);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }
}