// ymageChart.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 26.10.2005
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

public class ymageChart extends ymageMatrix {
    
    public static final int DIMENSION_RIGHT  = 0;
    public static final int DIMENSION_TOP    = 1;
    public static final int DIMENSION_LEFT   = 2;
    public static final int DIMENSION_BOTTOM = 3;
    public static final int DIMENSION_ANOT   = 4;
    
    int leftborder;
    int rightborder;
    int topborder;
    int bottomborder;
    int[] scales = new int[]{0,0,0,0,0};
    int[] pixels = new int[]{0,0,0,0,0};
    int[] offsets = new int[]{0,0,0,0,0};
    String[] colnames = new String[]{"FFFFFF","FFFFFF","FFFFFF","FFFFFF","FFFFFF"};
    String[] colscale = new String[]{null,null,null,null,null};
    String[] tablenames = new String[]{"","","","",""};
    String name;
    String backgroundColor, foregroundColor;
    
    public ymageChart(final int width, final int height, final String backgroundColor, final String foregroundColor, final String lightColor,
                      final int leftborder, final int rightborder, final int topborder, final int bottomborder,
                      final String name, final String subline) {
        super(width, height, ymageMatrix.MODE_REPLACE, backgroundColor);
        this.leftborder = leftborder;
        this.rightborder = rightborder;
        this.topborder = topborder;
        this.bottomborder = bottomborder;
        this.name = name;
        this.backgroundColor = backgroundColor;
        this.foregroundColor = foregroundColor;
        if (name != null) {
            this.setColor(foregroundColor);
            ymageToolPrint.print(this, width / 2 - name.length() * 3, 6, 0, name, -1);
        }
        if (subline != null) {
            this.setColor(lightColor);
            ymageToolPrint.print(this, width / 2 - subline.length() * 3, 14, 0, subline, -1);
        }
    }
    
    public void declareDimension(final int dimensionType, final int scale, final int pixelperscale, final int offset, final String colorNaming, final String colorScale, final String name) {
        if ((dimensionType == DIMENSION_LEFT) || (dimensionType == DIMENSION_RIGHT)) {
            drawVerticalScale((dimensionType == DIMENSION_LEFT), scale, pixelperscale, offset, colorNaming, colorScale, name);
        }
        if ((dimensionType == DIMENSION_TOP) || (dimensionType == DIMENSION_BOTTOM)) {
            drawHorizontalScale((dimensionType == DIMENSION_TOP), scale, pixelperscale, offset, colorNaming, colorScale, name);
        }
        scales[dimensionType] = scale;
        pixels[dimensionType] = pixelperscale;
        offsets[dimensionType] = offset;
        colnames[dimensionType] = colorNaming;
        colscale[dimensionType] = colorScale;
        tablenames[dimensionType] = name;
    }
    
    public void chartDot(final int dimension_x, final int dimension_y, final int coord_x, final int coord_y, final int dotsize, String anot) {
        final int x = (coord_x - offsets[dimension_x]) * pixels[dimension_x] / scales[dimension_x];
        final int y = (coord_y - offsets[dimension_y]) * pixels[dimension_y] / scales[dimension_y];
        if (dotsize == 1) plot(leftborder + x, height - bottomborder - y);
                      else dot(leftborder + x, height - bottomborder - y, dotsize, true);
        if (anot != null) ymageToolPrint.print(this, leftborder + x + dotsize + 2, height - bottomborder - y, 0, anot, -1);
    }
    
    public void chartLine(final int dimension_x, final int dimension_y, final int coord_x1, final int coord_y1, final int coord_x2, final int coord_y2) {
        final int x1 = (coord_x1 - offsets[dimension_x]) * pixels[dimension_x] / scales[dimension_x];
        final int y1 = (coord_y1 - offsets[dimension_y]) * pixels[dimension_y] / scales[dimension_y];
        final int x2 = (coord_x2 - offsets[dimension_x]) * pixels[dimension_x] / scales[dimension_x];
        final int y2 = (coord_y2 - offsets[dimension_y]) * pixels[dimension_y] / scales[dimension_y];
        line(leftborder + x1, height - bottomborder - y1, leftborder + x2, height - bottomborder - y2);
    }
    
    private void drawHorizontalScale(final boolean top, final int scale, final int pixelperscale, final int offset, final String colorNaming, final String colorScale, final String name) {
        final int y = (top) ? topborder : height - bottomborder;
        int x = leftborder;
        int s = offset;
        while (x < width - rightborder) {
            if ((colorScale != null) && (x > leftborder) && (x < (width - rightborder))) {
                setColor(colorScale);
                line(x, topborder, x, height - bottomborder);
            }
            setColor(colorNaming);
            line(x, y - 3, x, y + 3);
            ymageToolPrint.print(this, x, (top) ? y - 3 : y + 9, 0, Integer.toString(s), -1);
            x += pixelperscale;
            s += scale;
        }
        setColor(colorNaming);
        ymageToolPrint.print(this, width - rightborder, (top) ? y - 9 : y + 15, 0, name, 1);
        line(leftborder - 4, y, width - rightborder + 4, y);
    }
    
    private void drawVerticalScale(final boolean left, final int scale, final int pixelperscale, final int offset, final String colorNaming, final String colorScale, final String name) {
        final int x = (left) ? leftborder : width - rightborder;
        int y = height - bottomborder;
        int s = offset;
        String s1;
        int s1max = 0;
        while (y > topborder) {
            if ((colorScale != null) && (y > topborder) && (y < (height - bottomborder))) {
                setColor(colorScale);
                line(leftborder, y, width - rightborder, y);
            }
            setColor(colorNaming);
            line(x - 3, y, x + 3, y);
            s1 = (s >= 1000000 && s % 10000 == 0) ? Integer.toString(s / 1000000) + "M" : (s >= 1000 && s % 1000 == 0) ? Integer.toString(s / 1000) + "K" : Integer.toString(s);
            if (s1.length() > s1max) s1max = s1.length();
            ymageToolPrint.print(this, (left) ? leftborder - 4 : width - rightborder + 4, y, 0, s1, (left) ? 1 : -1);
            y -= pixelperscale;
            s += scale;
        }
        setColor(colorNaming);
        ymageToolPrint.print(this, (left) ? x - s1max * 6 - 6 : x + s1max * 6 + 9, topborder, 90, name, 1);
        line(x, topborder - 4, x, height - bottomborder + 4);
    }
   
    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final boolean invers = false;
        final String bg = (invers) ? "000000" : "FFFFFF";
        final String fg = (invers) ? "FFFFFF" : "000000";
        final String scale = (invers) ? "333333" : "CCCCCC";
        final String green = (invers) ? "008800" : "008800";
        final String blue = (invers) ? "0000FF" : "0000FF";
        final ymageChart ip = new ymageChart(660, 240, bg, fg, fg, 30, 30, 20, 20, "PEER PERFORMANCE GRAPH: PAGES/MINUTE and USED MEMORY", "");
        ip.declareDimension(DIMENSION_BOTTOM, 60, 60, -600, fg, scale, "TIME/SECONDS");
        //ip.declareDimension(DIMENSION_TOP, 10, 40, "000000", null, "count");
        ip.declareDimension(DIMENSION_LEFT, 50, 40, 0, green, scale , "PPM [PAGES/MINUTE]");
        ip.declareDimension(DIMENSION_RIGHT, 100, 20, 0, blue, scale, "MEMORY/MEGABYTE");
        ip.setColor(green);
        ip.chartDot(DIMENSION_BOTTOM, DIMENSION_LEFT, -160, 100, 5, null);
        ip.chartLine(DIMENSION_BOTTOM, DIMENSION_LEFT, -160, 100, -130, 200);
        ip.setColor(blue);
        ip.chartDot(DIMENSION_BOTTOM, DIMENSION_RIGHT, -50, 300, 2, null);
        ip.chartLine(DIMENSION_BOTTOM, DIMENSION_RIGHT, -80, 100, -50, 300);
        //ip.print(100, 100, 0, "TEXT", true);
        //ip.print(100, 100, 0, "1234", false);
        //ip.print(100, 100, 90, "TEXT", true);
        //ip.print(100, 100, 90, "1234", false);
        final File file = new File("/Users/admin/Desktop/testimage.png");
        try {
            final FileOutputStream fos = new FileOutputStream(file);
            ImageIO.write(ip.getImage(), "png", fos);
            fos.close();
        } catch (final IOException e) {}
        
    }
    
}
