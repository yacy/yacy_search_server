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
    
    private static long[] font7 = new long[] {
        0x0000000000000000L,0x0010204081000400L,0x0028500000000000L,0x002853F94FE50A00L,0x0018FE63E1BF8400L,0x0062C8208208E180L,0x0061218119511C80L,0x0010200000000000L,
        0x0008208102020200L,0x0010101020410400L,0x0010A8E7F3894400L,0x00002043E1020000L,0x0000000000030604L,0x00000003E0000000L,0x0000000000030600L,0x0002082082082000L,
        0x007D0E2C9A385F00L,0x0010614081021F00L,0x0038881041041F00L,0x00388810C0488E00L,0x0004185127E08100L,0x007E8103E0205F00L,0x00388903C4488E00L,0x007C081041020400L,
        0x00388911C4488E00L,0x00388911E0488E00L,0x00000060C0030600L,0x00000060C0030604L,0x0006318406030180L,0x000000F003C00000L,0x00C0603010C63000L,0x0038881041000400L,
        0x007D063C98F01F80L,0x007D060FF8306080L,0x00FD060FE8307F00L,0x003C860408084F00L,0x00F90A0C1830BE00L,0x00FF0207E8103F80L,0x00FF0207E8102000L,0x007D0204F8305F00L,
        0x0083060FF8306080L,0x0038204081020E00L,0x001C102040911C00L,0x00871A670991A180L,0x0081020408103F80L,0x00838EAC98306080L,0x0083868C98B0E080L,0x007D060C18305F00L,
        0x00FD060FE8102000L,0x007D060C18315F01L,0x00FD060FE890A080L,0x007D0603E0305F00L,0x00FE204081020400L,0x0083060C18305F00L,0x0083060C14450400L,0x0083060C19355100L,
        0x008288A08288A080L,0x008288A081020400L,0x00FE082082083F80L,0x003C408102040F00L,0x0080808080808080L,0x003C081020408F00L,0x0010511000000000L,0x0000000000003F80L,
        0x0010100000000000L,0x000001E027D09E80L,0x00810207E8307F00L,0x000001FC08101F80L,0x0002040BF8305F80L,0x000001F41FF01F00L,0x0018408382040800L,0x000001F4182FC0BEL,
        0x00810207E8306080L,0x0000200181020E00L,0x00001000C0810218L,0x0081021CCE132180L,0x0030204081020E00L,0x000001B499326480L,0x000001F418306080L,0x000001F418305F00L,
        0x000003F4183FA040L,0x000001FC182FC081L,0x000002F618102000L,0x000001F407C05F00L,0x00102043E1020300L,0x0000020C18305F00L,0x0000020C14450400L,0x0000020C19355100L,
        0x0000020B610DA080L,0x0000020C182FC0BEL,0x000003F8610C3F80L,0x0018408602040600L,0x0010204081020408L,0x0030102030810C00L,0x00000082D0400000L,0x00FF060C18307F80L
    };

    private static void print5(final RasterPlotter matrix, int x, int y, final int angle, final char letter, int intensity) {
        final int index = letter - 0x20;
        if (index < 0 || index >= font5.length || intensity <= 0) return;
        long character = font5[index];
        long bits;
        long c;
        int i2 = intensity / 2;
        int i3 = intensity / 3;
        int i5 = intensity / 5;
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

    private static void print7(final RasterPlotter matrix, int x, int y, final int angle, final char letter, final int intensity) {
        final int index = letter - 0x20;
        if (index < 0 || index >= font7.length || intensity <= 0) return;

        long character = font7[index];
        final int i2 = intensity / 2;

        for (int row = 0; row < 8; row++) {
            long bits = character & 0x7FL; // 7 bits per row
            character >>= 7; // next row

            if (angle == 0) {
                for (int col = 0; col < 7; col++) {
                    if ((bits & 1L) != 0L) matrix.plot(x + 7 - col, y, intensity);
                    bits >>= 1;
                }
                y--;
            } else if (angle == 90) {
                for (int col = 0; col < 7; col++) {
                    if ((bits & 1L) != 0L) matrix.plot(x, y - 7 + col, intensity);
                    bits >>= 1;
                }
                x--;
            } else if (angle == 315) {
                for (int col = 0; col < 7; col++) {
                    if ((bits & 1L) != 0L) {
                        final int px = x + 7 - col;
                        final int py = y + 7 - col;
                        matrix.plot(px, py, intensity);
                        if (i2 > 0) {
                            matrix.plot(px + 1, py, i2);
                            matrix.plot(px, py + 1, i2);
                        }
                    }
                    bits >>= 1;
                }
                x++;
                y--;
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

    public static void print7(final RasterPlotter matrix, final int x, final int y, final int angle, final String message, final int align, final int intensity) {
        if (message == null || message.isEmpty() || intensity <= 0) return;

        final int size = 8;
        final int halfStep = size / 2;
        int xx = x, yy = y;

        if (angle == 0) {
            xx = (align == -1) ? x : (align == 1) ? x - size * message.length() : x - halfStep * message.length();
            yy = y;
        } else if (angle == 90) {
            xx = x;
            yy = (align == -1) ? y : (align == 1) ? y + size * message.length() : y + halfStep * message.length();
        } else if (angle == 315) {
            xx = (align == -1) ? x : (align == 1) ? x - size * message.length() : x - halfStep * message.length();
            yy = (align == -1) ? y : (align == 1) ? y - size * message.length() : y - halfStep * message.length();
        }

        for (int i = 0; i < message.length(); i++) {
            print7(matrix, xx, yy, angle, message.charAt(i), intensity);
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
    
    public static void arcPrint7(final RasterPlotter matrix, final int cx, final int cy, final int radius, final double angle, final String message, final int intensity) {
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
        print7(matrix, xp, yp, 0, message, -1, intensity);
    }


    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");

        final File target = determineTarget(args);
        final RasterPlotter canvas = new RasterPlotter(360, 260, RasterPlotter.DrawMode.MODE_REPLACE, 0x000010);

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
        print7(canvas, 12, 120, 0, "print7 demo", -1, 100);
        canvas.setColor(0x00CCFF);
        print7(canvas, 340, 180, 0, "print7 right", 1, 90);
        canvas.setColor(0xFFCC00);
        print7(canvas, 330, 240, 90, "vertical7", -1, 90);
        canvas.setColor(0xFF66FF);
        print7(canvas, 140, 210, 315, "diagonal7", -1, 90);

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
