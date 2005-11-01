// ymageMatrixPainter.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 31.10.2005
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

package de.anomic.ymage;

import java.util.HashSet;
import java.util.ArrayList;

public class ymageMatrixPainter extends ymageMatrix implements ymagePainter {
    
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
    
    private static int[][] circles = new int[0][];
    
    public ymageMatrixPainter(int width, int height, String backgroundColor) {
        super(width, height, backgroundColor);
    }
    
    public ymageMatrixPainter(ymageMatrix matrix) {
        super(matrix);
    }
    
    private static int[] getCircleCoords(int radius) {
        if ((radius - 1) < circles.length) return circles[radius - 1];
        
        // read some lines from known circles
        HashSet crds = new HashSet();
        crds.add("0|0");
        String co;
        for (int i = Math.max(0, circles.length - 5); i < circles.length; i++) {
            for (int j = 0; j < circles[i].length; j = j + 2) {
                co = circles[i][j] + "|" + circles[i][j + 1];
                if (!(crds.contains(co))) crds.add(co);
            }
        }
        
        // copy old circles into new array
        int[][] newCircles = new int[radius + 30][];
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
            newCircles[r] = new int[2 * (crc.size() - 1)];
            int[] coords;
            for (int i = 0; i < crc.size() - 1; i++) {
                coords = (int[]) crc.get(i);
                newCircles[r][2 * i    ] = coords[0];
                newCircles[r][2 * i + 1] = coords[1];
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
    
    public void circle(int xc, int yc, int radius) {
        if (radius == 0) {
            plot(xc, yc);
        } else {
            int[] c = getCircleCoords(radius);
            int x, y;
            for (int i = (c.length / 2) - 1; i >= 0; i--) {
                x = c[2 * i    ];
                y = c[2 * i + 1];
                plot(xc + x    , yc - y - 1); // quadrant 1
                plot(xc - x + 1, yc - y - 1); // quadrant 2
                plot(xc + x    , yc + y    ); // quadrant 4
                plot(xc - x + 1, yc + y    ); // quadrant 3
            }
        }
    }
    
    public void circle(int xc, int yc, int radius, int fromArc, int toArc) {
        // draws only a part of a circle
        // arc is given in degree
        if (radius == 0) {
            plot(xc, yc);
        } else {
            int[] c = getCircleCoords(radius);
            int q = c.length / 2;
            int[][] c4 = new int[q * 4][];
            for (int i = 0; i < q; i++) {
                c4[i        ] = new int[]{    c[2 * (i        )], -c[2 * (i        ) + 1] - 1}; // quadrant 1
                c4[i +     q] = new int[]{1 - c[2 * (q - 1 - i)], -c[2 * (q - 1 - i) + 1] - 1}; // quadrant 2
                c4[i + 2 * q] = new int[]{1 - c[2 * (i        )],  c[2 * (i        ) + 1]    }; // quadrant 3
                c4[i + 3 * q] = new int[]{    c[2 * (q - 1 - i)],  c[2 * (q - 1 - i) + 1]    }; // quadrant 4
            }
            for (int i = q * 4 * fromArc / 360; i < q * 4 * toArc / 360; i++) {
                plot(xc + c4[i][0], yc + c4[i][1]);
            }
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
    
    private void print(int x, int y, int angle, char letter) {
        int index = (int) letter - 0x20;
        if (index >= font.length) return;
        long character = font[index];
        long row;
        for (int i = 0; i < 5; i++) {
            row = character & 0x1f;
            character = character >> 5;
            if (angle == 0) {
                for (int j = 0; j < 5; j++) {
                    if ((row & 1) == 1) plot(x + 5 - j, y);
                    row = row >> 1;
                }
                y--;
            }
            if (angle == 90) {
                for (int j = 0; j < 5; j++) {
                    if ((row & 1) == 1) plot(x, y - 5 + j);
                    row = row >> 1;
                }
                x--;
            }
        }
    }
    
    public void print(int x, int y, int angle, String message, boolean alignLeft) {
        int xx = 0, yy = 0;
        if (angle == 0) {
            xx = (alignLeft) ? x : x - 6 * message.length();
            yy = y;
        } else if (angle == 90) {
            xx = x;
            yy = (alignLeft) ? y : y + 6 * message.length();
        }
        for (int i = 0; i < message.length(); i++) {
            print(xx, yy, angle, message.charAt(i));
            if (angle == 0) xx += 6;
            else if (angle == 90) yy -= 6;
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
        print(xp, yp, 0, message, true);
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

}
