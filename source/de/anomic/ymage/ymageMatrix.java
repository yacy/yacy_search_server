// ymageMatrix.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 16.09.2005
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

package de.anomic.ymage;

public class ymageMatrix implements Cloneable {
    
    // colors regarding RGB Color Model
    public static final long ADDITIVE_RED   = 0xFF0000;
    public static final long ADDITIVE_GREEN = 0x00FF00;
    public static final long ADDITIVE_BLUE  = 0x0000FF;
    public static final long ADDITIVE_BLACK = 0xFFFFFF;
    
    // colors regarding CMY Color Model
    public static final long SUBTRACTIVE_CYAN    = 0xFF0000;
    public static final long SUBTRACTIVE_MAGENTA = 0x00FF00;
    public static final long SUBTRACTIVE_YELLOW  = 0x0000FF;
    public static final long SUBTRACTIVE_WHITE   = 0xFFFFFF;
    
    public static final byte MODE_REPLACE = 0;
    public static final byte MODE_ADD = 1;
    public static final byte MODE_SUB = 2;

    
    protected int  width, height;
    private byte[] grid; // one-dimensional arrays are much faster than two-dimensional
    private int    defaultColR, defaultColG, defaultColB;
    private byte   defaultMode;
    private boolean grabComplementary = false;
    
    public ymageMatrix(int width, int height, String backgroundColor) {
        this(width, height, colNum(backgroundColor));
    }
    
    public ymageMatrix(int width, int height, long backgroundColor) {
        this.width = width;
        this.height = height;
        this.defaultColR = 0xFF;
        this.defaultColG = 0xFF;
        this.defaultColB = 0xFF;
        this.defaultMode = MODE_REPLACE;
        grid = new byte[3*width*height];
        
        // fill grid with background color
        byte bgR = (byte) (backgroundColor >> 16);
        byte bgG = (byte) ((backgroundColor >> 8) & 0xff);
        byte bgB = (byte) (backgroundColor & 0xff);
        for (int n = 3 * width * height - 3; n >= 0; n = n - 3) {
            grid[n    ] = bgR;
            grid[n + 1] = bgG;
            grid[n + 2] = bgB;
        }
    }
    
    private static long colNum(String col) {
        return Long.parseLong(col, 16);
        //return Integer.parseInt(col.substring(0,2), 16) << 16 | Integer.parseInt(col.substring(2,4), 16) << 8 | Integer.parseInt(col.substring(4,6), 16);
    }
    
    private static String colStr(long c) {
        String s = Long.toHexString(c);
        while (s.length() < 6) s = "0" + s;
        return s;
    }
    
    public Object clone() {
        ymageMatrix ip = new ymageMatrix(this.width, this.height, 0);
        System.arraycopy(this.grid, 0, ip.grid, 0, this.grid.length);
        ip.defaultColR = this.defaultColR;
        ip.defaultColG = this.defaultColG;
        ip.defaultColB = this.defaultColB;
        ip.defaultMode = this.defaultMode;
        return ip;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setColor(long c) {
        defaultColR = (int) (c >> 16);
        defaultColG = (int) ((c >> 8) & 0xff);
        defaultColB = (int) (c & 0xff);
    }
    
    public void setColor(String s) {
        setColor(colNum(s));
    }
    
    public void setMode(byte m) {
        this.defaultMode = m;
    }
    
    public void plot(int x, int y) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        int n = 3 * (x + y * width);
        if (this.defaultMode == MODE_REPLACE) {
            grid[n    ] = (byte) defaultColR;
            grid[n + 1] = (byte) defaultColG;
            grid[n + 2] = (byte) defaultColB;
        } else if (this.defaultMode == MODE_ADD) {
            int r = ((int) (0xff & grid[n    ])) + defaultColR; if (r > 0xff) r = 0xff;
            int g = ((int) (0xff & grid[n + 1])) + defaultColG; if (g > 0xff) g = 0xff;
            int b = ((int) (0xff & grid[n + 2])) + defaultColB; if (b > 0xff) b = 0xff;
            grid[n    ] = (byte) r;
            grid[n + 1] = (byte) g;
            grid[n + 2] = (byte) b;
        } else if (this.defaultMode == MODE_SUB) {
            int r = ((int) (0xff & grid[n    ])) - defaultColR; if (r < 0) r = 0;
            int g = ((int) (0xff & grid[n + 1])) - defaultColG; if (g < 0) g = 0;
            int b = ((int) (0xff & grid[n + 2])) - defaultColB; if (b < 0) b = 0;
            grid[n    ] = (byte) r;
            grid[n + 1] = (byte) g;
            grid[n + 2] = (byte) b;
        }
    }
    
    
    public void line(int Ax, int Ay, int Bx, int By) {
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
    
    public void getColorMode(boolean grabComplementary) {
        this.grabComplementary = grabComplementary;
    }
    
    public int[] getColor(int x, int y) {
        int n = 3 * (x + y * width);
        if (grabComplementary) {
            int r = 0xff & grid[n    ];
            int g = 0xff & grid[n + 1];
            int b = 0xff & grid[n + 2];
            return new int[]{(0x1fe - g - b) / 2, (0x1fe - r - b) / 2, (0x1fe - r - g) / 2};
        } else {
            int r = 0xff & grid[n    ];
            int g = 0xff & grid[n + 1];
            int b = 0xff & grid[n + 2];
            return new int[]{r, g, b};
        }
    }

            
}
