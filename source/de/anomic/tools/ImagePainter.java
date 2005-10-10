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
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;
import java.awt.GraphicsConfiguration;
import java.awt.Transparency;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.HashSet;
import java.util.ArrayList;

public class ImagePainter {
    
    public static final long TRANSPARENT = -1;
    
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

    private static long[] font = new long[]{
        0x0000000,0x0421004,0x0A50000,0x0AFABEA,0x0FA38BE,0x09B39B2,0x0E82A8A,0x0420000,
        0x0221082,0x0821080,0x0051040,0x0023880,0x0001088,0x0003800,0x0000004,0x0111110,
        0x0E9D72E,0x046108E,0x0E8991F,0x0E88A2E,0x0657C42,0x1F8783E,0x0E87A2E,0x1F11084,
        0x0E8BA2E,0x0E8BC2E,0x0020080,0x0020088,0x0222082,0x00F83E0,0x0820888,0x0E11004,
        0x0EEFA0E,0x04547F1,0x1C97A3E,0x0E8420E,0x1E8C63E,0x1F8721F,0x1F87210,0x0E85E2E,
        0x118FE31,0x1F2109F,0x1F0862E,0x1197251,0x108421F,0x11DD631,0x11CD671,0x0E8C62E,
        0x1E8FA10,0x0E8D64D,0x1E8FA51,0x0E8382E,0x1F21084,0x118C62E,0x118C544,0x118C6AA,
        0x1151151,0x1151084,0x1F1111F,0x0E4210E,0x1041041,0x0E1084E,0x0454400,0x000001F,
        0x0820000,0x0003E2F,0x1087A3E,0x0003E0F,0x010BE2F,0x0064A8F,0x0623884,0x00324BE,
        0x1085B31,0x0401084,0x0401088,0x1084F93,0x0421084,0x0002AB5,0x0003A31,0x0003A2E,
        0x00F47D0,0x007C5E1,0x0011084,0x0001932,0x0471084,0x000462E,0x0004544,0x00056AA,
        0x000288A,0x0002884,0x0003C9E,0x0622086,0x0421084,0x0C2088C,0x0045440,0x1F8C63F
    };
    
    //private static final int radiusPrecalc = 180;
    //private static HashSet crds = new HashSet();
    //private static ArrayList crc;
    //private static int[][][] circles = new int[radiusPrecalc][][];
    private static int[][][] circles = new int[0][][];
    /*
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
    */
    
    private long[] grid; // one-dimensional arrays are much faster than two-dimensional
    private int width, height;
    private long defaultCol;
    private byte defaultMode;
    
    public ImagePainter(int width, int height, long backgroundColor) {
        this.width = width;
        this.height = height;
        this.defaultCol = ADDITIVE_BLACK;
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
    
    public void setColor(long c) {
        defaultCol = c;
    }
    
    public void setColor(String s) {
        defaultCol = colNum(s);
    }
    
    public void setMode(byte m) {
        defaultMode = m;
    }
    
    private void plot(int x, int y) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        if (defaultMode == MODE_REPLACE) {
            grid[x + y * width] = defaultCol;
        } else if (defaultMode == MODE_ADD) {
            int n = x + y * width;
            long c = grid[n];
            if (c < 0) {
                grid[n] = defaultCol;
            } else {
                int r = ((int) ((c >> 16) + (defaultCol >> 16))); if (r > 0xff) r = 0xff;
                int g = ((int) (((c >> 8) & 0xff) + ((defaultCol >> 8) & 0xff))); if (g > 0xff) g = 0xff;
                int b = ((int) ((c & 0xff) + (defaultCol & 0xff))); if (b > 0xff) b = 0xff;
                grid[n] = r << 16 | g << 8 | b;
            }
        } else if (defaultMode == MODE_SUB) {
            int n = x + y * width;
            long c = grid[n];
            if (c < 0) c = 0xffffff;
            int r = ((int) ((c >> 16) - (defaultCol >> 16))); if (r < 0) r = 0;
            int g = ((int) (((c >> 8) & 0xff) - ((defaultCol >> 8) & 0xff))); if (g < 0) g = 0;
            int b = ((int) ((c & 0xff) - (defaultCol & 0xff))); if (g < 0) g = 0;
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
    
    private static int[][] getCircleCoords(int radius) {
        if ((radius - 1) < circles.length) return circles[radius - 1];
        
        // read some lines from known circles
        HashSet crds = new HashSet();
        crds.add("0|0");
        String co;
        for (int i = Math.max(0, circles.length - 5); i < circles.length; i++) {
            for (int j = 0; j < circles[i].length; j++) {
                co = circles[i][j][0] + "|" + circles[i][j][1];
                if (!(crds.contains(co))) crds.add(co);
            }
        }
        
        // copy old circles into new array
        int[][][] newCircles = new int[radius + 30][][];
        System.arraycopy(circles, 0, newCircles, 0, circles.length);
        
        // compute more lines in new circles
        int x, y;
        ArrayList crc;
        for (int r = circles.length; r < newCircles.length; r++) {
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
            newCircles[r] = new int[crc.size() - 1][];
            for (int i = 0; i < crc.size() - 1; i++) {
                newCircles[r][i] = (int[]) crc.get(i);
                //System.out.print(circles[r][i][0] + "," +circles[r][i][1] + "; "); 
            }
            //System.out.println();
        }
        crc = null;
        crds = null;
        
        // move newCircles to circles array
        circles = newCircles;
        newCircles = null;
        
        // finally return wanted slice
        return circles[radius - 1];
    }
    
    private void circle(int xc, int yc, int radius) {
        if (radius == 0) {
            plot(xc, yc);
        } else {
            int[][] c = getCircleCoords(radius);
            int x, y;
            for (int i = c.length - 1; i >= 0; i--) {
                x = c[i][0];
                y = c[i][1];
                plot(xc + x    , yc - y - 1); // quadrant 1
                plot(xc - x + 1, yc - y - 1); // quadrant 2
                plot(xc + x    , yc + y    ); // quadrant 4
                plot(xc - x + 1, yc + y    ); // quadrant 3
            }
        }
    }
    
    private void circle(int xc, int yc, int radius, int fromArc, int toArc) {
        // draws only a part of a circle
        // arc is given in degree
        if (radius == 0) {
            plot(xc, yc);
        } else {
            int[][] c = getCircleCoords(radius);
            int q = c.length;
            int[][] c4 = new int[q * 4][];
            for (int i = 0; i < q; i++) {
                c4[i        ] = new int[]{    c[i        ][0], -c[i        ][1] - 1}; // quadrant 1
                c4[i +     q] = new int[]{1 - c[q - 1 - i][0], -c[q - 1 - i][1] - 1}; // quadrant 2
                c4[i + 2 * q] = new int[]{1 - c[i        ][0],  c[i        ][1]    }; // quadrant 3
                c4[i + 3 * q] = new int[]{    c[q - 1 - i][0],  c[q - 1 - i][1]    }; // quadrant 4
            }
            for (int i = q * 4 * fromArc / 360; i < q * 4 * toArc / 360; i++) {
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
    
    public void dot(int x, int y, int radius, boolean filled) {
        if (filled) {
            for (int r = radius; r >= 0; r--) circle(x, y, r);
        } else {
            circle(x, y, radius);
        }
    }
    
    public void arc(int x, int y, int innerRadius, int outerRadius, int fromArc, int toArc) {
        for (int r = innerRadius; r <= outerRadius; r++) circle(x, y, r, fromArc, toArc);
        
    }
    
    public void print(int x, int y, String message, boolean alignRight) {
        int xx = (alignRight) ? x : x - 6 * message.length();
        for (int i = 0; i < message.length(); i++) {
            print(xx, y, message.charAt(i));
            xx += 6;
        }
    }
    
    private static final int arcDist = 8;
    public void arcPrint(int cx, int cy, int radius, int angle, String message) {
        int x = cx + (int) ((radius + 1) * Math.cos(Math.PI * angle / 180));
        int y = cy - (int) ((radius + 1) * Math.sin(Math.PI * angle / 180));
        int yp = y + 3;
        if ((angle > arcDist) && (angle < 180 - arcDist)) yp = y;
        if ((angle > 180 + arcDist) && (angle < 360 - arcDist)) yp = y + 6;
        if ((angle > ( 90 - arcDist)) && (angle < ( 90 + arcDist))) yp -= 6;
        if ((angle > (270 - arcDist)) && (angle < (270 + arcDist))) yp += 6;
        int xp = x - 3 * message.length();
        if ((angle > (90 + arcDist)) && (angle < (270 - arcDist))) xp = x - 6 * message.length();
        if ((angle < (90 - arcDist)) || (angle > (270 + arcDist))) xp = x;
        print(xp, yp, message, true);
    }
    
    public void arcLine(int cx, int cy, int innerRadius, int outerRadius, int angle) {
        int xi = cx + (int) (innerRadius * Math.cos(Math.PI * angle / 180));
        int yi = cy - (int) (innerRadius * Math.sin(Math.PI * angle / 180));
        int xo = cx + (int) (outerRadius * Math.cos(Math.PI * angle / 180));
        int yo = cy - (int) (outerRadius * Math.sin(Math.PI * angle / 180));
        line(xi, yi, xo, yo);
    }
    
    public void arcDot(int cx, int cy, int arcRadius, int dotRadius, int angle) {
        int x = cx + (int) (arcRadius * Math.cos(Math.PI * angle / 180));
        int y = cy - (int) (arcRadius * Math.sin(Math.PI * angle / 180));
        dot(x, y, dotRadius, true);
    }
    
    public BufferedImage toImage(boolean complementary) {
        /*
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gs = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gs.getDefaultConfiguration();
        BufferedImage bi = gc.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
         */
        try {
            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D gr = bi.createGraphics();
            gr.setBackground(Color.white);
            gr.clearRect(0, 0, width, height);
            
            WritableRaster wr = bi.getRaster();
            long c;
            if (complementary) {
                int r, g, b;
                // then set pixels
                for (int i = width - 1; i >= 0; i--) {
                    for (int j = height - 1; j >= 0; j--) {
                        c = grid[i + j * width];
                        if (c >= 0) {
                            r = (int) (c >> 16);
                            g = (int) ((c >> 8) & 0xff);
                            b = (int) (c & 0xff);
                            wr.setPixel(i, j, new int[]{(0x1fe - g - b) / 2, (0x1fe - r - b) / 2, (0x1fe - r - g) / 2});
                        }
                    }
                }
            } else {
                for (int i = width - 1; i >= 0; i--) {
                    for (int j = height - 1; j >= 0; j--) {
                        c = grid[i + j * width];
                        if (c >= 0) {
                            wr.setPixel(i, j, new int[]{(int) (c >> 16), (int) ((c >> 8) & 0xff), (int) (c & 0xff)});
                        }
                    }
                }
            }
            return bi;
        } catch (Exception e) {
            // strange case where environment disallowes generation of graphics
            /*
             java.lang.InternalError: Can't connect to X11 window server using ':0.0' as the value of the DISPLAY variable.
             at sun.awt.X11GraphicsEnvironment.initDisplay(Native Method)
             at sun.awt.X11GraphicsEnvironment.access$000(X11GraphicsEnvironment.java:53)
             at sun.awt.X11GraphicsEnvironment$1.run(X11GraphicsEnvironment.java:142)
             at java.security.AccessController.doPrivileged(Native Method)
             at sun.awt.X11GraphicsEnvironment.<clinit>(X11GraphicsEnvironment.java:131)
             at java.lang.Class.forName0(Native Method)
             at java.lang.Class.forName(Class.java:164)
             at java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment(GraphicsEnvironment.java:68)
             at java.awt.image.BufferedImage.createGraphics(BufferedImage.java:1141)
             at de.anomic.tools.ImagePainter.toImage(ImagePainter.java:354)
             */
            System.out.println("Error with Graphics environment:");
            e.printStackTrace();
            return new BufferedImage(0, 0, BufferedImage.TYPE_INT_RGB);
        }
    }

}
