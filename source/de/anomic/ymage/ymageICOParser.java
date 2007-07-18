// ymageICOParser.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.07.2007 on http://yacy.net
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ymageICOParser {

    // this is a implementation of http://msdn2.microsoft.com/en-us/library/ms997538(d=printer).aspx
    
    public  static int ICONDIRENTRY_size = 16;
    
    private int idCount;
    ymageBMPParser.INFOHEADER[] infoheaders;
    ymageBMPParser.IMAGEMAP[] imagemaps;
    
    public static final boolean isICO(byte[] source) {
        // check the file magic
        return (source != null) && (source.length >= 4) && (source[0] == 0) && (source[1] == 0) && (source[2] == 1) && (source[3] == 0);
    }
    
    public ymageICOParser(byte[] source) {
        // read info-header
        idCount = ymageBMPParser.WORD(source, 4);

        // read the icon directory entry and the image entries
        ICONDIRENTRY[] icondirentries = new ICONDIRENTRY[idCount];
        infoheaders = new ymageBMPParser.INFOHEADER[idCount];
        ymageBMPParser.COLORTABLE[] colortables = new ymageBMPParser.COLORTABLE[idCount];
        imagemaps = new ymageBMPParser.IMAGEMAP[idCount];
        for (int i = 0; i < idCount; i++) {
            icondirentries[i] = new ICONDIRENTRY(source, 6 + i * ICONDIRENTRY_size);
            infoheaders[i] = new ymageBMPParser.INFOHEADER(source, icondirentries[i].dwImageOffset);
            colortables[i] = new ymageBMPParser.COLORTABLE(source, icondirentries[i].dwImageOffset + ymageBMPParser.INFOHEADER_size, infoheaders[i]);
            imagemaps[i] = new ymageBMPParser.IMAGEMAP(source, icondirentries[i].dwImageOffset + ymageBMPParser.INFOHEADER_size + colortables[i].colorbytes, icondirentries[i].bWidth, icondirentries[i].bHeight, infoheaders[i].biCompression, infoheaders[i].biBitCount, colortables[i]);
        }
    }
    
    
    public class ICONDIRENTRY {
        
        public int bWidth, bHeight, bColorCount, bReserved, wPlanes, wBitCount, dwBytesInRes, dwImageOffset;
        
        public ICONDIRENTRY(byte[] s, int offset) {
            // read info-header
            bWidth        = ymageBMPParser.BYTE(s, offset + 0);
            bHeight       = ymageBMPParser.BYTE(s, offset + 1);
            bColorCount   = ymageBMPParser.BYTE(s, offset + 2);
            bReserved     = ymageBMPParser.BYTE(s, offset + 3);
            wPlanes       = ymageBMPParser.WORD(s, offset + 4);
            wBitCount     = ymageBMPParser.WORD(s, offset + 6);
            dwBytesInRes  = ymageBMPParser.DWORD(s, offset + 8);
            dwImageOffset = ymageBMPParser.DWORD(s, offset + 12);
        }
    }
    
    public int images() {
        // return number of images in icon
        return idCount;
    }
    
    public BufferedImage getImage(int index) {
        return imagemaps[index].image;
    }
    
    public static void main(String[] args) {
        // read a ICO and write it as png
        System.setProperty("java.awt.headless", "true");
        File in = new File(args[0]);
        File out = new File(args[1]);
        
        byte[] file = new byte[(int) in.length()];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(in);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            fis.read(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        ymageICOParser parser = new ymageICOParser(file);
        
        try {
            ImageIO.write(parser.getImage(0), "PNG", out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}