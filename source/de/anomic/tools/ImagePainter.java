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
import java.util.HashSet;
import java.util.ArrayList;

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

    private static long[] font = new long[]{
        0x0,0x421004,0xa50000,0xafabea,0xfa38be,0x9b39b2,0xe82a8a,0x420000,0x221082,0x821080,0x51040,0x23880,0x1088,0x3800,0x4,0x111110,
        0xe9d72e,0x46108e,0xe8991f,0xe88a2e,0x657c42,0x1f8783e,0xe87a2e,0x1f11084,0xe8ba2e,0xe8bc2e,0x20080,0x20088,0x222082,0xf83e0,0x820888,0xe11004,
        0xeefa0e,0x4547f1,0x1c97a3e,0xe8420e,0x1e8c63e,0x1f8721f,0x1f87210,0xe85e2e,0x118fe31,0x1f2109f,0x1f0862e,0x1197251,0x108421f,0x11dd631,0x11cd671,0xe8c62e,
        0x1e8fa10,0xe8d64d,0x1e8fa51,0xe8382e,0x1f21084,0x118c62e,0x118c544,0x118c6aa,0x1151151,0x1151084,0x1f1111f,0xe4210e,0x1041041,0xe1084e,0x454400,0x1f
    };
    
    private static final int radiusPrecalc = 120;
    private static HashSet crds = new HashSet();
    private static ArrayList crc;
    private static int[][][] circles = new int[radiusPrecalc][][];
    static {
        // calculate coordinates
        int x, y;
        String co;
        crds.add("0|0");
        for (int r = 0; r < radiusPrecalc; r++) {
            crc = new ArrayList();
            for (int a = 0; a <= 2 * (r + 1); a++) {
                x = (int) ((r + 1) * Math.cos(Math.PI * a / (4 * (r + 1))));
                y = (int) ((r + 1) * Math.sin(Math.PI * a / (4 * (r + 1))));
                co = x + "|" + y;
                if (!(crds.contains(co))) {
                    crc.add(new int[]{x, y});
                    crds.add(co);
                }
                x = (int) ((r + 0.5) * Math.cos(Math.PI * a / (4 * (r + 1))));
                y = (int) ((r + 0.5) * Math.sin(Math.PI * a / (4 * (r + 1))));
                co = x + "|" + y;
                if (!(crds.contains(co))) {
                    crc.add(new int[]{x, y});
                    crds.add(co);
                }
            }
            // put coordinates into array
            //System.out.print("Radius " + r + " => " + crc.size() + " points: ");
            circles[r] = new int[crc.size() - 1][];
            for (int i = 0; i < crc.size() - 1; i++) {
                circles[r][i] = (int[]) crc.get(i);
                //System.out.print(circles[r][i][0] + "," +circles[r][i][1] + "; "); 
            }
            //System.out.println();
        }
        crc = null;
        crds = null;
    }
    
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
    
    /*
    private void plot(int x, int y) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        if (defaultMode == MODE_REPLACE) {
            grid[x + y * width] = defaultCol;
        } else if (defaultMode == MODE_MIX) {
            int n = x + y * width;
            long c = grid[n];
            if (c < 0) {
                //grid[n] = defaultCol;
                int r = (int) (0xFF - (defaultCol >> 16));
                int g = (int) (0xFF - ((defaultCol >> 8) & 0xff));
                int b = (int) (0xFF - (defaultCol & 0xff));
                grid[n] = r << 16 | g << 8 | b;
            } else {
                int r = (int) ((c >> 16) - (defaultCol >> 16));
                int g = (int) (((c >> 8) & 0xff) - ((defaultCol >> 8) & 0xff));
                int b = (int) ((c & 0xff) - (defaultCol & 0xff));
                grid[n] = ((r < 0) ? 0 : r << 16) | ((g < 0) ? 0 : g << 8) | ((b < 0) ? 0 : b);
            }
        }
    }
    */
    
    
    private void plot(int x, int y) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        if (defaultMode == MODE_REPLACE) {
            grid[x + y * width] = defaultCol;
        } else if (defaultMode == MODE_MIX) {
            int n = x + y * width;
            long c = grid[n];
            if (c < 0) {
                grid[n] = defaultCol;
            } else {
                int r = ((int) ((c >> 16) + (defaultCol >> 16))) & 0xff;
                int g = ((int) (((c >> 8) & 0xff) + ((defaultCol >> 8) & 0xff))) & 0xff;
                int b = ((int) ((c & 0xff) + (defaultCol & 0xff))) & 0xff;
                grid[n] = r << 16 | g << 8 | b;
            }
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
        if (radius == 0) {
            plot(xc, yc);
        } else {
            int[][] c = circles[radius - 1];
            int x, y;
            for (int i = c.length - 1; i >= 0; i--) {
                x = c[i][0];
                y = c[i][1];
                plot(xc + x, yc + y);
                plot(xc - x + 1, yc + y);
                plot(xc + x, yc - y + 1);
                plot(xc - x + 1, yc - y + 1);
            }
        }
    }
    
    private void circle(int xc, int yc, int radius, int fromArc, int toArc) {
        // draws only a part of a circle
        // arc is given in degree
        if (radius == 0) {
            plot(xc, yc);
        } else {
            int[][] c = circles[radius - 1];
            int q = c.length;
            int[][] c4 = new int[q * 4][];
            for (int i = 0; i < q; i++) {
                c4[i        ] = new int[]{  c[i        ][0],1-c[i        ][1]};
                c4[i +     q] = new int[]{1-c[q - 1 - i][0],1-c[q - 1 - i][1]};
                c4[i + 2 * q] = new int[]{1-c[i        ][0],  c[i        ][1]};
                c4[i + 3 * q] = new int[]{  c[q - 1 - i][0],  c[q - 1 - i][1]};
            }
            for (int i = q * 4 * fromArc / 360; i <= q * 4 * toArc / 360; i++) {
                plot(xc + c4[i][0], yc + c4[i][1]);
            }
        }
    }
    
    private void print(int x, int y, char letter) {
        int index = (int) letter - 0x20;
        if (index >= font.length) return;
        long character = font[index];
        long row;
        for (int i = 0; i < 5; i++) {
            row = character & 0x1f;
            character = character >> 5;
            for (int j = 0; j < 5; j++) {
                if ((row & 1) == 1) plot(x + 5 - j, y);
                row = row >> 1;
            }
            y--;
        }
    }
    
    public void dot(int x, int y, int radius, boolean filled, long color, byte mode) {
        setColor(color);
        setMode(mode);
        if (filled) {
            for (int r = radius; r >= 0; r--) circle(x, y, r);
        } else {
            circle(x, y, radius);
        }
    }
    
    public void arc(int x, int y, int innerRadius, int outerRadius, int fromArc, int toArc, long color, byte mode) {
        setColor(color);
        setMode(mode);
        for (int r = innerRadius; r <= outerRadius; r++) circle(x, y, r, fromArc, toArc);
        
    }
    
    public void print(int x, int y, String message, long color, byte mode) {
        setColor(color);
        setMode(mode);
        for (int i = 0; i < message.length(); i++) {
            print(x, y, message.charAt(i));
            x += 6;
        }
    }
    
    public BufferedImage toImage(boolean complementary) {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 
        Graphics2D g = bi.createGraphics();
        g.setBackground(Color.white);
        g.clearRect(0, 0, width, height);

        WritableRaster r = bi.getRaster();
        long c;
        for (int i = width - 1; i >= 0; i--) {
            for (int j = height - 1; j >= 0; j--) {
                c = grid[i + j * width];
                if (c >= 0) {
                    if (complementary) {
                        r.setPixel(i, j, new int[]{0xff - (int) ((((c >> 8) & 0xff) + (c & 0xff)) / 2), 0xff - (int) (((c >> 16) + (c & 0xff)) / 2) , 0xff - (int) ((((c >> 8) & 0xff) + (c >> 16)) / 2)});
                    } else {
                        r.setPixel(i, j, new int[]{(int) (c >> 16), (int) ((c >> 8) & 0xff), (int) (c & 0xff)});
                    }
                }
            }
        }
        
        return bi;
    }

}
