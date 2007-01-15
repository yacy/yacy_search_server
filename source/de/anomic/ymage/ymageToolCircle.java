package de.anomic.ymage;

import java.util.ArrayList;
import java.util.HashSet;

public class ymageToolCircle {    

    private static int[][] circles = new int[0][];

    
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
    
    public static void circle(ymageMatrix matrix, int xc, int yc, int radius) {
        if (radius == 0) {
            matrix.plot(xc, yc);
        } else {
            int[] c = getCircleCoords(radius);
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
    
    public static void circle(ymageMatrix matrix, int xc, int yc, int radius, int fromArc, int toArc) {
        // draws only a part of a circle
        // arc is given in degree
        if (radius == 0) {
            matrix.plot(xc, yc);
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
                matrix.plot(xc + c4[i][0], yc + c4[i][1]);
            }
        }
    }
}
