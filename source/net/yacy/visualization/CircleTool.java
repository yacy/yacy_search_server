// CircleTool.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.05.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.visualization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class CircleTool {

    private static List<int[]> circles = new ArrayList<>();
    
    public static void clearcache() {
        circles.clear();
    }

    private static int[] getCircleCoords(final short radius) {
        if (radius - 1 < circles.size()) return circles.get(radius - 1);

        // read some lines from known circles
        Set<Integer> crds = new HashSet<>();
        Integer co;
        for (short i = (short) Math.max(0, circles.size() - 1); i < circles.size(); i++) {
            int[] circle = circles.get(i);
            for (int c: circle) crds.add(c);
        }

        // compute more lines in new circles
        short x, y;
        List<Integer> crc;
        short r1;
        int rc = radius < 200 ? (radius < 100 ? 100 : radius + 9) : radius;
        for (short r = (short) circles.size(); r < rc; r++) {
            r1 = (short) (r + 1);
            crc = new ArrayList<>();
            for (short a = 0; a < 2 * r1; a++) {
                double h = RasterPlotter.PI4 * a / r1;
                double cosh = Math.cos(h);
                double sinh = Math.sin(h);
                x = (short) (r1 * cosh);
                y = (short) (r1 * sinh);
                co = x << 16 | y;
                if (!(crds.contains(co))) {
                    crc.add(co);
                    crds.add(co);
                }
                x = (short) ((r + 0.5) * cosh);
                y = (short) ((r + 0.5) * sinh);
                co = x << 16 | y;
                if (!(crds.contains(co))) {
                    crc.add(co);
                    crds.add(co);
                }
            }
            // put coordinates into array
            //System.out.print("Radius " + r + " => " + crc.size() + " points: ");
            int[] newCircle = new int[crc.size() - 1];
            int coords;
            for (short i = 0; i < crc.size() - 1; i++) {
                coords = crc.get(i);
                newCircle[i] = coords;
            }
            circles.add(newCircle);
        }
        crc = null;
        crds = null;

        // finally return wanted slice
        return circles.get(radius - 1);
    }

    public static void circle(final RasterPlotter matrix, final int xc, final int yc, final int radius, final int intensity) {
        if (radius == 0) {
            //matrix.plot(xc, yc, 100);
        } else {
            final int[] c = getCircleCoords((short) radius);
            short x, y;
            short limit = (short) c.length;
            int co;
            for (short i = 0; i < limit; i++) {
                co = c[i];
                x = (short) (0xffff & (co >> 16));
                y = (short) (0xffff & co);
                matrix.plot(xc + x    , yc - y - 1, intensity); // quadrant 1
                matrix.plot(xc - x + 1, yc - y - 1, intensity); // quadrant 2
                matrix.plot(xc + x    , yc + y    , intensity); // quadrant 4
                matrix.plot(xc - x + 1, yc + y    , intensity); // quadrant 3
            }
        }
    }

    public static void circle(final RasterPlotter matrix, final int xc, final int yc, final int radius, int fromArc, int toArc) {
        // draws only a part of a circle
        // arc is given in degree
        while (fromArc > 360) fromArc -=360;
        while (fromArc < 0  ) fromArc +=360;
        while (  toArc > 360)   toArc -=360;
        while (  toArc < 0  )   toArc +=360;
        if (radius == 0) {
            //matrix.plot(xc, yc, 100);
        } else {
            int[] c = getCircleCoords((short) radius);
            if (c == null) c = getCircleCoords((short) radius);
            final short q = (short) c.length;
            final short q2 = (short) (q * 2);
            final short q3 = (short) (q * 3);
            final short q4 = (short) (q * 4);
            final short[] c4x = new short[q4];
            final short[] c4y = new short[q4];
            short a0, a1, a2, a3, b0, b1;
            int co;
            for (short i = 0; i < q; i++) {
                b0 = i;
                b1 = (short) (q - 1 - i);
                co = c[b0];
                a0 = (short) (0xffff & (co >> 16));
                a1 = (short) (0xffff & co);
                co = c[b1];
                a2 = (short) (0xffff & (co >> 16));
                a3 = (short) (0xffff & co);
                c4x[i     ] =     a0    ; // quadrant 1
                c4y[i     ] = (short) (-a1 - 1);  // quadrant 1
                c4x[i + q ] = (short) (  1 - a2); // quadrant 2
                c4y[i + q ] = (short) (-a3 - 1);  // quadrant 2
                c4x[i + q2] = (short) (  1 - a0); // quadrant 3
                c4y[i + q2] =     a1    ; // quadrant 3
                c4x[i + q3] =     a2    ; // quadrant 4
                c4y[i + q3] =     a3    ; // quadrant 4
            }
            if (fromArc == toArc) {
                int i = q4 * fromArc / 360;
                matrix.plot(xc + c4x[i], yc + c4y[i], 100);
            } else if (fromArc > toArc) {
                // draw two parts
                int from = q4 * fromArc / 360;
                int to   = q4 * toArc / 360;
                for (int i = from; i < q4; i++) {
                    matrix.plot(xc + c4x[i], yc + c4y[i], 100);
                }
                for (int i = 0; i < to; i++) {
                    matrix.plot(xc + c4x[i], yc + c4y[i], 100);
                }
            } else {
                // can be drawn in one part
                int from = q4 * fromArc / 360;
                int to   = q4 * toArc / 360;
                for (int i = from; i < to; i++) {
                    matrix.plot(xc + c4x[i], yc + c4y[i], 100);
                }
            }
        }
    }
}
