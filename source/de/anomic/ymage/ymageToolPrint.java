package de.anomic.ymage;

public class ymageToolPrint {

    
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

    private static void print(ymageMatrix matrix, int x, int y, int angle, char letter) {
        int index = (int) letter - 0x20;
        if (index >= font.length) return;
        long character = font[index];
        long row;
        for (int i = 0; i < 5; i++) {
            row = character & 0x1f;
            character = character >> 5;
            if (angle == 0) {
                for (int j = 0; j < 5; j++) {
                    if ((row & 1) == 1) matrix.plot(x + 5 - j, y);
                    row = row >> 1;
                }
                y--;
            }
            if (angle == 90) {
                for (int j = 0; j < 5; j++) {
                    if ((row & 1) == 1) matrix.plot(x, y - 5 + j);
                    row = row >> 1;
                }
                x--;
            }
        }
    }
    
    public static void print(ymageMatrix matrix, int x, int y, int angle, String message, int align) {
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
        }
        for (int i = 0; i < message.length(); i++) {
            print(matrix, xx, yy, angle, message.charAt(i));
            if (angle == 0) xx += 6;
            else if (angle == 90) yy -= 6;
        }
    }
    
    
    private static final int arcDist = 8;
    public static void arcPrint(ymageMatrix matrix, int cx, int cy, int radius, int angle, String message) {
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
        print(matrix, xp, yp, 0, message, -1);
    }
    
    
}
