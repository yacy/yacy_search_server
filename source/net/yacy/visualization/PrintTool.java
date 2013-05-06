// PrintTool.java
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


public class PrintTool {


    private static long[] font = new long[]{
    	0x00000000000000L,0x00300C03000030L,0x00CC3300000000L,0x00CCFFCCCFFCCCL,0x02FFCC2FE0CFFEL,0x00C3CF0FC3CF0CL,0x02FCE02ECCE2ECL,0x00300C00000000L,
    	0x00030380C03803L,0x0300B00C0B0000L,0x0000332BA03000L,0x00000C0FC0C000L,0x0000000302C0C0L,0x0000000FC00000L,0x00000000000030L,0x00030383838380L,
    	0x00FCE3F33F2CFCL,0x00303C0300C0FCL,0x00FCE2CBEB83FFL,0x00FCE2C0EE2CFCL,0x00BCBB3FF0300CL,0x03FFC03FC02FFCL,0x00FCE03FEE2CFCL,0x03FF0B8380C030L,
    	0x00FCE2EFCE2CFCL,0x00FCE2EFF02CFCL,0x00000C0000C000L,0x00000C0002C0C0L,0x000E0E0E00E00EL,0x0000FFC00FFC00L,0x02C02C02C2C2C0L,0x00FC0B03000030L,
    	0x02FEFCFBEE00FCL,0x00B8BBB8BFFF03L,0x03F8CB3FEC2FFCL,0x00FCE0300E00FCL,0x03FCC2F03C2FFCL,0x03FFC03F0C03FFL,0x03FFC03F0C0300L,0x00FCE033FC2CFCL,
    	0x0303C0FFFC0F03L,0x03FF0C0300C3FFL,0x03FF00C03E2CFCL,0x030BCB3F0CB30BL,0x0300C0300C03FFL,0x0303F3FBBC8F03L,0x0383F8FBBCBF0BL,0x00FCE2F03E2CFCL,
    	0x03FCC2FFCC0300L,0x00FCE2F3BEB8FBL,0x03FCC2FFCCB30BL,0x00FEE00FC02EFCL,0x03FF0C0300C030L,0x0303C0F03E2CFCL,0x0303C0F8B3B030L,0x0303C0F23EECCCL,
    	0x038BBB8B8BBB8BL,0x0303B38B80C030L,0x03FF0B8B8B83FFL,0x003F0C0300C03FL,0x0380B80B80B80BL,0x03F00C0300C3F0L,0x00B8BBB8B00000L,0x000000000003FFL,
    	0x00E02C00000000L,0x0000002FFE0CFFL,0x0300C03FCC2FFCL,0x0000000FFE00FFL,0x000300CFFE0CFFL,0x0000BE32CEE0FFL,0x003C0E0FC0C030L,0x00002F8E32EFFCL,
    	0x0300C03BEFAF83L,0x0030000300C030L,0x0030000302C0E0L,0x0300C032FFE32FL,0x00300C0300C030L,0x0000000ECEEF33L,0x0000000FCE2F03L,0x0000000FCE2CFCL,
    	0x0000FF30BFF300L,0x00003FF833FC03L,0x00000B0380C030L,0x0000000BCBAFACL,0x00303F0300C030L,0x000000303E2CFCL,0x00000038BBB8B8L,0x000000333EECECL,
    	0x0000000EC2E0ECL,0x0000000EC2E030L,0x0000000FF2E3FCL,0x000F0B8B80B80FL,0x00300C0300C030L,0x03C0B80B8B83C0L,0x0000B83BB0B800L,0x03FFC0F03C0FFFL
    };

    private static void print(final RasterPlotter matrix, int x, int y, final int angle, final char letter) {
        final int index = letter - 0x20;
        if (index >= font.length) return;
        long character = font[index];
        long row;
        long c;
        for (int i = 0; i < 5; i++) {
            row = character & 0x3FFL;
            character = character >> 10;
            if (angle == 0) {
                for (int j = 0; j < 5; j++) {
                	c = row & 3L;
                    if (c == 3) matrix.plot(x + 5 - j, y, 100);
                    else if (c == 2) matrix.plot(x + 5 - j, y, 36);
                    row = row >> 2;
                }
                y--;
            }
            if (angle == 90) {
                for (int j = 0; j < 5; j++) {
                	c = row & 3L;
                    if (c == 3) matrix.plot(x, y - 5 + j, 100);
                    else if (c == 2) matrix.plot(x, y - 5 + j, 36);
                    row = row >> 2;
                }
                x--;
            }
            if (angle == 315) {
                for (int j = 0; j < 5; j++) {
                    c = row & 3L;
                    if (c == 3) { matrix.plot(x + 5 - j, y + 5 - j, 100); matrix.plot(x + 6 - j, y + 5 - j, 50); matrix.plot(x + 5 - j, y + 6 - j, 50); }
                    else if (c == 2) { matrix.plot(x + 5 - j, y + 5 - j, 36);  matrix.plot(x + 6 - j, y + 5 - j, 18); matrix.plot(x + 5 - j, y + 6 - j, 18); }
                    row = row >> 2;
                }
                x++;
                y--;
            }
        }
    }

    public static void print(final RasterPlotter matrix, final int x, final int y, final int angle, final String message, final int align) {
        // align = -1 : left
        // align =  1 : right
        // align =  0 : center
        int xx = 0, yy = 0;
        if (angle == 0) {
            xx = (align == -1) ? x : (align == 1) ? x - 6 * message.length() : x - 3 * message.length();
            yy = y;
        } else if (angle == 90) {
            xx = x;
            yy = (align == -1) ? y : (align == 1) ? y + 6 * message.length() : y + 3 * message.length();
        } else if (angle == 315) {
            xx = (align == -1) ? x : (align == 1) ? x - 6 * message.length() : x - 3 * message.length();
            yy = (align == -1) ? y : (align == 1) ? y - 6 * message.length() : y - 3 * message.length();
        }
        for (int i = 0; i < message.length(); i++) {
            print(matrix, xx, yy, angle, message.charAt(i));
            if (angle == 0) xx += 6;
            else if (angle == 90) yy -= 6;
            else if (angle == 315) {xx += 6; yy += 6;}
        }
    }


    private static final int arcDist = 8;
    /**
     * print a string at the distance of a circle
     * @param matrix the RasterPlotter
     * @param cx center of circle, x
     * @param cy center of circle, y
     * @param radius radius == distance of text from circle center
     * @param angle angle == position of text on a circle in distance of radius
     * @param message the message to be printed
     */
    public static void arcPrint(final RasterPlotter matrix, final int cx, final int cy, final int radius, final double angle, final String message) {
        final int x = cx + (int) ((radius + 1) * Math.cos(RasterPlotter.PI180 * angle));
        final int y = cy - (int) ((radius + 1) * Math.sin(RasterPlotter.PI180 * angle));
        int yp = y + 3;
        if ((angle > arcDist) && (angle < 180 - arcDist)) yp = y;
        if ((angle > 180 + arcDist) && (angle < 360 - arcDist)) yp = y + 6;
        if ((angle > ( 90 - arcDist)) && (angle < ( 90 + arcDist))) yp -= 6;
        if ((angle > (270 - arcDist)) && (angle < (270 + arcDist))) yp += 6;
        int xp = x - 3 * message.length();
        if ((angle > (90 + arcDist)) && (angle < (270 - arcDist))) xp = x - 6 * message.length();
        if ((angle < (90 - arcDist)) || (angle > (270 + arcDist))) xp = x;
        print(matrix, xp, yp, 0, message, -1);
    }


}
