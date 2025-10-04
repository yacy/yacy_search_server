/**
 *  PrintTool
 *  Copyright 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 22.05.2007 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.visualization;

import java.io.File;
import java.io.IOException;


public class PrintTool {

    private static long[] font5 = new long[]{
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
    
    private static long[] font6 = new long[] {
        0x000000000000L,0x008208200200L,0x012480000000L,0x012FD24BF480L,0x008FA8F8AF88L,0x031C842138C0L,0x008508562740L,0x004200000000L,
        0x004208208100L,0x008104104200L,0x0002847C4280L,0x0001047C4100L,0x000000008210L,0x0000007C0000L,0x00000000C300L,0x000084210800L,
        0x01E8E5A71780L,0x00C5041047C0L,0x01E8417A0FC0L,0x01E846061780L,0x00431493F100L,0x03F83E061780L,0x01E83E861780L,0x03F042108200L,
        0x01E85E861780L,0x01E8617C1780L,0x000008000200L,0x000200008210L,0x000108408100L,0x00001F01F000L,0x000204084200L,0x01E842100100L,
        0x01E96BBE0780L,0x01E861FE1840L,0x03E87E861F80L,0x01E860821780L,0x03C8A1862F00L,0x03F83E820FC0L,0x03F83E820800L,0x01E8609E1780L,
        0x02187F861840L,0x01F1041047C0L,0x001041861780L,0x022938922840L,0x020820820FC0L,0x021CED861840L,0x021C69963840L,0x01E861861780L,
        0x03E861FA0800L,0x01E861A65780L,0x03E861FA2840L,0x01E81E061780L,0x01F104104100L,0x021861861780L,0x021861852300L,0x02186186D480L,
        0x02148C312840L,0x01144A104100L,0x03F084210FC0L,0x0071041041C0L,0x000810204080L,0x038208208E00L,0x008522000000L,0x00000000003FL,
        0x008100000000L,0x0007027A2780L,0x01041E451780L,0x000390410380L,0x00209E8A2780L,0x000722F20780L,0x00620C208200L,0x0007A289E09CL,
        0x02083C8A2880L,0x008018208700L,0x00200208248CL,0x010518614480L,0x008208208180L,0x000D2AAAAA80L,0x000F228A2880L,0x0007228A2700L,
        0x000F228BC820L,0x0007A289E083L,0x000390410400L,0x000720702F00L,0x008708208100L,0x0008A28A2700L,0x0008A2514200L,0x0008AAAAA500L,
        0x000894214880L,0x0008A289E09CL,0x000F84210F80L,0x0071381041C0L,0x004104104100L,0x038207208E00L,0x000294000000L,0x03F86186187FL
    };
    
    private static long[] font6_bold = new long[] {
        0x000000000000L,0x00C30C30C00CL,0x033CC0000000L,0x01A6BF6BF69AL,0x00C7E0781F8CL,0x031CC6318CE3L,0x01CD9C637D9FL,0x00C30C000000L,
        0x006318618306L,0x018306186318L,0x000CCCFCCCC0L,0x00030CFCC300L,0x00000000C318L,0x000000FC0000L,0x00000000C300L,0x0000C6318C00L,
        0x01ECF7EF3CDEL,0x00C31C30C33FL,0x01ECC3198C3FL,0x01ECC3383CDEL,0x00639EDBF186L,0x03FC3E0C3CDEL,0x01ECF0FB3CDEL,0x03FCC630C30CL,
        0x01ECF37B3CDEL,0x01ECF37C3CDEL,0x00000C30030CL,0x00000C300318L,0x003198C18183L,0x00003F03F000L,0x0306060C6630L,0x01ECC318C00CL,
        0x01ECF7DF0C5EL,0x00C7B3FF3CF3L,0x03ECF3FB3CFEL,0x01ECF0C30CDEL,0x03CDB3CF3DBCL,0x03FC30F30C3FL,0x03FC30F30C30L,0x01ECF0DF3CDEL,
        0x033CF3FF3CF3L,0x01E30C30C31EL,0x00F186186D9CL,0x033DBCC3CDB3L,0x030C30C30C3FL,0x031EFFD71C71L,0x033EFFFF7CF3L,0x01ECF3CF3CDEL,
        0x03ECF3FB0C30L,0x01ECF3CF3787L,0x03ECF3FBCDB3L,0x01ECF0783CDEL,0x03F30C30C30CL,0x033CF3CF3CDEL,0x033CF3CF378CL,0x031C71C75EF1L,
        0x033CDE31ECF3L,0x033CDE30C30CL,0x03F0C6318C3FL,0x01E61861861EL,0x000C183060C0L,0x01E18618619EL,0x00C7B3000000L,0x00000000003FL,
        0x018300000000L,0x00001E0DFCDFL,0x000C30FB3CFEL,0x00001EC30C1EL,0x0000C37F3CDFL,0x00001ECFFC1EL,0x0001CC7CC30CL,0x00001FCDF0FEL,
        0x000C30FB3CF3L,0x00C01C30C31EL,0x0060061861BCL,0x000C30DBCDB3L,0x00070C30C31EL,0x000036FFFD71L,0x00003ECF3CF3L,0x00001ECF3CDEL,
        0x00003ECFEC30L,0x00001FCDF0C3L,0x00003ECF0C30L,0x00001FC1E0FEL,0x00033F30C307L,0x000033CF3CDFL,0x000033CF378CL,0x000031D7F7DBL,
        0x00003378C7B3L,0x000033CDF1BCL,0x00003F18C63FL,0x00730CE0C307L,0x00C30C30C30CL,0x03830C1CC338L,0x000010B42000L,0x03F86186187FL
    };
    
    private static long[] font7 = new long[] {
        0x00000000000000L,0x10204081000400L,0x28500000000000L,0x2853F94FE50A00L,0x10FE43E13F8400L,0xC388208208E180L,0x61218119511C80L,0x10200000000000L,
        0x08208102020200L,0x10101020410400L,0x10A8E7F3894400L,0x002043E1020000L,0x00000000030604L,0x000003E0000000L,0x00000000030600L,0x02082082082000L,
        0x7D0E2C9A385F00L,0x10614081021F00L,0x790810C2083F00L,0x7D0408E0305F00L,0x04185127E08100L,0x7E8103E0205F00L,0x790A07C8509E00L,0x7C081041020400L,
        0x388911C4488E00L,0x388911E0488E00L,0x00004080020400L,0x00004080020410L,0x06318406030180L,0x0000F003C00000L,0xC0603010C63000L,0x38881041000400L,
        0x7D063C98F01F80L,0x7D060FF8306080L,0xFD060FE8307F00L,0x3C860408084F00L,0xF90A0C1830BE00L,0xFF0207E8103F80L,0xFF0207E8102000L,0x7D0204F8305F00L,
        0x83060FF8306080L,0x38204081020E00L,0x1C102040911C00L,0x871A670991A180L,0x81020408103F80L,0x838EAC98306080L,0x83868C98B0E080L,0x7D060C18305F00L,
        0xFD060FE8102000L,0x7D060C18315F01L,0xFD060FE890A080L,0x7D0603E0305F00L,0xFE204081020400L,0x83060C18305F00L,0x83060C14450400L,0x83060C19355100L,
        0x8288A08288A080L,0x8288A081020400L,0xFE082082083F80L,0x3C408102040F00L,0x80808080808080L,0x3C081020408F00L,0x10511000000000L,0x00000000003F80L,
        0x10100000000000L,0x0001E027D09E80L,0x010207E8307F00L,0x0001FC08101F80L,0x00040BF8305F80L,0x0001F41FF01F00L,0x18408382040800L,0x0001F4182FC0BEL,
        0x010207E8306080L,0x00200181020E00L,0x001000C0810218L,0x01021CCE132180L,0x00604081020E00L,0x0001B499326480L,0x0001F418306080L,0x0001F418305F00L,
        0x0003F4183FA040L,0x0001FC182FC081L,0x0002F618102000L,0x0001F407C05F00L,0x002043E1020300L,0x00020C18305F00L,0x00020C14450400L,0x00020C19355100L,
        0x00020B610DA080L,0x00020C182FC0BEL,0x0003F8610C3F80L,0x18408602040600L,0x10204081020408L,0x30102030810C00L,0x000082D0400000L,0xFF060C18307F80L
    };

    private static void print5(final RasterPlotter matrix, int x, int y, final int angle, final char letter, int intensity) {
        final int index = letter - 0x20;
        if (index < 0 || index >= font5.length || intensity <= 0) return;
        long character = font5[index];
        long bits;
        long c;
        int i3 = intensity / 3;
        for (int row = 0; row < 5; row++) {
            bits = character & 0x3FFL; // 10 bits per row
            character = character >> 10; // next row
            if (angle == 0) {
                for (int col = 0; col < 5; col++) {
                	c = bits & 3L;
                    if (c == 3) matrix.plot(x + 5 - col, y, intensity);
                    else if (c == 2) matrix.plot(x + 5 - col, y, i3);
                    bits = bits >> 2;
                }
                y--;
            }
            if (angle == 90) {
                for (int col = 0; col < 5; col++) {
                	c = bits & 3L;
                    if (c == 3) matrix.plot(x, y - 5 + col, intensity);
                    else if (c == 2) matrix.plot(x, y - 5 + col, i3);
                    bits = bits >> 2;
                }
                x--;
            }
            if (angle == 315) {
                int i2 = intensity / 2;
                int i5 = intensity / 5;
                for (int col = 0; col < 5; col++) {
                    c = bits & 3L;
                    if (c == 3) { matrix.plot(x + 5 - col, y + 5 - col, intensity); matrix.plot(x + 6 - col, y + 5 - col, i2); matrix.plot(x + 5 - col, y + 6 - col, i2); }
                    else if (c == 2) { matrix.plot(x + 5 - col, y + 5 - col, i3);  matrix.plot(x + 6 - col, y + 5 - col, i5); matrix.plot(x + 5 - col, y + 6 - col, i5); }
                    bits = bits >> 2;
                }
                x++;
                y--;
            }
        }
    }

    private static void print6(final RasterPlotter matrix, int x, int y, final int angle, final char letter, final int intensity, boolean bold, boolean invers) {
        final int index = letter - 0x20;
        if (index < 0 || index >= font6.length || intensity <= 0) return;

        long character = bold ? font6_bold[index] : font6[index];
        long p = invers ? 0L : 1L;

        if (invers) {
            if (angle == 0) {
                for (int col = 0; col < 7; col++) matrix.plot(x + col, y + 1, intensity); // lower line
                for (int row = 0; row < 8; row++) matrix.plot(x - 1, y + 1 - row, intensity); // left line
            } else if (angle == 90) {
                for (int col = 0; col < 7; col++) matrix.plot(x + 1, y + col, intensity); // right line
                for (int row = 0; row < 8; row++) matrix.plot(x, y - 1, 50); // lower line
            } else if (angle == 315) {
                for (int col = 0; col < 7; col++) matrix.plot(x + col - 1, y + col, intensity);
                for (int row = 0; row < 8; row++) matrix.plot(x + row - 1, y - row - 1, 50);
            }
        }
        for (int row = 0; row < 7; row++) { // we have 7 rows!
            long bits = character & 0x3FL; // 6 bits per row; first row is lowest row
            character >>= 6; // next row

            if (angle == 0) {
                for (int col = 0; col < 6; col++) {
                    if ((bits & 1L) == p) matrix.plot(x + 5 - col, y, intensity);
                    bits >>= 1;
                }
                if (invers) matrix.plot(x + 6, y, intensity); // right line
                y--; // next row is up
            } else if (angle == 90) {
                for (int col = 0; col < 6; col++) {
                    if ((bits & 1L) == p) matrix.plot(x, y - 5 + col, intensity);
                    bits >>= 1;
                }
                if (invers) matrix.plot(x, y - 6, intensity); // top line
                x--; // next row goes to the left
            } else if (angle == 315) {
                final int i2 = intensity / 2;
                for (int col = 0; col < 6; col++) {
                    if ((bits & 1L) == p) {
                        final int px = x + 5 - col;
                        final int py = y + 5 - col;
                        matrix.plot(px, py, intensity);
                        if (i2 > 0) {
                            matrix.plot(px + 1, py, i2);
                            matrix.plot(px, py + 1, i2);
                        }
                    }
                    bits >>= 1;
                }
                if (invers) matrix.plot(x + 6, y + 6, intensity);
                x++; y--; // next row goes up and right
            }
        }
    }

    public static void print5(final RasterPlotter matrix, final int x, final int y, final int angle, final String message, final int align, int intensity) {
        // align = -1 : left
        // align =  1 : right
        // align =  0 : center
        final int size = 6;
        final int halfStep = size / 2;
        int xx = 0, yy = 0;

        if (angle == 0) {
            xx = (align == -1) ? x : (align == 1) ? x - 6 * message.length() : x - halfStep * message.length();
            yy = y;
        } else if (angle == 90) {
            xx = x;
            yy = (align == -1) ? y : (align == 1) ? y + 6 * message.length() : y + halfStep * message.length();
        } else if (angle == 315) {
            xx = (align == -1) ? x : (align == 1) ? x - 6 * message.length() : x - halfStep * message.length();
            yy = (align == -1) ? y : (align == 1) ? y - 6 * message.length() : y - halfStep * message.length();
        }
        for (int i = 0; i < message.length(); i++) {
            print5(matrix, xx, yy, angle, message.charAt(i), intensity);
            if (angle == 0) xx += size;
            else if (angle == 90) yy -= size;
            else if (angle == 315) {xx += size; yy += size;}
        }
    }

    public static void print6(final RasterPlotter matrix, final int x, final int y, final int angle, final String message, final int align, final int intensity, boolean bold, boolean invers) {
        if (message == null || message.isEmpty() || intensity <= 0) return;

        final int size = 7; // 6+1, we make 1 pixel space between each character
        int xx = x, yy = y;

        if (angle == 0) {
            xx = (align == -1) ? x : (align == 1) ? x - size * message.length() : x - size * message.length() / 2;
            yy = y;
        } else if (angle == 90) {
            xx = x;
            yy = (align == -1) ? y : (align == 1) ? y + size * message.length() : y + size * message.length() / 2;
        } else if (angle == 315) {
            xx = (align == -1) ? x : (align == 1) ? x - size * message.length() : x - size * message.length() / 2;
            yy = (align == -1) ? y : (align == 1) ? y - size * message.length() : y - size * message.length() / 2;
        }

        for (int i = 0; i < message.length(); i++) {
            print6(matrix, xx, yy, angle, message.charAt(i), intensity, bold, invers);
            if (angle == 0) xx += size;
            else if (angle == 90) yy -= size;
            else if (angle == 315) {xx += size; yy += size;}
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
    public static void arcPrint5(final RasterPlotter matrix, final int cx, final int cy, final int radius, final double angle, final String message, final int intensity) {
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
        print5(matrix, xp, yp, 0, message, -1, intensity);
    }
    
    public static void arcPrint6(final RasterPlotter matrix, final int cx, final int cy, final int radius, final double angle, final String message, final int intensity, boolean bold, boolean invers) {
        final int x = cx + (int) ((radius + 1) * Math.cos(RasterPlotter.PI180 * angle));
        final int y = cy - (int) ((radius + 1) * Math.sin(RasterPlotter.PI180 * angle));
        int yp = y + 4;
        if ((angle > arcDist) && (angle < 180 - arcDist)) yp = y;
        if ((angle > 180 + arcDist) && (angle < 360 - arcDist)) yp = y + 8;
        if ((angle > ( 90 - arcDist)) && (angle < ( 90 + arcDist))) yp -= 8;
        if ((angle > (270 - arcDist)) && (angle < (270 + arcDist))) yp += 8;
        int xp = x - 4 * message.length();
        if ((angle > (90 + arcDist)) && (angle < (270 - arcDist))) xp = x - 8 * message.length();
        if ((angle < (90 - arcDist)) || (angle > (270 + arcDist))) xp = x;
        print6(matrix, xp, yp, 0, message, -1, intensity, bold, invers);
    }


    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");

        final File target = determineTarget(args);
        final RasterPlotter canvas = new RasterPlotter(360, 260, RasterPlotter.DrawMode.MODE_REPLACE, 0x000010);

        boolean bold = true;
        boolean invers = true;
        canvas.setColor(0x00FFD0);
        print5(canvas, 12, 60, 0, "print5 left", -1, 90);
        canvas.setColor(0xFFAA40);
        print5(canvas, 348, 100, 0, "print5 right", 1, 90);
        canvas.setColor(0x66FFAA);
        print5(canvas, 24, 240, 90, "vertical5", -1, 90);
        canvas.setColor(0xFF6699);
        print5(canvas, 40, 150, 315, "diag5", -1, 90);
        canvas.setColor(0x99DDFF);
        arcPrint5(canvas, 260, 70, 40, 210, "arcPrint5", 90);

        canvas.setColor(0xFFFFFF);
        print6(canvas, 12, 120, 0, "print6 demo", -1, 100, bold, invers);
        canvas.setColor(0x00CCFF);
        print6(canvas, 348, 180, 0, "print6 right", 1, 90, bold, invers);
        canvas.setColor(0xFFCC00);
        print6(canvas, 330, 240, 90, "vertical6", -1, 90, bold, invers);
        canvas.setColor(0xFF66FF);
        print6(canvas, 140, 120, 315, "diagonal6", -1, 90, bold, invers);

        try {
            if (target.getParentFile() != null) target.getParentFile().mkdirs();
            canvas.save(target, "png");
        } catch (final IOException e) {
            throw new RuntimeException("Unable to save PrintTool demo image", e);
        }
    }

    private static File determineTarget(final String[] args) {
        if (args != null && args.length > 0) {
            final File supplied = new File(args[0]);
            if (supplied.isDirectory()) return new File(supplied, "printtool-demo.png");
            return supplied;
        }
        return new File("printtool-demo.png");
    }


}
