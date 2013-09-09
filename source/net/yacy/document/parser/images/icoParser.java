// icoParser.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 15.07.2007 on http://yacy.net
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

package net.yacy.document.parser.images;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.yacy.cora.util.ConcurrentLog;


public class icoParser {

    // this is a implementation of http://msdn2.microsoft.com/en-us/library/ms997538(d=printer).aspx
    
    public static final int ICONDIRENTRY_size = 16;
    
    private final int idCount;
    private bmpParser.INFOHEADER[] infoheaders;
    private bmpParser.IMAGEMAP[] imagemaps;
    
    public static final boolean isICO(final byte[] source) {
        // check the file magic
        return (source != null) && (source.length >= 4) && (source[0] == 0) && (source[1] == 0) && (source[2] == 1) && (source[3] == 0);
    }
    
    public icoParser(final byte[] source) {
        // read info-header
        idCount = bmpParser.WORD(source, 4);

        // read the icon directory entry and the image entries
        final ICONDIRENTRY[] icondirentries = new ICONDIRENTRY[idCount];
        infoheaders = new bmpParser.INFOHEADER[idCount];
        final bmpParser.COLORTABLE[] colortables = new bmpParser.COLORTABLE[idCount];
        imagemaps = new bmpParser.IMAGEMAP[idCount];
        for (int i = 0; i < idCount; i++) {
            icondirentries[i] = new ICONDIRENTRY(source, 6 + i * ICONDIRENTRY_size);
            infoheaders[i] = new bmpParser.INFOHEADER(source, icondirentries[i].dwImageOffset);
            colortables[i] = new bmpParser.COLORTABLE(source, icondirentries[i].dwImageOffset + bmpParser.INFOHEADER_size, infoheaders[i]);
            imagemaps[i] = new bmpParser.IMAGEMAP(source, icondirentries[i].dwImageOffset + bmpParser.INFOHEADER_size + colortables[i].colorbytes, icondirentries[i].bWidth, icondirentries[i].bHeight, infoheaders[i].biCompression, infoheaders[i].biBitCount, colortables[i]);
        }
    }
    
    
    public static class ICONDIRENTRY {
        
        public int bWidth, bHeight, bColorCount, bReserved, wPlanes, wBitCount, dwBytesInRes, dwImageOffset;
        
        public ICONDIRENTRY(final byte[] s, final int offset) {
            // read info-header
            bWidth        = bmpParser.BYTE(s, offset + 0);
            bHeight       = bmpParser.BYTE(s, offset + 1);
            bColorCount   = bmpParser.BYTE(s, offset + 2);
            bReserved     = bmpParser.BYTE(s, offset + 3);
            wPlanes       = bmpParser.WORD(s, offset + 4);
            wBitCount     = bmpParser.WORD(s, offset + 6);
            dwBytesInRes  = bmpParser.DWORD(s, offset + 8);
            dwImageOffset = bmpParser.DWORD(s, offset + 12);
        }
    }
    
    public int images() {
        // return number of images in icon
        return idCount;
    }
    
    public BufferedImage getImage(final int index) {
    	if (imagemaps == null || index >= imagemaps.length) return null;
        return imagemaps[index].getImage();
    }
    
    public static void main(final String[] args) {
        // read a ICO and write it as png
        System.setProperty("java.awt.headless", "true");
        final File in = new File(args[0]);
        final File out = new File(args[1]);
        
        final byte[] file = new byte[(int) in.length()];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(in);
            fis.read(file);
        } catch (final FileNotFoundException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        
        final icoParser parser = new icoParser(file);
        
        try {
            ImageIO.write(parser.getImage(0), "PNG", out);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }
    
}