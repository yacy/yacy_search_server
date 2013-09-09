/**
 *  HexGridPlotter
 *  Copyright 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 18.02.2010 at http://yacy.net
 *  
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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
import net.yacy.cora.util.ConcurrentLog;

/*
 * hexagonal grid plotter
 */
public class HexGridPlotter extends RasterPlotter {
    
    private final int cellwidth1, cellwidth2, cellwidth12;
    private final int gwidth, gheight;

    public HexGridPlotter(int width, int height, DrawMode drawMode, String backgroundColor, final int cellwidth) {
        super(width, height, drawMode, backgroundColor);
        this.cellwidth1 = cellwidth;
        this.cellwidth2 = cellwidth >> 1;
        this.cellwidth12 = this.cellwidth1 + this.cellwidth2;
        this.gwidth = (super.width - this.cellwidth2) / this.cellwidth12;
        this.gheight = (super.height - this.cellwidth2) / this.cellwidth2;
    }
    
    private int projectionX(final int x, final int y) {
        return (x + 1) * this.cellwidth12 - this.cellwidth2 + ((x & 1) == 0 ? ((y & 1) == 0 ? 0 : this.cellwidth2) : ((y & 1) == 0 ? this.cellwidth2 : 0));
    }
    
    private int projectionY(final int y) {
        return (y + 1) * this.cellwidth2;
    }

    public int gridWidth() {
        return this.gwidth;
    }
    
    public int gridHeight() {
        return this.gheight;
    }

    public void drawGrid(final String colorNaming) {
        setColor(Long.parseLong(colorNaming, 16));
        int x0, y0, x1, y1;
        for (int i = 0; i < this.gwidth; i++) {
            x0 = projectionX(i, -1);
            y0 = projectionY(-1);
            //super.plot(x0, y0, 30);
            for (int j = -1; j <= this.gheight + 1; j++) {
                x1 = projectionX(i, j);
                y1 = projectionY(j);
                //super.plot(x1, y1, 30);
                super.line(x0, y0, x1, y1, 40);
                x0 = x1;
                y0 = y1;
            }
        }
        
        for (int i = -1; i <= this.gwidth; i = i + 2) {
            for (int j = 0; j <= this.gheight; j = j + 2) {
                x0 = projectionX(i, j);
                y0 = projectionY(j);
                x1 = projectionX(i + 1, j);
                y1 = projectionY(j);
                super.line(x0 + 1, y0, x1 - 1, y1, 30);
                
                x0 = projectionX(i + 1, j + 1);
                y0 = projectionY(j + 1);
                x1 = projectionX(i + 2, j + 1);
                y1 = projectionY(j + 1);
                super.line(x0 + 1, y0, x1 - 1, y1, 30);
                
            }
        }
    }

    public void gridDot(final int x, final int y, final int radius, boolean filled, int intensity) {
        dot(projectionX(x, y), projectionY(y), radius, filled, intensity);
    }
    
    public void gridDot(final int x, final int y, final int radius, int fromArc, int toArc) {
        CircleTool.circle(this, projectionX(x, y), projectionY(y), radius, fromArc, toArc);
    }

    public void gridPrint(final int x, final int y, final int radius, final String messageHigh, final String messageLow, final int align) {
        if (messageHigh != null && messageHigh.length() > 0) PrintTool.print(this, projectionX(x, y) - align * radius, projectionY(y) - (cellwidth2 >> 2) - ((align == 0) ? radius : 0), 0, messageHigh, align);
        if (messageLow != null && messageLow.length() > 0) PrintTool.print(this, projectionX(x, y) - align * radius, projectionY(y) + (cellwidth2 >> 2) + 4 + ((align == 0) ? radius : 0), 0, messageLow, align);
    }
    
    public void gridPrint315(final int x, final int y, final int radius, final String message315) {
        if (message315 != null && message315.length() > 0) PrintTool.print(this, projectionX(x, y) + (radius >> 1), projectionY(y) + (cellwidth2 >> 2) + 4, 315, message315, -1);
    }
    
    public void gridLine(
            int Ax, int Ay, final int Bx, final int By
            ) {
        gridLine(Ax, Ay, Bx, By, null, 100, null, 100, -1, -1, -1, false);
    }
    
    public void gridLine(
            int Ax, int Ay, final int Bx, final int By,
            String colorLine, final int intensityLine,
            String colorDot, final int intensityDot, int dotDist, int dotPos, int dotRadius, boolean dotFilled
            ) {
        // we do not use Bresenham's line drawing algorithm here because we want only 0, 45 and 90 degree lines
        int x0 = projectionX(Ax, Ay);
        int y0 = projectionY(Ay);
        int x1 = projectionX(Bx, By);
        int y1 = projectionY(By);
        int horizontal;
        int dotc = 0;
        Long colorLine_l = colorLine == null ? null : Long.parseLong(colorLine, 16);
        Long colorDot_l = colorDot == null ? null : Long.parseLong(colorDot, 16);
        while (x1 != x0 || y1 != y0) {
            horizontal = 0;
            if (x1 > x0) {x1--; horizontal++;} else if (x1 < x0) {x1++; horizontal++;}
            if (y1 > y0) {y1--; horizontal++;} else if (y1 < y0) {y1++; horizontal++;}
            if (colorLine != null) this.setColor(colorLine_l);
            plot(x1, y1, (horizontal == 2) ? intensityLine : intensityLine * 8 / 10);
            if (dotc == dotPos) {
                if (colorDot != null) this.setColor(colorDot_l);
                if (dotRadius == 0) this.plot(x1, y1, intensityDot);
                else if (dotRadius > 0) this.dot(x1, y1, dotRadius, dotFilled, intensityDot);
            }
            dotc++;
            if (dotc == dotDist) dotc = 0;
        }
    }
    
    private static HexGridPlotter testImage0(int width, int height, String bgcolor, String gridcolor, String dotcolor) {
        final HexGridPlotter picture = new HexGridPlotter(width, height, bgcolor.equals("000000") ? DrawMode.MODE_ADD : DrawMode.MODE_SUB, bgcolor, 18);
        picture.drawGrid(gridcolor);
        long dotcolori = Long.parseLong(dotcolor, 16);
        picture.setColor(dotcolori);
        picture.gridDot(0, 0, 5, true, 100); picture.gridPrint(0, 0, 5, "", "0,0", -1);
        for (int i = 1; i < picture.gridHeight() -1; i++) {
            picture.setColor(dotcolori);picture.gridDot(0, i, 3, true, 100);
            picture.setColor(dotcolori);picture.gridPrint(0, i, 3, "", "0," + i, -1);
        }
        for (int i = 1; i < picture.gridWidth() -1; i++) {
            picture.setColor(dotcolori);picture.gridDot(i, 0, 3, true, 100);
            picture.setColor(dotcolori);picture.gridPrint315(i, 0, 3, i + ",0");
        }
        picture.setColor(dotcolori);
        picture.gridDot(0, picture.gheight - 1, 5, true, 100); picture.gridPrint(0, picture.gheight - 1, 5, "0, grid.gheight - 1", "", -1);
        picture.gridDot(picture.gwidth - 1, 0, 5, true, 100); picture.gridPrint(picture.gwidth - 1, 0, 5, "", "grid.gwidth - 1, 0", -1);
        picture.gridDot(picture.gwidth - 1, picture.gheight - 1, 5, true, 100); picture.gridPrint(picture.gwidth - 1, picture.gheight - 1, 5, "grid.gwidth - 1, grid.gheight - 1", "", 1);
        
        
        picture.gridDot(3, 3, 20, 0, 360);
        
        picture.gridDot(7, 5, 5, 0, 360);
        picture.gridPrint(7, 5, 8, "COMMUNICATION TEST", "TRANSFER 64KBIT", -1);
        picture.gridDot(14, 8, 5, true, 100);
        picture.gridLine(7, 5, 14, 8);
        picture.gridPrint315(14, 8, 8, "NULL");
        
        picture.gridDot(4, 28, 5, 0, 360);
        picture.gridPrint(4, 28, 8, "REVERSE", "ESREVER", -1);
        picture.gridLine(4, 28, 14, 8);
        
        picture.gridDot(picture.gwidth - 1, picture.gheight - 4, 5, 0, 360);
        picture.gridPrint(picture.gwidth - 1, picture.gheight - 4, 5, "INTERNET", "END", 1);
        picture.gridDot(picture.gwidth / 2, picture.gheight / 2, 5, 0, 360);
        picture.gridPrint(picture.gwidth / 2, picture.gheight / 2, 5, "HOME PEER", "ANON_23", 0);
        //grid.gridLine(grid.gwidth - 2, grid.gheight - 2, grid.gwidth / 2, grid.gheight / 2);
        picture.gridLine(picture.gwidth / 2, picture.gheight / 2, picture.gwidth - 1, picture.gheight - 4);
        return picture;
    }
    
    public static void main(final String[] args) {
        // go into headless awt mode
        //System.setProperty("java.awt.headless", "true");
        AnimationPlotter animation = new AnimationPlotter();
        for (int i = 640; i < 700; i++) {
            animation.addFrame(testImage0(i, 480, "000000", "555555", "33ff33").getImage(), 10);
        }
        animation.show();
        HexGridPlotter picture = testImage0(640, 480, "FFFFFF", "555555", "33ff33");
        
        final File file = new File("/Users/admin/Desktop/testimage.png");
        try {picture.save(file, "png");} catch (final IOException e) {}
        ConcurrentLog.shutdown();
        if (!System.getProperty("java.awt.headless", "false").equals("true")) picture.show();
    }
    
}
