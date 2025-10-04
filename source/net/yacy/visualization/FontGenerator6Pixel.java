/**
 *  FontGenerator6Pixel
 *  Copyright 2025 by Michael Christen
 *  First released 23.09.2025 at https://yacy.net
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

public class FontGenerator6Pixel { 

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
    public static final String[] font_light = {
        "......",  "..X...",  ".X..X.",  ".X..X.",  "..X...",  "XX...X",  "..X...",  "...X..",  
        "......",  "..X...",  ".X..X.",  "XXXXXX",  "XXXXX.",  "XX..X.",  ".X.X..",  "..X...",  
        "......",  "..X...",  "......",  ".X..X.",  "X.X...",  "...X..",  "..X...",  "......",  
        "......",  "..X...",  "......",  ".X..X.",  "XXXXX.",  "..X...",  ".X.X.X",  "......",  
        "......",  "......",  "......",  "XXXXXX",  "..X.X.",  ".X..XX",  "X...X.",  "......",  
        "......",  "..X...",  "......",  ".X..X.",  "XXXXX.",  "X...XX",  ".XXX.X",  "......",  
        "......",  "......",  "......",  "......",  "..X...",  "......",  "......",  "......",  
    
        "...X..",  "..X...",  "......",  "......",  "......",  "......",  "......",  "......",  
        "..X...",  "...X..",  "..X.X.",  "...X..",  "......",  "......",  "......",  "....X.",  
        "..X...",  "...X..",  "...X..",  "...X..",  "......",  "......",  "......",  "...X..",  
        "..X...",  "...X..",  ".XXXXX",  ".XXXXX",  "......",  ".XXXXX",  "......",  "..X...",  
        "..X...",  "...X..",  "...X..",  "...X..",  "..X...",  "......",  "..XX..",  ".X....",  
        "...X..",  "..X...",  "..X.X.",  "...X..",  "..X...",  "......",  "..XX..",  "X.....",  
        "......",  "......",  "......",  "......",  ".X....",  "......",  "......",  "......",  
    
        ".XXXX.",  "..XX..",  ".XXXX.",  ".XXXX.",  "...X..",  "XXXXXX",  ".XXXX.",  "XXXXXX",  
        "X...XX",  ".X.X..",  "X....X",  "X....X",  "..XX..",  "X.....",  "X.....",  ".....X",  
        "X..X.X",  "...X..",  ".....X",  "...XX.",  ".X.X..",  "XXXXX.",  "XXXXX.",  "....X.",  
        "X.X..X",  "...X..",  ".XXXX.",  ".....X",  "X..X..",  ".....X",  "X....X",  "...X..",  
        "XX...X",  "...X..",  "X.....",  "X....X",  "XXXXXX",  "X....X",  "X....X",  "..X...",  
        ".XXXX.",  ".XXXXX",  "XXXXXX",  ".XXXX.",  "...X..",  ".XXXX.",  ".XXXX.",  "..X...",  
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "......", 
    
        ".XXXX.",  ".XXXX.",  "......",  "......",  "......",  "......",  "......",  ".XXXX.",  
        "X....X",  "X....X",  "......",  "..X...",  "...X..",  "......",  "..X...",  "X....X",  
        ".XXXX.",  "X....X",  "..X...",  "......",  "..X...",  ".XXXXX",  "...X..",  "....X.",  
        "X....X",  ".XXXXX",  "......",  "......",  ".X....",  "......",  "....X.",  "...X..",  
        "X....X",  ".....X",  "......",  "..X...",  "..X...",  ".XXXXX",  "...X..",  "......",  
        ".XXXX.",  ".XXXX.",  "..X...",  "..X...",  "...X..",  "......",  "..X...",  "...X..",
        "......",  "......",  "......",  ".X....",  "......",  "......",  "......",  "......",
    
        ".XXXX.",  ".XXXX.",  "XXXXX.",  ".XXXX.",  "XXXX..",  "XXXXXX",  "XXXXXX",  ".XXXX.",  
        "X..X.X",  "X....X",  "X....X",  "X....X",  "X...X.",  "X.....",  "X.....",  "X....X",  
        "X.X.XX",  "X....X",  "XXXXX.",  "X.....",  "X....X",  "XXXXX.",  "XXXXX.",  "X.....",  
        "X.XXXX",  "XXXXXX",  "X....X",  "X.....",  "X....X",  "X.....",  "X.....",  "X..XXX",  
        "X.....",  "X....X",  "X....X",  "X....X",  "X...X.",  "X.....",  "X.....",  "X....X",  
        ".XXXX.",  "X....X",  "XXXXX.",  ".XXXX.",  "XXXX..",  "XXXXXX",  "X.....",  ".XXXX.",
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "......", 
    
        "X....X",  ".XXXXX",  ".....X",  "X...X.",  "X.....",  "X....X",  "X....X",  ".XXXX.",  
        "X....X",  "...X..",  ".....X",  "X..X..",  "X.....",  "XX..XX",  "XX...X",  "X....X",  
        "XXXXXX",  "...X..",  ".....X",  "XXX...",  "X.....",  "X.XX.X",  "X.X..X",  "X....X",  
        "X....X",  "...X..",  "X....X",  "X..X..",  "X.....",  "X....X",  "X..X.X",  "X....X",  
        "X....X",  "...X..",  "X....X",  "X...X.",  "X.....",  "X....X",  "X...XX",  "X....X",  
        "X....X",  ".XXXXX",  ".XXXX.",  "X....X",  "XXXXXX",  "X....X",  "X....X",  ".XXXX.",
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "......",
    
        "XXXXX.",  ".XXXX.",  "XXXXX.",  ".XXXX.",  ".XXXXX",  "X....X",  "X....X",  "X....X",  
        "X....X",  "X....X",  "X....X",  "X.....",  "...X..",  "X....X",  "X....X",  "X....X",  
        "X....X",  "X....X",  "X....X",  ".XXXX.",  "...X..",  "X....X",  "X....X",  "X....X",  
        "XXXXX.",  "X.X..X",  "XXXXX.",  ".....X",  "...X..",  "X....X",  "X....X",  "X....X",  
        "X.....",  "X..X.X",  "X...X.",  "X....X",  "...X..",  "X....X",  ".X..X.",  "X.XX.X",  
        "X.....",  ".XXXX.",  "X....X",  ".XXXX.",  "...X..",  ".XXXX.",  "..XX..",  ".X..X.",
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "......",
    
        "X....X",  ".X...X",  "XXXXXX",  "...XXX",  "......",  "XXX...",  "..X...",  "......",  
        ".X..X.",  ".X...X",  "....X.",  "...X..",  "X.....",  "..X...",  ".X.X..",  "......",  
        "..XX..",  "..X.X.",  "...X..",  "...X..",  ".X....",  "..X...",  "X...X.",  "......",  
        "..XX..",  "...X..",  "..X...",  "...X..",  "..X...",  "..X...",  "......",  "......",  
        ".X..X.",  "...X..",  ".X....",  "...X..",  "...X..",  "..X...",  "......",  "......",  
        "X....X",  "...X..",  "XXXXXX",  "...XXX",  "....X.",  "XXX...",  "......",  "......",
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "XXXXXX",
    
        "..X...",  "......",  ".X....",  "......",  "....X.",  "......",  "...XX.",  "......",  
        "...X..",  ".XXX..",  ".X....",  "..XXX.",  "....X.",  ".XXX..",  "..X...",  ".XXXX.",  
        "......",  "....X.",  ".XXXX.",  ".X....",  ".XXXX.",  "X...X.",  "..XX..",  "X...X.",  
        "......",  ".XXXX.",  ".X...X",  ".X....",  "X...X.",  "XXXX..",  "..X...",  "X...X.",  
        "......",  "X...X.",  ".X...X",  ".X....",  "X...X.",  "X.....",  "..X...",  ".XXXX.",  
        "......",  ".XXXX.",  ".XXXX.",  "..XXX.",  ".XXXX.",  ".XXXX.",  "..X...",  "....X.",
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  ".XXX..",
    
        "X.....",  "..X...",  "....X.",  ".X....",  "..X...",  "......",  "......",  "......",  
        "X.....",  "......",  "......",  ".X.X..",  "..X...",  "XX.X..",  "XXXX..",  ".XXX..",  
        "XXXX..",  ".XX...",  "....X.",  ".XX...",  "..X...",  "X.X.X.",  "X...X.",  "X...X.",  
        "X...X.",  "..X...",  "....X.",  ".XX...",  "..X...",  "X.X.X.",  "X...X.",  "X...X.",  
        "X...X.",  "..X...",  "....X.",  ".X.X..",  "..X...",  "X.X.X.",  "X...X.",  "X...X.",  
        "X...X.",  ".XXX..",  ".X..X.",  ".X..X.",  "...XX.",  "X.X.X.",  "X...X.",  ".XXX..",
        "......",  "......",  "..XX..",  "......",  "......",  "......",  "......",  "......",
    
        "......",  "......",  "......",  "......",  "..X...",  "......",  "......",  "......",  
        "XXXX..",  ".XXXX.",  "..XXX.",  ".XXX..",  ".XXX..",  "X...X.",  "X...X.",  "X...X.",  
        "X...X.",  "X...X.",  ".X....",  "X.....",  "..X...",  "X...X.",  "X...X.",  "X.X.X.",  
        "X...X.",  "X...X.",  ".X....",  ".XXX..",  "..X...",  "X...X.",  ".X.X..",  "X.X.X.",  
        "XXXX..",  ".XXXX.",  ".X....",  "....X.",  "..X...",  "X...X.",  ".X.X..",  "X.X.X.",  
        "X.....",  "....X.",  ".X....",  "XXXX..",  "...X..",  ".XXX..",  "..X...",  ".X.X..",
        "X.....",  "....XX",  "......",  "......",  "......",  "......",  "......",  "......",
    
        "......",  "......",  "......",  "...XXX",  "...X..",  "XXX...",  "......",  "XXXXXX",  
        "X...X.",  "X...X.",  "XXXXX.",  "...X..",  "...X..",  "..X...",  "..X.X.",  "X....X",  
        ".X.X..",  "X...X.",  "...X..",  "XXX...",  "...X..",  "...XXX",  ".X.X..",  "X....X",  
        "..X...",  "X...X.",  "..X...",  "...X..",  "...X..",  "..X...",  "......",  "X....X",  
        ".X.X..",  ".XXXX.",  ".X....",  "...X..",  "...X..",  "..X...",  "......",  "X....X",  
        "X...X.",  "....X.",  "XXXXX.",  "...XXX",  "...X..",  "XXX...",  "......",  "X....X",
        "......",  ".XXX..",  "......",  "......",  "......",  "......",  "......",  "XXXXXX",
    };
    
    public static final String[] font_bold = {
        "......",  "..XX..",  "XX..XX",  ".XX.X.",  "..XX..",  "XX...X",  ".XXX..",  "..XX..",  
        "......",  "..XX..",  "XX..XX",  ".XX.X.",  ".XXXXX",  "XX..XX",  "XX.XX.",  "..XX..",  
        "......",  "..XX..",  "......",  "XXXXXX",  "X.....",  "...XX.",  ".XXX..",  "..XX..",  
        "......",  "..XX..",  "......",  ".XX.X.",  ".XXXX.",  "..XX..",  ".XX...",  "......",  
        "......",  "..XX..",  "......",  "XXXXXX",  ".....X",  ".XX...",  "XX.XXX",  "......",  
        "......",  "......",  "......",  ".XX.X.",  "XXXXX.",  "XX..XX",  "XX.XX.",  "......",  
        "......",  "..XX..",  "......",  ".XX.X.",  "..XX..",  "X...XX",  ".XXXXX",  "......",  
    
        "...XX.",  ".XX...",  "......",  "......",  "......",  "......",  "......",  "......",  
        "..XX..",  "..XX..",  "XX..XX",  "..XX..",  "......",  "......",  "......",  "....XX",  
        ".XX...",  "...XX.",  "..XX..",  "..XX..",  "......",  "......",  "......",  "...XX.",  
        ".XX...",  "...XX.",  "XXXXXX",  "XXXXXX",  "......",  "XXXXXX",  "......",  "..XX..",  
        ".XX...",  "...XX.",  "..XX..",  "..XX..",  "..XX..",  "......",  "..XX..",  ".XX...",  
        "..XX..",  "..XX..",  "XX..XX",  "..XX..",  "..XX..",  "......",  "..XX..",  "XX....",  
        "...XX.",  ".XX...",  "......",  "......",  ".XX...",  "......",  "......",  "......",  
    
        ".XXXX.",  "..XX..",  ".XXXX.",  ".XXXX.",  "...XX.",  "XXXXXX",  ".XXXX.",  "XXXXXX",  
        "XX..XX",  "..XX..",  "XX..XX",  "XX..XX",  "..XXX.",  "XX....",  "XX..XX",  "XX..XX",  
        "XX.XXX",  ".XXX..",  "....XX",  "....XX",  ".XXXX.",  "XXXXX.",  "XX....",  "...XX.",  
        "XXX.XX",  "..XX..",  "...XX.",  "..XXX.",  "XX.XX.",  "....XX",  "XXXXX.",  "..XX..",  
        "XX..XX",  "..XX..",  ".XX...",  "....XX",  "XXXXXX",  "....XX",  "XX..XX",  "..XX..",  
        "XX..XX",  "..XX..",  "XX....",  "XX..XX",  "...XX.",  "XX..XX",  "XX..XX",  "..XX..",  
        ".XXXX.",  "XXXXXX",  "XXXXXX",  ".XXXX.",  "...XX.",  ".XXXX.",  ".XXXX.",  "..XX..",  
    
        ".XXXX.",  ".XXXX.",  "......",  "......",  "....XX",  "......",  "XX....",  ".XXXX.",  
        "XX..XX",  "XX..XX",  "......",  "......",  "...XX.",  "......",  ".XX...",  "XX..XX",  
        "XX..XX",  "XX..XX",  "..XX..",  "..XX..",  ".XX...",  "XXXXXX",  "...XX.",  "....XX",  
        ".XXXX.",  ".XXXXX",  "..XX..",  "..XX..",  "XX....",  "......",  "....XX",  "...XX.",  
        "XX..XX",  "....XX",  "......",  "......",  ".XX...",  "XXXXXX",  "...XX.",  "..XX..",  
        "XX..XX",  "XX..XX",  "..XX..",  "..XX..",  "...XX.",  "......",  ".XX...",  "......",  
        ".XXXX.",  ".XXXX.",  "..XX..",  ".XX...",  "....XX",  "......",  "XX....",  "..XX..",   
    
        ".XXXX.",  "..XX..",  "XXXXX.",  ".XXXX.",  "XXXX..",  "XXXXXX",  "XXXXXX",  ".XXXX.",  
        "XX..XX",  ".XXXX.",  "XX..XX",  "XX..XX",  "XX.XX.",  "XX....",  "XX....",  "XX..XX",  
        "XX.XXX",  "XX..XX",  "XX..XX",  "XX....",  "XX..XX",  "XX....",  "XX....",  "XX....",  
        "XX.XXX",  "XXXXXX",  "XXXXX.",  "XX....",  "XX..XX",  "XXXX..",  "XXXX..",  "XX.XXX",  
        "XX....",  "XX..XX",  "XX..XX",  "XX....",  "XX..XX",  "XX....",  "XX....",  "XX..XX",  
        "XX...X",  "XX..XX",  "XX..XX",  "XX..XX",  "XX.XX.",  "XX....",  "XX....",  "XX..XX",  
        ".XXXX.",  "XX..XX",  "XXXXX.",  ".XXXX.",  "XXXX..",  "XXXXXX",  "XX....",  ".XXXX.",  
    
        "XX..XX",  ".XXXX.",  "..XXXX",  "XX..XX",  "XX....",  "XX...X",  "XX..XX",  ".XXXX.",  
        "XX..XX",  "..XX..",  "...XX.",  "XX.XX.",  "XX....",  "XXX.XX",  "XXX.XX",  "XX..XX",  
        "XX..XX",  "..XX..",  "...XX.",  "XXXX..",  "XX....",  "XXXXXX",  "XXXXXX",  "XX..XX",  
        "XXXXXX",  "..XX..",  "...XX.",  "XX....",  "XX....",  "XX.X.X",  "XXXXXX",  "XX..XX",  
        "XX..XX",  "..XX..",  "...XX.",  "XXXX..",  "XX....",  "XX...X",  "XX.XXX",  "XX..XX",  
        "XX..XX",  "..XX..",  "XX.XX.",  "XX.XX.",  "XX....",  "XX...X",  "XX..XX",  "XX..XX",  
        "XX..XX",  ".XXXX.",  ".XXX..",  "XX..XX",  "XXXXXX",  "XX...X",  "XX..XX",  ".XXXX.",  
    
        "XXXXX.",  ".XXXX.",  "XXXXX.",  ".XXXX.",  "XXXXXX",  "XX..XX",  "XX..XX",  "XX...X",  
        "XX..XX",  "XX..XX",  "XX..XX",  "XX..XX",  "..XX..",  "XX..XX",  "XX..XX",  "XX...X",  
        "XX..XX",  "XX..XX",  "XX..XX",  "XX....",  "..XX..",  "XX..XX",  "XX..XX",  "XX...X",  
        "XXXXX.",  "XX..XX",  "XXXXX.",  ".XXXX.",  "..XX..",  "XX..XX",  "XX..XX",  "XX...X",  
        "XX....",  "XX..XX",  "XXXX..",  "....XX",  "..XX..",  "XX..XX",  "XX..XX",  "XX.X.X",  
        "XX....",  ".XXXX.",  "XX.XX.",  "XX..XX",  "..XX..",  "XX..XX",  ".XXXX.",  "XXX.XX",  
        "XX....",  "...XXX",  "XX..XX",  ".XXXX.",  "..XX..",  ".XXXX.",  "..XX..",  "XX...X",  
    
        "XX..XX",  "XX..XX",  "XXXXXX",  ".XXXX.",  "......",  ".XXXX.",  "..XX..",  "......",  
        "XX..XX",  "XX..XX",  "....XX",  ".XX...",  "XX....",  "...XX.",  ".XXXX.",  "......",  
        ".XXXX.",  ".XXXX.",  "...XX.",  ".XX...",  ".XX...",  "...XX.",  "XX..XX",  "......",  
        "..XX..",  "..XX..",  "..XX..",  ".XX...",  "..XX..",  "...XX.",  "......",  "......",  
        ".XXXX.",  "..XX..",  ".XX...",  ".XX...",  "...XX.",  "...XX.",  "......",  "......",  
        "XX..XX",  "..XX..",  "XX....",  ".XX...",  "....XX",  "...XX.",  "......",  "......",  
        "XX..XX",  "..XX..",  "XXXXXX",  ".XXXX.",  "......",  ".XXXX.",  "......",  "XXXXXX",  
    
        ".XX...",  "......",  "......",  "......",  "......",  "......",  "......",  "......",  
        "..XX..",  "......",  "XX....",  "......",  "....XX",  "......",  "...XXX",  "......",  
        "......",  ".XXXX.",  "XX....",  ".XXXX.",  "....XX",  ".XXXX.",  "..XX..",  ".XXXXX",  
        "......",  "....XX",  "XXXXX.",  "XX....",  ".XXXXX",  "XX..XX",  ".XXXXX",  "XX..XX",  
        "......",  ".XXXXX",  "XX..XX",  "XX....",  "XX..XX",  "XXXXXX",  "..XX..",  ".XXXXX",  
        "......",  "XX..XX",  "XX..XX",  "XX....",  "XX..XX",  "XX....",  "..XX..",  "....XX",  
        "......",  ".XXXXX",  "XXXXX.",  ".XXXX.",  ".XXXXX",  ".XXXX.",  "..XX..",  "XXXXX.",  
    
        "......",  "..XX..",  "...XX.",  "......",  "......",  "......",  "......",  "......",  
        "XX....",  "......",  "......",  "XX....",  ".XXX..",  "......",  "......",  "......",  
        "XX....",  ".XXX..",  "...XX.",  "XX....",  "..XX..",  "XX.XX.",  "XXXXX.",  ".XXXX.",  
        "XXXXX.",  "..XX..",  "...XX.",  "XX.XX.",  "..XX..",  "XXXXXX",  "XX..XX",  "XX..XX",  
        "XX..XX",  "..XX..",  "...XX.",  "XXXX..",  "..XX..",  "XXXXXX",  "XX..XX",  "XX..XX",  
        "XX..XX",  "..XX..",  "...XX.",  "XX.XX.",  "..XX..",  "XX.X.X",  "XX..XX",  "XX..XX",  
        "XX..XX",  ".XXXX.",  "XXXX..",  "XX..XX",  ".XXXX.",  "XX...X",  "XX..XX",  ".XXXX.",  
    
        "......",  "......",  "......",  "......",  "......",  "......",  "......",  "......",  
        "......",  "......",  "......",  "......",  "..XX..",  "......",  "......",  "......",  
        "XXXXX.",  ".XXXXX",  "XXXXX.",  ".XXXXX",  "XXXXXX",  "XX..XX",  "XX..XX",  "XX...X",  
        "XX..XX",  "XX..XX",  "XX..XX",  "XX....",  "..XX..",  "XX..XX",  "XX..XX",  "XX.X.X",  
        "XXXXX.",  ".XXXXX",  "XX....",  ".XXXX.",  "..XX..",  "XX..XX",  "XX..XX",  "XXXXXX",  
        "XX....",  "....XX",  "XX....",  "....XX",  "..XX..",  "XX..XX",  ".XXXX.",  ".XXXXX",  
        "XX....",  "....XX",  "XX....",  "XXXXX.",  "...XXX",  ".XXXXX",  "..XX..",  ".XX.XX",  
    
        "......",  "......",  "......",  "...XXX",  "..XX..",  "XXX...",  "......",  "XXXXXX",  
        "......",  "......",  "......",  "..XX..",  "..XX..",  "..XX..",  "......",  "X....X",  
        "XX..XX",  "XX..XX",  "XXXXXX",  "..XX..",  "..XX..",  "..XX..",  ".X....",  "X....X",  
        ".XXXX.",  "XX..XX",  "...XX.",  "XXX...",  "..XX..",  "...XXX",  "X.XX.X",  "X....X",  
        "..XX..",  ".XXXXX",  "..XX..",  "..XX..",  "..XX..",  "..XX..",  "....X.",  "X....X",  
        ".XXXX.",  "...XX.",  ".XX...",  "..XX..",  "..XX..",  "..XX..",  "......",  "X....X",  
        "XX..XX",  "XXXX..",  "XXXXXX",  "...XXX",  "..XX..",  "XXX...",  "......",  "XXXXXX"
    };

    public static void fontConverter(final String[] font) {

        final int matrix_width = 8;
        final int matrix_height = 12;
        final int font_width = 6;
        final int font_height = 7;
        
        for (int matrix_y = 0; matrix_y < matrix_height; matrix_y++) {
            for (int matrix_x = 0; matrix_x < matrix_width; matrix_x++) {
                int start = (matrix_y * matrix_width * font_height) + matrix_x;
                long b = 0;
                for (int row = 0; row < font_height; row++) {
                    b = b << font_width;
                    long v = 1 << (font_width - 1);
                    for (int col = 0; col < font_width; col++) {
                        int i = start + matrix_width * row;
                        if (font[i].charAt(col) == 'X') b += v;
                        else if (font[i].charAt(col) != '.') throw new RuntimeException("wrong char: " + font[i].charAt(col));
                        v = v >> 1;
                    }
                }
                String s = Long.toHexString(b).toUpperCase();
                while (s.length() < 12) s = "0" + s;
                System.out.print("0x" + s + "L,");
            }
            System.out.println();
        }
        
    } 
    
    public static void main(final String[] args) {

        fontConverter(font_light);
        System.out.println();
        fontConverter(font_bold);
        
    } 

} 
