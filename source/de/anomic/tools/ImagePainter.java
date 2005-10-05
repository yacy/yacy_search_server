// ImagePainter.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 16.09.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
 This Class implements some convenience-methods to support drawing of statistical Data
 It is not intended to replace existing awt-funktions even if it looks so
 This class provides some drawing methods that creates transparency effects that
 are not available in awt.
 */

package de.anomic.tools;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

public class ImagePainter {
    
    public static final long TRANSPARENT = -1;
    public static final long WHITE = 0xFFFFFF;
    public static final long RED   = 0xFF0000;
    public static final long GREEN = 0x00FF00;
    public static final long BLUE  = 0x0000FF;
    public static final long BLACK = 0;
    
    public static final byte MODE_REPLACE = 0;
    public static final byte MODE_REVERSE = 1;
    public static final byte MODE_MIX = 2;
    
    private long[] grid; // one-dimensional arrays are much faster than two-dimensional
    private int width, height;
    private long defaultCol;
    private byte defaultMode;
    
    public ImagePainter(int width, int height, long backgroundColor) {
        this.width = width;
        this.height = height;
        this.defaultCol = BLACK;
        this.defaultMode = MODE_REPLACE;
        grid = new long[width*height];
        for (int n= width * height - 1; n >= 0; n--) grid[n] = backgroundColor;
    }
    
    private long colNum(String col) {
        return Long.parseLong(col, 16);
        //return Integer.parseInt(col.substring(0,2), 16) << 16 | Integer.parseInt(col.substring(2,4), 16) << 8 | Integer.parseInt(col.substring(4,6), 16);
    }
    
    private String colStr(long c) {
        String s = Long.toHexString(c);
        while (s.length() < 6) s = "0" + s;
        return s;
    }
    
    private void setColor(long c) {
        defaultCol = c;
    }
    
    private void setMode(byte m) {
        defaultMode = m;
    }
    
    private void plot(int x, int y) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        if (defaultMode == MODE_REPLACE) {
            grid[x + y * width] = defaultCol;
        } else if (defaultMode == MODE_MIX) {
            int n = x + y * width;
            long c = grid[n];
            int r = ((int) ((c >> 16) + (defaultCol >> 16))) & 0xff;
            int g = ((int) (((c >> 8) & 0xff) + ((defaultCol >> 8) & 0xff))) & 0xff;
            int b = ((int) ((c & 0xff) + (defaultCol & 0xff))) & 0xff;
            grid[n] = r << 16 | g << 8 | b;
        }
    }
    
    private void line(int Ax, int Ay, int Bx, int By) {
        // Bresenham's line drawing algorithm
        int dX = Math.abs(Bx-Ax);
        int dY = Math.abs(By-Ay);
        int Xincr, Yincr;
        if (Ax > Bx) Xincr=-1; else Xincr=1;
        if (Ay > By) Yincr=-1; else Yincr=1;
        if (dX >= dY) {
            int dPr  = dY<<1;
            int dPru = dPr - (dX<<1);
            int P    = dPr - dX;
            for (; dX>=0; dX--) {
                plot(Ax, Ay);
                if (P > 0) {
                    Ax+=Xincr;
                    Ay+=Yincr;
                    P+=dPru;
                } else {
                    Ax+=Xincr;
                    P+=dPr;
                }
            }
        } else {
            int dPr  = dX<<1;
            int dPru = dPr - (dY<<1);
            int P    = dPr - dY;
            for (; dY>=0; dY--) {
                plot(Ax, Ay);
                if (P > 0) {
                    Ax+=Xincr;
                    Ay+=Yincr;
                    P+=dPru;
                } else {
                    Ay+=Yincr;
                    P+=dPr;
                }
            }
        }
    }
    
    private void circle(int xc, int yc, int radius) {
        int xn, yn;
        for (int i = 359; i >= 0; i--) {
            xn = xc + (int) (radius * Math.cos(Math.PI * 2 * i / 360));
            yn = yc + (int) (radius * Math.sin(Math.PI * 2 * i / 360));
            plot(xn, yn);
        }
    }
    
    public void dot(int x, int y, int radius, boolean filled, long color, byte mode) {
        setColor(color);
        setMode(mode);
        if (filled) {
            for (int i = radius; i >= 0; i--) circle(x, y, i);
        } else {
            circle(x, y, radius);
        }
    }
    
    
    public BufferedImage toImage() {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 
        Graphics2D g = bi.createGraphics();
        g.setBackground(Color.white);
        g.clearRect(0, 0, width, height);

        WritableRaster r = bi.getRaster();
        long c;
        for (int i = width - 1; i >= 0; i--) {
            for (int j = height - 1; j >= 0; j--) {
                c = grid[i + j * width];
                if (c >= 0) r.setPixel(i, j, new int[]{(int) (c >> 16), (int) ((c >> 8) & 0xff), (int) (c & 0xff)});
            }
        }
        
        return bi;
    }

}
