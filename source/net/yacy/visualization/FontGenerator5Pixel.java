/**
 *  FontGenerator5Pixel
 *  Copyright 2005 by Michael Christen
 *  First released 31.10.2005 at https://yacy.net
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

public class FontGenerator5Pixel { 

    /**
     * font: a character matrix of 96 characters:
     * 
     *  0x20: !"#$%&'
     *  0x28:()*+,-./
     *  0x30:01234567
     *  0x38:89:;<=>?
     *  0x40:@ABCDEFG
     *  0x48:HIJKLMNO
     *  0x50:PQRSTUVW
     *  0x58:XYZ[\]^_
     *  0x60:`abcdefg
     *  0x68:hijklmno
     *  0x70:pqrstuvw
     *  0x78:xyz{|}~
     */
    private static final String[] font = {
        ".....",  "..X..",  ".X.X.",  ".X.X.",  "+XXXX",  ".X..X",  "+XXX.",  "..X..",  
        ".....",  "..X..",  ".X.X.",  "XXXXX",  "X.X..",  "X.XX.",  "X+...",  "..X..",  
        ".....",  "..X..",  ".....",  ".X.X.",  "+XXX+",  ".XXX.",  "+X+X.",  ".....",  
        ".....",  ".....",  ".....",  "XXXXX",  "..X.X",  ".XX.X",  "X.X+.",  ".....",  
        ".....",  "..X..",  ".....",  ".X.X.",  "XXXX+",  "X..X.",  "+X+X.",  ".....",  
    
        "....X",  "X....",  ".....",  ".....",  ".....",  ".....",  ".....",  "....X",  
        "...X+",  "+X...",  ".X.X.",  "..X..",  ".....",  ".....",  ".....",  "...X+",  
        "...X.",  ".X...",  "++X++",  ".XXX.",  "..X..",  ".XXX.",  ".....",  "..X+.",  
        "...X+",  "+X...",  ".x.X.",  "..X..",  ".+X..",  ".....",  ".....",  ".X+..",  
        "....X",  "x....",  ".....",  ".....",  ".X...",  ".....",  "..X..",  "X+...",  
    
        ".XXX.",  "..X..",  ".XXX.",  ".XXX.",  ".+XX.",  "XXXXX",  ".XXX.",  "XXXXX",  
        "X+.XX",  ".XX..",  "X+.+X",  "X+.+X",  "+X+X.",  "X....",  "X+...",  "..+X+",  
        "X.X.X",  "..X..",  ".+XX+",  "...X+",  "XXXXX",  "XXXX.",  "XXXX+",  "..X+.",  
        "XX.+X",  "..X..",  "+X+..",  "X+.+X",  "...X.",  "...+X",  "X+.+X",  "..X..",  
        ".XXX.",  ".XXX.",  "XXXXX",  ".XXX.",  "...X.",  "XXXX.",  ".XXX.",  "..X..",  
    
        ".XXX.",  ".XXX.",  ".....",  ".....",  "...X+",  ".....",  "+X...",  ".XXX.",  
        "X+.+X",  "X+.+X",  "..X..",  "..X..",  "..X+.",  "XXXXX",  ".+X..",  "..+X.",  
        "+XXX.",  "+XXXX",  ".....",  ".....",  ".X+..",  ".....",  "..+X.",  "..X..",  
        "X+.+X",  "...+X",  "..X..",  ".+X..",  "..X+.",  "XXXXX",  ".+X..",  ".....",  
        ".XXX.",  ".XXX.",  ".....",  ".X...",  "...X+",  ".....",  "+X...",  "..X..",  
    
        "+XXX+",  ".+X+.",  "XXX+.",  ".XXX.",  "XXXX.",  "XXXXX",  "XXXXX",  ".XXX.",  
        "XXX.X",  "+X+X+",  "X.+X.",  "X+...",  "X..+X",  "X....",  "X....",  "X+...",  
        "X+XX+",  "X+.+X",  "XXXX+",  "X....",  "X...X",  "XXX..",  "XXX..",  "X.XXX",  
        "X+...",  "XXXXX",  "X..+X",  "X+...",  "X..+X",  "X....",  "X....",  "X..+X",  
        ".XXX.",  "X...X",  "XXXX.",  ".XXX.",  "XXXX.",  "XXXXX",  "X....",  ".XXX.",  
    
        "X...X",  "XXXXX",  "XXXXX",  "X..+X",  "X....",  "X...X",  "X+..X",  ".XXX.",  
        "X...X",  "..X..",  "....X",  "X.+X.",  "X....",  "XX.XX",  "XX+.X",  "X+.+X",  
        "XXXXX",  "..X..",  "....X",  "XXX..",  "X....",  "X+X+X",  "X+X+X",  "X...X",  
        "X...X",  "..X..",  "X+.+X",  "X.+X.",  "X....",  "X.+.X",  "X.+XX",  "X+.+X",  
        "X...X",  "XXXXX",  ".XXX.",  "X..+X",  "XXXXX",  "X...X",  "X..+X",  ".XXX.",  
    
        "XXXX.",  ".XXX.",  "XXXX.",  ".XXX+",  "XXXXX",  "X...X",  "X...X",  "X...X",  
        "X..+X",  "X+.+X",  "X..+X",  "X+...",  "..X..",  "X...X",  "X...X",  "X...X",  
        "XXXX.",  "X.X+X",  "XXXX.",  ".XXX.",  "..X..",  "X...X",  "X+.+X",  "X.+.X",  
        "X....",  "X++X+",  "X.+X.",  "...+X",  "..X..",  "X+.+X",  ".X+X.",  "X+X+X",  
        "X....",  ".XX+X",  "X..+X",  "+XXX.",  "..X..",  ".XXX.",  "..X..",  ".X.X.",  
    
        "X+.+X",  "X...X",  "XXXXX",  "..XXX",  "X+...",  "XXX..",  ".+X+.",  ".....",  
        "+X+X+",  "+X.X+",  "..+X+",  "..X..",  "+X+..",  "..X..",  "+X+X+",  ".....",  
        ".+X+.",  ".+X+.",  ".+X+.",  "..X..",  ".+X+.",  "..X..",  "X+.+X",  ".....",  
        "+X+X+",  "..X..",  "+X+..",  "..X..",  "..+X+",  "..X..",  ".....",  ".....",  
        "X+.+X",  "..X..",  "XXXXX",  "..XXX",  "...+X",  "XXX..",  ".....",  "XXXXX",  
    
        ".X+..",  ".....",  "X....",  ".....",  "....X",  ".....",  "..XX.",  ".....",  
        ".+X..",  ".....",  "X....",  ".....",  "....X",  "+XX+.",  "..X+.",  ".+XX+",  
        ".....",  "+XXXX",  "XXXX.",  ".XXXX",  ".XXXX",  "X.+X.",  ".XXX.",  ".X+.X",  
        ".....",  "X+..X",  "X..+X",  "X+...",  "X+..X",  "X+X+.",  "..X..",  ".+X+X",  
        ".....",  ".XXXX",  "XXXX.",  ".XXXX",  ".XXXX",  ".XXXX",  "..X..",  "XXXX.",  
    
        "X....",  "..X..",  "..X..",  "X....",  "..X..",  ".....",  ".....",  ".....",  
        "X....",  ".....",  ".....",  "X....",  "..X..",  ".....",  ".....",  ".....",  
        "X+XX+",  "..X..",  "..X..",  "X.+XX",  "..X..",  ".X+X.",  ".XXX.",  ".XXX.",  
        "XX++X",  "..X..",  ".+X..",  "XXX+.",  "..X..",  "X+X+X",  "X+.+X",  "X+.+X",  
        "X+..X",  "..X..",  ".X+..",  "X.+XX",  "..X..",  "X.X.X",  "X...X",  ".XXX.",  
    
        ".....",  ".....",  ".....",  ".....",  "..X..",  ".....",  ".....",  ".....",  
        "XXXX.",  ".XXXX",  "..+X.",  ".....",  ".XXX.",  ".....",  ".....",  ".....",  
        "X..+X",  "X+..X",  "..X+.",  ".+XX.",  "..X..",  "X...X",  "X+.+X",  "X.X.X",  
        "XXXX.",  ".XXXX",  "..X..",  "+X++X",  "..X..",  "X+.+X",  "+X+X+",  "X+X+X",  
        "X....",  "....X",  "..X..",  "X++X.",  "..X..",  ".XXX.",  ".+X+.",  ".X+X.",  
    
        ".....",  ".....",  ".....",  "...XX",  "..X..",  "XX...",  ".....",  "XXXXX",  
        ".....",  ".....",  ".....",  "..+X+",  "..X..",  "+X+..",  "+X+..",  "X...X",  
        ".X+X.",  ".X+X.",  ".XXXX",  ".+X+.",  "..X..",  ".+X+.",  "X+X+X",  "X...X",  
        ".+X+.",  ".+X+.",  ".+X+.",  "..+X+",  "..X..",  "+X+..",  "..+X+",  "X...X",  
        ".X+X.",  "..X..",  "XXXX.",  "...XX",  "..X..",  "XX...",  ".....",  "XXXXX" 
    };
    
    public static void main(final String[] args) {
        
        final int matrix_width = 8;
        final int matrix_height = 12;
        final int font_width = 5;
        final int font_height = 5;
        
        for (int matrix_y = 0; matrix_y < matrix_height; matrix_y++) {
            for (int matrix_x = 0; matrix_x < matrix_width; matrix_x++) {
                int start = (matrix_y * matrix_width * font_height) + matrix_x;
                long b = 0;
                for (int row = 0; row < font_height; row++) {
                    b = b << (font_width * 2);
                    long v = 1 << (font_width * 2 - 1);
                    for (int col = 0; col < font_width; col++) {
                        String l = font[start + matrix_width * row];
                        if (l.charAt(col) == '+')  b += v;
                        if (l.charAt(col) == 'X')  b += v + (v / 2);
                        v = v >> 2;
                    }
                }
                String s = Long.toHexString(b).toUpperCase();
                while (s.length() < 14) s = "0" + s;
                System.out.print("0x" + s + "L,");
            }
            System.out.println();
        }
        
    } 

} 
