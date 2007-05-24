// ymageMatrix.java 
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.09.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

/*
 This Class implements some convenience-methods to support drawing of statistical Data
 It is not intended to replace existing awt-funktions even if it looks so
 This class provides some drawing methods that creates transparency effects that
 are not available in awt.
 */

package de.anomic.ymage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

import javax.imageio.ImageIO;

import de.anomic.server.serverMemory;

public class ymageMatrix /*implements Cloneable*/ {
    
    // colors regarding CMY Color Model
    public static final long SUBTRACTIVE_CYAN    = 0xFF0000;
    public static final long SUBTRACTIVE_MAGENTA = 0x00FF00;
    public static final long SUBTRACTIVE_YELLOW  = 0x0000FF;
    public static final long SUBTRACTIVE_BLACK   = 0xFFFFFF;
    public static final long SUBTRACTIVE_WHITE   = 0x000000;
    public static final long SUBTRACTIVE_RED     = 0x007F7F;
    public static final long SUBTRACTIVE_GREEN   = 0x7F007F;
    public static final long SUBTRACTIVE_BLUE    = 0x7F7F00;
    
    public static final byte MODE_REPLACE = 0;
    public static final byte MODE_SUB = 2;

    
    protected int            width, height;
    private   BufferedImage  image;
    private   WritableRaster grid;
    private   int[]          defaultCol;
    private   byte           defaultMode;

    /*
    public ymageMatrix(ymageMatrix matrix) throws RuntimeException {
        if (!(serverMemory.available(1024 * 1024 + 3 * width * height, true))) throw new RuntimeException("ymage: not enough memory (" + serverMemory.available() + ") available");
        // clones the matrix
        this.width = matrix.width;
        this.height = matrix.height;
        this.defaultColR = matrix.defaultColR;
        this.defaultColG = matrix.defaultColG;
        this.defaultColB = matrix.defaultColB;
        this.defaultMode = matrix.defaultMode;
        this.grid = (WritableRaster) matrix.grid.
        System.arraycopy(matrix.grid, 0, this.grid, 0, matrix.grid.length);
    }
    */
    
    public ymageMatrix(int width, int height, String backgroundColor) {
        this(width, height, colNum(backgroundColor));
    }
    
    public ymageMatrix(int width, int height, long backgroundColor) {
        if (!(serverMemory.available(1024 * 1024 + 3 * width * height, true))) throw new RuntimeException("ymage: not enough memory (" + serverMemory.available() + ") available");
        this.width = width;
        this.height = height;
        this.defaultCol = new int[]{0xFF, 0xFF, 0xFF};
        this.defaultMode = MODE_REPLACE;
        
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = image.createGraphics();
        gr.setBackground(Color.white);
        //gr.clearRect(0, 0, width, height);
        
        grid = image.getRaster();
        //if (backgroundColor != SUBTRACTIVE_WHITE) {
            // fill grid with background color
            byte bgR = (byte) (0xFF - (backgroundColor >> 16));
            byte bgG = (byte) (0xFF - ((backgroundColor >> 8) & 0xff));
            byte bgB = (byte) (0xFF - (backgroundColor & 0xff));
            int[] c = new int[]{bgR, bgG, bgB};
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    grid.setPixel(i, j, c);
                }
            }
        //}
    }
    
    public BufferedImage getImage() {
        return this.image;
    }
    
    private static long colNum(String col) {
        return Long.parseLong(col, 16);
        //return Integer.parseInt(col.substring(0,2), 16) << 16 | Integer.parseInt(col.substring(2,4), 16) << 8 | Integer.parseInt(col.substring(4,6), 16);
    }
    
    /*
    public Object clone() {
        return new ymageMatrix(this);
    }
    */
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setColor(long c) {
        defaultCol[0] = (int) (c >> 16);
        defaultCol[1] = (int) ((c >> 8) & 0xff);
        defaultCol[2] = (int) (c & 0xff);
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
        //int n = 3 * (x + y * width);
        if (this.defaultMode == MODE_REPLACE) {
            grid.setPixel(x, y, defaultCol);
        } else if (this.defaultMode == MODE_SUB) {
            int[] c = new int[3];
            c = grid.getPixel(x, y, c);
            int r = (0xff & c[0]) - defaultCol[0]; if (r < 0) r = 0;
            int g = (0xff & c[1]) - defaultCol[1]; if (g < 0) g = 0;
            int b = (0xff & c[2]) - defaultCol[2]; if (b < 0) b = 0;
            grid.setPixel(x, y, new int[]{r, g, b});
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
    
    public void lineDot(int x0, int y0, int x1, int y1, int radius, int distance, long lineColor, long dotColor) {
        // draw a line with a dot at the end.
        // the radius value is the radius of the dot
        // the distance value is the distance of the dot border to the endpoint
        
        // compute first the angle of the line between the points
        double angle = (x1 - x0 > 0) ? Math.atan(((double) (y0 - y1)) / ((double) (x1 - x0))) : Math.PI - Math.atan(((double) (y0 - y1)) / ((double) (x0 - x1)));
        // now find two more points in between
        // first calculate the radius' of the points
        double ra = Math.sqrt((double) ((x0 - x1) * (x0 - x1) + (y0 - y1) * (y0 - y1))); // from a known point x1, y1
        double rb = ra - radius - distance;
        double rc = rb - radius;
        //System.out.println("CONTROL angle = " + angle);
        //System.out.println("CONTROL x1 = " + x1 + ", x1calc = " + ((x0 + ((int) ra * Math.cos(angle)))));
        //System.out.println("CONTROL y1 = " + y1 + ", y1calc = " + ((y0 - ((int) ra * Math.sin(angle)))));
        // the points are on a circle with radius rb and rc
        int x2 = x0 + ((int) (rb * Math.cos(angle)));
        int y2 = y0 - ((int) (rb * Math.sin(angle)));
        int x3 = x0 + ((int) (rc * Math.cos(angle)));
        int y3 = y0 - ((int) (rc * Math.sin(angle)));
        setColor(lineColor);
        line(x0, y0, x3, y3);
        setColor(dotColor);
        dot(x2, y2, radius, true);
    }
    
    public int[] getColor(int x, int y) {
        int[] c = new int[3];
        return grid.getPixel(x, y, c);
    }

    public void dot(int x, int y, int radius, boolean filled) {
        if (filled) {
            for (int r = radius; r >= 0; r--) ymageToolCircle.circle(this, x, y, r);
        } else {
            ymageToolCircle.circle(this, x, y, radius);
        }
    }
    
    public void arc(int x, int y, int innerRadius, int outerRadius, int fromArc, int toArc) {
        for (int r = innerRadius; r <= outerRadius; r++) ymageToolCircle.circle(this, x, y, r, fromArc, toArc);
    }

    public void arcLine(int cx, int cy, int innerRadius, int outerRadius, int angle) {
        int xi = cx + (int) (innerRadius * Math.cos(Math.PI * angle / 180));
        int yi = cy - (int) (innerRadius * Math.sin(Math.PI * angle / 180));
        int xo = cx + (int) (outerRadius * Math.cos(Math.PI * angle / 180));
        int yo = cy - (int) (outerRadius * Math.sin(Math.PI * angle / 180));
        line(xi, yi, xo, yo);
    }
    
    public void arcDot(int cx, int cy, int arcRadius, int angle, int dotRadius) {
        int x = cx + (int) (arcRadius * Math.cos(Math.PI * angle / 180));
        int y = cy - (int) (arcRadius * Math.sin(Math.PI * angle / 180));
        dot(x, y, dotRadius, true);
    }
    
    public void arcArc(int cx, int cy, int arcRadius, int angle, int innerRadius, int outerRadius, int fromArc, int toArc) {
        int x = cx + (int) (arcRadius * Math.cos(Math.PI * angle / 180));
        int y = cy - (int) (arcRadius * Math.sin(Math.PI * angle / 180));
        arc(x, y, innerRadius, outerRadius, fromArc, toArc);
    }
    
    public static void demoPaint(ymageMatrix m) {
        m.setMode(MODE_SUB);
        m.setColor(SUBTRACTIVE_CYAN);
        m.line(0,  10, 100,  10); ymageToolPrint.print(m, 0,   5, 0, "Cyan", -1);
        m.line(50, 0,   50, 300);
        m.setColor(SUBTRACTIVE_MAGENTA);
        m.line(0,  30, 100,  30); ymageToolPrint.print(m, 0,  25, 0, "Magenta", -1);
        m.line(55, 0,   55, 300);
        m.setColor(SUBTRACTIVE_YELLOW);
        m.line(0,  50, 100,  50); ymageToolPrint.print(m, 0,  45, 0, "Yellow", -1);
        m.line(60, 0,   60, 300);
        m.setColor(SUBTRACTIVE_BLACK);
        m.line(0,  70, 100,  70); ymageToolPrint.print(m, 0,  65, 0, "Black", -1);
        m.line(65, 0,   65, 300);
        m.setColor(SUBTRACTIVE_RED);
        m.line(0,  90, 100,  90); ymageToolPrint.print(m, 0,  85, 0, "Red", -1);
        m.line(70, 0,   70, 300);
        m.setColor(SUBTRACTIVE_GREEN);
        m.line(0, 110, 100, 110); ymageToolPrint.print(m, 0, 105, 0, "Green", -1);
        m.line(75, 0,   75, 300);
        m.setColor(SUBTRACTIVE_BLUE);
        m.line(0, 130, 100, 130); ymageToolPrint.print(m, 0, 125, 0, "Blue", -1);
        m.line(80, 0,   80, 300);
    }
    
    public static void main(String[] args) {
        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");
        
        ymageMatrix m = new ymageMatrix(200, 300, SUBTRACTIVE_WHITE);
        demoPaint(m);
        try {
            ImageIO.write(m.getImage(), "png", new java.io.File(args[0]));
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        
        // open file automatically, works only on Mac OS X
        /*
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] {"/usr/bin/osascript", "-e", "open \"" + args[0] + "\""});
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
    }
    
    
}
