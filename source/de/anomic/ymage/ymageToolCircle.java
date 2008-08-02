// ymageToolCircle.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.05.2007 on http://yacy.net
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

package de.anomic.ymage;

import java.util.ArrayList;
import java.util.HashSet;

public class ymageToolCircle {    

    private static int[][] circles = new int[0][];

    
    private static int[] getCircleCoords(final int radius) {
        if ((radius - 1) < circles.length) return circles[radius - 1];
        
        // read some lines from known circles
        HashSet<String> crds = new HashSet<String>();
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
        ArrayList<int[]> crc;
        for (int r = circles.length; r < newCircles.length; r++) {
            crc = new ArrayList<int[]>();
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
                coords = crc.get(i);
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
    
    public static void circle(final ymageMatrix matrix, final int xc, final int yc, final int radius) {
        if (radius == 0) {
            matrix.plot(xc, yc);
        } else {
            final int[] c = getCircleCoords(radius);
            int x, y;
            for (int i = (c.length / 2) - 1; i >= 0; i--) {
                x = c[2 * i    ];
                y = c[2 * i + 1];
                matrix.plot(xc + x    , yc - y - 1); // quadrant 1
                matrix.plot(xc - x + 1, yc - y - 1); // quadrant 2
                matrix.plot(xc + x    , yc + y    ); // quadrant 4
                matrix.plot(xc - x + 1, yc + y    ); // quadrant 3
            }
        }
    }
    
    public static void circle(final ymageMatrix matrix, final int xc, final int yc, final int radius, final int fromArc, final int toArc) {
        // draws only a part of a circle
        // arc is given in degree
        if (radius == 0) {
            matrix.plot(xc, yc);
        } else {
            final int[] c = getCircleCoords(radius);
            final int q = c.length / 2;
            final int[][] c4 = new int[q * 4][];
            for (int i = 0; i < q; i++) {
                c4[i        ] = new int[]{    c[2 * (i        )], -c[2 * (i        ) + 1] - 1}; // quadrant 1
                c4[i +     q] = new int[]{1 - c[2 * (q - 1 - i)], -c[2 * (q - 1 - i) + 1] - 1}; // quadrant 2
                c4[i + 2 * q] = new int[]{1 - c[2 * (i        )],  c[2 * (i        ) + 1]    }; // quadrant 3
                c4[i + 3 * q] = new int[]{    c[2 * (q - 1 - i)],  c[2 * (q - 1 - i) + 1]    }; // quadrant 4
            }
            for (int i = q * 4 * fromArc / 360; i < q * 4 * toArc / 360; i++) {
                matrix.plot(xc + c4[i][0], yc + c4[i][1]);
            }
        }
    }
}
